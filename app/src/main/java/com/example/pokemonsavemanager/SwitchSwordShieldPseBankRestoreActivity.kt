package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

private data class SwordShieldBankPreparation(
    val pokemon: SwordShieldPokemonRead,
    val destinations: List<SwordShieldPseBankDestination>,
    val sourceBankHash: String,
    val nativeRecordHash: String,
    val effects: List<String>
)

/** Restores one native PK8 or explicitly reviewed PSE conversion into a new save copy. */
class SwitchSwordShieldPseBankRestoreActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2149
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var bankEntryName: String? = null
    private var expectedBankSourceHash: String? = null
    private var conversionEffects: List<String> = emptyList()
    private var readyDestinations: List<SwordShieldPseBankDestination> = emptyList()
    private var readyPlan: SwordShieldPseBankRestoreCopyPlan? = null
    private var pendingPlan: SwordShieldPseBankRestoreCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        bankEntryName = intent?.getStringExtra("pse.bank.entry")
        if (sourceUri == null || expectedHash.isNullOrBlank() || bankEntryName.isNullOrBlank()) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Sword/Shield • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Restore to Pokémon Boxes"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE restores one checksum-valid native PK8 to an empty PC-box slot you choose in a new save copy. " +
                "Native PK8 entries remain byte-exact. Eligible Gen 7 PK7 and BDSP PB8 entries require an explicit Legalise review that lists every retained, reset, or omitted field. " +
                "Unsupported species, forms, items, moves, limits, or text remain blocked; this PSE conversion does not certify encounter or moveset legality."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and PSE Bank entry…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        review = Button(this).apply {
            text = "Choose destination & review"
            isEnabled = false
            setOnClickListener { showDestinationBoxPicker() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to PSE Bank"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val source = readSource()
                val hash = expectedHash ?: error("Reopen the source from PSE.")
                require(SwordShieldPseBankRestoreEditing.sha256(source) == hash) {
                    "The source changed. Reopen it from PSE."
                }
                val bankSource = readBankSource()
                val bankSourceHash = SwordShieldPseBankRestoreEditing.sha256(bankSource)
                expectedBankSourceHash = bankSourceHash
                val prepared = prepareBankRecord(bankSource)
                val pokemon = SwordShieldPseBankRestoreEditing.inspectBankRecord(prepared.first)
                val destinations = SwordShieldPseBankRestoreEditing.emptyDestinations(source)
                require(destinations.isNotEmpty()) { "All 960 native Sword/Shield PC-box slots are occupied." }
                SwordShieldBankPreparation(
                    pokemon, destinations, bankSourceHash,
                    SwordShieldPseBankRestoreEditing.sha256(prepared.first), prepared.second
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { prepared ->
                    readyDestinations = prepared.destinations
                    conversionEffects = prepared.effects
                    status.text = "Native source and Bank integrity verified\n" +
                        "${SwordShieldSpeciesNames.name(prepared.pokemon.species)} (#${prepared.pokemon.species}) • Level ${prepared.pokemon.cachedLevel}\n" +
                        "${prepared.destinations.size} empty native destination slots available\n" +
                        "Source SHA-256: ${expectedHash}\nBank source SHA-256: ${prepared.sourceBankHash}\n" +
                        "Prepared PK8 SHA-256: ${prepared.nativeRecordHash}" +
                        if (prepared.effects.isEmpty()) "" else "\n\nPSE conversion Legalise effects\n• " +
                            prepared.effects.joinToString("\n• ")
                }.onFailure { status.text = it.message ?: "The PSE Bank restore could not be prepared safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        review.isEnabled = !value && readyDestinations.isNotEmpty()
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun showDestinationBoxPicker() {
        if (busy || readyDestinations.isEmpty()) return
        val boxes = readyDestinations.groupBy { it.box }.toSortedMap()
        val labels = boxes.map { (box, slots) ->
            "Box $box • ${slots.size} empty slot${if (slots.size == 1) "" else "s"}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose Sword/Shield PC Box")
            .setItems(labels) { _, index ->
                val box = boxes.keys.elementAt(index)
                showDestinationSlotPicker(box, boxes.getValue(box))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDestinationSlotPicker(
        box: Int,
        destinations: List<SwordShieldPseBankDestination>
    ) {
        if (busy) return
        val labels = destinations.map { "Slot ${it.position}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Box $box • choose empty slot")
            .setItems(labels) { _, index -> prepareDestinationReview(destinations[index]) }
            .setNegativeButton("Back") { _, _ -> showDestinationBoxPicker() }
            .show()
    }

    private fun prepareDestinationReview(destination: SwordShieldPseBankDestination) {
        if (busy || destination !in readyDestinations) return
        setBusy(true)
        status.text = "Binding Box ${destination.box}, slot ${destination.position} to the restore review…"
        worker.execute {
            val result = runCatching {
                val source = readSource()
                val hash = expectedHash ?: error("Reopen the source from PSE.")
                val bank = readBankRecord()
                SwordShieldPseBankRestoreEditing.stage(
                    source, hash, bank, destination.box, destination.position
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { plan ->
                    readyPlan = plan
                    setBusy(false)
                    showReview(plan)
                }.onFailure {
                    readyPlan = null
                    status.text = it.message ?: "The selected destination could not be prepared safely."
                    setBusy(false)
                }
            }
        }
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return SwordShieldPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = SwordShieldPseBankRestoreEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun bankFile(): File {
        val name = bankEntryName ?: error("Reopen PSE Bank.")
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = File(directory, name).canonicalFile
        val native = entry.name.startsWith("switch_swsh_") && entry.extension.equals("pk8", true)
        val gen7 = entry.extension.equals("pk7", true)
        val bdsp = entry.name.startsWith("switch_bdsp_") && entry.extension.equals("pb8", true)
        require(entry.parentFile == directory && entry.isFile && (native || gen7 || bdsp)) {
            "This is not a current Sword/Shield, Gen 7, or BDSP PSE Bank entry."
        }
        if (gen7 || bdsp) require(intent.getBooleanExtra("pse.bank.legalise", false)) {
            "Review and explicitly choose the disclosed PSE Legalise conversion from PSE Bank."
        }
        return entry
    }

    private fun readBankSource(): ByteArray {
        val bytes = bankFile().readBytes()
        expectedBankSourceHash?.let {
            require(SwordShieldPseBankRestoreEditing.sha256(bytes) == it) {
                "The preserved PSE Bank source changed. Reopen PSE Bank."
            }
        }
        return bytes
    }

    private fun prepareBankRecord(source: ByteArray): Pair<ByteArray, List<String>> {
        val entry = bankFile()
        if (entry.extension.equals("pk7", true)) {
            val converted = Gen7ToSwordShieldPk8.review(source)
            return converted.nativeRecord to converted.effects
        }
        val hash = SwordShieldPseBankRestoreEditing.sha256(source)
        if (entry.extension.equals("pb8", true)) {
            require(entry.name == "switch_bdsp_${hash}.pb8") {
                "The PSE Bank filename no longer matches its native PB8 bytes."
            }
            val converted = BdspToSwordShieldPk8.review(source)
            return converted.nativeRecord to converted.effects
        }
        require(entry.name == "switch_swsh_${hash}.pk8") {
            "The PSE Bank filename no longer matches its native record bytes."
        }
        SwordShieldPseBankRestoreEditing.inspectBankRecord(source)
        return source to emptyList()
    }

    private fun readBankRecord(): ByteArray = prepareBankRecord(readBankSource()).first

    private fun showReview(plan: SwordShieldPseBankRestoreCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val p = plan.pokemon
        val moves = p.moves.filter { it.id != 0 }.joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        val effects = if (conversionEffects.isEmpty()) "" else
            "\n\nExplicit PSE conversion Legalise effects\n• " + conversionEffects.joinToString("\n• ")
        AlertDialog.Builder(this).setTitle(
            if (conversionEffects.isEmpty()) "Restore this Bank Pokémon to a new copy?"
            else "Legalise the converted record and restore to a new copy?"
        )
            .setMessage(
                "${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form} • Level ${p.cachedLevel}\n" +
                    "Nickname: ${p.nickname.ifBlank { "None" }} • Shiny: ${if (p.shiny) "Yes" else "No"}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n\n" +
                    "Destination: native Box ${plan.box}, slot ${plan.position}\n" +
                    "Allowed encrypted byte scope: one previously empty 344-byte PK8 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} " +
                    "plus the native SHA-256 footer. Every existing PC-box slot, all party data, and all unrelated bytes remain exact. " +
                    "PSE verifies the unchanged source and Bank entry, retains a verified recovery backup, reopens the persisted copy, and removes a failed copy when possible. " +
                    "The Bank entry remains available. Choose a NEW empty document." + effects
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
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/octet-stream"
                            addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "SaveData.bin")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }, createCopyRequest)
                    } catch (failure: Exception) {
                        pendingPlan = null
                        setBusy(false)
                        status.text = failure.message ?: "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.SWORD_SHIELD.name)
            .putExtra("pse.switch.sha256", hash)
            .putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
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
                    readBankRecord = { readBankRecord() },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.SWORD_SHIELD, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.SWORD_SHIELD, backup.file, backup.sha256
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
                        (if (conversionEffects.isEmpty()) "PSE Bank restore saved and reopened successfully.\n"
                        else "PSE conversion saved and reopened successfully.\n") +
                            "Box ${plan.box}, slot ${plan.position} • SHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source and Bank entry unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The PSE Bank restore copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                readyPlan = null
                readyDestinations = emptyList()
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
