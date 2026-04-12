package com.budgetsortbot.web

import com.budgetsortbot.domain.SyncLog
import com.budgetsortbot.domain.SyncSource
import com.budgetsortbot.domain.SyncStatus
import com.budgetsortbot.infrastructure.persistence.SyncLogRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.Sort
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(LogController::class)
class LogControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var syncLogRepository: SyncLogRepository

    @Test
    fun `GET api logs returns all sync logs`() {
        val logs =
            listOf(
                SyncLog(
                    id = 2L,
                    source = SyncSource.YNAB,
                    lastRun = Instant.parse("2024-01-15T10:05:00Z"),
                    status = SyncStatus.FAIL,
                    message = "Connection refused",
                ),
                SyncLog(
                    id = 1L,
                    source = SyncSource.EMAIL,
                    lastRun = Instant.parse("2024-01-15T10:00:00Z"),
                    status = SyncStatus.SUCCESS,
                    message = null,
                ),
            )
        every { syncLogRepository.findAll(any<Sort>()) } returns logs

        mockMvc
            .perform(get("/api/logs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].source").value("YNAB"))
            .andExpect(jsonPath("$[0].status").value("FAIL"))
            .andExpect(jsonPath("$[0].message").value("Connection refused"))
            .andExpect(jsonPath("$[1].source").value("EMAIL"))
            .andExpect(jsonPath("$[1].status").value("SUCCESS"))
    }

    @Test
    fun `GET api logs passes DESC lastRun sort to repository`() {
        every { syncLogRepository.findAll(any<Sort>()) } answers {
            val sort = firstArg<Sort>()
            val order = sort.getOrderFor("lastRun")
            require(order != null && order.isDescending) { "Expected DESC sort by lastRun" }
            emptyList()
        }

        mockMvc
            .perform(get("/api/logs"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET api logs returns empty list when no logs exist`() {
        every { syncLogRepository.findAll(any<Sort>()) } returns emptyList()

        mockMvc
            .perform(get("/api/logs"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
