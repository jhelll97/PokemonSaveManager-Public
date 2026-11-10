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

private data class ScarletVioletBankPreparation(
    val pokemon: ScarletVioletPokemonRead,
    val destinations: List<ScarletVioletPseBankDestination>,
    val sourceBankHash: String,
    val nativeRecordHash: String,
    val effects: List<String>,
    val registerCaught: Boolean
)

/** Restores one native PK9 or explicitly reviewed cross-generation conversion into a new save copy. */
class SwitchScarletVioletPseBankRestoreActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2152
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var bankEntryName: String? = null
    private var expectedBankSourceHash: String? = null
    private var conversionEffects: List<String> = emptyList()
    private var registerGen7Caught = false
    private var readyDestinations: List<ScarletVioletPseBankDestination> = emptyList()
    private var readyPlan: ScarletVioletPseBankRestoreCopyPlan? = null
    private var pendingPlan: ScarletVioletPseBankRestoreCopyPlan? = null
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
        if (sourceUri == null || expectedHash.isNullOrBlank() || bankEntryName.isNullOrBlank()) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Scarlet/Violet • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Restore to Pokémon Boxes"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "PSE restores one checksum-valid native PK9 to an empty PC-box slot you choose in a new save copy. " +
                "Native PK9 entries remain byte-exact. Eligible Gen 1 PSE1, Gen 2 PSE2, Gen 3 PK3, Gen 4 PK4, Gen 5 PK5, Gen 6 PK6, Generation 7 PK7, Sword/Shield PK8, BDSP PB8, Legends: Arceus PA8, and Legends: Z-A PA9 entries require an explicit Legalise review that lists every retained, reset, or omitted field. " +
                "Unsupported species, forms, items, moves, limits, or text remain blocked; this PSE conversion does not certify encounter, ability, Tera, or moveset legality."
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
                require(ScarletVioletPseBankRestoreEditing.sha256(source) == hash) {
                    "The source changed. Reopen it from PSE."
                }
                val bankSource = readBankSource()
                val bankSourceHash = ScarletVioletPseBankRestoreEditing.sha256(bankSource)
                expectedBankSourceHash = bankSourceHash
                val prepared = prepareBankRecord(bankSource, source)
                val pokemon = ScarletVioletPseBankRestoreEditing.inspectBankRecord(prepared.first)
                val bankExtension = bankFile().extension
                if (bankExtension.equals("pse1", true) ||
                    bankExtension.equals("pse2", true) ||
                    bankExtension.equals("pk3", true) ||
                    bankExtension.equals("pk4", true) ||
                    bankExtension.equals("pk5", true) ||
                    bankExtension.equals("pk7", true) ||
                    bankExtension.equals("pa9", true)) {
                    require(pokemon.species in ScarletVioletPokedexReadOnly.supportedSpecies(source)) {
                        "Species #${pokemon.species} is not present in this Scarlet/Violet save's native Pokédex table."
                    }
                }
                val destinations = ScarletVioletPseBankRestoreEditing.emptyDestinations(source)
                require(destinations.isNotEmpty()) {
                    "All 960 native Scarlet/Violet PC-box slots are occupied."
                }
                ScarletVioletBankPreparation(
                    pokemon, destinations, bankSourceHash,
                    ScarletVioletPseBankRestoreEditing.sha256(prepared.first), prepared.second,
                    bankExtension.equals("pse1", true) ||
                        bankExtension.equals("pse2", true) ||
                        bankExtension.equals("pk3", true) ||
                        bankExtension.equals("pk4", true) ||
                        bankExtension.equals("pk5", true) ||
                        bankExtension.equals("pk7", true)
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { prepared ->
                    readyDestinations = prepared.destinations
                    conversionEffects = prepared.effects
                    registerGen7Caught = prepared.registerCaught
                    status.text = "Native source and Bank integrity verified\n" +
                        "${ScarletVioletSpeciesNames.name(prepared.pokemon.species)} (#${prepared.pokemon.species}) • Level ${prepared.pokemon.cachedLevel}\n" +
                        "${prepared.destinations.size} empty native destination slots available\n" +
                        "Source SHA-256: ${expectedHash}\nBank source SHA-256: ${prepared.sourceBankHash}\n" +
                        "Prepared PK9 SHA-256: ${prepared.nativeRecordHash}" +
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
        AlertDialog.Builder(this).setTitle("Choose Scarlet/Violet PC Box")
            .setItems(labels) { _, index ->
                val box = boxes.keys.elementAt(index)
                showDestinationSlotPicker(box, boxes.getValue(box))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDestinationSlotPicker(
        box: Int,
        destinations: List<ScarletVioletPseBankDestination>
    ) {
        if (busy) return
        val labels = destinations.map { "Slot ${it.position}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Box $box • choose empty slot")
            .setItems(labels) { _, index -> prepareDestinationReview(destinations[index]) }
            .setNegativeButton("Back") { _, _ -> showDestinationBoxPicker() }
            .show()
    }

    private fun prepareDestinationReview(destination: ScarletVioletPseBankDestination) {
        if (busy || destination !in readyDestinations) return
        setBusy(true)
        status.text = "Binding Box ${destination.box}, slot ${destination.position} to the restore review…"
        worker.execute {
            val result = runCatching {
                val source = readSource()
                val hash = expectedHash ?: error("Reopen the source from PSE.")
                val bank = readBankRecord(source)
                ScarletVioletPseBankRestoreEditing.stage(
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
        return ScarletVioletPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = ScarletVioletPseBankRestoreEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun bankFile(): File {
        val name = bankEntryName ?: error("Reopen PSE Bank.")
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = File(directory, name).canonicalFile
        val native = entry.name.startsWith("switch_sv_") && entry.extension.equals("pk9", true)
        val gen1 = entry.name.startsWith("pse1_") && entry.extension.equals("pse1", true)
        val gen2 = entry.extension.equals("pse2", true)
        val gen3 = entry.extension.equals("pk3", true)
        val gen4 = entry.extension.equals("pk4", true)
        val gen5 = entry.extension.equals("pk5", true)
        val gen6 = entry.extension.equals("pk6", true)
        val gen7 = entry.extension.equals("pk7", true)
        val swordShield = entry.name.startsWith("switch_swsh_") && entry.extension.equals("pk8", true)
        val bdsp = entry.name.startsWith("switch_bdsp_") && entry.extension.equals("pb8", true)
        val legendsArceus = entry.name.startsWith("switch_pla_") && entry.extension.equals("pa8", true)
        val legendsZa = entry.name.startsWith("switch_za_") && entry.extension.equals("pa9", true)
        require(entry.parentFile == directory && entry.isFile &&
            (native || gen1 || gen2 || gen3 || gen4 || gen5 || gen6 || gen7 || swordShield || bdsp || legendsArceus || legendsZa)) {
            "This is not a current Scarlet/Violet, Gen 1, Gen 2, Gen 3, Gen 4, Gen 5, Gen 6, Generation 7, Sword/Shield, BDSP, Legends: Arceus, or Legends: Z-A PSE Bank entry."
        }
        if (gen1 || gen2 || gen3 || gen4 || gen5 || gen6 || gen7 || swordShield || bdsp || legendsArceus || legendsZa) require(intent.getBooleanExtra("pse.bank.legalise", false)) {
            "Review and explicitly choose the disclosed cross-generation Legalise conversion from PSE Bank."
        }
        return entry
    }

    private fun readBankSource(): ByteArray {
        val entry = bankFile()
        when {
            entry.extension.equals("pk7", true) -> {
                require(!expectedBankSourceHash.isNullOrBlank()) {
                    "The reviewed Gen 7 Bank hash is missing. Reopen PSE Bank."
                }
            }
            entry.name.startsWith("switch_za_") && entry.extension.equals("pa9", true) -> {
                require(!expectedBankSourceHash.isNullOrBlank()) {
                    "The reviewed Legends: Z-A Bank hash is missing. Reopen PSE Bank."
                }
            }
        }
        val bytes = entry.readBytes()
        expectedBankSourceHash?.let {
            require(ScarletVioletPseBankRestoreEditing.sha256(bytes) == it) {
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
            val converted = Gen1ToScarletVioletPk9.review(
                source, ScarletVioletTrainerReadOnly.read(destination), metadata
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
            val converted = Gen2ToScarletVioletPk9.review(
                source, ScarletVioletTrainerReadOnly.read(destination), metadata
            )
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pk3", true)) {
            val metadata = Gen1ToSwordShieldMetadata(
                intent.getIntExtra("pse.gen3.friendship", -1),
                intent.getIntExtra("pse.gen3.gender_rate", -2),
                intent.getIntExtra("pse.gen3.ability_id", 0),
                intent.getIntExtra("pse.gen3.ability_number", 0)
            )
            val converted = Gen3ToScarletVioletPk9.review(
                source, ScarletVioletTrainerReadOnly.read(destination), metadata
            )
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pk4", true)) {
            val metadata = Gen1ToSwordShieldMetadata(
                intent.getIntExtra("pse.gen4.friendship", -1),
                intent.getIntExtra("pse.gen4.gender_rate", -2),
                intent.getIntExtra("pse.gen4.ability_id", 0),
                intent.getIntExtra("pse.gen4.ability_number", 0)
            )
            val converted = Gen4ToScarletVioletPk9.review(
                source, ScarletVioletTrainerReadOnly.read(destination), metadata
            )
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pk5", true)) {
            val metadata = Gen1ToSwordShieldMetadata(
                intent.getIntExtra("pse.gen5.friendship", -1),
                intent.getIntExtra("pse.gen5.gender_rate", -2),
                intent.getIntExtra("pse.gen5.ability_id", 0),
                intent.getIntExtra("pse.gen5.ability_number", 0)
            )
            val converted = Gen5ToScarletVioletPk9.review(
                source, ScarletVioletTrainerReadOnly.read(destination), metadata
            )
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pk6", true)) {
            val metadata = Gen1ToSwordShieldMetadata(
                intent.getIntExtra("pse.gen6.friendship", -1),
                intent.getIntExtra("pse.gen6.gender_rate", -2),
                intent.getIntExtra("pse.gen6.ability_id", 0),
                intent.getIntExtra("pse.gen6.ability_number", 0)
            )
            val converted = Gen6ToScarletVioletPk9.review(
                source, ScarletVioletTrainerReadOnly.read(destination), metadata
            )
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pk7", true)) {
            val metadata = Gen1ToSwordShieldMetadata(
                intent.getIntExtra("pse.gen7.friendship", -1),
                intent.getIntExtra("pse.gen7.gender_rate", -2),
                intent.getIntExtra("pse.gen7.ability_id", 0),
                intent.getIntExtra("pse.gen7.ability_number", 0)
            )
            val converted = Gen7ToScarletVioletPk9.review(
                source, ScarletVioletTrainerReadOnly.read(destination), metadata
            )
            return converted.nativeRecord to converted.effects
        }
        val hash = ScarletVioletPseBankRestoreEditing.sha256(source)
        if (entry.extension.equals("pk8", true)) {
            require(entry.name == "switch_swsh_${hash}.pk8") {
                "The PSE Bank filename no longer matches its native PK8 bytes."
            }
            val converted = SwordShieldToScarletVioletPk9.review(source)
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pb8", true)) {
            require(entry.name == "switch_bdsp_${hash}.pb8") {
                "The PSE Bank filename no longer matches its native PB8 bytes."
            }
            val converted = BdspToScarletVioletPk9.review(source)
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pa8", true)) {
            require(entry.name == "switch_pla_${hash}.pa8") {
                "The PSE Bank filename no longer matches its native PA8 bytes."
            }
            val converted = LegendsArceusToScarletVioletPk9.review(source)
            return converted.nativeRecord to converted.effects
        }
        if (entry.extension.equals("pa9", true)) {
            require(entry.name == "switch_za_${hash}.pa9") {
                "The PSE Bank filename no longer matches its native PA9 bytes."
            }
            val converted = LegendsZaToScarletVioletPk9.review(source)
            return converted.nativeRecord to converted.effects
        }
        require(entry.name == "switch_sv_${hash}.pk9") {
            "The PSE Bank filename no longer matches its native record bytes."
        }
        ScarletVioletPseBankRestoreEditing.inspectBankRecord(source)
        return source to emptyList()
    }

    private fun readBankRecord(destinationSource: ByteArray): ByteArray {
        val record = prepareBankRecord(readBankSource(), destinationSource).first
        val extension = bankFile().extension
        if (extension.equals("pse1", true) ||
            extension.equals("pse2", true) ||
            extension.equals("pk3", true) ||
            extension.equals("pk4", true) ||
            extension.equals("pk5", true) ||
            extension.equals("pk6", true) ||
            extension.equals("pk7", true) ||
            extension.equals("pa9", true)
        ) {
            val pokemon = ScarletVioletPseBankRestoreEditing.inspectBankRecord(record)
            require(pokemon.species in ScarletVioletPokedexReadOnly.supportedSpecies(destinationSource)) {
                "Species #${pokemon.species} is not present in this Scarlet/Violet save's native Pokédex table."
            }
        }
        return record
    }

    private fun showReview(plan: ScarletVioletPseBankRestoreCopyPlan) {
        if (busy || plan !== readyPlan) return
        setBusy(true)
        var dispatched = false
        val p = plan.pokemon
        val moves = p.moves.filter { it.id != 0 }.joinToString { "#${it.id} (PP ${it.pp})" }.ifBlank { "None" }
        val effects = if (conversionEffects.isEmpty()) "" else
            "\n\nExplicit PSE conversion Legalise effects\n• " + conversionEffects.joinToString("\n• ")
        val dexScope = when {
            plan.addedCaughtRegistration -> {
                val ranges = plan.dexAbsoluteRanges.joinToString { range ->
                    if (range.first == range.last) "0x${range.first.toString(16).uppercase()}"
                    else "0x${range.first.toString(16).uppercase()}-0x${range.last.toString(16).uppercase()}"
                }
                " The same reviewed transaction registers form 0 as caught with the converted gender, " +
                    "shiny state, and English language in the native Pokédex fields at ${ranges}. " +
                    "Display selections, unrelated forms, regional entries, and unknown bits remain exact."
            }
            registerGen7Caught ->
                " This species already has the complete native caught, form, gender, shiny, and English-language registration, so those fields remain exact."
            else -> ""
        }
        AlertDialog.Builder(this).setTitle(
            if (conversionEffects.isEmpty()) "Restore this Bank Pokémon to a new copy?"
            else "Legalise the converted record and restore to a new copy?"
        )
            .setMessage(
                "${ScarletVioletSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form} • Level ${p.cachedLevel}\n" +
                    "Nickname: ${p.nickname.ifBlank { "None" }} • Shiny: ${if (p.shiny) "Yes" else "No"}\n" +
                    "OT ${p.originalTrainer} • TID ${p.tid} / SID ${p.sid}\nMoves: $moves\n\n" +
                    "Destination: native Box ${plan.box}, slot ${plan.position}\n" +
                    "Allowed native byte scope: one previously empty 344-byte PK9 PC-box slot at 0x${plan.absoluteOffset.toString(16).uppercase()}, " +
                    "the reviewed native Pokédex fields when required, and the native SHA-256 footer. Every existing PC-box slot, all party data, ride Pokémon data, and all unrelated bytes remain exact." +
                    dexScope + " PSE verifies the unchanged source and Bank entry, retains a verified recovery backup, reopens the persisted copy, and removes a failed copy when possible. " +
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
            .putExtra("pse.switch.family", SwitchGameFamily.SCARLET_VIOLET.name)
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
                    readBankRecord = {
                        val source = readSource()
                        readBankRecord(source)
                    },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.SCARLET_VIOLET, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.SCARLET_VIOLET, backup.file, backup.sha256
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
