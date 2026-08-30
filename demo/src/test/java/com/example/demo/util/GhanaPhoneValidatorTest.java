package com.example.demo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GhanaPhoneValidatorTest {

    @Test
    void acceptsLocalFormat() {
        assertTrue(GhanaPhoneValidator.isValid("0244567890"));
        assertTrue(GhanaPhoneValidator.isValid("0551234567"));
    }

    @Test
    void acceptsInternationalFormat() {
        assertTrue(GhanaPhoneValidator.isValid("+233244567890"));
        assertTrue(GhanaPhoneValidator.isValid("+233551234567"));
    }

    @Test
    void acceptsWhitespaceAndDashes() {
        assertTrue(GhanaPhoneValidator.isValid("+233 24 456 7890"));
        assertTrue(GhanaPhoneValidator.isValid("024-456-7890"));
    }

    @Test
    void rejectsWrongDigitCounts() {
        assertFalse(GhanaPhoneValidator.isValid("024456789"));   // 9 digits after 0
        assertFalse(GhanaPhoneValidator.isValid("02445678901"));  // 11 digits after 0
        assertFalse(GhanaPhoneValidator.isValid("+23324456789"));  // 8 digits after +233
        assertFalse(GhanaPhoneValidator.isValid("+2332445678901")); // 10 digits after +233
    }

    @Test
    void rejectsOtherFormats() {
        assertFalse(GhanaPhoneValidator.isValid("244567890"));    // no leading 0 or +233
        assertFalse(GhanaPhoneValidator.isValid("+234244567890")); // Nigeria code
        assertFalse(GhanaPhoneValidator.isValid("+123244567890")); // unknown country code
        assertFalse(GhanaPhoneValidator.isValid("abc123"));
        assertFalse(GhanaPhoneValidator.isValid(null));
        assertFalse(GhanaPhoneValidator.isValid(""));
    }

    @Test
    void requireValidReturnsTrimmedAndThrows() {
        assertEquals("0244567890", GhanaPhoneValidator.requireValid(" 0244567890 ", "Phone number"));
        assertThrows(IllegalArgumentException.class,
                () -> GhanaPhoneValidator.requireValid("024456789", "Phone number"));
        assertThrows(IllegalArgumentException.class,
                () -> GhanaPhoneValidator.requireValid("  ", "Phone number"));
    }
}
