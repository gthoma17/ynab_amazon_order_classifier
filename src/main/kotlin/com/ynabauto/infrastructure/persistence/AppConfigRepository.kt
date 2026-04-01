package com.ynabauto.infrastructure.persistence

import com.ynabauto.domain.AppConfig
import org.springframework.data.jpa.repository.JpaRepository

interface AppConfigRepository : JpaRepository<AppConfig, String>
