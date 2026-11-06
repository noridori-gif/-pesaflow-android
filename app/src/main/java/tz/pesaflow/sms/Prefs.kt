package tz.pesaflow.sms

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class PendingSms(val id: String, val sender: String, val body: String, val receivedAt: Long)

object Prefs {
    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("pesaflow", Context.MODE_PRIVATE)

    fun serverUrl(c: Context): String? = sp(c).getString("url", null)
    fun token(c: Context): String? = sp(c).getString("token", null)
    fun isPaired(c: Context) = !serverUrl(c).isNullOrBlank() && !token(c).isNullOrBlank()

    /** Code kutoka web: PESAFLOW1.<base64url(JSON {u, t})> */
    fun pair(c: Context, code: String): Boolean {
        val raw = code.trim()
        if (!raw.startsWith("PESAFLOW1.")) return false
        return try {
            val json = String(Base64.decode(raw.removePrefix("PESAFLOW1."), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val o = JSONObject(json)
            val url = o.getString("u").trimEnd('/')
            val token = o.getString("t")
            if (!url.startsWith("https://") && !url.startsWith("http://10.") && !url.startsWith("http://192.168.")) return false
            sp(c).edit().putString("url", url).putString("token", token).putString("authError", null).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun unpair(c: Context) = sp(c).edit().remove("url").remove("token").apply()

    fun extraSenders(c: Context): List<String> =
        (sp(c).getString("extraSenders", "") ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
    fun setExtraSenders(c: Context, v: String) = sp(c).edit().putString("extraSenders", v).apply()

    fun authError(c: Context): String? = sp(c).getString("authError", null)
    fun setAuthError(c: Context, v: String?) = sp(c).edit().putString("authError", v).apply()

    fun lastSync(c: Context) = sp(c).getLong("lastSync", 0L)
    fun totalSent(c: Context) = sp(c).getInt("totalSent", 0)
    fun recordSync(c: Context, sent: Int) =
        sp(c).edit().putLong("lastSync", System.currentTimeMillis()).putInt("totalSent", totalSent(c) + sent).apply()

    // ---- foleni ya SMS zinazosubiri kutumwa ----
    private val lock = Any()
    private const val MAX_QUEUE = 3000

    fun enqueue(c: Context, items: List<PendingSms>) = synchronized(lock) {
        val list = queue(c).toMutableList()
        val seen = list.map { it.sender + it.receivedAt + it.body.hashCode() }.toHashSet()
        for (i in items) if (seen.add(i.sender + i.receivedAt + i.body.hashCode())) list.add(i)
        save(c, list.takeLast(MAX_QUEUE))
    }

    fun queue(c: Context): List<PendingSms> = synchronized(lock) {
        val arr = JSONArray(sp(c).getString("queue", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PendingSms(o.getString("id"), o.optString("sender"), o.getString("body"), o.getLong("at"))
        }
    }

    fun remove(c: Context, ids: Set<String>) = synchronized(lock) {
        save(c, queue(c).filterNot { it.id in ids })
    }

    private fun save(c: Context, list: List<PendingSms>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("sender", it.sender).put("body", it.body).put("at", it.receivedAt)) }
        sp(c).edit().putString("queue", arr.toString()).apply()
    }
}
