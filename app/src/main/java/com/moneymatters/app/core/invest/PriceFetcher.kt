package com.moneymatters.app.core.invest

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The outcome of downloading one file. A failure carries a reason fit to show the investor as it is. */
sealed interface FetchResult {
    class Ok(val bytes: ByteArray) : FetchResult
    class Failed(val httpStatus: Int?, val reason: String) : FetchResult
}

fun interface PriceFetcher {
    suspend fun fetch(url: String): FetchResult
}

/** Reads at most [limit] bytes; written out because `InputStream.readNBytes` needs Android 13 and the app supports 8. */
private fun readCapped(stream: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val read = stream.read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val MAX_BYTES = 40 * 1024 * 1024
private const val BROWSER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

/**
 * Downloads with browser-like headers, a few retries with growing pauses on a flaky connection or a 5xx/429,
 * and a minimum gap between requests so a run never hammers a source (plan.html → "Downloads use ... retries with
 * backoff, and rate limiting"). A 404 or 403 is final: retrying won't make a moved file reappear.
 */
class HttpPriceFetcher(
    private val retries: Int = 3,
    private val backoffMillis: Long = 1_000,
    private val minGapMillis: Long = 1_000,
    private val timeoutMillis: Int = 30_000,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) : PriceFetcher {
    private var lastRequestAt = 0L

    override suspend fun fetch(url: String): FetchResult {
        var failure: FetchResult.Failed = FetchResult.Failed(null, "No attempt was made")
        for (attempt in 0..retries) {
            if (attempt > 0) pause(backoffMillis * (1L shl (attempt - 1)))
            val wait = minGapMillis - (clock() - lastRequestAt)
            if (lastRequestAt != 0L && wait > 0) pause(wait)
            lastRequestAt = clock()
            val result = withContext(Dispatchers.IO) { attemptOnce(url) }
            if (result is FetchResult.Ok) return result
            failure = result as FetchResult.Failed
            if (!isRetryable(failure)) return failure
        }
        return failure
    }

    private fun isRetryable(failure: FetchResult.Failed) = failure.httpStatus == null || failure.httpStatus == 429 || failure.httpStatus >= 500

    private fun attemptOnce(url: String): FetchResult {
        val connection = try { URL(url).openConnection() as HttpURLConnection } catch (e: IOException) {
            return FetchResult.Failed(null, "The address isn't valid: $url")
        }
        return try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("User-Agent", BROWSER_AGENT)
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
            if ("nseindia.com" in url) connection.setRequestProperty("Referer", "https://www.nseindia.com/")
            when (val code = connection.responseCode) {
                200 -> {
                    val bytes = connection.inputStream.use { readCapped(it, MAX_BYTES) }
                    if (bytes.isEmpty()) FetchResult.Failed(200, "The source returned an empty file.") else FetchResult.Ok(bytes)
                }
                404 -> FetchResult.Failed(404, "File not found (404): the source has moved, or there was no trading that day.")
                403 -> FetchResult.Failed(403, "Blocked (403): the source refused the request.")
                429 -> FetchResult.Failed(429, "Too many requests (429): the source asked us to slow down.")
                in 500..599 -> FetchResult.Failed(code, "The source had a problem ($code).")
                else -> FetchResult.Failed(code, "Unexpected response ($code).")
            }
        } catch (e: IOException) {
            FetchResult.Failed(null, "Couldn't connect: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }
}
