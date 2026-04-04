package com.budgetsortbot.domain

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

/**
 * Read-only JPA entity mapping to the [entries] table created by the
 * Blacklite SQLite appender.  The table has no explicit primary key, so
 * [BlackliteEntryId] (epoch_secs + nanos) serves as a composite key.
 * Mutation is prevented by Hibernate's [@Immutable] annotation.
 *
 * Schema (Blacklite-managed, not Flyway):
 *   epoch_secs LONG
 *   nanos      INTEGER
 *   level      INTEGER
 *   content    BLOB
 */
@Entity
@Immutable
@Table(name = "entries")
class BlackliteEntry(

    @EmbeddedId
    val id: BlackliteEntryId,

    @Column(name = "level")
    val level: Int?,

    @Lob
    @Column(name = "content")
    val content: ByteArray?
)
