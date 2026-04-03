package com.ynabauto.config

import com.ninjasquad.springmockk.MockkBean
import com.ynabauto.infrastructure.persistence.AmazonOrderRepository
import com.ynabauto.infrastructure.persistence.SyncLogRepository
import com.ynabauto.infrastructure.ynab.YnabClient
import com.ynabauto.service.ConfigService
import com.ynabauto.service.ConnectionProbeService
import com.ynabauto.service.DryRunService
import com.ynabauto.service.ReportSanitizationService
import com.ynabauto.service.SyncScheduler
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl

@WebMvcTest
@Import(WebConfig::class)
class WebConfigTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var configService: ConfigService

    @MockkBean
    private lateinit var connectionProbeService: ConnectionProbeService

    @MockkBean
    private lateinit var amazonOrderRepository: AmazonOrderRepository

    @MockkBean
    private lateinit var syncLogRepository: SyncLogRepository

    @MockkBean
    private lateinit var ynabClient: YnabClient

    @MockkBean
    private lateinit var syncScheduler: SyncScheduler

    @MockkBean
    private lateinit var dryRunService: DryRunService

    @MockkBean
    private lateinit var reportSanitizationService: ReportSanitizationService

    @Test
    fun `non-API path forwards to index html`() {
        mockMvc.perform(get("/some-client-side-route"))
            .andExpect(forwardedUrl("/index.html"))
    }

    @Test
    fun `nested non-API path forwards to index html`() {
        mockMvc.perform(get("/categories"))
            .andExpect(forwardedUrl("/index.html"))
    }
}
