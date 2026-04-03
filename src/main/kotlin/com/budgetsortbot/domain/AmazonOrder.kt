package com.budgetsortbot.domain

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
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    val emailMessageId: String,

    @Column(nullable = false)
    val orderDate: Instant,

    @Column(nullable = false)
    val totalAmount: BigDecimal,

    @Column(nullable = false)
    val itemsJson: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: OrderStatus,

    val ynabTransactionId: String? = null,

    val ynabCategoryId: String? = null,

    @Column(nullable = false)
    val createdAt: Instant
)
