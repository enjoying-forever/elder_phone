package com.kantu.phone

/** Pure policy shared by all future call providers; never interpret contact data as a URI. */
internal object PhonePolicy {
    fun dialableNumber(raw: String): String? {
        val normalized = raw.filterNot { it.isWhitespace() || it in "-()" }
        return normalized.takeIf { it.matches(Regex("\\+?[0-9]{3,20}")) }
    }

    // Do not truncate suffixes: unrelated international numbers can have identical last 11 digits.
    fun contactKey(raw: String): String = raw.filter { it.isDigit() || it == '+' }
}
