package tz.pesaflow.sms

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.res.ColorStateList
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tz.pesaflow.sms.databinding.ActivityMainBinding
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        Notifier.ensureChannel(this)
        UploadWorker.schedulePeriodic(this)

        b.pasteBtn.setOnClickListener {
            val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            b.codeInput.setText(text.trim())
        }
        b.pairBtn.setOnClickListener {
            if (Prefs.pair(this, b.codeInput.text.toString())) {
                b.codeInput.setText("")
                toast("Simu imeunganishwa")
                askPermissions()
                UploadWorker.enqueueNow(this)
            } else toast("Code si sahihi. Tengeneza mpya kwenye web: Mipangilio → Simu.")
            refresh()
        }
        b.permBtn.setOnClickListener { askPermissions() }
        b.historyBtn.setOnClickListener { chooseHistoryRange() }
        b.syncBtn.setOnClickListener { UploadWorker.enqueueNow(this); toast("Inatuma…") }
        b.openWebBtn.setOnClickListener {
            Prefs.serverUrl(this)?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$it/dashboard"))) }
        }
        b.saveSendersBtn.setOnClickListener {
            Prefs.setExtraSenders(this, b.sendersInput.text.toString())
            toast("Imehifadhiwa")
        }
        b.unpairBtn.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setMessage("Ondoa muunganisho wa simu hii na PesaFlow?")
                .setPositiveButton("Ondoa") { _, _ -> Prefs.unpair(this); refresh() }
                .setNegativeButton("Hapana", null)
                .show()
        }
        b.sendersInput.setText(Prefs.extraSenders(this).joinToString(", "))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val list = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(list.toTypedArray())
    }

    /** Chaguo za muda wa historia: (lebo, idadi ya siku). Siku 1 = kuanzia saa 6 usiku wa leo. */
    private val historyOptions = listOf(
        "Leo tu" to 1, "Siku 3" to 3, "Wiki 1" to 7, "Wiki 2" to 14,
        "Mwezi 1" to 30, "Miezi 3" to 90, "Miezi 6" to 180, "Mwaka 1" to 365,
    )

    private fun chooseHistoryRange() {
        if (!has(Manifest.permission.READ_SMS)) { askPermissions(); return }
        val labels = (historyOptions.map { it.first } + "Andika idadi ya siku…").toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Leta SMS za muda gani?")
            .setItems(labels) { _, which ->
                if (which < historyOptions.size) importHistory(historyOptions[which].second, historyOptions[which].first)
                else askCustomDays()
            }
            .setNegativeButton("Ghairi", null)
            .show()
    }

    private fun askCustomDays() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "mf. 45"
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Idadi ya siku (1–730)")
            .setView(input)
            .setPositiveButton("Leta") { _, _ ->
                val d = input.text.toString().toIntOrNull()
                if (d == null || d < 1 || d > 730) toast("Weka namba kati ya 1 na 730")
                else importHistory(d, "siku $d")
            }
            .setNegativeButton("Ghairi", null)
            .show()
    }

    private fun importHistory(days: Int, label: String) {
        b.historyBtn.isEnabled = false
        b.historyBtn.text = "Inasoma SMS…"
        lifecycleScope.launch {
            val n = withContext(Dispatchers.IO) { HistoryImporter.import(this@MainActivity, days) }
            b.historyBtn.isEnabled = true
            b.historyBtn.text = getString(R.string.history_btn)
            toast(if (n == 0) "Hakuna SMS za fedha ($label)" else "SMS $n ($label) zimewekwa kwenye foleni na zinatumwa")
            if (n > 0) UploadWorker.enqueueNow(this@MainActivity)
            refresh()
        }
    }

    private fun refresh() {
        val paired = Prefs.isPaired(this)
        val smsOk = has(Manifest.permission.RECEIVE_SMS)
        b.pairSection.visibility = if (paired) View.GONE else View.VISIBLE
        b.pairedSection.visibility = if (paired) View.VISIBLE else View.GONE
        b.permCard.visibility = if (smsOk) View.GONE else View.VISIBLE

        val err = Prefs.authError(this)
        val ok = paired && smsOk && err == null
        b.status.text = when {
            !paired -> "Bado haijaunganishwa na PesaFlow"
            err != null -> err
            !smsOk -> "Imeunganishwa, lakini inahitaji ruhusa ya SMS"
            else -> "Inafanya kazi · miamala inaingia yenyewe"
        }
        b.status.setBackgroundResource(if (ok) R.drawable.bg_ok else R.drawable.bg_warn)
        val fg = if (ok) 0xFFFFFFFF.toInt() else 0xFF5C3A00.toInt()
        val dot = if (ok) 0xFF7CF0C4.toInt() else 0xFFA86A00.toInt()
        b.status.setTextColor(fg)
        TextViewCompat.setCompoundDrawableTintList(b.status, ColorStateList.valueOf(dot))

        b.server.text = Prefs.serverUrl(this)?.removePrefix("https://") ?: ""
        b.server.visibility = if (paired) View.VISIBLE else View.GONE

        val last = Prefs.lastSync(this)
        b.statPending.text = Prefs.queue(this).size.toString()
        b.statSent.text = Prefs.totalSent(this).toString()
        b.statLast.text = when {
            last == 0L -> "—"
            System.currentTimeMillis() - last < 86_400_000L -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(last))
            else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(last))
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
