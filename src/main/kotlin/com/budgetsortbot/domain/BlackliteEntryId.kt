package com.budgetsortbot.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class BlackliteEntryId(
    @Column(name = "epoch_secs")
    val epochSecs: Long = 0,

    @Column(name = "nanos")
    val nanos: Int = 0
) : Serializable
