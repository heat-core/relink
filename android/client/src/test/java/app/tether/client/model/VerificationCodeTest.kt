package app.tether.client.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerificationCodeTest {
    @Test fun acceptsEnglishAndNormalizesPersianAndArabicDigits() {
        assertEquals("084392", VerificationCode.extract("Your one-time code is 084392."))
        assertEquals("482913", VerificationCode.extract("Your verification code is 482913"))
        assertEquals("482913", VerificationCode.extract("Your verification code is 482913. Details: https://example.com, help +1 (555) 010-0123"))
        assertEquals("084392", VerificationCode.extract("کد تأیید: ۰۸۴۳۹۲"))
        assertEquals("084392", VerificationCode.extract("OTP: ٠٨٤٣٩٢"))
    }

    @Test fun ignoresNumbersWithoutCodeContext() {
        assertNull(VerificationCode.extract("Code 482913"))
        assertNull(VerificationCode.extract("Code 482913. Details: https://example.com, help +1 (555) 010-0123"))
        assertNull(VerificationCode.extract("Invoice 123456"))
        assertNull(VerificationCode.extract("Verification code link: https://example.test/123456"))
        assertNull(VerificationCode.extract("Verification code expires 2026/11/25"))
        assertNull(VerificationCode.extract("OTP: 123456789"))
    }

    @Test fun rejectsAmbiguousCandidatesAndAcceptsRepeatedCode() {
        assertNull(VerificationCode.extract("OTP: 123456 or 654321"))
        assertEquals("123456", VerificationCode.extract("OTP: 123456. Repeat 123456"))
    }

    @Test fun distinguishesVerificationFromDatesLinksAndCommercialIdentifiers() {
        listOf(
            "Your promo code: 145822", "Your postal code: 145822",
            "Verification code expires at 1930. Order 145822.",
            "OTP help: example.test/145822", "Tracking code: 145822", "CVV2: 1458",
            "کد پیگیری: ۱۴۵۸۲۲. مبلغ ۴۲۵۰۰ تومان", "شماره پیامک 30009014",
            "OTP: 1403-08-30", "Your code 145822 تومان",
        ).forEach { assertNull(it, VerificationCode.extract(it)) }
        assertEquals("084392", VerificationCode.extract("Order 145822 on 2026-11-27. Your verification code is 084392."))
        assertEquals("084392", VerificationCode.extract("https://example.test/145822 کد ورود: ٠٨٤٣٩٢"))
        assertEquals("7741", VerificationCode.extract("G-7741 is your Google verification code."))
        assertEquals("123456", VerificationCode.extract("WhatsApp code 123-456."))
    }
}
