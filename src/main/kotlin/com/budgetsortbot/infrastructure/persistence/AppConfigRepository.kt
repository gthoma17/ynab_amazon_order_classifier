package com.budgetsortbot.infrastructure.persistence

import com.budgetsortbot.domain.AppConfig
import org.springframework.data.jpa.repository.JpaRepository

interface AppConfigRepository : JpaRepository<AppConfig, String>
