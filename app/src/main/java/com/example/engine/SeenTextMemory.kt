package com.example.engine

import com.example.data.model.SeenTextEntry

/**
 * Thread-safe ring buffer storing the last [capacity] seen text entries with timestamps.
 */
class SeenTextMemory(private val capacity: Int = 500) {

    private val buffer = ArrayDeque<SeenTextEntry>(capacity)
    private val lock = Any()

    fun record(text: String, timestamp: Long = System.currentTimeMillis()) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            if (buffer.size >= capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(SeenTextEntry(trimmed, timestamp))
        }
    }

    fun getAllEntries(): List<SeenTextEntry> {
        synchronized(lock) {
            return buffer.toList()
        }
    }

    fun hasSeenAfter(query: String, afterTimestamp: Long): Boolean {
        synchronized(lock) {
            val lowerQuery = query.trim().lowercase()
            if (lowerQuery.isEmpty()) return false
            for (entry in buffer) {
                if (entry.timestamp >= afterTimestamp && entry.text.lowercase().contains(lowerQuery)) {
                    return true
                }
            }
        }
        return false
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
    }
}
