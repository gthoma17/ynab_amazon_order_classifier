package com.budgetsortbot.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "category_rules")
data class CategoryRule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val ynabCategoryId: String,

    @Column(nullable = false)
    val ynabCategoryName: String,

    @Column(nullable = false)
    val userDescription: String,

    @Column(nullable = false)
    val updatedAt: Instant
)
