package com.example.core.util

import com.example.data.database.BedEntity

/**
 * Utility for establishing consistent bed identity, normalization,
 * and robust mapping between physical beds, bed assignments, and tenants.
 *
 * Resolves discrepancies such as "Bed 1" vs "Bed A" vs "B1" vs "1"
 * referring to the same physical bed slot in a PG room.
 */
object BedIdentityUtil {

    enum class BedNamingConvention {
        NUMBER, // e.g. "Bed 1", "Bed 2"
        LETTER  // e.g. "Bed A", "Bed B"
    }

    private val NUMBER_REGEX = Regex("^(?:Bed\\s*|B)?(\\d+)$", RegexOption.IGNORE_CASE)
    private val LETTER_REGEX = Regex("^(?:Bed\\s*|B)?([A-Za-z])$", RegexOption.IGNORE_CASE)

    /**
     * Parses a bed identifier into its 1-based physical slot index.
     * Examples:
     * - "Bed 1", "Bed1", "B1", "1" -> 1
     * - "Bed 2", "Bed2", "B2", "2" -> 2
     * - "Bed A", "BedA", "BA", "A", "a" -> 1
     * - "Bed B", "BedB", "BB", "B", "b" -> 2
     * - "Bed C" -> 3
     * - "Bed 10", "B10", "10" -> 10
     */
    fun parseBedIndex(bedId: String?): Int? {
        if (bedId.isNullOrBlank()) return null
        val trimmed = bedId.trim()

        val numMatch = NUMBER_REGEX.matchEntire(trimmed)
        if (numMatch != null) {
            val num = numMatch.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        val letterMatch = LETTER_REGEX.matchEntire(trimmed)
        if (letterMatch != null) {
            val letterStr = letterMatch.groupValues[1]
            if (letterStr.isNotEmpty()) {
                val ch = letterStr[0].uppercaseChar()
                if (ch in 'A'..'Z') {
                    return (ch.code - 'A'.code) + 1
                }
            }
        }

        return null
    }

    /**
     * Determines whether two bed identifiers refer to the same physical bed.
     * True if they are identical (case-insensitive) OR if both map to the same bed slot index.
     */
    fun matchesSameBed(bedId1: String?, bedId2: String?): Boolean {
        if (bedId1.isNullOrBlank() || bedId2.isNullOrBlank()) return false
        val b1 = bedId1.trim()
        val b2 = bedId2.trim()
        if (b1.equals(b2, ignoreCase = true)) return true

        val idx1 = parseBedIndex(b1)
        val idx2 = parseBedIndex(b2)
        return idx1 != null && idx2 != null && idx1 == idx2
    }

    /**
     * Detects whether the beds in a room predominantly follow LETTER or NUMBER convention.
     */
    fun determineConvention(bedIds: Collection<String>): BedNamingConvention {
        val nonBlank = bedIds.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return BedNamingConvention.NUMBER

        val hasLetter = nonBlank.any { LETTER_REGEX.matches(it.trim()) }
        val hasNumber = nonBlank.any { NUMBER_REGEX.matches(it.trim()) }

        return if (hasLetter && !hasNumber) {
            BedNamingConvention.LETTER
        } else {
            BedNamingConvention.NUMBER
        }
    }

    /**
     * Formats a 1-based index into a canonical bed label.
     */
    fun formatBedId(index: Int, convention: BedNamingConvention = BedNamingConvention.NUMBER): String {
        return if (convention == BedNamingConvention.LETTER && index in 1..26) {
            "Bed ${('A' + (index - 1))}"
        } else {
            "Bed $index"
        }
    }

    /**
     * Finds the matching existing physical bed entity if one exists (by exact string or physical slot index).
     */
    fun findMatchingBed(bedId: String?, existingBeds: Collection<BedEntity>): BedEntity? {
        if (bedId.isNullOrBlank()) return null
        return existingBeds.firstOrNull { !it.deleted && matchesSameBed(it.bedId, bedId) }
    }

    /**
     * Resolves an incoming bed identifier to the authoritative bed identifier in the room.
     * If a physical bed entity exists in the room that matches this bed (e.g. Bed 1 exists for Bed A),
     * returns the existing bed's bedId ("Bed 1").
     * Otherwise, formats to the canonical convention of the room.
     */
    fun resolveAuthoritativeBedId(bedId: String, existingBeds: Collection<BedEntity>): String {
        val match = findMatchingBed(bedId, existingBeds)
        if (match != null) return match.bedId

        val idx = parseBedIndex(bedId)
        return if (idx != null) {
            val convention = determineConvention(existingBeds.filter { !it.deleted }.map { it.bedId })
            formatBedId(idx, convention)
        } else {
            bedId.trim()
        }
    }
}
