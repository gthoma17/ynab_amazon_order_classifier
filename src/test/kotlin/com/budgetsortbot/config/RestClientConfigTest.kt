package com.budgetsortbot.config

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate

class RestClientConfigTest {

    private val config = RestClientConfig()
    private lateinit var mockServer: MockRestServiceServer
    private lateinit var restTemplate: RestTemplate

    @BeforeEach
    fun setup() {
        restTemplate = RestTemplate()
        mockServer = MockRestServiceServer.createServer(restTemplate)
    }

    @Test
    fun `restClientCustomizer bean is not null`() {
        val customizer = config.restClientCustomizer()
        assertNotNull(customizer)
    }

    @Test
    fun `restClientCustomizer applies User-Agent header to all requests`() {
        mockServer.expect(requestTo("https://example.com/test"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("User-Agent", "YNAB-Amazon-Automator/1.0"))
            .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN))

        val builder = RestClient.builder().requestFactory(restTemplate.requestFactory)
        config.restClientCustomizer().customize(builder)
        val client = builder.build()

        client.get().uri("https://example.com/test").retrieve().toBodilessEntity()

        mockServer.verify()
    }
}
