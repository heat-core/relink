import Foundation

/// Pairing code: `tether1:<S_P>.<token>.<name>` (base64url, no padding), shown as a QR code since 1.2 in its link form
/// `tether://pair?c=<S_P>.<token>.<name>`, which the iPhone's Camera app offers to open in the app. Both forms parse.
public struct PairingCode: Equatable, Sendable {
    public static let prefix = "tether1:"
    /// The link form's start (the QR code since 1.2).
    public static let linkPrefix = "tether://pair?c="
    public var peripheralKey: Data
    public var token: Data
    public var deviceName: String

    public init(peripheralKey: Data, token: Data, deviceName: String) {
        self.peripheralKey = peripheralKey
        self.token = token
        self.deviceName = deviceName
    }

    public var string: String {
        Self.prefix + peripheralKey.base64URL() + "." + token.base64URL() + "." + Data(deviceName.utf8).base64URL()
    }

    /// The link form (what the QR code holds since 1.2).
    public var link: String {
        Self.linkPrefix + peripheralKey.base64URL() + "." + token.base64URL() + "." + Data(deviceName.utf8).base64URL()
    }

    /// True for a `tether:` pairing link (`tether://pair?…` or `tether:pair?…`), valid or not.
    public static func isPairLink(_ s: String) -> Bool {
        let t = s.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return t.hasPrefix("tether://pair?") || t.hasPrefix("tether:pair?") || t.hasPrefix("tether://pair/?")
    }

    /// Parses either form: `tether1:…` (the code, and the QR code of 1.1 phones) or `tether://pair?c=…`.
    public static func parse(_ s: String) -> PairingCode? {
        let trimmed = s.trimmingCharacters(in: .whitespacesAndNewlines)
        if isPairLink(trimmed) {
            guard let q = trimmed.split(separator: "?", maxSplits: 1).last else { return nil }
            for item in q.split(separator: "&") {
                let kv = item.split(separator: "=", maxSplits: 1)
                if kv.count == 2, kv[0] == "c" { return parseBody(String(kv[1]).removingPercentEncoding ?? String(kv[1])) }
            }
            return nil
        }
        guard trimmed.hasPrefix(prefix) else { return nil }
        return parseBody(String(trimmed.dropFirst(prefix.count)))
    }

    private static func parseBody(_ body: String) -> PairingCode? {
        let parts = body.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 3,
              let key = Data(base64URL: String(parts[0])), key.count == 65, key.first == 0x04,
              let token = Data(base64URL: String(parts[1])), token.count == 16,
              let nameData = Data(base64URL: String(parts[2])),
              let name = String(data: nameData, encoding: .utf8) else { return nil }
        return PairingCode(peripheralKey: key, token: token, deviceName: name)
    }
}

/// G.711 µ-law, the fallback codec (8 kHz, 64 kbps).
public enum G711 {
    public static func encode(_ sample: Int16) -> UInt8 {
        let bias: Int32 = 0x84
        let clip: Int32 = 32635
        var s = Int32(sample)
        let sign: Int32 = s < 0 ? 0x80 : 0
        if s < 0 { s = -s }
        if s > clip { s = clip }
        s += bias
        var exponent: Int32 = 7
        var mask: Int32 = 0x4000
        while exponent > 0 && (s & mask) == 0 {
            exponent -= 1
            mask >>= 1
        }
        let mantissa = (s >> (exponent + 3)) & 0x0f
        return UInt8(truncatingIfNeeded: ~(sign | (exponent << 4) | mantissa))
    }

    public static func decode(_ byte: UInt8) -> Int16 {
        let u = Int32(~byte & 0xff)
        let sign = u & 0x80
        let exponent = (u >> 4) & 0x07
        let mantissa = u & 0x0f
        var s = ((mantissa << 3) + 0x84) << exponent
        s -= 0x84
        return Int16(sign != 0 ? -s : s)
    }

    public static func encode(_ pcm: [Int16]) -> Data { Data(pcm.map(encode)) }
    public static func decode(_ data: Data) -> [Int16] { data.map(decode) }
}

/// Fixed-delay jitter buffer keyed by 16-bit sequence numbers. Late frames are dropped.
public struct JitterBuffer<Frame> {
    public let targetDepth: Int
    public let maxDepth: Int
    private var frames: [UInt16: Frame] = [:]
    private var nextSeq: UInt16?
    private var primed = false
    public private(set) var dropped = 0
    public private(set) var concealed = 0

    public init(targetDepth: Int = 3, maxDepth: Int = 12) {
        self.targetDepth = targetDepth
        self.maxDepth = maxDepth
    }

