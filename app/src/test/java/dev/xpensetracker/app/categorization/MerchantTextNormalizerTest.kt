package dev.xpensetracker.app.categorization

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantTextNormalizerTest {

    @Test
    fun `lowercases and collapses whitespace`() {
        assertEquals("amazon", MerchantTextNormalizer.normalize("  AMAZON  "))
    }

    @Test
    fun `strips non-alphanumeric characters`() {
        assertEquals("amazon icici", MerchantTextNormalizer.normalize("amazon@icici"))
    }

    @Test
    fun `same merchant text normalizes identically regardless of case`() {
        assertEquals(
            MerchantTextNormalizer.normalize("SWIGGY"),
            MerchantTextNormalizer.normalize("swiggy"),
        )
    }
}
