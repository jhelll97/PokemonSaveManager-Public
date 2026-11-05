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

/** Gen 1-shaped Pokémon editor for one native Sword/Shield PK8 Level field. */
class SwitchSwordShieldPokemonLevelActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2133
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: SwordShieldPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: SwordShieldPokemonRead? = null
    private var pendingPlan: SwordShieldLevelCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var level: EditText
    private lateinit var review: Button
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
            finish()
            return
        }
        val location = if (selectedArea == SwordShieldPokemonArea.BOX)
            "Box $box • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Sword/Shield • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Level"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChange the next Gen 1 Pokémon editor field in one native PK8. PSE sets EXP to the selected Level's minimum and rebuilds native cached battle values."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Level (1–100)"; textSize = 16f })
        level = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            isEnabled = false
        }
        root.addView(level)
        root.addView(TextView(this).apply {
            text = "Level follows the Gen 1 BASIC field order. PSE verifies the Generation 8 growth group and species/form base stats against the PK8's current cached stats, then rebuilds EXP, HP and all five battle stats from stat nature, IVs, Hyper Training and EVs. Eggs remain protected."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review Level change"
            isEnabled = false
            setOnClickListener { reviewLevel() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Species"
            setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(SwordShieldPokemonLevelEditing.sha256(bytes) == expectedHash) {
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
                require(!pokemon.isEgg) { "Egg level editing is not supported for Sword/Shield." }
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    current = pokemon
                    level.setText(pokemon.cachedLevel.toString())
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { SwordShieldSpeciesNames.name(pokemon.species) }}\n" +
                        "Current Level: ${pokemon.cachedLevel} • EXP: ${pokemon.experience}\n" +
                        "HP ${pokemon.currentHp}/${pokemon.maximumHp}\n" +
                        "Source SHA-256: ${SwordShieldPokemonLevelEditing.sha256(bytes)}"
                }.onFailure {
                    status.text = it.message ?: "The selected Pokémon could not be opened safely."
                }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        level.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return SwordShieldPokemonLevelEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = SwordShieldPokemonLevelEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewLevel() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val target = level.text.toString().toIntOrNull()
        if (target == null || target !in 1..100) {
            status.text = "Level must be 1–100."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                SwordShieldPokemonLevelEditing.stage(
                    bytes, hash, selectedArea, box, position, target
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure {
                        status.text = it.message ?: "The Level could not be staged."
                        setBusy(false)
                    }
            }
        }
    }

    private fun showReview(plan: SwordShieldLevelCopyPlan) {
        var dispatched = false
        val before = plan.before
        val after = plan.after
        AlertDialog.Builder(this).setTitle("Save this Level to a new copy?")
            .setMessage(
                "${plan.location} • ${before.nickname.ifBlank { SwordShieldSpeciesNames.name(before.species) }}\n" +
                    "Level: ${before.cachedLevel} → ${after.level}\n" +
                    "EXP: ${before.experience} → ${after.experience}\n" +
                    "HP: ${before.currentHp}/${before.maximumHp} → ${after.currentHp}/${after.maximumHp}\n" +
                    "Attack: ${before.cachedStats[0]} → ${after.attack}\n" +
                    "Defense: ${before.cachedStats[1]} → ${after.defense}\n" +
                    "Speed: ${before.cachedStats[2]} → ${after.speed}\n" +
                    "Sp. Atk: ${before.cachedStats[3]} → ${after.specialAttack}\n" +
                    "Sp. Def: ${before.cachedStats[4]} → ${after.specialDefense}\n\n" +
                    "Allowed encrypted scope: one 344-byte PK8 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PK8, only EXP, Level, current/max HP, five cached battle stats, and checksum may change. Species, form, nickname, moves, Dynamax/Gigantamax data, trainer identity, every other party and PC-box slot, and all unrelated bytes remain exact. " +
                    "PSE verifies a recovery backup and reopens the persisted copy. Choose a NEW empty document."
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
                            putExtra(Intent.EXTRA_TITLE, "main")
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
            .putExtra("pse.switch.family", SwitchGameFamily.SWORD_SHIELD.name)
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
                    retainBackup = { original ->
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.SWORD_SHIELD, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.SWORD_SHIELD, backup.file, backup.sha256)
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
                inspectCopy.visibility =
                    if (verifiedCopy != null) android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Level copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Level copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() {
        if (!busy) super.onBackPressed()
    }

    override fun onDestroy() {
        pendingPlan = null
        worker.shutdown()
        super.onDestroy()
    }
}
