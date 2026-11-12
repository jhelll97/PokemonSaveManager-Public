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
import android.widget.TextView
import java.util.concurrent.Executors

/** Gen 1-shaped Move 3 and current PP editor for one native Let's Go PB7. */
class SwitchLetsGoPokemonMove3Activity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2139
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var storageIndex = -1
    private var opened: ByteArray? = null
    private var selectedMoveId = -1
    private var preservedPpUps = 0
    private var pendingPlan: LetsGoMove3CopyPlan? = null
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
        storageIndex = intent?.getIntExtra("pse.switch.pokemon.index", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || storageIndex !in 0 until 1000) {
            finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Move 3"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Choose one native Let's Go Move 3 and its current PP. The existing PP Up count is preserved and used for the exact PP limit. Review the before/after values, then save a verified new copy."
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
                startActivity(Intent(this@SwitchLetsGoPokemonMove3Activity, SwitchLetsGoPokemonMove4Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.index", storageIndex)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(move4)
        move2 = Button(this).apply {
            text = "Back to Move 2"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLetsGoPokemonMove3Activity, SwitchLetsGoPokemonMove2Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.index", storageIndex)
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
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoPokemonMove3Editing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                val validation = LetsGoSaveValidation.inspect(bytes)
                require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
                val slot = LetsGoStorageReadOnly.read(bytes).slot(storageIndex)
                val pokemon = (slot.read as? LetsGoSlotRead.Pokemon)?.value
                    ?: error("The selected native PB7 is not checksum-valid.")
                val current = pokemon.moves[2]
                val choice = if (current.id == 0) null else
                    (LetsGoMoveCatalog.choice(current.id)
                        ?: error("Current Move 3 (#${current.id}) is not available in Let's Go."))
                val maximum = if (choice == null) {
                    require(current.pp == 0 && current.ppUps == 0) {
                        "An empty Move 3 slot retains PP data."
                    }
                    0
                } else LetsGoMoveCatalog.maximumPp(current.id, current.ppUps).also {
                    require(current.pp in 0..it) { "Current Move 3 PP exceeds its Let's Go maximum." }
                }
                Move3Opened(bytes, validation.version.displayName, pokemon, choice, current, maximum)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { openedState ->
                    opened = openedState.bytes
                    selectedMoveId = openedState.choice?.id ?: -1
                    preservedPpUps = openedState.current.ppUps
                    move.text = openedState.choice?.toString() ?: "Choose Move 3"
                    pp.setText(openedState.current.pp.toString())
                    status.text = "${openedState.displayName} • Native integrity verified\n" +
                        "Storage #${storageIndex + 1} • ${LetsGoSpeciesNames.name(openedState.pokemon.species)}\n" +
                        "Current Move 3: ${openedState.choice?.let { "${it.name} (#${it.id})" } ?: "Empty"}\n" +
                        "Current PP: ${openedState.current.pp}/${openedState.maximumPp} • Preserved PP Ups: ${openedState.current.ppUps}\n" +
                        "Source SHA-256: ${LetsGoPokemonMove3Editing.sha256(openedState.bytes)}"
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
        val choices = LetsGoMoveCatalog.choices.filter {
            runCatching { LetsGoMoveCatalog.maximumPp(it.id, preservedPpUps) }.isSuccess
        }
        val labels = choices.map { it.toString() }.toTypedArray()
        val checked = choices.indexOfFirst { it.id == selectedMoveId }
        AlertDialog.Builder(this).setTitle("Move 3")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = choices[which]
                selectedMoveId = selected.id
                move.text = selected.toString()
                pp.setText(LetsGoMoveCatalog.maximumPp(selected.id, preservedPpUps).toString())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LetsGoPokemonMove3Editing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPokemonMove3Editing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewMove() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        if (selectedMoveId < 0) {
            status.text = "Choose a native Let's Go Move 3 before review."
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
                LetsGoPokemonMove3Editing.stage(bytes, hash, storageIndex, selectedMoveId, requestedPp)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure { status.text = it.message ?: "Move 3 could not be staged."; setBusy(false) }
            }
        }
    }

    private fun showReview(plan: LetsGoMove3CopyPlan) {
        var dispatched = false
        val before = plan.before.moves[2]
        val beforeChoice = LetsGoMoveCatalog.choice(before.id)
        val beforeLabel = beforeChoice?.let { "${it.name} (#${it.id})" } ?: "Empty"
        AlertDialog.Builder(this).setTitle("Save this Move 3 to a new copy?")
            .setMessage(
                "Storage #${plan.storageIndex + 1} • ${LetsGoSpeciesNames.name(plan.before.species)}\n" +
                    "Move 3: $beforeLabel → ${plan.move.name} (#${plan.move.id})\n" +
                    "Current PP: ${before.pp} → ${plan.pp} / ${plan.maximumPp}\n" +
                    "PP Ups: ${plan.ppUps} (preserved)\n\n" +
                    "Legality handling: PSE accepts only native Let's Go move IDs, requires unique consecutive move slots, and enforces the exact game-specific PP maximum for the preserved PP Up count. Species learnability is not claimed by this bounded field edit.\n\n" +
                    "Allowed native scope: one 260-byte PB7 record at 0x${plan.recordOffset.toString(16).uppercase()} and block 9's two-byte CRC. " +
                    "Inside the PB7, only Move 3, its current PP, and checksum may change. PP Ups, Moves 1–2, Move 4, species, Trainer ID, nickname, stats, party and partner pointers, every other Pokémon, and all unrelated bytes remain exact. " +
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
                            putExtra(Intent.EXTRA_TITLE, "SaveData.bin")
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
            .putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name)
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
                        val backup = SwitchSafeBackupStore.createVerified(this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, backup.file, backup.sha256)
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
        val pokemon: LetsGoPokemonRead,
        val choice: LetsGoMoveChoice?,
        val current: LetsGoMoveRead,
        val maximumPp: Int
    )
}
