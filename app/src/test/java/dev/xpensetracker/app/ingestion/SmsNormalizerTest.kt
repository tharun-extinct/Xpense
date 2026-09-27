package dev.xpensetracker.app.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNormalizerTest {

    @Test
    fun `known bank sender is allowlisted`() {
        assertTrue(SmsNormalizer.isAllowlistedSender("VM-HDFCBK"))
        assertTrue(SmsNormalizer.isAllowlistedSender("AX-SBIINB"))
        assertTrue(SmsNormalizer.isAllowlistedSender("ICICIB"))
    }

    @Test
    fun `sender carrying a DLT category suffix is allowlisted`() {
        assertTrue(SmsNormalizer.isAllowlistedSender("AD-HDFCBK-S"))
        assertTrue(SmsNormalizer.isAllowlistedSender("JD-SBIINB-T"))
    }

    @Test
    fun `merchant sender is not allowlisted so a spend is not counted twice`() {
        assertFalse(SmsNormalizer.isAllowlistedSender("SWIGGY"))
        assertFalse(SmsNormalizer.isAllowlistedSender("ZOMATO"))
    }

    @Test
    fun `issuer is taken from the middle segment of a DLT header`() {
        assertEquals("HDFCBK", SmsNormalizer.issuerFromSender("AD-HDFCBK-S"))
        assertEquals("HDFCBK", SmsNormalizer.issuerFromSender("VM-HDFCBK"))
        assertEquals("HDFCBK", SmsNormalizer.issuerFromSender("HDFCBK"))
    }

    @Test
    fun `unrelated sender is not allowlisted`() {
        assertFalse(SmsNormalizer.isAllowlistedSender("+919876543210"))
        assertFalse(SmsNormalizer.isAllowlistedSender("MOM"))
        assertFalse(SmsNormalizer.isAllowlistedSender("a very long sender that is not an id"))
    }

    @Test
    fun `same sms produces the same hash whether seen live or during backfill`() {
        val sender = "VM-HDFCBK"
        val body = "Rs.500 debited from A/c XX1234"
        val timestamp = 1_726_000_030_000L // arbitrary, within the same minute as below

        val normalized = SmsNormalizer.normalizeBody(body)
        val liveHash = SmsNormalizer.contentHash(sender, normalized, timestamp)
        val backfillHash = SmsNormalizer.contentHash(sender, normalized, timestamp + 5_000L) // few seconds later, same minute

        assertEquals(liveHash, backfillHash)
    }

    @Test
    fun `different messages produce different hashes`() {
        val hash1 = SmsNormalizer.contentHash("VM-HDFCBK", "Rs.500 debited", 1_726_000_000_000L)
        val hash2 = SmsNormalizer.contentHash("VM-HDFCBK", "Rs.600 debited", 1_726_000_000_000L)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `body normalization collapses whitespace`() {
        assertEquals("Rs.500 debited from A/c", SmsNormalizer.normalizeBody("Rs.500   debited\nfrom A/c "))
    }
}
