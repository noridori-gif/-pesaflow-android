package tz.pesaflow.sms

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Inatuma SMS zilizo kwenye foleni kwenda /api/ingest/sms. Inajaribu tena mtandao ukikosekana. */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val c = applicationContext
        val url = Prefs.serverUrl(c) ?: return@withContext Result.success()
        val token = Prefs.token(c) ?: return@withContext Result.success()

        var sentTotal = 0
        while (true) {
            val batch = Prefs.queue(c).take(BATCH)
            if (batch.isEmpty()) break

            val payload = JSONObject().put("messages", JSONArray().apply {
                batch.forEach { put(JSONObject().put("sender", it.sender).put("body", it.body).put("received_at", it.receivedAt)) }
            })

            val conn = (URL("$url/api/ingest/sms").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = conn.responseCode
                when {
                    code == 401 || code == 403 -> {
                        Prefs.setAuthError(c, "Simu hii imezimwa au haijaruhusiwa. Unganisha upya kwa pairing code mpya.")
                        return@withContext Result.failure()
                    }
                    code in 200..299 -> {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        Prefs.remove(c, batch.map { it.id }.toSet())
                        sentTotal += batch.size
                        Prefs.setAuthError(c, null)
                        notifyResults(c, body)
                    }
                    code == 400 -> {
                        // Data mbovu: iondoe ili isizuie foleni
                        Prefs.remove(c, batch.map { it.id }.toSet())
                    }
                    else -> return@withContext Result.retry()
                }
            } catch (e: Exception) {
                return@withContext Result.retry()
            } finally {
                conn.disconnect()
            }
        }
        if (sentTotal > 0) Prefs.recordSync(c, sentTotal)
        Result.success()
    }

    private fun notifyResults(c: Context, body: String) {
        try {
            val results = JSONObject(body).optJSONArray("results") ?: return
            val summaries = (0 until results.length())
                .map { results.getJSONObject(it) }
                .filter { it.optString("status") == "parsed" }
                .mapNotNull { it.optString("summary").takeIf { s -> s.isNotBlank() && s != "null" } }
            if (summaries.isNotEmpty()) Notifier.transactions(c, summaries)
        } catch (_: Exception) { }
    }

    companion object {
        private const val BATCH = 100
        private val NET = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun enqueueNow(c: Context) {
            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(NET)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(c).enqueueUniqueWork("pesaflow-upload", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }

        /** Kinga: hata ikitokea kitu, foleni inakaguliwa kila saa 6. */
        fun schedulePeriodic(c: Context) {
            val req = PeriodicWorkRequestBuilder<UploadWorker>(6, TimeUnit.HOURS).setConstraints(NET).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("pesaflow-periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
