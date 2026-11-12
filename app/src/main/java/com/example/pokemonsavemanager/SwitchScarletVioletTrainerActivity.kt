package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Gen 1-shaped Scarlet/Violet Trainer Data editor, preserving the reference field order. */
class SwitchScarletVioletTrainerActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val createCopyRequest = 2116
    private var sourceUri: Uri? = null
    private var expectedHash: String? = null
    private var opened: ByteArray? = null
    private var pendingPlan: ScarletVioletTrainerCopyPlan? = null
    private var busy = false
    private var verifiedCopy: Pair<Uri, String>? = null
    private var moneyAvailable = false
    private var openedMoney: Long? = null
    private lateinit var status: TextView
    private lateinit var name: EditText
    private lateinit var trainerId: EditText
    private lateinit var money: EditText
    private lateinit var badges: EditText
    private lateinit var playHours: EditText
    private lateinit var playMinutes: EditText
    private lateinit var playSeconds: EditText
    private lateinit var review: Button
    private lateinit var inspectCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent?.data
        expectedHash = intent?.getStringExtra("pse.switch.sha256")
        if (sourceUri == null || expectedHash.isNullOrBlank()) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(TextView(this).apply { text = "Trainer Data"; textSize = 28f })
        root.addView(TextView(this).apply {
            text = "Pokémon Scarlet/Violet\nEdit Trainer Data in the same field order as Gen 1 using the native Scarlet/Violet save layout. The selected source stays unchanged."
            textSize = 16f
        })
        status = TextView(this).apply {
            text = "Validating the selected source…"
            textSize = 16f
            setTextIsSelectable(true)
        }
        root.addView(status)
        root.addView(TextView(this).apply {
            text = "Trainer Name"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "Your in-game trainer name • 1–12 UTF-16 code units"
            textSize = 14f
        })
        name = EditText(this).apply { hint = "Trainer name" }
        root.addView(name)
        root.addView(TextView(this).apply {
            text = "Trainer ID"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "The native 16-bit ID attached to your trainer • 0–65535"
            textSize = 14f
        })
        trainerId = EditText(this).apply {
            hint = "Trainer ID"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(trainerId)
        root.addView(TextView(this).apply {
            text = "Money"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "Current money carried by your trainer • 0–9,999,999"
            textSize = 14f
        })
        money = EditText(this).apply {
            hint = "Money"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(money)
        root.addView(TextView(this).apply {
            text = "Badges"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "Number of Gym Badges earned • 0–8"
            textSize = 14f
        })
        badges = EditText(this).apply {
            hint = "Badges"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(badges)
        root.addView(TextView(this).apply {
            text = "Play Time"
            textSize = 18f
        })
        root.addView(TextView(this).apply {
            text = "Set the save's recorded play time. Minutes and seconds must be 0–59."
            textSize = 14f
        })
        root.addView(TextView(this).apply { text = "Hours • 0–65535"; textSize = 14f })
        playHours = EditText(this).apply {
            hint = "Hours"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(playHours)
        root.addView(TextView(this).apply { text = "Minutes • 0–59"; textSize = 14f })
        playMinutes = EditText(this).apply {
            hint = "Minutes"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(playMinutes)
        root.addView(TextView(this).apply { text = "Seconds • 0–59"; textSize = 14f })
        playSeconds = EditText(this).apply {
            hint = "Seconds"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        root.addView(playSeconds)
        review = Button(this).apply {
            text = "Review and save new copy"
            setOnClickListener { reviewChange() }
        }
        root.addView(review)
        inspectCopy = Button(this).apply {
            text = "Inspect verified copy"
            visibility = android.view.View.GONE
            setOnClickListener { inspectVerifiedCopy() }
        }
        root.addView(inspectCopy)
        root.addView(Button(this).apply {
            text = "Back to Trainer Data"
            setOnClickListener { if (!busy) finish() }
        })
        OmegaRubyUi.styleTrainerEditor(this, root)
        setContentView(OmegaRubyUi.scrollingPage(this, root))

        setBusy(true)
        worker.execute {
            val result = runCatching {
                val bytes = readSource()
                require(ScarletVioletTrainerEditing.sha256(bytes) == expectedHash) {
                    "The source changed. Reopen it from PSE."
                }
                Triple(bytes, ScarletVioletTrainerReadOnly.read(bytes), ScarletVioletPlayTimeReadOnly.read(bytes))
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (bytes, trainer, playTime) ->
                    opened = bytes
                    name.setText(trainer.name)
                    trainerId.setText(trainer.tid16.toString())
                    moneyAvailable = trainer.money != null
                    openedMoney = trainer.money
                    money.setText(trainer.money?.toString().orEmpty())
                    if (!moneyAvailable) money.hint = "Not present in this save"
                    badges.setText(trainer.badgeCount.toString())
                    playHours.setText(playTime.hours.toString())
                    playMinutes.setText(playTime.minutes.toString())
                    playSeconds.setText(playTime.seconds.toString())
                    status.text = "${trainer.version.displayName}\nCurrent trainer name: ${trainer.name.ifBlank { "Not set" }}\n" +
                        "Current Trainer ID: ${trainer.tid16}\n" +
                        "Current Money: ${trainer.money?.toString() ?: "Not present"}\n" +
                        "Current Badges: ${trainer.badgeCount} / 8\n" +
                        "Current Play Time: ${playTime.savedTimeText}\n" +
                        "Source SHA-256: ${ScarletVioletTrainerEditing.sha256(bytes)}"
                }.onFailure { status.text = it.message ?: "The save could not be opened safely." }
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        val ready = opened != null
        name.isEnabled = !value && ready
        trainerId.isEnabled = !value && ready
        money.isEnabled = !value && ready && moneyAvailable
        badges.isEnabled = !value && ready
        playHours.isEnabled = !value && ready
        playMinutes.isEnabled = !value && ready
        playSeconds.isEnabled = !value && ready
        review.isEnabled = !value && ready
        inspectCopy.isEnabled = !value && verifiedCopy != null
    }

    private fun reviewChange() {
        if (busy || pendingPlan != null) return
        val bytes = opened ?: return
        val hash = expectedHash ?: return
        val requestedTrainerId = trainerId.text.toString().toIntOrNull()
        if (requestedTrainerId == null || requestedTrainerId !in 0..65535) {
            status.text = "Trainer ID must be a whole number between 0 and 65535."
            return
        }
        val requestedMoney = if (moneyAvailable) money.text.toString().toLongOrNull() else null
        if (moneyAvailable && (requestedMoney == null ||
                (requestedMoney != openedMoney && requestedMoney !in 0L..9_999_999L))) {
            status.text = "Money must be a whole number between 0 and 9,999,999."
            return
        }
        val requestedBadges = badges.text.toString().toIntOrNull()
        if (requestedBadges == null || requestedBadges !in 0..8) {
            status.text = "Badges must be a whole number between 0 and 8."
            return
        }
        val requestedPlayHours = playHours.text.toString().toIntOrNull()
        if (requestedPlayHours == null || requestedPlayHours !in 0..65535) {
            status.text = "Play Time hours must be a whole number between 0 and 65535."
            return
        }
        val requestedPlayMinutes = playMinutes.text.toString().toIntOrNull()
        if (requestedPlayMinutes == null || requestedPlayMinutes !in 0..59) {
            status.text = "Play Time minutes must be a whole number between 0 and 59."
            return
        }
        val requestedPlaySeconds = playSeconds.text.toString().toIntOrNull()
        if (requestedPlaySeconds == null || requestedPlaySeconds !in 0..59) {
            status.text = "Play Time seconds must be a whole number between 0 and 59."
            return
        }
        val plan = try {
            ScarletVioletTrainerEditing.stage(
                bytes, hash, name.text.toString(), requestedTrainerId, requestedMoney, requestedBadges,
                requestedPlayHours, requestedPlayMinutes, requestedPlaySeconds
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The requested trainer change is unavailable."
            return
        }
        setBusy(true)
        var dispatched = false
        val changes = buildList {
            if (plan.nameChanged) add("Trainer Name\n${plan.beforeName.ifBlank { "(blank)" }} → ${plan.afterName}")
            if (plan.trainerIdChanged) add("Trainer ID\n${plan.beforeTrainerId} → ${plan.afterTrainerId}")
            if (plan.moneyChanged) add("Money\n${plan.beforeMoney} → ${plan.afterMoney}")
            if (plan.badgesChanged) add("Badges\n${plan.beforeBadgeCount} → ${plan.afterBadgeCount}")
            if (plan.playTimeChanged) add(
                "Play Time\n${playTimeText(plan.beforePlayHours, plan.beforePlayMinutes, plan.beforePlaySeconds)} → " +
                    playTimeText(plan.afterPlayHours, plan.afterPlayMinutes, plan.afterPlaySeconds)
            )
        }.joinToString("\n\n")
        val scopes = buildList {
            if (plan.nameChanged) add("the 26-byte native trainer-name field at encrypted container offset 0x${plan.nameOffset.toString(16).uppercase()}")
            if (plan.trainerIdChanged) add("the 2-byte native Trainer ID field at encrypted container offset 0x${plan.trainerIdOffset.toString(16).uppercase()}")
            if (plan.moneyChanged) add("the 4-byte native Money field at encrypted container offset 0x${plan.moneyOffset.toString(16).uppercase()}")
            if (plan.badgesChanged) add("${plan.badgeOffsets.size} native Gym-clear flag byte${if (plan.badgeOffsets.size == 1) "" else "s"} at encrypted container offsets ${plan.badgeOffsets.joinToString { "0x${it.toString(16).uppercase()}" }}")
            if (plan.playTimeChanged) add("the 12-byte native Play Time object at encrypted container offset 0x${plan.playTimeOffset.toString(16).uppercase()}")
        }.joinToString(" and ")
        AlertDialog.Builder(this)
            .setTitle("Save Trainer Data to a new copy?")
            .setMessage(
                "$changes\n\n" +
                    "Allowed byte scope: $scopes plus the native SHA-256 footer. " +
                    "PSE will verify a recovery backup and reopen the persisted copy. Choose a NEW empty document; the selected source cannot be replaced."
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
                        startActivityForResult(
                            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "application/octet-stream"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                putExtra(Intent.EXTRA_TITLE, "SaveData.bin")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            }, createCopyRequest
                        )
                    } catch (failure: Exception) {
                        pendingPlan = null
                        setBusy(false)
                        status.text = failure.message ?: "The save picker could not be opened. Nothing was written."
                    }
                }
            }.show()
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
                            this, SwitchGameFamily.SCARLET_VIOLET, original
                        )
                        backupName = backup.file.name
                        SwitchSafeBackupStore.readVerified(
                            this, SwitchGameFamily.SCARLET_VIOLET, backup.file, backup.sha256
                        )
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
                inspectCopy.visibility = if (verifiedCopy != null) android.view.View.VISIBLE else android.view.View.GONE
                status.text = result.fold(
                    onSuccess = {
                        val changes = buildList {
                            if (plan.nameChanged) add("Trainer Name: ${plan.beforeName.ifBlank { "(blank)" }} → ${plan.afterName}")
                            if (plan.trainerIdChanged) add("Trainer ID: ${plan.beforeTrainerId} → ${plan.afterTrainerId}")
                            if (plan.moneyChanged) add("Money: ${plan.beforeMoney} → ${plan.afterMoney}")
                            if (plan.badgesChanged) add("Badges: ${plan.beforeBadgeCount} → ${plan.afterBadgeCount}")
                            if (plan.playTimeChanged) add(
                                "Play Time: ${playTimeText(plan.beforePlayHours, plan.beforePlayMinutes, plan.beforePlaySeconds)} → " +
                                    playTimeText(plan.afterPlayHours, plan.afterPlayMinutes, plan.afterPlaySeconds)
                            )
                        }.joinToString("\n")
                        "Trainer Data copy saved and reopened successfully.\n" +
                            "$changes\n" +
                            "SHA-256: ${plan.editedSha256}\nRecovery backup: $backupName\nOriginal source unchanged."
                    },
                    onFailure = {
                        (it.message ?: "The copy could not be verified.") +
                            "\nRecovery backup: ${backupName ?: "not created"}"
                    }
                )
                setBusy(false)
            }
        }
    }

    private fun inspectVerifiedCopy() {
        if (busy) return
        val (uri, hash) = verifiedCopy ?: return
        try {
            startActivity(
                Intent(this, SwitchReadOnlyActivity::class.java)
                    .setData(uri)
                    .putExtra("pse.switch.family", SwitchGameFamily.SCARLET_VIOLET.name)
                    .putExtra("pse.switch.sha256", hash)
                    .putExtra("pse.switch.section", "trainer")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (failure: Exception) {
            status.text = failure.message ?: "The verified copy could not be opened for inspection."
        }
    }

    private fun readSource(): ByteArray {
        val uri = sourceUri ?: error("Reopen the source from PSE.")
        require(uri.scheme == "content") { "A document source is required." }
        return readDocument(uri)
    }

    private fun readDocument(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: error("The document cannot be read.")
        return ScarletVioletTrainerEditing.readDocumentBytes(input)
    }

    private fun playTimeText(hours: Int, minutes: Int, seconds: Int): String =
        "${hours.toString().padStart(4, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

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
