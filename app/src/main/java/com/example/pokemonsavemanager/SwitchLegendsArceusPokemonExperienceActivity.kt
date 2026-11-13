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

private data class LegendsArceusExperienceOpen(
    val bytes: ByteArray,
    val displayName: String,
    val pokemon: LegendsArceusPokemonRead,
    val currentLevel: Int,
    val maximumExperience: Long
)

/** Gen 1-shaped Pokémon editor for one native Legends: Arceus PA8 Experience field. */
class SwitchLegendsArceusPokemonExperienceActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2134
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var area: LegendsArceusPokemonArea? = null
    private var pasture = 0
    private var position = 0
    private var opened: ByteArray? = null
    private var current: LegendsArceusPokemonRead? = null
    private var pendingPlan: LegendsArceusExperienceCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var experience: EditText
    private lateinit var review: Button
    private lateinit var originalTrainer: Button
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
            finish()
            return
        }
        val location = if (selectedArea == LegendsArceusPokemonArea.PASTURE)
            "Pasture $pasture • Slot $position" else "Party $position"
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Legends: Arceus • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Experience"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "$location\nChange the next Gen 1 Pokémon editor field in one native PA8. PSE derives Level from the exact EXP value and rebuilds native cached battle values where the party format stores them."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply { text = "Experience"; textSize = 16f })
        experience = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            isEnabled = false
        }
        root.addView(experience)
        root.addView(TextView(this).apply {
            text = "Experience follows Level in the Gen 1 BASIC field order. The exact value must fit the native growth curve through Level 100. Pasture PA8 records store EXP without a cached Level, so PSE changes only EXP there. Party PA8 records also rebuild Level, current/max HP and all five battle stats from Legends: Arceus base stats, stat nature, IVs, Hyper Training and Ganbaru values. Eggs and unverified forms remain protected."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review Experience change"
            isEnabled = false
            setOnClickListener { reviewExperience() }
        }
        root.addView(review)
        originalTrainer = Button(this).apply {
            text = "Next field: Original Trainer name"
            isEnabled = false
            setOnClickListener {
                val uri = sourceUri ?: return@setOnClickListener
                val hash = expectedHash ?: return@setOnClickListener
                val selectedArea = area ?: return@setOnClickListener
                startActivity(Intent(this@SwitchLegendsArceusPokemonExperienceActivity,
                    SwitchLegendsArceusPokemonOriginalTrainerActivity::class.java)
                    .setData(uri)
                    .putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.pokemon.area", selectedArea.name)
                    .putExtra("pse.switch.pokemon.pasture", pasture)
                    .putExtra("pse.switch.pokemon.position", position)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }
        root.addView(originalTrainer)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Level"
            setOnClickListener { if (!busy) finish() }
        })
        OmegaRubyUi.stylePokemonEditor(this, root, status)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LegendsArceusPokemonExperienceEditing.sha256(bytes) == expectedHash) {
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
                require(!pokemon.isEgg) { "Egg experience editing is not supported for Legends: Arceus." }
                val currentLevel = LegendsArceusPokemonExperienceEditing.currentLevel(pokemon)
                val maximumExperience = LegendsArceusPokemonExperienceEditing.maximumExperience(pokemon)
                LegendsArceusExperienceOpen(
                    bytes, validation.version.displayName, pokemon, currentLevel, maximumExperience
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { openedSource ->
                    val bytes = openedSource.bytes
                    val displayName = openedSource.displayName
                    val pokemon = openedSource.pokemon
                    opened = bytes
                    current = pokemon
                    experience.setText(pokemon.experience.toString())
                    val cacheSummary = if (pokemon.cachedLevel != null)
                        "Party HP ${pokemon.currentHp}/${pokemon.maximumHp}\n" else
                        "Pasture record: EXP is the native Level source; no party stat cache is stored.\n"
                    status.text = "$displayName • Native integrity verified\n" +
                        "$location • ${pokemon.nickname.ifBlank { LegendsArceusSpeciesNames.name(pokemon.species) }}\n" +
                        "Current Level: ${openedSource.currentLevel} • EXP: ${pokemon.experience}\n" +
                        "Native Level 100 maximum: ${openedSource.maximumExperience}\n" +
                        cacheSummary +
                        "Source SHA-256: ${LegendsArceusPokemonExperienceEditing.sha256(bytes)}"
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
        experience.isEnabled = !value && ready
        review.isEnabled = !value && ready
        originalTrainer.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LegendsArceusPokemonExperienceEditing.readDocumentBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LegendsArceusPokemonExperienceEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewExperience() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val selectedArea = area ?: return
        val target = experience.text.toString().toLongOrNull()
        if (target == null || target !in 0L..0xFFFFFFFFL) {
            status.text = "Experience must be a non-negative whole number."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LegendsArceusPokemonExperienceEditing.stage(
                    bytes, hash, selectedArea, pasture, position, target
                )
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { showReview(it) }
                    .onFailure {
                        status.text = it.message ?: "The Experience could not be staged."
                        setBusy(false)
                    }
            }
        }
    }

    private fun showReview(plan: LegendsArceusExperienceCopyPlan) {
        var dispatched = false
        val before = plan.before
        val after = plan.after
        val afterStats = after.cachedStats
        val partyReview = if (afterStats != null) {
            "HP: ${before.currentHp}/${before.maximumHp} → ${after.currentHp}/${after.maximumHp}\n" +
                "Attack: ${before.cachedStats?.get(0)} → ${afterStats[0]}\n" +
                "Defense: ${before.cachedStats?.get(1)} → ${afterStats[1]}\n" +
                "Speed: ${before.cachedStats?.get(2)} → ${afterStats[2]}\n" +
                "Sp. Atk: ${before.cachedStats?.get(3)} → ${afterStats[3]}\n" +
                "Sp. Def: ${before.cachedStats?.get(4)} → ${afterStats[4]}\n"
        } else "Pasture PA8: EXP changes; current HP and the absent party cache remain unchanged.\n"
        val nativeScope = if (plan.area == LegendsArceusPokemonArea.PARTY)
            "Inside the PA8, only EXP, Level, current/max HP, five cached battle stats, and checksum may change."
        else "Inside the PA8, only EXP and checksum may change; the 360-byte pasture format has no cached Level or battle-stat tail."
        AlertDialog.Builder(this).setTitle("Save this Experience to a new copy?")
            .setMessage(
                "${plan.location} • ${before.nickname.ifBlank { LegendsArceusSpeciesNames.name(before.species) }}\n" +
                    "Experience: ${before.experience} → ${after.experience}\n" +
                    "Derived Level: ${plan.beforeLevel} → ${after.level}\n" +
                    "Native Level 100 maximum: ${plan.maximumExperience}\n" +
                    partyReview + "\n" +
                    "Allowed encrypted scope: one ${plan.recordSize}-byte PA8 slot at 0x${plan.absoluteOffset.toString(16).uppercase()} and the native SHA-256 footer. " +
                    "$nativeScope Species, form, Alpha and Noble state, nickname, moves, Ganbaru values, friendship, trainer identity, every other party and pasture slot, and all unrelated bytes remain exact. " +
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
            .putExtra("pse.switch.family", SwitchGameFamily.LEGENDS_ARCEUS.name)
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
                inspectCopy.visibility =
                    if (verifiedCopy != null) android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        "Experience copy saved and reopened successfully.\nSHA-256: ${plan.editedSha256}\n" +
                            "Recovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The Experience copy could not be verified.") +
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
