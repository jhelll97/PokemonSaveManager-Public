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

private data class LetsGoExperienceOpen(
    val bytes: ByteArray,
    val displayName: String,
    val pokemon: LetsGoPokemonRead,
    val maximumExperience: Long
)

/** Gen 1-shaped Pokémon editor for one native Let's Go PB7 Experience field. */
class SwitchLetsGoPokemonExperienceActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2133
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var storageIndex = -1
    private var opened: ByteArray? = null
    private var current: LetsGoPokemonRead? = null
    private var pendingPlan: LetsGoExperienceCopyPlan? = null
    private var verifiedCopy: Pair<Uri, String>? = null
    private var busy = false
    private lateinit var status: TextView
    private lateinit var experience: EditText
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        storageIndex = intent?.getIntExtra("pse.switch.pokemon.index", -1) ?: -1
        if (sourceUri == null || expectedHash.isNullOrBlank() || storageIndex !in 0 until 1000) {
            finish()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Let's Go • Pokémon Boxes"; textSize = 24f })
        root.addView(TextView(this).apply { text = "Edit Pokémon • Experience"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Change the next Gen 1 Pokémon editor field in one native PB7. PSE derives Level from the exact EXP value and rebuilds native cached battle values."
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
            text = "Experience follows Level in the Gen 1 BASIC field order. The value must fit this species' native growth curve through Level 100. PSE derives Level and rebuilds HP, battle stats and CP from base stats, form, nature, IVs, Hyper Training, Awakening Values and friendship. Eggs and unverified forms remain protected."
            textSize = 14f
        })
        review = Button(this).apply {
            text = "Review Experience change"
            isEnabled = false
            setOnClickListener { reviewExperience() }
        }
        root.addView(review)
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
        setContentView(ScrollView(this).apply { addView(root) })

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(LetsGoPokemonExperienceEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                val validation = LetsGoSaveValidation.inspect(bytes)
                require(validation is LetsGoValidation.Verified) {
                    "A validated native Let's Go save is required."
                }
                val pokemon = (LetsGoStorageReadOnly.read(bytes).slot(storageIndex).read
                    as? LetsGoSlotRead.Pokemon)?.value
                    ?: error("The selected native PB7 is not checksum-valid.")
                require(!pokemon.isEgg) { "Egg experience editing is not supported for Let's Go." }
                val maximumExperience = LetsGoPokemonExperienceEditing.maximumExperience(
                    pokemon.species, pokemon.form
                ) ?: error(
                    "This PB7 form has no verified native Let's Go stat entry, so Experience remains protected."
                )
                LetsGoExperienceOpen(bytes, validation.version.displayName, pokemon, maximumExperience)
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
                    status.text = "${displayName} • Native integrity verified\n" +
                        "Storage #${storageIndex + 1} • ${pokemon.nickname.ifBlank { LetsGoSpeciesNames.name(pokemon.species) }}\n" +
                        "Current Level: ${pokemon.cachedLevel} • EXP: ${pokemon.experience}\n" +
                        "Native Level 100 maximum: ${openedSource.maximumExperience}\n" +
                        "HP ${pokemon.currentHp}/${pokemon.maximumHp} • CP ${pokemon.combatPower}\n" +
                        "Source SHA-256: ${LetsGoPokemonExperienceEditing.sha256(bytes)}"
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
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return LetsGoPokemonExperienceEditing.readSaveBytes(
            contentResolver.openInputStream(uri) ?: error("The source document cannot be read.")
        )
    }

    private fun readDocument(uri: Uri): ByteArray = LetsGoPokemonExperienceEditing.readDocumentBytes(
        contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
    )

    private fun reviewExperience() {
        if (busy) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val target = experience.text.toString().toLongOrNull()
        if (target == null || target !in 0L..0xFFFFFFFFL) {
            status.text = "Experience must be a non-negative whole number."
            return
        }
        setBusy(true)
        worker.execute {
            val result = runCatching {
                LetsGoPokemonExperienceEditing.stage(bytes, hash, storageIndex, target)
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

    private fun showReview(plan: LetsGoExperienceCopyPlan) {
        var dispatched = false
        val before = plan.before
        val after = plan.after
        AlertDialog.Builder(this).setTitle("Save this Experience to a new copy?")
            .setMessage(
                "Storage #${plan.storageIndex + 1} • ${before.nickname.ifBlank { LetsGoSpeciesNames.name(before.species) }}\n" +
                    "Experience: ${before.experience} → ${after.experience}\n" +
                    "Derived Level: ${before.cachedLevel} → ${after.level}\n" +
                    "Native Level 100 maximum: ${plan.maximumExperience}\n" +
                    "HP: ${before.currentHp}/${before.maximumHp} → ${after.currentHp}/${after.maximumHp}\n" +
                    "Attack: ${before.attack} → ${after.attack}\n" +
                    "Defense: ${before.defense} → ${after.defense}\n" +
                    "Speed: ${before.speed} → ${after.speed}\n" +
                    "Sp. Atk: ${before.specialAttack} → ${after.specialAttack}\n" +
                    "Sp. Def: ${before.specialDefense} → ${after.specialDefense}\n" +
                    "CP: ${before.combatPower} → ${after.combatPower}\n\n" +
                    "Allowed native scope: one 260-byte PB7 record at 0x${plan.recordOffset.toString(16).uppercase()} and block 9's two-byte CRC. " +
                    "Inside the PB7, only Experience, derived Level, current/max HP, five cached battle stats, CP, and checksum may change. Species, form, nickname, moves, trainer identity, party and partner pointers, every other Pokémon, and all unrelated bytes remain exact. " +
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
                            putExtra(Intent.EXTRA_TITLE, "SaveData.bin")
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
            .putExtra("pse.switch.family", SwitchGameFamily.LETS_GO_PIKACHU_EEVEE.name)
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
                            this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, original)
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, backup.file, backup.sha256)
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
