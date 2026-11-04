package com.example.pokemonsavemanager

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** Exports one exact native WR7 receipt summary without modifying the source save. */
class SwitchLetsGoMysteryGiftActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val exportRequest = 2122
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var selectedSlot = -1
    private var record: LetsGoMysteryGiftRecord? = null
    private var rawRecord: ByteArray? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var export: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        selectedSlot = intent?.getIntExtra("pse.lets_go.wr7.slot", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedSlot !in 0 until 10) {
            finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Mystery Gift Receipt"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Export one exact native 0x140-byte WR7 receipt summary. The source save remains unchanged."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        export = Button(this).apply {
            text = "Export exact native WR7 summary"; isEnabled = false; setOnClickListener { exportRecord() }
        }
        root.addView(export)
        root.addView(Button(this).apply { text = "Back to PSE"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoGoParkEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                val history = LetsGoMysteryGiftHistoryReadOnly.read(bytes)
                val summary = history.records.firstOrNull { it.slot == selectedSlot + 1 }
                    ?: throw IllegalArgumentException("The selected WR7 history slot is empty.")
                summary to LetsGoMysteryGiftHistoryReadOnly.recordBytes(bytes, selectedSlot)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (summary, bytes) ->
                    record = summary; rawRecord = bytes
                    status.text = "${summary.giftTypeName} • Card #${summary.cardId} • History slot ${summary.slot}\n" +
                        "Recorded UTC: ${summary.utcTimestamp ?: "Outside supported Instant range"}\n" +
                        "All 21 native block CRCs passed.\nSource SHA-256: $expectedHash"
                }.onFailure { status.text = it.message ?: "The WR7 receipt summary could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        export.isEnabled = !value && rawRecord != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        val input = contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        return LetsGoGoParkEditing.readDocumentBytes(input)
    }

    private fun exportRecord() {
        if (busy || rawRecord == null) return
        val summary = record ?: return
        setBusy(true)
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, "Lets-Go-WR7-card-${summary.cardId}-slot-${summary.slot}.wr7")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }, exportRequest)
        } catch (failure: Exception) {
            status.text = failure.message ?: "The WR7 export picker could not be opened."; setBusy(false)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != exportRequest) return
        val uri = data?.data
        val expected = rawRecord?.copyOf()
        if (resultCode != RESULT_OK || uri == null || expected == null) {
            status.text = "WR7 export cancelled. The source save was not changed."; setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the save. Nothing was exported."; setBusy(false); return
        }
        setBusy(true); status.text = "Exporting and reopening the native WR7 summary…"
        worker.execute {
            var started = false
            val result = runCatching {
                require(LetsGoGoParkEditing.sha256(readSource()) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                require(readWr7(uri).isEmpty()) { "Choose a new empty document; existing files cannot be replaced." }
                started = true
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: error("The WR7 document is not writable.")
                output.use { it.write(expected); it.flush() }
                require(readWr7(uri).contentEquals(expected)) {
                    "The exported WR7 document differs from the selected native summary."
                }
                require(LetsGoGoParkEditing.sha256(readSource()) == expectedHash) {
                    "The source changed during export."
                }
            }.recoverCatching { failure ->
                val removed = started && runCatching {
                    DocumentsContract.deleteDocument(contentResolver, uri)
                }.getOrDefault(false)
                throw IllegalStateException((failure.message ?: "WR7 export failed.") +
                    if (!started) " Nothing was exported."
                    else if (removed) " The failed export was removed."
                    else " The failed export could not be removed; do not use it.", failure)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = result.fold(
                    onSuccess = { "Native WR7 summary exported and reopened successfully (${expected.size} bytes). The source save is unchanged." },
                    onFailure = { it.message ?: "The WR7 export could not be verified." }
                )
                setBusy(false)
            }
        }
    }

    private fun readWr7(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The WR7 document cannot be read.")
        return input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(256)
            while (true) {
                val remaining = 0x141 - output.size()
                val count = it.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                if (count == 0) {
                    val single = it.read()
                    if (single < 0) break
                    output.write(single)
                } else output.write(buffer, 0, count)
                require(output.size() <= 0x140) { "The WR7 document exceeds 0x140 bytes." }
            }
            output.toByteArray()
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
