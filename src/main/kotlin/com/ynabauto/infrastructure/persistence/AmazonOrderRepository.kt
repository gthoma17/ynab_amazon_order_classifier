package com.ynabauto.infrastructure.persistence

import com.ynabauto.domain.AmazonOrder
import com.ynabauto.domain.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository

interface AmazonOrderRepository : JpaRepository<AmazonOrder, Long> {
    fun findByStatus(status: OrderStatus): List<AmazonOrder>
}
