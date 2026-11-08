package dev.fatter.gpp.manager.play

import android.content.Context
import com.aurora.gplayapi.PlayClient
import com.aurora.gplayapi.device.DeviceProfile
import com.aurora.gplayapi.device.DeviceRegistry
import com.aurora.gplayapi.session.DefaultPlaySessions
import com.aurora.gplayapi.session.PlaySessions
import com.aurora.gplayapi.session.SessionFactory
import com.aurora.gplayapi.session.TokenType
import dev.fatter.gpp.core.Logger
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.Locale
import java.util.Properties

/**
 * Play access for the rootless install path.
 *
 * Preferred: an anonymous AUTH token from the Aurora OSS dispenser, exchanged for a
 * signed-in Play session (authenticated delivery lane). If the dispenser is unavailable,
 * gplayapi's anonymous checkin session takes over (anonymous delivery lane). Both are
 * soft-fail: callers keep the local-APK fallback.
 */
object PlayStoreSession {

    const val GRINDR_PACKAGE = "com.grindrapp.android"

    /** Public Aurora OSS dispenser (anonymous AUTH tokens). */
    const val DEFAULT_DISPENSER_URL = "https://auroraoss.com/api/auth"

    private const val DEVICE_ASSET = "play_device_px_9a.properties"
    private const val DEVICE_ID = "px_9a"

    fun loadDeviceProperties(context: Context): Properties {
        val props = Properties()
        context.assets.open(DEVICE_ASSET).use { props.load(it) }
        return props
    }

    fun deviceProfile(context: Context): DeviceProfile =
        DeviceRegistry.parse(DEVICE_ID, loadDeviceProperties(context))

    /**
     * Builds a [PlayClient] for [GRINDR_PACKAGE].
     *
     * The dispenser is tried first because its authenticated lane is the one that has been
     * shipping; the anonymous lane is the fallback when the dispenser is blocked or down.
     */
    suspend fun createClient(
        context: Context,
        httpClient: PlayHttpClient = PlayHttpClient(),
        dispenserUrl: String = DEFAULT_DISPENSER_URL,
    ): PlayClient {
        val device = deviceProfile(context)
        val locale = Locale.getDefault()

        val authenticated = try {
            val dispensed = dispense(httpClient, device, dispenserUrl)
            SessionFactory(httpClient.engine).authenticate(
                email = dispensed.email,
                token = dispensed.token,
                tokenType = TokenType.AUTH,
                device = device,
                locale = locale,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("Play dispenser auth failed (${e.message}); using anonymous Play session")
            null
        }

        val sessions = if (authenticated != null) {
            PlaySessions { authenticated }
        } else {
            DefaultPlaySessions(httpClient.engine, device, locale)
        }
        return PlayClient(httpClient.engine, sessions)
    }

    private suspend fun dispense(
        httpClient: PlayHttpClient,
        device: DeviceProfile,
        dispenserUrl: String,
    ): DispensedAuth {
        val body = JSONObject(device.properties).toString().toByteArray(Charsets.UTF_8)
        val response = httpClient.postJson(dispenserUrl, body)
        if (!response.isSuccessful) {
            throw IllegalStateException(
                "Play dispenser HTTP ${response.code}: " +
                    response.message.ifBlank { "auth failed" }
            )
        }

        val json = JSONObject(String(response.body, Charsets.UTF_8))
        val email = json.optString("email").ifBlank {
            throw IllegalStateException("Play dispenser response missing email")
        }
        val token = json.optString("authToken").ifBlank {
            json.optString("auth")
        }.ifBlank {
            throw IllegalStateException("Play dispenser response missing authToken")
        }

        Logger.i("Play anonymous session for $email (dispenser)")
        return DispensedAuth(email, token)
    }

    private data class DispensedAuth(val email: String, val token: String)
}
