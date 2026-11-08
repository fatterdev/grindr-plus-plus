package dev.fatter.gpp.manager.play

import com.aurora.gplayapi.engine.OkHttpEngine
import com.aurora.gplayapi.net.HttpEngine
import com.aurora.gplayapi.net.HttpMethod
import com.aurora.gplayapi.net.HttpResponse
import com.aurora.gplayapi.net.PlayRequest
import com.aurora.gplayapi.net.RequestBody
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttp transport for gplayapi 4 and the Aurora OSS token dispenser.
 *
 * gplayapi 4 talks to the network through [HttpEngine]; [engine] exposes the
 * same configured client to the library, while [postJson] serves the dispenser.
 */
class PlayHttpClient(
    private val userAgent: String = "dev.fatter.gpp-manager-play",
) {

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val engine: HttpEngine = OkHttpEngine(okHttpClient)

    suspend fun postJson(url: String, body: ByteArray): HttpResponse =
        engine.execute(
            PlayRequest(
                method = HttpMethod.POST,
                url = url,
                headers = mapOf("User-Agent" to userAgent),
                body = RequestBody.Bytes(body, "application/json"),
            )
        )
}
