package com.example.pokemonsavemanager

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

private val GEN6_VERSION_EXCLUSIVE_RECORD_ID =
    Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
private const val MAX_AUDITABLE_GEN6_EVIDENCE_LENGTH = 512

private fun isAuditableGen6EvidenceReference(reference: String): Boolean =
    reference.isNotBlank() &&
        reference.length <= MAX_AUDITABLE_GEN6_EVIDENCE_LENGTH &&
        reference == reference.trim() &&
        reference.none {
            Character.isISOControl(it) ||
                Character.getType(it) == Character.FORMAT.toInt() ||
                Character.getType(it) == Character.LINE_SEPARATOR.toInt() ||
                Character.getType(it) == Character.PARAGRAPH_SEPARATOR.toInt()
        }

data class Gen6VersionExclusiveLegalityResult(
    val destinationGame: Gen6ConverterGame,
    val species: Int,
    val encounterLegal: Boolean,
    val movesLegal: Boolean,
    val abilityLegal: Boolean,
    val formLegal: Boolean,
    val originMetadataLegal: Boolean,
    val note: String = ""
) {
    init {
        require(
            note.isEmpty() || isAuditableGen6EvidenceReference(note)
        ) {
            "Version-exclusive legality notes must be bounded, trimmed and single-line."
        }
    }

    val isLegal: Boolean
        get() = encounterLegal && movesLegal && abilityLegal &&
            formLegal && originMetadataLegal

    internal fun failedChecks(): String = buildList {
        if (!encounterLegal) add("encounter")
        if (!movesLegal) add("moves")
        if (!abilityLegal) add("ability")
        if (!formLegal) add("form")
        if (!originMetadataLegal) add("origin metadata")
    }.joinToString()
}

fun interface Gen6VersionExclusiveLegalityVerifier {
    fun verify(
        destinationGame: Gen6ConverterGame,
        record: Gen6Pk6Record,
        evidenceReference: String
    ): Gen6VersionExclusiveLegalityResult
}

/**
 * One evidence-backed, destination-specific PK6 record offered as an optional
 * version exclusive. Catalog construction is an explicit trust boundary: the
 * caller must supply the legality evidence used to approve the exact record.
 */
internal fun gen6VersionExclusiveRecordFingerprint(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            append("0123456789abcdef"[value ushr 4])
            append("0123456789abcdef"[value and 0x0F])
        }
    }
}

/**
 * Audited metadata for one production catalog record. The expected fingerprint
 * is stored separately from the PK6 source so substituted or stale bytes fail
 * before legality verification or UI exposure.
 */
data class Gen6VersionExclusiveEvidenceManifest(
    val id: String,
    val destinationGame: Gen6ConverterGame,
    val species: Int,
    val recordFingerprint: String,
    val encounterEvidenceReference: String
) {
    init {
        require(GEN6_VERSION_EXCLUSIVE_RECORD_ID.matches(id)) {
            "Version-exclusive evidence id must be a safe lowercase record id."
        }
        require(species in 1..721) {
            "Version-exclusive evidence ${id} has invalid Gen 6 species ${species}."
        }
        require(
            recordFingerprint.length == 64 &&
                recordFingerprint.all {
                    it in '0'..'9' || it in 'a'..'f'
                }
        ) {
            "Version-exclusive evidence ${id} requires a lowercase SHA-256 fingerprint."
        }
        require(
            isAuditableGen6EvidenceReference(encounterEvidenceReference)
        ) {
            "Version-exclusive evidence ${id} requires an auditable " +
                "single-line encounter evidence reference."
        }
    }

}

fun interface Gen6VersionExclusiveRecordSource {
    fun read(manifestId: String): ByteArray?
}

/**
 * Reads lawful user-installed PK6 records without allowing a manifest id to
 * escape the configured directory. Symbolic links are rejected so an audited
 * manifest can only resolve the exact regular file installed for its id.
 */
