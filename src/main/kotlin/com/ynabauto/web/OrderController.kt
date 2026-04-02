package com.ynabauto.web

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.ynabauto.domain.OrderStatus
import com.ynabauto.infrastructure.persistence.AmazonOrderRepository
import com.ynabauto.web.dto.PendingOrderResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val amazonOrderRepository: AmazonOrderRepository,
    private val objectMapper: ObjectMapper
) {

    @GetMapping("/pending")
    fun getPendingOrders(): List<PendingOrderResponse> {
        return amazonOrderRepository.findByStatus(OrderStatus.PENDING).map { order ->
            val items: List<String> = objectMapper.readValue(order.itemsJson, object : TypeReference<List<String>>() {})
            PendingOrderResponse(
                id = requireNotNull(order.id) { "Persisted AmazonOrder must have a non-null id" },
                orderDate = order.orderDate,
                totalAmount = order.totalAmount,
                items = items,
                status = order.status.name,
                createdAt = order.createdAt
            )
        }
    }
}
