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

private data class LegendsZaBankPreparation(
    val pokemon: LegendsZaPokemonRead,
    val destinations: List<LegendsZaPseBankDestination>,
    val sourceBankHash: String,
    val nativeRecordHash: String,
    val effects: List<String>,
    val registerCaught: Boolean
)

/** Restores one native PA9 or explicitly reviewed cross-generation conversion into a new Z-A save copy. */
class SwitchLegendsZaPseBankRestoreActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2153
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var bankEntryName: String? = null
    private var expectedBankSourceHash: String? = null
    private var conversionEffects: List<String> = emptyList()
    private var registerGen7Caught = false
    private var readyDestinations: List<LegendsZaPseBankDestination> = emptyList()
    private var readyPlan: LegendsZaPseBankRestoreCopyPlan? = null
    private var pendingPlan: LegendsZaPseBankRestoreCopyPlan? = null
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
        root.addView(TextView(this).apply { text = "Legends: Z-A • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Restore to Pokémon Boxes"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE restores one checksum-valid native PA9 to an empty PC-box slot you choose in a new save copy. " +
                "Native PA9 entries remain byte-exact. Eligible Gen 1 PSE1, Generation 7 PK7, Sword/Shield PK8, BDSP PB8, Legends: Arceus PA8, and Scarlet/Violet PK9 entries require an explicit Legalise review that lists every retained, reset, or omitted field. " +
                "Unsupported species, forms, items, moves, limits, or text remain blocked; these PSE conversions do not certify encounter, ability, Mega, or moveset legality."
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
                require(LegendsZaPseBankRestoreEditing.sha256(source) == hash) {
                    "The source changed. Reopen it from PSE."
                }
                val bankSource = readBankSource()
                val bankSourceHash = LegendsZaPseBankRestoreEditing.sha256(bankSource)
                expectedBankSourceHash = bankSourceHash
                val prepared = prepareBankRecord(bankSource, source)
                val pokemon = LegendsZaPseBankRestoreEditing.inspectBankRecord(prepared.first)
                if (prepared.second.isNotEmpty()) require(
                    pokemon.species in LegendsZaPokedexReadOnly.supportedSpecies(source)
                ) {
                    "Species #${pokemon.species} is not present in this Legends: Z-A save's native Pokédex table."
                }
                val destinations = LegendsZaPseBankRestoreEditing.emptyDestinations(source)
                require(destinations.isNotEmpty()) {
                    "All 960 native Legends: Z-A PC-box slots are occupied."
                }
                LegendsZaBankPreparation(
                    pokemon, destinations, bankSourceHash,
                    LegendsZaPseBankRestoreEditing.sha256(prepared.first), prepared.second,
                    bankFile().extension.equals("pse1", true) ||
                        bankFile().extension.equals("pse2", true) ||
                        bankFile().extension.equals("pk7", true)
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { prepared ->
                    readyDestinations = prepared.destinations
                    conversionEffects = prepared.effects
                    registerGen7Caught = prepared.registerCaught
                    val p = prepared.pokemon
                    status.text = "Native source and Bank integrity verified\n" +
                        "${ScarletVioletSpeciesNames.name(p.species)} (#${p.species}) • Level ${p.cachedLevel}\n" +
                        "${prepared.destinations.size} empty native destination slots available\n" +
                        "Bank source SHA-256: ${prepared.sourceBankHash}\nPrepared PA9 SHA-256: ${prepared.nativeRecordHash}" +
                        if (prepared.effects.isEmpty()) "" else "\n\n${conversionSourceName()} -> Legends: Z-A Legalise effects\n• " +
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
        AlertDialog.Builder(this).setTitle("Choose Legends: Z-A Box")
            .setItems(labels) { _, index ->
                val box = boxes.keys.elementAt(index)
                showDestinationSlotPicker(box, boxes.getValue(box))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDestinationSlotPicker(
        box: Int,
        destinations: List<LegendsZaPseBankDestination>
    ) {
        if (busy) return
        val labels = destinations.map { "Slot ${it.position}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Box $box • choose empty slot")
            .setItems(labels) { _, index -> prepareDestinationReview(destinations[index]) }
            .setNegativeButton("Back") { _, _ -> showDestinationBoxPicker() }
            .show()
    }

    private fun prepareDestinationReview(destination: LegendsZaPseBankDestination) {
        if (busy || destination !in readyDestinations) return
        setBusy(true)
        status.text = "Binding Box ${destination.box}, slot ${destination.position} to the restore review…"
        worker.execute {
            val result = runCatching {
                val source = readSource()
                val hash = expectedHash ?: error("Reopen the source from PSE.")
                val bank = readBankRecord(source)
                LegendsZaPseBankRestoreEditing.stage(
                    source, hash, bank, destination.box, destination.position,
                    registerCaught = registerGen7Caught
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
        return LegendsZaPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsZaPseBankRestoreEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun conversionSourceName(): String = when {
        bankEntryName?.endsWith(".pse1", true) == true -> "Generation 1"
        bankEntryName?.endsWith(".pse2", true) == true -> "Generation 2"
        bankEntryName?.endsWith(".pk7", true) == true -> "Generation 7"
        bankEntryName?.endsWith(".pk9", true) == true -> "Scarlet/Violet"
        bankEntryName?.endsWith(".pk8", true) == true -> "Sword/Shield"
        bankEntryName?.endsWith(".pb8", true) == true -> "BDSP"
        bankEntryName?.endsWith(".pa8", true) == true -> "Legends: Arceus"
        else -> "Native PA9"
    }

    private fun bankFile(): File {
        val name = bankEntryName ?: error("Reopen PSE Bank.")
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = File(directory, name).canonicalFile
        val native = entry.name.startsWith("switch_za_") && entry.extension.equals("pa9", true)
        val gen1 = entry.name.startsWith("pse1_") && entry.extension.equals("pse1", true)
        val gen2 = entry.extension.equals("pse2", true)
        val gen7 = entry.extension.equals("pk7", true)
        val scarletViolet = entry.name.startsWith("switch_sv_") && entry.extension.equals("pk9", true)
        val swordShield = entry.name.startsWith("switch_swsh_") && entry.extension.equals("pk8", true)
        val bdsp = entry.name.startsWith("switch_bdsp_") && entry.extension.equals("pb8", true)
        val legendsArceus = entry.name.startsWith("switch_pla_") && entry.extension.equals("pa8", true)
        require(entry.parentFile == directory && entry.isFile &&
            (native || gen1 || gen2 || gen7 || swordShield || bdsp || legendsArceus || scarletViolet)) {
            "This is not a current Legends: Z-A, Gen 1, Gen 2, Generation 7, Sword/Shield, BDSP, Legends: Arceus, or Scarlet/Violet PSE Bank entry."
        }
        if (gen1 || gen2 || gen7 || swordShield || bdsp || legendsArceus || scarletViolet) require(intent.getBooleanExtra("pse.bank.legalise", false)) {
            "Review and explicitly choose the disclosed ${conversionSourceName()} Legalise conversion from PSE Bank."
        }
        return entry
    }

    private fun readBankSource(): ByteArray {
        val bytes = bankFile().readBytes()
        expectedBankSourceHash?.let {
            require(LegendsZaPseBankRestoreEditing.sha256(bytes) == it) {
                "The preserved PSE Bank source changed. Reopen PSE Bank."
            }
        }
        return bytes
    }

    private fun prepareBankRecord(
        source: ByteArray,
        destination: ByteArray
    ): Pair<ByteArray, List<String>> {
        val entry = bankFile()
        if (entry.extension.equals("pse1", true)) {
            val metadata = Gen1ToSwordShieldMetadata(
                intent.getIntExtra("pse.gen1.friendship", -1),
                intent.getIntExtra("pse.gen1.gender_rate", -2),
                intent.getIntExtra("pse.gen1.ability_id", 0),
                intent.getIntExtra("pse.gen1.ability_number", 0)
            )
            val converted = Gen1ToLegendsZaPa9.review(
                source, LegendsZaTrainerReadOnly.read(destination), metadata
            )
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pse2", true)) {
            val metadata = Gen1ToSwordShieldMetadata(
                intent.getIntExtra("pse.gen2.friendship", -1),
                intent.getIntExtra("pse.gen2.gender_rate", -2),
                intent.getIntExtra("pse.gen2.ability_id", 0),
                intent.getIntExtra("pse.gen2.ability_number", 0)
            )
            val converted = Gen2ToLegendsZaPa9.review(
                source, LegendsZaTrainerReadOnly.read(destination), metadata
            )
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pk7", true)) {
            val converted = Gen7ToLegendsZaPa9.review(source)
            return converted.nativeRecord to converted.effects
        }
        val hash = LegendsZaPseBankRestoreEditing.sha256(source)
        if (entry.extension.equals("pk8", true)) {
            require(entry.name == "switch_swsh_${hash}.pk8") {
                "The PSE Bank filename no longer matches its native PK8 bytes."
            }
            val converted = SwordShieldToLegendsZaPa9.review(source)
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pb8", true)) {
            require(entry.name == "switch_bdsp_${hash}.pb8") {
                "The PSE Bank filename no longer matches its native PB8 bytes."
            }
            val converted = BdspToLegendsZaPa9.review(source)
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pa8", true)) {
            require(entry.name == "switch_pla_${hash}.pa8") {
                "The PSE Bank filename no longer matches its native PA8 bytes."
            }
            val converted = LegendsArceusToLegendsZaPa9.review(source)
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pk9", true)) {
            require(entry.name == "switch_sv_${hash}.pk9") {
                "The PSE Bank filename no longer matches its native PK9 bytes."
            }
            val converted = ScarletVioletToLegendsZaPa9.review(source)
            return converted.nativeRecord to converted.effects
        }
        require(entry.name == "switch_za_${hash}.pa9") {
            "The PSE Bank filename no longer matches its native record bytes."
        }
        LegendsZaPseBankRestoreEditing.inspectBankRecord(source)
        return source to emptyList()
    }

    private fun readBankRecord(destinationSource: ByteArray): ByteArray {
        val prepared = prepareBankRecord(readBankSource(), destinationSource)
        if (prepared.second.isNotEmpty()) {
            val pokemon = LegendsZaPseBankRestoreEditing.inspectBankRecord(prepared.first)
            require(pokemon.species in LegendsZaPokedexReadOnly.supportedSpecies(destinationSource)) {
                "Species #${pokemon.species} is not present in this Legends: Z-A save's native Pokédex table."
            }
        }
        return prepared.first
    }

    private fun showReview(plan: LegendsZaPseBankRestoreCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val p = plan.pokemon
        val moves = p.moves.filter { it.id != 0 }.joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        val effects = if (conversionEffects.isEmpty()) "" else
            "\n\nExplicit ${conversionSourceName()} Legalise effects\n• " + conversionEffects.joinToString("\n• ")
        val dexScope = when {
            !plan.caughtRegistrationReviewed -> ""
            plan.addedCaughtRegistration -> {
                val ranges = plan.dexAbsoluteRanges.joinToString { range ->
                    if (range.first == range.last) "0x${range.first.toString(16).uppercase()}"
                    else "0x${range.first.toString(16).uppercase()}–0x${range.last.toString(16).uppercase()}"
                }
                " plus Z-A Pokédex #${plan.dexNumber} (${plan.dexSection?.displayName}) native caught/default-form, " +
                    "gender, shiny when applicable, and English fields at $ranges"
            }
            else -> " while the complete existing Z-A caught registration remains exact"
        }
        AlertDialog.Builder(this).setTitle(
            if (conversionEffects.isEmpty()) "Restore this Bank Pokémon to a new copy?"
            else "Legalise ${conversionSourceName()} and restore to a new copy?"
        )
            .setMessage(
                "${ScarletVioletSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form} • Level ${p.cachedLevel}\n" +
                    "Nickname: ${p.nickname.ifBlank { "None" }} • Shiny: ${if (p.shiny) "Yes" else "No"} • Alpha: ${if (p.isAlpha) "Yes" else "No"}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n\n" +
                    "Destination: native Box ${plan.box}, slot ${plan.position}\n" +
                    "Allowed native byte scope: the 344-byte PA9 body of one previously empty PC-box slot at 0x${plan.absoluteOffset.toString(16).uppercase()}" +
                    dexScope + " plus the native SHA-256 footer. Mega, Alpha, display and unknown Pokédex fields, the target slot's 64-byte native footer, every existing PC-box slot, all party data, the save revision, and all unrelated bytes remain exact. " +
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
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ZA.name)
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
                    readBankRecord = { readBankRecord(readSource()) },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.LEGENDS_ZA, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ZA, backup.file, backup.sha256
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
                        else "${conversionSourceName()} -> Legends: Z-A PSE conversion saved and reopened successfully.\n") +
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
