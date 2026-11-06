package tz.pesaflow.sms

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        b.historyBtn.setOnClickListener { importHistory() }
        b.syncBtn.setOnClickListener { UploadWorker.enqueueNow(this); toast("Inatuma…") }
        b.openWebBtn.setOnClickListener {
            Prefs.serverUrl(this)?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$it/dashboard"))) }
        }
        b.saveSendersBtn.setOnClickListener {
            Prefs.setExtraSenders(this, b.sendersInput.text.toString())
            toast("Imehifadhiwa")
        }
        b.unpairBtn.setOnClickListener {
            AlertDialog.Builder(this)
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

    private fun importHistory() {
        if (!has(Manifest.permission.READ_SMS)) { askPermissions(); return }
        b.historyBtn.isEnabled = false
        lifecycleScope.launch {
            val n = withContext(Dispatchers.IO) { HistoryImporter.import(this@MainActivity, 90) }
            b.historyBtn.isEnabled = true
            toast(if (n == 0) "Hakuna SMS za fedha za siku 90 zilizopatikana" else "SMS $n zimewekwa kwenye foleni")
            if (n > 0) UploadWorker.enqueueNow(this@MainActivity)
            refresh()
        }
    }

    private fun refresh() {
        val paired = Prefs.isPaired(this)
        val smsOk = has(Manifest.permission.RECEIVE_SMS)
        b.pairSection.visibility = if (paired) View.GONE else View.VISIBLE
        b.pairedSection.visibility = if (paired) View.VISIBLE else View.GONE

        val pending = Prefs.queue(this).size
        val last = Prefs.lastSync(this)
        val err = Prefs.authError(this)
        b.status.text = when {
            !paired -> "Haijaunganishwa. Tengeneza pairing code kwenye web: Mipangilio → Simu za Android."
            err != null -> err
            !smsOk -> "Imeunganishwa, lakini ruhusa ya kusoma SMS haijatolewa."
            else -> "Inafanya kazi. Miamala mipya ya M-Pesa, Mixx, Airtel, HaloPesa, Selcom na benki inaingia yenyewe."
        }
        b.status.setBackgroundResource(if (paired && smsOk && err == null) R.drawable.bg_ok else R.drawable.bg_warn)
        b.server.text = Prefs.serverUrl(this) ?: ""
        b.stats.text = buildString {
            append("Zinasubiri kutumwa: $pending\n")
            append("Zimetumwa jumla: ${Prefs.totalSent(this@MainActivity)}\n")
            append("Mara ya mwisho: ")
            append(if (last == 0L) "bado" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last)))
        }
        b.permBtn.visibility = if (smsOk) View.GONE else View.VISIBLE
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
