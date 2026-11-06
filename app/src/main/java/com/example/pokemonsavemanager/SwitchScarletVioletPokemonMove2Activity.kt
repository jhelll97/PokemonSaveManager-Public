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

/** Gen 1-shaped Move 2 and current PP editor for one native Scarlet/Violet PK9. */
class SwitchScarletVioletPokemonMove2Activity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2167
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: ScarletVioletPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var selectedMoveId = -1
    private var preservedPpUps = 0
    private var pendingPlan: ScarletVioletMove2CopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var move: Button
    private lateinit var pp: EditText
    private lateinit var review: Button
    private lateinit var move3: Button
    private lateinit var move1: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            ScarletVioletPokemonArea.values().firstOrNull { it.name == value }
        }
        box = intent?.getIntExtra("pse.switch.pokemon.box", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == ScarletVioletPokemonArea.BOX && box !in 1..32) ||
            (selectedArea == ScarletVioletPokemonArea.PARTY && position !in 1..6)) {
            finish(); return
        }
        val location = if (selectedArea == ScarletVioletPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Scarlet/Violet • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Move 2"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChoose one usable native Scarlet/Violet Move 2 and its current PP. The existing PP Up count is preserved and used for the exact PP limit. Review the before/after values, then save a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Move 2"; textSize = 16f })
        move = Button(this).apply {
            text = "Choose Move 2"; isEnabled = false; setOnClickListener { chooseMove() }
        }
        root.addView(move)
        root.addView(TextView(this).apply { text = "Move 2 PP"; textSize = 16f })
        pp = EditText(this).apply {
            hint = "Current PP"
            inputType = InputType.TYPE_CLASS_NUMBER
            isEnabled = false
            maxLines = 1
        }
        root.addView(pp)
        review = Button(this).apply {
            text = "Review Move 2 change"; isEnabled = false; setOnClickListener { reviewMove() }
        }
        root.addView(review)
        move3 = Button(this).apply {
            text = "Next field: Move 3"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchScarletVioletPokemonMove2Activity,
                    SwitchScarletVioletPokemonMove3Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.box", box)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(move3)
        move1 = Button(this).apply {
            text = "Back to Move 1"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchScarletVioletPokemonMove2Activity,
                    SwitchScarletVioletPokemonMove1Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.box", box)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(move1)
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
                require(ScarletVioletPokemonMove2Editing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = ScarletVioletSaveValidation.inspect(bytes)
                require(validation is ScarletVioletValidation.Verified) {
                    "A validated native Scarlet/Violet save is required."
                }
                val storage = ScarletVioletStorageReadOnly.read(bytes)
                val read = if (selectedArea == ScarletVioletPokemonArea.BOX)
                    storage.box(box - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? ScarletVioletSlotRead.Pokemon)?.value
                    ?: error("The selected native PK9 is not checksum-valid.")
                val current = pokemon.moves[1]
                val choice = if (current.id == 0) null else
                    (ScarletVioletMoveCatalog.choice(current.id)
                        ?: error("Current Move 2 (#${current.id}) is not usable in Scarlet/Violet."))
                val maximum = if (choice == null) {
                    require(current.pp == 0 && current.ppUps == 0) {
                        "An empty Move 2 slot retains PP data."
                    }
                    0
                } else ScarletVioletMoveCatalog.maximumPp(current.id, current.ppUps).also {
                    require(current.pp in 0..it) {
                        "Current Move 2 PP exceeds its Scarlet/Violet maximum."
                    }
                }
                Move2Opened(bytes, validation.version.displayName, pokemon, choice, current, maximum)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { state ->
                    opened = state.bytes
                    selectedMoveId = state.choice?.id ?: -1
                    preservedPpUps = state.current.ppUps
                    move.text = state.choice?.toString() ?: "Choose Move 2"
                    pp.setText(state.current.pp.toString())
                    status.text = "${state.displayName} • Native integrity verified\n" +
                        "$location • ${ScarletVioletSpeciesNames.name(state.pokemon.species)}\n" +
                        "Current Move 2: ${state.choice?.let { "${it.name} (#${it.id})" } ?: "Empty"}\n" +
                        "Current PP: ${state.current.pp}/${state.maximumPp} • Preserved PP Ups: ${state.current.ppUps}\n" +
                        "Source SHA-256: ${ScarletVioletPokemonMove2Editing.sha256(state.bytes)}"
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
        move3.isEnabled = !value && opened != null
        move1.isEnabled = !value && opened != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun chooseMove() {
        if (busy || opened == null) return
        val choices = ScarletVioletMoveCatalog.choices.filter {
            runCatching { ScarletVioletMoveCatalog.maximumPp(it.id, preservedPpUps) }.isSuccess
        }
        val labels = choices.map { it.toString() }.toTypedArray()
        val checked = choices.indexOfFirst { it.id == selectedMoveId }
        AlertDialog.Builder(this).setTitle("Move 2")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = choices[which]
                selectedMoveId = selected.id
                move.text = selected.toString()
                pp.setText(ScarletVioletMoveCatalog.maximumPp(selected.id, preservedPpUps).toString())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return ScarletVioletPokemonMove2Editing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = ScarletVioletPokemonMove2Editing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewMove() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        if (selectedMoveId < 0) {
            status.text = "Choose a usable native Scarlet/Violet Move 2 before review."
            return
        }
        val requestedPp = pp.text.toString().toIntOrNull()
        if (requestedPp == null) {
            status.text = "Move 2 PP must be a whole number within the selected move's exact limit."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                ScarletVioletPokemonMove2Editing.stage(
                    bytes, hash, selectedArea, box, position, selectedMoveId, requestedPp
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure { status.text = it.message ?: "Move 2 could not be staged."; setBusy(false) }
            }
        }
    }

    private fun showReview(plan: ScarletVioletMove2CopyPlan) {
        var dispatched = false
        val before = plan.before.moves[1]
        val beforeChoice = ScarletVioletMoveCatalog.choice(before.id)
        val beforeLabel = beforeChoice?.let { "${it.name} (#${it.id})" } ?: "Empty"
        AlertDialog.Builder(this).setTitle("Save this Move 2 to a new copy?")
            .setMessage(
                "${plan.location} • ${ScarletVioletSpeciesNames.name(plan.before.species)}\n" +
                    "Move 2: $beforeLabel → ${plan.move.name} (#${plan.move.id})\n" +
                    "Current PP: ${before.pp} → ${plan.pp} / ${plan.maximumPp}\n" +
                    "PP Ups: ${plan.ppUps} (preserved)\n\n" +
                    "Legality handling: PSE accepts only IDs in Scarlet/Violet's native Generation 9 usable-move bitset through Malignant Chain, excludes every game-marked unusable move plus Struggle, requires unique consecutive slots, and enforces the exact Generation 9 PP maximum for the preserved PP Up count. Species learnability is not claimed by this bounded field edit.\n\n" +
                    "Allowed encrypted scope: one 344-byte PK9 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PK9, only Move 2, its current PP, and checksum may change. PP Ups, Move 1, Moves 3–4, relearn moves, species, form, Trainer ID, nickname, stats, Tera types, scale, the unselected storage object, every other party and PC-box slot, the dedicated ride Pokémon, and all unrelated bytes remain exact. " +
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
            .putExtra("pse.switch.family", SwitchGameFamily.SCARLET_VIOLET.name)
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
                        val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.SCARLET_VIOLET, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.SCARLET_VIOLET, backup.file, backup.sha256)
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
                    onSuccess = { "Move 2 copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." },
                    onFailure = { (it.message ?: "The Move 2 copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }

    private data class Move2Opened(
        val bytes: ByteArray,
        val displayName: String,
        val pokemon: ScarletVioletPokemonRead,
        val choice: ScarletVioletMoveChoice?,
        val current: ScarletVioletMoveRead,
        val maximumPp: Int
    )
}
