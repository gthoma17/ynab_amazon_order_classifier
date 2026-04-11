package com.budgetsortbot.e2e

import com.budgetsortbot.Application
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.springframework.boot.runApplication
import java.io.File

/**
 * Manual dev server for exercising the full UI against WireMock without touching live APIs.
 *
 * Start backend:   ./gradlew runDevServer
 * Start frontend:  cd frontend && npm run dev
 * Open browser:    http://localhost:5173
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * VALID API KEYS  (copy-paste into the Configuration page → API Keys section)
 * ──────────────────────────────────────────────────────────────────────────────
 *   YNAB Token:            ynab-valid-dev-token
 *   FastMail API Token:    fastmail-valid-dev-token
 *   Gemini API Key:        gemini-valid-dev-key
 *   YNAB Budget ID:        budget-dev-001   ← shown in the budget dropdown
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * INVALID API KEYS (use any OTHER value to trigger error states)
 * ──────────────────────────────────────────────────────────────────────────────
 *   YNAB wrong token:   wrong-ynab-token    → "Test YNAB" / get-budgets / categories / transactions all return 401
 *   FastMail wrong key: wrong-fm-token      → "Test FastMail" returns 401
 *   Gemini wrong key:   wrong-gemini-key    → "Test Gemini" returns 400 INVALID_ARGUMENT
 *
 * Run via:  ./gradlew runDevServer
 */

const val VALID_YNAB_TOKEN = "ynab-valid-dev-token"
const val VALID_FASTMAIL_TOKEN = "fastmail-valid-dev-token"
const val VALID_GEMINI_KEY = "gemini-valid-dev-key"
const val DEV_BUDGET_ID = "budget-dev-001"

fun main() {
    val dbFile = File("/tmp/budget-sortbot-dev.sqlite")
    val logDbFile = File("/tmp/budget-sortbot-dev-logs.sqlite")
    dbFile.delete()
    logDbFile.delete()

    val wireMock = WireMockServer(WireMockConfiguration.options().port(9090))
    wireMock.start()

    stubYnabWithAuth(wireMock)
    stubFastMailWithAuth(wireMock)
    stubGeminiWithAuth(wireMock)

    System.setProperty("app.fastmail.base-url", "http://localhost:9090")
    System.setProperty("app.ynab.base-url", "http://localhost:9090/v1")
    System.setProperty("app.gemini.base-url", "http://localhost:9090/v1beta")
    System.setProperty("server.port", "8080")
    System.setProperty("spring.datasource.url", "jdbc:sqlite:${dbFile.absolutePath}")
    System.setProperty("app.blacklite.path", logDbFile.absolutePath)

    printBanner()

    runApplication<Application>()
}

private fun printBanner() {
    println(
        """
        ╔══════════════════════════════════════════════════════════════════╗
        ║           budget-sortbot  —  WireMock Dev Server                ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║  Backend listening on  http://localhost:8080                     ║
        ║  WireMock stubs on     http://localhost:9090                     ║
        ║                                                                  ║
        ║  Start frontend (separate terminal):                             ║
        ║    cd frontend && npm run dev                                    ║
        ║  Then open:  http://localhost:5173                               ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║  VALID API KEYS                                                  ║
        ║    YNAB Token:          $VALID_YNAB_TOKEN              ║
        ║    FastMail Token:      $VALID_FASTMAIL_TOKEN          ║
        ║    Gemini Key:          $VALID_GEMINI_KEY              ║
        ║    YNAB Budget ID:      $DEV_BUDGET_ID                          ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║  INVALID (error state) — use any OTHER string                    ║
        ║    YNAB wrong token → 401 on test, budgets, categories, txns    ║
        ║    FastMail wrong   → 401 on test + email search                ║
        ║    Gemini wrong     → 400 INVALID_ARGUMENT on test + classify   ║
        ╚══════════════════════════════════════════════════════════════════╝
        """.trimIndent(),
    )
}

// ─── YNAB ─────────────────────────────────────────────────────────────────────

