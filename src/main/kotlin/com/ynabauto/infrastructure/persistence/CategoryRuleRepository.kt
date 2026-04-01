package com.ynabauto.infrastructure.persistence

import com.ynabauto.domain.CategoryRule
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRuleRepository : JpaRepository<CategoryRule, Long> {
    fun findByYnabCategoryId(ynabCategoryId: String): CategoryRule?
}
