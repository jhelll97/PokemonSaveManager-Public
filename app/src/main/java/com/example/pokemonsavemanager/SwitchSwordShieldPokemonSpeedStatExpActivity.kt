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

/** Gen 1-shaped Pokémon editor mapping Speed Stat Exp to one native Sword/Shield Speed EV. */
class SwitchSwordShieldPokemonSpeedStatExpActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2146
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: SwordShieldPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: SwordShieldPokemonRead? = null
    private var pendingPlan: SwordShieldSpeedStatExpCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var speedEv: EditText
    private lateinit var review: Button
    private lateinit var defenseStatExp: Button
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
        val location = if (selectedArea == SwordShieldPokemonArea.BOX)
            "Box $box • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Sword/Shield • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Speed Stat Exp"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nSpeed Stat Exp follows Defense Stat Exp in Gen 1. Sword/Shield uses a native 0–252 Speed EV with a 510 total EV limit instead of Gen 1's 0–65,535 Stat Exp field. This control edits that exact native counterpart, rebuilds Speed, then saves a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Speed Stat Exp / EV"; textSize = 16f })
        speedEv = EditText(this).apply {
            hint = "0–252"; inputType = InputType.TYPE_CLASS_NUMBER; isEnabled = false; maxLines = 1
        }
        root.addView(speedEv)
        review = Button(this).apply {
            text = "Review Speed Stat Exp change"; isEnabled = false
            setOnClickListener { reviewSpeedStatExp() }
        }
        root.addView(review)
        defenseStatExp = Button(this).apply {
            text = "Back to Defense Stat Exp"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchSwordShieldPokemonSpeedStatExpActivity,
                    SwitchSwordShieldPokemonDefenseStatExpActivity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.box", box)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(defenseStatExp)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokémon"; setOnClickListener { if (!busy) finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(SwordShieldPokemonSpeedStatExpEditing.sha256(bytes) == expectedHash) {
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
                require(!pokemon.isEgg) { "Egg Speed Stat Exp / EV editing is not supported for Sword/Shield." }
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    current = pokemon
                    speedEv.setText(pokemon.evs[3].toString())
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { SwordShieldSpeciesNames.name(pokemon.species) }}\n" +
                        "Current Speed EV: ${pokemon.evs[3]} • Total EVs: ${pokemon.evs.sum()}/510\n" +
                        "Speed: ${pokemon.cachedStats[2]}\n" +
                        "Source SHA-256: ${SwordShieldPokemonSpeedStatExpEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        speedEv.isEnabled = !value && ready
        review.isEnabled = !value && ready
        defenseStatExp.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return SwordShieldPokemonSpeedStatExpEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = SwordShieldPokemonSpeedStatExpEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewSpeedStatExp() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = speedEv.text.toString().toIntOrNull()
        if (requested == null || requested !in 0..252) {
            status.text = "Speed EV must be a whole number between 0 and 252."
            return
        }
        val pokemon = current ?: return
        if (requested + pokemon.evs.filterIndexed { index, _ -> index != 3 }.sum() > 510) {
            status.text = "That Speed EV would exceed Sword/Shield's native 510 total EV limit."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                SwordShieldPokemonSpeedStatExpEditing.stage(
                    bytes, hash, selectedArea, box, position, requested
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure {
                        status.text = it.message ?: "Speed Stat Exp could not be staged."
                        setBusy(false)
                    }
            }
        }
    }

    private fun showReview(plan: SwordShieldSpeedStatExpCopyPlan) {
        var dispatched = false
        val beforeTotal = plan.before.evs.sum()
        val afterTotal = plan.before.evs.filterIndexed { index, _ -> index != 3 }.sum() + plan.after.speedEv
        AlertDialog.Builder(this).setTitle("Save this Speed Stat Exp to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { SwordShieldSpeciesNames.name(plan.before.species) }}\n" +
                    "Speed EV: ${plan.before.evs[3]} → ${plan.after.speedEv}\n" +
                    "Total EVs: $beforeTotal → $afterTotal / 510\n" +
                    "Speed: ${plan.before.cachedStats[2]} → ${plan.after.speed}\n\n" +
                    "Allowed encrypted scope: one 344-byte PK8 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PK8, only the Speed EV byte, derived Speed, and checksum may change. The other five EVs, every IV, Hyper Training, nature, HP, Attack, Defense and the other battle stats, Dynamax/Gigantamax data, trainer identity, species, nickname, moves, the unselected storage object, every other party and PC-box slot, and all unrelated bytes remain exact. " +
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
                inspectCopy.visibility = if (verifiedCopy != null)
                    android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Speed Stat Exp copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Speed Stat Exp copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                setBusy(false)
            }
        }
    }

    @Deprecated("Legacy Activity back callback")
    override fun onBackPressed() { if (!busy) super.onBackPressed() }
    override fun onDestroy() { pendingPlan = null; worker.shutdown(); super.onDestroy() }
}
