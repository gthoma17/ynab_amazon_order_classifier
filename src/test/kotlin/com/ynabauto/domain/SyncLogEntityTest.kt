package com.ynabauto.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import com.ynabauto.infrastructure.persistence.SyncLogRepository
import java.time.Instant

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SyncLogEntityTest {

    @Autowired
    private lateinit var syncLogRepository: SyncLogRepository

    @Test
    fun `can save and retrieve a SyncLog`() {
        val log = SyncLog(
            source = SyncSource.EMAIL,
            lastRun = Instant.now(),
            status = SyncStatus.SUCCESS,
            message = null
        )

        val saved = syncLogRepository.save(log)

        assertNotNull(saved.id)
        assertEquals(SyncSource.EMAIL, saved.source)
        assertEquals(SyncStatus.SUCCESS, saved.status)
        assertNull(saved.message)
    }

    @Test
    fun `can save a failed SyncLog with an error message`() {
        val log = SyncLog(
            source = SyncSource.YNAB,
            lastRun = Instant.now(),
            status = SyncStatus.FAIL,
            message = "Connection timeout while fetching YNAB transactions"
        )

        val saved = syncLogRepository.save(log)

        assertNotNull(saved.id)
        assertEquals(SyncSource.YNAB, saved.source)
        assertEquals(SyncStatus.FAIL, saved.status)
        assertEquals("Connection timeout while fetching YNAB transactions", saved.message)
    }

    @Test
    fun `can find SyncLogs by source`() {
        syncLogRepository.save(SyncLog(source = SyncSource.EMAIL, lastRun = Instant.now(), status = SyncStatus.SUCCESS, message = null))
        syncLogRepository.save(SyncLog(source = SyncSource.YNAB, lastRun = Instant.now(), status = SyncStatus.SUCCESS, message = null))
        syncLogRepository.save(SyncLog(source = SyncSource.EMAIL, lastRun = Instant.now(), status = SyncStatus.FAIL, message = "error"))

        val emailLogs = syncLogRepository.findBySource(SyncSource.EMAIL)

        assertEquals(2, emailLogs.size)
    }

    @Test
    fun `can retrieve most recent SyncLog for a given source`() {
        val earlier = Instant.parse("2024-01-01T12:00:00Z")
        val later = Instant.parse("2024-01-02T12:00:00Z")

        syncLogRepository.save(SyncLog(source = SyncSource.EMAIL, lastRun = earlier, status = SyncStatus.SUCCESS, message = null))
        syncLogRepository.save(SyncLog(source = SyncSource.EMAIL, lastRun = later, status = SyncStatus.FAIL, message = "later failure"))

        val latest = syncLogRepository.findTopBySourceOrderByLastRunDesc(SyncSource.EMAIL)

        assertNotNull(latest)
        assertEquals(SyncStatus.FAIL, latest!!.status)
        assertEquals(later, latest.lastRun)
    }
}
