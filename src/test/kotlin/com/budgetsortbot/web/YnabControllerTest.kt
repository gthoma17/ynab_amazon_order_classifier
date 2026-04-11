package com.budgetsortbot.web

import com.budgetsortbot.infrastructure.ynab.YnabBudget
import com.budgetsortbot.infrastructure.ynab.YnabCategory
import com.budgetsortbot.infrastructure.ynab.YnabClient
import com.budgetsortbot.service.ConfigService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(YnabController::class)
class YnabControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var ynabClient: YnabClient

    @MockkBean
    private lateinit var configService: ConfigService

    @Test
    fun `GET api ynab budgets returns budgets using provided token`() {
        every { ynabClient.getBudgets("my-token") } returns
            listOf(
                YnabBudget(id = "budget-1", name = "My Budget"),
                YnabBudget(id = "budget-2", name = "Savings"),
            )

        mockMvc
            .perform(get("/api/ynab/budgets").param("token", "my-token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("budget-1"))
            .andExpect(jsonPath("$[0].name").value("My Budget"))
            .andExpect(jsonPath("$[1].id").value("budget-2"))
            .andExpect(jsonPath("$[1].name").value("Savings"))
    }

    @Test
    fun `GET api ynab budgets falls back to saved token when no token param`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "saved-token"
        every { ynabClient.getBudgets("saved-token") } returns
            listOf(
                YnabBudget(id = "budget-1", name = "My Budget"),
            )

        mockMvc
            .perform(get("/api/ynab/budgets"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("budget-1"))
    }

    @Test
    fun `GET api ynab budgets returns 400 when no token available`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns null

        mockMvc
            .perform(get("/api/ynab/budgets"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET api ynab categories returns categories from YNAB client`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "token-123"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns "budget-abc"
        every { ynabClient.getCategories("budget-abc", "token-123") } returns
            listOf(
                YnabCategory(id = "cat-1", name = "Groceries", categoryGroupName = "Monthly Bills"),
                YnabCategory(id = "cat-2", name = "Electronics", categoryGroupName = "Personal"),
            )

        mockMvc
            .perform(get("/api/ynab/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("cat-1"))
            .andExpect(jsonPath("$[0].name").value("Groceries"))
            .andExpect(jsonPath("$[0].categoryGroupName").value("Monthly Bills"))
            .andExpect(jsonPath("$[1].id").value("cat-2"))
    }

    @Test
    fun `GET api ynab categories returns 500 when YNAB token not configured`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns null

        mockMvc
            .perform(get("/api/ynab/categories"))
            .andExpect(status().isInternalServerError)
    }

    @Test
    fun `GET api ynab categories returns 500 when YNAB budget ID not configured`() {
        every { configService.getValue(ConfigService.YNAB_TOKEN) } returns "token-123"
        every { configService.getValue(ConfigService.YNAB_BUDGET_ID) } returns null

        mockMvc
            .perform(get("/api/ynab/categories"))
            .andExpect(status().isInternalServerError)
    }
}
