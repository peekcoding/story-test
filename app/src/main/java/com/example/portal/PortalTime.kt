package com.example.portal

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Times from the portal are ISO 8601 with an offset, e.g. "2026-11-10T14:00:00+05:00".
 */
object PortalTime {

    /** Milliseconds since 1970 for a portal time, or null when empty or unreadable. */
    fun parseMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                Instant.parse(value).toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }

    /** "14:00" today, or "11 Nov 14:00" on another day, in the phone's time zone. */
    fun formatLocal(millis: Long): String {
        val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val today = java.time.LocalDate.now(ZoneId.systemDefault())
        val pattern = if (time.toLocalDate() == today) "HH:mm" else "d MMM HH:mm"
        return time.format(DateTimeFormatter.ofPattern(pattern))
    }
}
