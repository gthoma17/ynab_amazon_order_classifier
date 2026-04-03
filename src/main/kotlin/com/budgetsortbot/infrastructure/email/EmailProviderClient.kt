package com.budgetsortbot.infrastructure.email

import java.time.Instant

interface EmailProviderClient {
    fun searchOrders(token: String, sinceDate: Instant): List<EmailOrder>
}

data class EmailOrder(
    val messageId: String,
    val receivedAt: Instant,
    val bodyText: String
)
