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

/** Gen 1-shaped Pokémon editor mapping Special Stat Exp to Legends: Z-A's paired native Special EVs. */
class SwitchLegendsZaPokemonSpecialStatExpActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2189
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsZaPokemonArea? = null
    private var box = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: LegendsZaPokemonRead? = null
    private var pendingPlan: LegendsZaSpecialStatExpCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var specialEv: EditText
    private lateinit var review: Button
    private lateinit var speedStatExp: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        area = intent?.getStringExtra("pse.switch.pokemon.area")?.let { value ->
            LegendsZaPokemonArea.values().firstOrNull { it.name == value }
        }
        box = intent?.getIntExtra("pse.switch.pokemon.box", 0) ?: 0
        position = intent?.getIntExtra("pse.switch.pokemon.position", 0) ?: 0
        val selectedArea = area
        if (sourceUri == null || expectedHash.isNullOrBlank() || selectedArea == null ||
            position !in 1..30 || (selectedArea == LegendsZaPokemonArea.BOX && box !in 1..32) ||
            (selectedArea == LegendsZaPokemonArea.PARTY && position !in 1..6)) {
            finish(); return
        }
        val location = if (selectedArea == LegendsZaPokemonArea.BOX)
            "Box $box • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Z-A • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Special Stat Exp"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nSpecial Stat Exp follows Speed Stat Exp in Gen 1. Gen 1 has one Special Stat Exp field; Legends: Z-A stores separate 0–252 Special Attack and Special Defense EVs under a shared 510 total EV limit. This shared control applies one reviewed value to both native counterparts, rebuilds both Special stats, then saves a verified new copy."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Special Stat Exp / paired EVs"; textSize = 16f })
        specialEv = EditText(this).apply {
            hint = "0–252"; inputType = InputType.TYPE_CLASS_NUMBER; isEnabled = false; maxLines = 1
        }
        root.addView(specialEv)
        review = Button(this).apply {
            text = "Review Special Stat Exp change"; isEnabled = false
            setOnClickListener { reviewSpecialStatExp() }
        }
        root.addView(review)
        speedStatExp = Button(this).apply {
            text = "Back to Speed Stat Exp"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsZaPokemonSpecialStatExpActivity,
                    SwitchLegendsZaPokemonSpeedStatExpActivity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.box", box)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(speedStatExp)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"; visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Pokémon"; setOnClickListener { if (!busy) finish() }
        })
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsZaPokemonSpecialStatExpEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = LegendsZaSaveValidation.inspect(bytes)
                require(validation is LegendsZaValidation.Verified) {
                    "A validated native Legends: Z-A save is required."
                }
                val storage = LegendsZaStorageReadOnly.read(bytes)
                val read = if (selectedArea == LegendsZaPokemonArea.BOX)
                    storage.box(box - 1)[position - 1].read else storage.party()[position - 1].read
                val pokemon = (read as? LegendsZaSlotRead.Pokemon)?.value
                    ?: error("The selected native PA9 is not checksum-valid.")
                require(!pokemon.isEgg) { "Egg Special Stat Exp / EV editing is not supported for Legends: Z-A." }
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    current = pokemon
                    specialEv.setText(if (pokemon.evs[4] == pokemon.evs[5]) pokemon.evs[4].toString() else "")
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { ScarletVioletSpeciesNames.name(pokemon.species) }}\n" +
                        "Current Special Attack EV: ${pokemon.evs[4]} • Special Attack: ${pokemon.cachedStats[3]}\n" +
                        "Current Special Defense EV: ${pokemon.evs[5]} • Special Defense: ${pokemon.cachedStats[4]}\n" +
                        "Total EVs: ${pokemon.evs.sum()}/510\n" +
                        "Source SHA-256: ${LegendsZaPokemonSpecialStatExpEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        specialEv.isEnabled = !value && ready
        review.isEnabled = !value && ready
        speedStatExp.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsZaPokemonSpecialStatExpEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsZaPokemonSpecialStatExpEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewSpecialStatExp() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = specialEv.text.toString().toIntOrNull()
        if (requested == null || requested !in 0..252) {
            status.text = "Special EV must be a whole number between 0 and 252."
            return
        }
        val pokemon = current ?: return
        if (requested * 2 +
            pokemon.evs.filterIndexed { index, _ -> index != 4 && index != 5 }.sum() > 510) {
            status.text = "Those paired Special EVs would exceed Legends: Z-A's native 510 total EV limit."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LegendsZaPokemonSpecialStatExpEditing.stage(
                    bytes, hash, selectedArea, box, position, requested
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure {
                        status.text = it.message ?: "Special Stat Exp could not be staged."
                        setBusy(false)
                    }
            }
        }
    }

    private fun showReview(plan: LegendsZaSpecialStatExpCopyPlan) {
        var dispatched = false
        val beforeTotal = plan.before.evs.sum()
        val afterTotal = plan.before.evs.filterIndexed { index, _ -> index != 4 && index != 5 }.sum() +
            plan.after.specialEv * 2
        AlertDialog.Builder(this).setTitle("Save this Special Stat Exp to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { ScarletVioletSpeciesNames.name(plan.before.species) }}\n" +
                    "Special Attack EV: ${plan.before.evs[4]} → ${plan.after.specialEv}\n" +
                    "Special Defense EV: ${plan.before.evs[5]} → ${plan.after.specialEv}\n" +
                    "Total EVs: $beforeTotal → $afterTotal / 510\n" +
                    "Special Attack: ${plan.before.cachedStats[3]} → ${plan.after.specialAttack}\n" +
                    "Special Defense: ${plan.before.cachedStats[4]} → ${plan.after.specialDefense}\n\n" +
                    "Allowed encrypted scope: one 344-byte PA9 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "Inside the PA9, only the paired Special EV bytes, both derived Special stats, and checksum may change. The other four EVs, every IV, Hyper Training, nature, HP, Attack, Defense, Speed and their battle stats, Alpha state, ball, scale, friendship, trainer identity, species, nickname, moves, the target slot footer, the unselected storage object, every other party and PC-box slot, and all unrelated bytes remain exact. " +
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
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ZA.name)
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
                            this, SwitchGameFamily.LEGENDS_ZA, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ZA, backup.file, backup.sha256)
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
                        "Special Stat Exp copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Special Stat Exp copy could not be verified.") +
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