private fun stubYnabWithAuth(wireMock: WireMockServer) {
    val validAuth = "Bearer $VALID_YNAB_TOKEN"

    // Priority 1: valid token → success responses
    wireMock.stubFor(
        get(urlEqualTo("/v1/budgets"))
            .withHeader("Authorization", equalTo(validAuth))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """{"data":{"budgets":[{"id":"$DEV_BUDGET_ID","name":"Dev Test Budget"}],"default_budget":null}}""",
                    ),
            ),
    )

    wireMock.stubFor(
        get(urlPathMatching("/v1/budgets/.*/categories"))
            .withHeader("Authorization", equalTo(validAuth))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"data":{"category_groups":[
                            {"id":"group-1","name":"Shopping","deleted":false,"categories":[
                                {"id":"cat-electronics","name":"Electronics","deleted":false},
                                {"id":"cat-home","name":"Home Improvement","deleted":false},
                                {"id":"cat-groceries","name":"Groceries","deleted":false}
                            ]},
                            {"id":"group-2","name":"Entertainment","deleted":false,"categories":[
                                {"id":"cat-books","name":"Books","deleted":false},
                                {"id":"cat-games","name":"Games & Hobbies","deleted":false}
                            ]}
                        ]}}
                        """.trimIndent(),
                    ),
            ),
    )

    wireMock.stubFor(
        get(urlPathMatching("/v1/budgets/.*/transactions"))
            .withHeader("Authorization", equalTo(validAuth))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"data":{"transactions":[{
                            "id":"txn-dev-1",
                            "date":"2024-01-15",
                            "amount":-42600,
                            "memo":null,
                            "category_id":null,
                            "payee_name":"Amazon.com"
                        },{
                            "id":"txn-dev-2",
                            "date":"2024-01-18",
                            "amount":-1299,
                            "memo":null,
                            "category_id":null,
                            "payee_name":"Amazon.com"
                        }],"server_knowledge":1000}}
                        """.trimIndent(),
                    ),
            ),
    )

    wireMock.stubFor(
        put(urlPathMatching("/v1/budgets/.*/transactions/.*"))
            .withHeader("Authorization", equalTo(validAuth))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"data":{"transaction":{"id":"txn-dev-1","date":"2024-01-15","amount":-42600}}}"""),
            ),
    )

    // Priority 10: wrong/missing token → 401 for ALL YNAB paths
    val ynabUnauthorized =
        aResponse()
            .withStatus(401)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"error":{"id":"401","name":"unauthorized","detail":"Unauthorized"}}""")

    wireMock.stubFor(
        get(urlEqualTo("/v1/budgets"))
            .atPriority(10)
            .willReturn(ynabUnauthorized),
    )

    wireMock.stubFor(
        get(urlPathMatching("/v1/budgets/.*/categories"))
            .atPriority(10)
            .willReturn(ynabUnauthorized),
    )

    wireMock.stubFor(
        get(urlPathMatching("/v1/budgets/.*/transactions"))
            .atPriority(10)
            .willReturn(ynabUnauthorized),
    )

    wireMock.stubFor(
        put(urlPathMatching("/v1/budgets/.*/transactions/.*"))
            .atPriority(10)
            .willReturn(ynabUnauthorized),
    )
}

// ─── FastMail ─────────────────────────────────────────────────────────────────

