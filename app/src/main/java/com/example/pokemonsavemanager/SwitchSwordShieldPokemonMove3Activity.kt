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

/** Gen 1-shaped Move 3 and current PP editor for one native Sword/Shield PK8. */
class SwitchSwordShieldPokemonMove3Activity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2139
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: SwordShieldPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var selectedMoveId = -1
    private var preservedPpUps = 0
    private var pendingPlan: SwordShieldMove3CopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var move: Button
    private lateinit var pp: EditText
    private lateinit var review: Button
    private lateinit var move4: Button
    private lateinit var move2: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            SwordShieldPokemonArea.values().firstOrNull { it.name == value }
        }
        box = intent?.getIntExtra("pse.switch.pokemon.box", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == SwordShieldPokemonArea.BOX && box !in 1..32) ||
            (selectedArea == SwordShieldPokemonArea.PARTY && position !in 1..6)) {
            finish(); return
        }
        val location = if (selectedArea == SwordShieldPokemonArea.BOX) "Box $box • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Sword/Shield • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Move 3"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChoose one usable native Sword/Shield Move 3 and its current PP. The existing PP Up count is preserved and used for the exact PP limit. Review the before/after values, then save a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Move 3"; textSize = 16f })
        move = Button(this).apply {
            text = "Choose Move 3"; isEnabled = false; setOnClickListener { chooseMove() }
        }
        root.addView(move)
        root.addView(TextView(this).apply { text = "Move 3 PP"; textSize = 16f })
        pp = EditText(this).apply {
            hint = "Current PP"
            inputType = InputType.TYPE_CLASS_NUMBER
            isEnabled = false
            maxLines = 1
        }
        root.addView(pp)
        review = Button(this).apply {
            text = "Review Move 3 change"; isEnabled = false; setOnClickListener { reviewMove() }
        }
        root.addView(review)
        move4 = Button(this).apply {
            text = "Next field: Move 4"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchSwordShieldPokemonMove3Activity,
                    SwitchSwordShieldPokemonMove4Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.box", box)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(move4)
        move2 = Button(this).apply {
            text = "Back to Move 2"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchSwordShieldPokemonMove3Activity,
                    SwitchSwordShieldPokemonMove2Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.box", box)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(move2)
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
                require(SwordShieldPokemonMove3Editing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = SwordShieldSaveValidation.inspect(bytes)
                require(validation is SwordShieldValidation.Verified) {
                    "A validated native Sword/Shield save is required."
                }
                val storage = SwordShieldStorageReadOnly.read(bytes)
                val read = if (selectedArea == SwordShieldPokemonArea.BOX)
                    storage.box(box - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? SwordShieldSlotRead.Pokemon)?.value
                    ?: error("The selected native PK8 is not checksum-valid.")
                val current = pokemon.moves[2]
                val choice = if (current.id == 0) null else
                    (SwordShieldMoveCatalog.choice(current.id)
                        ?: error("Current Move 3 (#${current.id}) is not usable in Sword/Shield."))
                val maximum = if (choice == null) {
                    require(current.pp == 0 && current.ppUps == 0) {
                        "An empty Move 3 slot retains PP data."
                    }
                    0
                } else SwordShieldMoveCatalog.maximumPp(current.id, current.ppUps).also {
                    require(current.pp in 0..it) { "Current Move 3 PP exceeds its Sword/Shield maximum." }
                }
                Move3Opened(bytes, validation.version.displayName, pokemon, choice, current, maximum)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { state ->
                    opened = state.bytes
                    selectedMoveId = state.choice?.id ?: -1
                    preservedPpUps = state.current.ppUps
                    move.text = state.choice?.toString() ?: "Choose Move 3"
                    pp.setText(state.current.pp.toString())
                    status.text = "${state.displayName} • Native integrity verified\n" +
                        "$location • ${SwordShieldSpeciesNames.name(state.pokemon.species)}\n" +
                        "Current Move 3: ${state.choice?.let { "${it.name} (#${it.id})" } ?: "Empty"}\n" +
                        "Current PP: ${state.current.pp}/${state.maximumPp} • Preserved PP Ups: ${state.current.ppUps}\n" +
                        "Source SHA-256: ${SwordShieldPokemonMove3Editing.sha256(state.bytes)}"
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
        move4.isEnabled = !value && opened != null
        move2.isEnabled = !value && opened != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun chooseMove() {
        if (busy || opened == null) return
        val choices = SwordShieldMoveCatalog.choices.filter {
            runCatching { SwordShieldMoveCatalog.maximumPp(it.id, preservedPpUps) }.isSuccess
        }
        val labels = choices.map { it.toString() }.toTypedArray()
        val checked = choices.indexOfFirst { it.id == selectedMoveId }
        AlertDialog.Builder(this).setTitle("Move 3")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = choices[which]
                selectedMoveId = selected.id
                move.text = selected.toString()
                pp.setText(SwordShieldMoveCatalog.maximumPp(selected.id, preservedPpUps).toString())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return SwordShieldPokemonMove3Editing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = SwordShieldPokemonMove3Editing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewMove() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        if (selectedMoveId < 0) {
            status.text = "Choose a usable native Sword/Shield Move 3 before review."
            return
        }
        val requestedPp = pp.text.toString().toIntOrNull()
        if (requestedPp == null) {
            status.text = "Move 3 PP must be a whole number within the selected move's exact limit."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                SwordShieldPokemonMove3Editing.stage(
                    bytes, hash, selectedArea, box, position, selectedMoveId, requestedPp
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure { status.text = it.message ?: "Move 3 could not be staged."; setBusy(false) }
            }
        }
    }

    private fun showReview(plan: SwordShieldMove3CopyPlan) {
        var dispatched = false
        val before = plan.before.moves[2]
        val beforeChoice = SwordShieldMoveCatalog.choice(before.id)
        val beforeLabel = beforeChoice?.let { "${it.name} (#${it.id})" } ?: "Empty"
        AlertDialog.Builder(this).setTitle("Save this Move 3 to a new copy?")
            .setMessage(
                "${plan.location} • ${SwordShieldSpeciesNames.name(plan.before.species)}\n" +
                    "Move 3: $beforeLabel → ${plan.move.name} (#${plan.move.id})\n" +
                    "Current PP: ${before.pp} → ${plan.pp} / ${plan.maximumPp}\n" +
                    "PP Ups: ${plan.ppUps} (preserved)\n\n" +
                    "Legality handling: PSE accepts only usable native Sword/Shield moves, excludes dummied, Z, Dynamax-only and Struggle IDs, requires unique consecutive slots, and enforces the exact Generation 8 PP maximum for the preserved PP Up count. Species learnability is not claimed by this bounded field edit.\n\n" +
                    "Allowed encrypted scope: one 344-byte PK8 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PK8, only Move 3, its current PP, and checksum may change. PP Ups, Moves 1–2, Move 4, relearn moves, species, form, Trainer ID, nickname, stats, Dynamax and Gigantamax data, the unselected storage object, every other party and PC-box slot, and all unrelated bytes remain exact. " +
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
            .putExtra("pse.switch.family", SwitchGameFamily.SWORD_SHIELD.name)
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
                        val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.SWORD_SHIELD, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.SWORD_SHIELD, backup.file, backup.sha256)
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
                    onSuccess = { "Move 3 copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." },
                    onFailure = { (it.message ?: "The Move 3 copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }

    private data class Move3Opened(
        val bytes: ByteArray,
        val displayName: String,
        val pokemon: SwordShieldPokemonRead,
        val choice: SwordShieldMoveChoice?,
        val current: SwordShieldMoveRead,
        val maximumPp: Int
    )
}
