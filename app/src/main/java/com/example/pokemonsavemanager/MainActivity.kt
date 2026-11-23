package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.os.Handler
import android.os.Looper
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Executors
import android.widget.*
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : Activity() {
    private val PICK_SWITCH_SOURCE = 1015
    private val PICK_SWITCH_SOURCE_TREE = 1016
    private val PICK_ALL_SOURCE_TREE = 1017
    private var activeSwitchSave: ScannedSave? = null
    private var sourceScanCancellation: java.util.concurrent.atomic.AtomicBoolean? = null
    private val PICK_SAVE = 1001
    private val EXPORT_SAVE = 1002
    private val EXPORT_GEN2_SAVE = 1014
    private val PICK_AZAHAR_TREE = 1003
    private val PICK_SAFE_FOLDER = 1004
    private val PICK_PIZZABOYC_TREE = 1005
    private val PICK_PIZZABOYC_SAVE = 1006
    private val PICK_GBA_TREE = 1007
    private val PICK_GBA_SAVE = 1008
    private val PICK_DS_TREE = 1009
    private val PICK_DS_SAVE = 1010
    private val REPAIR_REMEMBERED_SAVE = 1011
    private val PICK_GEN6_CONVERTER_TEMPLATE = 1012
    private val EXPORT_GEN6_CONVERTED_SAVE = 1013
    private val BG = Color.rgb(7, 16, 24)
    private val CARD = Color.rgb(13, 38, 55)
    private val TEXT = Color.WHITE
    private val MUTED = Color.rgb(160, 180, 194)
    private val GOOD = Color.rgb(120, 220, 140)
    private val WARN = Color.rgb(255, 190, 90)
    private val ACCENT = Color.rgb(70, 190, 255)

    private var importedFile: File? = null
    private var currentSourceUri: Uri? = null
    private var currentSourceLabel: String? = null
    private data class PendingGen6UiSource(
        val filePath: String,
        val sourceBytes: ByteArray,
        val sourceUri: String?,
        val preview: Gen6ConvertSavePreview
    )
    private data class PendingGen6UiExport(
        val filePath: String,
        val sourceBytes: ByteArray,
        val sourceUri: String?,
        val templateUri: String,
        val templateBytes: ByteArray,
        val prepared: Gen6PreparedSave
    )
    private var pendingGen6UiSource: PendingGen6UiSource? = null
    private var pendingGen6UiExport: PendingGen6UiExport? = null
    private data class PendingGen2SaveExport(val file:File,val sourceUri:Uri?,val sourceLabel:String?,val bytes:ByteArray,val profileKey:String)
    private var pendingGen2SaveExport:PendingGen2SaveExport?=null
    private var rememberedSaveRepairTarget: RememberedSave? = null
    private val imageExecutor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var saveCard: LinearLayout
    private val metaPrefs by lazy { getSharedPreferences("pse_pokemon_meta", MODE_PRIVATE) }
    private val choiceCatalogCache = mutableMapOf<String, List<NamedChoice>>()
    private var pokemonMultiSelectMode = false
    private val selectedPokemonSlots = linkedSetOf<Int>()
    private var phoneBackAction: (() -> Unit)? = null
    private var bankViewGeneration = 1
    private var bankViewBox = 0
    private var bankMultiSelectMode = false
    private val selectedBankFiles = linkedSetOf<String>()
    private var gen1BoxMultiSelectMode = false
    private val selectedGen1BoxSlots = linkedSetOf<Int>()
    private var gen1PartyMultiSelectMode = false
    private val selectedGen1PartySlots = linkedSetOf<Int>()
    private var gen2BoxMultiSelectMode = false
    private val selectedGen2BoxSlots = linkedSetOf<Int>()
    private var gen2PartyMultiSelectMode = false
    private val selectedGen2PartySlots = linkedSetOf<Int>()
    private var gen3BoxMultiSelectMode = false
    private var selectedGen3BoxIndex = 0
    private val selectedGen3BoxSlots = linkedSetOf<Int>()

    data class SaveAnalysis(
        val family: String,
        val supported: Boolean,
        val expectedSize: Int?,
        val actualSize: Int,
        val footerValid: Boolean,
        val sha256: String,
        val note: String,
        val trainer: TrainerData?,
        val validationDetail: String? = null
    ) {
        val structurallyValid: Boolean get() = supported && footerValid && expectedSize == actualSize
    }

    data class ScannedSave(
        val uri: Uri,
        val displayName: String,
        val relativePath: String,
        val analysis: SaveAnalysis,
        val detectedGame: String,
        val generation: Int,
        val switchFamily: String = ""
    )

    data class RememberedSave(
        val uri: String,
        val displayName: String,
        val relativePath: String,
        val detectedGame: String,
        val generation: Int,
        val lastSeen: Long,
        val trainerName: String = "",
        val baselineHash: String = "",
        val status: String = "Ready",
        val switchFamily: String = ""
    )

    data class NamedChoice(val id: Int, val name: String) {
        override fun toString(): String = if (id == 0) name else "$name (#$id)"
    }

    data class TrainerData(
        val trainerName: String,
        val tid: Int,
        val sid: Int,
        val gameId: Int,
        val exactGame: String,
        val gameMatchesFamily: Boolean,
        val gender: String,
        val language: String,
        val money: Long,
        val badges: Int,
        val playedHours: Int,
        val playedMinutes: Int,
        val playedSeconds: Int,
        val playedFrames: Int = 0
    )

    data class SourceValidation(
        val ok: Boolean,
        val readable: Boolean,
        val writable: Boolean,
        val structurallyValid: Boolean,
        val gameMatches: Boolean,
        val changedExternally: Boolean,
        val sourceHash: String?,
        val sourceGame: String?,
        val message: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importedFile = null
        currentSourceUri = null
        currentSourceLabel = null
        showHome()
    }

    override fun onDestroy() {
        sourceScanCancellation?.set(true)
        super.onDestroy()
    }

    override fun onBackPressed() {
        val action = phoneBackAction
        if (action != null) {
            action()
        } else {
            super.onBackPressed()
        }
    }

    private fun showHome() {
        resetGen3BoxSelection()
        phoneBackAction = null
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(7, 16, 24)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(42), dp(18), dp(28))
            setBackgroundColor(Color.rgb(7, 16, 24))
        }
        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "PSE"
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            setTextColor(Color.rgb(70, 190, 255))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "⚙"
            textSize = 26f
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Pokémon Save Editor"
            textSize = 15f
            setTextColor(MUTED)
            setPadding(dp(2), 0, 0, dp(18))
        })

        statusText = TextView(this).apply { visibility = View.GONE }
        root.addView(statusText)

        root.addView(TextView(this).apply {
            text = "CURRENT SAVE"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ACCENT)
            setPadding(dp(4), 0, 0, dp(8))
        })
        saveCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = roundedBg(Color.rgb(12, 35, 51), Color.rgb(25, 145, 220), 16)
            isClickable = false
            isFocusable = false
        }
        root.addView(saveCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        refreshSaveCard()

        addRememberedSavesSection(root)

        root.addView(TextView(this).apply {
            text = "EDITOR"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ACCENT)
            setPadding(dp(4), 0, 0, dp(8))
        })
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun row(a: String, aa: () -> Unit, b: String, bb: () -> Unit) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            addDashboardCard(r, a, aa, 1f)
            addDashboardCard(r, b, bb, 1f)
            grid.addView(r, LinearLayout.LayoutParams(-1, -2))
        }
        row("◎  Trainer Data", { showTrainerData() }, "◉  Pokémon Boxes", { if (!openActiveSwitchSection("storage")) showPokemonViewer() })
        row("◇  PSE Bank", { showBank() }, "▤  Safe Backups", { showSafeBackups() })
        row("▦  Items", { showItemsEditor() }, "▣  Pokédex", { showPokedexEditor() })
        val eventsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addDashboardCard(eventsRow, "🎁  Events", {
            val f=importedFile
            if(activeSwitchSave != null){openActiveSwitchSection("events")}
            else if(f==null){showDialog("Events","Load a save before opening Events.")}
            else{
                val data=runCatching{f.readBytes()}.getOrNull()
                when{
                    data==null->showDialog("Events","PSE could not read the loaded save.")
                    detectGen2ChecksumProfile(data)!=null->showGen2Events()
                    data.size==SIZE_G1->showGen1Events()
                    else->showDialog("Events","PSE could not identify a supported event catalog for the loaded save.")
                }
            }
        }, 1f)
        grid.addView(eventsRow, LinearLayout.LayoutParams(-1, -2))
        root.addView(grid)
        val converterSource = importedFile?.let { runCatching { it.readBytes() }.getOrNull() }
        if (converterSource != null && gen3SaveValid(converterSource)) {
            addButton(root, "Game Details (Gen 3)") { showGen3GameDetailsMenu() }
        }
        if (converterSource != null && generationForSize(converterSource.size) == 6) {
            addButton(root, "⇄  Convert Save (Gen 6)") { showGen6ConvertSave() }
        }

        root.addView(TextView(this).apply {
            text = "SAVE MANAGEMENT"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ACCENT)
            setPadding(dp(4), dp(14), 0, dp(8))
        })
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addToolCard(tools, "↓", "Import") { pickSave() }
        addToolCard(tools, "⌕", "Scan") { showEmulatorScanner() }
        addToolCard(tools, "↑", "Export") { exportCurrentSave() }
        addToolCard(tools, "↪", "Save & Replace", primary = true) { confirmReplaceCurrentSave() }
        root.addView(tools)

        val inspect = TextView(this).apply {
            text = "Inspect current save  ›"
            textSize = 14f
            setTextColor(MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(10))
            setOnClickListener { inspectCurrentSave() }
        }
        root.addView(inspect)
        root.addView(TextView(this).apply {
            text = "Current source manager  ›"
            textSize = 14f
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
            setOnClickListener { showSourceManager() }
        })
        val installedVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        root.addView(TextView(this).apply {
            text = "PSE v$installedVersion • Gen 1–7 + Switch Gen 7–9 (read-only)"
            textSize = 12f
            setTextColor(Color.rgb(105, 125, 140))
            gravity = Gravity.CENTER
        })
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 16, 24))
        }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(buildBottomNav("home"), LinearLayout.LayoutParams(-1, -2))
        setContentView(shell)
    }

    private data class Gen5BankBatchItem(
        val bankFile:File,val bankBytes:ByteArray,val source:PokemonEntry,
        val sourceBinding:PseBankSourceBinding
    )

    private fun chooseSelectedGen5BankDestination(chosen:List<File>){
        val file=importedFile?:return showDialog("Gen 5 Bank multi-withdrawal","Load a valid Black, White, Black 2 or White 2 destination save first.")
        val openedUri=currentSourceUri;val openedLabel=currentSourceLabel
        if(chosen.isEmpty())return showDialog("Gen 5 Bank multi-withdrawal","Select one or more Gen 5 Bank Pokémon first.")
        if(chosen.map{it.absolutePath}.distinct().size!=chosen.size)return showDialog("Gen 5 Bank multi-withdrawal","The selection contains the same Bank record more than once. Nothing was changed.")
        val destination=runCatching{file.readBytes()}.getOrNull()?:return showDialog("Gen 5 Bank multi-withdrawal","The destination save could not be read. Nothing was changed.")
        val profile=detectGen5Profile(destination)?:return showDialog("Gen 5 Bank multi-withdrawal","The loaded destination is not a native Generation 5 save. Nothing was changed.")
        if(!gen5ChecksumTableValid(destination,profile)||(0 until 24).any{!gen5BoxChecksumValid(destination,it,profile)})return showDialog("Gen 5 Bank multi-withdrawal","The loaded destination is not a checksum-clean native Generation 5 save. Nothing was changed.")
        val root=bankDir()
        if(runCatching{root.absoluteFile!=root.canonicalFile||!root.isDirectory}.getOrDefault(true))return showDialog("Gen 5 Bank multi-withdrawal","The PSE Bank folder could not be verified.")
        val selected=try{chosen.map{candidate->
            require(candidate.absoluteFile==candidate.canonicalFile){"A selected Gen 5 Bank record path is not canonical."}
            val bankFile=candidate
            require(bankFile.parentFile==root&&bankFile.isFile&&bankFile.extension.equals("pk5",true)){"A selected record is outside the Generation 5 PSE Bank."}
            val sourceBinding=PseBankSourceBinding.capture(bankFile)?:error("A selected Gen 5 Bank record identity could not be captured.")
            val bytes=bankFile.readBytes();val source=if(bytes.size==136)parsePk5(bytes,false)else null
            require(sourceBinding.matches(bytes,true)&&source!=null&&source.checksumValid){"A selected Gen 5 Bank record is not a checksum-valid native PK5."}
            Gen5BankBatchItem(bankFile,bytes,source,sourceBinding)
        }}catch(e:Exception){return showDialog("Gen 5 Bank multi-withdrawal",(e.message?:"A selected Gen 5 Bank record could not be read.")+" Nothing was changed.")}
        if(selected.map{it.bankFile.absolutePath}.distinct().size!=selected.size)return showDialog("Gen 5 Bank multi-withdrawal","The selection resolves to the same Bank record more than once. Nothing was changed.")
        val targets=(0 until 24).mapNotNull{box->
            val slots=(0 until 30).filter{slot->val offset=0x00400+box*0x1000+slot*136;val raw=destination.copyOfRange(offset,offset+136);parsePk5(raw,false)==null&&raw.all{it==0.toByte()}}
            if(slots.size>=selected.size)box to slots.take(selected.size) else null
        }
        if(targets.isEmpty())return showDialog("Gen 5 Bank multi-withdrawal","No Generation 5 PC box has ${selected.size} empty slots for the complete selection. Free capacity, reduce the selection, or load another valid save. Nothing was changed.")
        var consumed=false
        AlertDialog.Builder(this).setTitle("Move ${selected.size} Gen 5 Pokémon to which box?")
            .setMessage("This is a PSE Bank withdrawal, not an official Pokémon Bank transaction or provenance claim. No Pokémon conversion or Legalise change is performed. PSE will copy each exact native 136-byte PK5 into the listed structurally empty slots in selection order, update only that box's native CRC and checksum-table fields, retain verified recovery data, reopen every destination record, then remove Bank records last.")
            .setItems(targets.map{(box,slots)->"Box ${box+1} • Slots ${slots.joinToString(", "){(it+1).toString()}}"}.toTypedArray()){_,which->
                if(consumed||which !in targets.indices){consumed=true;return@setItems};consumed=true
                val(box,slots)=targets[which]
                withdrawSelectedGen5BankPokemon(selected,destination,box,slots,file,openedUri,openedLabel)
            }.setNegativeButton("Cancel"){_,_->consumed=true}.setOnCancelListener{consumed=true}.show()
    }

    private fun withdrawSelectedGen5BankPokemon(selected:List<Gen5BankBatchItem>,openedDestination:ByteArray,targetBox:Int,targetSlots:List<Int>,file:File,openedUri:Uri?,openedLabel:String?){
        fun bound()=importedFile?.absolutePath==file.absolutePath&&currentSourceUri==openedUri&&currentSourceLabel==openedLabel
        val destinationBinding=PseBankDestinationBinding.capture(file)
        if(destinationBinding==null||!destinationBinding.matches(openedDestination,bound())){
            showDialog("Gen 5 Bank multi-withdrawal","The destination identity changed after review. Reopen PSE Bank. Nothing was changed.");return
        }
        var writeStarted=false;var safetyBackup:File?=null;var backupBinding:PseBankDestinationBinding?=null
        var stagedDestination:ByteArray?=null
        val recoveryCopies=ArrayList<Pair<Gen5BankBatchItem,File>>();val cleanupAttempted=HashSet<String>()
        val removedSources=ArrayList<File>()
        val recoveryBindings=HashMap<String,PseBankSourceBinding>()
        fun recoveryCopiesMatch()=recoveryCopies.size==selected.size&&recoveryCopies.all{(item,copy)->
            recoveryBindings[copy.absolutePath]?.matches(item.bankBytes,true)==true
        }
        var retainedRecovery=false
        try{
            require(selected.isNotEmpty()&&selected.map{it.bankFile.absolutePath}.distinct().size==selected.size){"The prepared Gen 5 Bank selection is empty or contains a duplicate."}
            require(targetBox in 0 until 24&&targetSlots.size==selected.size&&targetSlots.distinct().size==targetSlots.size&&targetSlots.all{it in 0 until 30}){"The prepared Generation 5 destination slots are invalid."}
            val original=file.readBytes();require(destinationBinding.matches(openedDestination,bound())&&original.contentEquals(openedDestination)&&generationForSize(original.size)==5){"The Generation 5 destination or source identity changed after slot selection."}
            val profile=detectGen5Profile(original)?:error("The Generation 5 destination no longer has a valid native layout.")
            require(gen5ChecksumTableValid(original,profile)&&(0 until 24).all{gen5BoxChecksumValid(original,it,profile)}){"The Generation 5 destination no longer passes native checksum validation."}
            val refreshed=selected.map{item->
                val bytes=item.bankFile.readBytes();val source=if(bytes.size==136)parsePk5(bytes,false)else null
                require(item.sourceBinding.matches(item.bankBytes,true)&&bytes.contentEquals(item.bankBytes)&&source!=null&&source.checksumValid&&source.pid==item.source.pid&&source.species==item.source.species&&source.tid==item.source.tid&&source.sid==item.source.sid){"A selected Gen 5 Bank record changed after destination review."}
                Gen5BankBatchItem(item.bankFile,bytes,source,item.sourceBinding)
            }
            val work=original.copyOf();val offsets=ArrayList<Int>();val allowed=HashSet<Int>()
            for(i in refreshed.indices){
                val offset=0x00400+targetBox*0x1000+targetSlots[i]*136;offsets.add(offset)
                val empty=work.copyOfRange(offset,offset+136);require(parsePk5(empty,false)==null&&empty.all{it==0.toByte()}){"Box ${targetBox+1} • Slot ${targetSlots[i]+1} is no longer structurally empty."}
                refreshed[i].bankBytes.copyInto(work,offset);for(p in offset until offset+136)allowed.add(p)
                val raw=work.copyOfRange(offset,offset+136);val staged=parsePk5(raw,false)
                require(raw.contentEquals(refreshed[i].bankBytes)&&staged!=null&&staged.checksumValid&&staged.pid==refreshed[i].source.pid&&staged.species==refreshed[i].source.species&&staged.tid==refreshed[i].source.tid&&staged.sid==refreshed[i].source.sid){"An exact selected PK5 did not stage in its destination slot."}
            }
            for(i in 0..1){allowed.add(0x00400+targetBox*0x1000+0x0FF2+i);allowed.add(profile.checksumTable+2+targetBox*2+i);allowed.add(profile.checksumBlockChecksumOffset+i)}
            updateGen5BoxChecksum(work,targetBox,profile)
            require(work.indices.all{it in allowed||work[it]==original[it]}){"The Gen 5 batch changed data outside the selected PK5 slots and required native checksum fields."}
            require(gen5BoxChecksumValid(work,targetBox,profile)&&gen5ChecksumTableValid(work,profile)){"The staged Generation 5 destination failed native checksum verification."}
            val backupRoot=backupsDir();require(backupRoot.absoluteFile==backupRoot.canonicalFile&&backupRoot.isDirectory){"The canonical destination backup folder is unavailable."}
            val backup=File.createTempFile(backupGameName(file)+"_PreGen5BankBatch_",".backup",backupRoot);safetyBackup=backup
            require(backup.absoluteFile==backup.canonicalFile&&backup.parentFile==backupRoot){"The Generation 5 recovery backup path could not be bound."}
            val retainedBinding=PseBankDestinationBinding.capture(backup)?:error("The Generation 5 recovery backup identity could not be captured.")
            backupBinding=retainedBinding
            backup.writeBytes(original);require(retainedBinding.matches(original,true)){"The exact Generation 5 recovery backup did not verify."};retainGen6ToGen5SafetyBackup(backup,original)
            val bankRoot=bankDir();require(bankRoot.absoluteFile==bankRoot.canonicalFile&&bankRoot.isDirectory){"The canonical PSE Bank recovery folder is unavailable."}
            refreshed.forEachIndexed{index,item->
                val copy=File.createTempFile("PSE_G5Batch_${index+1}_BankRecovery_",".tmp",bankRoot);recoveryCopies.add(item to copy)
                require(copy.absoluteFile==copy.canonicalFile&&copy.parentFile==bankRoot){"A PK5 Bank recovery snapshot path could not be bound."}
                val copyBinding=PseBankSourceBinding.capture(copy)?:error("A PK5 Bank recovery snapshot identity could not be captured.")
                recoveryBindings[copy.absolutePath]=copyBinding
                copy.writeBytes(item.bankBytes)
                require(copyBinding.matches(item.bankBytes,true)){"A PK5 Bank recovery snapshot did not verify."}
            }
            fun recoveryValid()=retainedBinding.matches(original,isBackupKept(backup))&&recoveryCopiesMatch()
            require(destinationBinding.matches(original,bound())&&refreshed.all{it.sourceBinding.matches(it.bankBytes,true)}&&recoveryValid()){"The destination, Bank records or retained recovery data changed before writing."}
            stagedDestination=work.copyOf()
            writeStarted=true;file.writeBytes(work);val written=file.readBytes()
            require(destinationBinding.matches(work,bound())&&written.contentEquals(work)&&written.indices.all{it in allowed||written[it]==original[it]}){"The persisted Generation 5 save differs from the reviewed write scope."}
            val writtenProfile=detectGen5Profile(written)?:error("The persisted Generation 5 save could not be reopened.")
            require(gen5ChecksumTableValid(written,writtenProfile)&&(0 until 24).all{gen5BoxChecksumValid(written,it,writtenProfile)}){"The persisted Generation 5 save failed native checksum verification."}
            for(i in refreshed.indices){val actual=written.copyOfRange(offsets[i],offsets[i]+136);val reopened=parsePk5(actual,false);val source=refreshed[i].source;require(actual.contentEquals(refreshed[i].bankBytes)&&reopened!=null&&reopened.checksumValid&&reopened.pid==source.pid&&reopened.species==source.species&&reopened.tid==source.tid&&reopened.sid==source.sid){"An exact PK5 did not verify after destination reopen."}}
            for(item in refreshed){
                require(destinationBinding.matches(work,bound())&&recoveryValid()){"The verified Generation 5 destination or retained recovery data changed before Bank cleanup."}
                require(exactBankPathsAbsent(removedSources)){"An earlier removed Gen 5 Bank path reappeared before the next cleanup step."}
                val current=item.bankFile.readBytes();val parsed=if(current.size==136)parsePk5(current,false)else null
                require(item.sourceBinding.matches(item.bankBytes,true)&&current.contentEquals(item.bankBytes)&&parsed!=null&&parsed.checksumValid){"A selected Gen 5 Bank record changed before cleanup."}
                cleanupAttempted.add(item.bankFile.absolutePath);require(item.bankFile.delete()&&!item.bankFile.exists()){"A selected Gen 5 Bank record could not be removed after destination verification."}
                removedSources.add(item.bankFile)
                require(exactBankPathsAbsent(removedSources)){"A removed Gen 5 Bank path did not remain absent after cleanup."}
            }
            require(removedSources.size==refreshed.size&&exactBankPathsAbsent(removedSources)&&destinationBinding.matches(work,bound())&&recoveryValid()){"The verified Generation 5 destination, removed Bank paths or retained recovery data changed after Bank cleanup."}
            retainedRecovery=finishSelectedNativeBankBatchRecoveryCleanup(
                recoveryCopies.map{it.second},recoveryCopies.map{it.first.bankBytes}
            )
            requireSelectedNativeBankBatchPostCleanup(
                5,destinationBinding.matches(work,bound()),retainedRecovery,recoveryCopies.map{it.second},
                file,work,backup,original,retainedBinding.matches(original,isBackupKept(backup)),refreshed.map{it.bankFile}
            )
            selectedBankFiles.clear();bankMultiSelectMode=false
            Toast.makeText(
                this,gen5BankBatchSuccessMessage(refreshed.size,targetBox,targetSlots,retainedRecovery),
                Toast.LENGTH_LONG
            ).show();showBank()
        }catch(e:Exception){
            val bankRestored=selected.map{item->runCatching{
                val copy=recoveryCopies.firstOrNull{it.first.bankFile.absolutePath==item.bankFile.absolutePath}?.second
                restoreExactMissingStandardBankRecord(
                    item.bankFile,item.bankBytes,
                    cleanupAttempted.contains(item.bankFile.absolutePath),copy
                )
                val restored=item.bankFile.readBytes();val parsed=if(restored.size==136)parsePk5(restored,false)else null
                require(item.sourceBinding.matches(item.bankBytes,true)&&restored.contentEquals(item.bankBytes)&&parsed!=null&&parsed.checksumValid&&parsed.pid==item.source.pid&&parsed.species==item.source.species&&parsed.tid==item.source.tid&&parsed.sid==item.source.sid){"An exact Gen 5 Bank record could not be restored or verified."};true
            }.getOrDefault(false)}.all{it}
            val destinationRestored=runCatching{
                val current=file.readBytes()
                require(destinationBinding.matches(current,bound())){"The Generation 5 destination identity changed during recovery; the current file was left untouched."}
                if(!current.contentEquals(openedDestination)){
                    require(writeStarted){"No Generation 5 destination write began; unknown current bytes were left untouched."}
                    val staged=stagedDestination
                    require(staged!=null&&current.contentEquals(staged)){"The Generation 5 destination no longer matches this batch withdrawal's staged bytes; the retained backup was left for recovery."}
                    file.writeBytes(openedDestination)
                }
                val restored=file.readBytes();val restoredProfile=detectGen5Profile(restored)
                require(destinationBinding.matches(openedDestination,bound())&&restored.contentEquals(openedDestination)&&restoredProfile!=null&&gen5ChecksumTableValid(restored,restoredProfile)&&(0 until 24).all{gen5BoxChecksumValid(restored,it,restoredProfile)}){"The restored Generation 5 destination did not verify."};true
            }.getOrDefault(false)
            val retainedBackup=safetyBackup?.let{saved->runCatching{
                backupBinding?.matches(openedDestination,isBackupKept(saved))==true
            }.getOrDefault(false)}?:false
            val verifiedRecoveryCopies=recoveryCopies.filter{(item,copy)->runCatching{
                val recovered=copy.readBytes();val parsed=if(recovered.size==136)parsePk5(recovered,false)else null
                recoveryBindings[copy.absolutePath]?.matches(item.bankBytes,true)==true&&recovered.contentEquals(item.bankBytes)&&parsed!=null&&parsed.checksumValid&&
                    parsed.pid==item.source.pid&&parsed.species==item.source.species&&
                    parsed.tid==item.source.tid&&parsed.sid==item.source.sid
            }.getOrDefault(false)}
            val allRecoveryCopiesVerified=recoveryCopies.size==selected.size&&verifiedRecoveryCopies.size==selected.size
            val recoveryCopiesClean=if(bankRestored&&destinationRestored&&recoveryCopiesMatch()){
                cleanupExactBankRecoverySnapshots(
                    recoveryCopies.map{it.second},recoveryCopies.map{it.first.bankBytes}
                )
            }else false
            showDialog(
                "Gen 5 Bank multi-withdrawal failed",
                gen5BankBatchFailureMessage(
                    e.message,bankRestored,destinationRestored,recoveryCopiesClean,
                    retainedBackup,allRecoveryCopiesVerified,verifiedRecoveryCopies.size,
                    selected.size,safetyBackup,verifiedRecoveryCopies
                )
            )
        }
    }

    private data class Gen6BankBatchItem(val bankFile:File,val bankBytes:ByteArray,val source:PokemonEntry)

    private fun gen6BankDestinationSlotIsStructurallyEmpty(
        data:ByteArray,boxOffset:Int,slotCount:Int,maxSpecies:Int,global:Int
    ):Boolean{
        if(global !in 0 until slotCount)return false
        val offset=boxOffset+global*PK6_STORED_SIZE
        if(offset<0||offset+PK6_STORED_SIZE>data.size)return false
        val raw=data.copyOfRange(offset,offset+PK6_STORED_SIZE)
        return raw.all{it==0.toByte()}&&
            parsePk6(raw,0,PK6_STORED_SIZE,false,maxSpecies)==null
    }

    private data class Gen7BankBatchItem(val bankFile:File,val bankBytes:ByteArray,val source:PokemonEntry)

    private fun gen7BankDestinationSlotIsStructurallyEmpty(
        data:ByteArray,boxOffset:Int,slotCount:Int,maxSpecies:Int,global:Int
    ):Boolean{
        if(global !in 0 until slotCount)return false
        val offset=boxOffset+global*PK6_STORED_SIZE
        if(offset<0||offset+PK6_STORED_SIZE>data.size)return false
        val raw=data.copyOfRange(offset,offset+PK6_STORED_SIZE)
        return raw.all{it==0.toByte()}&&
            parsePk6(raw,0,PK6_STORED_SIZE,false,maxSpecies)==null
    }

    private fun chooseSelectedStandardBankDestination(chosen:List<File>,generation:Int){
        val file=importedFile?:return
        val openedUri=currentSourceUri;val openedLabel=currentSourceLabel
        runSelectedStandardBankBatchReview(
            this,chosen,generation,file,
            {importedFile?.absolutePath==file.absolutePath&&currentSourceUri==openedUri&&currentSourceLabel==openedLabel},
            {data->boxStorageInfo(data)},{bankDir()},{backupsDir()},{backupGameName(file)},
            {data,boxOffset,slotCount,maxSpecies,slot->
                if(generation==6)gen6BankDestinationSlotIsStructurallyEmpty(data,boxOffset,slotCount,maxSpecies,slot)
                else gen7BankDestinationSlotIsStructurallyEmpty(data,boxOffset,slotCount,maxSpecies,slot)
            },
            {raw,maxSpecies->raw.size==PK6_STORED_SIZE&&parsePk6(raw,0,PK6_STORED_SIZE,false,maxSpecies)?.checksumValid==true},
            {data->if(generation==6)generationForSize(data.size)==6&&gen6BoxChecksumValid(data)&&gen6BlockChecksumValid(data,20)
                else generationForSize(data.size)==7&&validateGen7CoreChecksums(data).first},
            {data->refreshBoxChecksum(data)},
            {backup,data->if(generation==6)retainGenToGen6SafetyBackup(backup,data)else retainGen1ToGen7SafetyBackup(backup,data)},
            {backup->isBackupKept(backup)},
            {title,message->showDialog(title,message)},
            {moved,targetBox->
                selectedBankFiles.clear();bankMultiSelectMode=false
                Toast.makeText(this,"${moved} Pokémon moved to Box ${targetBox+1} and verified",Toast.LENGTH_LONG).show()
                showBank()
            }
        )
    }

    private data class Gen2BankBatchItem(
        val bankFile:File,
        val bankBytes:ByteArray,
        val source:Gen2Pokemon,
        val sourceBinding:PseBankSourceBinding
    )

    private fun chooseSelectedGen2BankDestination(chosen:List<File>) {
        val openedSource=currentSourceUri;val openedLabel=currentSourceLabel
        val file=importedFile?:return
        if(chosen.isEmpty()){showDialog("Move selected","Select one or more Gen 2 Bank Pokémon first.");return}
        if(chosen.map{it.absolutePath}.distinct().size!=chosen.size){showDialog("Move selected","The selection contains the same Gen 2 Bank entry more than once. Nothing was changed.");return}
        val openedDestination=runCatching{file.readBytes()}.getOrNull()?:return showDialog("Move selected","The Gen 2 destination could not be read.")
        val openedProfile=detectGen2Profile(openedDestination)?:return showDialog("Move selected","The Gen 2 destination failed checksum validation. Nothing was changed.")
        if(!gen2ChecksumValid(openedDestination,openedProfile)){showDialog("Move selected","The Gen 2 destination checksum is invalid. Nothing was changed.");return}
        val selected=try{
            val bankRoot=bankDir()
            require(bankRoot.absoluteFile==bankRoot.canonicalFile&&bankRoot.isDirectory){"The canonical Gen 2 Bank folder is unavailable."}
            chosen.map{bankFile->
                require(bankFile.absoluteFile==bankFile.canonicalFile&&bankFile.isFile&&
                    bankFile.extension.equals("pse2",true)&&bankFile.parentFile==bankRoot){"A selected Gen 2 Bank record is missing or outside the active Bank."}
                val sourceBinding=PseBankSourceBinding.capture(bankFile)
                    ?:error("A selected Gen 2 Bank record identity could not be captured.")
                val bytes=bankFile.readBytes();val source=parseGen2BankPayload(bytes)?:error("A selected Gen 2 Bank record is invalid.")
                require(sourceBinding.matches(bytes,true)){"A selected Gen 2 Bank record identity changed during review."}
                Gen2BankBatchItem(bankFile,bytes,source,sourceBinding)
            }
        }catch(e:Exception){showDialog("Move selected",(e.message?:"A selected Gen 2 Bank record could not be read.")+" Nothing was changed.");return}
        val targets=availableGen2BoxTargets(openedDestination).filter{it.second>=selected.size}
        if(targets.isEmpty()){showDialog("Move selected","No Gen 2 PC box has enough reported free slots for the complete selection. Nothing was changed.");return}
        var consumed=false
        AlertDialog.Builder(this).setTitle("Move ${selected.size} Pokémon to which box?")
            .setMessage("PSE will revalidate every selected Bank record and the complete destination, place the batch in order, register Seen and Owned status, verify exact persistence, then remove Bank records last.")
            .setItems(targets.map{(box,empty)->gen2BoxDestinationLabel(box,empty,file,openedProfile,currentSourceUri,openedDestination)}.toTypedArray()){_,which->
                if(consumed||which !in targets.indices){consumed=true;showDialog("Move selected","This destination choice was already handled or is invalid. Reopen the Bank selection. Nothing was changed.");return@setItems}
                consumed=true
                moveSelectedGen2BankPokemon(selected,openedDestination,targets[which].first,file,openedSource,openedLabel)
            }
            .setNegativeButton("Cancel"){_,_->consumed=true}.setOnCancelListener{consumed=true}.show()
    }

    private fun moveSelectedGen2BankPokemon(selected:List<Gen2BankBatchItem>,openedDestination:ByteArray,targetBox:Int,openedFile:File,openedSource:Uri?,openedLabel:String?) {
        fun bound()=importedFile?.absolutePath==openedFile.absolutePath&&currentSourceUri==openedSource&&currentSourceLabel==openedLabel
        val destinationBinding=PseBankDestinationBinding.capture(openedFile)
        if(destinationBinding==null||!destinationBinding.matches(openedDestination,bound())){
            showDialog("Gen 2 Bank multi-withdrawal","The destination identity changed after review. Reopen PSE Bank. Nothing was changed.");return
        }
        val file=openedFile;val fresh=runCatching{file.readBytes()}.getOrNull()?:return;val removed=ArrayList<Pair<File,ByteArray>>();var writeStarted=false
        var stagedDestination:ByteArray?=null
        val recoveryProfileKey=detectGen2Profile(openedDestination)?.key
        var safetyBackup:File?=null
        var backupBinding:PseBankDestinationBinding?=null
        val bankRecovery=ArrayList<File>()
        val bankRecoveryBindings=HashMap<String,PseBankSourceBinding>()
        fun recoverySnapshotsMatch(expected:List<Gen2BankBatchItem>):Boolean=
            bankRecovery.size==expected.size&&bankRecovery.indices.all{index->
                bankRecoveryBindings[bankRecovery[index].absolutePath]
                    ?.matches(expected[index].bankBytes,true)==true
            }
        var completionMessage=""
        try{
            require(destinationBinding.matches(openedDestination,bound())&&fresh.contentEquals(openedDestination)&&verifyGen2SaveIntegrity(fresh).first){"The captured Gen 2 batch destination identity or full integrity changed."}
            require(selected.isNotEmpty()){"The prepared Gen 2 Bank selection is empty."};require(selected.map{it.bankFile.absolutePath}.distinct().size==selected.size){"The prepared selection contains a duplicate Gen 2 Bank entry."}
            require(fresh.contentEquals(openedDestination)){"The Gen 2 destination changed after the box list opened."};val profile=detectGen2Profile(fresh)?:error("The fresh Gen 2 destination failed checksum validation.");require(gen2ChecksumValid(fresh,profile)){"The fresh Gen 2 checksum is invalid."};require(targetBox in 1..14){"The selected Gen 2 destination box is invalid."}
            require(profile.key==detectGen2Profile(openedDestination)?.key){"The Gen 2 batch destination profile changed."}
            val location="Box $targetBox";val before=gen2LocationPayloads(fresh,location,profile);require(before.size+selected.size<=20){"Box $targetBox no longer has room for the complete selection."}
            val bankRoot=bankDir();require(bankRoot.absoluteFile==bankRoot.canonicalFile&&bankRoot.isDirectory){"The canonical Gen 2 Bank folder is unavailable."}
            val refreshed=selected.map{item->require(item.sourceBinding.matches(item.bankBytes,true)&&item.bankFile.isFile&&item.bankFile.extension.equals("pse2",true)&&item.bankFile.parentFile==bankRoot){"A selected Gen 2 Bank record is no longer available."};val current=item.bankFile.readBytes();require(item.sourceBinding.matches(current,true)&&current.contentEquals(item.bankBytes)){"A selected Gen 2 Bank record changed after destination selection."};val source=parseGen2BankPayload(current)?:error("A selected Gen 2 Bank record is invalid.");require(sameGen2BankRecord(source,item.source)){"A selected Gen 2 Pokémon changed after destination selection."};Gen2BankBatchItem(item.bankFile,current,source,item.sourceBinding)}
            val work=fresh.copyOf();val slots=ArrayList<Int>();val rawRecords=ArrayList<ByteArray>()
            for(item in refreshed){val o=GEN2_BANK_HEADER_SIZE;val monRaw=item.bankBytes.copyOfRange(o,o+32);val otRaw=item.bankBytes.copyOfRange(o+32,o+43);val nickRaw=item.bankBytes.copyOfRange(o+43,o+54);slots.add(appendGen2BoxPokemon(work,targetBox,monRaw,otRaw,nickRaw));rawRecords.add(monRaw);markGen2PokedexOwnedSeen(work,profile,item.source.species)}
            updateGen2PrimaryChecksum(work,profile);val stagedProfile=detectGen2Profile(work)?:error("The staged Gen 2 destination failed checksum verification.");require(gen2ChecksumValid(work,stagedProfile)){"The staged Gen 2 checksum is invalid."};val stagedPayloads=gen2LocationPayloads(work,location,stagedProfile);require(stagedPayloads.size==before.size+refreshed.size&&sameGen2PayloadList(stagedPayloads.take(before.size),before)){"Existing Gen 2 box records changed during batch staging."}
            for(i in refreshed.indices){val item=refreshed[i];val slot=slots[i];val staged=parseGen2Box(work,targetBox).firstOrNull{it.slot==slot};require(staged!=null&&sameGen2BankRecord(staged,item.source)){"A staged Gen 2 Bank record did not preserve every supported field."};require(gen2BoxRawCore(work,targetBox,slot).contentEquals(rawRecords[i])){"An exact Gen 2 Bank record did not stage."};require(gen2PokedexOwnedSeen(work,stagedProfile,item.source.species)){"A transferred species did not register Seen and Owned during staging."}}
            require(verifyGen2SaveIntegrity(work).first&&stagedProfile.key==profile.key){"The complete staged Gen 2 batch withdrawal failed integrity or profile validation."}
            val backupRoot=backupsDir();require(backupRoot.absoluteFile==backupRoot.canonicalFile&&backupRoot.isDirectory){"The canonical destination backup folder is unavailable."}
            val backup=File.createTempFile(backupGameName(file)+"_PreGen2BankBatch_",".backup",backupRoot);safetyBackup=backup
            require(backup.absoluteFile==backup.canonicalFile&&backup.parentFile==backupRoot){"The Gen 2 batch recovery backup path could not be bound."}
            val retainedBinding=PseBankDestinationBinding.capture(backup)
                ?:error("The Gen 2 batch recovery backup identity could not be captured.")
            backupBinding=retainedBinding
            backup.writeBytes(fresh);require(retainedBinding.matches(fresh,true)){"The batch withdrawal recovery backup did not persist exactly."}
            retainGen2SafetyBackup(backup,fresh)
            for((index,item) in refreshed.withIndex()){
                val recovery=File.createTempFile("PSE_G2BatchWithdraw_${index+1}_",".tmp",bankRoot);bankRecovery.add(recovery)
                require(recovery.absoluteFile==recovery.canonicalFile&&recovery.parentFile==bankRoot){"A Gen 2 Bank recovery snapshot path could not be bound."}
                val recoveryBinding=PseBankSourceBinding.capture(recovery)
                    ?:error("A Gen 2 Bank recovery snapshot identity could not be captured.")
                bankRecoveryBindings[recovery.absolutePath]=recoveryBinding
                recovery.writeBytes(item.bankBytes)
                val recovered=recovery.readBytes()
                require(recoveryBinding.matches(item.bankBytes,true)&&recovery.isFile&&recovered.contentEquals(item.bankBytes)&&parseGen2BankPayload(recovered)?.let{sameGen2BankRecord(it,item.source)}==true){"A Gen 2 Bank recovery snapshot did not verify."}
            }
            require(refreshed.all{it.sourceBinding.matches(it.bankBytes,true)}){"A selected Gen 2 Bank record changed during staging."}
            require(destinationBinding.matches(fresh,bound())){"The active Gen 2 destination changed during staging. Nothing was written."}
            require(destinationBinding.matches(fresh,bound())&&refreshed.all{it.sourceBinding.matches(it.bankBytes,true)}&&retainedBinding.matches(fresh,isBackupKept(backup))){"The destination, Bank record identity or retained recovery backup changed before batch withdrawal."}
            require(recoverySnapshotsMatch(refreshed)&&refreshed.indices.all{index->
                val recovered=bankRecovery[index].readBytes()
                bankRecovery[index].isFile&&recovered.contentEquals(refreshed[index].bankBytes)&&parseGen2BankPayload(recovered)?.let{sameGen2BankRecord(it,refreshed[index].source)}==true
            }){"A Gen 2 Bank recovery snapshot changed before batch withdrawal."}
            stagedDestination=work.copyOf()
            writeStarted=true
            file.writeBytes(work);val written=file.readBytes();require(destinationBinding.matches(work,bound())&&written.contentEquals(work)){"The Gen 2 destination changed while the batch was written."};val writtenProfile=detectGen2Profile(written)?:error("The persisted Gen 2 destination failed checksum verification.");require(gen2ChecksumValid(written,writtenProfile)){"The persisted Gen 2 checksum is invalid."};val afterPayloads=gen2LocationPayloads(written,location,writtenProfile);require(afterPayloads.size==stagedPayloads.size&&sameGen2PayloadList(afterPayloads,stagedPayloads)){"The persisted Gen 2 box records changed after staging."}
            require(destinationBinding.matches(work,bound())&&writtenProfile.key==profile.key&&verifyGen2SaveIntegrity(written).first){"The persisted Gen 2 batch withdrawal or destination identity did not verify."}
            for(i in refreshed.indices){val item=refreshed[i];val slot=slots[i];val persisted=parseGen2Box(written,targetBox).firstOrNull{it.slot==slot};require(persisted!=null&&sameGen2BankRecord(persisted,item.source)){"A persisted Gen 2 Bank record did not preserve every supported field."};require(gen2BoxRawCore(written,targetBox,slot).contentEquals(rawRecords[i])){"An exact Gen 2 Bank record did not persist."};require(gen2PokedexOwnedSeen(written,writtenProfile,item.source.species)){"A transferred species was not registered Seen and Owned after persistence."}}
            for((index,item) in refreshed.withIndex()){
                require(exactBankPathsAbsent(removed.map{it.first})){
                    "An earlier removed Gen 2 Bank path reappeared before the next cleanup step."
                }
                require(destinationBinding.matches(work,bound())){
                    "The verified Gen 2 destination changed before Bank cleanup."
                }
                require(retainedBinding.matches(fresh,isBackupKept(backup))){
                    "The retained Gen 2 batch recovery backup changed before Bank cleanup."
                }
                val recoveryBytes=bankRecovery[index].readBytes()
                require(bankRecoveryBindings[bankRecovery[index].absolutePath]?.matches(item.bankBytes,true)==true&&
                    bankRecovery[index].isFile&&recoveryBytes.contentEquals(item.bankBytes)&&
                    parseGen2BankPayload(recoveryBytes)?.let{sameGen2BankRecord(it,item.source)}==true){
                    "A Gen 2 Bank recovery snapshot changed before cleanup."
                }
                val cleanup=item.bankFile.readBytes()
                require(item.sourceBinding.matches(item.bankBytes,true)&&cleanup.contentEquals(item.bankBytes)&&
                    parseGen2BankPayload(cleanup)?.let{sameGen2BankRecord(it,item.source)}==true){
                    "A selected Gen 2 Bank record changed before cleanup."
                }
                val deleted=item.bankFile.delete()
                require(deleted&&!item.bankFile.exists()){
                    "A selected Gen 2 Bank record could not be removed after destination verification."
                }
                removed.add(item.bankFile to item.bankBytes)
                require(exactBankPathsAbsent(removed.map{it.first})){
                    "A removed Gen 2 Bank path did not remain absent after cleanup."
                }
            }
            require(removed.size==refreshed.size&&exactBankPathsAbsent(removed.map{it.first})){
                "The verified Gen 2 removed Bank batch changed after cleanup."
            }
            require(destinationBinding.matches(work,bound())){"The verified Gen 2 destination changed after Bank cleanup."}
            require(retainedBinding.matches(fresh,isBackupKept(backup))){"The retained Gen 2 batch recovery backup changed after Bank cleanup."}
            require(recoverySnapshotsMatch(refreshed)&&refreshed.indices.all{index->
                val recovered=bankRecovery[index].readBytes()
                bankRecovery[index].isFile&&recovered.contentEquals(refreshed[index].bankBytes)&&parseGen2BankPayload(recovered)?.let{sameGen2BankRecord(it,refreshed[index].source)}==true
            }){"A Gen 2 Bank recovery snapshot changed after Bank cleanup."}
            completionMessage=finishSelectedGen2BankWithdrawal(
                bankRecovery,refreshed.map{it.bankBytes},refreshed.size,targetBox,
                destinationBinding.matches(work,bound()),file,work,backup,fresh,retainedBinding.matches(fresh,isBackupKept(backup)),removed
            )
            require(destinationBinding.matches(work,bound())&&
                retainedBinding.matches(fresh,isBackupKept(backup))){
                "The verified Gen 2 destination or retained backup identity changed during recovery cleanup."
            }
            require(bankRecovery.indices.all{index->
                !bankRecovery[index].exists()||
                    bankRecoveryBindings[bankRecovery[index].absolutePath]
                        ?.matches(refreshed[index].bankBytes,true)==true
            }){"A retained Gen 2 Bank recovery snapshot identity changed during cleanup."}
        }catch(e:Exception){
            var bankRestored=true
            for(item in selected){
                val attempted=removed.any{it.first.absolutePath==item.bankFile.absolutePath}
                val recovered=runCatching{
                    if(item.bankFile.exists())require(item.bankFile.readBytes().contentEquals(item.bankBytes)){"An existing Bank recovery path changed; it was not overwritten."}
                    else{require(attempted){"A Bank record disappeared before PSE attempted its cleanup; it was not recreated."};require(item.bankFile.createNewFile()){"A Bank recovery path could not be reserved."};item.bankFile.writeBytes(item.bankBytes)}
                    val restored=item.bankFile.readBytes();require(item.sourceBinding.matches(item.bankBytes,true)&&restored.contentEquals(item.bankBytes)&&parseGen2BankPayload(restored)?.let{sameGen2BankRecord(it,item.source)}==true){"A restored Gen 2 Bank record did not verify."};true
                }.getOrDefault(false)
                bankRestored=recovered&&bankRestored
            }
            val destinationRestored=runCatching{
                val found=file.readBytes()
                require(destinationBinding.matches(found,bound())){"The Gen 2 destination identity changed during recovery; the current file was left untouched."}
                if(writeStarted&&!found.contentEquals(fresh)){
                    val staged=stagedDestination
                    require(staged!=null&&found.contentEquals(staged)){"The Gen 2 destination no longer matches this batch's staged bytes; the retained backup was left for recovery."}
                    file.writeBytes(fresh)
                }
                val restored=file.readBytes();val restoredProfile=detectGen2Profile(restored)
                require(destinationBinding.matches(fresh,bound())&&restored.contentEquals(fresh)&&restoredProfile?.key==recoveryProfileKey&&verifyGen2SaveIntegrity(restored).first){"The restored Gen 2 destination identity, bytes, profile or integrity did not verify."};true
            }.getOrDefault(false)
            val retainedBackup=safetyBackup?.let{saved->runCatching{
                backupBinding?.matches(fresh,isBackupKept(saved))==true
            }.getOrDefault(false)}?:false
            val verifiedBankRecovery=bankRecovery.mapIndexedNotNull{index,copy->
                val item=selected.getOrNull(index)?:return@mapIndexedNotNull null
                runCatching{
                    val recovered=copy.readBytes()
                    if(bankRecoveryBindings[copy.absolutePath]?.matches(item.bankBytes,true)==true&&copy.isFile&&recovered.contentEquals(item.bankBytes)&&parseGen2BankPayload(recovered)?.let{sameGen2BankRecord(it,item.source)}==true)copy else null
                }.getOrNull()
            }
            val allBankRecoveryVerified=bankRecovery.size==selected.size&&verifiedBankRecovery.size==selected.size
            val bankRecoveryClean=if(bankRestored&&destinationRestored&&recoverySnapshotsMatch(selected)){
                cleanupExactBankRecoverySnapshots(
                    bankRecovery,selected.map{it.bankBytes}
                )
            }else false
            val recovery=when{
                bankRestored&&destinationRestored&&bankRecoveryClean->
                    "The exact Gen 2 destination and every selected Bank record were restored or kept and verified."
                bankRestored&&destinationRestored->
                    "The exact Gen 2 destination and every selected Bank record were restored or kept and verified. One or more exact temporary Gen 2 Bank recovery snapshots changed or could not be removed safely and were retained for review."
                bankRestored&&retainedBackup->
                    "Every selected Gen 2 Bank record was restored or kept. Destination restoration was not verified, but the exact retained destination backup verified."
                bankRestored->
                    "Every selected Gen 2 Bank record was restored or kept, but destination restoration and its retained backup were not verified. Unknown destination bytes were left untouched."
                destinationRestored&&allBankRecoveryVerified->
                    "The exact Gen 2 destination was restored. Bank restoration was incomplete, but every exact Bank recovery snapshot verified."
                destinationRestored&&verifiedBankRecovery.isNotEmpty()->
                    "The exact Gen 2 destination was restored. Bank restoration was incomplete; only ${verifiedBankRecovery.size} of ${selected.size} Bank recovery snapshots verified."
                destinationRestored->
                    "The exact Gen 2 destination was restored, but Bank restoration and all Bank recovery snapshots were not verified."
                retainedBackup&&allBankRecoveryVerified->
                    "Destination and Bank restoration were incomplete, but the exact retained destination backup and every Bank recovery snapshot verified."
                retainedBackup&&verifiedBankRecovery.isNotEmpty()->
                    "Destination and Bank restoration were incomplete. The exact retained destination backup and ${verifiedBankRecovery.size} of ${selected.size} Bank recovery snapshots verified."
                retainedBackup->
                    "Destination and Bank restoration were incomplete. The exact retained destination backup verified, but no Bank recovery snapshot did."
                allBankRecoveryVerified->
                    "Destination and Bank restoration were incomplete. Every Bank recovery snapshot verified, but the retained destination backup did not."
                verifiedBankRecovery.isNotEmpty()->
                    "Destination and Bank restoration were incomplete. Only ${verifiedBankRecovery.size} of ${selected.size} Bank recovery snapshots verified; the retained destination backup did not."
                else->
                    "Automatic recovery and all retained recovery snapshots could not be verified. Keep the current destination and Bank files for review."
            }
            showDialog(
                "Move selected failed",
                (e.message?:"Unknown error")+"\n\n$recovery"+
                    (safetyBackup?.takeIf{retainedBackup}?.let{"\n\nVerified recovery backup: "+it.name}?:"")+
                    (verifiedBankRecovery.filter{it.exists()}.takeIf{it.isNotEmpty()}?.joinToString(prefix="\nVerified Bank recovery snapshots: "){it.name}?:"")
            );return
        }
        selectedBankFiles.clear();bankMultiSelectMode=false;Toast.makeText(this,completionMessage,Toast.LENGTH_LONG).show();showBank()
    }


    private fun moveSelectedGen1BankPokemon(chosen:List<File>) {
        val file=importedFile?:return;val openedSource=currentSourceUri;val openedLabel=currentSourceLabel
        fun bound()=importedFile?.absolutePath==file.absolutePath&&currentSourceUri==openedSource&&currentSourceLabel==openedLabel
        fun activeMatches(expected:ByteArray)=openedSource==null||runCatching{contentResolver.openInputStream(openedSource)?.use{it.readBytes()}}.getOrNull()?.contentEquals(expected)==true
        val initial=runCatching{file.readBytes()}.getOrNull()?:return
        if(chosen.isEmpty()){showDialog("Move selected","Select one or more Gen 1 Bank Pokémon first.");return}
        if(chosen.map{it.absolutePath}.distinct().size!=chosen.size){showDialog("Move selected","The selection contains the same Bank entry more than once. Nothing was changed.");return}
        if(detectGen2Profile(initial)!=null){chooseSelectedGen1BankToGen2Destination(chosen);return}
        if(generationForSize(initial.size)==3 && gen3SaveValid(initial)){chooseSelectedGen1BankToGen3Destination(chosen);return}
        if(generationForSize(initial.size)==6){prepareSelectedGen1BankToGen6(chosen);return}
        if(detectGen5Profile(initial)!=null){prepareSelectedGen1BankToGen5(chosen);return}
        if(detectGen4Profile(initial)!=null){prepareSelectedGen1BankToGen4(chosen);return}
        if(generationForSize(initial.size)==7){prepareSelectedGen1BankToGen7(chosen);return}
        val destinationIntegrity=verifyGen1SaveIntegrity(initial)
        if(!destinationIntegrity.first){showDialog("Move selected",destinationIntegrity.second+" Nothing was changed.");return}
        if(!bound()){showDialog("Move selected","The loaded Gen 1 save identity changed. Reopen the Bank selection. Nothing was changed.");return}
        val destinationBinding=PseBankDestinationBinding.capture(file)
        if(destinationBinding==null||!destinationBinding.matches(initial,bound())){
            showDialog("Move selected","The Gen 1 destination identity changed after review. Reopen the Bank selection. Nothing was changed.");return
        }
        val selected=try{
            val bankRoot=bankDir()
            require(bankRoot.absoluteFile==bankRoot.canonicalFile&&bankRoot.isDirectory){"The canonical Gen 1 Bank folder is unavailable."}
            chosen.map{bankFile->
                require(bankFile.absoluteFile==bankFile.canonicalFile&&bankFile.isFile&&
                    bankFile.extension.equals("pse1",true)&&bankFile.parentFile==bankRoot){
                    "A selected Gen 1 Bank record is missing or outside the active Bank."
                }
                val sourceBinding=PseBankSourceBinding.capture(bankFile)
                    ?:error("A selected Gen 1 Bank record identity could not be captured.")
                val bytes=bankFile.readBytes()
                val source=parseGen1BankPayload(bytes)?:error("A selected Gen 1 Bank record is invalid.")
                require(sourceBinding.matches(bytes,true)){"A selected Gen 1 Bank record identity changed during review."}
                Gen1BankBatchItem(bankFile,bytes,source,sourceBinding)
            }
        }catch(e:Exception){showDialog("Move selected",e.message?:"A selected Gen 1 Bank record could not be read.");return}
        val targets=availableGen1BoxTargets(initial).filter{it.second>=selected.size}
        if(targets.isEmpty()){showDialog("Move selected","No single writable Gen 1 box has room for all ${selected.size} selected Pokémon. Nothing was changed.");return}
        var consumed=false
        AlertDialog.Builder(this).setTitle("Move ${selected.size} Pokémon to which box?").setItems(targets.map{(b,e)->"Box $b • $e empty"}.toTypedArray()){_,which->
            if(consumed||which !in targets.indices){consumed=true;showDialog("Move selected","This destination choice was already handled or is invalid. Reopen the Bank selection. Nothing was changed.");return@setItems}
            consumed=true
            if(!destinationBinding.matches(initial,bound())){showDialog("Move selected","The loaded Gen 1 save identity changed after the box list opened. Reopen the Bank selection. Nothing was changed.");return@setItems}
            val box=targets[which].first
            val fresh=runCatching{file.readBytes()}.getOrNull()?:return@setItems
            var writeStarted=false;var activeWriteAttempted=false;var safetyBackup:File?=null
            var backupBinding:PseBankDestinationBinding?=null
            var stagedDestination:ByteArray?=null
            val bankRecovery=ArrayList<File>()
            val bankRecoveryBindings=HashMap<String,PseBankSourceBinding>()
            val removed=ArrayList<Pair<File,ByteArray>>()
            fun recoverySnapshotsMatch(expected:List<Gen1BankBatchItem>):Boolean=
                bankRecovery.size==expected.size&&bankRecovery.indices.all{index->
                    bankRecoveryBindings[bankRecovery[index].absolutePath]
                        ?.matches(expected[index].bankBytes,true)==true
                }
            try {
                require(destinationBinding.matches(initial,bound())&&fresh.contentEquals(initial)){"The Gen 1 destination or its source identity changed after the box list opened."}
                val freshIntegrity=verifyGen1SaveIntegrity(fresh);require(freshIntegrity.first){freshIntegrity.second}
                require(fresh.size==SIZE_G1&&hasValidGen1MainChecksum(fresh)){"The fresh Gen 1 save is invalid."}
                require(activeMatches(fresh)){"The active Gen 1 game save changed before batch withdrawal. Nothing was overwritten."}
                require(gen1BoxIsWritable(fresh,box)){"Box $box is no longer writable."}
                val before=parseGen1Box(fresh,box).pokemon.sortedBy{it.slot}
                require(before.size+selected.size<=GEN1_BOX_CAPACITY){"Box $box no longer has room for the complete selection."}
                val bankRoot=bankDir();require(bankRoot.absoluteFile==bankRoot.canonicalFile&&bankRoot.isDirectory){"The canonical Gen 1 Bank folder is unavailable."}
                val refreshed=selected.map{item->
                    require(item.sourceBinding.matches(item.bankBytes,true)&&item.bankFile.isFile&&
                        item.bankFile.extension.equals("pse1",true)&&item.bankFile.parentFile==bankRoot){
                        "A selected Gen 1 Bank record is no longer available."
                    }
                    val current=item.bankFile.readBytes()
                    require(item.sourceBinding.matches(current,true)&&current.contentEquals(item.bankBytes)){"A selected Gen 1 Bank record changed before the destination was confirmed."}
                    val parsed=parseGen1BankPayload(current)?:error("A selected Gen 1 Bank record is invalid.")
                    require(sameGen1TransferRecord(parsed,item.source)){"A selected Gen 1 Pokémon changed before the destination was confirmed."}
                    Gen1BankBatchItem(item.bankFile,current,parsed,item.sourceBinding)
                }
                val work=fresh.copyOf()
                for(item in refreshed){
                    val raw=item.bankBytes;val parsed=item.source
                    val o=GEN1_BANK_HEADER_SIZE
                    appendGen1BoxPokemon(work,box,raw.copyOfRange(o,o+GEN1_BOX_MON_SIZE),raw.copyOfRange(o+GEN1_BOX_MON_SIZE,o+GEN1_BOX_MON_SIZE+11),raw.copyOfRange(o+GEN1_BOX_MON_SIZE+11,GEN1_BANK_FILE_SIZE))
                    val dex=gen1DexFromInternalIndex(parsed.species)?:error("Bank Pokémon species could not be mapped to the Gen 1 Pokédex.")
                    markGen1PokedexCaught(work,dex)
                }
                updateGen1MainChecksum(work)
                val staged=parseGen1Box(work,box).pokemon.sortedBy{it.slot}
                require(staged.size==before.size+refreshed.size){"Not all selected Pokémon were staged in the destination box."}
                require(before.indices.all{sameGen1TransferRecord(staged[it],before[it])}){"An existing boxed Pokémon changed during Bank withdrawal staging."}
                for(i in refreshed.indices){
                    val expected=refreshed[i].source;val actual=staged[before.size+i]
                    require(sameGen1TransferRecord(actual,expected)){"A selected Pokémon changed during Bank withdrawal staging."}
                    val dex=gen1DexFromInternalIndex(expected.species)?:error("Transferred Pokémon could not be mapped to the Gen 1 Pokédex.")
                    val bit=dex-1;val mask=1 shl(bit%8)
                    require((work[0x25A3+bit/8].toInt() and mask)!=0&&(work[0x25B6+bit/8].toInt() and mask)!=0){"PC transfer Pokédex registration was not staged."}
                }
                val pre=verifyGen1WriteState(work,box);require(pre.first){pre.second}
                val backupRoot=backupsDir();require(backupRoot.absoluteFile==backupRoot.canonicalFile&&backupRoot.isDirectory){"The canonical destination backup folder is unavailable."}
                val backup=File.createTempFile(backupGameName(file)+"_PreGen1BankBatchWithdraw_",".backup",backupRoot);safetyBackup=backup
                require(backup.absoluteFile==backup.canonicalFile&&backup.parentFile==backupRoot){"The Gen 1 batch recovery backup path could not be bound."}
                val retainedBinding=PseBankDestinationBinding.capture(backup)
                    ?:error("The Gen 1 batch recovery backup identity could not be captured.")
                backupBinding=retainedBinding
                backup.writeBytes(fresh);require(retainedBinding.matches(fresh,true)){"The exact Gen 1 batch recovery backup did not verify."};retainGen2ToGen1SafetyBackup(backup,fresh)
                for((index,item) in refreshed.withIndex()){
                    val recovery=File.createTempFile("PSE_G1BatchWithdraw_${index+1}_",".tmp",bankRoot);bankRecovery.add(recovery)
                    require(recovery.absoluteFile==recovery.canonicalFile&&recovery.parentFile==bankRoot){"A Gen 1 Bank recovery snapshot path could not be bound."}
                    val recoveryBinding=PseBankSourceBinding.capture(recovery)
                        ?:error("A Gen 1 Bank recovery snapshot identity could not be captured.")
                    bankRecoveryBindings[recovery.absolutePath]=recoveryBinding
                    recovery.writeBytes(item.bankBytes)
                    val recovered=recovery.readBytes()
                    require(recoveryBinding.matches(item.bankBytes,true)&&recovered.contentEquals(item.bankBytes)&&parseGen1BankPayload(recovered)?.let{sameGen1TransferRecord(it,item.source)}==true){"A Gen 1 Bank recovery snapshot did not verify."}
                }
                require(destinationBinding.matches(fresh,bound())&&activeMatches(fresh)&&retainedBinding.matches(fresh,isBackupKept(backup))){"The destination identity or retained backup changed before batch withdrawal."}
                require(recoverySnapshotsMatch(refreshed)&&refreshed.indices.all{index->val item=refreshed[index];item.sourceBinding.matches(item.bankBytes,true)&&bankRecovery[index].readBytes().contentEquals(item.bankBytes)&&parseGen1BankPayload(bankRecovery[index].readBytes())?.let{sameGen1TransferRecord(it,item.source)}==true}){"A selected Bank record or recovery snapshot changed before batch withdrawal."}
                stagedDestination=work.copyOf()
                writeStarted=true;file.writeBytes(work)
                val written=file.readBytes();require(destinationBinding.matches(work,bound())&&written.contentEquals(work)){"The working save changed while it was being written."}
                val post=verifyGen1WriteState(written,box);require(post.first){post.second}
                val persisted=parseGen1Box(written,box).pokemon.sortedBy{it.slot}
                require(persisted.size==staged.size&&persisted.indices.all{sameGen1TransferRecord(persisted[it],staged[it])}){"The destination box changed after persistence."}
                val analysis=analyzeSave(file);require(analysis.structurallyValid){"The updated source failed validation after Bank transfer."}
                if(openedSource!=null){
                    require(activeMatches(fresh)){"The active game save changed before the verified batch was copied. Nothing was overwritten."}
                    activeWriteAttempted=true
                    contentResolver.openOutputStream(openedSource,"wt")?.use{it.write(written)}?:error("The active game save could not be opened for Bank transfer.")
                    val sourceAfter=contentResolver.openInputStream(openedSource)?.use{it.readBytes()}?:error("The active game save could not be re-read after Bank transfer.")
                    require(sourceAfter.contentEquals(written)){"The active game save did not preserve the verified transaction."}
                }
                for((index,item) in refreshed.withIndex()){
                    val bankFile=item.bankFile;val bankBytes=item.bankBytes;val parsed=item.source
                    require(exactBankPathsAbsent(removed.map{it.first})){
                        "An earlier removed Gen 1 Bank path reappeared before the next cleanup step."
                    }
                    require(destinationBinding.matches(work,bound())&&activeMatches(work)){"The verified Gen 1 destination changed before Bank cleanup."}
                    val recoveryBytes=bankRecovery[index].readBytes()
                    require(retainedBinding.matches(fresh,isBackupKept(backup))&&bankRecoveryBindings[bankRecovery[index].absolutePath]?.matches(bankBytes,true)==true&&recoveryBytes.contentEquals(bankBytes)&&parseGen1BankPayload(recoveryBytes)?.let{sameGen1TransferRecord(it,parsed)}==true){"A retained Gen 1 recovery copy changed before Bank cleanup."}
                    val cleanup=bankFile.readBytes();require(item.sourceBinding.matches(bankBytes,true)&&cleanup.contentEquals(bankBytes) && parseGen1BankPayload(cleanup)?.let{sameGen1TransferRecord(it,parsed)}==true){"A selected Bank record changed before cleanup. The destination save was restored."}
                    val deleted=bankFile.delete()
                    require(deleted&&!bankFile.exists()){ "A Bank record could not be removed after the save verified." }
                    removed.add(bankFile to bankBytes)
                    require(exactBankPathsAbsent(removed.map{it.first})){
                        "A removed Gen 1 Bank path did not remain absent after cleanup."
                    }
                }
                require(removed.size==refreshed.size&&exactBankPathsAbsent(removed.map{it.first})){
                    "The verified Gen 1 removed Bank batch changed after cleanup."
                }
                require(destinationBinding.matches(work,bound())&&activeMatches(work)){"The verified Gen 1 destination changed after Bank cleanup."}
                require(retainedBinding.matches(fresh,isBackupKept(backup))&&recoverySnapshotsMatch(refreshed)&&refreshed.indices.all{index->val recovered=bankRecovery[index].readBytes();recovered.contentEquals(refreshed[index].bankBytes)&&parseGen1BankPayload(recovered)?.let{sameGen1TransferRecord(it,refreshed[index].source)}==true}){"A retained Gen 1 recovery copy changed after Bank cleanup."}
                val completionMessage=finishSelectedGen1BankWithdrawal(
                    bankRecovery,refreshed.map{it.bankBytes},refreshed.size,box,
                    file,work,backup,fresh,retainedBinding.matches(fresh,isBackupKept(backup)),refreshed.map{it.bankFile}
                ){destinationBinding.matches(work,bound())&&activeMatches(work)}
                require(destinationBinding.matches(work,bound())&&activeMatches(work)&&
                    retainedBinding.matches(fresh,isBackupKept(backup))){
                    "The verified Gen 1 destination or retained backup identity changed during recovery cleanup."
                }
                require(bankRecovery.indices.all{index->
                    !bankRecovery[index].exists()||
                        bankRecoveryBindings[bankRecovery[index].absolutePath]
                            ?.matches(refreshed[index].bankBytes,true)==true
                }){"A retained Gen 1 Bank recovery snapshot identity changed during cleanup."}
                updateSourceBaseline(analysis.sha256,analysis.trainer?.exactGame?:analysis.family,written.size.toLong())
                selectedBankFiles.clear();bankMultiSelectMode=false
                Toast.makeText(this,completionMessage,Toast.LENGTH_LONG).show();showBank()
            }catch(e:Exception){
                var bankRestored=true
                for(item in selected){
                    val bankFile=item.bankFile;val bankBytes=item.bankBytes;val source=item.source
                    val attempted=removed.any{it.first.absolutePath==bankFile.absolutePath}
                    val recovered=runCatching{
                        if(bankFile.exists())require(bankFile.readBytes().contentEquals(bankBytes)){"An existing Bank recovery path changed; it was not overwritten."}
                        else{require(attempted){"A Gen 1 Bank record disappeared before PSE verified its cleanup; its path was not overwritten."};require(bankFile.createNewFile()){ "A Bank recovery path could not be reserved." };bankFile.writeBytes(bankBytes)}
                        val restored=bankFile.readBytes();require(item.sourceBinding.matches(bankBytes,true)&&restored.contentEquals(bankBytes)&&parseGen1BankPayload(restored)?.let{sameGen1TransferRecord(it,source)}==true){"A restored Gen 1 Bank record did not verify."};true
                    }.getOrDefault(false)
                    bankRestored=recovered&&bankRestored
                }
                val destinationRestored=runCatching{
                    val staged=stagedDestination
                    val found=file.readBytes()
                    require(destinationBinding.matches(found,bound())){"The Gen 1 destination identity changed during recovery; the current file was left untouched."}
                    if(writeStarted&&!found.contentEquals(fresh)){
                        require(staged!=null&&found.contentEquals(staged)){"The Gen 1 working save no longer matches this batch's staged bytes; the retained backup was left for recovery."}
                        file.writeBytes(fresh)
                    }
                    val restored=file.readBytes();require(destinationBinding.matches(fresh,bound())&&restored.contentEquals(fresh)&&verifyGen1SaveIntegrity(restored).first){"The restored Gen 1 working save did not verify."}
                    if(activeWriteAttempted&&openedSource!=null&&!activeMatches(fresh)){
                        val activeNow=contentResolver.openInputStream(openedSource)?.use{it.readBytes()}?:error("The active Gen 1 save could not be reopened for recovery.")
                        require(staged!=null&&activeNow.contentEquals(staged)){"The active Gen 1 save no longer matches this batch's staged bytes; it was left untouched."}
                        contentResolver.openOutputStream(openedSource,"wt")?.use{it.write(fresh)}?:error("The active Gen 1 save could not be reopened for recovery.")
                    }
                    require(activeMatches(fresh)){"The active Gen 1 save recovery did not verify."};true
                }.getOrDefault(false)
                val retainedBackup=safetyBackup?.let{saved->runCatching{
                    backupBinding?.matches(fresh,isBackupKept(saved))==true
                }.getOrDefault(false)}?:false
                val verifiedBankRecovery=bankRecovery.mapIndexedNotNull{index,copy->
                    val item=selected.getOrNull(index)?:return@mapIndexedNotNull null
                    runCatching{
                        val recovered=copy.readBytes()
                        if(bankRecoveryBindings[copy.absolutePath]?.matches(item.bankBytes,true)==true&&copy.isFile&&recovered.contentEquals(item.bankBytes)&&parseGen1BankPayload(recovered)?.let{sameGen1TransferRecord(it,item.source)}==true)copy else null
                    }.getOrNull()
                }
                val allBankRecoveryVerified=bankRecovery.size==selected.size&&verifiedBankRecovery.size==selected.size
                val bankRecoveryClean=if(bankRestored&&destinationRestored&&recoverySnapshotsMatch(selected)){
                    cleanupExactBankRecoverySnapshots(
                        bankRecovery,selected.map{it.bankBytes}
                    )
                }else false
                val recovery=when{
                    bankRestored&&destinationRestored&&bankRecoveryClean->
                        "The exact Gen 1 destination and every selected Bank record were restored or kept and verified."
                    bankRestored&&destinationRestored->
                        "The exact Gen 1 destination and every selected Bank record were restored or kept and verified. One or more exact temporary Gen 1 Bank recovery snapshots changed or could not be removed safely and were retained for review."
                    bankRestored&&retainedBackup->
                        "Every selected Gen 1 Bank record was restored or kept. Destination restoration was not verified, but the exact retained destination backup verified."
                    bankRestored->
                        "Every selected Gen 1 Bank record was restored or kept, but destination restoration and its retained backup were not verified. Unknown destination bytes were left untouched."
                    destinationRestored&&allBankRecoveryVerified->
                        "The exact Gen 1 destination was restored. Bank restoration was incomplete, but every exact Bank recovery snapshot verified."
                    destinationRestored&&verifiedBankRecovery.isNotEmpty()->
                        "The exact Gen 1 destination was restored. Bank restoration was incomplete; only ${verifiedBankRecovery.size} of ${selected.size} Bank recovery snapshots verified."
                    destinationRestored->
                        "The exact Gen 1 destination was restored, but Bank restoration and all Bank recovery snapshots were not verified."
                    retainedBackup&&allBankRecoveryVerified->
                        "Destination and Bank restoration were incomplete, but the exact retained destination backup and every Bank recovery snapshot verified."
                    retainedBackup&&verifiedBankRecovery.isNotEmpty()->
                        "Destination and Bank restoration were incomplete. The exact retained destination backup and ${verifiedBankRecovery.size} of ${selected.size} Bank recovery snapshots verified."
                    retainedBackup->
                        "Destination and Bank restoration were incomplete. The exact retained destination backup verified, but no Bank recovery snapshot did."
                    allBankRecoveryVerified->
                        "Destination and Bank restoration were incomplete. Every Bank recovery snapshot verified, but the retained destination backup did not."
                    verifiedBankRecovery.isNotEmpty()->
                        "Destination and Bank restoration were incomplete. Only ${verifiedBankRecovery.size} of ${selected.size} Bank recovery snapshots verified; the retained destination backup did not."
                    else->
                        "Automatic recovery and all retained recovery snapshots could not be verified. Keep the current destination and Bank files for review."
                }
                showDialog("Move selected failed",(e.message?:"Unknown error")+"\n\n"+recovery+
                    (safetyBackup?.takeIf{retainedBackup}?.let{"\n\nVerified recovery backup: ${it.name}"}?:"")+
                    (verifiedBankRecovery.filter{it.exists()}.takeIf{it.isNotEmpty()}?.joinToString(prefix="\nVerified Bank recovery snapshots: "){it.name}?:""))
            }
        }.setNegativeButton("Cancel"){_,_->consumed=true}.setOnCancelListener{consumed=true}.show()
    }

    private fun gen1BoxPayloadSnapshot(data:ByteArray,box:Int):List<ByteArray> =
        parseGen1Box(data,box).pokemon.sortedBy{it.slot}.map{buildGen1BankPayload(data,it,"Box $box • Slot ${it.slot+1}",false)}

    private fun sameGen1Payloads(a:List<ByteArray>,b:List<ByteArray>):Boolean=
        a.size==b.size&&a.indices.all{a[it].contentEquals(b[it])}

    private data class Gen1Gen7BatchPrepared(
        val bankFile:File,
        val bankBytes:ByteArray,
        val source:Gen1Pokemon,
        val sourceBinding:PseBankSourceBinding,
        val pk7:ByteArray,
        val converted:PokemonEntry
    )

    private fun prepareSelectedGen1BankToGen7(chosen:List<File>){
        val destination=importedFile?:return
        if(chosen.isEmpty()){showDialog("Gen 1 → Gen 7 multi-transfer","Select one or more Gen 1 Bank Pokémon first.");return}
        if(chosen.map{it.absolutePath}.distinct().size!=chosen.size){showDialog("Gen 1 → Gen 7 multi-transfer","The selection contains the same Bank entry more than once. Nothing was changed.");return}
        val saveData=runCatching{destination.readBytes()}.getOrElse{showDialog("Gen 1 → Gen 7 multi-transfer","The Gen 7 destination save could not be read. Nothing was changed.");return}
        if(generationForSize(saveData.size)!=7){showDialog("Gen 1 → Gen 7 multi-transfer","Load a Pokémon Sun, Moon, Ultra Sun or Ultra Moon save first.");return}
        val info=boxStorageInfo(saveData)?:run{showDialog("Gen 1 → Gen 7 multi-transfer","PSE could not resolve the Gen 7 PC storage.");return}
        val(boxOffset,slotCount,maxSpecies)=info
        val checksumState=validateGen7CoreChecksums(saveData)
        if(!checksumState.first){showDialog("Gen 1 → Gen 7 multi-transfer",checksumState.second);return}
        val hasCapacity=(0 until slotCount/30).any{box->
            (0 until 30).count{slot->gen6DestinationSlotIsStructurallyEmpty(saveData,boxOffset,slotCount,maxSpecies,box*30+slot)}>=chosen.size
        }
        if(!hasCapacity){showDialog("Gen 1 → Gen 7 multi-transfer","No single Gen 7 PC box has enough structurally empty slots for all ${chosen.size} selected Pokémon. Nonzero destination records were left unchanged.");return}
        val selected=try{loadBoundGen1BankBatch(chosen,bankDir(),::parseGen1BankPayload)}catch(e:Exception){showDialog("Gen 1 → Gen 7 multi-transfer",e.message?:"A selected Bank record could not be read.");return}
        val trainer=parseTrainerGen7(saveData,saveData.size)?:run{showDialog("Gen 1 → Gen 7 multi-transfer","PSE could not read the destination Gen 7 trainer data.");return}
        val loading=AlertDialog.Builder(this).setTitle("Prepare ${chosen.size} Gen 1 → Gen 7 conversions").setMessage("Checking every preserved Gen 1 record and preparing native PK7 conversions…").setCancelable(false).create()
        loading.show()
        val prepared=ArrayList<Gen1Gen7BatchPrepared>()
        fun stop(message:String){if(loading.isShowing)loading.dismiss();showDialog("Gen 1 → Gen 7 multi-transfer",message)}
        fun next(index:Int){
            if(index>=selected.size){
                if(loading.isShowing)loading.dismiss()
                if(!selected.all{it.sourceBinding.matches(it.bankBytes,true)}){showDialog("Gen 1 → Gen 7 multi-transfer","A selected Bank identity changed during preparation. Nothing was changed.");return}
                chooseSelectedGen1BankToGen7Destination(prepared,saveData)
                return
            }
            val item=selected[index];val bankFile=item.bankFile;val bankBytes=item.bankBytes;val source=item.source
            val dex=gen1DexFromInternalIndex(source.species)?:run{stop("A selected Gen 1 species could not be mapped to the National Pokédex. Nothing was changed.");return}
            loadGen1MoveSourcesAsync(dex) movesDone@{moveSources->
                if(moveSources==null){stop("PSE could not load the Gen 1 move-legality data. Check your connection and try again; no Bank record was changed.");return@movesDone}
                loadGen1SpeciesMetaAsync(dex) speciesDone@{speciesMeta->
                    if(speciesMeta==null){stop("PSE could not load the Gen 1 species data. Check your connection and try again; no Bank record was changed.");return@speciesDone}
                    loadGen1ToGen7TransferMetaAsync(dex,source.moves) transferDone@{transferMeta->
                        if(!item.sourceBinding.matches(item.bankBytes,true)){stop("A selected Bank identity changed during preparation. Nothing was changed.");return@transferDone}
                        if(transferMeta==null){stop("PSE could not load the Gen 7 conversion data. Check your connection and try again; no Bank record was changed.");return@transferDone}
                        val illegalMoves=source.moves.filter{moveId->moveId!=0&&moveSources[moveId]?.let{it.nonLevelSource||(it.minimumLevel!=null&&it.minimumLevel<=source.level)}!=true}
                        if(illegalMoves.isNotEmpty()){
                            val name=source.nickname.ifBlank{gen1SpeciesNameFromInternal(source.species)}
                            stop("$name needs the existing Make Legal & Transfer review. Transfer that Pokémon individually so PSE can show and confirm its exact legalisation plan; the complete multi-selection was left unchanged.")
                            return@transferDone
                        }
                        val pk7=try{buildGen1ToGen7Pk7(bankBytes,source,saveData,trainer,speciesMeta,transferMeta)}catch(e:Exception){stop(e.message?:"A selected Pokémon could not be converted to PK7.");return@transferDone}
                        val converted=parsePk6(pk7,0,PK6_STORED_SIZE,false,807)?:run{stop("A prepared Gen 7 conversion could not be verified. Nothing was changed.");return@transferDone}
                        prepared.add(Gen1Gen7BatchPrepared(bankFile,bankBytes,source,item.sourceBinding,pk7,converted))
                        next(index+1)
                    }
                }
            }
        }
        next(0)
    }

    private fun chooseSelectedGen1BankToGen7Destination(prepared:List<Gen1Gen7BatchPrepared>,preparedDestination:ByteArray){
        val file=importedFile?:return
        val data=runCatching{file.readBytes()}.getOrNull()?:return
        if(!data.contentEquals(preparedDestination)){showDialog("Gen 1 → Gen 7 multi-transfer","The Gen 7 destination changed while the batch was being prepared. Nothing was written; try again.");return}
        val(boxOffset,slotCount,maxSpecies)=boxStorageInfo(data)?:return
        val targets=(0 until slotCount/30).mapNotNull{box->
            val empty=(0 until 30).filter{slot->gen6DestinationSlotIsStructurallyEmpty(data,boxOffset,slotCount,maxSpecies,box*30+slot)}
            if(empty.size>=prepared.size)box to empty.size else null
        }
        if(targets.isEmpty()){showDialog("Gen 1 → Gen 7 multi-transfer","No single Gen 7 PC box has enough structurally empty slots for the complete prepared batch. Nonzero destination records were left unchanged.");return}
        val labels=targets.map{"Box ${it.first+1} • ${it.second} structurally empty slots"}.toTypedArray()
        val disclosure=gen1ToGen7BatchDisclosure(prepared.map{item->val p=item.source;val q=item.converted;Gen1ToGen7BatchDisclosure(p.nickname.ifBlank{gen1SpeciesNameFromInternal(p.species)},requireNotNull(gen1DexFromInternalIndex(p.species)),p.level,p.experience,p.moves,p.movePP,p.movePpUps,p.statExp,item.bankBytes[GEN1_BANK_HEADER_SIZE+7].toInt() and 0xFF,q,readU32LE(decryptPk6IfNeeded(item.pk7),0),gen6Pk6HandlerName(item.pk7))})
        showGen1ToGen7BatchDisclosureAndDestination(this,prepared.size,disclosure,labels){selectedTarget->transferSelectedGen1BankToGen7(prepared,preparedDestination,targets[selectedTarget].first)}
    }

    private fun transferSelectedGen1BankToGen7(preparedBatch:List<Gen1Gen7BatchPrepared>,preparedDestination:ByteArray,box:Int){
        val file=importedFile?:return
        val openedFile=file;val openedUri=currentSourceUri;val openedLabel=currentSourceLabel
        fun bound()=importedFile?.absolutePath==openedFile.absolutePath&&currentSourceUri==openedUri&&currentSourceLabel==openedLabel
        val destinationBinding=PseBankDestinationBinding.capture(file)
        if(destinationBinding==null||!destinationBinding.matches(preparedDestination,bound())){
            showDialog("Gen 1 → Gen 7 multi-transfer","The destination identity changed after review. Reopen PSE Bank. Nothing was changed.");return
        }
        val fresh=runCatching{file.readBytes()}.getOrElse{showDialog("Gen 1 → Gen 7 multi-transfer failed","The fresh Gen 7 destination save could not be read. Nothing was changed.");return}
        val removed=ArrayList<Pair<File,ByteArray>>();val cleanupAttempted=HashSet<String>()
        var writeStarted=false;var safetyBackup:File?=null;var stagedDestination:ByteArray?=null;var bankRecovery:ExactBankRecoverySet?=null
        try{
            require(destinationBinding.matches(preparedDestination,bound())){"The Gen 7 destination identity changed. Nothing was changed."}
            require(preparedBatch.isNotEmpty()){"No Gen 1 Bank entries were prepared."}
            require(preparedBatch.map{it.bankFile.absolutePath}.distinct().size==preparedBatch.size){"The selected Gen 1 Bank entries contain a duplicate."}
            require(fresh.contentEquals(preparedDestination)){"The Gen 7 destination changed after the batch was prepared. Nothing was changed."}
            require(generationForSize(fresh.size)==7){"The loaded destination is no longer a Gen 7 save."}
            val info=boxStorageInfo(fresh)?:error("PSE could not resolve the Gen 7 PC storage.")
            val(boxOffset,slotCount,maxSpecies)=info
            require(box in 0 until slotCount/30){"The selected Gen 7 destination box is invalid."}
            val storageLength=slotCount*PK6_STORED_SIZE
            require(boxOffset>=0&&boxOffset+storageLength<=fresh.size){"The Gen 7 PC storage is outside the save."}
            val freshChecksums=validateGen7CoreChecksums(fresh);require(freshChecksums.first){freshChecksums.second}
            val refreshed=preparedBatch.map{prepared->
                val current=runCatching{prepared.bankFile.readBytes()}.getOrElse{error("A selected Gen 1 Bank record could not be read.")}
                require(prepared.sourceBinding.matches(prepared.bankBytes,true)&&current.contentEquals(prepared.bankBytes)){"A selected Gen 1 Bank identity or record changed after preparation. Nothing was changed."}
                val source=parseGen1BankPayload(current)?:error("A selected Gen 1 Bank record failed validation.")
                require(sameGen1TransferRecord(source,prepared.source)){"A selected Gen 1 Bank record no longer matches its prepared conversion."}
                require(prepared.pk7.size==PK6_STORED_SIZE){"A prepared PK7 has an unexpected size."}
                val converted=parsePk6(prepared.pk7,0,PK6_STORED_SIZE,false,807)?:error("A prepared PK7 could not be parsed.")
                val mismatch=gen1ToGen7PkMismatch(converted,prepared.converted);val ppUpsMatch=gen6Pk6PpUps(prepared.pk7).contentEquals(source.movePpUps)
                require(mismatch==null&&converted.tid==source.tid&&ppUpsMatch){"A prepared PK7 differs in ${mismatch?:if(converted.tid!=source.tid)"Trainer ID" else "PP-Up data"}."}
                Gen1Gen7BatchPrepared(prepared.bankFile,current,source,prepared.sourceBinding,prepared.pk7,converted)
            }
            val currentEmpty=(0 until 30).filter{slot->gen6DestinationSlotIsStructurallyEmpty(fresh,boxOffset,slotCount,maxSpecies,box*30+slot)}
            require(currentEmpty.size>=refreshed.size){"Gen 7 Box ${box+1} no longer has enough structurally empty slots for the complete batch. Nonzero records were left unchanged."}
            val work=fresh.copyOf();val beforeStorage=fresh.copyOfRange(boxOffset,boxOffset+storageLength)
            val expected=ArrayList<Triple<Int,ByteArray,PokemonEntry>>();val changed=HashSet<Int>()
            for(i in refreshed.indices){
                val prepared=refreshed[i];val target=box*30+currentEmpty[i];val relative=target*PK6_STORED_SIZE;val offset=boxOffset+relative
                require(gen6DestinationSlotIsStructurallyEmpty(work,boxOffset,slotCount,maxSpecies,target)){"A Gen 7 destination slot is no longer structurally empty. Nonzero records were left unchanged."}
                prepared.pk7.copyInto(work,offset)
                val stagedBytes=work.copyOfRange(offset,offset+PK6_STORED_SIZE)
                val staged=parsePk6(stagedBytes,0,PK6_STORED_SIZE,false,807)?:error("A staged PK7 could not be parsed.")
                val mismatch=gen1ToGen7PkMismatch(staged,prepared.converted);val ppUpsMatch=gen6Pk6PpUps(stagedBytes).contentEquals(prepared.source.movePpUps)
                require(stagedBytes.contentEquals(prepared.pk7)&&mismatch==null&&ppUpsMatch){"A staged PK7 differs in ${mismatch?:if(!ppUpsMatch)"PP-Up data" else "native record bytes"}."}
                for(index in relative until relative+PK6_STORED_SIZE)changed.add(index)
                expected.add(Triple(target,prepared.pk7,staged))
            }
            fun pcStorageIsolated(data:ByteArray):Boolean{
                if(data.size!=fresh.size)return false
                val after=data.copyOfRange(boxOffset,boxOffset+storageLength)
                return after.indices.all{it in changed||after[it]==beforeStorage[it]}
            }
            require(pcStorageIsolated(work)){"Unrelated Gen 7 PC data changed during batch staging."}
            for((_,_,staged) in expected)registerGen7Dex(work,staged.species,staged.form,staged.gender,staged.shiny,staged.language)
            updateGen7SaveBlockChecksum(work,14,boxOffset,storageLength)
            val dexInfo=gen7BlockInfo(work,6)?:error("Gen 7 Pokédex block layout is unavailable.")
            updateGen7SaveBlockChecksum(work,6,dexInfo.first,dexInfo.second)
            finalizeGen7Save(work)
            require(expected.all{gen7DexRegistered(work,it.third)}){"A staged Gen 7 Pokédex registration did not verify for the complete batch."}
            val stagedChecksums=validateGen7CoreChecksums(work);require(stagedChecksums.first){stagedChecksums.second}
            require(pcStorageIsolated(work)){"Unrelated Gen 7 PC data changed while rebuilding batch integrity data."}
            require(destinationBinding.matches(fresh,bound())&&refreshed.all{it.sourceBinding.matches(it.bankBytes,true)}){"The destination or a selected Bank identity changed before backup. Nothing was written."}
            val recovery=File.createTempFile(backupGameName(file)+"_PreGen1BatchToGen7_",".backup",backupsDir());safetyBackup=recovery
            recovery.writeBytes(fresh);require(recovery.readBytes().contentEquals(fresh)){"The exact Gen 7 batch recovery backup did not verify."};retainGen1ToGen7SafetyBackup(recovery,fresh)
            val sourceRecovery=ExactBankRecoverySet("PSE_G1ToG7_BatchRecovery_");for(prepared in refreshed)sourceRecovery.capture(prepared.bankBytes,bankDir());require(sourceRecovery.matches()){ "The exact Gen 1 Bank recovery set did not verify." };bankRecovery=sourceRecovery
            require(destinationBinding.matches(fresh,bound())&&isBackupKept(recovery)&&recovery.readBytes().contentEquals(fresh)&&sourceRecovery.matches()&&refreshed.all{it.sourceBinding.matches(it.bankBytes,true)}){"The destination, selected Bank identities or retained recovery backup changed before writing."}
            stagedDestination=work.copyOf()
            writeStarted=true
            file.writeBytes(work)
            val written=file.readBytes();require(destinationBinding.matches(work,bound())&&written.contentEquals(work)){"The Gen 7 destination changed while the batch was being written."};require(boxStorageInfo(written)==info){"The persisted Gen 7 PC layout changed unexpectedly."}
            for((target,bytes,staged) in expected){
                val offset=boxOffset+target*PK6_STORED_SIZE;val actual=written.copyOfRange(offset,offset+PK6_STORED_SIZE)
                val persisted=parsePk6(actual,0,PK6_STORED_SIZE,false,807)?:error("A persisted PK7 could not be parsed.")
                val mismatch=gen1ToGen7PkMismatch(persisted,staged);val ppUpsMatch=gen6Pk6PpUps(actual).contentEquals(gen6Pk6PpUps(bytes));val dexMatches=gen7DexRegistered(written,persisted)
                require(actual.contentEquals(bytes)&&mismatch==null&&ppUpsMatch&&dexMatches){"Persisted PK7 verification failed: ${mismatch?:if(!ppUpsMatch)"PP-Up data" else if(!dexMatches)"Pokédex registration" else "native record bytes"}."}
            }
            val writtenChecksums=validateGen7CoreChecksums(written);require(writtenChecksums.first){writtenChecksums.second}
            require(pcStorageIsolated(written)){"Unrelated Gen 7 PC data changed after batch persistence."}
            for(prepared in refreshed){
                require(destinationBinding.matches(work,bound())&&isBackupKept(recovery)&&recovery.readBytes().contentEquals(fresh)&&sourceRecovery.matches()&&exactBankPathsAbsent(removed.map{it.first})){"The verified destination identity, data, removed paths or retained recovery changed before Bank cleanup."}
                val current=runCatching{prepared.bankFile.readBytes()}.getOrElse{error("A selected Gen 1 Bank record could not be re-read before cleanup.")}
                require(prepared.sourceBinding.matches(prepared.bankBytes,true)&&current.contentEquals(prepared.bankBytes) && parseGen1BankPayload(current)?.let{sameGen1TransferRecord(it,prepared.source)}==true){"A selected Gen 1 Bank identity or record changed before cleanup. The Gen 7 destination was restored."}
                cleanupAttempted.add(prepared.bankFile.absolutePath);require(prepared.bankFile.delete()&&!prepared.bankFile.exists()){"A Bank entry could not be removed after destination verification."};removed.add(prepared.bankFile to prepared.bankBytes)
                require(sourceRecovery.matches()&&exactBankPathsAbsent(removed.map{it.first})){"A removed Gen 1 Bank path or retained recovery changed during cleanup."}
            }
            require(destinationBinding.matches(work,bound())&&removed.size==refreshed.size&&exactBankPathsAbsent(removed.map{it.first})&&sourceRecovery.matches()){ "The verified Gen 7 destination, removed Bank batch or retained recovery changed after cleanup." }
        }catch(e:Exception){
            for(prepared in preparedBatch)runCatching{restoreExactMissingStandardBankRecord(prepared.bankFile,prepared.bankBytes,cleanupAttempted.contains(prepared.bankFile.absolutePath),null)}
            val banksRestored=preparedBatch.all{prepared->runCatching{val bytes=prepared.bankFile.readBytes();prepared.sourceBinding.matches(prepared.bankBytes,true)&&bytes.contentEquals(prepared.bankBytes)&&parseGen1BankPayload(bytes)?.let{sameGen1TransferRecord(it,prepared.source)}==true}.getOrDefault(false)}
            val saveRestored=runCatching{
                val current=file.readBytes();require(destinationBinding.matches(current,bound())){"The Gen 7 destination identity changed during recovery; the current file was left untouched."}
                restoreExactStagedDestination(file,fresh,stagedDestination,writeStarted,"No Generation 7 destination write began; unknown current bytes were left untouched.","The Generation 7 destination no longer matches this batch transfer's staged bytes; the retained backup was left for recovery.")
                val restored=file.readBytes()
                require(destinationBinding.matches(fresh,bound())&&restored.contentEquals(fresh)&&validateGen7CoreChecksums(restored).first){"The restored Generation 7 destination did not verify."}
                true
            }.getOrDefault(false)
            val recovery=when{
                banksRestored&&saveRestored->"The exact Gen 7 destination and every selected Gen 1 Bank record were restored or kept and verified."
                banksRestored->"Every selected Gen 1 Bank record was restored or kept, but destination recovery was not verified. Use the retained Gen 7 backup before editing again."
                saveRestored->"The exact Gen 7 destination was restored or kept, but one or more Gen 1 Bank records were not verified. Keep the remaining Bank records and recovery data for review."
                else->"Automatic recovery could not be fully verified. Stop editing and keep the retained backup and remaining files for review."
            }
            val retainedBankNames=bankRecovery?.retainedNames().orEmpty();val retainedBankNotice=if(retainedBankNames.isBlank())"" else "\n\nBank recovery snapshots: $retainedBankNames"
            showDialog("Gen 1 → Gen 7 multi-transfer failed","${e.message?:"Unknown error"}\n\n$recovery"+(safetyBackup?.let{"\n\nRecovery backup: "+it.name}?:"")+retainedBankNotice)
            return
        }
        val recoveryNotice=if(bankRecovery==null||bankRecovery?.cleanupExact()==true)"" else "; one or more exact Bank recoveries retained for review"
        selectedBankFiles.clear();bankMultiSelectMode=false
        Toast.makeText(this,"${preparedBatch.size} Gen 1 Pokémon transferred to Gen 7 Box ${box+1} and verified$recoveryNotice",Toast.LENGTH_LONG).show()
        showPokemonViewer("boxes",box)
    }


    private fun gen1SwapBytes(data:ByteArray,a:Int,b:Int,size:Int){if(a==b)return;val t=data.copyOfRange(a,a+size);data.copyOfRange(b,b+size).copyInto(data,a);t.copyInto(data,b)}
    private fun sameGen1ReorderRecord(a:Gen1Pokemon,b:Gen1Pokemon):Boolean=
        a.species==b.species&&a.tid==b.tid&&a.nickname==b.nickname&&a.otName==b.otName&&a.level==b.level&&a.currentHp==b.currentHp&&a.maxHp==b.maxHp&&a.experience==b.experience&&a.moves.contentEquals(b.moves)&&a.movePP.contentEquals(b.movePP)&&a.movePpUps.contentEquals(b.movePpUps)&&a.statExp.contentEquals(b.statExp)&&a.dvs.contentEquals(b.dvs)

    private fun loadFreshGen1ReorderPokemon(party:Boolean,boxNumber:Int?,expected:List<Gen1Pokemon>,title:String):List<Gen1Pokemon>?{
        if(expected.size<2){showDialog(title,"There are not enough Pokémon to reorder.");return null}
        if(expected.map{it.slot}.distinct().size!=expected.size){showDialog(title,"The displayed Pokémon list contains duplicate slots. Reopen it and try again.");return null}
        val file=importedFile?:return null
        val fresh=runCatching{file.readBytes()}.getOrElse{showDialog(title,"The fresh Gen 1 save could not be read. Nothing was changed.");return null}
        val integrity=verifyGen1SaveIntegrity(fresh)
        if(!integrity.first){showDialog(title,integrity.second+" Nothing was changed.");return null}
        if(!party&&!gen1BoxIsWritable(fresh,requireNotNull(boxNumber))){showDialog(title,"Box ${requireNotNull(boxNumber)} is not writable. Nothing was changed.");return null}
        val current=(if(party)parseGen1Party(fresh)else parseGen1Box(fresh,requireNotNull(boxNumber)).pokemon).sortedBy{it.slot}
        val opened=expected.sortedBy{it.slot}
        if(current.size!=opened.size||current.zip(opened).any{(a,b)->!sameGen1ReorderRecord(a,b)}){showDialog(title,"The Pokémon order or contents changed. Reopen the list and try again.");return null}
        return current
    }

    private fun reorderGen1Pokemon(party:Boolean,boxNumber:Int?,from:Gen1Pokemon,to:Gen1Pokemon){
        val file=importedFile?:return;val original=runCatching{file.readBytes()}.getOrNull()?:return
        if(from.slot==to.slot)return
        try{
            val integrity=verifyGen1SaveIntegrity(original);require(integrity.first){integrity.second}
            require(original.size==SIZE_G1&&hasValidGen1MainChecksum(original)){"The fresh Gen 1 save is invalid."};val before=if(party)parseGen1Party(original)else parseGen1Box(original,requireNotNull(boxNumber)).pokemon;val freshFrom=before.firstOrNull{it.slot==from.slot};val freshTo=before.firstOrNull{it.slot==to.slot};require(freshFrom!=null&&freshTo!=null&&sameGen1ReorderRecord(freshFrom,from)&&sameGen1ReorderRecord(freshTo,to)){"A selected Pokémon changed before reordering."};val work=original.copyOf()
            if(party){
                val count=(work[GEN1_PARTY_COUNT].toInt() and 0xFF).coerceIn(0,6);require(from.slot in 0 until count && to.slot in 0 until count){"Party slot is outside the current party."}
                val sp=work[GEN1_PARTY_SPECIES+from.slot];work[GEN1_PARTY_SPECIES+from.slot]=work[GEN1_PARTY_SPECIES+to.slot];work[GEN1_PARTY_SPECIES+to.slot]=sp
                gen1SwapBytes(work,GEN1_PARTY_MONS+from.slot*GEN1_PARTY_MON_SIZE,GEN1_PARTY_MONS+to.slot*GEN1_PARTY_MON_SIZE,GEN1_PARTY_MON_SIZE)
                gen1SwapBytes(work,GEN1_PARTY_OT_NAMES+from.slot*11,GEN1_PARTY_OT_NAMES+to.slot*11,11);gen1SwapBytes(work,GEN1_PARTY_NICKS+from.slot*11,GEN1_PARTY_NICKS+to.slot*11,11);updateGen1MainChecksum(work)
            }else{
                val box=requireNotNull(boxNumber);require(gen1BoxIsWritable(work,box)){"Box $box is not writable."};val base=gen1BoxDataOffset(work,box);val count=(work[base].toInt() and 0xFF).coerceIn(0,GEN1_BOX_CAPACITY);require(from.slot in 0 until count && to.slot in 0 until count){"Box slot is outside the current Pokémon list."}
                val sp=work[base+GEN1_BOX_SPECIES_LIST+from.slot];work[base+GEN1_BOX_SPECIES_LIST+from.slot]=work[base+GEN1_BOX_SPECIES_LIST+to.slot];work[base+GEN1_BOX_SPECIES_LIST+to.slot]=sp
                gen1SwapBytes(work,base+GEN1_BOX_MONS+from.slot*GEN1_BOX_MON_SIZE,base+GEN1_BOX_MONS+to.slot*GEN1_BOX_MON_SIZE,GEN1_BOX_MON_SIZE);gen1SwapBytes(work,base+GEN1_BOX_OT_NAMES+from.slot*11,base+GEN1_BOX_OT_NAMES+to.slot*11,11);gen1SwapBytes(work,base+GEN1_BOX_NICKS+from.slot*11,base+GEN1_BOX_NICKS+to.slot*11,11);updateGen1ChecksumsAfterBoxChange(work,box)
            }
            val expected=before.toMutableList();val tmp=expected[from.slot];expected[from.slot]=expected[to.slot];expected[to.slot]=tmp;val pre=verifyGen1WriteState(work,if(party)null else requireNotNull(boxNumber));require(pre.first){pre.second};val staged=if(party)parseGen1Party(work)else parseGen1Box(work,requireNotNull(boxNumber)).pokemon;require(staged.size==expected.size&&staged.zip(expected).all{(a,b)->sameGen1ReorderRecord(a,b)}){"The staged Pokémon order or fields did not verify."};file.writeBytes(work);val written=file.readBytes();require(written.contentEquals(work)){"The save changed while the Gen 1 reorder was being written."};val post=verifyGen1WriteState(written,if(party)null else requireNotNull(boxNumber));require(post.first){post.second};val persisted=if(party)parseGen1Party(written)else parseGen1Box(written,requireNotNull(boxNumber)).pokemon;require(persisted.size==expected.size&&persisted.zip(expected).all{(a,b)->sameGen1ReorderRecord(a,b)}){"The persisted Pokémon order or fields did not verify."}
            Toast.makeText(this,"Gen 1 Pokémon reordered and verified",Toast.LENGTH_LONG).show();showGen1PokemonViewer(if(party)"party" else "boxes",boxNumber)
        }catch(e:Exception){runCatching{file.writeBytes(original)};showDialog("Reorder failed",(e.message?:"Unknown error")+"\n\nThe original working save was restored.")}
    }

    private fun chooseGen1ReorderSourceFromPokemon(boxNumber:Int,source:Gen1Pokemon,pokemon:List<Gen1Pokemon>){
        val current=loadFreshGen1ReorderPokemon(false,boxNumber,pokemon,"Reorder Box")?:return
        val boundSource=current.firstOrNull{it.slot==source.slot&&sameGen1ReorderRecord(it,source)}?:run{showDialog("Reorder Box","The selected Pokémon changed. Reopen the box and try again.");return}
        val choices=current.filter{it.slot!=boundSource.slot}
        if(choices.isEmpty()){showDialog("Reorder Box","There is no other Pokémon in this box to swap with.");return}
        val labels=choices.map{"Slot ${it.slot+1} • ${it.nickname.ifBlank{gen1SpeciesNameFromInternal(it.species)}} • Lv.${it.level}"}.toTypedArray()
        AlertDialog.Builder(this).setTitle("Swap with which Pokémon?").setItems(labels){_,i->reorderGen1Pokemon(false,boxNumber,boundSource,choices[i])}.setNegativeButton("Cancel",null).show()
    }

    private fun chooseGen1ReorderSource(party:Boolean,boxNumber:Int?,pokemon:List<Gen1Pokemon>){
        val ordered=loadFreshGen1ReorderPokemon(party,boxNumber,pokemon,if(party)"Reorder Party" else "Reorder Box")?:return
        val labels=ordered.map{"${it.slot+1}. ${it.nickname.ifBlank{gen1SpeciesNameFromInternal(it.species)}} • Lv.${it.level}"}.toTypedArray()
        AlertDialog.Builder(this).setTitle(if(party)"Choose Party Pokémon to move" else "Choose Pokémon to reorder").setItems(labels){_,fromIndex->
            val source=ordered[fromIndex];val destinations=ordered.filter{it.slot!=source.slot};val dlabels=destinations.map{"Swap with slot ${it.slot+1} • ${it.nickname.ifBlank{gen1SpeciesNameFromInternal(it.species)}}"}.toTypedArray()
            AlertDialog.Builder(this).setTitle("Move ${source.nickname.ifBlank{gen1SpeciesNameFromInternal(source.species)}} to which position?").setItems(dlabels){_,toIndex->reorderGen1Pokemon(party,boxNumber,source,destinations[toIndex])}.setNegativeButton("Cancel",null).show()
        }.setNegativeButton("Cancel",null).show()
    }

    private fun sameGen1DeleteRecord(a:Gen1Pokemon,b:Gen1Pokemon):Boolean=
        a.species==b.species&&a.tid==b.tid&&a.nickname==b.nickname&&a.otName==b.otName&&a.level==b.level&&a.currentHp==b.currentHp&&a.maxHp==b.maxHp&&a.experience==b.experience&&a.moves.contentEquals(b.moves)&&a.movePP.contentEquals(b.movePP)&&a.movePpUps.contentEquals(b.movePpUps)&&a.statExp.contentEquals(b.statExp)&&a.dvs.contentEquals(b.dvs)
    private fun sameGen1DuplicateRecord(a:Gen1Pokemon,b:Gen1Pokemon,partyStats:Boolean=false):Boolean=
        a.species==b.species&&a.tid==b.tid&&a.nickname==b.nickname&&a.otName==b.otName&&a.level==b.level&&a.experience==b.experience&&a.moves.contentEquals(b.moves)&&a.movePP.contentEquals(b.movePP)&&a.movePpUps.contentEquals(b.movePpUps)&&a.statExp.contentEquals(b.statExp)&&a.dvs.contentEquals(b.dvs)&&(!partyStats||(a.currentHp==b.currentHp&&a.maxHp==b.maxHp))
    private fun editSelectedGen1Levels(party:Boolean,box:Int?,chosen:List<Gen1Pokemon>){
        if(chosen.isEmpty()){showDialog("Set selected level","Select one or more Pokémon first.");return}
        val file=importedFile?:return
        val fresh=runCatching{file.readBytes()}.getOrElse{showDialog("Set selected level","The fresh Gen 1 save could not be read. Nothing was changed.");return}
        val integrity=verifyGen1SaveIntegrity(fresh)
        if(!integrity.first){showDialog("Set selected level",integrity.second+" Nothing was changed.");return}
        if(!party&&(box==null||!gen1BoxIsWritable(fresh,box))){showDialog("Set selected level","The selected Gen 1 box is no longer writable. Nothing was changed.");return}
        if(chosen.map{it.slot}.distinct().size!=chosen.size){showDialog("Set selected level","The selection contains a duplicate Pokémon slot. Nothing was changed.");return}
        val source=if(party)parseGen1Party(fresh)else parseGen1Box(fresh,requireNotNull(box)).pokemon
        val selected=chosen.sortedBy{it.slot}
        if(selected.any{p->source.firstOrNull{it.slot==p.slot}?.let{sameGen1DuplicateRecord(it,p,party)}!=true}){showDialog("Set selected level","A selected Pokémon changed before the batch level edit. Nothing was changed.");return}
        val bound=selected.map{p->source.first{it.slot==p.slot}}
        val input=EditText(this).apply{inputType=android.text.InputType.TYPE_CLASS_NUMBER;hint="1–100";setSingleLine(true)}
        AlertDialog.Builder(this).setTitle("Set ${bound.size} Pokémon to level…").setView(input).setPositiveButton("Apply"){_,_->val level=input.text.toString().toIntOrNull();if(level==null||level !in 1..100){showDialog("Set selected level","Enter a level from 1 to 100. Nothing was changed.");return@setPositiveButton};applySelectedGen1Level(party,box,bound,level)}.setNegativeButton("Cancel",null).show()
    }
    private fun applySelectedGen1Level(party:Boolean,box:Int?,chosen:List<Gen1Pokemon>,level:Int){
        val file=importedFile?:return
        loadGen1TransferMeta(chosen){metaMap->if(metaMap==null){showDialog("Set selected level","PSE could not load the Gen 1 base-stat data needed to recalculate stats. Nothing was changed.");return@loadGen1TransferMeta}
            val original=runCatching{file.readBytes()}.getOrNull()?:return@loadGen1TransferMeta
            val integrity=verifyGen1SaveIntegrity(original)
            if(!integrity.first){showDialog("Set selected level",integrity.second+" Nothing was changed.");return@loadGen1TransferMeta}
            if(original.size!=SIZE_G1||!hasValidGen1MainChecksum(original)){showDialog("Set selected level","The fresh Gen 1 save is invalid. Nothing was changed.");return@loadGen1TransferMeta}
            if(!party&&!gen1BoxIsWritable(original,requireNotNull(box))){showDialog("Set selected level","The selected Gen 1 box is no longer writable. Nothing was changed.");return@loadGen1TransferMeta}
            try{val work=original.copyOf();val before=if(party)parseGen1Party(original)else parseGen1Box(original,requireNotNull(box)).pokemon;for(p in chosen.sortedBy{it.slot}){val current=before.firstOrNull{it.slot==p.slot};require(current!=null&&current.species==p.species&&current.tid==p.tid&&current.nickname==p.nickname&&current.otName==p.otName&&current.level==p.level&&current.experience==p.experience&&current.moves.contentEquals(p.moves)&&current.movePP.contentEquals(p.movePP)&&current.movePpUps.contentEquals(p.movePpUps)&&current.dvs.contentEquals(p.dvs)&&current.statExp.contentEquals(p.statExp)){"A selected Pokémon changed before the batch level edit."};val dex=requireNotNull(gen1DexFromInternalIndex(p.species));val meta=requireNotNull(metaMap[dex]);val mon=if(party)GEN1_PARTY_MONS+p.slot*GEN1_PARTY_MON_SIZE else {val base=gen1BoxDataOffset(work,requireNotNull(box));require(base>=0){"The selected Gen 1 box is not writable."};base+GEN1_BOX_MONS+p.slot*GEN1_BOX_MON_SIZE};val experience=gen1ExperienceForLevel(meta.growth,level);work[mon+3]=level.toByte();writeU24BE(work,mon+14,experience);if(party){work[mon+33]=level.toByte();val stats=gen1CalculatedStats(meta,level,p.dvs,p.statExp);val max=stats[0].coerceAtLeast(1);val cur=if(p.maxHp>0&&p.currentHp>=p.maxHp)max else p.currentHp.coerceIn(0,max);writeU16BE(work,mon+1,cur);writeU16BE(work,mon+34,max);for(i in 1..4)writeU16BE(work,mon+34+i*2,stats[i])}}
                if(party)updateGen1MainChecksum(work)else updateGen1ChecksumsAfterBoxChange(work,requireNotNull(box));val pre=verifyGen1WriteState(work,if(party)null else requireNotNull(box));require(pre.first){pre.second};if(party)for(p in chosen){val dex=requireNotNull(gen1DexFromInternalIndex(p.species));val check=verifyGen1PartyDerivedStats(work,p.slot,requireNotNull(metaMap[dex]));require(check.first){check.second}};file.writeBytes(work);val written=file.readBytes();require(written.contentEquals(work)){"The save changed while the Gen 1 batch level edit was being written."};val post=verifyGen1WriteState(written,if(party)null else requireNotNull(box));require(post.first){post.second};val after=if(party)parseGen1Party(written)else parseGen1Box(written,requireNotNull(box)).pokemon;for(p in chosen){val q=after.firstOrNull{it.slot==p.slot}?:error("A selected Pokémon was missing after re-reading.");val dex=requireNotNull(gen1DexFromInternalIndex(p.species));val expected=gen1ExperienceForLevel(requireNotNull(metaMap[dex]).growth,level);require(q.level==level&&q.experience==expected){"A selected Pokémon level and experience did not verify."}};if(party)for(p in chosen){val dex=requireNotNull(gen1DexFromInternalIndex(p.species));val check=verifyGen1PartyDerivedStats(written,p.slot,requireNotNull(metaMap[dex]));require(check.first){check.second}};gen1PartyMultiSelectMode=false;gen1BoxMultiSelectMode=false;selectedGen1PartySlots.clear();selectedGen1BoxSlots.clear();Toast.makeText(this,"${chosen.size} Pokémon set to Lv.$level and verified",Toast.LENGTH_LONG).show();if(party)showGen1PokemonViewer("party")else showGen1PokemonViewer("boxes",box)
            }catch(e:Exception){runCatching{file.writeBytes(original)};showDialog("Set selected level failed",(e.message?:"Unknown error")+"\n\nThe original working save was restored and no partial batch edit was kept.")}}
    }

    private fun duplicateSelectedGen1Box(box:Int,chosen:List<Gen1Pokemon>){
        val file=importedFile?:return;val original=runCatching{file.readBytes()}.getOrNull()?:return
        val initialIntegrity=verifyGen1SaveIntegrity(original)
        if(!initialIntegrity.first){showDialog("Duplicate selected",initialIntegrity.second+" Nothing was changed.");return}
        if(original.size!=SIZE_G1||!hasValidGen1MainChecksum(original)){showDialog("Duplicate selected","The fresh Gen 1 save is invalid. Nothing was changed.");return}
        if(!gen1BoxIsWritable(original,box)){showDialog("Duplicate selected","Box $box is no longer writable. Nothing was changed.");return}
        if(chosen.isEmpty()){showDialog("Duplicate selected","Select one or more Pokémon first.");return}
        if(chosen.map{it.slot}.distinct().size!=chosen.size){showDialog("Duplicate selected","The selection contains a duplicate box slot. Nothing was changed.");return}
        val source=parseGen1Box(original,box).pokemon
        if(chosen.any{p->source.firstOrNull{it.slot==p.slot}?.let{sameGen1DuplicateRecord(it,p)}!=true}){showDialog("Duplicate selected","A selected Pokémon changed before duplication. Nothing was changed.");return}
        if(source.size+chosen.size>20){showDialog("Duplicate selected","Box $box does not have room for all ${chosen.size} duplicates. Nothing was changed.");return}
        try{require(original.size==SIZE_G1&&hasValidGen1MainChecksum(original)){"The fresh Gen 1 save is invalid."};require(gen1BoxIsWritable(original,box)){"Box $box is not writable."};val ordered=chosen.sortedBy{it.slot}.map{p->source.firstOrNull{it.slot==p.slot}.also{require(it!=null&&sameGen1DuplicateRecord(it,p)){"A selected Pokémon changed before duplication."}}!!};val work=original.copyOf();val payloads=ordered.map{p->buildGen1BankPayload(original,p,"Box $box • Slot ${p.slot+1}",false)}
            for(raw in payloads){val o=GEN1_BANK_HEADER_SIZE;appendGen1BoxPokemon(work,box,raw.copyOfRange(o,o+GEN1_BOX_MON_SIZE),raw.copyOfRange(o+GEN1_BOX_MON_SIZE,o+GEN1_BOX_MON_SIZE+11),raw.copyOfRange(o+GEN1_BOX_MON_SIZE+11,GEN1_BANK_FILE_SIZE))}
            val pre=verifyGen1WriteState(work,box);require(pre.first){pre.second};val staged=parseGen1Box(work,box).pokemon;require(staged.size==source.size+ordered.size){"Box count did not verify."};for((i,p) in ordered.withIndex())require(sameGen1DuplicateRecord(staged[source.size+i],p)){"A staged duplicate did not preserve every stored field."};for(p in source)require(staged.firstOrNull{it.slot==p.slot}?.let{sameGen1DuplicateRecord(it,p)}==true){"An original boxed Pokémon changed."};file.writeBytes(work);val written=file.readBytes();require(written.contentEquals(work)){"The save changed while the Gen 1 box duplicates were being written."};val post=verifyGen1WriteState(written,box);require(post.first){post.second};val persisted=parseGen1Box(written,box).pokemon;require(persisted.size==source.size+ordered.size){"Box count did not verify after re-reading."};for((i,p) in ordered.withIndex())require(sameGen1DuplicateRecord(persisted[source.size+i],p)){"A persisted duplicate did not preserve every stored field."};for(p in source)require(persisted.firstOrNull{it.slot==p.slot}?.let{sameGen1DuplicateRecord(it,p)}==true){"An original boxed Pokémon changed after re-reading."};gen1BoxMultiSelectMode=false;selectedGen1BoxSlots.clear();Toast.makeText(this,"${ordered.size} Pokémon duplicated and verified",Toast.LENGTH_LONG).show();showGen1PokemonViewer("boxes",box)
        }catch(e:Exception){runCatching{file.writeBytes(original)};showDialog("Duplicate selected failed",(e.message?:"Unknown error")+"\n\nThe original working save was restored and no partial duplication was kept.")}
    }
    private fun duplicateSelectedGen1Party(chosen:List<Gen1Pokemon>){
        val file=importedFile?:return
        if(chosen.isEmpty()){showDialog("Duplicate selected","Select one or more Pokémon first.");return}
        val selectionBase=runCatching{file.readBytes()}.getOrElse{showDialog("Duplicate selected","The fresh Gen 1 save could not be read. Nothing was changed.");return}
        val initialIntegrity=verifyGen1SaveIntegrity(selectionBase)
        if(!initialIntegrity.first){showDialog("Duplicate selected",initialIntegrity.second+" Nothing was changed.");return}
        if(chosen.map{it.slot}.distinct().size!=chosen.size){showDialog("Duplicate selected","The selection contains a duplicate party slot. Nothing was changed.");return}
        val selectionSource=parseGen1Party(selectionBase)
        if(chosen.any{p->selectionSource.firstOrNull{it.slot==p.slot}?.let{sameGen1DuplicateRecord(it,p,true)}!=true}){showDialog("Duplicate selected","A selected party Pokémon changed before duplication. Nothing was changed.");return}
        if(selectionSource.size+chosen.size>6){showDialog("Duplicate selected","The Gen 1 party can hold only 6 Pokémon. There is not enough room for all ${chosen.size} duplicates. Nothing was changed.");return}
        loadGen1TransferMeta(chosen){metaMap->if(metaMap==null){showDialog("Duplicate selected","PSE could not load the Gen 1 base-stat data needed to verify party stats. Nothing was changed.");return@loadGen1TransferMeta}
            val original=runCatching{file.readBytes()}.getOrNull()?:return@loadGen1TransferMeta
            val actionIntegrity=verifyGen1SaveIntegrity(original)
            if(!actionIntegrity.first){showDialog("Duplicate selected",actionIntegrity.second+" Nothing was changed.");return@loadGen1TransferMeta}
            if(original.size!=SIZE_G1||!hasValidGen1MainChecksum(original)){showDialog("Duplicate selected","The fresh Gen 1 save is invalid. Nothing was changed.");return@loadGen1TransferMeta}
            val source=parseGen1Party(original)
            if(source.size+chosen.size>6){showDialog("Duplicate selected","The Gen 1 party can hold only 6 Pokémon. There is not enough room for all ${chosen.size} duplicates. Nothing was changed.");return@loadGen1TransferMeta}
            try{val ordered=chosen.sortedBy{it.slot}.map{p->source.firstOrNull{it.slot==p.slot}.also{require(it!=null&&sameGen1DuplicateRecord(it,p,true)){"A selected Pokémon changed before duplication."}}!!};val work=original.copyOf();val payloads=ordered.map{p->p to buildGen1BankPayload(original,p,"Party • Slot ${p.slot+1}",true)}
                for((p,raw) in payloads){val dex=requireNotNull(gen1DexFromInternalIndex(p.species));val meta=requireNotNull(metaMap[dex]);val o=GEN1_BANK_HEADER_SIZE;appendGen1PartyPokemon(work,raw.copyOfRange(o,o+GEN1_BOX_MON_SIZE),raw.copyOfRange(o+GEN1_BOX_MON_SIZE,o+GEN1_BOX_MON_SIZE+11),raw.copyOfRange(o+GEN1_BOX_MON_SIZE+11,GEN1_BANK_FILE_SIZE),meta,p)}
                val pre=verifyGen1WriteState(work,null);require(pre.first){pre.second};val staged=parseGen1Party(work);require(staged.size==source.size+ordered.size){"Party count did not verify."};for((i,p) in ordered.withIndex()){val mon=staged[source.size+i];require(sameGen1DuplicateRecord(mon,p)){"A staged party duplicate did not preserve every stored field."};val dex=requireNotNull(gen1DexFromInternalIndex(mon.species));val check=verifyGen1PartyDerivedStats(work,mon.slot,requireNotNull(metaMap[dex]));require(check.first){check.second}};for(p in source)require(staged.firstOrNull{it.slot==p.slot}?.let{sameGen1DuplicateRecord(it,p,true)}==true){"An original party Pokémon changed."}
                file.writeBytes(work);val written=file.readBytes();require(written.contentEquals(work)){"The save changed while the Gen 1 party duplicates were being written."};val post=verifyGen1WriteState(written,null);require(post.first){post.second};val persisted=parseGen1Party(written);require(persisted.size==source.size+ordered.size){"Party count did not verify after re-reading."};for((i,p) in ordered.withIndex()){val mon=persisted[source.size+i];require(sameGen1DuplicateRecord(mon,p)){"A persisted party duplicate did not preserve every stored field."};val dex=requireNotNull(gen1DexFromInternalIndex(mon.species));val check=verifyGen1PartyDerivedStats(written,mon.slot,requireNotNull(metaMap[dex]));require(check.first){check.second}};for(p in source)require(persisted.firstOrNull{it.slot==p.slot}?.let{sameGen1DuplicateRecord(it,p,true)}==true){"An original party Pokémon changed after re-reading."}
                gen1PartyMultiSelectMode=false;selectedGen1PartySlots.clear();Toast.makeText(this,"${ordered.size} Pokémon duplicated and verified",Toast.LENGTH_LONG).show();showGen1PokemonViewer("party")
            }catch(e:Exception){runCatching{file.writeBytes(original)};showDialog("Duplicate selected failed",(e.message?:"Unknown error")+"\n\nThe original working save was restored and no partial duplication was kept.")}
        }
    }

    private fun confirmDeleteSelectedGen1Box(box:Int,chosen:List<Gen1Pokemon>){
        if(chosen.isEmpty()){showDialog("Delete selected","Select one or more Pokémon first.");return}
        val file=importedFile?:return
        val fresh=runCatching{file.readBytes()}.getOrElse{showDialog("Delete selected","The fresh Gen 1 save could not be read. Nothing was changed.");return}
        val integrity=verifyGen1SaveIntegrity(fresh)
        if(!integrity.first){showDialog("Delete selected",integrity.second+" Nothing was changed.");return}
        if(!gen1BoxIsWritable(fresh,box)){showDialog("Delete selected","Box $box is not writable. Nothing was changed.");return}
        val source=parseGen1Box(fresh,box).pokemon
        val selected=chosen.sortedBy{it.slot}
        if(selected.map{it.slot}.distinct().size!=selected.size||selected.any{p->source.firstOrNull{it.slot==p.slot}?.let{sameGen1DeleteRecord(it,p)}!=true}){showDialog("Delete selected","A selected Pokémon changed before confirmation. Nothing was changed.");return}
        val bound=selected.map{p->source.first{it.slot==p.slot}}
        AlertDialog.Builder(this).setTitle("Delete ${bound.size} selected Pokémon?").setMessage("This removes all selected Pokémon from Box $box. PSE will verify the save and restore the original if verification fails.").setPositiveButton("Delete"){_,_->deleteSelectedGen1Box(box,bound)}.setNegativeButton("Cancel",null).show()
    }
    private fun deleteSelectedGen1Box(box:Int,chosen:List<Gen1Pokemon>){
        val file=importedFile?:return;val original=runCatching{file.readBytes()}.getOrNull()?:return
        try{require(chosen.isNotEmpty()){"Select one or more Pokémon first."};val integrity=verifyGen1SaveIntegrity(original);require(integrity.first){integrity.second};require(gen1BoxIsWritable(original,box)){"Box $box is not writable."};val source=parseGen1Box(original,box).pokemon;val ordered=chosen.sortedByDescending{it.slot}.map{p->source.firstOrNull{it.slot==p.slot}.also{require(it!=null&&sameGen1DeleteRecord(it,p)){"A selected Pokémon changed before deletion."}}!!};require(ordered.map{it.slot}.distinct().size==ordered.size){"The selected box slots were duplicated."};val removed=ordered.map{it.slot}.toSet();val expected=source.filterNot{removed.contains(it.slot)};val work=original.copyOf();for(p in ordered)removeGen1BoxSlot(work,box,p.slot);val pre=verifyGen1WriteState(work,box);require(pre.first){pre.second};val staged=parseGen1Box(work,box).pokemon;require(staged.size==expected.size){"Box count did not verify."};require(staged.zip(expected).all{(a,b)->sameGen1DeleteRecord(a,b)}){"A surviving boxed Pokémon changed."};file.writeBytes(work);val written=file.readBytes();require(written.contentEquals(work)){"The save changed while the Gen 1 box deletion was being written."};val post=verifyGen1WriteState(written,box);require(post.first){post.second};val persisted=parseGen1Box(written,box).pokemon;require(persisted.size==expected.size){"Box count did not verify after re-reading."};require(persisted.zip(expected).all{(a,b)->sameGen1DeleteRecord(a,b)}){"A surviving boxed Pokémon changed after re-reading."};gen1BoxMultiSelectMode=false;selectedGen1BoxSlots.clear();Toast.makeText(this,"${ordered.size} Pokémon deleted and verified",Toast.LENGTH_LONG).show();showGen1PokemonViewer("boxes",box)}catch(e:Exception){runCatching{file.writeBytes(original)};showDialog("Delete selected failed",(e.message?:"Unknown error")+"\n\nThe original working save was restored and no partial deletion was kept.")}
    }
    private fun confirmDeleteSelectedGen1Party(chosen:List<Gen1Pokemon>){
        if(chosen.isEmpty()){showDialog("Delete selected","Select one or more Pokémon first.");return}
        val file=importedFile?:return
        val fresh=runCatching{file.readBytes()}.getOrElse{showDialog("Delete selected","The fresh Gen 1 save could not be read. Nothing was changed.");return}
        val integrity=verifyGen1SaveIntegrity(fresh)
        if(!integrity.first){showDialog("Delete selected",integrity.second+" Nothing was changed.");return}
        val source=parseGen1Party(fresh)
        val selected=chosen.sortedBy{it.slot}
        if(selected.map{it.slot}.distinct().size!=selected.size||selected.any{p->source.firstOrNull{it.slot==p.slot}?.let{sameGen1DeleteRecord(it,p)}!=true}){showDialog("Delete selected","A selected party Pokémon changed before confirmation. Nothing was changed.");return}
        val bound=selected.map{p->source.first{it.slot==p.slot}}
        if(source.size-bound.size<1){showDialog("Delete selected","Generation 1 must keep at least one Pokémon in the party. Nothing was changed.");return}
        AlertDialog.Builder(this).setTitle("Delete ${bound.size} selected Pokémon?").setMessage("This removes all selected Pokémon from the Party. PSE will verify the save and restore the original if verification fails.").setPositiveButton("Delete"){_,_->deleteSelectedGen1Party(bound)}.setNegativeButton("Cancel",null).show()
    }
    private fun deleteSelectedGen1Party(chosen:List<Gen1Pokemon>){
        val file=importedFile?:return;val original=runCatching{file.readBytes()}.getOrNull()?:return
        try{require(chosen.isNotEmpty()){"Select one or more Pokémon first."};val integrity=verifyGen1SaveIntegrity(original);require(integrity.first){integrity.second};val source=parseGen1Party(original);val ordered=chosen.sortedByDescending{it.slot}.map{p->source.firstOrNull{it.slot==p.slot}.also{require(it!=null&&sameGen1DeleteRecord(it,p)){"A selected Pokémon changed before deletion."}}!!};require(ordered.map{it.slot}.distinct().size==ordered.size){"The selected party slots were duplicated."};require(source.size-ordered.size>=1){"Generation 1 must keep at least one Pokémon in the party."};val removed=ordered.map{it.slot}.toSet();val expected=source.filterNot{removed.contains(it.slot)};val work=original.copyOf();for(p in ordered)removeGen1PartySlot(work,p.slot);val pre=verifyGen1WriteState(work,null);require(pre.first){pre.second};val staged=parseGen1Party(work);require(staged.size==expected.size){"Party count did not verify."};require(staged.zip(expected).all{(a,b)->sameGen1DeleteRecord(a,b)}){"A surviving party Pokémon changed."};file.writeBytes(work);val written=file.readBytes();require(written.contentEquals(work)){"The save changed while the Gen 1 party deletion was being written."};val post=verifyGen1WriteState(written,null);require(post.first){post.second};val persisted=parseGen1Party(written);require(persisted.size==expected.size){"Party count did not verify after re-reading."};require(persisted.zip(expected).all{(a,b)->sameGen1DeleteRecord(a,b)}){"A surviving party Pokémon changed after re-reading."};gen1PartyMultiSelectMode=false;selectedGen1PartySlots.clear();Toast.makeText(this,"${ordered.size} Pokémon deleted and verified",Toast.LENGTH_LONG).show();showGen1PokemonViewer("party")}catch(e:Exception){runCatching{file.writeBytes(original)};showDialog("Delete selected failed",(e.message?:"Unknown error")+"\n\nThe original working save was restored and no partial deletion was kept.")}
    }

    private fun appendGen1PartyPokemon(data:ByteArray,monRaw:ByteArray,otRaw:ByteArray,nickRaw:ByteArray,meta:Gen1SpeciesMeta,p:Gen1Pokemon):Int{
        val count=(data[GEN1_PARTY_COUNT].toInt() and 0xFF).coerceIn(0,6);require(count<6){"The party is full."}
        val mon=GEN1_PARTY_MONS+count*GEN1_PARTY_MON_SIZE
        data[GEN1_PARTY_COUNT]=(count+1).toByte();data[GEN1_PARTY_SPECIES+count]=monRaw[0];data[GEN1_PARTY_SPECIES+count+1]=0xFF.toByte()
        monRaw.copyInto(data,mon);otRaw.copyInto(data,GEN1_PARTY_OT_NAMES+count*11);nickRaw.copyInto(data,GEN1_PARTY_NICKS+count*11)
        val level=p.level.coerceIn(1,100);data[mon+33]=level.toByte();val stats=gen1CalculatedStats(meta,level,p.dvs,p.statExp);writeU16BE(data,mon+1,stats[0].coerceAtLeast(1));writeU16BE(data,mon+34,stats[0].coerceAtLeast(1));for(i in 1..4)writeU16BE(data,mon+34+i*2,stats[i]);updateGen1MainChecksum(data);return count
    }

    private fun loadGen1TransferMeta(chosen:List<Gen1Pokemon>,done:(Map<Int,Gen1SpeciesMeta>?)->Unit){
        val dexes=chosen.mapNotNull{gen1DexFromInternalIndex(it.species)}.distinct();if(dexes.size!=chosen.map{it.species}.distinct().size){done(null);return}
        val out=linkedMapOf<Int,Gen1SpeciesMeta>();fun next(i:Int){if(i>=dexes.size){done(out);return};val dex=dexes[i];loadGen1SpeciesMetaAsync(dex){m->if(m==null){done(null)}else{out[dex]=m;next(i+1)}}};next(0)
    }

    private fun sameGen1TransferRecord(a:Gen1Pokemon,b:Gen1Pokemon,partyStats:Boolean=false):Boolean=
        a.species==b.species&&a.tid==b.tid&&a.nickname==b.nickname&&a.otName==b.otName&&a.level==b.level&&a.experience==b.experience&&a.moves.contentEquals(b.moves)&&a.movePP.contentEquals(b.movePP)&&a.movePpUps.contentEquals(b.movePpUps)&&a.statExp.contentEquals(b.statExp)&&a.dvs.contentEquals(b.dvs)&&(!partyStats||(a.currentHp==b.currentHp&&a.maxHp==b.maxHp))

    private fun moveSelectedGen1BoxPokemonToParty(sourceBox:Int,chosen:List<Gen1Pokemon>){
        val file=importedFile?:return
        val selectionBase=runCatching{file.readBytes()}.getOrNull()?:return
        val selectionIntegrity=verifyGen1SaveIntegrity(selectionBase)
        if(!selectionIntegrity.first){showDialog("Move to Party",selectionIntegrity.second+" Nothing was changed.");return}
        val selected=chosen.sortedBy{it.slot}
        if(selected.isEmpty()){showDialog("Move to Party","Select one or more Pokémon first.");return}
        if(selected.map{it.slot}.distinct().size!=selected.size){showDialog("Move to Party","The selection contains a duplicate slot. Nothing was changed.");return}
        if(selectionBase.size!=SIZE_G1||!hasValidGen1MainChecksum(selectionBase)||!gen1BoxIsWritable(selectionBase,sourceBox)){showDialog("Move to Party","The source save or Box $sourceBox is not writable. Nothing was changed.");return}
        val selectionSource=parseGen1Box(selectionBase,sourceBox).pokemon.sortedBy{it.slot}
        if(selected.any{p->selectionSource.firstOrNull{it.slot==p.slot}?.let{sameGen1TransferRecord(it,p)}!=true}){showDialog("Move to Party","A selected Pokémon changed before the move started. Nothing was changed.");return}
        val selectionParty=parseGen1Party(selectionBase).sortedBy{it.slot}
        if(selectionParty.size+selected.size>6){showDialog("Move to Party","The party only has ${6-selectionParty.size} empty slot${if(6-selectionParty.size==1)"" else "s"}. Nothing was changed.");return}
        loadGen1TransferMeta(selected){metaMap->
            if(metaMap==null){showDialog("Move to Party","PSE could not load the Gen 1 base-stat data needed to rebuild party stats. Nothing was changed.");return@loadGen1TransferMeta}
            val fresh=runCatching{file.readBytes()}.getOrNull()?:return@loadGen1TransferMeta
            try{
                val freshIntegrity=verifyGen1SaveIntegrity(fresh);require(freshIntegrity.first){freshIntegrity.second}
                require(fresh.size==SIZE_G1&&hasValidGen1MainChecksum(fresh)){"The fresh Gen 1 save is invalid."}
                require(gen1BoxIsWritable(fresh,sourceBox)){"Box $sourceBox is no longer writable."}
                val sourceBefore=parseGen1Box(fresh,sourceBox).pokemon.sortedBy{it.slot}
                val partyBefore=parseGen1Party(fresh).sortedBy{it.slot}
                require(partyBefore.size+selected.size<=6){"The party no longer has room for the complete selection."}
                val ordered=selected.map{p->sourceBefore.firstOrNull{it.slot==p.slot}.also{require(it!=null&&sameGen1TransferRecord(it,p)){"A selected Pokémon changed while base-stat data was loading."}}!!}
                val selectedSlots=ordered.map{it.slot}.toSet()
                val expectedSource=sourceBefore.filterNot{it.slot in selectedSlots}
                val work=fresh.copyOf()
                val payloads=ordered.map{p->p to buildGen1BankPayload(fresh,p,"Box $sourceBox • Slot ${p.slot+1}",false)}
                for((p,raw) in payloads){val dex=requireNotNull(gen1DexFromInternalIndex(p.species));val meta=requireNotNull(metaMap[dex]);val o=GEN1_BANK_HEADER_SIZE;appendGen1PartyPokemon(work,raw.copyOfRange(o,o+GEN1_BOX_MON_SIZE),raw.copyOfRange(o+GEN1_BOX_MON_SIZE,o+GEN1_BOX_MON_SIZE+11),raw.copyOfRange(o+GEN1_BOX_MON_SIZE+11,GEN1_BANK_FILE_SIZE),meta,p)}
                for((p,_) in payloads.sortedByDescending{it.first.slot})removeGen1BoxSlot(work,sourceBox,p.slot)
                val pre=verifyGen1WriteState(work,sourceBox);require(pre.first){pre.second}
                val stagedSource=parseGen1Box(work,sourceBox).pokemon.sortedBy{it.slot};require(stagedSource.size==expectedSource.size&&stagedSource.zip(expectedSource).all{(a,b)->sameGen1TransferRecord(a,b)}){"A remaining boxed Pokémon changed during staging."}
                val stagedParty=parseGen1Party(work).sortedBy{it.slot};require(stagedParty.size==partyBefore.size+ordered.size){"Party count did not verify."};require(stagedParty.take(partyBefore.size).zip(partyBefore).all{(a,b)->sameGen1TransferRecord(a,b,true)}){"An existing party Pokémon changed during staging."}
                for((i,p) in ordered.withIndex()){val added=stagedParty[partyBefore.size+i];require(sameGen1TransferRecord(added,p)){"A staged party Pokémon did not preserve every stored field."};val dex=requireNotNull(gen1DexFromInternalIndex(added.species));val check=verifyGen1PartyDerivedStats(work,added.slot,requireNotNull(metaMap[dex]));require(check.first){check.second}}
                file.writeBytes(work)
                val written=file.readBytes();require(written.contentEquals(work)){"The save changed while the Gen 1 Box-to-Party transfer was being written."};val post=verifyGen1WriteState(written,sourceBox);require(post.first){post.second}
                val persistedSource=parseGen1Box(written,sourceBox).pokemon.sortedBy{it.slot};require(persistedSource.size==expectedSource.size&&persistedSource.zip(expectedSource).all{(a,b)->sameGen1TransferRecord(a,b)}){"A remaining boxed Pokémon changed after re-reading."}
                val persistedParty=parseGen1Party(written).sortedBy{it.slot};require(persistedParty.size==partyBefore.size+ordered.size){"Party count did not verify after re-reading."};require(persistedParty.take(partyBefore.size).zip(partyBefore).all{(a,b)->sameGen1TransferRecord(a,b,true)}){"An existing party Pokémon changed after re-reading."}
                for((i,p) in ordered.withIndex()){val added=persistedParty[partyBefore.size+i];require(sameGen1TransferRecord(added,p)){"A persisted party Pokémon did not preserve every stored field."};val dex=requireNotNull(gen1DexFromInternalIndex(added.species));val check=verifyGen1PartyDerivedStats(written,added.slot,requireNotNull(metaMap[dex]));require(check.first){check.second}}
                gen1BoxMultiSelectMode=false;selectedGen1BoxSlots.clear();Toast.makeText(this,"${ordered.size} Pokémon moved to Party and verified",Toast.LENGTH_LONG).show();showGen1PokemonViewer("party")
            }catch(e:Exception){runCatching{file.writeBytes(fresh)};showDialog("Move to Party failed",(e.message?:"Unknown error")+"\n\nThe fresh pre-transfer save was restored and no partial transfer was kept.")}
        }
    }

    private fun moveSelectedGen1PartyPokemonToBox(chosen:List<Gen1Pokemon>){
        val file=importedFile?:return
        val selectionBase=runCatching{file.readBytes()}.getOrNull()?:return
        val selectionIntegrity=verifyGen1SaveIntegrity(selectionBase)
        if(!selectionIntegrity.first){showDialog("Move to PC Box",selectionIntegrity.second+" Nothing was changed.");return}
        val selected=chosen.sortedBy{it.slot}
        if(selected.isEmpty()){showDialog("Move to PC Box","Select one or more party Pokémon first.");return}
        if(selected.map{it.slot}.distinct().size!=selected.size){showDialog("Move to PC Box","The selection contains a duplicate party slot. Nothing was changed.");return}
        if(selectionBase.size!=SIZE_G1||!hasValidGen1MainChecksum(selectionBase)){showDialog("Move to PC Box","The source save is invalid. Nothing was changed.");return}
        val selectionParty=parseGen1Party(selectionBase).sortedBy{it.slot}
        if(selected.any{p->selectionParty.firstOrNull{it.slot==p.slot}?.let{sameGen1TransferRecord(it,p,true)}!=true}){showDialog("Move to PC Box","A selected party Pokémon changed before the move started. Nothing was changed.");return}
        if(selectionParty.size-selected.size<1){showDialog("Move to PC Box","Generation 1 must keep at least one Pokémon in the party. Nothing was changed.");return}
        val targets=availableGen1BoxTargets(selectionBase).filter{it.second>=selected.size};if(targets.isEmpty()){showDialog("Move to PC Box","No initialized Gen 1 PC box has room for all ${selected.size} selected Pokémon. Nothing was changed.");return}
        AlertDialog.Builder(this).setTitle("Move ${selected.size} Pokémon to which PC Box?").setItems(targets.map{(b,e)->"Box $b • $e empty"}.toTypedArray()){_,which->
            val target=targets[which].first
            val fresh=runCatching{file.readBytes()}.getOrNull()?:return@setItems
            try{
                val freshIntegrity=verifyGen1SaveIntegrity(fresh);require(freshIntegrity.first){freshIntegrity.second}
                require(fresh.size==SIZE_G1&&hasValidGen1MainChecksum(fresh)){"The fresh Gen 1 save is invalid."}
                require(gen1BoxIsWritable(fresh,target)){"Box $target is no longer writable."}
                val partyBefore=parseGen1Party(fresh).sortedBy{it.slot}
                val targetBefore=parseGen1Box(fresh,target).pokemon.sortedBy{it.slot}
                require(partyBefore.size-selected.size>=1){"Generation 1 must keep at least one Pokémon in the party."}
                require(targetBefore.size+selected.size<=GEN1_BOX_CAPACITY){"Box $target no longer has room for the complete selection."}
                val ordered=selected.map{p->partyBefore.firstOrNull{it.slot==p.slot}.also{require(it!=null&&sameGen1TransferRecord(it,p,true)){"A selected party Pokémon changed before the destination was confirmed."}}!!}
                val selectedSlots=ordered.map{it.slot}.toSet()
                val expectedParty=partyBefore.filterNot{it.slot in selectedSlots}
                val expectedTarget=targetBefore+ordered
                val work=fresh.copyOf()
                val payloads=ordered.map{p->p to buildGen1BankPayload(fresh,p,"Party • Slot ${p.slot+1}",true)}
                for((_,raw) in payloads){val o=GEN1_BANK_HEADER_SIZE;appendGen1BoxPokemon(work,target,raw.copyOfRange(o,o+GEN1_BOX_MON_SIZE),raw.copyOfRange(o+GEN1_BOX_MON_SIZE,o+GEN1_BOX_MON_SIZE+11),raw.copyOfRange(o+GEN1_BOX_MON_SIZE+11,GEN1_BANK_FILE_SIZE))}
                for((p,_) in payloads.sortedByDescending{it.first.slot})removeGen1PartySlot(work,p.slot)
                val pre=verifyGen1WriteState(work,target);require(pre.first){pre.second}
                val stagedParty=parseGen1Party(work).sortedBy{it.slot};require(stagedParty.size==expectedParty.size&&stagedParty.zip(expectedParty).all{(a,b)->sameGen1TransferRecord(a,b,true)}){"A remaining party Pokémon changed during staging."}
                val stagedTarget=parseGen1Box(work,target).pokemon.sortedBy{it.slot};require(stagedTarget.size==expectedTarget.size&&stagedTarget.zip(expectedTarget).all{(a,b)->sameGen1TransferRecord(a,b)}){"Box $target did not contain the exact selected Pokémon in order."}
                file.writeBytes(work)
                val written=file.readBytes();require(written.contentEquals(work)){"The save changed while the Gen 1 Party-to-Box transfer was being written."};val post=verifyGen1WriteState(written,target);require(post.first){post.second}
                val persistedParty=parseGen1Party(written).sortedBy{it.slot};require(persistedParty.size==expectedParty.size&&persistedParty.zip(expectedParty).all{(a,b)->sameGen1TransferRecord(a,b,true)}){"A remaining party Pokémon changed after re-reading."}
                val persistedTarget=parseGen1Box(written,target).pokemon.sortedBy{it.slot};require(persistedTarget.size==expectedTarget.size&&persistedTarget.zip(expectedTarget).all{(a,b)->sameGen1TransferRecord(a,b)}){"Box $target did not preserve the exact selected Pokémon after re-reading."}
                gen1PartyMultiSelectMode=false;selectedGen1PartySlots.clear();Toast.makeText(this,"${ordered.size} Pokémon moved to Box $target and verified",Toast.LENGTH_LONG).show();showGen1PokemonViewer("boxes",target)
            }catch(e:Exception){runCatching{file.writeBytes(fresh)};showDialog("Move to PC Box failed",(e.message?:"Unknown error")+"\n\nThe fresh pre-transfer save was restored and no partial transfer was kept.")}
        }.setNegativeButton("Cancel",null).show()
    }

    private fun moveSelectedGen1BoxPokemonToBox(sourceBox:Int,chosen:List<Gen1Pokemon>){
        val file=importedFile?:return
        val selectionBase=runCatching{file.readBytes()}.getOrNull()?:return
        val selectionIntegrity=verifyGen1SaveIntegrity(selectionBase)
        if(!selectionIntegrity.first){showDialog("Move selected",selectionIntegrity.second+" Nothing was changed.");return}
        val selected=chosen.sortedBy{it.slot}
        if(selected.isEmpty()){showDialog("Move selected","Select one or more Pokémon first.");return}
        if(selected.map{it.slot}.distinct().size!=selected.size){showDialog("Move selected","The selection contains a duplicate slot. Nothing was changed.");return}
        if(!gen1BoxIsWritable(selectionBase,sourceBox)){showDialog("Move selected","Box $sourceBox is not writable.");return}
        val selectionSource=parseGen1Box(selectionBase,sourceBox).pokemon
        if(selected.any{p->selectionSource.firstOrNull{it.slot==p.slot}?.let{sameGen1ReorderRecord(it,p)}!=true}){showDialog("Move selected","A selected Pokémon changed before the move started. Nothing was changed.");return}
        val targets=(1..12).filter{it!=sourceBox}.mapNotNull{b->
            if(!gen1BoxIsWritable(selectionBase,b)) null else {
                val count=runCatching{parseGen1Box(selectionBase,b).pokemon.size}.getOrDefault(20)
                if(20-count>=selected.size) b to (20-count) else null
            }
        }
        if(targets.isEmpty()){showDialog("Move selected","No other initialised Gen 1 box has room for all ${selected.size} selected Pokémon. Nothing was changed.");return}
        AlertDialog.Builder(this).setTitle("Move to which PC Box?")
            .setItems(targets.map{(b,e)->"Box $b • $e empty"}.toTypedArray()){_,which->
                val target=targets[which].first
                val fresh=runCatching{file.readBytes()}.getOrNull()?:return@setItems
                try{
                    val freshIntegrity=verifyGen1SaveIntegrity(fresh);require(freshIntegrity.first){freshIntegrity.second}
                    require(fresh.size==SIZE_G1&&hasValidGen1MainChecksum(fresh)){"The fresh Gen 1 save is invalid."}
                    require(target!=sourceBox&&gen1BoxIsWritable(fresh,sourceBox)&&gen1BoxIsWritable(fresh,target)){"The source or destination box is no longer writable."}
                    val sourceBefore=parseGen1Box(fresh,sourceBox).pokemon.sortedBy{it.slot}
                    val targetBefore=parseGen1Box(fresh,target).pokemon.sortedBy{it.slot}
                    require(targetBefore.size+selected.size<=GEN1_BOX_CAPACITY){"The destination box no longer has room for the complete selection."}
                    require(selected.all{p->sourceBefore.firstOrNull{it.slot==p.slot}?.let{sameGen1ReorderRecord(it,p)}==true}){"A selected Pokémon changed before the destination was confirmed."}
                    val selectedSlots=selected.map{it.slot}.toSet()
                    val payloads=selected.map{p->p to buildGen1BankPayload(fresh,p,"Box $sourceBox • Slot ${p.slot+1}",false)}
                    val expectedSource=sourceBefore.filterNot{it.slot in selectedSlots}.map{buildGen1BankPayload(fresh,it,"Box $sourceBox • Slot ${it.slot+1}",false)}
                    val expectedTarget=targetBefore.map{buildGen1BankPayload(fresh,it,"Box $target • Slot ${it.slot+1}",false)}+payloads.map{it.second}
                    val work=fresh.copyOf()
                    for((_,raw) in payloads){
                        val o=GEN1_BANK_HEADER_SIZE
                        appendGen1BoxPokemon(work,target,raw.copyOfRange(o,o+GEN1_BOX_MON_SIZE),raw.copyOfRange(o+GEN1_BOX_MON_SIZE,o+GEN1_BOX_MON_SIZE+11),raw.copyOfRange(o+GEN1_BOX_MON_SIZE+11,GEN1_BANK_FILE_SIZE))
                    }
                    for((p,_) in payloads.sortedByDescending{it.first.slot}) removeGen1BoxSlot(work,sourceBox,p.slot)
                    val preSource=verifyGen1WriteState(work,sourceBox);require(preSource.first){preSource.second}
                    val preTarget=verifyGen1WriteState(work,target);require(preTarget.first){preTarget.second}
                    require(sameGen1Payloads(gen1BoxPayloadSnapshot(work,sourceBox),expectedSource)){"The staged source box did not preserve its remaining Pokémon exactly."}
                    require(sameGen1Payloads(gen1BoxPayloadSnapshot(work,target),expectedTarget)){"The staged destination box did not contain the exact selected Pokémon in order."}
                    file.writeBytes(work)
                    val written=file.readBytes()
                    require(written.contentEquals(work)){"The save changed while the Gen 1 cross-box transfer was being written."}
                    val postSource=verifyGen1WriteState(written,sourceBox);require(postSource.first){postSource.second}
                    val postTarget=verifyGen1WriteState(written,target);require(postTarget.first){postTarget.second}
                    require(sameGen1Payloads(gen1BoxPayloadSnapshot(written,sourceBox),expectedSource)){"The persisted source box did not preserve its remaining Pokémon exactly."}
                    require(sameGen1Payloads(gen1BoxPayloadSnapshot(written,target),expectedTarget)){"The persisted destination box did not contain the exact selected Pokémon in order."}
                    gen1BoxMultiSelectMode=false;selectedGen1BoxSlots.clear()
                    Toast.makeText(this,"${selected.size} Pokémon moved from Box $sourceBox to Box $target and verified",Toast.LENGTH_LONG).show()
                    showGen1PokemonViewer("boxes",target)
                }catch(e:Exception){
                    runCatching{file.writeBytes(fresh)}
                    showDialog("Move to PC Box failed",(e.message?:"Unknown error")+"\n\nThe original working save was restored and no partial transfer was kept.")
                }
            }.setNegativeButton("Cancel",null).show()
    }

    private fun writeSelectedGen1ToGen3PcAndDexFrozen(save:ByteArray,pc:ByteArray,dexes:List<Int>){
        // One active-section map for the entire batch. Updating PC section checksums can
        // otherwise change which redundant Gen 3 slot later helpers consider active.
        val map=gen3ActiveSectionMap(save)?:error("Could not map active Gen 3 save sections")
        require(pc.size==33744){"Unexpected Gen 3 PC storage size ${pc.size}"}
        var cursor=0
        for(id in 5..13){
            val base=map[id]
            if(base<0)error("Missing active Gen 3 PC section $id")
            val len=if(id==13)2000 else 3968
            require(cursor+len<=pc.size && base+len<=save.size){"Gen 3 PC section $id is out of range"}
            pc.copyInto(save,base,cursor,cursor+len)
            updateGen3SectionChecksumAt(save,base,id)
            cursor+=len
        }
        require(cursor==pc.size){"Gen 3 PC storage write was incomplete"}
        val s0=map[0];val s1=map[1]
        if(s0<0||s1<0)error("Missing active Gen 3 Pokédex sections")
        val game=detectGen3GameFromData(save,currentSourceLabel?:importedFile?.name.orEmpty())
        val frlg=game.contains("FireRed")||game.contains("LeafGreen")
        val emerald=game.contains("Emerald")
        val seen2Rel=when{frlg->0x5F8;emerald->0x988;else->0x938}
        val seen3LargeRel=when{frlg->0x3A18;emerald->0x3B24;else->0x3A8C}
        val seen3SectionId=1+(seen3LargeRel/3968)
        val seen3Rel=seen3LargeRel%3968
        val sSeen3=map[seen3SectionId]
        if(sSeen3<0)error("Missing active Gen 3 Pokédex mirror section $seen3SectionId")
        for(dex in dexes.distinct()){
            require(dex in 1..386){"Invalid Gen 3 Pokédex species $dex"}
            val bit=dex-1;val mask=1 shl(bit and 7);val byte=bit/8
            for(o in intArrayOf(s0+0x28+byte,s0+0x5C+byte,s1+seen2Rel+byte,sSeen3+seen3Rel+byte)){
                require(o in save.indices){"Gen 3 Pokédex offset is out of range"}
                save[o]=(save[o].toInt() or mask).toByte()
            }
        }
        updateGen3SectionChecksumAt(save,s0,0)
        updateGen3SectionChecksumAt(save,s1,1)
        if(sSeen3!=s1)updateGen3SectionChecksumAt(save,sSeen3,seen3SectionId)
    }

    private fun chooseSelectedGen1BankToGen3Destination(chosen:List<File>){
        val file=importedFile?:return
        val data=runCatching{file.readBytes()}.getOrNull()?:return
        if(generationForSize(data.size)!=3||!gen3SaveValid(data)){showDialog("Gen 1 → Gen 3","Load a valid Generation 3 save first.");return}
        val parsed=try{loadBoundGen1BankBatch(chosen,bankDir(),::parseGen1BankPayload).map{item->val pk3=buildGen1ToGen3Pk3(item.bankBytes,item.source,data);val q=parsePk3(pk3,0,false)?:error("A selected Gen 1 Pokémon could not be converted to PK3.");Gen1Gen3BatchPrepared(item.bankFile,item.bankBytes,item.source,item.sourceBinding,pk3,q)}}catch(e:Exception){showDialog("Gen 1 → Gen 3",e.message?:"Conversion failed");return}
        if(!parsed.all{it.sourceBinding.matches(it.bankBytes,true)}){showDialog("Gen 1 → Gen 3","A selected Bank identity changed during preparation. Nothing was changed.");return}
        val pc=gen3PcBuffer(data)?:run{showDialog("Gen 1 → Gen 3","Could not reconstruct Gen 3 PC storage.");return}
        val targets=(0 until 14).mapNotNull{b->val empty=(0 until 30).filter{slot->gen3DestinationSlotIsStructurallyEmpty(pc,b*30+slot)};if(empty.size>=chosen.size)b to empty.take(chosen.size) else null}
        if(targets.isEmpty()){showDialog("Gen 1 → Gen 3","No single Gen 3 PC box has ${chosen.size} structurally empty slots for the complete selection. Existing or nonzero slot data was left unchanged; free capacity, reduce the selection, or load another valid save.");return}
        val labels=targets.map{(box,slots)->"Box ${box+1} • Slots ${slots.joinToString(", "){(it+1).toString()}}"}.toTypedArray()
        val disclosure=gen1ToGen3BatchDisclosure(parsed.map{item->val p=item.source;Gen1ToGen3BatchDisclosure(p.nickname.ifBlank{gen1SpeciesNameFromInternal(p.species)},requireNotNull(gen1DexFromInternalIndex(p.species)),p.level,p.experience,p.moves,p.movePP,p.movePpUps,p.statExp,item.bankBytes[GEN1_BANK_HEADER_SIZE+7].toInt() and 0xFF,item.converted)})
        showGen1ToGen3BatchDisclosureAndDestination(this,chosen.size,disclosure,labels){selectedTarget->val(box,slots)=targets[selectedTarget];transferSelectedGen1BankToGen3(parsed,data,box,slots)}
    }

    private data class Gen1Gen3BatchPrepared(val bankFile:File,val bankBytes:ByteArray,val source:Gen1Pokemon,val sourceBinding:PseBankSourceBinding,val pk3:ByteArray,val converted:PokemonEntry)
    private fun gen3DestinationSlotIsStructurallyEmpty(pc:ByteArray,global:Int):Boolean{
        if(global !in 0 until 420)return false
        val off=4+global*80
        if(off+80>pc.size)return false
        val raw=pc.copyOfRange(off,off+80)
        return raw.all{it==0.toByte()}&&parsePk3(raw,0,false)==null
    }
    private fun transferSelectedGen1BankToGen3(chosen:List<Gen1Gen3BatchPrepared>,preparedDestination:ByteArray,box:Int,targetSlots:List<Int>){
        val file=importedFile?:return;val openedFile=file;val openedUri=currentSourceUri;val openedLabel=currentSourceLabel
        fun bound()=importedFile?.absolutePath==openedFile.absolutePath&&currentSourceUri==openedUri&&currentSourceLabel==openedLabel
        val destinationBinding=PseBankDestinationBinding.capture(file)
        if(destinationBinding==null||!destinationBinding.matches(preparedDestination,bound())){
            showDialog("Gen 1 → Gen 3","The destination identity changed after review. Reopen PSE Bank. Nothing was changed.");return
        }
        val fresh=runCatching{file.readBytes()}.getOrElse{showDialog("Gen 1 → Gen 3 multi-conversion failed","The fresh destination save could not be read. Nothing was changed.");return}
        val removed=ArrayList<Pair<File,ByteArray>>();val cleanupAttempted=HashSet<String>();var writeStarted=false;var safetyBackup:File?=null;var stagedDestination:ByteArray?=null;var bankRecovery:ExactBankRecoverySet?=null
        try{
            require(destinationBinding.matches(preparedDestination,bound())){"The Gen 3 destination identity changed. Nothing was changed."}
            require(chosen.isNotEmpty()){"No Gen 1 Bank entries were selected."}
            require(chosen.map{it.bankFile.absolutePath}.distinct().size==chosen.size){"The selected Gen 1 Bank entries contain a duplicate."}
            require(fresh.contentEquals(preparedDestination)){"The Gen 3 destination changed after the batch was prepared. Nothing was written."}
            require(generationForSize(fresh.size)==3&&gen3SaveValid(fresh)){"The loaded destination is no longer a valid Gen 3 save."}
            require(box in 0..13){"Invalid Gen 3 destination box selection."}
            require(targetSlots.size==chosen.size&&targetSlots.distinct().size==targetSlots.size&&targetSlots.all{it in 0 until 30}){"The reviewed Gen 3 destination slots are invalid."}
            val refreshed=chosen.map{item->
                val bankBytes=runCatching{item.bankFile.readBytes()}.getOrElse{error("A selected Gen 1 Bank record could not be read.")}
                val source=parseGen1BankPayload(bankBytes)?:error("A selected Gen 1 Bank record failed validation.")
                require(item.sourceBinding.matches(item.bankBytes,true)&&bankBytes.contentEquals(item.bankBytes)&&sameGen1TransferRecord(source,item.source)){"A selected Gen 1 Bank identity or record changed before conversion. Nothing was changed."}
                val freshPk3=buildGen1ToGen3Pk3(bankBytes,source,fresh);val freshConverted=parsePk3(freshPk3,0,false)?:error("A refreshed Gen 1 Pokémon could not be converted to PK3.")
                require(freshPk3.contentEquals(item.pk3)&&sameGen1ToGen3BatchPk3(freshConverted,item.converted)&&gen3MovePpUps(freshPk3).contentEquals(source.movePpUps)){"A prepared Gen 3 conversion or PP-Up value changed before confirmation. Nothing was changed."}
                Gen1Gen3BatchPrepared(item.bankFile,bankBytes,source,item.sourceBinding,freshPk3,freshConverted)
            }
            val save=fresh.copyOf();val pc=gen3PcBuffer(save)?:error("Could not reconstruct Gen 3 PC storage");val beforePc=pc.copyOf()
            require(targetSlots.all{slot->gen3DestinationSlotIsStructurallyEmpty(pc,box*30+slot)}){"A reviewed Gen 3 destination slot is no longer structurally empty. Existing slot data was not overwritten."}
            val expected=ArrayList<Triple<Int,ByteArray,PokemonEntry>>()
            for(i in refreshed.indices){
                val item=refreshed[i];val freshPk3=item.pk3;val freshConverted=item.converted;val target=box*30+targetSlots[i];val off=4+target*80
                require(gen3DestinationSlotIsStructurallyEmpty(pc,target)){"Box ${box+1} • Slot ${targetSlots[i]+1} is no longer structurally empty. Existing slot data was not overwritten."}
                freshPk3.copyInto(pc,off);val stagedBytes=pc.copyOfRange(off,off+80);val staged=parsePk3(stagedBytes,0,false)?:error("A converted PK3 could not be parsed after placement.")
                require(stagedBytes.contentEquals(freshPk3)&&sameGen1ToGen3BatchPk3(staged,freshConverted)&&gen3MovePpUps(stagedBytes).contentEquals(item.source.movePpUps)){"A staged PK3 did not preserve every supported converted field and PP-Up value."}
                expected.add(Triple(target,freshPk3,staged))
            }
            val changed=expected.flatMap{(target,bytes,_)->val off=4+target*80;(off until off+bytes.size).toList()}.toHashSet()
            require(pc.indices.all{it in changed||pc[it]==beforePc[it]}){"Unrelated Gen 3 PC data changed during batch staging."}
            fun dexesRegistered(data:ByteArray,dexes:List<Int>)=gen1ToGen3BatchDexesRegistered(data,dexes,gen3ActiveSectionMap(data),detectGen3GameFromData(data,currentSourceLabel?:importedFile?.name.orEmpty()))
            val dexes=expected.map{it.third.species}
            writeSelectedGen1ToGen3PcAndDexFrozen(save,pc,dexes)
            require(gen3SaveValid(save)){"Gen 3 checksum verification failed"}
            require(dexesRegistered(save,dexes)){"The staged Gen 3 Pokédex mirrors did not verify for the complete batch."}
            require(destinationBinding.matches(fresh,bound())&&refreshed.all{it.sourceBinding.matches(it.bankBytes,true)}){"The destination or a selected Bank identity changed before backup. Nothing was written."}
            val recovery=File.createTempFile(backupGameName(file)+"_PreGen1BatchToGen3_",".backup",backupsDir());safetyBackup=recovery
            recovery.writeBytes(fresh);require(recovery.readBytes().contentEquals(fresh)){"The exact Gen 3 batch recovery backup did not verify."};retainGen2ToGen3SafetyBackup(recovery,fresh)
            val sourceRecovery=createGen1ToGen3BatchRecovery(refreshed.map{it.bankBytes},bankDir());bankRecovery=sourceRecovery
            requireGen1ToGen3BatchRecoveryBoundary(destinationBinding.matches(fresh,bound())&&refreshed.all{it.sourceBinding.matches(it.bankBytes,true)},isBackupKept(recovery)&&recovery.readBytes().contentEquals(fresh),sourceRecovery,removed,0,0)
            stagedDestination=save.copyOf();writeStarted=true;file.writeBytes(save)
            val verify=file.readBytes();require(destinationBinding.matches(save,bound())&&verify.contentEquals(save)){"The Gen 3 destination changed while the multi-conversion was being written."};require(gen3SaveValid(verify)){"Written Gen 3 save failed checksum verification"}
            require(dexesRegistered(verify,dexes)){"The persisted Gen 3 Pokédex mirrors did not verify for the complete batch."}
            val vpc=gen3PcBuffer(verify)?:error("Could not re-read Gen 3 PC storage")
            for((target,bytes,staged) in expected){val off=4+target*80;val actual=vpc.copyOfRange(off,off+80);val q=parsePk3(actual,0,false);require(actual.contentEquals(bytes)&&q!=null&&sameGen1ToGen3BatchPk3(q,staged)&&gen3MovePpUps(actual).contentEquals(gen3MovePpUps(bytes))){"A persisted PK3 or PP-Up value did not preserve every supported converted field."}}
            require(vpc.indices.all{it in changed||vpc[it]==beforePc[it]}){"Unrelated Gen 3 PC data changed after batch persistence."}
            for(item in refreshed){
                requireGen1ToGen3BatchRecoveryBoundary(destinationBinding.matches(save,bound()),isBackupKept(recovery)&&recovery.readBytes().contentEquals(fresh),sourceRecovery,removed,removed.size,1)
                val current=runCatching{item.bankFile.readBytes()}.getOrElse{error("A selected Gen 1 Bank record could not be re-read before cleanup.")}
                require(item.sourceBinding.matches(item.bankBytes,true)&&current.contentEquals(item.bankBytes)&&parseGen1BankPayload(current)?.let{sameGen1TransferRecord(it,item.source)}==true){"A selected Gen 1 Bank identity or record changed before cleanup. The Gen 3 destination was restored."}
                cleanupAttempted.add(item.bankFile.absolutePath)
                require(item.bankFile.delete()&&!item.bankFile.exists()){"A Bank entry could not be removed after destination verification."}
                removed.add(item.bankFile to item.bankBytes);requireGen1ToGen3BatchRecoveryBoundary(destinationBinding.matches(save,bound()),isBackupKept(recovery)&&recovery.readBytes().contentEquals(fresh),sourceRecovery,removed,removed.size,1)
            }
            requireGen1ToGen3BatchRecoveryBoundary(destinationBinding.matches(save,bound()),isBackupKept(recovery)&&recovery.readBytes().contentEquals(fresh),sourceRecovery,removed,refreshed.size,2)
        }catch(e:Exception){
            val banksRestored=chosen.all{item->runCatching{restoreExactMissingStandardBankRecord(item.bankFile,item.bankBytes,cleanupAttempted.contains(item.bankFile.absolutePath),null);val bytes=item.bankFile.readBytes();item.sourceBinding.matches(item.bankBytes,true)&&bytes.contentEquals(item.bankBytes)&&parseGen1BankPayload(bytes)?.let{sameGen1TransferRecord(it,item.source)}==true}.getOrDefault(false)}
            val saveRestored=runCatching{
                val current=file.readBytes()
                require(destinationBinding.matches(current,bound())){"The Gen 3 destination identity changed during recovery; the current file was left untouched."}
                restoreExactStagedDestination(file,fresh,stagedDestination,writeStarted,"No Generation 3 destination write began; unknown current bytes were left untouched.","The Generation 3 destination no longer matches this batch transfer's staged bytes; the retained backup was left for recovery.")
                val restored=file.readBytes()
                require(destinationBinding.matches(fresh,bound())&&restored.contentEquals(fresh)&&generationForSize(restored.size)==3&&gen3SaveValid(restored)){"The restored Generation 3 destination did not verify."}
                true
            }.getOrDefault(false)
            val recovery=gen1ToGen3BatchRecoveryMessage(banksRestored,saveRestored)
            showDialog("Gen 1 → Gen 3 multi-conversion failed","${e.message?:"Unknown error"}\n\n$recovery"+(safetyBackup?.let{"\n\nRecovery backup: "+it.name}?:"")+gen1ToGen3BatchRecoveryFailureNotice(bankRecovery))
            return
        }
        val recoveryNotice=finishGen1ToGen3BatchRecovery(bankRecovery)
        selectedBankFiles.clear();bankMultiSelectMode=false
        Toast.makeText(this,"${chosen.size} Gen 1 Pokémon converted to Gen 3 Box ${box+1} and verified$recoveryNotice",Toast.LENGTH_LONG).show()
        showGen3PokemonViewer("boxes",box)
    }


    private fun chooseSelectedGen1BankToGen2Destination(chosen:List<File>){
        val openedSource=currentSourceUri;val openedLabel=currentSourceLabel
        val file=importedFile?:return
        val data=runCatching{file.readBytes()}.getOrNull()?:return
        val profile=detectGen2Profile(data)?:run{showDialog("Transfer to Gen 2","Load a valid Gold, Silver or Crystal save first.");return}
        val parsed=try {
            loadBoundGen1BankBatch(chosen,bankDir(),::parseGen1BankPayload).map{item->Gen1Gen2BatchPrepared(item.bankFile,item.bankBytes,item.source,item.sourceBinding)}
        } catch(e:Exception) {
            showDialog("Transfer to Gen 2",e.message?:"A selected Gen 1 Bank record could not be read. Nothing was changed.")
            return
        }
        if(!parsed.all{it.sourceBinding.matches(it.bankBytes,true)}){showDialog("Transfer to Gen 2","A selected Bank identity changed during preparation. Nothing was changed.");return}
        val targets=availableGen2BoxTargets(data).filter{it.second>=chosen.size}
        if(targets.isEmpty()){showDialog("Transfer to Gen 2","No single Gen 2 PC box has room for all ${chosen.size} selected Pokémon. Nothing was changed.");return}
        val labels=targets.map{gen2BoxDestinationLabel(it.first,it.second,file,profile,currentSourceUri,data)}.toTypedArray()
        val disclosure=gen1ToGen2BatchDisclosure(parsed.map{item->val p=item.source;Gen1ToGen2BatchDisclosure(p.nickname.ifBlank{gen1SpeciesNameFromInternal(p.species)},requireNotNull(gen1DexFromInternalIndex(p.species)),item.bankBytes[GEN1_BANK_HEADER_SIZE+7].toInt() and 0xFF,p.moves,p.movePP,p.movePpUps)})
        fun showDestinationPicker(){
            var selectedTarget = 0
            var consumed=false
            AlertDialog.Builder(this).setTitle("Choose ${profile.displayName} destination")
                .setSingleChoiceItems(labels,selectedTarget){_,which->selectedTarget=which}
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Convert"){_,_->
                    if(consumed||selectedTarget !in targets.indices){showDialog("Transfer to Gen 2","This batch destination choice was already handled or is invalid. Reopen the Bank selection. Nothing was changed.");return@setPositiveButton}
                    consumed=true
                    transferSelectedGen1BankToGen2(parsed,targets[selectedTarget].first,file,data,openedSource,openedLabel,profile.key)
                }
                .show()
        }
        AlertDialog.Builder(this).setTitle("PSE Convert ${chosen.size} Gen 1 Pokémon to ${profile.displayName}")
            .setMessage(disclosure)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Choose destination"){_,_->showDestinationPicker()}
            .show()
    }

    private data class Gen1Gen2BatchPrepared(val bankFile:File,val bankBytes:ByteArray,val source:Gen1Pokemon,val sourceBinding:PseBankSourceBinding)
    private fun transferSelectedGen1BankToGen2(chosen:List<Gen1Gen2BatchPrepared>,targetBox:Int,openedFile:File,openedDestination:ByteArray,openedSource:Uri?,openedLabel:String?,profileKey:String){
        val file=openedFile
        fun bound()=importedFile?.absolutePath==file.absolutePath&&currentSourceUri==openedSource&&currentSourceLabel==openedLabel
        val destinationBinding=PseBankDestinationBinding.capture(file)
        if(destinationBinding==null||!destinationBinding.matches(openedDestination,bound())){
            showDialog("Transfer to Gen 2","The destination identity changed after review. Reopen PSE Bank. Nothing was changed.");return
        }
        val fresh=runCatching{file.readBytes()}.getOrElse{showDialog("Gen 1 → Gen 2 multi-transfer failed","The fresh destination save could not be read. Nothing was changed.");return}
        val removed=ArrayList<Pair<File,ByteArray>>()
        val cleanupAttempted=HashSet<String>()
        var writeStarted=false
        var safetyBackup:File?=null
        var stagedDestination:ByteArray?=null
        var bankRecovery:ExactBankRecoverySet?=null
        try{
            require(destinationBinding.matches(openedDestination,bound())&&fresh.contentEquals(openedDestination)&&verifyGen2SaveIntegrity(fresh).first){"The selected batch destination identity or data changed. Reopen the Bank selection."}
            require(chosen.isNotEmpty()){"No Gen 1 Bank entries were selected."}
            require(chosen.map{it.bankFile.absolutePath}.distinct().size==chosen.size){"The selected Gen 1 Bank entries contain a duplicate."}
            val profile=detectGen2Profile(fresh)?:error("The loaded destination is no longer a valid Gen 2 save.")
            require(profile.key==profileKey){"The selected Gen 2 batch destination profile changed."}
            require(targetBox in 1..14){"Invalid Gen 2 destination box."}
            val base=gen2BoxOffset(targetBox);val beforeCount=fresh[base].toInt() and 0xFF
            require(beforeCount in 0..20){"Box $targetBox is structurally invalid."}
            require(beforeCount+chosen.size<=20){"Box $targetBox no longer has room for all selected Pokémon."}
            val before=parseGen2Box(fresh,targetBox).sortedBy{it.slot}
            require(before.size==beforeCount){"Box $targetBox contains an invalid Pokémon record."}
            val refreshed=chosen.map{item->
                val bankBytes=runCatching{item.bankFile.readBytes()}.getOrElse{error("A selected Gen 1 Bank record could not be read.")}
                val source=parseGen1BankPayload(bankBytes)?:error("A selected Gen 1 Bank record failed validation.")
                require(item.sourceBinding.matches(item.bankBytes,true)&&bankBytes.contentEquals(item.bankBytes)&&sameGen1TransferRecord(source,item.source)){"A selected Gen 1 Bank identity or record changed before transfer. Nothing was changed."}
                Gen1Gen2BatchPrepared(item.bankFile,bankBytes,source,item.sourceBinding)
            }
            val ownedBase=if(profile.key=="C")0x2A27 else 0x2A4C
            val seenBase=if(profile.key=="C")0x2A47 else 0x2A6C
            val work=fresh.copyOf();val expected=ArrayList<Triple<Int,Gen1Pokemon,ByteArray>>()
            for(item in refreshed){
                val bankBytes=item.bankBytes;val source=item.source
                val (mon,ot,nick)=buildGen1BankToGen2Record(bankBytes,source)
                val slot=appendGen2BoxPokemon(work,targetBox,mon,ot,nick);expected.add(Triple(slot,source,bankBytes))
                val dex=gen1DexFromInternalIndex(source.species)?:error("Species mapping failed during Pokédex registration.")
                val bit=dex-1;val mask=1 shl(bit and 7);val byte=bit/8
                work[ownedBase+byte]=(work[ownedBase+byte].toInt() or mask).toByte();work[seenBase+byte]=(work[seenBase+byte].toInt() or mask).toByte()
            }
            updateGen2PrimaryChecksum(work,profile)
            require(gen2ChecksumValid(work,profile)){"The Gen 2 checksum could not be rebuilt after multi-transfer."}
            fun sameGen2(a:Gen2Pokemon,b:Gen2Pokemon)=a.species==b.species&&a.tid==b.tid&&a.nickname==b.nickname&&a.otName==b.otName&&a.level==b.level&&a.heldItem==b.heldItem&&a.experience==b.experience&&a.moves.contentEquals(b.moves)&&a.movePP.contentEquals(b.movePP)&&a.statExp.contentEquals(b.statExp)&&a.dvs.contentEquals(b.dvs)&&a.happiness==b.happiness&&a.pokerus==b.pokerus&&a.caughtData==b.caughtData
            fun convertedMatches(q:Gen2Pokemon,source:Gen1Pokemon,bankBytes:ByteArray):Boolean{
                val dex=gen1DexFromInternalIndex(source.species)?:return false
                return q.species==dex&&q.tid==source.tid&&q.nickname==source.nickname&&q.otName==source.otName&&q.level==source.level&&q.heldItem==(bankBytes[GEN1_BANK_HEADER_SIZE+7].toInt() and 0xFF)&&q.experience==source.experience&&q.moves.contentEquals(source.moves)&&q.movePP.contentEquals(source.movePP)&&q.statExp.contentEquals(source.statExp)&&q.dvs.contentEquals(source.dvs)&&q.happiness==70&&q.pokerus==0&&q.caughtData==0
            }
            fun dexRegistered(data:ByteArray,source:Gen1Pokemon,owned:Int,seen:Int):Boolean{
                val dex=gen1DexFromInternalIndex(source.species)?:return false;val bit=dex-1;val mask=1 shl(bit and 7);val byte=bit/8
                return (data[owned+byte].toInt() and mask)!=0&&(data[seen+byte].toInt() and mask)!=0
            }
            val staged=parseGen2Box(work,targetBox).sortedBy{it.slot}
            require(staged.size==before.size+refreshed.size){"The staged Gen 2 box count did not increase by the complete batch."}
            require(staged.take(before.size).zip(before).all{(a,b)->sameGen2(a,b)}){"An existing Gen 2 box record changed during staging."}
            for((slot,source,bankBytes) in expected){
                val q=staged.firstOrNull{it.slot==slot};require(q!=null&&convertedMatches(q,source,bankBytes)){"A staged Gen 2 Pokémon did not preserve every supported field."}
                val expectedMon=buildGen1BankToGen2Record(bankBytes,source).first
                require(gen2BoxRawCore(work,targetBox,slot).contentEquals(expectedMon)){"A staged Gen 2 record did not preserve its exact converted bytes, including PP Ups."}
                require(dexRegistered(work,source,ownedBase,seenBase)){"A staged Gen 2 Pokédex registration did not verify."}
            }
            require(verifyGen2SaveIntegrity(work).first){"The complete staged Gen 2 batch destination failed integrity verification."}
            require(destinationBinding.matches(fresh,bound())&&refreshed.all{it.sourceBinding.matches(it.bankBytes,true)}){"The destination or a selected Bank identity changed before backup. Nothing was written."}
            val backup=File.createTempFile(backupGameName(file)+"_PreGen1BankBatch_",".backup",backupsDir());safetyBackup=backup
            backup.writeBytes(fresh);require(backup.readBytes().contentEquals(fresh)){"The batch recovery backup did not persist exactly."}
            retainGen2SafetyBackup(backup,fresh)
            val sourceRecovery=createGen1ToGen2BatchRecovery(refreshed.map{it.bankBytes},bankDir());bankRecovery=sourceRecovery
            requireGen1ToGen2BatchRecoveryBoundary(destinationBinding.matches(fresh,bound())&&refreshed.all{it.sourceBinding.matches(it.bankBytes,true)},backup.readBytes().contentEquals(fresh)&&isBackupKept(backup),sourceRecovery,removed,0,0)
            stagedDestination=work.copyOf()
            writeStarted=true
            file.writeBytes(work)
            val written=file.readBytes();require(destinationBinding.matches(work,bound())&&written.contentEquals(work)){"The Gen 2 destination changed while the multi-transfer was being written."};val writtenProfile=detectGen2Profile(written)?:error("The written Gen 2 save failed checksum verification.")
            require(destinationBinding.matches(work,bound())&&writtenProfile.key==profileKey&&verifyGen2SaveIntegrity(written).first){"The complete persisted batch destination identity or data did not verify."}
            val after=parseGen2Box(written,targetBox).sortedBy{it.slot}
            require(after.size==before.size+refreshed.size){"The persisted Gen 2 box count did not increase by the complete batch."}
            require(after.take(before.size).zip(before).all{(a,b)->sameGen2(a,b)}){"An existing Gen 2 box record changed after persistence."}
            val verifyOwned=if(writtenProfile.key=="C")0x2A27 else 0x2A4C;val verifySeen=if(writtenProfile.key=="C")0x2A47 else 0x2A6C
            for((slot,source,bankBytes) in expected){
                val q=after.firstOrNull{it.slot==slot};require(q!=null&&convertedMatches(q,source,bankBytes)){"A converted Gen 2 Pokémon did not preserve every supported field after re-reading."}
                val expectedMon=buildGen1BankToGen2Record(bankBytes,source).first
                require(gen2BoxRawCore(written,targetBox,slot).contentEquals(expectedMon)){"A persisted Gen 2 record changed from its exact staged bytes, including PP Ups."}
                require(dexRegistered(written,source,verifyOwned,verifySeen)){"A transferred Pokémon did not register as Seen and Owned in the Gen 2 Pokédex."}
            }
            require(gen2ChecksumValid(written,writtenProfile)){"The Gen 2 save checksum did not remain valid."}
            for(item in refreshed){
                val bankFile=item.bankFile;val bankBytes=item.bankBytes;val source=item.source
                val current=runCatching{bankFile.readBytes()}.getOrElse{error("A selected Gen 1 Bank record could not be re-read before cleanup.")}
                require(item.sourceBinding.matches(bankBytes,true)&&current.contentEquals(bankBytes) && parseGen1BankPayload(current)?.let{sameGen1TransferRecord(it,source)}==true){"A selected Gen 1 Bank identity or record changed before cleanup; further Bank removal was stopped."}
                requireGen1ToGen2BatchRecoveryBoundary(destinationBinding.matches(work,bound()),isBackupKept(backup)&&backup.readBytes().contentEquals(fresh),sourceRecovery,removed,removed.size,1)
                cleanupAttempted.add(bankFile.absolutePath)
                require(bankFile.delete()){"A Bank entry could not be removed after destination verification."}
                require(!bankFile.exists()){"A batch Bank deletion could not be verified."}
                removed.add(bankFile to bankBytes)
                requireGen1ToGen2BatchRecoveryBoundary(destinationBinding.matches(work,bound()),isBackupKept(backup)&&backup.readBytes().contentEquals(fresh),sourceRecovery,removed,removed.size,1)
            }
            requireGen1ToGen2BatchRecoveryBoundary(destinationBinding.matches(work,bound()),isBackupKept(backup)&&backup.readBytes().contentEquals(fresh),sourceRecovery,removed,refreshed.size,2)
        }catch(e:Exception){
            for(item in chosen)runCatching{restoreExactMissingStandardBankRecord(item.bankFile,item.bankBytes,cleanupAttempted.contains(item.bankFile.absolutePath),null)}
            val banksRestored=chosen.all{item->runCatching{val bytes=item.bankFile.readBytes();item.sourceBinding.matches(item.bankBytes,true)&&bytes.contentEquals(item.bankBytes)&&parseGen1BankPayload(bytes)?.let{sameGen1TransferRecord(it,item.source)}==true}.getOrDefault(false)}
            val saveRestored=runCatching{
                val current=file.readBytes()
                require(destinationBinding.matches(current,bound())){"The Gen 2 destination identity changed during recovery; the current file was left untouched."}
                restoreExactStagedDestination(file,fresh,stagedDestination,writeStarted,"No Generation 2 batch destination write began; unknown current bytes were left untouched.","The Generation 2 batch destination no longer matches this transfer's staged bytes; the retained backup was left for recovery.")
                val restored=file.readBytes()
                require(destinationBinding.matches(fresh,bound())&&restored.contentEquals(fresh)&&verifyGen2SaveIntegrity(restored).first&&
                    detectGen2Profile(restored)?.key==profileKey){"The restored Generation 2 batch destination did not verify."}
                true
            }.getOrDefault(false)
            val recovery=gen1ToGen2BatchRecoveryMessage(banksRestored,saveRestored)
            showDialog("Gen 1 → Gen 2 multi-transfer failed",(e.message?:"Unknown error")+"\n\n"+recovery+(safetyBackup?.let{"\n\nRecovery backup: "+it.name}?:"")+gen1ToGen2BatchRecoveryFailureNotice(bankRecovery))
            return
        }
        selectedBankFiles.clear();bankMultiSelectMode=false
        val recoveryNotice=finishGen1ToGen2BatchRecovery(bankRecovery)
        Toast.makeText(this,"${chosen.size} Gen 1 Pokémon transferred to Gen 2 Box $targetBox and verified$recoveryNotice",Toast.LENGTH_LONG).show()
        showGen2PokemonViewer("boxes",targetBox)
    }

    private fun moveSelectedGen1BoxPokemonToBank(boxNumber:Int,chosen:List<Gen1Pokemon>){
        val file=importedFile?:return
        val openedUri=currentSourceUri
        val openedLabel=currentSourceLabel
        fun bound()=importedFile?.absolutePath==file.absolutePath&&currentSourceUri==openedUri&&currentSourceLabel==openedLabel
        val fresh=runCatching{file.readBytes()}.getOrNull()?:return
        val selectionIntegrity=verifyGen1SaveIntegrity(fresh)
        if(!selectionIntegrity.first){showDialog("Move selected",selectionIntegrity.second+" Nothing was changed.");return}
        val selected=chosen.sortedBy{it.slot}
        if(selected.isEmpty()){showDialog("Move selected","Select one or more Pokémon in this box first.");return}
        if(selected.map{it.slot}.distinct().size!=selected.size){showDialog("Move selected","The selection contains a duplicate slot. Nothing was changed.");return}
        val created=ArrayList<Pair<File,ByteArray>>()
        var sourceWriteStarted=false
        var safetyBackup:File?=null
        var stagedSource:ByteArray?=null
        try{
            require(bound()){"The loaded Gen 1 source identity changed."}
            require(fresh.size==SIZE_G1&&hasValidGen1MainChecksum(fresh)&&verifyGen1SaveIntegrity(fresh).first){"The fresh Gen 1 save is invalid."}
            require(gen1BoxIsWritable(fresh,boxNumber)){"Box $boxNumber is not writable."}
            val sourceBefore=parseGen1Box(fresh,boxNumber).pokemon.sortedBy{it.slot}
            val ordered=selected.map{p->sourceBefore.firstOrNull{it.slot==p.slot}.also{require(it!=null&&sameGen1TransferRecord(it,p)){"A selected Pokémon changed before the Bank deposit."}}!!}
            val selectedSlots=ordered.map{it.slot}.toSet()
            val expectedRemaining=sourceBefore.filterNot{it.slot in selectedSlots}
            val bankCountBefore=gen1BankEntryCountForDeposit()
            requireGen1BankCapacity(bankCountBefore,ordered.size)
            val payloads=ordered.map{p->p to buildGen1BankPayload(fresh,p,"Box $boxNumber • Slot ${p.slot+1}",false)}

            val backup=File.createTempFile(backupGameName(file)+"_PreGen1BankBoxBatch_",".backup",backupsDir())
            safetyBackup=backup
            backup.writeBytes(fresh)
            require(backup.readBytes().contentEquals(fresh)&&verifyGen1SaveIntegrity(backup.readBytes()).first){"The exact Gen 1 box batch recovery backup did not verify."}
            val kept=keptBackupNames().apply{add(backup.name)}
            val prefs=getSharedPreferences("pse_backup_state",MODE_PRIVATE)
            require(prefs.edit().putStringSet("kept",kept).commit()){"The Gen 1 box batch recovery backup Keep pin could not be saved. Nothing was written."}
            require(isBackupKept(backup)&&backup.readBytes().contentEquals(fresh)){"The retained Gen 1 box batch backup could not be rechecked."}

            for((p,payload) in payloads){
                require(bound()&&file.readBytes().contentEquals(fresh)){"The loaded Gen 1 source changed while the Bank batch was staged."}
                require(gen1BankEntryCountForDeposit()==bankCountBefore+created.size){"PSE Bank changed while the Gen 1 deposit was being staged. Nothing was changed."}
                val out=File.createTempFile("pse1_${gen1DexFromInternalIndex(p.species)?:p.species}_",".pse1",bankDir())
                created.add(out to payload.copyOf())
                out.writeBytes(payload)
                val stored=out.readBytes()
                require(stored.contentEquals(payload)){"A new Bank record changed while it was being written."}
                val parsed=parseGen1BankPayload(stored)?:error("A new Bank record did not verify.")
                require(sameGen1TransferRecord(parsed,p)){"A new Bank record did not preserve every supported Pokémon field."}
            }
            require(gen1BankEntryCountForDeposit()==bankCountBefore+created.size){"PSE Bank changed before the Gen 1 deposit could commit. Nothing was changed."}
            val work=fresh.copyOf()
            for((p,_) in payloads.sortedByDescending{it.first.slot})removeGen1BoxSlot(work,boxNumber,p.slot)
            val pre=verifyGen1WriteState(work,boxNumber)
            require(pre.first){pre.second}
            val staged=parseGen1Box(work,boxNumber).pokemon.sortedBy{it.slot}
            require(staged.size==expectedRemaining.size&&staged.zip(expectedRemaining).all{(a,b)->sameGen1TransferRecord(a,b)}){"A remaining boxed Pokémon changed during Bank deposit staging."}
            require(bound()&&file.readBytes().contentEquals(fresh)&&isBackupKept(backup)&&backup.readBytes().contentEquals(fresh)&&
                created.size==payloads.size&&created.indices.all{index->created[index].second.contentEquals(payloads[index].second)&&created[index].first.readBytes().contentEquals(payloads[index].second)}){
                "The Gen 1 source, staged Bank records or retained recovery backup changed before writing."
            }

            stagedSource=work.copyOf()
            sourceWriteStarted=true
            file.writeBytes(work)
            val written=file.readBytes()
            require(bound()&&written.contentEquals(work)){"The loaded source identity or Gen 1 save bytes changed while the Bank deposit was being written."}
            val post=verifyGen1WriteState(written,boxNumber)
            require(post.first){post.second}
            val remaining=parseGen1Box(written,boxNumber).pokemon.sortedBy{it.slot}
            require(remaining.size==expectedRemaining.size&&remaining.zip(expectedRemaining).all{(a,b)->sameGen1TransferRecord(a,b)}){"A remaining boxed Pokémon changed after Bank deposit persistence."}
            for((index,item) in created.withIndex()){
                val(out,payload)=item
                val stored=out.readBytes()
                val parsed=parseGen1BankPayload(stored)
                require(stored.contentEquals(payload)&&payload.contentEquals(payloads[index].second)&&parsed!=null&&sameGen1TransferRecord(parsed,ordered[index])){"A staged Bank record changed before commit completion."}
            }
            require(isBackupKept(backup)&&backup.readBytes().contentEquals(fresh)){"The retained Gen 1 box batch backup changed after persistence."}

            gen1BoxMultiSelectMode=false
            selectedGen1BoxSlots.clear()
            Toast.makeText(this,"${ordered.size} Gen 1 Pokémon moved to PSE Bank and verified",Toast.LENGTH_LONG).show()
            showGen1PokemonViewer("boxes",boxNumber)
        }catch(error:Exception){
            val sourceRestored=runCatching{
                restoreExactStagedDestination(file,fresh,stagedSource,sourceWriteStarted,"No Generation 1 source write began; unknown current bytes were left untouched.","The Generation 1 source no longer matches this box batch deposit's staged bytes; the retained backup was left for recovery.")
                val restored=file.readBytes()
                require(restored.contentEquals(fresh)&&restored.size==SIZE_G1&&hasValidGen1MainChecksum(restored)&&verifyGen1SaveIntegrity(restored).first){"The restored Generation 1 source did not verify."}
                true
            }.getOrDefault(false)
            var bankClean=true
            for((out,payload) in created){
                val removed=runCatching{
                    if(out.exists()){
                        require(out.readBytes().contentEquals(payload)){"A staged Gen 1 Bank record changed; it was retained for review."}
                        require(out.delete()){"An exact staged Gen 1 Bank record could not be removed."}
                    }
                    require(!out.exists()){"An exact staged Gen 1 Bank record still exists."}
                }.isSuccess
                if(!removed)bankClean=false
            }
            val recovery=when{
                sourceRestored&&bankClean->"The exact fresh Gen 1 source was restored or kept, and every exact staged Bank file was removed."
                sourceRestored->"The exact fresh Gen 1 source was restored or kept, but one or more staged Bank files remain for review."
                bankClean->"Every exact staged Bank file was removed, but source recovery was not verified. Use the retained Gen 1 box batch backup before editing again."
                else->"Automatic recovery could not be verified. Keep the retained Gen 1 box batch backup and any remaining Bank files for review."
            }
            showDialog("Move selected failed",(error.message?:"Unknown error")+"\n\n"+recovery+(safetyBackup?.let{"\n\nRecovery backup: "+it.name}?:""))
        }
    }
    private fun parseGen1ItemList(data:ByteArray,base:Int,capacity:Int,allowUninitialised:Boolean=false):List<Pair<Int,Int>>?{
        if(data.size!=SIZE_G1||base<0||base+2+capacity*2>data.size)return null
        val count=data[base].toInt()and 255
        if(allowUninitialised&&count==0xFF)return emptyList()
        if(count !in 0..capacity)return null
        if((data[base+1+count*2].toInt()and 255)!=0xFF)return null
        val out=ArrayList<Pair<Int,Int>>()
        for(i in 0 until count){val id=data[base+1+i*2].toInt()and 255;val qty=data[base+2+i*2].toInt()and 255;if(id !in 1..254||qty !in 1..99)return null;out.add(id to qty)}
        return out
    }

    private fun writeGen1ItemList(data:ByteArray,base:Int,capacity:Int,items:List<Pair<Int,Int>>){
        require(items.size<=capacity){"The Gen 1 item list exceeds its capacity."}
        require(items.all{it.first in 1..254&&it.second in 1..99}){"A Gen 1 item ID or quantity is invalid."}
        data[base]=items.size.toByte();java.util.Arrays.fill(data,base+1,base+2+capacity*2,0.toByte())
        for((i,item) in items.withIndex()){data[base+1+i*2]=item.first.toByte();data[base+2+i*2]=item.second.toByte()}
        data[base+1+items.size*2]=0xFF.toByte()
    }

    private val gen2BallItemIds=setOf(1,2,4,5,157,159,160,161,164,165,166)

    private val gen2OrdinaryItemIds=setOf(
        3,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,46,47,48,49,51,52,53,57,60,62,63,64,65,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89,91,92,93,94,95,96,97,98,99,101,102,103,104,105,106,107,108,109,110,111,112,113,114,117,118,119,121,122,123,124,125,126,131,132,138,139,140,143,144,146,150,151,152,156,158,163,167,168,169,170,172,173,174,180,181,182,183,184,185,186,187,188,189
    )

    private val gen2PCKeyItemIds=setOf(7,54,55,58,59,61,66,67,68,69,71,127,128,130,133,134,175,178)

    private val gen2PCMachineItemIds=setOf(
        191,192,193,194,196,197,198,199,200,201,202,203,204,205,206,207,208,209,210,211,212,213,214,215,216,217,218,219,221,222,223,224,225,226,227,228,229,230,231,232,233,234,235,236,237,238,239,240,241,242,243,244,245,246,247,248,249
    )

    private val gen2TMItemIds=gen2PCMachineItemIds.filter{it<243}.toSet()
    private val gen2HeldItemIds=gen2OrdinaryItemIds+gen2BallItemIds+gen2TMItemIds
    private val gen2ItemNames="NONE|MASTER BALL|ULTRA BALL|BRIGHTPOWDER|GREAT BALL|# BALL|TERU-SAMA|BICYCLE|MOON STONE|ANTIDOTE|BURN HEAL|ICE HEAL|AWAKENING|PARLYZ HEAL|FULL RESTORE|MAX POTION|HYPER POTION|SUPER POTION|POTION|ESCAPE ROPE|REPEL|MAX ELIXER|FIRE STONE|THUNDERSTONE|WATER STONE|TERU-SAMA|HP UP|PROTEIN|IRON|CARBOS|LUCKY PUNCH|CALCIUM|RARE CANDY|X ACCURACY|LEAF STONE|METAL POWDER|NUGGET|# DOLL|FULL HEAL|REVIVE|MAX REVIVE|GUARD SPEC.|SUPER REPEL|MAX REPEL|DIRE HIT|TERU-SAMA|FRESH WATER|SODA POP|LEMONADE|X ATTACK|TERU-SAMA|X DEFEND|X SPEED|X SPECIAL|COIN CASE|ITEMFINDER|TERU-SAMA|EXP.SHARE|OLD ROD|GOOD ROD|SILVER LEAF|SUPER ROD|PP UP|ETHER|MAX ETHER|ELIXER|RED SCALE|SECRETPOTION|S.S.TICKET|MYSTERY EGG|CLEAR BELL|SILVER WING|MOOMOO MILK|QUICK CLAW|PSNCUREBERRY|GOLD LEAF|SOFT SAND|SHARP BEAK|PRZCUREBERRY|BURNT BERRY|ICE BERRY|POISON BARB|KING'S ROCK|BITTER BERRY|MINT BERRY|RED APRICORN|TINYMUSHROOM|BIG MUSHROOM|SILVERPOWDER|BLU APRICORN|TERU-SAMA|AMULET COIN|YLW APRICORN|GRN APRICORN|CLEANSE TAG|MYSTIC WATER|TWISTEDSPOON|WHT APRICORN|BLACKBELT|BLK APRICORN|TERU-SAMA|PNK APRICORN|BLACKGLASSES|SLOWPOKETAIL|PINK BOW|STICK|SMOKE BALL|NEVERMELTICE|MAGNET|MIRACLEBERRY|PEARL|BIG PEARL|EVERSTONE|SPELL TAG|RAGECANDYBAR|GS BALL|BLUE CARD|MIRACLE SEED|THICK CLUB|FOCUS BAND|TERU-SAMA|ENERGYPOWDER|ENERGY ROOT|HEAL POWDER|REVIVAL HERB|HARD STONE|LUCKY EGG|CARD KEY|MACHINE PART|EGG TICKET|LOST ITEM|STARDUST|STAR PIECE|BASEMENT KEY|PASS|TERU-SAMA|TERU-SAMA|TERU-SAMA|CHARCOAL|BERRY JUICE|SCOPE LENS|TERU-SAMA|TERU-SAMA|METAL COAT|DRAGON FANG|TERU-SAMA|LEFTOVERS|TERU-SAMA|TERU-SAMA|TERU-SAMA|MYSTERYBERRY|DRAGON SCALE|BERSERK GENE|TERU-SAMA|TERU-SAMA|TERU-SAMA|SACRED ASH|HEAVY BALL|FLOWER MAIL|LEVEL BALL|LURE BALL|FAST BALL|TERU-SAMA|LIGHT BALL|FRIEND BALL|MOON BALL|LOVE BALL|NORMAL BOX|GORGEOUS BOX|SUN STONE|POLKADOT BOW|TERU-SAMA|UP-GRADE|BERRY|GOLD BERRY|SQUIRTBOTTLE|TERU-SAMA|PARK BALL|RAINBOW WING|TERU-SAMA|BRICK PIECE|SURF MAIL|LITEBLUEMAIL|PORTRAITMAIL|LOVELY MAIL|EON MAIL|MORPH MAIL|BLUESKY MAIL|MUSIC MAIL|MIRAGE MAIL|TERU-SAMA|TM01|TM02|TM03|TM04|TERU-SAMA|TM05|TM06|TM07|TM08|TM09|TM10|TM11|TM12|TM13|TM14|TM15|TM16|TM17|TM18|TM19|TM20|TM21|TM22|TM23|TM24|TM25|TM26|TM27|TM28|TERU-SAMA|TM29|TM30|TM31|TM32|TM33|TM34|TM35|TM36|TM37|TM38|TM39|TM40|TM41|TM42|TM43|TM44|TM45|TM46|TM47|TM48|TM49|TM50|HM01|HM02|HM03|HM04|HM05|HM06|HM07|TERU-SAMA|TERU-SAMA|TERU-SAMA|TERU-SAMA|TERU-SAMA|TERU-SAMA|?".split("|")
    private fun gen2ItemName(itemId:Int):String? = gen2ItemNames.getOrNull(itemId)?.takeIf{itemId in 1..255&&it!="TERU-SAMA"&&it!="?"}
    private fun gen2HeldItemName(itemId:Int):String? = if(itemId==0)"None" else gen2ItemName(itemId)?.takeIf{itemId in gen2HeldItemIds}
    private fun canonicalGen2HeldItemChoices():List<NamedChoice> = mutableListOf(NamedChoice(0,"None")).apply {
        gen2HeldItemIds.sorted().forEach { itemId -> gen2HeldItemName(itemId)?.let { name -> add(NamedChoice(itemId,name)) } }
    }
    private fun canonicalGen2ItemChoices(itemIds:Set<Int>):List<NamedChoice> = itemIds.sorted().mapNotNull { itemId ->
        gen2ItemName(itemId)?.let { name -> NamedChoice(itemId,name) }
    }

    private val gen2CrystalOnlyPCItemIds=setOf(70,115,116,129)
    private val gen2CommonPCItemIds=gen2OrdinaryItemIds+gen2BallItemIds+gen2PCMachineItemIds+gen2PCKeyItemIds
    private fun gen2PCItemIds(profile:Gen2Profile):Set<Int> = if(profile.key=="C")gen2CommonPCItemIds+gen2CrystalOnlyPCItemIds else gen2CommonPCItemIds

    private fun gen2TMHMStorageBase(profile:Gen2Profile):Int = if(profile.key=="C")0x23E7 else 0x23E6

    private fun parseGen2TMHMStorage(data:ByteArray,profile:Gen2Profile):List<Int>?{
        val base=gen2TMHMStorageBase(profile);if(base<0||base+57>data.size)return null;val out=ArrayList<Int>()
        for(i in 0 until 57){val quantity=data[base+i].toInt()and 0xFF;val max=if(i<50)99 else 1;if(quantity !in 0..max)return null;out.add(quantity)}
        return out
    }

    private fun writeGen2TMHMStorage(data:ByteArray,profile:Gen2Profile,quantities: