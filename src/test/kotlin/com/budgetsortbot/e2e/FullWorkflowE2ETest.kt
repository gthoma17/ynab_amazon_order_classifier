package com.budgetsortbot.e2e

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.budgetsortbot.domain.CategoryRule
import com.budgetsortbot.domain.OrderStatus
import com.budgetsortbot.infrastructure.persistence.AmazonOrderRepository
import com.budgetsortbot.infrastructure.persistence.CategoryRuleRepository
import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
import com.budgetsortbot.service.ConfigService
import com.budgetsortbot.service.EmailIngestionService
import com.budgetsortbot.service.YnabSyncService
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FullWorkflowE2ETest {

    companion object {
        @JvmField
        val wireMock: WireMockServer =
            WireMockServer(WireMockConfiguration.options().dynamicPort()).also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val port = wireMock.port()
            registry.add("app.ynab.base-url") { "http://localhost:$port/v1" }
            registry.add("app.fastmail.base-url") { "http://localhost:$port" }
            registry.add("app.gemini.base-url") { "http://localhost:$port/v1beta" }
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            wireMock.stop()
        }
    }

    @Autowired
    private lateinit var configService: ConfigService

    @Autowired
    private lateinit var emailIngestionService: EmailIngestionService

    @Autowired
    private lateinit var ynabSyncService: YnabSyncService

    @Autowired
    private lateinit var amazonOrderRepository: AmazonOrderRepository

    @Autowired
    private lateinit var categoryRuleRepository: CategoryRuleRepository

    @Autowired
    private lateinit var syncLogRepository: SyncLogRepository

    @BeforeEach
    fun setup() {
        wireMock.resetAll()
        amazonOrderRepository.deleteAll()
        categoryRuleRepository.deleteAll()
        syncLogRepository.deleteAll()

        configService.setValue(ConfigService.YNAB_TOKEN, "test-ynab-token")
        configService.setValue(ConfigService.YNAB_BUDGET_ID, "budget-e2e")
        configService.setValue(ConfigService.FASTMAIL_API_TOKEN, "test-fastmail-token")
        configService.setValue(ConfigService.GEMINI_KEY, "test-gemini-key")
        // Ensure test emails (dated 2024-01-15) are not filtered out by the start-from guard
        configService.setValue(ConfigService.START_FROM_DATE, "2024-01-01")

        categoryRuleRepository.save(
            CategoryRule(
                ynabCategoryId = "cat-electronics",
                ynabCategoryName = "Electronics",
                userDescription = "Electronics, gadgets, cables, phones, computers",
                updatedAt = Instant.now()
            )
        )
    }

    @Test
    fun `full workflow from email ingestion to YNAB transaction completion`() {
        val wireMockPort = wireMock.port()

        // Stub: FastMail JMAP session
        wireMock.stubFor(
            get(urlEqualTo("/.well-known/jmap"))
                .withHeader("Authorization", equalTo("Bearer test-fastmail-token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "username": "user@fastmail.com",
                                "apiUrl": "http://localhost:$wireMockPort/jmap/api/",
                                "primaryAccounts": {
                                    "urn:ietf:params:jmap:core": "acct-e2e",
                                    "urn:ietf:params:jmap:mail": "acct-e2e"
                                }
                            }
                            """.trimIndent()
                        )
                )
        )

        // Stub: FastMail Email/query
        wireMock.stubFor(
            post(urlEqualTo("/jmap/api/"))
                .withRequestBody(containing("Email/query"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"methodResponses":[["Email/query",{
                                "accountId":"acct-e2e",
                                "ids":["email-e2e-1"],
                                "total":1,
                                "position":0
                            },"a"]]}
                            """.trimIndent()
                        )
                )
        )

        // Stub: FastMail Email/get — body contains parseable Amazon order details
        wireMock.stubFor(
            post(urlEqualTo("/jmap/api/"))
                .withRequestBody(containing("Email/get"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"methodResponses":[["Email/get",{
                                "accountId":"acct-e2e",
                                "list":[{
                                    "id":"email-e2e-1",
                                    "messageId":["<order-e2e-001@amazon.com>"],
                                    "receivedAt":"2024-01-15T10:00:00Z",
                                    "bodyValues":{"1":{"value":"* TOTO Bidet Toilet Seat\n  Quantity: 1\n  426 USD\n\nGrand Total:\n426.00 USD","isEncodingProblem":false,"isTruncated":false}},
                                    "textBody":[{"partId":"1","type":"text/plain"}]
                                }],
                                "notFound":[]
                            },"a"]]}
                            """.trimIndent()
                        )
                )
        )

        // Stub: YNAB get transactions — returns a matching transaction
        wireMock.stubFor(
            get(urlPathMatching("/v1/budgets/budget-e2e/transactions"))
                .withHeader("Authorization", equalTo("Bearer test-ynab-token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"data":{"transactions":[{
                                "id":"txn-e2e-1",
                                "date":"2024-01-15",
                                "amount":-426000,
                                "memo":null,
                                "category_id":null,
                                "payee_name":"Amazon.com"
                            }],"server_knowledge":1000}}
                            """.trimIndent()
                        )
                )
        )

        // Stub: Gemini classification — returns the electronics category ID
        wireMock.stubFor(
            post(urlPathMatching("/v1beta/models/.*:generateContent"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"candidates":[{"content":{"parts":[{"text":"cat-electronics"}]}}]}"""
                        )
                )
        )

        // Stub: YNAB update transaction
        wireMock.stubFor(
            put(urlPathMatching("/v1/budgets/budget-e2e/transactions/txn-e2e-1"))
                .withHeader("Authorization", equalTo("Bearer test-ynab-token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"data":{"transaction":{"id":"txn-e2e-1","date":"2024-01-15","amount":-49990}}}"""
                        )
                )
        )

        // --- Step 1: Run email ingestion ---
        emailIngestionService.ingest()

        val ordersAfterIngestion = amazonOrderRepository.findAll()
        assertEquals(1, ordersAfterIngestion.size, "Expected one order saved after email ingestion")
        val pendingOrder = ordersAfterIngestion.first()
        assertEquals(OrderStatus.PENDING, pendingOrder.status)
        assertEquals("<order-e2e-001@amazon.com>", pendingOrder.emailMessageId)

        // --- Step 2: Run YNAB sync ---
        ynabSyncService.sync()

        val ordersAfterSync = amazonOrderRepository.findAll()
        assertEquals(1, ordersAfterSync.size, "Expected exactly one order after YNAB sync")
        val completedOrder = ordersAfterSync.first()
        assertEquals(OrderStatus.COMPLETED, completedOrder.status)
        assertEquals("txn-e2e-1", completedOrder.ynabTransactionId)
        assertEquals("cat-electronics", completedOrder.ynabCategoryId)

        // Verify YNAB update transaction was called
        wireMock.verify(putRequestedFor(urlPathMatching("/v1/budgets/budget-e2e/transactions/txn-e2e-1")))
    }
}
