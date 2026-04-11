package com.budgetsortbot.infrastructure.persistence

import com.budgetsortbot.domain.AmazonOrder
import com.budgetsortbot.domain.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository

interface AmazonOrderRepository : JpaRepository<AmazonOrder, Long> {
    fun findByStatus(status: OrderStatus): List<AmazonOrder>
}
