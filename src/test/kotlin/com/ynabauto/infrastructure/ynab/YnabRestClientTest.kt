package com.ynabauto.infrastructure.ynab

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.time.LocalDate

class YnabRestClientTest {

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var ynabRestClient: YnabRestClient

    @BeforeEach
    fun setup() {
        val restTemplate = RestTemplate()
        mockServer = MockRestServiceServer.createServer(restTemplate)
        val objectMapper = jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        ynabRestClient = YnabRestClient(RestClient.builder().requestFactory(restTemplate.requestFactory), objectMapper, "https://api.ynab.com/v1")
    }

    // --- getTransactions ---

    @Test
    fun `getTransactions sends GET with Authorization header and parses response`() {
        mockServer.expect(requestToUriTemplate("${"https://api.ynab.com/v1"}/budgets/{budgetId}/transactions", "budget-123"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-xyz"))
            .andRespond(withSuccess(
                """{"data":{"transactions":[
                    {"id":"txn-1","date":"2024-01-15","amount":-4999000,"memo":"Amazon","category_id":"cat-1","payee_name":"Amazon.com"}
                ]}}""",
                MediaType.APPLICATION_JSON
            ))

        val transactions = ynabRestClient.getTransactions("budget-123", "token-xyz")

        assertEquals(1, transactions.size)
        assertEquals("txn-1", transactions[0].id)
        assertEquals(LocalDate.of(2024, 1, 15), transactions[0].date)
        assertEquals(-4999000L, transactions[0].amount)
        assertEquals("Amazon", transactions[0].memo)
        assertEquals("cat-1", transactions[0].categoryId)
        assertEquals("Amazon.com", transactions[0].payeeName)
        mockServer.verify()
    }

    @Test
    fun `getTransactions includes since_date query param when provided`() {
        mockServer.expect(requestToUriTemplate(
            "${"https://api.ynab.com/v1"}/budgets/{budgetId}/transactions?since_date={sinceDate}",
            "budget-abc",
            "2024-03-01"
        ))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"data":{"transactions":[]}}""", MediaType.APPLICATION_JSON))

        val result = ynabRestClient.getTransactions("budget-abc", "any-token", sinceDate = LocalDate.of(2024, 3, 1))

        assertEquals(0, result.size)
        mockServer.verify()
    }

    @Test
    fun `getTransactions returns empty list when no transactions in response`() {
        mockServer.expect(requestToUriTemplate("${"https://api.ynab.com/v1"}/budgets/{budgetId}/transactions", "budget-empty"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"data":{"transactions":[]}}""", MediaType.APPLICATION_JSON))

        val result = ynabRestClient.getTransactions("budget-empty", "token")

        assertEquals(0, result.size)
        mockServer.verify()
    }

    @Test
    fun `getTransactions handles nullable fields gracefully`() {
        mockServer.expect(requestToUriTemplate("${"https://api.ynab.com/v1"}/budgets/{budgetId}/transactions", "budget-123"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """{"data":{"transactions":[
                    {"id":"txn-2","date":"2024-02-01","amount":-1000,"memo":null,"category_id":null,"payee_name":null}
                ]}}""",
                MediaType.APPLICATION_JSON
            ))

        val transactions = ynabRestClient.getTransactions("budget-123", "token")

        assertEquals(1, transactions.size)
        assertNull(transactions[0].memo)
        assertNull(transactions[0].categoryId)
        assertNull(transactions[0].payeeName)
        mockServer.verify()
    }

    // --- getCategories ---

    @Test
    fun `getCategories sends GET with Authorization header and returns flattened category list`() {
        mockServer.expect(requestToUriTemplate("${"https://api.ynab.com/v1"}/budgets/{budgetId}/categories", "budget-123"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer token-xyz"))
            .andRespond(withSuccess(
                """{"data":{"category_groups":[
                    {"id":"group-1","name":"Everyday Expenses","deleted":false,"categories":[
                        {"id":"cat-1","name":"Groceries","deleted":false},
                        {"id":"cat-2","name":"Dining Out","deleted":false}
                    ]},
                    {"id":"group-2","name":"Savings","deleted":false,"categories":[
                        {"id":"cat-3","name":"Emergency Fund","deleted":false}
                    ]}
                ]}}""",
                MediaType.APPLICATION_JSON
            ))

        val categories = ynabRestClient.getCategories("budget-123", "token-xyz")

        assertEquals(3, categories.size)
        assertEquals("cat-1", categories[0].id)
        assertEquals("Groceries", categories[0].name)
        assertEquals("Everyday Expenses", categories[0].categoryGroupName)
        assertEquals("cat-3", categories[2].id)
        assertEquals("Savings", categories[2].categoryGroupName)
        mockServer.verify()
    }

    @Test
    fun `getCategories excludes deleted category groups and deleted categories`() {
        mockServer.expect(requestToUriTemplate("${"https://api.ynab.com/v1"}/budgets/{budgetId}/categories", "budget-123"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                """{"data":{"category_groups":[
                    {"id":"group-active","name":"Active","deleted":false,"categories":[
                        {"id":"cat-active","name":"Active Cat","deleted":false},
                        {"id":"cat-deleted","name":"Deleted Cat","deleted":true}
                    ]},
                    {"id":"group-deleted","name":"Deleted Group","deleted":true,"categories":[
                        {"id":"cat-in-deleted-group","name":"In Deleted Group","deleted":false}
                    ]}
                ]}}""",
                MediaType.APPLICATION_JSON
            ))

        val categories = ynabRestClient.getCategories("budget-123", "token")

        assertEquals(1, categories.size)
        assertEquals("cat-active", categories[0].id)
        mockServer.verify()
    }

    @Test
    fun `getCategories returns empty list when no category groups`() {
        mockServer.expect(requestToUriTemplate("${"https://api.ynab.com/v1"}/budgets/{budgetId}/categories", "budget-empty"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"data":{"category_groups":[]}}""", MediaType.APPLICATION_JSON))

        val result = ynabRestClient.getCategories("budget-empty", "token")

        assertEquals(0, result.size)
        mockServer.verify()
    }

    // --- updateTransaction ---

    @Test
    fun `updateTransaction sends PUT with Authorization header and correct body`() {
        mockServer.expect(requestToUriTemplate(
            "${"https://api.ynab.com/v1"}/budgets/{budgetId}/transactions/{transactionId}",
            "budget-123",
            "txn-abc"
        ))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("Authorization", "Bearer update-token"))
            .andRespond(withSuccess(
                """{"data":{"transaction":{"id":"txn-abc","date":"2024-01-15","amount":-4999000}}}""",
                MediaType.APPLICATION_JSON
            ))

        ynabRestClient.updateTransaction(
            budgetId = "budget-123",
            transactionId = "txn-abc",
            token = "update-token",
            memo = "Amazon: USB Cable, Phone Case",
            categoryId = "cat-electronics"
        )

        mockServer.verify()
    }
}
