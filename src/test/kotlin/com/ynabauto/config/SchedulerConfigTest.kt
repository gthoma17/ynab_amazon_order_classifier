package com.ynabauto.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor

@SpringBootTest
class SchedulerConfigTest {

    @Autowired
    private lateinit var scheduledAnnotationBeanPostProcessor: ScheduledAnnotationBeanPostProcessor

    @Test
    fun `scheduling is enabled and ScheduledAnnotationBeanPostProcessor is present`() {
        // If @EnableScheduling is active, Spring registers this bean
        assert(scheduledAnnotationBeanPostProcessor != null)
    }
}
