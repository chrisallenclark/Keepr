package com.aulorvy.keepr.ui.files

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aulorvy.keepr.core.domain.PersonFileLibrary
import com.aulorvy.keepr.core.domain.PersonFilePayload
import com.aulorvy.keepr.core.model.Person
import com.aulorvy.keepr.core.model.PersonFile
import com.aulorvy.keepr.ui.components.EmptyState
import com.aulorvy.keepr.ui.components.EmptyStateArea
import com.aulorvy.keepr.ui.components.FormDateRow
import com.aulorvy.keepr.ui.components.FormTextField
import com.aulorvy.keepr.ui.components.GroupedSection
import com.aulorvy.keepr.ui.components.KText
import com.aulorvy.keepr.ui.components.KeeprMenu
import com.aulorvy.keepr.ui.components.KeeprSheet
import com.aulorvy.keepr.ui.components.KeeprTopBar
import com.aulorvy.keepr.ui.components.ListRow
import com.aulorvy.keepr.ui.components.MenuEntry
import com.aulorvy.keepr.ui.components.SectionSpacer
import com.aulorvy.keepr.ui.components.SheetBody
import com.aulorvy.keepr.ui.components.SheetHeader
import com.aulorvy.keepr.ui.components.SwipeAction
import com.aulorvy.keepr.ui.components.SwipeActionsRow
import com.aulorvy.keepr.ui.components.TabBarClearance
import com.aulorvy.keepr.ui.components.ToolbarButton
import com.aulorvy.keepr.ui.components.rememberHaptics
import com.aulorvy.keepr.ui.screens.AppState
import com.aulorvy.keepr.ui.screens.ConfirmDialog
import com.aulorvy.keepr.ui.screens.LocalAppState
import com.aulorvy.keepr.ui.screens.observeStore
import com.aulorvy.keepr.ui.theme.KeeprTheme
import com.aulorvy.keepr.ui.theme.Radius
import com.aulorvy.keepr.ui.theme.Spacing
import com.aulorvy.keepr.ui.theme.medium
import kotlinx.coroutines.launch

/**
 * Everything on file for one person. Translated from
 * Keepr/Features/Person/PersonFilesView.swift.
 *
 * Built around the consultation case: photograph their results, name it, and
 * have the stack read chronologically a year later. Newest first, because
 * the question is almost always "what did the last one say".
 *
 * Photos come from the system photo picker, the camera app or the document
 * picker; none needs a storage or camera permission. Everything stays in
 * Keepr's private storage.
 */
