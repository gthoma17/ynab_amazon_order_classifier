package com.ynabauto.web

import com.ynabauto.infrastructure.ynab.YnabCategory
import com.ynabauto.infrastructure.ynab.YnabClient
import com.ynabauto.service.ConfigService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(YnabController::class)
class YnabControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var ynabClient: YnabClient

    @MockBean
    private lateinit var configService: ConfigService

    @Test
    fun `GET api ynab categories returns categories from YNAB client`() {
        `when`(configService.getValue(ConfigService.YNAB_TOKEN)).thenReturn("token-123")
        `when`(configService.getValue(ConfigService.YNAB_BUDGET_ID)).thenReturn("budget-abc")
        `when`(ynabClient.getCategories("budget-abc", "token-123")).thenReturn(
            listOf(
                YnabCategory(id = "cat-1", name = "Groceries", categoryGroupName = "Monthly Bills"),
                YnabCategory(id = "cat-2", name = "Electronics", categoryGroupName = "Personal")
            )
        )

        mockMvc.perform(get("/api/ynab/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("cat-1"))
            .andExpect(jsonPath("$[0].name").value("Groceries"))
            .andExpect(jsonPath("$[0].categoryGroupName").value("Monthly Bills"))
            .andExpect(jsonPath("$[1].id").value("cat-2"))
    }

    @Test
    fun `GET api ynab categories returns 500 when YNAB token not configured`() {
        `when`(configService.getValue(ConfigService.YNAB_TOKEN)).thenReturn(null)

        mockMvc.perform(get("/api/ynab/categories"))
            .andExpect(status().isInternalServerError)
    }

    @Test
    fun `GET api ynab categories returns 500 when YNAB budget ID not configured`() {
        `when`(configService.getValue(ConfigService.YNAB_TOKEN)).thenReturn("token-123")
        `when`(configService.getValue(ConfigService.YNAB_BUDGET_ID)).thenReturn(null)

        mockMvc.perform(get("/api/ynab/categories"))
            .andExpect(status().isInternalServerError)
    }
}
