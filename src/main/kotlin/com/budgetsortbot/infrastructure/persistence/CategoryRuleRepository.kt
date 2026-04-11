package com.budgetsortbot.infrastructure.persistence

import com.budgetsortbot.domain.CategoryRule
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRuleRepository : JpaRepository<CategoryRule, Long> {
    fun findByYnabCategoryId(ynabCategoryId: String): CategoryRule?
}
