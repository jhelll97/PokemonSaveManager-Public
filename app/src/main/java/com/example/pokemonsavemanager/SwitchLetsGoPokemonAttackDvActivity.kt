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

/** Gen 1-shaped Pokémon editor for one native Let's Go PB7 Attack DV / IV. */
class SwitchLetsGoPokemonAttackDvActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2141
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var storageIndex = -1
    private var opened: ByteArray? = null
    private var pendingPlan: LetsGoAttackDvCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var attackIv: EditText
    private lateinit var review: Button
    private lateinit var defenseDv: Button
    private lateinit var move4: Button
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
        root.addView(TextView(this).apply { text = "Edit Pokémon • Attack DV / IV"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Attack is the first field in Gen 1's DV section. Let's Go stores its native counterpart as a 0–31 Attack IV. PSE rebuilds the derived Attack stat and CP, then saves a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Attack DV / IV"; textSize = 16f })
        attackIv = EditText(this).apply {
            hint = "0–31"
            inputType = InputType.TYPE_CLASS_NUMBER
            isEnabled = false
            maxLines = 1
        }
        root.addView(attackIv)
        review = Button(this).apply {
            text = "Review Attack DV / IV change"; isEnabled = false; setOnClickListener { reviewAttackDv() }
        }
        root.addView(review)
        defenseDv = Button(this).apply {
            text = "Next field: Defense DV / IV"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLetsGoPokemonAttackDvActivity,
                    SwitchLetsGoPokemonDefenseDvActivity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.index", storageIndex)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(defenseDv)
        move4 = Button(this).apply {
            text = "Back to Move 4"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLetsGoPokemonAttackDvActivity, SwitchLetsGoPokemonMove4Activity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.index", storageIndex)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(move4)
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
                require(LetsGoPokemonAttackDvEditing.sha256(bytes) == expectedHash) { "The source changed. Reopen it from PSE." }
                val validation = LetsGoSaveValidation.inspect(bytes)
                require(validation is LetsGoValidation.Verified) { "A validated native Let's Go save is required." }
                val slot = LetsGoStorageReadOnly.read(bytes).slot(storageIndex)
                val pokemon = (slot.read as? LetsGoSlotRead.Pokemon)?.value
                    ?: error("The selected native PB7 is not checksum-valid.")
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    attackIv.setText(pokemon.ivs[1].toString())
                    val attackHyperTrained = pokemon.hyperTrainingFlags and (1 shl 1) != 0
                    status.text = "$displayName • Native integrity verified\n" +
                        "Storage #${storageIndex + 1} • ${LetsGoSpeciesNames.name(pokemon.species)}\n" +
                        "Current Attack IV: ${pokemon.ivs[1]} • Derived Attack: ${pokemon.attack}\n" +
                        "Combat Power: ${pokemon.combatPower} • Hyper Training: ${if (attackHyperTrained) "Attack active" else "Not active for Attack"}\n" +
                        "Source SHA-256: ${LetsGoPokemonAttackDvEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        attackIv.isEnabled = !value && opened != null
        review.isEnabled = !value && opened != null
        defenseDv.isEnabled = !value && opened != null
        move4.isEnabled = !value && opened != null
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LetsGoPokemonAttackDvEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPokemonAttackDvEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewAttackDv() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val requested = attackIv.text.toString().toIntOrNull()
        if (requested == null || requested !in 0..31) {
            status.text = "Attack IV must be a whole number between 0 and 31."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LetsGoPokemonAttackDvEditing.stage(bytes, hash, storageIndex, requested)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure { status.text = it.message ?: "The Attack DV / IV could not be staged."; setBusy(false) }
            }
        }
    }

    private fun showReview(plan: LetsGoAttackDvCopyPlan) {
        var dispatched = false
        val attackHyperTrained = plan.before.hyperTrainingFlags and (1 shl 1) != 0
        AlertDialog.Builder(this).setTitle("Save this Attack DV / IV to a new copy?")
            .setMessage(
                "Storage #${plan.storageIndex + 1} • ${LetsGoSpeciesNames.name(plan.before.species)}\n" +
                    "Attack IV: ${plan.before.ivs[1]} → ${plan.after.attackIv}\n" +
                    "Derived Attack: ${plan.before.attack} → ${plan.after.attack}\n" +
                    "Combat Power: ${plan.before.combatPower} → ${plan.after.combatPower}\n" +
                    "Attack Hyper Training: ${if (attackHyperTrained) "Active (effective IV remains 31)" else "Not active"}\n\n" +
                    "Allowed native scope: one 260-byte PB7 record at 0x${plan.recordOffset.toString(16).uppercase()} and block 9's two-byte CRC. " +
                    "Inside the PB7, only the five Attack-IV bits, derived Attack stat, CP, and checksum may change. Other IVs, Hyper Training, Awakening Values, nature, HP and the other four battle stats, trainer identity, species, nickname, moves, party and partner pointers, every other Pokémon, and all unrelated bytes remain exact. " +
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
                    onSuccess = { "Attack DV / IV copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged." },
                    onFailure = { (it.message ?: "The Attack DV / IV copy could not be verified.") + "\nRecovery backup: ${backupName ?: "not created"}" }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}

