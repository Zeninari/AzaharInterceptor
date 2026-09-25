package com.zeninari.azaharinterceptor

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import android.view.KeyEvent
import android.os.Bundle
import android.widget.Toast
import android.widget.Button
import android.widget.Spinner
import android.widget.EditText
import android.widget.TextView
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.app.ActivityManager
import android.content.Intent
import android.content.ComponentName
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomnavigation.BottomNavigationView

private const val SysNandID = "00000000000000000000000000000000"
private const val VERSION_NAME = "Version 1.0"
private val debugInfo = mutableMapOf<String, String>()
private fun resetDebugInfo() {
    debugInfo.clear()

    listOf(
        "file", "extension", "type", "title id", "title type", "title identifier",
        "directory", "title directory", "content directory", "tmd", "boot content",
        "app", "selected", "package", "command", "route type", "result"
    )
        .forEach { key -> debugInfo[key] = "n/a"
    }
}

private fun debugValue(key: String): String = debugInfo[key] ?: "n/a"

class MainActivity : AppCompatActivity() {

    private val directoryPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("settings", MODE_PRIVATE).edit {
                    putString("azaharDirectory", it.toString())
                }

                recreate()
            }
        }

    private fun getSavedExtension(): String {
        val extension = getSharedPreferences("settings", MODE_PRIVATE).getString("extension", ".n3ds")
            ?: ".n3ds"

        val normalized = extension.trim().lowercase()

        return if (normalized.startsWith(".")) {
            normalized
        } else {
            ".$normalized"
        }
    }

    private fun saveExtension(extension: String) {
        getSharedPreferences("settings", MODE_PRIVATE).edit {
            putString("extension", extension)
        }
    }

    // Patched to accept ROM's as passthrough
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleLaunchIntent()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleLaunchIntent()
    }

    private fun removeStaleInterceptorTasks() {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val currentTaskId = taskId

        activityManager.appTasks.forEach { appTask ->
            val info = appTask.taskInfo
                ?: return@forEach

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (info.taskId != currentTaskId && info.numActivities == 0) {
                    appTask.finishAndRemoveTask()
                }
            }
        }
    }

    // Dropdown logic
    private fun setupEmulatorSpinner(spinner: Spinner) {
        val installedEmulators = getInstalledEmulators()

        val names = installedEmulators.map {
            it.name
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val preferences = getSharedPreferences("settings", MODE_PRIVATE)
        val savedId = preferences.getString("emulator", "AZAHAR")
            ?: "AZAHAR"

        val savedIndex = installedEmulators.indexOfFirst {
            it.id == savedId
        }

        if (savedIndex >= 0) {
            spinner.setSelection(savedIndex)
        }
    }

    private fun getInstalledEmulators(): List<EmulatorDefinition> {
        return EmulatorDefinitions.all.filter { emulator ->
            emulator.packages.any { packageName ->
                try {
                    packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    false
                }
            }
        }
    }

    // Is It A Shortcut or a ROM?
    private fun handleLaunchIntent() {
        removeStaleInterceptorTasks()
        resetDebugInfo()

        val incomingUri = intent?.data

        if (incomingUri == null) {
            debugInfo["result"] = "No ROM URI received. Please open with iiSU or another front end"
            showInterface()
            return
        }

        val fileName = DocumentFile.fromSingleUri(this, incomingUri)
            ?.name

        debugInfo["file"] = fileName
            ?: "unknown"

        val dotIndex = fileName?.lastIndexOf('.')

        val incomingExtension = if (fileName != null && dotIndex != null && dotIndex >= 0 && dotIndex < fileName.lastIndex) {
            fileName.substring(dotIndex).lowercase()
        } else {
            null
        }

        val configuredExtension = getSavedExtension()
        debugInfo["extension"] = incomingExtension
            ?: "none"

        // Normal ROM: launch using the selected emulator.
        if (incomingExtension != configuredExtension) {
            debugInfo["type"] = "normal rom"
            launchSelectedEmulator(incomingUri)
            finish()
            return
        }

        // Shortcut file: read the Title ID.
        val contents = readIncomingUri()
        val titleId = contents.trim().lowercase()
        debugInfo["type"] = "shortcut"
        debugInfo["title id"] = titleId

        val savedRoot = getSavedAzaharDirectory()

        if (savedRoot != null) {
            val result = processTitle(savedRoot, titleId)
            debugInfo["directory"] = savedRoot

            if (result.launched) {
                return
            }

            showInterface()
            return
        }

        debugInfo["result"] = "no SAF directory selected"
        showInterface()
    }

    //Read URI; Return Contents
    private fun readIncomingUri(): String {
        val romUri = intent?.data ?: return "ERROR: No ROM URI received."

        return try {
            contentResolver.openInputStream(romUri)?.use { input ->
                val buffer = ByteArray(16)
                val bytesRead = input.read(buffer)

                if (bytesRead != 16) {
                    return "ERROR: Invalid Title ID length: $bytesRead bytes"
                }

                String(buffer, Charsets.UTF_8)
            }
                ?: "Could not open ROM URI."
        }
        catch (e: Exception) {
            "Error reading incoming URI:\n${e.message}"
        }
    }

    // SAF Directory Permission
    private fun getSavedAzaharDirectory(): String? {
        return getSharedPreferences("settings", MODE_PRIVATE).getString("azaharDirectory", null)
    }

    // Process Incoming File
    private fun processTitle(savedRoot: String, titleId: String): TitleResult {
        val root = DocumentFile.fromTreeUri(this, savedRoot.toUri())
            ?: run {
                debugInfo["result"] = "SAF error: Invalid or inaccessible directory."
                return TitleResult(safReady = false, launched = false)
            }

        if (!root.isDirectory || !root.canRead()) {
            debugInfo["result"] = "SAF error: Invalid or inaccessible directory."
            return TitleResult(safReady = false, launched = false)
        }

        if (titleId.isEmpty()) {
            debugInfo["result"] = "ERROR: Empty Title ID."
            return TitleResult(safReady = true, launched = false)
        }

        if (titleId.length != 16) {
            debugInfo["result"] = "ERROR: Invalid Title ID: $titleId"
            return TitleResult(safReady = true, launched = false)
        }

        val titleType = titleId.take(8)
        val titleIdentifier = titleId.drop(8)
        debugInfo["title type"] = titleType
        debugInfo["title identifier"] = titleIdentifier

        val titleDirectory = findTitleDirectory(root, titleType, titleIdentifier)
        debugInfo["title directory"] = titleDirectory?.uri?.toString()
            ?: "not found"

        titleDirectory
            ?: run {
                debugInfo["result"] = when (titleType) {
                    "00040000" -> "ERROR: SDMC Title Not Found: $titleIdentifier"
                    "00040010" -> "ERROR: NAND Title Not Found: $titleIdentifier"
                    "00040030" -> "ERROR: SYSTEM Title Not Found: $titleIdentifier"
                    else -> "ERROR: Unsupported Title ID: $titleId"
                }

                return TitleResult(safReady = true, launched = false)
            }

        val contentDirectory = titleDirectory.findFile("content")
            ?.takeIf { it.isDirectory }

        debugInfo["content directory"] = contentDirectory?.uri?.toString()
            ?: "not found"

        contentDirectory
            ?: run {
                debugInfo["result"] = "ERROR: Content Not Found"
                return TitleResult(safReady = true, launched = false)
            }

        val tmdFile = findTmdFile(contentDirectory)
        debugInfo["tmd"] = tmdFile?.uri?.toString()
            ?: "not found"

        tmdFile
            ?: run {
                debugInfo["result"] = "ERROR: TMD Not Found"
                return TitleResult(safReady = true, launched = false)
            }

        val bootContentId = readBootContentId(tmdFile)
        debugInfo["boot content"] = bootContentId?.let {
            "%08x".format(it)
        } ?: "not found"

        bootContentId
            ?: run {
                debugInfo["result"] = "ERROR: Could Not Determine Boot Content"
                return TitleResult(safReady = true, launched = false)
            }

        val appName = "%08x.app".format(bootContentId)
        val appFile = contentDirectory.findFile(appName)
            ?.takeIf { it.isFile }

        debugInfo["app"] = appFile?.uri?.toString()
            ?: "not found"

        appFile
            ?: run {
                debugInfo["result"] = "ERROR: Boot Content Not Found: $appName"
                return TitleResult(safReady = true, launched = false)
            }

        val appUri = appFile.uri
        launchSelectedEmulator(appUri)
        finish()

        return TitleResult(safReady = true, launched = true)
    }

    private fun findTitleDirectory(root: DocumentFile, titleType: String, titleIdentifier: String): DocumentFile? {

        val path = when (titleType) {
            "00040000" -> arrayOf("sdmc", "Nintendo 3DS", SysNandID, SysNandID, "title", titleType, titleIdentifier)
            "00040010", "00040030" -> arrayOf("nand", SysNandID, "title", titleType, titleIdentifier)

            else -> return null
        }
        return path.fold(root as DocumentFile?) { current, folder ->
            current?.findFile(folder)
        }

    }

    // Find TMD
    private fun findTmdFile(contentDirectory: DocumentFile): DocumentFile? {
        return contentDirectory.listFiles().firstOrNull {
            it.isFile && it.name?.endsWith(".tmd", ignoreCase = true) == true
        }
    }

    // Read TMD; Returns Boot Content
    private fun readBootContentId(tmdFile: DocumentFile): Long? {

        val data = contentResolver.openInputStream(tmdFile.uri)?.use {
            it.readBytes()
        }
            ?: return null

        if (data.size < 4) {
            return null
        }

        fun readU16(offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 8) or
                    (data[offset + 1].toInt() and 0xFF)
        }

        fun readU32(offset: Int): Long {
            return ((data[offset].toLong() and 0xFF) shl 24) or
                    ((data[offset + 1].toLong() and 0xFF) shl 16) or
                    ((data[offset + 2].toLong() and 0xFF) shl 8) or
                    (data[offset + 3].toLong() and 0xFF)
        }

        val signatureType = readU32(0)

        val headerOffset = when (signatureType) {
            0x00010000L,
            0x00010003L -> 0x240

            0x00010001L,
            0x00010004L -> 0x140

            0x00010002L,
            0x00010005L -> 0x080

            else -> return null
        }

        val bootContentIndex = readU16(headerOffset + 0xA0)
        val contentCount = readU16(headerOffset + 0x9E)
        val contentTable = headerOffset + 0xC4 + (64 * 0x24)
        val contentRecordSize = 0x30

        for (i in 0 until contentCount) {

            val offset = contentTable + (i * contentRecordSize)
            if (offset + contentRecordSize > data.size) {
                return null
            }

            val contentId = readU32(offset)
            val contentIndex = readU16(offset + 0x04)

            if (contentIndex == bootContentIndex) {
                return contentId
            }
        }

        return null
    }

     // Launcher
     private fun launchSelectedEmulator(romUri: Uri) {

         val preferences = getSharedPreferences("settings", MODE_PRIVATE)
         val savedId = preferences.getString("emulator", "AZAHAR")
             ?: "AZAHAR"

         val emulator = EmulatorDefinitions.all.firstOrNull {
             it.id == savedId
         }

         debugInfo["selected"] = emulator?.name
             ?: "not found"
         debugInfo["route type"] = emulator?.routeType?.name?.lowercase()
             ?: "n/a"

         if (emulator == null) {
             debugInfo["result"] = "ERROR: Selected emulator definition not found: $savedId"
             return
         }

         val installedPackage = emulator.packages.firstOrNull { packageName ->
             try {
                 packageManager.getPackageInfo(packageName, 0)
                 true
             } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                 false
             }
         }

         debugInfo["package"] = installedPackage
             ?: "not found"

         if (installedPackage == null) {
             debugInfo["result"] = "ERROR: Selected emulator is not installed: ${emulator.name}"
             return
         }

         val command = emulator.commands.firstOrNull {
             definition -> definition.command.startsWith(installedPackage)
         }

         debugInfo["command"] = command?.command
             ?: "not found"

         if (command == null) {
             debugInfo["result"] = "ERROR: No launch command found for installed package: $installedPackage"
             return
         }

         val resolvedCommand = command.command.replace("%PACKAGE%", installedPackage).replace("%ROM_URI%", romUri.toString())
         debugInfo["command"] = resolvedCommand

         val launchIntent = parseEmulatorCommand(resolvedCommand)
             ?: return

         launchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
         debugInfo["result"] = "emulator launched"
         startActivity(launchIntent)
     }

