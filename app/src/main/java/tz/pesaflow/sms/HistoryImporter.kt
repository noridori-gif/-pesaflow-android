package tz.pesaflow.sms

import android.content.Context
import android.provider.Telephony
import java.util.UUID

/** Inasoma SMS za fedha zilizopo tayari kwenye simu (siku N zilizopita) na kuziweka kwenye foleni. */
object HistoryImporter {
    /** days = 1 inamaanisha "leo" (kuanzia saa 6 usiku); days = 7 ni leo pamoja na siku 6 zilizopita, n.k. */
    fun import(c: Context, days: Int = 90): Int {
        val since = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis
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
