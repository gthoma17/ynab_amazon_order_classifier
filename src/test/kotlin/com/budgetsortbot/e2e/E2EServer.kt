package com.budgetsortbot.e2e

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.budgetsortbot.Application
import org.springframework.boot.runApplication
import java.io.File

/**
 * Standalone server used by Playwright E2E tests.
 *
 * Starts a WireMock instance on a random port, stubs all three external
 * dependencies (FastMail JMAP, YNAB API, Gemini API), then starts the real
 * Spring Boot application pointing at those stubs.
 *
 * This lets Playwright tests exercise the actual React UI → real backend →
 * WireMock stubs pipeline without touching any live external service.
 *
 * Run via:  ./gradlew runE2EServer
 */
fun main() {
    // Ensure a clean database on every fresh start
    val dbFile = File("/tmp/ynab-e2e.sqlite")
    dbFile.delete()

    // Start WireMock on a random available port
    val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
    wireMock.start()
    val port = wireMock.port()

    stubFastMail(wireMock, port)
    stubYnab(wireMock)
    stubGemini(wireMock)

    // Inject WireMock URLs and E2E-specific settings into Spring's Environment
    System.setProperty("app.fastmail.base-url", "http://localhost:$port")
    System.setProperty("app.ynab.base-url", "http://localhost:$port/v1")
    System.setProperty("app.gemini.base-url", "http://localhost:$port/v1beta")
    System.setProperty("server.port", "8080")
    System.setProperty("spring.datasource.url", "jdbc:sqlite:${dbFile.absolutePath}")

    runApplication<Application>()
}

private fun stubFastMail(wireMock: WireMockServer, selfPort: Int) {
    // JMAP session discovery
    wireMock.stubFor(
        get(urlEqualTo("/.well-known/jmap"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                            "username": "me@fastmail.com",
                            "apiUrl": "http://localhost:$selfPort/jmap/api/",
                            "primaryAccounts": {
                                "urn:ietf:params:jmap:core": "acct-e2e",
                                "urn:ietf:params:jmap:mail": "acct-e2e"
                            }
                        }
                        """.trimIndent()
                    )
            )
    )

    // Email/query — returns a single order email ID
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

    // Email/get — returns the order email body in real Amazon format
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
}

private fun stubYnab(wireMock: WireMockServer) {
    // GET budgets — used by the YNAB "Test Connection" probe and the budget dropdown
    wireMock.stubFor(
        get(urlEqualTo("/v1/budgets"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"data":{"budgets":[{"id":"my-budget-id","name":"My Test Budget"}],"default_budget":null}}""")
            )
    )

    // GET categories — used by the Category Rules view
    wireMock.stubFor(
        get(urlPathMatching("/v1/budgets/.*/categories"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"data":{"category_groups":[
                            {"id":"group-1","name":"Shopping","deleted":false,"categories":[
                                {"id":"cat-electronics","name":"Electronics","deleted":false},
                                {"id":"cat-home","name":"Home Improvement","deleted":false}
                            ]}
                        ]}}
                        """.trimIndent()
                    )
            )
    )

    // GET transactions — returns one Amazon transaction matching the order
    wireMock.stubFor(
        get(urlPathMatching("/v1/budgets/.*/transactions"))
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

    // PUT update transaction
    wireMock.stubFor(
        put(urlPathMatching("/v1/budgets/.*/transactions/.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """{"data":{"transaction":{"id":"txn-e2e-1","date":"2024-01-15","amount":-426000}}}"""
                    )
            )
    )
}

private fun stubGemini(wireMock: WireMockServer) {
    // GET models — used by the Gemini "Test Connection" probe
    wireMock.stubFor(
        get(urlPathMatching("/v1beta/models"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"models":[]}""")
            )
    )

    wireMock.stubFor(
        post(urlPathMatching("/v1beta/models/.*:generateContent"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"candidates":[{"content":{"parts":[{"text":"cat-electronics"}]}}]}""")
            )
    )
}