    public var depth: Int { frames.count }

    /// Signed distance a - b in sequence space.
    static func distance(_ a: UInt16, _ b: UInt16) -> Int { Int(Int16(bitPattern: a &- b)) }

    public mutating func push(seq: UInt16, _ frame: Frame) {
        if let next = nextSeq, Self.distance(seq, next) < 0 {
            dropped += 1
            return
        }
        if nextSeq == nil { nextSeq = seq }
        frames[seq] = frame
        // Too far behind: skip ahead to the oldest frames we can still use (past lost ones too, or the depth grows).
        while frames.count > maxDepth, let next = nextSeq {
            frames.removeValue(forKey: next)
            nextSeq = next &+ 1
            dropped += 1
        }
    }

    /// Called once per frame period. `nil` means play silence / conceal.
    public mutating func pop() -> Frame? {
        guard let next = nextSeq else { return nil }
        if !primed {
            guard frames.count >= targetDepth else { return nil }
            primed = true
        }
        nextSeq = next &+ 1
        if let f = frames.removeValue(forKey: next) { return f }
        concealed += 1
        if frames.isEmpty { primed = false; nextSeq = nil }
        return nil
    }

    public mutating func reset() {
        frames.removeAll()
        nextSeq = nil
        primed = false
    }
}

/// Finds a one-time code in an SMS body (4–8 digits, preferring ones near "code"-like words).
public enum OTPExtractor {
    public static func extract(_ text: String) -> String? { find(text)?.code }

    /// Returns a code with adjacent verification context and its UTF-16 range in the original text.
    public static func find(_ text: String) -> (code: String, range: NSRange)? {
        let normalized = normalizeDigits(text)
        let message = normalized as NSString
        let whole = NSRange(location: 0, length: message.length)
        let links = url.matches(in: normalized, range: whole).map(\.range)
        var found: (String, NSRange)?
        for match in candidate.matches(in: normalized, range: whole) {
            let r = match.range
            if links.contains(where: { NSIntersectionRange($0, r).length != 0 }) { continue }
            let before = message.substring(with: NSRange(location: max(0, r.location - 96), length: min(r.location, 96)))
            let afterStart = NSMaxRange(r)
            let after = message.substring(with: NSRange(location: afterStart, length: min(96, whole.length - afterStart)))
            if matches(unit, after) { continue }
            let leading = firstMatch(prefix, before)
            let trailing = firstMatch(suffix, after)
            let labeled = (leading.map { !matches(nonCode, $0) } ?? false) || (trailing.map { !matches(nonCode, $0) } ?? false)
            let tagged = normalized.hasPrefix("<#>") && matches(appCode, before)
            let bank = matches(bankField, before) && matches(bankTitle, normalized)
            guard labeled || tagged || bank else { continue }
            if matches(alternate, after) { return nil }
            let code = message.substring(with: r).filter(\.isNumber)
            if let existing = found, existing.0 != code { return nil }
            if found == nil { found = (code, r) }
        }
        return found
    }

    private static func matches(_ regex: NSRegularExpression, _ text: String) -> Bool { firstMatch(regex, text) != nil }

