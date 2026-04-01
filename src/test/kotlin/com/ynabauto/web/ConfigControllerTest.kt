package com.ynabauto.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ynabauto.domain.CategoryRule
import com.ynabauto.service.ConfigService
import com.ynabauto.web.dto.ApiKeysRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(ConfigController::class)
class ConfigControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var configService: ConfigService

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `GET api config keys returns all key values`() {
        `when`(configService.getValue(ConfigService.YNAB_TOKEN)).thenReturn("my-ynab-token")
        `when`(configService.getValue(ConfigService.YNAB_BUDGET_ID)).thenReturn("budget-123")
        `when`(configService.getValue(ConfigService.FASTMAIL_USER)).thenReturn("user@fastmail.com")
        `when`(configService.getValue(ConfigService.FASTMAIL_TOKEN)).thenReturn(null)
        `when`(configService.getValue(ConfigService.GEMINI_KEY)).thenReturn(null)

        mockMvc.perform(get("/api/config/keys"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ynabToken").value("my-ynab-token"))
            .andExpect(jsonPath("$.ynabBudgetId").value("budget-123"))
            .andExpect(jsonPath("$.fastmailUser").value("user@fastmail.com"))
            .andExpect(jsonPath("$.fastmailToken").doesNotExist())
            .andExpect(jsonPath("$.geminiKey").doesNotExist())
    }

    @Test
    fun `PUT api config keys updates only provided keys`() {
        val request = ApiKeysRequest(ynabToken = "new-token", ynabBudgetId = "new-budget")

        mockMvc.perform(
            put("/api/config/keys")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNoContent)

        verify(configService).setValue(ConfigService.YNAB_TOKEN, "new-token")
        verify(configService).setValue(ConfigService.YNAB_BUDGET_ID, "new-budget")
    }

    @Test
    fun `GET api config categories returns all category rules`() {
        val rules = listOf(
            CategoryRule(id = 1L, ynabCategoryId = "cat-1", ynabCategoryName = "Food", userDescription = "Groceries", updatedAt = Instant.now()),
            CategoryRule(id = 2L, ynabCategoryId = "cat-2", ynabCategoryName = "Tech", userDescription = "Electronics", updatedAt = Instant.now())
        )
        `when`(configService.getAllCategoryRules()).thenReturn(rules)

        mockMvc.perform(get("/api/config/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].ynabCategoryId").value("cat-1"))
            .andExpect(jsonPath("$[0].ynabCategoryName").value("Food"))
            .andExpect(jsonPath("$[1].ynabCategoryId").value("cat-2"))
    }

    @Test
    fun `PUT api config categories saves category rules`() {
        val capturedRules = mutableListOf<CategoryRule>()
        Mockito.doAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            capturedRules.addAll(inv.getArgument<List<*>>(0) as List<CategoryRule>)
            null
        }.`when`(configService).saveCategoryRules(ArgumentMatchers.anyList())

        val body = """
            [
              {"ynabCategoryId":"cat-1","ynabCategoryName":"Food","userDescription":"Groceries"},
              {"ynabCategoryId":"cat-2","ynabCategoryName":"Tech","userDescription":"Electronics"}
            ]
        """.trimIndent()

        mockMvc.perform(
            put("/api/config/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isNoContent)

        assertEquals(2, capturedRules.size)
        assertEquals("cat-1", capturedRules[0].ynabCategoryId)
        assertEquals("cat-2", capturedRules[1].ynabCategoryId)
    }
}
