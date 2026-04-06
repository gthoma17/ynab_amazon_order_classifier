package com.budgetsortbot.config

import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RestClientConfig {
    @Bean
    fun restClientCustomizer(): RestClientCustomizer =
        RestClientCustomizer { builder ->
            builder.defaultHeader("User-Agent", "YNAB-Amazon-Automator/1.0")
        }
}
