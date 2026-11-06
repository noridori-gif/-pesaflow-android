package tz.pesaflow.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.util.UUID

/** SMS mpya ikiingia: kama ni ya fedha, iweke kwenye foleni na uanzishe kutuma. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // SMS ndefu huja vipande vingi: viunganishe kwa mtumaji
        val grouped = parts.groupBy { it.originatingAddress ?: "" }
        val items = grouped.mapNotNull { (sender, pieces) ->
            val body = pieces.joinToString("") { it.messageBody ?: "" }
            val at = pieces.first().timestampMillis
            if (Senders.isFinancial(context, sender) && Senders.looksLikeTransaction(body)) {
                PendingSms(UUID.randomUUID().toString(), sender, body, at)
            } else null
        }
        if (items.isEmpty()) return

        Prefs.enqueue(context, items)
        if (Prefs.isPaired(context)) UploadWorker.enqueueNow(context)
    }
}
