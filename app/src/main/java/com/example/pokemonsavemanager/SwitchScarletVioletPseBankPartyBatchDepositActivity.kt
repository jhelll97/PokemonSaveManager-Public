package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.security.MessageDigest
import java.util.concurrent.Executors

private data class ScarletVioletPartyBatchCandidate(
    val position: Int,
    val label: String,
    val record: ByteArray,
    val bankName: String
)

/** Copies a reviewed selection of exact native PK9 party records into content-addressed PSE Bank entries. */
class SwitchScarletVioletPseBankPartyBatchDepositActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var candidates: List<ScarletVioletPartyBatchCandidate> = emptyList()
    private var busy = false
    private lateinit var status: TextView
    private lateinit var choose: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        if (sourceUri == null || expectedHash.isNullOrBlank()) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Scarlet/Violet • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Copy selected native Party PK9"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Choose several readable native Pokémon from the current Party. PSE reopens the exact save before copying, verifies every complete 344-byte PK9, and stores each content-addressed entry byte-for-byte. Party order, party count, every PC slot, the ride Pokémon and the Scarlet/Violet save remain unchanged."
            textSize = 16f
        })
        status = TextView(this).apply { text = "Reopening and validating the selected save…"; textSize = 16f }
        root.addView(status)
        choose = Button(this).apply {
            text = "Choose Pokémon"
            isEnabled = false
            setOnClickListener { showSelection() }
        }
        root.addView(choose)
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        loadCandidates()
    }

    private fun loadCandidates() {
        setBusy(true)
        worker.execute {
            val result = runCatching { candidatesFrom(readVerifiedSource()) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    candidates = it
                    choose.text = if (it.isEmpty()) "Choose Pokémon" else "Choose from ${it.size} Pokémon"
                    status.text = if (it.isEmpty())
                        "The current Party has no readable native Pokémon available to copy. Empty and unreadable slots remain untouched."
                    else "${it.size} readable native Pokémon available in the current Party. Empty and unreadable slots are excluded."
                }.onFailure {
                    status.text = it.message ?: "The selected Scarlet/Violet save could not be validated."
                }
                setBusy(false)
            }
        }
    }

    private fun showSelection() {
        if (busy || candidates.isEmpty()) return
        val selected = BooleanArray(candidates.size)
        var bulkButton: Button? = null
        var reviewButton: Button? = null
        fun refreshSelectionActions() {
            bulkButton?.text = if (selected.all { it }) "Clear all" else "Select all"
            reviewButton?.text = "Review (${selected.count { it }})"
            reviewButton?.isEnabled = selected.any { it }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose 1-${candidates.size} native Party PK9 records")
            .setMultiChoiceItems(candidates.map { it.label }.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
                refreshSelectionActions()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Select all", null)
            .setPositiveButton("Review (0)", null)
            .create()
        dialog.setOnShowListener {
            bulkButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            reviewButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            refreshSelectionActions()
            bulkButton?.setOnClickListener {
                val check = selected.any { !it }
                selected.indices.forEach { index ->
                    selected[index] = check
                    dialog.listView.setItemChecked(index, check)
                }
                refreshSelectionActions()
            }
            reviewButton?.setOnClickListener {
                val chosen = candidates.filterIndexed { index, _ -> selected[index] }
                if (chosen.isEmpty()) {
                    status.text = "Choose at least one Pokémon. Nothing was copied."
                } else {
                    dialog.dismiss()
                    review(chosen)
                }
            }
        }
        dialog.show()
    }

    private fun review(chosen: List<ScarletVioletPartyBatchCandidate>) {
        if (busy) return
        setBusy(true)
        worker.execute {
            val result = runCatching {
                val current = candidatesFrom(readVerifiedSource())
                chosen.map { reviewed ->
                    current.firstOrNull { it.position == reviewed.position && it.record.contentEquals(reviewed.record) }
                        ?: error("Party slot ${reviewed.position} changed during review.")
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setBusy(false)
                result.onSuccess { showFinalReview(it) }
                    .onFailure { status.text = it.message ?: "The selected Party batch changed during review." }
            }
        }
    }

    private fun showFinalReview(chosen: List<ScarletVioletPartyBatchCandidate>) {
        val uniqueCount = chosen.map { it.bankName }.distinct().size
        val message = buildString {
            append("Copy ${chosen.size} selected native Party PK9 record${if (chosen.size == 1) "" else "s"} to PSE Bank?\n\n")
            chosen.forEach { append("• Party slot ${it.position}: ${it.label.substringAfter(": ")}\n  ${it.bankName}\n") }
            append("\n$uniqueCount content-addressed Bank entr${if (uniqueCount == 1) "y" else "ies"} will exist after verification. Identical PK9 bytes share one Bank entry. Party order and count, PC boxes, ride Pokémon, records, checksums and title identity remain unchanged.")
        }
        AlertDialog.Builder(this).setTitle("Review native Party Bank batch")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Copy to PSE Bank") { _, _ -> commit(chosen) }
            .show()
    }

    private fun commit(chosen: List<ScarletVioletPartyBatchCandidate>) {
        if (busy) return
        setBusy(true)
        status.text = "Reopening the source and verifying the complete Party batch…"
        worker.execute {
            val result = runCatching {
                val current = candidatesFrom(readVerifiedSource())
                val exact = chosen.map { reviewed ->
                    current.firstOrNull { it.position == reviewed.position && it.record.contentEquals(reviewed.record) }
                        ?: error("Party slot ${reviewed.position} changed before copying.")
                }
                SwitchPseBankStore.depositScarletVioletBatch(this, exact.map { it.record })
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = result.fold(
                    onSuccess = {
                        val unique = it.entries.map { file -> file.name }.distinct()
                        "Native Scarlet/Violet Party Bank batch verified.\n${unique.size} content-addressed entr${if (unique.size == 1) "y" else "ies"} available; ${it.createdEntries.size} newly created.\n\n" +
                            unique.joinToString("\n") + "\n\nThe source save, Party, ride Pokémon and every native PC slot remain unchanged."
                    },
                    onFailure = { (it.message ?: "The native Party Bank batch could not be copied.") + "\n\nThe Scarlet/Violet save was not changed." }
                )
                if (result.isSuccess) candidates = emptyList()
                setBusy(false)
            }
        }
    }

    private fun readVerifiedSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the selected Scarlet/Violet save from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        val bytes = ScarletVioletPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
        require(sha256(bytes) == expectedHash) { "The selected save changed. Reopen it from PSE." }
        return bytes
    }

    private fun candidatesFrom(source: ByteArray): List<ScarletVioletPartyBatchCandidate> {
        val storage = ScarletVioletStorageReadOnly.read(source)
        val records = ScarletVioletSaveValidation.readPokemonBlocks(source).party
        val size = ScarletVioletPokemonReadOnly.RECORD_SIZE
        return storage.party().mapNotNull { slot ->
            val pokemon = (slot.read as? ScarletVioletSlotRead.Pokemon)?.value ?: return@mapNotNull null
            val offset = (slot.position - 1) * size
            val record = records.copyOfRange(offset, offset + size)
            require(ScarletVioletPokemonReadOnly.read(record) is ScarletVioletSlotRead.Pokemon) {
                "Party slot ${slot.position} no longer contains a checksum-valid native PK9."
            }
            val species = ScarletVioletSpeciesNames.name(pokemon.species)
            val name = pokemon.nickname.takeIf { it.isNotBlank() && it != species }
                ?.let { "$it ($species)" } ?: species
            ScarletVioletPartyBatchCandidate(
                slot.position,
                "Party ${slot.position}: $name • Lv ${pokemon.cachedLevel}" +
                    (if (pokemon.shiny) " • Shiny" else "") +
                    (if (pokemon.isEgg) " • Egg" else ""),
                record,
                "switch_sv_${sha256(record)}.pk9"
            )
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        choose.isEnabled = !value && candidates.isNotEmpty()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
}
