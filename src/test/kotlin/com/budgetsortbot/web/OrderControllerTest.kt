package com.budgetsortbot.web

import com.ninjasquad.springmockk.MockkBean
import com.budgetsortbot.domain.AmazonOrder
import com.budgetsortbot.domain.OrderStatus
import com.budgetsortbot.infrastructure.persistence.AmazonOrderRepository
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.Instant

@WebMvcTest(OrderController::class)
class OrderControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var amazonOrderRepository: AmazonOrderRepository

    @Test
    fun `GET api orders pending returns list of pending orders`() {
        val orders = listOf(
            AmazonOrder(
                id = 1L,
                emailMessageId = "msg-1@amazon.com",
                orderDate = Instant.parse("2024-01-15T10:00:00Z"),
                totalAmount = BigDecimal("49.99"),
                itemsJson = """["USB Cable","Phone Case"]""",
                status = OrderStatus.PENDING,
                createdAt = Instant.parse("2024-01-15T10:05:00Z")
            )
        )
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns orders

        mockMvc.perform(get("/api/orders/pending"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].totalAmount").value(49.99))
            .andExpect(jsonPath("$[0].items.length()").value(2))
            .andExpect(jsonPath("$[0].items[0]").value("USB Cable"))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
    }

    @Test
    fun `GET api orders pending returns empty list when no pending orders`() {
        every { amazonOrderRepository.findByStatus(OrderStatus.PENDING) } returns emptyList()

        mockMvc.perform(get("/api/orders/pending"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
