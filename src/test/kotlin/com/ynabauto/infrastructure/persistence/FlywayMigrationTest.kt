package com.ynabauto.infrastructure.persistence

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.jdbc.core.JdbcTemplate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayMigrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `V1 migration creates app_config table with correct columns`() {
        val columns = getColumnNames("app_config")

        assertTrue(columns.contains("key"), "Expected column 'key' in app_config")
        assertTrue(columns.contains("value"), "Expected column 'value' in app_config")
        assertTrue(columns.contains("updated_at"), "Expected column 'updated_at' in app_config")
    }

    @Test
    fun `V1 migration creates category_rules table with correct columns`() {
        val columns = getColumnNames("category_rules")

        assertTrue(columns.contains("id"), "Expected column 'id' in category_rules")
        assertTrue(columns.contains("ynab_category_id"), "Expected column 'ynab_category_id' in category_rules")
        assertTrue(columns.contains("ynab_category_name"), "Expected column 'ynab_category_name' in category_rules")
        assertTrue(columns.contains("user_description"), "Expected column 'user_description' in category_rules")
        assertTrue(columns.contains("updated_at"), "Expected column 'updated_at' in category_rules")
    }

    @Test
    fun `V1 migration creates amazon_orders table with correct columns`() {
        val columns = getColumnNames("amazon_orders")

        assertTrue(columns.contains("id"), "Expected column 'id' in amazon_orders")
        assertTrue(columns.contains("email_message_id"), "Expected column 'email_message_id' in amazon_orders")
        assertTrue(columns.contains("order_date"), "Expected column 'order_date' in amazon_orders")
        assertTrue(columns.contains("total_amount"), "Expected column 'total_amount' in amazon_orders")
        assertTrue(columns.contains("items_json"), "Expected column 'items_json' in amazon_orders")
        assertTrue(columns.contains("status"), "Expected column 'status' in amazon_orders")
        assertTrue(columns.contains("ynab_transaction_id"), "Expected column 'ynab_transaction_id' in amazon_orders")
        assertTrue(columns.contains("ynab_category_id"), "Expected column 'ynab_category_id' in amazon_orders")
        assertTrue(columns.contains("created_at"), "Expected column 'created_at' in amazon_orders")
    }

    @Test
    fun `V1 migration creates sync_logs table with correct columns`() {
        val columns = getColumnNames("sync_logs")

        assertTrue(columns.contains("id"), "Expected column 'id' in sync_logs")
        assertTrue(columns.contains("source"), "Expected column 'source' in sync_logs")
        assertTrue(columns.contains("last_run"), "Expected column 'last_run' in sync_logs")
        assertTrue(columns.contains("status"), "Expected column 'status' in sync_logs")
        assertTrue(columns.contains("message"), "Expected column 'message' in sync_logs")
    }

    @Test
    fun `amazon_orders email_message_id has a unique constraint`() {
        jdbcTemplate.execute(
            "INSERT INTO amazon_orders (email_message_id, order_date, total_amount, items_json, status, created_at) " +
            "VALUES ('unique-msg-id', '2024-01-01T00:00:00Z', 9.99, '[\"Item\"]', 'PENDING', '2024-01-01T00:00:00Z')"
        )

        assertThrows(Exception::class.java) {
            jdbcTemplate.execute(
                "INSERT INTO amazon_orders (email_message_id, order_date, total_amount, items_json, status, created_at) " +
                "VALUES ('unique-msg-id', '2024-01-02T00:00:00Z', 19.99, '[\"Other\"]', 'PENDING', '2024-01-02T00:00:00Z')"
            )
        }
    }

    private fun getColumnNames(tableName: String): List<String> {
        return jdbcTemplate.queryForList("PRAGMA table_info($tableName)")
            .map { row -> (row["name"] as String).lowercase() }
    }
}
