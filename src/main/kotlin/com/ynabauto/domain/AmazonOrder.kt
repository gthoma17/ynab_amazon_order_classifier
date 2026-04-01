package com.ynabauto.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "amazon_orders")
data class AmazonOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "email_message_id", nullable = false, unique = true)
    val emailMessageId: String,

    @Column(name = "order_date", nullable = false)
    val orderDate: Instant,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: BigDecimal,

    @Column(name = "items_json", nullable = false)
    val itemsJson: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    val status: OrderStatus,

    @Column(name = "ynab_transaction_id")
    val ynabTransactionId: String? = null,

    @Column(name = "ynab_category_id")
    val ynabCategoryId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
)
