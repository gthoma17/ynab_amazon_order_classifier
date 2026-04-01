package com.ynabauto.domain

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
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "ynab_category_id", nullable = false)
    val ynabCategoryId: String,

    @Column(name = "ynab_category_name", nullable = false)
    val ynabCategoryName: String,

    @Column(name = "user_description", nullable = false)
    val userDescription: String,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant
)
