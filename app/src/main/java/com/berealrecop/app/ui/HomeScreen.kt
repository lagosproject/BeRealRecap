package com.berealrecop.app.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.berealrecop.app.R
import com.berealrecop.app.data.BeRealItem
import com.berealrecop.app.ui.theme.*
import kotlinx.coroutines.delay
import java.text.DateFormatSymbols
import java.util.Locale

data class MonthFilter(val year: Int, val monthIndex: Int, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    items: List<BeRealItem>,
    durationPerImage: Float,
    isExporting: Boolean,
    exportProgress: Float,
    exportProgressText: String,
    exportedVideoUri: Uri?,
    onDurationChange: (Float) -> Unit,
    onToggleItem: (BeRealItem) -> Unit,
    onSelectAllFiltered: (List<BeRealItem>, Boolean) -> Unit,
    onClearItems: () -> Unit,
    onScanFolder: () -> Unit,
    onPickPhotos: () -> Unit,
    onExportVideo: (List<BeRealItem>) -> Unit,
    onOpenExportedVideo: (Uri) -> Unit,
    onDismissExportDialog: () -> Unit
) {
    var showPreview by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val currentItemsList = items.toList()

    // Dynamically localized month names based on user's current locale (ES, EN, FR, etc.)
    val localeMonths = remember { DateFormatSymbols(Locale.getDefault()).months }

    // Group items into distinct months
    val availableMonths = remember(currentItemsList) {
        currentItemsList.map { item ->
            val monthName = if (item.monthIndex in 1..12 && item.monthIndex - 1 < localeMonths.size) {
                val raw = localeMonths[item.monthIndex - 1]
                raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } else {
                "Month ${item.monthIndex}"
            }
            MonthFilter(item.year, item.monthIndex, "$monthName ${item.year}")
        }.distinct().sortedWith(compareByDescending<MonthFilter> { it.year }.thenByDescending { it.monthIndex })
    }

    var selectedMonth by remember(availableMonths) {
        mutableStateOf(availableMonths.firstOrNull())
    }

    // Filter items according to the selected month (or all if none selected)
    val displayedItems = remember(currentItemsList, selectedMonth) {
        if (selectedMonth == null) {
            currentItemsList
        } else {
            currentItemsList.filter { it.year == selectedMonth?.year && it.monthIndex == selectedMonth?.monthIndex }
        }
    }

    val includedCount = displayedItems.count { it.isIncluded }
    val totalDurationSec = includedCount * durationPerImage
    val allIncluded = displayedItems.isNotEmpty() && includedCount == displayedItems.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = AccentWhite
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.action_clear),
                                tint = AccentWhite
                            )
                        }
                    }
                    IconButton(onClick = onScanFolder) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_scan),
                            tint = AccentWhite
                        )
                    }
                    IconButton(onClick = onPickPhotos) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(R.string.action_pick),
                            tint = AccentWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = DarkSurface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Duration Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.duration_per_photo),
                            color = AccentWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.duration_seconds_format, durationPerImage),
                            color = BeRealYellow,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = durationPerImage,
                        onValueChange = onDurationChange,
                        valueRange = 0.2f..2.5f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentWhite,
                            activeTrackColor = AccentWhite,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Action Buttons (Vista previa + Exportar)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPreview = true },
                            enabled = includedCount > 0,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentWhite
                            ),
                            border = BorderStroke(1.5.dp, AccentWhite.copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_preview),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = { onExportVideo(displayedItems) },
                            enabled = includedCount > 0 && !isExporting,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentWhite,
                                contentColor = DarkBackground,
                                disabledContainerColor = DarkSurfaceVariant,
                                disabledContentColor = TextSecondary
                            )
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_export, String.format(Locale.getDefault(), "%.1f", totalDurationSec)),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Month Selector Chips (if photos exist)
            if (availableMonths.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMonth == null,
                        onClick = { selectedMonth = null },
                        label = { Text(stringResource(R.string.filter_all, items.size), fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentWhite,
                            selectedLabelColor = DarkBackground,
                            containerColor = DarkSurface,
                            labelColor = AccentWhite
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = DarkSurfaceVariant,
                            selectedBorderColor = AccentWhite,
                            enabled = true,
                            selected = selectedMonth == null
                        )
                    )

                    availableMonths.forEach { month ->
                        val count = items.count { it.year == month.year && it.monthIndex == month.monthIndex }
                        val isSelected = selectedMonth == month
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMonth = month },
                            label = { Text("${month.label} ($count)", fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentWhite,
                                selectedLabelColor = DarkBackground,
                                containerColor = DarkSurface,
                                labelColor = AccentWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = DarkSurfaceVariant,
                                selectedBorderColor = AccentWhite,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }
            }

            // Stats / Selection Control Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.selection_summary, includedCount, displayedItems.size),
                            color = AccentWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = selectedMonth?.label ?: stringResource(R.string.all_months),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (displayedItems.isNotEmpty()) {
                        TextButton(
                            onClick = { onSelectAllFiltered(displayedItems, !allIncluded) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (allIncluded) stringResource(R.string.deselect_all) else stringResource(R.string.select_all),
                                fontSize = 12.sp,
                                color = BeRealYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (displayedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.empty_title),
                            color = AccentWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.empty_description),
                            color = TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onScanFolder,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentWhite, contentColor = DarkBackground)
                        ) {
                            Text(stringResource(R.string.btn_scan_folder), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        BeRealGridItem(
                            item = item,
                            onToggle = { onToggleItem(item) }
                        )
                    }
                }
            }
        }
    }

    // Live Video Preview Modal
    if (showPreview) {
        PreviewDialog(
            items = displayedItems,
            durationPerImage = durationPerImage,
            onDurationChange = onDurationChange,
            onDismiss = { showPreview = false },
            onExport = {
                showPreview = false
                onExportVideo(displayedItems)
            }
        )
    }

    // Clear Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = DarkSurface,
            title = {
                Text(stringResource(R.string.dialog_clear_title), color = AccentWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    stringResource(R.string.dialog_clear_message),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearItems()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30), contentColor = Color.White)
                ) {
                    Text(stringResource(R.string.btn_clear), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.btn_cancel), color = AccentWhite)
                }
            }
        )
    }

    // Exporting Progress Dialog
    if (isExporting) {
        Dialog(onDismissRequest = {}) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { exportProgress },
                        modifier = Modifier.size(64.dp),
                        color = AccentWhite,
                        trackColor = DarkSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.exporting_title),
                        color = AccentWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = exportProgressText,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Success Dialog
    if (exportedVideoUri != null) {
        AlertDialog(
            onDismissRequest = onDismissExportDialog,
            shape = RoundedCornerShape(16.dp),
            containerColor = DarkSurface,
            title = {
                Text(stringResource(R.string.success_title), color = AccentWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    stringResource(R.string.success_message),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { onOpenExportedVideo(exportedVideoUri) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentWhite, contentColor = DarkBackground)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.btn_open_share), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissExportDialog) {
                    Text(stringResource(R.string.btn_close), color = AccentWhite)
                }
            }
        )
    }
}

