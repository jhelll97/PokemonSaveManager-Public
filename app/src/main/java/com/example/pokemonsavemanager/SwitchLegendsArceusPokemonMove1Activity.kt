package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/** Gen 1-shaped Move 1 and current PP editor for one native Legends: Arceus PA8. */
class SwitchLegendsArceusPokemonMove1Activity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2137
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var selectedMoveId = -1
    private var pendingPlan: LegendsArceusMove1CopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var move: Button
    private lateinit var pp: EditText
    private lateinit var review: Button
    private lateinit var move2: Button
    private lateinit var trainerId: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            LegendsArceusPokemonArea.values().firstOrNull { it.name == value }
        }
        pasture = intent?.getIntExtra("pse.switch.pokemon.pasture", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == LegendsArceusPokemonArea.PASTURE && pasture !in 1..32) ||
            (selectedArea == LegendsArceusPokemonArea.PARTY && position !in 1..6)) {
            finish(); return
        }
        val location = if (selectedArea == LegendsArceusPokemonArea.PASTURE) "Pasture $pasture • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Move 1"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChoose one usable native Legends: Arceus Move 1 and its current PP. Review the before/after values, then save a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Move 1"; textSize = 16f })
        move = Button(this).apply {
            text = "Choose Move 1"; isEnabled = false; setOnClickListener { chooseMove() }
        }
        root.addView(move)
        root.addView(TextView(this).apply { text = "Move 1 PP"; textSize = 16f })
        pp = EditText(this).apply {
            hint = "Current PP"
            inputType = InputType.TYPE_CLASS_NUMBER
            isEnabled = false
            maxLines = 1
        }
        root.addView(pp)
        review = Button(this).apply {
            text = "Review Move 1 change"; isEnabled = false; setOnClickListener { reviewMove() }
        }
        root.addView(review)
        move2 = Button(this).apply {
            text = "Next field: Move 2"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsArceusPokemonMove1Activity,
                    SwitchLegendsArceusPokemonMove2Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.pasture", pasture)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(move2)
        trainerId = Button(this).apply {
            text = "Back to Trainer ID"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsArceusPokemonMove1Activity,
                    SwitchLegendsArceusPokemonTrainerIdActivity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.pasture", pasture)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(trainerId)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply { text = "Back to Pokémon"; setOnClickListener { if (!busy) finish() } })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusPokemonMove1Editing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = LegendsArceusSaveValidation.inspect(bytes)
                require(validation is LegendsArceusValidation.Verified) {
                    "A validated native Legends: Arceus save is required."
                }
                val storage = LegendsArceusStorageReadOnly.read(bytes)
                val read = if (selectedArea == LegendsArceusPokemonArea.PASTURE)
                    storage.pasture(pasture - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? LegendsArceusSlotRead.Pokemon)?.value
                    ?: error("The selected native PA8 is not checksum-valid.")
                val current = pokemon.moves.first()
                val choice = LegendsArceusMoveCatalog.choice(current.id)
                    ?: error("Current Move 1 (#${current.id}) is not usable in Legends: Arceus.")
                val maximum = LegendsArceusMoveCatalog.maximumPp(current.id)
                require(current.pp in 0..maximum) { "Current Move 1 PP exceeds its Legends: Arceus maximum." }
                Move1Opened(bytes, validation.version.displayName, pokemon, choice, current, maximum)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { state ->
                    opened = state.bytes
                    selectedMoveId = state.choice.id
                    move.text = state.choice.toString()
                    pp.setText(state.current.pp.toString())
                    status.text = "${state.displayName} • Native integrity verified\n" +
                        "$location • ${LegendsArceusSpeciesNames.name(state.pokemon.species)}\n" +
                        "Current Move 1: ${state.choice.name} (#${state.choice.id})\n" +
                        "Current PP: ${state.current.pp}/${state.maximumPp}\n" +
                        "Source SHA-256: ${LegendsArceusPokemonMove1Editing.sha256(state.bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        move.isEnabled = !value && opened != null
        pp.isEnabled = !value && opened != null
        review.isEnabled = !value && opened != null
        move2.isEnabled = !value && opened != null
        trainerId.isEnabled = !value && opened != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun chooseMove() {
        if (busy || opened == null) return
        val choices = LegendsArceusMoveCatalog.choices.filter {
            runCatching { LegendsArceusMoveCatalog.maximumPp(it.id) }.isSuccess
        }
        val labels = choices.map { it.toString() }.toTypedArray()
        val checked = choices.indexOfFirst { it.id == selectedMoveId }
        AlertDialog.Builder(this).setTitle("Move 1")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = choices[which]
                selectedMoveId = selected.id
                move.text = selected.toString()
                pp.setText(LegendsArceusMoveCatalog.maximumPp(selected.id).toString())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsArceusPokemonMove1Editing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsArceusPokemonMove1Editing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewMove() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requestedPp = pp.text.toString().toIntOrNull()
        if (requestedPp == null) {
            status.text = "Move 1 PP must be a whole number within the selected move's exact limit."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LegendsArceusPokemonMove1Editing.stage(
                    bytes, hash, selectedArea, pasture, position, selectedMoveId, requestedPp
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure { status.text = it.message ?: "Move 1 could not be staged."; setBusy(false) }
            }
        }
    }

    private fun showReview(plan: LegendsArceusMove1CopyPlan) {
        var dispatched = false
        val before = plan.before.moves.first()
        val beforeChoice = LegendsArceusMoveCatalog.choice(before.id)
        AlertDialog.Builder(this).setTitle("Save this Move 1 to a new copy?")
            .setMessage(
                "${plan.location} • ${LegendsArceusSpeciesNames.name(plan.before.species)}\n" +
                    "Move 1: ${beforeChoice?.name ?: "Move #${before.id}"} (#${before.id}) → ${plan.move.name} (#${plan.move.id})\n" +
                    "Current PP: ${before.pp} → ${plan.pp} / ${plan.maximumPp}\n\n" +
                    "Legality handling: PSE accepts only IDs in Legends: Arceus's native usable-move bitset through Take Heart, excludes every move the game marks unusable plus Struggle, requires unique consecutive slots, and enforces the game's exact PP maximum. Species learnability is not claimed by this bounded field edit.\n\n" +
                    "Allowed encrypted scope: one ${plan.recordSize}-byte PA8 record at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PA8, only Move 1, its current PP, and checksum may change. Moves 2–4, relearn moves, species, form, Trainer ID, nickname, stats, Alpha and Noble state, Ganbaru values, the unselected storage object, every other party and pasture slot, and all unrelated bytes remain exact. " +
                    "PSE verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
            )
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { if (!dispatched) setBusy(false) }
            .setPositiveButton("Choose new copy") { _, _ ->
                if (!dispatched && !isFinishing && !isDestroyed) {
                    dispatched = true; pendingPlan = plan; verifiedCopy = null
                    inspectCopy.visibility = android.view.View.GONE
                    try {
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = "application/octet-stream"; addCategory(Intent.CATEGORY_OPENABLE)
                            putExtra(Intent.EXTRA_TITLE, "main")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        }, createCopyRequest)
                    } catch (failure: Exception) {
                        pendingPlan = null; setBusy(false)
                        status.text = failure.message ?: "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        startActivity(Intent(this, SwitchReadOnlyActivity::class.java).setData(uri)
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
            .putExtra("pse.switch.sha256", hash).putExtra("pse.switch.section", "storage")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != createCopyRequest) return
        val plan = pendingPlan
        pendingPlan = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || plan == null) {
            status.text = "Save cancelled or review expired. Nothing was written."; setBusy(false); return
        }
        if (uri.scheme != "content" || uri == sourceUri) {
            status.text = "Choose a new document separate from the source. Nothing was written."; setBusy(false); return
        }
        setBusy(true)
        status.text = "Saving and verifying a new copy…"
        worker.execute {
            var backupName: String? = null
            val result = runCatching {
                plan.saveNewCopy(
                    readSource = { readSource() },
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.LEGENDS_ARCEUS, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.LEGENDS_ARCEUS, backup.file, backup.sha256)
                    },
                    readDestination = { readDocument(uri) },
                    writeDestination = { edited ->
                        val output = contentResolver.openOutputStream(uri, "wt") ?: error("The new document is not writable.")
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
                    onSuccess = { "Move 1 copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." },
                    onFailure = { (it.message ?: "The Move 1 copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }

    private data class Move1Opened(
        val bytes: ByteArray,
        val displayName: String,
        val pokemon: LegendsArceusPokemonRead,
        val choice: LegendsArceusMoveChoice,
        val current: LegendsArceusMoveRead,
        val maximumPp: Int
    )
}
