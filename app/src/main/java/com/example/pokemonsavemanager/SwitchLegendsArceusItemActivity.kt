package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/** Explicit Legends: Arceus Items editor. It writes a reviewed new copy and never replaces the source. */
class SwitchLegendsArceusItemActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2106
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var pocketIndex = -1
    private var slot = -1
    private var addMode = false
    private var opened: ByteArray? = null
    private var pendingPlan: LegendsArceusItemCopyPlan? = null
    private var busy = false
    private var verifiedCopy: Pair<Uri, String>? = null
    private var allowedIds: List<Int> = emptyList()
    private lateinit var status: TextView
    private lateinit var action: Spinner
    private lateinit var itemPicker: Spinner
    private lateinit var quantity: EditText
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        pocketIndex = intent?.getIntExtra("pse.switch.item.pocket", -1) ?: -1
        slot = intent?.getIntExtra("pse.switch.item.slot", -1) ?: -1
        addMode = intent?.getBooleanExtra("pse.switch.item.add", false) == true
        if (sourceUri == null || expectedHash.isNullOrBlank() || pocketIndex !in 0..3 || slot < 0) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Items"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "Review one native item-slot change and save a new copy. PSE retains a verified recovery backup, reopens the copy, and removes a failed copy when possible. The selected source stays unchanged."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)

        action = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SwitchLegendsArceusItemActivity,
                android.R.layout.simple_spinner_dropdown_item,
                if (addMode) listOf("Add item") else listOf("Change quantity", "Remove item")
            )
        }
        root.addView(action)

        itemPicker = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SwitchLegendsArceusItemActivity,
                android.R.layout.simple_spinner_dropdown_item,
                emptyList<String>()
            )
            visibility = if (addMode) android.view.View.VISIBLE else android.view.View.GONE
        }
        root.addView(itemPicker)

        quantity = EditText(this).apply {
            hint = "Quantity"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(quantity)

        review = Button(this).apply {
            text = "Review and save new copy"
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Items"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusItemEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val inventory = LegendsArceusInventoryReadOnly.read(bytes)
                val pocket = inventory.pockets.getOrNull(pocketIndex)
                    ?: error("Reopen the inventory pocket from PSE.")
                val entry = pocket.items.getOrNull(slot)
                    ?: error("Reopen the item slot from PSE.")
                require(entry.withinActiveCapacity) { "Slots beyond the active native capacity remain read-only." }
                if (addMode) {
                    require(pocket.allowsCataloguedAdd && !entry.isRecorded) {
                        "This native slot is unavailable for adding an item."
                    }
                    val present = pocket.recordedItems.map { it.itemId }.toSet()
                    allowedIds = LegendsArceusItemNames.cataloguedItemIds().filter { it !in present }
                    require(allowedIds.isNotEmpty()) { "This pocket has no unused catalogued items." }
                } else require(entry.isRecorded && entry.count > 0 &&
                    LegendsArceusItemNames.isCatalogued(entry.itemId)) {
                    "This saved record is not a catalogued editable item."
                }
                Triple(bytes, pocket, entry)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, pocket, entry) ->
                    opened = bytes
                    if (addMode) {
                        itemPicker.adapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_spinner_dropdown_item,
                            allowedIds.map { "${LegendsArceusItemNames.name(it)} (#$it)" }
                        )
                        quantity.setText("1")
                        status.text = "${pocket.name} • Empty native slot ${entry.slot + 1}\n${allowedIds.size} unused catalogued items\nSource SHA-256: ${LegendsArceusItemEditing.sha256(bytes)}\nChoose an item and quantity."
                    } else {
                        quantity.setText(entry.count.toString())
                        status.text = "${pocket.name} • Slot ${entry.slot + 1}\n${entry.itemName} (#${entry.itemId}) ×${entry.count}\nReference maximum: ${pocket.referenceMaxCount}\nSource SHA-256: ${LegendsArceusItemEditing.sha256(bytes)}"
                    }
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        action.isEnabled = !value && ready && !addMode
        itemPicker.isEnabled = !value && ready && addMode && allowedIds.isNotEmpty()
        quantity.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        try {
            startActivity(
                Intent(this, SwitchReadOnlyActivity::class.java)
                    .setData(uri)
                    .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
                    .putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.section", "inventory")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The verified copy could not be opened for inspection."
        }
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return LegendsArceusItemEditing.readDocumentBytes(input)
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val mode = when {
            addMode -> LegendsArceusItemEditMode.ADD
            action.selectedItemPosition == 0 -> LegendsArceusItemEditMode.QUANTITY
            else -> LegendsArceusItemEditMode.REMOVE
        }
        val count = if (mode == LegendsArceusItemEditMode.REMOVE) null else quantity.text.toString().toIntOrNull()
        val selectedItemId = if (mode == LegendsArceusItemEditMode.ADD)
            allowedIds.getOrNull(itemPicker.selectedItemPosition) else null
        val plan = try {
            LegendsArceusItemEditing.stage(
                bytes, hash, pocketIndex, slot, mode, selectedItemId, count
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested item change is unavailable."
            return
        }

        setBusy(true)
        var dispatched = false
        AlertDialog.Builder(this)
            .setTitle("Save item change to a new copy?")
            .setMessage(
                "${plan.pocketName} • Native slot ${plan.slot + 1}\n" +
                    "${plan.beforeLabel} → ${plan.afterLabel}\n\n" +
                    "Allowed decoded scope: one four-byte item record in block 0x${plan.blockKey.toString(16).uppercase()} plus the native SHA-256 footer. " +
                    "PSE will verify a recovery backup and reopen the persisted copy. The independent favorite flag is preserved. Choose a NEW empty document; existing files and the selected source cannot be replaced."
            )
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true
                    pendingPlan = plan
                    verifiedCopy = null
                    inspectCopy.visibility = android.view.View.GONE
                    try {
                        startActivityForResult(
                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "application/octet-stream"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "SaveData.bin")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            },
                            createCopyRequest
                        )
                    } catch (failure: Exception) {
                        pendingPlan = null
                        setBusy(false)
                        status.text = failure.message ?: "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != createCopyRequest) return
        val plan = pendingPlan
        pendingPlan = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || plan == null) {
            status.text = "Save cancelled or review expired. Nothing was written."
            setBusy(false)
            return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the source. Nothing was written."
            setBusy(false)
            return
        }
        setBusy(true)
        status.text = "Saving and verifying a new copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching {
                plan.saveNewCopy(
                    readSource = { readSource() },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this,
                            SwitchGameFamily.LEGENDS_ARCEUS,
                            original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this,
                            SwitchGameFamily.LEGENDS_ARCEUS,
                            backup.file,
                            backup.sha256
                        )
                    },
                    readDestination = { readDocument(uri) },
                    writeDestination = { edited ->
                        val output = contentResolver.openOutputStream(uri, "wt")
                            ?: error("The new document is not writable.")
                        output.use { it.write(edited); it.flush() }
                    },
                    discardDestination = { DocumentsContract.deleteDocument(contentResolver, uri) }
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                verifiedCopy = result.getOrNull()?.let { uri to plan.editedSha256 }
                inspectCopy.visibility = if (verifiedCopy != null) android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Edited copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() {
        if (!busy) super.onBackPressed()
    }

    override fun onDestroy() {
        pendingPlan = null
        worker.shutdown()
        super.onDestroy()
    }
}
