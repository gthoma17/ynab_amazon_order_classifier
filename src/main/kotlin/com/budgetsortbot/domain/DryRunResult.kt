package com.budgetsortbot.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "dry_run_results")
data class DryRunResult(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
    val orderId: Long? = null,
    @Column(nullable = false) val orderDate: Instant,
    @Column(nullable = false) val totalAmount: BigDecimal,
    @Column(nullable = false) val itemsJson: String,
    val ynabTransactionId: String? = null,
    val proposedCategoryId: String? = null,
    val proposedCategoryName: String? = null,
    val errorMessage: String? = null,
    @Column(nullable = false) val runAt: Instant
)