class Gen6DirectoryVersionExclusiveRecordSource private constructor(
    directory: File,
    private val afterRecordCapture: (String, File) -> Unit
) : Gen6VersionExclusiveRecordSource {
    constructor(directory: File) : this(directory, { _, _ -> })

    private val declaredRoot = directory.absoluteFile
    private val root = declaredRoot.canonicalFile
    private val rootAttributes: BasicFileAttributes

    init {
        require(!Files.isSymbolicLink(declaredRoot.toPath())) {
            "Version-exclusive record directory must not be a symbolic link."
        }
        require(root.isDirectory) {
            "Version-exclusive record directory does not exist."
        }
        rootAttributes = directoryAttributes()
    }

    override fun read(manifestId: String): ByteArray? {
        requireRootUnchanged()
        require(GEN6_VERSION_EXCLUSIVE_RECORD_ID.matches(manifestId)) {
            "Version-exclusive manifest id is not a safe record filename."
        }
        val declared = File(root, "$manifestId.pk6")
        require(!Files.isSymbolicLink(declared.toPath())) {
            "Version-exclusive record $manifestId must not be a symbolic link."
        }
        if (!declared.exists()) return null
        val recordFile = declared.canonicalFile
        require(recordFile.parentFile == root && recordFile.isFile) {
            "Version-exclusive record $manifestId must be a regular file in the installed record directory."
        }
        val recordSnapshot = Gen6SourceFileSnapshot.capture(recordFile)
        afterRecordCapture(manifestId, recordFile)
        val bytes = recordSnapshot.bytes
        require(bytes.size == Gen6Pk6Adapter.STORED_SIZE) {
            "Version-exclusive record $manifestId has ${bytes.size} bytes; " +
                "expected ${Gen6Pk6Adapter.STORED_SIZE}."
        }
        recordSnapshot.requireUnchanged(
            "Version-exclusive record $manifestId"
        )
        requireRootUnchanged()
        return bytes.copyOf()
    }

    private fun requireRootUnchanged() {
        check(declaredRoot.canonicalFile == root) {
            "Version-exclusive record directory path identity changed."
        }
        val current = directoryAttributes()
        check(
            (
                rootAttributes.fileKey() == null ||
                    current.fileKey() == rootAttributes.fileKey()
            ) &&
                current.lastModifiedTime() ==
                    rootAttributes.lastModifiedTime() &&
                current.size() == rootAttributes.size()
        ) {
            "Version-exclusive record directory identity changed."
        }
    }

    private fun directoryAttributes(): BasicFileAttributes =
        Files.readAttributes(
            root.toPath(),
            BasicFileAttributes::class.java
        )

    companion object {
        internal fun withAfterRecordCaptureForTest(
            directory: File,
            afterRecordCapture: (String, File) -> Unit
        ): Gen6DirectoryVersionExclusiveRecordSource =
            Gen6DirectoryVersionExclusiveRecordSource(
                directory,
                afterRecordCapture
            )
    }
}

/**
 * Loads a catalog only when every audited manifest resolves to the exact PK6
 * bytes that were approved. No partial catalog is returned on missing or
 * mismatched evidence.
 */
class Gen6VersionExclusiveCatalogProvider(
    manifests: Iterable<Gen6VersionExclusiveEvidenceManifest>,
    private val recordSource: Gen6VersionExclusiveRecordSource,
    private val legalityVerifier: Gen6VersionExclusiveLegalityVerifier
) {
    private val manifestsInOrder = manifests.toList()

    init {
        require(manifestsInOrder.isNotEmpty()) {
            "Version-exclusive evidence manifests must not be empty."
        }
        val ids = mutableSetOf<String>()
        val speciesByDestination =
            mutableSetOf<Pair<Gen6ConverterGame, Int>>()
        val recordFingerprints = mutableSetOf<String>()
        for (manifest in manifestsInOrder) {
            require(ids.add(manifest.id)) {
                "Duplicate Gen 6 version-exclusive evidence id ${manifest.id}."
            }
            require(
                speciesByDestination.add(
                    manifest.destinationGame to manifest.species
                )
            ) {
                "Duplicate Gen 6 version-exclusive evidence species " +
                    "${manifest.species} for ${manifest.destinationGame}."
            }
            require(recordFingerprints.add(manifest.recordFingerprint)) {
                "Duplicate Gen 6 version-exclusive audited PK6 fingerprint " +
                    "${manifest.recordFingerprint}."
            }
        }
    }

    fun load(): Gen6VersionExclusiveCatalog {
        val entries = manifestsInOrder.map { manifest ->
            val record = requireNotNull(recordSource.read(manifest.id)) {
                "Version-exclusive record ${manifest.id} is missing."
            }.copyOf()
            require(
                gen6VersionExclusiveRecordFingerprint(record) ==
                    manifest.recordFingerprint
            ) {
                "Version-exclusive record ${manifest.id} does not match its audited fingerprint."
            }
            Gen6VersionExclusiveEntry(
                id = manifest.id,
                destinationGame = manifest.destinationGame,
                species = manifest.species,
                encryptedPk6 = record,
                legalityEvidence = manifest.encounterEvidenceReference
            )
        }
        return Gen6VersionExclusiveCatalog(entries, legalityVerifier)
    }
}

