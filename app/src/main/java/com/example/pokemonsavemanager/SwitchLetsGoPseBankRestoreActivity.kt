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

private data class PreparedLetsGoBank(
    val nativeRecord: ByteArray,
    val sourceSha256: String,
    val effects: List<String>,
    val converted: Boolean
)

private data class PreparedLetsGoRestore(
    val plan: LetsGoPseBankRestoreCopyPlan,
    val bank: PreparedLetsGoBank,
    val version: LetsGoVersion
)

/** Restores one reviewed native or explicitly Legalised PSE Bank entry to a new Let's Go save copy. */
class SwitchLetsGoPseBankRestoreActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2148
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var bankEntryName: String? = null
    private var expectedBankSourceHash: String? = null
    private var legalise = false
    private var conversionEffects = emptyList<String>()
    private var bankSourceSha256: String? = null
    private var destinationVersion: LetsGoVersion? = null
    private var readyPlan: LetsGoPseBankRestoreCopyPlan? = null
    private var pendingPlan: LetsGoPseBankRestoreCopyPlan? = null
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
        expectedBankSourceHash = intent?.getStringExtra("pse.bank.sha256")
        legalise = intent?.getBooleanExtra("pse.bank.legalise", false) == true
        if (sourceUri == null || expectedHash.isNullOrBlank() || bankEntryName.isNullOrBlank()) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Restore to Pokémon Boxes"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE restores one checksum-valid PB7 to the next unused native storage record in a new save copy. " +
                "Same-format Let's Go PB7 records remain exact. Eligible Legends: Z-A PA9 records require an explicit Legalise review before conversion to title-specific PB7. " +
                "Eggs, save-bound partner forms, unsupported species/forms and destination-invalid moves remain protected."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source and PSE Bank entry…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        review = Button(this).apply {
            text = "Review restore to new copy"
            isEnabled = false
            setOnClickListener { readyPlan?.let { showReview(it) } }
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
                require(LetsGoPseBankRestoreEditing.sha256(source) == hash) {
                    "The source changed. Reopen it from PSE."
                }
                val verified = LetsGoSaveValidation.inspect(source) as? LetsGoValidation.Verified
                    ?: error("A validated native Let's Go destination is required.")
                val bank = prepareBankRecord(verified.version)
                PreparedLetsGoRestore(
                    LetsGoPseBankRestoreEditing.stage(source, hash, bank.nativeRecord),
                    bank,
                    verified.version
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { prepared ->
                    val plan = prepared.plan
                    readyPlan = plan
                    conversionEffects = prepared.bank.effects
                    bankSourceSha256 = prepared.bank.sourceSha256
                    destinationVersion = prepared.version
                    val p = plan.pokemon
                    status.text = "${prepared.version.displayName} source and Bank integrity verified\n" +
                        "${LetsGoSpeciesNames.name(p.species)} (#${p.species}) • Level ${p.cachedLevel}\n" +
                        "Destination: storage slot ${plan.storageIndex + 1}\n" +
                        "Source SHA-256: ${plan.sourceSha256}\n" +
                        "Bank source SHA-256: ${prepared.bank.sourceSha256}\n" +
                        (if (prepared.bank.converted) "Prepared PB7 SHA-256: ${plan.bankSha256}"
                        else "Native PB7 SHA-256: ${plan.bankSha256}")
                }.onFailure { status.text = it.message ?: "The PSE Bank restore could not be prepared safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        review.isEnabled = !value && readyPlan != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LetsGoPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPseBankRestoreEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun bankFile(): File {
        val name = bankEntryName ?: error("Reopen PSE Bank.")
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = File(directory, name).canonicalFile
        val native = entry.name.startsWith("switch_lgpe_") && entry.extension.equals("pb7", true)
        val swordShield = entry.name.startsWith("switch_swsh_") && entry.extension.equals("pk8", true)
        val legendsZa = entry.name.startsWith("switch_za_") && entry.extension.equals("pa9", true)
        require(entry.parentFile == directory && entry.isFile && (native || swordShield || legendsZa)) {
            "This is not a current Let's Go-compatible PSE Bank entry."
        }
        return entry
    }

    private fun prepareBankRecord(version: LetsGoVersion): PreparedLetsGoBank {
        val entry = bankFile()
        val bytes = entry.readBytes()
        val hash = LetsGoPseBankRestoreEditing.sha256(bytes)
        val reviewed = expectedBankSourceHash
        require(!reviewed.isNullOrBlank()) {
            "The reviewed PSE Bank identity is missing. Reopen PSE Bank."
        }
        require(hash == reviewed) {
            "The PSE Bank entry changed after review. Reopen PSE Bank."
        }
        return if (entry.extension.equals("pb7", true)) {
            require(entry.name == "switch_lgpe_${hash}.pb7") {
                "The PSE Bank filename no longer matches its native PB7 bytes."
            }
            LetsGoPseBankRestoreEditing.inspectBankRecord(bytes)
            PreparedLetsGoBank(bytes, hash, emptyList(), false)
        } else if (entry.extension.equals("pk8", true)) {
            require(legalise) {
                "Sword/Shield PK8 requires the reviewed Legalise choice from the Let's Go Bank screen."
            }
            require(entry.name == "switch_swsh_${hash}.pk8") {
                "The PSE Bank filename no longer matches its native PK8 bytes."
            }
            val converted = SwordShieldToLetsGoPb7.review(bytes, version)
            LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
            PreparedLetsGoBank(converted.nativeRecord, hash, converted.effects, true)
        } else {
            require(legalise) {
                "Legends: Z-A PA9 requires the reviewed Legalise choice from the Let's Go Bank screen."
            }
            require(entry.name == "switch_za_${hash}.pa9") {
                "The PSE Bank filename no longer matches its native PA9 bytes."
            }
            val converted = LegendsZaToLetsGoPb7.review(bytes, version)
            LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
            PreparedLetsGoBank(converted.nativeRecord, hash, converted.effects, true)
        }
    }

    private fun readPreparedBankRecord(): ByteArray {
        val version = destinationVersion ?: error("Reopen the destination from PSE Bank.")
        return prepareBankRecord(version).nativeRecord
    }

    private fun showReview(plan: LetsGoPseBankRestoreCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val p = plan.pokemon
        val moves = p.moves.filter { it.id != 0 }.joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        val effects = if (conversionEffects.isEmpty()) "" else buildString {
            append("\n\nAccepted Legalise effects\n• ")
            append(conversionEffects.joinToString("\n• "))
        }
        val title = if (conversionEffects.isEmpty())
            "Restore this Bank Pokémon to a new copy?"
        else "Legalise and restore this Pokémon?"
        AlertDialog.Builder(this).setTitle(title)
            .setMessage(
                "${LetsGoSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form} • Level ${p.cachedLevel}\n" +
                    "Nickname: ${p.nickname.ifBlank { "None" }} • Shiny: ${if (p.shiny) "Yes" else "No"}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n\n" +
                    "Destination: native storage slot ${plan.storageIndex + 1}\n" +
                    "Allowed byte scope: one previously empty 260-byte PB7 record at 0x${plan.recordOffset.toString(16).uppercase()}, " +
                    "the two-byte storage count, and block 8 and block 9 CRCs. Existing Pokémon, party pointers, partner pointer, and every unrelated byte remain exact. " +
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
            .putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name)
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
                    readBankRecord = { readPreparedBankRecord() },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, backup.file, backup.sha256
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
                        "PSE Bank restore saved and reopened successfully.\n" +
                            "Storage slot ${plan.storageIndex + 1} • SHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source and Bank entry unchanged.\n" +
                            "Bank source SHA-256: ${bankSourceSha256 ?: "unavailable"}"
                    },
                    onFailure = {
                        (it.message ?: "The PSE Bank restore copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                readyPlan = null
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