@Composable
fun PreviewDialog(
    items: List<BeRealItem>,
    durationPerImage: Float,
    onDurationChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    val includedItems = remember(items.toList()) { items.filter { it.isIncluded } }
    if (includedItems.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }

    // Automatic slideshow progression based on configured duration
    LaunchedEffect(isPlaying, currentIndex, durationPerImage, includedItems.size) {
        if (isPlaying && includedItems.isNotEmpty()) {
            delay((durationPerImage * 1000L).toLong())
            currentIndex = (currentIndex + 1) % includedItems.size
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val currentItem = includedItems.getOrNull(currentIndex)

            // Video Canvas Simulation (Exact 9:16 Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                    .align(Alignment.Center)
                    .background(Color.Black)
            ) {
                if (currentItem != null) {
                    // Photo centered vertically in 3:4 ratio inside 9:16 canvas
                    AsyncImage(
                        model = currentItem.filePath ?: currentItem.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .align(Alignment.Center),
                        contentScale = ContentScale.Fit
                    )

                    // Day Number rendered in top bar (exact simulation of exported video)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.125f) // 240 / 1920 = 12.5%
                            .align(Alignment.TopCenter),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${currentItem.dayNumber}",
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close), tint = Color.White)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${currentIndex + 1} / ${includedItems.size}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentWhite, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.action_preview).let { stringResource(R.string.action_clear).substringBefore(" ") }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Bottom Player Controls Overlay
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                color = Color.Black.copy(alpha = 0.88f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Playback progress bar
                    LinearProgressIndicator(
                        progress = { (currentIndex + 1).toFloat() / includedItems.size },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = BeRealYellow,
                        trackColor = DarkSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Duration Slider with live update
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.preview_speed),
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Text(
                            text = stringResource(R.string.preview_speed_format, durationPerImage, includedItems.size * durationPerImage),
                            color = BeRealYellow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = durationPerImage,
                        onValueChange = onDurationChange,
                        valueRange = 0.2f..2.5f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = DarkSurfaceVariant
                        )
                    )

                    // Transport controls (Prev, Play/Pause, Next)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                currentIndex = if (currentIndex > 0) currentIndex - 1 else includedItems.lastIndex
                            }
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.btn_previous), tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color.White, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.btn_pause) else stringResource(R.string.btn_play),
                                tint = Color.Black,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        IconButton(
                            onClick = {
                                currentIndex = (currentIndex + 1) % includedItems.size
                            }
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.btn_next), tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BeRealGridItem(
    item: BeRealItem,
    onToggle: () -> Unit
) {
    val alpha = if (item.isIncluded) 1f else 0.4f

    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .border(
                width = if (item.isIncluded) 1.dp else 0.5.dp,
                color = if (item.isIncluded) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onToggle() }
    ) {
        AsyncImage(
            model = item.filePath ?: item.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = alpha
        )

        // Day Number Badge (Top Center)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${item.dayNumber}",
                color = AccentWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }

        // Selection Checkmark (Top Right)
        if (item.isIncluded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(AccentWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Time Badge (Bottom Center)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%02d:%02d", item.hour, item.minute),
                color = TextSecondary,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
