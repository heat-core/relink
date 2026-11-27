package app.tether.data

/** Combines the platform callbacks for one send request. */
internal class SmsParts {
    data class Result(val state: String, val error: Int? = null)

    private val seen = HashSet<Int>()
    private var partCount = 0
    private var sendError: Int? = null
    private var sent = false
    private var finished = false
    private var deliveryError: Int? = null
    private var receipt: Result? = null

    fun onSent(part: Int, count: Int, error: Int?): List<Result> {
        if (sent || finished || part !in 0 until count) return emptyList()
        if (partCount == 0) partCount = count
        if (partCount != count || !seen.add(part)) return emptyList()
        if (sendError == null) sendError = error
        if (seen.size < count) return emptyList()
        sendError?.let {
            finished = true
            return listOf(Result("failed", it))
        }
        sent = true
        val result = receipt ?: return listOf(Result("sent"))
        finished = true
        return listOf(Result("sent"), result)
    }

    fun onDelivery(last: Boolean, error: Int?): List<Result> {
        if (finished || receipt != null) return emptyList()
        if (!last) {
            if (deliveryError == null) deliveryError = error
            return emptyList()
        }
        // Some Android radio paths only request a receipt for the final part. Preserve that rule.
        val result = (error ?: deliveryError)?.let { Result("failed", it) } ?: Result("delivered")
        receipt = result
        if (!sent) return emptyList()   // a delivery callback must not complete a still-pending send
        finished = true
        return listOf(result)
    }
}
