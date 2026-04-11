package com.budgetsortbot.infrastructure.email

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant

@Component
class FastMailJmapClient(
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    @Value("\${app.fastmail.base-url:https://api.fastmail.com}") private val fastmailBaseUrl: String = FASTMAIL_BASE_URL,
) : EmailProviderClient {
    companion object {
        const val FASTMAIL_BASE_URL = "https://api.fastmail.com"
        const val JMAP_MAIL_URN = "urn:ietf:params:jmap:mail"
        const val JMAP_CORE_URN = "urn:ietf:params:jmap:core"
        const val AMAZON_FROM_FILTER = "auto-confirm@amazon.com"
        const val AMAZON_SUBJECT_FILTER = "Ordered:"
        private val log = KotlinLogging.logger {}
    }

    private val client = restClientBuilder.build()

    override fun searchOrders(
        token: String,
        sinceDate: Instant,
    ): List<EmailOrder> {
        val session = getSession(token)
        val accountId =
            session.primaryAccounts[JMAP_MAIL_URN]
                ?: throw RuntimeException("No JMAP mail account found in session")

        val emailIds = queryOrderEmails(session.apiUrl, accountId, token, sinceDate)
        log.debug { "Email/query returned ${emailIds.size} email IDs" }

        if (emailIds.isEmpty()) return emptyList()

        val orders = fetchEmailDetails(session.apiUrl, accountId, token, emailIds)
        log.debug { "Fetched ${orders.size} email orders from ${emailIds.size} IDs" }
        return orders
    }

    private fun getSession(token: String): JmapSession {
        val rawBody =
            client
                .get()
                .uri("$fastmailBaseUrl/.well-known/jmap")
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(String::class.java)
        log.debug { "JMAP session response: $rawBody" }
        rawBody ?: throw RuntimeException("Failed to get JMAP session")
        val session = objectMapper.readValue(rawBody, JmapSession::class.java)
        log.debug { "JMAP session: apiUrl=${session.apiUrl}, accounts=${session.primaryAccounts.keys}" }
        return session
    }

    private fun queryOrderEmails(
        apiUrl: String,
        accountId: String,
        token: String,
        sinceDate: Instant,
    ): List<String> {
        val response =
            postJmap(
                apiUrl,
                token,
                JmapRequest(
                    using = listOf(JMAP_CORE_URN, JMAP_MAIL_URN),
                    methodCalls =
                        listOf(
                            listOf(
                                "Email/query",
                                mapOf(
                                    "accountId" to accountId,
                                    "filter" to
                                        mapOf(
                                            "from" to AMAZON_FROM_FILTER,
                                            "subject" to AMAZON_SUBJECT_FILTER,
                                            "after" to sinceDate.toString(),
                                        ),
                                    "sort" to listOf(mapOf("property" to "receivedAt", "isAscending" to false)),
                                    "limit" to 50,
                                ),
                                "a",
                            ),
                        ),
                ),
            )

        val queryResult =
            response.methodResponses
                .firstOrNull { it[0].asText() == "Email/query" }
                ?.get(1)
                ?: throw RuntimeException("Email/query response not found")

        return queryResult["ids"]?.map { it.asText() } ?: emptyList()
    }

    private fun fetchEmailDetails(
        apiUrl: String,
        accountId: String,
        token: String,
        emailIds: List<String>,
    ): List<EmailOrder> {
        val response =
            postJmap(
                apiUrl,
                token,
                JmapRequest(
                    using = listOf(JMAP_CORE_URN, JMAP_MAIL_URN),
                    methodCalls =
                        listOf(
                            listOf(
                                "Email/get",
                                mapOf(
                                    "accountId" to accountId,
                                    "ids" to emailIds,
                                    "properties" to listOf("id", "messageId", "receivedAt", "bodyValues", "textBody"),
                                    "fetchTextBodyValues" to true,
                                ),
                                "a",
                            ),
                        ),
                ),
            )

        val emailList =
            response.methodResponses
                .firstOrNull { it[0].asText() == "Email/get" }
                ?.get(1)
                ?.get("list")
                ?: return emptyList()

        return emailList.mapNotNull { email ->
            val messageId = email["messageId"]?.firstOrNull()?.asText() ?: return@mapNotNull null
            val receivedAt =
                email["receivedAt"]
                    ?.asText()
                    ?.let { Instant.parse(it) }
                    ?: return@mapNotNull null
            val textPartId = email["textBody"]?.firstOrNull()?.get("partId")?.asText()
            val bodyText = textPartId?.let { email["bodyValues"]?.get(it)?.get("value")?.asText() } ?: ""

            EmailOrder(
                messageId = messageId,
                receivedAt = receivedAt,
                bodyText = bodyText,
            )
        }
    }

    private fun postJmap(
        apiUrl: String,
        token: String,
        request: JmapRequest,
    ): JmapResponse {
        val rawBody =
            client
                .post()
                .uri(apiUrl)
                .header("Authorization", "Bearer $token")
                .body(request)
                .retrieve()
                .body(String::class.java)
        log.debug { "JMAP response: $rawBody" }
        rawBody ?: throw RuntimeException("Empty JMAP response")
        return objectMapper.readValue(rawBody, JmapResponse::class.java)
    }
}

private data class JmapSession(
    val apiUrl: String,
    val primaryAccounts: Map<String, String>,
)

private data class JmapRequest(
    val using: List<String>,
    val methodCalls: List<List<Any?>>,
)

private data class JmapResponse(
    val methodResponses: List<JsonNode>,
)
