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

private data class SwordShieldBatchDepositCandidate(
    val position: Int,
    val label: String,
    val record: ByteArray,
    val bankName: String
)

/** Copies a reviewed selection of exact native PK8 box records into content-addressed PSE Bank entries. */
class SwitchSwordShieldPseBankBatchDepositActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var box = 0
    private var candidates: List<SwordShieldBatchDepositCandidate> = emptyList()
    private var busy = false
    private lateinit var status: TextView
    private lateinit var choose: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        box = intent?.getIntExtra("pse.switch.box", 0) ?: 0
        if (sourceUri == null || expectedHash.isNullOrBlank() || box !in 1..32) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Sword/Shield • PSE Bank"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Copy selected native PK8 from Box $box"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Choose several readable native Pokémon from this box. PSE reopens the exact save before copying, verifies every complete 344-byte PK8, and stores each content-addressed entry byte-for-byte. The Sword/Shield save and every box slot remain unchanged."
            textSize = 16f
        })
        status = TextView(this).apply { text = "Reopening and validating Box $box from the selected save…"; textSize = 16f }
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
                    status.text = if (it.isEmpty())
                        "Box $box has no readable native Pokémon available to copy. Empty and unreadable slots remain untouched."
                    else "${it.size} readable native Pokémon available in Box $box. Empty and unreadable slots are excluded."
                }.onFailure {
                    status.text = it.message ?: "The selected Sword/Shield save could not be validated."
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
            .setTitle("Box $box • Choose 1-${candidates.size} native PK8 records")
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

    private fun review(chosen: List<SwordShieldBatchDepositCandidate>) {
        if (busy) return
        setBusy(true)
        worker.execute {
            val result = runCatching {
                val current = candidatesFrom(readVerifiedSource())
                chosen.map { reviewed ->
                    current.firstOrNull { it.position == reviewed.position && it.record.contentEquals(reviewed.record) }
                        ?: error("Box $box, slot ${reviewed.position} changed during review.")
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

    private fun showFinalReview(chosen: List<SwordShieldBatchDepositCandidate>) {
        val uniqueCount = chosen.map { it.bankName }.distinct().size
        val message = buildString {
            append("Copy ${chosen.size} selected native PK8 record${if (chosen.size == 1) "" else "s"} from Box $box to PSE Bank?\n\n")
            chosen.forEach { append("• Slot ${it.position}: ${it.label.substringAfter(": ")}\n  ${it.bankName}\n") }
            append("\n$uniqueCount content-addressed Bank entr${if (uniqueCount == 1) "y" else "ies"} will exist after verification. Identical PK8 bytes share one Bank entry. The source save, slots, records, checksums and title identity remain unchanged.")
        }
        AlertDialog.Builder(this).setTitle("Review Box $box Bank batch")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Copy ${chosen.size} to PSE Bank") { _, _ -> commit(chosen) }
            .show()
    }

    private fun commit(chosen: List<SwordShieldBatchDepositCandidate>) {
        if (busy) return
        setBusy(true)
        status.text = "Reopening Box $box and verifying ${chosen.size} reviewed Bank record${if (chosen.size == 1) "" else "s"}…"
        worker.execute {
            val result = runCatching {
                val current = candidatesFrom(readVerifiedSource())
                val exact = chosen.map { reviewed ->
                    current.firstOrNull { it.position == reviewed.position && it.record.contentEquals(reviewed.record) }
                        ?: error("Box $box, slot ${reviewed.position} changed before copying.")
                }
                val deposited = SwitchPseBankStore.depositSwordShieldBatch(this, exact.map { it.record })
                deposited
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                status.text = result.fold(
                    onSuccess = {
                        val unique = it.entries.map { file -> file.name }.distinct()
                        "Native Sword/Shield Bank batch from Box $box verified.\n${unique.size} content-addressed entr${if (unique.size == 1) "y" else "ies"} available; ${it.createdEntries.size} newly created.\n\n" +
                            unique.joinToString("\n") + "\n\nThe source save and every native slot remain unchanged."
                    },
                    onFailure = { (it.message ?: "The native Bank batch could not be copied.") + "\n\nThe Sword/Shield save was not changed." }
                )
                if (result.isSuccess) candidates = emptyList()
                setBusy(false)
            }
        }
    }

    private fun readVerifiedSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the selected Sword/Shield save from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        val bytes = SwordShieldPseBankRestoreEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
        require(sha256(bytes) == expectedHash) { "The selected save changed. Reopen it from PSE." }
        return bytes
    }

    private fun candidatesFrom(source: ByteArray): List<SwordShieldBatchDepositCandidate> {
        val storage = SwordShieldStorageReadOnly.read(source)
        val records = SwordShieldSaveValidation.readPokemonBlocks(source).boxes
        val size = SwordShieldPokemonReadOnly.RECORD_SIZE
        return storage.box(box - 1).mapNotNull { slot ->
            val pokemon = (slot.read as? SwordShieldSlotRead.Pokemon)?.value ?: return@mapNotNull null
            val offset = ((box - 1) * 30 + slot.position - 1) * size
            val record = records.copyOfRange(offset, offset + size)
            require(SwordShieldPokemonReadOnly.read(record) is SwordShieldSlotRead.Pokemon) {
                "Box $box, slot ${slot.position} no longer contains a checksum-valid native PK8."
            }
            val species = SwordShieldSpeciesNames.name(pokemon.species)
            val name = pokemon.nickname.takeIf { it.isNotBlank() && it != species }
                ?.let { "$it ($species)" } ?: species
            SwordShieldBatchDepositCandidate(
                slot.position,
                "Slot ${slot.position}: $name • Lv ${pokemon.cachedLevel}" + if (pokemon.shiny) " • Shiny" else "",
                record,
                "switch_swsh_${sha256(record)}.pk8"
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
