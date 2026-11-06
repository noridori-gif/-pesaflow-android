package tz.pesaflow.sms

import android.content.Context
import android.provider.Telephony
import java.util.UUID

/** Inasoma SMS za fedha zilizopo tayari kwenye simu (siku N zilizopita) na kuziweka kwenye foleni. */
object HistoryImporter {
    fun import(c: Context, days: Int = 90): Int {
        val since = System.currentTimeMillis() - days * 86_400_000L
        val items = mutableListOf<PendingSms>()
        c.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cur ->
            val iA = cur.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iB = cur.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iD = cur.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cur.moveToNext()) {
                val sender = cur.getString(iA) ?: continue
                val body = cur.getString(iB) ?: continue
                if (Senders.isFinancial(c, sender) && Senders.looksLikeTransaction(body)) {
                    items.add(PendingSms(UUID.randomUUID().toString(), sender, body, cur.getLong(iD)))
                }
            }
        }
        if (items.isNotEmpty()) Prefs.enqueue(c, items)
        return items.size
    }
}
