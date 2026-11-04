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
        root.addView(label("Switch Saves • Read-only", 24f))
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
        status = label("Let’s Go Pikachu/Eevee, Sword/Shield and BDSP: native detection, party, storage and Pokémon details are available after validation. Let’s Go, Sword/Shield and BDSP also have Trainer Data. Sword/Shield has regional Pokédex browsing with saved form and Gigantamax flags. BDSP has 493-species Pokédex records with gender/shiny, form, language and Spinda data. Let’s Go has Pokédex browsing plus reviewed seen/caught registration to a verified new copy. Legends: Arceus also supports a Trainer Data, party, 32 pastures and Pokémon details with Alpha, Noble and saved Ganbaru values. Scarlet/Violet also supports a Trainer Data, party, 32 boxes, ride Pokémon and details including saved Tera values. Legends: Z-A also supports a Trainer Data, party, 32 boxes and Pokémon details including Alpha, scale and obedience.")
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
            text = "◎  Trainer Data • Read-only"; isEnabled = false
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
            text = "◉  Pokémon Boxes • Read-only"; isEnabled = false
            setOnClickListener { session?.let { showSwitchPokemonBoxes(it) } }
        }
        partyButton = Button(this).apply {
            text = "Party Pokémon • Read-only"; isEnabled = false
            setOnClickListener { session?.let { openSwitchParty(it) } }
        }
        root.addView(storageButton)
        root.addView(partyButton)
        rideButton = Button(this).apply {
            text = "Ride Pokémon • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { bound -> bound.scarletVioletStorage?.let {
                showScarletVioletPokemon(bound, "Ride Pokémon", it.rideLegend)
            } } }
        }
        root.addView(rideButton)
        pokedexButton = Button(this).apply {
            text = "Pokédex"; isEnabled = false
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
            text = "Items • Read-only"; isEnabled = false
            setOnClickListener { session?.let { if (it.legendsZaInventory != null) showLegendsZaInventoryPockets(it) else if (it.scarletVioletInventory != null) showScarletVioletInventoryPockets(it) else if (it.legendsArceusInventory != null) showLegendsArceusInventoryPockets(it) else if (it.bdspInventory != null) showBdspInventoryPockets(it) else if (it.swordShieldInventory != null) showSwordShieldInventoryPockets(it) else showLetsGoInventoryPockets(it) } }
        }
        root.addView(inventoryButton)
        undergroundButton = Button(this).apply {
            text = "Grand Underground • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUndergroundSections(it) } }
        }
        root.addView(undergroundButton)
        nurseryButton = Button(this).apply {
            text = "Nursery • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { if (it.letsGoDayCare != null) showLetsGoDayCare(it) else showBdspNursery(it) } }
        }
        root.addView(nurseryButton)
        captureRecordsButton = Button(this).apply {
            text = "Capture Records • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showLetsGoCaptureRecords(it) } }
        }
        root.addView(captureRecordsButton)
        battleTowerButton = Button(this).apply {
            text = "Battle Tower • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspBattleTower(it) } }
        }
        root.addView(battleTowerButton)
        poffinsButton = Button(this).apply {
            text = "Poffin Case • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspPoffins(it, 0, false) } }
        }
        root.addView(poffinsButton)
        appearanceButton = Button(this).apply {
            text = "Appearance and Position • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspAppearance(it) } }
        }
        root.addView(appearanceButton)
        timestampsButton = Button(this).apply {
            text = "Saved Timestamps • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspTimestamps(it) } }
        }
        root.addView(timestampsButton)
        playTimeButton = Button(this).apply {
            text = "Recorded Playtime • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { if (it.letsGoPlayTime != null) showLetsGoPlayTime(it) else showBdspPlayTime(it) } }
        }
        root.addView(playTimeButton)
        gameOptionsButton = Button(this).apply {
            text = "Game Options • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { if (it.letsGoGameOptions != null) showLetsGoGameOptions(it) else showBdspGameOptions(it) } }
        }
        root.addView(gameOptionsButton)
        undergroundActivityButton = Button(this).apply {
            text = "Underground Activity • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUndergroundActivity(it) } }
        }
        root.addView(undergroundActivityButton)
        recordStatisticsButton = Button(this).apply {
            text = "Record Statistics • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspRecords(it) } }
        }
        root.addView(recordStatisticsButton)
        additionalRecordsButton = Button(this).apply {
            text = "Additional Record Trainers • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspAdditionalRecords(it) } }
        }
        root.addView(additionalRecordsButton)
        battleTrainerStatusButton = Button(this).apply {
            text = "Battle Trainer Status • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspBattleTrainerFilters(it) } }
        }
        root.addView(battleTrainerStatusButton)
        honeyTreesButton = Button(this).apply {
            text = "Honey Trees • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspHoneyTrees(it) } }
        }
        root.addView(honeyTreesButton)
        encounterButton = Button(this).apply {
            text = "Encounters • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspEncounterMenu(it) } }
        }
        root.addView(encounterButton)
        berryPlotsButton = Button(this).apply {
            text = "Berry Plot Records • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspBerryPlots(it) } }
        }
        root.addView(berryPlotsButton)
        secretBaseButton = Button(this).apply {
            text = "Secret Base • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspSecretBase(it) } }
        }
        root.addView(secretBaseButton)
        npcRecordsButton = Button(this).apply {
            text = "Underground NPC Records • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUndergroundNpcFilters(it) } }
        }
        root.addView(npcRecordsButton)
        undergroundReturnButton = Button(this).apply {
            text = "Underground Return Data • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUndergroundReturn(it) } }
        }
        root.addView(undergroundReturnButton)
        sealStickersButton = Button(this).apply {
            text = "Seal Stickers • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspSealStickerFilters(it) } }
        }
        root.addView(sealStickersButton)
        ballCapsulesButton = Button(this).apply {
            text = "Ball Capsules • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspBallCapsules(it, 0, false) } }
        }
        root.addView(ballCapsulesButton)
        contestButton = Button(this).apply {
            text = "Contest Records • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspContest(it) } }
        }
        root.addView(contestButton)
        unionRoomButton = Button(this).apply {
            text = "Union Room Saved Data • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspUnionRoom(it) } }
        }
        root.addView(unionRoomButton)
        randomGroupsButton = Button(this).apply {
            text = "Random Group Records • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspRandomGroups(it) } }
        }
        root.addView(randomGroupsButton)
        menuSelectionButton = Button(this).apply {
            text = "Saved Menu Layout • Read-only"; isEnabled = false; visibility = android.view.View.GONE
            setOnClickListener { session?.let { showBdspMenuSelection(it) } }
        }
        root.addView(menuSelectionButton)
        root.addView(label("Read-only phase: editing, transfers, conversion, export and save replacement are unavailable. The preview is held in memory and closes when you leave this screen."))
        root.addView(Button(this).apply { text = "Back to PSE"; setOnClickListener { finish() } })
        setContentView(scroll)
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

    private fun setBusy(busy: Boolean) {
        familyPicker.isEnabled = !busy
        openButton.isEnabled = !busy
        clearButton.isEnabled = !busy && session != null
        partyButton.isEnabled = !busy && session?.supports(SwitchReadCapability.PARTY) == true
        storageButton.isEnabled = !busy && (session?.supports(SwitchReadCapability.PC_OR_BOXES) == true ||
            session?.supports(SwitchReadCapability.PASTURES) == true)
        storageButton.text = "◉  Pokémon Boxes • Read-only"
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
        battleTowerButton.isEnabled = !busy && session?.bdspBattleTower != null
        battleTowerButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        nurseryButton.text = if (session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE) "Day Care • Read-only" else "Nursery • Read-only"
        nurseryButton.isEnabled = !busy && (session?.bdspNursery != null || session?.letsGoDayCare != null)
        nurseryButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL || session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE)
            android.view.View.VISIBLE else android.view.View.GONE
        captureRecordsButton.isEnabled = !busy && session?.letsGoCaptureRecords != null
        captureRecordsButton.visibility = if (session?.profile?.family == SwitchGameFamily.LETS_GO_PIKACHU_EEVEE)
            android.view.View.VISIBLE else android.view.View.GONE
        undergroundButton.isEnabled = !busy && session?.bdspUnderground != null
        undergroundButton.visibility = if (session?.profile?.family == SwitchGameFamily.BRILLIANT_DIAMOND_SHINING_PEARL)
            android.view.View.VISIBLE else android.view.View.GONE
        inventoryButton.isEnabled = !busy && session?.supports(SwitchReadCapability.INVENTORY) == true
        pokedexButton.isEnabled = !busy && session?.supports(SwitchReadCapability.POKEDEX) == true
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
        fun cardBackground(fill: Int): android.graphics.drawable.GradientDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(fill); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(25, 145, 220))
        }
        val accent = Color.rgb(70, 190, 255)
        dashboardRoot.addView(label("PSE", 38f, accent))
        dashboardRoot.addView(label("Pokémon Save Editor • Read-only", 15f))
        dashboardRoot.addView(label("CURRENT SAVE", 13f, accent))
        dashboardRoot.addView(label("${bound.displayName}\nGeneration ${bound.profile.family.generation} • Nintendo Switch\nNative integrity verified • Read-only", 18f).apply {
            setPadding(dp(18), dp(16), dp(18), dp(16)); background = cardBackground(Color.rgb(12, 35, 51))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })
        dashboardRoot.addView(label(status.text.toString(), 14f))
        dashboardRoot.addView(label(if (busy) "Reading selected source…" else "EDITOR • READ-ONLY", 13f, accent))
        val targets = mapOf("trainer" to trainerButton, "storage" to storageButton,
            "inventory" to inventoryButton, "pokedex" to pokedexButton)
        val entries = SwitchReadDashboard.entries(bound)
        val detailGroups = SwitchReadGameDetails.groups(bound)
        for ((pairIndex, pair) in entries.chunked(2).withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (entry in pair) {
                val target = targets.getValue(entry.section)
                row.addView(Button(this).apply {
                    text = entry.label + if (entry.enabled) "\nRead-only" else "\nUnavailable for this save"
                    textSize = 16f; isAllCaps = false; minHeight = dp(100); setTextColor(Color.WHITE)
                    background = cardBackground(Color.rgb(12, 35, 51))
                    isEnabled = !busy && entry.enabled && target.isEnabled; alpha = if (isEnabled) 1f else 0.5f
                    setOnClickListener { if (session === bound && !dashboardBusy && target.isEnabled) target.performClick() }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            if (pair.size == 1 && detailGroups.isNotEmpty()) row.addView(Button(this).apply {
                text = "Game Details\nRead-only"; textSize = 16f; isAllCaps = false; minHeight = dp(100)
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
                    text = "◇  PSE Bank • Manage"
                    isAllCaps = false; textSize = 17f; isEnabled = !busy
                    setOnClickListener { if (session === bound && !dashboardBusy) showSwitchBank(bound) }
                })
                dashboardRoot.addView(Button(this).apply {
                    text = "▤  Safe Backups"
                    isAllCaps = false; textSize = 17f; isEnabled = !busy && bound.supports(SwitchReadCapability.SAFE_BACKUPS)
                    setOnClickListener { if (session === bound && !dashboardBusy) showSwitchSafeBackups(bound) }
                })
            }
        }
        if (bound.letsGoDayCare != null) dashboardRoot.addView(Button(this).apply {
            text = "Day Care • Read-only"; isAllCaps = false; isEnabled = !busy && nurseryButton.isEnabled
            setOnClickListener { if (session === bound && !dashboardBusy && nurseryButton.isEnabled) nurseryButton.performClick() }
        })
        if (bound.letsGoCaptureRecords != null) dashboardRoot.addView(Button(this).apply {
            text = "Capture Records • Read-only"; isAllCaps = false; isEnabled = !busy && captureRecordsButton.isEnabled
            setOnClickListener { if (session === bound && !dashboardBusy && captureRecordsButton.isEnabled) captureRecordsButton.performClick() }
        })
        if (bound.letsGoGameOptions != null) dashboardRoot.addView(Button(this).apply {
            text = "Game Options • Read-only"; isAllCaps = false; isEnabled = !busy && gameOptionsButton.isEnabled
            setOnClickListener { if (session === bound && !dashboardBusy && gameOptionsButton.isEnabled) gameOptionsButton.performClick() }
        })
        dashboardRoot.addView(Button(this).apply {
            text = "Inspect current save  ›"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP text speed • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP Battle Style • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP move animations • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP party/box preference • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP vertical camera • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP horizontal camera • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP input mode • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP nickname display • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP nickname registration • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP gyro-sensor preference • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP fossil camera shake • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP auto-report preference • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP Kanji-script preference • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP music volume • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP sound-effects volume • Save new copy"; isAllCaps = false; isEnabled = !busy
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
            text = "Edit BDSP voice volume • Save new copy"; isAllCaps = false; isEnabled = !busy
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
                text = "Edit BDSP options • Save new copy"; isAllCaps = false; isEnabled = !busy
                setOnClickListener {
                    if (session === bound && !dashboardBusy && selectedSourceUri != null && editOptionsDialog == null) {
                        (editOptionsRoot.parent as? android.view.ViewGroup)?.removeView(editOptionsRoot)
                        editOptionsDialog = AlertDialog.Builder(this@SwitchReadOnlyActivity)
                            .setTitle("BDSP • Edit options")
                            .setView(ScrollView(this@SwitchReadOnlyActivity).apply { addView(editOptionsRoot) })
                            .setNegativeButton("Cancel", null).create()
                        editOptionsDialog?.setOnDismissListener { editOptionsDialog = null }
                        editOptionsDialog?.show()
                    }
                }
            })
        }
        dashboardRoot.addView(label("Only BDSP options listed in Edit BDSP options can be edited to a new copy. Other editing, transfers, conversion and source replacement remain locked.", 14f))
        dashboardRoot.addView(Button(this).apply {
            text = "Close save preview"; isAllCaps = false; isEnabled = !busy
            setOnClickListener { if (session === bound && !dashboardBusy && clearButton.isEnabled) clearButton.performClick() }
        })
        dashboardRoot.addView(Button(this).apply { text = "Back to PSE"; isAllCaps = false; setOnClickListener { finish() } })
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
            text = "Browse saved game details by topic. Each section is read-only and keeps its own validation checks."
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
        dialog = AlertDialog.Builder(this).setTitle("${bound.displayName} • Game Details")
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
                text = entry.label + if (entry.enabled && target.isEnabled) " • Read-only" else " • Unavailable for this save"
                isAllCaps = false; isEnabled = entry.enabled && target.isEnabled
                setOnClickListener { if (session === bound && !dashboardBusy && target.isEnabled) target.performClick() }
            })
        }
        AlertDialog.Builder(this).setTitle(group.label + " • Read-only")
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
        status.text = "Reading a bounded, read-only snapshot…"
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
                            "Stored Pokémon: ${it.count}/1000\nParty and storage: read-only access available"
                        } ?: result.swordShieldStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied box slots: ${it.occupiedBoxSlots}/960 " +
                                "(${it.unreadableBoxSlots} unreadable)\nParty and 32 boxes: read-only access available"
                        } ?: result.bdspStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied box slots: ${it.occupiedBoxSlots}/1200 " +
                                "(${it.unreadableBoxSlots} unreadable)\nParty and 40 boxes: read-only access available"
                        } ?: result.legendsZaStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied box slots: ${it.occupiedBoxSlots}/960 " +
                                "(${it.unreadableBoxSlots} unreadable)\nParty and 32 boxes: read-only access available"
                        } ?: result.scarletVioletStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied box slots: ${it.occupiedBoxSlots}/960 " +
                                "(${it.unreadableBoxSlots} unreadable)\nParty, 32 boxes and ride Pokémon: read-only access available"
                        } ?: result.legendsArceusStorage?.let {
                            "Party: ${it.partyCount}/6\nOccupied pasture slots: ${it.occupiedPastureSlots}/960 " +
                                "(${it.unreadablePastureSlots} unreadable)\nParty and 32 pastures: read-only access available"
                        } ?: "Party and storage: unavailable" +
                            (result.storageUnavailableReason?.let { "\n$it" } ?: "")) +
                        "\n\nTrainer profile: " + (if (result.swordShieldTrainer != null || result.letsGoTrainer != null || result.bdspTrainer != null || result.legendsArceusTrainer != null || result.scarletVioletTrainer != null || result.legendsZaTrainer != null) "read-only access available"
                            else "unavailable" + (result.trainerUnavailableReason?.let { "\n$it" } ?: "")) +
                        "\n\nPokédex: " + (result.legendsZaPokedex?.let { "read-only access available • ${it.sections.sumOf { section -> section.entries.size }} species • ${it.revision?.displayName ?: "Revision unknown"}" }
                            ?: result.scarletVioletPokedex?.let { "read-only access available • ${it.layout.displayName} • ${it.regions.size} regional tables" }
                            ?: result.legendsArceusPokedex?.let { "read-only Hisui research • ${it.obtainedCount}/242 obtained • ${it.reportedCount}/242 reported" }
                            ?: result.bdspPokedex?.let { "read-only access available • ${it.seenCount}/493 seen • ${it.caughtCount}/493 caught" }
                            ?: result.swordShieldPokedex?.let { "read-only access available • ${it.regions.size} native regional tables" }
                            ?: result.letsGoPokedex?.let { "read-only access available • ${it.seenCount}/153 seen • ${it.caughtCount}/153 caught" }
                            ?: ("unavailable" + (result.pokedexUnavailableReason?.let { "\n$it" } ?: ""))) +
                        "\n\nInventory: " + (result.legendsZaInventory?.let { "read-only access available • 8 pockets • ${it.recordedCount} recorded items" }
                            ?: result.scarletVioletInventory?.let { "read-only access available • 10 pockets • ${it.recordedCount} recorded items" }
                            ?: result.legendsArceusInventory?.let { "read-only access available • 4 pockets • ${it.recordedItemCount} recorded slots" }
                            ?: result.bdspInventory?.let { "read-only access available • 8 pockets • ${it.recordedCount} recorded items • ${it.unlistedRecords.size} unlisted records" }
                            ?: result.swordShieldInventory?.let { "read-only access available • 9 pockets • ${it.recordedCount} recorded slots" }
                            ?: result.letsGoInventory?.let { "read-only access available • 7 pockets • ${it.recordedCount} recorded slots" }
                            ?: ("unavailable" + (result.inventoryUnavailableReason?.let { "\n$it" } ?: "")))
                    status.text = if (za != null)
                        "${za.version.displayName} detected • Container integrity verified • Read-only."
                    else if (sv != null)
                        "${sv.version.displayName} detected • Container integrity verified • Read-only."
                    else if (arceus != null)
                        "${arceus.version.displayName} detected • Container integrity verified • Read-only."
                    else if (bdsp != null)
                        "${bdsp.version.displayName} detected • Native file integrity verified • Read-only."
                    else if (swsh != null)
                        "${swsh.version.displayName} detected • Container integrity verified • Read-only."
                    else if (checked == null)
                        "File snapshot open • Read-only. No supported native format detected."
                    else
                        "${checked.version.displayName} detected • Structure and block CRCs verified • Read-only."
                }
                setBusy(false)
                if (result != null) {
                    val section = initialSection
                    initialSection = null
                    val button = when (section) { "trainer" -> trainerButton; "party" -> partyButton; "storage" -> storageButton; "inventory" -> inventoryButton; "pokedex" -> pokedexButton; else -> null }
                    if (button?.isEnabled == true) button.performClick()
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
            SwitchGameFamily.LETS_GO_PIKACHU_EEVEE -> "Pokémon Storage"
            else -> "PC Boxes"
        }
        val gap = (12 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap, gap, gap, gap)
        }
        root.addView(TextView(this).apply {
            text = "${bound.displayName} • Read-only\nChoose the same Party or PC Boxes view used by Gen 1. Native storage: $nativeStorage."
            setPadding(0, 0, 0, gap)
        })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val party = Button(this).apply {
            text = "Party"
            isAllCaps = false
            isEnabled = bound.supports(SwitchReadCapability.PARTY)
        }
        val boxes = Button(this).apply {
            text = "PC Boxes"
            isAllCaps = false
            isEnabled = bound.supports(SwitchReadCapability.PC_OR_BOXES) ||
                bound.supports(SwitchReadCapability.PASTURES)
        }
        tabs.addView(party, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = gap / 2 })
        tabs.addView(boxes, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = gap / 2 })
        root.addView(tabs)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Pokémon Boxes")
            .setView(root)
            .setNegativeButton("←  Back to PSE", null)
            .create()
        party.setOnClickListener {
            dialog.dismiss()
            if (session === bound) openSwitchParty(bound)
        }
        boxes.setOnClickListener {
            dialog.dismiss()
            if (session === bound) openSwitchStorage(bound)
        }
        dialog.show()
    }

    private fun slotLabel(slot: LetsGoStorageSlot): String {
        val name = when (val read = slot.read) {
            LetsGoSlotRead.Empty -> "Empty"
            is LetsGoSlotRead.Invalid -> "Unreadable Pokémon • ${read.reason}"
            is LetsGoSlotRead.Pokemon -> {
                val p = read.value
                val species = LetsGoSpeciesNames.name(p.species)
                val nick = p.nickname.takeIf { it.isNotBlank() && it != species }
                (nick?.let { "$it ($species)" } ?: species) +
                    " • Cached Lv ${p.cachedLevel}" + if (p.shiny) " • Shiny" else ""
            }
        }
        return "#${slot.index + 1}  $name" +
            (if (slot.partyPositions.isEmpty()) "" else " • Party ${slot.partyPositions.joinToString()}") +
            (if (slot.partner) " • Partner pointer" else "")
    }

    private fun showLetsGoParty(bound: SwitchReadSession) {
        if (session !== bound) return
        val storage = bound.letsGoStorage ?: return
        val party = storage.party()
        val labels = party.map { "Party ${it.position}: " + (it.storage?.let { slot -> slotLabel(slot) } ?: "Empty") }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Party")
            .setItems(labels.toTypedArray()) { _, position ->
                if (session === bound) party[position].storage?.let { showLetsGoPokemon(bound, it) }
            }.setNegativeButton("Close", null).show()
    }

    private fun showLetsGoStorage(bound: SwitchReadSession, page: Int) {
        if (session !== bound) return
        val storage = bound.letsGoStorage ?: return
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        list.addView(TextView(this).apply {
            text = "${storage.count}/1000 stored • Native list order • Read-only\n" +
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
            .setTitle("Storage • Page ${page + 1}/40")
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
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nGender: $gender\n")
            append("Cached level: ${p.cachedLevel}\nEXP: ${p.experience}\nNature ID: ${p.nature}\n")
            append("Cached HP: ${p.currentHp}/${p.maximumHp}\nShiny: ${if (p.shiny) "Yes" else "No"}\n")
            append("Egg flag: ${p.isEgg}\nPartner form: ${p.isPartnerForm}\n")
            append("Partner pointer: ${slot.partner}\nParty positions: ${slot.partyPositions.joinToString().ifBlank { "None" }}\n\n")
            append("Original trainer: ${p.originalTrainer}\nTID16: ${p.tid}\nSID16: ${p.sid}\n")
            append("PID: ${p.pid.toString(16).uppercase()}\nEncryption constant: ${p.encryptionConstant.toString(16).uppercase()}\n\n")
            append("Moves\n")
            p.moves.forEachIndexed { i, move -> append("${i + 1}. Move #${move.id} • PP ${move.pp}\n") }
            append("\nIVs (HP/Atk/Def/Speed/SpA/SpD): ${p.ivs.joinToString(" / ")}\n")
            append("Awakening values: ${p.awakened.joinToString(" / ")}\n\n")
            append("Pokémon record checksum passed. Read-only access; legality is not evaluated.")
        }
        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        })
        val dialog = AlertDialog.Builder(this).setTitle("Storage #${slot.index + 1} • Read-only")
            .setView(scroll).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Add to PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
            "The selected save changed after it was opened. Reopen it before adding Pokémon to Bank."
        }
        return bound.snapshot.readLetsGoRecord(index)
    }

    private fun swordShieldLabel(read: SwordShieldSlotRead): String = when (read) {
        SwordShieldSlotRead.Empty -> "Empty"
        is SwordShieldSlotRead.Invalid -> "Unreadable Pokémon • ${read.reason}"
        is SwordShieldSlotRead.Pokemon -> {
            val p = read.value
            val species = SwordShieldSpeciesNames.name(p.species)
            val nick = p.nickname.takeIf { it.isNotBlank() && it != species }
            (nick?.let { "$it ($species)" } ?: species) + " • Cached Lv ${p.cachedLevel}" +
                (if (p.shiny) " • Shiny" else "") + (if (p.isEgg) " • Egg" else "")
        }
    }

    private fun showSwordShieldParty(bound: SwitchReadSession) {
        if (session !== bound) return
        val storage = bound.swordShieldStorage ?: return
        val party = storage.party()
        val labels = party.map { "Party ${it.position}: ${swordShieldLabel(it.read)}" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Party ${storage.partyCount}/6")
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
            text = "32 native boxes • 30 slots each • Read-only\n" +
                "${storage.occupiedBoxSlots}/960 occupied slots (${storage.unreadableBoxSlots} unreadable). " +
                "Box names, wallpapers and unlock state are not read in this view."
            setPadding(16, 16, 16, 16)
        })
        for (slot in storage.box(box)) list.addView(Button(this).apply {
            text = "Slot ${slot.position}: ${swordShieldLabel(slot.read)}"
            isAllCaps = false; isEnabled = slot.read !is SwordShieldSlotRead.Empty
            setOnClickListener { if (session === bound) showSwordShieldPokemon(bound, "Box ${box + 1} • Slot ${slot.position}", slot.read) }
        })
        val dialog = AlertDialog.Builder(this).setTitle("${bound.displayName} • Box ${box + 1}/32")
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
            entry.file.name + "\n" + entry.byteCount + " bytes • SHA-256 " + entry.sha256.take(12) + "…"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Safe Backups • " + bound.displayName)
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
        val dialog = AlertDialog.Builder(this).setTitle("Safe Backup • " + bound.displayName)
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
                    "Before • " + current.size + " bytes\nSHA-256: " + currentSha +
                        "\n\nAfter • " + backup.size + " bytes\nSHA-256: " + backupSha +
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
        val directory = File(filesDir, "pse_bank")
        val entries = directory.listFiles()?.filter {
            it.isFile && it.name.startsWith("switch_lgpe_") && it.extension == "pb7"
        }?.sortedBy { it.name }.orEmpty()
        val labels = entries.map { file ->
            val parsed = runCatching { LetsGoPokemonReadOnly.read(file.readBytes()) }.getOrNull()
            val pokemon = (parsed as? LetsGoSlotRead.Pokemon)?.value
            if (pokemon == null) file.name + " • unreadable"
            else pokemon.nickname.ifBlank { LetsGoSpeciesNames.name(pokemon.species) } +
                " (#" + pokemon.species + ")" + (if (pokemon.shiny) " • Shiny" else "") + " • PB7"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Bank • " + entries.size + " Let's Go Pokémon")
            .setPositiveButton("Close", null)
        if (entries.isEmpty()) {
            builder.setMessage("No Let's Go Pokémon are stored. Open Pokémon Boxes, choose a Pokémon, then tap Add to PSE Bank.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showLetsGoBankEntry(bound, entries[index])
            }
        }
        builder.show()
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
            append("${LetsGoSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nCached level: ${p.cachedLevel}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"}\nOriginal trainer: ${p.originalTrainer}\n\n")
            append("Native PB7 checksum passed. Removing this Bank copy never changes the Switch save.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("PSE Bank • Let's Go")
            .setMessage(detail).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
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

    private fun showBdspBank(bound: SwitchReadSession) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank")
        val entries = directory.listFiles()?.filter {
            it.isFile && it.name.startsWith("switch_bdsp_") && it.extension == "pb8"
        }?.sortedBy { it.name }.orEmpty()
        val labels = entries.map { file ->
            val parsed = runCatching { BdspPokemonReadOnly.read(file.readBytes()) }.getOrNull()
            val pokemon = (parsed as? BdspSlotRead.Pokemon)?.value
            if (pokemon == null) file.name + " • unreadable"
            else pokemon.nickname.ifBlank { SwordShieldSpeciesNames.name(pokemon.species) } +
                " (#" + pokemon.species + ")" + (if (pokemon.shiny) " • Shiny" else "") + " • PB8"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Bank • " + entries.size + " BDSP Pokémon")
            .setPositiveButton("Close", null)
        if (entries.isEmpty()) {
            builder.setMessage("No BDSP Pokémon are stored. Open Pokémon Boxes, choose a Pokémon, then tap Add to PSE Bank.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showBdspBankEntry(bound, entries[index])
            }
        }
        builder.show()
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
            append("${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nCached level: ${p.cachedLevel}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"}\nOriginal trainer: ${p.originalTrainer}\n\n")
            append("Native PB8 checksum passed. Removing this Bank copy never changes the Switch save.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("PSE Bank • BDSP")
            .setMessage(detail).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
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
            it.isFile && it.name.startsWith("switch_pla_") && it.extension == "pa8"
        }?.sortedBy { it.name }.orEmpty()
        val labels = entries.map { file ->
            val parsed = runCatching { LegendsArceusPokemonReadOnly.read(file.readBytes()) }.getOrNull()
            val pokemon = (parsed as? LegendsArceusSlotRead.Pokemon)?.value
            if (pokemon == null) file.name + " • unreadable"
            else pokemon.nickname.ifBlank { LegendsArceusSpeciesNames.name(pokemon.species) } +
                " (#" + pokemon.species + ")" + (if (pokemon.shiny) " • Shiny" else "") +
                (if (pokemon.isAlpha) " • Alpha" else "") + " • PA8"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Bank • " + entries.size + " Legends: Arceus Pokémon")
            .setPositiveButton("Close", null)
        if (entries.isEmpty()) {
            builder.setMessage("No Legends: Arceus Pokémon are stored. Open Pokémon Boxes, choose a Pokémon, then tap Add to PSE Bank.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showLegendsArceusBankEntry(bound, entries[index])
            }
        }
        builder.show()
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
            append("${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"} • Alpha: ${if (p.isAlpha) "Yes" else "No"}\n")
            append("Original trainer: ${p.originalTrainer}\n\n")
            append("Native PA8 checksum passed. Removing this Bank copy never changes the Switch save.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("PSE Bank • Legends: Arceus")
            .setMessage(detail).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
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
            it.isFile && it.name.startsWith("switch_sv_") && it.extension == "pk9"
        }?.sortedBy { it.name }.orEmpty()
        val labels = entries.map { file ->
            val parsed = runCatching { ScarletVioletPokemonReadOnly.read(file.readBytes()) }.getOrNull()
            val pokemon = (parsed as? ScarletVioletSlotRead.Pokemon)?.value
            if (pokemon == null) file.name + " • unreadable"
            else pokemon.nickname.ifBlank { ScarletVioletSpeciesNames.name(pokemon.species) } +
                " (#" + pokemon.species + ")" + (if (pokemon.shiny) " • Shiny" else "") +
                (if (pokemon.isEgg) " • Egg" else "") + " • PK9"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Bank • " + entries.size + " Scarlet/Violet Pokémon")
            .setPositiveButton("Close", null)
        if (entries.isEmpty()) {
            builder.setMessage("No Scarlet/Violet Pokémon are stored. Open Pokémon Boxes, choose a Pokémon, then tap Add to PSE Bank.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showScarletVioletBankEntry(bound, entries[index])
            }
        }
        builder.show()
    }

    private fun showScarletVioletBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = file.canonicalFile
        val parsed = runCatching {
            require(entry.parentFile == directory && entry.name.startsWith("switch_sv_") && entry.extension == "pk9") {
                "This is not a Scarlet/Violet Bank entry."
            }
            ScarletVioletPokemonReadOnly.read(entry.readBytes())
        }.getOrElse { ScarletVioletSlotRead.Invalid(it.message ?: "The Bank entry could not be read.") }
        if (parsed !is ScarletVioletSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle("Unreadable PSE Bank entry")
                .setMessage((parsed as? ScarletVioletSlotRead.Invalid)?.reason ?: "The Bank entry is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = parsed.value
        val detail = buildString {
            append("${ScarletVioletSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"} • Egg: ${if (p.isEgg) "Yes" else "No"}\n")
            append("Original trainer: ${p.originalTrainer}\n\n")
            append("Native PK9 checksum passed. Removing this Bank copy never changes the Switch save.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("PSE Bank • Scarlet/Violet")
            .setMessage(detail).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed; the Bank entry was left unchanged." }
                    require(entry.parentFile == directory && entry.name.startsWith("switch_sv_") && entry.extension == "pk9") {
                        "This is not a removable Scarlet/Violet Bank entry."
                    }
                    require(ScarletVioletPokemonReadOnly.read(entry.readBytes()) is ScarletVioletSlotRead.Pokemon) {
                        "The Bank record is invalid; it was left unchanged."
                    }
                    require(entry.delete()) { "The Bank entry could not be removed." }
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Removed from PSE Bank")
                        .setMessage(entry.name + " was removed. The Switch save was not changed.")
                        .setPositiveButton("Back to Bank") { _, _ -> if (session === bound) showScarletVioletBank(bound) }
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
    private fun showLegendsZaBank(bound: SwitchReadSession) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank")
        val entries = directory.listFiles()?.filter {
            it.isFile && it.name.startsWith("switch_za_") && it.extension == "pa9"
        }?.sortedBy { it.name }.orEmpty()
        val labels = entries.map { file ->
            val parsed = runCatching { LegendsZaPokemonReadOnly.read(file.readBytes()) }.getOrNull()
            val pokemon = (parsed as? LegendsZaSlotRead.Pokemon)?.value
            if (pokemon == null) file.name + " • unreadable"
            else pokemon.nickname.ifBlank { ScarletVioletSpeciesNames.name(pokemon.species) } +
                " (#" + pokemon.species + ")" + (if (pokemon.shiny) " • Shiny" else "") +
                (if (pokemon.isAlpha) " • Alpha" else "") + (if (pokemon.isEgg) " • Egg" else "") + " • PA9"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle("PSE Bank • " + entries.size + " Legends: Z-A Pokémon")
            .setPositiveButton("Close", null)
        if (entries.isEmpty()) {
            builder.setMessage("No Legends: Z-A Pokémon are stored. Open Pokémon Boxes, choose a Pokémon, then tap Add to PSE Bank.")
        } else {
            builder.setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showLegendsZaBankEntry(bound, entries[index])
            }
        }
        builder.show()
    }

    private fun showLegendsZaBankEntry(bound: SwitchReadSession, file: File) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank").canonicalFile
        val entry = file.canonicalFile
        val parsed = runCatching {
            require(entry.parentFile == directory && entry.name.startsWith("switch_za_") && entry.extension == "pa9") {
                "This is not a Legends: Z-A Bank entry."
            }
            LegendsZaPokemonReadOnly.read(entry.readBytes())
        }.getOrElse { LegendsZaSlotRead.Invalid(it.message ?: "The Bank entry could not be read.") }
        if (parsed !is LegendsZaSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle("Unreadable PSE Bank entry")
                .setMessage((parsed as? LegendsZaSlotRead.Invalid)?.reason ?: "The Bank entry is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = parsed.value
        val detail = buildString {
            append("${ScarletVioletSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"} • Alpha: ${if (p.isAlpha) "Yes" else "No"} • Egg: ${if (p.isEgg) "Yes" else "No"}\n")
            append("Original trainer: ${p.originalTrainer}\n\n")
            append("Native PA9 checksum passed. Removing this Bank copy never changes the Switch save.")
        }
        val dialog = AlertDialog.Builder(this).setTitle("PSE Bank • Legends: Z-A")
            .setMessage(detail).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    require(session === bound) { "The active save changed; the Bank entry was left unchanged." }
                    require(entry.parentFile == directory && entry.name.startsWith("switch_za_") && entry.extension == "pa9") {
                        "This is not a removable Legends: Z-A Bank entry."
                    }
                    require(LegendsZaPokemonReadOnly.read(entry.readBytes()) is LegendsZaSlotRead.Pokemon) {
                        "The Bank record is invalid; it was left unchanged."
                    }
                    require(entry.delete()) { "The Bank entry could not be removed." }
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Removed from PSE Bank")
                        .setMessage(entry.name + " was removed. The Switch save was not changed.")
                        .setPositiveButton("Back to Bank") { _, _ -> if (session === bound) showLegendsZaBank(bound) }
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
    private fun showSwordShieldBank(bound: SwitchReadSession) {
        if (session !== bound) return
        val directory = File(filesDir, "pse_bank")
        val entries = directory.listFiles()?.filter { it.isFile && it.name.startsWith("switch_swsh_") && it.extension == "pk8" }?.sortedBy { it.name }.orEmpty()
        val labels = entries.map { file ->
            val parsed = runCatching { SwordShieldPokemonReadOnly.read(file.readBytes()) }.getOrNull()
            val pokemon = (parsed as? SwordShieldSlotRead.Pokemon)?.value
            if (pokemon == null) file.name + " • unreadable" else pokemon.nickname.ifBlank { SwordShieldSpeciesNames.name(pokemon.species) } + " (#" + pokemon.species + ")" + (if (pokemon.shiny) " • Shiny" else "") + " • PK8"
        }
        AlertDialog.Builder(this).setTitle("PSE Bank • " + entries.size + " Sword/Shield Pokémon")
            .setItems(labels.toTypedArray()) { _, index ->
                if (session !== bound) return@setItems
                val result = runCatching { SwordShieldPokemonReadOnly.read(entries[index].readBytes()) }.getOrNull()
                showSwordShieldPokemon(bound, "PSE Bank • " + entries[index].name, result ?: SwordShieldSlotRead.Invalid("Bank record could not be read."))
            }
            .setPositiveButton("Close", null).show()
    }

    private fun swordShieldRecordBytes(bound: SwitchReadSession, location: String): ByteArray {
        val uri = selectedSourceUri ?: throw IllegalStateException("The selected save is no longer available.")
        val original = bound.snapshot.bytesCopy()
        val current = contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= 16 * 1024 * 1024) { "The selected file exceeds the 16 MiB intake limit." }
            bytes
        } ?: throw java.io.IOException("The selected save could not be reopened.")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val originalHash = digest.digest(original)
        val currentHash = digest.digest(current)
        require(java.security.MessageDigest.isEqual(originalHash, currentHash)) {
            "The selected save changed after it was opened. Reopen it before adding Pokémon to Bank."
        }
        return bound.snapshot.readSwordShieldRecord(location)
    }

    private fun showSwordShieldPokemon(bound: SwitchReadSession, location: String, read: SwordShieldSlotRead) {
        if (session !== bound) return
        if (read !is SwordShieldSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle(location)
                .setMessage((read as? SwordShieldSlotRead.Invalid)?.reason ?: "This slot is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = read.value
        val gender = listOf("Male", "Female", "Genderless").getOrNull(p.gender) ?: "Unknown (${p.gender})"
        val detail = buildString {
            append("${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nGender: $gender\nEgg flag: ${p.isEgg}\n")
            append("Cached level: ${p.cachedLevel}\nEXP: ${p.experience}\n")
            append("Nature ID: ${p.nature}\nStat nature ID: ${p.statNature}\n")
            append("Cached HP: ${p.currentHp}/${p.maximumHp}\n")
            append("Cached Atk/Def/Speed/SpA/SpD: ${p.cachedStats.joinToString(" / ")}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"}\nHeld item ID: ${p.heldItem}\nAbility ID: ${p.ability}\n")
            append("Dynamax level: ${p.dynamaxLevel}\nGigantamax factor: ${p.canGigantamax}\n\n")
            append("Original trainer: ${p.originalTrainer}\nTID16: ${p.tid}\nSID16: ${p.sid}\n")
            append("PID: ${p.pid.toString(16).uppercase()}\nEncryption constant: ${p.encryptionConstant.toString(16).uppercase()}\n\nMoves\n")
            p.moves.forEachIndexed { i, move -> append("${i + 1}. Move #${move.id} • PP ${move.pp} • PP Ups ${move.ppUps}\n") }
            append("Relearn move IDs: ${p.relearnMoves.joinToString(" / ")}\n\n")
            append("IVs (HP/Atk/Def/Speed/SpA/SpD): ${p.ivs.joinToString(" / ")}\n")
            append("EVs (same order): ${p.evs.joinToString(" / ")}\n\n")
            append("PK8 stored-data checksum passed. Cached party stats are outside that checksum and may be stale, especially in boxes. ")
            append("Read-only access; legality and game-presence checks are not evaluated.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        val bankEntryName = location.takeIf { it.startsWith("PSE Bank • ") }
            ?.removePrefix("PSE Bank • ")
        val dialog = AlertDialog.Builder(this).setTitle("$location • Sword/Shield")
            .setView(scroll).setNegativeButton("Close", null).create()
        dialog.setButton(AlertDialog.BUTTON_POSITIVE,
            if (bankEntryName == null) "Add to PSE Bank" else "Remove from PSE Bank",
            null as android.content.DialogInterface.OnClickListener?)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (session !== bound) { dialog.dismiss(); return@setOnClickListener }
                if (bankEntryName != null) {
                    try {
                        val directory = File(filesDir, "pse_bank").canonicalFile
                        val entry = File(directory, bankEntryName).canonicalFile
                        require(entry.parentFile == directory && entry.name.startsWith("switch_swsh_") && entry.extension == "pk8") {
                            "This is not a removable Sword/Shield Bank entry."
                        }
                        require(SwordShieldPokemonReadOnly.read(entry.readBytes()) is SwordShieldSlotRead.Pokemon) {
                            "The Bank record is invalid; it was left unchanged."
                        }
                        require(entry.delete()) { "The Bank entry could not be removed." }
                        dialog.dismiss()
                        AlertDialog.Builder(this).setTitle("Removed from PSE Bank")
                            .setMessage(bankEntryName + " was removed. The Switch save was not changed.")
                            .setPositiveButton("Back to Bank") { _, _ -> if (session === bound) showSwordShieldBank(bound) }
                            .setNegativeButton("Close", null).show()
                    } catch (error: Exception) {
                        AlertDialog.Builder(this).setTitle("Could not remove Bank entry")
                            .setMessage(error.message ?: "The entry was left unchanged.")
                            .setPositiveButton("Close", null).show()
                    }
                    return@setOnClickListener
                }
                try {
                    val raw = swordShieldRecordBytes(bound, location)
                    val entry = SwitchPseBankStore.depositSwordShield(this, raw)
                    dialog.dismiss()
                    AlertDialog.Builder(this).setTitle("Added to PSE Bank")
                        .setMessage(p.nickname.ifBlank { SwordShieldSpeciesNames.name(p.species) } + " copied as native PK8. The Switch save was not changed.\n\n" + entry.name)
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

    private fun bdspLabel(read: BdspSlotRead): String = when (read) {
        BdspSlotRead.Empty -> "Empty"
        is BdspSlotRead.Invalid -> "Unreadable Pokémon • ${read.reason}"
        is BdspSlotRead.Pokemon -> {
            val p = read.value
            val species = SwordShieldSpeciesNames.name(p.species)
            val nick = p.nickname.takeIf { it.isNotBlank() && it != species }
            (nick?.let { "$it ($species)" } ?: species) + " • Cached Lv ${p.cachedLevel}" +
                (if (p.shiny) " • Shiny" else "") + (if (p.isEgg) " • Egg" else "")
        }
    }

    private fun showBdspParty(bound: SwitchReadSession) {
        if (session !== bound) return
        val storage = bound.bdspStorage ?: return
        val party = storage.party()
        val labels = party.map { "Party ${it.position}: ${bdspLabel(it.read)}" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Party ${storage.partyCount}/6")
            .setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showBdspPokemon(bound, "Party ${party[index].position}", party[index].read)
            }.setNegativeButton("Close", null).show()
    }

    private fun showBdspBox(bound: SwitchReadSession, box: Int) {
        if (session !== bound) return
        val storage = bound.bdspStorage ?: return
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        list.addView(TextView(this).apply {
            text = "40 native boxes • 30 slots each • Read-only\n" +
                "${storage.occupiedBoxSlots}/1200 occupied slots (${storage.unreadableBoxSlots} unreadable). " +
                "Box names, wallpapers and unlock state are not read in this view."
            setPadding(16, 16, 16, 16)
        })
        for (slot in storage.box(box)) list.addView(Button(this).apply {
            text = "Slot ${slot.position}: ${bdspLabel(slot.read)}"
            isAllCaps = false; isEnabled = slot.read !is BdspSlotRead.Empty
            setOnClickListener { if (session === bound) showBdspPokemon(bound, "Box ${box + 1} • Slot ${slot.position}", slot.read) }
        })
        val dialog = AlertDialog.Builder(this).setTitle("${bound.displayName} • Box ${box + 1}/40")
            .setView(scroll).setNegativeButton("Close", null).create()
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(Button(this).apply {
            text = "Previous"; isEnabled = box > 0
            setOnClickListener { dialog.dismiss(); if (session === bound) showBdspBox(bound, box - 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(Button(this).apply {
            text = "Next"; isEnabled = box < 39
            setOnClickListener { dialog.dismiss(); if (session === bound) showBdspBox(bound, box + 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(nav); dialog.show()
    }

    private fun showBdspPokemon(bound: SwitchReadSession, location: String, read: BdspSlotRead) {
        if (session !== bound) return
        if (read !is BdspSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle(location)
                .setMessage((read as? BdspSlotRead.Invalid)?.reason ?: "This slot is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = read.value
        val gender = listOf("Male", "Female", "Genderless").getOrNull(p.gender) ?: "Unknown (${p.gender})"
        val detail = buildString {
            append("${SwordShieldSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nGender: $gender\nEgg flag: ${p.isEgg}\n")
            append("Cached level: ${p.cachedLevel}\nEXP: ${p.experience}\n")
            append("Nature ID: ${p.nature}\nStat nature ID: ${p.statNature}\n")
            append("Cached HP: ${p.currentHp}/${p.maximumHp}\n")
            append("Cached Atk/Def/Speed/SpA/SpD: ${p.cachedStats.joinToString(" / ")}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"}\nHeld item ID: ${p.heldItem}\nAbility ID: ${p.ability}\n")
            append("OT friendship: ${p.originalTrainerFriendship}\nHandling-trainer friendship: ${p.handlingTrainerFriendship}\n")
            val handler = when (p.currentHandler) { 0 -> "Original trainer"; 1 -> "Handling trainer"; else -> "Unknown (${p.currentHandler})" }
            append("Current handler: $handler\n")
            append("Contest values (Cool/Beauty/Cute/Clever/Tough/Sheen): ${p.contestValues.joinToString(" / ")}\n\n")
            append("Original trainer: ${p.originalTrainer}\nTID16: ${p.tid}\nSID16: ${p.sid}\n")
            append("PID: ${p.pid.toString(16).uppercase()}\nEncryption constant: ${p.encryptionConstant.toString(16).uppercase()}\n\nMoves\n")
            p.moves.forEachIndexed { i, move -> append("${i + 1}. Move #${move.id} • PP ${move.pp} • PP Ups ${move.ppUps}\n") }
            append("Relearn move IDs: ${p.relearnMoves.joinToString(" / ")}\n\n")
            append("IVs (HP/Atk/Def/Speed/SpA/SpD): ${p.ivs.joinToString(" / ")}\n")
            append("EVs (same order): ${p.evs.joinToString(" / ")}\n\n")
            append("PB8 stored-data checksum passed. Cached party stats are outside that checksum and may be stale, especially in boxes. ")
            append("Read-only access; legality and game-presence checks are not evaluated.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        val canAddToBank = Regex("^(Box [0-9]+ • Slot [0-9]+|Party [0-9]+)$").matches(location)
        val dialog = AlertDialog.Builder(this).setTitle("$location • Read-only")
            .setView(scroll).setNegativeButton("Close", null).create()
        if (canAddToBank) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Add to PSE Bank",
                null as android.content.DialogInterface.OnClickListener?)
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (session !== bound) { dialog.dismiss(); return@setOnClickListener }
                    try {
                        val raw = bdspRecordBytes(bound, location)
                        val entry = SwitchPseBankStore.depositBdsp(this, raw)
                        dialog.dismiss()
                        AlertDialog.Builder(this).setTitle("Added to PSE Bank")
                            .setMessage(p.nickname.ifBlank { SwordShieldSpeciesNames.name(p.species) } +
                                " copied as native PB8. The Switch save was not changed.\n\n" + entry.name)
                            .setPositiveButton("Close", null).show()
                    } catch (error: Exception) {
                        AlertDialog.Builder(this).setTitle("Could not add to PSE Bank")
                            .setMessage(error.message ?: "The save could not be read again.")
                            .setPositiveButton("Close", null).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun bdspRecordBytes(bound: SwitchReadSession, location: String): ByteArray {
        val uri = selectedSourceUri ?: throw IllegalStateException("The selected save is no longer available.")
        val original = bound.snapshot.bytesCopy()
        val current = contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= 16 * 1024 * 1024) { "The selected file exceeds the 16 MiB intake limit." }
            bytes
        } ?: throw java.io.IOException("The selected save could not be reopened.")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        require(java.security.MessageDigest.isEqual(digest.digest(original), digest.digest(current))) {
            "The selected save changed after it was opened. Reopen it before adding Pokémon to Bank."
        }
        return bound.snapshot.readBdspRecord(location)
    }

    private fun showSwordShieldTrainer(bound: SwitchReadSession) {
        if (session !== bound || !bound.supports(SwitchReadCapability.TRAINER_PROFILE)) return
        val trainer = bound.swordShieldTrainer ?: return
        val detail = buildString {
            append("${trainer.version.displayName}\nTrainer: ${trainer.name.ifEmpty { "Not set" }}\n")
            append("Trainer gender: ${if (trainer.gender == 0) "Male" else "Female"}\n")
            append("Language: ${trainer.languageName} (ID ${trainer.languageId})\n")
            append("Jersey number: ${trainer.jerseyNumber.ifEmpty { "Not set" }}\n")
            append("Trainer ID (six digits): ${trainer.displayTrainerId}\n")
            append("Stored ID32: ${trainer.id32}\nTID16: ${trainer.tid16}\nSID16: ${trainer.sid16}\n")
            append("Watts: ${trainer.watts}\n\n")
            append("Native status fields • Read-only. Trainer gender and language are the saved game fields. ")
            append("Money, play time, badges, League Card and appearance details are not read in this view.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        AlertDialog.Builder(this).setTitle("Trainer Data • Read-only")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun legendsArceusLabel(read: LegendsArceusSlotRead): String = when (read) {
        LegendsArceusSlotRead.Empty -> "Empty"
        is LegendsArceusSlotRead.Invalid -> "Unreadable Pokémon • ${read.reason}"
        is LegendsArceusSlotRead.Pokemon -> {
            val p = read.value
            val species = LegendsArceusSpeciesNames.name(p.species)
            val nick = p.nickname.takeIf { it.isNotBlank() && it != species }
            (nick?.let { "$it ($species)" } ?: species) + (p.cachedLevel?.let { " • Cached Lv $it" } ?: "") +
                (if (p.shiny) " • Shiny" else "") + (if (p.isAlpha) " • Alpha" else "") + (if (p.isNoble) " • Noble" else "")
        }
    }

    private fun showLegendsArceusParty(bound: SwitchReadSession) {
        if (session !== bound) return
        val storage = bound.legendsArceusStorage ?: return
        val party = storage.party()
        val labels = party.map { "Party ${it.position}: ${legendsArceusLabel(it.read)}" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Party ${storage.partyCount}/6")
            .setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showLegendsArceusPokemon(bound, "Party ${party[index].position}", party[index].read)
            }.setNegativeButton("Close", null).show()
    }

    private fun showLegendsArceusPasture(bound: SwitchReadSession, pasture: Int) {
        if (session !== bound) return
        val storage = bound.legendsArceusStorage ?: return
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        list.addView(TextView(this).apply {
            text = "32 native pastures • 30 slots each • Read-only\n" +
                "${storage.occupiedPastureSlots}/960 occupied slots (${storage.unreadablePastureSlots} unreadable). " +
                "Custom pasture names and unlock state are not read in this view."
            setPadding(16, 16, 16, 16)
        })
        for (slot in storage.pasture(pasture)) list.addView(Button(this).apply {
            text = "Slot ${slot.position}: ${legendsArceusLabel(slot.read)}"
            isAllCaps = false; isEnabled = slot.read !is LegendsArceusSlotRead.Empty
            setOnClickListener { if (session === bound) showLegendsArceusPokemon(bound, "Pasture ${pasture + 1} • Slot ${slot.position}", slot.read) }
        })
        val dialog = AlertDialog.Builder(this).setTitle("${bound.displayName} • Pasture ${pasture + 1}/32")
            .setView(scroll).setNegativeButton("Close", null).create()
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(Button(this).apply {
            text = "Previous"; isEnabled = pasture > 0
            setOnClickListener { dialog.dismiss(); if (session === bound) showLegendsArceusPasture(bound, pasture - 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(Button(this).apply {
            text = "Next"; isEnabled = pasture < 31
            setOnClickListener { dialog.dismiss(); if (session === bound) showLegendsArceusPasture(bound, pasture + 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(nav); dialog.show()
    }

    private fun showLegendsArceusPokemon(bound: SwitchReadSession, location: String, read: LegendsArceusSlotRead) {
        if (session !== bound) return
        if (read !is LegendsArceusSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle(location)
                .setMessage((read as? LegendsArceusSlotRead.Invalid)?.reason ?: "This slot is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = read.value
        val gender = listOf("Male", "Female", "Genderless").getOrNull(p.gender) ?: "Unknown (${p.gender})"
        val detail = buildString {
            append("${LegendsArceusSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nGender: $gender\n")
            append("Alpha: ${p.isAlpha}\nNoble: ${p.isNoble}\nShiny: ${p.shiny}\nSaved egg flag: ${p.isEgg}\n")
            append("EXP: ${p.experience}\nNature ID: ${p.nature}\nStat nature ID: ${p.statNature}\n")
            append("Saved current HP: ${p.currentHp}\n")
            if (p.cachedLevel != null) {
                append("Cached party level: ${p.cachedLevel}\nCached maximum HP: ${p.maximumHp}\n")
                append("Cached Atk/Def/Speed/SpA/SpD: ${p.cachedStats?.joinToString(" / ")}\n")
            } else append("Pasture record: level, maximum HP and party stats are not stored in this record.\n")
            append("Ball ID: ${p.ball}\n\nOriginal trainer: ${p.originalTrainer}\nTID16: ${p.tid}\nSID16: ${p.sid}\n")
            append("OT friendship: ${p.originalTrainerFriendship}\nHandling-trainer friendship: ${p.handlingTrainerFriendship}\n")
            val handler = when (p.currentHandler) { 0 -> "Original trainer"; 1 -> "Handling trainer"; else -> "Unknown (${p.currentHandler})" }
            append("Current handler: $handler\n")
            append("PID: ${p.pid.toString(16).uppercase()}\nEncryption constant: ${p.encryptionConstant.toString(16).uppercase()}\n\nMoves\n")
            p.moves.forEachIndexed { i, move -> append("${i + 1}. Move #${move.id} • PP ${move.pp}\n") }
            append("Saved relearn move IDs: ${p.relearnMoves.joinToString(" / ")}\n\n")
            append("IVs (HP/Atk/Def/Speed/SpA/SpD): ${p.ivs.joinToString(" / ")}\n")
            append("Saved Ganbaru values (same order): ${p.ganbaruValues.joinToString(" / ")}\n")
            append("These are raw saved training increments; derived effort levels and recalculated stats are not evaluated.\n\n")
            append("PA8 stored-data checksum passed. Cached party stats are outside that checksum and may be stale. ")
            append("Read-only access; legality and game-presence checks are not evaluated.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        val canAddToBank = Regex("^(Pasture [0-9]+ • Slot [0-9]+|Party [0-9]+)$").matches(location)
        val dialog = AlertDialog.Builder(this).setTitle("$location • Read-only")
            .setView(scroll).setNegativeButton("Close", null).create()
        if (canAddToBank) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Add to PSE Bank",
                null as android.content.DialogInterface.OnClickListener?)
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (session !== bound) { dialog.dismiss(); return@setOnClickListener }
                    try {
                        val raw = legendsArceusRecordBytes(bound, location)
                        val entry = SwitchPseBankStore.depositLegendsArceus(this, raw)
                        dialog.dismiss()
                        AlertDialog.Builder(this).setTitle("Added to PSE Bank")
                            .setMessage(p.nickname.ifBlank { LegendsArceusSpeciesNames.name(p.species) } +
                                " copied as native PA8. The Switch save was not changed.\n\n" + entry.name)
                            .setPositiveButton("Close", null).show()
                    } catch (error: Exception) {
                        AlertDialog.Builder(this).setTitle("Could not add to PSE Bank")
                            .setMessage(error.message ?: "The save could not be read again.")
                            .setPositiveButton("Close", null).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun legendsArceusRecordBytes(bound: SwitchReadSession, location: String): ByteArray {
        val uri = selectedSourceUri ?: throw IllegalStateException("The selected save is no longer available.")
        val original = bound.snapshot.bytesCopy()
        val current = contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= 16 * 1024 * 1024) { "The selected file exceeds the 16 MiB intake limit." }
            bytes
        } ?: throw java.io.IOException("The selected save could not be reopened.")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        require(java.security.MessageDigest.isEqual(digest.digest(original), digest.digest(current))) {
            "The selected save changed after it was opened. Reopen it before adding Pokémon to Bank."
        }
        return bound.snapshot.readLegendsArceusRecord(location)
    }

    private fun scarletVioletLabel(read: ScarletVioletSlotRead): String = when (read) {
        ScarletVioletSlotRead.Empty -> "Empty"
        is ScarletVioletSlotRead.Invalid -> "Unreadable Pokémon • ${read.reason}"
        is ScarletVioletSlotRead.Pokemon -> {
            val p = read.value
            val species = ScarletVioletSpeciesNames.name(p.species)
            val nick = p.nickname.takeIf { it.isNotBlank() && it != species }
            (nick?.let { "$it ($species)" } ?: species) + " • Cached Lv ${p.cachedLevel}" +
                (if (p.shiny) " • Shiny" else "") + (if (p.isEgg) " • Egg" else "")
        }
    }

    private fun showScarletVioletParty(bound: SwitchReadSession) {
        if (session !== bound) return
        val storage = bound.scarletVioletStorage ?: return
        val party = storage.party()
        val labels = party.map { "Party ${it.position}: ${scarletVioletLabel(it.read)}" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Party ${storage.partyCount}/6")
            .setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showScarletVioletPokemon(bound, "Party ${party[index].position}", party[index].read)
            }.setNegativeButton("Close", null).show()
    }

    private fun showScarletVioletBox(bound: SwitchReadSession, box: Int) {
        if (session !== bound) return
        val storage = bound.scarletVioletStorage ?: return
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        list.addView(TextView(this).apply {
            text = "32 native boxes • 30 slots each • Read-only\n" +
                "${storage.occupiedBoxSlots}/960 occupied slots (${storage.unreadableBoxSlots} unreadable). " +
                "The dedicated ride Pokémon is shown separately. Box names, wallpapers and unlock state are not read in this view."
            setPadding(16, 16, 16, 16)
        })
        for (slot in storage.box(box)) list.addView(Button(this).apply {
            text = "Slot ${slot.position}: ${scarletVioletLabel(slot.read)}"
            isAllCaps = false; isEnabled = slot.read !is ScarletVioletSlotRead.Empty
            setOnClickListener { if (session === bound) showScarletVioletPokemon(bound, "Box ${box + 1} • Slot ${slot.position}", slot.read) }
        })
        val dialog = AlertDialog.Builder(this).setTitle("${bound.displayName} • Box ${box + 1}/32")
            .setView(scroll).setNegativeButton("Close", null).create()
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(Button(this).apply {
            text = "Previous"; isEnabled = box > 0
            setOnClickListener { dialog.dismiss(); if (session === bound) showScarletVioletBox(bound, box - 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(Button(this).apply {
            text = "Next"; isEnabled = box < 31
            setOnClickListener { dialog.dismiss(); if (session === bound) showScarletVioletBox(bound, box + 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(nav); dialog.show()
    }

    private fun showScarletVioletPokemon(bound: SwitchReadSession, location: String, read: ScarletVioletSlotRead) {
        if (session !== bound) return
        if (read !is ScarletVioletSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle(location)
                .setMessage((read as? ScarletVioletSlotRead.Invalid)?.reason ?: "This slot is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = read.value
        val gender = listOf("Male", "Female", "Genderless").getOrNull(p.gender) ?: "Unknown (${p.gender})"
        val detail = buildString {
            append("${ScarletVioletSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nGender: $gender\nEgg flag: ${p.isEgg}\n")
            append("Cached level: ${p.cachedLevel}\nEXP: ${p.experience}\n")
            append("Nature ID: ${p.nature}\nStat nature ID: ${p.statNature}\n")
            append("Cached HP: ${p.currentHp}/${p.maximumHp}\n")
            append("Cached Atk/Def/Speed/SpA/SpD: ${p.cachedStats.joinToString(" / ")}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"}\nHeld item ID: ${p.heldItem}\nAbility ID: ${p.ability}\n")
            append("OT friendship: ${p.originalTrainerFriendship}\nHandling-trainer friendship: ${p.handlingTrainerFriendship}\n")
            val handler = when (p.currentHandler) { 0 -> "Original trainer"; 1 -> "Handling trainer"; else -> "Unknown (${p.currentHandler})" }
            append("Current handler: $handler\n")
            append("Saved original Tera type ID: ${p.teraTypeOriginal}\nSaved Tera override ID: ${p.teraTypeOverride}\n")
            append("Scale: ${p.scale}\nObedience level: ${p.obedienceLevel}\n")
            append("Tera fields are raw saved values; battle/form effects are not evaluated.\n\n")
            append("Original trainer: ${p.originalTrainer}\nTID16: ${p.tid}\nSID16: ${p.sid}\n")
            append("PID: ${p.pid.toString(16).uppercase()}\nEncryption constant: ${p.encryptionConstant.toString(16).uppercase()}\n\nMoves\n")
            p.moves.forEachIndexed { i, move -> append("${i + 1}. Move #${move.id} • PP ${move.pp} • PP Ups ${move.ppUps}\n") }
            append("Relearn move IDs: ${p.relearnMoves.joinToString(" / ")}\n\n")
            append("IVs (HP/Atk/Def/Speed/SpA/SpD): ${p.ivs.joinToString(" / ")}\n")
            append("EVs (same order): ${p.evs.joinToString(" / ")}\n\n")
            append("PK9 stored-data checksum passed. Cached party stats are outside that checksum and may be stale, especially in boxes. ")
            append("Read-only access; legality and game-presence checks are not evaluated.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        val canAddToBank = Regex("^(Box [0-9]+ • Slot [0-9]+|Party [0-9]+|Ride Pokémon)$").matches(location)
        val dialog = AlertDialog.Builder(this).setTitle("$location • Read-only")
            .setView(scroll).setNegativeButton("Close", null).create()
        if (canAddToBank) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Add to PSE Bank",
                null as android.content.DialogInterface.OnClickListener?)
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (session !== bound) { dialog.dismiss(); return@setOnClickListener }
                    try {
                        val raw = scarletVioletRecordBytes(bound, location)
                        val entry = SwitchPseBankStore.depositScarletViolet(this, raw)
                        dialog.dismiss()
                        AlertDialog.Builder(this).setTitle("Added to PSE Bank")
                            .setMessage(p.nickname.ifBlank { ScarletVioletSpeciesNames.name(p.species) } +
                                " copied as native PK9. The Switch save was not changed.\n\n" + entry.name)
                            .setPositiveButton("Close", null).show()
                    } catch (error: Exception) {
                        AlertDialog.Builder(this).setTitle("Could not add to PSE Bank")
                            .setMessage(error.message ?: "The save could not be read again.")
                            .setPositiveButton("Close", null).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun scarletVioletRecordBytes(bound: SwitchReadSession, location: String): ByteArray {
        val uri = selectedSourceUri ?: throw IllegalStateException("The selected save is no longer available.")
        val original = bound.snapshot.bytesCopy()
        val current = contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= 16 * 1024 * 1024) { "The selected file exceeds the 16 MiB intake limit." }
            bytes
        } ?: throw java.io.IOException("The selected save could not be reopened.")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        require(java.security.MessageDigest.isEqual(digest.digest(original), digest.digest(current))) {
            "The selected save changed after it was opened. Reopen it before adding Pokémon to Bank."
        }
        return bound.snapshot.readScarletVioletRecord(location)
    }

    private fun legendsZaLabel(read: LegendsZaSlotRead): String = when (read) {
        LegendsZaSlotRead.Empty -> "Empty"
        is LegendsZaSlotRead.Invalid -> "Unreadable Pokémon • ${read.reason}"
        is LegendsZaSlotRead.Pokemon -> {
            val p = read.value
            val species = ScarletVioletSpeciesNames.name(p.species)
            val nick = p.nickname.takeIf { it.isNotBlank() && it != species }
            (nick?.let { "$it ($species)" } ?: species) + " • Cached Lv ${p.cachedLevel}" +
                (if (p.shiny) " • Shiny" else "") + (if (p.isAlpha) " • Alpha" else "") + (if (p.isEgg) " • Egg" else "")
        }
    }

    private fun showLegendsZaParty(bound: SwitchReadSession) {
        if (session !== bound) return
        val storage = bound.legendsZaStorage ?: return
        val party = storage.party()
        val labels = party.map { "Party ${it.position}: ${legendsZaLabel(it.read)}" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Party ${storage.partyCount}/6")
            .setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showLegendsZaPokemon(bound, "Party ${party[index].position}", party[index].read)
            }.setNegativeButton("Close", null).show()
    }

    private fun showLegendsZaBox(bound: SwitchReadSession, box: Int) {
        if (session !== bound) return
        val storage = bound.legendsZaStorage ?: return
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        list.addView(TextView(this).apply {
            text = "32 native boxes • 30 slots each • Read-only\n" +
                "${storage.occupiedBoxSlots}/960 occupied slots (${storage.unreadableBoxSlots} unreadable). " +
                "Box names, wallpapers and unlock state are not read in this view."
            setPadding(16, 16, 16, 16)
        })
        for (slot in storage.box(box)) list.addView(Button(this).apply {
            text = "Slot ${slot.position}: ${legendsZaLabel(slot.read)}"
            isAllCaps = false; isEnabled = slot.read !is LegendsZaSlotRead.Empty
            setOnClickListener { if (session === bound) showLegendsZaPokemon(bound, "Box ${box + 1} • Slot ${slot.position}", slot.read) }
        })
        val dialog = AlertDialog.Builder(this).setTitle("${bound.displayName} • Box ${box + 1}/32")
            .setView(scroll).setNegativeButton("Close", null).create()
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(Button(this).apply {
            text = "Previous"; isEnabled = box > 0
            setOnClickListener { dialog.dismiss(); if (session === bound) showLegendsZaBox(bound, box - 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(Button(this).apply {
            text = "Next"; isEnabled = box < 31
            setOnClickListener { dialog.dismiss(); if (session === bound) showLegendsZaBox(bound, box + 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(nav); dialog.show()
    }

    private fun showLegendsZaPokemon(bound: SwitchReadSession, location: String, read: LegendsZaSlotRead) {
        if (session !== bound) return
        if (read !is LegendsZaSlotRead.Pokemon) {
            AlertDialog.Builder(this).setTitle(location)
                .setMessage((read as? LegendsZaSlotRead.Invalid)?.reason ?: "This slot is empty.")
                .setPositiveButton("Close", null).show()
            return
        }
        val p = read.value
        val gender = listOf("Male", "Female", "Genderless").getOrNull(p.gender) ?: "Unknown (${p.gender})"
        val detail = buildString {
            append("${ScarletVioletSpeciesNames.name(p.species)} (#${p.species}) • Form ${p.form}\n")
            append("Nickname: ${p.nickname.ifBlank { "None" }}\nGender: $gender\nEgg flag: ${p.isEgg}\n")
            append("Cached level: ${p.cachedLevel}\nEXP: ${p.experience}\n")
            append("Nature ID: ${p.nature}\nStat nature ID: ${p.statNature}\n")
            append("Cached HP: ${p.currentHp}/${p.maximumHp}\n")
            append("Cached Atk/Def/Speed/SpA/SpD: ${p.cachedStats.joinToString(" / ")}\n")
            append("Shiny: ${if (p.shiny) "Yes" else "No"}\nHeld item ID: ${p.heldItem}\nAbility ID: ${p.ability}\n")
            append("OT friendship: ${p.originalTrainerFriendship}\nHandling-trainer friendship: ${p.handlingTrainerFriendship}\n")
            val handler = when (p.currentHandler) { 0 -> "Original trainer"; 1 -> "Handling trainer"; else -> "Unknown (${p.currentHandler})" }
            append("Current handler: $handler\n")
            append("Alpha: ${p.isAlpha}\nBall ID: ${p.ball}\n")
            append("Scale: ${p.scale}\nObedience level: ${p.obedienceLevel}\n")
            append("Move+ records, Mega Evolution state and battle/form effects are not evaluated in this view.\n\n")
            append("Original trainer: ${p.originalTrainer}\nTID16: ${p.tid}\nSID16: ${p.sid}\n")
            append("PID: ${p.pid.toString(16).uppercase()}\nEncryption constant: ${p.encryptionConstant.toString(16).uppercase()}\n\nMoves\n")
            p.moves.forEachIndexed { i, move -> append("${i + 1}. Move #${move.id} • PP ${move.pp} • PP Ups ${move.ppUps}\n") }
            append("Relearn move IDs: ${p.relearnMoves.joinToString(" / ")}\n\n")
            append("IVs (HP/Atk/Def/Speed/SpA/SpD): ${p.ivs.joinToString(" / ")}\n")
            append("EVs (same order): ${p.evs.joinToString(" / ")}\n\n")
            append("PA9 stored-data checksum passed. Cached party stats are outside that checksum and may be stale, especially in boxes. ")
            append("Read-only access; legality and game-presence checks are not evaluated.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        val canAddToBank = Regex("^(Box [0-9]+ • Slot [0-9]+|Party [0-9]+)$").matches(location)
        val dialog = AlertDialog.Builder(this).setTitle("$location • Read-only")
            .setView(scroll).setNegativeButton("Close", null).create()
        if (canAddToBank) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Add to PSE Bank",
                null as android.content.DialogInterface.OnClickListener?)
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    if (session !== bound) { dialog.dismiss(); return@setOnClickListener }
                    try {
                        val raw = legendsZaRecordBytes(bound, location)
                        val entry = SwitchPseBankStore.depositLegendsZa(this, raw)
                        dialog.dismiss()
                        AlertDialog.Builder(this).setTitle("Added to PSE Bank")
                            .setMessage(p.nickname.ifBlank { ScarletVioletSpeciesNames.name(p.species) } +
                                " copied as native PA9. The Switch save was not changed.\n\n" + entry.name)
                            .setPositiveButton("Close", null).show()
                    } catch (error: Exception) {
                        AlertDialog.Builder(this).setTitle("Could not add to PSE Bank")
                            .setMessage(error.message ?: "The save could not be read again.")
                            .setPositiveButton("Close", null).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun legendsZaRecordBytes(bound: SwitchReadSession, location: String): ByteArray {
        val uri = selectedSourceUri ?: throw IllegalStateException("The selected save is no longer available.")
        val original = bound.snapshot.bytesCopy()
        val current = contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= 16 * 1024 * 1024) { "The selected file exceeds the 16 MiB intake limit." }
            bytes
        } ?: throw java.io.IOException("The selected save could not be reopened.")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        require(java.security.MessageDigest.isEqual(digest.digest(original), digest.digest(current))) {
            "The selected save changed after it was opened. Reopen it before adding Pokémon to Bank."
        }
        return bound.snapshot.readLegendsZaRecord(location)
    }

    private fun showLetsGoCaptureRecords(bound: SwitchReadSession) {
        if (session !== bound || dashboardBusy) return
        val records = bound.letsGoCaptureRecords ?: return
        val nonzero = records.entries.filter { it.captured != 0L || it.transferred != 0L }
        val lines = buildString {
            appendLine("Saved capture records • read-only")
            appendLine("153 native species entries; raw block retained (" + records.rawBlockBytes.size + " bytes).")
            appendLine("Total caught: " + records.totalCaptured)
            appendLine("Total transferred to Professor Oak: " + records.totalTransferred)
            appendLine()
            if (nonzero.isEmpty()) appendLine("No non-zero species records are stored.")
            else nonzero.forEach { entry -> appendLine("Species " + entry.species + ": caught " + entry.captured + ", transferred " + entry.transferred) }
            appendLine()
            append("No current-party, legality, encounter or unsupported-state inference is made.")
        }
        AlertDialog.Builder(this).setTitle(bound.displayName + " • Capture Records")
            .setMessage(lines).setPositiveButton("Close", null).show()
    }
    private fun showLetsGoDayCare(bound: SwitchReadSession) {
        if (session !== bound || dashboardBusy) return
        val read = bound.letsGoDayCare ?: return
        val detail = buildString {
            append("${read.version.displayName} • Day Care • Read-only\n\n")
            val p = read.pokemon
            if (p != null) {
                val gender = listOf("Male", "Female", "Genderless").getOrNull(p.gender) ?: "Unknown (${p.gender})"
                append("Stored Pokémon: ${LetsGoSpeciesNames.name(p.species)} (#${p.species})\n")
                append("Form: ${p.form}\nGender: $gender\nSaved EXP: ${p.experience}\nNature ID: ${p.nature}\n")
                append("TID16: ${p.tid}\nSID16: ${p.sid}\nPID: ${p.pid.toString(16).uppercase().padStart(8, '0')}\n")
                append("Encryption constant: ${p.encryptionConstant.toString(16).uppercase().padStart(8, '0')}\n")
                append("Stored PB7 checksum passed.\n")
            } else if (read.isZeroStoredRecord) append("Stored record is all zero bytes.\n")
            else append("Stored Pokémon summary unavailable: ${read.recordUnavailableReason}\n")
            append("\nThe saved record has no party-stat tail. Level and HP are not supplied by this view. ")
            append("The eight prefix bytes have no established field meanings here. No occupied/deposited flag, breeding, Egg, fee, earned EXP or withdrawal eligibility is inferred.\n\n")
            append("Opaque prefix (8 bytes):\n${read.rawHeaderBytes.joinToString(" ") { "%02X".format(it) }}\n\n")
            append("Original stored record (232 encrypted bytes):\n${read.rawStoredBytes.joinToString(" ") { "%02X".format(it) }}\n\n")
            append("All 21 native block CRCs passed. Raw data remains available even when the inner record is unreadable. No normalization, deposit, withdrawal, repair, export, replacement or save writing. Legality is not evaluated.")
        }
        AlertDialog.Builder(this).setTitle("Day Care • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Close", null).show()
    }

    private fun showLetsGoGameOptions(bound: SwitchReadSession) {
        if (session !== bound || dashboardBusy) return
        val read = bound.letsGoGameOptions ?: return
        val detail = "${read.version.displayName} • Game options • Read-only\n\n" +
            "Saved text-speed ID: ${read.textSpeedId}\n" +
            "Battle animations: ${if (read.animationsEnabled) "On" else "Off"} (saved bit ${read.battleAnimationId})\n" +
            "Battle style: ${if (read.battleStyleId == 0) "Switch" else "Set"} (saved bit ${read.battleStyleId})\n" +
            "Configuration language: ${read.languageName ?: "Unlisted saved language ID"}\n" +
            "Language ID: ${read.languageId} • Saved language index: ${read.rawLanguageIndex}\n\n" +
            "Unknown upper 24 bits: ${read.unknownUpperBits} (hex ${read.unknownUpperBits.toString(16).uppercase().padStart(6, '0')})\n" +
            "Raw word: ${read.rawWord} (signed ${read.signedConfigValue})\n" +
            "Word bits: ${read.rawWord.toString(16).uppercase().padStart(8, '0')}\nRaw bytes: " + read.rawBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nThe configuration language may differ from the Trainer Data. Text-speed menu choices and meanings of the upper bits are not inferred. All values remain exactly as saved. No option changes, normalization, export, replacement or save writing."
        AlertDialog.Builder(this).setTitle("Game options • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Close", null).show()
    }

    private fun showLetsGoLastSaved(bound: SwitchReadSession) {
        if (session !== bound || dashboardBusy) return
        val read = bound.letsGoLastSaved ?: return
        val detail = "${read.version.displayName} • Last saved • Read-only\n\n" +
            "Saved calendar: ${read.calendarText ?: "Unavailable"}\n" +
            (read.unavailableReason?.let { "$it\n" } ?: "") +
            "\nPrecision: minute. Seconds and time zone are not supplied by this field.\n" +
            "Year: ${read.year} (saved year offset ${read.rawYear})\nMonth: ${read.month} (saved month index ${read.rawMonth})\n" +
            "Day: ${read.day}\nHour: ${read.hour}\nMinute: ${read.minute}\n\n" +
            "Raw saved word: ${read.rawWord}\nBits: ${read.rawWord.toString(16).uppercase().padStart(8, '0')}\n" +
            "Raw bytes: " + read.rawBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nInvalid calendar components are retained without normalization or repair. A valid calendar value does not establish clock accuracy or elapsed time. No UTC/local conversion, timer update, export, replacement or save writing."
        AlertDialog.Builder(this).setTitle("Last saved • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Close", null).show()
    }

    private fun showLetsGoPlayTime(bound: SwitchReadSession) {
        if (session !== bound || dashboardBusy) return
        val read = bound.letsGoPlayTime ?: return
        val warning = if (read.hasCanonicalMinutesAndSeconds) "" else
            "\nMinutes or seconds are outside 0..59. Their saved values are retained without carrying, clamping or repair.\n"
        val detail = "${read.version.displayName} • Recorded playtime • Read-only\n\n" +
            "Saved playtime: ${read.savedTimeText}\nHours: ${read.hours}\nMinutes: ${read.minutes}\nSeconds: ${read.seconds}\n" + warning +
            "\nThis is the recorded game timer, not elapsed wall time or the current clock.\n\n" +
            "Last-saved field (raw word): ${read.rawLastSavedWord}\n" +
            "Last-saved bits: ${read.rawLastSavedWord.toString(16).uppercase().padStart(8, '0')}\n\n" +
            "Complete native playtime block (8 bytes):\n" + read.rawBlockBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nAll 21 native block CRCs passed. No clock conversion, timer advance, reset, normalization, export, replacement or save writing."
        AlertDialog.Builder(this).setTitle("Recorded playtime • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Close", null)
            .setNeutralButton("Last saved") { _, _ -> if (session === bound && !dashboardBusy) showLetsGoLastSaved(bound) }.show()
    }

    private fun showLetsGoTrainer(bound: SwitchReadSession) {
        if (session !== bound) return
        val trainer = bound.letsGoTrainer ?: return
        val gender = if (trainer.gender == 0) "Male" else "Female"
        val starter = when (trainer.starterChoiceId) { 0 -> "Not selected"; 1 -> "Pikachu"; 2 -> "Eevee"; else -> "Unknown (${trainer.starterChoiceId})" }
        val starterGender = listOf("Male", "Female", "Genderless").getOrNull(trainer.starterGenderId)
            ?: "Unknown (${trainer.starterGenderId})"
        val detail = buildString {
            append("${trainer.version.displayName} • Trainer Data • Read-only\n\n")
            append("Trainer: ${trainer.name.ifBlank { "Not set" }}\nGender: $gender\n")
            append("Language: ${trainer.languageName} (ID ${trainer.languageId})\n")
            append("Displayed trainer ID: ${trainer.displayTrainerId}\n")
            append("Raw ID32: ${trainer.id32}\nTID16: ${trainer.tid16}\nSID16: ${trainer.sid16}\n\n")
            append("Rival: ${trainer.rivalName.ifBlank { "Not set" }}\nSaved money: ${trainer.money}\n")
            append("Saved partner choice: $starter\nSaved partner gender: $starterGender\n\n")
            append("Partner choice is saved trainer metadata, separate from the party/partner Pokémon record. ")
            append("Money is the raw saved unsigned value; currency limits and story consistency are not evaluated.\n\n")
            append("All 21 native block CRCs passed before reading. No editing or save replacement is available.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Trainer Data")
            .setView(scroll).setPositiveButton("Close", null)
            .setNeutralButton("Recorded playtime") { _, _ -> if (session === bound && !dashboardBusy) showLetsGoPlayTime(bound) }
            .setNegativeButton("Game options") { _, _ -> if (session === bound && !dashboardBusy) showLetsGoGameOptions(bound) }.show()
    }

    private fun showBdspTrainer(bound: SwitchReadSession) {
        if (session !== bound) return
        val trainer = bound.bdspTrainer ?: return
        val detail = buildString {
            append("${trainer.version.displayName} • Trainer Data • Read-only\n")
            append("Save layout: ${trainer.revision.displayName}\n\n")
            append("Trainer: ${trainer.name.ifBlank { "Not set" }}\nGender: ${if (trainer.gender == 0) "Male" else "Female"}\n")
            append("Language: ${trainer.languageName} (ID ${trainer.languageId})\n")
            append("Displayed trainer ID: ${trainer.displayTrainerId}\n")
            append("Raw ID32: ${trainer.id32}\nTID16: ${trainer.tid16}\nSID16: ${trainer.sid16}\n\n")
            append("Rival: ${trainer.rivalName.ifBlank { "Not set" }}\nMoney: ${trainer.money}\n")
            append("Saved badge count: ${trainer.badgeCount}/8\nGame-clear flag: ${trainer.gameClear}\n\n")
            append("Badge count and game-clear flag are saved values; individual badges and story consistency are not evaluated.\n\n")
            bound.bdspTrainerAppearance?.let { append(bdspAppearanceSummary(it)); append("\n") }
            bound.bdspGameOptions?.let { append(bdspGameOptionsSummary(it)); append("\n") }
            bound.bdspPlayTime?.let { append(bdspPlayTimeSummary(it)); append("\n") }
            bound.bdspTimestamps?.let { append(bdspTimestampSummary(it)); append("\n") }
            append("Native whole-file MD5 passed before reading. No editing or save replacement is available.")
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
            if (bound.bdspRecordStatistics != null) addView(Button(this@SwitchReadOnlyActivity).apply {
                text = "Record statistics • Read-only"
                setOnClickListener { if (session === bound) showBdspRecords(bound) }
            })
        }
        if (bound.bdspAdditionalRecords != null) body.addView(Button(this).apply {
            text = "Additional record trainers • Read-only"
            setOnClickListener { if (session === bound) showBdspAdditionalRecords(bound) }
        })
        if (bound.bdspBattleTrainerStatus != null) body.addView(Button(this).apply {
            text = "Battle-trainer status • Read-only"
            setOnClickListener { if (session === bound) showBdspBattleTrainerFilters(bound) }
        })
        if (bound.bdspHoneyTrees != null) body.addView(Button(this).apply {
            text = "Honey Trees • Read-only"
            setOnClickListener { if (session === bound) showBdspHoneyTrees(bound) }
        })
        if (bound.bdspEncounter != null) body.addView(Button(this).apply {
            text = "Encounters • Read-only"
            setOnClickListener { if (session === bound) showBdspEncounterMenu(bound) }
        })
        if (bound.bdspBerryPlots != null) body.addView(Button(this).apply {
            text = "Berry Plot Records • Read-only"
            setOnClickListener { if (session === bound) showBdspBerryPlots(bound) }
        })
        if (bound.bdspUnionRoom != null) body.addView(Button(this).apply {
            text = "Union Room saved data • Read-only"
            setOnClickListener { if (session === bound) showBdspUnionRoom(bound) }
        })
        if (bound.bdspRandomGroups != null) body.addView(Button(this).apply {
            text = "Random group records • Read-only"
            setOnClickListener { if (session === bound) showBdspRandomGroups(bound) }
        })
        if (bound.bdspMenuSelection != null) body.addView(Button(this).apply {
            text = "Saved menu layout • Read-only"
            setOnClickListener { if (session === bound) showBdspMenuSelection(bound) }
        })
        val scroll = ScrollView(this).apply { addView(body) }
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • Trainer Data")
            .setView(scroll).setPositiveButton("Close", null)
        if (bound.bdspNursery != null) builder.setNeutralButton("Nursery") { _, _ -> if (session === bound) showBdspNursery(bound) }
        if (bound.bdspBattleTower != null) builder.setNegativeButton("Battle Tower") { _, _ -> if (session === bound) showBdspBattleTower(bound) }
        builder.show()
    }

    private fun showLegendsArceusTrainer(bound: SwitchReadSession) {
        if (session !== bound) return
        val trainer = bound.legendsArceusTrainer ?: return
        fun saved(value: Long?): String = value?.toString() ?: "Not present"
        val detail = buildString {
            append("${trainer.version.displayName} • Trainer Data • Read-only\n\n")
            append("Trainer: ${trainer.name.ifBlank { "Not set" }}\nGender: ${if (trainer.gender == 0) "Male" else "Female"}\n")
            append("Language: ${trainer.languageName} (ID ${trainer.languageId})\n")
            append("Displayed trainer ID: ${trainer.displayTrainerId}\n")
            append("Raw ID32: ${trainer.id32}\nTID16: ${trainer.tid16}\nSID16: ${trainer.sid16}\n\n")
            append("Money: ${saved(trainer.money)}\nCurrent Merit Points: ${saved(trainer.meritCurrent)}\n")
            append("Total Merit Points earned: ${saved(trainer.meritTotal)}\n")
            append("Galaxy Expedition Team rank: ${saved(trainer.expeditionRank)}\nSatchel upgrades: ${saved(trainer.satchelUpgrades)}\n\n")
            append("Absent counters stay marked Not present. Merit totals, research progress and story consistency are not evaluated.\n\n")
            append("Native container integrity passed before reading. No editing or save replacement is available.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Trainer Data")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showScarletVioletTrainer(bound: SwitchReadSession) {
        if (session !== bound) return
        val trainer = bound.scarletVioletTrainer ?: return
        fun saved(value: Long?): String = value?.toString() ?: "Not present"
        val detail = buildString {
            append("${trainer.version.displayName} • Trainer Data • Read-only\n\n")
            append("Trainer: ${trainer.name.ifBlank { "Not set" }}\nGender: ${if (trainer.gender == 0) "Male" else "Female"}\n")
            append("Language: ${trainer.languageName} (ID ${trainer.languageId})\n")
            append("Displayed trainer ID: ${trainer.displayTrainerId}\n")
            append("Raw ID32: ${trainer.id32}\nTID16: ${trainer.tid16}\nSID16: ${trainer.sid16}\n\n")
            append("Birthday (month/day): ${if (trainer.birthMonth == 0) "Not set" else "${trainer.birthMonth}/${trainer.birthDay}"}\n")
            append("Money: ${saved(trainer.money)}\nLeague Points: ${saved(trainer.leaguePoints)}\n")
            append("Blueberry Points: ${saved(trainer.blueberryPoints)}\n\n")
            append("Currencies are raw saved unsigned values; game balance limits and story consistency are not evaluated. ")
            append("Absent counters stay marked Not present and do not establish DLC or progression status.\n\n")
            append("Native container integrity passed before reading. No editing or save replacement is available.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Trainer Data")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showLegendsZaTrainer(bound: SwitchReadSession) {
        if (session !== bound) return
        val trainer = bound.legendsZaTrainer ?: return
        fun saved(value: Long?): String = value?.toString() ?: "Not present"
        val detail = buildString {
            append("${trainer.version.displayName} • Trainer Data • Read-only\n\n")
            append("Revision marker: ${trainer.revision?.displayName ?: "Not present"}\n")
            append("Trainer: ${trainer.name.ifBlank { "Not set" }}\nGender: ${if (trainer.gender == 0) "Male" else "Female"}\n")
            append("Language: ${trainer.languageName} (ID ${trainer.languageId})\n")
            append("Displayed trainer ID: ${trainer.displayTrainerId}\n")
            append("Raw ID32: ${trainer.id32}\nTID16: ${trainer.tid16}\nSID16: ${trainer.sid16}\n\n")
            append("Birthday (month/day): ${if (trainer.birthMonth == 0) "Not set" else "${trainer.birthMonth}/${trainer.birthDay}"}\n")
            append("Birthday given: ${trainer.wasBirthdayGiven}\nObserved birthday: ${trainer.observedBirthday}\n")
            append("Observed birthday year (raw): ${trainer.observedBirthdayYear}\n\n")
            append("Money: ${saved(trainer.money)}\nZ-A Royale ticket points: ${saved(trainer.royaleTicketPoints)}\n")
            append("Infinite Z-A Royale ticket points: ${saved(trainer.infiniteTicketPoints)}\n")
            append("Player HP (raw): ${trainer.playerHp}\nSaved Mega gauge (raw): ${trainer.megaGaugePercent}\n\n")
            append("Counters and Mega gauge are raw saved values; balance limits, percentage scaling and story consistency are not evaluated. ")
            append("Absent counters stay marked Not present and do not establish DLC or progression status.\n\n")
            append("Native container integrity passed before reading. No editing or save replacement is available.")
        }
        val scroll = ScrollView(this).apply {
            addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            })
        }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Trainer Data")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showLetsGoPokedex(bound: SwitchReadSession, requestedPage: Int) {
        if (session !== bound) return
        val dex = bound.letsGoPokedex ?: return
        val lastPage = (dex.entries.size - 1) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "Kanto: ${dex.kantoSeenCount}/151 seen • ${dex.kantoCaughtCount}/151 caught\n" +
                "Including Meltan/Melmetal: ${dex.seenCount}/153 seen • ${dex.caughtCount}/153 caught\n" +
                "Saved viewed entry: #${dex.currentViewedSpecies}\nNational Dex flag (raw): ${dex.nationalDexFlag}\n\n" +
                "Saved registration flags only. These counts do not establish story or completion rewards."
            textSize = 16f; setTextIsSelectable(true)
        })
        for (entry in dex.entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "#${entry.speciesId} ${LetsGoSpeciesNames.name(entry.speciesId)} • " +
                "${if (entry.seen) "Seen" else "Not seen"} • ${if (entry.caught) "Caught" else "Not caught"}"
            setOnClickListener { showLetsGoDexEntry(bound, entry.speciesId) }
        })
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • Pokédex ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Close", null)
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showLetsGoPokedex(bound, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showLetsGoPokedex(bound, page + 1) }
        builder.show()
    }

    private fun showLetsGoDexEntry(bound: SwitchReadSession, species: Int) {
        if (session !== bound) return
        val entry = bound.letsGoPokedex?.entry(species) ?: return
        val variants = listOf("Male / genderless", "Female", "Shiny male / genderless", "Shiny female")
        fun flags(values: List<Boolean>): String = variants.mapIndexed { i, label -> "$label: ${values[i]}" }.joinToString("\n")
        val detail = buildString {
            append("#${entry.speciesId} ${LetsGoSpeciesNames.name(entry.speciesId)} • Pokédex\n\n")
            append("Caught: ${entry.caught}\nSeen: ${entry.seen}\n\nBase-species seen flags\n${flags(entry.seenVariants)}\n\n")
            append("Base-species displayed flags\n${flags(entry.displayedVariants)}\n\n")
            append("Registered languages: ${entry.languages.joinToString().ifBlank { "None recorded" }}\n\n")
            for (form in entry.forms) {
                append("Size records • ${if (form.formId == 0) "base form" else "saved form ID ${form.formId}"}\n")
                for (record in form.sizeRecords) append("${record.group}: ${if (record.isRecorded) "Recorded" else "Not recorded"} • " +
                    "height scalar ${record.heightScalar}, weight scalar ${record.weightScalar}, flagged ${record.isFlagged} (raw ${record.flagByte})\n")
                append("\n")
            }
            append("Flags and size records are shown as saved; contradictory flags are not repaired. Size scalars are not absolute measurements. ")
            append("Alternate-form seen/display flags, story progress and rewards are outside this view. New native seen or caught registration can be saved to a reviewed new copy; existing registrations are preserved.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • ${LetsGoSpeciesNames.name(species)}")
            .setView(scroll).setNegativeButton("Close", null)
            .setPositiveButton("Add registration • New copy") { _, _ ->
                if (session === bound) launchLetsGoPokedexEditor(bound, species)
            }.show()
    }

    private fun launchLetsGoPokedexEditor(bound: SwitchReadSession, species: Int) {
        val source = selectedSourceUri ?: return
        if (session !== bound) return
        startActivity(
            Intent(this, SwitchLetsGoPokedexActivity::class.java)
                .setData(source)
                .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                .putExtra("pse.switch.dex.species", species)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun showSwordShieldDexRegions(bound: SwitchReadSession) {
        if (session !== bound) return
        val dex = bound.swordShieldPokedex ?: return
        val labels = SwordShieldDexRegion.values().map { region -> dex.region(region)?.let {
            "${region.displayName}: ${it.seenCount}/${region.entryCount} seen • ${it.caughtCount}/${region.entryCount} caught"
        } ?: "${region.displayName}: Not present in this save" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Pokédex • Read-only")
            .setItems(labels.toTypedArray()) { _, index ->
                val region = SwordShieldDexRegion.values()[index]
                if (session === bound && dex.region(region) != null) showSwordShieldPokedex(bound, region, 0)
            }.setPositiveButton("Close", null).show()
    }

    private fun showSwordShieldPokedex(bound: SwitchReadSession, region: SwordShieldDexRegion, requestedPage: Int) {
        if (session !== bound) return
        val table = bound.swordShieldPokedex?.region(region) ?: return
        val lastPage = (table.entries.size - 1) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${region.displayName}: ${table.seenCount}/${region.entryCount} seen • ${table.caughtCount}/${region.entryCount} caught\n\n" +
                "Saved regional records only. Shared species can have separate regional records; these counts do not prove story progress, rewards or DLC entitlement."
            textSize = 16f; setTextIsSelectable(true)
        })
        for (entry in table.entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "${entry.number.toString().padStart(3, '0')} • ${SwordShieldSpeciesNames.name(entry.speciesId)} • " +
                "${if (entry.seen) "Seen" else "Not seen"} • ${if (entry.caught) "Caught" else "Not caught"}"
            setOnClickListener { showSwordShieldDexEntry(bound, region, entry.number) }
        })
        val builder = AlertDialog.Builder(this).setTitle("${region.displayName} Pokédex ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Regions") { _, _ -> if (session === bound) showSwordShieldDexRegions(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showSwordShieldPokedex(bound, region, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showSwordShieldPokedex(bound, region, page + 1) }
        builder.show()
    }

    private fun showSwordShieldDexEntry(bound: SwitchReadSession, region: SwordShieldDexRegion, number: Int) {
        if (session !== bound) return
        val entry = bound.swordShieldPokedex?.region(region)?.entry(number) ?: return
        val variants = listOf("Male / genderless", "Female", "Shiny male / genderless", "Shiny female")
        val detail = buildString {
            append("${region.displayName} #${entry.number} • ${SwordShieldSpeciesNames.name(entry.speciesId)} (National #${entry.speciesId})\nRead-only\n\n")
            append("Seen: ${entry.seen}\nCaught: ${entry.caught}\nCaught Gigantamax flag: ${entry.caughtGigantamax}\n")
            append("Secondary Gigantamax flag (raw +0x28 bit 0): ${entry.secondaryGigantamaxFlag}\n\n")
            for ((index, bits) in entry.seenBits.withIndex()) append("${variants[index]} seen bit indices: ${bits.joinToString().ifBlank { "None" }}\n")
            append("\nRegistered languages: ${entry.languages.joinToString().ifBlank { "None recorded" }}\n")
            append("Displayed form ID (raw): ${entry.displayedFormId}\nDisplayed gender ID (raw): ${entry.displayedGenderId}\n")
            append("Display Gigantamax: ${entry.displayGigantamax}\nDisplay shiny: ${entry.displayShiny}\nBattled count: ${entry.battledCount}\n\n")
            append("Raw flags (+0x20): ${entry.rawFlags}\nRaw extra (+0x28): ${entry.rawExtra1}\nRaw extra (+0x2C): ${entry.rawExtra2}\n\n")
            append("Seen bits are native indices, not named or legality-checked forms. Bit 63 is a special Gigantamax/Eternamax marker; Urshifu also uses bit 62 for its second Gigantamax form. ")
            append("Alcremie indices encode cream/sweet combinations. Flags and contradictory states remain as saved; no repair or completion/reward inference. No editing or save replacement.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("${region.displayName} • ${SwordShieldSpeciesNames.name(entry.speciesId)}")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showBdspPokedex(bound: SwitchReadSession, requestedPage: Int) {
        if (session !== bound) return
        val dex = bound.bdspPokedex ?: return
        val lastPage = (dex.entries.size - 1) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "Stored species records: ${dex.heardCount}/493 heard of • ${dex.seenCount}/493 seen • ${dex.caughtCount}/493 caught\n" +
                "Regional Dex obtained: ${dex.regionalDex.value} (raw ${dex.regionalDex.rawValue})\n" +
                "National Dex obtained: ${dex.nationalDex.value} (raw ${dex.nationalDex.rawValue})\n\n" +
                "National species order. Saved flags/counts do not establish story completion, rewards or regional-dex completion."
            textSize = 16f; setTextIsSelectable(true)
        })
        for (entry in dex.entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "#${entry.speciesId} ${SwordShieldSpeciesNames.name(entry.speciesId)} • ${entry.state.displayName}"
            setOnClickListener { showBdspDexEntry(bound, entry.speciesId) }
        })
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • Pokédex ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Close", null)
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspPokedex(bound, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspPokedex(bound, page + 1) }
        builder.show()
    }

    private fun showBdspDexEntry(bound: SwitchReadSession, species: Int) {
        if (session !== bound) return
        val entry = bound.bdspPokedex?.entry(species) ?: return
        val variants = listOf("Male", "Female", "Shiny male", "Shiny female")
        fun flag(value: BdspDexFlag): String = "${value.value} (raw ${value.rawValue})"
        val detail = buildString {
            append("#${entry.speciesId} ${SwordShieldSpeciesNames.name(entry.speciesId)} • Pokédex • Read-only\n\n")
            append("Saved state: ${entry.state.displayName} (${entry.state.ordinal})\nHeard of: ${entry.heard}\nSeen: ${entry.seen}\nCaught: ${entry.caught}\n\n")
            for (index in variants.indices) append("${variants[index]} registration: ${flag(entry.genderFlags[index])}\n")
            append("\nRegistered languages: ${entry.languages.joinToString().ifBlank { "None recorded" }}\nRaw language flags: ${entry.rawLanguageFlags}\n\n")
            if (entry.forms.isEmpty()) append("No dedicated native form-flag array for this species.\n")
            else for (form in entry.forms) append("Saved form ID ${form.formId}: normal ${flag(form.normal)} • shiny ${flag(form.shiny)}\n")
            if (entry.spinda.isNotEmpty()) {
                append("\nSpinda saved pattern ECs\n")
                for (pattern in entry.spinda) append("${pattern.variant}: seen EC ${pattern.seenEc} • caught EC ${pattern.caughtEc}\n")
                append("ECs are raw unsigned saved values, including zero; they do not establish registration.\n")
            }
            append("\nNative booleans are true only when the full four-byte saved value equals 1. Unknown raw words are preserved. ")
            append("Gender/form/language flags remain independent of saved state; contradictions are not repaired. Form IDs are native indices, not legality verdicts. No editing or save replacement.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • ${SwordShieldSpeciesNames.name(species)}")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showLegendsArceusPokedex(bound: SwitchReadSession, requestedPage: Int) {
        if (session !== bound) return
        val dex = bound.legendsArceusPokedex ?: return
        val lastPage = (dex.entries.size - 1) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "Hisui research records • Read-only\n${dex.obtainedCount}/242 obtained • ${dex.updatedCount}/242 updated\n" +
                "${dex.reportedCount}/242 reported • ${dex.perfectCount}/242 saved perfect flags\n" +
                "Saved research points: ${dex.totalResearchPoints}\nUpdate counter: ${dex.updateCounter} • Report counter: ${dex.reportCounter}\n" +
                "Hisui completed flag: ${dex.hisuiCompletedFlag} • Perfect flag: ${dex.hisuiPerfectFlag}\nRaw global flags: ${dex.rawGlobalFlags}\n\n" +
                "Saved research values only. Task thresholds, seen/form statistics, local-area lists and rewards are not evaluated."
            textSize = 16f; setTextIsSelectable(true)
        })
        for (entry in dex.entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "Hisui #${entry.number} ${LegendsArceusSpeciesNames.name(entry.speciesId)} • Obtained ${entry.numObtained} • Reported ${entry.reported}"
            setOnClickListener { showLegendsArceusDexEntry(bound, entry.number) }
        })
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • Research ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Close", null)
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showLegendsArceusPokedex(bound, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showLegendsArceusPokedex(bound, page + 1) }
        builder.show()
    }

    private fun showLegendsArceusDexEntry(bound: SwitchReadSession, number: Int) {
        if (session !== bound) return
        val entry = bound.legendsArceusPokedex?.entry(number) ?: return
        val detail = buildString {
            append("Hisui #${entry.number} • ${LegendsArceusSpeciesNames.name(entry.speciesId)} (National #${entry.speciesId})\nRead-only research record\n\n")
            append("Obtained count: ${entry.numObtained}\nEver updated: ${entry.updated}\nAny report: ${entry.reported}\nSaved perfect flag: ${entry.perfect}\n")
            append("Path of Solitude complete flag: ${entry.solitudeComplete}\nRaw research rate: ${entry.rawResearchRate}\n")
            append("Update counter: ${entry.updateCounter}\nLast report counter: ${entry.lastReportCounter}\nRaw reported progress: ${entry.rawReportedProgress}\nRaw flags: ${entry.rawFlags}\n\n")
            append("Selected display form ID: ${entry.selectedForm}\nSelected gender-1 flag: ${entry.selectedGender1}\nSelected shiny: ${entry.selectedShiny}\nSelected alpha: ${entry.selectedAlpha}\n\n")
            append("Saved counters\n")
            for (counter in entry.counters) append("${counter.label}: ${counter.value}\n")
            append("\nRaw rates and progress do not establish research level or completed tasks. Required task rules can cap the effective rate. ")
            append("Move/type slots do not identify tasks; some counters do not apply to every species. Selected display flags do not establish seen/caught forms. ")
            append("Saved contradictions and unknown bits are preserved. No editing or save replacement.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Hisui research • ${LegendsArceusSpeciesNames.name(entry.speciesId)}")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showScarletVioletDexRegions(bound: SwitchReadSession) {
        if (session !== bound) return
        val dex = bound.scarletVioletPokedex ?: return
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Pokédex • Read-only")
            .setItems(dex.regions.map { "${it.region.displayName} • ${it.seenCount}/${it.entries.size} seen • ${it.caughtCount} caught" }.toTypedArray()) { _, index ->
                if (session === bound) showScarletVioletPokedex(bound, dex.regions[index].region, 0)
            }.setNegativeButton("Close", null).show()
    }

    private fun showScarletVioletPokedex(bound: SwitchReadSession, region: ScarletVioletDexRegion, requestedPage: Int) {
        if (session !== bound) return
        val dex = bound.scarletVioletPokedex ?: return
        val table = dex.region(region) ?: return
        val lastPage = (table.entries.size - 1) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${dex.layout.displayName} • ${region.displayName}\n${table.knownCount}/${table.entries.size} known • ${table.seenCount} seen • ${table.caughtCount} caught\n\n" +
                "Saved regional records only. Listing an area does not establish DLC access, story completion, rewards or a completed Pokédex."
            textSize = 16f; setTextIsSelectable(true)
        })
        for (entry in table.entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "#${entry.number} ${ScarletVioletSpeciesNames.name(entry.speciesId)} • Known ${entry.known} • Seen ${entry.seen} • Caught ${entry.caught}"
            setOnClickListener { showScarletVioletDexEntry(bound, region, entry.number) }
        })
        val builder = AlertDialog.Builder(this).setTitle("${region.displayName} Pokédex ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Regions") { _, _ -> if (session === bound) showScarletVioletDexRegions(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showScarletVioletPokedex(bound, region, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showScarletVioletPokedex(bound, region, page + 1) }
        builder.show()
    }

    private fun showScarletVioletDexEntry(bound: SwitchReadSession, region: ScarletVioletDexRegion, number: Int) {
        if (session !== bound) return
        val dex = bound.scarletVioletPokedex ?: return
        val entry = dex.region(region)?.entry(number) ?: return
        fun forms(word: Long): String = (0..31).filter { word and (1L shl it) != 0L }.joinToString().ifBlank { "None" }
        val detail = buildString {
            append("${region.displayName} #${entry.number} • ${ScarletVioletSpeciesNames.name(entry.speciesId)} (National #${entry.speciesId})\nRead-only • ${dex.layout.displayName}\n\n")
            append("Known: ${entry.known}\nSeen: ${entry.seen}\nCaught: ${entry.caught}\nShiny seen: ${entry.shinySeen}\nNew display: ${entry.newDisplay}\n")
            entry.rawState?.let { append("Raw state: $it (0 none, 1 known, 2 seen, 3 caught; other values retained)\n") }
            append("\nSeen form IDs: ${forms(entry.seenForms)} (raw ${entry.seenForms})\n")
            entry.obtainedForms?.let { append("Obtained form IDs: ${forms(it)} (raw $it)\n") }
            entry.heardForms?.let { append("Heard form IDs: ${forms(it)} (raw $it)\n") }
            entry.checkedForms?.let { append("Checked form IDs: ${forms(it)} (raw $it)\n") }
            val genders = listOf("Male", "Female", "Genderless").filterIndexed { bit, _ -> entry.genderFlags and (1 shl bit) != 0 }
            append("\nSeen genders: ${genders.joinToString().ifBlank { "None" }} (raw ${entry.genderFlags})\n")
            append("Registered languages: ${entry.languages.joinToString().ifBlank { "None" }} (raw ${entry.languageFlags})\nRaw shiny flags: ${entry.rawShinyFlags}\n")
            entry.rawNewFlag?.let { append("Raw new flag: $it\n") }; entry.rawGenderDifference?.let { append("Raw display gender-difference flag: $it\n") }
            append("\nSaved display selections\n")
            for (display in entry.displays) append("${display.region.displayName}: form ID ${display.formId} • gender value ${display.rawGender} • shiny value ${display.rawShiny}\n")
            append("\nForm bits and display selections are native IDs, not legality verdicts. Unknown bits, raw values and contradictory flags remain unchanged. ")
            append("Original saves have one state and form-seen flags; DLC-era saves have independent obtained/seen/heard/checked flags. No editing or save replacement.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("${region.displayName} • ${ScarletVioletSpeciesNames.name(entry.speciesId)}")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showLegendsZaDexSections(bound: SwitchReadSession) {
        if (session !== bound) return
        val dex = bound.legendsZaPokedex ?: return
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Pokédex • Read-only")
            .setItems(dex.sections.map { "${it.section.displayName} • ${it.seenCount}/${it.entries.size} seen • ${it.caughtCount} caught" }.toTypedArray()) { _, index ->
                if (session === bound) showLegendsZaPokedex(bound, dex.sections[index].section, 0)
            }.setNegativeButton("Close", null).show()
    }

    private fun showLegendsZaPokedex(bound: SwitchReadSession, section: LegendsZaDexSection, requestedPage: Int) {
        if (session !== bound) return
        val dex = bound.legendsZaPokedex ?: return
        val table = dex.section(section) ?: return
        val lastPage = (table.entries.size - 1) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${section.displayName} • ${dex.revision?.displayName ?: "Revision unknown"}\n${table.seenCount}/${table.entries.size} seen • ${table.caughtCount} caught\n\n" +
                "Native Pokédex numbering. Hyperspace records require a known DLC revision marker. Saved flags do not establish story/DLC unlocks, rewards or completion."
            textSize = 16f; setTextIsSelectable(true)
        })
        for (entry in table.entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "Pokédex #${entry.number} ${ScarletVioletSpeciesNames.name(entry.speciesId)} • Seen ${entry.seen} • Caught ${entry.caught}"
            setOnClickListener { showLegendsZaDexEntry(bound, section, entry.number) }
        })
        val builder = AlertDialog.Builder(this).setTitle("${section.displayName} Pokédex ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Sections") { _, _ -> if (session === bound) showLegendsZaDexSections(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showLegendsZaPokedex(bound, section, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showLegendsZaPokedex(bound, section, page + 1) }
        builder.show()
    }

    private fun showLegendsZaDexEntry(bound: SwitchReadSession, section: LegendsZaDexSection, number: Int) {
        if (session !== bound) return
        val entry = bound.legendsZaPokedex?.section(section)?.entry(number) ?: return
        fun forms(word: Long): String = (0..31).filter { word and (1L shl it) != 0L }.joinToString().ifBlank { "None" }
        val detail = buildString {
            append("Pokédex #${entry.number} • ${ScarletVioletSpeciesNames.name(entry.speciesId)} (National #${entry.speciesId})\n${section.displayName} • Read-only\n\n")
            append("Seen: ${entry.seen}\nCaught: ${entry.caught}\nShiny flags recorded: ${entry.shinyRecorded}\nAlpha seen: ${entry.alphaSeen} (raw ${entry.rawAlphaFlag})\nNew display: ${entry.newDisplay} (raw ${entry.rawNewFlag})\n\n")
            append("Caught form IDs: ${forms(entry.caughtForms)} (raw ${entry.caughtForms})\nSeen form IDs: ${forms(entry.seenForms)} (raw ${entry.seenForms})\nShiny form IDs: ${forms(entry.shinyForms)} (raw ${entry.shinyForms})\n")
            val genders = listOf("Male", "Female", "Genderless").filterIndexed { bit, _ -> entry.genderFlags and (1 shl bit) != 0 }
            val megaBits = (0..7).filter { entry.megaFlags and (1 shl it) != 0 }
            append("\nSeen genders: ${genders.joinToString().ifBlank { "None" }} (raw ${entry.genderFlags})\nMega flag indices: ${megaBits.joinToString().ifBlank { "None" }} (raw ${entry.megaFlags})\n")
            append("Registered languages: ${entry.languages.joinToString().ifBlank { "None" }} (raw ${entry.languageFlags})\n\n")
            append("Saved display: form ID ${entry.displayForm} • ${entry.displayGender} (raw gender ${entry.rawDisplayGender}) • shiny ${entry.displayShiny} (raw ${entry.rawDisplayShiny})\n\n")
            append("Caught, seen, shiny, Mega, Alpha and display flags remain independent. Mega bits depend on species/revision and are shown as native indices. ")
            append("Unknown bits and contradictory values are preserved. Form IDs are not legality verdicts. No editing or save replacement.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Z-A Pokédex • ${ScarletVioletSpeciesNames.name(entry.speciesId)}")
            .setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showLetsGoInventoryPockets(bound: SwitchReadSession) {
        if (session !== bound) return
        val bag = bound.letsGoInventory ?: return
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Inventory • Read-only")
            .setItems(bag.pockets.map { "${it.pocket.displayName} • ${it.recordedCount}/${it.slots.size} recorded slots" }.toTypedArray()) { _, index ->
                if (session === bound) showLetsGoInventoryPocket(bound, bag.pockets[index].pocket, 0)
            }.setNegativeButton("Close", null).show()
    }

    private fun showLetsGoInventoryPocket(bound: SwitchReadSession, pocket: LetsGoInventoryPocket, requestedPage: Int) {
        if (session !== bound) return
        val bag = bound.letsGoInventory ?: return
        val table = bag.pocket(pocket)
        val entries = table.recordedSlots
        val lastPage = ((entries.size - 1).coerceAtLeast(0)) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${table.recordedCount}/${table.slots.size} recorded slots • ${table.slots.size - table.recordedCount} zero slots\n\n" +
                "Native slot order, including duplicate items and unusual nonzero records. Counts and flags are saved values. Catalogued records can be changed through an explicitly reviewed new copy; unusual records remain locked."
            textSize = 16f; setTextIsSelectable(true)
        })
        val emptySlot = table.slots.firstOrNull { it.isEmpty }
        if (emptySlot != null) root.addView(Button(this).apply {
            text = "＋  Add item • Save new copy"
            isAllCaps = false
            setOnClickListener {
                if (session === bound) launchLetsGoItemEditor(bound, pocket, emptySlot.slot, true)
            }
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "This pocket contains no nonzero saved records."; textSize = 16f })
        for (entry in entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "Slot ${entry.slot} • ${entry.itemName} ×${entry.count}" + (if (entry.isNew) " • New" else "") +
                (if (entry.unknownBits != 0L || entry.exceedsReferenceMax || !entry.listedInPocket || entry.count == 0) " • Check saved details" else "")
            setOnClickListener { showLetsGoInventorySlot(bound, pocket, entry.slot) }
        })
        val builder = AlertDialog.Builder(this).setTitle("${pocket.displayName} ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Pockets") { _, _ -> if (session === bound) showLetsGoInventoryPockets(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showLetsGoInventoryPocket(bound, pocket, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showLetsGoInventoryPocket(bound, pocket, page + 1) }
        builder.show()
    }

    private fun showLetsGoInventorySlot(bound: SwitchReadSession, pocket: LetsGoInventoryPocket, slot: Int) {
        if (session !== bound) return
        val entry = bound.letsGoInventory?.pocket(pocket)?.slot(slot) ?: return
        val detail = buildString {
            append("${pocket.displayName} • Native slot ${entry.slot}\n${entry.itemName} (#${entry.itemId})\n\n")
            append("Saved count: ${entry.count}\nNew flag: ${entry.isNew}\nListed in this pocket's native catalogue: ${entry.listedInPocket}\n")
            append("Reference stack maximum: ${entry.referenceMaxCount?.toString() ?: "Unknown for this record"}\nExceeds reference maximum: ${entry.exceedsReferenceMax}\n\n")
            append("Raw packed value: ${entry.rawValue}\nRaw 15-bit item field: ${entry.rawItemField}\nRaw 15-bit count field: ${entry.rawCountField}\nOther saved bits: ${entry.unknownBits}\n\n")
            append("Item/count display follows the native getter masks. Unusual IDs, zero counts, duplicate slots and unknown bits are retained. ")
            append("Reference catalogues and maxima are descriptive, not a legality verdict. Reserved bits are not labelled favorites. ")
            append(if (entry.listedInPocket && entry.referenceMaxCount != null) "Use Edit item to review a quantity change or removal saved to a new copy." else "This unusual record remains locked.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • ${entry.itemName}")
            .setView(scroll).setNegativeButton("Close", null)
        if (entry.listedInPocket && entry.referenceMaxCount != null) {
            builder.setPositiveButton("Edit item • New copy") { _, _ ->
                if (session === bound) launchLetsGoItemEditor(bound, pocket, entry.slot, false)
            }
        }
        builder.show()
    }

    private fun launchLetsGoItemEditor(
        bound: SwitchReadSession,
        pocket: LetsGoInventoryPocket,
        slot: Int,
        add: Boolean
    ) {
        val source = selectedSourceUri ?: return
        if (session !== bound) return
        startActivity(
            Intent(this, SwitchLetsGoItemActivity::class.java)
                .setData(source)
                .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                .putExtra("pse.switch.item.pocket", pocket.name)
                .putExtra("pse.switch.item.slot", slot)
                .putExtra("pse.switch.item.add", add)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun showSwordShieldInventoryPockets(bound: SwitchReadSession) {
        if (session !== bound) return
        val bag = bound.swordShieldInventory ?: return
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Items")
            .setItems(bag.pockets.map { "${it.pocket.displayName} • ${it.recordedCount}/${it.slots.size} recorded slots" }.toTypedArray()) { _, index ->
                if (session === bound) showSwordShieldInventoryPocket(bound, bag.pockets[index].pocket, 0)
            }.setNegativeButton("Close", null).show()
    }

    private fun showSwordShieldInventoryPocket(bound: SwitchReadSession, pocket: SwordShieldInventoryPocket, requestedPage: Int) {
        if (session !== bound) return
        val bag = bound.swordShieldInventory ?: return
        val table = bag.pocket(pocket)
        val entries = table.recordedSlots
        val lastPage = ((entries.size - 1).coerceAtLeast(0)) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${table.recordedCount}/${table.slots.size} recorded slots • ${table.slots.size - table.recordedCount} zero slots\n\n" +
                "Native slot order, including duplicate items and unusual nonzero records. Counts and flags are saved values. Catalogued records can be changed through an explicitly reviewed new copy; unusual records remain locked."
            textSize = 16f; setTextIsSelectable(true)
        })
        val emptySlot = table.slots.firstOrNull { it.isEmpty }
        if (emptySlot != null) root.addView(Button(this).apply {
            text = "＋  Add item • Save new copy"
            isAllCaps = false
            setOnClickListener {
                if (session === bound) launchSwordShieldItemEditor(bound, pocket, emptySlot.slot, true)
            }
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "This pocket contains no nonzero saved records."; textSize = 16f })
        for (entry in entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "Slot ${entry.slot} • ${entry.itemName} ×${entry.count}" + (if (entry.isNew) " • New" else "") + (if (entry.isFavorite) " • Favorite" else "") +
                (if (entry.unknownBits != 0L || entry.exceedsReferenceMax || !entry.listedInPocket || entry.count == 0) " • Check saved details" else "")
            setOnClickListener { showSwordShieldInventorySlot(bound, pocket, entry.slot) }
        })
        val builder = AlertDialog.Builder(this).setTitle("${pocket.displayName} ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Pockets") { _, _ -> if (session === bound) showSwordShieldInventoryPockets(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showSwordShieldInventoryPocket(bound, pocket, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showSwordShieldInventoryPocket(bound, pocket, page + 1) }
        builder.show()
    }

    private fun showSwordShieldInventorySlot(bound: SwitchReadSession, pocket: SwordShieldInventoryPocket, slot: Int) {
        if (session !== bound) return
        val entry = bound.swordShieldInventory?.pocket(pocket)?.slot(slot) ?: return
        val detail = buildString {
            append("${pocket.displayName} • Native slot ${entry.slot}\n${entry.itemName} (#${entry.itemId})\n\n")
            append("Saved count: ${entry.count}\nNew flag: ${entry.isNew}\nFavorite flag: ${entry.isFavorite}\nListed in this pocket's native catalogue: ${entry.listedInPocket}\n")
            append("Reference stack maximum: ${entry.referenceMaxCount?.toString() ?: "Unknown for this record"}\nExceeds reference maximum: ${entry.exceedsReferenceMax}\n\n")
            append("Raw packed value: ${entry.rawValue}\nRaw 15-bit item field: ${entry.rawItemField}\nRaw 15-bit count field: ${entry.rawCountField}\nOther saved bits: ${entry.unknownBits}\n\n")
            append("Item/count display follows the native getter masks. Unusual IDs, zero counts, duplicate slots and unknown bits are retained. ")
            append("Reference catalogues and maxima are descriptive, not a legality verdict. Favorite is saved bit 31 and remains preserved by quantity edits. ")
            append(if (entry.listedInPocket && entry.referenceMaxCount != null) "Use Edit item to review a quantity change or removal saved to a new copy." else "This unusual record remains locked.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • ${entry.itemName}")
            .setView(scroll).setNegativeButton("Close", null)
        if (entry.listedInPocket && entry.referenceMaxCount != null) {
            builder.setPositiveButton("Edit item • New copy") { _, _ ->
                if (session === bound) launchSwordShieldItemEditor(bound, pocket, entry.slot, false)
            }
        }
        builder.show()
    }

    private fun launchSwordShieldItemEditor(
        bound: SwitchReadSession,
        pocket: SwordShieldInventoryPocket,
        slot: Int,
        add: Boolean
    ) {
        val source = selectedSourceUri ?: return
        if (session !== bound) return
        startActivity(
            Intent(this, SwitchSwordShieldItemActivity::class.java)
                .setData(source)
                .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                .putExtra("pse.switch.item.pocket", pocket.name)
                .putExtra("pse.switch.item.slot", slot)
                .putExtra("pse.switch.item.add", add)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun showBdspInventoryPockets(bound: SwitchReadSession) {
        if (session !== bound) return
        val bag = bound.bdspInventory ?: return
        val labels = bag.pockets.map { "${it.pocket.displayName} • ${it.recordedCount} recorded items" } +
            "Other saved records • ${bag.unlistedRecords.size} unlisted indices"
        val undergroundIndex = if (bound.bdspUnderground != null) labels.size else -1
        val poffinIndex = if (bound.bdspPoffins != null) labels.size + (if (undergroundIndex >= 0) 1 else 0) else -1
        val sealIndex = if (bound.bdspSealStickers != null) labels.size + (if (undergroundIndex >= 0) 1 else 0) + (if (poffinIndex >= 0) 1 else 0) else -1
        val capsuleIndex = if (bound.bdspBallCapsules != null) labels.size + (if (undergroundIndex >= 0) 1 else 0) + (if (poffinIndex >= 0) 1 else 0) + (if (sealIndex >= 0) 1 else 0) else -1
        val contestIndex = if (bound.bdspContest != null) labels.size + (if (undergroundIndex >= 0) 1 else 0) + (if (poffinIndex >= 0) 1 else 0) + (if (sealIndex >= 0) 1 else 0) + (if (capsuleIndex >= 0) 1 else 0) else -1
        val menu = labels + listOfNotNull(
            bound.bdspUnderground?.let { "Grand Underground • Read-only" },
            bound.bdspPoffins?.let { "Poffin Case • Read-only" },
            bound.bdspSealStickers?.let { "Seal Stickers • Read-only" },
            bound.bdspBallCapsules?.let { "Ball Capsules • Read-only" },
            bound.bdspContest?.let { "Contest Records • Read-only" })
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Items")
            .setItems(menu.toTypedArray()) { _, index ->
                if (session === bound) {
                    when {
                        index == contestIndex -> showBdspContest(bound)
                        index == capsuleIndex -> showBdspBallCapsules(bound, 0, false)
                        index == sealIndex -> showBdspSealStickerFilters(bound)
                        index == undergroundIndex -> showBdspUndergroundSections(bound)
                        index == poffinIndex -> showBdspPoffins(bound, 0, false)
                        index in labels.indices -> showBdspInventoryPocket(bound, bag.pockets.getOrNull(index)?.pocket, 0)
                    }
                }
            }.setNegativeButton("Close", null).show()
    }


    private fun showBdspInventoryPocket(bound: SwitchReadSession, pocket: BdspInventoryPocket?, requestedPage: Int) {
        if (session !== bound) return
        val bag = bound.bdspInventory ?: return
        val entries = if (pocket == null) bag.unlistedRecords else bag.pocket(pocket).recordedSlots
        val title = pocket?.displayName ?: "Other saved records"
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${entries.size} nonzero records • ${bag.revision.displayName}\n\n" +
                (if (pocket == null) "Unlisted item indices in native array order. " else "Saved sort-number order; ties use item ID, zero-sort residuals remain last. ") +
                "Counts, flags and sort numbers are saved values. Zero-quantity and unusual records are retained. Catalogued non-empty records can be changed through an explicitly reviewed new copy; unusual records remain locked."
            textSize = 16f; setTextIsSelectable(true)
        })
        val emptyItem = pocket?.let { selectedPocket ->
            BdspInventoryReadOnly.allowedItemIds(selectedPocket).firstOrNull { bag.slot(it).isEmpty }
        }
        if (pocket != null && emptyItem != null) root.addView(Button(this).apply {
            text = "＋  Add item • Save new copy"
            isAllCaps = false
            setOnClickListener {
                if (session === bound) launchBdspItemEditor(bound, pocket, -1, true)
            }
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "No nonzero saved records in this section."; textSize = 16f })
        for (entry in entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "${entry.itemName} (#${entry.itemId}) ×${entry.count} • Sort ${entry.sortOrder}" +
                (if (entry.isNew) " • New" else "") + (if (entry.isFavorite) " • Favorite" else "") +
                (if (!entry.hasSortOrder || entry.hasUnusualMetadata || entry.count == 0) " • Check saved details" else "")
            setOnClickListener { showBdspInventoryItem(bound, entry.itemId) }
        })
        val builder = AlertDialog.Builder(this).setTitle("$title ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Pockets") { _, _ -> if (session === bound) showBdspInventoryPockets(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspInventoryPocket(bound, pocket, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspInventoryPocket(bound, pocket, page + 1) }
        builder.show()
    }

    private fun showBdspInventoryItem(bound: SwitchReadSession, itemId: Int) {
        if (session !== bound) return
        val entry = bound.bdspInventory?.slot(itemId) ?: return
        val detail = buildString {
            append("${entry.itemName} (#${entry.itemId}) • Read-only\n${entry.pocket?.displayName ?: "Not in the native pocket catalogue"}\n\n")
            append("Saved signed count: ${entry.count}\nNew: ${entry.isNew} (seen word equals 0)\nFavorite: ${entry.isFavorite} (word equals 1)\n")
            append("Saved sort number: ${entry.sortOrder}\nHas native obtained/sort marker: ${entry.hasSortOrder}\n")
            append("Reference maximum: ${entry.referenceMaxCount?.toString() ?: "Unknown"}\nExceeds reference maximum: ${entry.exceedsReferenceMax}\n\n")
            append("Raw count word: ${entry.rawCount}\nRaw seen word: ${entry.rawSeenFlag}\nRaw favorite word: ${entry.rawFavoriteFlag}\nAlignment word: ${entry.alignment}\n\n")
            append("Item ID is the native array index. Full-width flag getters use exact equality, not a low bit or nonzero check. ")
            append("Zero quantities, negative counts, conflicting markers and alignment bytes are retained. Catalogue/maxima are descriptive, not a legality verdict. ")
            append("A completely zero record has no saved sort marker and is hidden from this list, even though the native New getter returns true. ")
            append(if (entry.pocket != null && entry.count > 0 && entry.referenceMaxCount != null) "Use Edit item to review a quantity change or removal saved to a new copy." else "This unusual record remains locked.")
        }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • ${entry.itemName}")
            .setView(scroll).setNegativeButton("Close", null)
        val selectedPocket = entry.pocket
        if (selectedPocket != null && entry.count > 0 && entry.referenceMaxCount != null) {
            builder.setPositiveButton("Edit item • New copy") { _, _ ->
                if (session === bound) launchBdspItemEditor(bound, selectedPocket, entry.itemId, false)
            }
        }
        builder.show()
    }

    private fun launchBdspItemEditor(
        bound: SwitchReadSession,
        pocket: BdspInventoryPocket,
        itemId: Int,
        add: Boolean
    ) {
        val source = selectedSourceUri ?: return
        if (session !== bound) return
        startActivity(
            Intent(this, SwitchBdspItemActivity::class.java)
                .setData(source)
                .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                .putExtra("pse.switch.item.pocket", pocket.name)
                .putExtra("pse.switch.item.id", itemId)
                .putExtra("pse.switch.item.add", add)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun showLegendsArceusInventoryPockets(bound: SwitchReadSession) {
        if (session !== bound) return
        val bag = bound.legendsArceusInventory ?: return
        val labels = bag.pockets.map { "${it.name} • ${it.recordedItems.size} recorded slots" } +
            "Favorite flags • ${bag.favoriteItemIds.size} saved flags"
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Items")
            .setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showLegendsArceusInventoryPocket(bound, index, 0)
            }.setNegativeButton("Close", null).show()
    }

    private fun showLegendsArceusInventoryPocket(bound: SwitchReadSession, index: Int, requestedPage: Int) {
        if (session !== bound) return
        val bag = bound.legendsArceusInventory ?: return
        val pocket = bag.pockets.getOrNull(index)
        if (pocket == null && index != bag.pockets.size) return
        val total = pocket?.recordedItems?.size ?: bag.favoriteItemIds.size
        val lastPage = (total - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val title = pocket?.name ?: "Favorite flags"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = if (pocket == null) "Saved favorite flags include items absent from the bag. A favorite flag does not prove ownership."
                else "${pocket.capacity} active slots • ${pocket.items.size} physical slots • ${pocket.residualCount} recorded beyond active capacity\n" +
                    "Satchel upgrades: ${bag.rawSatchelUpgrades}\nSaved order and signed quantities are retained. Catalogued records in active slots can be changed through a reviewed new copy; unusual and residual records remain locked."
            textSize = 16f; setTextIsSelectable(true)
        })
        val emptySlot = pocket?.takeIf { it.allowsCataloguedAdd }?.items
            ?.firstOrNull { it.withinActiveCapacity && !it.isRecorded }
        if (pocket != null && emptySlot != null) root.addView(Button(this).apply {
            text = "＋  Add item • Save new copy"
            isAllCaps = false
            setOnClickListener {
                if (session === bound) launchLegendsArceusItemEditor(bound, index, emptySlot.slot, true)
            }
        })
        if (total == 0) root.addView(TextView(this).apply { text = "No saved records in this section."; textSize = 16f })
        if (pocket != null) for (entry in pocket.recordedItems.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "Slot ${entry.slot + 1}: ${entry.itemName} ×${entry.count}" +
                (if (entry.isFavorite) " • Favorite" else "") + (if (!entry.withinActiveCapacity) " • Beyond active capacity" else "")
            setOnClickListener { showLegendsArceusInventoryItem(bound, index, entry.slot) }
        }) else for (id in bag.favoriteItemIds.drop(page * 20).take(20)) root.addView(TextView(this).apply {
            text = "${LegendsArceusItemNames.name(id)} (#$id) • Favorite"; textSize = 16f; setTextIsSelectable(true)
        })
        val builder = AlertDialog.Builder(this).setTitle("$title ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Pockets") { _, _ -> if (session === bound) showLegendsArceusInventoryPockets(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showLegendsArceusInventoryPocket(bound, index, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showLegendsArceusInventoryPocket(bound, index, page + 1) }
        builder.show()
    }

    private fun showLegendsArceusInventoryItem(bound: SwitchReadSession, pocketIndex: Int, slot: Int) {
        if (session !== bound) return
        val pocket = bound.legendsArceusInventory?.pockets?.getOrNull(pocketIndex) ?: return
        val entry = pocket.items.getOrNull(slot) ?: return
        val editable = entry.withinActiveCapacity && entry.count > 0 &&
            LegendsArceusItemNames.isCatalogued(entry.itemId)
        val detail = "${entry.itemName} (#${entry.itemId}) • Read-only\n${pocket.name} • Slot ${slot + 1}\n\n" +
            "Saved signed quantity: ${entry.count}\nRaw item word: ${entry.rawItemId}\nRaw quantity word: ${entry.rawCount}\n" +
            "Favorite: ${entry.isFavorite}\nWithin active capacity: ${entry.withinActiveCapacity}\nReference maximum: ${pocket.referenceMaxCount}\n\n" +
            "Duplicate IDs, zero or negative quantities and records beyond the active satchel are retained. Reference maxima are descriptive; no legality or ownership is inferred. " +
            if (editable) "Use Edit item to review a quantity change or removal saved to a new copy. The independent favorite flag remains unchanged."
            else "This unusual or inactive record remains locked."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • ${entry.itemName}")
            .setView(scroll).setNegativeButton("Close", null)
        if (editable) builder.setPositiveButton("Edit item • New copy") { _, _ ->
            if (session === bound) launchLegendsArceusItemEditor(bound, pocketIndex, slot, false)
        }
        builder.show()
    }

    private fun launchLegendsArceusItemEditor(
        bound: SwitchReadSession,
        pocketIndex: Int,
        slot: Int,
        add: Boolean
    ) {
        val source = selectedSourceUri ?: return
        if (session !== bound) return
        startActivity(
            Intent(this, SwitchLegendsArceusItemActivity::class.java)
                .setData(source)
                .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                .putExtra("pse.switch.item.pocket", pocketIndex)
                .putExtra("pse.switch.item.slot", slot)
                .putExtra("pse.switch.item.add", add)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun showScarletVioletInventoryPockets(bound: SwitchReadSession) {
        if (session !== bound) return
        val bag = bound.scarletVioletInventory ?: return
        val labels = bag.pockets.map { "${it.pocket.displayName} • ${it.recordedSlots.size} recorded items" } +
            "Other saved records • ${bag.unlistedRecords.size} unlisted indices"
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Items")
            .setItems(labels.toTypedArray()) { _, index ->
                if (session === bound) showScarletVioletInventoryPocket(bound, index, 0)
            }.setNegativeButton("Close", null).show()
    }

    private fun showScarletVioletInventoryPocket(bound: SwitchReadSession, index: Int, requestedPage: Int) {
        if (session !== bound) return
        val bag = bound.scarletVioletInventory ?: return
        val pocket = bag.pockets.getOrNull(index)
        if (pocket == null && index != bag.pockets.size) return
        val entries = pocket?.recordedSlots ?: bag.unlistedRecords
        val title = pocket?.pocket?.displayName ?: "Other saved records"
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${entries.size} recorded items in native item-ID order. Zero quantities, unknown flags and mismatched saved pockets remain visible. Normal catalogued records can be changed through an explicitly reviewed new copy; unusual records remain locked."
            textSize = 16f; setTextIsSelectable(true)
        })
        if (pocket != null && ScarletVioletInventoryReadOnly.allowedItemIds(pocket.pocket)
                .any { !bag.slot(it).isRecorded }) root.addView(Button(this).apply {
            text = "＋  Add item • Save new copy"
            isAllCaps = false
            setOnClickListener {
                if (session === bound) launchScarletVioletItemEditor(bound, pocket.pocket, -1, true)
            }
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "No saved records in this section."; textSize = 16f })
        for (entry in entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "${entry.itemName} (#${entry.itemId}) ×${entry.count}" +
                (if (entry.isNew) " • New" else "") + (if (entry.isFavorite) " • Favorite" else "") +
                (if (!entry.isObtained || !entry.pouchMatchesCatalogue) " • Check saved details" else "")
            setOnClickListener { showScarletVioletInventoryItem(bound, entry.itemId) }
        })
        val builder = AlertDialog.Builder(this).setTitle("$title ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Pockets") { _, _ -> if (session === bound) showScarletVioletInventoryPockets(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showScarletVioletInventoryPocket(bound, index, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showScarletVioletInventoryPocket(bound, index, page + 1) }
        builder.show()
    }

    private fun showScarletVioletInventoryItem(bound: SwitchReadSession, itemId: Int) {
        if (session !== bound) return
        val entry = bound.scarletVioletInventory?.slot(itemId) ?: return
        val detail = "${entry.itemName} (#${entry.itemId}) • Read-only\n${entry.pocket?.displayName ?: "Not in the native catalogue"}\n\n" +
            "Saved signed quantity: ${entry.count}\nRaw quantity word: ${entry.rawCount}\nSaved pouch word: ${entry.rawPouch}\n" +
            "Matches catalogue pocket: ${entry.pouchMatchesCatalogue}\nNew: ${entry.isNew}\nFavorite: ${entry.isFavorite}\nObtained flag: ${entry.isObtained}\n" +
            "Raw flag word: ${entry.rawFlags}\nPadding word: ${entry.padding}\nReference maximum: ${entry.referenceMaxCount?.toString() ?: "Unknown"}\n\n" +
            "The item ID is the saved array index. Catalogue pockets and maxima describe the format; they do not establish legality or ownership. Empty 0/FFFFFFFF pouch-only records are hidden from lists. All 3,000 records remain in the snapshot. " +
            if (entry.isEditable) "Use Edit item to review a quantity change or removal saved to a new copy. New and favorite flags remain unchanged for quantity edits."
            else "This unusual or mismatched record remains locked."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • ${entry.itemName}")
            .setView(scroll).setNegativeButton("Close", null)
        val selectedPocket = entry.pocket
        if (selectedPocket != null && entry.isEditable) builder.setPositiveButton("Edit item • New copy") { _, _ ->
            if (session === bound) launchScarletVioletItemEditor(bound, selectedPocket, entry.itemId, false)
        }
        builder.show()
    }

    private fun launchScarletVioletItemEditor(
        bound: SwitchReadSession,
        pocket: ScarletVioletInventoryPocket,
        itemId: Int,
        add: Boolean
    ) {
        val source = selectedSourceUri ?: return
        if (session !== bound) return
        startActivity(
            Intent(this, SwitchScarletVioletItemActivity::class.java)
                .setData(source)
                .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                .putExtra("pse.switch.item.pocket", pocket.name)
                .putExtra("pse.switch.item.id", itemId)
                .putExtra("pse.switch.item.add", add)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }


    private fun showLegendsZaInventoryPockets(bound: SwitchReadSession) {
        if (session !== bound) return
        val bag = bound.legendsZaInventory ?: return
        val labels = bag.pockets.map { "${it.pocket.displayName} • ${if (it.unlocked) "Unlocked" else "Locked"} • ${it.recordedSlots.size} recorded items" } +
            "Other saved records • ${bag.unlistedRecords.size} unlisted indices"
        val menu = labels + "Bag flags and metadata"
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Items")
            .setItems(menu.toTypedArray()) { _, index ->
                if (session === bound) showLegendsZaInventoryPocket(bound, index, 0)
            }.setNegativeButton("Close", null).show()
    }

    private fun showLegendsZaInventoryPocket(bound: SwitchReadSession, index: Int, requestedPage: Int) {
        if (session !== bound) return
        val bag = bound.legendsZaInventory ?: return
        if (index == bag.pockets.size + 1) { showLegendsZaInventoryMetadata(bound); return }
        val pocket = bag.pockets.getOrNull(index)
        if (pocket == null && index != bag.pockets.size) return
        val entries = pocket?.recordedSlots ?: bag.unlistedRecords
        val title = pocket?.pocket?.displayName ?: "Other saved records"
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${entries.size} recorded items in native item-ID order. Zero quantities, unknown flags and mismatched saved pockets remain visible. Normal catalogued records in unlocked pockets can be changed through an explicitly reviewed new copy; unusual records remain locked."
            textSize = 16f; setTextIsSelectable(true)
        })
        if (pocket != null && pocket.unlocked && LegendsZaInventoryReadOnly.allowedItemIds(pocket.pocket).any { id ->
                val slot = bag.slot(id)
                !slot.isRecorded && id !in setOf(2612, 2619) && (slot.referenceMaxCount ?: 0) > 0
            }) root.addView(Button(this).apply {
            text = "＋  Add item • Save new copy"
            isAllCaps = false
            setOnClickListener {
                if (session === bound) launchLegendsZaItemEditor(bound, pocket.pocket, -1, true)
            }
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "No saved records in this section."; textSize = 16f })
        for (entry in entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "${entry.itemName} (#${entry.itemId}) ×${entry.count}" +
                (if (entry.isNew) " • New" else "") + (if (entry.isFavorite) " • Favorite" else "") +
                (if (!entry.hasValidPouch || !entry.pouchMatchesCatalogue) " • Check saved details" else "")
            setOnClickListener { showLegendsZaInventoryItem(bound, entry.itemId) }
        })
        val builder = AlertDialog.Builder(this).setTitle("$title ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Pockets") { _, _ -> if (session === bound) showLegendsZaInventoryPockets(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showLegendsZaInventoryPocket(bound, index, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showLegendsZaInventoryPocket(bound, index, page + 1) }
        builder.show()
    }

    private fun showLegendsZaInventoryItem(bound: SwitchReadSession, itemId: Int) {
        if (session !== bound) return
        val entry = bound.legendsZaInventory?.slot(itemId) ?: return
        val detail = "${entry.itemName} (#${entry.itemId}) • Read-only\n${entry.pocket?.displayName ?: "Not in the native catalogue"}\n\n" +
            "Saved signed quantity: ${entry.count}\nRaw quantity word: ${entry.rawCount}\nSaved pouch word: ${entry.rawPouch}\n" +
            "Matches catalogue pocket: ${entry.pouchMatchesCatalogue}\nNew: ${entry.isNew}\nFavorite: ${entry.isFavorite}\nValid pouch marker: ${entry.hasValidPouch}\nNew pickup notification: ${entry.isNewNotify}\nNew in shop: ${entry.isNewShop}\nHeld flag: ${entry.isHeld}\n" +
            "Raw flag word: ${entry.rawFlags}\nPadding word: ${entry.padding}\nReference maximum: ${entry.referenceMaxCount?.toString() ?: "Unknown"}\n\n" +
            "The item ID is the saved array index. Catalogue pockets and maxima describe the format; they do not establish legality or ownership. Native zero/unacquired templates are hidden from lists. Held flags are saved metadata and do not prove a current Pokémon relationship. All 3,000 records remain in the snapshot. " +
            if (entry.isEditable && entry.pocket?.let { p -> bound.legendsZaInventory?.pockets?.firstOrNull { it.pocket == p }?.unlocked } == true)
                "Use Edit item to review a quantity change or removal saved to a new copy. Saved new, favorite, shop, and held flags remain unchanged for quantity edits."
            else "This unusual, special-count, mismatched, or locked-pocket record remains locked."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • ${entry.itemName}")
            .setView(scroll).setNegativeButton("Close", null)
        val selectedPocket = entry.pocket
        if (selectedPocket != null && entry.isEditable &&
            bound.legendsZaInventory?.pockets?.firstOrNull { it.pocket == selectedPocket }?.unlocked == true) {
            builder.setPositiveButton("Edit item • New copy") { _, _ ->
                if (session === bound) launchLegendsZaItemEditor(bound, selectedPocket, entry.itemId, false)
            }
        }
        builder.show()
    }

    private fun launchLegendsZaItemEditor(
        bound: SwitchReadSession,
        pocket: LegendsZaInventoryPocket,
        itemId: Int,
        add: Boolean
    ) {
        val source = selectedSourceUri ?: return
        if (session !== bound) return
        startActivity(
            Intent(this, SwitchLegendsZaItemActivity::class.java)
                .setData(source)
                .putExtra("pse.switch.sha256", bound.snapshot.sourceSha256)
                .putExtra("pse.switch.item.pocket", pocket.name)
                .putExtra("pse.switch.item.id", itemId)
                .putExtra("pse.switch.item.add", add)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun showLegendsZaInventoryMetadata(bound: SwitchReadSession) {
        if (session !== bound) return
        val bag = bound.legendsZaInventory ?: return
        val detail = "${bag.revision?.displayName ?: "Revision unknown"} • Read-only\n\n" +
            "Saved pocket flags: ${bag.rawPouchFlags}\nAdditional pocket flag (bit 8): ${bag.additionalPocketFlag}\n" +
            "Mega Shard reference limit: ${if (bag.megaShardMaxPatched) 9999 else 999}\n\n" +
            bag.pockets.joinToString("\n") { "${it.pocket.displayName}: ${if (it.unlocked) "Unlocked" else "Locked"}" } +
            "\n\nRaw bag tail (128 bytes):\n" + bag.tailBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } }
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Bag flags")
            .setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun showBdspUndergroundSections(bound: SwitchReadSession) {
        if (session !== bound) return
        val underground = bound.bdspUnderground ?: return
        val kinds = BdspUndergroundKind.values()
        val labels = kinds.map { "${it.displayName} • ${underground.entries(it).size} recorded" } +
            "Other saved records • ${underground.entries(null).size} unlisted indices"
        val activityIndex = if (bound.bdspUndergroundActivity != null) labels.size else -1
        val secretBaseIndex = if (bound.bdspSecretBase != null) labels.size + (if (activityIndex >= 0) 1 else 0) else -1
        val npcIndex = if (bound.bdspUndergroundNpc != null) labels.size + (if (activityIndex >= 0) 1 else 0) + (if (secretBaseIndex >= 0) 1 else 0) else -1
        val returnDataIndex = if (bound.bdspUndergroundReturn != null) labels.size + (if (activityIndex >= 0) 1 else 0) + (if (secretBaseIndex >= 0) 1 else 0) + (if (npcIndex >= 0) 1 else 0) else -1
        val menu = labels + listOfNotNull(bound.bdspUndergroundActivity?.let { "Activity records • Read-only" },
            bound.bdspSecretBase?.let { "Secret Base • Read-only" },
            bound.bdspUndergroundNpc?.let { "NPC interaction records • Read-only" },
            bound.bdspUndergroundReturn?.let { "Saved return data • Read-only" })
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Grand Underground • Read-only")
            .setItems(menu.toTypedArray()) { _, index ->
                if (session === bound) {
                    if (index == returnDataIndex) showBdspUndergroundReturn(bound)
                    else if (index == npcIndex) showBdspUndergroundNpcFilters(bound)
                    else if (index == secretBaseIndex) showBdspSecretBase(bound)
                    else if (index == activityIndex) showBdspUndergroundActivity(bound)
                    else if (index in labels.indices) showBdspUndergroundSection(bound, kinds.getOrNull(index), 0, false)
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun showBdspUndergroundSection(bound: SwitchReadSession, kind: BdspUndergroundKind?,
        requestedPage: Int, includeEmpty: Boolean) {
        if (session !== bound) return
        val underground = bound.bdspUnderground ?: return
        val entries = underground.entries(kind, includeEmpty)
        val title = kind?.displayName ?: "Other saved records"
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${entries.size} ${if (includeEmpty) "total" else "recorded"} entries in Underground ID order. Select an entry for details. Quantities and flags are shown as saved."
            textSize = 16f; setTextIsSelectable(true)
        })
        root.addView(Button(this).apply {
            text = if (includeEmpty) "Show recorded entries" else "Include empty entries"
            setOnClickListener { if (session === bound) showBdspUndergroundSection(bound, kind, 0, !includeEmpty) }
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "No saved records in this section."; textSize = 16f })
        for (entry in entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "${entry.name} (UG #${entry.undergroundId}) ×${entry.count}" +
                (if (entry.isNew) " • New" else "") + (if (entry.isFavorite) " • Favorite" else "") +
                (if (entry.hasUnusualMetadata) " • Check saved details" else "")
            setOnClickListener { showBdspUndergroundItem(bound, entry.undergroundId) }
        })
        val builder = AlertDialog.Builder(this).setTitle("$title ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Sections") { _, _ -> if (session === bound) showBdspUndergroundSections(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspUndergroundSection(bound, kind, page - 1, includeEmpty) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspUndergroundSection(bound, kind, page + 1, includeEmpty) }
        builder.show()
    }

    private fun showBdspUndergroundItem(bound: SwitchReadSession, undergroundId: Int) {
        if (session !== bound) return
        val entry = bound.bdspUnderground?.slot(undergroundId) ?: return
        val definition = entry.catalogue
        val detail = "${entry.name} • Read-only\nUnderground ID: ${entry.undergroundId}\n" +
            "${entry.kind?.displayName ?: "Unlisted index"}\n\nSaved signed quantity: ${entry.count}\nRaw quantity word: ${entry.rawCount}\n" +
            "New: ${entry.isNew}\nFavorite: ${entry.isFavorite}\nRaw hide-new word: ${entry.rawHideNewFlag}\nRaw favorite word: ${entry.rawFavoriteFlag}\n" +
            (definition?.let { "${it.kind.referenceLabel}: ${it.nativeReferenceId}\n" } ?: "No catalogue mapping\n") +
            "Reference maximum: ${entry.referenceMaxCount?.toString() ?: "Unknown"}\n\n" +
            "Underground IDs differ from normal bag IDs. All 999 entries remain in the snapshot, including zero quantities, unknown indices and nonstandard flag words. Catalogue mappings and maxima are descriptive; they do not prove ownership or legality. Statue inventory does not describe Secret Base placement or active effects."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • ${entry.name}")
            .setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun showBdspNursery(bound: SwitchReadSession) {
        if (session !== bound) return
        val nursery = bound.bdspNursery ?: return
        val eggState = when (nursery.rawEggAvailableFlag) { 0L -> "Not ready"; 1L -> "Ready"; else -> "Unknown saved flag (${nursery.rawEggAvailableFlag})" }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${bound.displayName} • Nursery • Read-only\n\n" +
                "Egg availability: $eggState\nSaved egg step count: ${nursery.eggStepCount}\n\n" +
                "${nursery.decodedParentCount} decoded parents • ${nursery.unreadableParentCount} unreadable records\n" +
                "Select a parent for its saved Pokémon details."
            textSize = 16f; setTextIsSelectable(true)
        })
        for (parent in nursery.parents) root.addView(Button(this).apply {
            text = "Parent ${parent.position} • ${bdspLabel(parent.read)}"
            setOnClickListener { if (session === bound) showBdspPokemon(bound, "Nursery parent ${parent.position}", parent.read) }
        })
        root.addView(Button(this).apply {
            text = "Saved egg metadata"
            setOnClickListener { if (session === bound) showBdspNurseryMetadata(bound) }
        })
        root.addView(TextView(this).apply {
            text = "Read-only saved state. Parent compatibility, future eggs and time until an egg are not calculated. Unreadable parents stay separate from empty slots; egg metadata remains inspectable."
            textSize = 16f
        })
        AlertDialog.Builder(this).setTitle("Nursery • Read-only")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Close", null).show()
    }

    private fun showBdspNurseryMetadata(bound: SwitchReadSession) {
        if (session !== bound) return
        val nursery = bound.bdspNursery ?: return
        val detail = "${nursery.version.displayName} • Save layout ${nursery.revision.displayName}\n\n" +
            "Raw egg-available word: ${nursery.rawEggAvailableFlag}\nNative flag equals 1: ${nursery.isEggAvailable}\n" +
            "Canonical flag word: ${nursery.hasCanonicalEggFlag}\nSaved step count (signed): ${nursery.eggStepCount}\n" +
            "Raw step word: ${nursery.rawEggStepCount}\nSaved seed (unsigned 64-bit): ${nursery.eggSeed}\nSaved seed (hex): ${nursery.eggSeedHex}\n\n" +
            "The complete seed is preserved, including any saved sign extension. No seed generation, RNG advance, counter reset or repair is performed. Cached parent stats may be stale."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Nursery metadata • Read-only").setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun showBdspBattleTower(bound: SwitchReadSession) {
        if (session !== bound) return
        val tower = bound.bdspBattleTower ?: return
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${bound.displayName} • Battle Tower • Read-only\n\n" +
                "Battle Points: ${tower.battlePoints}\nSaved maximum Master Rank: ${tower.maximumMasterRank}\n" +
                "Current mode: ${BdspBattleTowerMode.label(tower.rawCurrentMode)}\nPrevious mode: ${BdspBattleTowerMode.label(tower.rawPreviousMode)}\n" +
                "Saved daily challenge counter: ${tower.dailyChallengeCount}\nSaved challenge counter: ${tower.challengeCount}\n\nSelect a mode for its saved records."
            textSize = 16f; setTextIsSelectable(true)
        })
        for (record in tower.records) root.addView(Button(this).apply {
            text = "${record.mode.displayName} • Rank ${record.rank} • Streak ${record.currentStreak}"
            setOnClickListener { if (session === bound) showBdspBattleTowerRecord(bound, record.mode) }
        })
        root.addView(Button(this).apply {
            text = "Saved Tower metadata"
            setOnClickListener { if (session === bound) showBdspBattleTowerMetadata(bound) }
        })
        root.addView(TextView(this).apply {
            text = "All four modes remain readable, including inactive modes. Values are shown as saved; ranks, counters and their consistency are not recalculated."
            textSize = 16f
        })
        AlertDialog.Builder(this).setTitle("Battle Tower • Read-only")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Close", null).show()
    }

    private fun showBdspBattleTowerRecord(bound: SwitchReadSession, mode: BdspBattleTowerMode) {
        if (session !== bound) return
        val record = bound.bdspBattleTower?.record(mode) ?: return
        val suspended = when (record.rawSuspendedFlag) { 0L -> "No"; 1L -> "Yes"; else -> "Unknown (${record.rawSuspendedFlag})" }
        val detail = "${mode.displayName} • Read-only\n\n" +
            "Saved rank: ${record.rank}\nRank-down loss counter: ${record.rankDownLosses}\n" +
            "Current streak: ${record.currentStreak}\nPrevious streak counter: ${record.previousStreak}\n" +
            "Total win counter: ${record.totalWins}\nWin loop counter: ${record.winLoopCounter}\nLoss counter: ${record.lossCounter}\n" +
            "Suspended: $suspended\nRaw suspension word: ${record.rawSuspendedFlag}\nRaw cleared byte: ${record.rawClearedFlag}\n\n" +
            "Battle seed (hex): ${record.battleSeed.toString(16).uppercase().padStart(16, '0')}\n" +
            record.trainerSeeds.mapIndexed { i, seed -> "Trainer seed ${i + 1}: ${seed.toString(16).uppercase().padStart(16, '0')}" }.joinToString("\n") +
            "\n\nRaw mode record (104 bytes):\n" + record.rawBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nSeeds and counters are saved data only. No trainer prediction, rank repair or RNG advance is performed. The previous streak counter is not recomputed as a lifetime record."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("${mode.displayName} • Read-only").setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showBdspBattleTowerMetadata(bound: SwitchReadSession) {
        if (session !== bound) return
        val tower = bound.bdspBattleTower ?: return
        val detail = "Save layout ${tower.revision.displayName} • Read-only\n\n" +
            "Raw maximum Master Rank word: ${tower.rawMaximumMasterRank}\nCurrent mode ID: ${tower.rawCurrentMode}\nPrevious mode ID: ${tower.rawPreviousMode}\n" +
            "\nRaw Battle Tower block (440 bytes):\n" + tower.rawBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nAll saved bytes remain exact, including alignment and unclassified fields. Whole-file integrity passed before reading."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Tower metadata • Read-only").setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun showBdspPoffins(bound: SwitchReadSession, requestedPage: Int, includeEmpty: Boolean) {
        if (session !== bound) return
        val poffins = bound.bdspPoffins ?: return
        val entries = poffins.entries(includeEmpty)
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${bound.displayName} • Poffin Case • Read-only\n\n" +
                "${poffins.knownPoffinCount} known Poffins • ${poffins.unknownTypeCount} unknown records • ${poffins.emptyCount} empty slots\n" +
                "Saved cooking count: ${poffins.cookingCount}\n\n" +
                "${if (includeEmpty) "All slots" else "Non-empty records"} in saved slot order. Select a slot for its flavours and raw details."
            textSize = 16f; setTextIsSelectable(true)
        })
        root.addView(Button(this).apply {
            text = if (includeEmpty) "Hide empty slots" else "Include empty slots"
            setOnClickListener { if (session === bound) showBdspPoffins(bound, 0, !includeEmpty) }
        })
        root.addView(Button(this).apply {
            text = "Saved case metadata"
            setOnClickListener { if (session === bound) showBdspPoffinMetadata(bound) }
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "All 100 saved slots have the empty type marker."; textSize = 16f })
        for (entry in entries.drop(page * 20).take(20)) root.addView(Button(this).apply {
            text = "Slot ${entry.position} • ${entry.name}" + (if (!entry.isEmpty) " • Lv ${entry.level} • Smooth ${entry.smoothness}" else "") +
                (if (entry.isNew) " • New" else "")
            setOnClickListener { showBdspPoffin(bound, entry.index) }
        })
        val builder = AlertDialog.Builder(this).setTitle("Poffin Case ${page + 1}/${lastPage + 1} • Read-only")
            .setView(ScrollView(this).apply { addView(root) }).setPositiveButton("Close", null)
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspPoffins(bound, page - 1, includeEmpty) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspPoffins(bound, page + 1, includeEmpty) }
        builder.show()
    }

    private fun showBdspPoffin(bound: SwitchReadSession, index: Int) {
        if (session !== bound) return
        val entry = bound.bdspPoffins?.slot(index) ?: return
        val detail = "Slot ${entry.position} • ${entry.name} • Read-only\n\n" +
            "Saved type ID: ${entry.typeId}\nEmpty marker: ${entry.isEmpty}\nSaved level: ${entry.level}\nSaved smoothness/feel: ${entry.smoothness}\n" +
            "Saved flavours (Spicy / Dry / Sweet / Bitter / Sour):\n${entry.flavors.joinToString(" / ")}\n\n" +
            "New: ${entry.isNew}\nRaw new word: ${entry.rawNewFlag}\nCanonical new word: ${entry.hasCanonicalNewFlag}\n\n" +
            "Raw slot (16 bytes):\n${entry.rawBytes.joinToString(" ") { "%02X".format(it) }}\n\n" +
            "Only type 255 marks an empty slot. Empty slots may retain other saved data. Unknown types, unusual flags and padding are preserved. Type, level and flavours are not recalculated or used to modify Pokémon contest stats."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Poffin slot ${entry.position} • Read-only").setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun showBdspPoffinMetadata(bound: SwitchReadSession) {
        if (session !== bound) return
        val poffins = bound.bdspPoffins ?: return
        val detail = "Save layout ${poffins.revision.displayName} • Read-only\n\n" +
            "Saved cooking count (signed): ${poffins.cookingCount}\nRaw cooking word: ${poffins.rawCookingCount}\n\n" +
            "Raw Poffin case (1,604 bytes):\n" + poffins.rawBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nAll 100 slots stay in native order. No sorting, feeding, cleanup or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Poffin case metadata • Read-only").setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun bdspAppearanceSummary(appearance: BdspTrainerAppearanceRead): String {
        fun coordinate(value: Float): String = if (value.isFinite()) value.toString() else "Non-finite saved value ($value)"
        val dsPlayer = when (appearance.rawDsPlayerFlag) { 0 -> "Off"; 1 -> "On"; else -> "Unknown (${appearance.rawDsPlayerFlag})" }
        return "Appearance and position • Read-only\n" +
            "Saved outfit: ${appearance.fashionName ?: "Unknown fashion #${appearance.fashionId}"}\n" +
            "Outfit ID: ${appearance.fashionId}\nBody type ID: ${appearance.bodyTypeId}\nTrainer view ID: ${appearance.trainerViewId}\n" +
            "Saved region byte: ${appearance.regionCode}\nSaved starter-type ID: ${appearance.starterTypeId}\nDS Player flag: $dsPlayer\n" +
            "Follower index: ${appearance.followerIndex}\nSaved grid X / Y: ${appearance.gridX} / ${appearance.gridY}\n" +
            "Saved height: ${coordinate(appearance.height)}\nSaved rotation: ${coordinate(appearance.rotation)}\n"
    }

    private fun showBdspAppearance(bound: SwitchReadSession) {
        if (session !== bound) return
        val appearance = bound.bdspTrainerAppearance ?: return
        val detail = "${bound.displayName} • Save layout ${appearance.revision.displayName}\n\n" +
            bdspAppearanceSummary(appearance) +
            "\nRaw height bits: ${appearance.rawHeightBits.toString(16).uppercase().padStart(8, '0')}\n" +
            "Raw rotation bits: ${appearance.rawRotationBits.toString(16).uppercase().padStart(8, '0')}\n" +
            "Raw DS Player byte: ${appearance.rawDsPlayerFlag}\n\n" +
            "Raw native trainer status (80 bytes):\n" + appearance.rawStatusBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nThese are saved IDs and coordinates. Clothing unlocks, outfit compatibility, follower identity, map validity and coordinate units are not inferred. Non-finite values keep their original raw bits. No editing, teleporting or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Appearance and position • Read-only")
            .setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun bdspTimestampSummary(timestamps: BdspTimestampsRead): String = buildString {
        append("Saved timestamps • UTC • Read-only\n")
        for (entry in timestamps.entries) {
            append("${entry.kind.displayName}: ${entry.utcTimestamp ?: "Outside supported FILETIME range"}\n")
            append("Saved count: ${entry.count}; raw FILETIME ticks: ${entry.fileTimeTicks}\n")
        }
        append("These are saved values, not elapsed playtime. Zero FILETIME displays its native 1601 epoch. Penalty activity and daily-event eligibility are not inferred.\n")
    }

    private fun showBdspTimestamps(bound: SwitchReadSession) {
        if (session !== bound) return
        val timestamps = bound.bdspTimestamps ?: return
        val detail = "${bound.displayName} • Save layout ${timestamps.revision.displayName}\n\n" +
            bdspTimestampSummary(timestamps) + "\nRaw FILETIME words (hex):\n" +
            timestamps.entries.joinToString("\n") { "${it.kind.displayName}: ${java.lang.Long.toHexString(it.fileTimeTicks).uppercase().padStart(16, '0')}" } +
            "\n\nRaw native system data (312 bytes):\n" + timestamps.rawSystemBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nDates use UTC and retain 100-nanosecond precision. Out-of-range values keep their signed ticks and exact raw words without hiding other dates. Counts are saved counters with no inferred duration. Clock snapshot, alignment and reserved bytes are retained without interpretation. No clock changes, timer reset, editing or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Saved timestamps • UTC • Read-only")
            .setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun bdspPlayTimeSummary(playTime: BdspPlayTimeRead): String = buildString {
        append("Recorded playtime • Read-only\n")
        append("Saved hours:minutes:seconds: ${playTime.savedTimeText}\n")
        if (!playTime.hasCanonicalMinutesAndSeconds)
            append("Saved minutes or seconds are outside 0–59. Values are shown unchanged; this is not a normalized duration.\n")
        append("Hours: ${playTime.hours}; minutes: ${playTime.minutes}; seconds: ${playTime.seconds}\n")
    }

    private fun showBdspPlayTime(bound: SwitchReadSession) {
        if (session !== bound) return
        val playTime = bound.bdspPlayTime ?: return
        val detail = "${bound.displayName} • Save layout ${playTime.revision.displayName}\n\n" +
            bdspPlayTimeSummary(playTime) + "\nRaw recorded playtime (4 bytes):\n" +
            playTime.rawPlayTimeBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nThis is the recorded playtime saved in the file, separate from UTC save timestamps. Values do not advance while this view is open. Zero is displayed as zero recorded time. No game-specific hour cap, session duration or clock consistency is inferred. No time changes, normalization or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Recorded playtime • Read-only")
            .setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun bdspGameOptionsSummary(options: BdspGameOptionsRead): String = buildString {
        fun label(id: Number, name: String?): String = if (name == null) "Unknown (ID $id)" else "$name (ID $id)"
        append("Game options • Read-only\n")
        append("Text speed: ${label(options.textSpeedId, BdspGameOptionNames.textSpeed(options.textSpeedId))}\n")
        append("Language: ${label(options.languageId, BdspGameOptionNames.language(options.languageId))}\n")
        append("Window type ID: ${options.windowTypeId}\n")
        append("Move animations: ${label(options.moveAnimationsId, BdspGameOptionNames.moveAnimations(options.moveAnimationsId))}\n")
        append("Battle style: ${label(options.battleStyleId, BdspGameOptionNames.battleStyle(options.battleStyleId))}\n")
        append("Party/Box setting: ${label(options.partyBoxId, BdspGameOptionNames.partyBox(options.partyBoxId))}\n")
        append("Camera up/down: ${label(options.cameraVerticalId, BdspGameOptionNames.camera(options.cameraVerticalId))}\n")
        append("Camera left/right: ${label(options.cameraHorizontalId, BdspGameOptionNames.camera(options.cameraHorizontalId))}\n")
        append("Input mode: ${label(options.inputModeId, BdspGameOptionNames.inputMode(options.inputModeId))}\n")
        for (flag in options.flags) {
            val state = when (flag.rawByte) { 0 -> "Off"; 1 -> "On"; else -> "Unknown byte ${flag.rawByte}" }
            append("${flag.kind.displayName}: $state\n")
        }
        append("Saved volume values • BGM: ${options.bgmVolume}; sound effects: ${options.soundEffectsVolume}; voice: ${options.voiceVolume}\n")
    }

    private fun showBdspGameOptions(bound: SwitchReadSession) {
        if (session !== bound) return
        val options = bound.bdspGameOptions ?: return
        val detail = "${bound.displayName} • Save layout ${options.revision.displayName}\n\n" +
            bdspGameOptionsSummary(options) + "\nRaw flag slots (hex) and native boolean reads:\n" +
            options.flags.joinToString("\n") { "${it.kind.displayName}: ${it.rawWord.toString(16).uppercase().padStart(8, '0')}; native enabled: ${it.nativeEnabled}" } +
            "\n\nRaw native configuration (64 bytes):\n" + options.rawConfigBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nThese are saved settings. Unknown IDs and flag bytes remain unchanged. Volume values are raw saved values, not percentages. Flags use the first byte; their remaining slot bytes are retained. Instant is a reference text-speed ID and does not imply a supported in-game menu choice. Window artwork, menu availability and setting compatibility are not inferred. No setting changes or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Game options • Read-only")
            .setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun showBdspUndergroundActivity(bound: SwitchReadSession) {
        if (session !== bound) return
        val activity = bound.bdspUndergroundActivity ?: return
        val detail = "${bound.displayName} • Save layout ${activity.revision.displayName}\n\n" +
            "Grand Underground activity • Read-only\n" +
            "Fossil digging plays: ${activity.fossilDiggingPlayCount}\n" +
            "Statue TV broadcasts: ${activity.statueTvBroadcastCount}\n" +
            "Likes on Secret Base TV broadcasts: ${activity.secretBaseTvLikeCount}\n" +
            "Other Secret Base likes (saved counter): ${activity.otherSecretBaseLikeCount}\n" +
            "Successful Light Stone searches: ${activity.successfulLightStoneSearchCount}\n\n" +
            "Reserved word 1: ${activity.reserved1} (hex ${java.lang.Long.toHexString(activity.reserved1).uppercase().padStart(16, '0')})\n" +
            "Reserved word 2: ${activity.reserved2} (hex ${java.lang.Long.toHexString(activity.reserved2).uppercase().padStart(16, '0')})\n\n" +
            "Raw native activity record (32 bytes):\n" + activity.rawRecordBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nCounters are shown exactly as saved, including negative values. Zero is a saved value. These records do not establish inventory quantities, Secret Base placement, active statue effects, rewards or online ownership. Reserved words have no inferred meaning. No counter reset, repair or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Grand Underground activity • Read-only")
            .setView(scroll).setPositiveButton("Close", null).show()
    }


    private fun showBdspRecords(bound: SwitchReadSession) {
        if (session !== bound) return
        val records = bound.bdspRecordStatistics ?: return
        val labels = records.sets.map { "Set ${it.index}${if (it.index == 0) " • reference head" else " • saved set"} • 30 records" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Record statistics • Read-only")
            .setItems(labels.toTypedArray()) { _, index -> if (session === bound && index in records.sets.indices) showBdspRecordSet(bound, index) }
            .setPositiveButton("Trainer records") { _, _ -> if (session === bound) showBdspAdditionalRecords(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspRecordSet(bound: SwitchReadSession, setIndex: Int) {
        if (session !== bound) return
        val records = bound.bdspRecordStatistics ?: return
        if (setIndex !in records.sets.indices) return
        val set = records.set(setIndex)
        val labels = set.entries.map { "#${it.recordId} ${it.displayName}: ${it.signedValue}" }
        AlertDialog.Builder(this).setTitle("Record set $setIndex • Read-only")
            .setItems(labels.toTypedArray()) { _, id -> if (session === bound && id in set.entries.indices) showBdspRecord(bound, setIndex, id) }
            .setPositiveButton("Sets") { _, _ -> if (session === bound) showBdspRecords(bound) }
            .setNeutralButton("Raw set") { _, _ -> if (session === bound) showBdspRecordRaw(bound, setIndex) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspRecord(bound: SwitchReadSession, setIndex: Int, recordId: Int) {
        if (session !== bound) return
        val records = bound.bdspRecordStatistics ?: return
        if (setIndex !in records.sets.indices || recordId !in 0 until 30) return
        val entry = records.set(setIndex).entry(recordId)
        val detail = "${bound.displayName} • Save layout ${records.revision.displayName}\n\n" +
            "Set $setIndex • record #$recordId\n${entry.displayName}\nReference key: ${entry.referenceKey}\n\n" +
            "Saved signed value: ${entry.signedValue}\nRaw unsigned word: ${entry.rawValue}\nRaw hex: ${entry.rawValue.toString(16).uppercase().padStart(8, '0')}\n" +
            "Reference limit (descriptive): ${entry.referenceLimit}\n\n" +
            "Saved values are never clamped to reference limits. Negative values and original bits remain intact. Set 0 is the set read by the reference head getter. Other sets have no inferred dates, chronology or ownership. Clear-time units, rate units and ribbon bit meanings are not inferred. These records do not establish rewards, progress validity or consistency with other save sections. No editing, reset or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Record #$recordId • Read-only").setView(scroll)
            .setPositiveButton("Set") { _, _ -> if (session === bound) showBdspRecordSet(bound, setIndex) }.setNegativeButton("Close", null).show()
    }

    private fun showBdspRecordRaw(bound: SwitchReadSession, setIndex: Int) {
        if (session !== bound) return
        val records = bound.bdspRecordStatistics ?: return
        if (setIndex !in records.sets.indices) return
        val raw = records.set(setIndex).rawBytes
        val detail = "${bound.displayName} • Save layout ${records.revision.displayName}\n\n" +
            "Set $setIndex • 30 native words • 120 bytes\n" + raw.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nBytes remain in saved order. Other record sets are not labelled as historical snapshots. No normalization or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Raw record set $setIndex • Read-only").setView(scroll)
            .setPositiveButton("Set") { _, _ -> if (session === bound) showBdspRecordSet(bound, setIndex) }.setNegativeButton("Close", null).show()
    }


    private fun showBdspAdditionalRecords(bound: SwitchReadSession) {
        if (session !== bound) return
        val records = bound.bdspAdditionalRecords ?: return
        if (!records.hasBlock) {
            AlertDialog.Builder(this).setTitle("Additional record trainers • Read-only")
                .setMessage("${bound.displayName} • Save layout ${records.revision.displayName}\n\n${records.absenceReason} No records are synthesized.")
                .setPositiveButton("Close", null).show()
            return
        }
        val labels = records.records.map {
            val name = it.name?.let { value -> if (value.isEmpty()) "Empty name" else "\"$value\"" } ?: "Name unavailable"
            "Slot ${it.index} • $name${if (!it.hasNonzeroRecordBytes) " • all-zero record" else ""}"
        }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Additional record trainers • Read-only")
            .setItems(labels.toTypedArray()) { _, index -> if (session === bound && index in records.records.indices) showBdspAdditionalRecord(bound, index) }
            .setPositiveButton("Raw ranking data") { _, _ -> if (session === bound) showBdspAdditionalRanking(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspAdditionalRecord(bound: SwitchReadSession, index: Int) {
        if (session !== bound) return
        val records = bound.bdspAdditionalRecords ?: return
        if (!records.hasBlock || index !in records.records.indices) return
        val entry = records.record(index)
        val language = BdspGameOptionNames.language(entry.languageId) ?: "Unknown"
        val detail = "${bound.displayName} • Save layout ${records.revision.displayName}\n\nSlot $index • Read-only\n" +
            "Saved name: ${entry.name?.let { "\"$it\"" } ?: "Unavailable"}\nName issue: ${entry.nameIssue ?: "None"}\n" +
            "NULL terminator in name field: ${entry.hasNameTerminator}\n" +
            "Language: $language (ID ${entry.languageId})\nSaved gender byte: ${entry.genderByte}\nSaved body-type ID: ${entry.bodyTypeId}\n" +
            "Raw ID32: ${entry.id32}\nTID16: ${entry.tid16}\nSID16: ${entry.sid16}\n\n" +
            "Raw native record (48 bytes):\n" + entry.rawRecordBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nNames follow the saved UTF-16LE field up to its first NULL or all 13 code units. Malformed names do not hide numeric fields or other slots. Original name/trash, padding and reserved bytes stay intact. Empty or all-zero records do not prove an associated trainer exists. Gender/body IDs, ranking associations, ownership and chronology are not inferred. No name replacement, editing or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Additional trainer slot $index • Read-only").setView(scroll)
            .setPositiveButton("Slots") { _, _ -> if (session === bound) showBdspAdditionalRecords(bound) }.setNegativeButton("Close", null).show()
    }

    private fun showBdspAdditionalRanking(bound: SwitchReadSession) {
        if (session !== bound) return
        val records = bound.bdspAdditionalRecords ?: return
        if (!records.hasBlock) return
        val detail = "${bound.displayName} • Save layout ${records.revision.displayName}\n\n" +
            "Saved ranking region • 384 bytes\n" + records.rawRankingBytes.chunked(32).mapIndexed { i, row ->
                "Row $i\n" + row.chunked(16).joinToString("\n") { line -> line.joinToString(" ") { "%02X".format(it) } }
            }.joinToString("\n\n") +
            "\n\nRows retain native order. Byte meanings, flags, ranks and associations with trainer slots are unclassified. No normalization or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Raw ranking region • Read-only").setView(scroll)
            .setPositiveButton("Slots") { _, _ -> if (session === bound) showBdspAdditionalRecords(bound) }.setNegativeButton("Close", null).show()
    }


    private fun bdspBattleTrainerFlag(word: Long): String = when (word) { 0L -> "Clear"; 1L -> "Set"; else -> "Unknown ($word)" }

    private fun showBdspBattleTrainerFilters(bound: SwitchReadSession) {
        if (session !== bound) return
        val status = bound.bdspBattleTrainerStatus ?: return
        val filters = BdspBattleTrainerFilter.values()
        val labels = filters.map { "${it.displayName} • ${status.filtered(it).size}" }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Battle-trainer status • Read-only")
            .setItems(labels.toTypedArray()) { _, index -> if (session === bound) filters.getOrNull(index)?.let { showBdspBattleTrainerPage(bound, it, 0) } }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspBattleTrainerPage(bound: SwitchReadSession, filter: BdspBattleTrainerFilter, requestedPage: Int) {
        if (session !== bound) return
        val status = bound.bdspBattleTrainerStatus ?: return
        val entries = status.filtered(filter)
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${filter.displayName} • ${entries.size} entries\nWin flags set: ${status.winFlagCount}; battle-searcher flags set: ${status.battleSearcherFlagCount}; entries with unknown flag words: ${status.unknownEntryCount}\nSaved trainer indices stay in native order. Flags do not establish trainer identity, rematch eligibility or story consistency."
            textSize = 16f; setPadding(0, 0, 0, 16)
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "No saved entries match this filter."; textSize = 16f })
        entries.drop(page * 20).take(20).forEach { entry -> root.addView(Button(this).apply {
            text = "Trainer index ${entry.index} • Win: ${bdspBattleTrainerFlag(entry.rawWinWord)} • Searcher: ${bdspBattleTrainerFlag(entry.rawBattleSearcherWord)}"
            setOnClickListener { if (session === bound) showBdspBattleTrainerDetail(bound, entry.index, filter, page) }
        }) }
        val builder = AlertDialog.Builder(this).setTitle("Battle-trainer status • ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Filters") { _, _ -> if (session === bound) showBdspBattleTrainerFilters(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspBattleTrainerPage(bound, filter, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspBattleTrainerPage(bound, filter, page + 1) }
        builder.show()
    }

    private fun showBdspBattleTrainerDetail(bound: SwitchReadSession, index: Int, filter: BdspBattleTrainerFilter, page: Int) {
        if (session !== bound) return
        val status = bound.bdspBattleTrainerStatus ?: return
        if (index !in status.entries.indices) return
        val entry = status.entry(index)
        val detail = "${bound.displayName} • Save layout ${status.revision.displayName}\n\nSaved trainer index $index\n" +
            "Win flag: ${bdspBattleTrainerFlag(entry.rawWinWord)}\nBattle-searcher flag: ${bdspBattleTrainerFlag(entry.rawBattleSearcherWord)}\n\n" +
            "Raw win word: ${entry.rawWinWord} (hex ${entry.rawWinWord.toString(16).uppercase().padStart(8, '0')})\n" +
            "Raw battle-searcher word: ${entry.rawBattleSearcherWord} (hex ${entry.rawBattleSearcherWord.toString(16).uppercase().padStart(8, '0')})\n" +
            "Native boolean reads • Win: ${entry.isWin}; battle-searcher: ${entry.isBattleSearcher}\n\n" +
            "Raw native entry (8 bytes):\n" + entry.rawBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nNative booleans are true only when the complete four-byte word equals 1. Unknown words remain exact and are not changed to zero or one. Trainer names, encounters, rematch eligibility and story progress are not inferred. No defeat-all, rebattle-all, reset, editing or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Trainer index $index • Read-only").setView(scroll)
            .setPositiveButton("Entries") { _, _ -> if (session === bound) showBdspBattleTrainerPage(bound, filter, page) }
            .setNegativeButton("Close", null).show()
    }


    private fun bdspHoneySpreadFlag(word: Long): String = when (word) { 0L -> "Clear"; 1L -> "Set"; else -> "Unknown ($word)" }

    private fun showBdspHoneyTrees(bound: SwitchReadSession) {
        if (session !== bound) return
        val honey = bound.bdspHoneyTrees ?: return
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${bound.displayName} • Save layout ${honey.revision.displayName}\n\nSaved update minutes: ${honey.lastUpdateMinutes}\nSaved tree number: ${honey.treeNumber}\nSpread flags set: ${honey.spreadFlagCount}; unknown spread words: ${honey.unknownSpreadWordCount}\n\nAll 21 saved tree indices are shown in native order. Minutes and IDs remain exact. Timer readiness, clock dates, locations and encounter species are not inferred."
            textSize = 16f; setPadding(0, 0, 0, 16); setTextIsSelectable(true)
        })
        honey.entries.forEach { entry -> root.addView(Button(this).apply {
            text = "Tree index ${entry.index} • Spread: ${bdspHoneySpreadFlag(entry.rawSpreadWord)} • Minutes: ${entry.minutes}"
            setOnClickListener { if (session === bound) showBdspHoneyTreeDetail(bound, entry.index) }
        }) }
        AlertDialog.Builder(this).setTitle("Honey Trees • Read-only")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Raw section") { _, _ -> if (session === bound) showBdspHoneyRaw(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspHoneyTreeDetail(bound: SwitchReadSession, index: Int) {
        if (session !== bound) return
        val honey = bound.bdspHoneyTrees ?: return
        if (index !in honey.entries.indices) return
        val entry = honey.entry(index)
        val detail = "Saved tree index $index\nSpread flag: ${bdspHoneySpreadFlag(entry.rawSpreadWord)}\n" +
            "Raw spread word: ${entry.rawSpreadWord} (hex ${entry.rawSpreadWord.toString(16).uppercase().padStart(8, '0')})\n" +
            "Native boolean read: ${entry.isSpread}\nSigned saved minutes: ${entry.minutes}\n" +
            "Saved encounter-table ID: ${entry.tableId}\nSaved rarity ID: ${entry.rarityId}\nSaved shake ID: ${entry.shakeId}\n\n" +
            "Raw native entry (12 bytes, including alignment):\n" + entry.rawBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nSpread is true only when the complete four-byte word equals 1. Unknown words and all minute/ID values are retained. These fields do not establish timer readiness, location or encounter species. No tree editing, timer advancement, repair or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Tree index $index • Read-only").setView(scroll)
            .setPositiveButton("Trees") { _, _ -> if (session === bound) showBdspHoneyTrees(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspHoneyRaw(bound: SwitchReadSession) {
        if (session !== bound) return
        val honey = bound.bdspHoneyTrees ?: return
        val detail = "Saved Honey Tree section (264 bytes):\n\n" + honey.rawHoneyBytes.chunked(12).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nFirst 12 bytes: signed 64-bit update minutes, saved tree-number byte and three retained alignment bytes. Remaining 252 bytes: 21 entries of 12 bytes in native order. Update minutes are not converted to a date; no clock epoch is assumed."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Honey Trees • Raw section • Read-only").setView(scroll)
            .setPositiveButton("Trees") { _, _ -> if (session === bound) showBdspHoneyTrees(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun bdspEncounterFlag(word: Long): String = when (word) { 0L -> "Clear"; 1L -> "Set"; else -> "Unknown ($word)" }

    private fun showBdspEncounterMenu(bound: SwitchReadSession) {
        if (session !== bound) return
        if (bound.bdspEncounter == null) return
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Encounters • Read-only")
            .setItems(arrayOf("Saved encounter overview", "Saved radar records", "Saved roamers", "Raw encounter section")) { _, index ->
                if (session === bound) when (index) {
                    0 -> showBdspEncounterOverview(bound)
                    1 -> showBdspRadarRecords(bound)
                    2 -> showBdspRoamers(bound)
                    3 -> showBdspEncounterRaw(bound)
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun showBdspEncounterOverview(bound: SwitchReadSession) {
        if (session !== bound) return
        val encounter = bound.bdspEncounter ?: return
        val detail = "${bound.displayName} • Save layout ${encounter.revision.displayName}\n\n" +
            "Saved walk count: ${encounter.walkCount}\nSafari seed (UInt32): ${encounter.safariSeed}\nGenerate seed (UInt32): ${encounter.generateSeed}\n\n" +
            "Hill talk flag: ${bdspEncounterFlag(encounter.rawHillTalkWord)}\nRaw Hill talk word: ${encounter.rawHillTalkWord}\nNative boolean: ${encounter.isHillTalk}\n" +
            "Hill encounter-table IDs: ${encounter.hillTable1}, ${encounter.hillTable2}\n\n" +
            "Saved before-zone ID: ${encounter.beforeZone}\nSaved old-zone ID: ${encounter.oldZone}\n\n" +
            "Generate-valid flag: ${bdspEncounterFlag(encounter.rawGenerateValidWord)}\nRaw generate-valid word: ${encounter.rawGenerateValidWord}\nNative boolean: ${encounter.isGenerateValid}\n" +
            "Saved spray count (signed): ${encounter.sprayCount}\nSpray type ID: ${encounter.sprayType}\nVs. Seeker charge: ${encounter.vsSeekerCharge}\nPoké Radar charge: ${encounter.radarCharge}\nFlute type ID: ${encounter.fluteType}\n\n" +
            "These are saved values. Negative counts, unknown IDs and charge values are retained. Native booleans are true only when the complete four-byte word equals 1. No spawn eligibility, map names, encounter outcomes or RNG predictions are inferred."
        showBdspEncounterText(bound, "Encounter overview • Read-only", detail)
    }

    private fun showBdspRadarRecords(bound: SwitchReadSession) {
        if (session !== bound) return
        val encounter = bound.bdspEncounter ?: return
        val detail = encounter.radarRecords.joinToString("\n\n") { record ->
            "Saved radar record ${record.index}\nSpecies ID (UInt32): ${record.speciesId}\nChain (UInt32): ${record.chain}\nRaw 8 bytes: " + record.rawBytes.joinToString(" ") { "%02X".format(it) }
        } + "\n\nAll three records remain in native order. Numeric species IDs and chain words are retained exactly; they do not establish a current encounter, chain eligibility or chronological order."
        showBdspEncounterText(bound, "Radar records • Read-only", detail)
    }

    private fun showBdspRoamers(bound: SwitchReadSession) {
        if (session !== bound) return
        val encounter = bound.bdspEncounter ?: return
        AlertDialog.Builder(this).setTitle("Saved roamers • Read-only")
            .setItems(encounter.roamers.map { "Roamer index ${it.index} • Species ID ${it.speciesId} • Zone ID ${it.zoneId}" }.toTypedArray()) { _, index ->
                if (session === bound) showBdspRoamerDetail(bound, index)
            }.setNegativeButton("Encounters") { _, _ -> if (session === bound) showBdspEncounterMenu(bound) }.show()
    }

    private fun showBdspRoamerDetail(bound: SwitchReadSession, index: Int) {
        if (session !== bound) return
        val encounter = bound.bdspEncounter ?: return
        if (index !in encounter.roamers.indices) return
        val record = encounter.roamer(index)
        val detail = "Saved roamer index $index\nZone ID (signed): ${record.zoneId}\n" +
            "Seed (UInt64): ${record.seed}\nSeed hex: ${record.seed.toString(16).uppercase().padStart(16, '0')}\n" +
            "Species ID (UInt32): ${record.speciesId}\nHP (UInt32): ${record.hp}\nSaved level byte: ${record.level}\n" +
            "Status word (UInt32): ${record.statusWord}\nStatus hex: ${record.statusWord.toString(16).uppercase().padStart(8, '0')}\n" +
            "Encounter byte: ${record.encounterByte}\n\nRaw native record (32 bytes, including alignment):\n" + record.rawBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nSaved IDs, seeds, status words and encounter bytes remain exact. No guessed map names, species names, status meanings, active/defeated state, spawn eligibility or encounter outcomes. No RNG advance, reset, editing or save writing is available."
        showBdspEncounterText(bound, "Roamer index $index • Read-only", detail)
    }

    private fun showBdspEncounterRaw(bound: SwitchReadSession) {
        if (session !== bound) return
        val encounter = bound.bdspEncounter ?: return
        val detail = "Saved encounter section (392 bytes, including alignment and the separately browsable Honey Tree section):\n\n" +
            encounter.rawEncounterBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nAll bytes remain unchanged. Honey Tree fields are available through the existing Honey Trees view. No decoding or display action writes back to the save."
        showBdspEncounterText(bound, "Encounter section • Read-only", detail)
    }

    private fun showBdspEncounterText(bound: SwitchReadSession, title: String, detail: String) {
        if (session !== bound) return
        if (bound.bdspEncounter == null) return
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle(title).setView(scroll)
            .setPositiveButton("Encounters") { _, _ -> if (session === bound) showBdspEncounterMenu(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspBerryPlots(bound: SwitchReadSession, requestedPage: Int = 0) {
        if (session !== bound) return
        val berries = bound.bdspBerryPlots ?: return
        val lastPage = (berries.plots.size - 1).coerceAtLeast(0) / 16
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${bound.displayName} • Save layout ${berries.revision.displayName}\n\nSaved update minutes: ${berries.lastUpdateMinutes}\n\nAll 128 saved plots remain in native order, including zero-filled records. Plot fields are shown as saved bytes; berry type, growth state, yield and location are not decoded. Update minutes are not converted to a date."
            textSize = 16f; setPadding(0, 0, 0, 16); setTextIsSelectable(true)
        })
        berries.plots.drop(page * 16).take(16).forEach { plot -> root.addView(Button(this).apply {
            text = "Plot index ${plot.index}\n" + plot.rawBytes.joinToString(" ") { "%02X".format(it) }
            setOnClickListener { if (session === bound) showBdspBerryPlotDetail(bound, plot.index, page) }
        }) }
        val builder = AlertDialog.Builder(this).setTitle("Berry plot records • ${page + 1}/${lastPage + 1} • Read-only")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Raw section") { _, _ -> if (session === bound) showBdspBerryRaw(bound, page) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspBerryPlots(bound, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspBerryPlots(bound, page + 1) }
        builder.show()
    }

    private fun showBdspBerryPlotDetail(bound: SwitchReadSession, index: Int, page: Int) {
        if (session !== bound) return
        val berries = bound.bdspBerryPlots ?: return
        if (index !in berries.plots.indices) return
        val plot = berries.plot(index)
        val detail = "Saved plot index $index\n\nRaw native record (16 bytes):\n" +
            plot.rawBytes.chunked(8).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nAll bytes are retained exactly. Individual field definitions are not yet verified. No berry type, planting/growth state, readiness, yield, watering state or location is inferred, including for zero-filled records. No planting, harvesting, timer advancement, editing or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Plot index $index • Read-only").setView(scroll)
            .setPositiveButton("Plots") { _, _ -> if (session === bound) showBdspBerryPlots(bound, page) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspBerryRaw(bound: SwitchReadSession, page: Int) {
        if (session !== bound) return
        val berries = bound.bdspBerryPlots ?: return
        val detail = "Saved berry section (2,056 bytes):\n\n" +
            berries.rawBerryBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nFirst 2,048 bytes: 128 plot records of 16 bytes. Final 8 bytes: signed saved update minutes (${berries.lastUpdateMinutes}). No clock epoch, calendar date or growth readiness is assumed. No decoding or display action writes back to the save."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Berry section • Read-only").setView(scroll)
            .setPositiveButton("Plots") { _, _ -> if (session === bound) showBdspBerryPlots(bound, page) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspSecretBase(bound: SwitchReadSession, requestedPage: Int = 0) {
        if (session !== bound) return
        val base = bound.bdspSecretBase ?: return
        val lastPage = (base.statues.size - 1).coerceAtLeast(0) / 10
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${bound.displayName} • Save layout ${base.revision.displayName}\n\nSaved talk-player data ID: ${base.talkPlayerDataId}\nSaved talk-player count: ${base.talkPlayerCount}\n" +
                "Base zone ID (signed): ${base.zoneId}\nSaved position X/Y (signed): ${base.x}, ${base.y}\nDirection byte: ${base.direction}\nExpansion-status byte: ${base.expansionStatus}\nSaved good count (signed): ${base.goodCount}\n" +
                "Raw enable/alignment bytes: " + base.rawEnableBytes.joinToString(" ") { "%02X".format(it) } +
                "\n\nAll 30 saved statue records remain in native order, regardless of the saved count. Zero or negative IDs are not used to infer empty slots. Names, effects, unlocks and layout legality are not inferred. Enable-byte semantics are not decoded."
            textSize = 16f; setPadding(0, 0, 0, 16); setTextIsSelectable(true)
        })
        base.statues.drop(page * 10).take(10).forEach { statue -> root.addView(Button(this).apply {
            text = "Statue index ${statue.index} • ID ${statue.statueId} • Pedestal ${statue.pedestalId}\nX/Y: ${statue.x}, ${statue.y} • Direction: ${statue.direction}"
            setOnClickListener { if (session === bound) showBdspStatueDetail(bound, statue.index, page) }
        }) }
        val builder = AlertDialog.Builder(this).setTitle("Secret Base • ${page + 1}/${lastPage + 1} • Read-only")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Raw record") { _, _ -> if (session === bound) showBdspSecretBaseRaw(bound, page) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspSecretBase(bound, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspSecretBase(bound, page + 1) }
        builder.show()
    }

    private fun showBdspStatueDetail(bound: SwitchReadSession, index: Int, page: Int) {
        if (session !== bound) return
        val base = bound.bdspSecretBase ?: return
        if (index !in base.statues.indices) return
        val statue = base.statue(index)
        val detail = "Saved statue index $index\nStatue ID (signed): ${statue.statueId}\nPedestal ID (signed): ${statue.pedestalId}\n" +
            "Saved X/Y (signed): ${statue.x}, ${statue.y}\nSaved direction (signed): ${statue.direction}\n\nRaw native record (20 bytes):\n" +
            statue.rawBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nAll values remain exact, including negative or unknown IDs, positions and directions. No guessed statue/pedestal names, type effects, unlocks, occupied state, placement validity or base legality. No placing, moving, deleting, resetting, editing or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Statue index $index • Read-only").setView(scroll)
            .setPositiveButton("Base") { _, _ -> if (session === bound) showBdspSecretBase(bound, page) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspSecretBaseRaw(bound: SwitchReadSession, page: Int) {
        if (session !== bound) return
        val base = bound.bdspSecretBase ?: return
        val detail = "Saved Secret Base record (624 bytes):\n\n" +
            base.rawBaseBytes.chunked(20).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nFirst 20 bytes: saved header. Next 600 bytes: 30 statue records of 20 bytes. Final four bytes: enable/alignment data retained without flag decoding. No display action writes back to the save."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Secret Base raw record • Read-only").setView(scroll)
            .setPositiveButton("Base") { _, _ -> if (session === bound) showBdspSecretBase(bound, page) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspUndergroundNpcFilters(bound: SwitchReadSession) {
        if (session !== bound) return
        val records = bound.bdspUndergroundNpc ?: return
        val filters = BdspUndergroundNpcFilter.values()
        AlertDialog.Builder(this).setTitle("${bound.displayName} • NPC records • Read-only")
            .setItems(filters.map { "${it.displayName} • ${records.filtered(it).size}" }.toTypedArray()) { _, index ->
                if (session === bound) filters.getOrNull(index)?.let { showBdspUndergroundNpcPage(bound, it, 0) }
            }.setNegativeButton("Close", null).show()
    }

    private fun showBdspUndergroundNpcPage(bound: SwitchReadSession, filter: BdspUndergroundNpcFilter, requestedPage: Int) {
        if (session !== bound) return
        val records = bound.bdspUndergroundNpc ?: return
        val entries = records.filtered(filter)
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${filter.displayName} • ${entries.size} entries\nNative recorded-entry count: ${records.recordedEntryCount}/100\n\nThe native count includes every nonzero byte, including repeated values. This is not a count of unique NPCs. Entries retain their saved indices. NPC identities, Spiritomb availability and quest completion are not inferred."
            textSize = 16f; setPadding(0, 0, 0, 16)
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "No saved entries match this filter."; textSize = 16f })
        entries.drop(page * 20).take(20).forEach { entry -> root.addView(Button(this).apply {
            text = "Saved index ${entry.index} • Byte ${entry.value} • Hex ${entry.value.toString(16).uppercase().padStart(2, '0')}"
            setOnClickListener { if (session === bound) showBdspUndergroundNpcDetail(bound, entry.index, filter, page) }
        }) }
        val builder = AlertDialog.Builder(this).setTitle("Underground NPC records • ${page + 1}/${lastPage + 1}")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Filters") { _, _ -> if (session === bound) showBdspUndergroundNpcFilters(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspUndergroundNpcPage(bound, filter, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspUndergroundNpcPage(bound, filter, page + 1) }
        builder.show()
    }

    private fun showBdspUndergroundNpcDetail(bound: SwitchReadSession, index: Int, filter: BdspUndergroundNpcFilter, page: Int) {
        if (session !== bound) return
        val records = bound.bdspUndergroundNpc ?: return
        if (index !in records.entries.indices) return
        val entry = records.entry(index)
        val detail = "Saved interaction index $index\nExact unsigned byte: ${entry.value}\nHex: ${entry.value.toString(16).uppercase().padStart(2, '0')}\n" +
            "Contributes to the native nonzero-entry count: ${entry.value != 0}\n\n" +
            "Full saved NPC section (100 bytes, native order):\n" + records.rawNpcBytes.chunked(20).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nRepeated values are retained and each nonzero entry counts separately. No guessed NPC name, unique-interaction count, Spiritomb eligibility or quest state. No marking seen, filling, clearing, resetting, editing or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("NPC record index $index • Read-only").setView(scroll)
            .setPositiveButton("Entries") { _, _ -> if (session === bound) showBdspUndergroundNpcPage(bound, filter, page) }
            .setNegativeButton("Close", null).show()
    }

    private fun bdspReturnFloat(bits: Long): String = "${Float.fromBits(bits.toInt())} • Raw hex ${bits.toString(16).uppercase().padStart(8, '0')}"

    private fun showBdspUndergroundReturn(bound: SwitchReadSession) {
        if (session !== bound) return
        val saved = bound.bdspUndergroundReturn ?: return
        val detail = "${bound.displayName} • Save layout ${saved.revision.displayName}\n\n" +
            "Saved return zone ID: ${saved.returnZoneId}\nSaved return grid X/Y/Z: ${saved.gridX}, ${saved.gridY}, ${saved.gridZ}\n\n" +
            "Saved Zenmetsu zone ID: ${saved.zenmetsuZoneId}\nZenmetsu position X: ${bdspReturnFloat(saved.rawZenmetsuXBits)}\n" +
            "Zenmetsu position Y: ${bdspReturnFloat(saved.rawZenmetsuYBits)}\nZenmetsu position Z: ${bdspReturnFloat(saved.rawZenmetsuZBits)}\n" +
            "Saved Zenmetsu direction: ${saved.zenmetsuDirection}\nSaved Underground return zone ID: ${saved.returnUgZoneId}\n\n" +
            "All integer fields remain signed and exact. Float32 display retains raw bits, including signed zero, subnormal values, infinities and NaN payloads. Raw hex is authoritative for NaN payloads. Non-finite values are shown without normalization.\n\n" +
            "These are saved return-location fields, not a current-location map. Zone names, coordinate systems, direction names, spawn validity and teleport destinations are not inferred. No teleporting, movement, location editing, repair or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Underground return data • Read-only").setView(scroll)
            .setPositiveButton("Raw fields") { _, _ -> if (session === bound) showBdspUndergroundReturnRaw(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspUndergroundReturnRaw(bound: SwitchReadSession) {
        if (session !== bound) return
        val saved = bound.bdspUndergroundReturn ?: return
        val detail = "Saved return header (36 bytes):\n\n" +
            saved.rawReturnBytes.chunked(4).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nSaved Underground return zone (4 separate bytes):\n" + saved.rawUndergroundZoneBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nThe fields occupy separate native regions. Dig points, Underground encounter Pokémon/positions and Secret Base data are excluded from this view. All 40 saved bytes remain unchanged."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Return data raw fields • Read-only").setView(scroll)
            .setPositiveButton("Return data") { _, _ -> if (session === bound) showBdspUndergroundReturn(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun bdspSealObtainedFlag(word: Long): String = when (word) { 0L -> "Clear"; 1L -> "Set"; else -> "Unknown ($word)" }

    private fun showBdspSealStickerFilters(bound: SwitchReadSession) {
        if (session !== bound) return
        val seals = bound.bdspSealStickers ?: return
        val filters = BdspSealStickerFilter.values()
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Seal stickers • Read-only")
            .setItems(filters.map { "${it.displayName} • ${seals.filtered(it).size}" }.toTypedArray()) { _, index ->
                if (session === bound) filters.getOrNull(index)?.let { showBdspSealStickerPage(bound, it, 0) }
            }.setNegativeButton("Close", null).show()
    }

    private fun showBdspSealStickerPage(bound: SwitchReadSession, filter: BdspSealStickerFilter, requestedPage: Int) {
        if (session !== bound) return
        val seals = bound.bdspSealStickers ?: return
        val entries = seals.filtered(filter)
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 20
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "${filter.displayName} • ${entries.size} entries\nSaved obtained flags set: ${seals.obtainedFlagCount}/200\n\nNative catalogue keys identify known IDs. Unknown IDs and all signed Count/TotalCount values remain exact, including negative values and values above 99. Flags and counts do not establish unlocks or Ball Capsule placement."
            textSize = 16f; setPadding(0, 0, 0, 16)
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "No saved entries match this filter."; textSize = 16f })
        entries.drop(page * 20).take(20).forEach { entry -> root.addView(Button(this).apply {
            text = "ID ${entry.index} • ${entry.nativeLabel ?: "Unlisted seal"}\nCount ${entry.count} • Total ${entry.totalCount} • Obtained: ${bdspSealObtainedFlag(entry.rawObtainedWord)}"
            setOnClickListener { if (session === bound) showBdspSealStickerDetail(bound, entry.index, filter, page) }
        }) }
        val builder = AlertDialog.Builder(this).setTitle("Seal stickers • ${page + 1}/${lastPage + 1} • Read-only")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Filters") { _, _ -> if (session === bound) showBdspSealStickerFilters(bound) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspSealStickerPage(bound, filter, page - 1) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspSealStickerPage(bound, filter, page + 1) }
        builder.show()
    }

    private fun showBdspSealStickerDetail(bound: SwitchReadSession, index: Int, filter: BdspSealStickerFilter, page: Int) {
        if (session !== bound) return
        val seals = bound.bdspSealStickers ?: return
        if (index !in seals.entries.indices) return
        val entry = seals.entry(index)
        val detail = "Saved seal ID/index $index\nNative catalogue key: ${entry.nativeLabel ?: "Unlisted ID"}\n" +
            "Signed Count: ${entry.count}\nSigned TotalCount: ${entry.totalCount}\nSaved obtained flag: ${bdspSealObtainedFlag(entry.rawObtainedWord)}\n" +
            "Raw obtained word: ${entry.rawObtainedWord} (hex ${entry.rawObtainedWord.toString(16).uppercase().padStart(8, '0')})\nNative boolean read: ${entry.isGet}\n\n" +
            "Raw native record (12 bytes):\n" + entry.rawBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nNative obtained is true only when the complete four-byte word equals 1. Unknown words, zero/negative counts, counts above the reference maximum of 99 and unlisted IDs remain unchanged. No guessed unlock, capsule placement or remaining-use claims. No granting all, clearing, count editing, repair, export or save writing is available."
        val scroll = ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
            text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }) }
        AlertDialog.Builder(this).setTitle("Seal ID $index • Read-only").setView(scroll)
            .setPositiveButton("Entries") { _, _ -> if (session === bound) showBdspSealStickerPage(bound, filter, page) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspBallCapsules(bound: SwitchReadSession, requestedPage: Int, nonzeroOnly: Boolean) {
        if (session !== bound) return
        val read = bound.bdspBallCapsules ?: return
        val entries = read.filtered(nonzeroOnly)
        val lastPage = (entries.size - 1).coerceAtLeast(0) / 10
        val page = requestedPage.coerceIn(0, lastPage)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "Saved capsule count byte: ${read.savedCapsuleCount}" + (if (read.countWithinCapacity) "" else " • exceeds native capacity 99; retained") +
                "\n${if (nonzeroOnly) "Nonzero raw records" else "All saved records"}: ${entries.size}/99\nRaw four-byte header: " +
                read.rawHeaderBytes.joinToString(" ") { "%02X".format(it) } +
                "\n\nAll 99 records remain available regardless of the saved count. Nonzero bytes do not establish an active or unlocked capsule. Saved species/encryption words do not prove an equipped Pokémon. Seal IDs and signed coordinates are shown exactly; no coordinate system, bounds or effects are inferred."
            textSize = 16f; setPadding(0, 0, 0, 16); setTextIsSelectable(true)
        })
        if (entries.isEmpty()) root.addView(TextView(this).apply { text = "No records have nonzero bytes."; textSize = 16f })
        entries.drop(page * 10).take(10).forEach { capsule -> root.addView(Button(this).apply {
            text = "Capsule ${capsule.index + 1} • Saved index ${capsule.index}\nSpecies ID ${capsule.speciesId} • EC ${capsule.encryptionConstant.toString(16).uppercase().padStart(8, '0')}\n20 saved affix records"
            setOnClickListener { if (session === bound) showBdspBallCapsuleDetail(bound, capsule.index, page, nonzeroOnly) }
        }) }
        val builder = AlertDialog.Builder(this).setTitle("${bound.displayName} • Ball Capsules • ${page + 1}/${lastPage + 1} • Read-only")
            .setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton(if (nonzeroOnly) "Show all" else "Nonzero records") { _, _ -> if (session === bound) showBdspBallCapsules(bound, 0, !nonzeroOnly) }
        if (page > 0) builder.setNegativeButton("Previous") { _, _ -> if (session === bound) showBdspBallCapsules(bound, page - 1, nonzeroOnly) }
        if (page < lastPage) builder.setNeutralButton("Next") { _, _ -> if (session === bound) showBdspBallCapsules(bound, page + 1, nonzeroOnly) }
        builder.show()
    }

    private fun showBdspBallCapsuleDetail(bound: SwitchReadSession, index: Int, page: Int, nonzeroOnly: Boolean) {
        if (session !== bound) return
        val read = bound.bdspBallCapsules ?: return
        if (index !in read.capsules.indices) return
        val capsule = read.capsule(index)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "Saved capsule index $index\nSpecies ID (UInt32): ${capsule.speciesId}\nEncryption constant: ${capsule.encryptionConstant} (hex ${capsule.encryptionConstant.toString(16).uppercase().padStart(8, '0')})\nUnknown word (UInt32): ${capsule.unknownWord}\n\nAll 20 saved affix slots are retained, including zero and unlisted IDs. These fields do not identify a verified equipped Pokémon, valid placement, unlock or effect."
            textSize = 16f; setPadding(0, 0, 0, 16); setTextIsSelectable(true)
        })
        capsule.seals.forEach { seal -> root.addView(Button(this).apply {
            text = "Affix ${seal.index + 1} • ID ${seal.sealId} • ${seal.nativeLabel ?: "Unlisted ID"}\nSigned X ${seal.x} • Y ${seal.y} • Z ${seal.z}"
            setOnClickListener { if (session === bound) showBdspAffixedSealDetail(bound, index, seal.index, page, nonzeroOnly) }
        }) }
        AlertDialog.Builder(this).setTitle("Capsule ${index + 1} • Read-only").setView(ScrollView(this).apply { addView(root) })
            .setPositiveButton("Capsules") { _, _ -> if (session === bound) showBdspBallCapsules(bound, page, nonzeroOnly) }
            .setNeutralButton("Raw record") { _, _ -> if (session === bound) showBdspBallCapsuleRaw(bound, index, page, nonzeroOnly) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspAffixedSealDetail(bound: SwitchReadSession, index: Int, sealIndex: Int, page: Int, nonzeroOnly: Boolean) {
        if (session !== bound) return
        val read = bound.bdspBallCapsules ?: return
        if (index !in read.capsules.indices || sealIndex !in 0 until 20) return
        val seal = read.capsule(index).seal(sealIndex)
        val detail = "Saved capsule index $index • Affix index $sealIndex\nUnsigned seal ID: ${seal.sealId}\nNative key: ${seal.nativeLabel ?: "Unlisted ID"}\nSigned Int16 X: ${seal.x}\nSigned Int16 Y: ${seal.y}\nSigned Int16 Z: ${seal.z}\n\nRaw eight-byte affix record:\n" +
            seal.rawBytes.joinToString(" ") { "%02X".format(it) } + "\n\nCoordinates and unknown IDs remain exact. No editing, placement normalization, unlock, repair, export or save writing."
        AlertDialog.Builder(this).setTitle("Affix ${sealIndex + 1} • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            }) }).setPositiveButton("Capsule") { _, _ -> if (session === bound) showBdspBallCapsuleDetail(bound, index, page, nonzeroOnly) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspBallCapsuleRaw(bound: SwitchReadSession, index: Int, page: Int, nonzeroOnly: Boolean) {
        if (session !== bound) return
        val read = bound.bdspBallCapsules ?: return
        if (index !in read.capsules.indices) return
        val raw = read.capsule(index).rawBytes
        AlertDialog.Builder(this).setTitle("Capsule ${index + 1} • 172 raw bytes • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = raw.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } }
                textSize = 16f; setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            }) }).setPositiveButton("Capsule") { _, _ -> if (session === bound) showBdspBallCapsuleDetail(bound, index, page, nonzeroOnly) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspContest(bound: SwitchReadSession) {
        if (session !== bound) return
        val read = bound.bdspContest ?: return
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "Saved Contest rank points: ${read.rankPoints}\nFive saved photo records are retained as raw native data. The raw blocks do not prove photo contents, contest eligibility or awards. No editing, reset, repair, export, replacement or save writing is available." +
                "\n\nSaved photo language IDs\n" + (bound.bdspContestPhotoLanguages?.entries?.joinToString("\n") { "${it.category.nativeName}: ${it.languageId}" } ?: "Photo language metadata unavailable for this preview.")
            textSize = 16f; setTextIsSelectable(true); setPadding(0, 0, 0, 16)
        })
        read.photos.forEach { photo -> root.addView(Button(this).apply {
            text = "Photo ${photo.index + 1} • ${photo.rawBytes.size} raw bytes"
            setOnClickListener { if (session === bound) showBdspContestPhoto(bound, photo.index) }
        }) }
        if (bound.bdspContestPhotoLanguages != null) root.addView(Button(this).apply {
            text = "Photo languages & raw data"
            setOnClickListener { if (session === bound) showBdspContestPhotoLanguages(bound) }
        })
        AlertDialog.Builder(this).setTitle("Contest Records • Read-only").setView(ScrollView(this).apply { addView(root) }).setNegativeButton("Close", null).show()
    }

    private fun showBdspContestPhoto(bound: SwitchReadSession, index: Int) {
        if (session !== bound) return
        val read = bound.bdspContest ?: return
        if (index !in read.photos.indices) return
        val raw = read.photo(index).rawBytes
        AlertDialog.Builder(this).setTitle("Contest photo ${index + 1} • Raw record")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = raw.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } }
                textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Contest") { _, _ -> if (session === bound) showBdspContest(bound) }.setNegativeButton("Close", null).show()
    }
    private fun showBdspContestPhotoLanguages(bound: SwitchReadSession) {
        if (session !== bound) return
        val read = bound.bdspContestPhotoLanguages ?: return
        val detail = read.entries.joinToString("\n") { "${it.index + 1}. ${it.category.nativeName} • Saved language ID ${it.languageId}" } +
            "\n\nThese are the five native saved language bytes. All values, including zero and unrecognized IDs, are retained. They do not change the trainer language or establish photo contents, eligibility or awards.\n\nRaw language block (24 bytes):\n" +
            read.rawBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nReserved bytes after the five IDs (19 bytes):\n" + read.reservedBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nReserved data is not decoded. No language editing, normalization, repair, export, replacement or save writing."
        AlertDialog.Builder(this).setTitle("Contest photo languages • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Contest") { _, _ -> if (session === bound) showBdspContest(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspUnionRoom(bound: SwitchReadSession) {
        if (session !== bound) return
        val read = bound.bdspUnionRoom ?: return
        fun hex(word: Long): String = word.toString(16).uppercase().padStart(8, '0')
        val flag = when (read.rawInitTalkWord) { 0L -> "Clear"; 1L -> "Set"; else -> "Unknown saved word" }
        val detail = "Saved initial-talk flag: $flag\nNative boolean read: ${read.isInitTalk}\nRaw flag word: ${read.rawInitTalkWord} (hex ${hex(read.rawInitTalkWord)})\n\n" +
            "Native PenaltyCount label (signed Int32): ${read.penaltyCount}\nRaw count word: ${read.rawPenaltyCountWord} (hex ${hex(read.rawPenaltyCountWord)})\n\n" +
            "Native PenaltyTime label (Float32): ${read.penaltyTime}\nRaw time bits: ${read.rawPenaltyTimeBits} (hex ${hex(read.rawPenaltyTimeBits)})\n\n" +
            "Field meanings are provisional; raw values remain authoritative. No time units, remaining penalty, expiry, online access or unlock status are inferred. Negative counts, unknown flags, signed zero, infinities and NaN payload words are retained. A VM may quiet a signaling NaN when displaying Float32; the raw word stays exact.\n\n" +
            "Raw native Union Room record (12 bytes):\n" + read.rawBytes.joinToString(" ") { "%02X".format(it) } +
            "\n\nRead-only. No penalty clearing, resets, normalization, editing, repair, export, replacement or save writing."
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Union Room saved data • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Close", null).show()
    }

    private fun bdspRandomGroupName(name: BdspRandomGroupNameRead): String =
        name.text?.ifEmpty { "Empty saved name" } ?: (name.unavailableReason ?: "Name unavailable; raw bytes retained.")

    private fun showBdspRandomGroups(bound: SwitchReadSession) {
        if (session !== bound) return
        val read = bound.bdspRandomGroups ?: return
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        root.addView(TextView(this).apply {
            text = "All 12 saved random-group records are retained, including zero and malformed records. Names and user IDs are saved data; they do not verify online identities, usable groups or membership. Seeds are shown without advancing the RNG. Ticks remain signed raw values; no timestamp validity or current-clock claims."
            textSize = 16f; setPadding(0, 0, 0, 16)
        })
        read.records.forEach { record -> root.addView(Button(this).apply {
            text = "Record ${record.index + 1} • ${bdspRandomGroupName(record.groupName)}\nSaved player: ${bdspRandomGroupName(record.playerName)}"
            setOnClickListener { if (session === bound) showBdspRandomGroup(bound, record.index) }
        }) }
        AlertDialog.Builder(this).setTitle("${bound.displayName} • Random groups • Read-only")
            .setView(ScrollView(this).apply { addView(root) }).setNegativeButton("Close", null).show()
    }

    private fun showBdspRandomGroup(bound: SwitchReadSession, index: Int) {
        if (session !== bound) return
        val read = bound.bdspRandomGroups ?: return
        if (index !in read.records.indices) return
        val record = read.record(index)
        val flag = when (record.rawMaleByte) { 0 -> "Clear"; 1 -> "Set"; else -> "Unknown saved byte" }
        val detail = "Saved index $index\nGroup name: ${bdspRandomGroupName(record.groupName)}\nSaved player name: ${bdspRandomGroupName(record.playerName)}\n\n" +
            "Saved male flag: $flag • Raw byte ${record.rawMaleByte}\nNative boolean read: ${record.male}\nAlignment bytes: " + record.alignmentBytes.joinToString(" ") { "%02X".format(it) } +
            "\nRegion code (signed): ${record.regionCode}\nUser ID (signed): ${record.userId}\nSeed (UInt64): ${record.seed}\nSeed hex: ${record.seed.toString(16).uppercase().padStart(16, '0')}\n" +
            "Random (UInt64): ${record.random}\nRandom hex: ${record.random.toString(16).uppercase().padStart(16, '0')}\nSaved ticks (Int64): ${record.ticks}\nTick bits: ${record.ticks.toULong().toString(16).uppercase().padStart(16, '0')}\n\n" +
            "Raw record (132 bytes, including both complete name buffers):\n" + record.rawBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } +
            "\n\nMalformed names are isolated individually. No identity lookup, online access, group joining, name repair, RNG advance, timestamp conversion, editing, export or save writing."
        AlertDialog.Builder(this).setTitle("Random-group record ${index + 1} • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Records") { _, _ -> if (session === bound) showBdspRandomGroups(bound) }
            .setNegativeButton("Close", null).show()
    }

    private fun showBdspMenuSelection(bound: SwitchReadSession) {
        if (session !== bound) return
        val read = bound.bdspMenuSelection ?: return
        fun hex(word: Long): String = word.toString(16).uppercase().padStart(8, '0')
        val detail = buildString {
            append("${bound.displayName} • Save layout ${read.revision.displayName}\n\n")
            append("Last selected menu ID (signed): ${read.lastSelectedMenuId}\n")
            append("Last selected ID bits: ${hex(read.lastSelectedMenuId.toLong() and 0xFFFFFFFFL)}\n\n")
            for (item in read.items) {
                val flag = when (item.rawNewWord) { 0L -> "Clear"; 1L -> "Set"; else -> "Unknown saved word" }
                append("Saved index ${item.index} • Menu ID ${item.menuId}\n")
                append("ID bits: ${hex(item.menuId.toLong() and 0xFFFFFFFFL)}\n")
                append("New indicator: $flag • Native boolean read: ${item.isNew}\n")
                append("Raw flag word: ${item.rawNewWord} (hex ${hex(item.rawNewWord)})\n\n")
            }
            append("These are saved menu records. IDs are shown exactly, including duplicate, negative and unknown values. The last selected ID need not match a saved slot. A new indicator does not establish menu availability, unlocks or a usable action. Menu labels and shortcuts are not inferred.\n\n")
            append("Raw menu data (68 bytes):\n")
            append(read.rawBytes.chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } })
            append("\n\nRead-only. No menu reordering, selection changes, indicator clearing, normalization, editing, export, replacement or save writing.")
        }
        AlertDialog.Builder(this).setTitle("Saved menu layout • Read-only")
            .setView(ScrollView(this).apply { addView(TextView(this@SwitchReadOnlyActivity).apply {
                text = detail; textSize = 16f; setTextIsSelectable(true); setPadding(24, 16, 24, 16)
            }) }).setPositiveButton("Close", null).show()
    }

    override fun onDestroy() {
        requestVersion++
        session = null
        reader.shutdownNow()
        super.onDestroy()
    }
}

