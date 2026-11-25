package app.tether.client.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodeTest {
    @Test fun acceptsEnglishAndNormalizesPersianAndArabicDigits() {
        assertEquals("084392", VerificationCode.extract("Your one-time code is 084392."))
        assertEquals("084392", VerificationCode.extract("کد تأیید: ۰۸۴۳۹۲"))
        assertEquals("084392", VerificationCode.extract("OTP: ٠٨٤٣٩٢"))
    }

    @Test fun ignoresNumbersWithoutCodeContext() {
        assertNull(VerificationCode.extract("Invoice 123456"))
        assertNull(VerificationCode.extract("Verification code link: https://example.test/123456"))
        assertNull(VerificationCode.extract("Verification code expires 2026/11/25"))
        assertNull(VerificationCode.extract("OTP: 123456789"))
    }

    @Test fun rejectsAmbiguousCandidatesAndAcceptsRepeatedCode() {
        assertNull(VerificationCode.extract("OTP: 123456 or 654321"))
        assertEquals("123456", VerificationCode.extract("OTP: 123456. Repeat 123456"))
    }
}
