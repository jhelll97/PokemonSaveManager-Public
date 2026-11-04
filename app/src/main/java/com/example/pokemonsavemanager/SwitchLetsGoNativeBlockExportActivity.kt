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

/** Exports one exact numeric native block without modifying the source save. */
class SwitchLetsGoNativeBlockExportActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val exportRequest = 2124
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var selectedBlockId = -1
    private var block: LetsGoNativeBlockExport? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var export: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        selectedBlockId = intent?.getIntExtra("pse.lets_go.block.id", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() ||
            selectedBlockId !in LetsGoSaveValidation.blocks.indices) {
            finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Native Block Export"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Export one exact numeric native block. Undocumented blocks retain their numeric IDs and the source save remains unchanged."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        export = Button(this).apply {
            text = "Export exact native block"; isEnabled = false; setOnClickListener { exportBlock() }
        }
        root.addView(export)
        root.addView(Button(this).apply { text = "Back to PSE"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoGoParkEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                LetsGoBlockDirectoryReadOnly.export(bytes, selectedBlockId)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { selected ->
                    block = selected
                    val entry = selected.entry
                    status.text = "${selected.version.displayName} • Block ${entry.id.toString().padStart(2, '0')}\n" +
                        "Range: 0x${hex(entry.offset)}–0x${hex(entry.endOffset)} • ${entry.length} bytes\n" +
                        "Descriptor and CRC-16/ARC: PASS\nAll 21 native block CRCs passed.\n" +
                        "Source SHA-256: $expectedHash"
                }.onFailure { status.text = it.message ?: "The selected native block could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        export.isEnabled = !value && block != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        val input = contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        return LetsGoGoParkEditing.readDocumentBytes(input)
    }

    private fun exportBlock() {
        if (busy) return
        val selected = block ?: return
        setBusy(true)
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, "Lets-Go-native-block-${selected.entry.id.toString().padStart(2, '0')}.bin")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }, exportRequest)
        } catch (failure: Exception) {
            status.text = failure.message ?: "The native-block export picker could not be opened."; setBusy(false)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != exportRequest) return
        val uri = data?.data
        val selected = block
        val expected = selected?.bytes?.copyOf()
        if (resultCode != RESULT_OK || uri == null || selected == null || expected == null) {
            status.text = "Native-block export cancelled. The source save was not changed."; setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the save. Nothing was exported."; setBusy(false); return
        }
        setBusy(true); status.text = "Exporting and reopening native block ${selected.entry.id}…"
        worker.execute {
            var started = false
            val result = runCatching {
                require(LetsGoGoParkEditing.sha256(readSource()) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                require(readBlock(uri, expected.size).isEmpty()) {
                    "Choose a new empty document; existing files cannot be replaced."
                }
                started = true
                val output = contentResolver.openOutputStream(uri, "wt")
                    ?: error("The native-block document is not writable.")
                output.use { it.write(expected); it.flush() }
                require(readBlock(uri, expected.size).contentEquals(expected)) {
                    "The exported document differs from native block ${selected.entry.id}."
                }
                require(LetsGoGoParkEditing.sha256(readSource()) == expectedHash) {
                    "The source changed during export."
                }
            }.recoverCatching { failure ->
                val removed = started && runCatching {
                    DocumentsContract.deleteDocument(contentResolver, uri)
                }.getOrDefault(false)
                throw IllegalStateException((failure.message ?: "Native-block export failed.") +
                    if (!started) " Nothing was exported."
                    else if (removed) " The failed export was removed."
                    else " The failed export could not be removed; do not use it.", failure)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = result.fold(
                    onSuccess = {
                        "Native block ${selected.entry.id} exported and reopened successfully " +
                            "(${expected.size} bytes). The source save is unchanged."
                    },
                    onFailure = { it.message ?: "The native-block export could not be verified." }
                )
                setBusy(false)
            }
        }
    }

    private fun readBlock(uri: Uri, expectedLength: Int): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The native-block document cannot be read.")
        return input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val remaining = expectedLength + 1 - output.size()
                val count = it.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                if (count == 0) {
                    val single = it.read()
                    if (single < 0) break
                    output.write(single)
                } else output.write(buffer, 0, count)
                require(output.size() <= expectedLength) {
                    "The native-block document exceeds the selected block length."
                }
            }
            output.toByteArray()
        }
    }

    private fun hex(value: Int): String = value.toString(16).uppercase().padStart(5, '0')

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