    private static func firstMatch(_ regex: NSRegularExpression, _ text: String) -> String? {
        let ns = text as NSString
        return regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)).map { ns.substring(with: $0.range) }
    }

    private static let candidate = try! NSRegularExpression(pattern: #"(?<![\p{L}\p{N}+*/:.,،٫٬])(?<![0-9][- \t])(?:[0-9]{3}[- \t][0-9]{3}|[0-9]{4,8})(?![\p{L}\p{N}*/:٫٬])(?![.,،][0-9])(?![- \t][0-9])"#)
    private static let prefix = try! NSRegularExpression(pattern: #"(?i)(?:\b(?:otp|one[- ]time(?:[ \t]+(?:password|code))?|(?:verification|security|login|authentication|activation|confirmation)[ \t]+(?:code|pin)|your(?:[ \t]+[a-z][a-z0-9]{1,15}){0,3}[ \t]+code|(?:telegram|whatsapp)[ \t]+code)\b|کد[ \t]*(?:تأیید|تایید|فعال[ \t‌]*سازی|ورود(?:[ \t]+به[ \t]+[\p{L}‌]+(?=[ \t]*[:：]))?|یک[ \t‌]*بار[ \t‌]*مصرف)|رمز[ \t]*(?:پویا|دوم[ \t]+پویا|عبور[ \t]+موقت|یک[ \t‌]*بار[ \t‌]*مصرف))(?:[ \t]+(?:شما|ورود|خرید|تأیید|تایید|is))*[ \t]*[:：=]?[ \t]*$"#)
    private static let suffix = try! NSRegularExpression(pattern: #"(?i)^[ \t]+is[ \t]+(?:(?:your|the)[ \t]+)?(?:[a-z][a-z0-9]{1,15}[ \t]+){0,3}(?:one[- ]time[ \t]+password|code|otp)\b"#)
    private static let nonCode = try! NSRegularExpression(pattern: #"(?i)\b(?:promo(?:tion(?:al)?)?|coupon|discount|tracking|transaction|order|invoice|cvv2?|postal|zip|reference)\b|پیگیری|سفارش|تخفیف|تراکنش|پستی|تبلیغ"#)
    private static let unit = try! NSRegularExpression(pattern: #"(?i)^[ \t]*(?:[$€£]|(?:rial|rials|toman|irr|usd|eur|dollars?|seconds?|minutes?|sec|min)\b|ریال|تومان|ثانیه|دقیقه)"#)
    private static let url = try! NSRegularExpression(pattern: #"(?i)(?:https?://|www\.|(?:[a-z0-9-]+\.)+[a-z]{2,}/)[^\s<>]+"#)
    private static let appCode = try! NSRegularExpression(pattern: #"(?i)\bcode[ \t]*[:：][ \t]*$"#)
    private static let bankTitle = try! NSRegularExpression(pattern: #"(?m)^رمز[ \t]+دوم[ \t]+پویا[ \t]*$"#)
    private static let bankField = try! NSRegularExpression(pattern: #"(?:^|\n)[ \t]*رمز[ \t]*[:：][ \t]*\z"#)
    private static let alternate = try! NSRegularExpression(pattern: #"(?i)^[ \t]+(?:or|یا)[ \t]+[0-9]{4,8}\b"#)

    /// Converts Persian and Arabic-Indic digits to ASCII.
    public static func normalizeDigits(_ s: String) -> String {
        var out = ""
        for scalar in s.unicodeScalars {
            switch scalar.value {
            case 0x06F0...0x06F9: out.unicodeScalars.append(Unicode.Scalar(scalar.value - 0x06F0 + 0x30)!)
            case 0x0660...0x0669: out.unicodeScalars.append(Unicode.Scalar(scalar.value - 0x0660 + 0x30)!)
            default: out.unicodeScalars.append(scalar)
            }
        }
        return out
    }
}

/// Normalises phone numbers for matching contacts to SMS / calls.
public enum PhoneNumber {
    public static func digits(_ s: String) -> String {
        OTPExtractor.normalizeDigits(s).filter { $0.isNumber || $0 == "+" }
    }

    /// Last 9 digits; good enough to match "+98912…" with "0912…".
    public static func matchKey(_ s: String) -> String {
        let d = digits(s).filter(\.isNumber)
        return String(d.suffix(9))
    }
}

/// Search as people type it: case, accents and Arabic / Persian letter variants (ي/ی, ك/ک, ة/ه, ZWNJ) do not
/// matter, and a number matches in any notation ("0912…" finds "+98 912…", Persian digits work).
public enum SearchText {
    public static func fold(_ s: String) -> String {
        var t = s.folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: nil)
        for (a, b) in [("ي", "ی"), ("ى", "ی"), ("ك", "ک"), ("ة", "ه"), ("أ", "ا"), ("إ", "ا"), ("آ", "ا"), ("\u{200C}", ""), ("\u{200F}", "")] {
            t = t.replacingOccurrences(of: a, with: b)
        }
        return t
    }

    /// Digits of a typed number without a trunk prefix ("0") or the Iranian country code, so notations meet.
    public static func numberCore(_ s: String) -> String {
        var d = PhoneNumber.digits(s).filter(\.isNumber)
        if d.hasPrefix("0098") { d.removeFirst(4) } else if d.hasPrefix("98") && d.count > 10 { d.removeFirst(2) }
        while d.hasPrefix("0") { d.removeFirst() }
        return d
    }

    /// True when `query` matches any of the names or numbers.
    public static func matches(_ query: String, names: [String], numbers: [String]) -> Bool {
        let q = fold(query.trimmingCharacters(in: .whitespaces))
        if q.isEmpty { return true }
        if names.contains(where: { fold($0).contains(q) }) { return true }
        let qd = numberCore(query)
        guard qd.count >= 2, query.contains(where: { $0.isNumber }) else { return false }
        return numbers.contains { numberCore($0).contains(qd) }
    }
}
