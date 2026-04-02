package com.ynabauto.e2e

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.ynabauto.domain.AppConfig
import com.ynabauto.domain.CategoryRule
import com.ynabauto.domain.OrderStatus
import com.ynabauto.infrastructure.persistence.AmazonOrderRepository
import com.ynabauto.infrastructure.persistence.AppConfigRepository
import com.ynabauto.infrastructure.persistence.CategoryRuleRepository
import com.ynabauto.infrastructure.persistence.SyncLogRepository
import com.ynabauto.service.ConfigService
import com.ynabauto.service.EmailIngestionService
import com.ynabauto.service.YnabSyncService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "app.email.poll-interval-ms=999999999",
        "app.ynab.poll-interval-ms=999999999"
    ]
)
class FullWorkflowE2eTest {

    companion object {
        private val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort()).also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            val port = wireMock.port()
            registry.add("app.ynab.base-url") { "http://localhost:$port" }
            registry.add("app.fastmail.base-url") { "http://localhost:$port" }
            registry.add("app.gemini.base-url") { "http://localhost:$port/v1beta" }
        }
    }

    @Autowired lateinit var emailIngestionService: EmailIngestionService
    @Autowired lateinit var ynabSyncService: YnabSyncService
    @Autowired lateinit var amazonOrderRepository: AmazonOrderRepository
    @Autowired lateinit var appConfigRepository: AppConfigRepository
    @Autowired lateinit var categoryRuleRepository: CategoryRuleRepository
    @Autowired lateinit var syncLogRepository: SyncLogRepository

    @BeforeEach
    fun setup() {
        wireMock.resetAll()
        amazonOrderRepository.deleteAll()
        syncLogRepository.deleteAll()
        categoryRuleRepository.deleteAll()
        appConfigRepository.deleteAll()

        appConfigRepository.saveAll(
            listOf(
                AppConfig(ConfigService.FASTMAIL_USER, "user@fastmail.com", Instant.now()),
                AppConfig(ConfigService.FASTMAIL_TOKEN, "test-fm-token", Instant.now()),
                AppConfig(ConfigService.YNAB_TOKEN, "test-ynab-token", Instant.now()),
                AppConfig(ConfigService.YNAB_BUDGET_ID, "test-budget-id", Instant.now()),
                AppConfig(ConfigService.GEMINI_KEY, "test-gemini-key", Instant.now()),
            )
        )

        categoryRuleRepository.save(
            CategoryRule(
                ynabCategoryId = "cat-electronics",
                ynabCategoryName = "Electronics",
                userDescription = "Electronic devices and accessories",
                updatedAt = Instant.now()
            )
        )
    }

    @Test
    fun `full workflow ingests email, matches YNAB transaction, and classifies order`() {
        val port = wireMock.port()

        // Stub: FastMail JMAP session
        wireMock.stubFor(
            get(urlEqualTo("/.well-known/jmap"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "apiUrl": "http://localhost:$port/jmap/api/",
                              "primaryAccounts": {
                                "urn:ietf:params:jmap:mail": "account-e2e-001"
                              }
                            }
                            """.trimIndent()
                        )
                )
        )

        // Stub: JMAP Email/query
        wireMock.stubFor(
            post(urlEqualTo("/jmap/api/"))
                .withRequestBody(matchingJsonPath("$.methodCalls[0][0]", equalTo("Email/query")))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "methodResponses": [
                                ["Email/query", {"ids": ["email-e2e-001"]}, "a"]
                              ]
                            }
                            """.trimIndent()
                        )
                )
        )

        // Stub: JMAP Email/get
        wireMock.stubFor(
            post(urlEqualTo("/jmap/api/"))
                .withRequestBody(matchingJsonPath("$.methodCalls[0][0]", equalTo("Email/get")))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "methodResponses": [
                                ["Email/get", {
                                  "list": [{
                                    "id": "email-e2e-001",
                                    "messageId": ["<order-confirm-e2e@amazon.com>"],
                                    "receivedAt": "2026-04-01T12:00:00Z",
                                    "textBody": [{"partId": "1", "blobId": "blob-1", "size": 100, "type": "text/plain"}],
                                    "bodyValues": {
                                      "1": {
                                        "value": "Thank you for your order!\n1 of: USB-C Cable\nOrder Total: ${'$'}25.00",
                                        "isEncodingProblem": false,
                                        "isTruncated": false
                                      }
                                    }
                                  }],
                                  "notFound": []
                                }, "a"]
                              ]
                            }
                            """.trimIndent()
                        )
                )
        )

        // Stub: YNAB get transactions
        wireMock.stubFor(
            get(urlPathEqualTo("/budgets/test-budget-id/transactions"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "data": {
                                "transactions": [{
                                  "id": "txn-e2e-001",
                                  "date": "2026-04-01",
                                  "amount": -25000,
                                  "memo": null,
                                  "cleared": "cleared",
                                  "approved": true,
                                  "category_id": null,
                                  "payee_name": "Amazon.com",
                                  "deleted": false
                                }],
                                "server_knowledge": 12345
                              }
                            }
                            """.trimIndent()
                        )
                )
        )

        // Stub: Gemini classification
        wireMock.stubFor(
            post(urlPathEqualTo("/v1beta/models/gemini-2.5-flash-lite:generateContent"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "candidates": [{
                                "content": {
                                  "parts": [{"text": "cat-electronics"}]
                                }
                              }]
                            }
                            """.trimIndent()
                        )
                )
        )

        // Stub: YNAB update transaction
        wireMock.stubFor(
            put(urlPathEqualTo("/budgets/test-budget-id/transactions/txn-e2e-001"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "data": {
                                "transaction": {
                                  "id": "txn-e2e-001",
                                  "date": "2026-04-01",
                                  "amount": -25000,
                                  "memo": "Amazon order",
                                  "cleared": "cleared",
                                  "approved": true,
                                  "category_id": "cat-electronics",
                                  "payee_name": "Amazon.com",
                                  "deleted": false
                                },
                                "server_knowledge": 12346
                              }
                            }
                            """.trimIndent()
                        )
                )
        )

        // Step 1: Run email ingestion
        emailIngestionService.ingest()

        // Assert order saved as PENDING
        val orders = amazonOrderRepository.findAll()
        assertEquals(1, orders.size, "Expected exactly one order after ingestion")
        val order = orders.first()
        assertEquals(OrderStatus.PENDING, order.status)
        assertEquals(0, BigDecimal("25.00").compareTo(order.totalAmount), "Order amount should be 25.00")
        assertEquals("<order-confirm-e2e@amazon.com>", order.emailMessageId)

        // Step 2: Run YNAB sync (match + classify + update)
        ynabSyncService.sync()

        // Assert order is COMPLETED with classification
        val completedOrder = amazonOrderRepository.findById(order.id!!).orElse(null)
        assertNotNull(completedOrder, "Order should still exist after sync")
        assertEquals(OrderStatus.COMPLETED, completedOrder.status)
        assertEquals("txn-e2e-001", completedOrder.ynabTransactionId)
        assertEquals("cat-electronics", completedOrder.ynabCategoryId)
    }
}
