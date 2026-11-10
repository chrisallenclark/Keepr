package com.chrisallenclark.keepr.core.backup

import com.chrisallenclark.keepr.core.InMemoryPreferences
import com.chrisallenclark.keepr.core.persistence.KeeprStore
import com.chrisallenclark.keepr.core.persistence.PreferenceKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** The .keeprbackup format: round trips, wrong passwords, and every kind of tampering. */
class BackupTests {

    private val password = "correct horse battery".toCharArray()
    /** Low, so the suite stays quick; the count is read from each header, never assumed. */
    private val iterations = 1_000
    private val chunk = 4096
    private val created = Instant.parse("2026-11-10T09:30:00Z")
    private val app = BackupAppInfo(versionName = "0.9.0", versionCode = 42, databaseSchemaVersion = 1)
    private val scratch: File = Files.createTempDirectory("keepr-backup-test").toFile()
    private val staging = File(scratch, "staging")

    @AfterTest
    fun cleanUp() {
        scratch.deleteRecursively()
    }

    // MARK: - Fixtures

    private val photo = ByteArray(3_000) { (it * 7).toByte() }
    private val fileBytes = Random(1).nextBytes(10_000)
    private val thumbnail = ByteArray(500) { (it % 251).toByte() }

    private fun records(): BackupRecords {
        val alice = PersonRecord(
            id = "11111111-1111-1111-1111-111111111111", contactIdentifier = "contact-1", givenName = "Alice",
            familyName = "Ng", preferredName = "Al", company = "Acme", jobTitle = "CTO", workNote = "Runs platform",
            phoneNumbers = "+1 555 0100\n+1 555 0101", emailAddresses = "alice@example.com", photoBlob = BackupFormat.personPhotoBlob("11111111-1111-1111-1111-111111111111"),
            contextRaw = "both", priorityRaw = "high", statusRaw = "active", isFavorite = true, createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_100_000, lastInteractionAt = 1_700_000_200_000, howWeMet = "Conference — “quotes” and \"escapes\"\n",
            introducedBy = "Bob", notes = "Likes ☕️ and 日本", birthday = 631_152_000_000, postalAddress = "1 Main St",
            contactNote = null, cadenceDays = 30, awaitingReplySince = null, reviewedAt = 1_700_000_300_000,
            isSampleData = false, modifiedAt = 1_700_000_400_000,
        )
        val bob = alice.copy(
            id = "22222222-2222-2222-2222-222222222222", givenName = "Bob", familyName = "", preferredName = null,
            photoBlob = null, isFavorite = false, cadenceDays = null, birthday = null, phoneNumbers = "", emailAddresses = "",
        )
        val interaction = InteractionRecord(
            id = "33333333-3333-3333-3333-333333333333", personId = alice.id, occurredAt = 1_700_000_500_000,
            createdAt = 1_700_000_500_001, kindRaw = "meeting", title = "Lunch", rawNote = "Talked about the launch",
            summary = null, isQuickCapture = true, awaitingReply = true, modifiedAt = 5,
        )
        val note = NoteRecord(
            id = "44444444-4444-4444-4444-444444444444", personId = alice.id, sourceInteractionId = interaction.id,
            title = "Launch plan", body = "Line one\nLine two", sourceRaw = "typed", occurredAt = 1, createdAt = 2,
            updatedAt = 3, isPinned = true, modifiedAt = 4,
        )
        val tag = TagRecord(
            id = "55555555-5555-5555-5555-555555555555", name = "Client", kindRaw = "business", isBuiltIn = true,
            builtInKey = "Client", aliases = "customer\nbuyer", cadenceDays = 14, sortOrder = 10, symbolName = "briefcase",
            createdAt = 6, modifiedAt = 7,
        )
        val group = GroupRecord(
            id = "66666666-6666-6666-6666-666666666666", name = "Life Time", symbolName = "figure.run", detail = "Gym",
            aliases = null, contextRaw = "personal", createdAt = 8, sortOrder = 1, isSampleData = false, modifiedAt = 9,
        )
        return BackupRecords(
            people = listOf(alice, bob),
            interactions = listOf(interaction),
            memories = listOf(
                MemoryRecord(
                    id = "77777777-7777-7777-7777-777777777777", personId = alice.id, sourceInteractionId = interaction.id,
                    content = "Daughter starts college in fall", categoryRaw = "family", importanceRaw = "high",
                    createdAt = 10, updatedAt = 11, isArchived = false, modifiedAt = 12,
                ),
            ),
            followUps = listOf(
                FollowUpRecord(
                    id = "88888888-8888-8888-8888-888888888888", personId = alice.id, sourceInteractionId = interaction.id,
                    title = "Send the proposal", note = "Before Friday", dueDate = 1_800_000_000_000, hasTime = true,
                    priorityRaw = "high", isCompleted = false, completedAt = null, createdAt = 13, lastSnoozedAt = 14,
                    notificationIdentifier = "followup-88888888", modifiedAt = 15,
                ),
            ),
            notes = listOf(note),
            files = listOf(
                FileRecord(
                    id = "99999999-9999-9999-9999-999999999999", personId = alice.id, sourceInteractionId = interaction.id,
                    conversationNoteId = note.id, title = "Signed form", kindRaw = "pdf", fileExtension = "pdf",
                    dataBlob = BackupFormat.fileDataBlob("99999999-9999-9999-9999-999999999999"),
                    thumbnailBlob = BackupFormat.fileThumbnailBlob("99999999-9999-9999-9999-999999999999"),
                    byteCount = fileBytes.size, capturedAt = 16, createdAt = 17, note = "Page 2 matters", modifiedAt = 18,
                ),
                FileRecord(
                    id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", personId = bob.id, sourceInteractionId = null,
                    conversationNoteId = null, title = "Bytes never arrived", kindRaw = "image", fileExtension = "jpg",
                    dataBlob = null, thumbnailBlob = null, byteCount = 0, capturedAt = 19, createdAt = 20, note = null, modifiedAt = 21,
                ),
            ),
            tags = listOf(tag),
            groups = listOf(group),
            links = listOf(
                LinkRecord(
                    id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", personAId = alice.id, personBId = bob.id,
                    labelAToB = "Manager of", labelBToA = "Reports to", note = "Since 2024", createdAt = 22, modifiedAt = 23,
                ),
            ),
            personTags = listOf(PersonTagRecord(alice.id, tag.id, 0), PersonTagRecord(bob.id, tag.id, 0)),
            groupMembers = listOf(GroupMemberRecord(alice.id, group.id, 0)),
            deletions = listOf(DeletionRecord("cccccccc-cccc-cccc-cccc-cccccccccccc", "people", 24)),
            settings = mapOf(
                PreferenceKey.CONTEXT_MODE to SettingValue.Text("personal"),
                PreferenceKey.HAS_ONBOARDED to SettingValue.Switch(true),
                KeeprStore.SEEDED_KEYS_DEFAULT to SettingValue.TextList(listOf("Client", "Friend")),
            ),
        )
    }

