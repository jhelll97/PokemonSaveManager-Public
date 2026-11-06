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

/** Gen 1-shaped Pokémon editor for one native Legends: Arceus PA8 Special DV / IV. */
class SwitchLegendsArceusPokemonSpecialDvActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2143
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: LegendsArceusPokemonRead? = null
    private var pendingPlan: LegendsArceusSpecialDvCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var specialIv: EditText
    private lateinit var review: Button
    private lateinit var speedDv: Button
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
        val location = if (selectedArea == LegendsArceusPokemonArea.PASTURE)
            "Pasture $pasture • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Special DV / IV"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nSpecial follows Speed in Gen 1's DV section. Gen 1 has one Special DV; Legends: Arceus stores separate 0–31 Special Attack and Special Defense IVs. This shared control sets both native IVs to the reviewed value. Party PA8 records also store both derived Special stats; pasture PA8 records derive them when moved into the party."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"; textSize = 16f; setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Special DV / IV"; textSize = 16f })
        specialIv = EditText(this).apply {
            hint = "0–31"; inputType = InputType.TYPE_CLASS_NUMBER; isEnabled = false; maxLines = 1
        }
        root.addView(specialIv)
        review = Button(this).apply {
            text = "Review Special DV / IV change"; isEnabled = false
            setOnClickListener { reviewSpecialDv() }
        }
        root.addView(review)
        speedDv = Button(this).apply {
            text = "Back to Speed DV / IV"; isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selected = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsArceusPokemonSpecialDvActivity,
                    SwitchLegendsArceusPokemonSpeedDvActivity::class.java)
                    .setData(uri).putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selected.name)
                    .putExtra("pse.switch.pokemon.pasture", pasture)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(speedDv)
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
                require(LegendsArceusPokemonSpecialDvEditing.sha256(bytes) == expectedHash) {
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
                require(!pokemon.isEgg) { "Egg Special DV / IV editing is not supported for Legends: Arceus." }
                Triple(bytes, validation.version.displayName, pokemon)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, displayName, pokemon) ->
                    opened = bytes
                    current = pokemon
                    specialIv.setText(if (pokemon.ivs[4] == pokemon.ivs[5]) pokemon.ivs[4].toString() else "")
                    val specialAttackHyperTrained = pokemon.hyperTrainingFlags and (1 shl 3) != 0
                    val specialDefenseHyperTrained = pokemon.hyperTrainingFlags and (1 shl 4) != 0
                    val specialSummary = pokemon.cachedStats?.let {
                        "Derived Special Attack: ${it[3]} • Derived Special Defense: ${it[4]}"
                    } ?: "Pasture PA8: no cached Special-stat tail; the game derives both in party."
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { LegendsArceusSpeciesNames.name(pokemon.species) }}\n" +
                        "Current Special Attack IV: ${pokemon.ivs[4]}\n" +
                        "Current Special Defense IV: ${pokemon.ivs[5]}\n" +
                        "$specialSummary\n" +
                        "Hyper Training: Sp. Atk ${if (specialAttackHyperTrained) "active" else "off"}, Sp. Def ${if (specialDefenseHyperTrained) "active" else "off"}\n" +
                        "Source SHA-256: ${LegendsArceusPokemonSpecialDvEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The selected Pokémon could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null && current != null
        specialIv.isEnabled = !value && ready
        review.isEnabled = !value && ready
        speedDv.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsArceusPokemonSpecialDvEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsArceusPokemonSpecialDvEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewSpecialDv() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val requested = specialIv.text.toString().toIntOrNull()
        if (requested == null || requested !in 0..31) {
            status.text = "Special IV must be a whole number between 0 and 31."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LegendsArceusPokemonSpecialDvEditing.stage(
                    bytes, hash, selectedArea, pasture, position, requested
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure {
                        status.text = it.message ?: "The Special DV / IV could not be staged."
                        setBusy(false)
                    }
            }
        }
    }

    private fun showReview(plan: LegendsArceusSpecialDvCopyPlan) {
        var dispatched = false
        val specialAttackHyperTrained = plan.before.hyperTrainingFlags and (1 shl 3) != 0
        val specialDefenseHyperTrained = plan.before.hyperTrainingFlags and (1 shl 4) != 0
        val specialReview = if (plan.after.specialAttack != null && plan.after.specialDefense != null) {
            "Derived Special Attack: ${plan.before.cachedStats?.get(3)} → ${plan.after.specialAttack}\n" +
                "Derived Special Defense: ${plan.before.cachedStats?.get(4)} → ${plan.after.specialDefense}\n"
        } else "Pasture PA8: no cached Special-stat tail; only the two native Special IVs are stored.\n"
        val nativeScope = if (plan.after.specialAttack == null)
            "Inside the pasture PA8, only the paired five-bit Special Attack and Special Defense IV fields and checksum may change."
        else "Inside the party PA8, only the paired five-bit Special Attack and Special Defense IV fields, both derived Special stats, and checksum may change."
        AlertDialog.Builder(this).setTitle("Save this Special DV / IV to a new copy?")
            .setMessage(
                "${plan.location} • ${plan.before.nickname.ifBlank { LegendsArceusSpeciesNames.name(plan.before.species) }}\n" +
                    "Special Attack IV: ${plan.before.ivs[4]} → ${plan.after.specialIv}\n" +
                    "Special Defense IV: ${plan.before.ivs[5]} → ${plan.after.specialIv}\n" +
                    specialReview +
                    "Hyper Training: Sp. Atk ${if (specialAttackHyperTrained) "active (effective IV remains 31)" else "off"}; Sp. Def ${if (specialDefenseHyperTrained) "active (effective IV remains 31)" else "off"}\n\n" +
                    "Allowed encrypted scope: one ${plan.recordSize}-byte PA8 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "$nativeScope HP, Attack, Defense and Speed IVs, Hyper Training, Ganbaru values, nature, HP and the other three battle stats, Alpha and Noble state, friendship, trainer identity, species, nickname, moves, the unselected storage object, every other party and pasture slot, and all unrelated bytes remain exact. " +
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
                        val backup = SwitchSafeBackupStore.createVerified(
                            this, SwitchGameFamily.LEGENDS_ARCEUS, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LEGENDS_ARCEUS, backup.file, backup.sha256)
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
                        "Special DV / IV copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Special DV / IV copy could not be verified.") +
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