data class Gen6VersionExclusiveChoice(
    val id: String,
    val destinationGame: Gen6ConverterGame,
    val species: Int,
    val legalityEvidence: String,
    val recordFingerprint: String
) {
    init {
        require(id.isNotBlank()) {
            "Version-exclusive choice id must not be blank."
        }
        require(GEN6_VERSION_EXCLUSIVE_RECORD_ID.matches(id)) {
            "Version-exclusive choice id must be a safe lowercase record id."
        }
        require(species in 1..721) {
            "Version-exclusive choice $id has invalid Gen 6 species $species."
        }
        require(isAuditableGen6EvidenceReference(legalityEvidence)) {
            "Version-exclusive choice $id requires legality evidence that is " +
                "trimmed and single-line."
        }
        require(
            recordFingerprint.length == 64 &&
                recordFingerprint.all {
                    it in '0'..'9' || it in 'a'..'f'
                }
        ) {
            "Version-exclusive choice must identify its exact PK6 record."
        }
    }
}

class Gen6VersionExclusiveEntry(
    val id: String,
    val destinationGame: Gen6ConverterGame,
    val species: Int,
    encryptedPk6: ByteArray,
    val legalityEvidence: String
) {
    private val record = encryptedPk6.copyOf()

    init {
        require(id.isNotBlank()) { "Version-exclusive catalog id must not be blank." }
        require(GEN6_VERSION_EXCLUSIVE_RECORD_ID.matches(id)) {
            "Version-exclusive catalog id must be a safe lowercase record id."
        }
        require(species in 1..721) {
            "Version-exclusive entry $id has invalid Gen 6 species $species."
        }
        require(isAuditableGen6EvidenceReference(legalityEvidence)) {
            "Version-exclusive entry $id requires legality evidence that is " +
                "trimmed and single-line."
        }
        val decoded = Gen6Pk6Adapter.decode(record.copyOf())
        require(decoded.species == species) {
            "Version-exclusive entry $id does not contain species $species."
        }
    }

    internal fun recordBytes(): ByteArray = record.copyOf()

    internal fun choice() = Gen6VersionExclusiveChoice(
        id = id,
        destinationGame = destinationGame,
        species = species,
        legalityEvidence = legalityEvidence,
        recordFingerprint = gen6VersionExclusiveRecordFingerprint(record)
    )
}

/**
 * Resolves only the entries the user explicitly selected, in selection order.
 * It rejects duplicate ids and records intended for another destination title.
 */
