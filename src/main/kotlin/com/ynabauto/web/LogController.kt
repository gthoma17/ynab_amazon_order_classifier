package com.ynabauto.web

import com.ynabauto.infrastructure.persistence.SyncLogRepository
import com.ynabauto.web.dto.SyncLogResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/logs")
class LogController(
    private val syncLogRepository: SyncLogRepository
) {

    @GetMapping
    fun getLogs(): List<SyncLogResponse> {
        return syncLogRepository.findAll().map { log ->
            SyncLogResponse(
                id = requireNotNull(log.id) { "Persisted SyncLog must have a non-null id" },
                source = log.source.name,
                lastRun = log.lastRun,
                status = log.status.name,
                message = log.message
            )
        }
    }
}
