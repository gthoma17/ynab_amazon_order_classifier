package com.budgetsortbot.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import com.budgetsortbot.infrastructure.persistence.CategoryRuleRepository
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRuleEntityTest {

    @Autowired
    private lateinit var categoryRuleRepository: CategoryRuleRepository

    @Test
    fun `can save and retrieve a CategoryRule`() {
        val rule = CategoryRule(
            ynabCategoryId = "cat-uuid-001",
            ynabCategoryName = "Groceries",
            userDescription = "Food and household supplies from stores",
            updatedAt = Instant.now()
        )

        val saved = categoryRuleRepository.save(rule)

        assertNotNull(saved.id)
        assertEquals("cat-uuid-001", saved.ynabCategoryId)
        assertEquals("Groceries", saved.ynabCategoryName)
        assertEquals("Food and household supplies from stores", saved.userDescription)
    }

    @Test
    fun `can find CategoryRule by ynabCategoryId`() {
        val rule = CategoryRule(
            ynabCategoryId = "cat-uuid-002",
            ynabCategoryName = "Electronics",
            userDescription = "Gadgets, cables, and tech accessories",
            updatedAt = Instant.now()
        )
        categoryRuleRepository.save(rule)

        val found = categoryRuleRepository.findByYnabCategoryId("cat-uuid-002")

        assertNotNull(found)
        assertEquals("Electronics", found!!.ynabCategoryName)
    }

    @Test
    fun `can update userDescription on a CategoryRule`() {
        val rule = CategoryRule(
            ynabCategoryId = "cat-uuid-003",
            ynabCategoryName = "Books",
            userDescription = "Books and reading material",
            updatedAt = Instant.now()
        )
        val saved = categoryRuleRepository.save(rule)

        val updated = saved.copy(userDescription = "Books, audiobooks, and magazines")
        categoryRuleRepository.save(updated)

        val found = categoryRuleRepository.findById(saved.id!!)
        assertTrue(found.isPresent)
        assertEquals("Books, audiobooks, and magazines", found.get().userDescription)
    }

    @Test
    fun `can list all CategoryRules`() {
        categoryRuleRepository.save(CategoryRule(ynabCategoryId = "c1", ynabCategoryName = "Cat1", userDescription = "desc1", updatedAt = Instant.now()))
        categoryRuleRepository.save(CategoryRule(ynabCategoryId = "c2", ynabCategoryName = "Cat2", userDescription = "desc2", updatedAt = Instant.now()))

        val all = categoryRuleRepository.findAll()

        assertEquals(2, all.size)
    }
}