class Gen6VersionExclusiveCatalog(
    entries: Iterable<Gen6VersionExclusiveEntry>,
    private val legalityVerifier: Gen6VersionExclusiveLegalityVerifier
) {
    private val entriesInOrder = entries.toList()

    private val entriesById = buildMap {
        val speciesByDestination = mutableSetOf<Pair<Gen6ConverterGame, Int>>()
        val recordFingerprints = mutableSetOf<String>()
        for (entry in entriesInOrder) {
            check(put(entry.id, entry) == null) {
                "Duplicate Gen 6 version-exclusive catalog id ${entry.id}."
            }
            check(speciesByDestination.add(entry.destinationGame to entry.species)) {
                "Duplicate Gen 6 version-exclusive species ${entry.species} for ${entry.destinationGame}."
            }
            val recordFingerprint =
                gen6VersionExclusiveRecordFingerprint(entry.recordBytes())
            check(recordFingerprints.add(recordFingerprint)) {
                "Duplicate Gen 6 version-exclusive catalog PK6 fingerprint " +
                    "$recordFingerprint."
            }
        }
        for (entry in entriesInOrder) {
            verifiedRecord(entry)
        }
    }

    fun choicesFor(
        destinationGame: Gen6ConverterGame
    ): List<Gen6VersionExclusiveChoice> =
        entriesInOrder
            .filter { it.destinationGame == destinationGame }
            .map { it.choice() }

    fun selectedChoicesFor(
        destinationGame: Gen6ConverterGame,
        selectedIds: List<String>
    ): List<Gen6VersionExclusiveChoice> =
        selectedEntries(destinationGame, selectedIds).map { it.choice() }

    fun resolve(
        destinationGame: Gen6ConverterGame,
        selectedIds: List<String>
    ): List<ByteArray> =
        selectedEntries(destinationGame, selectedIds).map { entry ->
            // Revalidate the exact bytes and legality at use time instead
            // of trusting catalog construction alone.
            verifiedRecord(entry)
        }

    internal fun scopedTo(
        destinationGame: Gen6ConverterGame
    ): Gen6VersionExclusiveCatalog =
        Gen6VersionExclusiveCatalog(
            entriesInOrder.filter { it.destinationGame == destinationGame },
            legalityVerifier
        )

    private fun selectedEntries(
        destinationGame: Gen6ConverterGame,
        selectedIds: List<String>
    ): List<Gen6VersionExclusiveEntry> {
        val stableSelectedIds = selectedIds.toList()
        require(stableSelectedIds.isNotEmpty()) {
            "Select at least one destination-version-exclusive Pokémon."
        }
        require(stableSelectedIds.none { it.isBlank() }) {
            "Version-exclusive selection ids must not be blank."
        }
        require(
            stableSelectedIds.all {
                GEN6_VERSION_EXCLUSIVE_RECORD_ID.matches(it)
            }
        ) {
            "Version-exclusive selection ids must be safe lowercase record ids."
        }
        val seenIds = mutableSetOf<String>()
        val duplicateId =
            stableSelectedIds.firstOrNull { !seenIds.add(it) }
        require(duplicateId == null) {
            "Destination-version-exclusive selection $duplicateId " +
                "may be selected only once."
        }
        return stableSelectedIds.map { id ->
            val entry = entriesById[id]
                ?: throw IllegalArgumentException(
                    "Version-exclusive selection $id is not in the verified catalog."
                )
            require(entry.destinationGame == destinationGame) {
                "Version-exclusive selection $id is for ${entry.destinationGame}, not $destinationGame."
            }
            entry
        }
    }

    private fun verifiedRecord(entry: Gen6VersionExclusiveEntry): ByteArray {
        val record = entry.recordBytes()
        val decoded = Gen6Pk6Adapter.decode(record)
        require(decoded.species == entry.species) {
            "Version-exclusive selection ${entry.id} changed after catalog validation."
        }
        val encryptedBefore = decoded.encryptedBytes.copyOf()
        val decryptedBefore = decoded.decryptedBytes.copyOf()
        val movesBefore = decoded.moves.copyOf()
        val movePpBefore = decoded.movePp.copyOf()
        val ivsBefore = decoded.ivs.copyOf()
        val evsBefore = decoded.evs.copyOf()
        val result = legalityVerifier.verify(
            entry.destinationGame,
            decoded,
            entry.legalityEvidence
        )
        check(
            decoded.encryptedBytes.contentEquals(encryptedBefore) &&
                decoded.decryptedBytes.contentEquals(decryptedBefore) &&
                decoded.moves.contentEquals(movesBefore) &&
                decoded.movePp.contentEquals(movePpBefore) &&
                decoded.ivs.contentEquals(ivsBefore) &&
                decoded.evs.contentEquals(evsBefore)
        ) {
            "Version-exclusive legality verifier changed decoded PK6 record ${entry.id}."
        }
        require(result.destinationGame == entry.destinationGame) {
            "Version-exclusive legality result targeted ${result.destinationGame}, " +
                "not ${entry.destinationGame}."
        }
        require(result.species == entry.species) {
            "Version-exclusive legality result identified species ${result.species}, " +
                "not ${entry.species}."
        }
        require(result.isLegal) {
            val failed = result.failedChecks().ifBlank { "unspecified checks" }
            "Version-exclusive entry ${entry.id} failed $failed legality verification" +
                result.note.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        }
        return record
    }
}