@Composable
fun PersonFilesScreen(person: Person, onBack: () -> Unit) {
    val state = LocalAppState.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    observeStore().value
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography

    var addMenuOpen by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<PersonFile?>(null) }
    var renaming by remember { mutableStateOf<PersonFile?>(null) }
    var deleting by remember { mutableStateOf<PersonFile?>(null) }
    var rowMenuFor by remember { mutableStateOf<java.util.UUID?>(null) }
    // The camera's target file, kept across the camera app taking over the screen.
    var cameraFileName by rememberSaveable { mutableStateOf<String?>(null) }

    if (person.isDeleted) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val hasCamera = remember {
        runCatching { Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null }.getOrDefault(false)
    }

    fun insert(payload: PersonFilePayload, titledAfter: String? = null) {
        PersonFileLibrary.insert(state.context, person, payload, titledAfter, state.now(), state.calendar)
        state.save()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isImporting = true
        scope.launch {
            var failures = 0
            for (uri in uris) {
                val payload = runCatching { PersonFileImport.photo(context, uri) }.getOrNull()
                if (payload == null) failures++ else insert(payload)
            }
            isImporting = false
            if (failures > 0) {
                importError = if (failures == 1) "One photo couldn't be read." else "$failures photos couldn't be read."
            } else {
                haptics.success()
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val name = cameraFileName ?: return@rememberLauncherForActivityResult
        cameraFileName = null
        val shot = PersonFileImport.cameraFile(context, name)
        if (!saved) {
            shot.delete()
            return@rememberLauncherForActivityResult
        }
        isImporting = true
        scope.launch {
            val payload = runCatching {
                PersonFileImport.photo(context, kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { shot.readBytes() })
            }.getOrNull()
            shot.delete()
            isImporting = false
            if (payload == null) {
                importError = "That photo couldn't be saved."
            } else {
                insert(payload)
                haptics.success()
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        isImporting = true
        scope.launch {
            var failures = 0
            var tooLarge = 0
            for (uri in uris) {
                val imported = try {
                    PersonFileImport.document(context, uri)
                } catch (error: PersonFileImport.TooLarge) {
                    tooLarge++
                    continue
                } catch (error: Exception) {
                    null
                }
                if (imported == null) failures++ else insert(imported.payload, imported.title)
            }
            isImporting = false
            importError = when {
                tooLarge > 0 -> "Files over ${PersonFileImport.MAX_BYTES / (1024 * 1024)} MB can't be added."
                failures > 0 -> "Some files couldn't be read."
                else -> null
            }
            if (importError == null) haptics.success()
        }
    }

    fun takePhoto() {
        val (file, uri) = runCatching { PersonFileImport.newCameraTarget(context) }.getOrNull() ?: run {
            importError = "The camera couldn't be opened."
            return
        }
        cameraFileName = file.name
        runCatching { camera.launch(uri) }.onFailure {
            cameraFileName = null
            file.delete()
            importError = "The camera couldn't be opened."
        }
    }

    fun share(file: PersonFile) {
        scope.launch {
            val uri: Uri? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PersonFileSharing.export(context, file) }
            if (uri == null) {
                openError = "That file is missing its contents."
            } else if (!PersonFileSharing.share(context, uri, file)) {
                openError = "Nothing on this phone can share that file."
            }
        }
    }

    fun open(file: PersonFile) {
        if (!file.hasData) {
            openError = "That file is missing its contents."
            return
        }
        viewing = file
    }

    val addEntries = buildList<MenuEntry> {
        if (hasCamera) add(MenuEntry.Action("Take Photo", "camera", onClick = ::takePhoto))
        add(MenuEntry.Action("Choose Photos", "photo.on.rectangle") {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        add(MenuEntry.Action("Choose File", "folder") { documentPicker.launch(arrayOf("*/*")) })
    }

    val files = person.fileList

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(colors.groupedBackground)) {
            KeeprTopBar(
                title = "Files",
                background = colors.groupedBackground,
                leading = { ToolbarButton("chevron.left", "Back", onBack) },
                trailing = {
                    Box {
                        ToolbarButton("plus", "Add", { addMenuOpen = true })
                        KeeprMenu(addMenuOpen, { addMenuOpen = false }, addEntries)
                    }
                },
            )

            if (files.isEmpty()) {
                EmptyStateArea(Modifier.weight(1f).fillMaxWidth()) {
                    EmptyState(
                        "No files yet",
                        "folder",
                        description = "Photograph a consultation sheet, keep a signed form, or save their plan. Stays on your device.",
                    )
                }
            } else {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(Spacing.small))
                    GroupedSection {
                        files.forEach { file ->
                            row(16.dp + 44.dp + Spacing.medium) {
                                key(file.id) {
                                    Box {
                                        SwipeActionsRow(
                                            trailing = listOf(
                                                SwipeAction("Delete", "trash", colors.red) { deleting = file },
                                                SwipeAction("Rename", "pencil", colors.accent) { renaming = file },
                                            ),
                                            allowsFullSwipe = false,
                                        ) {
                                            ListRow(onClick = { open(file) }, onLongClick = { rowMenuFor = file.id }) {
                                                PersonFileRow(file, state, Modifier.weight(1f))
                                            }
                                        }
                                        KeeprMenu(
                                            rowMenuFor == file.id,
                                            { rowMenuFor = null },
                                            listOf(
                                                MenuEntry.Action("Rename & Date", "pencil") { renaming = file },
                                                MenuEntry.Action("Share", "square.and.arrow.up") { share(file) },
                                                MenuEntry.Divider,
                                                MenuEntry.Action("Delete", "trash", isDestructive = true) { deleting = file },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SectionSpacer()
                    GroupedSection {
                        row {
                            ListRow {
                                KText(
                                    "Files are stored on this device only, inside Keepr. Nothing is uploaded.",
                                    style = typography.footnote,
                                    color = colors.secondaryLabel,
                                )
                            }
                        }
                    }
                    TabBarClearance()
                }
            }
        }

        if (isImporting) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(colors.glass, RoundedCornerShape(Radius.card))
                    .padding(Spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.small),
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = colors.secondaryLabel, strokeWidth = 2.5.dp)
                KText("Adding…", style = typography.subheadline, color = colors.secondaryLabel)
            }
        }
    }

    viewing?.let { file -> FileViewer(file, onDismiss = { viewing = null }) }
    renaming?.let { file -> PersonFileDetailsSheet(file, onDismiss = { renaming = null }) }
    deleting?.let { file ->
        ConfirmDialog(
            title = "Delete “${file.title}”?",
            message = "It's removed from Keepr on this device. Copies you've already shared elsewhere aren't affected.",
            confirmTitle = "Delete",
            onConfirm = {
                PersonFileLibrary.delete(state.context, file, state.now())
                state.save()
                haptics.light()
            },
            onDismiss = { deleting = null },
        )
    }
    importError?.let { message -> InfoAlert("Couldn't add that", message, onDismiss = { importError = null }) }
    openError?.let { message -> InfoAlert("Couldn't open that", message, onDismiss = { openError = null }) }
}

/** iOS `PersonFileRow`: its preview, what it's called, and when it's from. */
@Composable
fun PersonFileRow(file: PersonFile, state: AppState, modifier: Modifier = Modifier) {
    val colors = KeeprTheme.colors
    val typography = KeeprTheme.typography
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        PersonFileThumbnail(file, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            KText(file.title, style = typography.subheadline.medium, maxLines = 1)
            KText(state.calendar.abbreviatedDate(file.capturedAt), style = typography.caption, color = colors.secondaryLabel)
            file.note?.takeIf { it.isNotEmpty() }?.let {
                KText(it, style = typography.caption, color = colors.secondaryLabel, maxLines = 2)
            }
        }
        KText(file.sizeLabel, style = typography.caption2, color = colors.tertiaryLabel)
    }
}

/**
 * iOS `PersonFileDetailsEditor`. Naming and dating a file is the whole
 * difference between a folder of photos and a record you can read. The date
 * defaults to the day it was added rather than pretending to know when the
 * photo was taken. Edits apply when the sheet closes, however it closes — on
 * iOS they're bound live.
 */
@Composable
fun PersonFileDetailsSheet(file: PersonFile, onDismiss: () -> Unit) {
    val state = LocalAppState.current
    var title by rememberSaveable(file.id) { mutableStateOf(file.title) }
    var note by rememberSaveable(file.id) { mutableStateOf(file.note.orEmpty()) }
    var capturedAt by remember(file.id) { mutableStateOf(file.capturedAt) }

    fun done() {
        if (!file.isDeleted) {
            PersonFileLibrary.updateDetails(file, title, capturedAt, note)
            file.person?.touch(state.now())
            state.save()
        }
        onDismiss()
    }

    KeeprSheet(onDismiss = ::done) {
        SheetHeader("File Details", confirmTitle = "Done", onConfirm = ::done)
        SheetBody(Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            GroupedSection(header = "Name") {
                row { FormTextField(title, { title = it }, "What this is") }
            }
            SectionSpacer()
            GroupedSection(footer = "When this is from — the day of the consultation, not the day you added it.") {
                row { FormDateRow("Date", capturedAt, { capturedAt = it }, calendar = state.calendar) }
            }
            SectionSpacer()
            GroupedSection(header = "Note") {
                row { FormTextField(note, { note = it }, "Anything worth remembering about it", maxLines = 6) }
            }
        }
    }
}
