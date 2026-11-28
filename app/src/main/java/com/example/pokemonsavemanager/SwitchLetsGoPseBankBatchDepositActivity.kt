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

private data class LetsGoBatchDepositCandidate(
    val index: Int,
    val label: String,
    val record: ByteArray,
    val bankName: String
)

/** Copies a reviewed selection of exact native PB7 storage records into content-addressed PSE Bank entries. */
class SwitchLetsGoPseBankBatchDepositActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var page = 0
    private var candidates: List<LetsGoBatchDepositCandidate> = emptyList()
    private var busy = false
    private lateinit var status: TextView
    private lateinit var choose: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        page = intent?.getIntExtra("pse.switch.storage.page", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || page !in 0 until 40) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Copy selected native PB7 from storage page ${page + 1}"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Choose several readable native Pokémon from this storage page. PSE reopens the exact save before copying, verifies every complete 260-byte PB7, and stores each content-addressed entry byte-for-byte. The Let's Go save, stored-list order, party pointers, partner pointer and every native record remain unchanged."
            textSize = 16f
        })
        status = TextView(this).apply { text = "Reopening and validating storage page ${page + 1} from the selected save…"; textSize = 16f }
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
                        "Storage page ${page + 1} has no readable native Pokémon available to copy. Empty and unreadable records remain untouched."
                    else "${it.size} readable native Pokémon available on storage page ${page + 1}. Empty and unreadable records are excluded."
                }.onFailure {
                    status.text = it.message ?: "The selected Let's Go save could not be validated."
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
            .setTitle("Storage page ${page + 1} • Choose 1-${candidates.size} native PB7 records")
            .setMultiChoiceItems(candidates.map { it.label }.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
                refreshSelectionActions()
            }
            .setNegativeButton("Cancel") { _, _ ->
                val count = selected.count { it }
                status.text = "Cancelled Bank selection from storage page ${page + 1} with $count record${if (count == 1) "" else "s"} selected. Nothing was copied."
            }
            .setNeutralButton("Select all", null)
            .setPositiveButton("Review (0)", null)
            .create()
        dialog.setOnCancelListener {
            val count = selected.count { it }
            status.text = "Cancelled Bank selection from storage page ${page + 1} with $count record${if (count == 1) "" else "s"} selected. Nothing was copied."
        }
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

    private fun review(chosen: List<LetsGoBatchDepositCandidate>) {
        if (busy) return
        setBusy(true)
        worker.execute {
            val result = runCatching {
                val current = candidatesFrom(readVerifiedSource())
                chosen.map { reviewed ->
                    current.firstOrNull { it.index == reviewed.index && it.record.contentEquals(reviewed.record) }
                        ?: error("Stored record #${reviewed.index + 1} changed during review.")
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setBusy(false)
                result.onSuccess { showFinalReview(it) }
                    .onFailure { status.text = it.message ?: "The selected batch changed during review." }
            }
        }
    }

    private fun showFinalReview(chosen: List<LetsGoBatchDepositCandidate>) {
        val uniqueCount = chosen.map { it.bankName }.distinct().size
        val message = buildString {
            append("Copy ${chosen.size} selected native PB7 record${if (chosen.size == 1) "" else "s"} from storage page ${page + 1} to PSE Bank?\n\n")
            chosen.forEach { append("• Stored record #${it.index + 1}: ${it.label.substringAfter(": ")}\n  ${it.bankName}\n") }
            append("\n$uniqueCount content-addressed Bank entr${if (uniqueCount == 1) "y" else "ies"} will exist after verification. Identical PB7 bytes share one Bank entry. The source save, native stored-list order, party pointers, partner pointer, records, checksums and title identity remain unchanged.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("Review storage page ${page + 1} Bank batch")
            .setMessage(message)
            .setNegativeButton("Cancel") { _, _ ->
                status.text = "Cancelled copying ${chosen.size} reviewed Bank record${if (chosen.size == 1) "" else "s"} from storage page ${page + 1}. Nothing was copied."
            }
            .setPositiveButton("Copy ${chosen.size} to PSE Bank") { _, _ -> commit(chosen) }
            .create()
        dialog.setOnCancelListener {
            status.text = "Cancelled copying ${chosen.size} reviewed Bank record${if (chosen.size == 1) "" else "s"} from storage page ${page + 1}. Nothing was copied."
        }
        dialog.show()
    }

    private fun commit(chosen: List<LetsGoBatchDepositCandidate>) {
        if (busy) return
        setBusy(true)
        status.text = "Reopening storage page ${page + 1} and verifying ${chosen.size} reviewed Bank record${if (chosen.size == 1) "" else "s"}…"
        worker.execute {
            val result = runCatching {
                val current = candidatesFrom(readVerifiedSource())
                val exact = chosen.map { reviewed ->
                    current.firstOrNull { it.index == reviewed.index && it.record.contentEquals(reviewed.record) }
                        ?: error("Stored record #${reviewed.index + 1} changed before copying.")
                }
                SwitchPseBankStore.depositLetsGoBatch(this, exact.map { it.record })
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = result.fold(
                    onSuccess = {
                        val unique = it.entries.map { file -> file.name }.distinct()
                        "Native Let's Go Bank batch from storage page ${page + 1} verified.\nReviewed ${chosen.size} native record${if (chosen.size == 1) "" else "s"}; ${unique.size} content-addressed entr${if (unique.size == 1) "y" else "ies"} available; ${it.createdEntries.size} newly created.\n\n" +
                            unique.joinToString("\n") + "\n\nThe source save and every native storage record remain unchanged."
                    },
                    onFailure = { (it.message ?: "The native Bank batch could not be copied.") + "\n\nThe Let's Go save was not changed." }
                )
                if (result.isSuccess) candidates = emptyList()
                setBusy(false)
            }
        }
    }

    private fun readVerifiedSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the selected Let's Go save from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        val bytes = LetsGoPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
        require(sha256(bytes) == expectedHash) { "The selected save changed. Reopen it from PSE." }
        return bytes
    }

    private fun candidatesFrom(source: ByteArray): List<LetsGoBatchDepositCandidate> {
        val storage = LetsGoStorageReadOnly.read(source)
        val size = LetsGoPokemonReadOnly.RECORD_SIZE
        return storage.page(page).mapNotNull { slot ->
            val pokemon = (slot.read as? LetsGoSlotRead.Pokemon)?.value ?: return@mapNotNull null
            val offset = 0x5C00 + slot.index * size
            val record = source.copyOfRange(offset, offset + size)
            require(LetsGoPokemonReadOnly.read(record) is LetsGoSlotRead.Pokemon) {
                "Stored record #${slot.index + 1} no longer contains a checksum-valid native PB7."
            }
            val species = LetsGoSpeciesNames.name(pokemon.species)
            val name = pokemon.nickname.takeIf { it.isNotBlank() && it != species }
                ?.let { "$it ($species)" } ?: species
            val assignments = buildList {
                if (slot.partyPositions.isNotEmpty()) add("Party ${slot.partyPositions.joinToString()}")
                if (slot.partner) add("Partner")
            }
            LetsGoBatchDepositCandidate(
                slot.index,
                "Stored #${slot.index + 1}: $name • Lv ${pokemon.cachedLevel}" +
                    (if (pokemon.shiny) " • Shiny" else "") +
                    (if (pokemon.isEgg) " • Egg" else "") +
                    (if (assignments.isEmpty()) "" else " • ${assignments.joinToString(" • ")}"),
                record,
                "switch_lgpe_${sha256(record)}.pb7"
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
