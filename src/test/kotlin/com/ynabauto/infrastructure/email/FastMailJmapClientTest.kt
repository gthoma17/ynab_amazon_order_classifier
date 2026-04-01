package com.ynabauto.infrastructure.email

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.time.Instant

class FastMailJmapClientTest {

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var fastMailClient: FastMailJmapClient

    @BeforeEach
    fun setup() {
        val restTemplate = RestTemplate()
        mockServer = MockRestServiceServer.createServer(restTemplate)
        fastMailClient = FastMailJmapClient(RestClient.builder().requestFactory(restTemplate.requestFactory))
    }

    @Test
    fun `searchOrders returns empty list when Email query returns no ids`() {
        mockServer.expect(requestTo("${FastMailJmapClient.FASTMAIL_BASE_URL}/.well-known/jmap"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer test-token"))
            .andRespond(withSuccess(SESSION_JSON, MediaType.APPLICATION_JSON))

        mockServer.expect(requestTo(JMAP_API_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """{"methodResponses":[["Email/query",{"accountId":"u1","ids":[],"total":0,"position":0},"a"]]}""",
                MediaType.APPLICATION_JSON
            ))

        val result = fastMailClient.searchOrders("user@fastmail.com", "test-token", Instant.parse("2024-01-01T00:00:00Z"))

        assertEquals(0, result.size)
        mockServer.verify()
    }

    @Test
    fun `searchOrders sends correct Authorization header on all requests`() {
        mockServer.expect(requestTo("${FastMailJmapClient.FASTMAIL_BASE_URL}/.well-known/jmap"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer my-token"))
            .andRespond(withSuccess(SESSION_JSON, MediaType.APPLICATION_JSON))

        mockServer.expect(requestTo(JMAP_API_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer my-token"))
            .andRespond(withSuccess(
                """{"methodResponses":[["Email/query",{"accountId":"u1","ids":[],"total":0,"position":0},"a"]]}""",
                MediaType.APPLICATION_JSON
            ))

        fastMailClient.searchOrders("user@fastmail.com", "my-token", Instant.parse("2024-01-01T00:00:00Z"))

        mockServer.verify()
    }

    @Test
    fun `searchOrders returns parsed EmailOrders when emails are found`() {
        mockServer.expect(requestTo("${FastMailJmapClient.FASTMAIL_BASE_URL}/.well-known/jmap"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(SESSION_JSON, MediaType.APPLICATION_JSON))

        mockServer.expect(requestTo(JMAP_API_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                """{"methodResponses":[["Email/query",{"accountId":"u1","ids":["email-1","email-2"],"total":2,"position":0},"a"]]}""",
                MediaType.APPLICATION_JSON
            ))

        mockServer.expect(requestTo(JMAP_API_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(EMAIL_GET_RESPONSE_JSON, MediaType.APPLICATION_JSON))

        val result = fastMailClient.searchOrders("user@fastmail.com", "test-token", Instant.parse("2024-01-01T00:00:00Z"))

        assertEquals(2, result.size)
        assertEquals("<order-123@amazon.com>", result[0].messageId)
        assertEquals(Instant.parse("2024-01-15T10:00:00Z"), result[0].receivedAt)
        assertEquals("Your Amazon order confirmation...", result[0].bodyText)
        assertEquals("<order-456@amazon.com>", result[1].messageId)
        mockServer.verify()
    }

    @Test
    fun `searchOrders uses Amazon filter in Email query`() {
        mockServer.expect(requestTo("${FastMailJmapClient.FASTMAIL_BASE_URL}/.well-known/jmap"))
            .andRespond(withSuccess(SESSION_JSON, MediaType.APPLICATION_JSON))

        mockServer.expect(requestTo(JMAP_API_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect { request ->
                val body = (request as MockClientHttpRequest).bodyAsString
                assert(body.contains(FastMailJmapClient.AMAZON_FROM_FILTER)) {
                    "Expected body to contain '${FastMailJmapClient.AMAZON_FROM_FILTER}' but was: $body"
                }
                assert(body.contains(FastMailJmapClient.AMAZON_SUBJECT_FILTER)) {
                    "Expected body to contain '${FastMailJmapClient.AMAZON_SUBJECT_FILTER}' but was: $body"
                }
            }
            .andRespond(withSuccess(
                """{"methodResponses":[["Email/query",{"accountId":"u1","ids":[],"total":0,"position":0},"a"]]}""",
                MediaType.APPLICATION_JSON
            ))

        fastMailClient.searchOrders("user@fastmail.com", "token", Instant.parse("2024-01-01T00:00:00Z"))

        mockServer.verify()
    }

    companion object {
        const val JMAP_API_URL = "https://api.fastmail.com/jmap/api/"

        val SESSION_JSON = """
            {
                "username": "user@fastmail.com",
                "apiUrl": "$JMAP_API_URL",
                "primaryAccounts": {
                    "urn:ietf:params:jmap:core": "u1",
                    "urn:ietf:params:jmap:mail": "u1"
                }
            }
        """.trimIndent()

        val EMAIL_GET_RESPONSE_JSON = """
            {"methodResponses":[["Email/get",{
                "accountId":"u1",
                "list":[
                    {
                        "id":"email-1",
                        "messageId":["<order-123@amazon.com>"],
                        "receivedAt":"2024-01-15T10:00:00Z",
                        "bodyValues":{"1":{"value":"Your Amazon order confirmation...","isEncodingProblem":false,"isTruncated":false}},
                        "textBody":[{"partId":"1","type":"text/plain"}]
                    },
                    {
                        "id":"email-2",
                        "messageId":["<order-456@amazon.com>"],
                        "receivedAt":"2024-01-20T12:00:00Z",
                        "bodyValues":{"1":{"value":"Another Amazon order...","isEncodingProblem":false,"isTruncated":false}},
                        "textBody":[{"partId":"1","type":"text/plain"}]
                    }
                ],
                "notFound":[]
            },"a"]]}
        """.trimIndent()
    }
}
