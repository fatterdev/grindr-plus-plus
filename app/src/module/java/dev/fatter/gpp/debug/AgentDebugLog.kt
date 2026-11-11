package dev.fatter.gpp.debug

import android.util.Log
import org.json.JSONObject

/**
 * Logcat-only diagnostic sink. No network transport.
 * Do not log secrets / PII.
 */
object AgentDebugLog {
    private const val TAG = "GPP-Debug"

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix",
    ) {
        val payload = JSONObject()
            .put("runId", runId)
            .put("hypothesisId", hypothesisId)
            .put("location", location)
            .put("message", message)
            .put("timestamp", System.currentTimeMillis())
            .put(
                "data",
                JSONObject().also { obj ->
                    data.forEach { (k, v) -> obj.put(k, v ?: JSONObject.NULL) }
                },
            )
        Log.i(TAG, payload.toString())
    }
}
