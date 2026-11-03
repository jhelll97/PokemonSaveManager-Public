package com.example.pokemonsavemanager

private const val GEN6_PC_CAPACITY = 31 * 30
private const val GEN6_PARTY_CAPACITY = 6

/**
 * Immutable capacity result produced before any Gen 6 destination bytes are
 * serialized. Selected destination-exclusive records consume PC slots just
 * like migrated boxed PK6 records; party records consume only party slots.
 */
data class Gen6ConversionCapacityReport(
    val sourcePcRecords: Int,
    val selectedVersionExclusiveRecords: Int,
    val destinationPcSlots: Int,
    val sourcePartyRecords: Int,
    val destinationPartySlots: Int
) {
    init {
        require(sourcePcRecords >= 0) { "Source PC record count cannot be negative." }
        require(selectedVersionExclusiveRecords >= 0) {
            "Selected version-exclusive record count cannot be negative."
        }
        require(destinationPcSlots >= 0) { "Destination PC slot count cannot be negative." }
        require(sourcePartyRecords >= 0) { "Source party record count cannot be negative." }
        require(destinationPartySlots >= 0) { "Destination party slot count cannot be negative." }
        require(sourcePcRecords <= Int.MAX_VALUE - selectedVersionExclusiveRecords) {
            "Required Gen 6 PC slot count exceeds the supported range."
        }
        require(sourcePcRecords <= GEN6_PC_CAPACITY) {
            "Source PC record count cannot exceed $GEN6_PC_CAPACITY."
        }
        require(sourcePcRecords + selectedVersionExclusiveRecords <= GEN6_PC_CAPACITY) {
            "Required Gen 6 PC slot count cannot exceed $GEN6_PC_CAPACITY."
        }
        require(selectedVersionExclusiveRecords <= GEN6_PC_CAPACITY) {
            "Selected version-exclusive record count cannot exceed $GEN6_PC_CAPACITY."
        }
        require(destinationPcSlots <= GEN6_PC_CAPACITY) {
            "Destination PC slot count cannot exceed $GEN6_PC_CAPACITY."
        }
        require(sourcePartyRecords <= GEN6_PARTY_CAPACITY) {
            "Source party record count cannot exceed $GEN6_PARTY_CAPACITY."
        }
        require(destinationPartySlots <= GEN6_PARTY_CAPACITY) {
            "Destination party slot count cannot exceed $GEN6_PARTY_CAPACITY."
        }
    }

    val requiredPcSlots: Int
        get() = sourcePcRecords + selectedVersionExclusiveRecords

    val pcFits: Boolean
        get() = requiredPcSlots <= destinationPcSlots

    val partyFits: Boolean
        get() = sourcePartyRecords <= destinationPartySlots

    val missingPcSlots: Int
        get() = (requiredPcSlots - destinationPcSlots).coerceAtLeast(0)

    val missingPartySlots: Int
        get() = (sourcePartyRecords - destinationPartySlots).coerceAtLeast(0)

    val canConvert: Boolean
        get() = pcFits && partyFits

    fun requireFits() {
        require(pcFits) {
            "Destination has $destinationPcSlots vacant Gen 6 PC slots for " +
                "$sourcePcRecords source PC records and " +
                "$selectedVersionExclusiveRecords selected version-exclusive records."
        }
        require(partyFits) {
            "Destination has $destinationPartySlots vacant Gen 6 party slots for " +
                "$sourcePartyRecords source party records."
        }
    }
}

/**
 * Performs the combined PC/party capacity check against validated immutable
 * snapshots. This keeps capacity failure ahead of writer construction and
 * serialization, so neither the source nor destination template can be
 * partially changed when the destination is full.
 */
object Gen6ConversionCapacityPreflight {
    fun inspect(
        sourceBytes: ByteArray,
        destinationTemplate: ByteArray,
        request: Gen6ConverterRequest
    ): Gen6ConversionCapacityReport {
        val plan = Gen6SaveConverterPlanner.plan(request)
        plan.requireExportable()
        val stableRequest = plan.request
        val sourceSnapshot = sourceBytes.copyOf()
        val templateSnapshot = destinationTemplate.copyOf()

        val sourcePcLayout = Gen6PcStorageLayout(stableRequest.source, sourceSnapshot)
        val destinationPcLayout =
            Gen6PcStorageLayout(stableRequest.destination, templateSnapshot)
        val sourcePartyLayout =
            Gen6PartyStorageLayout(stableRequest.source, sourceSnapshot)
        val destinationPartyLayout =
            Gen6PartyStorageLayout(stableRequest.destination, templateSnapshot)

        val sourcePcRecords = sourcePcLayout.sourceExtractor().extract(
            sourceSnapshot.copyOf(),
            stableRequest.source
        ).size
        val destinationPcSlots =
            destinationPcLayout.capacityProvider().availableSlots(
                templateSnapshot.copyOf(),
                stableRequest.destination
            )
        val sourcePartyRecords = sourcePartyLayout.sourceRecords(
            sourceSnapshot.copyOf(),
            stableRequest.source
        ).size
        val destinationPartySlots = destinationPartyLayout.availableSlots(
            templateSnapshot.copyOf(),
            stableRequest.destination
        )

        check(sourceBytes.contentEquals(sourceSnapshot)) {
            "Gen 6 capacity inspection changed the conversion source."
        }
        check(destinationTemplate.contentEquals(templateSnapshot)) {
            "Gen 6 capacity inspection changed the destination template."
        }
        return Gen6ConversionCapacityReport(
            sourcePcRecords = sourcePcRecords,
            selectedVersionExclusiveRecords =
                stableRequest.selectedVersionExclusiveIds.size,
            destinationPcSlots = destinationPcSlots,
            sourcePartyRecords = sourcePartyRecords,
            destinationPartySlots = destinationPartySlots
        )
    }
}
