package app.tether.client.model

/** Extract a single code with adjacent verification context. */
internal object VerificationCode {
    private val candidate = Regex("""(?<![\p{L}\p{N}+*/:.,،٫٬])(?<![0-9][- \t])(?:[0-9]{3}[- \t][0-9]{3}|[0-9]{4,8})(?![\p{L}\p{N}*/:٫٬])(?![.,،][0-9])(?![- \t][0-9])""")
    private val prefix = Regex("""(?i)(?:\b(?:otp|one[- ]time(?:[ \t]+(?:password|code))?|(?:verification|security|login|authentication|activation|confirmation)[ \t]+(?:code|pin)|your(?:[ \t]+[a-z][a-z0-9]{1,15}){0,3}[ \t]+code|(?:telegram|whatsapp)[ \t]+code)\b|کد[ \t]*(?:تأیید|تایید|فعال[ \t‌]*سازی|ورود(?:[ \t]+به[ \t]+[\p{L}‌]+(?=[ \t]*[:：]))?|یک[ \t‌]*بار[ \t‌]*مصرف)|رمز[ \t]*(?:پویا|دوم[ \t]+پویا|عبور[ \t]+موقت|یک[ \t‌]*بار[ \t‌]*مصرف))(?:[ \t]+(?:شما|ورود|خرید|تأیید|تایید|is))*[ \t]*[:：=]?[ \t]*$""")
    private val suffix = Regex("""(?i)^[ \t]+is[ \t]+(?:(?:your|the)[ \t]+)?(?:[a-z][a-z0-9]{1,15}[ \t]+){0,3}(?:one[- ]time[ \t]+password|code|otp)\b""")
    private val nonCode = Regex("""(?i)\b(?:promo(?:tion(?:al)?)?|coupon|discount|tracking|transaction|order|invoice|cvv2?|postal|zip|reference)\b|پیگیری|سفارش|تخفیف|تراکنش|پستی|تبلیغ""")
    private val unit = Regex("""(?i)^[ \t]*(?:[$€£]|(?:rial|rials|toman|irr|usd|eur|dollars?|seconds?|minutes?|sec|min)\b|ریال|تومان|ثانیه|دقیقه)""")
    private val url = Regex("""(?i)(?:https?://|www\.|(?:[a-z0-9-]+\.)+[a-z]{2,}/)[^\s<>]+""")
    private val appCode = Regex("""(?i)\bcode[ \t]*[:：][ \t]*$""")
    private val bankTitle = Regex("""(?m)^رمز[ \t]+دوم[ \t]+پویا[ \t]*$""")
    private val bankField = Regex("""(?:^|\n)[ \t]*رمز[ \t]*[:：][ \t]*\z""")
    private val alternate = Regex("""(?i)^[ \t]+(?:or|یا)[ \t]+[0-9]{4,8}\b""")

    fun extract(body: String): String? {
        val text = buildString {
            for (ch in body) append(when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            })
        }
        val links = url.findAll(text).map { it.range }.toList()
        var found: String? = null
        for (match in candidate.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (links.any { it.first < end && start <= it.last }) continue
            val before = text.substring(maxOf(0, start - 96), start)
            val after = text.substring(end, minOf(text.length, end + 96))
            if (unit.containsMatchIn(after)) continue
            val labeled = prefix.find(before)?.value?.let { !nonCode.containsMatchIn(it) } == true ||
                suffix.find(after)?.value?.let { !nonCode.containsMatchIn(it) } == true
            val tagged = text.startsWith("<#>") && appCode.containsMatchIn(before)
            val bank = bankField.containsMatchIn(before) && bankTitle.containsMatchIn(text)
            if (!labeled && !tagged && !bank) continue
            if (alternate.containsMatchIn(after)) return null
            val code = match.value.filter { it in '0'..'9' }
            if (found != null && found != code) return null
            found = code
        }
        return found
    }
}
