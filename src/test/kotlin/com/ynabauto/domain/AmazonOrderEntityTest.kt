package com.ynabauto.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import com.ynabauto.infrastructure.persistence.AmazonOrderRepository
import java.math.BigDecimal
import java.time.Instant

@DataJpaTest
class AmazonOrderEntityTest {

    @Autowired
    private lateinit var amazonOrderRepository: AmazonOrderRepository

    @Test
    fun `can save and retrieve an AmazonOrder`() {
        val order = AmazonOrder(
            emailMessageId = "msg-001@amazon.com",
            orderDate = Instant.parse("2024-01-15T10:00:00Z"),
            totalAmount = BigDecimal("49.99"),
            itemsJson = """["USB Cable","Phone Case"]""",
            status = OrderStatus.PENDING,
            createdAt = Instant.now()
        )

        val saved = amazonOrderRepository.save(order)

        assertNotNull(saved.id)
        assertEquals("msg-001@amazon.com", saved.emailMessageId)
        assertEquals(BigDecimal("49.99"), saved.totalAmount)
        assertEquals(OrderStatus.PENDING, saved.status)
        assertNull(saved.ynabTransactionId)
        assertNull(saved.ynabCategoryId)
    }

    @Test
    fun `can find AmazonOrders by status`() {
        amazonOrderRepository.save(AmazonOrder(
            emailMessageId = "msg-002@amazon.com",
            orderDate = Instant.now(),
            totalAmount = BigDecimal("25.00"),
            itemsJson = """["Notebook"]""",
            status = OrderStatus.PENDING,
            createdAt = Instant.now()
        ))
        amazonOrderRepository.save(AmazonOrder(
            emailMessageId = "msg-003@amazon.com",
            orderDate = Instant.now(),
            totalAmount = BigDecimal("100.00"),
            itemsJson = """["Headphones"]""",
            status = OrderStatus.MATCHED,
            createdAt = Instant.now()
        ))

        val pendingOrders = amazonOrderRepository.findByStatus(OrderStatus.PENDING)

        assertEquals(1, pendingOrders.size)
        assertEquals("msg-002@amazon.com", pendingOrders[0].emailMessageId)
    }

    @Test
    fun `emailMessageId must be unique`() {
        amazonOrderRepository.save(AmazonOrder(
            emailMessageId = "msg-duplicate@amazon.com",
            orderDate = Instant.now(),
            totalAmount = BigDecimal("10.00"),
            itemsJson = """["Item"]""",
            status = OrderStatus.PENDING,
            createdAt = Instant.now()
        ))

        assertThrows(Exception::class.java) {
            amazonOrderRepository.saveAndFlush(AmazonOrder(
                emailMessageId = "msg-duplicate@amazon.com",
                orderDate = Instant.now(),
                totalAmount = BigDecimal("20.00"),
                itemsJson = """["Other Item"]""",
                status = OrderStatus.PENDING,
                createdAt = Instant.now()
            ))
        }
    }

    @Test
    fun `can update status from PENDING to MATCHED and set ynabTransactionId`() {
        val order = amazonOrderRepository.save(AmazonOrder(
            emailMessageId = "msg-004@amazon.com",
            orderDate = Instant.now(),
            totalAmount = BigDecimal("75.50"),
            itemsJson = """["Keyboard"]""",
            status = OrderStatus.PENDING,
            createdAt = Instant.now()
        ))

        val matched = order.copy(
            status = OrderStatus.MATCHED,
            ynabTransactionId = "ynab-txn-xyz"
        )
        amazonOrderRepository.save(matched)

        val found = amazonOrderRepository.findById(order.id!!)
        assertTrue(found.isPresent)
        assertEquals(OrderStatus.MATCHED, found.get().status)
        assertEquals("ynab-txn-xyz", found.get().ynabTransactionId)
    }

    @Test
    fun `can transition order through full lifecycle to COMPLETED`() {
        val order = amazonOrderRepository.save(AmazonOrder(
            emailMessageId = "msg-005@amazon.com",
            orderDate = Instant.now(),
            totalAmount = BigDecimal("199.99"),
            itemsJson = """["Monitor"]""",
            status = OrderStatus.PENDING,
            createdAt = Instant.now()
        ))

        val completed = order.copy(
            status = OrderStatus.COMPLETED,
            ynabTransactionId = "ynab-txn-abc",
            ynabCategoryId = "cat-electronics"
        )
        amazonOrderRepository.save(completed)

        val found = amazonOrderRepository.findById(order.id!!)
        assertTrue(found.isPresent)
        assertEquals(OrderStatus.COMPLETED, found.get().status)
        assertEquals("cat-electronics", found.get().ynabCategoryId)
    }
}
