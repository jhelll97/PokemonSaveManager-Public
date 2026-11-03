package com.example.pokemonsavemanager

import java.io.File
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files

fun interface Gen6VersionExclusiveManifestSource {
    fun read(): List<Gen6VersionExclusiveEvidenceManifest>
}

/**
 * Reads an installed, human-auditable UTF-8 TSV evidence manifest.
 *
 * The exact header is:
 * id<TAB>destination<TAB>species<TAB>record_sha256<TAB>evidence
 *
 * Records remain separate .pk6 files resolved by
 * [Gen6DirectoryVersionExclusiveRecordSource]. This source never supplies or
 * approves encounter data itself.
 */
class Gen6TsvVersionExclusiveManifestSource private constructor(
    manifestFile: File,
    private val afterCapture: () -> Unit
) : Gen6VersionExclusiveManifestSource {
    constructor(manifestFile: File) : this(manifestFile, {})

    private val declaredFile = manifestFile.absoluteFile

    override fun read(): List<Gen6VersionExclusiveEvidenceManifest> {
        requireUnlinkedManifestPath()
        val source = declaredFile.canonicalFile
        require(source.isFile) {
            "Version-exclusive evidence manifest does not exist."
        }
        val snapshot = Gen6SourceFileSnapshot.capture(source)
        afterCapture()
        val lines = readUtf8Lines(snapshot.bytes)
        require(lines.firstOrNull() == HEADER) {
            "Version-exclusive evidence manifest header is invalid."
        }
        val manifestRows = lines.drop(1)
        val blankRowIndex = manifestRows.indexOfFirst { it.isBlank() }
        require(blankRowIndex == -1) {
            "Version-exclusive evidence manifest line " +
                "${blankRowIndex + 2} must not be blank."
        }
        val manifests = manifestRows.mapIndexed { index, line ->
            parseLine(line, index + 2)
        }
        require(manifests.isNotEmpty()) {
            "Version-exclusive evidence manifest contains no records."
        }
        require(manifests.map { it.id }.distinct().size == manifests.size) {
            "Version-exclusive evidence manifest contains duplicate ids."
        }
        require(
            manifests.map { it.recordFingerprint }.distinct().size ==
                manifests.size
        ) {
            "Version-exclusive evidence manifest contains duplicate record fingerprints."
        }
        require(
            manifests
                .map { it.destinationGame to it.species }
                .distinct()
                .size == manifests.size
        ) {
            "Version-exclusive evidence manifest contains duplicate destination species."
        }
        requireUnlinkedManifestPath()
        require(declaredFile.canonicalFile == source) {
            "Version-exclusive evidence manifest path identity changed."
        }
        snapshot.requireUnchanged(
            "Version-exclusive evidence manifest"
        )
        return manifests.toList()
    }

    private fun readUtf8Lines(bytes: ByteArray): List<String> {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            InputStreamReader(bytes.inputStream(), decoder)
                .buffered()
                .use { it.readLines() }
        } catch (failure: CharacterCodingException) {
            throw IllegalArgumentException(
                "Version-exclusive evidence manifest must contain valid UTF-8.",
                failure
            )
        }
    }

    private fun requireUnlinkedManifestPath() {
        val declaredDirectory = requireNotNull(declaredFile.parentFile) {
            "Version-exclusive evidence manifest requires an installation directory."
        }
        require(!Files.isSymbolicLink(declaredDirectory.toPath())) {
            "Version-exclusive evidence manifest directory must not be a symbolic link."
        }
        require(!Files.isSymbolicLink(declaredFile.toPath())) {
            "Version-exclusive evidence manifest must not be a symbolic link."
        }
    }

    private fun parseLine(
        line: String,
        lineNumber: Int
    ): Gen6VersionExclusiveEvidenceManifest {
        val fields = line.split('\t')
        require(fields.size == FIELD_COUNT && fields.none { it.isBlank() }) {
            "Version-exclusive evidence manifest line $lineNumber must contain five non-blank fields."
        }
        require(SAFE_MANIFEST_ID.matches(fields[0])) {
            "Version-exclusive evidence manifest line $lineNumber has an unsafe id."
        }
        val destination = try {
            Gen6ConverterGame.valueOf(fields[1])
        } catch (invalid: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Version-exclusive evidence manifest line $lineNumber has an invalid destination.",
                invalid
            )
        }
        val speciesText = fields[2]
        val species = speciesText.toIntOrNull()
            ?: throw IllegalArgumentException(
                "Version-exclusive evidence manifest line $lineNumber has an invalid species."
            )
        require(speciesText == species.toString()) {
            "Version-exclusive evidence manifest line $lineNumber must use a canonical decimal species."
        }
        return Gen6VersionExclusiveEvidenceManifest(
            id = fields[0],
            destinationGame = destination,
            species = species,
            recordFingerprint = fields[3],
            encounterEvidenceReference = fields[4]
        )
    }

    companion object {
        internal fun withAfterCaptureForTest(
            manifestFile: File,
            afterCapture: () -> Unit
        ): Gen6TsvVersionExclusiveManifestSource =
            Gen6TsvVersionExclusiveManifestSource(
                manifestFile,
                afterCapture
            )

        private const val HEADER =
            "id\tdestination\tspecies\trecord_sha256\tevidence"
        private const val FIELD_COUNT = 5
        private val SAFE_MANIFEST_ID =
            Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
    }
}
