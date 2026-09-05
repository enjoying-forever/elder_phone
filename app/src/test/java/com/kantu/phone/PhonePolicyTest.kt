package com.kantu.phone

import org.junit.Assert.*
import org.junit.Test

class PhonePolicyTest {
    @Test fun acceptsChineseMobile() {
        assertEquals("13800138000", PhonePolicy.dialableNumber("13800138000"))
    }
    @Test fun acceptsFormattedInternationalNumber() {
        assertEquals("+12025550123", PhonePolicy.dialableNumber("+1 (202) 555-0123"))
    }
    @Test fun acceptsShortServiceNumber() {
        assertEquals("10086", PhonePolicy.dialableNumber("10086"))
    }
    @Test fun rejectsBlankAndTooShort() {
        listOf("", " ", "12", "+").forEach { assertNull(PhonePolicy.dialableNumber(it)) }
    }
    @Test fun rejectsServiceCodes() {
        listOf("*#06#", "**21*123456#", "*123", "123#").forEach { assertNull(PhonePolicy.dialableNumber(it)) }
    }
    @Test fun rejectsUriInjection() {
        listOf("tel:123456", "123?x=1", "123;456", "123,456", "123%23456").forEach {
            assertNull(PhonePolicy.dialableNumber(it))
        }
    }
    @Test fun rejectsMisplacedPlusAndOversize() {
        listOf("12+3456", "++123456", "1".repeat(21)).forEach { assertNull(PhonePolicy.dialableNumber(it)) }
    }
    @Test fun ignoresVisualFormattingForContactKeys() {
        assertEquals("+12025550123", PhonePolicy.contactKey("+1 (202) 555-0123"))
    }
    @Test fun doesNotMergeDifferentCountriesBySuffix() {
        assertNotEquals(PhonePolicy.contactKey("+8613800138000"), PhonePolicy.contactKey("+113800138000"))
    }
}
