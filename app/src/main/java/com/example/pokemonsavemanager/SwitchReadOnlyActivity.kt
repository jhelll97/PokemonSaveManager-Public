package com.example.pokemonsavemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/** Isolated from legacy writers; Safe Backups owns the guarded Switch source-restore path. */
class SwitchReadOnlyActivity : Activity() {
    private val reader = Executors.newSingleThreadExecutor()
    private val pickRequest = 2101
    private var initialSection: String? = null
    private var pendingFamily: SwitchGameFamily? = null
    private var session: SwitchReadSession? = null
    private var selectedSourceUri: android.net.Uri? = null
    private var requestVersion = 0
    private var inspectionMode = false
    private var dashboardBusy = false
    private lateinit var pageScroll: ScrollView
    private lateinit var dashboardRoot: LinearLayout
    private lateinit var inspectionRoot: LinearLayout
    private lateinit var inspectionReturnButton: Button
    private lateinit var familyPicker: Spinner
    private lateinit var openButton: Button
    private lateinit var clearButton: Button
    private lateinit var partyButton: Button
    private lateinit var storageButton: Button
    private lateinit var rideButton: Button
    private lateinit var menuSelectionButton: Button
    private lateinit var randomGroupsButton: Button
    private lateinit var unionRoomButton: Button
    private lateinit var contestButton: Button
    private lateinit var ballCapsulesButton: Button
    private lateinit var sealStickersButton: Button
    private lateinit var undergroundReturnButton: Button
    private lateinit var npcRecordsButton: Button
    private lateinit var secretBaseButton: Button
    private lateinit var berryPlotsButton: Button
    private lateinit var encounterButton: Button
    private lateinit var honeyTreesButton: Button
    private lateinit var battleTrainerStatusButton: Button
    private lateinit var additionalRecordsButton: Button
    private lateinit var recordStatisticsButton: Button
    private lateinit var undergroundActivityButton: Button
    private lateinit var gameOptionsButton: Button
    private lateinit var playTimeButton: Button
    private lateinit var timestampsButton: Button
    private lateinit var appearanceButton: Button
    private lateinit var poffinsButton: Button
    private lateinit var battleTowerButton: Button
    private lateinit var nurseryButton: Button
    private lateinit var captureRecordsButton: Button
    private lateinit var goParkButton: Button
    private lateinit var undergroundButton: Button
    private lateinit var inventoryButton: Button
    private lateinit var pokedexButton: Button
    private lateinit var trainerButton: Button
    private lateinit var status: TextView
    private lateinit var preview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (18 * resources.displayMetrics.density).toInt()
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(7, 16, 24)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding * 2, padding, padding)
        }
        pageScroll = scroll
        inspectionRoot = root
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dashboardRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE
            setPadding(padding, padding * 2, padding, padding)
        }
        page.addView(dashboardRoot)
        page.addView(root)
        scroll.addView(page)
        fun label(value: String, size: Float = 16f): TextView = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, padding / 2, 0, padding / 2)
        }
        root.addView(label("Switch Saves â€¢ Read-only", 24f))
        root.addView(label("Browse extracted Switch saves in read-only mode. Use PSE Import or Scan to select a source. Native identity and integrity must validate before read features become available."))
        inspectionReturnButton = Button(this).apply {
            text = "Back to game dashboard"; isAllCaps = false; visibility = android.view.View.GONE
            setOnClickListener { if (session?.isFormatVerified == true && !dashboardBusy) {
                inspectionMode = false; refreshDashboard(false); pageScroll.scrollTo(0, 0)
            } }
        }
        root.addView(inspectionReturnButton)
        root.addView(label("Game family"))
        familyPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@SwitchReadOnlyActivity,
                android.R.layout.simple_spinner_dropdown_item,
                SwitchGameFamily.values().map { it.displayName })
        }
        root.addView(familyPicker)
        openButton = Button(this).apply {
            text = "Open extracted save (read-only)"
            setOnClickListener { pickSource() }
        }
        root.addView(openButton)
        status = label("Letâ€™s Go Pikachu/Eevee, Sword/Shield and BDSP: native detection, party, storage and PokÃ©mon details are available after validation. Letâ€™s Go, Sword/Shield and BDSP also have Trainer Data. Sword/Shield has regional PokÃ©dex browsing with saved form and Gigantamax flags. BDSP has 493-species PokÃ©dex records with gender/shiny, form, language and Spinda data. Letâ€™s Go has PokÃ©dex browsing plus reviewed seen/caught registration to a verified new copy. Legends: Arceus also supports a Trainer Data, party, 32 pastures and PokÃ©mon details with Alpha, Noble and saved Ganbaru values. Scarlet/Violet also supports a Trainer Data, party, 32 boxes, ride PokÃ©mon and details including saved Tera values. Legends: Z-A also supports a Trainer Data, party, 32 boxes and PokÃ©mon details including Alpha, scale and obedience.")
        root.addView(status)
        preview = label("No Switch file open. File inspection is available for all six game families. Save contents remain unvalidated.")
            .apply { setTextIsSelectable(true) }
        root.addView(preview)
        clearButton = Button(this).apply {
            text = "Close file preview"
            isEnabled = false
            setOnClickListener {
                requestVersion++
                session = null
                selectedSourceUri = null
                preview.text = "No Switch file open."
                status.text = "File preview closed. Open another extracted save for read-only inspection."
                isEnabled = false
                partyButton.isEnabled = false
                storageButton.isEnabled = false
                trainerButton.isEnabled = false
                pokedexButton.isEnabled = false
                inventoryButton.isEnabled = false
                menuSelectionButton.isEnabled = false
                menuSelectionButton.visibility = android.view.View.GONE
                randomGroupsButton.isEnabled = false
                randomGroupsButton.visibility = android.view.View.GONE
                unionRoomButton.isEnabled = false
                unionRoomButton.visibility = android.view.View.GONE
                contestButton.isEnabled = false
                contestButton.visibility = android.view.View.GONE
                ballCapsulesButton.isEnabled = false
                ballCapsulesButton.visibility = android.view.View.GONE
                sealStickersButton.isEnabled = false
                sealStickersButton.visibility = android.view.View.GONE
                undergroundReturnButton.isEnabled = false
                undergroundReturnButton.visibility = android.view.View.GONE
                npcRecordsButton.isEnabled = false
                npcRecordsButton.visibility = android.view.View.GONE
                secretBaseButton.isEnabled = false
                secretBaseButton.visibility = android.view.View.GONE
                berryPlotsButton.isEnabled = false
                berryPlotsButton.visibility = android.view.View.GONE
                encounterButton.isEnabled = false
                encounterButton.visibility = android.view.View.GONE
                honeyTreesButton.isEnabled = false
                honeyTreesButton.visibility = android.view.View.GONE
                battleTrainerStatusButton.isEnabled = false
                battleTrainerStatusButton.visibility = android.view.View.GONE
                additionalRecordsButton.isEnabled = false
                additionalRecordsButton.visibility = android.view.View.GONE
                recordStatisticsButton.isEnabled = false
                recordStatisticsButton.visibility = android.view.View.GONE
                undergroundActivityButton.isEnabled = false
                undergroundActivityButton.visibility = android.view.View.GONE
                gameOptionsButton.isEnabled = false
                gameOptionsButton.visibility = android.view.View.GONE
                playTimeButton.isEnabled = false
                playTimeButton.visibility = android.view.View.GONE
                timestampsButton.isEnabled = false
                timestampsButton.visibility = android.view.View.GONE
                appearanceButton.isEnabled = false
                appearanceButton.visibility = android.view.View.GONE
                poffinsButton.isEnabled = false
                poffinsButton.visibility = android.view.View.GONE
                battleTowerButton.isEnabled = false
                battleTowerButton.visibility = android.view.View.GONE
                nurseryButton.isEnabled = false
                nurseryButton.visibility = android.view.View.GONE
                captureRecordsButton.isEnabled = false
                captureRecordsButton.visibility = android.view.View.GONE
                goParkButton.isEnabled = false
                goParkButton.visibility = android.view.View.GONE
                undergroundButton.isEnabled = false
                undergroundButton.visibility = android.view.View.GONE
                rideButton.isEnabled = false
                rideButton.visibility = android.view.View.GONE
                inspectionMode = false
                refreshDashboard(false)
            }
        }
        root.addView(clearButton)
        trainerButton = Button(this).apply {
            text = "â—Ž  Trainer Data â€¢ Read-only"; isEnabled = false
            setOnClickListener { session?.let {
                when {
                    it.legendsZaTrainer != null -> showLegendsZaTrainer(it)
                    it.scarletVioletTrainer != null -> showScarletVioletTrainer(it)
                    it.legendsArceusTrainer != null -> showLegendsArceusTrainer(it)
                    it.bdspTrainer != null -> showBdspTrainer(it)
                    it.letsGoTrainer != null -> showLetsGoTrainer(it)
                    else -> showSwordShieldTrainer(it)
                }
            } }
        }
        root.addView(trainerButton)
        storageButton = Button(this).apply {
            text = "â—‰  PokÃ©mon Boxes â€¢ Read-only"; isEnabled = false
            setOnClickListener { session?.let { showSwitchPokemonBoxes(it) } }
        }
        partyButton = Button(this).apply {
            text = "Party PokÃ©mon â€¢ Read-only"; isEnabled = false
            setOnClickListener { session?.let { openSwitchParty(it) } }
        }
        root.addView(storageButton)
        root.addView(partyButton)
        rideButton = Button(this).apply {
            text = "Ride PokÃ©mon â€¢ Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { bound -> bound.scarletVioletStorage?.let {
                showScarletVioletPokemon(bound, "Ride PokÃ©mon", it.rideLegend)
            } } }
        }
        root.addView(rideButton)
        pokedexButton = Button(this).apply {
            text = "PokÃ©dex"; isEnabled = false
            setOnClickListener { session?.let {
                when {
                    it.legendsZaPokedex != null -> showLegendsZaDexSections(it)
                    it.scarletVioletPokedex != null -> showScarletVioletDexRegions(it)
                    it.legendsArceusPokedex != null -> showLegendsArceusPokedex(it, 0)
                    it.bdspPokedex != null -> showBdspPokedex(it, 0)
                    it.swordShieldPokedex != null -> showSwordShieldDexRegions(it)
                    else -> showLetsGoPokedex(it, 0)
                }
            } }
        }
        root.addView(pokedexButton)
        inventoryButton = Button(this).apply {
            text = "Items â€¢ Read-only"; isEnabled = false
            setOnClickListener { session?.let { if (it.legendsZaInventory != null) showLegendsZaInventoryPockets(it) else if (it.scarletVioletInventory != null) showScarletVioletInventoryPockets(it) else if (it.legendsArceusInventory != null) showLegendsArceusInventoryPockets(it) else if (it.bdspInventory != null) showBdspInventoryPockets(it) else if (it.swordShieldInventory != null) showSwordShieldInventoryPockets(it) else showLetsGoInventoryPockets(it) } }
        }
        root.addView(inventoryButton)
        undergroundButton = Button(this).apply {
            text = "Grand Underground â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUndergroundSections(it) } }
        }
        root.addView(undergroundButton)
        nurseryButton = Button(this).apply {
            text = "Nursery â€¢ Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let {
                when {
                    it.swordShieldDaycare != null -> showSwordShieldDaycare(it)
                    it.letsGoDayCare != null -> showLetsGoDayCare(it)
                    else -> showBdspNursery(it)
                }
            } }
        }
        root.addView(nurseryButton)
        captureRecordsButton = Button(this).apply {
            text = "Capture Records"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showLetsGoCaptureRecords(it) } }
        }
        root.addView(captureRecordsButton)
        goParkButton = Button(this).apply {
            text = "GO Park â€¢ Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showLetsGoGoParkAreas(it) } }
        }
        root.addView(goParkButton)
        battleTowerButton = Button(this).apply {
            text = "Battle Tower â€¢ Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let {
                if (it.swordShieldBattleTower != null) showSwordShieldBattleTower(it) else showBdspBattleTower(it)
            } }
        }
        root.addView(battleTowerButton)
        poffinsButton = Button(this).apply {
            text = "Poffin Case â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspPoffins(it, 0, false) } }
        }
        root.addView(poffinsButton)
        appearanceButton = Button(this).apply {
            text = "Appearance and Position â€¢ Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspAppearance(it) } }
        }
        root.addView(appearanceButton)
        timestampsButton = Button(this).apply {
            text = "Saved Timestamps â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspTimestamps(it) } }
        }
        root.addView(timestampsButton)
        playTimeButton = Button(this).apply {
            text = "Recorded Playtime â€¢ Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { if (it.letsGoPlayTime != null) showLetsGoPlayTime(it) else showBdspPlayTime(it) } }
        }
        root.addView(playTimeButton)
        gameOptionsButton = Button(this).apply {
            text = "Game Options â€¢ Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { if (it.letsGoGameOptions != null) showLetsGoGameOptions(it) else showBdspGameOptions(it) } }
        }
        root.addView(gameOptionsButton)
        undergroundActivityButton = Button(this).apply {
            text = "Underground Activity â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUndergroundActivity(it) } }
        }
        root.addView(undergroundActivityButton)
        recordStatisticsButton = Button(this).apply {
            text = "Record Statistics â€¢ Edit head"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspRecords(it) } }
        }
        root.addView(recordStatisticsButton)
        additionalRecordsButton = Button(this).apply {
            text = "Additional Record Trainers â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspAdditionalRecords(it) } }
        }
        root.addView(additionalRecordsButton)
        battleTrainerStatusButton = Button(this).apply {
            text = "Battle Trainer Status â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspBattleTrainerFilters(it) } }
        }
        root.addView(battleTrainerStatusButton)
        honeyTreesButton = Button(this).apply {
            text = "Honey Trees â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspHoneyTrees(it) } }
        }
        root.addView(honeyTreesButton)
        encounterButton = Button(this).apply {
            text = "Encounters â€¢ Overview Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspEncounterMenu(it) } }
        }
        root.addView(encounterButton)
        berryPlotsButton = Button(this).apply {
            text = "Berry Plot Records â€¢ Edit timing"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspBerryPlots(it) } }
        }
        root.addView(berryPlotsButton)
        secretBaseButton = Button(this).apply {
            text = "Secret Base â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspSecretBase(it) } }
        }
        root.addView(secretBaseButton)
        npcRecordsButton = Button(this).apply {
            text = "Underground NPC Records â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUndergroundNpcFilters(it) } }
        }
        root.addView(npcRecordsButton)
        undergroundReturnButton = Button(this).apply {
            text = "Underground Return Data â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUndergroundReturn(it) } }
        }
        root.addView(undergroundReturnButton)
        sealStickersButton = Button(this).apply {
            text = "Seal Stickers â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspSealStickerFilters(it) } }
        }
        root.addView(sealStickersButton)
        ballCapsulesButton = Button(this).apply {
            text = "Ball Capsules â€¢ Affix Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspBallCapsules(it, 0, false) } }
        }
        root.addView(ballCapsulesButton)
        contestButton = Button(this).apply {
            text = "Contest Records â€¢ Edit rank points"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspContest(it) } }
        }
        root.addView(contestButton)
        unionRoomButton = Button(this).apply {
            text = "Union Room Saved Data â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUnionRoom(it) } }
        }
        root.addView(unionRoomButton)
        randomGroupsButton = Button(this).apply {
            text = "Random Group Records â€¢ Edit"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspRandomGroups(it) } }
        }
        root.addView(randomGroupsButton)
        menuSelectionButton = Button(this).apply {
            text = "Saved Menu Layout â€¢ Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspMenuSelection(it) } }
        }
        root.addView(menuSelectionButton)
        root.addView(label("Inspection stays read-only. Supported edit actions open a separate before/after review and write only to a verified new copy. The preview is held in memory and closes when you leave this screen."))
        root.addView(Button(this).apply { text = "Back to PSE"; setOnClickListener { finish() } })
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 16, 24))
        }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(buildBottomNav(), LinearLayout.LayoutParams(-1, -2))
        setContentView(shell)
        val source = intent?.data
        val family = intent?.getStringExtra("pse.switch.family")?.let { value -> SwitchGameFamily.values().firstOrNull { it.name == value } }
        if (source != null && family != null) {
            familyPicker.visibility = android.view.View.GONE
            openButton.visibility = android.view.View.GONE
            initialSection = intent.getStringExtra("pse.switch.section")
            pendingFamily = family
            setBusy(true)
            onActivityResult(pickRequest, RESULT_OK, Intent().setData(source))
        }
    }

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            minimumHeight = (64 * resources.displayMetrics.density).toInt()
            setPadding((8 * resources.displayMetrics.density).toInt(), (5 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt(), (5 * resources.displayMetrics.density).toInt())
            setBackgroundColor(Color.rgb(9, 25, 36))
        }
        fun item(icon: String, label: String, active: Boolean, action: () -> Unit) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setOnClickListener { action() }
            }
            box.addView(TextView(this).apply {
                text = icon
                textSize = 20f
                gravity = android.view.Gravity.CENTER
                setTextColor(if (active) Color.rgb(45, 185, 255) else Color.rgb(125, 145, 158))
            })
            box.addView(TextView(this).apply {
                text = label
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                typeface = if (active) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                setTextColor(if (active) Color.rgb(70, 190, 255) else Color.rgb(145, 160, 170))
            })
            nav.addView(box, LinearLayout.LayoutParams(0, -1, 1f))
        }
        item("âŒ‚", "Home", false) { finish() }
        item("â—‰", "PokÃ©mon", true) {
            session?.let { openSwitchParty(it) } ?: pickSource()
        }
        item("â–£", "Bank", false) {
            session?.let { showSwitchBank(it) } ?: finish()
        }
        item("â–¤", "Backups", false) {
            session?.let { showSwitchSafeBackups(it) } ?: finish()
        }
        return nav
    }

    private fun setBusy(busy: Boolean) {
        familyPicker.isEnabled = !busy
        openButton.isEnabled = !busy
        clearButton.isEnabled = !busy && session != null
        partyButton.isEnabled = !busy && session?.supports(SwitchReadCapability.PARTY) == true
        storageButton.isEnabled = !busy && (session?.supports(SwitchReadCapability.PC_OR_BOXES) == true ||
            session?.supports(SwitchReadCapability.PASTURES) == true)
        storageButton.text = if (session?.profile?.family in setOf(
                SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, SwitchGameFamily.SWORD_SHIELD,
                SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL, SwitchGameFamily.LEGENDS_ARCEUS,
                SwitchGameFamily.SCARLET_VIOLET, SwitchGameFamily.LEGENDS_ZA))
            "â—‰  PokÃ©mon Boxes â€¢ Nickname edit" else "â—‰  PokÃ©mon Boxes â€¢ Read-only"
        menuSelectionButton.isEnabled = !busy && session?.bdspMenuSelection != null
        menuSelectionButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL) android.view.View.VISIBLE else android.view.View.GONE
        randomGroupsButton.isEnabled = !busy && session?.bdspRandomGroups != null
        randomGroupsButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL) android.view.View.VISIBLE else android.view.View.GONE
        unionRoomButton.isEnabled = !busy && session?.bdspUnionRoom != null
        unionRoomButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL) android.view.View.VISIBLE else android.view.View.GONE
        contestButton.isEnabled = !busy && session?.bdspContest != null
        contestButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL) android.view.View.VISIBLE else android.view.View.GONE
        ballCapsulesButton.isEnabled = !busy && session?.bdspBallCapsules != null
        ballCapsulesButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        sealStickersButton.isEnabled = !busy && session?.bdspSealStickers != null
        sealStickersButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        undergroundReturnButton.isEnabled = !busy && session?.bdspUndergroundReturn != null
        undergroundReturnButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        npcRecordsButton.isEnabled = !busy && session?.bdspUndergroundNpc != null
        npcRecordsButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        secretBaseButton.isEnabled = !busy && session?.bdspSecretBase != null
        secretBaseButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        berryPlotsButton.isEnabled = !busy && session?.bdspBerryPlots != null
        berryPlotsButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        encounterButton.isEnabled = !busy && session?.bdspEncounter != null
        encounterButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        honeyTreesButton.isEnabled = !busy && session?.bdspHoneyTrees != null
        honeyTreesButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        battleTrainerStatusButton.isEnabled = !busy && session?.bdspBattleTrainerStatus != null
        battleTrainerStatusButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        additionalRecordsButton.isEnabled = !busy && session?.bdspAdditionalRecords != null
        additionalRecordsButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        recordStatisticsButton.isEnabled = !busy && session?.bdspRecordStatistics != null
        recordStatisticsButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        undergroundActivityButton.isEnabled = !busy && session?.bdspUndergroundActivity != null
        undergroundActivityButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        gameOptionsButton.isEnabled = !busy && (session?.bdspGameOptions != null || session?.letsGoGameOptions != null)
        gameOptionsButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL || session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE)
            android.view.View.VISIBLE else android.view.View.GONE
        playTimeButton.isEnabled = !busy && (session?.bdspPlayTime != null || session?.letsGoPlayTime != null)
        playTimeButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL || session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE)
            android.view.View.VISIBLE else android.view.View.GONE
        timestampsButton.isEnabled = !busy && session?.bdspTimestamps != null
        timestampsButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        appearanceButton.isEnabled = !busy && session?.bdspTrainerAppearance != null
        appearanceButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        poffinsButton.isEnabled = !busy && session?.bdspPoffins != null
        poffinsButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        battleTowerButton.isEnabled = !busy && (session?.bdspBattleTower != null || session?.swordShieldBattleTower != null)
        battleTowerButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL ||
            session?.profile?.family == SwitchGameFamily.SWORD_SHIELD)
            android.view.View.VISIBLE else android.view.View.GONE
        nurseryButton.text = if (session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE) "Day Care â€¢ Read-only" else "Nursery â€¢ Read-only"
        nurseryButton.isEnabled = !busy && (session?.bdspNursery != null || session?.letsGoDayCare != null || session?.swordShieldDaycare != null)
        nurseryButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL ||
            session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE || session?.profile?.family == SwitchGameFamily.SWORD_SHIELD)
            android.view.View.VISIBLE else android.view.View.GONE
        captureRecordsButton.isEnabled = !busy && session?.letsGoCaptureRecords != null
        captureRecordsButton.visibility = if (session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE)
            android.view.View.VISIBLE else android.view.View.GONE
        goParkButton.isEnabled = !busy && session?.letsGoGoPark != null
        goParkButton.visibility = if (session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE)
            android.view.View.VISIBLE else android.view.View.GONE
        undergroundButton.isEnabled = !busy && session?.bdspUnderground != null
        undergroundButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        inventoryButton.isEnabled = !busy && session?.supports(SwitchReadCapability.INVENTORY) == true
        pokedexButton.isEnabled = !busy && session?.supports(SwitchReadCapability.POKEDEX) == true
        trainerButton.text = if (session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE ||
            session?.profile?.family == SwitchGameFamily.SWORD_SHIELD ||
            session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL ||
            session?.profile?.family == SwitchGameFamily.LEGENDS_ARCEUS ||
            session?.profile?.family == SwitchGameFamily.SCARLET_VIOLET)
            "â—Ž  Trainer Data" else "â—Ž  Trainer Data â€¢ Read-only"
        trainerButton.isEnabled = !busy && session?.supports(SwitchReadCapability.TRAINER_PROFILE) == true
        rideButton.isEnabled = !busy && session?.scarletVioletStorage != null
        rideButton.visibility = if (session?.profile?.family == SwitchGameFamily.SCARLET_VIOLET)
            android.view.View.VISIBLE else android.view.View.GONE
        refreshDashboard(busy)
    }

    private fun refreshDashboard(busy: Boolean) {
        dashboardBusy = busy
        val bound = session
        val verified = bound?.isFormatVerified == true
        inspectionReturnButton.visibility = if (verified) android.view.View.VISIBLE else android.view.View.GONE
        inspectionReturnButton.isEnabled = verified && !busy
        dashboardRoot.removeAllViews()
        dashboardRoot.visibility = if (verified && !inspectionMode) android.view.View.VISIBLE else android.view.View.GONE
        inspectionRoot.visibility = if (verified && !inspectionMode) android.view.View.GONE else android.view.View.VISIBLE
        if (bound == null || !verified) return
        fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
        fun label(value: String, size: Float = 16f, color: Int = Color.WHITE): TextView = TextView(this).apply {
            text = value; textSize = size; setTextColor(color); setPadding(dp(4), dp(6), dp(4), dp(10))
        }
        fun cardBackground(fill: Int): android.graphics.drawable.GradientDrawable =
            OmegaRubyUi.roundedCard(this, fill)
        val accent = OmegaRubyUi.accent
        val trainerDataEditing = (bound.profile.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE ||
            bound.profile.family == SwitchGameFamily.SWORD_SHIELD ||
            bound.profile.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL ||
            bound.profile.family == SwitchGameFamily.LEGENDS_ARCEUS ||
            bound.profile.family == SwitchGameFamily.SCARLET_VIOLET ||
            bound.profile.family == SwitchGameFamily.LEGENDS_ZA) && selectedSourceUri != null
        val pokemonEditing = bound.profile.family in setOf(
            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE,
            SwitchGameFamily.SWORD_SHIELD,
            SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL,
            SwitchGameFamily.LEGENDS_ARCEUS,
            SwitchGameFamily.SCARLET_VIOLET,
            SwitchGameFamily.LEGENDS_ZA
        ) && selectedSourceUri != null
        dashboardRoot.addView(label("PSE", 38f, accent))
        dashboardRoot.addView(label(if (trainerDataEditing) "PokÃ©mon Save Editor" else "PokÃ©mon Save Editor â€¢ Read-only", 15f))
        dashboardRoot.addView(label("CURRENT SAVE", 13f, accent))
        dashboardRoot.addView(label("${bound.displayName}\nGeneration ${bound.profile.family.generation} â€¢ Nintendo Switch\nNative integrity verified" +
            if (trainerDataEditing) " â€¢ Trainer Data editing available" else " â€¢ Read-only", 18f).apply {
            setPadding(dp(18), dp(16), dp(18), dp(16)); background = cardBackground(Color.rgb(12, 35, 51))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        dashboardRoot.addView(label(status.text.toString(), 14f))
        dashboardRoot.addView(label(if (busy) "Reading selected sourceâ€¦" else if (trainerDataEditing)
            "EDITOR â€¢ TRAINER DATA" else "EDITOR â€¢ READ-ONLY", 13f, accent))
        val targets = mapOf("trainer" to trainerButton, "storage" to storageButton,
            "inventory" to inventoryButton, "pokedex" to pokedexButton)
        val entries = SwitchReadDashboard.entries(bound)
        val detailGroups = SwitchReadGameDetails.groups(bound)
        for ((pairIndex, pair) in entries.chunked(2).withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (entry in pair) {
                val target = targets.getValue(entry.section)
                row.addView(Button(this).apply {
                    val trainerEditing = entry.section == "trainer" && trainerDataEditing
                    val storageEditing = entry.section == "storage" && pokemonEditing
                    text = entry.label + when {
                        !entry.enabled -> "\nUnavailable for this save"
                        trainerEditing -> "\nEdit Trainer Data"
                        storageEditing -> "\nEdit PokÃ©mon"
                        else -> "\nRead-only"
                    }
                    textSize = 16f; isAllCaps = false; minHeight = dp(100); setTextColor(Color.WHITE)
                    background = cardBackground(Color.rgb(12, 35, 51))
                    isEnabled = !busy && entry.enabled && target.isEnabled; alpha = if (isEnabled) 1f else 0.5f
                    setOnClickListener { if (session === bound && !dashboardBusy && target.isEnabled) target.performClick() }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            if (pair.size == 1 && detailGroups.isNotEmpty()) row.addView(Button(this).apply {
                text = "Game Details\nView and edit"; textSize = 16f; isAllCaps = false; minHeight = dp(100)
                setTextColor(Color.WHITE); background = cardBackground(Color.rgb(12, 35, 51))
                isEnabled = !busy && detailGroups.any { group -> group.entries.any { it.enabled } }
                alpha = if (isEnabled) 1f else 0.5f
                setOnClickListener { if (session === bound && !dashboardBusy) showBdspGameDetailGroups(bound) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            else if (pair.size == 1 && bound.letsGoPlayTime != null) row.addView(Button(this).apply {
                text = "Recorded Playtime\nRead-only"; textSize = 16f; isAllCaps = false; minHeight = dp(100)
                setTextColor(Color.WHITE); background = cardBackground(Color.rgb(12, 35, 51))
                isEnabled = !busy && playTimeButton.isEnabled; alpha = if (isEnabled) 1f else 0.5f
                setOnClickListener { if (session === bound && !dashboardBusy && playTimeButton.isEnabled) playTimeButton.performClick() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            else if (pair.size == 1) row.addView(android.view.View(this), LinearLayout.LayoutParams(0, 0, 1f))
            dashboardRoot.addView(row)
            if (pairIndex == 0 && bound.profile.family in setOf(
                    SwitchGameFamily.LETS_GO_PIKACHU_EEVEE,
                    SwitchGameFamily.SWORD_SHIELD,
                    SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL,
                    SwitchGameFamily.LEGENDS_ARCEUS,
                    SwitchGameFamily.SCARLET_VIOLET,
                    SwitchGameFamily.LEGENDS_ZA
                )) {
                dashboardRoot.addView(Button(this).apply {
                    text = "â—‡  PSE Bank â€¢ Manage"
                    isAllCaps = false; textSize = 17f; isEnabled = !busy
                    setOnClickListener { if (session === bound && !dashboardBusy) showSwitchBank(bound) }
                })
                dashboardRoot.addView(Button(this).apply {
                    text = "â–¤  Safe Backups"
                    isAllCaps = false; textSize = 17f; isEnabled = !busy && bound.supports(SwitchReadCapability.SAFE_BACKUPS)
                    setOnClickListener { if (session === bound && !dashboardBusy) showSwitchSafeBackups(bound) }
                })
            }
        }
        dashboardRoot.addView(OmegaRubyUi.sectionLabel(this, "GAME FEATURES"))
        if (bound.profile.family in setOf(SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, SwitchGameFamily.SWORD_SHIELD,
                SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL,
                SwitchGameFamily.LEGENDS_ARCEUS,
                SwitchGameFamily.SCARLET_VIOLET,
                SwitchGameFamily.LEGENDS_ZA)) dashboardRoot.addView(Button(this).apply {
            text = "ðŸŽ  Events"; isAllCaps = false; textSize = 17f; isEnabled = !busy && selectedSourceUri != null
            setOnClickListener { if (session === bound && !dashboardBusy) launchSwitchEvents(bound) }
        })
        if (bound.letsGoDayCare != null) dashboardRoot.addView(Button(this).apply {
            text = "Day Care â€¢ Read-only"; isAllCaps = false; isEnabled = !busy && nurseryButton.isEnabled
            setOnClickListener { if (session === bound && !dashboardBusy && nurseryButton.isEnabled) nurseryButton.performClick() }
        })
        if (bound.swordShieldPlayTime != null) dashboardRoot.addView(Button(this).apply {
            text = "Recorded Playtime â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldPlayTime(bound) }
        })
        if (bound.swordShieldBattleTower != null) dashboardRoot.addView(Button(this).apply {
            text = "Battle Tower Records â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldBattleTower(bound) }
        })
        if (bound.swordShieldDaycare != null) dashboardRoot.addView(Button(this).apply {
            text = "Nursery Records â€¢ Read-only"; isAllCaps = false; isEnabled = !busy && nurseryButton.isEnabled
            setOnClickListener { if (session === bound && !dashboardBusy && nurseryButton.isEnabled) nurseryButton.performClick() }
        })
        if (bound.swordShieldBoxLayout != null) dashboardRoot.addView(Button(this).apply {
            text = "Box Layout â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldBoxLayout(bound) }
        })
        if (bound.swordShieldRallyDigging != null) dashboardRoot.addView(Button(this).apply {
            text = "Rally & Digging Records â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldRallyDigging(bound) }
        })
        if (bound.swordShieldStarterChoice != null) dashboardRoot.addView(Button(this).apply {
            text = "Starter Choice â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldStarterChoice(bound) }
        })
        if (bound.swordShieldLeagueCard != null) dashboardRoot.addView(Button(this).apply {
            text = "League Card â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldLeagueCard(bound) }
        })
        if (bound.swordShieldIsleOfArmorProgress != null) dashboardRoot.addView(Button(this).apply {
            text = "Isle of Armor Progress â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldIsleOfArmorProgress(bound) }
        })
        if (bound.swordShieldCrownTundraProgress != null) dashboardRoot.addView(Button(this).apply {
            text = "Crown Tundra Progress â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldCrownTundraProgress(bound) }
        })
        if (bound.swordShieldStarTournament != null) dashboardRoot.addView(Button(this).apply {
            text = "Galarian Star Tournament â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldStarTournament(bound) }
        })
        if (bound.swordShieldSwordsOfJustice != null) dashboardRoot.addView(Button(this).apply {
            text = "Swords of Justice Quest â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldSwordsOfJustice(bound) }
        })
        if (bound.swordShieldAdventureSteps != null) dashboardRoot.addView(Button(this).apply {
            text = "Adventure Step Counters â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldAdventureSteps(bound) }
        })
        if (bound.swordShieldHallOfFameTime != null) dashboardRoot.addView(Button(this).apply {
            text = "Hall of Fame Time â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldHallOfFameTime(bound) }
        })
        if (bound.swordShieldPokedexViewState != null) dashboardRoot.addView(Button(this).apply {
            text = "PokÃ©dex View State â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldPokedexViewState(bound) }
        })
        if (bound.swordShieldRestrictedSparring != null) dashboardRoot.addView(Button(this).apply {
            text = "Restricted Sparring Streaks â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showSwordShieldRestrictedSparring(bound) }
        })
        if (bound.letsGoCaptureRecords != null) dashboardRoot.addView(Button(this).apply {
            text = "Capture Records"; isAllCaps = false; isEnabled = !busy && captureRecordsButton.isEnabled
            setOnClickListener { if (session === bound && !dashboardBusy && captureRecordsButton.isEnabled) captureRecordsButton.performClick() }
        })
        if (bound.letsGoGoPark != null) dashboardRoot.addView(Button(this).apply {
            text = "GO Park â€¢ Read-only"; isAllCaps = false; isEnabled = !busy && goParkButton.isEnabled
            setOnClickListener { if (session === bound && !dashboardBusy && goParkButton.isEnabled) goParkButton.performClick() }
        })
        if (bound.letsGoStorage != null) dashboardRoot.addView(Button(this).apply {
            text = "Storage Pool Map â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoStoragePoolMap(bound) }
        })
        if (bound.letsGoPlayerLocation != null) dashboardRoot.addView(Button(this).apply {
            text = "Player Position â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoPlayerLocation(bound) }
        })
        if (bound.letsGoAdventureBegin != null) dashboardRoot.addView(Button(this).apply {
            text = "Adventure Start â€¢ Edit"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoAdventureBegin(bound) }
        })
        if (bound.letsGoSecureValues != null) dashboardRoot.addView(Button(this).apply {
            text = "Save Secure Values â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoSecureValues(bound) }
        })
        if (bound.letsGoGameSyncIdentity != null) dashboardRoot.addView(Button(this).apply {
            text = "Game Sync Identity â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoGameSyncIdentity(bound) }
        })
        if (bound.letsGoEquippedAppearance != null) dashboardRoot.addView(Button(this).apply {
            text = "Equipped Appearance â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoEquippedAppearance(bound) }
        })
        if (bound.letsGoBlockDirectory != null) dashboardRoot.addView(Button(this).apply {
            text = "Native Block Directory â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoBlockDirectory(bound) }
        })
        val masterTitles = bound.letsGoMasterTrainerTitles
        if (masterTitles != null) dashboardRoot.addView(Button(this).apply {
            text = if (masterTitles.unlockedCount == masterTitles.totalTitles) "Master Trainer Titles â€¢ All unlocked" else "Master Trainer Titles"
            isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoMasterTrainerTitles(bound) }
        })
        if (bound.letsGoEventWork != null) dashboardRoot.addView(Button(this).apply {
            text = "Event Work Inspector â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoEventWork(bound) }
        })
        if (bound.letsGoMysteryGiftHistory != null) dashboardRoot.addView(Button(this).apply {
            text = "Mystery Gift History â€¢ Read-only"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) showLetsGoMysteryGiftHistory(bound) }
        })
        val playerAccessories = bound.letsGoPlayerAccessories
        if (playerAccessories != null && selectedSourceUri != null) dashboardRoot.addView(Button(this).apply {
            text = if (playerAccessories.isAllUnlocked) "Player Accessories â€¢ All unlocked" else "Player Accessories â€¢ Unlock to new copy"
            isAllCaps = false; isEnabled = !busy && !playerAccessories.isAllUnlocked
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) startActivity(
                    Intent(this@SwitchReadOnlyActivity, SwitchLetsGoPlayerAccessoriesActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.accessory.target", LetsGoAccessoryTarget.PLAYER.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        })
        val starterAccessories = bound.letsGoStarterAccessories
        if (starterAccessories != null && selectedSourceUri != null) dashboardRoot.addView(Button(this).apply {
            text = if (starterAccessories.isAllUnlocked) "Partner Accessories â€¢ All unlocked" else "Partner Accessories â€¢ Unlock to new copy"
            isAllCaps = false; isEnabled = !busy && !starterAccessories.isAllUnlocked
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) startActivity(
                    Intent(this@SwitchReadOnlyActivity, SwitchLetsGoPlayerAccessoriesActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.accessory.target", LetsGoAccessoryTarget.STARTER.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        })
        if (bound.letsGoGameOptions != null) dashboardRoot.addView(Button(this).apply {
            text = "Game Options â€¢ Read-only"; isAllCaps = false; isEnabled = !busy && gameOptionsButton.isEnabled
            setOnClickListener { if (session === bound && !dashboardBusy && gameOptionsButton.isEnabled) gameOptionsButton.performClick() }
        })
        dashboardRoot.addView(Button(this).apply {
            text = "Inspect current save  â€º"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy) {
                inspectionMode = true; refreshDashboard(false); pageScroll.scrollTo(0, 0)
            } }
        })
        val editOptionsRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        var editOptionsDialog: AlertDialog? = null
        if (bound.bdspGameOptions?.textSpeedId?.let { it in 0L..3L } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP text speed â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.battleStyleId?.let { it in 0..1 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP Battle Style â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.BATTLE_STYLE.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.moveAnimationsId?.let { it in 0..1 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP move animations â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.MOVE_ANIMATIONS.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.partyBoxId?.let { it in 0..1 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP party/box preference â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.PARTY_BOX.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.cameraVerticalId?.let { it in 0..1 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP vertical camera â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.CAMERA_VERTICAL.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.cameraHorizontalId?.let { it in 0..1 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP horizontal camera â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.CAMERA_HORIZONTAL.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.inputModeId?.let { it in 0..1 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP input mode â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.INPUT_MODE.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.flag(BdspGameOptionFlag.SHOW_NICKNAMES)?.hasCanonicalByte == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP nickname display â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.SHOW_NICKNAMES.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.flag(BdspGameOptionFlag.NICKNAME_REGISTRATION)?.hasCanonicalByte == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP nickname registration â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.NICKNAME_REGISTRATION.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.flag(BdspGameOptionFlag.GYRO_SENSOR)?.hasCanonicalByte == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP gyro-sensor preference â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.GYRO_SENSOR.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.flag(BdspGameOptionFlag.FOSSIL_CAMERA_SHAKE)?.hasCanonicalByte == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP fossil camera shake â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.FOSSIL_CAMERA_SHAKE.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.flag(BdspGameOptionFlag.AUTO_REPORT)?.hasCanonicalByte == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP auto-report preference â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.AUTO_REPORT.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.flag(BdspGameOptionFlag.KANJI)?.hasCanonicalByte == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP Kanji-script preference â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.KANJI_SCRIPT.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.bgmVolume?.let { it in 0..10 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP music volume â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.BGM_VOLUME.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.soundEffectsVolume?.let { it in 0..10 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP sound-effects volume â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.SOUND_EFFECTS_VOLUME.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (bound.bdspGameOptions?.voiceVolume?.let { it in 0..10 } == true && selectedSourceUri != null) editOptionsRoot.addView(Button(this).apply {
            text = "Edit BDSP voice volume â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && !dashboardBusy && source != null) {
                    editOptionsDialog?.dismiss()
                    startActivity(Intent(this@SwitchReadOnlyActivity, SwitchBdspTextSpeedActivity::class.java)
                        .setData(source).putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.setting", BdspEditableSetting.VOICE_VOLUME.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }
            }
        })
        if (editOptionsRoot.childCount > 0) {
            editOptionsRoot.addView(label("Choose one saved option. You will review the change before choosing a new copy; the source stays unchanged.", 16f), 0)
            dashboardRoot.addView(Button(this).apply {
                text = "Edit BDSP options â€¢ Save new copy"; isAllCaps = false; isEnabled = !busy
                setOnClickListener {
                    if (session === bound && !dashboardBusy && selectedSourceUri != null && editOptionsDialog == null) {
                        (editOptionsRoot.parent as? android.view.ViewGroup)?.removeView(editOptionsRoot)
                        editOptionsDialog = AlertDialog.Builder(this@SwitchReadOnlyActivity)
                            .setTitle("BDSP â€¢ Edit options")
                            .setView(ScrollView(this@SwitchReadOnlyActivity).apply { addView(editOptionsRoot) })
                            .setNegativeButton("Cancel", null).create()
                        editOptionsDialog?.setOnDismissListener { editOptionsDialog = null }
                        editOptionsDialog?.show()
                    }
                }
            })
        }
        dashboardRoot.addView(label("Only controls explicitly marked Edit can write to a verified new copy. Other source replacement remains locked.", 14f))
        dashboardRoot.addView(OmegaRubyUi.sectionLabel(this, "SAVE MANAGEMENT"))
        dashboardRoot.addView(Button(this).apply {
            text = "Close save preview"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy && clearButton.isEnabled) clearButton.performClick() }
        })
        dashboardRoot.addView(Button(this).apply { text = "Back to PSE"; isAllCaps = false; setOnClickListener { finish() } })
        OmegaRubyUi.styleActionTree(this, dashboardRoot)
    }

    private fun bdspGameDetailTargets(): Map<String, Button> = mapOf(
        "appearance" to appearanceButton, "playtime" to playTimeButton, "timestamps" to timestampsButton,
        "options" to gameOptionsButton, "menu" to menuSelectionButton, "poffins" to poffinsButton,
        "stickers" to sealStickersButton, "capsules" to ballCapsulesButton, "underground" to undergroundButton,
        "encounters" to encounterButton, "honey" to honeyTreesButton, "berries" to berryPlotsButton,
        "secretbase" to secretBaseButton, "npcs" to npcRecordsButton, "return" to undergroundReturnButton,
        "activity" to undergroundActivityButton, "tower" to battleTowerButton, "trainers" to battleTrainerStatusButton,
        "statistics" to recordStatisticsButton, "additional" to additionalRecordsButton, "nursery" to nurseryButton,
        "contest" to contestButton, "union" to unionRoomButton, "groups" to randomGroupsButton
    )

    private fun showBdspGameDetailGroups(bound: SwitchReadSession) {
        if (session !== bound || dashboardBusy) return
        val groups = SwitchReadGameDetails.groups(bound)
        if (groups.isEmpty()) return
        val targets = bdspGameDetailTargets()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "Browse saved game details by topic. Sections remain read-only unless explicitly marked Edit, and each keeps its own validation checks."
            textSize = 16f; setPadding(0, 0, 0, 16)
        })
        lateinit var dialog: AlertDialog
        groups.forEachIndexed { index, group ->
            val available = group.entries.count { it.enabled && targets.getValue(it.section).isEnabled }
            root.addView(Button(this).apply {
                text = "${group.label}\n$available/${group.entries.size} sections available"; isAllCaps = false
                isEnabled = available > 0
                setOnClickListener { if (session === bound && !dashboardBusy) {
                    dialog.dismiss(); showBdspGameDetailGroup(bound, index)
                } }
            })
        }
        dialog = AlertDialog.Builder(this).setTitle("${bound.displayName} â€¢ Game Details")
            .setView(ScrollView(this).apply { addView(root) }).setNegativeButton("Close", null).create()
        dialog.show()
    }

    private fun showBdspGameDetailGroup(bound: SwitchReadSession, index: Int) {
        if (session !== bound || dashboardBusy) return
        val groups = SwitchReadGameDetails.groups(bound)
        if (index !in groups.indices) return
        val group = groups[index]
        val targets = bdspGameDetailTargets()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        for (entry in group.entries) {
            val target = targets.getValue(entry.section)
            root.addView(Button(this).apply {
                text = entry.label + if (entry.enabled && target.isEnabled)
                    (if (entry.section in setOf("timestamps", "poffins", "stickers", "capsules", "underground", "encounters", "honey")) " â€¢ Edit" else " â€¢ Read-only")
                else " â€¢ Unavailable for this save"
                isAllCaps = false; isEnabled = entry.enabled && target.isEnabled
                setOnClickListener { if (session === bound && !dashboardBusy && target.isEnabled) target.performClick() }
            })
        }
        AlertDialog.Builder(this).setTitle(group.label)
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("All sections") { _, _ -> if (session === bound && !dashboardBusy) showBdspGameDetailGroups(bound) }
            .setNegativeButton("Close", null).show()
    }

    override fun onBackPressed() {
        if (inspectionMode && session?.isFormatVerified == true && !dashboardBusy) {
            inspectionMode = false; refreshDashboard(false); pageScroll.scrollTo(0, 0)
        } else super.onBackPressed()
    }

    private fun pickSource() {
        pendingFamily = SwitchGameFamily.values()[familyPicker.selectedItemPosition]
        setBusy(true)
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, pickRequest)
        } catch (_: Exception) {
            pendingFamily = null
            setBusy(false)
            status.text = "The file picker could not be opened. Any previous preview is unchanged."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickRequest) return
        val family = pendingFamily
        pendingFamily = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || family == null) {
            setBusy(false)
            status.text = "Open cancelled. Any previous preview is unchanged."
            return
        }
        if (uri.scheme != "content") {
            setBusy(false)
            status.text = "Choose a file from the Android document picker. Any previous preview is unchanged."
            return
        }
        val version = ++requestVersion
        status.text = "Reading a bounded, read-only snapshotâ€¦"
        reader.execute {
            var loaded: SwitchReadSession? = null
            var failure = "The selected file could not be read. Choose an accessible extracted save file."
            try {
                val input = contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("No readable stream")
                val candidate = SwitchReadSession(SwitchReadSnapshot.readFrom(family, input))
                val validation = candidate.validation
                if (validation is LetsGoValidation.Invalid)
                    throw IllegalArgumentException("Let's Go save rejected: ${validation.reason}")
                val swsh = candidate.swordShieldValidation
                if (swsh is SwordShieldValidation.Invalid)
                    throw IllegalArgumentException("Sword/Shield save rejected: ${swsh.reason}")
                val bdsp = candidate.bdspValidation
                if (bdsp is BdspValidation.Invalid)
                    throw IllegalArgumentException("BDSP save rejected: ${bdsp.reason}")
                val arceus = candidate.legendsArceusValidation
                if (arceus is LegendsArceusValidation.Invalid)
                    throw IllegalArgumentException("Legends: Arceus save rejected: ${arceus.reason}")
                val sv = candidate.scarletVioletValidation
                if (sv is ScarletVioletValidation.Invalid)
                    throw IllegalArgumentException("Scarlet/Violet save rejected: ${sv.reason}")
                val za = candidate.legendsZaValidation
                if (za is LegendsZaValidation.Invalid)
                    throw IllegalArgumentException("Legends: Z-A save rejected: ${za.reason}")
                if (intent?.data != null) {
                    require(candidate.isFormatVerified) { "The shared source no longer has a supported native identity." }
                    val expectedFamily = intent.getStringExtra("pse.switch.family")
                    val expectedHash = intent.getStringExtra("pse.switch.sha256")
                    require(candidate.profile.family.name == expectedFamily && candidate.snapshot.sourceSha256 == expectedHash) {
                        "This source changed after selection. Reopen it from PSE to validate the fresh save."
                    }
                }
                loaded = candidate
            } catch (e: IllegalArgumentException) {
                failure = e.message ?: "The selected file cannot be inspected."
            } catch (_: Exception) {
                // A failed read never discards the previous successful snapshot.
            }
            val result = loaded
            val error = failure
            runOnUiThread {
                if (isFinishing || isDestroyed || requestVersion != version) return@runOnUiThread
                if (result == null) {
                    status.text = "$error Any previous preview is unchanged."
                } else {
                    session = result
                    selectedSourceUri = uri
                    inspectionMode = false
                    val checked = result.validation as? LetsGoValidation.Verified
                    val swsh = result.swordShieldValidation as? SwordShieldValidation.Verified
                    val bdsp = result.bdspValidation as? BdspValidation.Verified
                    val arceus = result.legendsArceusValidation as? LegendsArceusValidation.Verified
                    val sv = result.scarletVioletValidation as? ScarletVioletValidation.Verified
                    val za = result.legendsZaValidation as? LegendsZaValidation.Verified
                    val identity = if (za != null) {
                        "Detected game: ${za.version.displayName}\nFormat: native Legends: Z-A save\n" +
                            "Save layout: ${za.revision?.displayName ?: "not identified"}\n" +
                            "Container hash: passed\nBlock stream: ${za.blockCount} blocks checked"
                    } else if (sv != null) {
                        "Detected game: ${sv.version.displayName}\nFormat: native Scarlet/Violet save\n" +
                            "Container hash: passed\nBlock stream: ${sv.blockCount} blocks checked"
                    } else if (arceus != null) {
                        "Detected game: ${arceus.version.displayName}\nFormat: native Legends: Arceus save\n" +
                            "Container hash: passed\nBlock stream: ${arceus.blockCount} blocks checked"
                    } else if (bdsp != null) {
                        "Detected game: ${bdsp.version.displayName}\n" +
                            "Format: native BDSP save\nSave layout revision: ${bdsp.revision.displayName}\n" +
                            "Native whole-file MD5: passed"
                    } else if (swsh != null) {
                        "Detected game: ${swsh.version.displayName}\n" +
                            "Format: native Sword/Shield save\n" +
                            "Container hash: passed\nBlock stream: ${swsh.blockCount} blocks checked"
                    } else if (checked == null) {
                        "Format: unverified\nChecksums: not checked"
                    } else {
                        "Detected game: ${checked.version.displayName}\n" +
                            "Format: native Let's Go save\n" +
                            "Checksums: all ${checked.checkedBlocks} native blocks passed"
                    }
                    preview.text = "Selected family (hint): ${result.snapshot.family.displayName}\n" +
                        "File size: ${result.snapshot.byteCount} bytes\n" +
                        "SHA-256: ${result.snapshot.sourceSha256}\n\n" +
                        identity + "\n\n" + (result.letsGoStorage?.let {
                            "Stored PokÃ©mon: ${it.count}/1000\nParty and storage browsing available â€¢ Native nickname editing available"
                        } ?: result.swordShieldStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied box slots: ${it.occupiedBoxSlots}/960 " +
                                "(${it.unreadableBoxSlots} unreadable)\nParty and 32 boxes browsing available â€¢ Native nickname editing available"
                        } ?: result.bdspStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied box slots: ${it.occupiedBoxSlots}/1200 " +
                                "(${it.unreadableBoxSlots} unreadable)\nParty and 40 boxes browsing available â€¢ Native nickname editing available"
                        } ?: result.legendsZaStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied box slots: ${it.occupiedBoxSlots}/960 " +
                                "(${it.unreadableBoxSlots} unreadable)\nParty and 32 boxes: read-only access available"
                        } ?: result.scarletVioletStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied box slots: ${it.occupiedBoxSlots}/960 " +
                                "(${it.unreadableBoxSlots} unreadable)\nParty, 32 boxes and ride PokÃ©mon: read-only access available"
                        } ?: result.legendsArceusStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied pasture slots: ${it.occupiedPastureSlots}/960 " +
                                "(${it.unreadablePastureSlots} unreadable)\nParty and 32 pastures browsing available â€¢ Native nickname editing available"
                        } ?: "Party and storage: unavailable" +
                            (result.storageUnavailableReason?.let { "\n$it" } ?: "")) +
                        "\n\nTrainer profile: " + (if (result.swordShieldTrainer != null || result.letsGoTrainer != null || result.bdspTrainer != null || result.legendsArceusTrainer != null || result.scarletVioletTrainer != null || result.legendsZaTrainer != null) "read-only access available"
                            else "unavailable" + (result.trainerUnavailableReason?.let { "\n$it" } ?: "")) +
                        "\n\nPokÃ©dex: " + (result.legendsZaPokedex?.let { "read-only access available â€¢ ${it.sections.sumOf { section -> section.entries.size }} species â€¢ ${it.revision?.displayName ?: "Revision unknown"}" }
                            ?: result.scarletVioletPokedex?.let { "read-only access available â€¢ ${it.layout.displayName} â€¢ ${it.regions.size} regional tables" }
                            ?: result.legendsArceusPokedex?.let { "read-only Hisui research â€¢ ${it.obtainedCount}/242 obtained â€¢ ${it.reportedCount}/242 reported" }
                            ?: result.bdspPokedex?.let { "read-only access available â€¢ ${it.seenCount}/493 seen â€¢ ${it.caughtCount}/493 caught" }
                            ?: result.swordShieldPokedex?.let { "read-only access available â€¢ ${it.regions.size} native regional tables" }
                            ?: result.letsGoPokedex?.let { "read-only access available â€¢ ${it.seenCount}/153 seen â€¢ ${it.caughtCount}/153 caught" }
                            ?: ("unavailable" + (result.pokedexUnavailableReason?.let { "\n$it" } ?: ""))) +
                        "\n\nInventory: " + (result.legendsZaInventory?.let { "read-only access available â€¢ 8 pockets â€¢ ${it.recordedCount} recorded items" }
                            ?: result.scarletVioletInventory?.let { "read-only access available â€¢ 10 pockets â€¢ ${it.recordedCount} recorded items" }
                            ?: result.legendsArceusInventory?.let { "read-only access available â€¢ 4 pockets â€¢ ${it.recordedItemCount} recorded slots" }
                            ?: result.bdspInventory?.let { "read-only access available â€¢ 8 pockets â€¢ ${it.recordedCount} recorded items â€¢ ${it.unlistedRecords.size} unlisted records" }
                            ?: result.swordShieldInventory?.let { "read-only access available â€¢ 9 pockets â€¢ ${it.recordedCount} recorded slots" }
                            ?: result.letsGoInventory?.let { "read-only access available â€¢ 7 pockets â€¢ ${it.recordedCount} recorded slots" }
                            ?: ("unavailable" + (result.inventoryUnavailableReason?.let { "\n$it" } ?: "")))
                    status.text = if (za != null)
                        "${za.version.displayName} detected â€¢ Container integrity verified â€¢ Read-only."
                    else if (sv != null)
                        "${sv.version.displayName} detected â€¢ Container integrity verified â€¢ Read-only."
                    else if (arceus != null)
                        "${arceus.version.displayName} detected â€¢ Container integrity verified â€¢ Read-only."
                    else if (bdsp != null)
                        "${bdsp.version.displayName} detected â€¢ Native file integrity verified â€¢ Read-only."
                    else if (swsh != null)
                        "${swsh.version.displayName} detected â€¢ Container integrity verified â€¢ Read-only."
                    else if (checked == null)
                        "File snapshot open â€¢ Read-only. No supported native format detected."
                    else
                        "${checked.version.displayName} detected â€¢ Structure and block CRCs verified â€¢ Read-only."
                }
                setBusy(false)
                if (result != null) {
                    val section = initialSection
                    initialSection = null
                    val button = when (section) { "trainer" -> trainerButton; "party" -> partyButton; "storage" -> storageButton; "inventory" -> inventoryButton; "pokedex" -> pokedexButton; else -> null }
                    if (section == "events" && result.profile.family in setOf(
                            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE, SwitchGameFamily.SWORD_SHIELD,
                            SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL,
                            SwitchGameFamily.LEGENDS_ARCEUS,
                            SwitchGameFamily.SCARLET_VIOLET,
                            SwitchGameFamily.LEGENDS_ZA))
                        launchSwitchEvents(result)
                    else if (button?.isEnabled == true) button.performClick()
                    else if (button != null) {
                        status.text = "This read-only section is not available for the selected save. Other available sections remain accessible below."
                        refreshDashboard(false)
                    }
                }
            }
        }
    }

    private fun openSwitchParty(bound: SwitchReadSession) {
        if (session !== bound) return
        when {
            bound.legendsZaStorage != null -> showLegendsZaParty(bound)
            bound.scarletVioletStorage != null -> showScarletVioletParty(bound)
            bound.legendsArceusStorage != null -> showLegendsArceusParty(bound)
            bound.bdspStorage != null -> showBdspParty(bound)
            bound.swordShieldStorage != null -> showSwordShieldParty(bound)
            else -> showLetsGoParty(bound)
        }
    }

    private fun launchSwitchEvents(bound: SwitchReadSession) {
        val source = selectedSourceUri ?: return
        if (session !== bound) return
        val activity = when (bound.profile.family) {
            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE -> SwitchLetsGoEventActivity::class.java
            SwitchGameFamily.SWORD_SHIELD -> SwitchSwordShieldEventActivity::class.java
            SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL -> SwitchBdspEventActivity::class.java
            SwitchGameFamily.LEGENDS_ARCEUS -> SwitchLegendsArceusEventActivity::class.java
            SwitchGameFamily.SCARLET_VIOLET -> SwitchScarletVioletEventActivity::class.java
            SwitchGameFamily.LEGENDS_ZA -> SwitchLegendsZaEventActivity::class.java
            else -> return
        }
        startActivity(Intent(this, activity).setData(source)
            .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun openSwitchStorage(bound: SwitchReadSession) {
        if (session !== bound) return
        when {
            bound.legendsZaStorage != null -> showLegendsZaBox(bound, 0)
            bound.scarletVioletStorage != null -> showScarletVioletBox(bound, 0)
            bound.legendsArceusStorage != null -> showLegendsArceusPasture(bound, 0)
            bound.bdspStorage != null -> showBdspBox(bound, 0)
            bound.swordShieldStorage != null -> showSwordShieldBox(bound, 0)
            else -> showLetsGoStorage(bound, 0)
        }
    }

    private fun showSwitchPokemonBoxes(bound: SwitchReadSession) {
        if (session !== bound) return
        val nativeStorage = when (bound.profile.family) {
            SwitchGameFamily.LEGENDS_ARCEUS -> "Pastures"
            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE -> "PokÃ©mon Storage"
            else -> "PC Boxes"
        }
        inspectionMode = true
        inspectionRoot.visibility = android.view.View.GONE
        dashboardRoot.visibility = android.view.View.VISIBLE
        dashboardRoot.removeAllViews()
        val gap = (16 * resources.displayMetrics.density).toInt()
        dashboardRoot.setPadding(gap, gap * 2, gap, gap)
        dashboardRoot.setBackgroundColor(OmegaRubyUi.pageBackground)
        dashboardRoot.addView(TextView(this).apply {
            text = "PokÃ©mon Boxes"
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        dashboardRoot.addView(TextView(this).apply {
            text = "PokÃ©mon ${bound.displayName} â€¢ Viewer â€¢ Read-only"
            textSize = 14f
            setTextColor(OmegaRubyUi.mutedText)
            setPadding(0, (4 * resources.displayMetrics.density).toInt(), 0, (12 * resources.displayMetrics.density).toInt())
        })
        val back = Button(this).apply {
            text = "â†  Back to PSE"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = OmegaRubyUi.roundedCard(this@SwitchReadOnlyActivity)
            setOnClickListener {
                dashboardRoot.visibility = android.view.View.GONE
                inspectionRoot.visibility = android.view.View.VISIBLE
                inspectionMode = false
                refreshDashboard(false)
            }
        }
        dashboardRoot.addView(back, LinearLayout.LayoutParams(-1, (48 * resources.displayMetrics.density).toInt()).apply {
            bottomMargin = gap
        })
        val intro = TextView(this).apply {
            text = "Native storage: $nativeStorage"
            textSize = 14f
            setTextColor(Color.rgb(160, 180, 195))
            setPadding(0, 0, 0, gap / 2)
        }
        dashboardRoot.addView(intro)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val party = Button(this).apply {
            text = "Party"
            OmegaRubyUi.styleTab(this@SwitchReadOnlyActivity, this, selected = false)
            isEnabled = bound.supports(SwitchReadCapability.PARTY)
        }
        val boxes = Button(this).apply {
            text = "PC Boxes"
            OmegaRubyUi.styleTab(this@SwitchReadOnlyActivity, this, selected = true)
            isEnabled = bound.supports(SwitchReadCapability.PC_OR_BOXES) ||
                bound.supports(SwitchReadCapability.PASTURES)
        }
        tabs.addView(party, LinearLayout.LayoutParams(0, (52 * resources.displayMetrics.density).toInt(), 1f).apply { rightMargin = gap / 4 })
        tabs.addView(boxes, LinearLayout.LayoutParams(0, (52 * resources.displayMetrics.density).toInt(), 1f).apply { leftMargin = gap / 4 })
        dashboardRoot.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (8 * resources.displayMetrics.density).toInt()
            bottomMargin = (10 * resources.displayMetrics.density).toInt()
        })
        party.setOnClickListener {
            if (session === bound) openSwitchParty(bound)
        }
        boxes.setOnClickListener {
            if (session === bound) openSwitchStorage(bound)
        }
        // The activity shell already supplies the fixed bottom navigation used by
        // every other page; keep it outside the scrolling content.
        pageScroll.scrollTo(0, 0)
    }

    private fun slotLabel(slot: LetsGoStorageSlot): String {
        val name = when (val read = slot.read) {
            LetsGoSlotRead.Empty -> "Empty"
            is LetsGoSlotRead.Invalid -> "Unreadable PokÃ©mon â€¢ ${read.reason}"
            is LetsGoSlotRead.Pokemon -> {
                val p = read.value
                val species = LetsGoSpeciesNames.name(p.species)
                val nick = p.nickname.takeIf { it.isNotBlank() && it != species }
                (nick?.let { "$it ($species)" } ?: species) +
                    " â€¢ Cached Lv ${p.cachedLevel}" + if (p.shiny) " â€¢ Shiny" else ""
            }
        }
        return "#${slot.index + 1}  $name" +
            (if (slot.partyPositions.isEmpty()) "" else " â€¢ Party ${slot.partyPositions.joinToString()}") +
            (if (slot.partner) " â€¢ Partner pointer" else "")
    }

    private fun showLetsGoParty(bound: SwitchReadSession) {
        if (session !== bound) return
        val storage = bound.letsGoStorage ?: return
        val party = storage.party()
        val labels = party.map { "Party ${it.position}: " + (it.storage?.let { slot -> slotLabel(slot) } ?: "Empty") }
        AlertDialog.Builder(this).setTitle("${bound.displayName} â€¢ Party")
            .setItems(labels.toTypedArray()) { _, position ->
                if (session === bound) party[position].storage?.let { showLetsGoPokemon(bound, it) }
            }.setNegativeButton("Close", null).show()
    }

    private fun showLetsGoStoragePoolMap(bound: SwitchReadSession) {
        if (session !== bound || dashboardBusy) return
        val storage = bound.letsGoStorage ?: return
        fun pointer(slot: LetsGoStorageSlot?): String {
            if (slot == null) return "1001 â€¢ Empty sentinel"
            val target = when (val read = slot.read) {
                LetsGoSlotRead.Empty -> "Empty record"
                is LetsGoSlotRead.Invalid -> "Unreadable PB7 â€¢ ${read.reason}"
                is LetsGoSlotRead.Pokemon -> {
                    val pokemon = read.value
                    val species = LetsGoSpeciesNames.name(pokemon.species)
                    val nickname = pokemon.nickname.takeIf { it.isNotBlank() && it != species }
                    (nickname?.let { "$it ($species)" } ?: species) +
                        " â€¢ #${pokemon.species} â€¢ Form ${pokemon.form}"
                }
            }
            return "${slot.index} â€¢ Stored record #${slot.index + 1} â€¢ $target"
        }
        val detail = buildString {
            append("${bound.displayName} â€¢ Storage Pool Map â€¢ Read-only\n\n")
            append("Native stored-list count: ${storage.count} of 1,000\n")
            append("Next stored-list index: ${storage.count}\n")
            for (party in storage.party()) {
                append("Party ${party.position} pointer: ${pointer(party.storage)}\n")
            }
            val partner = storage.partnerIndex?.let { storage.slot(it) }
            append("Partner pointer: ${pointer(partner)}\n\n")
            append("Pointers are the native zero-based PokeListHeader values; 1001 is the native empty sentinel. ")
            append("Every nonempty pointer was required to target the current compressed stored list, and party pointers were required to be distinct. ")
            append("All 21 native block CRCs passed. Pointed PB7 records are shown as checksum-valid details or explicit parser failures without compacting the list. ")
            append("No pointer change, party reorder, partner reassignment, compression, export, replacement or save writing.")
        }
        AlertDialog.Builder(this).setTitle("Storage Pool Map â€¢ Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Close", null).show()
    }

    private fun showLetsGoStorage(bound: SwitchReadSession, page: Int) {
        if (session !== bound) return
        val storage = bound.letsGoStorage ?: return
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        list.addView(TextView(this).apply {
            text = "${storage.count}/1000 stored â€¢ Native list order â€¢ Read-only\n" +
                "Partner pointer: ${storage.partnerIndex?.let { "#${it + 1}" } ?: "None"}"
            setPadding(16, 16, 16, 16)
        })
        for (slot in storage.page(page)) list.addView(Button(this).apply {
            text = slotLabel(slot)
            isAllCaps = false
            isEnabled = slot.read !is LetsGoSlotRead.Empty
            setOnClickListener { if (session === bound) showLetsGoPokemon(bound, slot) }
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Storage â€¢ Page ${page + 1}/40")
            .setView(scroll).setNegativeButton("Close", null).create()
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(Button(this).apply {
            text = "Previous"; isEnabled = page > 0
            setOnClickListener { dialog.dismiss(); if (session === bound) showLetsGoStorage(bound, page - 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(Button(this).apply {
            text = "Next"; isEnabled = page < 39
            setOnClickListener { dialog.dismiss(); if (session === bound) showLetsGoStorage(bound, page + 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(nav)
        dialog.show()
    }

    private fun showLetsGoPokemon(bound: SwitchReadSession, slot: LetsGoStorageSlot) {
        if (session !== bound) return
        val read = slot.read
        if (read !is LetsGoSlotRead.Pokemon) {
            val reason = (read as? LetsGoSlotRead.Invalid)?.reason ?: "This slot is empty."
            AlertDialog.Builder(this).setTitle("Storage #${slot.index + 1}")
                .setMessage(reason).setPositiveButton("Close", null).show()
            return
        }
        val p = read.value
        val gender = listOf("Male", "Female", "Genderless").getOrNull(p.gender) ?: "Unknown (${p.gender})"
        val detail = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nGender: $gender\n")
            append("Cached level: ${p.cachedLevel}\nEXP: ${p.experience}\nNature ID: ${p.nature}\n")
            append("Cached HP: ${p.currentHp}/${p.maximumHp}\nShiny: ${if (p.shiny) "Yes" else "No"}\n")
            append("Egg flag: ${p.isEgg}\nPartner form: ${p.isPartnerForm}\n")
            append("Partner pointer: ${slot.partner}\nParty positions: ${slot.partyPositions.joinToString().ifBlank { "None" }}\n\n")
            append("Original trainer: ${p.originalTrainer}\nTID16: ${p.tid}\nSID16: ${p.sid}\n")
            append("PID: ${p.pid.toString(16).uppercase()}\nEncryption constant: ${p.encryptionConstant.toString(16).uppercase()}\n\n")
            append("Moves\n")
            p.moves.forEachIndexed { i, move -> append("${i + 1}. Move #${move.id} â€¢ PP ${move.pp}\n") }
            append("\nIVs (HP/Atk/Def/Speed/SpA/SpD): ${p.ivs.joinToString(" / ")}\n")
            append("Awakening values: ${p.awakened.joinToString(" / ")}\n\n")
            append("PokÃ©mon record checksum passed. Native nickname editing is available; legality is not evaluated.")
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        })
        if (!slot.partner && !p.isPartnerForm && !p.isEgg && p.form == 0 &&
            LetsGoPokemonEvolutionEditing.choices(p.species).isNotEmpty()) {
            content.addView(Button(this).apply {
                text = "Evolve PokÃ©mon"
                isAllCaps = false
                setOnClickListener {
                    val source = selectedSourceUri
                    if (session === bound && source != null) startActivity(
                        Intent(this@SwitchReadOnlyActivity, SwitchLetsGoPokemonEvolutionActivity::class.java)
                            .setData(source)
                            .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                            .putExtra("pse.switch.pokemon.index", slot.index)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            })
        }
        val edit = Button(this).apply {
            text = "Edit PokÃ©mon"
            isAllCaps = false
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && source != null) startActivity(
                    Intent(this@SwitchReadOnlyActivity, SwitchLetsGoPokemonNicknameActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.pokemon.index", slot.index)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        }
        content.addView(edit)
        content.addView(Button(this).apply {
            text = "Duplicate PokÃ©mon"
            isAllCaps = false
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && source != null) startActivity(
                    Intent(this@SwitchReadOnlyActivity, SwitchLetsGoPokemonDuplicateActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.pokemon.index", slot.index)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        })
        if (slot.partyPositions.isEmpty() && !slot.partner && !p.isPartnerForm && !p.isEgg) {
            content.addView(Button(this).apply {
                text = "Swap with PokÃ©mon in this Box"
                isAllCaps = false
                setOnClickListener {
                    val source = selectedSourceUri
                    if (session === bound && source != null) startActivity(
                        Intent(this@SwitchReadOnlyActivity, SwitchLetsGoPokemonSwapActivity::class.java)
                            .setData(source)
                            .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                            .putExtra("pse.switch.pokemon.index", slot.index)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            })
        }
        if (!slot.partner && !p.isPartnerForm && !p.isEgg) {
            content.addView(Button(this).apply {
                text = if (slot.partyPositions.isEmpty()) "Move to Party" else "Move to PC Box"
                isAllCaps = false
                setOnClickListener {
                    val source = selectedSourceUri
                    if (session === bound && source != null) startActivity(
                        Intent(this@SwitchReadOnlyActivity, SwitchLetsGoPokemonPartyMoveActivity::class.java)
                            .setData(source)
                            .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                            .putExtra("pse.switch.pokemon.index", slot.index)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            })
        }
        val bank = Button(this).apply { text = "Add to PSE Bank"; isAllCaps = false }
        content.addView(bank)
        content.addView(Button(this).apply {
            text = "Delete PokÃ©mon"
            isAllCaps = false
            setOnClickListener {
                val source = selectedSourceUri
                if (session === bound && source != null) startActivity(
                    Intent(this@SwitchReadOnlyActivity, SwitchLetsGoPokemonDeleteActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.switch.pokemon.index", slot.index)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }
        })
        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this).setTitle("Storage #${slot.index + 1} â€¢ PokÃ©mon Details")
            .setView(scroll).setNegativeButton("Close", null).create()
        dialog.setOnShowListener {
            bank.setOnClickListener {
                if (session !== bound) { dialog.dismiss(); return@setOnClickListener }
                try {
                    val raw = letsGoRecordBytes(bound, slot.index)
                    val entry = SwitchPseBankStore.depositLetsGo(this, raw)
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Added to PSE Bank")
                        .setMessage(p.nickname.ifBlank { LetsGoSpeciesNames.name(p.species) } +
                            " copied as native PB7. The Switch save was not changed.\n\n" + entry.name)
                        .setPositiveButton("Close", null).show()
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not add to PSE Bank")
                        .setMessage(error.message ?: "The save could not be read again.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
        dialog.show()
    }

    private fun letsGoRecordBytes(bound: SwitchReadSession, index: Int): ByteArray {
        val uri = selectedSourceUri ?: throw IllegalStateException("The selected save is no longer available.")
        val original = bound.snapshot.bytesCopy()
        val current = contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= 16 * 1024 * 1024) { "The selected file exceeds the 16 MiB intake limit." }
            bytes
        } ?: throw java.io.IOException("The selected save could not be reopened.")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        require(java.security.MessageDigest.isEqual(digest.digest(original), digest.digest(current))) {
            "The selected save changed after it was opened. Reopen it before adding PokÃ©mon to Bank."
        }
        return bound.snapshot.readLetsGoRecord(index)
    }

    private fun swordShieldLabel(read: SwordShieldSlotRead): String = when (read) {
        SwordShieldSlotRead.Empty -> "Empty"
        is SwordShieldSlotRead.Invalid -> "Unreadable PokÃ©mon â€¢ ${read.reason}"
        is SwordShieldSlotRead.Pokemon -> {
            val p = read.value
            val species = SwordShieldSpeciesNames.name(p.species)
            val nick = p.nickname.takeIf { it.isNotBlank() && it != species }
            (nick?.let { "$it ($species)" } ?: species) + " â€¢ Cached Lv ${p.cachedLevel}" +
                (if (p.shiny) " â€¢ Shiny" else "") + (if (p.isEgg) " â€¢ Egg" else "")
        }
    }

    private fun showSwordShieldParty(bound: SwitchReadSession) {
        if (session !== bound) return
        val storage = bound.swordShieldStorage ?: return
        val party = storage.party()
        val labels = party.map { "Party ${it.position}: ${swordShieldLabel(it.read)}" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} â€¢ Party ${storage.partyCount}/6")
            .setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showSwordShieldPokemon(bound, "Party ${party[index].position}", party[index].read)
            }.setNegativeButton("Close", null).show()
    }

    private fun showSwordShieldBox(bound: SwitchReadSession, box: Int) {
        if (session !== bound) return
        val storage = bound.swordShieldStorage ?: return
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        list.addView(TextView(this).apply {
            text = "32 native boxes â€¢ 30 slots each â€¢ Nickname editing available\n" +
                "${storage.occupiedBoxSlots}/960 occupied slots (${storage.unreadableBoxSlots} unreadable). " +
                "Box names, wallpapers and unlock state are not read in this view."
            setPadding(16, 16, 16, 16)
        })
        for (slot in storage.box(box)) list.addView(Button(this).apply {
            text = "Slot ${slot.position}: ${swordShieldLabel(slot.read)}"
            isAllCaps = false; isEnabled = slot.read !is SwordShieldSlotRead.Empty
            setOnClickListener { if (session === bound) showSwordShieldPokemon(bound, "Box ${box + 1} â€¢ Slot ${slot.position}", slot.read) }
        })
        val dialog = AlertDialog.Builder(this).setTitle("${bound.displayName} â€¢ Box ${box + 1}/32")
            .setView(scroll).setNegativeButton("Close", null).create()
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(Button(this).apply {
            text = "Previous"; isEnabled = box > 0
            setOnClickListener { dialog.dismiss(); if (session === bound) showSwordShieldBox(bound, box - 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(Button(this).apply {
            text = "Next"; isEnabled = box < 31
            setOnClickListener { dialog.dismiss(); if (session === bound) showSwordShieldBox(bound, box + 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(nav); dialog.show()
    }

    private fun showSwitchSafeBackups(bound: SwitchReadSession) {
        if (session !== bound) return
        val entries = SwitchSafeBackupStore.listVerified(this, bound.profile.family)
        val labels = entries.map { entry ->
            entry.file.name + "\n" + entry.byteCount + " bytes â€¢ SHA-256 " + entry.sha256.take(12) + "â€¦"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Safe Backups â€¢ " + bound.displayName)
            .setPositiveButton("Close", null)
            .setNeutralButton("Create verified backup") { _, _ -> createSwitchSafeBackup(bound) }
        if (entries.isEmpty()) {
            builder.setMessage("No verified backups exist for this Switch title. Opening this screen never creates a backup; use Create verified backup to copy the unchanged source after its identity and native integrity are rechecked.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showSwitchSafeBackupEntry(bound, entries[index])
            }
        }
        builder.show()
    }

    private fun createSwitchSafeBackup(bound: SwitchReadSession) {
        if (session !== bound) return
        try {
            val bytes = verifiedCurrentSwitchSource(bound)
            val entry = SwitchSafeBackupStore.createVerified(this, bound.profile.family, bytes)
            AlertDialog.Builder(this).setTitle("Verified backup created")
                .setMessage(entry.file.name + "\n\n" + entry.byteCount + " bytes\nSHA-256: " + entry.sha256 +
                    "\n\nThe selected Switch save was not changed.")
                .setPositiveButton("Back to Safe Backups") { _, _ -> if (session === bound) showSwitchSafeBackups(bound) }
                .setNegativeButton("Close", null).show()
        } catch (error: Exception) {
            AlertDialog.Builder(this).setTitle("Backup was not created")
                .setMessage((error.message ?: "The source could not be verified.") + "\n\nThe selected Switch save was not changed.")
                .setPositiveButton("Close", null).show()
        }
    }

    private fun verifiedCurrentSwitchSource(bound: SwitchReadSession): ByteArray {
        require(session === bound) { "The active save changed. Reopen Safe Backups." }
        val uri = selectedSourceUri ?: throw IllegalStateException("The selected save is no longer available.")
        val original = bound.snapshot.bytesCopy()
        val current = readSelectedSwitchSource(uri)
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        require(java.security.MessageDigest.isEqual(digest.digest(original), digest.digest(current))) {
            "The selected save changed after it was opened. Reopen it before creating a backup."
        }
        return current
    }

    private fun showSwitchSafeBackupEntry(bound: SwitchReadSession, opened: SwitchSafeBackupEntry) {
        if (session !== bound) return
        val entry = runCatching {
            SwitchSafeBackupStore.inspect(this, bound.profile.family, opened.file)
        }.getOrElse { error ->
            AlertDialog.Builder(this).setTitle("Backup could not be verified")
                .setMessage(error.message ?: "The backup was left unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val dialog = AlertDialog.Builder(this).setTitle("Safe Backup â€¢ " + bound.displayName)
            .setMessage(entry.file.name + "\n\n" + entry.byteCount + " bytes\nSHA-256: " + entry.sha256 +
                "\n\nNative title and integrity validation passed. This backup is an exact source snapshot.")
            .setPositiveButton("Restore source", null)
            .setNeutralButton("Delete backup", null)
            .setNegativeButton("Close", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                prepareSwitchSafeBackupRestore(bound, entry)
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed; the backup was left unchanged." }
                    SwitchSafeBackupStore.removeVerified(this, bound.profile.family, entry.file, entry.sha256)
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Backup deleted")
                        .setMessage(entry.file.name + " was removed. The selected Switch save was not changed.")
                        .setPositiveButton("Back to Safe Backups") { _, _ -> if (session === bound) showSwitchSafeBackups(bound) }
                        .setNegativeButton("Close", null).show()
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Backup was not deleted")
                        .setMessage(error.message ?: "The backup was left unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
        dialog.show()
    }

    private fun prepareSwitchSafeBackupRestore(bound: SwitchReadSession, entry: SwitchSafeBackupEntry) {
        if (session !== bound) return
        try {
            val family = bound.profile.family
            val current = verifiedCurrentSwitchSource(bound)
            val backup = SwitchSafeBackupStore.readVerified(this, family, entry.file, entry.sha256)
            require(!current.contentEquals(backup)) { "The selected source already matches this backup." }
            requireSelectedSwitchSourceWriteAccess(
                selectedSourceUri ?: throw IllegalStateException("The selected source is no longer available.")
            )
            val currentSha = SwitchSafeBackupStore.validateSource(family, current)
            val backupSha = SwitchSafeBackupStore.validateSource(family, backup)
            val rollback = SwitchSafeBackupStore.createVerified(this, family, current)
            val rollbackBytes = SwitchSafeBackupStore.readVerified(this, family, rollback.file, rollback.sha256)
            require(rollbackBytes.contentEquals(current)) { "The recovery snapshot does not match the current source." }

            AlertDialog.Builder(this).setTitle("Restore backup to selected source?")
                .setMessage(
                    "Before â€¢ " + current.size + " bytes\nSHA-256: " + currentSha +
                        "\n\nAfter â€¢ " + backup.size + " bytes\nSHA-256: " + backupSha +
                        "\n\nWrite scope: replace the entire selected source with exactly " + backup.size +
                        " verified backup bytes.\nRecovery snapshot: " + rollback.file.name +
                        "\n\nThe source will be reopened and validated after persistence. Any failed write will immediately restore and verify the recovery snapshot."
                )
                .setPositiveButton("Restore source") { _, _ ->
                    performSwitchSafeBackupRestore(bound, entry, current, backup, rollback)
                }
                .setNegativeButton("Cancel", null).show()
        } catch (error: Exception) {
            AlertDialog.Builder(this).setTitle("Restore is unavailable")
                .setMessage((error.message ?: "The source or backup could not be verified.") +
                    "\n\nNo source bytes were written.")
                .setPositiveButton("Close", null).show()
        }
    }

    private fun performSwitchSafeBackupRestore(
        bound: SwitchReadSession,
        entry: SwitchSafeBackupEntry,
        expectedCurrent: ByteArray,
        expectedBackup: ByteArray,
        rollback: SwitchSafeBackupEntry
    ) {
        val uri = selectedSourceUri
        var writeStarted = false
        try {
            require(session === bound && uri != null) { "The active source changed. Reopen Safe Backups." }
            val family = bound.profile.family
            val current = verifiedCurrentSwitchSource(bound)
            require(current.contentEquals(expectedCurrent)) { "The selected source changed after review." }
            val backup = SwitchSafeBackupStore.readVerified(this, family, entry.file, entry.sha256)
            require(backup.contentEquals(expectedBackup)) { "The backup changed after review." }
            val recovery = SwitchSafeBackupStore.readVerified(this, family, rollback.file, rollback.sha256)
            require(recovery.contentEquals(expectedCurrent)) { "The recovery snapshot changed after review." }

            writeSelectedSwitchSource(uri, backup) { writeStarted = true }
            val persisted = readSelectedSwitchSource(uri)
            require(persisted.contentEquals(backup)) { "The restored source did not persist byte-for-byte." }
            require(SwitchSafeBackupStore.validateSource(family, persisted) == entry.sha256) {
                "The restored source failed its expected native identity."
            }

            session = null
            selectedSourceUri = null
            AlertDialog.Builder(this).setTitle("Backup restored and verified")
                .setMessage(
                    "The selected source now matches " + entry.file.name +
                        ".\n\nPersisted SHA-256: " + entry.sha256 +
                        "\nRecovery snapshot retained: " + rollback.file.name +
                        "\n\nReturn to PSE and rescan the restored save before making another change."
                )
                .setPositiveButton("Return to PSE") { _, _ -> finish() }
                .setCancelable(false).show()
        } catch (error: Exception) {
            var rollbackVerified = false
            var rollbackFailure: String? = null
            if (writeStarted && uri != null) {
                try {
                    writeSelectedSwitchSource(uri, expectedCurrent)
                    val recovered = readSelectedSwitchSource(uri)
                    require(recovered.contentEquals(expectedCurrent)) { "Recovery bytes did not persist exactly." }
                    SwitchSafeBackupStore.validateSource(bound.profile.family, recovered)
                    rollbackVerified = true
                } catch (rollbackError: Exception) {
                    rollbackFailure = rollbackError.message ?: "Recovery verification failed."
                }
            }
            val outcome = when {
                !writeStarted -> "No source bytes were written."
                rollbackVerified -> "The failed copy was replaced with the verified recovery snapshot and reopened successfully."
                else -> "Automatic recovery could not be verified: " + rollbackFailure +
                    "\nDo not use this source; recover it from " + rollback.file.name + "."
            }
            AlertDialog.Builder(this).setTitle(if (rollbackVerified || !writeStarted) "Restore was not completed" else "Restore and recovery failed")
                .setMessage((error.message ?: "The restore could not be completed.") + "\n\n" + outcome +
                    "\nThe Safe Backup and recovery snapshot were retained.")
                .setPositiveButton("Close", null).show()
        }
    }

    private fun readSelectedSwitchSource(uri: android.net.Uri): ByteArray =
        contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().also { bytes ->
                require(bytes.isNotEmpty() && bytes.size <= SwitchReadSnapshot.MAX_SAVE_BYTES) {
                    "The selected source is empty or exceeds the 16 MiB intake limit."
                }
            }
        } ?: throw java.io.IOException("The selected source could not be reopened.")

    private fun requireSelectedSwitchSourceWriteAccess(uri: android.net.Uri) {
        contentResolver.openFileDescriptor(uri, "rw")?.use { return }
        throw java.io.IOException("The selected source does not provide write access. Re-select it and grant file access.")
    }

    private fun writeSelectedSwitchSource(
        uri: android.net.Uri,
        bytes: ByteArray,
        onWriteStarted: () -> Unit = {}
    ) {
        require(bytes.isNotEmpty() && bytes.size <= SwitchReadSnapshot.MAX_SAVE_BYTES) {
            "The restore bytes are outside the Switch save intake limit."
        }
        val descriptor = contentResolver.openFileDescriptor(uri, "rwt")
            ?: throw java.io.IOException("The selected source does not provide write access.")
        descriptor.use { opened ->
            FileOutputStream(opened.fileDescriptor).use { output ->
                onWriteStarted()
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        }
    }
    private fun showSwitchBank(bound: SwitchReadSession) {
        if (session !== bound) return
        when (bound.profile.family) {
            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE -> showLetsGoBank(bound)
            SwitchGameFamily.SWORD_SHIELD -> showSwordShieldBank(bound)
            SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL -> showBdspBank(bound)
            SwitchGameFamily.LEGENDS_ARCEUS -> showLegendsArceusBank(bound)
            SwitchGameFamily.SCARLET_VIOLET -> showScarletVioletBank(bound)
            SwitchGameFamily.LEGENDS_ZA -> showLegendsZaBank(bound)
            else -> return
        }
    }

    private fun showLetsGoBank(bound: SwitchReadSession) {
        if (session !== bound) return
        val verified = bound.snapshot.inspectLetsGo() as? LetsGoValidation.Verified
        if (verified == null) {
            AlertDialog.Builder(this).setTitle("PSE Bank unavailable")
                .setMessage("Reopen a validated native Let's Go destination save.")
                .setPositiveButton("Close", null).show()
            return
        }
        val directory = File(filesDir, "pse_bank")
        val entries = directory.listFiles()?.filter {
            it.isFile && ((it.name.startsWith("switch_lgpe_") && it.extension.equals("pb7", true)) ||
                it.extension.equals("pse1", true) ||
                it.extension.equals("pse2", true) ||
                it.extension.equals("pk3", true) ||
                it.extension.equals("pk4", true) ||
                it.extension.equals("pk5", true) ||
                it.extension.equals("pk6", true) ||
                it.extension.equals("pk7", true) ||
                (it.name.startsWith("switch_swsh_") && it.extension.equals("pk8", true)) ||
                (it.name.startsWith("switch_bdsp_") && it.extension.equals("pb8", true)) ||
                (it.name.startsWith("switch_pla_") && it.extension.equals("pa8", true)) ||
                (it.name.startsWith("switch_sv_") && it.extension.equals("pk9", true)) ||
                (it.name.startsWith("switch_za_") && it.extension.equals("pa9", true)))
        }?.sortedBy { it.name }.orEmpty()
        val labels = entries.map { file ->
            if (file.extension.equals("pse1", true)) {
                runCatching { Gen1ToSwordShieldPk8.inspectSource(file.readBytes()) }
                    .fold(
                        onSuccess = { source ->
                            source.nickname.ifBlank { LetsGoSpeciesNames.name(source.species) } +
                                " (#${source.species}) â€¢ Gen 1 PSE1 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pse2", true)) {
                runCatching { Gen2ToSwordShieldPk8.inspectSource(file.readBytes()) }
                    .fold(
                        onSuccess = { source ->
                            source.nickname.ifBlank { LetsGoSpeciesNames.name(source.species) } +
                                " (#${source.species}) â€¢ Gen 2 PSE2 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pk3", true)) {
                runCatching { Gen3ToSwordShieldPk8.inspectSource(file.readBytes()) }
                    .fold(
                        onSuccess = { source ->
                            source.nickname.ifBlank { LetsGoSpeciesNames.name(source.species) } +
                                " (#${source.species}) â€¢ Gen 3 PK3 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pk4", true)) {
                runCatching { Gen4ToSwordShieldPk8.inspectSource(file.readBytes()) }
                    .fold(
                        onSuccess = { source ->
                            source.nickname.ifBlank { LetsGoSpeciesNames.name(source.species) } +
                                " (#${source.species}) â€¢ Gen 4 PK4 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pk5", true)) {
                runCatching { Gen5ToSwordShieldPk8.inspectSource(file.readBytes()) }
                    .fold(
                        onSuccess = { source ->
                            source.nickname.ifBlank { LetsGoSpeciesNames.name(source.species) } +
                                " (#${source.species}) â€¢ Gen 5 PK5 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pk6", true)) {
                runCatching { Gen6ToLetsGoPb7.review(file.readBytes(), verified.version) }
                    .fold(
                        onSuccess = { converted ->
                            converted.sourceNickname.ifBlank { LetsGoSpeciesNames.name(converted.sourceSpecies) } +
                                " (#${converted.sourceSpecies}) â€¢ Gen 6 PK6 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pk7", true)) {
                runCatching { Gen7ToLetsGoPb7.review(file.readBytes(), verified.version) }
                    .fold(
                        onSuccess = { converted ->
                            converted.sourceNickname.ifBlank { LetsGoSpeciesNames.name(converted.sourceSpecies) } +
                                " (#${converted.sourceSpecies}) â€¢ Gen 7 PK7 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pb7", true)) {
                val parsed = runCatching { LetsGoPokemonReadOnly.read(file.readBytes()) }.getOrNull()
                val pokemon = (parsed as? LetsGoSlotRead.Pokemon)?.value
                if (pokemon == null) file.name + " â€¢ unreadable"
                else pokemon.nickname.ifBlank { LetsGoSpeciesNames.name(pokemon.species) } +
                    " (#" + pokemon.species + ")" + (if (pokemon.shiny) " â€¢ Shiny" else "") + " â€¢ PB7"
            } else if (file.extension.equals("pk8", true)) {
                runCatching { SwordShieldToLetsGoPb7.review(file.readBytes(), verified.version) }
                    .fold(
                        onSuccess = { converted ->
                            converted.sourceNickname.ifBlank { LetsGoSpeciesNames.name(converted.sourceSpecies) } +
                                " (#${converted.sourceSpecies}) â€¢ Sword/Shield PK8 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pb8", true)) {
                runCatching { BdspToLetsGoPb7.review(file.readBytes(), verified.version) }
                    .fold(
                        onSuccess = { converted ->
                            converted.sourceNickname.ifBlank { LetsGoSpeciesNames.name(converted.sourceSpecies) } +
                                " (#${converted.sourceSpecies}) â€¢ BDSP PB8 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pa8", true)) {
                runCatching { LegendsArceusToLetsGoPb7.review(file.readBytes(), verified.version) }
                    .fold(
                        onSuccess = { converted ->
                            converted.sourceNickname.ifBlank { LetsGoSpeciesNames.name(converted.sourceSpecies) } +
                                " (#${converted.sourceSpecies}) â€¢ Legends: Arceus PA8 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else if (file.extension.equals("pk9", true)) {
                runCatching { ScarletVioletToLetsGoPb7.review(file.readBytes(), verified.version) }
                    .fold(
                        onSuccess = { converted ->
                            converted.sourceNickname.ifBlank { LetsGoSpeciesNames.name(converted.sourceSpecies) } +
                                " (#${converted.sourceSpecies}) â€¢ Scarlet/Violet PK9 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            } else {
                runCatching { LegendsZaToLetsGoPb7.review(file.readBytes(), verified.version) }
                    .fold(
                        onSuccess = { converted ->
                            converted.sourceNickname.ifBlank { LetsGoSpeciesNames.name(converted.sourceSpecies) } +
                                " (#${converted.sourceSpecies}) â€¢ Z-A PA9 -> ${verified.version.displayName} PB7 â€¢ Legalise"
                        },
                        onFailure = { file.name + " â€¢ blocked for ${verified.version.displayName}" }
                    )
            }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Bank â€¢ " + entries.size + " entries for ${verified.version.displayName}")
            .setPositiveButton("Close", null)
        if (entries.isEmpty()) {
            builder.setMessage("No eligible Gen 1 PSE1, Gen 2 PSE2, Gen 3 PK3, Gen 4 PK4, Gen 5 PK5, Gen 6 PK6, Gen 7 PK7, native Let's Go PB7, Sword/Shield PK8, BDSP PB8, Legends: Arceus PA8, Scarlet/Violet PK9, or Legends: Z-A PA9 entries are stored. Open PokÃ©mon Boxes, choose a PokÃ©mon, then tap Add to PSE Bank.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) {
                    val selected = entries[index]
                    when {
                        selected.extension.equals("pse1", true) ->
                            showLetsGoGen1BankEntry(bound, selected, verified.version)
                        selected.extension.equals("pse2", true) ->
                            showLetsGoGen2BankEntry(bound, selected, verified.version)
                        selected.extension.equals("pk3", true) ->
                            showLetsGoGen3BankEntry(bound, selected, verified.version)
                        selected.extension.equals("pk4", true) ->
                            showLetsGoGen4BankEntry(bound, selected, verified.version)
                        selected.extension.equals("pk5", true) ->
                            showLetsGoGen5BankEntry(bound, selected, verified.version)
                        selected.extension.equals("pk6", true) ->
                            showLetsGoGen6BankEntry(bound, selected, verified.version)
                        selected.extension.equals("pk7", true) ->
                            showLetsGoGen7BankEntry(bound, selected, verified.version)
                        selected.extension.equals("pk8", true) ->
                            showLetsGoSwordShieldBankEntry(bound, selected, verified.version)
                        selected.extension.equals("pb8", true) ->
                            showLetsGoBdspBankEntry(bound, selected, verified.version)
                        selected.extension.equals("pa8", true) ->
                            showLetsGoLegendsArceusBankEntry(bound, selected, verified.version)
                        selected.extension.equals("pk9", true) ->
                            showLetsGoScarletVioletBankEntry(bound, selected, verified.version)
                        selected.extension.equals("pa9", true) ->
                            showLetsGoLegendsZaBankEntry(bound, selected, verified.version)
                        else -> showLetsGoBankEntry(bound, selected)
                    }
                }
            }
        }
        builder.show()
    }

    private fun showLetsGoGen1BankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pse1", true)) {
                "This is not a current Generation 1 PSE Bank entry."
            }
            val bank = entry.readBytes()
            val source = Gen1ToSwordShieldPk8.inspectSource(bank)
            val destination = bound.snapshot.bytesCopy()
            val trainer = LetsGoTrainerReadOnly.read(destination)
            require(trainer.version == destinationVersion && trainer.languageId == 2) {
                "This bounded route requires the opened English ${destinationVersion.displayName} trainer."
            }
            Triple(bank, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 1 -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PSE1 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LetsGoPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 1 -> ${destinationVersion.displayName}")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PB7 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen1SwordShieldMetadata(
                    opened.second.species
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LetsGoPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Generation 1 Bank entry changed during review."
                }
                metadata to Gen1ToLetsGoPb7.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
                        append("Source: ${selected.name} â€¢ preserved validated 63-byte native PSE1\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Explicit destination: ${destinationVersion.displayName}\n")
                        append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append(
                            "\n\nContinue only if you accept every disclosed Legalise effect. " +
                                "This cross-title PSE conversion does not claim an official Time Capsule, " +
                                "PokÃ© Transporter, Bank or HOME transfer, trade, encounter record, legality " +
                                "certification, or provenance."
                        )
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 1 -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pse1", true)) {
                                    "The Generation 1 Bank entry changed."
                                }
                                val currentBank = selected.readBytes()
                                require(LetsGoPseBankRestoreEditing.sha256(currentBank) == openedHash) {
                                    "The PSE Bank entry changed. Reopen PSE Bank."
                                }
                                val destination = readSelectedSwitchSource(source)
                                require(LetsGoPseBankRestoreEditing.sha256(destination) ==
                                    bound.snapshot.sourceSha256) {
                                    "The Let's Go source changed. Reopen it from PSE."
                                }
                                val trainer = LetsGoTrainerReadOnly.read(destination)
                                require(trainer.version == destinationVersion) {
                                    "The explicit destination title changed. Reopen PSE Bank."
                                }
                                Gen1ToLetsGoPb7.review(currentBank, trainer, metadata)
                                startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen1.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen1.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen1.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen1.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this).setTitle("Could not prepare Gen 1 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 1 -> ${destinationVersion.displayName} blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PSE1 Bank entry and Let's Go save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLetsGoGen2BankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pse2", true)) {
                "This is not a current Generation 2 PSE Bank entry."
            }
            val bank = entry.readBytes()
            val source = Gen2ToSwordShieldPk8.inspectSource(bank)
            val destination = bound.snapshot.bytesCopy()
            val trainer = LetsGoTrainerReadOnly.read(destination)
            require(trainer.version == destinationVersion && trainer.languageId == 2) {
                "This bounded route requires the opened English ${destinationVersion.displayName} trainer."
            }
            Triple(bank, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 2 -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PSE2 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LetsGoPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 2 -> ${destinationVersion.displayName}")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PB7 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen1SwordShieldMetadata(
                    opened.second.species
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LetsGoPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Generation 2 Bank entry changed during review."
                }
                metadata to Gen2ToLetsGoPb7.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
                        append("Source: ${selected.name} â€¢ preserved validated 62-byte native PSE2\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Explicit destination: ${destinationVersion.displayName}\n")
                        append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append(
                            "\n\nContinue only if you accept every disclosed Legalise effect. " +
                                "This cross-title PSE conversion does not claim an official Time Capsule, " +
                                "PokÃ© Transfer, Bank or HOME transfer, trade, encounter record, legality " +
                                "certification, or provenance."
                        )
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 2 -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pse2", true)) {
                                    "The Generation 2 Bank entry changed."
                                }
                                val currentBank = selected.readBytes()
                                require(LetsGoPseBankRestoreEditing.sha256(currentBank) == openedHash) {
                                    "The PSE Bank entry changed. Reopen PSE Bank."
                                }
                                val destination = readSelectedSwitchSource(source)
                                require(LetsGoPseBankRestoreEditing.sha256(destination) ==
                                    bound.snapshot.sourceSha256) {
                                    "The Let's Go source changed. Reopen it from PSE."
                                }
                                val trainer = LetsGoTrainerReadOnly.read(destination)
                                require(trainer.version == destinationVersion) {
                                    "The explicit destination title changed. Reopen PSE Bank."
                                }
                                Gen2ToLetsGoPb7.review(currentBank, trainer, metadata)
                                startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen2.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen2.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen2.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen2.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this).setTitle("Could not prepare Gen 2 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 2 -> ${destinationVersion.displayName} blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PSE2 Bank entry and Let's Go save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLetsGoGen3BankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk3", true)) {
                "This is not a current Generation 3 PSE Bank entry."
            }
            val bank = entry.readBytes()
            val source = Gen3ToSwordShieldPk8.inspectSource(bank)
            val destination = bound.snapshot.bytesCopy()
            val trainer = LetsGoTrainerReadOnly.read(destination)
            require(trainer.version == destinationVersion && trainer.languageId == 2) {
                "This bounded route requires the opened English ${destinationVersion.displayName} trainer."
            }
            Triple(bank, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 3 -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK3 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LetsGoPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 3 -> ${destinationVersion.displayName}")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PB7 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen3SwordShieldMetadata(
                    opened.second.species, opened.second.abilitySlot
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LetsGoPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Generation 3 Bank entry changed during review."
                }
                metadata to Gen3ToLetsGoPb7.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
                        append("Source: ${selected.name} â€¢ preserved checksum-valid 80-byte native PK3\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Explicit destination: ${destinationVersion.displayName}\n")
                        append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append(
                            "\n\nContinue only if you accept every disclosed Legalise effect. " +
                                "This backward cross-title PSE conversion does not claim an official Pal Park, " +
                                "PokÃ© Transfer, Bank or HOME transfer, trade, encounter record, legality " +
                                "certification, or provenance."
                        )
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 3 -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk3", true)) {
                                    "The Generation 3 Bank entry changed."
                                }
                                val currentBank = selected.readBytes()
                                require(LetsGoPseBankRestoreEditing.sha256(currentBank) == openedHash) {
                                    "The PSE Bank entry changed. Reopen PSE Bank."
                                }
                                val destination = readSelectedSwitchSource(source)
                                require(LetsGoPseBankRestoreEditing.sha256(destination) ==
                                    bound.snapshot.sourceSha256) {
                                    "The Let's Go source changed. Reopen it from PSE."
                                }
                                val trainer = LetsGoTrainerReadOnly.read(destination)
                                require(trainer.version == destinationVersion) {
                                    "The explicit destination title changed. Reopen PSE Bank."
                                }
                                Gen3ToLetsGoPb7.review(currentBank, trainer, metadata)
                                startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen3.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen3.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen3.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen3.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this).setTitle("Could not prepare Gen 3 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 3 -> ${destinationVersion.displayName} blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK3 Bank entry and Let's Go save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLetsGoGen4BankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk4", true)) {
                "This is not a current Generation 4 PSE Bank entry."
            }
            val bank = entry.readBytes()
            val source = Gen4ToSwordShieldPk8.inspectSource(bank)
            val destination = bound.snapshot.bytesCopy()
            val trainer = LetsGoTrainerReadOnly.read(destination)
            require(trainer.version == destinationVersion && trainer.languageId == 2) {
                "This bounded route requires the opened English ${destinationVersion.displayName} trainer."
            }
            Triple(bank, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 4 -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK4 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LetsGoPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 4 -> ${destinationVersion.displayName}")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PB7 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen4SwordShieldMetadata(
                    opened.second.species, opened.second.abilitySlot, opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LetsGoPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Generation 4 Bank entry changed during review."
                }
                metadata to Gen4ToLetsGoPb7.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
                        append("Source: ${selected.name} â€¢ preserved checksum-valid 136-byte native PK4\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Explicit destination: ${destinationVersion.displayName}\n")
                        append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append(
                            "\n\nContinue only if you accept every disclosed Legalise effect. " +
                                "This cross-title PSE conversion does not claim an official PokÃ© Transfer, " +
                                "Bank or HOME transfer, trade, encounter record, legality certification, or provenance."
                        )
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 4 -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk4", true)) {
                                    "The Generation 4 Bank entry changed."
                                }
                                val currentBank = selected.readBytes()
                                require(LetsGoPseBankRestoreEditing.sha256(currentBank) == openedHash) {
                                    "The PSE Bank entry changed. Reopen PSE Bank."
                                }
                                val destination = readSelectedSwitchSource(source)
                                require(LetsGoPseBankRestoreEditing.sha256(destination) ==
                                    bound.snapshot.sourceSha256) {
                                    "The Let's Go source changed. Reopen it from PSE."
                                }
                                val trainer = LetsGoTrainerReadOnly.read(destination)
                                require(trainer.version == destinationVersion) {
                                    "The explicit destination title changed. Reopen PSE Bank."
                                }
                                Gen4ToLetsGoPb7.review(currentBank, trainer, metadata)
                                startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen4.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen4.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen4.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen4.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this).setTitle("Could not prepare Gen 4 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 4 -> ${destinationVersion.displayName} blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK4 Bank entry and Let's Go save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLetsGoGen5BankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk5", true)) {
                "This is not a current Generation 5 PSE Bank entry."
            }
            val bank = entry.readBytes()
            val source = Gen5ToSwordShieldPk8.inspectSource(bank)
            val destination = bound.snapshot.bytesCopy()
            val trainer = LetsGoTrainerReadOnly.read(destination)
            require(trainer.version == destinationVersion && trainer.languageId == 2) {
                "This bounded route requires the opened English ${destinationVersion.displayName} trainer."
            }
            Triple(bank, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 5 -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK5 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LetsGoPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 5 -> ${destinationVersion.displayName}")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PB7 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen5SwordShieldMetadata(
                    opened.second.species, opened.second.abilitySlot, opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LetsGoPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Generation 5 Bank entry changed during review."
                }
                metadata to Gen5ToLetsGoPb7.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
                        append("Source: ${selected.name} â€¢ preserved checksum-valid 136-byte native PK5\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Explicit destination: ${destinationVersion.displayName}\n")
                        append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append(
                            "\n\nContinue only if you accept every disclosed Legalise effect. " +
                                "This cross-title PSE conversion does not claim an official PokÃ© Transfer, " +
                                "Bank or HOME transfer, trade, encounter record, legality certification, or provenance."
                        )
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 5 -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk5", true)) {
                                    "The Generation 5 Bank entry changed."
                                }
                                val currentBank = selected.readBytes()
                                require(LetsGoPseBankRestoreEditing.sha256(currentBank) == openedHash) {
                                    "The PSE Bank entry changed. Reopen PSE Bank."
                                }
                                val destination = readSelectedSwitchSource(source)
                                require(LetsGoPseBankRestoreEditing.sha256(destination) ==
                                    bound.snapshot.sourceSha256) {
                                    "The Let's Go source changed. Reopen it from PSE."
                                }
                                val trainer = LetsGoTrainerReadOnly.read(destination)
                                require(trainer.version == destinationVersion) {
                                    "The explicit destination title changed. Reopen PSE Bank."
                                }
                                Gen5ToLetsGoPb7.review(currentBank, trainer, metadata)
                                startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen5.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen5.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen5.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen5.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this).setTitle("Could not prepare Gen 5 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 5 -> ${destinationVersion.displayName} blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK5 Bank entry and Let's Go save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLetsGoGen6BankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk6", true)) {
                "This is not a current Generation 6 PSE Bank entry."
            }
            val bank = entry.readBytes()
            val hash = LetsGoPseBankRestoreEditing.sha256(bank)
            hash to Gen6ToLetsGoPb7.review(bank, destinationVersion)
        }
        val (openedHash, converted) = opened.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 6 -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK6 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved checksum-valid 232-byte native PK6\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Explicit destination: ${destinationVersion.displayName}\n")
            append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This cross-title PSE conversion does not claim an official Bank, Transporter or HOME " +
                    "transfer, trade, encounter record, legality certification, or provenance."
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Gen 6 -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val current = bound.snapshot.inspectLetsGo() as? LetsGoValidation.Verified
                        ?: error("Reopen a validated native Let's Go destination.")
                    require(current.version == destinationVersion) {
                        "The explicit destination title changed. Reopen PSE Bank."
                    }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                    require(selected.parentFile == directory && selected.isFile &&
                        selected.extension.equals("pk6", true)) {
                        "The Generation 6 Bank entry changed."
                    }
                    val bank = selected.readBytes()
                    val hash = LetsGoPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash) {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    Gen6ToLetsGoPb7.review(bank, destinationVersion)
                    startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Gen 6 conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLetsGoGen7BankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk7", true)) {
                "This is not a current Generation 7 PSE Bank entry."
            }
            val bank = entry.readBytes()
            val hash = LetsGoPseBankRestoreEditing.sha256(bank)
            hash to Gen7ToLetsGoPb7.review(bank, destinationVersion)
        }
        val (openedHash, converted) = opened.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 7 -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK7 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved checksum-valid 232-byte native PK7\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Explicit destination: ${destinationVersion.displayName}\n")
            append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This cross-title PSE conversion does not claim an official Bank, Transporter or HOME " +
                    "transfer, trade, encounter record, legality certification, or provenance."
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Gen 7 -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val current = bound.snapshot.inspectLetsGo() as? LetsGoValidation.Verified
                        ?: error("Reopen a validated native Let's Go destination.")
                    require(current.version == destinationVersion) {
                        "The explicit destination title changed. Reopen PSE Bank."
                    }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                    require(selected.parentFile == directory && selected.isFile &&
                        selected.extension.equals("pk7", true)) {
                        "The Generation 7 Bank entry changed."
                    }
                    val bank = selected.readBytes()
                    val hash = LetsGoPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash) {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    Gen7ToLetsGoPb7.review(bank, destinationVersion)
                    startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Gen 7 conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLetsGoBdspBankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("switch_bdsp_") && entry.extension.equals("pb8", true)) {
                "This is not a current BDSP PSE Bank entry."
            }
            val bank = entry.readBytes()
            val hash = LetsGoPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_bdsp_${hash}.pb8") {
                "The PSE Bank filename no longer matches its native PB8 bytes."
            }
            hash to BdspToLetsGoPb7.review(bank, destinationVersion)
        }
        val (openedHash, converted) = result.getOrElse {
            AlertDialog.Builder(this).setTitle("BDSP -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PB8 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved checksum-valid 344-byte native PB8\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Explicit destination: ${destinationVersion.displayName}\n")
            append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, " +
                    "trade, encounter record, legality certification, or provenance."
            )
        }
        AlertDialog.Builder(this)
            .setTitle("BDSP -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val current = bound.snapshot.inspectLetsGo() as? LetsGoValidation.Verified
                        ?: error("Reopen a validated native Let's Go destination.")
                    require(current.version == destinationVersion) {
                        "The explicit destination title changed. Reopen PSE Bank."
                    }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                    require(selected.parentFile == directory && selected.isFile &&
                        selected.name.startsWith("switch_bdsp_") &&
                        selected.extension.equals("pb8", true)) {
                        "The BDSP Bank entry changed."
                    }
                    val bank = selected.readBytes()
                    val hash = LetsGoPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_bdsp_${hash}.pb8") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    BdspToLetsGoPb7.review(bank, destinationVersion)
                    startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare BDSP conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLetsGoSwordShieldBankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("switch_swsh_") && entry.extension.equals("pk8", true)) {
                "This is not a current Sword/Shield PSE Bank entry."
            }
            val bank = entry.readBytes()
            val hash = LetsGoPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_swsh_${hash}.pk8") {
                "The PSE Bank filename no longer matches its native PK8 bytes."
            }
            hash to SwordShieldToLetsGoPb7.review(bank, destinationVersion)
        }
        val (openedHash, converted) = result.getOrElse {
            AlertDialog.Builder(this).setTitle("Sword/Shield -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK8 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved checksum-valid 344-byte native PK8\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Explicit destination: ${destinationVersion.displayName}\n")
            append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, " +
                    "trade, encounter record, legality certification, or provenance."
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Sword/Shield -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val current = bound.snapshot.inspectLetsGo() as? LetsGoValidation.Verified
                        ?: error("Reopen a validated native Let's Go destination.")
                    require(current.version == destinationVersion) {
                        "The explicit destination title changed. Reopen PSE Bank."
                    }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                    require(selected.parentFile == directory && selected.isFile &&
                        selected.name.startsWith("switch_swsh_") &&
                        selected.extension.equals("pk8", true)) {
                        "The Sword/Shield Bank entry changed."
                    }
                    val bank = selected.readBytes()
                    val hash = LetsGoPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_swsh_${hash}.pk8") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    SwordShieldToLetsGoPb7.review(bank, destinationVersion)
                    startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Sword/Shield conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLetsGoBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = file.canonicalFile
        val parsed = runCatching {
            require(entry.parentFile == directory && entry.name.startsWith("switch_lgpe_") && entry.extension == "pb7") {
                "This is not a Let's Go Bank entry."
            }
            LetsGoPokemonReadOnly.read(entry.readBytes())
        }.getOrElse { LetsGoSlotRead.Invalid(it.message ?: "The Bank entry could not be read.") }
        if (parsed !is LetsGoSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle("Unreadable PSE Bank entry")
                .setMessage((parsed as? LetsGoSlotRead.Invalid)?.reason ?: "The Bank entry is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = parsed.value
        val detail = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nCached level: ${p.cachedLevel}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"}\nOriginal trainer: ${p.originalTrainer}\n\n")
            append("Native PB7 checksum passed. Restore writes the exact Bank record only to a reviewed new save copy; the active source and Bank entry remain unchanged. Removing this Bank copy never changes the Switch save.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("PSE Bank â€¢ Let's Go")
            .setMessage(detail).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Restore to new save copy",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri ?: error("Reopen the selected Let's Go save from PSE.")
                    require(entry.parentFile == directory && entry.name.startsWith("switch_lgpe_") && entry.extension == "pb7") {
                        "This is not a current Let's Go Bank entry."
                    }
                    val bank = entry.readBytes()
                    require(LetsGoPokemonReadOnly.read(bank) is LetsGoSlotRead.Pokemon) {
                        "The Bank record is invalid and cannot be restored."
                    }
                    val bankHash = LetsGoPseBankRestoreEditing.sha256(bank)
                    require(entry.name == "switch_lgpe_${bankHash}.pb7") {
                        "The Bank entry changed. Reopen PSE Bank."
                    }
                    dialog.dismiss()
                    startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java).setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", entry.name)
                        .putExtra("pse.bank.sha256", bankHash)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Bank restore")
                        .setMessage(error.message ?: "The source and Bank entry were left unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed; the Bank entry was left unchanged." }
                    require(entry.parentFile == directory && entry.name.startsWith("switch_lgpe_") && entry.extension == "pb7") {
                        "This is not a removable Let's Go Bank entry."
                    }
                    require(LetsGoPokemonReadOnly.read(entry.readBytes()) is LetsGoSlotRead.Pokemon) {
                        "The Bank record is invalid; it was left unchanged."
                    }
                    require(entry.delete()) { "The Bank entry could not be removed." }
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Removed from PSE Bank")
                        .setMessage(entry.name + " was removed. The Switch save was not changed.")
                        .setPositiveButton("Back to Bank") { _, _ -> if (session === bound) showLetsGoBank(bound) }
                        .setNegativeButton("Close", null).show()
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not remove Bank entry")
                        .setMessage(error.message ?: "The entry was left unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
        dialog.show()
    }

    private fun showLetsGoScarletVioletBankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("switch_sv_") && entry.extension.equals("pk9", true)) {
                "This is not a current Scarlet/Violet PSE Bank entry."
            }
            val bank = entry.readBytes()
            val hash = LetsGoPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_sv_${hash}.pk9") {
                "The PSE Bank filename no longer matches its native PK9 bytes."
            }
            hash to ScarletVioletToLetsGoPb7.review(bank, destinationVersion)
        }
        val (openedHash, converted) = opened.getOrElse {
            AlertDialog.Builder(this).setTitle("Scarlet/Violet -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK9 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved checksum-valid 344-byte native PK9\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Explicit destination: ${destinationVersion.displayName}\n")
            append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, " +
                    "trade, encounter record, legality certification, or provenance."
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Scarlet/Violet -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val current = bound.snapshot.inspectLetsGo() as? LetsGoValidation.Verified
                        ?: error("Reopen a validated native Let's Go destination.")
                    require(current.version == destinationVersion) {
                        "The explicit destination title changed. Reopen PSE Bank."
                    }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                    require(selected.parentFile == directory && selected.isFile &&
                        selected.name.startsWith("switch_sv_") &&
                        selected.extension.equals("pk9", true)) {
                        "The Scarlet/Violet Bank entry changed."
                    }
                    val bank = selected.readBytes()
                    val hash = LetsGoPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_sv_${hash}.pk9") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    ScarletVioletToLetsGoPb7.review(bank, destinationVersion)
                    startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Scarlet/Violet conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLetsGoLegendsArceusBankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("switch_pla_") && entry.extension.equals("pa8", true)) {
                "This is not a current Legends: Arceus PSE Bank entry."
            }
            val bank = entry.readBytes()
            val hash = LetsGoPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_pla_${hash}.pa8") {
                "The PSE Bank filename no longer matches its native PA8 bytes."
            }
            hash to LegendsArceusToLetsGoPb7.review(bank, destinationVersion)
        }
        val (openedHash, converted) = opened.getOrElse {
            AlertDialog.Builder(this).setTitle("Legends: Arceus -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PA8 Bank entry and Legends: Arceus source remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved checksum-valid ${selected.length()}-byte native PA8\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Explicit destination: ${destinationVersion.displayName}\n")
            append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, " +
                    "trade, encounter record, legality certification, or provenance."
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Legends: Arceus -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val current = bound.snapshot.inspectLetsGo() as? LetsGoValidation.Verified
                        ?: error("Reopen a validated native Let's Go destination.")
                    require(current.version == destinationVersion) {
                        "The explicit destination title changed. Reopen PSE Bank."
                    }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                    require(selected.parentFile == directory && selected.isFile &&
                        selected.name.startsWith("switch_pla_") &&
                        selected.extension.equals("pa8", true)) {
                        "The Legends: Arceus Bank entry changed."
                    }
                    val bank = selected.readBytes()
                    val hash = LetsGoPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_pla_${hash}.pa8") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    LegendsArceusToLetsGoPb7.review(bank, destinationVersion)
                    startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Legends: Arceus conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLetsGoLegendsZaBankEntry(
        bound: SwitchReadSession,
        file: File,
        destinationVersion: LetsGoVersion
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("switch_za_") && entry.extension.equals("pa9", true)) {
                "This is not a current Legends: Z-A PSE Bank entry."
            }
            val bank = entry.readBytes()
            val hash = LetsGoPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_za_${hash}.pa9") {
                "The PSE Bank filename no longer matches its native PA9 bytes."
            }
            LegendsZaToLetsGoPb7.review(bank, destinationVersion)
        }
        val converted = result.getOrElse {
            AlertDialog.Builder(this).setTitle("Legends: Z-A -> ${destinationVersion.displayName} blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PA9 Bank entry and Let's Go save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LetsGoPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved 344-byte PA9\n")
            append("Explicit destination: ${destinationVersion.displayName}\n")
            append("Prepared destination: checksum-valid 260-byte native PB7\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, trade, encounter record, or provenance."
            )
        }
        AlertDialog.Builder(this)
            .setTitle("Legends: Z-A -> ${destinationVersion.displayName} â€¢ PSE Legalise review")
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val current = bound.snapshot.inspectLetsGo() as? LetsGoValidation.Verified
                        ?: error("Reopen a validated native Let's Go destination.")
                    require(current.version == destinationVersion) {
                        "The explicit destination title changed. Reopen PSE Bank."
                    }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected ${destinationVersion.displayName} save from PSE.")
                    require(selected.parentFile == directory && selected.isFile &&
                        selected.name.startsWith("switch_za_") &&
                        selected.extension.equals("pa9", true)) {
                        "The Legends: Z-A Bank entry changed."
                    }
                    val bank = selected.readBytes()
                    val hash = LetsGoPseBankRestoreEditing.sha256(bank)
                    require(selected.name == "switch_za_${hash}.pa9") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    LegendsZaToLetsGoPb7.review(bank, destinationVersion)
                    startActivity(Intent(this, SwitchLetsGoPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", hash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Legends: Z-A conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showBdspBank(bound: SwitchReadSession) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank")
        val entries = directory.listFiles()?.filter {
            it.isFile && ((it.name.startsWith("switch_bdsp_") && it.extension.equals("pb8", true)) ||
                (it.name.startsWith("pse1_") && it.extension.equals("pse1", true)) ||
                it.extension.equals("pse2", true) ||
                it.extension.equals("pk3", true) ||
                it.extension.equals("pk4", true) ||
                it.extension.equals("pk5", true) ||
                it.extension.equals("pk6", true) ||
                it.extension.equals("pk7", true) ||
                (it.name.startsWith("switch_lgpe_") && it.extension.equals("pb7", true)) ||
                (it.name.startsWith("switch_swsh_") && it.extension.equals("pk8", true)) ||
                (it.name.startsWith("switch_pla_") && it.extension.equals("pa8", true)) ||
                (it.name.startsWith("switch_sv_") && it.extension.equals("pk9", true)) ||
                (it.name.startsWith("switch_za_") && it.extension.equals("pa9", true)))
        }?.sortedWith(compareBy<File>({
            when {
                it.extension.equals("pb8", true) -> 0
                it.extension.equals("pse1", true) -> 1
                it.extension.equals("pse2", true) -> 2
                it.extension.equals("pk3", true) -> 3
                it.extension.equals("pk4", true) -> 4
                it.extension.equals("pk5", true) -> 5
                it.extension.equals("pk6", true) -> 6
                it.extension.equals("pk7", true) -> 7
                it.extension.equals("pb7", true) -> 8
                it.extension.equals("pk8", true) -> 9
                it.extension.equals("pa8", true) -> 10
                it.extension.equals("pk9", true) -> 11
                else -> 12
            }
        }, { it.name }))
            .orEmpty()
        val labels = entries.map { file ->
            if (file.extension.equals("pse1", true)) {
                runCatching { Gen1ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank { SwordShieldSpeciesNames.name(source.species) } +
                            " (#" + source.species + ") â€¢ Gen 1 PSE1 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 1 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pse2", true)) {
                runCatching { Gen2ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank { SwordShieldSpeciesNames.name(source.species) } +
                            " (#" + source.species + ") â€¢ Gen 2 PSE2 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 2 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk3", true)) {
                runCatching { Gen3ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank { SwordShieldSpeciesNames.name(source.species) } +
                            " (#" + source.species + ") â€¢ Gen 3 PK3 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 3 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk4", true)) {
                runCatching { Gen4ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank { SwordShieldSpeciesNames.name(source.species) } +
                            " (#" + source.species + ") â€¢ Gen 4 PK4 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 4 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk5", true)) {
                runCatching { Gen5ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank { SwordShieldSpeciesNames.name(source.species) } +
                            " (#" + source.species + ") â€¢ Gen 5 PK5 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 5 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk6", true)) {
                runCatching { Gen6ToBdspPb8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank { SwordShieldSpeciesNames.name(source.species) } +
                            " (#" + source.species + ") â€¢ Gen 6 PK6 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 6 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk7", true)) {
                val converted = runCatching { Gen7ToBdspPb8.review(file.readBytes()) }
                converted.fold(
                    onSuccess = {
                        val p = BdspPseBankRestoreEditing.inspectBankRecord(it.nativeRecord)
                        p.nickname.ifBlank { SwordShieldSpeciesNames.name(p.species) } +
                            " (#" + p.species + ") â€¢ Gen 7 PK7 -> PB8 Legalise"
                    },
                    onFailure = { file.name + " â€¢ Gen 7 route blocked â€¢ " + (it.message ?: "unreadable") }
                )
            } else if (file.extension.equals("pb7", true) &&
                file.name.startsWith("switch_lgpe_")) {
                runCatching { LetsGoToBdspPb8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank {
                            SwordShieldSpeciesNames.name(review.sourceSpecies)
                        } + " (#" + review.sourceSpecies + ") â€¢ Let's Go PB7 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Let's Go route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk8", true)) {
                runCatching { SwordShieldToBdspPb8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank { SwordShieldSpeciesNames.name(review.sourceSpecies) } +
                            " (#" + review.sourceSpecies + ") â€¢ Sword/Shield PK8 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Sword/Shield route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pa8", true)) {
                runCatching { LegendsArceusToBdspPb8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank { SwordShieldSpeciesNames.name(review.sourceSpecies) } +
                            " (#" + review.sourceSpecies + ") â€¢ Legends: Arceus PA8 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Legends: Arceus route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk9", true)) {
                runCatching { ScarletVioletToBdspPb8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank { SwordShieldSpeciesNames.name(review.sourceSpecies) } +
                            " (#" + review.sourceSpecies + ") â€¢ Scarlet/Violet PK9 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Scarlet/Violet route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pa9", true)) {
                runCatching { LegendsZaToBdspPb8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank { SwordShieldSpeciesNames.name(review.sourceSpecies) } +
                            " (#" + review.sourceSpecies + ") â€¢ Legends: Z-A PA9 -> PB8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Legends: Z-A route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else {
                val parsed = runCatching { BdspPokemonReadOnly.read(file.readBytes()) }.getOrNull()
                val pokemon = (parsed as? BdspSlotRead.Pokemon)?.value
                if (pokemon == null) file.name + " â€¢ unreadable"
                else pokemon.nickname.ifBlank { SwordShieldSpeciesNames.name(pokemon.species) } +
                    " (#" + pokemon.species + ")" + (if (pokemon.shiny) " â€¢ Shiny" else "") + " â€¢ PB8"
            }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Bank â€¢ " + entries.size + " BDSP routes")
            .setPositiveButton("Close", null)
        if (entries.isEmpty()) {
            builder.setMessage("No native BDSP PB8, eligible Gen 1 PSE1, Gen 2 PSE2, Gen 3 PK3, Gen 4 PK4, Gen 5 PK5, Gen 6 PK6, Gen 7 PK7, Let's Go PB7, Sword/Shield PK8, Legends: Arceus PA8, Scarlet/Violet PK9, or Legends: Z-A PA9 Bank entries are available.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session !== bound) return@setItems
                val selected = entries[index]
                when {
                    selected.extension.equals("pse1", true) -> showBdspGen1BankEntry(bound, selected)
                    selected.extension.equals("pse2", true) -> showBdspGen2BankEntry(bound, selected)
                    selected.extension.equals("pk3", true) -> showBdspGen3BankEntry(bound, selected)
                    selected.extension.equals("pk4", true) -> showBdspGen4BankEntry(bound, selected)
                    selected.extension.equals("pk5", true) -> showBdspGen5BankEntry(bound, selected)
                    selected.extension.equals("pk6", true) -> showBdspGen6BankEntry(bound, selected)
                    selected.extension.equals("pk7", true) -> showBdspGen7BankEntry(bound, selected)
                    selected.extension.equals("pb7", true) -> showBdspLetsGoBankEntry(bound, selected)
                    selected.extension.equals("pk8", true) -> showBdspSwordShieldBankEntry(bound, selected)
                    selected.extension.equals("pa8", true) -> showBdspLegendsArceusBankEntry(bound, selected)
                    selected.extension.equals("pk9", true) -> showBdspScarletVioletBankEntry(bound, selected)
                    selected.extension.equals("pa9", true) -> showBdspLegendsZaBankEntry(bound, selected)
                    else -> showBdspBankEntry(bound, selected)
                }
            }
        }
        builder.show()
    }

    private fun showBdspSwordShieldBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(
                entry != null && entry.parentFile == directory && entry.isFile &&
                    entry.name.startsWith("switch_swsh_") && entry.extension.equals("pk8", true)
            ) { "This is not a current Sword/Shield PSE Bank entry." }
            val bank = entry.readBytes()
            val hash = BdspPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_swsh_${hash}.pk8") {
                "The PSE Bank filename no longer matches its native PK8 bytes."
            }
            SwordShieldToBdspPb8.review(bank) to hash
        }
        val reviewed = opened.getOrElse {
            AlertDialog.Builder(this).setTitle("Sword/Shield -> BDSP blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK8 Bank entry and BDSP save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val (converted, openedHash) = reviewed
        val p = BdspPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved 344-byte PK8\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-title PSE conversion does not claim an official HOME transfer, trade, or provenance."
            )
        }
        AlertDialog.Builder(this).setTitle("Sword/Shield -> BDSP â€¢ PSE Legalise review")
            .setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri ?: error("Reopen the selected BDSP save from PSE.")
                    require(
                        selected.parentFile == directory && selected.isFile &&
                            selected.name.startsWith("switch_swsh_") &&
                            selected.extension.equals("pk8", true)
                    ) { "The Sword/Shield Bank entry changed." }
                    val bank = selected.readBytes()
                    val hash = BdspPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_swsh_${hash}.pk8") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    SwordShieldToBdspPb8.review(bank)
                    startActivity(Intent(this, SwitchBdspPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Sword/Shield conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showBdspLegendsArceusBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(
                entry != null && entry.parentFile == directory && entry.isFile &&
                    entry.name.startsWith("switch_pla_") && entry.extension.equals("pa8", true)
            ) { "This is not a current Legends: Arceus PSE Bank entry." }
            val bank = entry.readBytes()
            val hash = BdspPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_pla_${hash}.pa8") {
                "The PSE Bank filename no longer matches its native PA8 bytes."
            }
            hash to LegendsArceusToBdspPb8.review(bank)
        }
        val (openedHash, converted) = result.getOrElse {
            AlertDialog.Builder(this).setTitle("Legends: Arceus -> BDSP blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PA8 Bank entry and BDSP save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = BdspPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved ${selected.length()}-byte PA8\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-title PSE conversion does not claim an official HOME transfer, trade, or provenance."
            )
        }
        AlertDialog.Builder(this).setTitle("Legends: Arceus -> BDSP â€¢ PSE Legalise review")
            .setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri ?: error("Reopen the selected BDSP save from PSE.")
                    require(
                        selected.parentFile == directory && selected.isFile &&
                            selected.name.startsWith("switch_pla_") &&
                            selected.extension.equals("pa8", true)
                    ) { "The Legends: Arceus Bank entry changed." }
                    val bank = selected.readBytes()
                    val hash = BdspPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_pla_${hash}.pa8") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    LegendsArceusToBdspPb8.review(bank)
                    startActivity(Intent(this, SwitchBdspPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Legends: Arceus conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showBdspScarletVioletBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(
                entry != null && entry.parentFile == directory && entry.isFile &&
                    entry.name.startsWith("switch_sv_") && entry.extension.equals("pk9", true)
            ) { "This is not a current Scarlet/Violet PSE Bank entry." }
            val bank = entry.readBytes()
            val hash = BdspPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_sv_${hash}.pk9") {
                "The PSE Bank filename no longer matches its native PK9 bytes."
            }
            hash to ScarletVioletToBdspPb8.review(bank)
        }
        val (openedHash, converted) = result.getOrElse {
            AlertDialog.Builder(this).setTitle("Scarlet/Violet -> BDSP blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK9 Bank entry and BDSP save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = BdspPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved 344-byte PK9\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, trade, or provenance."
            )
        }
        AlertDialog.Builder(this).setTitle("Scarlet/Violet -> BDSP â€¢ PSE Legalise review")
            .setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri ?: error("Reopen the selected BDSP save from PSE.")
                    require(
                        selected.parentFile == directory && selected.isFile &&
                            selected.name.startsWith("switch_sv_") &&
                            selected.extension.equals("pk9", true)
                    ) { "The Scarlet/Violet Bank entry changed." }
                    val bank = selected.readBytes()
                    val hash = BdspPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_sv_${hash}.pk9") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    ScarletVioletToBdspPb8.review(bank)
                    startActivity(Intent(this, SwitchBdspPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Scarlet/Violet conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showBdspLegendsZaBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(
                entry != null && entry.parentFile == directory && entry.isFile &&
                    entry.name.startsWith("switch_za_") && entry.extension.equals("pa9", true)
            ) { "This is not a current Legends: Z-A PSE Bank entry." }
            val bank = entry.readBytes()
            val hash = BdspPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_za_${hash}.pa9") {
                "The PSE Bank filename no longer matches its native PA9 bytes."
            }
            hash to LegendsZaToBdspPb8.review(bank)
        }
        val (openedHash, converted) = result.getOrElse {
            AlertDialog.Builder(this).setTitle("Legends: Z-A -> BDSP blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PA9 Bank entry and BDSP save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = BdspPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) â€¢ Level ${p.cachedLevel}\n")
            append("Source: ${selected.name} â€¢ preserved 344-byte PA9\n")
            append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, trade, or provenance."
            )
        }
        AlertDialog.Builder(this).setTitle("Legends: Z-A -> BDSP â€¢ PSE Legalise review")
            .setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri ?: error("Reopen the selected BDSP save from PSE.")
                    require(
                        selected.parentFile == directory && selected.isFile &&
                            selected.name.startsWith("switch_za_") &&
                            selected.extension.equals("pa9", true)
                    ) { "The Legends: Z-A Bank entry changed." }
                    val bank = selected.readBytes()
                    val hash = BdspPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_za_${hash}.pa9") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    LegendsZaToBdspPb8.review(bank)
                    startActivity(Intent(this, SwitchBdspPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Legends: Z-A conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showBdspGen1BankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("pse1_") && entry.extension.equals("pse1", true)) {
                "This is not a current Gen 1 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen1ToSwordShieldPk8.inspectSource(bytes)
            val trainer = BdspTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 1 route currently requires an English BDSP destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 1 -> BDSP blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PSE1 Bank entry and BDSP save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = BdspPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this).setTitle("Prepare Gen 1 -> BDSP")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PB8 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen1SwordShieldMetadata(opened.second.species)
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 1 Bank entry changed during review."
                }
                metadata to Gen1ToBdspPb8.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = BdspPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(SwordShieldSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ Level " + p.cachedLevel + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved 63-byte PSE1 record\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this).setTitle("Gen 1 -> BDSP â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected BDSP save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.name.startsWith("pse1_") &&
                                    selected.extension.equals("pse1", true)) {
                                    "The Gen 1 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                                    "The Gen 1 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen1ToBdspPb8.review(current, opened.third, metadata)
                                startActivity(Intent(
                                    this,
                                    SwitchBdspPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen1.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen1.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen1.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen1.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 1 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 1 -> BDSP blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PSE1 Bank entry and BDSP save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }


    private fun showBdspGen2BankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pse2", true)) {
                "This is not a current Gen 2 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen2ToSwordShieldPk8.inspectSource(bytes)
            val trainer = BdspTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 2 route currently requires an English BDSP destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 2 -> BDSP blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PSE2 Bank entry and BDSP save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = BdspPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this).setTitle("Prepare Gen 2 -> BDSP")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PB8 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen1SwordShieldMetadata(opened.second.species)
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 2 Bank entry changed during review."
                }
                metadata to Gen2ToBdspPb8.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = BdspPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(SwordShieldSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ Level " + p.cachedLevel + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved 62-byte PSE2 record\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 2 -> BDSP â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected BDSP save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pse2", true)) {
                                    "The Gen 2 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                                    "The Gen 2 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen2ToBdspPb8.review(current, opened.third, metadata)
                                startActivity(Intent(
                                    this,
                                    SwitchBdspPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen2.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen2.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen2.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen2.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 2 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 2 -> BDSP blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PSE2 Bank entry and BDSP save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showBdspGen3BankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk3", true)) {
                "This is not a current Gen 3 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen3ToSwordShieldPk8.inspectSource(bytes)
            val trainer = BdspTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 3 route currently requires an English BDSP destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 3 -> BDSP blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK3 Bank entry and BDSP save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = BdspPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this).setTitle("Prepare Gen 3 -> BDSP")
            .setMessage("Loading documented species metadata and building a reviewed PB8 preview from the exact selected PK3â€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen3SwordShieldMetadata(
                    opened.second.species, opened.second.abilitySlot
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 3 Bank entry changed during review."
                }
                metadata to Gen3ToBdspPb8.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = BdspPseBankRestoreEditing.inspectBankRecord(
                        converted.nativeRecord
                    )
                    val message = buildString {
                        append(SwordShieldSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ Level " + p.cachedLevel + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 80-byte PK3\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 3 -> BDSP â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected BDSP save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk3", true)) {
                                    "The Gen 3 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    BdspPseBankRestoreEditing.sha256(current) == openedHash
                                ) {
                                    "The Gen 3 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen3ToBdspPb8.review(current, opened.third, metadata)
                                startActivity(Intent(
                                    this,
                                    SwitchBdspPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen3.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen3.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen3.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen3.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 3 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 3 -> BDSP blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK3 Bank entry and BDSP save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showBdspGen4BankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk4", true)) {
                "This is not a current Gen 4 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen4ToSwordShieldPk8.inspectSource(bytes)
            val trainer = BdspTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 4 route currently requires an English BDSP destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 4 -> BDSP blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK4 Bank entry and BDSP save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = BdspPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this).setTitle("Prepare Gen 4 -> BDSP")
            .setMessage("Loading documented species metadata and building a reviewed PB8 preview from the exact selected PK4â€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen4SwordShieldMetadata(
                    opened.second.species, opened.second.abilitySlot, opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 4 Bank entry changed during review."
                }
                metadata to Gen4ToBdspPb8.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = BdspPseBankRestoreEditing.inspectBankRecord(
                        converted.nativeRecord
                    )
                    val message = buildString {
                        append(SwordShieldSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ Level " + p.cachedLevel + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 136-byte PK4\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 4 -> BDSP â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected BDSP save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk4", true)) {
                                    "The Gen 4 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    BdspPseBankRestoreEditing.sha256(current) == openedHash
                                ) {
                                    "The Gen 4 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen4ToBdspPb8.review(current, opened.third, metadata)
                                startActivity(Intent(
                                    this,
                                    SwitchBdspPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen4.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen4.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen4.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen4.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 4 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 4 -> BDSP blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK4 Bank entry and BDSP save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showBdspGen5BankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk5", true)) {
                "This is not a current Gen 5 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen5ToSwordShieldPk8.inspectSource(bytes)
            val trainer = BdspTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 5 route currently requires an English BDSP destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 5 -> BDSP blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK5 Bank entry and BDSP save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = BdspPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this).setTitle("Prepare Gen 5 -> BDSP")
            .setMessage("Loading documented species metadata and building a reviewed PB8 preview from the exact selected PK5â€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen5SwordShieldMetadata(
                    opened.second.species, opened.second.abilitySlot, opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 5 Bank entry changed during review."
                }
                metadata to Gen5ToBdspPb8.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = BdspPseBankRestoreEditing.inspectBankRecord(
                        converted.nativeRecord
                    )
                    val message = buildString {
                        append(SwordShieldSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ Level " + p.cachedLevel + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 136-byte PK5\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 5 -> BDSP â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected BDSP save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk5", true)) {
                                    "The Gen 5 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    BdspPseBankRestoreEditing.sha256(current) == openedHash
                                ) {
                                    "The Gen 5 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen5ToBdspPb8.review(current, opened.third, metadata)
                                startActivity(Intent(
                                    this,
                                    SwitchBdspPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen5.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen5.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen5.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen5.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 5 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 5 -> BDSP blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK5 Bank entry and BDSP save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showBdspGen6BankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk6", true)) {
                "This is not a current Gen 6 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen6ToBdspPb8.inspectSource(bytes)
            val trainer = BdspTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 6 route currently requires an English BDSP destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 6 -> BDSP blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK6 Bank entry and BDSP save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = BdspPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this).setTitle("Prepare Gen 6 -> BDSP")
            .setMessage("Loading documented species metadata and building a reviewed PB8 preview from the exact selected PK6â€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen6SwordShieldMetadata(
                    opened.second.species, opened.second.abilityNumber, opened.second.ability
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 6 Bank entry changed during review."
                }
                metadata to Gen6ToBdspPb8.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = BdspPseBankRestoreEditing.inspectBankRecord(
                        converted.nativeRecord
                    )
                    val message = buildString {
                        append(SwordShieldSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ Level " + p.cachedLevel + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 232-byte PK6\n")
                        append("Reviewed Bank SHA-256: $openedHash\n")
                        append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 6 -> BDSP â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri
                                    ?: error("Reopen the selected BDSP save from PSE.")
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk6", true)) {
                                    "The Gen 6 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    BdspPseBankRestoreEditing.sha256(current) == openedHash
                                ) {
                                    "The Gen 6 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen6ToBdspPb8.review(current, opened.third, metadata)
                                startActivity(Intent(
                                    this,
                                    SwitchBdspPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen6.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen6.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen6.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen6.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 6 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 6 -> BDSP blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK6 Bank entry and BDSP save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showBdspGen7BankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk7", true)) {
                "This is not a current Gen 7 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen7ToBdspPb8.inspectSource(bytes)
            val trainer = BdspTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 7 route currently requires an English BDSP destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 7 -> BDSP blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK7 Bank entry and BDSP save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = BdspPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this).setTitle("Prepare Gen 7 -> BDSP")
            .setMessage("Loading documented species metadata and building a reviewed PB8 preview from the exact selected PK7â€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen7SwordShieldMetadata(
                    opened.second.species, opened.second.abilityNumber,
                    opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 7 Bank entry changed during review."
                }
                metadata to Gen7ToBdspPb8.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = BdspPseBankRestoreEditing.inspectBankRecord(
                        converted.nativeRecord
                    )
                    val message = buildString {
                        append(SwordShieldSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ Level " + p.cachedLevel + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 232-byte PK7\n")
                        append("Reviewed Bank SHA-256: " + openedHash + "\n")
                        append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 7 -> BDSP â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected BDSP save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk7", true)) {
                                    "The Gen 7 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(BdspPseBankRestoreEditing.sha256(current) == openedHash) {
                                    "The Gen 7 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen7ToBdspPb8.review(current, opened.third, metadata)
                                startActivity(Intent(
                                    this,
                                    SwitchBdspPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen7.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen7.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen7.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen7.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 7 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 7 -> BDSP blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK7 Bank entry and BDSP save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showBdspLetsGoBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("switch_lgpe_") && entry.extension.equals("pb7", true)) {
                "This is not a current Let's Go PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val hash = BdspPseBankRestoreEditing.sha256(bytes)
            require(entry.name == "switch_lgpe_${hash}.pb7") {
                "The PSE Bank filename no longer matches its native PB7 bytes."
            }
            val source = LetsGoToBdspPb8.inspectSource(bytes)
            val trainer = BdspTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Let's Go route currently requires an English BDSP destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Let's Go -> BDSP blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PB7 Bank entry and BDSP save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = BdspPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this).setTitle("Prepare Let's Go -> BDSP")
            .setMessage("Loading documented species metadata and building a reviewed PB8 preview from the exact selected PB7â€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadBdspSpeciesMetadata(
                    opened.second.species, opened.second.abilityNumber,
                    opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(BdspPseBankRestoreEditing.sha256(current) == openedHash &&
                    selected.name == "switch_lgpe_${openedHash}.pb7") {
                    "The Let's Go Bank entry changed during review."
                }
                metadata to LetsGoToBdspPb8.review(current, opened.third, metadata)
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val pokemon = BdspPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(SwordShieldSpeciesNames.name(pokemon.species) + " (#" +
                            pokemon.species + ") â€¢ Level " + pokemon.cachedLevel + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 260-byte native PB7\n")
                        append("Reviewed Bank SHA-256: " + openedHash + "\n")
                        append("Prepared destination: checksum-valid 344-byte native BDSP PB8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Let's Go -> BDSP â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected BDSP save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.name.startsWith("switch_lgpe_") &&
                                    selected.extension.equals("pb7", true)) {
                                    "The Let's Go Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(BdspPseBankRestoreEditing.sha256(current) == openedHash &&
                                    selected.name == "switch_lgpe_${openedHash}.pb7") {
                                    "The Let's Go Bank entry changed. Reopen PSE Bank."
                                }
                                LetsGoToBdspPb8.review(current, opened.third, metadata)
                                startActivity(Intent(
                                    this,
                                    SwitchBdspPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.lets_go.friendship", metadata.baseFriendship)
                                    .putExtra("pse.lets_go.gender_rate", metadata.genderRate)
                                    .putExtra("pse.lets_go.ability_id", metadata.abilityId)
                                    .putExtra("pse.lets_go.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Let's Go conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Let's Go -> BDSP blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PB7 Bank entry and BDSP save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showBdspBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = file.canonicalFile
        val parsed = runCatching {
            require(entry.parentFile == directory && entry.name.startsWith("switch_bdsp_") && entry.extension == "pb8") {
                "This is not a BDSP Bank entry."
            }
            BdspPokemonReadOnly.read(entry.readBytes())
        }.getOrElse { BdspSlotRead.Invalid(it.message ?: "The Bank entry could not be read.") }
        if (parsed !is BdspSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle("Unreadable PSE Bank entry")
                .setMessage((parsed as? BdspSlotRead.Invalid)?.reason ?: "The Bank entry is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = parsed.value
        val detail = buildString {
            append("${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) â€¢ Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nCached level: ${p.cachedLevel}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"}\nOriginal trainer: ${p.originalTrainer}\n\n")
            append("Native PB8 checksum passed. Restore writes the exact Bank record only to a reviewed new save copy; the active source and Bank entry remain unchanged. Removing this Bank copy never changes the Switch save.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("PSE Bank â€¢ BDSP")
            .setMessage(detail).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Restore to new save copy",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri ?: error("Reopen the selected BDSP save from PSE.")
                    require(entry.parentFile == directory && entry.name.startsWith("switch_bdsp_") && entry.extension == "pb8") {
                        "This is not a current BDSP Bank entry."
                    }
                    require(BdspPokemonReadOnly.read(entry.readBytes()) is BdspSlotRead.Pokemon) {
                        "The Bank record is invalid and cannot be restored."
                    }
                    dialog.dismiss()
                    startActivity(Intent(this, SwitchBdspPseBankRestoreActivity::class.java).setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", entry.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Bank restore")
                        .setMessage(error.message ?: "The source and Bank entry were left unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed; the Bank entry was left unchanged." }
                    require(entry.parentFile == directory && entry.name.startsWith("switch_bdsp_") && entry.extension == "pb8") {
                        "This is not a removable BDSP Bank entry."
                    }
                    require(BdspPokemonReadOnly.read(entry.readBytes()) is BdspSlotRead.Pokemon) {
                        "The Bank record is invalid; it was left unchanged."
                    }
                    require(entry.delete()) { "The Bank entry could not be removed." }
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Removed from PSE Bank")
                        .setMessage(entry.name + " was removed. The Switch save was not changed.")
                        .setPositiveButton("Back to Bank") { _, _ -> if (session === bound) showBdspBank(bound) }
                        .setNegativeButton("Close", null).show()
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not remove Bank entry")
                        .setMessage(error.message ?: "The entry was left unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
        dialog.show()
    }

    private fun showLegendsArceusBank(bound: SwitchReadSession) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank")
        val entries = directory.listFiles()?.filter {
            it.isFile && ((it.name.startsWith("switch_pla_") && it.extension.equals("pa8", true)) ||
                (it.name.startsWith("pse1_") && it.extension.equals("pse1", true)) ||
                it.extension.equals("pse2", true) ||
                it.extension.equals("pk3", true) ||
                it.extension.equals("pk4", true) ||
                it.extension.equals("pk5", true) ||
                it.extension.equals("pk6", true) ||
                it.extension.equals("pk7", true) ||
                (it.name.startsWith("switch_lgpe_") && it.extension.equals("pb7", true)) ||
                (it.name.startsWith("switch_swsh_") && it.extension.equals("pk8", true)) ||
                (it.name.startsWith("switch_bdsp_") && it.extension.equals("pb8", true)) ||
                (it.name.startsWith("switch_sv_") && it.extension.equals("pk9", true)) ||
                (it.name.startsWith("switch_za_") && it.extension.equals("pa9", true)))
        }?.sortedWith(compareBy<File>({
            when {
                it.extension.equals("pa8", true) -> 0
                it.extension.equals("pse1", true) -> 1
                it.extension.equals("pse2", true) -> 2
                it.extension.equals("pk3", true) -> 3
                it.extension.equals("pk4", true) -> 4
                it.extension.equals("pk5", true) -> 5
                it.extension.equals("pk6", true) -> 6
                it.extension.equals("pk7", true) -> 7
                it.extension.equals("pb7", true) -> 8
                it.extension.equals("pk8", true) -> 9
                it.extension.equals("pb8", true) -> 10
                it.extension.equals("pk9", true) -> 11
                else -> 12
            }
        }, { it.name })).orEmpty()
        val labels = entries.map { file ->
            if (file.extension.equals("pse1", true)) {
                runCatching { Gen1ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank {
                            LegendsArceusSpeciesNames.name(source.species)
                        } + " (#" + source.species + ") â€¢ Gen 1 PSE1 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 1 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pse2", true)) {
                runCatching { Gen2ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank {
                            LegendsArceusSpeciesNames.name(source.species)
                        } + " (#" + source.species + ") â€¢ Gen 2 PSE2 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 2 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk3", true)) {
                runCatching { Gen3ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank {
                            LegendsArceusSpeciesNames.name(source.species)
                        } + " (#" + source.species + ") â€¢ Gen 3 PK3 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 3 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk4", true)) {
                runCatching { Gen4ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank {
                            LegendsArceusSpeciesNames.name(source.species)
                        } + " (#" + source.species + ") â€¢ Gen 4 PK4 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 4 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk5", true)) {
                runCatching { Gen5ToSwordShieldPk8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank {
                            LegendsArceusSpeciesNames.name(source.species)
                        } + " (#" + source.species + ") â€¢ Gen 5 PK5 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 5 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk6", true)) {
                runCatching { Gen6ToLegendsArceusPa8.inspectSource(file.readBytes()) }.fold(
                    onSuccess = { source ->
                        source.nickname.ifBlank {
                            LegendsArceusSpeciesNames.name(source.species)
                        } + " (#" + source.species + ") â€¢ Gen 6 PK6 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Gen 6 route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk7", true)) {
                val converted = runCatching { Gen7ToLegendsArceusPa8.review(file.readBytes()) }
                converted.fold(
                    onSuccess = {
                        val p = LegendsArceusPseBankRestoreEditing.inspectBankRecord(it.nativeRecord)
                        p.nickname.ifBlank { LegendsArceusSpeciesNames.name(p.species) } +
                            " (#" + p.species + ") â€¢ Gen 7 PK7 -> PA8 Legalise"
                    },
                    onFailure = { file.name + " â€¢ Gen 7 route blocked â€¢ " + (it.message ?: "unreadable") }
                )
            } else if (file.extension.equals("pb7", true) &&
                file.name.startsWith("switch_lgpe_")) {
                runCatching { LetsGoToLegendsArceusPa8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank {
                            LegendsArceusSpeciesNames.name(review.sourceSpecies)
                        } + " (#" + review.sourceSpecies + ") â€¢ Let's Go PB7 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Let's Go route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk8", true)) {
                runCatching { SwordShieldToLegendsArceusPa8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank { LegendsArceusSpeciesNames.name(review.sourceSpecies) } +
                            " (#" + review.sourceSpecies + ") â€¢ Sword/Shield PK8 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Sword/Shield route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pb8", true)) {
                runCatching { BdspToLegendsArceusPa8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank { LegendsArceusSpeciesNames.name(review.sourceSpecies) } +
                            " (#" + review.sourceSpecies + ") â€¢ BDSP PB8 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ BDSP route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pk9", true)) {
                runCatching { ScarletVioletToLegendsArceusPa8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank { LegendsArceusSpeciesNames.name(review.sourceSpecies) } +
                            " (#" + review.sourceSpecies + ") â€¢ Scarlet/Violet PK9 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Scarlet/Violet route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else if (file.extension.equals("pa9", true)) {
                runCatching { LegendsZaToLegendsArceusPa8.review(file.readBytes()) }.fold(
                    onSuccess = { review ->
                        review.sourceNickname.ifBlank { LegendsArceusSpeciesNames.name(review.sourceSpecies) } +
                            " (#" + review.sourceSpecies + ") â€¢ Legends: Z-A PA9 -> PA8 Legalise"
                    },
                    onFailure = { error ->
                        file.name + " â€¢ Legends: Z-A route blocked â€¢ " +
                            (error.message ?: "unsupported conversion")
                    }
                )
            } else {
                val parsed = runCatching { LegendsArceusPokemonReadOnly.read(file.readBytes()) }.getOrNull()
                val pokemon = (parsed as? LegendsArceusSlotRead.Pokemon)?.value
                if (pokemon == null) file.name + " â€¢ unreadable"
                else pokemon.nickname.ifBlank { LegendsArceusSpeciesNames.name(pokemon.species) } +
                    " (#" + pokemon.species + ")" + (if (pokemon.shiny) " â€¢ Shiny" else "") +
                    (if (pokemon.isAlpha) " â€¢ Alpha" else "") + " â€¢ PA8"
            }
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Bank â€¢ " + entries.size + " Legends: Arceus routes")
            .setPositiveButton("Close", null)
        if (entries.isEmpty()) {
            builder.setMessage("No native Legends: Arceus PA8, eligible Gen 1 PSE1, Gen 2 PSE2, Gen 3 PK3, Gen 4 PK4, Gen 5 PK5, Gen 6 PK6, Gen 7 PK7, Let's Go PB7, Sword/Shield PK8, BDSP PB8, Scarlet/Violet PK9, or Legends: Z-A PA9 Bank entries are available.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session !== bound) return@setItems
                val selected = entries[index]
                when {
                    selected.extension.equals("pse1", true) ->
                        showLegendsArceusGen1BankEntry(bound, selected)
                    selected.extension.equals("pse2", true) ->
                        showLegendsArceusGen2BankEntry(bound, selected)
                    selected.extension.equals("pk3", true) ->
                        showLegendsArceusGen3BankEntry(bound, selected)
                    selected.extension.equals("pk4", true) ->
                        showLegendsArceusGen4BankEntry(bound, selected)
                    selected.extension.equals("pk5", true) ->
                        showLegendsArceusGen5BankEntry(bound, selected)
                    selected.extension.equals("pk6", true) ->
                        showLegendsArceusGen6BankEntry(bound, selected)
                    selected.extension.equals("pk7", true) -> showLegendsArceusGen7BankEntry(bound, selected)
                    selected.extension.equals("pb7", true) -> showLegendsArceusLetsGoBankEntry(bound, selected)
                    selected.extension.equals("pk8", true) -> showLegendsArceusSwordShieldBankEntry(bound, selected)
                    selected.extension.equals("pb8", true) -> showLegendsArceusBdspBankEntry(bound, selected)
                    selected.extension.equals("pk9", true) -> showLegendsArceusScarletVioletBankEntry(bound, selected)
                    selected.extension.equals("pa9", true) -> showLegendsArceusLegendsZaBankEntry(bound, selected)
                    else -> showLegendsArceusBankEntry(bound, selected)
                }
            }
        }
        builder.show()
    }

    private fun showLegendsArceusSwordShieldBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(
                entry != null && entry.parentFile == directory && entry.isFile &&
                    entry.name.startsWith("switch_swsh_") && entry.extension.equals("pk8", true)
            ) { "This is not a current Sword/Shield PSE Bank entry." }
            val bank = entry.readBytes()
            val hash = LegendsArceusPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_swsh_${hash}.pk8") {
                "The PSE Bank filename no longer matches its native PK8 bytes."
            }
            SwordShieldToLegendsArceusPa8.review(bank) to hash
        }
        val reviewed = opened.getOrElse {
            AlertDialog.Builder(this).setTitle("Sword/Shield -> Legends: Arceus blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK8 Bank entry and Legends: Arceus save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val (converted, openedHash) = reviewed
        val p = LegendsArceusPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) â€¢ EXP ${p.experience}\n")
            append("Source: ${selected.name} â€¢ preserved 344-byte PK8\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This cross-title PSE conversion does not claim an official HOME transfer, trade, or provenance."
            )
        }
        AlertDialog.Builder(this).setTitle("Sword/Shield -> Legends: Arceus â€¢ PSE Legalise review")
            .setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected Legends: Arceus save from PSE.")
                    require(
                        selected.parentFile == directory && selected.isFile &&
                            selected.name.startsWith("switch_swsh_") &&
                            selected.extension.equals("pk8", true)
                    ) { "The Sword/Shield Bank entry changed." }
                    val bank = selected.readBytes()
                    val hash = LegendsArceusPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_swsh_${hash}.pk8") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    SwordShieldToLegendsArceusPa8.review(bank)
                    startActivity(Intent(this, SwitchLegendsArceusPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Sword/Shield conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLegendsArceusBdspBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(
                entry != null && entry.parentFile == directory && entry.isFile &&
                    entry.name.startsWith("switch_bdsp_") && entry.extension.equals("pb8", true)
            ) { "This is not a current BDSP PSE Bank entry." }
            val bank = entry.readBytes()
            val hash = LegendsArceusPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_bdsp_${hash}.pb8") {
                "The PSE Bank filename no longer matches its native PB8 bytes."
            }
            hash to BdspToLegendsArceusPa8.review(bank)
        }
        val (openedHash, converted) = result.getOrElse {
            AlertDialog.Builder(this).setTitle("BDSP -> Legends: Arceus blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PB8 Bank entry and Legends: Arceus save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LegendsArceusPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) â€¢ EXP ${p.experience}\n")
            append("Source: ${selected.name} â€¢ preserved 344-byte PB8\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This cross-title PSE conversion does not claim an official HOME transfer, trade, or provenance."
            )
        }
        AlertDialog.Builder(this).setTitle("BDSP -> Legends: Arceus â€¢ PSE Legalise review")
            .setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected Legends: Arceus save from PSE.")
                    require(
                        selected.parentFile == directory && selected.isFile &&
                            selected.name.startsWith("switch_bdsp_") &&
                            selected.extension.equals("pb8", true)
                    ) { "The BDSP Bank entry changed." }
                    val bank = selected.readBytes()
                    val hash = LegendsArceusPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_bdsp_${hash}.pb8") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    BdspToLegendsArceusPa8.review(bank)
                    startActivity(Intent(this, SwitchLegendsArceusPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare BDSP conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLegendsArceusScarletVioletBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(
                entry != null && entry.parentFile == directory && entry.isFile &&
                    entry.name.startsWith("switch_sv_") && entry.extension.equals("pk9", true)
            ) { "This is not a current Scarlet/Violet PSE Bank entry." }
            val bank = entry.readBytes()
            val hash = LegendsArceusPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_sv_${hash}.pk9") {
                "The PSE Bank filename no longer matches its native PK9 bytes."
            }
            hash to ScarletVioletToLegendsArceusPa8.review(bank)
        }
        val (openedHash, converted) = result.getOrElse {
            AlertDialog.Builder(this).setTitle("Scarlet/Violet -> Legends: Arceus blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PK9 Bank entry and Legends: Arceus save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LegendsArceusPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) â€¢ EXP ${p.experience}\n")
            append("Source: ${selected.name} â€¢ preserved 344-byte PK9\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, trade, or provenance."
            )
        }
        AlertDialog.Builder(this).setTitle("Scarlet/Violet -> Legends: Arceus â€¢ PSE Legalise review")
            .setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected Legends: Arceus save from PSE.")
                    require(
                        selected.parentFile == directory && selected.isFile &&
                            selected.name.startsWith("switch_sv_") &&
                            selected.extension.equals("pk9", true)
                    ) { "The Scarlet/Violet Bank entry changed." }
                    val bank = selected.readBytes()
                    val hash = LegendsArceusPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_sv_${hash}.pk9") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    ScarletVioletToLegendsArceusPa8.review(bank)
                    startActivity(Intent(this, SwitchLegendsArceusPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Scarlet/Violet conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLegendsArceusLegendsZaBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val result = runCatching {
            require(
                entry != null && entry.parentFile == directory && entry.isFile &&
                    entry.name.startsWith("switch_za_") && entry.extension.equals("pa9", true)
            ) { "This is not a current Legends: Z-A PSE Bank entry." }
            val bank = entry.readBytes()
            val hash = LegendsArceusPseBankRestoreEditing.sha256(bank)
            require(entry.name == "switch_za_${hash}.pa9") {
                "The PSE Bank filename no longer matches its native PA9 bytes."
            }
            hash to LegendsZaToLegendsArceusPa8.review(bank)
        }
        val (openedHash, converted) = result.getOrElse {
            AlertDialog.Builder(this).setTitle("Legends: Z-A -> Legends: Arceus blocked")
                .setMessage(
                    (it.message ?: "No safe conversion is available.") +
                        "\n\nThe PA9 Bank entry and Legends: Arceus save remain unchanged."
                )
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val p = LegendsArceusPseBankRestoreEditing.inspectBankRecord(converted.nativeRecord)
        val message = buildString {
            append("${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) â€¢ EXP ${p.experience}\n")
            append("Source: ${selected.name} â€¢ preserved 344-byte PA9\n")
            append("Reviewed Bank SHA-256: $openedHash\n")
            append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
            append("Exact disclosed conversion effects\nâ€¢ ")
            append(converted.effects.joinToString("\nâ€¢ "))
            append(
                "\n\nContinue only if you accept every disclosed Legalise effect. " +
                    "This backward cross-generation PSE conversion does not claim an official HOME transfer, trade, or provenance."
            )
        }
        AlertDialog.Builder(this).setTitle("Legends: Z-A -> Legends: Arceus â€¢ PSE Legalise review")
            .setMessage(message).setNegativeButton("Cancel", null)
            .setPositiveButton("Legalise & choose destination") { _, _ ->
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri
                        ?: error("Reopen the selected Legends: Arceus save from PSE.")
                    require(
                        selected.parentFile == directory && selected.isFile &&
                            selected.name.startsWith("switch_za_") &&
                            selected.extension.equals("pa9", true)
                    ) { "The Legends: Z-A Bank entry changed." }
                    val bank = selected.readBytes()
                    val hash = LegendsArceusPseBankRestoreEditing.sha256(bank)
                    require(hash == openedHash && selected.name == "switch_za_${hash}.pa9") {
                        "The PSE Bank entry changed. Reopen PSE Bank."
                    }
                    LegendsZaToLegendsArceusPa8.review(bank)
                    startActivity(Intent(this, SwitchLegendsArceusPseBankRestoreActivity::class.java)
                        .setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", selected.name)
                        .putExtra("pse.bank.sha256", openedHash)
                        .putExtra("pse.bank.legalise", true)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Legends: Z-A conversion")
                        .setMessage(error.message ?: "Both sources remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }.show()
    }

    private fun showLegendsArceusGen1BankEntry(
        bound: SwitchReadSession,
        file: File
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("pse1_") && entry.extension.equals("pse1", true)) {
                "This is not a current Gen 1 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen1ToSwordShieldPk8.inspectSource(bytes)
            val trainer = LegendsArceusTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 1 route currently requires an English Legends: Arceus destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 1 -> Legends: Arceus blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PSE1 Bank entry and Legends: Arceus save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LegendsArceusPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 1 -> Legends: Arceus")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PA8 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen1SwordShieldMetadata(opened.second.species)
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LegendsArceusPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 1 Bank entry changed during review."
                }
                metadata to Gen1ToLegendsArceusPa8.review(
                    current, opened.third, metadata
                )
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LegendsArceusPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(LegendsArceusSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ EXP " + p.experience + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved 63-byte PSE1 record\n")
                        append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 1 -> Legends: Arceus â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected Legends: Arceus save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.name.startsWith("pse1_") &&
                                    selected.extension.equals("pse1", true)) {
                                    "The Gen 1 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    LegendsArceusPseBankRestoreEditing.sha256(current) ==
                                        openedHash
                                ) {
                                    "The Gen 1 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen1ToLegendsArceusPa8.review(
                                    current, opened.third, metadata
                                )
                                startActivity(Intent(
                                    this,
                                    SwitchLegendsArceusPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen1.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen1.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen1.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen1.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 1 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 1 -> Legends: Arceus blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PSE1 Bank entry and Legends: Arceus save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }


    private fun showLegendsArceusGen2BankEntry(
        bound: SwitchReadSession,
        file: File
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pse2", true)) {
                "This is not a current Gen 2 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen2ToSwordShieldPk8.inspectSource(bytes)
            val trainer = LegendsArceusTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 2 route currently requires an English Legends: Arceus destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 2 -> Legends: Arceus blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PSE2 Bank entry and Legends: Arceus save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LegendsArceusPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 2 -> Legends: Arceus")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PA8 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen1SwordShieldMetadata(opened.second.species)
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LegendsArceusPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 2 Bank entry changed during review."
                }
                metadata to Gen2ToLegendsArceusPa8.review(
                    current, opened.third, metadata
                )
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LegendsArceusPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(LegendsArceusSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ EXP " + p.experience + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved 62-byte PSE2 record\n")
                        append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 2 -> Legends: Arceus â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected Legends: Arceus save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pse2", true)) {
                                    "The Gen 2 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    LegendsArceusPseBankRestoreEditing.sha256(current) ==
                                        openedHash
                                ) {
                                    "The Gen 2 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen2ToLegendsArceusPa8.review(
                                    current, opened.third, metadata
                                )
                                startActivity(Intent(
                                    this,
                                    SwitchLegendsArceusPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen2.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen2.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen2.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen2.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 2 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 2 -> Legends: Arceus blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PSE2 Bank entry and Legends: Arceus save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLegendsArceusGen3BankEntry(
        bound: SwitchReadSession,
        file: File
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk3", true)) {
                "This is not a current Gen 3 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen3ToSwordShieldPk8.inspectSource(bytes)
            val trainer = LegendsArceusTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 3 route currently requires an English Legends: Arceus destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 3 -> Legends: Arceus blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK3 Bank entry and Legends: Arceus save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LegendsArceusPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 3 -> Legends: Arceus")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PA8 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen3SwordShieldMetadata(opened.second.species, opened.second.abilitySlot)
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LegendsArceusPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 3 Bank entry changed during review."
                }
                metadata to Gen3ToLegendsArceusPa8.review(
                    current, opened.third, metadata
                )
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LegendsArceusPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(LegendsArceusSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ EXP " + p.experience + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 80-byte PK3 record\n")
                        append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 3 -> Legends: Arceus â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected Legends: Arceus save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk3", true)) {
                                    "The Gen 3 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    LegendsArceusPseBankRestoreEditing.sha256(current) ==
                                        openedHash
                                ) {
                                    "The Gen 3 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen3ToLegendsArceusPa8.review(
                                    current, opened.third, metadata
                                )
                                startActivity(Intent(
                                    this,
                                    SwitchLegendsArceusPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen3.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen3.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen3.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen3.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 3 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 3 -> Legends: Arceus blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK3 Bank entry and Legends: Arceus save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLegendsArceusGen4BankEntry(
        bound: SwitchReadSession,
        file: File
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk4", true)) {
                "This is not a current Gen 4 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen4ToSwordShieldPk8.inspectSource(bytes)
            val trainer = LegendsArceusTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 4 route currently requires an English Legends: Arceus destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 4 -> Legends: Arceus blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK4 Bank entry and Legends: Arceus save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LegendsArceusPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 4 -> Legends: Arceus")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PA8 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen4SwordShieldMetadata(
                    opened.second.species, opened.second.abilitySlot, opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LegendsArceusPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 4 Bank entry changed during review."
                }
                metadata to Gen4ToLegendsArceusPa8.review(
                    current, opened.third, metadata
                )
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LegendsArceusPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(LegendsArceusSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ EXP " + p.experience + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 136-byte PK4 record\n")
                        append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 4 -> Legends: Arceus â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected Legends: Arceus save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk4", true)) {
                                    "The Gen 4 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    LegendsArceusPseBankRestoreEditing.sha256(current) ==
                                        openedHash
                                ) {
                                    "The Gen 4 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen4ToLegendsArceusPa8.review(
                                    current, opened.third, metadata
                                )
                                startActivity(Intent(
                                    this,
                                    SwitchLegendsArceusPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen4.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen4.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen4.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen4.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 4 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 4 -> Legends: Arceus blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK4 Bank entry and Legends: Arceus save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLegendsArceusGen5BankEntry(
        bound: SwitchReadSession,
        file: File
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk5", true)) {
                "This is not a current Gen 5 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen5ToSwordShieldPk8.inspectSource(bytes)
            val trainer = LegendsArceusTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 5 route currently requires an English Legends: Arceus destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 5 -> Legends: Arceus blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK5 Bank entry and Legends: Arceus save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LegendsArceusPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 5 -> Legends: Arceus")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PA8 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen5SwordShieldMetadata(
                    opened.second.species, opened.second.abilitySlot, opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LegendsArceusPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 5 Bank entry changed during review."
                }
                metadata to Gen5ToLegendsArceusPa8.review(
                    current, opened.third, metadata
                )
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LegendsArceusPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(LegendsArceusSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ EXP " + p.experience + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 136-byte PK5 record\n")
                        append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 5 -> Legends: Arceus â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected Legends: Arceus save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk5", true)) {
                                    "The Gen 5 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    LegendsArceusPseBankRestoreEditing.sha256(current) ==
                                        openedHash
                                ) {
                                    "The Gen 5 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen5ToLegendsArceusPa8.review(
                                    current, opened.third, metadata
                                )
                                startActivity(Intent(
                                    this,
                                    SwitchLegendsArceusPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen5.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen5.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen5.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen5.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 5 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 5 -> Legends: Arceus blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK5 Bank entry and Legends: Arceus save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLegendsArceusGen6BankEntry(
        bound: SwitchReadSession,
        file: File
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk6", true)) {
                "This is not a current Gen 6 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen6ToLegendsArceusPa8.inspectSource(bytes)
            val trainer = LegendsArceusTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 6 route currently requires an English Legends: Arceus destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 6 -> Legends: Arceus blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK6 Bank entry and Legends: Arceus save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LegendsArceusPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 6 -> Legends: Arceus")
            .setMessage("Loading documented species metadata and building a deterministic reviewed PA8 previewâ€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen6SwordShieldMetadata(
                    opened.second.species, opened.second.abilityNumber, opened.second.ability
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LegendsArceusPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 6 Bank entry changed during review."
                }
                metadata to Gen6ToLegendsArceusPa8.review(
                    current, opened.third, metadata
                )
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val p = LegendsArceusPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(LegendsArceusSpeciesNames.name(p.species) + " (#" +
                            p.species + ") â€¢ EXP " + p.experience + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 232-byte PK6 record\n")
                        append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 6 -> Legends: Arceus â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected Legends: Arceus save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk6", true)) {
                                    "The Gen 6 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    LegendsArceusPseBankRestoreEditing.sha256(current) ==
                                        openedHash
                                ) {
                                    "The Gen 6 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen6ToLegendsArceusPa8.review(
                                    current, opened.third, metadata
                                )
                                startActivity(Intent(
                                    this,
                                    SwitchLegendsArceusPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen6.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen6.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen6.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen6.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 6 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 6 -> Legends: Arceus blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK6 Bank entry and Legends: Arceus save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLegendsArceusGen7BankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.extension.equals("pk7", true)) {
                "This is not a current Gen 7 PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val source = Gen7ToLegendsArceusPa8.inspectSource(bytes)
            val trainer = LegendsArceusTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Gen 7 route currently requires an English Legends: Arceus destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Gen 7 -> Legends: Arceus blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PK7 Bank entry and Legends: Arceus save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LegendsArceusPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Gen 7 -> Legends: Arceus")
            .setMessage("Loading documented species metadata and building a reviewed PA8 preview from the exact selected PK7â€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadGen7SwordShieldMetadata(
                    opened.second.species, opened.second.abilityNumber,
                    opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LegendsArceusPseBankRestoreEditing.sha256(current) == openedHash) {
                    "The Gen 7 Bank entry changed during review."
                }
                metadata to Gen7ToLegendsArceusPa8.review(
                    current, opened.third, metadata
                )
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val pokemon = LegendsArceusPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(LegendsArceusSpeciesNames.name(pokemon.species) + " (#" +
                            pokemon.species + ") â€¢ EXP " + pokemon.experience + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 232-byte PK7\n")
                        append("Reviewed Bank SHA-256: " + openedHash + "\n")
                        append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Gen 7 -> Legends: Arceus â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected Legends: Arceus save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.extension.equals("pk7", true)) {
                                    "The Gen 7 Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    LegendsArceusPseBankRestoreEditing.sha256(current) ==
                                        openedHash
                                ) {
                                    "The Gen 7 Bank entry changed. Reopen PSE Bank."
                                }
                                Gen7ToLegendsArceusPa8.review(
                                    current, opened.third, metadata
                                )
                                startActivity(Intent(
                                    this,
                                    SwitchLegendsArceusPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.gen7.friendship", metadata.baseFriendship)
                                    .putExtra("pse.gen7.gender_rate", metadata.genderRate)
                                    .putExtra("pse.gen7.ability_id", metadata.abilityId)
                                    .putExtra("pse.gen7.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Gen 7 conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this).setTitle("Gen 7 -> Legends: Arceus blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PK7 Bank entry and Legends: Arceus save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLegendsArceusLetsGoBankEntry(
        bound: SwitchReadSession,
        file: File
    ) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = runCatching { file.canonicalFile }.getOrNull()
        val opened = runCatching {
            require(entry != null && entry.parentFile == directory && entry.isFile &&
                entry.name.startsWith("switch_lgpe_") && entry.extension.equals("pb7", true)) {
                "This is not a current Let's Go PSE Bank entry."
            }
            val bytes = entry.readBytes()
            val hash = LegendsArceusPseBankRestoreEditing.sha256(bytes)
            require(entry.name == "switch_lgpe_${hash}.pb7") {
                "The PSE Bank filename no longer matches its native PB7 bytes."
            }
            val source = LetsGoToLegendsArceusPa8.inspectSource(bytes)
            val trainer = LegendsArceusTrainerReadOnly.read(bound.snapshot.bytesCopy())
            require(trainer.languageId == 2) {
                "This bounded Let's Go route currently requires an English Legends: Arceus destination trainer."
            }
            Triple(bytes, source, trainer)
        }.getOrElse {
            AlertDialog.Builder(this).setTitle("Let's Go -> Legends: Arceus blocked")
                .setMessage((it.message ?: "No safe conversion is available.") +
                    "\n\nThe PB7 Bank entry and Legends: Arceus save remain unchanged.")
                .setPositiveButton("Close", null).show()
            return
        }
        val selected = entry ?: return
        val openedHash = LegendsArceusPseBankRestoreEditing.sha256(opened.first)
        val loading = AlertDialog.Builder(this)
            .setTitle("Prepare Let's Go -> Legends: Arceus")
            .setMessage("Loading documented species metadata and building a reviewed PA8 preview from the exact selected PB7â€¦")
            .setCancelable(false).create()
        loading.show()
        reader.execute {
            val prepared = runCatching {
                val metadata = loadLegendsArceusSpeciesMetadata(
                    opened.second.species, opened.second.abilityNumber,
                    opened.second.abilityId
                )
                require(session === bound) { "The active save changed. Reopen PSE Bank." }
                val current = selected.readBytes()
                require(LegendsArceusPseBankRestoreEditing.sha256(current) == openedHash &&
                    selected.name == "switch_lgpe_${openedHash}.pb7") {
                    "The Let's Go Bank entry changed during review."
                }
                metadata to LetsGoToLegendsArceusPa8.review(
                    current, opened.third, metadata
                )
            }
            runOnUiThread {
                if (loading.isShowing) loading.dismiss()
                if (isFinishing || isDestroyed) return@runOnUiThread
                prepared.onSuccess { (metadata, converted) ->
                    val pokemon = LegendsArceusPseBankRestoreEditing
                        .inspectBankRecord(converted.nativeRecord)
                    val message = buildString {
                        append(LegendsArceusSpeciesNames.name(pokemon.species) + " (#" +
                            pokemon.species + ") â€¢ EXP " + pokemon.experience + "\n")
                        append("Source: " + selected.name +
                            " â€¢ preserved checksum-valid 260-byte native PB7\n")
                        append("Reviewed Bank SHA-256: " + openedHash + "\n")
                        append("Prepared destination: checksum-valid 360-byte native Legends: Arceus PA8\n\n")
                        append("Exact disclosed conversion effects\nâ€¢ ")
                        append(converted.effects.joinToString("\nâ€¢ "))
                        append("\n\nContinue only if you accept every disclosed Legalise effect.")
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Let's Go -> Legends: Arceus â€¢ PSE Legalise review")
                        .setMessage(message).setNegativeButton("Cancel", null)
                        .setPositiveButton("Legalise & choose destination") { _, _ ->
                            try {
                                require(session === bound) {
                                    "The active save changed. Reopen PSE Bank."
                                }
                                val source = selectedSourceUri ?: error(
                                    "Reopen the selected Legends: Arceus save from PSE."
                                )
                                require(selected.parentFile == directory && selected.isFile &&
                                    selected.name.startsWith("switch_lgpe_") &&
                                    selected.extension.equals("pb7", true)) {
                                    "The Let's Go Bank entry changed."
                                }
                                val current = selected.readBytes()
                                require(
                                    LegendsArceusPseBankRestoreEditing.sha256(current) ==
                                        openedHash &&
                                        selected.name == "switch_lgpe_${openedHash}.pb7"
                                ) {
                                    "The Let's Go Bank entry changed. Reopen PSE Bank."
                                }
                                LetsGoToLegendsArceusPa8.review(
                                    current, opened.third, metadata
                                )
                                startActivity(Intent(
                                    this,
                                    SwitchLegendsArceusPseBankRestoreActivity::class.java
                                )
                                    .setData(source)
                                    .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                                    .putExtra("pse.bank.entry", selected.name)
                                    .putExtra("pse.bank.sha256", openedHash)
                                    .putExtra("pse.bank.legalise", true)
                                    .putExtra("pse.lets_go.friendship", metadata.baseFriendship)
                                    .putExtra("pse.lets_go.gender_rate", metadata.genderRate)
                                    .putExtra("pse.lets_go.ability_id", metadata.abilityId)
                                    .putExtra("pse.lets_go.ability_number", metadata.abilityNumber)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (error: Exception) {
                                AlertDialog.Builder(this)
                                    .setTitle("Could not prepare Let's Go conversion")
                                    .setMessage(error.message ?: "Both sources remain unchanged.")
                                    .setPositiveButton("Close", null).show()
                            }
                        }.show()
                }.onFailure {
                    AlertDialog.Builder(this)
                        .setTitle("Let's Go -> Legends: Arceus blocked")
                        .setMessage((it.message ?: "No safe conversion is available.") +
                            "\n\nThe PB7 Bank entry and Legends: Arceus save remain unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
    }

    private fun showLegendsArceusBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = file.canonicalFile
        val parsed = runCatching {
            require(entry.parentFile == directory && entry.name.startsWith("switch_pla_") && entry.extension == "pa8") {
                "This is not a Legends: Arceus Bank entry."
            }
            LegendsArceusPokemonReadOnly.read(entry.readBytes())
        }.getOrElse { LegendsArceusSlotRead.Invalid(it.message ?: "The Bank entry could not be read.") }
        if (parsed !is LegendsArceusSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle("Unreadable PSE Bank entry")
                .setMessage((parsed as? LegendsArceusSlotRead.Invalid)?.reason ?: "The Bank entry is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = parsed.value
        val detail = buildString {
            append("${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) â€¢ Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"} â€¢ Alpha: ${if (p.isAlpha) "Yes" else "No"}\n")
            append("Original trainer: ${p.originalTrainer}\n\n")
            append("Native PA8 checksum passed. Restore writes the exact 360-byte stored core only to a reviewed new save copy; a party-only tail has no pasture equivalent and remains unchanged in PSE Bank. The active source and Bank entry remain unchanged. Removing this Bank copy never changes the Switch save.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("PSE Bank â€¢ Legends: Arceus")
            .setMessage(detail).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Restore to new save copy",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed. Reopen PSE Bank." }
                    val source = selectedSourceUri ?: error("Reopen the selected Legends: Arceus save from PSE.")
                    require(entry.parentFile == directory && entry.name.startsWith("switch_pla_") && entry.extension == "pa8") {
                        "This is not a current Legends: Arceus Bank entry."
                    }
                    require(LegendsArceusPokemonReadOnly.read(entry.readBytes()) is LegendsArceusSlotRead.Pokemon) {
                        "The Bank record is invalid and cannot be restored."
                    }
                    dialog.dismiss()
                    startActivity(Intent(this, SwitchLegendsArceusPseBankRestoreActivity::class.java).setData(source)
                        .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                        .putExtra("pse.bank.entry", entry.name)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not prepare Bank restore")
                        .setMessage(error.message ?: "The source and Bank entry were left unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed; the Bank entry was left unchanged." }
                    require(entry.parentFile == directory && entry.name.startsWith("switch_pla_") && entry.extension == "pa8") {
                        "This is not a removable Legends: Arceus Bank entry."
                    }
                    require(LegendsArceusPokemonReadOnly.read(entry.readBytes()) is LegendsArceusSlotRead.Pokemon) {
                        "The Bank record is invalid; it was left unchanged."
                    }
                    require(entry.delete()) { "The Bank entry could not be removed." }
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Removed from PSE Bank")
                        .setMessage(entry.name + " was removed. The Switch save was not changed.")
                        .setPositiveButton("Back to Bank") { _, _ -> if (session === bound) showLegendsArceusBank(bound) }
                        .setNegativeButton("Close", null).show()
                } catch (error: Exception) {
                    AlertDialog.Builder(this).setTitle("Could not remove Bank entry")
                        .setMessage(error.message ?: "The entry was left unchanged.")
                        .setPositiveButton("Close", null).show()
                }
            }
        }
        dialog.show()
    }

    private fun showScarletVioletBank(bound: SwitchReadSession) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank")
        val entries = directory.listFiles()?.filter {
            it.isFile && (
                (it.name.startsWith("switch_sv_") && it.extension.equals("pk9", true)) ||
                    (it.name.startsWith("pse1_") && it.extension.equals("pse1", true)) ||
                    it.extension.equals("pse2", true) ||
                    it.extension.equals("pk3", true) ||
                    it.extension.equals("pk4", true) ||
                    it.extension.equals("pk5", true) ||
                    it.extension.equals("pk6", true) ||
                    (it.name.startsWith("switch_swsh_") && it.extension.equals("pk8", true)) ||
                    (it.name.startsWith("switch_bdsp_") && it.extension.equals("pb8",ã5çfòµë(š+my×"&V6÷&G5ÆåÆâ"¢VæB‚%6–ævÆW2f–7F÷&–W3¢G·&VBç6–ævÆW5f–7F÷&–W7ÕÆâ"¢VæB‚$7W'&VçB6–ævÆW27G&V³¢G·&VBç6–ævÆW57G&V·ÕÆåÆâ"¢VæB‚$F÷V&ÆW2f–7F÷&–W3¢G·&VBæF÷V&ÆW5f–7F÷&–W7ÕÆâ"¢VæB‚$7W'&VçBF÷V&ÆW27G&V³¢G·&VBæF÷V&ÆW57G&V·ÕÆåÆâ"¢VæB‚$Fö7VÖVçFVBæF—fR63¢’Ã““’Ã““’f–7F÷&–W2æB3f÷"7W'&VçB7G&V²â"¢–b‚&VBçf–7F÷&–W5v—F†–äFö7VÖVçFVE&ævRÇÂ&VBç7G&V·5v—F†–äFö7VÖVçFVE&ævR’°¢VæB‚$BÆV7BöæR6fVBfÇVR—2÷WG6–FR—G2Fö7VÖVçFVB6²4RF—7Æ—2—BW†7FÇ’v—F†÷WBæ÷&ÖÆ—¦–ær—Bâ"¢Ð¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚76VB&Vf÷&RF—7Æ’â"¢VæB‚$VF—B÷Vç2&Wf–WvVBæF—fRw&—FW"f÷"F†W6Rf÷W"&V6÷&G3²F†R6VÆV7FVB6÷W&6R&VÖ–ç2Væ6†ævVBâ"¢Ð¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B&GFÆRF÷vW"&V6÷&G2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆD&GFÆUF÷vW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$&GFÆRF÷vW"&V6÷&G2"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆDF–6&R†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆDF–6&Ró¢&WGW&à¢gVâ6Æ÷E7VÖÖ'’‡6Æ÷C¢7v÷&E6†–VÆDF–6&U6Æ÷E&VB“¢7G&–ærÒ'V–ÆE7G&–ær°¢fÂö67Wæ7’Òv†Vâ‡6Æ÷Bç&tö67W–VD'—FR’°¢Óâ$V×G’fÆr ¢Óâ$ö67W–VBfÆr ¢VÇ6RÓâ%Væ¶æ÷vâö67Wæ7’'—FRG·6Æ÷Bç&tö67W–VD'—FWÒ ¢Ð¢VæB‚%6Æ÷BG·6Æ÷Bç6Æ÷GÓ¢Fö67Wæ7’"¢v†Vâ‡fÂ&V6÷&BÒ6Æ÷Bçö¶VÖöâ’°¢7v÷&E6†–VÆDF–6&Uö¶VÖöå&VBäV×G’ÓâVæB‚"(
"7F÷&VB&V6÷&B—2V×G’"¢—27v÷&E6†–VÆDF–6&Uö¶VÖöå&VBä–çfÆ–BÓâVæB‚"(
"7F÷&VB³‚Væf–Æ&ÆS¢G·&V6÷&Bç&V6öçÒ"¢—27v÷&E6†–VÆDF–6&Uö¶VÖöå&VBåö¶VÖöâÓâ°¢fÂÒ&V6÷&BçfÇVP¢fÂ7V6–W2Ò7v÷&E6†–VÆE7V6–W4æÖW2ææÖR‡ç7V6–W2¢fÂæ–6¶æÖRÒææ–6¶æÖRçF¶T–b²—Bæ—4æ÷D&Ææ²‚’bb—BÒ7V6–W2Ð¢VæB‚"(
"G¶æ–6¶æÖSòæÆWB²"F—B‚G7V6–W2’"Òó¢7V6–W7Ò"¢–b‡æf÷&ÒÒ’VæB‚"(
"f÷&ÒG·æf÷&×Ò"¢VæB‚"(
"G¶Æ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’ævWD÷$çVÆÂ‡ævVæFW"’ó¢$vVæFW"G·ævVæFW'Ò'Ò"¢–b‡ç6†–ç’’VæB‚"(
"6†–ç’"¢–b‡æ—4Vvr’VæB‚"(
"Vvr"¢–b‚6Æ÷Bæ—4ö67W–VB’VæB‚"(
"–æ7F—fR7F÷&VB&V6÷&B"¢Ð¢Ð¢Ð¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"çW'6W'’&V6÷&G2(
"&VBÖöæÇ•ÆåÆâ"¢f÷"†çW'6W'’–â&VBæçW'6W&–W2’°¢VæB‚$çW'6W'’G¶çW'6W'’æçW'6W'—ÕÆâ"¢çW'6W'’ç6Æ÷G2æf÷$V6‚²VæB‡6Æ÷E7VÖÖ'’†—B’’æVæB‚uÆâr’Ð¢VæB‚$ÖWFFF&Vf—‚v÷&C¢G¶çW'6W'’çVæ¶æ÷vå&Vf—…v÷&GÕÆâ"¢VæB‚$ÖWFFFT–çC3"v÷&C¢G¶çW'6W'’çVæ¶æ÷våfÇVUv÷&GÕÆâ"¢VæB‚%$är6VVC¢G¶çW'6W'’ç&æu6VVGÕÆâ"¢VæB‚%$är6VVB†Wƒ¢G¶çW'6W'’ç&æu6VVBçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆâ"¢VæB‚$÷VRÖWFFFF–Ã¢G¶çW'6W'’çVæ¶æ÷våF–Ä'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆåÆâ"¢Ð¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBF†RƒSsÖ'—FRF–6&Rö&¦V7BÆ–÷WB76VB&Vf÷&RF—7Æ’â"¢VæB‚%³‚7VÖÖ&–W2&WV—&RF†V—"7F÷&VBÖFF6†V6·7V×2Fò72âö67Wæ7’'—FW2ÂF†RW‡÷6VB$är6VVBÂæB÷VRÖWFFF&VÖ–âW†7Bâ"¢VæB‚$æò'&VVF–ær&öw&W72Â6ö×F–&–Æ—G’ÂVvr&VF–æW72ÂfVRÂFW÷6—BÂv—F†G&vÂÂ&W6WBÂ&W—"ÂW‡÷'BÂ&WÆ6VÖVçBÂ÷"6fRw&—F–ær—2–æfW'&VB÷"&÷f–FVBâ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’°¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$çW'6W'’&V6÷&G2(
"&VBÖöæÇ’"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆD&÷„Æ–÷WB†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆD&÷„Æ–÷WBó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"&÷‚Æ–÷WB(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚$7W'&VçB&÷‚&r'—FS¢G·&VBç&t7W'&VçD&÷‡ÕÆâ"¢VæB‚$7W'&VçB&÷‚&r†Wƒ¢G·&VBç&t7W'&VçD&÷‚çFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ"Âsr—ÕÆâ"¢VæB‚%6VÆV7FVB&÷ƒ¢G·&VBæ7W'&VçD&÷„–æFWƒòæÆWB²$&÷‚G¶—B²Ò"Òó¢$÷WG6–FRF†RæF—fR(	33&ævR'ÕÆâ"¢VæB‚%VæÆö6¶VB&÷†W2&r'—FS¢G·&VBç&t&÷†W5VæÆö6¶VGÕÆâ"¢VæB‚%VæÆö6¶VB&÷†W2&r†Wƒ¢G·&VBç&t&÷†W5VæÆö6¶VBçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ"Âsr—ÕÆåÆâ"¢f÷"†VçG'’–â&VBææÖW2’°¢VæB‚$&÷‚G¶VçG'’æ&÷‡Ó¢"¢–b†VçG'’ææÖRÒçVÆÂ’VæB†VçG'’ææÖRæ–dV×G’²"†V×G’6fVBæÖR’"Ò¢VÇ6RVæB‚%Vç&VF&ÆR6fVBæÖR(
"G¶VçG'’çVæf–Æ&ÆU&V6öçÒ"¢VæB‚uÆâr¢–b†VçG'’ææÖRÓÒçVÆÂ’VæB‚%&rƒ#"'—FW3¢G¶VçG'’ç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆâ"¢Ð¢VæB‚%ÆåF†R6ö×ÆWFRæF—fR6öçF–æW"†6‚ÂƒCCÖ'—FR&÷‚ÖæÖRö&¦V7BÂöæRÖ'—FR7W'&VçBÖ&÷‚66Æ"ÂæBöæRÖ'—FRVæÆö6¶VBÖ&÷‚66Æ"76VB&Vf÷&RF—7Æ’â"¢VæB‚$V6‚6fVBæÖR—2FV6öFVBöæÇ’g&öÒ—G2æF—fRƒ#"Ö'—FRUDbÓbf–VÆBâ"¢VæB‚%F†RVæÆö6¶VBÖ&÷‚6÷VçB&VÖ–ç2F†RW†7B6fVB'—FRv—F†÷WB&ævR–æfW&Væ6Râ"¢VæB‚$æò&VæÖRÂ7W'&VçBÖ&÷‚6†ævRÂ&÷‚VæÆö6²Â&W—"ÂW‡÷'BÂ&WÆ6VÖVçBÂ÷"6fRw&—F–ær—2f–Æ&ÆR†W&Râ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’°¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$&÷‚Æ–÷WB(
"&VBÖöæÇ’"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆE&ÆÇ”F–vv–ær†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆE&ÆÇ”F–vv–æró¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"&ÆÇ’bF–vv–ær&V6÷&G5ÆåÆâ"¢VæB‚%&÷Fò&ÆÇ’&W7B66÷&S¢G·&VBç&÷Fõ&ÆÇ•66÷&WÕÆâ"¢–b‚&VBç&÷Fõ&ÆÇ•v—F†–äFö7VÖVçFVE&ævR’°¢VæB‚%6fVB&÷Fò&ÆÇ’66÷&R—2&÷fRF†RFö7VÖVçFVB“’Ã““’6æB&VÖ–ç2W†7BåÆâ"¢Ð¢VæB‚%ÆäF–vv–ærGVò(
"6¶–ÆÂ'&÷F†W"&W7B7G&V³¢G·&VBæF–vv–ætGVõ6¶–ÆÅ7G&V·ÕÆâ"¢VæB‚$F–vv–ærGVò(
"7FÖ–æ'&÷F†W"&W7B7G&V³¢G·&VBæF–vv–ætGVõ7FÖ–æ7G&V·ÕÆâ"¢VæB‚$F–vv–ær(
"Ö÷7BvGG2GVrW¢G·&VBæF–vv–æuvGG7ÕÆåÆâ"¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBÆÂf÷W"T–çC3"66Æ"Æ–÷WG276VB&Vf÷&RF—7Æ’â"¢VæB‚%4RFöW2æ÷B–æfW"VæF—66Æ÷6VB62f÷"F†RF–vv–ær&V6÷&G2â"¢VæB‚$VF—B÷Vç2&Wf–WvVBæF—fRw&—FW"f÷"F†W6Rf÷W"&V6÷&G3²F†R6VÆV7FVB6÷W&6R&VÖ–ç2Væ6†ævVBâ"¢Ð¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B&ÆÇ’bF–vv–ær&V6÷&G2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆE&ÆÇ”F–vv–æt7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%&ÆÇ’bF–vv–ær&V6÷&G2"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆE7F'FW$6†ö–6R†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆE7F'FW$6†ö–6Ró¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"7F'FW"6†ö–6UÆåÆâ"¢VæB‚%6fVB7F'FW#¢G·&VBç7F'FW$æÖRó¢%Væ¶æ÷vâæF—fRfÇVR'ÕÆâ"¢VæB‚%&rT–çC3#¢G·&VBç&ufÇVWÕÆâ"¢VæB‚%&r†Wƒ¢G·&VBç&ufÇVRçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆåÆâ"¢–b‡&VBç7F'FW$æÖRÓÒçVÆÂ’°¢VæB‚%F†R6fVBfÇVR—2÷WG6–FRF†RFö7VÖVçFVB(	3"Ö–æræB&VÖ–ç2W†7Bâ"¢Ð¢VæB‚$Fö7VÖVçFVBÖ–æs¢w&öö¶W’Â66÷&'Væç’Â"6ö&&ÆRâ"¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBW†7Bf÷W"Ö'—FR66Æ"Æ–÷WB76VB&Vf÷&RF—7Æ’â"¢VæB‚$VF—F–ærF†—2†—7F÷&–6Â&V6÷&BFöW2æ÷B&WÆ6Rö¼:–ÖöâÂ&WÆ’v–gBÂ÷"6†ævRF†R'G’â"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B7F'FW"6†ö–6R#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆE7F'FW$6†ö–6T7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%7F'FW"6†ö–6R"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆDÆVwVT6&B†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆDÆVwVT6&Bó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"ÆVwVR6&B(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚$6&BG&–æW#¢G·&VBçG&–æW$æÖWÕÆâ"¢VæB‚%G&–æW"”C3#¢G·&VBçG&–æW$–GÒ(
"‚G·&VBçG&–æW$–BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆâ"¢VæB‚$ÆæwVvR”C¢G·&VBæÆæwVvT–GÕÆâ"¢VæB‚$F—7Æ–VBvÖS¢G·&VBæF—7Æ–VDvÖTæÖRó¢%Væ¶æ÷vâ‚G·&VBæF—7Æ–VDvÖWÒ’'ÕÆâ"¢VæB‚$F—7Æ–VB7F'FW#¢G·&VBç7F'FW$æÖRó¢%Væ¶æ÷vâ‚G·&VBç7F'FW'Ò’'ÕÆâ"¢VæB‚$6&BçVÖ&W"'—FW3¢G·&VBæ6&DçVÖ&W$F—7Æ—ÕÆâ"¢VæB‚$vVæFW"”C¢G·&VBævVæFW'ÕÆåÆâ"¢VæB‚%ö¼:–FW‚÷væVC¢G·&VBçö¶VFW„÷væVGÕÆâ"¢VæB‚%6†–ç’ö¼:–Ööâf÷VæC¢G·&VBç6†–ç•ö¶VÖöäf÷VæGÕÆâ"¢VæB‚$7W''’G—W2÷væVC¢G·&VBæ7W''•G—W4÷væVGÕÆâ"¢VæB‚%ö¼:–Ööâ6Vv‡C¢G·&VBæ6Vv‡Eö¶VÖöçÕÆâ"¢VæB‚%&÷Fò&ÆÇ’66÷&S¢G·&VBç&÷Fõ&ÆÇ•66÷&WÕÆâ"¢VæB‚$vÆ"ö¼:–FW‚6ö×ÆWFR'—FS¢G·&VBævÆ$FW„6ö×ÆWFWÕÆâ"¢VæB‚$—6ÆRöb&Ö÷"ö¼:–FW‚6ö×ÆWFR'—FS¢G·&VBæ&Ö÷$FW„6ö×ÆWFWÕÆâ"¢VæB‚$7&÷vâGVæG&ö¼:–FW‚6ö×ÆWFR'—FS¢G·&VBæ7&÷väFW„6ö×ÆWFWÕÆåÆâ"¢VæB‚$GfVçGW&R×7F'Bf–VÆG3¢G·&VBç7F'FVE–V"çFõ7G&–ær‚’çE7F'BƒBÂsr—ÒÒG·&VBç7F'FVDÖöçF‚çFõ7G&–ær‚’çE7F'Bƒ"Âsr—ÒÒG·&VBç7F'FVDF’çFõ7G&–ær‚’çE7F'Bƒ"Âsr—ÕÆâ"¢VæB‚%&–çFVBF–ÖW7F×…Væ—‚6V6öæG2“¢G·&VBç&–çFVEF–ÖW7F×ÕÆåÆâ"¢VæB‚$F—7Æ–VBFVÕÆâ"¢&VBçFVÒæf÷$V6‚²ö¶VÖöâÓà¢VæB‚"G·ö¶VÖöâç6Æ÷GÒâ"¢–b‡ö¶VÖöâç7V6–W2ÓÒ’VæB‚$V×G’"¢VÇ6R°¢VæB‚"Gµ7v÷&E6†–VÆE7V6–W4æÖW2ææÖR‡ö¶VÖöâç7V6–W2—Ò‚2G·ö¶VÖöâç7V6–W7Ò’"¢VæB‚"(
"f÷&ÒG·ö¶VÖöâæf÷&×Ò(
"vVæFW"G·ö¶VÖöâævVæFW'Ò"¢VæB‚"(
"6†–ç’'—FRG·ö¶VÖöâç6†–ç—Ò"¢VæB‚"(
"T2‚G·ö¶VÖöâæVæ7'—F–öä6öç7FçBçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ò"¢VæB‚"(
"f÷&Ò&wVÖVçBG·ö¶VÖöâæf÷&Ô&wVÖVçGÒ"¢VæB‚"(
"Væ¶æ÷vâ‚G·ö¶VÖöâçVæ¶æ÷vâçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ò"¢Ð¢VæB‚uÆâr¢Ð¢VæB‚%ÆåF†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBW†7BƒCÖ'—FRÆVwVR6&Bö&¦V7B76VB&Vf÷&RF—7Æ’â"¢VæB‚%&r6ö×ÆWF–öâ'—FW2Â–FVçF–f–W'2ÂF–ÖW7F×2ÂæBFVÒf–VÆG2&VÖ–âW†7C²4RFöW2æ÷B–æfW"ÆVvÆ—G’÷"6FÆöwVRV&æ6RæÖW2â"¢VæB‚$æò6&B&Vg&W6‚ÂV&æ6RVF—BÂFVÒ&WÆ6VÖVçBÂW‡÷'BÂ&W—"Â÷"6fRw&—F–ær—2f–Æ&ÆR†W&Râ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’°¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$ÆVwVR6&B(
"&VBÖöæÇ’"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆD—6ÆTöd&Ö÷%&öw&W72†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆD—6ÆTöd&Ö÷%&öw&W72ó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"—6ÆRöb&Ö÷"&öw&W75ÆåÆâ"¢VæB‚$ÆöÆâF–vÆWGBf÷VæC¢G·&VBæÆöÆäF–vÆWGDf÷VæGÕÆâ"¢VæB‚$Ö7FW"Fö¦òvGBFöæF–öâ7FvS¢G·&VBæFö¦ôFöæF–öå&öw&W77Ò"¢VæB‚"(
"G·&VBæFö¦ôFöæF–öå7FGW7ÕÆâ"¢VæB‚$Ö7FW"Fö¦òvGG2FöæFVC¢G·&VBæFö¦ôFöæF–öåF÷FÇÕÆâ"¢VæB‚$7&ÒÖòÖÖF–2W6W3¢G·&VBæ7&ÔôÖF–5W6W7ÕÆâ"¢VæB‚%&W7G&–7FVB7'&–ærG—W26ÆV&VC¢G·&VBç&W7G&–7FVE7'&–æuG—W46ÆV&VGÕÆåÆâ"¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBÆÂf—fRW†7BT–çC3"66Æ"Æ–÷WG276VB&Vf÷&RF—7Æ’â"¢VæB‚$öæÇ’F†RFö7VÖVçFVB(	3RÖ7FW"Fö¦òFöæF–öâ7FvR—2–çFW'&WFVC²WfW'’æF—fRfÇVR&VÖ–ç2W†7Bâ"¢VæB‚$VF—F–ær6†ævW2öæÇ’6VÆV7FVB&öw&W7266Æ'3²4RFöW2æ÷B–æfW"&Wv&G2÷"VæÆö6²6–FRVffV7G2â"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B—6ÆRöb&Ö÷"&öw&W72#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆD—6ÆTöd&Ö÷%&öw&W747F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$—6ÆRöb&Ö÷"&öw&W72"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆD7&÷våGVæG&&öw&W72†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆD7&÷våGVæG&&öw&W72ó¢&WGW&à¢gVâ7V6–W2‡fÇVS¢Æöær“¢7G&–ærÒ–b‡fÇVR–âÂâãƒ“„Â¢"GfÇVR(
"Gµ7v÷&E6†–VÆE7V6–W4æÖW2ææÖR‡fÇVRçFô–çB‚’—Ò"VÇ6RfÇVRçFõ7G&–ær‚¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"7&÷vâGVæG&&öw&W75ÆåÆâ"¢VæB‚%7Æ—BÔFV6—6–öâ'V–ç2GFW&ã¢G·&VBç&Vv•GFW&çÒ(
"G·&VBç&Vv•GFW&å7FGW7ÕÆåÆâ"¢VæB‚%7v÷&G2öb§W7F–6Rfö÷G&–çG5Æâ"¢VæB‚$6ö&Æ–öã¢G·&VBæ6ö&Æ–öäfö÷G&–çG7Ò(
"G·&VBæfö÷G&–çE7FGW2‡&VBæ6ö&Æ–öäfö÷G&–çG2—ÕÆâ"¢VæB‚%FW'&¶–öã¢G·&VBçFW'&¶–öäfö÷G&–çG7Ò(
"G·&VBæfö÷G&–çE7FGW2‡&VBçFW'&¶–öäfö÷G&–çG2—ÕÆâ"¢VæB‚%f—&—¦–öã¢G·&VBçf—&—¦–öäfö÷G&–çG7Ò(
"G·&VBæfö÷G&–çE7FGW2‡&VBçf—&—¦–öäfö÷G&–çG2—ÕÆåÆâ"¢VæB‚$Ö‚Æ—"æ÷FVB7V6–W5Æâ"¢&VBææ÷FVE7V6–W2æf÷$V6„–æFW†VB²–æFW‚ÂfÇVRÓâVæB‚%6Æ÷BG¶–æFW‚²Ó¢G·7V6–W2‡fÇVR—ÕÆâ"’Ð¢VæB‚$VæFÆW72G–æÖ‚GfVçGW&R&W7B7G&V³¢G·&VBæVæFÆW757G&V·ÕÆâ"¢VæB‚$G–æÖ‚GfVçGW&RF—66öææV7B7G&V³¢G·&VBæF—66öææV7E7G&V·Ò(
"G·&VBæF—66öææV7E7FGW7ÕÆâ"¢VæB‚%Vöæ–7V6–W2†–çC¢G·7V6–W2‡&VBçVöæ–7V6–W4†–çB—ÕÆåÆâ"¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBÆÂFVâW†7BT–çC3"66Æ"Æ–÷WG276VB&Vf÷&RF—7Æ’â"¢VæB‚$öæÇ’´†U‚w2Fö7VÖVçFVB&Vv’GFW&âÂfö÷G&–çB&ævW2ÂæBF—66öææV7BfVRF‡&W6†öÆB&R–çFW'&WFVC²WfW'’æF—fRfÇVR&VÖ–ç2W†7Bâ"¢VæB‚$VF—F–ær6†ævW2öæÇ’6VÆV7FVB&öw&W7266Æ'3²4RFöW2æ÷B–æfW"Væ6÷VçFW"ÂfVRÂ&Wv&BÂ÷"VæÆö6²6–FRVffV7G2â"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B7&÷vâGVæG&&öw&W72#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆD7&÷våGVæG&&öw&W747F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$7&÷vâGVæG&&öw&W72"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆE7F%F÷W&æÖVçB†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆE7F%F÷W&æÖVçBó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"vÆ&–â7F"F÷W&æÖVçEÆåÆâ"¢VæB‚%F÷FÂf–7F÷&–W3¢G·&VBçF÷FÅf–7F÷&–W7ÕÆåÆâ"¢VæB‚%f–7F÷&–W2'’'FæW%Æâ"¢&VBç'FæW'2æf÷$V6‚²VæB‚"G¶—BææÖWÓ¢G¶—Bçf–7F÷&–W7ÕÆâ"’Ð¢VæB‚%ÆåF†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBÆÂ#W†7BT–çC3"66Æ"Æ–÷WG276VB&Vf÷&RF—7Æ’â"¢VæB‚%4R&W6VçG2V6‚æF—fRF÷FÂW†7FÇ’æBFöW2æ÷B–æfW"'FæW"f–Æ&–Æ—G’ÂF÷W&æÖVçBVæÆö6·2Â÷"&Wv&BVÆ–v–&–Æ—G’â"¢VæB‚$VF—F–ær6†ævW2öæÇ’6VÆV7FVBf–7F÷'’6÷VçFW'2æBFöW2æ÷BÇFW"'FæW"f–Æ&–Æ—G’÷"F÷W&æÖVçB7FFRâ"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BvÆ&–â7F"F÷W&æÖVçB#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆE7F%F÷W&æÖVçD7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$vÆ&–â7F"F÷W&æÖVçB"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆE7v÷&G4öd§W7F–6R†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆE7v÷&G4öd§W7F–6Ró¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"7v÷&G2öb§W7F–6RVW7EÆåÆâ"¢VæB‚%VW7B7FGW3¢G·&VBçVW7E7FGW7Ò(
"G·&VBçVW7E7FGW4Æ&VÇÕÆâ"¢VæB‚$÷fW&ÆÂ&öw&W73¢G·&VBçVW7E&öw&W77Ò(
"G·&VBçVW7E&öw&W74Æ&VÇÕÆåÆâ"¢VæB‚$6ö&Æ–öã¢G·&VBæ6ö&Æ–öå&öw&W77Ò(
"G·&VBç7v÷&G4ÖVÖ&W$Æ&VÂ‡&VBæ6ö&Æ–öå&öw&W72—ÕÆâ"¢VæB‚%FW'&¶–öã¢G·&VBçFW'&¶–öå&öw&W77Ò(
"G·&VBç7v÷&G4ÖVÖ&W$Æ&VÂ‡&VBçFW'&¶–öå&öw&W72—ÕÆâ"¢VæB‚%f—&—¦–öã¢G·&VBçf—&—¦–öå&öw&W77Ò(
"G·&VBç7v÷&G4ÖVÖ&W$Æ&VÂ‡&VBçf—&—¦–öå&öw&W72—ÕÆâ"¢VæB‚$¶VÆFVó¢G·&VBæ¶VÆFVõ&öw&W77Ò(
"G·&VBæ¶VÆFVõ&öw&W74Æ&VÇÕÆåÆâ"¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBÆÂ6—‚W†7BT–çC3"66Æ"Æ–÷WG276VB&Vf÷&RF—7Æ’â"¢VæB‚$öæÇ’´†U‚w2Fö7VÖVçFVBVW7BfÇVW2&R–çFW'&WFVC²WfW'’æF—fRfÇVR&VÖ–ç2W†7Bâ"¢VæB‚$VF—F–ær6†ævW2öæÇ’6VÆV7FVBVW7B×7FFR66Æ'2æBFöW2æ÷B–æfW"Væ6÷VçFW"Â6GW&RÂ&W÷'BÂ&Wv&BÂ÷"VæÆö6²6–FRVffV7G2â"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B7v÷&G2öb§W7F–6RVW7B#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆE7v÷&G4öd§W7F–6T7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%7v÷&G2öb§W7F–6RVW7B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆDGfVçGW&U7FW2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆDGfVçGW&U7FW2ó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"GfVçGW&R7FW6÷VçFW'5ÆåÆâ"¢VæB‚$&–¶R&ö÷7B6†&vR7FW3¢G·&VBæ&–¶T&ö÷7D6†&vU7FW7ÕÆâ"¢VæB‚$&–¶R&ö÷7B6†&vRÆ–Ö—C¢G·&VBæ&–¶T&ö÷7D6†&vTÆ–Ö—GÕÆâ"¢VæB‚$&–¶R&ö÷7B7FGW3¢G·&VBæ&–¶T&ö÷7E7FGW7ÕÆåÆâ"¢VæB‚$Vvr†F6‚7–6ÆR7FW3¢G·&VBæVvt†F6„7–6ÆU7FW7Ò(
"G·&VBæ7–6ÆU7FGW2‡&VBæVvt†F6„7–6ÆU7FW2—ÕÆâ"¢VæB‚$g&–VæG6†—vÆ²7FW3¢G·&VBæg&–VæG6†—vÆµ7FW7Ò(
"G·&VBæ7–6ÆU7FGW2‡&VBæg&–VæG6†—vÆµ7FW2—ÕÆåÆâ"¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBW†7BT–çC3"ÂT–çCbÂæBT–çC‚66Æ"Æ–÷WG276VB&Vf÷&RF—7Æ’â"¢VæB‚$öæÇ’F†RFö7VÖVçFVB6†&vRWVÆ—G’æB(	3#r7FW&ævW2&R–çFW'&WFVC²WfW'’æF—fRfÇVR&VÖ–ç2W†7Bâ"¢VæB‚$VF—F–ær6†ævW2öæÇ’6VÆV7FVBæF—fR6÷VçFW'3²4RFöW2æ÷B–æfW"6†&vRÂWw&FRÂ†F6‚Âg&–VæG6†—Â&Wv&BÂ÷"F–Ö–ær6–FRVffV7G2â"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BGfVçGW&R7FW6÷VçFW'2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆDGfVçGW&U7FW7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$GfVçGW&R7FW6÷VçFW'2"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆD†ÆÄödfÖUF–ÖR†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆD†ÆÄödfÖUF–ÖRó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"†ÆÂöbfÖRF–ÖUÆåÆâ"¢VæB‚%Væ—‚F–ÖW7F×6V6öæG3¢G·&VBæWö6…6V6öæG7ÕÆâ"¢VæB‚%&r–çCcB&—G3¢‚G·&VBç&t†W‡ÕÆâ"¢VæB‚%UD2FFRæBF–ÖS¢G·&VBçWF5F–ÖW7F×ó¢$÷WG6–FR¦fçF–ÖRä–ç7FçBw27W÷'FVB&ævR'ÕÆåÆâ"¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBW†7BV–v‡BÖ'—FR66Æ"Æ–÷WB76VB&Vf÷&RF—7Æ’â"¢VæB‚%´†U‚Fö7VÖVçG2F†—26–væVB–çCcB2F†RUD2Væ—‚F–ÖW7F×f÷"VçFW&–ærF†R†ÆÂöbfÖS²F†R6fVBfÇVRæB&r&—G2&VÖ–âW†7Bâ"¢VæB‚$VF—F–ær6†ævW2öæÇ’F†RFö7VÖVçFVB–çCcBF–ÖW7F×²4RFöW2æ÷B–æfW"†ÆÂöbfÖR†—7F÷'’Â&Wv&G2Â÷"÷F†W"&öw&W76–öââ"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B†ÆÂöbfÖRF–ÖR#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆD†ÆÄödfÖUF–ÖT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$†ÆÂöbfÖRF–ÖR"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆEö¶VFW…f–Wu7FFR†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆEö¶VFW…f–Wu7FFRó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"ö¼:–FW‚f–Wr7FFR(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚$7W'&VçB&Vv–öæÂö¼:–FWƒ¢G·&VBæ7W'&VçDFW‡Ò(
"G·&VBæ7W'&VçDFW„Æ&VÇÕÆåÆâ"¢VæB‚$Æ7Bf–WvVBvÆ"7V6–W3¢G·&VBç7V6–W4Æ&VÂ‡&VBævÆ$VçG'’—ÕÆâ"¢VæB‚$Æ7Bf–WvVB—6ÆRöb&Ö÷"7V6–W3¢G·&VBç7V6–W4Æ&VÂ‡&VBæ&Ö÷$VçG'’—ÕÆâ"¢VæB‚$Æ7Bf–WvVB7&÷vâGVæG&7V6–W3¢G·&VBç7V6–W4Æ&VÂ‡&VBæ7&÷väVçG'’—ÕÆåÆâ"¢VæB‚%F†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBW†7BT–çC3"æBT–çCb66Æ"Æ–÷WG276VB&Vf÷&RF—7Æ’â"¢VæB‚$öæÇ’´†U‚w2Fö7VÖVçFVB&Vv–öæÂ6VÆV7F÷"fÇVW2&R–çFW'&WFVC²WfW'’6fVB6VÆV7F÷"æB7V6–W2”B&VÖ–ç2W†7Bâ"¢VæB‚$æò&Vv–öæÂ6VÆV7F–öâÂÆ7B×f–WvVBVçG'’Âö¼:–FW‚&Vv—7G&F–öâÂ&W—"ÂW‡÷'BÂ÷"6fR×w&—F–ær7F–öâ—2f–Æ&ÆR†W&Râ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’°¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%ö¼:–FW‚f–Wr7FFR(
"&VBÖöæÇ’"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆE&W7G&–7FVE7'&–ær†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBç7v÷&E6†–VÆE&W7G&–7FVE7'&–æró¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"&W7G&–7FVB7'&–ær7G&V·5ÆåÆâ"¢&VBçG—W2æf÷$V6‚²VæB‚"G¶—BçG—TæÖWÓ¢G¶—Bæ&W7E7G&V·ÕÆâ"’Ð¢VæB‚%ÆåF†R6ö×ÆWFRæF—fR6öçF–æW"†6‚æBÆÂ‚W†7BT–çC3"66Æ"Æ–÷WG276VB&Vf÷&RF—7Æ’â"¢VæB‚%4R&W6VçG2V6‚6fVB&W7B7G&V²W†7FÇ’æBFöW2æ÷B–æfW"G—Rf–Æ&–Æ—G’Â7F÷&VBFV×2Â6ö×ÆWF–öâÂ÷"&Wv&BVÆ–v–&–Æ—G’â"¢VæB‚$VF—F–ær6†ævW2öæÇ’6VÆV7FVB&W7B×7G&V²6÷VçFW'2æBFöW2æ÷BÇFW"7F÷&VBFV×2Âf–Æ&–Æ—G’Â6ö×ÆWF–öâÂ&Wv&G2Â÷"VæÆö6²7FFRâ"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B&W7G&–7FVB7'&–ær7G&V·2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…7v÷&E6†–VÆE&W7G&–7FVE7'&–æt7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%&W7G&–7FVB7'&–ær7G&V·2"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâÆVvVæG4&6WW4Æ&VÂ‡&VC¢ÆVvVæG4&6WW56Æ÷E&VB“¢7G&–ærÒv†Vâ‡&VB’°¢ÆVvVæG4&6WW56Æ÷E&VBäV×G’Óâ$V×G’ ¢—2ÆVvVæG4&6WW56Æ÷E&VBä–çfÆ–BÓâ%Vç&VF&ÆRö¼:–Ööâ(
"G·&VBç&V6öçÒ ¢—2ÆVvVæG4&6WW56Æ÷E&VBåö¶VÖöâÓâ°¢fÂÒ&VBçfÇVP¢fÂ7V6–W2ÒÆVvVæG4&6WW57V6–W4æÖW2ææÖR‡ç7V6–W2¢fÂæ–6²Òææ–6¶æÖRçF¶T–b²—Bæ—4æ÷D&Ææ²‚’bb—BÒ7V6–W2Ð¢†æ–6³òæÆWB²"F—B‚G7V6–W2’"Òó¢7V6–W2’²‡æ66†VDÆWfVÃòæÆWB²"(
"66†VBÇbF—B"Òó¢""’°¢†–b‡ç6†–ç’’"(
"6†–ç’"VÇ6R""’²†–b‡æ—4Ç†’"(
"Ç†"VÇ6R""’²†–b‡æ—4æö&ÆR’"(
"æö&ÆR"VÇ6R""¢Ð¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW5'G’†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7F÷&vRÒ&÷VæBæÆVvVæG4&6WW57F÷&vRó¢&WGW&à¢fÂ'G’Ò7F÷&vRç'G’‚¢fÂÆ&VÇ2Ò'G’æÖ²%'G’G¶—Bç÷6—F–öçÓ¢G¶ÆVvVæG4&6WW4Æ&VÂ†—Bç&VB—Ò"Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"'G’G·7F÷&vRç'G”6÷VçGÒób"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW5ö¶VÖöâ†&÷VæBÂ%'G’G·'G•¶–æFW…Òç÷6—F–öçÒ"Â'G•¶–æFW…Òç&VB¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW57GW&R†&÷VæC¢7v—F6…&VE6W76–öâÂ7GW&S¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7F÷&vRÒ&÷VæBæÆVvVæG4&6WW57F÷&vRó¢&WGW&à¢fÂÆ—7BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4ÂÐ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†Æ—7B’Ð¢Æ—7BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ#3"æF—fR7GW&W2(
"36Æ÷G2V6‚(
"æF—fRæ–6¶æÖRÂ7GW&RÖæÖRæBVæÆö6²Ö6÷VçBVF—F–ærf–Æ&ÆUÆâ"°¢"G·7F÷&vRæö67W–VE7GW&U6Æ÷G7Òó“cö67W–VB6Æ÷G2‚G·7F÷&vRçVç&VF&ÆU7GW&U6Æ÷G7ÒVç&VF&ÆR’â"°¢$7W7FöÒæÖW2W6RF†RæF—fRrÖ6†&7FW"f–VÆC²VæÆö6²6÷VçBW6W2F†RæF—fR(	33"fÇVRâ ¢6WEFF–ærƒbÂbÂbÂb¢Ò¢Æ—7BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BVæÆö6¶VB7GW&W2 ¢—4ÆÄ62ÒfÇ6P¢—4Væ&ÆVBÒ6VÆV7FVE6÷W&6UW&’ÒçVÆÀ¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW57GW&UVæÆö6´7F—f—G“£¦6Æ72æ¦f¢’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Æ—7BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B7GW&RG·7GW&R²ÒæÖR ¢—4ÆÄ62ÒfÇ6P¢—4Væ&ÆVBÒ6VÆV7FVE6÷W&6UW&’ÒçVÆÀ¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW57GW&TæÖT7F—f—G“£¦6Æ72æ¦f¢’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"Â7GW&R²¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢f÷"‡6Æ÷B–â7F÷&vRç7GW&R‡7GW&R’’Æ—7BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6Æ÷BG·6Æ÷Bç÷6—F–öçÓ¢G¶ÆVvVæG4&6WW4Æ&VÂ‡6Æ÷Bç&VB—Ò ¢—4ÆÄ62ÒfÇ6S²—4Væ&ÆVBÒ6Æ÷Bç&VB—2ÆVvVæG4&6WW56Æ÷E&VBäV×G¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW5ö¶VÖöâ†&÷VæBÂ%7GW&RG·7GW&R²Ò(
"6Æ÷BG·6Æ÷Bç÷6—F–öçÒ"Â6Æ÷Bç&VB’Ð¢Ò¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"7GW&RG·7GW&R²Òó3""¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’æ7&VFR‚¢fÂæbÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBä„õ$•¤ôåDÂÐ¢æbæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%&Wf–÷W2#²—4Væ&ÆVBÒ7GW&Râ ¢6WDöä6Æ–6´Æ—7FVæW"²F–ÆöræF—6Ö—72‚“²–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW57GW&R†&÷VæBÂ7GW&RÒ’Ð¢ÒÂÆ–æV$Æ–÷WBäÆ–÷WE&×2ƒÂÓ"Âb’¢æbæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$æW‡B#²—4Væ&ÆVBÒ7GW&RÂ3¢6WDöä6Æ–6´Æ—7FVæW"²F–ÆöræF—6Ö—72‚“²–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW57GW&R†&÷VæBÂ7GW&R²’Ð¢ÒÂÆ–æV$Æ–÷WBäÆ–÷WE&×2ƒÂÓ"Âb’¢Æ—7BæFEf–Wr†æb“²F–Æörç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW5ö¶VÖöâ†&÷VæC¢7v—F6…&VE6W76–öâÂÆö6F–öã¢7G&–ærÂ&VC¢ÆVvVæG4&6WW56Æ÷E&VB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢–b‡&VB—2ÆVvVæG4&6WW56Æ÷E&VBåö¶VÖöâ’°¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR†Æö6F–öâ¢ç6WDÖW76vR‚‡&VB3òÆVvVæG4&6WW56Æ÷E&VBä–çfÆ–B“òç&V6öâó¢%F†—26Æ÷B—2V×G’â"¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢&WGW&à¢Ð¢fÂÒ&VBçfÇVP¢fÂvVæFW"ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’ævWD÷$çVÆÂ‡ævVæFW"’ó¢%Væ¶æ÷vâ‚G·ævVæFW'Ò’ ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G´ÆVvVæG4&6WW57V6–W4æÖW2ææÖR‡ç7V6–W2—Ò‚2G·ç7V6–W7Ò’(
"f÷&ÒG·æf÷&×ÕÆâ"¢VæB‚$æ–6¶æÖS¢G·ææ–6¶æÖRæ–d&Ææ²²$æöæR"×ÕÆävVæFW#¢FvVæFW%Æâ"¢VæB‚$Ç†¢G·æ—4Ç†ÕÆäæö&ÆS¢G·æ—4æö&ÆWÕÆå6†–ç“¢G·ç6†–ç—ÕÆå6fVBVvrfÆs¢G·æ—4VvwÕÆâ"¢VæB‚$U…¢G·æW‡W&–Væ6WÕÆäæGW&R”C¢G·ææGW&WÕÆå7FBæGW&R”C¢G·ç7FDæGW&WÕÆâ"¢VæB‚%6fVB7W'&VçB…¢G·æ7W'&VçD‡ÕÆâ"¢–b‡æ66†VDÆWfVÂÒçVÆÂ’°¢VæB‚$66†VB'G’ÆWfVÃ¢G·æ66†VDÆWfVÇÕÆä66†VBÖ†–×VÒ…¢G·æÖ†–×VÔ‡ÕÆâ"¢VæB‚$66†VBF²ôFVbõ7VVBõ7õ7C¢G·æ66†VE7FG3òæ¦ö–åFõ7G&–ær‚"ò"—ÕÆâ"¢ÒVÇ6RVæB‚%7GW&R&V6÷&C¢ÆWfVÂÂÖ†–×VÒ…æB'G’7FG2&Ræ÷B7F÷&VB–âF†—2&V6÷&BåÆâ"¢VæB‚$&ÆÂ”C¢G·æ&ÆÇÕÆåÆä÷&–v–æÂG&–æW#¢G·æ÷&–v–æÅG&–æW'ÕÆåD”Cc¢G·çF–GÕÆå4”Cc¢G·ç6–GÕÆâ"¢VæB‚$õBg&–VæG6†—¢G·æ÷&–v–æÅG&–æW$g&–VæG6†—ÕÆä†æFÆ–ær×G&–æW"g&–VæG6†—¢G·æ†æFÆ–æuG&–æW$g&–VæG6†—ÕÆâ"¢fÂ†æFÆW"Òv†Vâ‡æ7W'&VçD†æFÆW"’²Óâ$÷&–v–æÂG&–æW"#²Óâ$†æFÆ–ærG&–æW"#²VÇ6RÓâ%Væ¶æ÷vâ‚G·æ7W'&VçD†æFÆW'Ò’"Ð¢VæB‚$7W'&VçB†æFÆW#¢F†æFÆW%Æâ"¢VæB‚%”C¢G·ç–BçFõ7G&–ærƒb’çWW&66R‚—ÕÆäVæ7'—F–öâ6öç7FçC¢G·æVæ7'—F–öä6öç7FçBçFõ7G&–ærƒb’çWW&66R‚—ÕÆåÆäÖ÷fW5Æâ"¢æÖ÷fW2æf÷$V6„–æFW†VB²’ÂÖ÷fRÓâVæB‚"G¶’²ÒâÖ÷fR2G¶Ö÷fRæ–GÒ(
"G¶Ö÷fRçÕÆâ"’Ð¢VæB‚%6fVB&VÆV&âÖ÷fR”G3¢G·ç&VÆV&äÖ÷fW2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆåÆâ"¢VæB‚$•g2„…ôF²ôFVbõ7VVBõ7õ7B“¢G·æ—g2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆâ"¢VæB‚%6fVBvæ&'RfÇVW2‡6ÖR÷&FW"“¢G·ævæ&'UfÇVW2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆâ"¢VæB‚%F†W6R&R&r6fVBG&–æ–ær–æ7&VÖVçG3²Vff÷'BÆWfVÇ2W6RF†V—"•bÖ&6VBæF—fR&–2ÂæB66†VB'G’7FG2&Ræ÷B&V6Æ7VÆFVBåÆåÆâ"¢VæB‚%‚7F÷&VBÖFF6†V6·7VÒ76VBâ66†VB'G’7FG2&R÷WG6–FRF†B6†V6·7VÒæBÖ’&R7FÆRâ"¢VæB‚$æF—fRæ–6¶æÖRÂÇ†×7FGW2Âæö&ÆR×7FGW2Â6†–ç’×7FGW2Â6—¦RÂÖ÷fR6†÷ÂÇ†Ö÷fRÂ6GW&R&ÆÂæBVff÷'BÆWfVÂVF—F–ær&Rf–Æ&ÆRf÷"'G’æB7GW&R&V6÷&G3²ÆVvÆ—G’æBvÖR×&W6Væ6R6†V6·2&Ræ÷BWfÇVFVBâ"¢Ð¢fÂ6öçFVçBÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4ÂÐ¢6öçFVçBæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ7GW&TÖF6‚Ò&VvW‚‚%å7GW&R…³Ó•Ò²’(
"6Æ÷B…³Ó•Ò²’B"’æÖF6„VçF—&R†Æö6F–öâ¢fÂ'G”ÖF6‚Ò&VvW‚‚%å'G’…³Ó•Ò²’B"’æÖF6„VçF—&R†Æö6F–öâ¢fÂæF—fUF&vWBÒv†Vâ°¢7GW&TÖF6‚ÒçVÆÂÓâG&—ÆR„ÆVvVæG4&6WW5ö¶VÖöä&Vå5EU$RÀ¢7GW&TÖF6‚æw&÷WfÇVW5³ÒçFô–çB‚’Â7GW&TÖF6‚æw&÷WfÇVW5³%ÒçFô–çB‚’¢'G”ÖF6‚ÒçVÆÂÓâG&—ÆR„ÆVvVæG4&6WW5ö¶VÖöä&Vå%E’ÂÂ'G”ÖF6‚æw&÷WfÇVW5³ÒçFô–çB‚’¢VÇ6RÓâçVÆÀ¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ—4æö&ÆR’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BÇ†7FGW2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöäÇ†7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvr’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bæö&ÆR7FGW2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöäæö&ÆT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ—4æö&ÆR’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B6†–ç’7FGW2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöå6†–ç”7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ—4æö&ÆRbbæ—4Ç†’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BæF—fR6—¦R ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöå6—¦T7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ—4æö&ÆR’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BÖ÷fR6†÷W&6†6RòÖ7FW'’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöäÖ÷fU6†÷7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ—4æö&ÆRbbæ—4Ç†’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BÇ†Ö÷fR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöäÇ†Ö÷fT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ—4æö&ÆRbbæfFVgVÄVæ6÷VçFW"b`¢‡æ&ÆÂ–â#‚âã3bÇÂ‡ç7V6–W2–âCƒ2âãCƒBbbæ&ÆÂÓÒ3r’’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B6GW&R&ÆÂ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöä&ÆÄ7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbÆVvVæG4&6WW5ö¶VÖöäVff÷'DVF—F–æræ†4VF—F&ÆU7FB‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BVff÷'BÆWfVÇ2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöäVff÷'D7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ—4æö&ÆRbbæf÷&ÒÓÒb`¢ÆVvVæG4&6WW5ö¶VÖöäWföÇWF–öäVF—F–æræ6†ö–6W2‡ç7V6–W2’æ—4æ÷DV×G’‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$WföÇfRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöäWföÇWF–öä7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG4&6WW5ö¶VÖöäæ–6¶æÖT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢fÂ&æ²Ò'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$FBFò4R&æ² ¢—4ÆÄ62ÒfÇ6P¢Ð¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$GWÆ–6FRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG4&6WW5ö¶VÖöäGWÆ–6FT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢–b†æF—fUF&vWBÒçVÆÂbbæF—fUF&vWBæf—'7BÓÒÆVvVæG4&6WW5ö¶VÖöä&Vå5EU$Rb`¢æ—4Vvrbbæ—4æö&ÆR’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%7vv—F‚ö¼:–Ööâ–âF†—2&÷‚ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG4&6WW5ö¶VÖöå7v7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ—4æö&ÆR’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ–b†æF—fUF&vWBæf—'7BÓÒÆVvVæG4&6WW5ö¶VÖöä&Vå5EU$R¢$Ö÷fRFò'G’"VÇ6R$Ö÷fRFò2&÷‚ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöå'G”Ö÷fT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr†&æ²¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$FVÆWFRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG4&6WW5ö¶VÖöäFVÆWFT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç7GW&R"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†6öçFVçB’Ð¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"FÆö6F–öâ(
"ÆVvVæG3¢&6WW2"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’æ7&VFR‚¢–b†æF—fUF&vWBÒçVÆÂ’°¢F–Æörç6WDöå6†÷tÆ—7FVæW"°¢&æ²ç6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÒ&÷VæB’²F–ÆöræF—6Ö—72‚“²&WGW&ä6WDöä6Æ–6´Æ—7FVæW"Ð¢G'’°¢fÂ&rÒÆVvVæG4&6WW5&V6÷&D'—FW2†&÷VæBÂÆö6F–öâ¢fÂVçG'’Ò7v—F6…6T&æµ7F÷&RæFW÷6—DÆVvVæG4&6WW2‡F†—2Â&r¢F–ÆöræF—6Ö—72‚¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$FFVBFò4R&æ²"¢ç6WDÖW76vR‡ææ–6¶æÖRæ–d&Ææ²²ÆVvVæG4&6WW57V6–W4æÖW2ææÖR‡ç7V6–W2’Ò°¢"6÷–VB2æF—fR‚âF†R7v—F6‚6fRv2æ÷B6†ævVBåÆåÆâ"²VçG'’ææÖR¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ò6F6‚†W'&÷#¢W†6WF–öâ’°¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$6÷VÆBæ÷BFBFò4R&æ²"¢ç6WDÖW76vR†W'&÷"æÖW76vRó¢%F†R6fR6÷VÆBæ÷B&R&VBv–ââ"¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð¢Ð¢Ð¢Ð¢F–Æörç6†÷r‚¢Ð ¢&—fFRgVâÆVvVæG4&6WW5&V6÷&D'—FW2†&÷VæC¢7v—F6…&VE6W76–öâÂÆö6F–öã¢7G&–ær“¢'—FT'&’°¢fÂW&’Ò6VÆV7FVE6÷W&6UW&’ó¢F‡&÷r–ÆÆVvÅ7FFTW†6WF–öâ‚%F†R6VÆV7FVB6fR—2æòÆöævW"f–Æ&ÆRâ"¢fÂ÷&–v–æÂÒ&÷VæBç6æ6†÷Bæ'—FW46÷’‚¢fÂ7W'&VçBÒ6öçFVçE&W6öÇfW"æ÷Vä–çWE7G&VÒ‡W&’“òçW6R²–çWBÓà¢fÂ'—FW2Ò–çWBç&VD'—FW2‚¢&WV—&R†'—FW2ç6—¦RÃÒb¢#B¢#B’²%F†R6VÆV7FVBf–ÆRW†6VVG2F†RbÖ”"–çF¶RÆ–Ö—Bâ"Ð¢'—FW0¢Òó¢F‡&÷r¦fæ–òä”ôW†6WF–öâ‚%F†R6VÆV7FVB6fR6÷VÆBæ÷B&R&V÷VæVBâ"¢fÂF–vW7BÒ¦fç6V7W&—G’äÖW76vTF–vW7BævWD–ç7Fæ6R‚%4„Ó#Sb"¢&WV—&R†¦fç6V7W&—G’äÖW76vTF–vW7Bæ—4WVÂ†F–vW7BæF–vW7B†÷&–v–æÂ’ÂF–vW7BæF–vW7B†7W'&VçB’’’°¢%F†R6VÆV7FVB6fR6†ævVBgFW"—Bv2÷VæVBâ&V÷Vâ—B&Vf÷&RFF–ærö¼:–ÖöâFò&æ²â ¢Ð¢&WGW&â&÷VæBç6æ6†÷Bç&VDÆVvVæG4&6WW5&V6÷&B†Æö6F–öâ¢Ð ¢&—fFRgVâ66&ÆWEf–öÆWDÆ&VÂ‡&VC¢66&ÆWEf–öÆWE6Æ÷E&VB“¢7G&–ærÒv†Vâ‡&VB’°¢66&ÆWEf–öÆWE6Æ÷E&VBäV×G’Óâ$V×G’ ¢—266&ÆWEf–öÆWE6Æ÷E&VBä–çfÆ–BÓâ%Vç&VF&ÆRö¼:–Ööâ(
"G·&VBç&V6öçÒ ¢—266&ÆWEf–öÆWE6Æ÷E&VBåö¶VÖöâÓâ°¢fÂÒ&VBçfÇVP¢fÂ7V6–W2Ò66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR‡ç7V6–W2¢fÂæ–6²Òææ–6¶æÖRçF¶T–b²—Bæ—4æ÷D&Ææ²‚’bb—BÒ7V6–W2Ð¢†æ–6³òæÆWB²"F—B‚G7V6–W2’"Òó¢7V6–W2’²"(
"66†VBÇbG·æ66†VDÆWfVÇÒ"°¢†–b‡ç6†–ç’’"(
"6†–ç’"VÇ6R""’²†–b‡æ—4Vvr’"(
"Vvr"VÇ6R""¢Ð¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWE'G’†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7F÷&vRÒ&÷VæBç66&ÆWEf–öÆWE7F÷&vRó¢&WGW&à¢fÂ'G’Ò7F÷&vRç'G’‚¢fÂÆ&VÇ2Ò'G’æÖ²%'G’G¶—Bç÷6—F–öçÓ¢G·66&ÆWEf–öÆWDÆ&VÂ†—Bç&VB—Ò"Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"'G’G·7F÷&vRç'G”6÷VçGÒób"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWEö¶VÖöâ†&÷VæBÂ%'G’G·'G•¶–æFW…Òç÷6—F–öçÒ"Â'G•¶–æFW…Òç&VB¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWD&÷‚†&÷VæC¢7v—F6…&VE6W76–öâÂ&÷ƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7F÷&vRÒ&÷VæBç66&ÆWEf–öÆWE7F÷&vRó¢&WGW&à¢fÂÆ—7BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4ÂÐ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†Æ—7B’Ð¢Æ—7BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ#3"æF—fR&÷†W2(
"36Æ÷G2V6‚(
"æF—fRæ–6¶æÖRVF—F–ærf–Æ&ÆUÆâ"°¢"G·7F÷&vRæö67W–VD&÷…6Æ÷G7Òó“cö67W–VB6Æ÷G2‚G·7F÷&vRçVç&VF&ÆT&÷…6Æ÷G7ÒVç&VF&ÆR’â"°¢%F†RFVF–6FVB&–FRö¼:–Ööâ—26†÷vâ6W&FVÇ’â&÷‚æÖW2ÂvÆÇW'2æBVæÆö6²7FFR&Ræ÷B&VB–âF†—2f–Wrâ ¢6WEFF–ærƒbÂbÂbÂb¢Ò¢f÷"‡6Æ÷B–â7F÷&vRæ&÷‚†&÷‚’’Æ—7BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6Æ÷BG·6Æ÷Bç÷6—F–öçÓ¢G·66&ÆWEf–öÆWDÆ&VÂ‡6Æ÷Bç&VB—Ò ¢—4ÆÄ62ÒfÇ6S²—4Væ&ÆVBÒ6Æ÷Bç&VB—266&ÆWEf–öÆWE6Æ÷E&VBäV×G¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWEö¶VÖöâ†&÷VæBÂ$&÷‚G¶&÷‚²Ò(
"6Æ÷BG·6Æ÷Bç÷6—F–öçÒ"Â6Æ÷Bç&VB’Ð¢Ò¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"&÷‚G¶&÷‚²Òó3""¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’æ7&VFR‚¢fÂæbÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBä„õ$•¤ôåDÂÐ¢æbæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%&Wf–÷W2#²—4Væ&ÆVBÒ&÷‚â ¢6WDöä6Æ–6´Æ—7FVæW"²F–ÆöræF—6Ö—72‚“²–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWD&÷‚†&÷VæBÂ&÷‚Ò’Ð¢ÒÂÆ–æV$Æ–÷WBäÆ–÷WE&×2ƒÂÓ"Âb’¢æbæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$æW‡B#²—4Væ&ÆVBÒ&÷‚Â3¢6WDöä6Æ–6´Æ—7FVæW"²F–ÆöræF—6Ö—72‚“²–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWD&÷‚†&÷VæBÂ&÷‚²’Ð¢ÒÂÆ–æV$Æ–÷WBäÆ–÷WE&×2ƒÂÓ"Âb’¢Æ—7BæFEf–Wr†æb“²F–Æörç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWEö¶VÖöâ†&÷VæC¢7v—F6…&VE6W76–öâÂÆö6F–öã¢7G&–ærÂ&VC¢66&ÆWEf–öÆWE6Æ÷E&VB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢–b‡&VB—266&ÆWEf–öÆWE6Æ÷E&VBåö¶VÖöâ’°¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR†Æö6F–öâ¢ç6WDÖW76vR‚‡&VB3ò66&ÆWEf–öÆWE6Æ÷E&VBä–çfÆ–B“òç&V6öâó¢%F†—26Æ÷B—2V×G’â"¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢&WGW&à¢Ð¢fÂÒ&VBçfÇVP¢fÂvVæFW"ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’ævWD÷$çVÆÂ‡ævVæFW"’ó¢%Væ¶æ÷vâ‚G·ævVæFW'Ò’ ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"Gµ66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR‡ç7V6–W2—Ò‚2G·ç7V6–W7Ò’(
"f÷&ÒG·æf÷&×ÕÆâ"¢VæB‚$æ–6¶æÖS¢G·ææ–6¶æÖRæ–d&Ææ²²$æöæR"×ÕÆävVæFW#¢FvVæFW%ÆäVvrfÆs¢G·æ—4VvwÕÆâ"¢VæB‚$66†VBÆWfVÃ¢G·æ66†VDÆWfVÇÕÆäU…¢G·æW‡W&–Væ6WÕÆâ"¢VæB‚$æGW&S¢Gµ66&ÆWEf–öÆWEö¶VÖöäæGW&TVF—F–æræFW67&–&R‡ææGW&R—ÕÆâ"¢VæB‚%7FBæGW&S¢Gµ66&ÆWEf–öÆWEö¶VÖöäæGW&TVF—F–æræFW67&–&R‡ç7FDæGW&R—ÕÆâ"¢VæB‚$66†VB…¢G·æ7W'&VçD‡ÒòG·æÖ†–×VÔ‡ÕÆâ"¢VæB‚$66†VBF²ôFVbõ7VVBõ7õ7C¢G·æ66†VE7FG2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆâ"¢VæB‚%6†–ç“¢G¶–b‡ç6†–ç’’%–W2"VÇ6R$æò'ÕÆâ"¢VæB‚$†VÆB—FVÓ¢Gµ66&ÆWEf–öÆWEö¶VÖöä†VÆD—FVÔVF—F–ærææÖR‡æ†VÆD—FVÒ—ÕÆâ"¢VæB‚$&–Æ—G“¢Gµ66&ÆWEf–öÆWD&–Æ—G”6FÆöwVRæFW67&–&R‡æ&–Æ—G’Âæ&–Æ—G”çVÖ&W"—ÕÆâ"¢VæB‚$õBg&–VæG6†—¢G·æ÷&–v–æÅG&–æW$g&–VæG6†—ÕÆä†æFÆ–ær×G&–æW"g&–VæG6†—¢G·æ†æFÆ–æuG&–æW$g&–VæG6†—ÕÆâ"¢fÂ†æFÆW"Òv†Vâ‡æ7W'&VçD†æFÆW"’²Óâ$÷&–v–æÂG&–æW"#²Óâ$†æFÆ–ærG&–æW"#²VÇ6RÓâ%Væ¶æ÷vâ‚G·æ7W'&VçD†æFÆW'Ò’"Ð¢VæB‚$7W'&VçB†æFÆW#¢F†æFÆW%Æâ"¢fÂVffV7F—fUFW&Ò66&ÆWEf–öÆWEFW&G—W2æVffV7F—fR‡çFW&G—T÷&–v–æÂÂçFW&G—T÷fW'&–FR¢VæB‚$÷&–v–æÂFW&G—S¢Gµ66&ÆWEf–öÆWEFW&G—W2ææÖR‡çFW&G—T÷&–v–æÂ—Ò‚G·çFW&G—T÷&–v–æÇÒ•Æâ"¢VæB‚%6fVBFW&÷fW'&–FS¢G·çFW&G—T÷fW'&–FWÕÆâ"¢VæB‚$VffV7F—fRFW&G—S¢Gµ66&ÆWEf–öÆWEFW&G—W2ææÖR†VffV7F—fUFW&—ÕÆâ"¢VæB‚$æF—fR6—¦S¢Gµ66&ÆWEf–öÆWEö¶VÖöå66ÆTVF—F–æræÆ&VÂ‡ç66ÆR—ÕÆâ"¢VæB‚$æF—fR6—¦R66Æ'3¢Gµ66&ÆWEf–öÆWEö¶VÖöå6—¦U66Æ$VF—F–æræFW67&–&R‡ç6—¦U66Æ'2—ÕÆâ"¢VæB‚$ö&VF–Væ6RÆWfVÃ¢G·æö&VF–Væ6TÆWfVÇÕÆâ"¢VæB‚$÷&–v–âvÖS¢Gµ66&ÆWEf–öÆWEö¶VÖöä÷&–v–ävÖTVF—F–ærææÖR‡æ÷&–v–ävÖR—Ò‚2G·æ÷&–v–ävÖWÒ•Æâ"¢VæB‚$&GFÆRfW'6–öã¢Gµ66&ÆWEf–öÆWEö¶VÖöä&GFÆUfW'6–öäVF—F–ærææÖR‡æ&GFÆUfW'6–öâ—Ò‚2G·æ&GFÆUfW'6–öçÒ•Æâ"¢VæB‚$f÷&Ò&wVÖVçC¢G·æf÷&Ô&wVÖVçGÕÆâ"¢VæB‚$6GW&R&ÆÃ¢Gµ66&ÆWEf–öÆWEö¶VÖöä&ÆÄVF—F–ærææÖR‡æ&ÆÂ—Ò‚2G·æ&ÆÇÒ•Æâ"¢VæB‚$ÖWBÆWfVÃ¢G·æÖWDÆWfVÇÕÆâ"¢VæB‚$‡—W"G&–æ–æs¢Gµ66&ÆWEf–öÆWEö¶VÖöä‡—W%G&–æ–ætVF—F–ærç7VÖÖ'’‡æ‡—W%G&–æ–ætfÆw2—ÕÆâ"¢VæB‚$„ôÔRG&6¶W#¢"¢VæB†–b…66&ÆWEf–öÆWEö¶VÖöä†öÖUG&6¶W$VF—F–æræ†5G&6¶W"‡’¢#‚Gµ66&ÆWEf–öÆWEö¶VÖöä†öÖUG&6¶W$VF—F–ærçG&6¶W$†W‚‡—Ò"VÇ6R$æöæR"¢VæB‚%Æâ"¢VæB‚$&GFÆR7FGW3¢"¢VæB†–b…66&ÆWEf–öÆWEö¶VÖöå7FGW46öæF—F–öäVF—F–æræ†57FGW2‡’¢%&r‚Gµ66&ÆWEf–öÆWEö¶VÖöå7FGW46öæF—F–öäVF—F–ærç7FGW4†W‚‡—Ò ¢VÇ6R$†VÇF‡’ƒƒ’"¢VæB‚%Æâ"¢VæB‚%&V6÷&FVB7W'&VçBDÒÖ÷fW3¢Gµ66&ÆWEf–öÆWEö¶VÖöåFV6†æ–6ÄÖ6†–æTVF—F–ærç&V6÷&FVD7W'&VçDÖ÷fW2‡—ÕÆâ"¢–b‡æ7W'&VçD†æFÆW"–ââã’°¢VæB‚$g&–VæG6†—¢Gµ66&ÆWEf–öÆWEö¶VÖöäg&–VæG6†—VF—F–æræ7W'&VçDg&–VæG6†—‡—Ò"¢VæB‚"‚Gµ66&ÆWEf–öÆWEö¶VÖöäg&–VæG6†—VF—F–æræ†æFÆW$Æ&VÂ‡—Ò•Æâ"¢ÒVÇ6RVæB‚$g&–VæG6†—¢Vç7W÷'FVB7W'&VçBÖ†æFÆW"fÇVRG·æ7W'&VçD†æFÆW'ÕÆâ"¢VæB‚$VvrFFS¢Gµ66&ÆWEf–öÆWEö¶VÖöäVvtFFTVF—F–æræVvtFFR‡’ó¢$–çfÆ–BöV×G’'ÕÆâ"¢VæB‚$æF—fRVvrÆö6F–öã¢G·æVvtÆö6F–öçÕÆâ"¢VæB‚$ÖWBFFS¢Gµ66&ÆWEf–öÆWEö¶VÖöäÖWDFFTVF—F–æræÖWDFFR‡’ó¢$–çfÆ–BöV×G’'ÕÆâ"¢VæB‚$æF—fRÖWBÆö6F–öã¢G·æÖWDÆö6F–öçÕÆâ"¢VæB‚$VffV7F—fRFW&G—RföÆÆ÷w2æF—fR÷fW'&–FR6VÖçF–73²&GFÆRöf÷&ÒVffV7G2æBVæ6÷VçFW"ÆVvÆ—G’&Ræ÷BWfÇVFVBåÆåÆâ"¢VæB‚$÷&–v–æÂG&–æW#¢G·æ÷&–v–æÅG&–æW'ÕÆâ"¢VæB‚$÷&–v–æÂG&–æW"vVæFW#¢Gµ66&ÆWEf–öÆWEö¶VÖöä÷&–v–æÅG&–æW$vVæFW$VF—F–ærææÖR‡æ÷&–v–æÅG&–æW$vVæFW"—ÕÆâ"¢–b‡æ†æFÆ–æuG&–æW"æ—4æ÷D&Ææ²‚’’°¢VæB‚$†æFÆ–ærG&–æW#¢G·æ†æFÆ–æuG&–æW"æ–d&Ææ²²$æ÷B6WB"×ÕÆâ"¢VæB‚$†æFÆ–ærG&–æW"vVæFW#¢Gµ66&ÆWEf–öÆWEö¶VÖöä†æFÆ–æuG&–æW$vVæFW$VF—F–ærææÖR‡æ†æFÆ–æuG&–æW$vVæFW"—ÕÆâ"¢VæB‚$†æFÆ–ærG&–æW"ÆæwVvS¢Gµ66&ÆWEf–öÆWEö¶VÖöä†æFÆ–æuG&–æW$ÆæwVvTVF—F–ærææÖR‡æ†æFÆ–æuG&–æW$ÆæwVvR—ÕÆâ"¢Ð¢VæB‚%ö¼:–ÖöâÆæwVvS¢Gµ66&ÆWEf–öÆWEö¶VÖöäÆæwVvTVF—F–ærææÖR‡æÆæwVvR—ÕÆâ"¢VæB‚$7F—fRF—FÆS¢Gµ66&ÆWEf–öÆWEö¶VÖöäff—†VEF—FÆTVF—F–ærææÖR‡æff—†VE&–&&öâ—ÕÆâ"¢VæB‚%ö¼:—'W3¢Gµ66&ÆWEf–öÆWEö¶VÖöåö¶W'W4VF—F–æræFW67&–&R‡çö¶W'W57G&–âÂçö¶W'W4F—2—ÕÆâ"¢VæB‚$6öçFW7B6öæF—F–öã¢Gµ66&ÆWEf–öÆWEö¶VÖöä6öçFW7D6öæF—F–öäVF—F–æræFW67&–&R‡æ6öçFW7E7FG2—ÕÆâ"¢VæB‚$&÷‚Ö&¶–æw3¢Gµ66&ÆWEf–öÆWEö¶VÖöäÖ&¶–ætVF—F–æræFW67&–&R‡æÖ&¶–æt6öÆ÷'2—ÕÆâ"¢VæB‚%D”Cc¢G·çF–GÕÆå4”Cc¢G·ç6–GÕÆâ"¢VæB‚%”C¢G·ç–BçFõ7G&–ærƒb’çWW&66R‚—ÕÆäVæ7'—F–öâ6öç7FçC¢G·æVæ7'—F–öä6öç7FçBçFõ7G&–ærƒb’çWW&66R‚—ÕÆåÆäÖ÷fW5Æâ"¢æÖ÷fW2æf÷$V6„–æFW†VB²’ÂÖ÷fRÓâVæB‚"G¶’²ÒâÖ÷fR2G¶Ö÷fRæ–GÒ(
"G¶Ö÷fRçÒ(
"W2G¶Ö÷fRçW7ÕÆâ"’Ð¢VæB‚%&VÆV&âÖ÷fR”G3¢G·ç&VÆV&äÖ÷fW2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆåÆâ"¢VæB‚$•g2„…ôF²ôFVbõ7VVBõ7õ7B“¢G·æ—g2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆâ"¢VæB‚$Ug2‡6ÖR÷&FW"“¢G·æWg2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆåÆâ"¢VæB‚%³’7F÷&VBÖFF6†V6·7VÒ76VBâ66†VB'G’7FG2&R÷WG6–FRF†B6†V6·7VÒæBÖ’&R7FÆRÂW7V6–ÆÇ’–â&÷†W2â"¢VæB‚$æF—fRæ–6¶æÖRVF—F–ær—2f–Æ&ÆRf÷"'G’æB2Ö&÷‚&V6÷&G2âF†RFVF–6FVB&–FR&V6÷&B&VÖ–ç2'&÷w6RÖöæÇ“²ÆVvÆ—G’æBvÖR×&W6Væ6R6†V6·2&Ræ÷BWfÇVFVBâ"¢Ð¢fÂ6öçFVçBÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4ÂÐ¢6öçFVçBæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ&÷„ÖF6‚Ò&VvW‚‚%ä&÷‚…³Ó•Ò²’(
"6Æ÷B…³Ó•Ò²’B"’æÖF6„VçF—&R†Æö6F–öâ¢fÂ'G”ÖF6‚Ò&VvW‚‚%å'G’…³Ó•Ò²’B"’æÖF6„VçF—&R†Æö6F–öâ¢fÂæF—fUF&vWBÒv†Vâ°¢&÷„ÖF6‚ÒçVÆÂÓâG&—ÆR…66&ÆWEf–öÆWEö¶VÖöä&Vä$õ‚À¢&÷„ÖF6‚æw&÷WfÇVW5³ÒçFô–çB‚’Â&÷„ÖF6‚æw&÷WfÇVW5³%ÒçFô–çB‚’¢'G”ÖF6‚ÒçVÆÂÓâG&—ÆR…66&ÆWEf–öÆWEö¶VÖöä&Vå%E’ÂÂ'G”ÖF6‚æw&÷WfÇVW5³ÒçFô–çB‚’¢VÇ6RÓâçVÆÀ¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæf÷&ÒÓÒb`¢66&ÆWEf–öÆWEö¶VÖöäWföÇWF–öäVF—F–æræ6†ö–6W2†&÷VæBç6æ6†÷Bæ'—FW46÷’‚’Âç7V6–W2’æ—4æ÷DV×G’‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$WföÇfRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöäWföÇWF–öä7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbç7V6–W2Òrbbç7V6–W2Ò#Bb`¢66&ÆWEf–öÆWEFW&G—W2æ—4÷&–v–æÅfÆ–B‡çFW&G—T÷&–v–æÂ’b`¢66&ÆWEf–öÆWEFW&G—W2æ—4÷fW'&–FUfÆ–B‡çFW&G—T÷fW'&–FR’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BFW&G—R ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöåFW&G—T7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BæF—fR6—¦R ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöå66ÆT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöå6—¦U66Æ$VF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B†V–v‡BõvV–v‡B66Æ'2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöå6—¦U66Æ$7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bö&VF–Væ6RÆWfVÂ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäö&VF–Væ6T7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöä÷&–v–ävÖTVF—F–æræ6†ö–6W2æç’²—Bæ–BÓÒæ÷&–v–ävÖRÒ’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B÷&–v–âvÖR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä÷&–v–ävÖT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢æ&GFÆUfW'6–öâ–âÆ—7Döb€¢66&ÆWEf–öÆWEö¶VÖöä&GFÆUfW'6–öäVF—F–ærå5tõ$BÀ¢66&ÆWEf–öÆWEö¶VÖöä&GFÆUfW'6–öäVF—F–ærå4„”TÄ@¢’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$6ÆV"&GFÆRfW'6–öâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä&GFÆUfW'6–öä7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢fÂf÷&Ô&wVÖVçDÖ†–×VÒÒ66&ÆWEf–öÆWEö¶VÖöäf÷&Ô&wVÖVçDVF—F–æræÖ†–×VÒ‡¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢f÷&Ô&wVÖVçDÖ†–×VÒÒçVÆÂbbæf÷&Ô&wVÖVçB–ââæf÷&Ô&wVÖVçDÖ†–×VÒçFôÆöær‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bf÷&Ò&wVÖVçB ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäf÷&Ô&wVÖVçD7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöä&ÆÄVF—F–æræ6†ö–6W2æç’²—Bæ–BÓÒæ&ÆÂÒ’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B6GW&R&ÆÂ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä&ÆÄ7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"bbæÖWDÆWfVÂ–ââã’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BÖWBÆWfVÂ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäÖWDÆWfVÄ7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂb`¢66&ÆWEf–öÆWEö¶VÖöä‡—W%G&–æ–ætVF—F–æræVÆ–v–&ÆU7FG2‡’æ—4æ÷DV×G’‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$‡—W"G&–â7FB ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä‡—W%G&–æ–æt7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöäVff÷'EfÇVTVF—F–æræ—5&VF&ÆR‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ–b…66&ÆWEf–öÆWEö¶VÖöäVff÷'EfÇVTVF—F–æræ—5fÆ–B‡’¢$VF—BVff÷'BfÇVW2"VÇ6R$ÆVvÆ—6RVff÷'BfÇVW2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäVff÷'EfÇVT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöä–æF—f–GVÅfÇVTVF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B–æF—f–GVÂfÇVW2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä–æF—f–GVÅfÇVT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂb`¢66&ÆWEf–öÆWEö¶VÖöåFV6†æ–6ÄÖ6†–æTVF—F–æræ6†ö–6W2‡’æ—4æ÷DV×G’‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%&V6÷&BFV6†æ–6ÂÖ6†–æRW6R ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöåFV6†æ–6ÄÖ6†–æT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæ7W'&VçD†æFÆW"–ââã’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bg&–VæG6†— ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäg&–VæG6†—7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"bbæÖWDÆö6F–öâÒb`¢66&ÆWEf–öÆWEö¶VÖöäÖWDFFTVF—F–æræÖWDFFR‡’ÒçVÆÂ’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BÖWBFFR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäÖWDFFT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BÖWBÆö6F–öâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäÖWDÆö6F–öä7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöä†öÖUG&6¶W$VF—F–æræ†5G&6¶W"‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$6ÆV"„ôÔRG&6¶W" ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä†öÖUG&6¶W$7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöå7FGW46öæF—F–öäVF—F–æræ†57FGW2‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$6ÆV"&GFÆR7FGW2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöå7FGW46öæF—F–öä7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbb66&ÆWEf–öÆWEö¶VÖöä7W'&VçD‡VF—F–æræ6å&W7F÷&R‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%&W7F÷&R7W'&VçB… ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä7W'&VçD‡7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbb66&ÆWEf–öÆWEö¶VÖöäÖ†–×VÔ‡VF—F–æræ6äVF—B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BÖ†–×VÒ… ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäÖ†–×VÔ‡7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæfFVgVÄVæ6÷VçFW"bbæVvtÆö6F–öâÒb`¢66&ÆWEf–öÆWEö¶VÖöäVvtFFTVF—F–æræVvtFFR‡’ÒçVÆÂ’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BVvrFFR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäVvtFFT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BVvrÆö6F–öâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäVvtÆö6F–öä7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B÷&–v–æÂG&–æW"vVæFW" ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä÷&–v–æÅG&–æW$vVæFW$7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæfFVgVÄVæ6÷VçFW"bbæ7W'&VçD†æFÆW"ÓÒb`¢æ†æFÆ–æuG&–æW$vVæFW"–ââãb`¢æ†æFÆ–æuG&–æW$ÆæwVvR–â66&ÆWEf–öÆWEö¶VÖöä†æFÆ–æuG&–æW$ÆæwVvTVF—F–ærçfÇVW2‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B†æFÆ–ærG&–æW"æÖR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä†æFÆ–æuG&–æW$7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæfFVgVÄVæ6÷VçFW"bbæ7W'&VçD†æFÆW"ÓÒb`¢æ†æFÆ–æuG&–æW$vVæFW"–ââã’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B†æFÆ–ærG&–æW"vVæFW" ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä†æFÆ–æuG&–æW$vVæFW$7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæfFVgVÄVæ6÷VçFW"bbæ7W'&VçD†æFÆW"ÓÒb`¢æ†æFÆ–æuG&–æW$ÆæwVvR–â66&ÆWEf–öÆWEö¶VÖöä†æFÆ–æuG&–æW$ÆæwVvTVF—F–ærçfÇVW2‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B†æFÆ–ærG&–æW"ÆæwVvR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä†æFÆ–æuG&–æW$ÆæwVvT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöä7W'&VçD†æFÆW$VF—F–æræ6å7v—F6‚‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B7W'&VçB†æFÆW" ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä7W'&VçD†æFÆW$7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæfFVgVÄVæ6÷VçFW"b`¢æÆæwVvR–â66&ÆWEf–öÆWEö¶VÖöäÆæwVvTVF—F–ærçfÇVW2‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bö¼:–ÖöâÆæwVvR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäÆæwVvT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbb66&ÆWEf–öÆWEö¶VÖöäff—†VEF—FÆTVF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B7F—fRF—FÆR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäff—†VEF—FÆT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöäff—†VEF—FÆTVF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B&–&&öç2æBÖ&·2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöå&–&&öäÖ&´7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöåö¶W'W4VF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bö¼:—'W2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöåö¶W'W47F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöä6öçFW7D6öæF—F–öäVF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B6öçFW7B6öæF—F–öâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä6öçFW7D6öæF—F–öä7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöäÖ&¶–ætVF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B&÷‚Ö&¶–æw2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäÖ&¶–æt7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöä†VÆD—FVÔVF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B†VÆB—FVÒ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä†VÆD—FVÔ7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöä&–Æ—G”VF—F–æræ—5fÆ–B‡’b`¢66&ÆWEf–öÆWD&–Æ—G”6FÆöwVRæ6†ö–6W2‡ç7V6–W2Âæf÷&Ò’æç’°¢—Bæ&–Æ—G’Òæ&–Æ—G’ÇÂ—Bæ&–Æ—G”çVÖ&W"Òæ&–Æ—G”çVÖ&W ¢Ò’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B&–Æ—G’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöä&–Æ—G”7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöäæGW&TVF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BæGW&R ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäæGW&T7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrb`¢66&ÆWEf–öÆWEö¶VÖöå7FDæGW&TVF—F–æræ—5fÆ–B‡’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B7FBæGW&R ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöå7FDæGW&T7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B6†–ç’7FGW2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöå6†–ç”7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B6V7&WB”B ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöå6V7&WD–D7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BVæ7'—F–öâ6öç7FçB ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöäVæ7'—F–öä6öç7FçD7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BvVæFW" ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöävVæFW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEW'6öæÄ6FÆöwVRæf÷&×2‡ç7V6–W2’ç6—¦Râ’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bf÷&Ò ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöäf÷&Ô7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢ç7V6–W2Òrbbç7V6–W2Ò#Bb`¢66&ÆWEf–öÆWEFW&G—W2æ—4÷&–v–æÅfÆ–B‡çFW&G—T÷&–v–æÂ’b`¢66&ÆWEf–öÆWEFW&G—W2æ—4÷fW'&–FUfÆ–B‡çFW&G—T÷fW'&–FR’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B÷&–v–æÂFW&G—R ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöä÷&–v–æÅFW&G—T7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢66&ÆWEf–öÆWEö¶VÖöå&VÆV&äÖ÷fSVF—F–æræ—4VF—F&ÆR‡ç&VÆV&äÖ÷fW2’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B&VÆV&âÖ÷fR ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöå&VÆV&äÖ÷fS7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢‡ç&VÆV&äÖ÷fW2æf—'7D÷$çVÆÂ‚’ó¢’Òb`¢66&ÆWEf–öÆWEö¶VÖöå&VÆV&äÖ÷fS$VF—F–æræ—4VF—F&ÆR‡ç&VÆV&äÖ÷fW2’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B&VÆV&âÖ÷fR" ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöå&VÆV&äÖ÷fS$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢ç&VÆV&äÖ÷fW2çF¶Rƒ"’æÆÂ²—BÒÒb`¢66&ÆWEf–öÆWEö¶VÖöå&VÆV&äÖ÷fS4VF—F–æræ—4VF—F&ÆR‡ç&VÆV&äÖ÷fW2’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B&VÆV&âÖ÷fR2 ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöå&VÆV&äÖ÷fS47F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"b`¢ç&VÆV&äÖ÷fW2çF¶Rƒ2’æÆÂ²—BÒÒb`¢66&ÆWEf–öÆWEö¶VÖöå&VÆV&äÖ÷fSDVF—F–æræ—4VF—F&ÆR‡ç&VÆV&äÖ÷fW2’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—B&VÆV&âÖ÷fRB ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöå&VÆV&äÖ÷fSD7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4VvrbbæfFVgVÄVæ6÷VçFW"’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—BW'6öæÆ—G’”B…”B’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöåW'6öæÆ—G”–D7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöäæ–6¶æÖT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$GWÆ–6FRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöäGWÆ–6FT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢–b†æF—fUF&vWBÒçVÆÂbbæF—fUF&vWBæf—'7BÓÒ66&ÆWEf–öÆWEö¶VÖöä&Vä$õ‚bbæ—4Vvr’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%7vv—F‚ö¼:–Ööâ–âF†—2&÷‚ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEö¶VÖöå7v7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvr’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ–b†æF—fUF&vWBæf—'7BÓÒ66&ÆWEf–öÆWEö¶VÖöä&Vä$õ‚¢$Ö÷fRFò'G’"VÇ6R$Ö÷fRFò2&÷‚ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöå'G”Ö÷fT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢fÂ6äFEFô&æ²Ò&VvW‚‚%â„&÷‚³Ó•Ò²(
"6Æ÷B³Ó•Ò·Å'G’³Ó•Ò·Å&–FRö¼:–Ööâ’B"’æÖF6†W2†Æö6F–öâ¢fÂ&æ²Ò'WGFöâ‡F†—2’æÇ’²FW‡BÒ$FBFò4R&æ²#²—4ÆÄ62ÒfÇ6RÐ¢–b†6äFEFô&æ²’6öçFVçBæFEf–Wr†&æ²¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$FVÆWFRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6…66&ÆWEf–öÆWEö¶VÖöäFVÆWFT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†6öçFVçB’Ð¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"FÆö6F–öâ(
"66&ÆWBõf–öÆWB"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’æ7&VFR‚¢–b†6äFEFô&æ²’°¢F–Æörç6WDöå6†÷tÆ—7FVæW"°¢&æ²ç6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÒ&÷VæB’²F–ÆöræF—6Ö—72‚“²&WGW&ä6WDöä6Æ–6´Æ—7FVæW"Ð¢G'’°¢fÂ&rÒ66&ÆWEf–öÆWE&V6÷&D'—FW2†&÷VæBÂÆö6F–öâ¢fÂVçG'’Ò7v—F6…6T&æµ7F÷&RæFW÷6—E66&ÆWEf–öÆWB‡F†—2Â&r¢F–ÆöræF—6Ö—72‚¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$FFVBFò4R&æ²"¢ç6WDÖW76vR‡ææ–6¶æÖRæ–d&Ææ²²66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR‡ç7V6–W2’Ò°¢"6÷–VB2æF—fR³’âF†R7v—F6‚6fRv2æ÷B6†ævVBåÆåÆâ"²VçG'’ææÖR¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ò6F6‚†W'&÷#¢W†6WF–öâ’°¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$6÷VÆBæ÷BFBFò4R&æ²"¢ç6WDÖW76vR†W'&÷"æÖW76vRó¢%F†R6fR6÷VÆBæ÷B&R&VBv–ââ"¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð¢Ð¢Ð¢Ð¢F–Æörç6†÷r‚¢Ð ¢&—fFRgVâ66&ÆWEf–öÆWE&V6÷&D'—FW2†&÷VæC¢7v—F6…&VE6W76–öâÂÆö6F–öã¢7G&–ær“¢'—FT'&’°¢fÂW&’Ò6VÆV7FVE6÷W&6UW&’ó¢F‡&÷r–ÆÆVvÅ7FFTW†6WF–öâ‚%F†R6VÆV7FVB6fR—2æòÆöævW"f–Æ&ÆRâ"¢fÂ÷&–v–æÂÒ&÷VæBç6æ6†÷Bæ'—FW46÷’‚¢fÂ7W'&VçBÒ6öçFVçE&W6öÇfW"æ÷Vä–çWE7G&VÒ‡W&’“òçW6R²–çWBÓà¢fÂ'—FW2Ò–çWBç&VD'—FW2‚¢&WV—&R†'—FW2ç6—¦RÃÒb¢#B¢#B’²%F†R6VÆV7FVBf–ÆRW†6VVG2F†RbÖ”"–çF¶RÆ–Ö—Bâ"Ð¢'—FW0¢Òó¢F‡&÷r¦fæ–òä”ôW†6WF–öâ‚%F†R6VÆV7FVB6fR6÷VÆBæ÷B&R&V÷VæVBâ"¢fÂF–vW7BÒ¦fç6V7W&—G’äÖW76vTF–vW7BævWD–ç7Fæ6R‚%4„Ó#Sb"¢&WV—&R†¦fç6V7W&—G’äÖW76vTF–vW7Bæ—4WVÂ†F–vW7BæF–vW7B†÷&–v–æÂ’ÂF–vW7BæF–vW7B†7W'&VçB’’’°¢%F†R6VÆV7FVB6fR6†ævVBgFW"—Bv2÷VæVBâ&V÷Vâ—B&Vf÷&RFF–ærö¼:–ÖöâFò&æ²â ¢Ð¢&WGW&â&÷VæBç6æ6†÷Bç&VE66&ÆWEf–öÆWE&V6÷&B†Æö6F–öâ¢Ð ¢&—fFRgVâÆVvVæG5¦Æ&VÂ‡&VC¢ÆVvVæG5¦6Æ÷E&VB“¢7G&–ærÒv†Vâ‡&VB’°¢ÆVvVæG5¦6Æ÷E&VBäV×G’Óâ$V×G’ ¢—2ÆVvVæG5¦6Æ÷E&VBä–çfÆ–BÓâ%Vç&VF&ÆRö¼:–Ööâ(
"G·&VBç&V6öçÒ ¢—2ÆVvVæG5¦6Æ÷E&VBåö¶VÖöâÓâ°¢fÂÒ&VBçfÇVP¢fÂ7V6–W2Ò66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR‡ç7V6–W2¢fÂæ–6²Òææ–6¶æÖRçF¶T–b²—Bæ—4æ÷D&Ææ²‚’bb—BÒ7V6–W2Ð¢†æ–6³òæÆWB²"F—B‚G7V6–W2’"Òó¢7V6–W2’²"(
"66†VBÇbG·æ66†VDÆWfVÇÒ"°¢†–b‡ç6†–ç’’"(
"6†–ç’"VÇ6R""’²†–b‡æ—4Ç†’"(
"Ç†"VÇ6R""’²†–b‡æ—4Vvr’"(
"Vvr"VÇ6R""¢Ð¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦'G’†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7F÷&vRÒ&÷VæBæÆVvVæG5¦7F÷&vRó¢&WGW&à¢fÂ'G’Ò7F÷&vRç'G’‚¢fÂÆ&VÇ2Ò'G’æÖ²%'G’G¶—Bç÷6—F–öçÓ¢G¶ÆVvVæG5¦Æ&VÂ†—Bç&VB—Ò"Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"'G’G·7F÷&vRç'G”6÷VçGÒób"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦ö¶VÖöâ†&÷VæBÂ%'G’G·'G•¶–æFW…Òç÷6—F–öçÒ"Â'G•¶–æFW…Òç&VB¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦&÷‚†&÷VæC¢7v—F6…&VE6W76–öâÂ&÷ƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7F÷&vRÒ&÷VæBæÆVvVæG5¦7F÷&vRó¢&WGW&à¢fÂÆ—7BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4ÂÐ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†Æ—7B’Ð¢Æ—7BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ#3"æF—fR&÷†W2(
"36Æ÷G2V6‚(
"æF—fRæ–6¶æÖRVF—F–ærf–Æ&ÆUÆâ"°¢"G·7F÷&vRæö67W–VD&÷…6Æ÷G7Òó“cö67W–VB6Æ÷G2‚G·7F÷&vRçVç&VF&ÆT&÷…6Æ÷G7ÒVç&VF&ÆR’â"°¢$&÷‚æÖW2ÂvÆÇW'2æBVæÆö6²7FFR&Ræ÷B&VB–âF†—2f–Wrâ ¢6WEFF–ærƒbÂbÂbÂb¢Ò¢f÷"‡6Æ÷B–â7F÷&vRæ&÷‚†&÷‚’’Æ—7BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6Æ÷BG·6Æ÷Bç÷6—F–öçÓ¢G¶ÆVvVæG5¦Æ&VÂ‡6Æ÷Bç&VB—Ò ¢—4ÆÄ62ÒfÇ6S²—4Væ&ÆVBÒ6Æ÷Bç&VB—2ÆVvVæG5¦6Æ÷E&VBäV×G¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦ö¶VÖöâ†&÷VæBÂ$&÷‚G¶&÷‚²Ò(
"6Æ÷BG·6Æ÷Bç÷6—F–öçÒ"Â6Æ÷Bç&VB’Ð¢Ò¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"&÷‚G¶&÷‚²Òó3""¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’æ7&VFR‚¢fÂæbÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBä„õ$•¤ôåDÂÐ¢æbæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%&Wf–÷W2#²—4Væ&ÆVBÒ&÷‚â ¢6WDöä6Æ–6´Æ—7FVæW"²F–ÆöræF—6Ö—72‚“²–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦&÷‚†&÷VæBÂ&÷‚Ò’Ð¢ÒÂÆ–æV$Æ–÷WBäÆ–÷WE&×2ƒÂÓ"Âb’¢æbæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$æW‡B#²—4Væ&ÆVBÒ&÷‚Â3¢6WDöä6Æ–6´Æ—7FVæW"²F–ÆöræF—6Ö—72‚“²–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦&÷‚†&÷VæBÂ&÷‚²’Ð¢ÒÂÆ–æV$Æ–÷WBäÆ–÷WE&×2ƒÂÓ"Âb’¢Æ—7BæFEf–Wr†æb“²F–Æörç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦ö¶VÖöâ†&÷VæC¢7v—F6…&VE6W76–öâÂÆö6F–öã¢7G&–ærÂ&VC¢ÆVvVæG5¦6Æ÷E&VB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢–b‡&VB—2ÆVvVæG5¦6Æ÷E&VBåö¶VÖöâ’°¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR†Æö6F–öâ¢ç6WDÖW76vR‚‡&VB3òÆVvVæG5¦6Æ÷E&VBä–çfÆ–B“òç&V6öâó¢%F†—26Æ÷B—2V×G’â"¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢&WGW&à¢Ð¢fÂÒ&VBçfÇVP¢fÂvVæFW"ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’ævWD÷$çVÆÂ‡ævVæFW"’ó¢%Væ¶æ÷vâ‚G·ævVæFW'Ò’ ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"Gµ66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR‡ç7V6–W2—Ò‚2G·ç7V6–W7Ò’(
"f÷&ÒG·æf÷&×ÕÆâ"¢VæB‚$æ–6¶æÖS¢G·ææ–6¶æÖRæ–d&Ææ²²$æöæR"×ÕÆävVæFW#¢FvVæFW%ÆäVvrfÆs¢G·æ—4VvwÕÆâ"¢VæB‚$66†VBÆWfVÃ¢G·æ66†VDÆWfVÇÕÆäU…¢G·æW‡W&–Væ6WÕÆâ"¢VæB‚$æGW&R”C¢G·ææGW&WÕÆå7FBæGW&R”C¢G·ç7FDæGW&WÕÆâ"¢VæB‚$66†VB…¢G·æ7W'&VçD‡ÒòG·æÖ†–×VÔ‡ÕÆâ"¢VæB‚$66†VBF²ôFVbõ7VVBõ7õ7C¢G·æ66†VE7FG2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆâ"¢VæB‚%6†–ç“¢G¶–b‡ç6†–ç’’%–W2"VÇ6R$æò'ÕÆä†VÆB—FVÒ”C¢G·æ†VÆD—FV×ÕÆä&–Æ—G’”C¢G·æ&–Æ—G—ÕÆâ"¢VæB‚$õBg&–VæG6†—¢G·æ÷&–v–æÅG&–æW$g&–VæG6†—ÕÆä†æFÆ–ær×G&–æW"g&–VæG6†—¢G·æ†æFÆ–æuG&–æW$g&–VæG6†—ÕÆâ"¢fÂ†æFÆW"Òv†Vâ‡æ7W'&VçD†æFÆW"’²Óâ$÷&–v–æÂG&–æW"#²Óâ$†æFÆ–ærG&–æW"#²VÇ6RÓâ%Væ¶æ÷vâ‚G·æ7W'&VçD†æFÆW'Ò’"Ð¢VæB‚$7W'&VçB†æFÆW#¢F†æFÆW%Æâ"¢VæB‚$Ç†¢G·æ—4Ç†ÕÆä&ÆÂ”C¢G·æ&ÆÇÕÆâ"¢VæB‚%66ÆS¢G·ç66ÆWÕÆäö&VF–Væ6RÆWfVÃ¢G·æö&VF–Væ6TÆWfVÇÕÆâ"¢VæB‚$Ö÷fR²&V6÷&G2ÂÖVvWföÇWF–öâ7FFRæB&GFÆRöf÷&ÒVffV7G2&Ræ÷BWfÇVFVB–âF†—2f–WråÆåÆâ"¢VæB‚$÷&–v–æÂG&–æW#¢G·æ÷&–v–æÅG&–æW'ÕÆåD”Cc¢G·çF–GÕÆå4”Cc¢G·ç6–GÕÆâ"¢VæB‚%”C¢G·ç–BçFõ7G&–ærƒb’çWW&66R‚—ÕÆäVæ7'—F–öâ6öç7FçC¢G·æVæ7'—F–öä6öç7FçBçFõ7G&–ærƒb’çWW&66R‚—ÕÆåÆäÖ÷fW5Æâ"¢æÖ÷fW2æf÷$V6„–æFW†VB²’ÂÖ÷fRÓâVæB‚"G¶’²ÒâÖ÷fR2G¶Ö÷fRæ–GÒ(
"G¶Ö÷fRçÒ(
"W2G¶Ö÷fRçW7ÕÆâ"’Ð¢VæB‚%&VÆV&âÖ÷fR”G3¢G·ç&VÆV&äÖ÷fW2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆåÆâ"¢VæB‚$•g2„…ôF²ôFVbõ7VVBõ7õ7B“¢G·æ—g2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆâ"¢VæB‚$Ug2‡6ÖR÷&FW"“¢G·æWg2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆåÆâ"¢VæB‚%’7F÷&VBÖFF6†V6·7VÒ76VBâ66†VB'G’7FG2&R÷WG6–FRF†B6†V6·7VÒæBÖ’&R7FÆRÂW7V6–ÆÇ’–â&÷†W2â"¢VæB‚$æF—fRæ–6¶æÖRVF—F–ær—2f–Æ&ÆRf÷"'G’æB2Ö&÷‚&V6÷&G3²ÆVvÆ—G’æBvÖR×&W6Væ6R6†V6·2&Ræ÷BWfÇVFVBâ"¢Ð¢fÂ6öçFVçBÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4ÂÐ¢6öçFVçBæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ&÷„ÖF6‚Ò&VvW‚‚%ä&÷‚…³Ó•Ò²’(
"6Æ÷B…³Ó•Ò²’B"’æÖF6„VçF—&R†Æö6F–öâ¢fÂ'G”ÖF6‚Ò&VvW‚‚%å'G’…³Ó•Ò²’B"’æÖF6„VçF—&R†Æö6F–öâ¢fÂæF—fUF&vWBÒv†Vâ°¢&÷„ÖF6‚ÒçVÆÂÓâG&—ÆR„ÆVvVæG5¦ö¶VÖöä&Vä$õ‚À¢&÷„ÖF6‚æw&÷WfÇVW5³ÒçFô–çB‚’Â&÷„ÖF6‚æw&÷WfÇVW5³%ÒçFô–çB‚’¢'G”ÖF6‚ÒçVÆÂÓâG&—ÆR„ÆVvVæG5¦ö¶VÖöä&Vå%E’ÂÂ'G”ÖF6‚æw&÷WfÇVW5³ÒçFô–çB‚’¢VÇ6RÓâçVÆÀ¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvrbbæf÷&ÒÓÒb`¢ÆVvVæG5¦ö¶VÖöäWföÇWF–öäVF—F–æræ6†ö–6W2†&÷VæBç6æ6†÷Bæ'—FW46÷’‚’Âç7V6–W2’æ—4æ÷DV×G’‚’’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$WföÇfRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG5¦ö¶VÖöäWföÇWF–öä7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$VF—Bö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG5¦ö¶VÖöäæ–6¶æÖT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$GWÆ–6FRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG5¦ö¶VÖöäGWÆ–6FT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢–b†æF—fUF&vWBÒçVÆÂbbæF—fUF&vWBæf—'7BÓÒÆVvVæG5¦ö¶VÖöä&Vä$õ‚bbæ—4Vvr’°¢6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%7vv—F‚ö¼:–Ööâ–âF†—2&÷‚ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG5¦ö¶VÖöå7v7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b†æF—fUF&vWBÒçVÆÂbbæ—4Vvr’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ–b†æF—fUF&vWBæf—'7BÓÒÆVvVæG5¦ö¶VÖöä&Vä$õ‚¢$Ö÷fRFò'G’"VÇ6R$Ö÷fRFò2&÷‚ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG5¦ö¶VÖöå'G”Ö÷fT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢fÂ6äFEFô&æ²Ò&VvW‚‚%â„&÷‚³Ó•Ò²(
"6Æ÷B³Ó•Ò·Å'G’³Ó•Ò²’B"’æÖF6†W2†Æö6F–öâ¢fÂ&æ²Ò'WGFöâ‡F†—2’æÇ’²FW‡BÒ$FBFò4R&æ²#²—4ÆÄ62ÒfÇ6RÐ¢–b†6äFEFô&æ²’6öçFVçBæFEf–Wr†&æ²¢–b†æF—fUF&vWBÒçVÆÂ’6öçFVçBæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$FVÆWFRö¼:–Ööâ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6W76–öâÓÓÒ&÷VæBbb6÷W&6RÒçVÆÂ’7F'D7F—f—G’€¢–çFVçB€¢F†—47v—F6…&VDöæÇ”7F—f—G’À¢7v—F6„ÆVvVæG5¦ö¶VÖöäFVÆWFT7F—f—G“£¦6Æ72æ¦f¢¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&V"ÂæF—fUF&vWBæf—'7BææÖR¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâæ&÷‚"ÂæF—fUF&vWBç6V6öæB¢çWDW‡G&‚'6Rç7v—F6‚çö¶VÖöâç÷6—F–öâ"ÂæF—fUF&vWBçF†—&B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†6öçFVçB’Ð¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"FÆö6F–öâ(
"ÆVvVæG3¢¢Ô"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’æ7&VFR‚¢–b†6äFEFô&æ²’°¢F–Æörç6WDöå6†÷tÆ—7FVæW"°¢&æ²ç6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÒ&÷VæB’²F–ÆöræF—6Ö—72‚“²&WGW&ä6WDöä6Æ–6´Æ—7FVæW"Ð¢G'’°¢fÂ&rÒÆVvVæG5¦&V6÷&D'—FW2†&÷VæBÂÆö6F–öâ¢fÂVçG'’Ò7v—F6…6T&æµ7F÷&RæFW÷6—DÆVvVæG5¦‡F†—2Â&r¢F–ÆöræF—6Ö—72‚¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$FFVBFò4R&æ²"¢ç6WDÖW76vR‡ææ–6¶æÖRæ–d&Ææ²²66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR‡ç7V6–W2’Ò°¢"6÷–VB2æF—fR’âF†R7v—F6‚6fRv2æ÷B6†ævVBåÆåÆâ"²VçG'’ææÖR¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ò6F6‚†W'&÷#¢W†6WF–öâ’°¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$6÷VÆBæ÷BFBFò4R&æ²"¢ç6WDÖW76vR†W'&÷"æÖW76vRó¢%F†R6fR6÷VÆBæ÷B&R&VBv–ââ"¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð¢Ð¢Ð¢Ð¢F–Æörç6†÷r‚¢Ð ¢&—fFRgVâÆVvVæG5¦&V6÷&D'—FW2†&÷VæC¢7v—F6…&VE6W76–öâÂÆö6F–öã¢7G&–ær“¢'—FT'&’°¢fÂW&’Ò6VÆV7FVE6÷W&6UW&’ó¢F‡&÷r–ÆÆVvÅ7FFTW†6WF–öâ‚%F†R6VÆV7FVB6fR—2æòÆöævW"f–Æ&ÆRâ"¢fÂ÷&–v–æÂÒ&÷VæBç6æ6†÷Bæ'—FW46÷’‚¢fÂ7W'&VçBÒ6öçFVçE&W6öÇfW"æ÷Vä–çWE7G&VÒ‡W&’“òçW6R²–çWBÓà¢fÂ'—FW2Ò–çWBç&VD'—FW2‚¢&WV—&R†'—FW2ç6—¦RÃÒb¢#B¢#B’²%F†R6VÆV7FVBf–ÆRW†6VVG2F†RbÖ”"–çF¶RÆ–Ö—Bâ"Ð¢'—FW0¢Òó¢F‡&÷r¦fæ–òä”ôW†6WF–öâ‚%F†R6VÆV7FVB6fR6÷VÆBæ÷B&R&V÷VæVBâ"¢fÂF–vW7BÒ¦fç6V7W&—G’äÖW76vTF–vW7BævWD–ç7Fæ6R‚%4„Ó#Sb"¢&WV—&R†¦fç6V7W&—G’äÖW76vTF–vW7Bæ—4WVÂ†F–vW7BæF–vW7B†÷&–v–æÂ’ÂF–vW7BæF–vW7B†7W'&VçB’’’°¢%F†R6VÆV7FVB6fR6†ævVBgFW"—Bv2÷VæVBâ&V÷Vâ—B&Vf÷&RFF–ærö¼:–ÖöâFò&æ²â ¢Ð¢&WGW&â&÷VæBç6æ6†÷Bç&VDÆVvVæG5¦&V6÷&B†Æö6F–öâ¢Ð ¢&—fFRgVâ6†÷tÆWG4vô6GW&U&V6÷&G2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæÆWG4vô6GW&U&V6÷&G2ó¢&WGW&à¢fÂæöç¦W&òÒ&V6÷&G2æVçG&–W2æf–ÇFW"²—Bæ6GW&VBÒÂÇÂ—BçG&ç6fW'&VBÒÂÐ¢fÂÆ–æW2Ò'V–ÆE7G&–ær°¢VæDÆ–æR‚%6fVB6GW&R&V6÷&G2"¢VæDÆ–æR‚#S2æF—fR7V6–W2VçG&–W3²&r&Æö6²&WF–æVB‚"²&V6÷&G2ç&t&Æö6´'—FW2ç6—¦R²"'—FW2’â"¢VæDÆ–æR‚%F÷FÂ6Vv‡C¢"²&V6÷&G2çF÷FÄ6GW&VB¢VæDÆ–æR‚%F÷FÂG&ç6fW'&VBFò&öfW76÷"ö³¢"²&V6÷&G2çF÷FÅG&ç6fW'&VB¢VæDÆ–æR‚¢–b†æöç¦W&òæ—4V×G’‚’’VæDÆ–æR‚$æòæöâ×¦W&ò7V6–W2&V6÷&G2&R7F÷&VBâ"¢VÇ6Ræöç¦W&òæf÷$V6‚²VçG'’ÓâVæDÆ–æR‚%7V6–W2"²VçG'’ç7V6–W2²#¢6Vv‡B"²VçG'’æ6GW&VB²"ÂG&ç6fW'&VB"²VçG'’çG&ç6fW'&VB’Ð¢VæDÆ–æR‚¢VæB‚$öæRW"×7V6–W26Vv‡B÷"&öfW76÷"ö²G&ç6fW"6÷VçFW"Â÷"öæR6W&FRÆÂ×7V6–W2F÷FÂÂ6â&RVF—FVBFòfW&–f–VBæWr6÷’â7V6–W2VF—G2&W6W'fR&÷F‚F÷FÇ3²F÷FÂVF—G2&W6W'fRWfW'’7V6–W2&V6÷&BæBF†RVç6VÆV7FVBF÷FÂâæò7W'&VçB×'G’ÂÆVvÆ—G’ÂVæ6÷VçFW"÷"Vç7W÷'FVB×7FFR–æfW&Væ6R—2ÖFRâ"¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR†&÷VæBæF—7Æ”æÖR²"(
"6GW&R&V6÷&G2"¢ç6WDÖW76vR†Æ–æW2¢ç6WE÷6—F—fT'WGFöâ‚$VF—B7V6–W26÷VçB"’²òÂòÓâÆVæ6„ÆWG4vô6GW&U&V6÷&DVF—F÷"†&÷VæB’Ð¢ç6WDæWWG&Ä'WGFöâ‚$VF—BF÷FÇ2"’²òÂòÓâÆVæ6„ÆWG4vô6GW&UF÷FÄVF—F÷"†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆWG4vô6GW&U&V6÷&DVF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6„ÆWG4vô6GW&U&V6÷&G47F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâÆVæ6„ÆWG4vô6GW&UF÷FÄVF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6„ÆWG4vô6GW&UF÷FÄ7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâ6†÷tÆWG4vôvõ&´&V2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&²Ò&÷VæBæÆWG4vôvõ&²ó¢&WGW&à¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G·&²çfW'6–öâæF—7Æ”æÖWÒ(
"G·&²æö67W–VD6÷VçGÒóÃö67W–VBæF—fRu6Æ÷G5Æâ"°¢##&V2(
"S6Æ÷G2W"&V(
"&VBÖöæÇ’ ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb¢Ò¢&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$–×÷'BæF—fRu(
"6fRæWr6÷’#²—4ÆÄ62ÒfÇ6S²—4Væ&ÆVBÒ6VÆV7FVE6÷W&6UW&’ÒçVÆÀ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’ÆVæ6„ÆWG4vôvõ&´–×÷'B†&÷VæB’Ð¢Ò¢&WVB„ÆWG4vôvõ&µ&VDöæÇ’ä$Tô4õTåB’²–æFW‚Óà¢fÂ&VÒ–æFW‚²¢fÂ6÷VçBÒ&²æ&V†&V’ç6—¦P¢&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$tò&²F&V(
"F6÷VçBóSö67W–VB#²—4ÆÄ62ÒfÇ6S²—4Væ&ÆVBÒ6÷VçBâ ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vôvõ&´&V†&÷VæBÂ&V’Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$tò&²(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôvõ&´&V†&÷VæC¢7v—F6…&VE6W76–öâÂ&V¢–çB’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂVçG&–W2Ò&÷VæBæÆWG4vôvõ&³òæ&V†&V’ó¢&WGW&à¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢f÷"†VçG'’–âVçG&–W2’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢fÂ6†–ç’Ò–b†VçG'’ç6†–ç’’")ˆR"VÇ6R" ¢fÂf÷&ÒÒ–b†VçG'’æf÷&ÒÓÒ’""VÇ6R"(
"f÷&ÒG¶VçG'’æf÷&×Ò ¢FW‡BÒ%6Æ÷BG¶VçG'’ç6Æ÷GÒ(
"2G¶VçG'’ç7V6–W7ÒG¶VçG'’æF—7Æ”æÖWÒG6†–ç’Ff÷&ÕÆâ"°¢$ÆWfVÂG¶võ&´çVÖ&W"†VçG'’æÆWfVÂ—Ò(
"5G¶VçG'’æ7Ò ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vôvõ&´VçG'’†&÷VæBÂVçG'’’Ð¢Ò¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$tò&²F&V(
"G¶VçG&–W2ç6—¦WÒóSö67W–VB"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚$ÆÂ&V2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vôvõ&´&V2†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆWG4vôvõ&´–×÷'B†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6„ÆWG4vôvõ&´–×÷'D7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâ6†÷tÆWG4vôvõ&´VçG'’†&÷VæC¢7v—F6…&VE6W76–öâÂVçG'“¢ÆWG4vôvõ&´VçG'’’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂvVæFW"ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’ævWD÷$çVÆÂ†VçG'’ævVæFW"’ó¢%Væ¶æ÷vâ‚G¶VçG'’ævVæFW'Ò’ ¢fÂFFRÒ–b†VçG'’ç–V"–â#âã“““’bbVçG'’æÖöçF‚–ââã"bbVçG'’æF’–ââã3¢"SFBÒS&BÒS&B"æf÷&ÖB†VçG'’ç–V"ÂVçG'’æÖöçF‚ÂVçG'’æF’’VÇ6R%&rG¶VçG'’æ6GW&VDFFWÒ ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæDÆ–æR‚$tò&²G¶VçG'’æ&VÒ(
"6Æ÷BG¶VçG'’ç6Æ÷GÒ(
"æF—fR–æFW‚G¶VçG'’æ–æFW‡Ò"¢VæDÆ–æR‚%7V6–W3¢2G¶VçG'’ç7V6–W7ÒG´ÆWG4võ7V6–W4æÖW2ææÖR†VçG'’ç7V6–W2—Ò"¢VæDÆ–æR‚$æ–6¶æÖS¢G¶VçG'’ææ–6¶æÖRæ–d&Ææ²²$æöæR6fVB"×Ò"¢VæDÆ–æR‚$f÷&Ó¢G¶VçG'’æf÷&×Ò(
"vVæFW#¢FvVæFW"(
"6†–ç“¢G¶–b†VçG'’ç6†–ç’’%–W2"VÇ6R$æò'Ò"¢VæDÆ–æR‚$ÆWfVÃ¢G¶võ&´çVÖ&W"†VçG'’æÆWfVÂ—Ò(
"5¢G¶VçG'’æ7Ò(
"7F÷&VB…¢G¶VçG'’ç7F÷&VD‡Ò"¢VæDÆ–æR‚$tò•g3¢…G¶VçG'’æ—d‡ÒòGF6²G¶VçG'’æ—dGF6·ÒòFVfVç6RG¶VçG'’æ—dFVfVç6WÒ"¢VæDÆ–æR‚$†V–v‡C¢G¶võ&´çVÖ&W"†VçG'’æ†V–v‡B—ÒÒ(
"vV–v‡C¢G¶võ&´çVÖ&W"†VçG'’çvV–v‡B—Ò¶r"¢VæDÆ–æR‚$6GW&VBFFS¢FFFR"¢VæDÆ–æR‚$tòÖ÷fR”G3¢G¶VçG'’æf7DÖ÷fT–GÒÂG¶VçG'’æ6†&vVDÖ÷fT–GÒ"¢VæDÆ–æR‚%G&–æW#¢G¶VçG'’çW6W&æÖRæ–d&Ææ²²$æ÷B6fVB"×Ò"¢–b†VçG'’ç6V6öæEW6W&æÖRæ—4æ÷D&Ææ²‚’’VæDÆ–æR‚%6V6öæF'’G&–æW"7G&–æs¢G¶VçG'’ç6V6öæEW6W&æÖWÒ"¢VæDÆ–æR‚$6—G“¢G¶VçG'’æ6—G’æ–d&Ææ²²$æ÷B6fVB"×Ò"¢VæDÆ–æR‚¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&RF†—2&V6÷&Bv2&VBâF†—2f–WrFöW2æ÷B–æfW"Væ6÷VçFW"ÆVvÆ—G’Â6öçfW'BF†Ru&V6÷&BÂ÷"Ö÷fR—B–çFò7F÷&vRâ6Æ÷B7F–öç26âW‡÷'BF†RW†7Bu'—FW2÷"6ÆV"F†—26Æ÷B–â&Wf–WvVBæWr6fR6÷’â"¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR†VçG'’æF—7Æ”æÖR²"(
"tò&²"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$&6²Fòtò&²G¶VçG'’æ&VÒ"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vôvõ&´&V†&÷VæBÂVçG'’æ&V¢Òç6WDæWWG&Ä'WGFöâ‚%6Æ÷B7F–öç2"’²òÂòÓâÆVæ6„ÆWG4vôvõ&´VF—F÷"†&÷VæBÂVçG'’’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆWG4vôvõ&´VF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâÂVçG'“¢ÆWG4vôvõ&´VçG'’’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6„ÆWG4vôvõ&´7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rævõ÷&²æ–æFW‚"ÂVçG'’æ–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâvõ&´çVÖ&W"‡fÇVS¢fÆöB“¢7G&–ærÐ¢–b‚fÇVRæ—4f–æ—FR‚’’fÇVRçFõ7G&–ær‚’VÇ6R"Rã&b"æf÷&ÖB‡fÇVR ¢&—fFRgVâ6†÷tÆWG4vôF”6&R†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vôF”6&Ró¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"F’6&R(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚$æF—fRö67W–VBfÆs¢G¶–b‡&VBæ—4ö67W–VB’$ö67W–VB"VÇ6R$V×G’'Ò‡&r'—FRG·&VBç&tö67W–VD'—FWÒ•Æâ"¢VæB‚$æF—fR7FW6÷VçFW#¢G·&VBç7FW6÷VçFW'ÕÆåÆâ"¢fÂÒ&VBçö¶VÖöà¢–b‡ÒçVÆÂ’°¢fÂvVæFW"ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’ævWD÷$çVÆÂ‡ævVæFW"’ó¢%Væ¶æ÷vâ‚G·ævVæFW'Ò’ ¢VæB‚"G¶–b‡&VBæ—4ö67W–VB’$FW÷6—FVBö¼:–Ööâ"VÇ6R$–æ7F—fR7F÷&VB&V6÷&B'Ó¢G´ÆWG4võ7V6–W4æÖW2ææÖR‡ç7V6–W2—Ò‚2G·ç7V6–W7Ò•Æâ"¢VæB‚$f÷&Ó¢G·æf÷&×ÕÆävVæFW#¢FvVæFW%Æå6fVBU…¢G·æW‡W&–Væ6WÕÆäæGW&R”C¢G·ææGW&WÕÆâ"¢VæB‚%D”Cc¢G·çF–GÕÆå4”Cc¢G·ç6–GÕÆå”C¢G·ç–BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆâ"¢VæB‚$Væ7'—F–öâ6öç7FçC¢G·æVæ7'—F–öä6öç7FçBçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆâ"¢VæB‚%7F÷&VB#r6†V6·7VÒ76VBåÆâ"¢ÒVÇ6R–b‡&VBæ—5¦W&õ7F÷&VE&V6÷&B’VæB‚%7F÷&VB&V6÷&B—2ÆÂ¦W&ò'—FW2åÆâ"¢VÇ6RVæB‚%7F÷&VBö¼:–Ööâ7VÖÖ'’Væf–Æ&ÆS¢G·&VBç&V6÷&EVæf–Æ&ÆU&V6öçÕÆâ"¢VæB‚%ÆåF†R6fVB&V6÷&B†2æò'G’×7FBF–ÂâÆWfVÂæB…&Ræ÷B7WÆ–VB'’F†—2f–Wrâ"¢VæB‚%F†Rö67W–VBfÆræBVç6–væVB7FW6÷VçFW"&R6†÷vâW†7FÇ’27F÷&VC²&Vf—‚'—FW2(	32&VÖ–âVæFö7VÖVçFVB†W&Râ"¢VæB‚$æò'&VVF–ærÂVvrÂfVRÂV&æVBU…÷"v—F†G&vÂVÆ–v–&–Æ—G’—2–æfW'&VBåÆåÆâ"¢VæB‚$æF—fR†VFW"ƒ‚'—FW3²ö67Wæ7’BÂ7FW6÷VçFW"BN(	3r“¥ÆâG·&VBç&t†VFW$'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆåÆâ"¢VæB‚$÷&–v–æÂ7F÷&VB&V6÷&Bƒ#3"Væ7'—FVB'—FW2“¥ÆâG·&VBç&u7F÷&VD'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆåÆâ"¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VBâ&rFF&VÖ–ç2f–Æ&ÆRWfVâv†VâF†R–ææW"&V6÷&B—2Vç&VF&ÆRâæòæ÷&ÖÆ—¦F–öâÂFW÷6—BÂv—F†G&vÂÂ&W—"ÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâÆVvÆ—G’—2æ÷BWfÇVFVBâ"¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$F’6&R(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4võÆ–W$Æö6F–öâ†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4võÆ–W$Æö6F–öâó¢&WGW&à¢gVâ&r†&—G3¢Æöær“¢7G&–ærÒ&—G2çFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr¢gVâG&ç6f÷&Ò†Æ&VÃ¢7G&–ærÂfÇVS¢fÆöBÂ&—G3¢Æöær“¢7G&–ærÐ¢"FÆ&VÃ¢G¶–b‡fÇVRæ—4f–æ—FR‚’’fÇVRçFõ7G&–ær‚’VÇ6R$æöâÖf–æ—FR'Ò(
"&r‚G·&r†&—G2—Ò ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"Æ–W"÷6—F–öâ(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚$æF—fRÖ–FVçF–f–W#¢G·&VBæÖ–GÕÆâ"¢VæB‚$Ö–FVçF–f–W"†Wƒ¢G·&VBæÖ–BçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆâ"¢VæB‚$Ö'—FW3¢G·&VBæÖ'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆåÆâ"¢VæB‚%÷6—F–öåÆâ"¢VæB‡G&ç6f÷&Ò‚%‚"Â&VBç‚Â&VBç÷6—F–öä&—G5³Ò’’æVæB‚uÆâr¢VæB‡G&ç6f÷&Ò‚%’"Â&VBç’Â&VBç÷6—F–öä&—G5³%Ò’’æVæB‚uÆâr¢VæB‡G&ç6f÷&Ò‚%¢"Â&VBç¢Â&VBç÷6—F–öä&—G5³Ò’’æVæB‚%ÆåÆâ"¢VæB‚%66ÆUÆâ"¢VæB‡G&ç6f÷&Ò‚%‚"Â&VBç66ÆU‚Â&VBç66ÆT&—G5³Ò’’æVæB‚uÆâr¢VæB‡G&ç6f÷&Ò‚%’"Â&VBç66ÆU’Â&VBç66ÆT&—G5³%Ò’’æVæB‚uÆâr¢VæB‡G&ç6f÷&Ò‚%¢"Â&VBç66ÆU¢Â&VBç66ÆT&—G5³Ò’’æVæB‚%ÆåÆâ"¢VæB‚%&÷FF–öâVFW&æ–öåÆâ"¢VæB‡G&ç6f÷&Ò‚%‚"Â&VBç&÷FF–öå‚Â&VBç&÷FF–öä&—G5³Ò’’æVæB‚uÆâr¢VæB‡G&ç6f÷&Ò‚%’"Â&VBç&÷FF–öå’Â&VBç&÷FF–öä&—G5³%Ò’’æVæB‚uÆâr¢VæB‡G&ç6f÷&Ò‚%¢"Â&VBç&÷FF–öå¢Â&VBç&÷FF–öä&—G5³Ò’’æVæB‚uÆâr¢VæB‡G&ç6f÷&Ò‚%r"Â&VBç&÷FF–öårÂ&VBç&÷FF–öä&—G5³5Ò’’æVæB‚%ÆåÆâ"¢–b‚&VBæÆÅG&ç6f÷&×4f–æ—FR’VæB‚$öæR÷"Ö÷&R6fVBG&ç6f÷&ÒfÇVW2&RæöâÖf–æ—FS²F†V—"&r&—G2&R&WF–æVBv—F†÷WB–çFW'&WFF–öâåÆåÆâ"¢VæB‚%F†RÖ–FVçF–f–W"—26†÷vâW†7FÇ’26fVBæB—2æ÷BwVW76VB–çFòÆö6F–öâæÖRâ"¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&R&VF–ærâæòFVÆW÷'BÂ6ö÷&F–æFR6†ævRÂæ÷&ÖÆ—¦F–öâÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ"¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%Æ–W"÷6—F–öâ(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôGfVçGW&T&Vv–â†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vôGfVçGW&T&Vv–âó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"GfVçGW&R7F'EÆåÆâ"¢VæB‚%6V6öæG26–æ6R“sÓÓ££UD3¢G·&VBç6V6öæG56–æ6TWö6‡ÕÆâ"¢VæB‚%Vç6–væVB†Wƒ¢‚G·&VBç6V6öæG56–æ6TWö6‚çFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆâ"¢VæB‚%&r'—FW3¢G·&VBç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆåÆâ"¢–b‡&VBçWF5F–ÖW7F×ÒçVÆÂ’VæB‚%UD2F–ÖW7F×¢G·&VBçWF5F–ÖW7F×ÕÆåÆâ"¢VÇ6RVæB‚%UD2F–ÖW7F×Væf–Æ&ÆS¢G·&VBçVæf–Æ&ÆU&V6öçÕÆåÆâ"¢VæB‚%F†—2—2F†RæF—fRV–v‡BÖ'—FRVç6–væVBWö6‚fÇVRg&öÒÆ–W$vVôÆö6F–öâ&Æö6²râ"¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&R&VF–ærâVF—F–ær6†ævW2öæÇ’F†—2W†7BT–çCcBæB&Æö6²rw25$3²æòÆö6Â×F–ÖR6öçfW'6–öâ—2Æ–VBâ"¢Ð¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BGfVçGW&R7F'B#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆWG4vôGfVçGW&U7F'D7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$GfVçGW&R7F'B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4võ6V7W&UfÇVW2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4võ6V7W&UfÇVW2ó¢&WGW&à¢fÂ&VÆF–öâÒv†Vâ°¢&VBæ7W'&VçBâ&VBç&Wf–÷W2Óâ$7W'&VçB—2çVÖW&–6ÆÇ’w&VFW"F†â&Wf–÷W2 ¢&VBæ7W'&VçBÂ&VBç&Wf–÷W2Óâ$7W'&VçB—2çVÖW&–6ÆÇ’ÆW72F†â&Wf–÷W2 ¢VÇ6RÓâ$7W'&VçBæB&Wf–÷W2fÇVW2&RWVÂ ¢Ð¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"6fR6V7W&RfÇVW2(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚$7W'&VçB6V7W&RfÇVUÆâ"¢VæB‚%Vç6–væVC¢G·&VBæ7W'&VçGÕÆâ"¢VæB‚$†Wƒ¢‚G·&VBæ7W'&VçBçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆâ"¢VæB‚$'—FW3¢G·&VBæ7W'&VçD'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆåÆâ"¢VæB‚%&Wf–÷W26V7W&RfÇVUÆâ"¢VæB‚%Vç6–væVC¢G·&VBç&Wf–÷W7ÕÆâ"¢VæB‚$†Wƒ¢‚G·&VBç&Wf–÷W2çFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆâ"¢VæB‚$'—FW3¢G·&VBç&Wf–÷W4'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆåÆâ"¢VæB‚$6ö×&—6öã¢G&VÆF–öâåÆåÆâ"¢VæB‚%F†W6R&RF†RGvòæF—fRVç6–væVBcBÖ&—B4eô$TTbfö÷FW"fÇVW2â"¢VæB‚%F†V—"6Æö6²ÂVæ—BæB6ÆVæF"ÖVæ–ær&Ræ÷B–æfW'&VBâÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&RF—7Æ’â"¢VæB‚$æò6V7W&R×fÇVR6†ævRÂ7–æ6‡&öæ—¦F–öâÂæ÷&ÖÆ—¦F–öâÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ"¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%6fR6V7W&RfÇVW2(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôvÖU7–æ4–FVçF—G’†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vôvÖU7–æ4–FVçF—G’ó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"vÖR7–æ2–FVçF—G’(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚%7FFS¢G¶–b‡&VBæ—5Vç6WB’%Vç6WB†ÆÂ¦W&ò'—FW2’"VÇ6R%7F÷&VB'ÕÆâ"¢VæB‚$†Wƒ¢G·&VBæ†W‡ÕÆâ"¢VæB‚$'—FW3¢G·&VBæ–D'—FW2æ¦ö–åFõ7G&–ær‚""’²—BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ"Âsr’×ÕÆåÆâ"¢VæB‚%F†—2—2´†U‚w2Fö7VÖVçFVBV–v‡BÖ'—FRæF—fR×•7FGW2vÖR7–æ2”Bâ"¢VæB‚%F†R6fVBfÇVRFöW2æ÷BW7F&Æ—6‚F†Bâ66÷VçBÂ6W'fW"÷"öæÆ–æR6W'f–6R—27W'&VçFÇ’f–Æ&ÆRâ"¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&RF—7Æ’âæòæWGv÷&²&WVW7BÂ”B6†ævRÂæ÷&ÖÆ—¦F–öâÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ"¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$vÖR7–æ2–FVçF—G’(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôWV—VDV&æ6R†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vôWV—VDV&æ6Ró¢&WGW&à¢gVâ7G&–æt'V–ÆFW"æVæEfÇVW2‡F—FÆS¢7G&–ærÂfÇVW3¢Æ—7CÄÆWG4vôWV—VDV&æ6UfÇVSâ’°¢VæB‚"GF—FÆUÆâ"¢fÇVW2æf÷$V6‚²VçG'’Óà¢VæB‚"G¶VçG'’æÆ&VÇÓ¢G¶VçG'’çfÇVWÕÆâ"¢VæB‚"†Wƒ¢‚G¶VçG'’çfÇVRçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—Ò(
"×•7FGW2³‚G¶VçG'’æöfg6WBçFõ7G&–ærƒb’çWW&66R‚—ÕÆâ"¢Ð¢Ð¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"WV—VBV&æ6R(
"&VBÖöæÇ•ÆåÆâ"¢VæEfÇVW2‚%'FæW""Â&VBç'FæW"¢VæB‚%Æâ"¢VæEfÇVW2‚%Æ–W""Â&VBçÆ–W"¢VæB‚%ÆåF†W6R&RF†RW†7BæF—fRVç6–væVBcBÖ&—BWV—VBV&æ6R–FVçF–f–W'2Fö7VÖVçFVB–â×•7FGW2â"¢VæB‚$æò6FÆöwVRæÖW2Â÷væW'6†—Â6ö×F–&–Æ—G’÷"f—7VÂ&W7VÇB&R–æfW'&VBâ"¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&RF—7Æ’âæòV&æ6R6†ævW2ÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ"¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$WV—VBV&æ6R(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vô&Æö6´F—&V7F÷'’†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vô&Æö6´F—&V7F÷'’ó¢&WGW&à¢gVâ†W‚‡fÇVS¢–çBÂv–GFƒ¢–çBÒR“¢7G&–ærÒfÇVRçFõ7G&–ærƒb’çWW&66R‚’çE7F'B‡v–GF‚Âsr¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"æF—fR&Æö6²F—&V7F÷'’(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚$æF—fR&Æö6·3¢G·&VBæVçG&–W2ç6—¦WÕÆâ"¢VæB‚$FW67&—F÷"–FVçF—F–W2æBÆVæwF‡3¢G¶–b‡&VBæÆÄFW67&—F÷'4ÖF6‚’%52"VÇ6R$d”Â'ÕÆâ"¢VæB‚%7F÷&VBfW'7W26Æ7VÆFVB5$2Óbô$3¢G¶–b‡&VBæÆÄ7&74ÖF6‚’%52"VÇ6R$d”Â'ÕÆâ"¢f÷"†VçG'’–â&VBæVçG&–W2’°¢VæB‚%Æä&Æö6²G¶VçG'’æ–BçFõ7G&–ær‚’çE7F'Bƒ"Âsr—ÕÆâ"¢VæB‚$FF¢‚G¶†W‚†VçG'’æöfg6WB—Þ(	3‚G¶†W‚†VçG'’æVæDöfg6WB—Ò(
"G¶VçG'’æÆVæwF‡Ò'—FW5Æâ"¢VæB‚$FW67&—F÷#¢”BG¶VçG'’æFW67&—F÷$–GÒ(
"G¶VçG'’æFW67&—F÷$ÆVæwF‡Ò'—FW2"¢VæB‚"(
"G¶–b†VçG'’æFW67&—F÷$ÖF6†W2’%52"VÇ6R$d”Â'ÕÆâ"¢VæB‚$5$3¢7F÷&VB‚G¶†W‚†VçG'’ç7F÷&VD7&2ÂB—Ò(
"6Æ7VÆFVB‚G¶†W‚†VçG'’æ6Æ7VÆFVD7&2ÂB—Ò"¢VæB‚"(
"G¶–b†VçG'’æ7&4ÖF6†W2’%52"VÇ6R$d”Â'ÕÆâ"¢Ð¢VæB‚%ÆåF†RF—&V7F÷'’—26†÷vâv—F‚æF—fRçVÖW&–2&Æö6²”G3²VæFö7VÖVçFVB&Æö6·2&Ræ÷B76–væVBwVW76VBæÖW2â"¢VæB‚%F†R6ö×ÆWFRöæRÖÖV&–'—FR6fRæBÆÂ#FW67&—F÷'276VBfÆ–FF–öâ&Vf÷&RF—7Æ’â"¢VæB‚$æò6†V6·7VÒ&W—"Â&Æö6²6†ævRÂ&WÆ6VÖVçB÷"6fRw&—F–ærâW†7B&Æö6²W‡÷'B6÷–W2æF—fR'—FW2FòæWrFö7VÖVçBâ"¢Ð¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$æF—fR&Æö6²F—&V7F÷'’(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‡6VÆV7FVE6÷W&6UW&’ÒçVÆÂ’F–Æörç6WDæWWG&Ä'WGFöâ‚$W‡÷'BöæR&Æö6²"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vôæF—fT&Æö6´W‡÷'E–6¶W"†&÷VæBÂ&VB¢Ð¢F–Æörç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôæF—fT&Æö6´W‡÷'E–6¶W"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢&VC¢ÆWG4vô&Æö6´F—&V7F÷'•&V@¢’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢gVâ†W‚‡fÇVS¢–çBÂv–GFƒ¢–çBÒR“¢7G&–ærÒfÇVRçFõ7G&–ærƒb’çWW&66R‚’çE7F'B‡v–GF‚Âsr¢fÂÆ&VÇ2Ò&VBæVçG&–W2æÖ²VçG'’Óà¢$&Æö6²G¶VçG'’æ–BçFõ7G&–ær‚’çE7F'Bƒ"Âsr—Ò(
"‚G¶†W‚†VçG'’æöfg6WB—Þ(	3‚G¶†W‚†VçG'’æVæDöfg6WB—Ò(
"G¶VçG'’æÆVæwF‡Ò'—FW2 ¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$W‡÷'BW†7BæF—fR&Æö6²"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢fÂ6VÆV7FVBÒ&VBæVçG&–W5¶–æFW…Ð¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆWG4vôæF—fT&Æö6´W‡÷'D7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6RæÆWG5övòæ&Æö6²æ–B"Â6VÆV7FVBæ–B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Òç6WDæVvF—fT'WGFöâ‚$6æ6VÂ"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôÖ7FW%G&–æW%F—FÆW2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vôÖ7FW%G&–æW%F—FÆW2ó¢&WGW&à¢gVâ&ævW2†–G3¢Æ—7CÄ–çCâ“¢7G&–ær°¢–b†–G2æ—4V×G’‚’’&WGW&â$æöæR ¢fÂ÷WGWBÒ×WF&ÆTÆ—7DöcÅ7G&–æsâ‚¢f"7F'BÒ–G2æf—'7B‚¢f"&Wf–÷W2Ò7F'@¢f÷"‡fÇVR–â–G2æG&÷ƒ’’°¢–b‡fÇVRÓÒ&Wf–÷W2²’&Wf–÷W2ÒfÇVP¢VÇ6R°¢÷WGWB³Ò–b‡7F'BÓÒ&Wf–÷W2’"G7F'B"VÇ6R"G7F'N(	2G&Wf–÷W2 ¢7F'BÒfÇVP¢&Wf–÷W2ÒfÇVP¢Ð¢Ð¢÷WGWB³Ò–b‡7F'BÓÒ&Wf–÷W2’"G7F'B"VÇ6R"G7F'N(	2G&Wf–÷W2 ¢&WGW&â÷WGWBæ¦ö–åFõ7G&–ær‚"Â"¢Ð¢fÂçVÖ&W&VEVæÆö6¶VBÒ&VBçVæÆö6¶VEF—FÆT–G2æf–ÇFW"²—B–ââãS2Ð¢fÂçVÖ&W&VDÆö6¶VBÒƒâãS2’æf–ÇFW$æ÷B²&VBæ—5VæÆö6¶VB†—B’Ð¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"Ö7FW"G&–æW"F—FÆW2(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚%VæÆö6¶VC¢G·&VBçVæÆö6¶VD6÷VçGÒöbG·&VBçF÷FÅF—FÆW7ÕÆåÆâ"¢VæB‚%G&–æW"F—FÆR„”B“¢G¶–b‡&VBæ—5VæÆö6¶VBƒ’’%VæÆö6¶VB"VÇ6R$Æö6¶VB'ÕÆâ"¢VæB‚$çVÖ&W&VBF—FÆW2(	3S3¢G¶çVÖ&W&VEVæÆö6¶VBç6—¦WÒVæÆö6¶VEÆâ"¢VæB‚%VæÆö6¶VBçVÖ&W&VB”G3¢G·&ævW2†çVÖ&W&VEVæÆö6¶VB—ÕÆâ"¢VæB‚$Æö6¶VBçVÖ&W&VB”G3¢G·&ævW2†çVÖ&W&VDÆö6¶VB—ÕÆâ"¢VæB‚$w&æBF—FÆR„”BSB“¢G¶–b‡&VBæ—5VæÆö6¶VBƒSB’’%VæÆö6¶VB"VÇ6R$Æö6¶VB'ÕÆâ"¢VæB‚$&GFÆRF—FÆR„”BSR“¢G¶–b‡&VBæ—5VæÆö6¶VBƒSR’’%VæÆö6¶VB"VÇ6R$Æö6¶VB'ÕÆåÆâ"¢VæB‚%F†W6R&RF†RSbæF—fRF—FÆRfÆw2–âWfVçEv÷&²&Æö6²2âçVÖ&W&VBVçG&–W2&VÖ–âçVÖW&–2&F†W"F†â&V–ærwVW76VB–çFò7V6–W2÷"F—7Æ’æÖW2â"¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&R&VF–ærâæòF—FÆRVæÆö6²ÂfÆr6†ævRÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ"¢Ð¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$Ö7FW"G&–æW"F—FÆW2"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‡&VBçVæÆö6¶VD6÷VçBÂ&VBçF÷FÅF—FÆW2bb6VÆV7FVE6÷W&6UW&’ÒçVÆÂ’°¢F–Æörç6WDæWWG&Ä'WGFöâ‚%VæÆö6²öæR"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vôÖ7FW%G&–æW%F—FÆU–6¶W"†&÷VæBÂ&VB¢Ð¢Ð¢F–Æörç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôÖ7FW%G&–æW%F—FÆU–6¶W"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢&VC¢ÆWG4vôÖ7FW%G&–æW%F—FÆW5&V@¢’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢fÂÆö6¶VD–G2ÒƒVçF–Â&VBçF÷FÅF—FÆW2’æf–ÇFW$æ÷B²&VBæ—5VæÆö6¶VB†—B’Ð¢fÂÆ&VÇ2ÒÆö6¶VD–G2æÖ²–BÓâ"G´ÆWG4vôÖ7FW%G&–æW%F—FÆW5&VDöæÇ’çF—FÆTÆ&VÂ†–B—Ò(
"”BF–B"Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$6†ö÷6RöæRÆö6¶VBF—FÆR"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆWG4vôÖ7FW%G&–æW%F—FÆT7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6RæÆWG5övòæÖ7FW%÷F—FÆRæ–B"ÂÆö6¶VD–G5¶–æFW…Ò¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Òç6WDæVvF—fT'WGFöâ‚$6æ6VÂ"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôWfVçEv÷&²†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vôWfVçEv÷&²ó¢&WGW&à¢gVâ&ævW2†–G3¢Æ—7CÄ–çCâ“¢7G&–ær°¢–b†–G2æ—4V×G’‚’’&WGW&â$æöæR ¢fÂ÷WGWBÒ×WF&ÆTÆ—7DöcÅ7G&–æsâ‚¢f"7F'BÒ–G2æf—'7B‚¢f"&Wf–÷W2Ò7F'@¢f÷"‡fÇVR–â–G2æG&÷ƒ’’°¢–b‡fÇVRÓÒ&Wf–÷W2²’&Wf–÷W2ÒfÇVP¢VÇ6R°¢÷WGWB³Ò–b‡7F'BÓÒ&Wf–÷W2’"G7F'B"VÇ6R"G7F'N(	2G&Wf–÷W2 ¢7F'BÒfÇVP¢&Wf–÷W2ÒfÇVP¢Ð¢Ð¢÷WGWB³Ò–b‡7F'BÓÒ&Wf–÷W2’"G7F'B"VÇ6R"G7F'N(	2G&Wf–÷W2 ¢&WGW&â÷WGWBæ¦ö–åFõ7G&–ær‚"Â"¢Ð¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"WfVçBv÷&²–ç7V7F÷"(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚$æöç¦W&òv÷&²fÇVW3¢G·&VBææöç¦W&õv÷&´6÷VçGÒöbÃÆâ"¢VæB‚%6WB6FVv÷&—¦VBfÆw3¢G·&VBç6WDfÆt6÷VçGÒöbBÃ“eÆâ"¢f÷"†6FVv÷'’–â&VBçv÷&´6FVv÷&–W2’°¢VæB‚%ÆâG¶6FVv÷'’ææÖWÒv÷&²(
"G¶6FVv÷'’ææöç¦W&õfÇVW2ç6—¦WÒöbG¶6FVv÷'’çF÷FÇÒæöç¦W&õÆâ"¢VæB†–b†6FVv÷'’ææöç¦W&õfÇVW2æ—4V×G’‚’’$æöæUÆâ"VÇ6R6FVv÷'’ææöç¦W&õfÇVW2æ¦ö–åFõ7G&–ær‚"Â"’°¢"G¶—Bæ–GÓÒG¶—BçfÇVWÒ ¢Ò²%Æâ"¢Ð¢f÷"†6FVv÷'’–â&VBæfÆt6FVv÷&–W2’°¢VæB‚%ÆâG¶6FVv÷'’ææÖWÒfÆw2(
"G¶6FVv÷'’ç6WD–G2ç6—¦WÒöbG¶6FVv÷'’çF÷FÇÒ6WEÆâ"¢VæB‚%6WB”G3¢G·&ævW2†6FVv÷'’ç6WD–G2—ÕÆâ"¢Ð¢VæB‚%Æåv÷&²æBfÆr”G2&RÆö6ÂFòF†V—"F—7Æ–VBæF—fR6FVv÷'’âF†Rs"v÷&²VçG&–W2÷WG6–FR´†U‚w2æÖVB&ævW2&VÖ–âVæ6Æ76–f–VBâ"¢VæB‚$æòçVÖW&–2fÇVR÷"fÆr—276–væVBwVW76VB7F÷'’ÖVæ–ærâÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&R&VF–ærâ"¢VæB‚$æòv÷&²6†ævRÂfÆr6†ævRÂ&WÆ6VÖVçB÷"6fRw&—F–ærâW†7B&Æö6²W‡÷'B6÷–W2æF—fR'—FW2FòæWrFö7VÖVçBâ"¢Ð¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$WfVçBv÷&²–ç7V7F÷"(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‡6VÆV7FVE6÷W&6UW&’ÒçVÆÂ’F–Æörç6WDæWWG&Ä'WGFöâ‚$W‡÷'BæF—fR&Æö6²"’²òÂòÓà¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&ä6WDæWWG&Ä'WGFöà¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆWG4vôWfVçEv÷&´W‡÷'D7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢F–Æörç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vô×—7FW'”v–gD†—7F÷'’†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vô×—7FW'”v–gD†—7F÷'’ó¢&WGW&à¢gVâ&ævW2†–G3¢Æ—7CÄ–çCâ“¢7G&–ær°¢–b†–G2æ—4V×G’‚’’&WGW&â$æöæR ¢fÂ÷WGWBÒ×WF&ÆTÆ—7DöcÅ7G&–æsâ‚¢f"7F'BÒ–G2æf—'7B‚¢f"&Wf–÷W2Ò7F'@¢f÷"‡fÇVR–â–G2æG&÷ƒ’’°¢–b‡fÇVRÓÒ&Wf–÷W2²’&Wf–÷W2ÒfÇVP¢VÇ6R°¢÷WGWB³Ò–b‡7F'BÓÒ&Wf–÷W2’"G7F'B"VÇ6R"G7F'N(	2G&Wf–÷W2 ¢7F'BÒfÇVS²&Wf–÷W2ÒfÇVP¢Ð¢Ð¢÷WGWB³Ò–b‡7F'BÓÒ&Wf–÷W2’"G7F'B"VÇ6R"G7F'N(	2G&Wf–÷W2 ¢&WGW&â÷WGWBæ¦ö–åFõ7G&–ær‚"Â"¢Ð¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"×—7FW'’v–gB†—7F÷'’(
"&VBÖöæÇ•ÆåÆâ"¢VæB‚%7F÷&VBu#r7VÖÖ&–W3¢G·&VBç&V6÷&G2ç6—¦WÒöbÆâ"¢VæB‚%&V6V—fVBÖ6&BfÆw26WC¢G·&VBç&V6V—fVD6&D–G2ç6—¦WÒöbBÃ##EÆâ"¢VæB‚%&V6V—fVB6&B”G3¢G·&ævW2‡&VBç&V6V—fVD6&D–G2—ÕÆâ"¢f÷"‡&V6÷&B–â&VBç&V6÷&G2’°¢VæB‚%Æå6Æ÷BG·&V6÷&Bç6Æ÷GÒ(
"6&B2G·&V6÷&Bæ6&D–GÒ(
"G·&V6÷&Bæv–gEG—TæÖWÕÆâ"¢VæB‚%&V6÷&FVBWö6‚6V6öæG3¢G·&V6÷&BæWö6…6V6öæG7ÕÆâ"¢VæB‚%&V6÷&FVBUD3¢G·&V6÷&BçWF5F–ÖW7F×ó¢$÷WG6–FR7W÷'FVB–ç7FçB&ævR'ÕÆâ"¢VæB‚$6&BG—S¢G·&V6÷&Bæ6&EG—WÒ(
"6fVB—FVÒ6÷VçC¢G·&V6÷&Bæ—FVÔ6÷VçGÒ"¢VæB‚"(
"&V6V—fVBÆæwVvR”C¢G·&V6÷&Bç&V6V—fVDÆæwVvT–GÕÆâ"¢–b‡&V6÷&Bæv–gEG—T–BÓÒ’°¢VæB‚%ö¼:–Ööã¢G´ÆWG4võ7V6–W4æÖW2ææÖR‡&V6÷&Bç7V6–W2—Ò‚2G·&V6÷&Bç7V6–W7Ò•Æâ"¢VæB‚%6fVBFWF–Â'—FRBƒS¢G·&V6÷&BæFWF–Ä'—FWÕÆâ"¢VæB‚$÷&–v–æÂG&–æW#¢G·&V6÷&Bæ÷&–v–æÅG&–æW$æÖRó¢%Vç&VF&ÆR6fVBFW‡B'ÕÆâ"¢ÒVÇ6R–b‡&V6÷&Bæv–gEG—T–BÓÒ"’°¢VæB‚$—FV×3¢"¢VæB†–b‡&V6÷&Bæ—FV×2æ—4V×G’‚’’$æòæöç¦W&ò—FVÒVçG&–W2"VÇ6R&V6÷&Bæ—FV×2æ¦ö–åFõ7G&–ær²—FVÒÓà¢"G´ÆWG4vô—FVÔæÖW2ææÖR†—FVÒæ—FVÔ–B—Ò‚2G¶—FVÒæ—FVÔ–GÒ’9rG¶—FVÒçVçF—G—Ò ¢Ò¢VæB‚uÆâr¢ÒVÇ6R°¢VæB‚%6fVB7V6–W2”C¢G·&V6÷&Bç7V6–W7Ò(
"FWF–Â'—FS¢G·&V6÷&BæFWF–Ä'—FWÕÆâ"¢Ð¢Ð¢–b‡&VBç&V6÷&G2æ—4V×G’‚’’VæB‚%Æäæòæöç¦W&òu#r7VÖÖ'’&V6÷&G2&R7F÷&VBåÆâ"¢VæB‚%ÆåF†RFVâæF—fRu#r&V6÷&G2&R&V6V—B7VÖÖ&–W2Âæ÷B&V6öç7G'V7F&ÆRt#rF—7G&–'WF–öâf–ÆW2â"¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&R&VF–ærâæòv–gB–æ¦V7F–öâÂfÆr6†ævRÂ&V6öç7G'V7F–öâÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ"¢Ð¢fÂF–ÆörÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$×—7FW'’v–gB†—7F÷'’(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‡&VBç&V6÷&G2æ—4æ÷DV×G’‚’’F–Æörç6WDæWWG&Ä'WGFöâ‚$W‡÷'BöæRu#r"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vô×—7FW'”v–gDW‡÷'E–6¶W"†&÷VæBÂ&VB¢Ð¢F–Æörç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vô×—7FW'”v–gDW‡÷'E–6¶W"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢&VC¢ÆWG4vô×—7FW'”v–gD†—7F÷'•&V@¢’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢fÂÆ&VÇ2Ò&VBç&V6÷&G2æÖ²&V6÷&BÓà¢%6Æ÷BG·&V6÷&Bç6Æ÷GÒ(
"6&B2G·&V6÷&Bæ6&D–GÒ(
"G·&V6÷&Bæv–gEG—TæÖWÒ ¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$W‡÷'BæF—fRu#r7VÖÖ'’"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢fÂ6VÆV7FVBÒ&VBç&V6÷&G5¶–æFW…Ð¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆWG4vô×—7FW'”v–gD7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6RæÆWG5övòçw#rç6Æ÷B"Â6VÆV7FVBç6Æ÷BÒ¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Òç6WDæVvF—fT'WGFöâ‚$6æ6VÂ"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôvÖT÷F–öç2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vôvÖT÷F–öç2ó¢&WGW&à¢fÂFWF–ÂÒ"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"vÖR÷F–öç2(
"&VBÖöæÇ•ÆåÆâ"°¢%6fVBFW‡B×7VVB”C¢G·&VBçFW‡E7VVD–GÕÆâ"°¢$&GFÆRæ–ÖF–öç3¢G¶–b‡&VBææ–ÖF–öç4Væ&ÆVB’$öâ"VÇ6R$öfb'Ò‡6fVB&—BG·&VBæ&GFÆTæ–ÖF–öä–GÒ•Æâ"°¢$&GFÆR7G–ÆS¢G¶–b‡&VBæ&GFÆU7G–ÆT–BÓÒ’%7v—F6‚"VÇ6R%6WB'Ò‡6fVB&—BG·&VBæ&GFÆU7G–ÆT–GÒ•Æâ"°¢$6öæf–wW&F–öâÆæwVvS¢G·&VBæÆæwVvTæÖRó¢%VæÆ—7FVB6fVBÆæwVvR”B'ÕÆâ"°¢$ÆæwVvR”C¢G·&VBæÆæwVvT–GÒ(
"6fVBÆæwVvR–æFWƒ¢G·&VBç&tÆæwVvT–æFW‡ÕÆåÆâ"°¢%Væ¶æ÷vâWW"#B&—G3¢G·&VBçVæ¶æ÷våWW$&—G7Ò††W‚G·&VBçVæ¶æ÷våWW$&—G2çFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—Ò•Æâ"°¢%&rv÷&C¢G·&VBç&uv÷&GÒ‡6–væVBG·&VBç6–væVD6öæf–ufÇVWÒ•Æâ"°¢%v÷&B&—G3¢G·&VBç&uv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆå&r'—FW3¢"²&VBç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆåF†R6öæf–wW&F–öâÆæwVvRÖ’F–ffW"g&öÒF†RG&–æW"FFâFW‡B×7VVBÖVçR6†ö–6W2æBÖVæ–æw2öbF†RWW"&—G2&Ræ÷B–æfW'&VBâÆÂfÇVW2&VÖ–âW†7FÇ’26fVBâæò÷F–öâ6†ævW2Âæ÷&ÖÆ—¦F–öâÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ ¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$vÖR÷F–öç2(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôÆ7E6fVB†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4vôÆ7E6fVBó¢&WGW&à¢fÂFWF–ÂÒ"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"Æ7B6fVB(
"&VBÖöæÇ•ÆåÆâ"°¢%6fVB6ÆVæF#¢G·&VBæ6ÆVæF%FW‡Bó¢%Væf–Æ&ÆR'ÕÆâ"°¢‡&VBçVæf–Æ&ÆU&V6öãòæÆWB²"F—EÆâ"Òó¢""’°¢%Æå&V6—6–öã¢Ö–çWFRâ6V6öæG2æBF–ÖR¦öæR&Ræ÷B7WÆ–VB'’F†—2f–VÆBåÆâ"°¢%–V#¢G·&VBç–V'Ò‡6fVB–V"öfg6WBG·&VBç&u–V'Ò•ÆäÖöçFƒ¢G·&VBæÖöçF‡Ò‡6fVBÖöçF‚–æFW‚G·&VBç&tÖöçF‡Ò•Æâ"°¢$F“¢G·&VBæF—ÕÆä†÷W#¢G·&VBæ†÷W'ÕÆäÖ–çWFS¢G·&VBæÖ–çWFWÕÆåÆâ"°¢%&r6fVBv÷&C¢G·&VBç&uv÷&GÕÆä&—G3¢G·&VBç&uv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆâ"°¢%&r'—FW3¢"²&VBç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆä–çfÆ–B6ÆVæF"6ö×öæVçG2&R&WF–æVBv—F†÷WBæ÷&ÖÆ—¦F–öâ÷"&W—"âfÆ–B6ÆVæF"fÇVRFöW2æ÷BW7F&Æ—6‚6Æö6²67W&7’÷"VÆ6VBF–ÖRâæòUD2öÆö6Â6öçfW'6–öâÂF–ÖW"WFFRÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ ¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$Æ7B6fVB(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4võÆ•F–ÖR†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæBÇÂF6†&ö&D'W7’’&WGW&à¢fÂ&VBÒ&÷VæBæÆWG4võÆ•F–ÖRó¢&WGW&à¢fÂv&æ–ærÒ–b‡&VBæ†46æöæ–6ÄÖ–çWFW4æE6V6öæG2’""VÇ6P¢%ÆäÖ–çWFW2÷"6V6öæG2&R÷WG6–FRâãS’âF†V—"6fVBfÇVW2&R&WF–æVBv—F†÷WB6''––ærÂ6Æ×–ær÷"&W—"åÆâ ¢fÂFWF–ÂÒ"G·&VBçfW'6–öâæF—7Æ”æÖWÒ(
"&V6÷&FVBÆ—F–ÖR(
"&VBÖöæÇ•ÆåÆâ"°¢%6fVBÆ—F–ÖS¢G·&VBç6fVEF–ÖUFW‡GÕÆä†÷W'3¢G·&VBæ†÷W'7ÕÆäÖ–çWFW3¢G·&VBæÖ–çWFW7ÕÆå6V6öæG3¢G·&VBç6V6öæG7ÕÆâ"²v&æ–ær°¢%ÆåF†—2—2F†R&V6÷&FVBvÖRF–ÖW"Âæ÷BVÆ6VBvÆÂF–ÖR÷"F†R7W'&VçB6Æö6²åÆåÆâ"°¢$Æ7B×6fVBf–VÆB‡&rv÷&B“¢G·&VBç&tÆ7E6fVEv÷&GÕÆâ"°¢$Æ7B×6fVB&—G3¢G·&VBç&tÆ7E6fVEv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆåÆâ"°¢$6ö×ÆWFRæF—fRÆ—F–ÖR&Æö6²ƒ‚'—FW2“¥Æâ"²&VBç&t&Æö6´'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆäÆÂ#æF—fR&Æö6²5$7276VBâæò6Æö6²6öçfW'6–öâÂF–ÖW"Gfæ6RÂ&W6WBÂæ÷&ÖÆ—¦F–öâÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ ¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%&V6÷&FVBÆ—F–ÖR(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢ç6WDæWWG&Ä'WGFöâ‚$Æ7B6fVB"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vôÆ7E6fVB†&÷VæB’Òç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4võG&–æW"†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂG&–æW"Ò&÷VæBæÆWG4võG&–æW"ó¢&WGW&à¢fÂvVæFW"Ò–b‡G&–æW"ævVæFW"ÓÒ’$ÖÆR"VÇ6R$fVÖÆR ¢fÂ7F'FW"Òv†Vâ‡G&–æW"ç7F'FW$6†ö–6T–B’²Óâ$æ÷B6VÆV7FVB#²Óâ%–¶6‡R#²"Óâ$VWfVR#²VÇ6RÓâ%Væ¶æ÷vâ‚G·G&–æW"ç7F'FW$6†ö–6T–GÒ’"Ð¢fÂ7F'FW$vVæFW"ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’ævWD÷$çVÆÂ‡G&–æW"ç7F'FW$vVæFW$–B¢ó¢%Væ¶æ÷vâ‚G·G&–æW"ç7F'FW$vVæFW$–GÒ’ ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·G&–æW"çfW'6–öâæF—7Æ”æÖWÒ(
"G&–æW"FFÆåÆâ"¢VæB‚%G&–æW#¢G·G&–æW"ææÖRæ–d&Ææ²²$æ÷B6WB"×ÕÆävVæFW#¢FvVæFW%Æâ"¢VæB‚$ÆæwVvS¢G·G&–æW"æÆæwVvTæÖWÒ„”BG·G&–æW"æÆæwVvT–GÒ•Æâ"¢VæB‚$F—7Æ–VBG&–æW"”C¢G·G&–æW"æF—7Æ•G&–æW$–GÕÆâ"¢VæB‚%&r”C3#¢G·G&–æW"æ–C3'ÕÆåD”Cc¢G·G&–æW"çF–CgÕÆå4”Cc¢G·G&–æW"ç6–CgÕÆåÆâ"¢VæB‚%&—fÃ¢G·G&–æW"ç&—fÄæÖRæ–d&Ææ²²$æ÷B6WB"×ÕÆå6fVBÖöæW“¢G·G&–æW"æÖöæW—ÕÆâ"¢VæB‚$w–Ò&FvW3¢G·G&–æW"æ&FvT6÷VçGÒó…Æâ"¢VæB‚$æF—fRG&–æW"Ö6Æ72–æFWƒ¢G·G&–æW"çG&–æW$6Æ74–æFW‡ÕÆâ"¢VæB‚$÷fW'v÷&ÆBÖöFVÂvVæFW"”C¢G·G&–æW"æ÷fW'v÷&ÆDvVæFW$–GÕÆâ"¢VæB‚%6fVB'FæW"6†ö–6S¢G7F'FW%Æå6fVB'FæW"vVæFW#¢G7F'FW$vVæFW%ÆåÆâ"¢VæB‚%'FæW"6†ö–6R—26fVBG&–æW"ÖWFFFÂ6W&FRg&öÒF†R'G’÷'FæW"ö¼:–Ööâ&V6÷&Bâ"¢VæB‚$ÖöæW’—2F†R&r6fVBVç6–væVBfÇVS²7W'&Væ7’Æ–Ö—G2æB7F÷'’6öç6—7FVæ7’&Ræ÷BWfÇVFVBåÆåÆâ"¢VæB‚%G&–æW"Ö6Æ72æBÖöFVÂfÇVW2&VÖ–âçVÖW&–2&V6W6Ræò6ö×ÆWFRæF—fR6Æ72ÖæÖR6FÆöwVR—2&W6VçBåÆåÆâ"¢VæB‚$ÆÂ#æF—fR&Æö6²5$7276VB&Vf÷&R&VF–ærâG&–æW"æÖR6â&RVF—FVBF‡&÷Vv‚&Wf–WvVBæWrÖ6÷’fÆ÷s²F†R÷F†W"F—7Æ–VBf–VÆG2&VÖ–â&VBÖöæÇ’â"¢Ð¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BG&–æW"FF#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆWG4võG&–æW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G&–æW"FF"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢ç6WDæWWG&Ä'WGFöâ‚%&V6÷&FVBÆ—F–ÖR"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4võÆ•F–ÖR†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$vÖR÷F–öç2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’6†÷tÆWG4vôvÖT÷F–öç2†&÷VæB’Òç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7G&–æW"†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂG&–æW"Ò&÷VæBæ&G7G&–æW"ó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·G&–æW"çfW'6–öâæF—7Æ”æÖWÒ(
"G&–æW"FFÆâ"¢VæB‚%6fRÆ–÷WC¢G·G&–æW"ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"¢VæB‚%G&–æW#¢G·G&–æW"ææÖRæ–d&Ææ²²$æ÷B6WB"×ÕÆävVæFW#¢G¶–b‡G&–æW"ævVæFW"ÓÒ’$ÖÆR"VÇ6R$fVÖÆR'ÕÆâ"¢VæB‚$ÆæwVvS¢G·G&–æW"æÆæwVvTæÖWÒ„”BG·G&–æW"æÆæwVvT–GÒ•Æâ"¢VæB‚$F—7Æ–VBG&–æW"”C¢G·G&–æW"æF—7Æ•G&–æW$–GÕÆâ"¢VæB‚%&r”C3#¢G·G&–æW"æ–C3'ÕÆåD”Cc¢G·G&–æW"çF–CgÕÆå4”Cc¢G·G&–æW"ç6–CgÕÆåÆâ"¢VæB‚%&—fÃ¢G·G&–æW"ç&—fÄæÖRæ–d&Ææ²²$æ÷B6WB"×ÕÆäÖöæW“¢G·G&–æW"æÖöæW—ÕÆâ"¢VæB‚%6fVB&FvR6÷VçC¢G·G&–æW"æ&FvT6÷VçGÒó…ÆävÖRÖ6ÆV"fÆs¢G·G&–æW"ævÖT6ÆV'ÕÆåÆâ"¢VæB‚$&FvR6÷VçBæBvÖRÖ6ÆV"fÆr&R6fVBfÇVW3²–æF—f–GVÂ&FvW2æB7F÷'’6öç6—7FVæ7’&Ræ÷BWfÇVFVBåÆåÆâ"¢&÷VæBæ&G7G&–æW$V&æ6SòæÆWB²VæB†&G7V&æ6U7VÖÖ'’†—B’“²VæB‚%Æâ"’Ð¢&÷VæBæ&G7vÖT÷F–öç3òæÆWB²VæB†&G7vÖT÷F–öç57VÖÖ'’†—B’“²VæB‚%Æâ"’Ð¢&÷VæBæ&G7Æ•F–ÖSòæÆWB²VæB†&G7Æ•F–ÖU7VÖÖ'’†—B’“²VæB‚%Æâ"’Ð¢&÷VæBæ&G7F–ÖW7F×3òæÆWB²VæB†&G7F–ÖW7F×7VÖÖ'’†—B’“²VæB‚%Æâ"’Ð¢VæB‚$æF—fRv†öÆRÖf–ÆRÔCR76VB&Vf÷&R&VF–ærâG&–æW"æÖRÂG&–æW"”BæBÖöæW’÷Vâ–âF†R&Wf–WvVBæF—fRVF—F÷#²F†R÷F†W"F—7Æ–VBf–VÆG2&VÖ–â&VBÖöæÇ’â"¢Ð¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BG&–æW"FF#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7G&–æW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢–b†&÷VæBæ&G7&V6÷&E7FF—7F–72ÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ%&V6÷&B7FF—7F–72(
"&VBÖöæÇ’ ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&V6÷&G2†&÷VæB’Ð¢Ò¢Ð¢–b†&÷VæBæ&G7FF—F–öæÅ&V6÷&G2ÒçVÆÂ’&öG’æFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$FF—F–öæÂ&V6÷&BG&–æW'2(
"&VBÖöæÇ’ ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7FF—F–öæÅ&V6÷&G2†&÷VæB’Ð¢Ò¢–b†&÷VæBæ&G7&GFÆUG&–æW%7FGW2ÒçVÆÂ’&öG’æFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$&GFÆR×G&–æW"7FGW2(
"&VBÖöæÇ’ ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUG&–æW$f–ÇFW'2†&÷VæB’Ð¢Ò¢–b†&÷VæBæ&G7†öæW•G&VW2ÒçVÆÂ’&öG’æFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$†öæW’G&VW2(
"VF—B ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7†öæW•G&VW2†&÷VæB’Ð¢Ò¢–b†&÷VæBæ&G7Væ6÷VçFW"ÒçVÆÂ’&öG’æFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$Væ6÷VçFW'2(
"÷fW'f–WrVF—B ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7Væ6÷VçFW$ÖVçR†&÷VæB’Ð¢Ò¢–b†&÷VæBæ&G7&W''•Æ÷G2ÒçVÆÂ’&öG’æFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$&W''’Æ÷B&V6÷&G2(
"VF—BF–Ö–ær ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&W''•Æ÷G2†&÷VæB’Ð¢Ò¢–b†&÷VæBæ&G7Væ–öå&ööÒÒçVÆÂ’&öG’æFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%Væ–öâ&ööÒ6fVBFF(
"VF—B ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7Væ–öå&ööÒ†&÷VæB’Ð¢Ò¢–b†&÷VæBæ&G7&æFöÔw&÷W2ÒçVÆÂ’&öG’æFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%&æFöÒw&÷W&V6÷&G2(
"VF—B ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&æFöÔw&÷W2†&÷VæB’Ð¢Ò¢–b†&÷VæBæ&G7ÖVçU6VÆV7F–öâÒçVÆÂ’&öG’æFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6fVBÖVçRÆ–÷WB(
"&VBÖöæÇ’ ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7ÖVçU6VÆV7F–öâ†&÷VæB’Ð¢Ò¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G&–æW"FF"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b†&÷VæBæ&G7çW'6W'’ÒçVÆÂ’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$çW'6W'’"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7çW'6W'’†&÷VæB’Ð¢–b†&÷VæBæ&G7&GFÆUF÷vW"ÒçVÆÂ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚$&GFÆRF÷vW""’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUF÷vW"†&÷VæB’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW5G&–æW"†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂG&–æW"Ò&÷VæBæÆVvVæG4&6WW5G&–æW"ó¢&WGW&à¢gVâ6fVB‡fÇVS¢Æöæsò“¢7G&–ærÒfÇVSòçFõ7G&–ær‚’ó¢$æ÷B&W6VçB ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·G&–æW"çfW'6–öâæF—7Æ”æÖWÒ(
"G&–æW"FFÆåÆâ"¢VæB‚%G&–æW#¢G·G&–æW"ææÖRæ–d&Ææ²²$æ÷B6WB"×ÕÆävVæFW#¢G¶–b‡G&–æW"ævVæFW"ÓÒ’$ÖÆR"VÇ6R$fVÖÆR'ÕÆâ"¢VæB‚$ÆæwVvS¢G·G&–æW"æÆæwVvTæÖWÒ„”BG·G&–æW"æÆæwVvT–GÒ•Æâ"¢VæB‚$F—7Æ–VBG&–æW"”C¢G·G&–æW"æF—7Æ•G&–æW$–GÕÆâ"¢VæB‚%&r”C3#¢G·G&–æW"æ–C3'ÕÆåD”Cc¢G·G&–æW"çF–CgÕÆå4”Cc¢G·G&–æW"ç6–CgÕÆåÆâ"¢VæB‚$ÖöæW“¢G·6fVB‡G&–æW"æÖöæW’—ÕÆä7W'&VçBÖW&—Bö–çG3¢G·6fVB‡G&–æW"æÖW&—D7W'&VçB—ÕÆâ"¢VæB‚%F÷FÂÖW&—Bö–çG2V&æVC¢G·6fVB‡G&–æW"æÖW&—EF÷FÂ—ÕÆâ"¢VæB‚$vÆ‡’W‡VF—F–öâFVÒ&æ³¢G·6fVB‡G&–æW"æW‡VF—F–öå&æ²—ÕÆå6F6†VÂWw&FW3¢G·6fVB‡G&–æW"ç6F6†VÅWw&FW2—ÕÆåÆâ"¢VæB‚$'6VçB6÷VçFW'27F’Ö&¶VBæ÷B&W6VçBâÖW&—BF÷FÇ2Â&W6V&6‚&öw&W72æB7F÷'’6öç6—7FVæ7’&Ræ÷BWfÇVFVBåÆåÆâ"¢VæB‚$æF—fR6öçF–æW"–çFVw&—G’76VB&Vf÷&R&VF–ærâG&–æW"æÖRÂG&–æW"”BæBÖöæW’÷Vâ–âF†R6†&VB&Wf–WvVBVF—F÷"â7W'&VçBæBF÷FÂÖW&—Bö–çG2ÂvÆ‡’W‡VF—F–öâFVÒ&æ²æB6F6†VÂWw&FW2÷Vâ–âF—FÆR×7V6–f–2&Wf–WvVBVF—F÷'2v†VâF†V—"æF—fR&V6÷&G2&R&W6VçBâ"¢Ð¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BG&–æW"FF#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG4&6WW5G&–æW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢–b‡6÷W&6RÒçVÆÂbb‡G&–æW"æÖW&—D7W'&VçBÒçVÆÂÇÂG&–æW"æÖW&—EF÷FÂÒçVÆÂ’’°¢FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BÖW&—Bö–çG2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG4&6WW4ÖW&—Eö–çD7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b‡6÷W&6RÒçVÆÂbbG&–æW"æW‡VF—F–öå&æ²ÒçVÆÂ’°¢FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BvÆ‡’W‡VF—F–öâFVÒ&æ²#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG4&6WW4W‡VF—F–öå&æ´7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢–b‡6÷W&6RÒçVÆÂbbG&–æW"ç6F6†VÅWw&FW2ÒçVÆÂ’°¢FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B6F6†VÂWw&FW2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG4&6WW56F6†VÅWw&FT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G&–æW"FF"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWEG&–æW"†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂG&–æW"Ò&÷VæBç66&ÆWEf–öÆWEG&–æW"ó¢&WGW&à¢gVâ6fVB‡fÇVS¢Æöæsò“¢7G&–ærÒfÇVSòçFõ7G&–ær‚’ó¢$æ÷B&W6VçB ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·G&–æW"çfW'6–öâæF—7Æ”æÖWÒ(
"G&–æW"FFÆåÆâ"¢VæB‚%G&–æW#¢G·G&–æW"ææÖRæ–d&Ææ²²$æ÷B6WB"×ÕÆävVæFW#¢G¶–b‡G&–æW"ævVæFW"ÓÒ’$ÖÆR"VÇ6R$fVÖÆR'ÕÆâ"¢VæB‚$ÆæwVvS¢G·G&–æW"æÆæwVvTæÖWÒ„”BG·G&–æW"æÆæwVvT–GÒ•Æâ"¢VæB‚$F—7Æ–VBG&–æW"”C¢G·G&–æW"æF—7Æ•G&–æW$–GÕÆâ"¢VæB‚%&r”C3#¢G·G&–æW"æ–C3'ÕÆåD”Cc¢G·G&–æW"çF–CgÕÆå4”Cc¢G·G&–æW"ç6–CgÕÆåÆâ"¢VæB‚$&—'F†F’†ÖöçF‚öF’“¢G¶–b‡G&–æW"æ&—'F„ÖöçF‚ÓÒ’$æ÷B6WB"VÇ6R"G·G&–æW"æ&—'F„ÖöçF‡ÒòG·G&–æW"æ&—'F„F—Ò'ÕÆâ"¢VæB‚$ÖöæW“¢G·6fVB‡G&–æW"æÖöæW’—ÕÆäÆVwVRö–çG3¢G·6fVB‡G&–æW"æÆVwVUö–çG2—ÕÆâ"¢VæB‚$&ÇVV&W''’ö–çG3¢G·6fVB‡G&–æW"æ&ÇVV&W''•ö–çG2—ÕÆåÆâ"¢VæB‚$7W'&Væ6–W2&R&r6fVBVç6–væVBfÇVW3²vÖR&Ææ6RÆ–Ö—G2æB7F÷'’6öç6—7FVæ7’&Ræ÷BWfÇVFVBâ"¢VæB‚$'6VçB6÷VçFW'27F’Ö&¶VBæ÷B&W6VçBæBFòæ÷BW7F&Æ—6‚DÄ2÷"&öw&W76–öâ7FGW2åÆåÆâ"¢VæB‚$æF—fR6öçF–æW"–çFVw&—G’76VB&Vf÷&R&VF–ærâG&–æW"æÖRÂG&–æW"”BæBÖöæW’÷Vâ–âF†R&Wf–WvVBæF—fRVF—F÷"v†VâF†V—"æF—fRf–VÆG2&R&W6VçC²F†R÷F†W"F—7Æ–VBf–VÆG2&VÖ–â&VBÖöæÇ’â"¢Ð¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BG&–æW"FF#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6…66&ÆWEf–öÆWEG&–æW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G&–æW"FF"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦G&–æW"†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂG&–æW"Ò&÷VæBæÆVvVæG5¦G&–æW"ó¢&WGW&à¢gVâ6fVB‡fÇVS¢Æöæsò“¢7G&–ærÒfÇVSòçFõ7G&–ær‚’ó¢$æ÷B&W6VçB ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·G&–æW"çfW'6–öâæF—7Æ”æÖWÒ(
"G&–æW"FFÆåÆâ"¢VæB‚%&Wf—6–öâÖ&¶W#¢G·G&–æW"ç&Wf—6–öãòæF—7Æ”æÖRó¢$æ÷B&W6VçB'ÕÆâ"¢VæB‚%G&–æW#¢G·G&–æW"ææÖRæ–d&Ææ²²$æ÷B6WB"×ÕÆävVæFW#¢G¶–b‡G&–æW"ævVæFW"ÓÒ’$ÖÆR"VÇ6R$fVÖÆR'ÕÆâ"¢VæB‚$ÆæwVvS¢G·G&–æW"æÆæwVvTæÖWÒ„”BG·G&–æW"æÆæwVvT–GÒ•Æâ"¢VæB‚$F—7Æ–VBG&–æW"”C¢G·G&–æW"æF—7Æ•G&–æW$–GÕÆâ"¢VæB‚%&r”C3#¢G·G&–æW"æ–C3'ÕÆåD”Cc¢G·G&–æW"çF–CgÕÆå4”Cc¢G·G&–æW"ç6–CgÕÆåÆâ"¢VæB‚$&—'F†F’†ÖöçF‚öF’“¢G¶–b‡G&–æW"æ&—'F„ÖöçF‚ÓÒ’$æ÷B6WB"VÇ6R"G·G&–æW"æ&—'F„ÖöçF‡ÒòG·G&–æW"æ&—'F„F—Ò'ÕÆâ"¢VæB‚$&—'F†F’v—fVã¢G·G&–æW"çv4&—'F†F”v—fVçÕÆäö'6W'fVB&—'F†F“¢G·G&–æW"æö'6W'fVD&—'F†F—ÕÆâ"¢VæB‚$ö'6W'fVB&—'F†F’–V"‡&r“¢G·G&–æW"æö'6W'fVD&—'F†F•–V'ÕÆåÆâ"¢VæB‚$ÖöæW“¢G·6fVB‡G&–æW"æÖöæW’—ÕÆå¢Ô&÷–ÆRF–6¶WBö–çG3¢G·6fVB‡G&–æW"ç&÷–ÆUF–6¶WEö–çG2—ÕÆâ"¢VæB‚$–æf–æ—FR¢Ô&÷–ÆRF–6¶WBö–çG3¢G·6fVB‡G&–æW"æ–æf–æ—FUF–6¶WEö–çG2—ÕÆâ"¢VæB‚%Æ–W"…‡&r“¢G·G&–æW"çÆ–W$‡ÕÆå6fVBÖVvvVvR‡&r“¢G·G&–æW"æÖVvvVvUW&6VçGÕÆåÆâ"¢VæB‚$6÷VçFW'2æBÖVvvVvR&R&r6fVBfÇVW3²&Ææ6RÆ–Ö—G2ÂW&6VçFvR66Æ–æræB7F÷'’6öç6—7FVæ7’&Ræ÷BWfÇVFVBâ"¢VæB‚$'6VçB6÷VçFW'27F’Ö&¶VBæ÷B&W6VçBæBFòæ÷BW7F&Æ—6‚DÄ2÷"&öw&W76–öâ7FGW2åÆåÆâ"¢VæB‚$æF—fR6öçF–æW"–çFVw&—G’76VB&Vf÷&R&VF–ærâG&–æW"æÖRÂG&–æW"”BæBÖöæW’÷Vâ–âF†R&Wf–WvVBæF—fRVF—F÷"v†VâF†V—"æF—fRf–VÆG2&R&W6VçC²F†R÷F†W"F—7Æ–VBf–VÆG2&VÖ–â&VBÖöæÇ’â"¢Ð¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BG&–æW"FF#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„ÆVvVæG5¦G&–æW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G&–æW"FF"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4võö¶VFW‚†&÷VæC¢7v—F6…&VE6W76–öâÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBæÆWG4võö¶VFW‚ó¢&WGW&à¢fÂÆ7EvRÒ†FW‚æVçG&–W2ç6—¦RÒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ$¶çFó¢G¶FW‚æ¶çFõ6VVä6÷VçGÒóS6VVâ(
"G¶FW‚æ¶çFô6Vv‡D6÷VçGÒóS6Vv‡EÆâ"°¢$–æ6ÇVF–ærÖVÇFâôÖVÆÖWFÃ¢G¶FW‚ç6VVä6÷VçGÒóS26VVâ(
"G¶FW‚æ6Vv‡D6÷VçGÒóS26Vv‡EÆâ"°¢%6fVBf–WvVBVçG'“¢2G¶FW‚æ7W'&VçEf–WvVE7V6–W7ÕÆäæF–öæÂFW‚fÆr‡&r“¢G¶FW‚ææF–öæÄFW„fÆwÕÆåÆâ"°¢%6fVB&Vv—7G&F–öâfÆw2öæÇ’âF†W6R6÷VçG2Fòæ÷BW7F&Æ—6‚7F÷'’÷"6ö×ÆWF–öâ&Wv&G2â ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢f÷"†VçG'’–âFW‚æVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"2G¶VçG'’ç7V6–W4–GÒG´ÆWG4võ7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò(
""°¢"G¶–b†VçG'’ç6VVâ’%6VVâ"VÇ6R$æ÷B6VVâ'Ò(
"G¶–b†VçG'’æ6Vv‡B’$6Vv‡B"VÇ6R$æ÷B6Vv‡B'Ò ¢6WDöä6Æ–6´Æ—7FVæW"²6†÷tÆWG4vôFW„VçG'’†&÷VæBÂVçG'’ç7V6–W4–B’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"ö¼:–FW‚G·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆWG4võö¶VFW‚†&÷VæBÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆWG4võö¶VFW‚†&÷VæBÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vôFW„VçG'’†&÷VæC¢7v—F6…&VE6W76–öâÂ7V6–W3¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæÆWG4võö¶VFWƒòæVçG'’‡7V6–W2’ó¢&WGW&à¢fÂf&–çG2ÒÆ—7Döb‚$ÖÆRòvVæFW&ÆW72"Â$fVÖÆR"Â%6†–ç’ÖÆRòvVæFW&ÆW72"Â%6†–ç’fVÖÆR"¢gVâfÆw2‡fÇVW3¢Æ—7CÄ&ööÆVãâ“¢7G&–ærÒf&–çG2æÖ–æFW†VB²’ÂÆ&VÂÓâ"FÆ&VÃ¢G·fÇVW5¶•×Ò"Òæ¦ö–åFõ7G&–ær‚%Æâ"¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"2G¶VçG'’ç7V6–W4–GÒG´ÆWG4võ7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò(
"ö¼:–FW…ÆåÆâ"¢VæB‚$6Vv‡C¢G¶VçG'’æ6Vv‡GÕÆå6VVã¢G¶VçG'’ç6VVçÕÆåÆä&6R×7V6–W26VVâfÆw5ÆâG¶fÆw2†VçG'’ç6VVåf&–çG2—ÕÆåÆâ"¢VæB‚$&6R×7V6–W2F—7Æ–VBfÆw5ÆâG¶fÆw2†VçG'’æF—7Æ–VEf&–çG2—ÕÆåÆâ"¢VæB‚%&Vv—7FW&VBÆæwVvW3¢G¶VçG'’æÆæwVvW2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR&V6÷&FVB"×ÕÆåÆâ"¢f÷"†f÷&Ò–âVçG'’æf÷&×2’°¢VæB‚%6—¦R&V6÷&G2(
"G¶–b†f÷&Òæf÷&Ô–BÓÒ’&&6Rf÷&Ò"VÇ6R'6fVBf÷&Ò”BG¶f÷&Òæf÷&Ô–GÒ'ÕÆâ"¢f÷"‡&V6÷&B–âf÷&Òç6—¦U&V6÷&G2’VæB‚"G·&V6÷&Bæw&÷WÓ¢G¶–b‡&V6÷&Bæ—5&V6÷&FVB’%&V6÷&FVB"VÇ6R$æ÷B&V6÷&FVB'Ò(
""°¢&†V–v‡B66Æ"G·&V6÷&Bæ†V–v‡E66Æ'ÒÂvV–v‡B66Æ"G·&V6÷&BçvV–v‡E66Æ'ÒÂfÆvvVBG·&V6÷&Bæ—4fÆvvVGÒ‡&rG·&V6÷&BæfÆt'—FWÒ•Æâ"¢VæB‚%Æâ"¢Ð¢VæB‚$fÆw2æB6—¦R&V6÷&G2&R6†÷vâ26fVC²6öçG&F–7F÷'’fÆw2&Ræ÷B&W—&VBâ6—¦R66Æ'2&Ræ÷B'6öÇWFRÖV7W&VÖVçG2â"¢VæB‚$ÇFW&æFRÖf÷&Ò6VVâöF—7Æ’fÆw2Â7F÷'’&öw&W72æB&Wv&G2&R÷WG6–FRF†—2f–WrâæWræF—fR6VVâ÷"6Vv‡B&Vv—7G&F–öâ6â&R6fVBFò&Wf–WvVBæWr6÷“²W†—7F–ær&Vv—7G&F–öç2&R&W6W'fVBâ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G´ÆWG4võ7V6–W4æÖW2ææÖR‡7V6–W2—Ò"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢ç6WE÷6—F—fT'WGFöâ‚$FB&Vv—7G&F–öâ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆWG4võö¶VFW„VF—F÷"†&÷VæBÂ7V6–W2¢Òç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆWG4võö¶VFW„VF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâÂ7V6–W3¢–çB’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆWG4võö¶VFW„7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æFW‚ç7V6–W2"Â7V6–W2¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆDFW…&Vv–öç2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBç7v÷&E6†–VÆEö¶VFW‚ó¢&WGW&à¢fÂÆ&VÇ2Ò7v÷&E6†–VÆDFW…&Vv–öâçfÇVW2‚’æÖ²&Vv–öâÓâFW‚ç&Vv–öâ‡&Vv–öâ“òæÆWB°¢"G·&Vv–öâæF—7Æ”æÖWÓ¢G¶—Bç6VVä6÷VçGÒòG·&Vv–öâæVçG'”6÷VçGÒ6VVâ(
"G¶—Bæ6Vv‡D6÷VçGÒòG·&Vv–öâæVçG'”6÷VçGÒ6Vv‡B ¢Òó¢"G·&Vv–öâæF—7Æ”æÖWÓ¢æ÷B&W6VçB–âF†—26fR"Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"ö¼:–FW‚"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢fÂ&Vv–öâÒ7v÷&E6†–VÆDFW…&Vv–öâçfÇVW2‚•¶–æFW…Ð¢–b‡6W76–öâÓÓÒ&÷VæBbbFW‚ç&Vv–öâ‡&Vv–öâ’ÒçVÆÂ’6†÷u7v÷&E6†–VÆEö¶VFW‚†&÷VæBÂ&Vv–öâÂ¢Òç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆEö¶VFW‚†&÷VæC¢7v—F6…&VE6W76–öâÂ&Vv–öã¢7v÷&E6†–VÆDFW…&Vv–öâÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂF&ÆRÒ&÷VæBç7v÷&E6†–VÆEö¶VFWƒòç&Vv–öâ‡&Vv–öâ’ó¢&WGW&à¢fÂÆ7EvRÒ‡F&ÆRæVçG&–W2ç6—¦RÒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G·&Vv–öâæF—7Æ”æÖWÓ¢G·F&ÆRç6VVä6÷VçGÒòG·&Vv–öâæVçG'”6÷VçGÒ6VVâ(
"G·F&ÆRæ6Vv‡D6÷VçGÒòG·&Vv–öâæVçG'”6÷VçGÒ6Vv‡EÆåÆâ"°¢%6fVB&Vv–öæÂ&V6÷&G2öæÇ’â6†&VB7V6–W26â†fR6W&FR&Vv–öæÂ&V6÷&G3²F†W6R6÷VçG2Fòæ÷B&÷fR7F÷'’&öw&W72Â&Wv&G2÷"DÄ2VçF—FÆVÖVçBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢f÷"†VçG'’–âF&ÆRæVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG'’æçVÖ&W"çFõ7G&–ær‚’çE7F'Bƒ2Âsr—Ò(
"Gµ7v÷&E6†–VÆE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò(
""°¢"G¶–b†VçG'’ç6VVâ’%6VVâ"VÇ6R$æ÷B6VVâ'Ò(
"G¶–b†VçG'’æ6Vv‡B’$6Vv‡B"VÇ6R$æ÷B6Vv‡B'Ò ¢6WDöä6Æ–6´Æ—7FVæW"²6†÷u7v÷&E6†–VÆDFW„VçG'’†&÷VæBÂ&Vv–öâÂVçG'’æçVÖ&W"’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G·&Vv–öâæF—7Æ”æÖWÒö¼:–FW‚G·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚%&Vv–öç2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u7v÷&E6†–VÆDFW…&Vv–öç2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u7v÷&E6†–VÆEö¶VFW‚†&÷VæBÂ&Vv–öâÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u7v÷&E6†–VÆEö¶VFW‚†&÷VæBÂ&Vv–öâÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆDFW„VçG'’†&÷VæC¢7v—F6…&VE6W76–öâÂ&Vv–öã¢7v÷&E6†–VÆDFW…&Vv–öâÂçVÖ&W#¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBç7v÷&E6†–VÆEö¶VFWƒòç&Vv–öâ‡&Vv–öâ“òæVçG'’†çVÖ&W"’ó¢&WGW&à¢fÂf&–çG2ÒÆ—7Döb‚$ÖÆRòvVæFW&ÆW72"Â$fVÖÆR"Â%6†–ç’ÖÆRòvVæFW&ÆW72"Â%6†–ç’fVÖÆR"¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&Vv–öâæF—7Æ”æÖWÒ2G¶VçG'’æçVÖ&W'Ò(
"Gµ7v÷&E6†–VÆE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò„æF–öæÂ2G¶VçG'’ç7V6–W4–GÒ•ÆåÆâ"¢VæB‚%6VVã¢G¶VçG'’ç6VVçÕÆä6Vv‡C¢G¶VçG'’æ6Vv‡GÕÆä6Vv‡Bv–vçFÖ‚fÆs¢G¶VçG'’æ6Vv‡Dv–vçFÖ‡ÕÆâ"¢VæB‚%6V6öæF'’v–vçFÖ‚fÆr‡&r³ƒ#‚&—B“¢G¶VçG'’ç6V6öæF'”v–vçFÖ„fÆwÕÆåÆâ"¢f÷"‚†–æFW‚Â&—G2’–âVçG'’ç6VVä&—G2çv—F„–æFW‚‚’’VæB‚"G·f&–çG5¶–æFW…×Ò6VVâ&—B–æF–6W3¢G¶&—G2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR"×ÕÆâ"¢VæB‚%Æå&Vv—7FW&VBÆæwVvW3¢G¶VçG'’æÆæwVvW2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR&V6÷&FVB"×ÕÆâ"¢VæB‚$F—7Æ–VBf÷&Ò”B‡&r“¢G¶VçG'’æF—7Æ–VDf÷&Ô–GÕÆäF—7Æ–VBvVæFW"”B‡&r“¢G¶VçG'’æF—7Æ–VDvVæFW$–GÕÆâ"¢VæB‚$F—7Æ’v–vçFÖƒ¢G¶VçG'’æF—7Æ”v–vçFÖ‡ÕÆäF—7Æ’6†–ç“¢G¶VçG'’æF—7Æ•6†–ç—ÕÆä&GFÆVB6÷VçC¢G¶VçG'’æ&GFÆVD6÷VçGÕÆåÆâ"¢VæB‚%&rfÆw2‚³ƒ#“¢G¶VçG'’ç&tfÆw7ÕÆå&rW‡G&‚³ƒ#‚“¢G¶VçG'’ç&tW‡G&ÕÆå&rW‡G&‚³ƒ$2“¢G¶VçG'’ç&tW‡G&'ÕÆåÆâ"¢VæB‚%6VVâ&—G2&RæF—fR–æF–6W2Âæ÷BæÖVB÷"ÆVvÆ—G’Ö6†V6¶VBf÷&×2â&—Bc2—27V6–Âv–vçFÖ‚ôWFW&æÖ‚Ö&¶W#²W'6†–gRÇ6òW6W2&—Bc"f÷"—G26V6öæBv–vçFÖ‚f÷&Òâ"¢VæB‚$Æ7&VÖ–R–æF–6W2Væ6öFR7&VÒ÷7vVWB6öÖ&–æF–öç2âfÆw2æB6öçG&F–7F÷'’7FFW2&VÖ–â26fVC²æò&W—"÷"6ö×ÆWF–öâ÷&Wv&B–æfW&Væ6Râæ÷&ÖÂf÷&Ò–æFW‚6â&R&Vv—7FW&VBFò&Wf–WvVBæWr6÷“²7V6–Â–æF–6W2c"æBc2&VÖ–âÆö6¶VBâ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G·&Vv–öâæF—7Æ”æÖWÒ(
"Gµ7v÷&E6†–VÆE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢ç6WE÷6—F—fT'WGFöâ‚$FB&Vv—7G&F–öâ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6…7v÷&E6†–VÆEö¶VFW„VF—F÷"†&÷VæBÂ&Vv–öâÂçVÖ&W"¢Òç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6…7v÷&E6†–VÆEö¶VFW„VF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâÂ&Vv–öã¢7v÷&E6†–VÆDFW…&Vv–öâÂçVÖ&W#¢–çB’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6…7v÷&E6†–VÆEö¶VFW„7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æFW‚ç&Vv–öâ"Â&Vv–öâææÖR’çWDW‡G&‚'6Rç7v—F6‚æFW‚æçVÖ&W""ÂçVÖ&W"¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâ6†÷t&G7ö¶VFW‚†&÷VæC¢7v—F6…&VE6W76–öâÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBæ&G7ö¶VFW‚ó¢&WGW&à¢fÂÆ7EvRÒ†FW‚æVçG&–W2ç6—¦RÒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ%7F÷&VB7V6–W2&V6÷&G3¢G¶FW‚æ†V&D6÷VçGÒóC“2†V&Böb(
"G¶FW‚ç6VVä6÷VçGÒóC“26VVâ(
"G¶FW‚æ6Vv‡D6÷VçGÒóC“26Vv‡EÆâ"°¢%&Vv–öæÂFW‚ö'F–æVC¢G¶FW‚ç&Vv–öæÄFW‚çfÇVWÒ‡&rG¶FW‚ç&Vv–öæÄFW‚ç&ufÇVWÒ•Æâ"°¢$æF–öæÂFW‚ö'F–æVC¢G¶FW‚ææF–öæÄFW‚çfÇVWÒ‡&rG¶FW‚ææF–öæÄFW‚ç&ufÇVWÒ•ÆåÆâ"°¢$æF–öæÂ7V6–W2÷&FW"â6fVBfÆw2ö6÷VçG2Fòæ÷BW7F&Æ—6‚7F÷'’6ö×ÆWF–öâÂ&Wv&G2÷"&Vv–öæÂÖFW‚6ö×ÆWF–öââ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢f÷"†VçG'’–âFW‚æVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"2G¶VçG'’ç7V6–W4–GÒGµ7v÷&E6†–VÆE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò(
"G¶VçG'’ç7FFRæF—7Æ”æÖWÒ ¢6WDöä6Æ–6´Æ—7FVæW"²6†÷t&G7FW„VçG'’†&÷VæBÂVçG'’ç7V6–W4–B’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"ö¼:–FW‚G·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7ö¶VFW‚†&÷VæBÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7ö¶VFW‚†&÷VæBÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7FW„VçG'’†&÷VæC¢7v—F6…&VE6W76–öâÂ7V6–W3¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæ&G7ö¶VFWƒòæVçG'’‡7V6–W2’ó¢&WGW&à¢fÂf&–çG2ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â%6†–ç’ÖÆR"Â%6†–ç’fVÖÆR"¢gVâfÆr‡fÇVS¢&G7FW„fÆr“¢7G&–ærÒ"G·fÇVRçfÇVWÒ‡&rG·fÇVRç&ufÇVWÒ’ ¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"2G¶VçG'’ç7V6–W4–GÒGµ7v÷&E6†–VÆE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò(
"ö¼:–FW…ÆåÆâ"¢VæB‚%6fVB7FFS¢G¶VçG'’ç7FFRæF—7Æ”æÖWÒ‚G¶VçG'’ç7FFRæ÷&F–æÇÒ•Æä†V&Böc¢G¶VçG'’æ†V&GÕÆå6VVã¢G¶VçG'’ç6VVçÕÆä6Vv‡C¢G¶VçG'’æ6Vv‡GÕÆåÆâ"¢f÷"†–æFW‚–âf&–çG2æ–æF–6W2’VæB‚"G·f&–çG5¶–æFW…×Ò&Vv—7G&F–öã¢G¶fÆr†VçG'’ævVæFW$fÆw5¶–æFW…Ò—ÕÆâ"¢VæB‚%Æå&Vv—7FW&VBÆæwVvW3¢G¶VçG'’æÆæwVvW2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR&V6÷&FVB"×ÕÆå&rÆæwVvRfÆw3¢G¶VçG'’ç&tÆæwVvTfÆw7ÕÆåÆâ"¢–b†VçG'’æf÷&×2æ—4V×G’‚’’VæB‚$æòFVF–6FVBæF—fRf÷&ÒÖfÆr'&’f÷"F†—27V6–W2åÆâ"¢VÇ6Rf÷"†f÷&Ò–âVçG'’æf÷&×2’VæB‚%6fVBf÷&Ò”BG¶f÷&Òæf÷&Ô–GÓ¢æ÷&ÖÂG¶fÆr†f÷&Òææ÷&ÖÂ—Ò(
"6†–ç’G¶fÆr†f÷&Òç6†–ç’—ÕÆâ"¢–b†VçG'’ç7–æFæ—4æ÷DV×G’‚’’°¢VæB‚%Æå7–æF6fVBGFW&âT75Æâ"¢f÷"‡GFW&â–âVçG'’ç7–æF’VæB‚"G·GFW&âçf&–çGÓ¢6VVâT2G·GFW&âç6VVäV7Ò(
"6Vv‡BT2G·GFW&âæ6Vv‡DV7ÕÆâ"¢VæB‚$T72&R&rVç6–væVB6fVBfÇVW2Â–æ6ÇVF–ær¦W&ó²F†W’Fòæ÷BW7F&Æ—6‚&Vv—7G&F–öâåÆâ"¢Ð¢VæB‚%ÆäæF—fR&ööÆVç2&RG'VRöæÇ’v†VâF†RgVÆÂf÷W"Ö'—FR6fVBfÇVRWVÇ2âVæ¶æ÷vâ&rv÷&G2&R&W6W'fVBâ"¢VæB‚$vVæFW"öf÷&ÒöÆæwVvRfÆw2&VÖ–â–æFWVæFVçBöb6fVB7FFS²6öçG&F–7F–öç2&Ræ÷B&W—&VBâf÷&Ò”G2&RæF—fR–æF–6W2Âæ÷BÆVvÆ—G’fW&F–7G2âæ÷&ÖÂvVæFW"÷6†–ç’&Vv—7G&F–öâ6â&R6fVBFò&Wf–WvVBæWr6÷“²Væ¶æ÷vâ&r&Vv—7G&F–öâv÷&G2&VÖ–âÆö6¶VBâ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"Gµ7v÷&E6†–VÆE7V6–W4æÖW2ææÖR‡7V6–W2—Ò"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢ç6WE÷6—F—fT'WGFöâ‚$FB&Vv—7G&F–öâ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„&G7ö¶VFW„VF—F÷"†&÷VæBÂ7V6–W2¢Òç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„&G7ö¶VFW„VF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâÂ7V6–W3¢–çB’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6„&G7ö¶VFW„7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æFW‚ç7V6–W2"Â7V6–W2¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW5ö¶VFW‚†&÷VæC¢7v—F6…&VE6W76–öâÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBæÆVvVæG4&6WW5ö¶VFW‚ó¢&WGW&à¢fÂÆ7EvRÒ†FW‚æVçG&–W2ç6—¦RÒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ$†—7V’&W6V&6‚&V6÷&G2(
"&VBÖöæÇ•ÆâG¶FW‚æö'F–æVD6÷VçGÒó#C"ö'F–æVB(
"G¶FW‚çWFFVD6÷VçGÒó#C"WFFVEÆâ"°¢"G¶FW‚ç&W÷'FVD6÷VçGÒó#C"&W÷'FVB(
"G¶FW‚çW&fV7D6÷VçGÒó#C"6fVBW&fV7BfÆw5Æâ"°¢%6fVB&W6V&6‚ö–çG3¢G¶FW‚çF÷FÅ&W6V&6…ö–çG7ÕÆåWFFR6÷VçFW#¢G¶FW‚çWFFT6÷VçFW'Ò(
"&W÷'B6÷VçFW#¢G¶FW‚ç&W÷'D6÷VçFW'ÕÆâ"°¢$†—7V’6ö×ÆWFVBfÆs¢G¶FW‚æ†—7V”6ö×ÆWFVDfÆwÒ(
"W&fV7BfÆs¢G¶FW‚æ†—7V•W&fV7DfÆwÕÆå&rvÆö&ÂfÆw3¢G¶FW‚ç&tvÆö&ÄfÆw7ÕÆåÆâ"°¢%6fVB&W6V&6‚fÇVW2öæÇ’âF6²F‡&W6†öÆG2Â6VVâöf÷&Ò7FF—7F–72ÂÆö6ÂÖ&VÆ—7G2æB&Wv&G2&Ræ÷BWfÇVFVBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢f÷"†VçG'’–âFW‚æVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$†—7V’2G¶VçG'’æçVÖ&W'ÒG´ÆVvVæG4&6WW57V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò(
"ö'F–æVBG¶VçG'’æçVÔö'F–æVGÒ(
"&W÷'FVBG¶VçG'’ç&W÷'FVGÒ ¢6WDöä6Æ–6´Æ—7FVæW"²6†÷tÆVvVæG4&6WW4FW„VçG'’†&÷VæBÂVçG'’æçVÖ&W"’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"&W6V&6‚G·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW5ö¶VFW‚†&÷VæBÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW5ö¶VFW‚†&÷VæBÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW4FW„VçG'’†&÷VæC¢7v—F6…&VE6W76–öâÂçVÖ&W#¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæÆVvVæG4&6WW5ö¶VFWƒòæVçG'’†çVÖ&W"’ó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚$†—7V’2G¶VçG'’æçVÖ&W'Ò(
"G´ÆVvVæG4&6WW57V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò„æF–öæÂ2G¶VçG'’ç7V6–W4–GÒ•Æå&VBÖöæÇ’&W6V&6‚&V6÷&EÆåÆâ"¢VæB‚$ö'F–æVB6÷VçC¢G¶VçG'’æçVÔö'F–æVGÕÆäWfW"WFFVC¢G¶VçG'’çWFFVGÕÆäç’&W÷'C¢G¶VçG'’ç&W÷'FVGÕÆå6fVBW&fV7BfÆs¢G¶VçG'’çW&fV7GÕÆâ"¢VæB‚%F‚öb6öÆ—GVFR6ö×ÆWFRfÆs¢G¶VçG'’ç6öÆ—GVFT6ö×ÆWFWÕÆå&r&W6V&6‚&FS¢G¶VçG'’ç&u&W6V&6…&FWÕÆâ"¢VæB‚%WFFR6÷VçFW#¢G¶VçG'’çWFFT6÷VçFW'ÕÆäÆ7B&W÷'B6÷VçFW#¢G¶VçG'’æÆ7E&W÷'D6÷VçFW'ÕÆå&r&W÷'FVB&öw&W73¢G¶VçG'’ç&u&W÷'FVE&öw&W77ÕÆå&rfÆw3¢G¶VçG'’ç&tfÆw7ÕÆåÆâ"¢VæB‚%6VÆV7FVBF—7Æ’f÷&Ò”C¢G¶VçG'’ç6VÆV7FVDf÷&×ÕÆå6VÆV7FVBvVæFW"ÓfÆs¢G¶VçG'’ç6VÆV7FVDvVæFW#ÕÆå6VÆV7FVB6†–ç“¢G¶VçG'’ç6VÆV7FVE6†–ç—ÕÆå6VÆV7FVBÇ†¢G¶VçG'’ç6VÆV7FVDÇ†ÕÆåÆâ"¢VæB‚%6fVB6÷VçFW'5Æâ"¢f÷"†6÷VçFW"–âVçG'’æ6÷VçFW'2’VæB‚"G¶6÷VçFW"æÆ&VÇÓ¢G¶6÷VçFW"çfÇVWÕÆâ"¢VæB‚%Æå&r&FW2æB&öw&W72Fòæ÷BW7F&Æ—6‚&W6V&6‚ÆWfVÂ÷"6ö×ÆWFVBF6·2â&WV—&VBF6²'VÆW26â6F†RVffV7F—fR&FRâ"¢VæB‚$Ö÷fR÷G—R6Æ÷G2Fòæ÷B–FVçF–g’F6·3²6öÖR6÷VçFW'2Fòæ÷BÇ’FòWfW'’7V6–W2â6VÆV7FVBF—7Æ’fÆw2Fòæ÷BW7F&Æ—6‚6VVâö6Vv‡Bf÷&×2â"¢VæB‚%6fVB6öçG&F–7F–öç2æBVæ¶æ÷vâ&—G2&R&W6W'fVBâ–bF†—27V6–W2†2æ÷B&VVâö'F–æVBÂ4R6âFBöæÇ’F†RæF—fRö'F–æVBÖ6÷VçB&Vv—7G&F–öâFò&Wf–WvVBæWr6÷“²&W6V&6‚&öw&W72æBfÆw2&VÖ–âVæ6†ævVBâ"¢Ð¢fÂ6öçFVçBÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ$VF—BöæR&W6V&6‚6÷VçFW"(
"æWr6÷’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆVvVæG4&6WW5&W6V&6„6÷VçFW$VF—F÷"†&÷VæBÂçVÖ&W"¢Ð¢Ò¢–b‚VçG'’ç6öÆ—GVFT6ö×ÆWFR’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ$Ö&²F‚öb6öÆ—GVFR6ö×ÆWFR(
"æWr6÷’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆVvVæG4&6WW56öÆ—GVFTVF—F÷"†&÷VæBÂçVÖ&W"¢Ð¢Ò¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†6öçFVçB’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$†—7V’&W6V&6‚(
"G´ÆVvVæG4&6WW57V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‚VçG'’æö'F–æVB’'V–ÆFW"ç6WE÷6—F—fT'WGFöâ‚$FBö'F–æVB&Vv—7G&F–öâ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆVvVæG4&6WW5ö¶VFW„VF—F÷"†&÷VæBÂçVÖ&W"¢Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆVvVæG4&6WW56öÆ—GVFTVF—F÷"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢çVÖ&W#¢–ç@¢’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆVvVæG4&6WW56öÆ—GVFT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æFW‚æçVÖ&W""ÂçVÖ&W"¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð ¢&—fFRgVâÆVæ6„ÆVvVæG4&6WW5&W6V&6„6÷VçFW$VF—F÷"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢çVÖ&W#¢–ç@¢’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆVvVæG4&6WW5&W6V&6„6÷VçFW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æFW‚æçVÖ&W""ÂçVÖ&W"¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð ¢&—fFRgVâÆVæ6„ÆVvVæG4&6WW5ö¶VFW„VF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâÂçVÖ&W#¢–çB’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6„ÆVvVæG4&6WW5ö¶VFW„7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æFW‚æçVÖ&W""ÂçVÖ&W"¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWDFW…&Vv–öç2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBç66&ÆWEf–öÆWEö¶VFW‚ó¢&WGW&à¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"ö¼:–FW‚"¢ç6WD—FV×2†FW‚ç&Vv–öç2æÖ²"G¶—Bç&Vv–öâæF—7Æ”æÖWÒ(
"G¶—Bç6VVä6÷VçGÒòG¶—BæVçG&–W2ç6—¦WÒ6VVâ(
"G¶—Bæ6Vv‡D6÷VçGÒ6Vv‡B"ÒçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWEö¶VFW‚†&÷VæBÂFW‚ç&Vv–öç5¶–æFW…Òç&Vv–öâÂ¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWEö¶VFW‚†&÷VæC¢7v—F6…&VE6W76–öâÂ&Vv–öã¢66&ÆWEf–öÆWDFW…&Vv–öâÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBç66&ÆWEf–öÆWEö¶VFW‚ó¢&WGW&à¢fÂF&ÆRÒFW‚ç&Vv–öâ‡&Vv–öâ’ó¢&WGW&à¢fÂÆ7EvRÒ‡F&ÆRæVçG&–W2ç6—¦RÒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶FW‚æÆ–÷WBæF—7Æ”æÖWÒ(
"G·&Vv–öâæF—7Æ”æÖWÕÆâG·F&ÆRæ¶æ÷vä6÷VçGÒòG·F&ÆRæVçG&–W2ç6—¦WÒ¶æ÷vâ(
"G·F&ÆRç6VVä6÷VçGÒ6VVâ(
"G·F&ÆRæ6Vv‡D6÷VçGÒ6Vv‡EÆåÆâ"°¢%6fVB&Vv–öæÂ&V6÷&G2öæÇ’âÆ—7F–ærâ&VFöW2æ÷BW7F&Æ—6‚DÄ266W72Â7F÷'’6ö×ÆWF–öâÂ&Wv&G2÷"6ö×ÆWFVBö¼:–FW‚â ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢f÷"†VçG'’–âF&ÆRæVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"2G¶VçG'’æçVÖ&W'ÒGµ66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò(
"¶æ÷vâG¶VçG'’æ¶æ÷vçÒ(
"6VVâG¶VçG'’ç6VVçÒ(
"6Vv‡BG¶VçG'’æ6Vv‡GÒ ¢6WDöä6Æ–6´Æ—7FVæW"²6†÷u66&ÆWEf–öÆWDFW„VçG'’†&÷VæBÂ&Vv–öâÂVçG'’æçVÖ&W"’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G·&Vv–öâæF—7Æ”æÖWÒö¼:–FW‚G·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚%&Vv–öç2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWDFW…&Vv–öç2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWEö¶VFW‚†&÷VæBÂ&Vv–öâÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWEö¶VFW‚†&÷VæBÂ&Vv–öâÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWDFW„VçG'’†&÷VæC¢7v—F6…&VE6W76–öâÂ&Vv–öã¢66&ÆWEf–öÆWDFW…&Vv–öâÂçVÖ&W#¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBç66&ÆWEf–öÆWEö¶VFW‚ó¢&WGW&à¢fÂVçG'’ÒFW‚ç&Vv–öâ‡&Vv–öâ“òæVçG'’†çVÖ&W"’ó¢&WGW&à¢gVâf÷&×2‡v÷&C¢Æöær“¢7G&–ærÒƒâã3’æf–ÇFW"²v÷&BæBƒÂ6†Â—B’ÒÂÒæ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR"Ð¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·&Vv–öâæF—7Æ”æÖWÒ2G¶VçG'’æçVÖ&W'Ò(
"Gµ66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò„æF–öæÂ2G¶VçG'’ç7V6–W4–GÒ•Æå&VBÖöæÇ’(
"G¶FW‚æÆ–÷WBæF—7Æ”æÖWÕÆåÆâ"¢VæB‚$¶æ÷vã¢G¶VçG'’æ¶æ÷vçÕÆå6VVã¢G¶VçG'’ç6VVçÕÆä6Vv‡C¢G¶VçG'’æ6Vv‡GÕÆå6†–ç’6VVã¢G¶VçG'’ç6†–ç•6VVçÕÆäæWrF—7Æ“¢G¶VçG'’ææWtF—7Æ—ÕÆâ"¢VçG'’ç&u7FFSòæÆWB²VæB‚%&r7FFS¢F—BƒæöæRÂ¶æ÷vâÂ"6VVâÂ26Vv‡C²÷F†W"fÇVW2&WF–æVB•Æâ"’Ð¢VæB‚%Æå6VVâf÷&Ò”G3¢G¶f÷&×2†VçG'’ç6VVäf÷&×2—Ò‡&rG¶VçG'’ç6VVäf÷&×7Ò•Æâ"¢VçG'’æö'F–æVDf÷&×3òæÆWB²VæB‚$ö'F–æVBf÷&Ò”G3¢G¶f÷&×2†—B—Ò‡&rF—B•Æâ"’Ð¢VçG'’æ†V&Df÷&×3òæÆWB²VæB‚$†V&Bf÷&Ò”G3¢G¶f÷&×2†—B—Ò‡&rF—B•Æâ"’Ð¢VçG'’æ6†V6¶VDf÷&×3òæÆWB²VæB‚$6†V6¶VBf÷&Ò”G3¢G¶f÷&×2†—B—Ò‡&rF—B•Æâ"’Ð¢fÂvVæFW'2ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’æf–ÇFW$–æFW†VB²&—BÂòÓâVçG'’ævVæFW$fÆw2æBƒ6†Â&—B’ÒÐ¢VæB‚%Æå6VVâvVæFW'3¢G¶vVæFW'2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR"×Ò‡&rG¶VçG'’ævVæFW$fÆw7Ò•Æâ"¢VæB‚%&Vv—7FW&VBÆæwVvW3¢G¶VçG'’æÆæwVvW2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR"×Ò‡&rG¶VçG'’æÆæwVvTfÆw7Ò•Æå&r6†–ç’fÆw3¢G¶VçG'’ç&u6†–ç”fÆw7ÕÆâ"¢VçG'’ç&tæWtfÆsòæÆWB²VæB‚%&ræWrfÆs¢F—EÆâ"’Ó²VçG'’ç&tvVæFW$F–ffW&Væ6SòæÆWB²VæB‚%&rF—7Æ’vVæFW"ÖF–ffW&Væ6RfÆs¢F—EÆâ"’Ð¢VæB‚%Æå6fVBF—7Æ’6VÆV7F–öç5Æâ"¢f÷"†F—7Æ’–âVçG'’æF—7Æ—2’VæB‚"G¶F—7Æ’ç&Vv–öâæF—7Æ”æÖWÓ¢f÷&Ò”BG¶F—7Æ’æf÷&Ô–GÒ(
"vVæFW"fÇVRG¶F—7Æ’ç&tvVæFW'Ò(
"6†–ç’fÇVRG¶F—7Æ’ç&u6†–ç—ÕÆâ"¢VæB‚%Æäf÷&Ò&—G2æBF—7Æ’6VÆV7F–öç2&RæF—fR”G2Âæ÷BÆVvÆ—G’fW&F–7G2âVæ¶æ÷vâ&—G2Â&rfÇVW2æB6öçG&F–7F÷'’fÆw2&VÖ–âVæ6†ævVBâ"¢VæB‚$÷&–v–æÂ6fW2†fRöæR7FFRæBf÷&Ò×6VVâfÆw3²DÄ2ÖW&6fW2†fR–æFWVæFVçBö'F–æVB÷6VVâö†V&Bö6†V6¶VBfÆw2â4R6âFBöæR&Wf–WvVBæF—fRf÷&Ò&Vv—7G&F–öâFòæWr6÷’v†–ÆR&W6W'f–ærF—7Æ’6VÆV7F–öç2æBVæ¶æ÷vâ&—G2â"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G·&Vv–öâæF—7Æ”æÖWÒ(
"Gµ66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢ç6WE÷6—F—fT'WGFöâ‚$FB&Vv—7G&F–öâ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6…66&ÆWEf–öÆWEö¶VFW„VF—F÷"†&÷VæBÂ&Vv–öâÂçVÖ&W"¢Òç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6…66&ÆWEf–öÆWEö¶VFW„VF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâÀ¢&Vv–öã¢66&ÆWEf–öÆWDFW…&Vv–öâÂçVÖ&W#¢–çB’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6…66&ÆWEf–öÆWEö¶VFW„7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æFW‚ç&Vv–öâ"Â&Vv–öâææÖR¢çWDW‡G&‚'6Rç7v—F6‚æFW‚æçVÖ&W""ÂçVÖ&W"¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦FW…6V7F–öç2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBæÆVvVæG5¦ö¶VFW‚ó¢&WGW&à¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"ö¼:–FW‚(
"&VBÖöæÇ’"¢ç6WD—FV×2†FW‚ç6V7F–öç2æÖ²"G¶—Bç6V7F–öâæF—7Æ”æÖWÒ(
"G¶—Bç6VVä6÷VçGÒòG¶—BæVçG&–W2ç6—¦WÒ6VVâ(
"G¶—Bæ6Vv‡D6÷VçGÒ6Vv‡B"ÒçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦ö¶VFW‚†&÷VæBÂFW‚ç6V7F–öç5¶–æFW…Òç6V7F–öâÂ¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦ö¶VFW‚†&÷VæC¢7v—F6…&VE6W76–öâÂ6V7F–öã¢ÆVvVæG5¦FW…6V7F–öâÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂFW‚Ò&÷VæBæÆVvVæG5¦ö¶VFW‚ó¢&WGW&à¢fÂF&ÆRÒFW‚ç6V7F–öâ‡6V7F–öâ’ó¢&WGW&à¢fÂÆ7EvRÒ‡F&ÆRæVçG&–W2ç6—¦RÒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G·6V7F–öâæF—7Æ”æÖWÒ(
"G¶FW‚ç&Wf—6–öãòæF—7Æ”æÖRó¢%&Wf—6–öâVæ¶æ÷vâ'ÕÆâG·F&ÆRç6VVä6÷VçGÒòG·F&ÆRæVçG&–W2ç6—¦WÒ6VVâ(
"G·F&ÆRæ6Vv‡D6÷VçGÒ6Vv‡EÆåÆâ"°¢$æF—fRö¼:–FW‚çVÖ&W&–ærâ‡—W'76R&V6÷&G2&WV—&R¶æ÷vâDÄ2&Wf—6–öâÖ&¶W"â6fVBfÆw2Fòæ÷BW7F&Æ—6‚7F÷'’ôDÄ2VæÆö6·2Â&Wv&G2÷"6ö×ÆWF–öââ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢f÷"†VçG'’–âF&ÆRæVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%ö¼:–FW‚2G¶VçG'’æçVÖ&W'ÒGµ66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò(
"6VVâG¶VçG'’ç6VVçÒ(
"6Vv‡BG¶VçG'’æ6Vv‡GÒ ¢6WDöä6Æ–6´Æ—7FVæW"²6†÷tÆVvVæG5¦FW„VçG'’†&÷VæBÂ6V7F–öâÂVçG'’æçVÖ&W"’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G·6V7F–öâæF—7Æ”æÖWÒö¼:–FW‚G·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚%6V7F–öç2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦FW…6V7F–öç2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦ö¶VFW‚†&÷VæBÂ6V7F–öâÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦ö¶VFW‚†&÷VæBÂ6V7F–öâÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦FW„VçG'’†&÷VæC¢7v—F6…&VE6W76–öâÂ6V7F–öã¢ÆVvVæG5¦FW…6V7F–öâÂçVÖ&W#¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæÆVvVæG5¦ö¶VFWƒòç6V7F–öâ‡6V7F–öâ“òæVçG'’†çVÖ&W"’ó¢&WGW&à¢gVâf÷&×2‡v÷&C¢Æöær“¢7G&–ærÒƒâã3’æf–ÇFW"²v÷&BæBƒÂ6†Â—B’ÒÂÒæ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR"Ð¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚%ö¼:–FW‚2G¶VçG'’æçVÖ&W'Ò(
"Gµ66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò„æF–öæÂ2G¶VçG'’ç7V6–W4–GÒ•ÆâG·6V7F–öâæF—7Æ”æÖWÕÆåÆâ"¢VæB‚%6VVã¢G¶VçG'’ç6VVçÕÆä6Vv‡C¢G¶VçG'’æ6Vv‡GÕÆå6†–ç’fÆw2&V6÷&FVC¢G¶VçG'’ç6†–ç•&V6÷&FVGÕÆäÇ†6VVã¢G¶VçG'’æÇ†6VVçÒ‡&rG¶VçG'’ç&tÇ†fÆwÒ•ÆäæWrF—7Æ“¢G¶VçG'’ææWtF—7Æ—Ò‡&rG¶VçG'’ç&tæWtfÆwÒ•ÆåÆâ"¢VæB‚$6Vv‡Bf÷&Ò”G3¢G¶f÷&×2†VçG'’æ6Vv‡Df÷&×2—Ò‡&rG¶VçG'’æ6Vv‡Df÷&×7Ò•Æå6VVâf÷&Ò”G3¢G¶f÷&×2†VçG'’ç6VVäf÷&×2—Ò‡&rG¶VçG'’ç6VVäf÷&×7Ò•Æå6†–ç’f÷&Ò”G3¢G¶f÷&×2†VçG'’ç6†–ç”f÷&×2—Ò‡&rG¶VçG'’ç6†–ç”f÷&×7Ò•Æâ"¢fÂvVæFW'2ÒÆ—7Döb‚$ÖÆR"Â$fVÖÆR"Â$vVæFW&ÆW72"’æf–ÇFW$–æFW†VB²&—BÂòÓâVçG'’ævVæFW$fÆw2æBƒ6†Â&—B’ÒÐ¢fÂÖVv&—G2Òƒâãr’æf–ÇFW"²VçG'’æÖVvfÆw2æBƒ6†Â—B’ÒÐ¢VæB‚%Æå6VVâvVæFW'3¢G¶vVæFW'2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR"×Ò‡&rG¶VçG'’ævVæFW$fÆw7Ò•ÆäÖVvfÆr–æF–6W3¢G¶ÖVv&—G2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR"×Ò‡&rG¶VçG'’æÖVvfÆw7Ò•Æâ"¢VæB‚%&Vv—7FW&VBÆæwVvW3¢G¶VçG'’æÆæwVvW2æ¦ö–åFõ7G&–ær‚’æ–d&Ææ²²$æöæR"×Ò‡&rG¶VçG'’æÆæwVvTfÆw7Ò•ÆåÆâ"¢VæB‚%6fVBF—7Æ“¢f÷&Ò”BG¶VçG'’æF—7Æ”f÷&×Ò(
"G¶VçG'’æF—7Æ”vVæFW'Ò‡&rvVæFW"G¶VçG'’ç&tF—7Æ”vVæFW'Ò’(
"6†–ç’G¶VçG'’æF—7Æ•6†–ç—Ò‡&rG¶VçG'’ç&tF—7Æ•6†–ç—Ò•ÆåÆâ"¢VæB‚$6Vv‡BÂ6VVâÂ6†–ç’ÂÖVvÂÇ†æBF—7Æ’fÆw2&VÖ–â–æFWVæFVçBâÖVv&—G2FWVæBöâ7V6–W2÷&Wf—6–öâæB&R6†÷vâ2æF—fR–æF–6W2â"¢VæB‚%Væ¶æ÷vâ&—G2æB6öçG&F–7F÷'’fÇVW2&R&W6W'fVBâf÷&Ò”G2&Ræ÷BÆVvÆ—G’fW&F–7G2â4R6âFBöæR&Wf–WvVBæF—fRf÷&Ò&Vv—7G&F–öâFòæWr6÷’v†–ÆR&W6W'f–ærÖVvÂÇ†ÂF—7Æ’æBVæ¶æ÷vâf–VÆG2â"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%¢Ôö¼:–FW‚(
"Gµ66&ÆWEf–öÆWE7V6–W4æÖW2ææÖR†VçG'’ç7V6–W4–B—Ò"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢ç6WE÷6—F—fT'WGFöâ‚$FB&Vv—7G&F–öâ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆVvVæG5¦ö¶VFW„VF—F÷"†&÷VæBÂ6V7F–öâÂçVÖ&W"¢Òç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆVvVæG5¦ö¶VFW„VF—F÷"†&÷VæC¢7v—F6…&VE6W76–öâÀ¢6V7F–öã¢ÆVvVæG5¦FW…6V7F–öâÂçVÖ&W#¢–çB’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’„–çFVçB‡F†—2Â7v—F6„ÆVvVæG5¦ö¶VFW„7F—f—G“£¦6Æ72æ¦f’ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æFW‚ç6V7F–öâ"Â6V7F–öâææÖR¢çWDW‡G&‚'6Rç7v—F6‚æFW‚æçVÖ&W""ÂçVÖ&W"¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ’¢Ð ¢&—fFRgVâ6†÷tÆWG4vô–çfVçF÷'•ö6¶WG2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæÆWG4vô–çfVçF÷'’ó¢&WGW&à¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"–çfVçF÷'’(
"&VBÖöæÇ’"¢ç6WD—FV×2†&rçö6¶WG2æÖ²"G¶—Bçö6¶WBæF—7Æ”æÖWÒ(
"G¶—Bç&V6÷&FVD6÷VçGÒòG¶—Bç6Æ÷G2ç6—¦WÒ&V6÷&FVB6Æ÷G2"ÒçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆWG4vô–çfVçF÷'•ö6¶WB†&÷VæBÂ&rçö6¶WG5¶–æFW…Òçö6¶WBÂ¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vô–çfVçF÷'•ö6¶WB†&÷VæC¢7v—F6…&VE6W76–öâÂö6¶WC¢ÆWG4vô–çfVçF÷'•ö6¶WBÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæÆWG4vô–çfVçF÷'’ó¢&WGW&à¢fÂF&ÆRÒ&rçö6¶WB‡ö6¶WB¢fÂVçG&–W2ÒF&ÆRç&V6÷&FVE6Æ÷G0¢fÂÆ7EvRÒ‚†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G·F&ÆRç&V6÷&FVD6÷VçGÒòG·F&ÆRç6Æ÷G2ç6—¦WÒ&V6÷&FVB6Æ÷G2(
"G·F&ÆRç6Æ÷G2ç6—¦RÒF&ÆRç&V6÷&FVD6÷VçGÒ¦W&ò6Æ÷G5ÆåÆâ"°¢$æF—fR6Æ÷B÷&FW"Â–æ6ÇVF–ærGWÆ–6FR—FV×2æBVçW7VÂæöç¦W&ò&V6÷&G2â6÷VçG2æBfÆw2&R6fVBfÇVW2â6FÆöwVVB&V6÷&G26â&R6†ævVBF‡&÷Vv‚âW‡Æ–6—FÇ’&Wf–WvVBæWr6÷“²VçW7VÂ&V6÷&G2&VÖ–âÆö6¶VBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂV×G•6Æ÷BÒF&ÆRç6Æ÷G2æf—'7D÷$çVÆÂ²—Bæ—4V×G’Ð¢–b†V×G•6Æ÷BÒçVÆÂ’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ.ûÈ²FB—FVÒ(
"6fRæWr6÷’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆWG4vô—FVÔVF—F÷"†&÷VæBÂö6¶WBÂV×G•6Æ÷Bç6Æ÷BÂG'VR¢Ð¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ%F†—2ö6¶WB6öçF–ç2æòæöç¦W&ò6fVB&V6÷&G2â#²FW‡E6—¦RÒfbÒ¢f÷"†VçG'’–âVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6Æ÷BG¶VçG'’ç6Æ÷GÒ(
"G¶VçG'’æ—FVÔæÖWÒ9rG¶VçG'’æ6÷VçGÒ"²†–b†VçG'’æ—4æWr’"(
"æWr"VÇ6R""’°¢†–b†VçG'’çVæ¶æ÷vä&—G2ÒÂÇÂVçG'’æW†6VVG5&VfW&Væ6TÖ‚ÇÂVçG'’æÆ—7FVD–åö6¶WBÇÂVçG'’æ6÷VçBÓÒ’"(
"6†V6²6fVBFWF–Ç2"VÇ6R""¢6WDöä6Æ–6´Æ—7FVæW"²6†÷tÆWG4vô–çfVçF÷'•6Æ÷B†&÷VæBÂö6¶WBÂVçG'’ç6Æ÷B’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G·ö6¶WBæF—7Æ”æÖWÒG·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚%ö6¶WG2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆWG4vô–çfVçF÷'•ö6¶WG2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆWG4vô–çfVçF÷'•ö6¶WB†&÷VæBÂö6¶WBÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆWG4vô–çfVçF÷'•ö6¶WB†&÷VæBÂö6¶WBÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆWG4vô–çfVçF÷'•6Æ÷B†&÷VæC¢7v—F6…&VE6W76–öâÂö6¶WC¢ÆWG4vô–çfVçF÷'•ö6¶WBÂ6Æ÷C¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæÆWG4vô–çfVçF÷'“òçö6¶WB‡ö6¶WB“òç6Æ÷B‡6Æ÷B’ó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·ö6¶WBæF—7Æ”æÖWÒ(
"æF—fR6Æ÷BG¶VçG'’ç6Æ÷GÕÆâG¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ•ÆåÆâ"¢VæB‚%6fVB6÷VçC¢G¶VçG'’æ6÷VçGÕÆäæWrfÆs¢G¶VçG'’æ—4æWwÕÆäÆ—7FVB–âF†—2ö6¶WBw2æF—fR6FÆöwVS¢G¶VçG'’æÆ—7FVD–åö6¶WGÕÆâ"¢VæB‚%&VfW&Væ6R7F6²Ö†–×VÓ¢G¶VçG'’ç&VfW&Væ6TÖ„6÷VçCòçFõ7G&–ær‚’ó¢%Væ¶æ÷vâf÷"F†—2&V6÷&B'ÕÆäW†6VVG2&VfW&Væ6RÖ†–×VÓ¢G¶VçG'’æW†6VVG5&VfW&Væ6TÖ‡ÕÆåÆâ"¢VæB‚%&r6¶VBfÇVS¢G¶VçG'’ç&ufÇVWÕÆå&rRÖ&—B—FVÒf–VÆC¢G¶VçG'’ç&t—FVÔf–VÆGÕÆå&rRÖ&—B6÷VçBf–VÆC¢G¶VçG'’ç&t6÷VçDf–VÆGÕÆä÷F†W"6fVB&—G3¢G¶VçG'’çVæ¶æ÷vä&—G7ÕÆåÆâ"¢VæB‚$—FVÒö6÷VçBF—7Æ’föÆÆ÷w2F†RæF—fRvWGFW"Ö6·2âVçW7VÂ”G2Â¦W&ò6÷VçG2ÂGWÆ–6FR6Æ÷G2æBVæ¶æ÷vâ&—G2&R&WF–æVBâ"¢VæB‚%&VfW&Væ6R6FÆöwVW2æBÖ†–Ö&RFW67&—F—fRÂæ÷BÆVvÆ—G’fW&F–7Bâ&W6W'fVB&—G2&Ræ÷BÆ&VÆÆVBff÷&—FW2â"¢VæB†–b†VçG'’æÆ—7FVD–åö6¶WBbbVçG'’ç&VfW&Væ6TÖ„6÷VçBÒçVÆÂ’%W6RVF—B—FVÒFò&Wf–WrVçF—G’6†ævR÷"&VÖ÷fÂ6fVBFòæWr6÷’â"VÇ6R%F†—2VçW7VÂ&V6÷&B&VÖ–ç2Æö6¶VBâ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G¶VçG'’æ—FVÔæÖWÒ"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b†VçG'’æÆ—7FVD–åö6¶WBbbVçG'’ç&VfW&Væ6TÖ„6÷VçBÒçVÆÂ’°¢'V–ÆFW"ç6WE÷6—F—fT'WGFöâ‚$VF—B—FVÒ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆWG4vô—FVÔVF—F÷"†&÷VæBÂö6¶WBÂVçG'’ç6Æ÷BÂfÇ6R¢Ð¢Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆWG4vô—FVÔVF—F÷"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢ö6¶WC¢ÆWG4vô–çfVçF÷'•ö6¶WBÀ¢6Æ÷C¢–çBÀ¢FC¢&ööÆVà¢’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆWG4vô—FVÔ7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒçö6¶WB"Âö6¶WBææÖR¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒç6Æ÷B"Â6Æ÷B¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæFB"ÂFB¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆD–çfVçF÷'•ö6¶WG2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBç7v÷&E6†–VÆD–çfVçF÷'’ó¢&WGW&à¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"—FV×2"¢ç6WD—FV×2†&rçö6¶WG2æÖ²"G¶—Bçö6¶WBæF—7Æ”æÖWÒ(
"G¶—Bç&V6÷&FVD6÷VçGÒòG¶—Bç6Æ÷G2ç6—¦WÒ&V6÷&FVB6Æ÷G2"ÒçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷u7v÷&E6†–VÆD–çfVçF÷'•ö6¶WB†&÷VæBÂ&rçö6¶WG5¶–æFW…Òçö6¶WBÂ¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆD–çfVçF÷'•ö6¶WB†&÷VæC¢7v—F6…&VE6W76–öâÂö6¶WC¢7v÷&E6†–VÆD–çfVçF÷'•ö6¶WBÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBç7v÷&E6†–VÆD–çfVçF÷'’ó¢&WGW&à¢fÂF&ÆRÒ&rçö6¶WB‡ö6¶WB¢fÂVçG&–W2ÒF&ÆRç&V6÷&FVE6Æ÷G0¢fÂÆ7EvRÒ‚†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G·F&ÆRç&V6÷&FVD6÷VçGÒòG·F&ÆRç6Æ÷G2ç6—¦WÒ&V6÷&FVB6Æ÷G2(
"G·F&ÆRç6Æ÷G2ç6—¦RÒF&ÆRç&V6÷&FVD6÷VçGÒ¦W&ò6Æ÷G5ÆåÆâ"°¢$æF—fR6Æ÷B÷&FW"Â–æ6ÇVF–ærGWÆ–6FR—FV×2æBVçW7VÂæöç¦W&ò&V6÷&G2â6÷VçG2æBfÆw2&R6fVBfÇVW2â6FÆöwVVB&V6÷&G26â&R6†ævVBF‡&÷Vv‚âW‡Æ–6—FÇ’&Wf–WvVBæWr6÷“²VçW7VÂ&V6÷&G2&VÖ–âÆö6¶VBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂV×G•6Æ÷BÒF&ÆRç6Æ÷G2æf—'7D÷$çVÆÂ²—Bæ—4V×G’Ð¢–b†V×G•6Æ÷BÒçVÆÂ’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ.ûÈ²FB—FVÒ(
"6fRæWr6÷’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6…7v÷&E6†–VÆD—FVÔVF—F÷"†&÷VæBÂö6¶WBÂV×G•6Æ÷Bç6Æ÷BÂG'VR¢Ð¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ%F†—2ö6¶WB6öçF–ç2æòæöç¦W&ò6fVB&V6÷&G2â#²FW‡E6—¦RÒfbÒ¢f÷"†VçG'’–âVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6Æ÷BG¶VçG'’ç6Æ÷GÒ(
"G¶VçG'’æ—FVÔæÖWÒ9rG¶VçG'’æ6÷VçGÒ"²†–b†VçG'’æ—4æWr’"(
"æWr"VÇ6R""’²†–b†VçG'’æ—4ff÷&—FR’"(
"ff÷&—FR"VÇ6R""’°¢†–b†VçG'’çVæ¶æ÷vä&—G2ÒÂÇÂVçG'’æW†6VVG5&VfW&Væ6TÖ‚ÇÂVçG'’æÆ—7FVD–åö6¶WBÇÂVçG'’æ6÷VçBÓÒ’"(
"6†V6²6fVBFWF–Ç2"VÇ6R""¢6WDöä6Æ–6´Æ—7FVæW"²6†÷u7v÷&E6†–VÆD–çfVçF÷'•6Æ÷B†&÷VæBÂö6¶WBÂVçG'’ç6Æ÷B’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G·ö6¶WBæF—7Æ”æÖWÒG·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚%ö6¶WG2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u7v÷&E6†–VÆD–çfVçF÷'•ö6¶WG2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u7v÷&E6†–VÆD–çfVçF÷'•ö6¶WB†&÷VæBÂö6¶WBÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u7v÷&E6†–VÆD–çfVçF÷'•ö6¶WB†&÷VæBÂö6¶WBÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u7v÷&E6†–VÆD–çfVçF÷'•6Æ÷B†&÷VæC¢7v—F6…&VE6W76–öâÂö6¶WC¢7v÷&E6†–VÆD–çfVçF÷'•ö6¶WBÂ6Æ÷C¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBç7v÷&E6†–VÆD–çfVçF÷'“òçö6¶WB‡ö6¶WB“òç6Æ÷B‡6Æ÷B’ó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G·ö6¶WBæF—7Æ”æÖWÒ(
"æF—fR6Æ÷BG¶VçG'’ç6Æ÷GÕÆâG¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ•ÆåÆâ"¢VæB‚%6fVB6÷VçC¢G¶VçG'’æ6÷VçGÕÆäæWrfÆs¢G¶VçG'’æ—4æWwÕÆäff÷&—FRfÆs¢G¶VçG'’æ—4ff÷&—FWÕÆäÆ—7FVB–âF†—2ö6¶WBw2æF—fR6FÆöwVS¢G¶VçG'’æÆ—7FVD–åö6¶WGÕÆâ"¢VæB‚%&VfW&Væ6R7F6²Ö†–×VÓ¢G¶VçG'’ç&VfW&Væ6TÖ„6÷VçCòçFõ7G&–ær‚’ó¢%Væ¶æ÷vâf÷"F†—2&V6÷&B'ÕÆäW†6VVG2&VfW&Væ6RÖ†–×VÓ¢G¶VçG'’æW†6VVG5&VfW&Væ6TÖ‡ÕÆåÆâ"¢VæB‚%&r6¶VBfÇVS¢G¶VçG'’ç&ufÇVWÕÆå&rRÖ&—B—FVÒf–VÆC¢G¶VçG'’ç&t—FVÔf–VÆGÕÆå&rRÖ&—B6÷VçBf–VÆC¢G¶VçG'’ç&t6÷VçDf–VÆGÕÆä÷F†W"6fVB&—G3¢G¶VçG'’çVæ¶æ÷vä&—G7ÕÆåÆâ"¢VæB‚$—FVÒö6÷VçBF—7Æ’föÆÆ÷w2F†RæF—fRvWGFW"Ö6·2âVçW7VÂ”G2Â¦W&ò6÷VçG2ÂGWÆ–6FR6Æ÷G2æBVæ¶æ÷vâ&—G2&R&WF–æVBâ"¢VæB‚%&VfW&Væ6R6FÆöwVW2æBÖ†–Ö&RFW67&—F—fRÂæ÷BÆVvÆ—G’fW&F–7Bâff÷&—FR—26fVB&—B3æB&VÖ–ç2&W6W'fVB'’VçF—G’VF—G2â"¢VæB†–b†VçG'’æÆ—7FVD–åö6¶WBbbVçG'’ç&VfW&Væ6TÖ„6÷VçBÒçVÆÂ’%W6RVF—B—FVÒFò&Wf–WrVçF—G’6†ævR÷"&VÖ÷fÂ6fVBFòæWr6÷’â"VÇ6R%F†—2VçW7VÂ&V6÷&B&VÖ–ç2Æö6¶VBâ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G¶VçG'’æ—FVÔæÖWÒ"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b†VçG'’æÆ—7FVD–åö6¶WBbbVçG'’ç&VfW&Væ6TÖ„6÷VçBÒçVÆÂ’°¢'V–ÆFW"ç6WE÷6—F—fT'WGFöâ‚$VF—B—FVÒ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6…7v÷&E6†–VÆD—FVÔVF—F÷"†&÷VæBÂö6¶WBÂVçG'’ç6Æ÷BÂfÇ6R¢Ð¢Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6…7v÷&E6†–VÆD—FVÔVF—F÷"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢ö6¶WC¢7v÷&E6†–VÆD–çfVçF÷'•ö6¶WBÀ¢6Æ÷C¢–çBÀ¢FC¢&ööÆVà¢’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6…7v÷&E6†–VÆD—FVÔ7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒçö6¶WB"Âö6¶WBææÖR¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒç6Æ÷B"Â6Æ÷B¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæFB"ÂFB¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð ¢&—fFRgVâ6†÷t&G7–çfVçF÷'•ö6¶WG2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæ&G7–çfVçF÷'’ó¢&WGW&à¢fÂÆ&VÇ2Ò&rçö6¶WG2æÖ²"G¶—Bçö6¶WBæF—7Æ”æÖWÒ(
"G¶—Bç&V6÷&FVD6÷VçGÒ&V6÷&FVB—FV×2"Ò°¢$÷F†W"6fVB&V6÷&G2(
"G¶&rçVæÆ—7FVE&V6÷&G2ç6—¦WÒVæÆ—7FVB–æF–6W2 ¢fÂVæFW&w&÷VæD–æFW‚Ò–b†&÷VæBæ&G7VæFW&w&÷VæBÒçVÆÂ’Æ&VÇ2ç6—¦RVÇ6RÓ¢fÂöff–ä–æFW‚Ò–b†&÷VæBæ&G7öff–ç2ÒçVÆÂ’Æ&VÇ2ç6—¦R²†–b‡VæFW&w&÷VæD–æFW‚ãÒ’VÇ6R’VÇ6RÓ¢fÂ6VÄ–æFW‚Ò–b†&÷VæBæ&G76VÅ7F–6¶W'2ÒçVÆÂ’Æ&VÇ2ç6—¦R²†–b‡VæFW&w&÷VæD–æFW‚ãÒ’VÇ6R’²†–b‡öff–ä–æFW‚ãÒ’VÇ6R’VÇ6RÓ¢fÂ67VÆT–æFW‚Ò–b†&÷VæBæ&G7&ÆÄ67VÆW2ÒçVÆÂ’Æ&VÇ2ç6—¦R²†–b‡VæFW&w&÷VæD–æFW‚ãÒ’VÇ6R’²†–b‡öff–ä–æFW‚ãÒ’VÇ6R’²†–b‡6VÄ–æFW‚ãÒ’VÇ6R’VÇ6RÓ¢fÂ6öçFW7D–æFW‚Ò–b†&÷VæBæ&G76öçFW7BÒçVÆÂ’Æ&VÇ2ç6—¦R²†–b‡VæFW&w&÷VæD–æFW‚ãÒ’VÇ6R’²†–b‡öff–ä–æFW‚ãÒ’VÇ6R’²†–b‡6VÄ–æFW‚ãÒ’VÇ6R’²†–b†67VÆT–æFW‚ãÒ’VÇ6R’VÇ6RÓ¢fÂÖVçRÒÆ&VÇ2²Æ—7Dödæ÷DçVÆÂ€¢&÷VæBæ&G7VæFW&w&÷VæCòæÆWB²$w&æBVæFW&w&÷VæB(
"VF—B"ÒÀ¢&÷VæBæ&G7öff–ç3òæÆWB²%öff–â66R(
"VF—B"ÒÀ¢&÷VæBæ&G76VÅ7F–6¶W'3òæÆWB²%6VÂ7F–6¶W'2(
"VF—B"ÒÀ¢&÷VæBæ&G7&ÆÄ67VÆW3òæÆWB²$&ÆÂ67VÆW2(
"ff—‚VF—B"ÒÀ¢&÷VæBæ&G76öçFW7CòæÆWB²$6öçFW7B&V6÷&G2(
"VF—B&æ²ö–çG2"Ò¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"—FV×2"¢ç6WD—FV×2†ÖVçRçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’°¢v†Vâ°¢–æFW‚ÓÒ6öçFW7D–æFW‚Óâ6†÷t&G76öçFW7B†&÷VæB¢–æFW‚ÓÒ67VÆT–æFW‚Óâ6†÷t&G7&ÆÄ67VÆW2†&÷VæBÂÂfÇ6R¢–æFW‚ÓÒ6VÄ–æFW‚Óâ6†÷t&G76VÅ7F–6¶W$f–ÇFW'2†&÷VæB¢–æFW‚ÓÒVæFW&w&÷VæD–æFW‚Óâ6†÷t&G7VæFW&w&÷VæE6V7F–öç2†&÷VæB¢–æFW‚ÓÒöff–ä–æFW‚Óâ6†÷t&G7öff–ç2†&÷VæBÂÂfÇ6R¢–æFW‚–âÆ&VÇ2æ–æF–6W2Óâ6†÷t&G7–çfVçF÷'•ö6¶WB†&÷VæBÂ&rçö6¶WG2ævWD÷$çVÆÂ†–æFW‚“òçö6¶WBÂ¢Ð¢Ð¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ6†÷t&G7–çfVçF÷'•ö6¶WB†&÷VæC¢7v—F6…&VE6W76–öâÂö6¶WC¢&G7–çfVçF÷'•ö6¶WCòÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæ&G7–çfVçF÷'’ó¢&WGW&à¢fÂVçG&–W2Ò–b‡ö6¶WBÓÒçVÆÂ’&rçVæÆ—7FVE&V6÷&G2VÇ6R&rçö6¶WB‡ö6¶WB’ç&V6÷&FVE6Æ÷G0¢fÂF—FÆRÒö6¶WCòæF—7Æ”æÖRó¢$÷F†W"6fVB&V6÷&G2 ¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG&–W2ç6—¦WÒæöç¦W&ò&V6÷&G2(
"G¶&rç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢†–b‡ö6¶WBÓÒçVÆÂ’%VæÆ—7FVB—FVÒ–æF–6W2–âæF—fR'&’÷&FW"â"VÇ6R%6fVB6÷'BÖçVÖ&W"÷&FW#²F–W2W6R—FVÒ”BÂ¦W&ò×6÷'B&W6–GVÇ2&VÖ–âÆ7Bâ"’°¢$6÷VçG2ÂfÆw2æB6÷'BçVÖ&W'2&R6fVBfÇVW2â¦W&ò×VçF—G’æBVçW7VÂ&V6÷&G2&R&WF–æVBâ6FÆöwVVBæöâÖV×G’&V6÷&G26â&R6†ævVBF‡&÷Vv‚âW‡Æ–6—FÇ’&Wf–WvVBæWr6÷“²VçW7VÂ&V6÷&G2&VÖ–âÆö6¶VBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂV×G”—FVÒÒö6¶WCòæÆWB²6VÆV7FVEö6¶WBÓà¢&G7–çfVçF÷'•&VDöæÇ’æÆÆ÷vVD—FVÔ–G2‡6VÆV7FVEö6¶WB’æf—'7D÷$çVÆÂ²&rç6Æ÷B†—B’æ—4V×G’Ð¢Ð¢–b‡ö6¶WBÒçVÆÂbbV×G”—FVÒÒçVÆÂ’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ.ûÈ²FB—FVÒ(
"6fRæWr6÷’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„&G7—FVÔVF—F÷"†&÷VæBÂö6¶WBÂÓÂG'VR¢Ð¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æòæöç¦W&ò6fVB&V6÷&G2–âF†—26V7F–öââ#²FW‡E6—¦RÒfbÒ¢f÷"†VçG'’–âVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ’9rG¶VçG'’æ6÷VçGÒ(
"6÷'BG¶VçG'’ç6÷'D÷&FW'Ò"°¢†–b†VçG'’æ—4æWr’"(
"æWr"VÇ6R""’²†–b†VçG'’æ—4ff÷&—FR’"(
"ff÷&—FR"VÇ6R""’°¢†–b‚VçG'’æ†56÷'D÷&FW"ÇÂVçG'’æ†5VçW7VÄÖWFFFÇÂVçG'’æ6÷VçBÓÒ’"(
"6†V6²6fVBFWF–Ç2"VÇ6R""¢6WDöä6Æ–6´Æ—7FVæW"²6†÷t&G7–çfVçF÷'”—FVÒ†&÷VæBÂVçG'’æ—FVÔ–B’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"GF—FÆRG·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚%ö6¶WG2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7–çfVçF÷'•ö6¶WG2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7–çfVçF÷'•ö6¶WB†&÷VæBÂö6¶WBÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7–çfVçF÷'•ö6¶WB†&÷VæBÂö6¶WBÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7–çfVçF÷'”—FVÒ†&÷VæC¢7v—F6…&VE6W76–öâÂ—FVÔ–C¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæ&G7–çfVçF÷'“òç6Æ÷B†—FVÔ–B’ó¢&WGW&à¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ’(
"&VBÖöæÇ•ÆâG¶VçG'’çö6¶WCòæF—7Æ”æÖRó¢$æ÷B–âF†RæF—fRö6¶WB6FÆöwVR'ÕÆåÆâ"¢VæB‚%6fVB6–væVB6÷VçC¢G¶VçG'’æ6÷VçGÕÆäæWs¢G¶VçG'’æ—4æWwÒ‡6VVâv÷&BWVÇ2•Æäff÷&—FS¢G¶VçG'’æ—4ff÷&—FWÒ‡v÷&BWVÇ2•Æâ"¢VæB‚%6fVB6÷'BçVÖ&W#¢G¶VçG'’ç6÷'D÷&FW'ÕÆä†2æF—fRö'F–æVB÷6÷'BÖ&¶W#¢G¶VçG'’æ†56÷'D÷&FW'ÕÆâ"¢VæB‚%&VfW&Væ6RÖ†–×VÓ¢G¶VçG'’ç&VfW&Væ6TÖ„6÷VçCòçFõ7G&–ær‚’ó¢%Væ¶æ÷vâ'ÕÆäW†6VVG2&VfW&Væ6RÖ†–×VÓ¢G¶VçG'’æW†6VVG5&VfW&Væ6TÖ‡ÕÆåÆâ"¢VæB‚%&r6÷VçBv÷&C¢G¶VçG'’ç&t6÷VçGÕÆå&r6VVâv÷&C¢G¶VçG'’ç&u6VVäfÆwÕÆå&rff÷&—FRv÷&C¢G¶VçG'’ç&tff÷&—FTfÆwÕÆäÆ–væÖVçBv÷&C¢G¶VçG'’æÆ–væÖVçGÕÆåÆâ"¢VæB‚$—FVÒ”B—2F†RæF—fR'&’–æFW‚âgVÆÂ×v–GF‚fÆrvWGFW'2W6RW†7BWVÆ—G’Âæ÷BÆ÷r&—B÷"æöç¦W&ò6†V6²â"¢VæB‚%¦W&òVçF—F–W2ÂæVvF—fR6÷VçG2Â6öæfÆ–7F–ærÖ&¶W'2æBÆ–væÖVçB'—FW2&R&WF–æVBâ6FÆöwVRöÖ†–Ö&RFW67&—F—fRÂæ÷BÆVvÆ—G’fW&F–7Bâ"¢VæB‚$6ö×ÆWFVÇ’¦W&ò&V6÷&B†2æò6fVB6÷'BÖ&¶W"æB—2†–FFVâg&öÒF†—2Æ—7BÂWfVâF†÷Vv‚F†RæF—fRæWrvWGFW"&WGW&ç2G'VRâ"¢VæB†–b†VçG'’çö6¶WBÒçVÆÂbbVçG'’æ6÷VçBâbbVçG'’ç&VfW&Væ6TÖ„6÷VçBÒçVÆÂ’%W6RVF—B—FVÒFò&Wf–WrVçF—G’6†ævR÷"&VÖ÷fÂ6fVBFòæWr6÷’â"VÇ6R%F†—2VçW7VÂ&V6÷&B&VÖ–ç2Æö6¶VBâ"¢Ð¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G¶VçG'’æ—FVÔæÖWÒ"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢fÂ6VÆV7FVEö6¶WBÒVçG'’çö6¶W@¢–b‡6VÆV7FVEö6¶WBÒçVÆÂbbVçG'’æ6÷VçBâbbVçG'’ç&VfW&Væ6TÖ„6÷VçBÒçVÆÂ’°¢'V–ÆFW"ç6WE÷6—F—fT'WGFöâ‚$VF—B—FVÒ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„&G7—FVÔVF—F÷"†&÷VæBÂ6VÆV7FVEö6¶WBÂVçG'’æ—FVÔ–BÂfÇ6R¢Ð¢Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„&G7—FVÔVF—F÷"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢ö6¶WC¢&G7–çfVçF÷'•ö6¶WBÀ¢—FVÔ–C¢–çBÀ¢FC¢&ööÆVà¢’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„&G7—FVÔ7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒçö6¶WB"Âö6¶WBææÖR¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæ–B"Â—FVÔ–B¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæFB"ÂFB¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW4–çfVçF÷'•ö6¶WG2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæÆVvVæG4&6WW4–çfVçF÷'’ó¢&WGW&à¢fÂÆ&VÇ2Ò&rçö6¶WG2æÖ²"G¶—BææÖWÒ(
"G¶—Bç&V6÷&FVD—FV×2ç6—¦WÒ&V6÷&FVB6Æ÷G2"Ò°¢$ff÷&—FRfÆw2(
"G¶&ræff÷&—FT—FVÔ–G2ç6—¦WÒ6fVBfÆw2 ¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"—FV×2"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW4–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚Â¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW4–çfVçF÷'•ö6¶WB†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæÆVvVæG4&6WW4–çfVçF÷'’ó¢&WGW&à¢fÂö6¶WBÒ&rçö6¶WG2ævWD÷$çVÆÂ†–æFW‚¢–b‡ö6¶WBÓÒçVÆÂbb–æFW‚Ò&rçö6¶WG2ç6—¦R’&WGW&à¢fÂF÷FÂÒö6¶WCòç&V6÷&FVD—FV×3òç6—¦Ró¢&ræff÷&—FT—FVÔ–G2ç6—¦P¢fÂÆ7EvRÒ‡F÷FÂÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂF—FÆRÒö6¶WCòææÖRó¢$ff÷&—FRfÆw2 ¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ–b‡ö6¶WBÓÒçVÆÂ’%6fVBff÷&—FRfÆw2–æ6ÇVFR—FV×2'6VçBg&öÒF†R&râff÷&—FRfÆrFöW2æ÷B&÷fR÷væW'6†—â ¢VÇ6R"G·ö6¶WBæ66—G—Ò7F—fR6Æ÷G2(
"G·ö6¶WBæ—FV×2ç6—¦WÒ‡—6–6Â6Æ÷G2(
"G·ö6¶WBç&W6–GVÄ6÷VçGÒ&V6÷&FVB&W–öæB7F—fR66—G•Æâ"°¢%6F6†VÂWw&FW3¢G¶&rç&u6F6†VÅWw&FW7ÕÆå6fVB÷&FW"æB6–væVBVçF—F–W2&R&WF–æVBâ6FÆöwVVB&V6÷&G2–â7F—fR6Æ÷G26â&R6†ævVBF‡&÷Vv‚&Wf–WvVBæWr6÷“²VçW7VÂæB&W6–GVÂ&V6÷&G2&VÖ–âÆö6¶VBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂV×G•6Æ÷BÒö6¶WCòçF¶T–b²—BæÆÆ÷w46FÆöwVVDFBÓòæ—FV×0¢òæf—'7D÷$çVÆÂ²—Bçv—F†–ä7F—fT66—G’bb—Bæ—5&V6÷&FVBÐ¢–b‡ö6¶WBÒçVÆÂbbV×G•6Æ÷BÒçVÆÂ’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ.ûÈ²FB—FVÒ(
"6fRæWr6÷’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆVvVæG4&6WW4—FVÔVF—F÷"†&÷VæBÂ–æFW‚ÂV×G•6Æ÷Bç6Æ÷BÂG'VR¢Ð¢Ò¢–b‡F÷FÂÓÒ’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æò6fVB&V6÷&G2–âF†—26V7F–öââ#²FW‡E6—¦RÒfbÒ¢–b‡ö6¶WBÒçVÆÂ’f÷"†VçG'’–âö6¶WBç&V6÷&FVD—FV×2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6Æ÷BG¶VçG'’ç6Æ÷B²Ó¢G¶VçG'’æ—FVÔæÖWÒ9rG¶VçG'’æ6÷VçGÒ"°¢†–b†VçG'’æ—4ff÷&—FR’"(
"ff÷&—FR"VÇ6R""’²†–b‚VçG'’çv—F†–ä7F—fT66—G’’"(
"&W–öæB7F—fR66—G’"VÇ6R""¢6WDöä6Æ–6´Æ—7FVæW"²6†÷tÆVvVæG4&6WW4–çfVçF÷'”—FVÒ†&÷VæBÂ–æFW‚ÂVçG'’ç6Æ÷B’Ð¢Ò’VÇ6Rf÷"†–B–â&ræff÷&—FT—FVÔ–G2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G´ÆVvVæG4&6WW4—FVÔæÖW2ææÖR†–B—Ò‚2F–B’(
"ff÷&—FR#²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"GF—FÆRG·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚%ö6¶WG2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW4–çfVçF÷'•ö6¶WG2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW4–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚ÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG4&6WW4–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚ÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG4&6WW4–çfVçF÷'”—FVÒ†&÷VæC¢7v—F6…&VE6W76–öâÂö6¶WD–æFWƒ¢–çBÂ6Æ÷C¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂö6¶WBÒ&÷VæBæÆVvVæG4&6WW4–çfVçF÷'“òçö6¶WG3òævWD÷$çVÆÂ‡ö6¶WD–æFW‚’ó¢&WGW&à¢fÂVçG'’Òö6¶WBæ—FV×2ævWD÷$çVÆÂ‡6Æ÷B’ó¢&WGW&à¢fÂVF—F&ÆRÒVçG'’çv—F†–ä7F—fT66—G’bbVçG'’æ6÷VçBâb`¢ÆVvVæG4&6WW4—FVÔæÖW2æ—46FÆöwVVB†VçG'’æ—FVÔ–B¢fÂFWF–ÂÒ"G¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ’(
"&VBÖöæÇ•ÆâG·ö6¶WBææÖWÒ(
"6Æ÷BG·6Æ÷B²ÕÆåÆâ"°¢%6fVB6–væVBVçF—G“¢G¶VçG'’æ6÷VçGÕÆå&r—FVÒv÷&C¢G¶VçG'’ç&t—FVÔ–GÕÆå&rVçF—G’v÷&C¢G¶VçG'’ç&t6÷VçGÕÆâ"°¢$ff÷&—FS¢G¶VçG'’æ—4ff÷&—FWÕÆåv—F†–â7F—fR66—G“¢G¶VçG'’çv—F†–ä7F—fT66—G—ÕÆå&VfW&Væ6RÖ†–×VÓ¢G·ö6¶WBç&VfW&Væ6TÖ„6÷VçGÕÆåÆâ"°¢$GWÆ–6FR”G2Â¦W&ò÷"æVvF—fRVçF—F–W2æB&V6÷&G2&W–öæBF†R7F—fR6F6†VÂ&R&WF–æVBâ&VfW&Væ6RÖ†–Ö&RFW67&—F—fS²æòÆVvÆ—G’÷"÷væW'6†——2–æfW'&VBâ"°¢–b†VF—F&ÆR’%W6RVF—B—FVÒFò&Wf–WrVçF—G’6†ævR÷"&VÖ÷fÂ6fVBFòæWr6÷’âF†R–æFWVæFVçBff÷&—FRfÆr&VÖ–ç2Væ6†ævVBâ ¢VÇ6R%F†—2VçW7VÂ÷"–æ7F—fR&V6÷&B&VÖ–ç2Æö6¶VBâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G¶VçG'’æ—FVÔæÖWÒ"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b†VF—F&ÆR’'V–ÆFW"ç6WE÷6—F—fT'WGFöâ‚$VF—B—FVÒ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆVvVæG4&6WW4—FVÔVF—F÷"†&÷VæBÂö6¶WD–æFW‚Â6Æ÷BÂfÇ6R¢Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆVvVæG4&6WW4—FVÔVF—F÷"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢ö6¶WD–æFWƒ¢–çBÀ¢6Æ÷C¢–çBÀ¢FC¢&ööÆVà¢’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆVvVæG4&6WW4—FVÔ7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒçö6¶WB"Âö6¶WD–æFW‚¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒç6Æ÷B"Â6Æ÷B¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæFB"ÂFB¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWD–çfVçF÷'•ö6¶WG2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBç66&ÆWEf–öÆWD–çfVçF÷'’ó¢&WGW&à¢fÂÆ&VÇ2Ò&rçö6¶WG2æÖ²"G¶—Bçö6¶WBæF—7Æ”æÖWÒ(
"G¶—Bç&V6÷&FVE6Æ÷G2ç6—¦WÒ&V6÷&FVB—FV×2"Ò°¢$÷F†W"6fVB&V6÷&G2(
"G¶&rçVæÆ—7FVE&V6÷&G2ç6—¦WÒVæÆ—7FVB–æF–6W2 ¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"—FV×2"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWD–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚Â¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWD–çfVçF÷'•ö6¶WB†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBç66&ÆWEf–öÆWD–çfVçF÷'’ó¢&WGW&à¢fÂö6¶WBÒ&rçö6¶WG2ævWD÷$çVÆÂ†–æFW‚¢–b‡ö6¶WBÓÒçVÆÂbb–æFW‚Ò&rçö6¶WG2ç6—¦R’&WGW&à¢fÂVçG&–W2Òö6¶WCòç&V6÷&FVE6Æ÷G2ó¢&rçVæÆ—7FVE&V6÷&G0¢fÂF—FÆRÒö6¶WCòçö6¶WCòæF—7Æ”æÖRó¢$÷F†W"6fVB&V6÷&G2 ¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG&–W2ç6—¦WÒ&V6÷&FVB—FV×2–âæF—fR—FVÒÔ”B÷&FW"â¦W&òVçF—F–W2ÂVæ¶æ÷vâfÆw2æBÖ—6ÖF6†VB6fVBö6¶WG2&VÖ–âf—6–&ÆRâæ÷&ÖÂ6FÆöwVVB&V6÷&G26â&R6†ævVBF‡&÷Vv‚âW‡Æ–6—FÇ’&Wf–WvVBæWr6÷“²VçW7VÂ&V6÷&G2&VÖ–âÆö6¶VBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡ö6¶WBÒçVÆÂbb66&ÆWEf–öÆWD–çfVçF÷'•&VDöæÇ’æÆÆ÷vVD—FVÔ–G2‡ö6¶WBçö6¶WB¢æç’²&rç6Æ÷B†—B’æ—5&V6÷&FVBÒ’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ.ûÈ²FB—FVÒ(
"6fRæWr6÷’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6…66&ÆWEf–öÆWD—FVÔVF—F÷"†&÷VæBÂö6¶WBçö6¶WBÂÓÂG'VR¢Ð¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æò6fVB&V6÷&G2–âF†—26V7F–öââ#²FW‡E6—¦RÒfbÒ¢f÷"†VçG'’–âVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ’9rG¶VçG'’æ6÷VçGÒ"°¢†–b†VçG'’æ—4æWr’"(
"æWr"VÇ6R""’²†–b†VçG'’æ—4ff÷&—FR’"(
"ff÷&—FR"VÇ6R""’°¢†–b‚VçG'’æ—4ö'F–æVBÇÂVçG'’ç÷V6„ÖF6†W46FÆöwVR’"(
"6†V6²6fVBFWF–Ç2"VÇ6R""¢6WDöä6Æ–6´Æ—7FVæW"²6†÷u66&ÆWEf–öÆWD–çfVçF÷'”—FVÒ†&÷VæBÂVçG'’æ—FVÔ–B’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"GF—FÆRG·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚%ö6¶WG2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWD–çfVçF÷'•ö6¶WG2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWD–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚ÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷u66&ÆWEf–öÆWD–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚ÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷u66&ÆWEf–öÆWD–çfVçF÷'”—FVÒ†&÷VæC¢7v—F6…&VE6W76–öâÂ—FVÔ–C¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBç66&ÆWEf–öÆWD–çfVçF÷'“òç6Æ÷B†—FVÔ–B’ó¢&WGW&à¢fÂFWF–ÂÒ"G¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ’(
"&VBÖöæÇ•ÆâG¶VçG'’çö6¶WCòæF—7Æ”æÖRó¢$æ÷B–âF†RæF—fR6FÆöwVR'ÕÆåÆâ"°¢%6fVB6–væVBVçF—G“¢G¶VçG'’æ6÷VçGÕÆå&rVçF—G’v÷&C¢G¶VçG'’ç&t6÷VçGÕÆå6fVB÷V6‚v÷&C¢G¶VçG'’ç&u÷V6‡ÕÆâ"°¢$ÖF6†W26FÆöwVRö6¶WC¢G¶VçG'’ç÷V6„ÖF6†W46FÆöwVWÕÆäæWs¢G¶VçG'’æ—4æWwÕÆäff÷&—FS¢G¶VçG'’æ—4ff÷&—FWÕÆäö'F–æVBfÆs¢G¶VçG'’æ—4ö'F–æVGÕÆâ"°¢%&rfÆrv÷&C¢G¶VçG'’ç&tfÆw7ÕÆåFF–ærv÷&C¢G¶VçG'’çFF–æwÕÆå&VfW&Væ6RÖ†–×VÓ¢G¶VçG'’ç&VfW&Væ6TÖ„6÷VçCòçFõ7G&–ær‚’ó¢%Væ¶æ÷vâ'ÕÆåÆâ"°¢%F†R—FVÒ”B—2F†R6fVB'&’–æFW‚â6FÆöwVRö6¶WG2æBÖ†–ÖFW67&–&RF†Rf÷&ÖC²F†W’Fòæ÷BW7F&Æ—6‚ÆVvÆ—G’÷"÷væW'6†—âV×G’ôdddddddb÷V6‚ÖöæÇ’&V6÷&G2&R†–FFVâg&öÒÆ—7G2âÆÂ2Ã&V6÷&G2&VÖ–â–âF†R6æ6†÷Bâ"°¢–b†VçG'’æ—4VF—F&ÆR’%W6RVF—B—FVÒFò&Wf–WrVçF—G’6†ævR÷"&VÖ÷fÂ6fVBFòæWr6÷’âæWræBff÷&—FRfÆw2&VÖ–âVæ6†ævVBf÷"VçF—G’VF—G2â ¢VÇ6R%F†—2VçW7VÂ÷"Ö—6ÖF6†VB&V6÷&B&VÖ–ç2Æö6¶VBâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G¶VçG'’æ—FVÔæÖWÒ"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢fÂ6VÆV7FVEö6¶WBÒVçG'’çö6¶W@¢–b‡6VÆV7FVEö6¶WBÒçVÆÂbbVçG'’æ—4VF—F&ÆR’'V–ÆFW"ç6WE÷6—F—fT'WGFöâ‚$VF—B—FVÒ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6…66&ÆWEf–öÆWD—FVÔVF—F÷"†&÷VæBÂ6VÆV7FVEö6¶WBÂVçG'’æ—FVÔ–BÂfÇ6R¢Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6…66&ÆWEf–öÆWD—FVÔVF—F÷"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢ö6¶WC¢66&ÆWEf–öÆWD–çfVçF÷'•ö6¶WBÀ¢—FVÔ–C¢–çBÀ¢FC¢&ööÆVà¢’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6…66&ÆWEf–öÆWD—FVÔ7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒçö6¶WB"Âö6¶WBææÖR¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæ–B"Â—FVÔ–B¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæFB"ÂFB¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð  ¢&—fFRgVâ6†÷tÆVvVæG5¦–çfVçF÷'•ö6¶WG2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæÆVvVæG5¦–çfVçF÷'’ó¢&WGW&à¢fÂÆ&VÇ2Ò&rçö6¶WG2æÖ²"G¶—Bçö6¶WBæF—7Æ”æÖWÒ(
"G¶–b†—BçVæÆö6¶VB’%VæÆö6¶VB"VÇ6R$Æö6¶VB'Ò(
"G¶—Bç&V6÷&FVE6Æ÷G2ç6—¦WÒ&V6÷&FVB—FV×2"Ò°¢$÷F†W"6fVB&V6÷&G2(
"G¶&rçVæÆ—7FVE&V6÷&G2ç6—¦WÒVæÆ—7FVB–æF–6W2 ¢fÂÖVçRÒÆ&VÇ2²$&rfÆw2æBÖWFFF ¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"—FV×2"¢ç6WD—FV×2†ÖVçRçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚Â¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦–çfVçF÷'•ö6¶WB†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂ&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæÆVvVæG5¦–çfVçF÷'’ó¢&WGW&à¢–b†–æFW‚ÓÒ&rçö6¶WG2ç6—¦R²’²6†÷tÆVvVæG5¦–çfVçF÷'”ÖWFFF†&÷VæB“²&WGW&âÐ¢fÂö6¶WBÒ&rçö6¶WG2ævWD÷$çVÆÂ†–æFW‚¢–b‡ö6¶WBÓÒçVÆÂbb–æFW‚Ò&rçö6¶WG2ç6—¦R’&WGW&à¢fÂVçG&–W2Òö6¶WCòç&V6÷&FVE6Æ÷G2ó¢&rçVæÆ—7FVE&V6÷&G0¢fÂF—FÆRÒö6¶WCòçö6¶WCòæF—7Æ”æÖRó¢$÷F†W"6fVB&V6÷&G2 ¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG&–W2ç6—¦WÒ&V6÷&FVB—FV×2–âæF—fR—FVÒÔ”B÷&FW"â¦W&òVçF—F–W2ÂVæ¶æ÷vâfÆw2æBÖ—6ÖF6†VB6fVBö6¶WG2&VÖ–âf—6–&ÆRâæ÷&ÖÂ6FÆöwVVB&V6÷&G2–âVæÆö6¶VBö6¶WG26â&R6†ævVBF‡&÷Vv‚âW‡Æ–6—FÇ’&Wf–WvVBæWr6÷“²VçW7VÂ&V6÷&G2&VÖ–âÆö6¶VBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡ö6¶WBÒçVÆÂbbö6¶WBçVæÆö6¶VBbbÆVvVæG5¦–çfVçF÷'•&VDöæÇ’æÆÆ÷vVD—FVÔ–G2‡ö6¶WBçö6¶WB’æç’²–BÓà¢fÂ6Æ÷BÒ&rç6Æ÷B†–B¢6Æ÷Bæ—5&V6÷&FVBbb–B–â6WDöbƒ#c"Â#c’’bb‡6Æ÷Bç&VfW&Væ6TÖ„6÷VçBó¢’â ¢Ò’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ.ûÈ²FB—FVÒ(
"6fRæWr6÷’ ¢—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆVvVæG5¦—FVÔVF—F÷"†&÷VæBÂö6¶WBçö6¶WBÂÓÂG'VR¢Ð¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æò6fVB&V6÷&G2–âF†—26V7F–öââ#²FW‡E6—¦RÒfbÒ¢f÷"†VçG'’–âVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ’9rG¶VçG'’æ6÷VçGÒ"°¢†–b†VçG'’æ—4æWr’"(
"æWr"VÇ6R""’²†–b†VçG'’æ—4ff÷&—FR’"(
"ff÷&—FR"VÇ6R""’°¢†–b‚VçG'’æ†5fÆ–E÷V6‚ÇÂVçG'’ç÷V6„ÖF6†W46FÆöwVR’"(
"6†V6²6fVBFWF–Ç2"VÇ6R""¢6WDöä6Æ–6´Æ—7FVæW"²6†÷tÆVvVæG5¦–çfVçF÷'”—FVÒ†&÷VæBÂVçG'’æ—FVÔ–B’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"GF—FÆRG·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚%ö6¶WG2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦–çfVçF÷'•ö6¶WG2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚ÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷tÆVvVæG5¦–çfVçF÷'•ö6¶WB†&÷VæBÂ–æFW‚ÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦–çfVçF÷'”—FVÒ†&÷VæC¢7v—F6…&VE6W76–öâÂ—FVÔ–C¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæÆVvVæG5¦–çfVçF÷'“òç6Æ÷B†—FVÔ–B’ó¢&WGW&à¢fÂFWF–ÂÒ"G¶VçG'’æ—FVÔæÖWÒ‚2G¶VçG'’æ—FVÔ–GÒ’(
"&VBÖöæÇ•ÆâG¶VçG'’çö6¶WCòæF—7Æ”æÖRó¢$æ÷B–âF†RæF—fR6FÆöwVR'ÕÆåÆâ"°¢%6fVB6–væVBVçF—G“¢G¶VçG'’æ6÷VçGÕÆå&rVçF—G’v÷&C¢G¶VçG'’ç&t6÷VçGÕÆå6fVB÷V6‚v÷&C¢G¶VçG'’ç&u÷V6‡ÕÆâ"°¢$ÖF6†W26FÆöwVRö6¶WC¢G¶VçG'’ç÷V6„ÖF6†W46FÆöwVWÕÆäæWs¢G¶VçG'’æ—4æWwÕÆäff÷&—FS¢G¶VçG'’æ—4ff÷&—FWÕÆåfÆ–B÷V6‚Ö&¶W#¢G¶VçG'’æ†5fÆ–E÷V6‡ÕÆäæWr–6·Wæ÷F–f–6F–öã¢G¶VçG'’æ—4æWtæ÷F–g—ÕÆäæWr–â6†÷¢G¶VçG'’æ—4æWu6†÷ÕÆä†VÆBfÆs¢G¶VçG'’æ—4†VÆGÕÆâ"°¢%&rfÆrv÷&C¢G¶VçG'’ç&tfÆw7ÕÆåFF–ærv÷&C¢G¶VçG'’çFF–æwÕÆå&VfW&Væ6RÖ†–×VÓ¢G¶VçG'’ç&VfW&Væ6TÖ„6÷VçCòçFõ7G&–ær‚’ó¢%Væ¶æ÷vâ'ÕÆåÆâ"°¢%F†R—FVÒ”B—2F†R6fVB'&’–æFW‚â6FÆöwVRö6¶WG2æBÖ†–ÖFW67&–&RF†Rf÷&ÖC²F†W’Fòæ÷BW7F&Æ—6‚ÆVvÆ—G’÷"÷væW'6†—âæF—fR¦W&ò÷Væ7V—&VBFV×ÆFW2&R†–FFVâg&öÒÆ—7G2â†VÆBfÆw2&R6fVBÖWFFFæBFòæ÷B&÷fR7W'&VçBö¼:–Ööâ&VÆF–öç6†—âÆÂ2Ã&V6÷&G2&VÖ–â–âF†R6æ6†÷Bâ"°¢–b†VçG'’æ—4VF—F&ÆRbbVçG'’çö6¶WCòæÆWB²Óâ&÷VæBæÆVvVæG5¦–çfVçF÷'“òçö6¶WG3òæf—'7D÷$çVÆÂ²—Bçö6¶WBÓÒÓòçVæÆö6¶VBÒÓÒG'VR¢%W6RVF—B—FVÒFò&Wf–WrVçF—G’6†ævR÷"&VÖ÷fÂ6fVBFòæWr6÷’â6fVBæWrÂff÷&—FRÂ6†÷ÂæB†VÆBfÆw2&VÖ–âVæ6†ævVBf÷"VçF—G’VF—G2â ¢VÇ6R%F†—2VçW7VÂÂ7V6–ÂÖ6÷VçBÂÖ—6ÖF6†VBÂ÷"Æö6¶VB×ö6¶WB&V6÷&B&VÖ–ç2Æö6¶VBâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G¶VçG'’æ—FVÔæÖWÒ"¢ç6WEf–Wr‡67&öÆÂ’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢fÂ6VÆV7FVEö6¶WBÒVçG'’çö6¶W@¢–b‡6VÆV7FVEö6¶WBÒçVÆÂbbVçG'’æ—4VF—F&ÆRb`¢&÷VæBæÆVvVæG5¦–çfVçF÷'“òçö6¶WG3òæf—'7D÷$çVÆÂ²—Bçö6¶WBÓÒ6VÆV7FVEö6¶WBÓòçVæÆö6¶VBÓÒG'VR’°¢'V–ÆFW"ç6WE÷6—F—fT'WGFöâ‚$VF—B—FVÒ(
"æWr6÷’"’²òÂòÓà¢–b‡6W76–öâÓÓÒ&÷VæB’ÆVæ6„ÆVvVæG5¦—FVÔVF—F÷"†&÷VæBÂ6VÆV7FVEö6¶WBÂVçG'’æ—FVÔ–BÂfÇ6R¢Ð¢Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâÆVæ6„ÆVvVæG5¦—FVÔVF—F÷"€¢&÷VæC¢7v—F6…&VE6W76–öâÀ¢ö6¶WC¢ÆVvVæG5¦–çfVçF÷'•ö6¶WBÀ¢—FVÔ–C¢–çBÀ¢FC¢&ööÆVà¢’°¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&’ó¢&WGW&à¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢7F'D7F—f—G’€¢–çFVçB‡F†—2Â7v—F6„ÆVvVæG5¦—FVÔ7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒçö6¶WB"Âö6¶WBææÖR¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæ–B"Â—FVÔ–B¢çWDW‡G&‚'6Rç7v—F6‚æ—FVÒæFB"ÂFB¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð ¢&—fFRgVâ6†÷tÆVvVæG5¦–çfVçF÷'”ÖWFFF†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&rÒ&÷VæBæÆVvVæG5¦–çfVçF÷'’ó¢&WGW&à¢fÂFWF–ÂÒ"G¶&rç&Wf—6–öãòæF—7Æ”æÖRó¢%&Wf—6–öâVæ¶æ÷vâ'Ò(
"&VBÖöæÇ•ÆåÆâ"°¢%6fVBö6¶WBfÆw3¢G¶&rç&u÷V6„fÆw7ÕÆäFF—F–öæÂö6¶WBfÆr†&—B‚“¢G¶&ræFF—F–öæÅö6¶WDfÆwÕÆâ"°¢$ÖVv6†&B&VfW&Væ6RÆ–Ö—C¢G¶–b†&ræÖVv6†&DÖ…F6†VB’“““’VÇ6R““—ÕÆåÆâ"°¢&rçö6¶WG2æ¦ö–åFõ7G&–ær‚%Æâ"’²"G¶—Bçö6¶WBæF—7Æ”æÖWÓ¢G¶–b†—BçVæÆö6¶VB’%VæÆö6¶VB"VÇ6R$Æö6¶VB'Ò"Ò°¢%ÆåÆå&r&rF–Âƒ#‚'—FW2“¥Æâ"²&rçF–Ä'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÐ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"&rfÆw2"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæE6V7F–öç2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVæFW&w&÷VæBÒ&÷VæBæ&G7VæFW&w&÷VæBó¢&WGW&à¢fÂ¶–æG2Ò&G7VæFW&w&÷VæD¶–æBçfÇVW2‚¢fÂÆ&VÇ2Ò¶–æG2æÖ²"G¶—BæF—7Æ”æÖWÒ(
"G·VæFW&w&÷VæBæVçG&–W2†—B’ç6—¦WÒ&V6÷&FVB"Ò°¢$÷F†W"6fVB&V6÷&G2(
"G·VæFW&w&÷VæBæVçG&–W2†çVÆÂ’ç6—¦WÒVæÆ—7FVB–æF–6W2 ¢fÂ7F—f—G”–æFW‚Ò–b†&÷VæBæ&G7VæFW&w&÷VæD7F—f—G’ÒçVÆÂ’Æ&VÇ2ç6—¦RVÇ6RÓ¢fÂ6V7&WD&6T–æFW‚Ò–b†&÷VæBæ&G76V7&WD&6RÒçVÆÂ’Æ&VÇ2ç6—¦R²†–b†7F—f—G”–æFW‚ãÒ’VÇ6R’VÇ6RÓ¢fÂç4–æFW‚Ò–b†&÷VæBæ&G7VæFW&w&÷VæDç2ÒçVÆÂ’Æ&VÇ2ç6—¦R²†–b†7F—f—G”–æFW‚ãÒ’VÇ6R’²†–b‡6V7&WD&6T–æFW‚ãÒ’VÇ6R’VÇ6RÓ¢fÂ&WGW&äFF–æFW‚Ò–b†&÷VæBæ&G7VæFW&w&÷VæE&WGW&âÒçVÆÂ’Æ&VÇ2ç6—¦R²†–b†7F—f—G”–æFW‚ãÒ’VÇ6R’²†–b‡6V7&WD&6T–æFW‚ãÒ’VÇ6R’²†–b†ç4–æFW‚ãÒ’VÇ6R’VÇ6RÓ¢fÂÖVçRÒÆ&VÇ2²Æ—7Dödæ÷DçVÆÂ†&÷VæBæ&G7VæFW&w&÷VæD7F—f—G“òæÆWB²$7F—f—G’&V6÷&G2(
"VF—B"ÒÀ¢&÷VæBæ&G76V7&WD&6SòæÆWB²%6V7&WB&6R(
"VF—B"ÒÀ¢&÷VæBæ&G7VæFW&w&÷VæDç3òæÆWB²$å2–çFW&7F–öâ&V6÷&G2(
"VF—B"ÒÀ¢&÷VæBæ&G7VæFW&w&÷VæE&WGW&ãòæÆWB²%6fVB&WGW&âFF(
"VF—B"Ò¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"w&æBVæFW&w&÷VæB(
"—FVÒVF—B"¢ç6WD—FV×2†ÖVçRçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’°¢–b†–æFW‚ÓÒ&WGW&äFF–æFW‚’6†÷t&G7VæFW&w&÷VæE&WGW&â†&÷VæB¢VÇ6R–b†–æFW‚ÓÒç4–æFW‚’6†÷t&G7VæFW&w&÷VæDç4f–ÇFW'2†&÷VæB¢VÇ6R–b†–æFW‚ÓÒ6V7&WD&6T–æFW‚’6†÷t&G76V7&WD&6R†&÷VæB¢VÇ6R–b†–æFW‚ÓÒ7F—f—G”–æFW‚’6†÷t&G7VæFW&w&÷VæD7F—f—G’†&÷VæB¢VÇ6R–b†–æFW‚–âÆ&VÇ2æ–æF–6W2’6†÷t&G7VæFW&w&÷VæE6V7F–öâ†&÷VæBÂ¶–æG2ævWD÷$çVÆÂ†–æFW‚’ÂÂfÇ6R¢Ð¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæE6V7F–öâ†&÷VæC¢7v—F6…&VE6W76–öâÂ¶–æC¢&G7VæFW&w&÷VæD¶–æCòÀ¢&WVW7FVEvS¢–çBÂ–æ6ÇVFTV×G“¢&ööÆVâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVæFW&w&÷VæBÒ&÷VæBæ&G7VæFW&w&÷VæBó¢&WGW&à¢fÂVçG&–W2ÒVæFW&w&÷VæBæVçG&–W2†¶–æBÂ–æ6ÇVFTV×G’¢fÂF—FÆRÒ¶–æCòæF—7Æ”æÖRó¢$÷F†W"6fVB&V6÷&G2 ¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG&–W2ç6—¦WÒG¶–b†–æ6ÇVFTV×G’’'F÷FÂ"VÇ6R'&V6÷&FVB'ÒVçG&–W2–âVæFW&w&÷VæB”B÷&FW"â6VÆV7BâVçG'’f÷"FWF–Ç2âVçF—F–W2æBfÆw2&R6†÷vâ26fVBâ ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ–b†–æ6ÇVFTV×G’’%6†÷r&V6÷&FVBVçG&–W2"VÇ6R$–æ6ÇVFRV×G’VçG&–W2 ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæE6V7F–öâ†&÷VæBÂ¶–æBÂÂ–æ6ÇVFTV×G’’Ð¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æò6fVB&V6÷&G2–âF†—26V7F–öââ#²FW‡E6—¦RÒfbÒ¢f÷"†VçG'’–âVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"G¶VçG'’ææÖWÒ…Tr2G¶VçG'’çVæFW&w&÷VæD–GÒ’9rG¶VçG'’æ6÷VçGÒ"°¢†–b†VçG'’æ—4æWr’"(
"æWr"VÇ6R""’²†–b†VçG'’æ—4ff÷&—FR’"(
"ff÷&—FR"VÇ6R""’°¢†–b†VçG'’æ†5VçW7VÄÖWFFF’"(
"6†V6²6fVBFWF–Ç2"VÇ6R""¢6WDöä6Æ–6´Æ—7FVæW"²6†÷t&G7VæFW&w&÷VæD—FVÒ†&÷VæBÂVçG'’çVæFW&w&÷VæD–B’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"GF—FÆRG·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚%6V7F–öç2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæE6V7F–öç2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæE6V7F–öâ†&÷VæBÂ¶–æBÂvRÒÂ–æ6ÇVFTV×G’’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæE6V7F–öâ†&÷VæBÂ¶–æBÂvR²Â–æ6ÇVFTV×G’’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæD—FVÒ†&÷VæC¢7v—F6…&VE6W76–öâÂVæFW&w&÷VæD–C¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæ&G7VæFW&w&÷VæCòç6Æ÷B‡VæFW&w&÷VæD–B’ó¢&WGW&à¢fÂFVf–æ—F–öâÒVçG'’æ6FÆöwVP¢fÂFWF–ÂÒ"G¶VçG'’ææÖWÒ(
"—FVÒVF—EÆåVæFW&w&÷VæB”C¢G¶VçG'’çVæFW&w&÷VæD–GÕÆâ"°¢"G¶VçG'’æ¶–æCòæF—7Æ”æÖRó¢%VæÆ—7FVB–æFW‚'ÕÆåÆå6fVB6–væVBVçF—G“¢G¶VçG'’æ6÷VçGÕÆå&rVçF—G’v÷&C¢G¶VçG'’ç&t6÷VçGÕÆâ"°¢$æWs¢G¶VçG'’æ—4æWwÕÆäff÷&—FS¢G¶VçG'’æ—4ff÷&—FWÕÆå&r†–FRÖæWrv÷&C¢G¶VçG'’ç&t†–FTæWtfÆwÕÆå&rff÷&—FRv÷&C¢G¶VçG'’ç&tff÷&—FTfÆwÕÆâ"°¢†FVf–æ—F–öãòæÆWB²"G¶—Bæ¶–æBç&VfW&Væ6TÆ&VÇÓ¢G¶—BææF—fU&VfW&Væ6T–GÕÆâ"Òó¢$æò6FÆöwVRÖ–æuÆâ"’°¢%&VfW&Væ6RÖ†–×VÓ¢G¶VçG'’ç&VfW&Væ6TÖ„6÷VçCòçFõ7G&–ær‚’ó¢%Væ¶æ÷vâ'ÕÆåÆâ"°¢%VæFW&w&÷VæB”G2F–ffW"g&öÒæ÷&ÖÂ&r”G2âÆÂ““’VçG&–W2&VÖ–â–âF†R6æ6†÷BÂ–æ6ÇVF–ær¦W&òVçF—F–W2ÂVæ¶æ÷vâ–æF–6W2æBæöç7FæF&BfÆrv÷&G2â6FÆöwVRÖ–æw2æBÖ†–Ö&RFW67&—F—fS²F†W’Fòæ÷B&÷fR÷væW'6†—÷"ÆVvÆ—G’â7FGVR–çfVçF÷'’FöW2æ÷BFW67&–&R6V7&WB&6RÆ6VÖVçB÷"7F—fRVffV7G2â ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Â²–b†VçG'’æ6FÆöwVRÒçVÆÂbbVçG'’ç&VfW&Væ6TÖ„6÷VçBÒçVÆÂ¢%ÆåÆåW6RVF—B—FVÒFò&Wf–WrVçF—G’ÂæWr×7FFRÂ÷"ff÷&—FR6†ævW26fVBFòæWr6÷’â ¢VÇ6R%ÆåÆåF†—2VæÆ—7FVB&V6÷&B&VÖ–ç2Æö6¶VB&V6W6RæòfW&–f–VBVF—F–ærÆ–Ö—B—2f–Æ&ÆRâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂbbVçG'’æ6FÆöwVRÒçVÆÂbbVçG'’ç&VfW&Væ6TÖ„6÷VçBÒçVÆÂ’°¢FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BVæFW&w&÷VæB—FVÒ#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7VæFW&w&÷VæD—FVÔ7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çVæFW&w&÷VæBæ–B"ÂVæFW&w&÷VæD–B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"G¶VçG'’ææÖWÒ(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ6†÷t&G7çW'6W'’†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂçW'6W'’Ò&÷VæBæ&G7çW'6W'’ó¢&WGW&à¢fÂVvu7FFRÒv†Vâ†çW'6W'’ç&tVvtf–Æ&ÆTfÆr’²ÂÓâ$æ÷B&VG’#²ÂÓâ%&VG’#²VÇ6RÓâ%Væ¶æ÷vâ6fVBfÆr‚G¶çW'6W'’ç&tVvtf–Æ&ÆTfÆwÒ’"Ð¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"çW'6W'’(
"&VBÖöæÇ•ÆåÆâ"°¢$Vvrf–Æ&–Æ—G“¢FVvu7FFUÆå6fVBVvr7FW6÷VçC¢G¶çW'6W'’æVvu7FW6÷VçGÕÆåÆâ"°¢"G¶çW'6W'’æFV6öFVE&VçD6÷VçGÒFV6öFVB&VçG2(
"G¶çW'6W'’çVç&VF&ÆU&VçD6÷VçGÒVç&VF&ÆR&V6÷&G5Æâ"°¢%6VÆV7B&VçBf÷"—G26fVBö¼:–ÖöâFWF–Ç2â ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢f÷"‡&VçB–âçW'6W'’ç&VçG2’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%&VçBG·&VçBç÷6—F–öçÒ(
"G¶&G7Æ&VÂ‡&VçBç&VB—Ò ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7ö¶VÖöâ†&÷VæBÂ$çW'6W'’&VçBG·&VçBç÷6—F–öçÒ"Â&VçBç&VB’Ð¢Ò¢&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6fVBVvrÖWFFF ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7çW'6W'”ÖWFFF†&÷VæB’Ð¢Ò¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ%&VBÖöæÇ’6fVB7FFRâ&VçB6ö×F–&–Æ—G’ÂgWGW&RVvw2æBF–ÖRVçF–ÂâVvr&Ræ÷B6Æ7VÆFVBâVç&VF&ÆR&VçG27F’6W&FRg&öÒV×G’6Æ÷G3²VvrÖWFFF&VÖ–ç2–ç7V7F&ÆRâ ¢FW‡E6—¦RÒf`¢Ò¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$çW'6W'’(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7çW'6W'”ÖWFFF†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂçW'6W'’Ò&÷VæBæ&G7çW'6W'’ó¢&WGW&à¢fÂFWF–ÂÒ"G¶çW'6W'’çfW'6–öâæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG¶çW'6W'’ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢%&rVvrÖf–Æ&ÆRv÷&C¢G¶çW'6W'’ç&tVvtf–Æ&ÆTfÆwÕÆäæF—fRfÆrWVÇ2¢G¶çW'6W'’æ—4Vvtf–Æ&ÆWÕÆâ"°¢$6æöæ–6ÂfÆrv÷&C¢G¶çW'6W'’æ†46æöæ–6ÄVvtfÆwÕÆå6fVB7FW6÷VçB‡6–væVB“¢G¶çW'6W'’æVvu7FW6÷VçGÕÆâ"°¢%&r7FWv÷&C¢G¶çW'6W'’ç&tVvu7FW6÷VçGÕÆå6fVB6VVB‡Vç6–væVBcBÖ&—B“¢G¶çW'6W'’æVvu6VVGÕÆå6fVB6VVB††W‚“¢G¶çW'6W'’æVvu6VVD†W‡ÕÆåÆâ"°¢%F†R6ö×ÆWFR6VVB—2&W6W'fVBÂ–æ6ÇVF–ærç’6fVB6–vâW‡FVç6–öââæò6VVBvVæW&F–öâÂ$ärGfæ6RÂ6÷VçFW"&W6WB÷"&W—"—2W&f÷&ÖVBâ66†VB&VçB7FG2Ö’&R7FÆRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$çW'6W'’ÖWFFF(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ6†÷t&G7&GFÆUF÷vW"†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂF÷vW"Ò&÷VæBæ&G7&GFÆUF÷vW"ó¢&WGW&à¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"&GFÆRF÷vW"(
"&VBÖöæÇ•ÆåÆâ"°¢$&GFÆRö–çG3¢G·F÷vW"æ&GFÆUö–çG7ÕÆå6fVBÖ†–×VÒÖ7FW"&æ³¢G·F÷vW"æÖ†–×VÔÖ7FW%&æ·ÕÆâ"°¢$7W'&VçBÖöFS¢G´&G7&GFÆUF÷vW$ÖöFRæÆ&VÂ‡F÷vW"ç&t7W'&VçDÖöFR—ÕÆå&Wf–÷W2ÖöFS¢G´&G7&GFÆUF÷vW$ÖöFRæÆ&VÂ‡F÷vW"ç&u&Wf–÷W4ÖöFR—ÕÆâ"°¢%6fVBF–Ç’6†ÆÆVævR6÷VçFW#¢G·F÷vW"æF–Ç”6†ÆÆVævT6÷VçGÕÆå6fVB6†ÆÆVævR6÷VçFW#¢G·F÷vW"æ6†ÆÆVævT6÷VçGÕÆåÆå6VÆV7BÖöFRf÷"—G26fVB&V6÷&G2â ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢f÷"‡&V6÷&B–âF÷vW"ç&V6÷&G2’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ"G·&V6÷&BæÖöFRæF—7Æ”æÖWÒ(
"&æ²G·&V6÷&Bç&æ·Ò(
"7G&V²G·&V6÷&Bæ7W'&VçE7G&V·Ò ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUF÷vW%&V6÷&B†&÷VæBÂ&V6÷&BæÖöFR’Ð¢Ò¢&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6fVBF÷vW"ÖWFFF ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUF÷vW$ÖWFFF†&÷VæB’Ð¢Ò¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ$ÆÂf÷W"ÖöFW2&VÖ–â&VF&ÆRÂ–æ6ÇVF–ær–æ7F—fRÖöFW2âfÇVW2&R6†÷vâ26fVC²&æ·2Â6÷VçFW'2æBF†V—"6öç6—7FVæ7’&Ræ÷B&V6Æ7VÆFVBâ ¢FW‡E6—¦RÒf`¢Ò¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$&GFÆRF÷vW"(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&GFÆUF÷vW%&V6÷&B†&÷VæC¢7v—F6…&VE6W76–öâÂÖöFS¢&G7&GFÆUF÷vW$ÖöFR’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&BÒ&÷VæBæ&G7&GFÆUF÷vW#òç&V6÷&B†ÖöFR’ó¢&WGW&à¢fÂ7W7VæFVBÒv†Vâ‡&V6÷&Bç&u7W7VæFVDfÆr’²ÂÓâ$æò#²ÂÓâ%–W2#²VÇ6RÓâ%Væ¶æ÷vâ‚G·&V6÷&Bç&u7W7VæFVDfÆwÒ’"Ð¢fÂFWF–ÂÒ"G¶ÖöFRæF—7Æ”æÖWÒ(
"&VBÖöæÇ•ÆåÆâ"°¢%6fVB&æ³¢G·&V6÷&Bç&æ·ÕÆå&æ²ÖF÷vâÆ÷726÷VçFW#¢G·&V6÷&Bç&æ´F÷väÆ÷76W7ÕÆâ"°¢$7W'&VçB7G&V³¢G·&V6÷&Bæ7W'&VçE7G&V·ÕÆå&Wf–÷W27G&V²6÷VçFW#¢G·&V6÷&Bç&Wf–÷W57G&V·ÕÆâ"°¢%F÷FÂv–â6÷VçFW#¢G·&V6÷&BçF÷FÅv–ç7ÕÆåv–âÆö÷6÷VçFW#¢G·&V6÷&Bçv–äÆö÷6÷VçFW'ÕÆäÆ÷726÷VçFW#¢G·&V6÷&BæÆ÷746÷VçFW'ÕÆâ"°¢%7W7VæFVC¢G7W7VæFVEÆå&r7W7Vç6–öâv÷&C¢G·&V6÷&Bç&u7W7VæFVDfÆwÕÆå&r6ÆV&VB'—FS¢G·&V6÷&Bç&t6ÆV&VDfÆwÕÆåÆâ"°¢$&GFÆR6VVB††W‚“¢G·&V6÷&Bæ&GFÆU6VVBçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆâ"°¢&V6÷&BçG&–æW%6VVG2æÖ–æFW†VB²’Â6VVBÓâ%G&–æW"6VVBG¶’²Ó¢G·6VVBçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—Ò"Òæ¦ö–åFõ7G&–ær‚%Æâ"’°¢%ÆåÆå&rÖöFR&V6÷&BƒB'—FW2“¥Æâ"²&V6÷&Bç&t'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆå6VVG2æB6÷VçFW'2&R6fVBFFöæÇ’âæòG&–æW"&VF–7F–öâÂ&æ²&W—"÷"$ärGfæ6R—2W&f÷&ÖVBâF†R&Wf–÷W27G&V²6÷VçFW"—2æ÷B&V6ö×WFVB2Æ–fWF–ÖR&V6÷&Bâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶ÖöFRæF—7Æ”æÖWÒ(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&GFÆUF÷vW$ÖWFFF†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂF÷vW"Ò&÷VæBæ&G7&GFÆUF÷vW"ó¢&WGW&à¢fÂFWF–ÂÒ%6fRÆ–÷WBG·F÷vW"ç&Wf—6–öâæF—7Æ”æÖWÒ(
"&VBÖöæÇ•ÆåÆâ"°¢%&rÖ†–×VÒÖ7FW"&æ²v÷&C¢G·F÷vW"ç&tÖ†–×VÔÖ7FW%&æ·ÕÆä7W'&VçBÖöFR”C¢G·F÷vW"ç&t7W'&VçDÖöFWÕÆå&Wf–÷W2ÖöFR”C¢G·F÷vW"ç&u&Wf–÷W4ÖöFWÕÆâ"°¢%Æå&r&GFÆRF÷vW"&Æö6²ƒCC'—FW2“¥Æâ"²F÷vW"ç&t'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäÆÂ6fVB'—FW2&VÖ–âW†7BÂ–æ6ÇVF–ærÆ–væÖVçBæBVæ6Æ76–f–VBf–VÆG2âv†öÆRÖf–ÆR–çFVw&—G’76VB&Vf÷&R&VF–ærâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%F÷vW"ÖWFFF(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ6†÷t&G7öff–ç2†&÷VæC¢7v—F6…&VE6W76–öâÂ&WVW7FVEvS¢–çBÂ–æ6ÇVFTV×G“¢&ööÆVâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂöff–ç2Ò&÷VæBæ&G7öff–ç2ó¢&WGW&à¢fÂVçG&–W2Òöff–ç2æVçG&–W2†–æ6ÇVFTV×G’¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"öff–â66R(
"VF—EÆåÆâ"°¢"G·öff–ç2æ¶æ÷våöff–ä6÷VçGÒ¶æ÷vâöff–ç2(
"G·öff–ç2çVæ¶æ÷våG—T6÷VçGÒVæ¶æ÷vâ&V6÷&G2(
"G·öff–ç2æV×G”6÷VçGÒV×G’6Æ÷G5Æâ"°¢%6fVB6öö¶–ær6÷VçC¢G·öff–ç2æ6öö¶–æt6÷VçGÕÆåÆâ"°¢"G¶–b†–æ6ÇVFTV×G’’$ÆÂ6Æ÷G2"VÇ6R$æöâÖV×G’&V6÷&G2'Ò–â6fVB6Æ÷B÷&FW"â6VÆV7B6Æ÷BFò&Wf–Wr—G2æF—fRf–VÆG2æBVF—BöæR&V6÷&B–âfW&–f–VBæWr6÷’â ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ–b†–æ6ÇVFTV×G’’$†–FRV×G’6Æ÷G2"VÇ6R$–æ6ÇVFRV×G’6Æ÷G2 ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7öff–ç2†&÷VæBÂÂ–æ6ÇVFTV×G’’Ð¢Ò¢&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6fVB66RÖWFFF ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7öff–äÖWFFF†&÷VæB’Ð¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$ÆÂ6fVB6Æ÷G2†fRF†RV×G’G—RÖ&¶W"â#²FW‡E6—¦RÒfbÒ¢f÷"†VçG'’–âVçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6Æ÷BG¶VçG'’ç÷6—F–öçÒ(
"G¶VçG'’ææÖWÒ"²†–b‚VçG'’æ—4V×G’’"(
"ÇbG¶VçG'’æÆWfVÇÒ(
"6Öö÷F‚G¶VçG'’ç6Öö÷F†æW77Ò"VÇ6R""’°¢†–b†VçG'’æ—4æWr’"(
"æWr"VÇ6R""¢6WDöä6Æ–6´Æ—7FVæW"²6†÷t&G7öff–â†&÷VæBÂVçG'’æ–æFW‚’Ð¢Ò¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%öff–â66RG·vR²ÒòG¶Æ7EvR²Ò(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7öff–ç2†&÷VæBÂvRÒÂ–æ6ÇVFTV×G’’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7öff–ç2†&÷VæBÂvR²Â–æ6ÇVFTV×G’’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7öff–â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVçG'’Ò&÷VæBæ&G7öff–ç3òç6Æ÷B†–æFW‚’ó¢&WGW&à¢fÂFWF–ÂÒ%6Æ÷BG¶VçG'’ç÷6—F–öçÒ(
"G¶VçG'’ææÖWÕÆåÆâ"°¢%6fVBG—R”C¢G¶VçG'’çG—T–GÕÆäV×G’Ö&¶W#¢G¶VçG'’æ—4V×G—ÕÆå6fVBÆWfVÃ¢G¶VçG'’æÆWfVÇÕÆå6fVB6Öö÷F†æW72öfVVÃ¢G¶VçG'’ç6Öö÷F†æW77ÕÆâ"°¢%6fVBfÆf÷W'2…7–7’òG'’ò7vVWBò&—GFW"ò6÷W"“¥ÆâG¶VçG'’æfÆf÷'2æ¦ö–åFõ7G&–ær‚"ò"—ÕÆåÆâ"°¢$æWs¢G¶VçG'’æ—4æWwÕÆå&ræWrv÷&C¢G¶VçG'’ç&tæWtfÆwÕÆä6æöæ–6ÂæWrv÷&C¢G¶VçG'’æ†46æöæ–6ÄæWtfÆwÕÆåÆâ"°¢%&r6Æ÷Bƒb'—FW2“¥ÆâG¶VçG'’ç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’×ÕÆåÆâ"°¢$öæÇ’G—R#SRÖ&·2âV×G’6Æ÷BâV×G’6Æ÷G2Ö’&WF–â÷F†W"6fVBFFâVæ¶æ÷vâG—W2æBVçW7VÂfÆw2&VÖ–â&W6W'f&ÆRv†VâVçF÷V6†VBâVF—F–ær6†ævW2öæÇ’6VÆV7FVBFö7VÖVçFVBf–VÆG2æBF†RæF—fRv†öÆRÖf–ÆRÔCS²&W6W'fVB'—FW2&VÖ–âW†7Bâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—Böff–â6Æ÷BG¶VçG'’ç÷6—F–öçÒ#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7öff–ä7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çöff–âç6Æ÷B"Â–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%öff–â6Æ÷BG¶VçG'’ç÷6—F–öçÒ"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7öff–äÖWFFF†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂöff–ç2Ò&÷VæBæ&G7öff–ç2ó¢&WGW&à¢fÂFWF–ÂÒ%6fRÆ–÷WBG·öff–ç2ç&Wf—6–öâæF—7Æ”æÖWÒ(
"&VBÖöæÇ•ÆåÆâ"°¢%6fVB6öö¶–ær6÷VçB‡6–væVB“¢G·öff–ç2æ6öö¶–æt6÷VçGÕÆå&r6öö¶–ærv÷&C¢G·öff–ç2ç&t6öö¶–æt6÷VçGÕÆåÆâ"°¢%&röff–â66RƒÃcB'—FW2“¥Æâ"²öff–ç2ç&t'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäÆÂ6Æ÷G27F’–âæF—fR÷&FW"âF†—2ÖWFFFf–WrFöW2æ÷B6†ævRF†R6öö¶–ær6÷VçBÂ6÷'B&V6÷&G2ÂfVVBö¼:–ÖöâÂ÷"6ÆVâW6Æ÷BFFâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%öff–â66RÖWFFF(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ&G7V&æ6U7VÖÖ'’†V&æ6S¢&G7G&–æW$V&æ6U&VB“¢7G&–ær°¢gVâ6ö÷&F–æFR‡fÇVS¢fÆöB“¢7G&–ærÒ–b‡fÇVRæ—4f–æ—FR‚’’fÇVRçFõ7G&–ær‚’VÇ6R$æöâÖf–æ—FR6fVBfÇVR‚GfÇVR’ ¢fÂG5Æ–W"Òv†Vâ†V&æ6Rç&tG5Æ–W$fÆr’²Óâ$öfb#²Óâ$öâ#²VÇ6RÓâ%Væ¶æ÷vâ‚G¶V&æ6Rç&tG5Æ–W$fÆwÒ’"Ð¢&WGW&â$V&æ6RæB÷6—F–öâ(
"&VBÖöæÇ•Æâ"°¢%6fVB÷WFf—C¢G¶V&æ6Ræf6†–öäæÖRó¢%Væ¶æ÷vâf6†–öâ2G¶V&æ6Ræf6†–öä–GÒ'ÕÆâ"°¢$÷WFf—B”C¢G¶V&æ6Ræf6†–öä–GÕÆä&öG’G—R”C¢G¶V&æ6Ræ&öG•G—T–GÕÆåG&–æW"f–Wr”C¢G¶V&æ6RçG&–æW%f–Wt–GÕÆâ"°¢%6fVB&Vv–öâ'—FS¢G¶V&æ6Rç&Vv–öä6öFWÕÆå6fVB7F'FW"×G—R”C¢G¶V&æ6Rç7F'FW%G—T–GÕÆäE2Æ–W"fÆs¢FG5Æ–W%Æâ"°¢$föÆÆ÷vW"–æFWƒ¢G¶V&æ6RæföÆÆ÷vW$–æFW‡ÕÆå6fVBw&–B‚ò“¢G¶V&æ6Ræw&–E‡ÒòG¶V&æ6Ræw&–E—ÕÆâ"°¢%6fVB†V–v‡C¢G¶6ö÷&F–æFR†V&æ6Ræ†V–v‡B—ÕÆå6fVB&÷FF–öã¢G¶6ö÷&F–æFR†V&æ6Rç&÷FF–öâ—ÕÆâ ¢Ð ¢&—fFRgVâ6†÷t&G7V&æ6R†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂV&æ6RÒ&÷VæBæ&G7G&–æW$V&æ6Ró¢&WGW&à¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG¶V&æ6Rç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢&G7V&æ6U7VÖÖ'’†V&æ6R’°¢%Æå&r†V–v‡B&—G3¢G¶V&æ6Rç&t†V–v‡D&—G2çFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆâ"°¢%&r&÷FF–öâ&—G3¢G¶V&æ6Rç&u&÷FF–öä&—G2çFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆâ"°¢%&rE2Æ–W"'—FS¢G¶V&æ6Rç&tG5Æ–W$fÆwÕÆåÆâ"°¢%&ræF—fRG&–æW"7FGW2ƒƒ'—FW2“¥Æâ"²V&æ6Rç&u7FGW4'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆåF†W6R&R6fVB”G2æB6ö÷&F–æFW2â6Æ÷F†–ærVæÆö6·2Â÷WFf—B6ö×F–&–Æ—G’ÂföÆÆ÷vW"–FVçF—G’ÂÖfÆ–F—G’æB6ö÷&F–æFRVæ—G2&Ræ÷B–æfW'&VBâæöâÖf–æ—FRfÇVW2¶VWF†V—"÷&–v–æÂ&r&—G2âæòVF—F–ærÂFVÆW÷'F–ær÷"6fRw&—F–ær—2f–Æ&ÆRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$V&æ6RæB÷6—F–öâ(
"&VBÖöæÇ’"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ&G7F–ÖW7F×7VÖÖ'’‡F–ÖW7F×3¢&G7F–ÖW7F×5&VB“¢7G&–ærÒ'V–ÆE7G&–ær°¢VæB‚%6fVBF–ÖW7F×2(
"UD2(
"VF—EÆâ"¢f÷"†VçG'’–âF–ÖW7F×2æVçG&–W2’°¢VæB‚"G¶VçG'’æ¶–æBæF—7Æ”æÖWÓ¢G¶VçG'’çWF5F–ÖW7F×ó¢$÷WG6–FR7W÷'FVBd”ÄUD”ÔR&ævR'ÕÆâ"¢VæB‚%6fVB6÷VçC¢G¶VçG'’æ6÷VçGÓ²&rd”ÄUD”ÔRF–6·3¢G¶VçG'’æf–ÆUF–ÖUF–6·7ÕÆâ"¢Ð¢VæB‚%F†W6R&R6fVBfÇVW2Âæ÷BVÆ6VBÆ—F–ÖRâ¦W&òd”ÄUD”ÔRF—7Æ—2—G2æF—fRcWö6‚âVæÇG’7F—f—G’æBF–Ç’ÖWfVçBVÆ–v–&–Æ—G’&Ræ÷B–æfW'&VBåÆâ"¢Ð ¢&—fFRgVâ6†÷t&G7F–ÖW7F×2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂF–ÖW7F×2Ò&÷VæBæ&G7F–ÖW7F×2ó¢&WGW&à¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·F–ÖW7F×2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢&G7F–ÖW7F×7VÖÖ'’‡F–ÖW7F×2’²%Æå&rd”ÄUD”ÔRv÷&G2††W‚“¥Æâ"°¢F–ÖW7F×2æVçG&–W2æ¦ö–åFõ7G&–ær‚%Æâ"’²"G¶—Bæ¶–æBæF—7Æ”æÖWÓ¢G¶¦fæÆæräÆöærçFô†W…7G&–ær†—Bæf–ÆUF–ÖUF–6·2’çWW&66R‚’çE7F'BƒbÂsr—Ò"Ò°¢%ÆåÆå&ræF—fR7—7FVÒFFƒ3"'—FW2“¥Æâ"²F–ÖW7F×2ç&u7—7FVÔ'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäFFW2W6RUD2æB&WF–âÖææ÷6V6öæB&V6—6–öââ÷WBÖöb×&ævRfÇVW2¶VWF†V—"6–væVBF–6·2æBW†7B&rv÷&G2v—F†÷WB†–F–ær÷F†W"FFW2â6÷VçG2&R6fVB6÷VçFW'2v—F‚æò–æfW'&VBGW&F–öââ6Æö6²6æ6†÷BÂÆ–væÖVçBæB&W6W'fVB'—FW2&R&WF–æVBv—F†÷WB–çFW'&WFF–öââVF—F–ær6†ævW2öæÇ’6VÆV7FVBd”ÄUD”ÔRF–6·2æBF†RæF—fRv†öÆRÖf–ÆRÔCRâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B6fVBF–ÖW7F×2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7F–ÖW7F×47F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%6fVBF–ÖW7F×2(
"UD2"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ&G7Æ•F–ÖU7VÖÖ'’‡Æ•F–ÖS¢&G7Æ•F–ÖU&VB“¢7G&–ærÒ'V–ÆE7G&–ær°¢VæB‚%&V6÷&FVBÆ—F–ÖR(
"&VBÖöæÇ•Æâ"¢VæB‚%6fVB†÷W'3¦Ö–çWFW3§6V6öæG3¢G·Æ•F–ÖRç6fVEF–ÖUFW‡GÕÆâ"¢–b‚Æ•F–ÖRæ†46æöæ–6ÄÖ–çWFW4æE6V6öæG2¢VæB‚%6fVBÖ–çWFW2÷"6V6öæG2&R÷WG6–FR(	3S’âfÇVW2&R6†÷vâVæ6†ævVC²F†—2—2æ÷Bæ÷&ÖÆ—¦VBGW&F–öâåÆâ"¢VæB‚$†÷W'3¢G·Æ•F–ÖRæ†÷W'7Ó²Ö–çWFW3¢G·Æ•F–ÖRæÖ–çWFW7Ó²6V6öæG3¢G·Æ•F–ÖRç6V6öæG7ÕÆâ"¢Ð ¢&—fFRgVâ6†÷t&G7Æ•F–ÖR†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂÆ•F–ÖRÒ&÷VæBæ&G7Æ•F–ÖRó¢&WGW&à¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·Æ•F–ÖRç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢&G7Æ•F–ÖU7VÖÖ'’‡Æ•F–ÖR’²%Æå&r&V6÷&FVBÆ—F–ÖRƒB'—FW2“¥Æâ"°¢Æ•F–ÖRç&uÆ•F–ÖT'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆåF†—2—2F†R&V6÷&FVBÆ—F–ÖR6fVB–âF†Rf–ÆRÂ6W&FRg&öÒUD26fRF–ÖW7F×2âfÇVW2Fòæ÷BGfæ6Rv†–ÆRF†—2f–Wr—2÷Vââ¦W&ò—2F—7Æ–VB2¦W&ò&V6÷&FVBF–ÖRâæòvÖR×7V6–f–2†÷W"6Â6W76–öâGW&F–öâ÷"6Æö6²6öç6—7FVæ7’—2–æfW'&VBâæòF–ÖR6†ævW2Âæ÷&ÖÆ—¦F–öâ÷"6fRw&—F–ær—2f–Æ&ÆRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%&V6÷&FVBÆ—F–ÖR(
"&VBÖöæÇ’"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ&G7vÖT÷F–öç57VÖÖ'’†÷F–öç3¢&G7vÖT÷F–öç5&VB“¢7G&–ærÒ'V–ÆE7G&–ær°¢gVâÆ&VÂ†–C¢çVÖ&W"ÂæÖS¢7G&–æsò“¢7G&–ærÒ–b†æÖRÓÒçVÆÂ’%Væ¶æ÷vâ„”BF–B’"VÇ6R"FæÖR„”BF–B’ ¢VæB‚$vÖR÷F–öç2(
"&VBÖöæÇ•Æâ"¢VæB‚%FW‡B7VVC¢G¶Æ&VÂ†÷F–öç2çFW‡E7VVD–BÂ&G7vÖT÷F–öäæÖW2çFW‡E7VVB†÷F–öç2çFW‡E7VVD–B’—ÕÆâ"¢VæB‚$ÆæwVvS¢G¶Æ&VÂ†÷F–öç2æÆæwVvT–BÂ&G7vÖT÷F–öäæÖW2æÆæwVvR†÷F–öç2æÆæwVvT–B’—ÕÆâ"¢VæB‚%v–æF÷rG—R”C¢G¶÷F–öç2çv–æF÷uG—T–GÕÆâ"¢VæB‚$Ö÷fRæ–ÖF–öç3¢G¶Æ&VÂ†÷F–öç2æÖ÷fTæ–ÖF–öç4–BÂ&G7vÖT÷F–öäæÖW2æÖ÷fTæ–ÖF–öç2†÷F–öç2æÖ÷fTæ–ÖF–öç4–B’—ÕÆâ"¢VæB‚$&GFÆR7G–ÆS¢G¶Æ&VÂ†÷F–öç2æ&GFÆU7G–ÆT–BÂ&G7vÖT÷F–öäæÖW2æ&GFÆU7G–ÆR†÷F–öç2æ&GFÆU7G–ÆT–B’—ÕÆâ"¢VæB‚%'G’ô&÷‚6WGF–æs¢G¶Æ&VÂ†÷F–öç2ç'G”&÷„–BÂ&G7vÖT÷F–öäæÖW2ç'G”&÷‚†÷F–öç2ç'G”&÷„–B’—ÕÆâ"¢VæB‚$6ÖW&WöF÷vã¢G¶Æ&VÂ†÷F–öç2æ6ÖW&fW'F–6Ä–BÂ&G7vÖT÷F–öäæÖW2æ6ÖW&†÷F–öç2æ6ÖW&fW'F–6Ä–B’—ÕÆâ"¢VæB‚$6ÖW&ÆVgB÷&–v‡C¢G¶Æ&VÂ†÷F–öç2æ6ÖW&†÷&—¦öçFÄ–BÂ&G7vÖT÷F–öäæÖW2æ6ÖW&†÷F–öç2æ6ÖW&†÷&—¦öçFÄ–B’—ÕÆâ"¢VæB‚$–çWBÖöFS¢G¶Æ&VÂ†÷F–öç2æ–çWDÖöFT–BÂ&G7vÖT÷F–öäæÖW2æ–çWDÖöFR†÷F–öç2æ–çWDÖöFT–B’—ÕÆâ"¢f÷"†fÆr–â÷F–öç2æfÆw2’°¢fÂ7FFRÒv†Vâ†fÆrç&t'—FR’²Óâ$öfb#²Óâ$öâ#²VÇ6RÓâ%Væ¶æ÷vâ'—FRG¶fÆrç&t'—FWÒ"Ð¢VæB‚"G¶fÆræ¶–æBæF—7Æ”æÖWÓ¢G7FFUÆâ"¢Ð¢VæB‚%6fVBföÇVÖRfÇVW2(
"$tÓ¢G¶÷F–öç2æ&vÕföÇVÖWÓ²6÷VæBVffV7G3¢G¶÷F–öç2ç6÷VæDVffV7G5föÇVÖWÓ²fö–6S¢G¶÷F–öç2çfö–6UföÇVÖWÕÆâ"¢Ð ¢&—fFRgVâ6†÷t&G7vÖT÷F–öç2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ÷F–öç2Ò&÷VæBæ&G7vÖT÷F–öç2ó¢&WGW&à¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG¶÷F–öç2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢&G7vÖT÷F–öç57VÖÖ'’†÷F–öç2’²%Æå&rfÆr6Æ÷G2††W‚’æBæF—fR&ööÆVâ&VG3¥Æâ"°¢÷F–öç2æfÆw2æ¦ö–åFõ7G&–ær‚%Æâ"’²"G¶—Bæ¶–æBæF—7Æ”æÖWÓ¢G¶—Bç&uv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ó²æF—fRVæ&ÆVC¢G¶—BææF—fTVæ&ÆVGÒ"Ò°¢%ÆåÆå&ræF—fR6öæf–wW&F–öâƒcB'—FW2“¥Æâ"²÷F–öç2ç&t6öæf–t'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆåF†W6R&R6fVB6WGF–æw2âVæ¶æ÷vâ”G2æBfÆr'—FW2&VÖ–âVæ6†ævVBâföÇVÖRfÇVW2&R&r6fVBfÇVW2Âæ÷BW&6VçFvW2âfÆw2W6RF†Rf—'7B'—FS²F†V—"&VÖ–æ–ær6Æ÷B'—FW2&R&WF–æVBâ–ç7FçB—2&VfW&Væ6RFW‡B×7VVB”BæBFöW2æ÷B–×Ç’7W÷'FVB–âÖvÖRÖVçR6†ö–6Râv–æF÷r'Gv÷&²ÂÖVçRf–Æ&–Æ—G’æB6WGF–ær6ö×F–&–Æ—G’&Ræ÷B–æfW'&VBâæò6WGF–ær6†ævW2÷"6fRw&—F–ær—2f–Æ&ÆRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$vÖR÷F–öç2(
"&VBÖöæÇ’"¢ç6WEf–Wr‡67&öÆÂ’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæD7F—f—G’†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7F—f—G’Ò&÷VæBæ&G7VæFW&w&÷VæD7F—f—G’ó¢&WGW&à¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG¶7F—f—G’ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢$w&æBVæFW&w&÷VæB7F—f—G’(
"VF—EÆâ"°¢$f÷76–ÂF–vv–ærÆ—3¢G¶7F—f—G’æf÷76–ÄF–vv–æuÆ”6÷VçGÕÆâ"°¢%7FGVREb'&öF67G3¢G¶7F—f—G’ç7FGVUGd'&öF67D6÷VçGÕÆâ"°¢$Æ–¶W2öâ6V7&WB&6REb'&öF67G3¢G¶7F—f—G’ç6V7&WD&6UGdÆ–¶T6÷VçGÕÆâ"°¢$÷F†W"6V7&WB&6RÆ–¶W2‡6fVB6÷VçFW"“¢G¶7F—f—G’æ÷F†W%6V7&WD&6TÆ–¶T6÷VçGÕÆâ"°¢%7V66W76gVÂÆ–v‡B7FöæR6V&6†W3¢G¶7F—f—G’ç7V66W76gVÄÆ–v‡E7FöæU6V&6„6÷VçGÕÆåÆâ"°¢%&W6W'fVBv÷&B¢G¶7F—f—G’ç&W6W'fVCÒ††W‚G¶¦fæÆæräÆöærçFô†W…7G&–ær†7F—f—G’ç&W6W'fVC’çWW&66R‚’çE7F'BƒbÂsr—Ò•Æâ"°¢%&W6W'fVBv÷&B#¢G¶7F—f—G’ç&W6W'fVC'Ò††W‚G¶¦fæÆæräÆöærçFô†W…7G&–ær†7F—f—G’ç&W6W'fVC"’çWW&66R‚’çE7F'BƒbÂsr—Ò•ÆåÆâ"°¢%&ræF—fR7F—f—G’&V6÷&Bƒ3"'—FW2“¥Æâ"²7F—f—G’ç&u&V6÷&D'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆä6÷VçFW'2&R6†÷vâW†7FÇ’26fVBÂ–æ6ÇVF–æræVvF—fRfÇVW2â¦W&ò—26fVBfÇVRâF†W6R&V6÷&G2Fòæ÷BW7F&Æ—6‚–çfVçF÷'’VçF—F–W2Â6V7&WB&6RÆ6VÖVçBÂ7F—fR7FGVRVffV7G2Â&Wv&G2÷"öæÆ–æR÷væW'6†—â&W6W'fVBv÷&G2†fRæò–æfW'&VBÖVæ–æræB&VÖ–âW†7BGW&–ærVF—F–ærâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Â²%ÆåÆåW6RVF—B7F—f—G’6÷VçFW'2Fò&Wf–WrW†7Bf–VÆB6†ævW2æBw&—FRfW&–f–VBæWr6÷’â ¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B7F—f—G’6÷VçFW'2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7VæFW&w&÷VæD7F—f—G”VF—D7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$w&æBVæFW&w&÷VæB7F—f—G’(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ6†÷t&G7&V6÷&G2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7&V6÷&E7FF—7F–72ó¢&WGW&à¢fÂÆ&VÇ2Ò&V6÷&G2ç6WG2æÖ²%6WBG¶—Bæ–æFW‡ÒG¶–b†—Bæ–æFW‚ÓÒ’"(
"&VfW&Væ6R†VB"VÇ6R"(
"6fVB6WB'Ò(
"3&V6÷&G2"Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"&V6÷&B7FF—7F–72(
"VF—B†VB"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óâ–b‡6W76–öâÓÓÒ&÷VæBbb–æFW‚–â&V6÷&G2ç6WG2æ–æF–6W2’6†÷t&G7&V6÷&E6WB†&÷VæBÂ–æFW‚’Ð¢ç6WE÷6—F—fT'WGFöâ‚%G&–æW"&V6÷&G2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7FF—F–öæÅ&V6÷&G2†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&V6÷&E6WB†&÷VæC¢7v—F6…&VE6W76–öâÂ6WD–æFWƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7&V6÷&E7FF—7F–72ó¢&WGW&à¢–b‡6WD–æFW‚–â&V6÷&G2ç6WG2æ–æF–6W2’&WGW&à¢fÂ6WBÒ&V6÷&G2ç6WB‡6WD–æFW‚¢fÂÆ&VÇ2Ò6WBæVçG&–W2æÖ²"2G¶—Bç&V6÷&D–GÒG¶—BæF—7Æ”æÖWÓ¢G¶—Bç6–væVEfÇVWÒ"Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR€¢–b‡6WD–æFW‚ÓÒ’%&V6÷&B6WB(
"VF—B†VB"VÇ6R%&V6÷&B6WBG6WD–æFW‚(
"&VBÖöæÇ’ ¢¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–BÓâ–b‡6W76–öâÓÓÒ&÷VæBbb–B–â6WBæVçG&–W2æ–æF–6W2’6†÷t&G7&V6÷&B†&÷VæBÂ6WD–æFW‚Â–B’Ð¢ç6WE÷6—F—fT'WGFöâ‚%6WG2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&V6÷&G2†&÷VæB’Ð¢ç6WDæWWG&Ä'WGFöâ‚%&r6WB"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&V6÷&E&r†&÷VæBÂ6WD–æFW‚’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&V6÷&B†&÷VæC¢7v—F6…&VE6W76–öâÂ6WD–æFWƒ¢–çBÂ&V6÷&D–C¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7&V6÷&E7FF—7F–72ó¢&WGW&à¢–b‡6WD–æFW‚–â&V6÷&G2ç6WG2æ–æF–6W2ÇÂ&V6÷&D–B–âVçF–Â3’&WGW&à¢fÂVçG'’Ò&V6÷&G2ç6WB‡6WD–æFW‚’æVçG'’‡&V6÷&D–B¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·&V6÷&G2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢%6WBG6WD–æFW‚(
"&V6÷&B2G&V6÷&D–EÆâG¶VçG'’æF—7Æ”æÖWÕÆå&VfW&Væ6R¶W“¢G¶VçG'’ç&VfW&Væ6T¶W—ÕÆåÆâ"°¢%6fVB6–væVBfÇVS¢G¶VçG'’ç6–væVEfÇVWÕÆå&rVç6–væVBv÷&C¢G¶VçG'’ç&ufÇVWÕÆå&r†Wƒ¢G¶VçG'’ç&ufÇVRçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆâ"°¢%&VfW&Væ6RÆ–Ö—B†FW67&—F—fR“¢G¶VçG'’ç&VfW&Væ6TÆ–Ö—GÕÆåÆâ"°¢%6fVBfÇVW2&RæWfW"6Æ×VBFò&VfW&Væ6RÆ–Ö—G2âæVvF—fRfÇVW2æB÷&–v–æÂ&—G2&VÖ–â–çF7Bâ6WB—2F†R6WB&VB'’F†R&VfW&Væ6R†VBvWGFW"â÷F†W"6WG2†fRæò–æfW'&VBFFW2Â6‡&öæöÆöw’÷"÷væW'6†—â6ÆV"×F–ÖRVæ—G2Â&FRVæ—G2æB&–&&öâ&—BÖVæ–æw2&Ræ÷B–æfW'&VBâF†W6R&V6÷&G2Fòæ÷BW7F&Æ—6‚&Wv&G2Â&öw&W72fÆ–F—G’÷"6öç6—7FVæ7’v—F‚÷F†W"6fR6V7F–öç2âæòVF—F–ærÂ&W6WB÷"6fRw&—F–ær—2f–Æ&ÆRâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ–b‡6WD–æFW‚ÓÒ’°¢FWF–Âç&WÆ6R€¢$æòVF—F–ærÂ&W6WB÷"6fRw&—F–ær—2f–Æ&ÆRâ"À¢%F†R†VBfÇVR6â&RVF—FVBv—F†–â—G2Fö7VÖVçFVBF‡&÷Vv‚G¶VçG'’ç&VfW&Væ6TÆ–Ö—GÒ&ævS²VçW7VÂW†—7F–ærfÇVW2&VÖ–â&W6W'f&ÆRv†VâVçF÷V6†VBâ ¢¢ÒVÇ6RFWF–À¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6WD–æFW‚ÓÒbb6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B†VB&V6÷&B2G&V6÷&D–B#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7&V6÷&E7FF—7F–47F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ&G7ç&V6÷&Bæ–B"Â&V6÷&D–B¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR€¢–b‡6WD–æFW‚ÓÒ’$†VB&V6÷&B2G&V6÷&D–B(
"VF—B"VÇ6R%&V6÷&B2G&V6÷&D–B(
"&VBÖöæÇ’ ¢’ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚%6WB"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&V6÷&E6WB†&÷VæBÂ6WD–æFW‚’Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&V6÷&E&r†&÷VæC¢7v—F6…&VE6W76–öâÂ6WD–æFWƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7&V6÷&E7FF—7F–72ó¢&WGW&à¢–b‡6WD–æFW‚–â&V6÷&G2ç6WG2æ–æF–6W2’&WGW&à¢fÂ&rÒ&V6÷&G2ç6WB‡6WD–æFW‚’ç&t'—FW0¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·&V6÷&G2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢%6WBG6WD–æFW‚(
"3æF—fRv÷&G2(
"#'—FW5Æâ"²&ræ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆä'—FW2&VÖ–â–â6fVB÷&FW"â÷F†W"&V6÷&B6WG2&Ræ÷BÆ&VÆÆVB2†—7F÷&–6Â6æ6†÷G2âæòæ÷&ÖÆ—¦F–öâ÷"6fRw&—F–ær—2f–Æ&ÆRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%&r&V6÷&B6WBG6WD–æFW‚(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ¢ç6WE÷6—F—fT'WGFöâ‚%6WB"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&V6÷&E6WB†&÷VæBÂ6WD–æFW‚’Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ6†÷t&G7FF—F–öæÅ&V6÷&G2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7FF—F–öæÅ&V6÷&G2ó¢&WGW&à¢–b‚&V6÷&G2æ†4&Æö6²’°¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$FF—F–öæÂ&V6÷&BG&–æW'2(
"&VBÖöæÇ’"¢ç6WDÖW76vR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·&V6÷&G2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâG·&V6÷&G2æ'6Væ6U&V6öçÒæò&V6÷&G2&R7–çF†W6—¦VBâ"¢ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢&WGW&à¢Ð¢fÂÆ&VÇ2Ò&V6÷&G2ç&V6÷&G2æÖ°¢fÂæÖRÒ—BææÖSòæÆWB²fÇVRÓâ–b‡fÇVRæ—4V×G’‚’’$V×G’æÖR"VÇ6R%Â"GfÇVUÂ""Òó¢$æÖRVæf–Æ&ÆR ¢%6Æ÷BG¶—Bæ–æFW‡Ò(
"FæÖRG¶–b‚—Bæ†4æöç¦W&õ&V6÷&D'—FW2’"(
"ÆÂ×¦W&ò&V6÷&B"VÇ6R"'Ò ¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"FF—F–öæÂ&V6÷&BG&–æW'2(
"VF—B"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óâ–b‡6W76–öâÓÓÒ&÷VæBbb–æFW‚–â&V6÷&G2ç&V6÷&G2æ–æF–6W2’6†÷t&G7FF—F–öæÅ&V6÷&B†&÷VæBÂ–æFW‚’Ð¢ç6WE÷6—F—fT'WGFöâ‚%&r&æ¶–ærFF"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7FF—F–öæÅ&æ¶–ær†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7FF—F–öæÅ&V6÷&B†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7FF—F–öæÅ&V6÷&G2ó¢&WGW&à¢–b‚&V6÷&G2æ†4&Æö6²ÇÂ–æFW‚–â&V6÷&G2ç&V6÷&G2æ–æF–6W2’&WGW&à¢fÂVçG'’Ò&V6÷&G2ç&V6÷&B†–æFW‚¢fÂÆæwVvRÒ&G7vÖT÷F–öäæÖW2æÆæwVvR†VçG'’æÆæwVvT–B’ó¢%Væ¶æ÷vâ ¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·&V6÷&G2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆå6Æ÷BF–æFW‚(
"VF—EÆâ"°¢%6fVBæÖS¢G¶VçG'’ææÖSòæÆWB²%Â"F—EÂ""Òó¢%Væf–Æ&ÆR'ÕÆäæÖR—77VS¢G¶VçG'’ææÖT—77VRó¢$æöæR'ÕÆâ"°¢$åTÄÂFW&Ö–æF÷"–âæÖRf–VÆC¢G¶VçG'’æ†4æÖUFW&Ö–æF÷'ÕÆâ"°¢$ÆæwVvS¢FÆæwVvR„”BG¶VçG'’æÆæwVvT–GÒ•Æå6fVBvVæFW"'—FS¢G¶VçG'’ævVæFW$'—FWÕÆå6fVB&öG’×G—R”C¢G¶VçG'’æ&öG•G—T–GÕÆâ"°¢%&r”C3#¢G¶VçG'’æ–C3'ÕÆåD”Cc¢G¶VçG'’çF–CgÕÆå4”Cc¢G¶VçG'’ç6–CgÕÆåÆâ"°¢%&ræF—fR&V6÷&BƒC‚'—FW2“¥Æâ"²VçG'’ç&u&V6÷&D'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäæÖW2föÆÆ÷rF†R6fVBUDbÓdÄRf–VÆBWFò—G2f—'7BåTÄÂ÷"ÆÂ26öFRVæ—G2âÖÆf÷&ÖVBæÖW2Fòæ÷B†–FRçVÖW&–2f–VÆG2÷"÷F†W"6Æ÷G2âVæ6†ævVBæÖR÷G&6‚ÂFF–æræB&W6W'fVB'—FW27F’–çF7BâV×G’÷"ÆÂ×¦W&ò&V6÷&G2Fòæ÷B&÷fRâ76ö6–FVBG&–æW"W†—7G2âvVæFW"ö&öG’”G2Â&æ¶–ær76ö6–F–öç2Â÷væW'6†—æB6‡&öæöÆöw’&Ræ÷B–æfW'&VBâF†RFö7VÖVçFVBõBÂÆæwVvRÂvVæFW"Â&öG’G—RæB”C3"f–VÆG26â&RVF—FVB–âfW&–f–VBæWr6÷’â ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BFF—F–öæÂG&–æW"6Æ÷BF–æFW‚#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7FF—F–öæÅG&–æW$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ&G7æFF—F–öæÂç&V6÷&Bæ–æFW‚"Â–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$FF—F–öæÂG&–æW"6Æ÷BF–æFW‚(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚%6Æ÷G2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7FF—F–öæÅ&V6÷&G2†&÷VæB’Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7FF—F–öæÅ&æ¶–ær†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7FF—F–öæÅ&V6÷&G2ó¢&WGW&à¢–b‚&V6÷&G2æ†4&Æö6²’&WGW&à¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·&V6÷&G2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢%6fVB&æ¶–ær&Vv–öâ(
"3ƒB'—FW5Æâ"²&V6÷&G2ç&u&æ¶–æt'—FW2æ6‡Væ¶VBƒ3"’æÖ–æFW†VB²’Â&÷rÓà¢%&÷rF•Æâ"²&÷ræ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²Æ–æRÓâÆ–æRæ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÐ¢Òæ¦ö–åFõ7G&–ær‚%ÆåÆâ"’°¢%ÆåÆå&÷w2&WF–âæF—fR÷&FW"â'—FRÖVæ–æw2ÂfÆw2Â&æ·2æB76ö6–F–öç2v—F‚G&–æW"6Æ÷G2&RVæ6Æ76–f–VBâæòæ÷&ÖÆ—¦F–öâ÷"6fRw&—F–ær—2f–Æ&ÆRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%&r&æ¶–ær&Vv–öâ(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ¢ç6WE÷6—F—fT'WGFöâ‚%6Æ÷G2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7FF—F–öæÅ&V6÷&G2†&÷VæB’Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ&G7&GFÆUG&–æW$fÆr‡v÷&C¢Æöær“¢7G&–ærÒv†Vâ‡v÷&B’²ÂÓâ$6ÆV"#²ÂÓâ%6WB#²VÇ6RÓâ%Væ¶æ÷vâ‚Gv÷&B’"Ð ¢&—fFRgVâ6†÷t&G7&GFÆUG&–æW$f–ÇFW'2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7FGW2Ò&÷VæBæ&G7&GFÆUG&–æW%7FGW2ó¢&WGW&à¢fÂf–ÇFW'2Ò&G7&GFÆUG&–æW$f–ÇFW"çfÇVW2‚¢fÂÆ&VÇ2Òf–ÇFW'2æÖ²"G¶—BæF—7Æ”æÖWÒ(
"G·7FGW2æf–ÇFW&VB†—B’ç6—¦WÒ"Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"&GFÆR×G&–æW"7FGW2(
"VF—B"¢ç6WD—FV×2†Æ&VÇ2çFõG—VD'&’‚’’²òÂ–æFW‚Óâ–b‡6W76–öâÓÓÒ&÷VæB’f–ÇFW'2ævWD÷$çVÆÂ†–æFW‚“òæÆWB²6†÷t&G7&GFÆUG&–æW%vR†&÷VæBÂ—BÂ’ÒÐ¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&GFÆUG&–æW%vR†&÷VæC¢7v—F6…&VE6W76–öâÂf–ÇFW#¢&G7&GFÆUG&–æW$f–ÇFW"Â&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7FGW2Ò&÷VæBæ&G7&GFÆUG&–æW%7FGW2ó¢&WGW&à¢fÂVçG&–W2Ò7FGW2æf–ÇFW&VB†f–ÇFW"¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶f–ÇFW"æF—7Æ”æÖWÒ(
"G¶VçG&–W2ç6—¦WÒVçG&–W5Æåv–âfÆw26WC¢G·7FGW2çv–äfÆt6÷VçGÓ²&GFÆR×6V&6†W"fÆw26WC¢G·7FGW2æ&GFÆU6V&6†W$fÆt6÷VçGÓ²VçG&–W2v—F‚Væ¶æ÷vâfÆrv÷&G3¢G·7FGW2çVæ¶æ÷väVçG'”6÷VçGÕÆå6fVBG&–æW"–æF–6W27F’–âæF—fR÷&FW"âfÆw2Fòæ÷BW7F&Æ—6‚G&–æW"–FVçF—G’Â&VÖF6‚VÆ–v–&–Æ—G’÷"7F÷'’6öç6—7FVæ7’â ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æò6fVBVçG&–W2ÖF6‚F†—2f–ÇFW"â#²FW‡E6—¦RÒfbÒ¢VçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’æf÷$V6‚²VçG'’Óâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%G&–æW"–æFW‚G¶VçG'’æ–æFW‡Ò(
"v–ã¢G¶&G7&GFÆUG&–æW$fÆr†VçG'’ç&uv–åv÷&B—Ò(
"6V&6†W#¢G¶&G7&GFÆUG&–æW$fÆr†VçG'’ç&t&GFÆU6V&6†W%v÷&B—Ò ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUG&–æW$FWF–Â†&÷VæBÂVçG'’æ–æFW‚Âf–ÇFW"ÂvR’Ð¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$&GFÆR×G&–æW"7FGW2(
"G·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚$f–ÇFW'2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUG&–æW$f–ÇFW'2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUG&–æW%vR†&÷VæBÂf–ÇFW"ÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUG&–æW%vR†&÷VæBÂf–ÇFW"ÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&GFÆUG&–æW$FWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂf–ÇFW#¢&G7&GFÆUG&–æW$f–ÇFW"ÂvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ7FGW2Ò&÷VæBæ&G7&GFÆUG&–æW%7FGW2ó¢&WGW&à¢–b†–æFW‚–â7FGW2æVçG&–W2æ–æF–6W2’&WGW&à¢fÂVçG'’Ò7FGW2æVçG'’†–æFW‚¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·7FGW2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆå6fVBG&–æW"–æFW‚F–æFW…Æâ"°¢%v–âfÆs¢G¶&G7&GFÆUG&–æW$fÆr†VçG'’ç&uv–åv÷&B—ÕÆä&GFÆR×6V&6†W"fÆs¢G¶&G7&GFÆUG&–æW$fÆr†VçG'’ç&t&GFÆU6V&6†W%v÷&B—ÕÆåÆâ"°¢%&rv–âv÷&C¢G¶VçG'’ç&uv–åv÷&GÒ††W‚G¶VçG'’ç&uv–åv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ò•Æâ"°¢%&r&GFÆR×6V&6†W"v÷&C¢G¶VçG'’ç&t&GFÆU6V&6†W%v÷&GÒ††W‚G¶VçG'’ç&t&GFÆU6V&6†W%v÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ò•Æâ"°¢$æF—fR&ööÆVâ&VG2(
"v–ã¢G¶VçG'’æ—5v–çÓ²&GFÆR×6V&6†W#¢G¶VçG'’æ—4&GFÆU6V&6†W'ÕÆåÆâ"°¢%&ræF—fRVçG'’ƒ‚'—FW2“¥Æâ"²VçG'’ç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆäæF—fR&ööÆVç2&RG'VRöæÇ’v†VâF†R6ö×ÆWFRf÷W"Ö'—FRv÷&BWVÇ2â6†ævVBv÷&B×W7B&R6ÆV"÷"6WC²âVæ¶æ÷vâW†—7F–ærv÷&B&VÖ–ç2W†7Bv†VâVçF÷V6†VBâG&–æW"æÖW2ÂVæ6÷VçFW'2Â&VÖF6‚VÆ–v–&–Æ—G’æB7F÷'’&öw&W72&Ræ÷B–æfW'&VBâ4RVF—G2öæÇ’F†—26VÆV7FVBVçG'’æBFöW2æ÷B&÷f–FRFVfVBÖÆÂÂ&V&GFÆRÖÆÂ÷"&W6WB7F–öç2â ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BG&–æW"–æFW‚F–æFW‚#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7&GFÆUG&–æW%7FGW47F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ&G7æ&GFÆRçG&–æW"æ–æFW‚"Â–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%G&–æW"–æFW‚F–æFW‚(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚$VçG&–W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&GFÆUG&–æW%vR†&÷VæBÂf–ÇFW"ÂvR’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð  ¢&—fFRgVâ&G7†öæW•7&VDfÆr‡v÷&C¢Æöær“¢7G&–ærÒv†Vâ‡v÷&B’²ÂÓâ$6ÆV"#²ÂÓâ%6WB#²VÇ6RÓâ%Væ¶æ÷vâ‚Gv÷&B’"Ð ¢&—fFRgVâ6†÷t&G7†öæW•G&VW2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ†öæW’Ò&÷VæBæ&G7†öæW•G&VW2ó¢&WGW&à¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG¶†öæW’ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆå6fVBWFFRÖ–çWFW3¢G¶†öæW’æÆ7EWFFTÖ–çWFW7ÕÆå6fVBG&VRçVÖ&W#¢G¶†öæW’çG&VTçVÖ&W'ÕÆå7&VBfÆw26WC¢G¶†öæW’ç7&VDfÆt6÷VçGÓ²Væ¶æ÷vâ7&VBv÷&G3¢G¶†öæW’çVæ¶æ÷vå7&VEv÷&D6÷VçGÕÆåÆäÆÂ#6fVBG&VR–æF–6W2&R6†÷vâ–âæF—fR÷&FW"âÖ–çWFW2æB”G2&VÖ–âW†7BâF–ÖW"&VF–æW72Â6Æö6²FFW2ÂÆö6F–öç2æBVæ6÷VçFW"7V6–W2&Ræ÷B–æfW'&VBâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢†öæW’æVçG&–W2æf÷$V6‚²VçG'’Óâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%G&VR–æFW‚G¶VçG'’æ–æFW‡Ò(
"7&VC¢G¶&G7†öæW•7&VDfÆr†VçG'’ç&u7&VEv÷&B—Ò(
"Ö–çWFW3¢G¶VçG'’æÖ–çWFW7Ò ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7†öæW•G&VTFWF–Â†&÷VæBÂVçG'’æ–æFW‚’Ð¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$†öæW’G&VW2(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚%&r6V7F–öâ"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7†öæW•&r†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7†öæW•G&VTFWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ†öæW’Ò&÷VæBæ&G7†öæW•G&VW2ó¢&WGW&à¢–b†–æFW‚–â†öæW’æVçG&–W2æ–æF–6W2’&WGW&à¢fÂVçG'’Ò†öæW’æVçG'’†–æFW‚¢fÂFWF–ÂÒ%6fVBG&VR–æFW‚F–æFW…Æå7&VBfÆs¢G¶&G7†öæW•7&VDfÆr†VçG'’ç&u7&VEv÷&B—ÕÆâ"°¢%&r7&VBv÷&C¢G¶VçG'’ç&u7&VEv÷&GÒ††W‚G¶VçG'’ç&u7&VEv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ò•Æâ"°¢$æF—fR&ööÆVâ&VC¢G¶VçG'’æ—57&VGÕÆå6–væVB6fVBÖ–çWFW3¢G¶VçG'’æÖ–çWFW7ÕÆâ"°¢%6fVBVæ6÷VçFW"×F&ÆR”C¢G¶VçG'’çF&ÆT–GÕÆå6fVB&&—G’”C¢G¶VçG'’ç&&—G”–GÕÆå6fVB6†¶R”C¢G¶VçG'’ç6†¶T–GÕÆåÆâ"°¢%&ræF—fRVçG'’ƒ"'—FW2Â–æ6ÇVF–ærÆ–væÖVçB“¥Æâ"²VçG'’ç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆå7&VB—2G'VRöæÇ’v†VâF†R6ö×ÆWFRf÷W"Ö'—FRv÷&BWVÇ2âVæ¶æ÷vâv÷&G2æBÆÂÖ–çWFRô”BfÇVW2&VÖ–â&W6W'f&ÆRv†VâVçF÷V6†VBâF†W6Rf–VÆG2Fòæ÷BW7F&Æ—6‚F–ÖW"&VF–æW72ÂÆö6F–öâ÷"Væ6÷VçFW"7V6–W3²VF—F–ærFöW2æ÷BGfæ6RF–ÖW'2÷"&W—"–æfW'&VB7FFRâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Â²%ÆåÆåW6RVF—BG&VRFò&Wf–Wr†VFW"æB6VÆV7FVB×&V6÷&B6†ævW26fVBFòæWr6÷’âF†R÷F†W"#&V6÷&G2æB&WF–æVBFF–ær&VÖ–âW†7Bâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BG&VR–æFW‚F–æFW‚#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7†öæW•G&VT7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ†öæW’æ–æFW‚"Â–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%G&VR–æFW‚F–æFW‚(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚%G&VW2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7†öæW•G&VW2†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7†öæW•&r†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ†öæW’Ò&÷VæBæ&G7†öæW•G&VW2ó¢&WGW&à¢fÂFWF–ÂÒ%6fVB†öæW’G&VR6V7F–öâƒ#cB'—FW2“¥ÆåÆâ"²†öæW’ç&t†öæW”'—FW2æ6‡Væ¶VBƒ"’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäf—'7B"'—FW3¢6–væVBcBÖ&—BWFFRÖ–çWFW2Â6fVBG&VRÖçVÖ&W"'—FRæBF‡&VR&WF–æVBÆ–væÖVçB'—FW2â&VÖ–æ–ær#S"'—FW3¢#VçG&–W2öb"'—FW2–âæF—fR÷&FW"âWFFRÖ–çWFW2&Ræ÷B6öçfW'FVBFòFFS²æò6Æö6²Wö6‚—277VÖVBâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$†öæW’G&VW2(
"&r6V7F–öâ(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ¢ç6WE÷6—F—fT'WGFöâ‚%G&VW2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7†öæW•G&VW2†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ&G7Væ6÷VçFW$fÆr‡v÷&C¢Æöær“¢7G&–ærÒv†Vâ‡v÷&B’²ÂÓâ$6ÆV"#²ÂÓâ%6WB#²VÇ6RÓâ%Væ¶æ÷vâ‚Gv÷&B’"Ð ¢&—fFRgVâ6†÷t&G7Væ6÷VçFW$ÖVçR†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢–b†&÷VæBæ&G7Væ6÷VçFW"ÓÒçVÆÂ’&WGW&à¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"Væ6÷VçFW'2(
"÷fW'f–WrVF—B"¢ç6WD—FV×2†'&”öb‚%6fVBVæ6÷VçFW"÷fW'f–Wr(
"VF—B"Â%6fVB&F"&V6÷&G2(
"&VBÖöæÇ’"Â%6fVB&öÖW'2(
"&VBÖöæÇ’"Â%&rVæ6÷VçFW"6V7F–öâ(
"&VBÖöæÇ’"’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’v†Vâ†–æFW‚’°¢Óâ6†÷t&G7Væ6÷VçFW$÷fW'f–Wr†&÷VæB¢Óâ6†÷t&G7&F%&V6÷&G2†&÷VæB¢"Óâ6†÷t&G7&öÖW'2†&÷VæB¢2Óâ6†÷t&G7Væ6÷VçFW%&r†&÷VæB¢Ð¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7Væ6÷VçFW$÷fW'f–Wr†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVæ6÷VçFW"Ò&÷VæBæ&G7Væ6÷VçFW"ó¢&WGW&à¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG¶Væ6÷VçFW"ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢%6fVBvÆ²6÷VçC¢G¶Væ6÷VçFW"çvÆ´6÷VçGÕÆå6f&’6VVB…T–çC3"“¢G¶Væ6÷VçFW"ç6f&•6VVGÕÆävVæW&FR6VVB…T–çC3"“¢G¶Væ6÷VçFW"ævVæW&FU6VVGÕÆåÆâ"°¢$†–ÆÂFÆ²fÆs¢G¶&G7Væ6÷VçFW$fÆr†Væ6÷VçFW"ç&t†–ÆÅFÆµv÷&B—ÕÆå&r†–ÆÂFÆ²v÷&C¢G¶Væ6÷VçFW"ç&t†–ÆÅFÆµv÷&GÕÆäæF—fR&ööÆVã¢G¶Væ6÷VçFW"æ—4†–ÆÅFÆ·ÕÆâ"°¢$†–ÆÂVæ6÷VçFW"×F&ÆR”G3¢G¶Væ6÷VçFW"æ†–ÆÅF&ÆSÒÂG¶Væ6÷VçFW"æ†–ÆÅF&ÆS'ÕÆåÆâ"°¢%6fVB&Vf÷&R×¦öæR”C¢G¶Væ6÷VçFW"æ&Vf÷&U¦öæWÕÆå6fVBöÆB×¦öæR”C¢G¶Væ6÷VçFW"æöÆE¦öæWÕÆåÆâ"°¢$vVæW&FR×fÆ–BfÆs¢G¶&G7Væ6÷VçFW$fÆr†Væ6÷VçFW"ç&tvVæW&FUfÆ–Ev÷&B—ÕÆå&rvVæW&FR×fÆ–Bv÷&C¢G¶Væ6÷VçFW"ç&tvVæW&FUfÆ–Ev÷&GÕÆäæF—fR&ööÆVã¢G¶Væ6÷VçFW"æ—4vVæW&FUfÆ–GÕÆâ"°¢%6fVB7&’6÷VçB‡6–væVB“¢G¶Væ6÷VçFW"ç7&”6÷VçGÕÆå7&’G—R”C¢G¶Væ6÷VçFW"ç7&•G—WÕÆåg2â6VV¶W"6†&vS¢G¶Væ6÷VçFW"çg56VV¶W$6†&vWÕÆåö¼:’&F"6†&vS¢G¶Væ6÷VçFW"ç&F$6†&vWÕÆäfÇWFRG—R”C¢G¶Væ6÷VçFW"æfÇWFUG—WÕÆåÆâ"°¢%F†W6R&R6fVBfÇVW2âæVvF—fR6÷VçG2ÂVæ¶æ÷vâ”G2æB6†&vRfÇVW2&R&WF–æVBâæF—fR&ööÆVç2&RG'VRöæÇ’v†VâF†R6ö×ÆWFRf÷W"Ö'—FRv÷&BWVÇ2âæò7vâVÆ–v–&–Æ—G’ÂÖæÖW2ÂVæ6÷VçFW"÷WF6öÖW2÷"$är&VF–7F–öç2&R–æfW'&VBâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Â²%ÆåÆåW6RVF—B÷fW'f–WrFò&Wf–Wr—6öÆFVB66Æ"6†ævW26fVBFòæWr6÷’â†öæW’G&VW2Â&F"&V6÷&G2Â&öÖW'2ÂFF–ærÂæBVç&VÆFVB'—FW2&VÖ–âW†7Bâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BVæ6÷VçFW"÷fW'f–Wr#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7Væ6÷VçFW$÷fW'f–Wt7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$Væ6÷VçFW"÷fW'f–Wr(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚$Væ6÷VçFW'2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7Væ6÷VçFW$ÖVçR†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&F%&V6÷&G2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVæ6÷VçFW"Ò&÷VæBæ&G7Væ6÷VçFW"ó¢&WGW&à¢fÂFWF–ÂÒVæ6÷VçFW"ç&F%&V6÷&G2æ¦ö–åFõ7G&–ær‚%ÆåÆâ"’²&V6÷&BÓà¢%6fVB&F"&V6÷&BG·&V6÷&Bæ–æFW‡ÕÆå7V6–W2”B…T–çC3"“¢G·&V6÷&Bç7V6–W4–GÕÆä6†–â…T–çC3"“¢G·&V6÷&Bæ6†–çÕÆå&r‚'—FW3¢"²&V6÷&Bç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ð¢Ò²%ÆåÆäÆÂF‡&VR&V6÷&G2&VÖ–â–âæF—fR÷&FW"âçVÖW&–27V6–W2”G2æB6†–âv÷&G2&R&WF–æVBW†7FÇ“²F†W’Fòæ÷BW7F&Æ—6‚7W'&VçBVæ6÷VçFW"Â6†–âVÆ–v–&–Æ—G’÷"6‡&öæöÆöv–6Â÷&FW"â ¢6†÷t&G7Væ6÷VçFW%FW‡B†&÷VæBÂ%&F"&V6÷&G2(
"&VBÖöæÇ’"ÂFWF–Â¢Ð ¢&—fFRgVâ6†÷t&G7&öÖW'2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVæ6÷VçFW"Ò&÷VæBæ&G7Væ6÷VçFW"ó¢&WGW&à¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%6fVB&öÖW'2(
"&VBÖöæÇ’"¢ç6WD—FV×2†Væ6÷VçFW"ç&öÖW'2æÖ²%&öÖW"–æFW‚G¶—Bæ–æFW‡Ò(
"7V6–W2”BG¶—Bç7V6–W4–GÒ(
"¦öæR”BG¶—Bç¦öæT–GÒ"ÒçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&öÖW$FWF–Â†&÷VæBÂ–æFW‚¢Òç6WDæVvF—fT'WGFöâ‚$Væ6÷VçFW'2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7Væ6÷VçFW$ÖVçR†&÷VæB’Òç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&öÖW$FWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVæ6÷VçFW"Ò&÷VæBæ&G7Væ6÷VçFW"ó¢&WGW&à¢–b†–æFW‚–âVæ6÷VçFW"ç&öÖW'2æ–æF–6W2’&WGW&à¢fÂ&V6÷&BÒVæ6÷VçFW"ç&öÖW"†–æFW‚¢fÂFWF–ÂÒ%6fVB&öÖW"–æFW‚F–æFW…Æå¦öæR”B‡6–væVB“¢G·&V6÷&Bç¦öæT–GÕÆâ"°¢%6VVB…T–çCcB“¢G·&V6÷&Bç6VVGÕÆå6VVB†Wƒ¢G·&V6÷&Bç6VVBçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆâ"°¢%7V6–W2”B…T–çC3"“¢G·&V6÷&Bç7V6–W4–GÕÆä……T–çC3"“¢G·&V6÷&Bæ‡ÕÆå6fVBÆWfVÂ'—FS¢G·&V6÷&BæÆWfVÇÕÆâ"°¢%7FGW2v÷&B…T–çC3"“¢G·&V6÷&Bç7FGW5v÷&GÕÆå7FGW2†Wƒ¢G·&V6÷&Bç7FGW5v÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆâ"°¢$Væ6÷VçFW"'—FS¢G·&V6÷&BæVæ6÷VçFW$'—FWÕÆåÆå&ræF—fR&V6÷&Bƒ3"'—FW2Â–æ6ÇVF–ærÆ–væÖVçB“¥Æâ"²&V6÷&Bç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆå6fVB”G2Â6VVG2Â7FGW2v÷&G2æBVæ6÷VçFW"'—FW2&VÖ–âW†7BâæòwVW76VBÖæÖW2Â7V6–W2æÖW2Â7FGW2ÖVæ–æw2Â7F—fRöFVfVFVB7FFRÂ7vâVÆ–v–&–Æ—G’÷"Væ6÷VçFW"÷WF6öÖW2âæò$ärGfæ6RÂ&W6WBÂVF—F–ær÷"6fRw&—F–ær—2f–Æ&ÆRâ ¢6†÷t&G7Væ6÷VçFW%FW‡B†&÷VæBÂ%&öÖW"–æFW‚F–æFW‚(
"&VBÖöæÇ’"ÂFWF–Â¢Ð ¢&—fFRgVâ6†÷t&G7Væ6÷VçFW%&r†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂVæ6÷VçFW"Ò&÷VæBæ&G7Væ6÷VçFW"ó¢&WGW&à¢fÂFWF–ÂÒ%6fVBVæ6÷VçFW"6V7F–öâƒ3“"'—FW2Â–æ6ÇVF–ærÆ–væÖVçBæBF†R6W&FVÇ’'&÷w6&ÆR†öæW’G&VR6V7F–öâ“¥ÆåÆâ"°¢Væ6÷VçFW"ç&tVæ6÷VçFW$'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäÆÂ'—FW2&VÖ–âVæ6†ævVBâ†öæW’G&VRf–VÆG2&Rf–Æ&ÆRF‡&÷Vv‚F†RW†—7F–ær†öæW’G&VW2f–WrâæòFV6öF–ær÷"F—7Æ’7F–öâw&—FW2&6²FòF†R6fRâ ¢6†÷t&G7Væ6÷VçFW%FW‡B†&÷VæBÂ$Væ6÷VçFW"6V7F–öâ(
"&VBÖöæÇ’"ÂFWF–Â¢Ð ¢&—fFRgVâ6†÷t&G7Væ6÷VçFW%FW‡B†&÷VæC¢7v—F6…&VE6W76–öâÂF—FÆS¢7G&–ærÂFWF–Ã¢7G&–ær’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢–b†&÷VæBæ&G7Væ6÷VçFW"ÓÒçVÆÂ’&WGW&à¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‡F—FÆR’ç6WEf–Wr‡67&öÆÂ¢ç6WE÷6—F—fT'WGFöâ‚$Væ6÷VçFW'2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7Væ6÷VçFW$ÖVçR†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&W''•Æ÷G2†&÷VæC¢7v—F6…&VE6W76–öâÂ&WVW7FVEvS¢–çBÒ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&W'&–W2Ò&÷VæBæ&G7&W''•Æ÷G2ó¢&WGW&à¢fÂÆ7EvRÒ†&W'&–W2çÆ÷G2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò`¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG¶&W'&–W2ç&Wf—6–öâæF—7Æ”æÖWÕÆåÆå6fVBWFFRÖ–çWFW3¢G¶&W'&–W2æÆ7EWFFTÖ–çWFW7ÕÆåÆäÆÂ#‚6fVBÆ÷G2&VÖ–â–âæF—fR÷&FW"Â–æ6ÇVF–ær¦W&òÖf–ÆÆVB&V6÷&G2âÆ÷Bf–VÆG2&R6†÷vâ26fVB'—FW3²&W''’G—RÂw&÷wF‚7FFRÂ––VÆBæBÆö6F–öâ&Ræ÷BFV6öFVBâWFFRÖ–çWFW2&Ræ÷B6öçfW'FVBFòFFRâF†RFö7VÖVçFVB6–væVBF–Ö–ærf–VÆB6â&RVF—FVBv†–ÆRWfW'’Æ÷B&V6÷&B&VÖ–ç2W†7Bâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ.)ÈòVF—B6fVBWFFRÖ–çWFW2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7&W''•Æ÷EF–Ö–æt7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢&W'&–W2çÆ÷G2æG&÷‡vR¢b’çF¶Rƒb’æf÷$V6‚²Æ÷BÓâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%Æ÷B–æFW‚G·Æ÷Bæ–æFW‡ÕÆâ"²Æ÷Bç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ð¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&W''•Æ÷DFWF–Â†&÷VæBÂÆ÷Bæ–æFW‚ÂvR’Ð¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$&W''’Æ÷B&V6÷&G2(
"G·vR²ÒòG¶Æ7EvR²Ò(
"VF—BF–Ö–ær"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚%&r6V7F–öâ"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&W''•&r†&÷VæBÂvR’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&W''•Æ÷G2†&÷VæBÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&W''•Æ÷G2†&÷VæBÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&W''•Æ÷DFWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&W'&–W2Ò&÷VæBæ&G7&W''•Æ÷G2ó¢&WGW&à¢–b†–æFW‚–â&W'&–W2çÆ÷G2æ–æF–6W2’&WGW&à¢fÂÆ÷BÒ&W'&–W2çÆ÷B†–æFW‚¢fÂFWF–ÂÒ%6fVBÆ÷B–æFW‚F–æFW…ÆåÆå&ræF—fR&V6÷&Bƒb'—FW2“¥Æâ"°¢Æ÷Bç&t'—FW2æ6‡Væ¶VBƒ‚’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäÆÂ'—FW2&R&WF–æVBW†7FÇ’â–æF—f–GVÂf–VÆBFVf–æ—F–öç2&Ræ÷B–WBfW&–f–VBâæò&W''’G—RÂÆçF–æröw&÷wF‚7FFRÂ&VF–æW72Â––VÆBÂvFW&–ær7FFR÷"Æö6F–öâ—2–æfW'&VBÂ–æ6ÇVF–ærf÷"¦W&òÖf–ÆÆVB&V6÷&G2âæòÆçF–ærÂ†'fW7F–ærÂF–ÖW"Gfæ6VÖVçBÂVF—F–ær÷"6fRw&—F–ær—2f–Æ&ÆRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%Æ÷B–æFW‚F–æFW‚(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ¢ç6WE÷6—F—fT'WGFöâ‚%Æ÷G2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&W''•Æ÷G2†&÷VæBÂvR’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&W''•&r†&÷VæC¢7v—F6…&VE6W76–öâÂvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&W'&–W2Ò&÷VæBæ&G7&W''•Æ÷G2ó¢&WGW&à¢fÂFWF–ÂÒ%6fVB&W''’6V7F–öâƒ"ÃSb'—FW2“¥ÆåÆâ"°¢&W'&–W2ç&t&W''”'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäf—'7B"ÃC‚'—FW3¢#‚Æ÷B&V6÷&G2öbb'—FW2âf–æÂ‚'—FW3¢6–væVB6fVBWFFRÖ–çWFW2‚G¶&W'&–W2æÆ7EWFFTÖ–çWFW7Ò’âæò6Æö6²Wö6‚Â6ÆVæF"FFR÷"w&÷wF‚&VF–æW72—277VÖVBâæòFV6öF–ær÷"F—7Æ’7F–öâw&—FW2&6²FòF†R6fRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$&W''’6V7F–öâ(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ¢ç6WE÷6—F—fT'WGFöâ‚%Æ÷G2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&W''•Æ÷G2†&÷VæBÂvR’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G76V7&WD&6R†&÷VæC¢7v—F6…&VE6W76–öâÂ&WVW7FVEvS¢–çBÒ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&6RÒ&÷VæBæ&G76V7&WD&6Ró¢&WGW&à¢fÂÆ7EvRÒ†&6Rç7FGVW2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG¶&6Rç&Wf—6–öâæF—7Æ”æÖWÕÆåÆå6fVBFÆ²×Æ–W"FF”C¢G¶&6RçFÆµÆ–W$FF–GÕÆå6fVBFÆ²×Æ–W"6÷VçC¢G¶&6RçFÆµÆ–W$6÷VçGÕÆâ"°¢$&6R¦öæR”B‡6–væVB“¢G¶&6Rç¦öæT–GÕÆå6fVB÷6—F–öâ‚õ’‡6–væVB“¢G¶&6Rç‡ÒÂG¶&6Rç—ÕÆäF—&V7F–öâ'—FS¢G¶&6RæF—&V7F–öçÕÆäW‡ç6–öâ×7FGW2'—FS¢G¶&6RæW‡ç6–öå7FGW7ÕÆå6fVBvööB6÷VçB‡6–væVB“¢G¶&6RævööD6÷VçGÕÆâ"°¢%&rVæ&ÆRöÆ–væÖVçB'—FW3¢"²&6Rç&tVæ&ÆT'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆäÆÂ36fVB7FGVR&V6÷&G2&VÖ–â–âæF—fR÷&FW"Â&Vv&FÆW72öbF†R6fVB6÷VçBâ¦W&ò÷"æVvF—fR”G2&Ræ÷BW6VBFò–æfW"V×G’6Æ÷G2âæÖW2ÂVffV7G2ÂVæÆö6·2æBÆ–÷WBÆVvÆ—G’&Ræ÷B–æfW'&VBâVæ&ÆRÖ'—FR6VÖçF–72&Ræ÷BFV6öFVBâ6VÆV7B7FGVRFò&Wf–WræF—fR†VFW"æB&V6÷&BVF—G2â ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢&6Rç7FGVW2æG&÷‡vR¢’çF¶Rƒ’æf÷$V6‚²7FGVRÓâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%7FGVR–æFW‚G·7FGVRæ–æFW‡Ò(
"”BG·7FGVRç7FGVT–GÒ(
"VFW7FÂG·7FGVRçVFW7FÄ–GÕÆå‚õ“¢G·7FGVRç‡ÒÂG·7FGVRç—Ò(
"F—&V7F–öã¢G·7FGVRæF—&V7F–öçÒ ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G77FGVTFWF–Â†&÷VæBÂ7FGVRæ–æFW‚ÂvR’Ð¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%6V7&WB&6R(
"G·vR²ÒòG¶Æ7EvR²Ò(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚%&r&V6÷&B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76V7&WD&6U&r†&÷VæBÂvR’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76V7&WD&6R†&÷VæBÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76V7&WD&6R†&÷VæBÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G77FGVTFWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&6RÒ&÷VæBæ&G76V7&WD&6Ró¢&WGW&à¢–b†–æFW‚–â&6Rç7FGVW2æ–æF–6W2’&WGW&à¢fÂ7FGVRÒ&6Rç7FGVR†–æFW‚¢fÂFWF–ÂÒ%6fVB7FGVR–æFW‚F–æFW…Æå7FGVR”B‡6–væVB“¢G·7FGVRç7FGVT–GÕÆåVFW7FÂ”B‡6–væVB“¢G·7FGVRçVFW7FÄ–GÕÆâ"°¢%6fVB‚õ’‡6–væVB“¢G·7FGVRç‡ÒÂG·7FGVRç—ÕÆå6fVBF—&V7F–öâ‡6–væVB“¢G·7FGVRæF—&V7F–öçÕÆåÆå&ræF—fR&V6÷&Bƒ#'—FW2“¥Æâ"°¢7FGVRç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆäÆÂfÇVW2&VÖ–âW†7BÂ–æ6ÇVF–æræVvF—fR÷"Væ¶æ÷vâ”G2Â÷6—F–öç2æBF—&V7F–öç2âæòwVW76VB7FGVR÷VFW7FÂæÖW2ÂG—RVffV7G2ÂVæÆö6·2Âö67W–VB7FFRÂÆ6VÖVçBfÆ–F—G’÷"&6RÆVvÆ—G’â ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Â²%ÆåÆåW6RVF—B7FGVRFò&Wf–WrFö7VÖVçFVB6V7&WB&6R†VFW"æB6VÆV7FVB×&V6÷&B6†ævW26fVBFòæWr6÷’âF†RVæ&ÆRöÆ–væÖVçBv÷&BæBF†R÷F†W"#’&V6÷&G2&VÖ–âW†7Bâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B7FGVR–æFW‚F–æFW‚#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G76V7&WD&6T7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚ç6V7&WF&6Rç7FGVRæ–æFW‚"Â–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%7FGVR–æFW‚F–æFW‚(
"VF—B"’ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚$&6R"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76V7&WD&6R†&÷VæBÂvR’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G76V7&WD&6U&r†&÷VæC¢7v—F6…&VE6W76–öâÂvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&6RÒ&÷VæBæ&G76V7&WD&6Ró¢&WGW&à¢fÂFWF–ÂÒ%6fVB6V7&WB&6R&V6÷&Bƒc#B'—FW2“¥ÆåÆâ"°¢&6Rç&t&6T'—FW2æ6‡Væ¶VBƒ#’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäf—'7B#'—FW3¢6fVB†VFW"âæW‡Bc'—FW3¢37FGVR&V6÷&G2öb#'—FW2âf–æÂf÷W"'—FW3¢Væ&ÆRöÆ–væÖVçBFF&WF–æVBv—F†÷WBfÆrFV6öF–ærâæòF—7Æ’7F–öâw&—FW2&6²FòF†R6fRâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%6V7&WB&6R&r&V6÷&B(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ¢ç6WE÷6—F—fT'WGFöâ‚$&6R"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76V7&WD&6R†&÷VæBÂvR’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæDç4f–ÇFW'2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7VæFW&w&÷VæDç2ó¢&WGW&à¢fÂf–ÇFW'2Ò&G7VæFW&w&÷VæDç4f–ÇFW"çfÇVW2‚¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"å2&V6÷&G2(
"VF—B"¢ç6WD—FV×2†f–ÇFW'2æÖ²"G¶—BæF—7Æ”æÖWÒ(
"G·&V6÷&G2æf–ÇFW&VB†—B’ç6—¦WÒ"ÒçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’f–ÇFW'2ævWD÷$çVÆÂ†–æFW‚“òæÆWB²6†÷t&G7VæFW&w&÷VæDç5vR†&÷VæBÂ—BÂ’Ð¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæDç5vR†&÷VæC¢7v—F6…&VE6W76–öâÂf–ÇFW#¢&G7VæFW&w&÷VæDç4f–ÇFW"Â&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7VæFW&w&÷VæDç2ó¢&WGW&à¢fÂVçG&–W2Ò&V6÷&G2æf–ÇFW&VB†f–ÇFW"¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶f–ÇFW"æF—7Æ”æÖWÒ(
"G¶VçG&–W2ç6—¦WÒVçG&–W5ÆäæF—fR&V6÷&FVBÖVçG'’6÷VçC¢G·&V6÷&G2ç&V6÷&FVDVçG'”6÷VçGÒóÆåÆåF†RæF—fR6÷VçB–æ6ÇVFW2WfW'’æöç¦W&ò'—FRÂ–æ6ÇVF–ær&WVFVBfÇVW2âF†—2—2æ÷B6÷VçBöbVæ—VRå72âVçG&–W2&WF–âF†V—"6fVB–æF–6W2âå2–FVçF—F–W2Â7—&—FöÖ"f–Æ&–Æ—G’æBVW7B6ö×ÆWF–öâ&Ræ÷B–æfW'&VBâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æò6fVBVçG&–W2ÖF6‚F†—2f–ÇFW"â#²FW‡E6—¦RÒfbÒ¢VçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’æf÷$V6‚²VçG'’Óâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%6fVB–æFW‚G¶VçG'’æ–æFW‡Ò(
"'—FRG¶VçG'’çfÇVWÒ(
"†W‚G¶VçG'’çfÇVRçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ"Âsr—Ò ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæDç4FWF–Â†&÷VæBÂVçG'’æ–æFW‚Âf–ÇFW"ÂvR’Ð¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%VæFW&w&÷VæBå2&V6÷&G2(
"G·vR²ÒòG¶Æ7EvR²Ò"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚$f–ÇFW'2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæDç4f–ÇFW'2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæDç5vR†&÷VæBÂf–ÇFW"ÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæDç5vR†&÷VæBÂf–ÇFW"ÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæDç4FWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂf–ÇFW#¢&G7VæFW&w&÷VæDç4f–ÇFW"ÂvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&V6÷&G2Ò&÷VæBæ&G7VæFW&w&÷VæDç2ó¢&WGW&à¢–b†–æFW‚–â&V6÷&G2æVçG&–W2æ–æF–6W2’&WGW&à¢fÂVçG'’Ò&V6÷&G2æVçG'’†–æFW‚¢fÂFWF–ÂÒ%6fVB–çFW&7F–öâ–æFW‚F–æFW…ÆäW†7BVç6–væVB'—FS¢G¶VçG'’çfÇVWÕÆä†Wƒ¢G¶VçG'’çfÇVRçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ"Âsr—ÕÆâ"°¢$6öçG&–'WFW2FòF†RæF—fRæöç¦W&òÖVçG'’6÷VçC¢G¶VçG'’çfÇVRÒÕÆåÆâ"°¢$gVÆÂ6fVBå26V7F–öâƒ'—FW2ÂæF—fR÷&FW"“¥Æâ"²&V6÷&G2ç&tç4'—FW2æ6‡Væ¶VBƒ#’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆå&WVFVBfÇVW2&R&WF–æVBæBV6‚æöç¦W&òVçG'’6÷VçG26W&FVÇ’âæòwVW76VBå2æÖRÂVæ—VRÖ–çFW&7F–öâ6÷VçBÂ7—&—FöÖ"VÆ–v–&–Æ—G’÷"VW7B7FFRâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Â²%ÆåÆåW6RVF—B&V6÷&BFò&Wf–WrF†—2W†7B6fVB'—FRæBw&—FRfW&–f–VBæWr6÷’âF†R÷F†W"“’VçG&–W2&VÖ–âW†7Bâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—Bå2&V6÷&BF–æFW‚#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7VæFW&w&÷VæDç47F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚çVæFW&w&÷VæBæç2æ–æFW‚"Â–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$å2&V6÷&B–æFW‚F–æFW‚(
"VF—B"’ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚$VçG&–W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæDç5vR†&÷VæBÂf–ÇFW"ÂvR’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ&G7&WGW&äfÆöB†&—G3¢Æöær“¢7G&–ærÒ"G´fÆöBæg&öÔ&—G2†&—G2çFô–çB‚’—Ò(
"&r†W‚G¶&—G2çFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ò  ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæE&WGW&â†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ6fVBÒ&÷VæBæ&G7VæFW&w&÷VæE&WGW&âó¢&WGW&à¢fÂFWF–ÂÒ"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·6fVBç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"°¢%6fVB&WGW&â¦öæR”C¢G·6fVBç&WGW&å¦öæT–GÕÆå6fVB&WGW&âw&–B‚õ’õ£¢G·6fVBæw&–E‡ÒÂG·6fVBæw&–E—ÒÂG·6fVBæw&–E§ÕÆåÆâ"°¢%6fVB¦VæÖWG7R¦öæR”C¢G·6fVBç¦VæÖWG7U¦öæT–GÕÆå¦VæÖWG7R÷6—F–öâƒ¢G¶&G7&WGW&äfÆöB‡6fVBç&u¦VæÖWG7U„&—G2—ÕÆâ"°¢%¦VæÖWG7R÷6—F–öâ“¢G¶&G7&WGW&äfÆöB‡6fVBç&u¦VæÖWG7U”&—G2—ÕÆå¦VæÖWG7R÷6—F–öâ£¢G¶&G7&WGW&äfÆöB‡6fVBç&u¦VæÖWG7U¤&—G2—ÕÆâ"°¢%6fVB¦VæÖWG7RF—&V7F–öã¢G·6fVBç¦VæÖWG7TF—&V7F–öçÕÆå6fVBVæFW&w&÷VæB&WGW&â¦öæR”C¢G·6fVBç&WGW&åVu¦öæT–GÕÆåÆâ"°¢$ÆÂ–çFVvW"f–VÆG2&VÖ–â6–væVBæBW†7BâfÆöC3"F—7Æ’&WF–ç2&r&—G2Â–æ6ÇVF–ær6–væVB¦W&òÂ7V&æ÷&ÖÂfÇVW2Â–æf–æ—F–W2æBæâ–ÆöG2â&r†W‚—2WF†÷&—FF—fRf÷"æâ–ÆöG2âæöâÖf–æ—FRfÇVW2&R6†÷vâv—F†÷WBæ÷&ÖÆ—¦F–öâåÆåÆâ"°¢%F†W6R&R6fVB&WGW&âÖÆö6F–öâf–VÆG2Âæ÷B7W'&VçBÖÆö6F–öâÖâ¦öæRæÖW2Â6ö÷&F–æFR7—7FV×2ÂF—&V7F–öâæÖW2Â7vâfÆ–F—G’æBFVÆW÷'BFW7F–æF–öç2&Ræ÷B–æfW'&VBâVF—F–ær6†ævW2W†7BæF—fRfÇVW2æBFöW2æ÷BW&f÷&ÒFVÆW÷'F–ærÂÖ÷fVÖVçB÷"&W—"â ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Â²%ÆåÆåW6RVF—B&WGW&âFFFò&Wf–WrW†7Bf–VÆB6†ævW2æBw&—FRfW&–f–VBæWr6÷’â ¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B&WGW&âFF#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7VæFW&w&÷VæE&WGW&ä7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%VæFW&w&÷VæB&WGW&âFF(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚%&rf–VÆG2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæE&WGW&å&r†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7VæFW&w&÷VæE&WGW&å&r†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ6fVBÒ&÷VæBæ&G7VæFW&w&÷VæE&WGW&âó¢&WGW&à¢fÂFWF–ÂÒ%6fVB&WGW&â†VFW"ƒ3b'—FW2“¥ÆåÆâ"°¢6fVBç&u&WGW&ä'—FW2æ6‡Væ¶VBƒB’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆå6fVBVæFW&w&÷VæB&WGW&â¦öæRƒB6W&FR'—FW2“¥Æâ"²6fVBç&uVæFW&w&÷VæE¦öæT'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆåF†Rf–VÆG2ö67W’6W&FRæF—fR&Vv–öç2âF–rö–çG2ÂVæFW&w&÷VæBVæ6÷VçFW"ö¼:–Ööâ÷÷6—F–öç2æB6V7&WB&6RFF&RW†6ÇVFVBg&öÒF†—2f–WrâÆÂC6fVB'—FW2&VÖ–âVæ6†ævVBâ ¢fÂ67&öÆÂÒ67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%&WGW&âFF&rf–VÆG2(
"&VBÖöæÇ’"’ç6WEf–Wr‡67&öÆÂ¢ç6WE÷6—F—fT'WGFöâ‚%&WGW&âFF"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7VæFW&w&÷VæE&WGW&â†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ&G76VÄö'F–æVDfÆr‡v÷&C¢Æöær“¢7G&–ærÒv†Vâ‡v÷&B’²ÂÓâ$6ÆV"#²ÂÓâ%6WB#²VÇ6RÓâ%Væ¶æ÷vâ‚Gv÷&B’"Ð ¢&—fFRgVâ6†÷t&G76VÅ7F–6¶W$f–ÇFW'2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ6VÇ2Ò&÷VæBæ&G76VÅ7F–6¶W'2ó¢&WGW&à¢fÂf–ÇFW'2Ò&G76VÅ7F–6¶W$f–ÇFW"çfÇVW2‚¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"6VÂ7F–6¶W'2(
"VF—B"¢ç6WD—FV×2†f–ÇFW'2æÖ²"G¶—BæF—7Æ”æÖWÒ(
"G·6VÇ2æf–ÇFW&VB†—B’ç6—¦WÒ"ÒçFõG—VD'&’‚’’²òÂ–æFW‚Óà¢–b‡6W76–öâÓÓÒ&÷VæB’f–ÇFW'2ævWD÷$çVÆÂ†–æFW‚“òæÆWB²6†÷t&G76VÅ7F–6¶W%vR†&÷VæBÂ—BÂ’Ð¢Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G76VÅ7F–6¶W%vR†&÷VæC¢7v—F6…&VE6W76–öâÂf–ÇFW#¢&G76VÅ7F–6¶W$f–ÇFW"Â&WVW7FVEvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ6VÇ2Ò&÷VæBæ&G76VÅ7F–6¶W'2ó¢&WGW&à¢fÂVçG&–W2Ò6VÇ2æf–ÇFW&VB†f–ÇFW"¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò# ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ"G¶f–ÇFW"æF—7Æ”æÖWÒ(
"G¶VçG&–W2ç6—¦WÒVçG&–W5Æå6fVBö'F–æVBfÆw26WC¢G·6VÇ2æö'F–æVDfÆt6÷VçGÒó#ÆåÆäæF—fR6FÆöwVR¶W—2–FVçF–g’¶æ÷vâ”G2âVæ¶æ÷vâ”G2æBÆÂ6–væVB6÷VçBõF÷FÄ6÷VçBfÇVW2&VÖ–âW†7BÂ–æ6ÇVF–æræVvF—fRfÇVW2æBfÇVW2&÷fR“’âfÆw2æB6÷VçG2Fòæ÷BW7F&Æ—6‚VæÆö6·2÷"&ÆÂ67VÆRÆ6VÖVçBâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æò6fVBVçG&–W2ÖF6‚F†—2f–ÇFW"â#²FW‡E6—¦RÒfbÒ¢VçG&–W2æG&÷‡vR¢#’çF¶Rƒ#’æf÷$V6‚²VçG'’Óâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$”BG¶VçG'’æ–æFW‡Ò(
"G¶VçG'’ææF—fTÆ&VÂó¢%VæÆ—7FVB6VÂ'ÕÆä6÷VçBG¶VçG'’æ6÷VçGÒ(
"F÷FÂG¶VçG'’çF÷FÄ6÷VçGÒ(
"ö'F–æVC¢G¶&G76VÄö'F–æVDfÆr†VçG'’ç&tö'F–æVEv÷&B—Ò ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76VÅ7F–6¶W$FWF–Â†&÷VæBÂVçG'’æ–æFW‚Âf–ÇFW"ÂvR’Ð¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%6VÂ7F–6¶W'2(
"G·vR²ÒòG¶Æ7EvR²Ò(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚$f–ÇFW'2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76VÅ7F–6¶W$f–ÇFW'2†&÷VæB’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76VÅ7F–6¶W%vR†&÷VæBÂf–ÇFW"ÂvRÒ’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76VÅ7F–6¶W%vR†&÷VæBÂf–ÇFW"ÂvR²’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G76VÅ7F–6¶W$FWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂf–ÇFW#¢&G76VÅ7F–6¶W$f–ÇFW"ÂvS¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ6VÇ2Ò&÷VæBæ&G76VÅ7F–6¶W'2ó¢&WGW&à¢–b†–æFW‚–â6VÇ2æVçG&–W2æ–æF–6W2’&WGW&à¢fÂVçG'’Ò6VÇ2æVçG'’†–æFW‚¢fÂFWF–ÂÒ%6fVB6VÂ”Bö–æFW‚F–æFW…ÆäæF—fR6FÆöwVR¶W“¢G¶VçG'’ææF—fTÆ&VÂó¢%VæÆ—7FVB”B'ÕÆâ"°¢%6–væVB6÷VçC¢G¶VçG'’æ6÷VçGÕÆå6–væVBF÷FÄ6÷VçC¢G¶VçG'’çF÷FÄ6÷VçGÕÆå6fVBö'F–æVBfÆs¢G¶&G76VÄö'F–æVDfÆr†VçG'’ç&tö'F–æVEv÷&B—ÕÆâ"°¢%&rö'F–æVBv÷&C¢G¶VçG'’ç&tö'F–æVEv÷&GÒ††W‚G¶VçG'’ç&tö'F–æVEv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ò•ÆäæF—fR&ööÆVâ&VC¢G¶VçG'’æ—4vWGÕÆåÆâ"°¢%&ræF—fR&V6÷&Bƒ"'—FW2“¥Æâ"²VçG'’ç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆäæF—fRö'F–æVB—2G'VRöæÇ’v†VâF†R6ö×ÆWFRf÷W"Ö'—FRv÷&BWVÇ2âVæ¶æ÷vâv÷&G2Â¦W&òöæVvF—fR6÷VçG2Â6÷VçG2&÷fRF†R&VfW&Væ6RÖ†–×VÒöb“’æBVæÆ—7FVB”G2&VÖ–â&W6W'f&ÆRv†VâVçF÷V6†VBâVF—F–ær6†ævW2öæÇ’6VÆV7FVBf–VÆG2–âF†—2&V6÷&BæBF†RæF—fRv†öÆRÖf–ÆRÔCRâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B6VÂ7F–6¶W"”BF–æFW‚#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G76VÅ7F–6¶W$7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚ç6VÂæ–æFW‚"Â–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%6VÂ”BF–æFW‚(
"VF—B"’ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚$VçG&–W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76VÅ7F–6¶W%vR†&÷VæBÂf–ÇFW"ÂvR’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&ÆÄ67VÆW2†&÷VæC¢7v—F6…&VE6W76–öâÂ&WVW7FVEvS¢–çBÂæöç¦W&ôöæÇ“¢&ööÆVâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G7&ÆÄ67VÆW2ó¢&WGW&à¢fÂVçG&–W2Ò&VBæf–ÇFW&VB†æöç¦W&ôöæÇ’¢fÂÆ7EvRÒ†VçG&–W2ç6—¦RÒ’æ6öW&6TDÆV7Bƒ’ò ¢fÂvRÒ&WVW7FVEvRæ6öW&6T–âƒÂÆ7EvR¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ%6fVB67VÆR6÷VçB'—FS¢G·&VBç6fVD67VÆT6÷VçGÒ"²†–b‡&VBæ6÷VçEv—F†–ä66—G’’""VÇ6R"(
"W†6VVG2æF—fR66—G’““²&WF–æVB"’°¢%ÆâG¶–b†æöç¦W&ôöæÇ’’$æöç¦W&ò&r&V6÷&G2"VÇ6R$ÆÂ6fVB&V6÷&G2'Ó¢G¶VçG&–W2ç6—¦WÒó“•Æå&rf÷W"Ö'—FR†VFW#¢"°¢&VBç&t†VFW$'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆäÆÂ“’&V6÷&G2&VÖ–âf–Æ&ÆR&Vv&FÆW72öbF†R6fVB6÷VçBâæöç¦W&ò'—FW2Fòæ÷BW7F&Æ—6‚â7F—fR÷"VæÆö6¶VB67VÆRâ6fVB7V6–W2öVæ7'—F–öâv÷&G2Fòæ÷B&÷fRâWV—VBö¼:–Ööââ6VÂ”G2æB6–væVB6ö÷&F–æFW2&R6†÷vâW†7FÇ“²æò6ö÷&F–æFR7—7FVÒÂ&÷VæG2÷"VffV7G2&R–æfW'&VBâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b†VçG&–W2æ—4V×G’‚’’&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’²FW‡BÒ$æò&V6÷&G2†fRæöç¦W&ò'—FW2â#²FW‡E6—¦RÒfbÒ¢VçG&–W2æG&÷‡vR¢’çF¶Rƒ’æf÷$V6‚²67VÆRÓâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$67VÆRG¶67VÆRæ–æFW‚²Ò(
"6fVB–æFW‚G¶67VÆRæ–æFW‡ÕÆå7V6–W2”BG¶67VÆRç7V6–W4–GÒ(
"T2G¶67VÆRæVæ7'—F–öä6öç7FçBçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆã#6fVBff—‚&V6÷&G2 ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&ÆÄ67VÆTFWF–Â†&÷VæBÂ67VÆRæ–æFW‚ÂvRÂæöç¦W&ôöæÇ’’Ð¢Ò’Ð¢fÂ'V–ÆFW"ÒÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"&ÆÂ67VÆW2(
"G·vR²ÒòG¶Æ7EvR²Ò(
"ff—‚VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ†–b†æöç¦W&ôöæÇ’’%6†÷rÆÂ"VÇ6R$æöç¦W&ò&V6÷&G2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&ÆÄ67VÆW2†&÷VæBÂÂæöç¦W&ôöæÇ’’Ð¢–b‡vRâ’'V–ÆFW"ç6WDæVvF—fT'WGFöâ‚%&Wf–÷W2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&ÆÄ67VÆW2†&÷VæBÂvRÒÂæöç¦W&ôöæÇ’’Ð¢–b‡vRÂÆ7EvR’'V–ÆFW"ç6WDæWWG&Ä'WGFöâ‚$æW‡B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&ÆÄ67VÆW2†&÷VæBÂvR²Âæöç¦W&ôöæÇ’’Ð¢'V–ÆFW"ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&ÆÄ67VÆTFWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂvS¢–çBÂæöç¦W&ôöæÇ“¢&ööÆVâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G7&ÆÄ67VÆW2ó¢&WGW&à¢–b†–æFW‚–â&VBæ67VÆW2æ–æF–6W2’&WGW&à¢fÂ67VÆRÒ&VBæ67VÆR†–æFW‚¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ%6fVB67VÆR–æFW‚F–æFW…Æå7V6–W2”B…T–çC3"“¢G¶67VÆRç7V6–W4–GÕÆäVæ7'—F–öâ6öç7FçC¢G¶67VÆRæVæ7'—F–öä6öç7FçGÒ††W‚G¶67VÆRæVæ7'—F–öä6öç7FçBçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—Ò•ÆåVæ¶æ÷vâv÷&B…T–çC3"“¢G¶67VÆRçVæ¶æ÷våv÷&GÕÆåÆäÆÂ#6fVBff—‚6Æ÷G2&R&WF–æVBÂ–æ6ÇVF–ær¦W&òæBVæÆ—7FVB”G2âF†W6Rf–VÆG2Fòæ÷B–FVçF–g’fW&–f–VBWV—VBö¼:–ÖöâÂfÆ–BÆ6VÖVçBÂVæÆö6²÷"VffV7Bâ ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢67VÆRç6VÇ2æf÷$V6‚²6VÂÓâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ$ff—‚G·6VÂæ–æFW‚²Ò(
"”BG·6VÂç6VÄ–GÒ(
"G·6VÂææF—fTÆ&VÂó¢%VæÆ—7FVB”B'ÕÆå6–væVB‚G·6VÂç‡Ò(
"’G·6VÂç—Ò(
"¢G·6VÂç§Ò ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7ff—†VE6VÄFWF–Â†&÷VæBÂ–æFW‚Â6VÂæ–æFW‚ÂvRÂæöç¦W&ôöæÇ’’Ð¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$67VÆRG¶–æFW‚²Ò(
"ff—‚VF—B"’ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò¢ç6WE÷6—F—fT'WGFöâ‚$67VÆW2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&ÆÄ67VÆW2†&÷VæBÂvRÂæöç¦W&ôöæÇ’’Ð¢ç6WDæWWG&Ä'WGFöâ‚%&r&V6÷&B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&ÆÄ67VÆU&r†&÷VæBÂ–æFW‚ÂvRÂæöç¦W&ôöæÇ’’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7ff—†VE6VÄFWF–Â†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂ6VÄ–æFWƒ¢–çBÂvS¢–çBÂæöç¦W&ôöæÇ“¢&ööÆVâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G7&ÆÄ67VÆW2ó¢&WGW&à¢–b†–æFW‚–â&VBæ67VÆW2æ–æF–6W2ÇÂ6VÄ–æFW‚–âVçF–Â#’&WGW&à¢fÂ6VÂÒ&VBæ67VÆR†–æFW‚’ç6VÂ‡6VÄ–æFW‚¢fÂFWF–ÂÒ%6fVB67VÆR–æFW‚F–æFW‚(
"ff—‚–æFW‚G6VÄ–æFW…ÆåVç6–væVB6VÂ”C¢G·6VÂç6VÄ–GÕÆäæF—fR¶W“¢G·6VÂææF—fTÆ&VÂó¢%VæÆ—7FVB”B'ÕÆå6–væVB–çCbƒ¢G·6VÂç‡ÕÆå6–væVB–çCb“¢G·6VÂç—ÕÆå6–væVB–çCb£¢G·6VÂç§ÕÆåÆå&rV–v‡BÖ'—FRff—‚&V6÷&C¥Æâ"°¢6VÂç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆä6ö÷&F–æFW2æBVæ¶æ÷vâ”G2&VÖ–â&W6W'f&ÆRv†VâVçF÷V6†VBâVF—F–ær6†ævW2öæÇ’6VÆV7FVBf–VÆG2–âF†—2ff—‚&V6÷&BæBF†RæF—fRv†öÆRÖf–ÆRÔCS²67VÆR–FVçF—G’v÷&G2æBÆÂ÷F†W"ff—†W2&VÖ–âW†7Bâ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—Bff—‚G·6VÄ–æFW‚²Ò#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7&ÆÄ67VÆTff—„7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ67VÆRæ–æFW‚"Â–æFW‚¢çWDW‡G&‚'6Rç7v—F6‚æ67VÆRæff—‚"Â6VÄ–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$ff—‚G·6VÄ–æFW‚²Ò(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚$67VÆR"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&ÆÄ67VÆTFWF–Â†&÷VæBÂ–æFW‚ÂvRÂæöç¦W&ôöæÇ’’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&ÆÄ67VÆU&r†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çBÂvS¢–çBÂæöç¦W&ôöæÇ“¢&ööÆVâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G7&ÆÄ67VÆW2ó¢&WGW&à¢–b†–æFW‚–â&VBæ67VÆW2æ–æF–6W2’&WGW&à¢fÂ&rÒ&VBæ67VÆR†–æFW‚’ç&t'—FW0¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$67VÆRG¶–æFW‚²Ò(
"s"&r'—FW2(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ&ræ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÐ¢FW‡E6—¦RÒfc²6WEFF–ærƒ#BÂbÂ#BÂb“²6WEFW‡D—56VÆV7F&ÆR‡G'VR¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$67VÆR"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&ÆÄ67VÆTFWF–Â†&÷VæBÂ–æFW‚ÂvRÂæöç¦W&ôöæÇ’’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G76öçFW7B†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G76öçFW7Bó¢&WGW&à¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ%6fVB6öçFW7B&æ²ö–çG3¢G·&VBç&æµö–çG7ÕÆäæF—fRT–çC3"†Wƒ¢G·&VBç&æµö–çG2çFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr—ÕÆäf—fR6fVB†÷Fò&V6÷&G2&R&WF–æVB2&ræF—fRFFâF†R&r&Æö6·2Fòæ÷B&÷fR†÷Fò6öçFVçG2Â6öçFW7BVÆ–v–&–Æ—G’÷"v&G2â&æ²ö–çG26â&RVF—FVB–âfW&–f–VBæWr6÷“²†÷Fò&V6÷&G2&VÖ–â&VBÖöæÇ’&V6W6RF†V—"6ö×ÆWFRæF—fRVF—F–ær6VÖçF–72&Ræ÷BW7F&Æ—6†VBâ"°¢%ÆåÆå6fVB†÷FòÆæwVvR”G5Æâ"²†&÷VæBæ&G76öçFW7E†÷FôÆæwVvW3òæVçG&–W3òæ¦ö–åFõ7G&–ær‚%Æâ"’²"G¶—Bæ6FVv÷'’ææF—fTæÖWÓ¢G¶—BæÆæwVvT–GÒ"Òó¢%†÷FòÆæwVvRÖWFFFVæf–Æ&ÆRf÷"F†—2&Wf–Wrâ"¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒÂÂÂb¢Ò¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢–b‡6÷W&6RÒçVÆÂ’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ.)ÈòVF—B6öçFW7B&æ²ö–çG2#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G76öçFW7E&æµö–çD7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢&VBç†÷F÷2æf÷$V6‚²†÷FòÓâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%†÷FòG·†÷Fòæ–æFW‚²Ò(
"G·†÷Fòç&t'—FW2ç6—¦WÒ&r'—FW2 ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76öçFW7E†÷Fò†&÷VæBÂ†÷Fòæ–æFW‚’Ð¢Ò’Ð¢–b†&÷VæBæ&G76öçFW7E†÷FôÆæwVvW2ÒçVÆÂ’&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%†÷FòÆæwVvW2b&rFF ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76öçFW7E†÷FôÆæwVvW2†&÷VæB’Ð¢Ò¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$6öçFW7B&V6÷&G2(
"VF—B&æ²ö–çG2"’ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G76öçFW7E†÷Fò†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G76öçFW7Bó¢&WGW&à¢–b†–æFW‚–â&VBç†÷F÷2æ–æF–6W2’&WGW&à¢fÂ&rÒ&VBç†÷Fò†–æFW‚’ç&t'—FW0¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$6öçFW7B†÷FòG¶–æFW‚²Ò(
"&r&V6÷&B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ&ræ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÐ¢FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6öçFW7B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76öçFW7B†&÷VæB’Òç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð¢&—fFRgVâ6†÷t&G76öçFW7E†÷FôÆæwVvW2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G76öçFW7E†÷FôÆæwVvW2ó¢&WGW&à¢fÂFWF–ÂÒ&VBæVçG&–W2æ¦ö–åFõ7G&–ær‚%Æâ"’²"G¶—Bæ–æFW‚²ÒâG¶—Bæ6FVv÷'’ææF—fTæÖWÒ(
"6fVBÆæwVvR”BG¶—BæÆæwVvT–GÒ"Ò°¢%ÆåÆåF†W6R&RF†Rf—fRæF—fR6fVBÆæwVvR'—FW2âÆÂfÇVW2Â–æ6ÇVF–ær¦W&òæBVç&V6övæ—¦VB”G2Â&R&WF–æVBâF†W’Fòæ÷B6†ævRF†RG&–æW"ÆæwVvR÷"W7F&Æ—6‚†÷Fò6öçFVçG2ÂVÆ–v–&–Æ—G’÷"v&G2åÆåÆå&rÆæwVvR&Æö6²ƒ#B'—FW2“¥Æâ"°¢&VBç&t'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆå&W6W'fVB'—FW2gFW"F†Rf—fR”G2ƒ’'—FW2“¥Æâ"²&VBç&W6W'fVD'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆå&W6W'fVBFF—2æ÷BFV6öFVBâæòÆæwVvRVF—F–ærÂæ÷&ÖÆ—¦F–öâÂ&W—"ÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ ¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚$6öçFW7B†÷FòÆæwVvW2(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6öçFW7B"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G76öçFW7B†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7Væ–öå&ööÒ†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G7Væ–öå&ööÒó¢&WGW&à¢gVâ†W‚‡v÷&C¢Æöær“¢7G&–ærÒv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr¢fÂfÆrÒv†Vâ‡&VBç&t–æ—EFÆµv÷&B’²ÂÓâ$6ÆV"#²ÂÓâ%6WB#²VÇ6RÓâ%Væ¶æ÷vâ6fVBv÷&B"Ð¢fÂFWF–ÂÒ%6fVB–æ—F–Â×FÆ²fÆs¢FfÆuÆäæF—fR&ööÆVâ&VC¢G·&VBæ—4–æ—EFÆ·ÕÆå&rfÆrv÷&C¢G·&VBç&t–æ—EFÆµv÷&GÒ††W‚G¶†W‚‡&VBç&t–æ—EFÆµv÷&B—Ò•ÆåÆâ"°¢$æF—fRVæÇG”6÷VçBÆ&VÂ‡6–væVB–çC3"“¢G·&VBçVæÇG”6÷VçGÕÆå&r6÷VçBv÷&C¢G·&VBç&uVæÇG”6÷VçEv÷&GÒ††W‚G¶†W‚‡&VBç&uVæÇG”6÷VçEv÷&B—Ò•ÆåÆâ"°¢$æF—fRVæÇG•F–ÖRÆ&VÂ„fÆöC3"“¢G·&VBçVæÇG•F–ÖWÕÆå&rF–ÖR&—G3¢G·&VBç&uVæÇG•F–ÖT&—G7Ò††W‚G¶†W‚‡&VBç&uVæÇG•F–ÖT&—G2—Ò•ÆåÆâ"°¢$f–VÆBÖVæ–æw2&R&÷f—6–öæÃ²&rfÇVW2&VÖ–âWF†÷&—FF—fRâæòF–ÖRVæ—G2Â&VÖ–æ–ærVæÇG’ÂW‡—'’ÂöæÆ–æR66W72÷"VæÆö6²7FGW2&R–æfW'&VBâæVvF—fR6÷VçG2ÂVæ¶æ÷vâfÆw2Â6–væVB¦W&òÂ–æf–æ—F–W2æBæâ–ÆöBv÷&G2&R&WF–æVBâdÒÖ’V–WB6–væÆ–æræâv†VâF—7Æ––ærfÆöC3#²F†R&rv÷&B7F—2W†7BåÆåÆâ"°¢%&ræF—fRVæ–öâ&ööÒ&V6÷&Bƒ"'—FW2“¥Æâ"²&VBç&t'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%ÆåÆåF†RF‡&VRFö7VÖVçFVBæF—fRf–VÆG26â&RVF—FVB–âfW&–f–VBæWr6÷’â6†ævVB–æ—F–Â×FÆ²fÆr×W7B&R÷"²VçW7VÂVçF÷V6†VBv÷&G2&VÖ–âW†7BâVæÇG•F–ÖRVF—F–ærW6W2—G2W†7B†W†FV6–ÖÂfÆöC3"v÷&Bâ4RFöW2æ÷B&÷f–FR–æfW'&VBVæÇG’6ÆV&–ærÂW‡—'’ÂöæÆ–æR66W72÷"VæÆö6²7F–öç2â ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—BVæ–öâ&ööÒFF#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7Væ–öå&ööÔ7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"Væ–öâ&ööÒ6fVBFF(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ&G7&æFöÔw&÷WæÖR†æÖS¢&G7&æFöÔw&÷WæÖU&VB“¢7G&–ærÐ¢æÖRçFW‡Còæ–dV×G’²$V×G’6fVBæÖR"Òó¢†æÖRçVæf–Æ&ÆU&V6öâó¢$æÖRVæf–Æ&ÆS²&r'—FW2&WF–æVBâ" ¢&—fFRgVâ6†÷t&G7&æFöÔw&÷W2†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G7&æFöÔw&÷W2ó¢&WGW&à¢fÂ&ö÷BÒÆ–æV$Æ–÷WB‡F†—2’æÇ’²÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4Ã²6WEFF–ærƒ#BÂbÂ#BÂb’Ð¢&ö÷BæFEf–Wr…FW‡Ef–Wr‡F†—2’æÇ’°¢FW‡BÒ$ÆÂ"6fVB&æFöÒÖw&÷W&V6÷&G2&R&WF–æVBÂ–æ6ÇVF–ær¦W&òæBÖÆf÷&ÖVB&V6÷&G2âæÖW2æBW6W"”G2&R6fVBFF²F†W’Fòæ÷BfW&–g’öæÆ–æR–FVçF—F–W2ÂW6&ÆRw&÷W2÷"ÖVÖ&W'6†—â6VVG2&R6†÷vâv—F†÷WBGfæ6–ærF†R$ärâF–6·2&VÖ–â6–væVB&rfÇVW3²æòF–ÖW7F×fÆ–F—G’÷"7W'&VçBÖ6Æö6²6Æ–×2â ¢FW‡E6—¦RÒfc²6WEFF–ærƒÂÂÂb¢Ò¢&VBç&V6÷&G2æf÷$V6‚²&V6÷&BÓâ&ö÷BæFEf–Wr„'WGFöâ‡F†—2’æÇ’°¢FW‡BÒ%&V6÷&BG·&V6÷&Bæ–æFW‚²Ò(
"G¶&G7&æFöÔw&÷WæÖR‡&V6÷&Bæw&÷WæÖR—ÕÆå6fVBÆ–W#¢G¶&G7&æFöÔw&÷WæÖR‡&V6÷&BçÆ–W$æÖR—Ò ¢6WDöä6Æ–6´Æ—7FVæW"²–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&æFöÔw&÷W†&÷VæBÂ&V6÷&Bæ–æFW‚’Ð¢Ò’Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"&æFöÒw&÷W2(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr‡&ö÷B’Ò’ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7&æFöÔw&÷W†&÷VæC¢7v—F6…&VE6W76–öâÂ–æFWƒ¢–çB’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G7&æFöÔw&÷W2ó¢&WGW&à¢–b†–æFW‚–â&VBç&V6÷&G2æ–æF–6W2’&WGW&à¢fÂ&V6÷&BÒ&VBç&V6÷&B†–æFW‚¢fÂfÆrÒv†Vâ‡&V6÷&Bç&tÖÆT'—FR’²Óâ$6ÆV"#²Óâ%6WB#²VÇ6RÓâ%Væ¶æ÷vâ6fVB'—FR"Ð¢fÂFWF–ÂÒ%6fVB–æFW‚F–æFW…Æäw&÷WæÖS¢G¶&G7&æFöÔw&÷WæÖR‡&V6÷&Bæw&÷WæÖR—ÕÆå6fVBÆ–W"æÖS¢G¶&G7&æFöÔw&÷WæÖR‡&V6÷&BçÆ–W$æÖR—ÕÆåÆâ"°¢%6fVBÖÆRfÆs¢FfÆr(
"&r'—FRG·&V6÷&Bç&tÖÆT'—FWÕÆäæF—fR&ööÆVâ&VC¢G·&V6÷&BæÖÆWÕÆäÆ–væÖVçB'—FW3¢"²&V6÷&BæÆ–væÖVçD'—FW2æ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’Ò°¢%Æå&Vv–öâ6öFR‡6–væVB“¢G·&V6÷&Bç&Vv–öä6öFWÕÆåW6W"”B‡6–væVB“¢G·&V6÷&BçW6W$–GÕÆå6VVB…T–çCcB“¢G·&V6÷&Bç6VVGÕÆå6VVB†Wƒ¢G·&V6÷&Bç6VVBçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆâ"°¢%&æFöÒ…T–çCcB“¢G·&V6÷&Bç&æFö×ÕÆå&æFöÒ†Wƒ¢G·&V6÷&Bç&æFöÒçFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆå6fVBF–6·2„–çCcB“¢G·&V6÷&BçF–6·7ÕÆåF–6²&—G3¢G·&V6÷&BçF–6·2çFõTÆöær‚’çFõ7G&–ærƒb’çWW&66R‚’çE7F'BƒbÂsr—ÕÆåÆâ"°¢%&r&V6÷&Bƒ3"'—FW2Â–æ6ÇVF–ær&÷F‚6ö×ÆWFRæÖR'VffW'2“¥Æâ"²&V6÷&Bç&t'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ°¢%ÆåÆäÖÆf÷&ÖVBæÖW2&R—6öÆFVB–æF—f–GVÆÇ’æB&VÖ–âW†7BVæÆW72W‡Æ–6—FÇ’&WÆ6VBâF†RFö7VÖVçFVBf–VÆG26â&RVF—FVB–âfW&–f–VBæWr6÷’â4RFöW2æ÷BW&f÷&Ò–FVçF—G’Æöö·WÂöæÆ–æR66W72Âw&÷W¦ö–æ–ærÂ$ärGfæ6R÷"F–ÖW7F×6öçfW'6–öââ ¢fÂ6÷W&6RÒ6VÆV7FVE6÷W&6UW&¢fÂ&öG’ÒÆ–æV$Æ–÷WB‡F†—2’æÇ’°¢÷&–VçFF–öâÒÆ–æV$Æ–÷WBådU%D”4À¢FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò¢–b‡6÷W&6RÒçVÆÂ’FEf–Wr„'WGFöâ‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒ.)ÈòVF—B&æFöÒÖw&÷W&V6÷&BG¶–æFW‚²Ò#²—4ÆÄ62ÒfÇ6P¢6WDöä6Æ–6´Æ—7FVæW"°¢–b‡6W76–öâÓÓÒ&÷VæBbbF6†&ö&D'W7’’7F'D7F—f—G’€¢–çFVçB‡F†—47v—F6…&VDöæÇ”7F—f—G’Â7v—F6„&G7&æFöÔw&÷W7F—f—G“£¦6Æ72æ¦f¢ç6WDFF‡6÷W&6R¢çWDW‡G&‚'6Rç7v—F6‚ç6†#Sb"Â&÷VæBç6æ6†÷Bç6÷W&6U6†#Sb¢çWDW‡G&‚'6Rç7v—F6‚æ&G7ç&æFöÒæw&÷Wæ–æFW‚"Â–æFW‚¢æFDfÆw2„–çFVçBädÄuôu$åEõ$TEõU$•õU$Ô•54”ôâ¢¢Ð¢Ò¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%&æFöÒÖw&÷W&V6÷&BG¶–æFW‚²Ò(
"VF—B"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr†&öG’’Ò¢ç6WE÷6—F—fT'WGFöâ‚%&V6÷&G2"’²òÂòÓâ–b‡6W76–öâÓÓÒ&÷VæB’6†÷t&G7&æFöÔw&÷W2†&÷VæB’Ð¢ç6WDæVvF—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢&—fFRgVâ6†÷t&G7ÖVçU6VÆV7F–öâ†&÷VæC¢7v—F6…&VE6W76–öâ’°¢–b‡6W76–öâÓÒ&÷VæB’&WGW&à¢fÂ&VBÒ&÷VæBæ&G7ÖVçU6VÆV7F–öâó¢&WGW&à¢gVâ†W‚‡v÷&C¢Æöær“¢7G&–ærÒv÷&BçFõ7G&–ærƒb’çWW&66R‚’çE7F'Bƒ‚Âsr¢fÂFWF–ÂÒ'V–ÆE7G&–ær°¢VæB‚"G¶&÷VæBæF—7Æ”æÖWÒ(
"6fRÆ–÷WBG·&VBç&Wf—6–öâæF—7Æ”æÖWÕÆåÆâ"¢VæB‚$Æ7B6VÆV7FVBÖVçR”B‡6–væVB“¢G·&VBæÆ7E6VÆV7FVDÖVçT–GÕÆâ"¢VæB‚$Æ7B6VÆV7FVB”B&—G3¢G¶†W‚‡&VBæÆ7E6VÆV7FVDÖVçT–BçFôÆöær‚’æB„ddddddddÂ—ÕÆåÆâ"¢f÷"†—FVÒ–â&VBæ—FV×2’°¢fÂfÆrÒv†Vâ†—FVÒç&tæWuv÷&B’²ÂÓâ$6ÆV"#²ÂÓâ%6WB#²VÇ6RÓâ%Væ¶æ÷vâ6fVBv÷&B"Ð¢VæB‚%6fVB–æFW‚G¶—FVÒæ–æFW‡Ò(
"ÖVçR”BG¶—FVÒæÖVçT–GÕÆâ"¢VæB‚$”B&—G3¢G¶†W‚†—FVÒæÖVçT–BçFôÆöær‚’æB„ddddddddÂ—ÕÆâ"¢VæB‚$æWr–æF–6F÷#¢FfÆr(
"æF—fR&ööÆVâ&VC¢G¶—FVÒæ—4æWwÕÆâ"¢VæB‚%&rfÆrv÷&C¢G¶—FVÒç&tæWuv÷&GÒ††W‚G¶†W‚†—FVÒç&tæWuv÷&B—Ò•ÆåÆâ"¢Ð¢VæB‚%F†W6R&R6fVBÖVçR&V6÷&G2â”G2&R6†÷vâW†7FÇ’Â–æ6ÇVF–ærGWÆ–6FRÂæVvF—fRæBVæ¶æ÷vâfÇVW2âF†RÆ7B6VÆV7FVB”BæVVBæ÷BÖF6‚6fVB6Æ÷BâæWr–æF–6F÷"FöW2æ÷BW7F&Æ—6‚ÖVçRf–Æ&–Æ—G’ÂVæÆö6·2÷"W6&ÆR7F–öââÖVçRÆ&VÇ2æB6†÷'F7WG2&Ræ÷B–æfW'&VBåÆåÆâ"¢VæB‚%&rÖVçRFFƒc‚'—FW2“¥Æâ"¢VæB‡&VBç&t'—FW2æ6‡Væ¶VBƒb’æ¦ö–åFõ7G&–ær‚%Æâ"’²&÷rÓâ&÷ræ¦ö–åFõ7G&–ær‚""’²"S%‚"æf÷&ÖB†—B’ÒÒ¢VæB‚%ÆåÆå&VBÖöæÇ’âæòÖVçR&V÷&FW&–ærÂ6VÆV7F–öâ6†ævW2Â–æF–6F÷"6ÆV&–ærÂæ÷&ÖÆ—¦F–öâÂVF—F–ærÂW‡÷'BÂ&WÆ6VÖVçB÷"6fRw&—F–ærâ"¢Ð¢ÆW'DF–Æörä'V–ÆFW"‡F†—2’ç6WEF—FÆR‚%6fVBÖVçRÆ–÷WB(
"&VBÖöæÇ’"¢ç6WEf–Wr…67&öÆÅf–Wr‡F†—2’æÇ’²FEf–Wr…FW‡Ef–Wr‡F†—47v—F6…&VDöæÇ”7F—f—G’’æÇ’°¢FW‡BÒFWF–Ã²FW‡E6—¦RÒfc²6WEFW‡D—56VÆV7F&ÆR‡G'VR“²6WEFF–ærƒ#BÂbÂ#BÂb¢Ò’Ò’ç6WE÷6—F—fT'WGFöâ‚$6Æ÷6R"ÂçVÆÂ’ç6†÷r‚¢Ð ¢÷fW'&–FRgVâöäFW7G&÷’‚’°¢&WVW7EfW'6–öâ²°¢6W76–öâÒçVÆÀ¢&VFW"ç6‡WFF÷väæ÷r‚¢7WW"æöäFW7G&÷’‚¢Ð§Ð  