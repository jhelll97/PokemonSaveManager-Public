package com.example.pokemonsavemanager

/**
 * Immutable UI state for opt-in Gen 6 destination-version-exclusive choices.
 *
 * It accepts only metadata already approved by the verified catalog. Encrypted
 * PK6 bytes remain inside [Gen6VersionExclusiveCatalog] and are resolved only
 * when a conversion is prepared.
 */
class Gen6VersionExclusiveSelectionState private constructor(
    val destinationGame: Gen6ConverterGame,
    internal val sourceFingerprint: String,
    availableChoices: List<Gen6VersionExclusiveChoice>,
    selectedIds: List<String>
) {
    private val choices = availableChoices.toList()
    private val selection = selectedIds.toList()
    private val choicesById = choices.associateBy { it.id }

    init {
        require(
            sourceFingerprint.length == 64 &&
                sourceFingerprint.all {
                    it in '0'..'9' || it in 'a'..'f'
                }
        ) {
            "Version-exclusive selection state requires an exact source fingerprint."
        }
    }

    val availableChoices: List<Gen6VersionExclusiveChoice>
        get() = choices.toList()

    val selectedIds: List<String>
        get() = selection.toList()

    val selectedChoices: List<Gen6VersionExclusiveChoice>
        get() = selection.map { choicesById.getValue(it) }

    fun withSelection(
        choiceId: String,
        selected: Boolean
    ): Gen6VersionExclusiveSelectionState {
        require(choiceId in choicesById) {
            "Version-exclusive choice $choiceId is not approved for $destinationGame."
        }
        val nextSelection = when {
            selected && choiceId !in selection -> selection + choiceId
            !selected && choiceId in selection -> selection.filterNot { it == choiceId }
            else -> selection
        }
        return Gen6VersionExclusiveSelectionState(
            destinationGame,
            sourceFingerprint,
            choices,
            nextSelection
        )
    }

    companion object {
        fun from(
            preview: Gen6ConvertSavePreview
        ): Gen6VersionExclusiveSelectionState {
            val destination = preview.request.destination
            val choices = preview.availableVersionExclusives
            require(preview.versionExclusiveUnavailableReason == null) {
                "Version-exclusive selection state requires an available " +
                    "verified catalog."
            }
            require(choices.isNotEmpty()) {
                "Version-exclusive selection state requires at least one " +
                    "verified catalog choice."
            }
            require(choices.all { it.destinationGame == destination }) {
                "Version-exclusive choices must match the preview destination."
            }
            require(choices.map { it.id }.distinct().size == choices.size) {
                "Version-exclusive UI choices must have unique ids."
            }
            require(choices.map { it.species }.distinct().size == choices.size) {
                "Version-exclusive UI choices must have unique species."
            }
            require(
                choices.map { it.recordFingerprint }.distinct().size ==
                    choices.size
            ) {
                "Version-exclusive UI choices must identify unique exact PK6 records."
            }
            val selectedIds = preview.request.selectedVersionExclusiveIds
            require(
                preview.request.includeVersionExclusives ==
                    selectedIds.isNotEmpty()
            ) {
                "Version-exclusive opt-in must exactly match the reviewed selection."
            }
            require(selectedIds.size == selectedIds.distinct().size) {
                "A destination-version-exclusive Pokémon may be selected only once."
            }
            val availableIds = choices.mapTo(mutableSetOf()) { it.id }
            require(selectedIds.all { it in availableIds }) {
                "Every selected version-exclusive choice must be approved for the destination."
            }
            return Gen6VersionExclusiveSelectionState(
                destination,
                preview.sourceFingerprint,
                choices,
                selectedIds
            )
        }
    }
}