    private fun blobs(file: ByteArray = fileBytes): List<BackupBlobSource> = listOf(
        BackupBlobSource(BackupFormat.personPhotoBlob("11111111-1111-1111-1111-111111111111")) { ByteArrayInputStream(photo) },
        BackupBlobSource(BackupFormat.fileDataBlob("99999999-9999-9999-9999-999999999999")) { ByteArrayInputStream(file) },
        BackupBlobSource(BackupFormat.fileThumbnailBlob("99999999-9999-9999-9999-999999999999")) { ByteArrayInputStream(thumbnail) },
    )

    private fun backup(
        records: BackupRecords = records(),
        file: ByteArray = fileBytes,
        chunkSize: Int = chunk,
        tamper: BackupWriter.Tamper = BackupWriter.Tamper(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        BackupWriter.write(out, password, records, blobs(file), app, created, iterations, chunkSize, SecureRandom(), { _, _ -> }, tamper)
        return out.toByteArray()
    }

    private fun stage(bytes: ByteArray, with: CharArray = password): StagedBackup =
        BackupReader.stage(ByteArrayInputStream(bytes), with, staging)

    private fun headerLength(bytes: ByteArray) = BackupHeader.read(ByteArrayInputStream(bytes)).bytes.size

    /** The sealed chunks of [bytes], after the header. */
    private fun chunks(bytes: ByteArray, chunkSize: Int = chunk): List<ByteArray> {
        val start = headerLength(bytes)
        val sealed = chunkSize + BackupCrypto.TAG_LENGTH
        return (start until bytes.size step sealed).map { bytes.copyOfRange(it, minOf(it + sealed, bytes.size)) }
    }

    private fun assemble(bytes: ByteArray, chunks: List<ByteArray>): ByteArray =
        chunks.fold(bytes.copyOfRange(0, headerLength(bytes))) { acc, c -> acc + c }

    private inline fun <reified E : BackupException> assertRejected(bytes: ByteArray, with: CharArray = password) {
        assertFailsWith<E> { stage(bytes, with) }
        assertFalse(staging.exists(), "staging should be cleaned up after a failure")
    }

    // MARK: - Round trips

    @Test
    fun roundTripsEveryRecordTypeRelationshipAndBlob() {
        val original = records()
        val staged = stage(backup(original))
        assertEquals(original, staged.records)
        assertContentEquals(photo, staged.readBlob(BackupFormat.personPhotoBlob(original.people[0].id)))
        assertContentEquals(fileBytes, staged.readBlob(BackupFormat.fileDataBlob(original.files[0].id)))
        assertContentEquals(thumbnail, staged.readBlob(BackupFormat.fileThumbnailBlob(original.files[0].id)))

        val manifest = staged.manifest
        assertEquals(BackupFormat.FORMAT_MAJOR, manifest.formatVersion)
        assertEquals(created, manifest.createdAt)
        assertEquals(app, manifest.app)
        assertEquals(2, manifest.count("people"))
        assertEquals(2, manifest.count("files"))
        assertEquals(1, manifest.count("deletions"))
        assertEquals(3, manifest.blobs.size)
        assertEquals(
            sha256Hex(fileBytes),
            manifest.blobs.single { it.name == BackupFormat.fileDataBlob(original.files[0].id) }.sha256,
        )
        assertTrue(staged.directory.isDirectory)
        staged.discard()
        assertFalse(staging.exists())
    }

    @Test
    fun emptyStoreRoundTrips() {
        val out = ByteArrayOutputStream()
        BackupWriter.write(out, password, BackupRecords(), emptyList(), app, created, iterations, chunk)
        val staged = stage(out.toByteArray())
        assertEquals(BackupRecords(), staged.records)
        assertEquals(0, staged.manifest.count("people"))
    }

    @Test
    fun largeBlobSpansManyChunks() {
        val large = Random(7).nextBytes(chunk * 40 + 123)
        val bytes = backup(file = large)
        assertTrue(chunks(bytes).size > 40)
        val staged = stage(bytes)
        assertContentEquals(large, staged.readBlob(BackupFormat.fileDataBlob(records().files[0].id)))
    }

    @Test
    fun defaultChunkSizeHandlesBlobLargerThanAChunk() {
        val large = Random(9).nextBytes(BackupFormat.DEFAULT_CHUNK_SIZE * 2 + 17)
        val bytes = backup(file = large, chunkSize = BackupFormat.DEFAULT_CHUNK_SIZE)
        val staged = stage(bytes)
        assertContentEquals(large, staged.readBlob(BackupFormat.fileDataBlob(records().files[0].id)))
    }

    @Test
    fun plaintextExactlyFillingChunksStillHasAFinalChunk() {
        // Whatever the content length, the file always ends in a chunk marked
        // final; here the last one may be empty.
        for (size in listOf(0, 1, chunk - 1, chunk, chunk + 1)) {
            val out = ByteArrayOutputStream()
            val (header, keys) = BackupCrypto.newHeader(password, iterations, chunk, SecureRandom())
            val stream = BackupEncryptingOutputStream(out, header, keys)
            val data = Random(size).nextBytes(size)
            stream.write(data)
            stream.finish()
            val input = ByteArrayInputStream(out.toByteArray())
            val readHeader = BackupHeader.read(input)
            val decrypted = BackupDecryptingInputStream(input, readHeader, BackupCrypto.open(password, readHeader)).readBytes()
            assertContentEquals(data, decrypted, "size $size")
        }
    }

    @Test
    fun iterationCountIsStoredInTheHeader() {
        val bytes = backup()
        assertEquals(iterations, BackupHeader.read(ByteArrayInputStream(bytes)).iterations)
        assertEquals(600_000, BackupFormat.DEFAULT_ITERATIONS)
    }

    @Test
    fun eachBackupUsesFreshSaltAndNonce() {
        val a = BackupHeader.read(ByteArrayInputStream(backup()))
        val b = BackupHeader.read(ByteArrayInputStream(backup()))
        assertFalse(a.salt.contentEquals(b.salt))
        assertFalse(a.noncePrefix.contentEquals(b.noncePrefix))
    }

    @Test
    fun plaintextDoesNotAppearInTheFile() {
        val text = String(backup(), Charsets.ISO_8859_1)
        listOf("Alice", "manifest.json", "records.json", "Send the proposal").forEach { assertFalse(it in text, it) }
    }

    // MARK: - Passwords

    @Test
    fun wrongPasswordIsRefusedCleanly() {
        val bytes = backup()
        assertRejected<BackupException.WrongPassword>(bytes, "correct horse battery!".toCharArray())
        assertRejected<BackupException.WrongPassword>(bytes, "".toCharArray())
    }

    @Test
    fun passwordIsNormalizedSoComposedAndDecomposedMatch() {
        val composed = "café au lait".toCharArray()
        val decomposed = "café au lait".toCharArray()
        val out = ByteArrayOutputStream()
        BackupWriter.write(out, composed, records(), blobs(), app, created, iterations, chunk)
        assertEquals(records(), BackupReader.stage(ByteArrayInputStream(out.toByteArray()), decomposed, staging).records)
    }

    // MARK: - Tampering

    @Test
    fun everyHeaderByteFlipIsRejected() {
        val bytes = backup()
        for (position in 0 until headerLength(bytes)) {
            val tampered = bytes.copyOf().also { it[position] = (it[position].toInt() xor 0x01).toByte() }
            try {
                stage(tampered)
                fail("header byte $position flipped was accepted")
            } catch (expected: BackupException) {
                assertFalse(staging.exists())
            }
        }
    }

    @Test
    fun flippedBitInAnyChunkIsRejected() {
        val bytes = backup()
        val parts = chunks(bytes)
        assertTrue(parts.size >= 3)
        for (index in listOf(0, parts.size / 2, parts.size - 1)) {
            for (offset in listOf(0, parts[index].size / 2, parts[index].size - 1)) {
                val changed = parts.toMutableList()
                changed[index] = parts[index].copyOf().also { it[offset] = (it[offset].toInt() xor 0x80).toByte() }
                assertRejected<BackupException.Damaged>(assemble(bytes, changed))
            }
        }
    }

    @Test
    fun reorderedChunksAreRejected() {
        val bytes = backup()
        val parts = chunks(bytes)
        val swapped = parts.toMutableList().also { java.util.Collections.swap(it, 0, 1) }
        assertRejected<BackupException.Damaged>(assemble(bytes, swapped))
        val lastTwo = parts.toMutableList().also { java.util.Collections.swap(it, it.size - 2, it.size - 1) }
        assertRejected<BackupException.Damaged>(assemble(bytes, lastTwo))
    }

    @Test
    fun droppedOrRepeatedChunksAreRejected() {
        val bytes = backup()
        val parts = chunks(bytes)
        assertRejected<BackupException.Damaged>(assemble(bytes, parts.toMutableList().also { it.removeAt(1) }))
        assertRejected<BackupException.Damaged>(assemble(bytes, parts.toMutableList().also { it.add(1, parts[1]) }))
    }

    @Test
    fun truncationIsRejected() {
        val bytes = backup()
        val header = headerLength(bytes)
        val sealed = chunk + BackupCrypto.TAG_LENGTH
        // At a chunk boundary (the last whole chunk then claims to be final), mid-chunk,
        // one byte short, just the header, and nothing at all.
        listOf(header + sealed * 2, header + sealed + 100, bytes.size - 1, header + 10, header).forEach { length ->
            assertRejected<BackupException.Damaged>(bytes.copyOf(length))
        }
        assertRejected<BackupException.NotABackup>(ByteArray(0))
        assertRejected<BackupException.NotABackup>(bytes.copyOf(10))
    }

    @Test
    fun appendedDataIsRejected() {
        val bytes = backup()
        assertRejected<BackupException.Damaged>(bytes + byteArrayOf(0))
        assertRejected<BackupException.Damaged>(bytes + ByteArray(5_000))
        assertRejected<BackupException.Damaged>(bytes + chunks(bytes).last())
    }

    @Test
    fun otherFilesAreNotBackups() {
        assertRejected<BackupException.NotABackup>("PK\u0003\u0004 a zip file, not a backup".toByteArray())
    }

    // MARK: - Versions

    @Test
    fun newerMajorFormatInHeaderIsRefused() {
        val bytes = backup()
        val newer = bytes.copyOf().also { it[8] = 0; it[9] = 2 }
        assertRejected<BackupException.NewerVersion>(newer)
        assertEquals("This backup was made by a newer version of Keepr. Update the app, then try again.", BackupException.NewerVersion().message)
    }

    @Test
    fun newerMajorFormatInManifestIsRefused() {
        val bytes = backup(tamper = BackupWriter.Tamper(manifest = { it.replace("\"formatVersion\":1", "\"formatVersion\":2") }))
        assertRejected<BackupException.NewerVersion>(bytes)
    }

    @Test
    fun newerMinorVersionAndUnknownFieldsAreTolerated() {
        val bytes = backup(
            tamper = BackupWriter.Tamper(
                manifest = {
                    it.replace("\"formatMinorVersion\":0", "\"formatMinorVersion\":7,\"futureFlag\":{\"nested\":[1,2.5,null]}")
                        .replace("\"counts\":{", "\"counts\":{\"futureRecords\":3,")
                },
                records = {
                    it.replaceFirst("{\"people\":[{", "{\"futureTable\":[{\"x\":1}],\"people\":[{\"futureColumn\":\"hi\",")
                        .replace("\"settings\":{", "\"settings\":{\"keepr.future\":{\"type\":\"color\",\"value\":\"#fff\"},")
                },
            ),
        )
        val staged = stage(bytes)
        assertEquals(records(), staged.records)
        assertEquals(7, staged.manifest.formatMinorVersion)
    }

    // MARK: - Manifest checks

    @Test
    fun manifestHashMismatchIsRejected() {
        val bytes = backup(
            tamper = BackupWriter.Tamper(
                manifestValue = { manifest ->
                    manifest.copy(blobs = manifest.blobs.map { if (it.name.contains("file-data")) it.copy(sha256 = sha256Hex(byteArrayOf(1))) else it })
                },
            ),
        )
        assertRejected<BackupException.Damaged>(bytes)
    }

    @Test
    fun manifestRecordsHashMismatchIsRejected() {
        val bytes = backup(tamper = BackupWriter.Tamper(manifestValue = { it.copy(records = it.records.copy(sha256 = sha256Hex(byteArrayOf(2)))) }))
        assertRejected<BackupException.Damaged>(bytes)
    }

    @Test
    fun manifestCountMismatchIsRejected() {
        val bytes = backup(tamper = BackupWriter.Tamper(manifestValue = { it.copy(counts = it.counts + ("people" to 5)) }))
        assertRejected<BackupException.Damaged>(bytes)
    }

    @Test
    fun blobMissingFromTheFileIsRejected() {
        val bytes = backup(
            tamper = BackupWriter.Tamper(
                manifestValue = { it.copy(blobs = it.blobs + BackupBlobInfo("blobs/file-data/extra", 3, sha256Hex(byteArrayOf(1, 2, 3)))) },
            ),
        )
        assertRejected<BackupException.Damaged>(bytes)
    }

    @Test
    fun recordsThatAreNotValidJsonAreRejected() {
        // The hash is computed after tampering, so this reaches the parser.
        val bytes = backup(tamper = BackupWriter.Tamper(records = { it.dropLast(1) }))
        assertRejected<BackupException.Invalid>(bytes)
    }

    // MARK: - Records

    @Test
    fun danglingReferencesAreRefused() {
        val base = records()
        val bad = base.copy(memories = base.memories.map { it.copy(personId = "missing") })
        assertFailsWith<BackupException.Invalid> { bad.validate() }
        assertFailsWith<BackupException.Invalid> { base.copy(people = base.people + base.people[0]).validate() }
        assertFailsWith<BackupException.Invalid> { base.copy(groupMembers = listOf(GroupMemberRecord(base.people[0].id, "nope", 0))).validate() }
        // The writer refuses to produce such a file in the first place.
        assertFailsWith<BackupException.Invalid> { backup(bad) }
    }

    @Test
    fun writerRequiresBytesForEveryReferencedBlob() {
        val out = ByteArrayOutputStream()
        assertFailsWith<IllegalArgumentException> {
            BackupWriter.write(out, password, records(), blobs().drop(1), app, created, iterations, chunk)
        }
    }

    @Test
    fun blobChangingBetweenPassesFailsTheBackup() {
        var opened = 0
        val sources = blobs().map { source ->
            if (!source.name.contains("file-data")) source
            else BackupBlobSource(source.name) { ByteArrayInputStream(if (opened++ == 0) fileBytes else fileBytes.reversedArray()) }
        }
        assertFailsWith<java.io.IOException> {
            BackupWriter.write(ByteArrayOutputStream(), password, records(), sources, app, created, iterations, chunk)
        }
    }

    // MARK: - Settings

    @Test
    fun settingsCaptureOnlyTheWhitelist() {
        val prefs = InMemoryPreferences().apply {
            setString(PreferenceKey.CONTEXT_MODE, "personal")
            setString(PreferenceKey.GROUP_LABEL, "Place")
            setString(PreferenceKey.REMINDERS_ENABLED, "false")
            setBool(PreferenceKey.HAS_ONBOARDED, true)
            setString(PreferenceKey.PEOPLE_SORT, "recent")
            setStringList(KeeprStore.SEEDED_KEYS_DEFAULT, listOf("Client"))
            setString(BackupSettings.LAST_CADENCE_BACKLOG_NUDGE, "2026-11-01T09:00:00Z")
            setStringList("keepr.knownContactIdentifiers", listOf("a", "b"))
            setBool("keepr.hasContactBaseline", true)
            setBool(PreferenceKey.HAS_ASKED_FOR_NOTIFICATIONS, true)
        }
        val captured = BackupSettings.capture(prefs)
        assertEquals(SettingValue.Text("personal"), captured[PreferenceKey.CONTEXT_MODE])
        assertEquals(SettingValue.Text("false"), captured[PreferenceKey.REMINDERS_ENABLED])
        assertEquals(SettingValue.TextList(listOf("Client")), captured[KeeprStore.SEEDED_KEYS_DEFAULT])
        assertEquals(SettingValue.Text("2026-11-01T09:00:00Z"), captured[BackupSettings.LAST_CADENCE_BACKLOG_NUDGE])
        assertFalse("keepr.knownContactIdentifiers" in captured)
        assertFalse("keepr.hasContactBaseline" in captured)
        assertFalse(PreferenceKey.HAS_ASKED_FOR_NOTIFICATIONS in captured)
    }

    @Test
    fun applyingSettingsMatchesTheBackupAndTouchesNothingElse() {
        val target = InMemoryPreferences().apply {
            setString(PreferenceKey.GROUP_LABEL, "Gym")
            setString(PreferenceKey.PEOPLE_SORT, "recent")
            setStringList("keepr.knownContactIdentifiers", listOf("x"))
        }
        BackupSettings.apply(
            mapOf(
                PreferenceKey.CONTEXT_MODE to SettingValue.Text("personal"),
                PreferenceKey.REMINDERS_ENABLED to SettingValue.Text("false"),
                // Not on the list: ignored.
                "keepr.knownContactIdentifiers" to SettingValue.TextList(listOf("evil")),
                // Wrong type for the key: treated as absent.
                PreferenceKey.PEOPLE_SORT to SettingValue.Switch(true),
            ),
            target,
        )
        assertEquals("personal", target.string(PreferenceKey.CONTEXT_MODE))
        assertEquals("false", target.string(PreferenceKey.REMINDERS_ENABLED))
        assertEquals(null, target.string(PreferenceKey.GROUP_LABEL))
        assertEquals(null, target.string(PreferenceKey.PEOPLE_SORT))
        assertEquals(listOf("x"), target.stringList("keepr.knownContactIdentifiers"))
        assertTrue(target.bool(PreferenceKey.HAS_ONBOARDED))
    }

    // MARK: - Small pieces

    @Test
    fun jsonRoundTripsTrickyText() {
        val value = linkedMapOf(
            "text" to "quote \" backslash \\ newline \n tab \t nul \u0000 emoji 😀 line sep",
            "numbers" to listOf(0L, -1L, Long.MAX_VALUE, 2.5),
            "flags" to listOf(true, false, null),
            "nested" to mapOf("empty" to emptyMap<String, Any>(), "list" to emptyList<Any>()),
        )
        assertEquals(value, Json.parse(Json.write(value)))
        assertEquals(mapOf("a" to "é"), Json.parse("""{ "a" : "é" }"""))
        assertFailsWith<JsonException> { Json.parse("{\"a\":1,}") }
        assertFailsWith<JsonException> { Json.parse("[1] 2") }
    }

    @Test
    fun passwordStrengthHint() {
        assertEquals(PasswordStrength.TOO_SHORT, PasswordStrength.of("abc"))
        assertEquals(PasswordStrength.WEAK, PasswordStrength.of("password123"))
        assertEquals(PasswordStrength.WEAK, PasswordStrength.of("aaaaaaaaaa"))
        assertEquals(PasswordStrength.WEAK, PasswordStrength.of("tulipsun"))
        assertEquals(PasswordStrength.FAIR, PasswordStrength.of("Tulipsun"))
        assertEquals(PasswordStrength.STRONG, PasswordStrength.of("Tulip-sun-42"))
        assertEquals(PasswordStrength.VERY_STRONG, PasswordStrength.of("Correct-Horse-Battery-9"))
    }

    @Test
    fun summaryAndConfirmationWording() {
        val summary = BackupSummary.of(stage(backup()).manifest)
        assertEquals(BackupSummary(people = 2, interactions = 1, memories = 1, followUps = 1, notes = 1, files = 2, groups = 1, links = 1), summary)
        assertFalse(summary.isEmpty)
        assertTrue(BackupSummary(0, 0, 0, 0, 0, 0, 0, 0).isEmpty)
        assertEquals("Replace 1 person with 2 people from the backup?", BackupSummary.replaceQuestion(summary.copy(people = 1), summary))
        assertEquals("Places", summary.rows("Places")[6].first)
    }

    @Test
    fun removingEveryRecordDetachesThem() {
        val context = com.chrisallenclark.keepr.core.model.ModelContext()
        val person = com.chrisallenclark.keepr.core.model.Person("Ada", "Lovelace")
        context.insert(person)
        context.insert(com.chrisallenclark.keepr.core.model.Memory("Likes maths", person = person))
        assertEquals(1, BackupSummary.of(context).memories)
        context.removeAll()
        assertTrue(context.all().isEmpty())
        assertTrue(person.isDeleted)
        assertEquals(null, person.modelContext)
        assertTrue(BackupSummary.of(context).isEmpty)
    }

    @Test
    fun defaultFileName() {
        assertEquals("Keepr Backup 2026-11-10.keeprbackup", BackupFormat.defaultFileName(LocalDate.of(2026, 11, 10)))
    }
}
