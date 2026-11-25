package app.tether.client.model

/** Only extract a single short code from messages that explicitly identify a verification code. */
internal object VerificationCode {
    private val hint = Regex(
        """\b(?:otp|one[- ]time(?: password| code)?|verification code|security code|login code|authentication code)\b|کد\s*(?:تأیید|تایید|ورود|یکبارمصرف|یک[‌\s]*بار[‌\s]*مصرف)|رمز\s*پویا""",
        RegexOption.IGNORE_CASE
    )
    private val digits = Regex("""(?<![\p{L}\p{N}/])[0-9۰-۹٠-٩]{4,8}(?![\p{L}\p{N}/])""")
    private val url = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun extract(body: String): String? {
        val text = url.replace(body.take(1200), " ")
        if (!hint.containsMatchIn(text)) return null
        return digits.findAll(text).map { candidate ->
            candidate.value.map { Character.digit(it, 10) }.joinToString("")
        }.distinct().take(2).toList().singleOrNull()
    }
}