private fun stubFastMailWithAuth(wireMock: WireMockServer) {
    val selfPort = 9090
    val validAuth = "Bearer $VALID_FASTMAIL_TOKEN"

    val fmUnauthorized =
        aResponse()
            .withStatus(401)
            .withHeader("Content-Type", "application/json")
            .withBody(
                """{"type":"urn:ietf:params:jmap:error:notAuthorized","status":401,"detail":"You are not authorized to make this request"}""",
            )

    // Priority 1: valid token — JMAP session discovery
    wireMock.stubFor(
        get(urlEqualTo("/.well-known/jmap"))
            .withHeader("Authorization", equalTo(validAuth))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                            "username": "dev@fastmail.com",
                            "apiUrl": "http://localhost:$selfPort/jmap/api/",
                            "primaryAccounts": {
                                "urn:ietf:params:jmap:core": "acct-dev",
                                "urn:ietf:params:jmap:mail": "acct-dev"
                            }
                        }
                        """.trimIndent(),
                    ),
            ),
    )

    // Priority 1: valid token — Email/query
    wireMock.stubFor(
        post(urlEqualTo("/jmap/api/"))
            .withHeader("Authorization", equalTo(validAuth))
            .withRequestBody(containing("Email/query"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"methodResponses":[["Email/query",{
                            "accountId":"acct-dev",
                            "ids":["email-dev-1","email-dev-2"],
                            "total":2,
                            "position":0
                        },"a"]]}
                        """.trimIndent(),
                    ),
            ),
    )

    // Priority 1: valid token — Email/get
    wireMock.stubFor(
        post(urlEqualTo("/jmap/api/"))
            .withHeader("Authorization", equalTo(validAuth))
            .withRequestBody(containing("Email/get"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"methodResponses":[["Email/get",{
                            "accountId":"acct-dev",
                            "list":[
                                {
                                    "id":"email-dev-1",
                                    "messageId":["<order-dev-001@amazon.com>"],
                                    "receivedAt":"2024-01-15T10:00:00Z",
                                    "bodyValues":{"1":{"value":"* TOTO Bidet Toilet Seat\n  Quantity: 1\n  42.60 USD\n\nGrand Total:\n42.60 USD","isEncodingProblem":false,"isTruncated":false}},
                                    "textBody":[{"partId":"1","type":"text/plain"}]
                                },
                                {
                                    "id":"email-dev-2",
                                    "messageId":["<order-dev-002@amazon.com>"],
                                    "receivedAt":"2024-01-18T14:30:00Z",
                                    "bodyValues":{"1":{"value":"* Kindle Paperwhite\n  Quantity: 1\n  12.99 USD\n\nGrand Total:\n12.99 USD","isEncodingProblem":false,"isTruncated":false}},
                                    "textBody":[{"partId":"1","type":"text/plain"}]
                                }
                            ],
                            "notFound":[]
                        },"a"]]}
                        """.trimIndent(),
                    ),
            ),
    )

    // Priority 10: wrong/missing token — all FastMail endpoints return 401
    wireMock.stubFor(
        get(urlEqualTo("/.well-known/jmap"))
            .atPriority(10)
            .willReturn(fmUnauthorized),
    )

    wireMock.stubFor(
        post(urlEqualTo("/jmap/api/"))
            .atPriority(10)
            .willReturn(fmUnauthorized),
    )
}

// ─── Gemini ───────────────────────────────────────────────────────────────────

private fun stubGeminiWithAuth(wireMock: WireMockServer) {
    val geminiInvalid =
        aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody(
                """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}""",
            )

    // Priority 1: valid key — GET models probe
    wireMock.stubFor(
        get(urlPathMatching("/v1beta/models"))
            .withQueryParam("key", equalTo(VALID_GEMINI_KEY))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"models":[{"name":"models/gemini-2.5-flash-lite"}]}"""),
            ),
    )

    // Priority 1: valid key — generateContent (classify)
    wireMock.stubFor(
        post(urlPathMatching("/v1beta/models/.*:generateContent"))
            .withQueryParam("key", equalTo(VALID_GEMINI_KEY))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"candidates":[{"content":{"parts":[{"text":"cat-electronics"}]}}]}"""),
            ),
    )

    // Priority 10: wrong/missing key — all Gemini paths return 400
    wireMock.stubFor(
        get(urlPathMatching("/v1beta/models"))
            .atPriority(10)
            .willReturn(geminiInvalid),
    )

    wireMock.stubFor(
        post(urlPathMatching("/v1beta/models/.*:generateContent"))
            .atPriority(10)
            .willReturn(geminiInvalid),
    )
}
