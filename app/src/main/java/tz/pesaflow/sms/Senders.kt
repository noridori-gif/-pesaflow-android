package tz.pesaflow.sms

import android.content.Context

/**
 * Inaamua kama SMS imetoka kwa huduma ya fedha. SMS nyingine (za watu binafsi, matangazo)
 * HAZITUMWI kwenye server kamwe.
 */
object Senders {
    private val DEFAULT = listOf(
        Regex("m-?pesa|vodacom", RegexOption.IGNORE_CASE),
        Regex("mixx|tigo ?pesa|\\byas\\b", RegexOption.IGNORE_CASE),
        Regex("airtel", RegexOption.IGNORE_CASE),
        Regex("halo ?pesa|halotel", RegexOption.IGNORE_CASE),
        Regex("selcom", RegexOption.IGNORE_CASE),
        Regex("crdb|nmb|nbc|stanbic|absa|equity|exim|dtb|azania|kcb|tpb|amana|akiba|bank|benki", RegexOption.IGNORE_CASE),
    )

    // Maneno yanayoonyesha muamala (kuchuja matangazo kutoka kwa sender wa fedha)
    private val TX_WORDS = Regex(
        "umepokea|umetuma|imetumwa|umelipa|umetoa|imethibitishwa|confirmed|received|credited|debited|paid|withdraw|salio|balance",
        RegexOption.IGNORE_CASE,
    )
    private val AMOUNT = Regex("(tshs?|tzs)\\.?\\s*[0-9]", RegexOption.IGNORE_CASE)

    fun isFinancial(context: Context, sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        // Namba za simu za kawaida si huduma za fedha
        if (Regex("^\\+?[0-9]{9,15}$").matches(sender.trim())) {
            return Prefs.extraSenders(context).any { it.equals(sender.trim(), ignoreCase = true) }
        }
        if (DEFAULT.any { it.containsMatchIn(sender) }) return true
        return Prefs.extraSenders(context).any { sender.contains(it, ignoreCase = true) }
    }

    fun looksLikeTransaction(body: String): Boolean = AMOUNT.containsMatchIn(body) && TX_WORDS.containsMatchIn(body)
}