private fun parseEmulatorCommand(command: String): Intent? {

    val parts = command.trim().split(Regex("\\s+"))

    if (parts.isEmpty()) {
        debugInfo["result"] = "ERROR: Empty emulator command."
        return null
    }

    val component = parts[0]
    val componentParts = component.split("/", limit = 2)

    if (componentParts.size != 2) {
        debugInfo["result"] = "ERROR: Invalid emulator component: $component"
        return null
    }

    val commandPackage = componentParts[0]
    val activityName = componentParts[1]

    val fullActivityName = if (activityName.startsWith(".")) {
        "$commandPackage$activityName"
    } else {
        activityName
    }

    val launchIntent = Intent()

    launchIntent.component = ComponentName(commandPackage, fullActivityName)

    var index = 1

    while (index < parts.size) {

        when (parts[index]) {

            "--activity-clear-task" -> {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            "--activity-clear-top" -> {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            "-a" -> {
                if (index + 1 >= parts.size) {
                    debugInfo["result"] = "ERROR: Missing value for -a."
                    return null
                }

                launchIntent.action = parts[index + 1]
                index++
            }

            "-d" -> {
                if (index + 1 >= parts.size) {
                    debugInfo["result"] = "ERROR: Missing value for -d."
                    return null
                }

                launchIntent.data = parts[index + 1].toUri()
                index++
            }

            "-e" -> {
                if (index + 2 >= parts.size) {
                    debugInfo["result"] = "ERROR: Invalid -e argument."
                    return null
                }

                val key = parts[index + 1]
                val value = parts[index + 2]

                launchIntent.putExtra(key, value)

                index += 2
            }
        }

        index++
    }

    return launchIntent
}

    // Interface Logic
    @SuppressLint("SetTextI18n")
    private fun showInterface() {
        setContentView(R.layout.activity_main)

        val extensionInput = findViewById<EditText>(R.id.extensionInput)
        val directoryValue = findViewById<TextView>(R.id.directoryValue)
        val selectDirectoryButton = findViewById<Button>(R.id.selectDirectoryButton)
        val saveSettingsButton = findViewById<Button>(R.id.saveSettingsButton)
        val debugText = findViewById<TextView>(R.id.debugText)

        val emulatorSpinner = findViewById<Spinner>(R.id.emulatorSpinner)
        val emulatorWarning = findViewById<TextView>(R.id.emulatorWarning)
        setupEmulatorSpinner(emulatorSpinner)

        val versionText = findViewById<TextView>(R.id.versionText)
        versionText.text = "Version $VERSION_NAME"

        val debugPage = findViewById<LinearLayout>(R.id.debugPage)
        val createPage = findViewById<LinearLayout>(R.id.createPage)
        val settingsPage = findViewById<ScrollView>(R.id.settingsPage)
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        extensionInput.setText(getSavedExtension())

        val savedRoot = getSavedAzaharDirectory()
        directoryValue.text = savedRoot
            ?: "No directory selected"

        selectDirectoryButton.setOnClickListener {
            directoryPicker.launch(null)
        }

        saveSettingsButton.setOnClickListener {
            val extension = extensionInput.text.toString().trim()

            if (extension.isNotEmpty()) {
                saveExtension(extension)

                val installedEmulators = getInstalledEmulators()
                val selectedIndex = emulatorSpinner.selectedItemPosition

                if (selectedIndex in installedEmulators.indices) {
                    val selectedEmulator = installedEmulators[selectedIndex]
                    getSharedPreferences("settings", MODE_PRIVATE).edit {
                        putString("emulator", selectedEmulator.id)
                    }
                }

                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }

        emulatorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val installedEmulators = getInstalledEmulators()

                if (position !in installedEmulators.indices) {
                    emulatorWarning.visibility = View.GONE
                    return
                }

                val selectedEmulator = installedEmulators[position]

                emulatorWarning.visibility =
                    if (selectedEmulator.id == "AZAHAR" || selectedEmulator.id == "AZAHARPLUS" || selectedEmulator.id == "AZAHARDEBUG") {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                emulatorWarning.visibility = View.GONE
            }
        }

        // Yes I know it's ugly, but it's easier to display plain text like this
        debugText.text = buildString {
            appendLine("⊰ Interceptor ⊱")
            appendLine()
            appendLine("file: ${debugValue("file")}")
            appendLine("extension: ${debugValue("extension")}")
            appendLine("type: ${debugValue("type")}")
            appendLine()

            appendLine("⊰ Shortcut ⊱")
            appendLine()
            appendLine("title id: ${debugValue("title id")}")
            appendLine("title type: ${debugValue("title type")}")
            appendLine("title identifier: ${debugValue("title identifier")}")
            appendLine()

            appendLine("⊰ SAF ⊱")
            appendLine()
            appendLine("directory: ${debugValue("directory")}")
            appendLine("title directory: ${debugValue("title directory")}")
            appendLine("content directory: ${debugValue("content directory")}")
            appendLine("tmd: ${debugValue("tmd")}")
            appendLine("boot content: ${debugValue("boot content")}")
            appendLine("app: ${debugValue("app")}")
            appendLine()

            appendLine("⊰ Emulator ⊱")
            appendLine()
            appendLine("selected: ${debugValue("selected")}")
            appendLine("package: ${debugValue("package")}")
            appendLine("command: ${debugValue("command")}")
            appendLine("route type: ${debugValue("route type")}")
            appendLine()

            appendLine("⊰ Result ⊱")
            appendLine()
            appendLine(debugValue("result"))
        }

        // Navigation
        bottomNavigation.selectedItemId = R.id.nav_debug
        bottomNavigation.setOnItemSelectedListener { item ->

            debugPage.visibility = View.GONE
            createPage.visibility = View.GONE
            settingsPage.visibility = View.GONE

            when (item.itemId) {

                R.id.nav_debug -> {
                    debugPage.visibility = View.VISIBLE
                    true
                }

                /*
                R.id.nav_create -> {
                    createPage.visibility = View.VISIBLE
                    true
                }
                 */

                R.id.nav_settings -> {
                    settingsPage.visibility = View.VISIBLE
                    true
                }

                else -> false
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    navigateMajorPage(-1)
                    return true
                }

                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    navigateMajorPage(1)
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun navigateMajorPage(direction: Int) {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        val pages = listOf(R.id.nav_debug, /*R.id.nav_create,*/ R.id.nav_settings)
        val currentIndex = pages.indexOf(bottomNavigation.selectedItemId)

        if (currentIndex == -1) {
            bottomNavigation.selectedItemId = R.id.nav_debug
            return
        }

        val nextIndex = (currentIndex + direction + pages.size) % pages.size
        bottomNavigation.selectedItemId = pages[nextIndex]
    }

    private data class TitleResult(val safReady: Boolean, val launched: Boolean)
}