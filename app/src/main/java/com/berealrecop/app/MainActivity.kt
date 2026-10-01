package com.berealrecop.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.berealrecop.app.data.BeRealItem
import com.berealrecop.app.data.BeRealParser
import com.berealrecop.app.ui.HomeScreen
import com.berealrecop.app.ui.theme.BeRealRecopTheme
import com.berealrecop.app.video.VideoEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private val itemsState = mutableStateListOf<BeRealItem>()
    private var durationState by mutableFloatStateOf(0.8f)
    private var isExportingState by mutableStateOf(false)
    private var exportProgressState by mutableFloatStateOf(0f)
    private var exportProgressTextState by mutableStateOf("")
    private var exportedVideoUriState by mutableStateOf<Uri?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            scanBeRealPhotos()
        } else {
            Toast.makeText(this, getString(R.string.toast_permission_needed), Toast.LENGTH_SHORT).show()
        }
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val parsedItems = uris.map { uri ->
                    BeRealParser.parseItem(this@MainActivity, uri)
                }.sorted()

                withContext(Dispatchers.Main) {
                    itemsState.clear()
                    itemsState.addAll(parsedItems)
                    Toast.makeText(this@MainActivity, getString(R.string.toast_photos_selected, parsedItems.size), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BeRealRecopTheme {
                HomeScreen(
                    items = itemsState,
                    durationPerImage = durationState,
                    isExporting = isExportingState,
                    exportProgress = exportProgressState,
                    exportProgressText = exportProgressTextState,
                    exportedVideoUri = exportedVideoUriState,
                    onDurationChange = { durationState = it },
                    onToggleItem = { item ->
                        val index = itemsState.indexOfFirst { it.id == item.id }
                        if (index != -1) {
                            itemsState[index] = item.copy(isIncluded = !item.isIncluded)
                        }
                    },
                    onSelectAllFiltered = { filteredList, selectAll ->
                        val idsToUpdate = filteredList.map { it.id }.toSet()
                        for (i in itemsState.indices) {
                            if (itemsState[i].id in idsToUpdate) {
                                itemsState[i] = itemsState[i].copy(isIncluded = selectAll)
                            }
                        }
                    },
                    onClearItems = {
                        itemsState.clear()
                        Toast.makeText(this@MainActivity, getString(R.string.toast_list_cleared), Toast.LENGTH_SHORT).show()
                    },
                    onScanFolder = { checkPermissionsAndScan() },
                    onPickPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onExportVideo = { filteredList -> startVideoExport(filteredList) },
                    onOpenExportedVideo = { uri -> openVideo(uri) },
                    onDismissExportDialog = { exportedVideoUriState = null }
                )
            }
        }

        checkPermissionsAndScan()
    }

    private fun checkPermissionsAndScan() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            scanBeRealPhotos()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    fun scanBeRealPhotos() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = mutableListOf<BeRealItem>()

            // 1. Direct scan of /sdcard/DCIM/BeReal
            val dcimBeRealDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "BeReal")
            val altBeRealDir = File("/sdcard/DCIM/BeReal")

            val targetDir = when {
                dcimBeRealDir.exists() && dcimBeRealDir.isDirectory -> dcimBeRealDir
                altBeRealDir.exists() && altBeRealDir.isDirectory -> altBeRealDir
                else -> null
            }

            if (targetDir != null) {
                targetDir.listFiles { file ->
                    val name = file.name.lowercase()
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                }?.forEach { file ->
                    val uri = Uri.fromFile(file)
                    val item = BeRealParser.parseItem(
                        context = this@MainActivity,
                        uri = uri,
                        filePath = file.absolutePath,
                        givenFileName = file.name
                    )
                    list.add(item)
                }
            }

            // 2. Query MediaStore if direct directory didn't yield files or to complement
            if (list.isEmpty()) {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                )
                val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
                val selectionArgs = arrayOf("%/BeReal/%")

                try {
                    contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${MediaStore.Images.Media.DATE_ADDED} ASC"
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val name = cursor.getString(nameCol)
                            val data = cursor.getString(dataCol)
                            val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                            val item = BeRealParser.parseItem(
                                context = this@MainActivity,
                                uri = contentUri,
                                filePath = data,
                                givenFileName = name
                            )
                            list.add(item)
                        }
                    }
                } catch (_: Exception) {}
            }

            // Sort chronologically
            val sortedList = list.sorted()

            withContext(Dispatchers.Main) {
                itemsState.clear()
                itemsState.addAll(sortedList)
                if (sortedList.isNotEmpty()) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_bereals_found, sortedList.size), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startVideoExport(itemsToExport: List<BeRealItem> = itemsState) {
        if (isExportingState) return
        isExportingState = true
        exportProgressState = 0f
        exportProgressTextState = getString(R.string.exporting_preparing)

        lifecycleScope.launch {
            try {
                val encoder = VideoEncoder(this@MainActivity)
                val resultUri = encoder.encodeVideo(
                    items = itemsToExport,
                    durationPerImageSec = durationState
                ) { progress, current, total ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        exportProgressState = progress
                        exportProgressTextState = getString(R.string.exporting_progress, current, total, (progress * 100).toInt())
                    }
                }

                withContext(Dispatchers.Main) {
                    isExportingState = false
                    exportedVideoUriState = resultUri
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isExportingState = false
                    Toast.makeText(this@MainActivity, getString(R.string.toast_export_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openVideo(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir en Instagram / Galería"))
        }
    }
}
