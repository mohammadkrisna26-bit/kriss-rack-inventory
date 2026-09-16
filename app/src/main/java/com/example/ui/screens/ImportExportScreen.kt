package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.repository.BackupRestoreResult
import com.example.data.repository.InventoryRepository
import com.example.ui.viewmodel.InventoryViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportExportScreen(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allProducts by viewModel.allProducts.collectAsState()
    val departments by viewModel.allDepartments.collectAsState()
    val sections by viewModel.allSections.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Backup/Restore, 1: Export, 2: Import CSV

    // Full ZIP Backup & Restore states
    var generatedBackupZipFile by remember { mutableStateOf<File?>(null) }
    var isGeneratingZipBackup by remember { mutableStateOf(false) }

    var selectedRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var restoreSuccessResult by remember { mutableStateOf<BackupRestoreResult?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    // JSON fallback backup & restore state
    var backupJsonText by remember { mutableStateOf("") }
    var isGeneratingBackup by remember { mutableStateOf(false) }
    var restoreInputJson by remember { mutableStateOf("") }
    var isRestoringJson by remember { mutableStateOf(false) }

    // Save ZIP backup file to external / download storage launcher
    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && generatedBackupZipFile != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        generatedBackupZipFile!!.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    viewModel.emitMessage("File backup berhasil disimpan ke penyimpanan HP!")
                } catch (e: Exception) {
                    viewModel.emitMessage("Gagal menyimpan file: ${e.message}")
                }
            }
        }
    }

    // Pick backup file from HP storage launcher
    val restoreFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    // Export Excel state
    var generatedExcelFile by remember { mutableStateOf<File?>(null) }
    var isExportingExcel by remember { mutableStateOf(false) }

    // Export CSV state
    var exportedCsvText by remember { mutableStateOf("") }
    var isExportingCsv by remember { mutableStateOf(false) }

    // Import state
    var inputCsvText by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<InventoryRepository.CsvImportPreview?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val sampleCsvTemplate = remember {
        """Artikel,Nama Produk,Departemen,Komuditi,Alamat Komuditi,Jumlah Stok,Deskripsi
50000001,Bor Listrik 13mm Cordless,Tools,T-04,Rak Tools Mesin B1,10,Bor baterai 20V
50000002,Gembok Kuningan 50mm Heavy Duty,Hardware,H-03,Rak Hardware B1,25,Gembok anti karat 4 kunci
50000003,Lemari Locker Pegawai 6 Pintu,Loker Rak Kabinet,LK-02,Display Loker B2,5,Loker plat besi kunci master"""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab Header
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 12.dp
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("BACKUP & RESTORE", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT (EXCEL / CSV)", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("IMPORT CSV", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> {
                // --- BACKUP & RESTORE TAB ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // CARD 1: BACKUP DATA (ZIP with Images)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Backup,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Backup Data",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Cadangkan Seluruh Data & Foto Produk ke File ZIP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Mencadangkan SELURUH data inventaris ke dalam satu file ZIP offline yang aman. Semua foto produk disimpan sebagai file gambar biner utuh (bukan sekadar path), sehingga data tetap aman walaupun aplikasi diperbaiki, diperbarui, atau berpindah HP.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Nomor Artikel & Nama",
                                    "Departemen",
                                    "Komuditi & Alamat Rak",
                                    "Jumlah Stok",
                                    "Foto Produk Asli (.jpg)",
                                    "Relasi & Riwayat"
                                ).forEach { badgeText ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "✓ $badgeText",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${departments.size} Departemen • ${sections.size} Komuditi • ${allProducts.size} Produk tersimpan",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isGeneratingZipBackup = true
                                        try {
                                            val zipFile = viewModel.createZipBackup()
                                            generatedBackupZipFile = zipFile
                                            viewModel.emitMessage("Backup berhasil")

                                            // Otomatis buka Android Share Sheet
                                            try {
                                                val fileUri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    zipFile
                                                )
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    putExtra(Intent.EXTRA_STREAM, fileUri)
                                                    putExtra(Intent.EXTRA_SUBJECT, zipFile.name)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Bagikan File Backup Inventaris"))
                                            } catch (e: Exception) {
                                                // Handle chooser error gracefully
                                            }
                                        } catch (e: Exception) {
                                            viewModel.emitMessage(e.message ?: "Gagal membuat backup")
                                        } finally {
                                            isGeneratingZipBackup = false
                                        }
                                    }
                                },
                                enabled = !isGeneratingZipBackup,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isGeneratingZipBackup) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("MEMBUAT BACKUP ZIP & FOTO...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Backup, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("BUAT BACKUP DATA (.ZIP)", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Result card after ZIP created
                            generatedBackupZipFile?.let { zipFile ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFD1FAE5),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF065F46),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Backup Berhasil!",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF065F46),
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "${zipFile.name} (${(zipFile.length() + 1023) / 1024} KB)",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF065F46).copy(alpha = 0.85f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val fileUri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            zipFile
                                                        )
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "application/zip"
                                                            putExtra(Intent.EXTRA_STREAM, fileUri)
                                                            putExtra(Intent.EXTRA_SUBJECT, zipFile.name)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan File Backup"))
                                                    } catch (e: Exception) {
                                                        viewModel.emitMessage("Gagal membagikan: ${e.message}")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Bagikan File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    saveBackupLauncher.launch(zipFile.name)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Simpan ke HP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // CARD 2: RESTORE DATA
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Restore Data",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Pulihkan Seluruh Inventaris & Foto Produk",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Pilih file backup (.ZIP atau .JSON) dari penyimpanan HP. Seluruh data produk, Komuditi, jumlah stok, riwayat lokasi, dan foto produk akan dipulihkan kembali ke aplikasi secara utuh.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    restoreFilePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "*/*"))
                                },
                                enabled = !isRestoringBackup,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isRestoringBackup) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("MEMULIHKAN DATA & FOTO...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.UploadFile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("PILIH FILE BACKUP DARI HP", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Result card after Restore
                            restoreSuccessResult?.let { res ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFD1FAE5),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF065F46),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Restore Berhasil!",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF065F46),
                                                fontSize = 15.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = "• ${res.departmentsRestored} Departemen dipulihkan\n• ${res.sectionsRestored} Komuditi dipulihkan\n• ${res.productsRestored} Produk & Stok dipulihkan\n• ${res.imagesRestored} Foto Produk berhasil disinkronkan",
                                            fontSize = 13.sp,
                                            color = Color(0xFF065F46),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // CARD 3: ADVANCED RAW JSON BACKUP / RESTORE
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Opsi Cadangan Teks (JSON Alternatif)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Gunakan opsi ini jika ingin menyalin atau menempelkan teks cadangan database JSON secara manual.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        isGeneratingBackup = true
                                        val json = viewModel.createFullBackup()
                                        backupJsonText = json
                                        isGeneratingBackup = false
                                        viewModel.emitMessage("Cadangan JSON berhasil dibuat")
                                    }
                                },
                                enabled = !isGeneratingBackup,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isGeneratingBackup) "Membuat Teks JSON..." else "Hasilkan Teks Cadangan JSON", fontWeight = FontWeight.SemiBold)
                            }

                            if (backupJsonText.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("AZKO_Backup_JSON", backupJsonText))
                                            viewModel.emitMessage("Teks JSON disalin ke clipboard!")
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Salin JSON", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(Intent.EXTRA_SUBJECT, "AZKO_Inventory_Backup.json")
                                                putExtra(Intent.EXTRA_TEXT, backupJsonText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Simpan / Bagikan Cadangan JSON"))
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bagikan", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = restoreInputJson,
                                onValueChange = { restoreInputJson = it },
                                label = { Text("Paste Teks Cadangan JSON Disini") },
                                placeholder = { Text("{\"version\":1, \"departments\":[...], \"products\":[...]}") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { showRestoreConfirmDialog = true },
                                enabled = restoreInputJson.isNotBlank() && !isRestoringJson,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("PULIHKAN DARI TEKS JSON", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            1 -> {
                // --- EXPORT TAB (EXCEL & CSV) ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Card 1: Excel (.xlsx) with Embedded Images
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFF107C41).copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = Color(0xFF107C41),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Export ke File Excel (.xlsx)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Dengan Foto Produk Tertanam (Embedded)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF107C41),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Membuat file spreadsheet resmi (.xlsx) yang kompatibel dengan Microsoft Excel, WPS Office, dan Google Sheets. Foto produk disematkan langsung sebagai gambar nyata di dalam sel tabel (bukan sekadar link/path), lengkap dengan Nomor Artikel, Nama Produk, Departemen, Komuditi, Alamat Komuditi, Jumlah Stok, dan Deskripsi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Total ${allProducts.size} artikel siap diekspor",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isExportingExcel = true
                                        try {
                                            val file = viewModel.exportProductsToExcel(allProducts)
                                            generatedExcelFile = file
                                            viewModel.emitMessage("File Excel berhasil dibuat dengan gambar produk embedded!")
                                        } catch (e: Exception) {
                                            viewModel.emitMessage(e.message ?: "Gagal membuat file Excel")
                                        } finally {
                                            isExportingExcel = false
                                        }
                                    }
                                },
                                enabled = !isExportingExcel,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF107C41)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (isExportingExcel) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("MEMPROSES EXCEL & GAMBAR...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("EKSPOR KE EXCEL (.XLSX)", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Excel generated file actions
                            generatedExcelFile?.let { file ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFD1FAE5),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF065F46),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "File Excel Berhasil Dibuat!",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF065F46),
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = "${file.name} (${(file.length() + 1023) / 1024} KB)",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF065F46).copy(alpha = 0.85f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        val fileUri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            file
                                                        )
                                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                                            putExtra(Intent.EXTRA_STREAM, fileUri)
                                                            putExtra(Intent.EXTRA_SUBJECT, file.name)
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan File Excel Inventaris"))
                                                    } catch (e: Exception) {
                                                        viewModel.emitMessage("Gagal membagikan file: ${e.message}")
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF065F46)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Bagikan Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    try {
                                                        val fileUri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            file
                                                        )
                                                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(fileUri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        }
                                                        context.startActivity(viewIntent)
                                                    } catch (e: Exception) {
                                                        viewModel.emitMessage("Tidak ada aplikasi pembuka Excel di perangkat ini")
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Buka File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Card 2: CSV Export
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Export Data ke Format CSV",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Unduh teks CSV yang memuat nomor artikel, nama produk, departemen, komuditi, alamat komuditi, dan stok untuk impor ke sistem lain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isExportingCsv = true
                                        val csv = viewModel.generateExportCsv(allProducts)
                                        exportedCsvText = csv
                                        isExportingCsv = false
                                        viewModel.emitMessage("CSV berhasil dibuat!")
                                    }
                                },
                                enabled = !isExportingCsv,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("GENERATE EXPORT CSV", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (exportedCsvText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Preview File CSV",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("AZKO_Inventory_CSV", exportedCsvText))
                                                viewModel.emitMessage("Teks CSV disalin ke clipboard!")
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Salin", fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "AZKO_Inventory_Export.csv")
                                                    putExtra(Intent.EXTRA_TEXT, exportedCsvText)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Bagikan Data Inventory"))
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Bagikan", fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = exportedCsvText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // --- IMPORT CSV TAB ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Import Data Produk & Komuditi dari CSV",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Format Kolom: Artikel, Nama Produk, Departemen, Komuditi, Alamat Komuditi, Jumlah Stok, Deskripsi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { inputCsvText = sampleCsvTemplate },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Muat Contoh Template CSV", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = inputCsvText,
                                onValueChange = {
                                    inputCsvText = it
                                    importPreview = null
                                },
                                label = { Text("Paste atau Ketik Teks CSV di Sini") },
                                placeholder = { Text("Artikel,Nama Produk,Departemen,Komuditi,Alamat Komuditi,Jumlah Stok,Deskripsi...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val preview = viewModel.parseCsv(inputCsvText)
                                        importPreview = preview
                                    }
                                },
                                enabled = inputCsvText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("VALIDASI CSV", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Import Validation Results Preview
                    importPreview?.let { preview ->
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Hasil Validasi File CSV",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Ditemukan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${preview.totalFound} Data", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFD1FAE5),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Siap Diimport", fontSize = 11.sp, color = Color(0xFF065F46))
                                            Text("${preview.readyToImport} Data", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF065F46))
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (preview.problematicCount > 0) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Bermasalah", fontSize = 11.sp, color = if (preview.problematicCount > 0) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${preview.problematicCount} Data", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (preview.problematicCount > 0) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }

                                if (preview.problemItems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Daftar Data Bermasalah:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    preview.problemItems.forEach { prob ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEF2F2),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Baris ${prob.rowNumber}: ${prob.errorMessage}",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF991B1B)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        isImporting = true
                                        viewModel.executeImport(preview.validItems) {
                                            isImporting = false
                                            inputCsvText = ""
                                            importPreview = null
                                        }
                                    },
                                    enabled = preview.readyToImport > 0 && !isImporting,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("IMPORT ${preview.readyToImport} DATA SEKARANG", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog before restoring database
    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirmDialog = false
                selectedRestoreUri = null
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Konfirmasi Restore Data", fontWeight = FontWeight.Bold) },
            text = {
                Text("Restore akan mengganti data yang ada di aplikasi. Pastikan Anda sudah melakukan backup. Lanjutkan?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        val uriToRestore = selectedRestoreUri
                        if (uriToRestore != null) {
                            coroutineScope.launch {
                                isRestoringBackup = true
                                restoreSuccessResult = null
                                try {
                                    context.contentResolver.openInputStream(uriToRestore)?.use { input ->
                                        val result = viewModel.restoreBackupStream(input)
                                        result.onSuccess { res ->
                                            restoreSuccessResult = res
                                            viewModel.emitMessage("Restore berhasil")
                                        }.onFailure { err ->
                                            viewModel.emitMessage(err.message ?: "Gagal memulihkan file backup")
                                        }
                                    } ?: run {
                                        viewModel.emitMessage("Gagal membuka file backup yang dipilih")
                                    }
                                } catch (e: Exception) {
                                    viewModel.emitMessage("Error: ${e.message}")
                                } finally {
                                    isRestoringBackup = false
                                    selectedRestoreUri = null
                                }
                            }
                        } else if (restoreInputJson.isNotBlank()) {
                            coroutineScope.launch {
                                isRestoringJson = true
                                restoreSuccessResult = null
                                val res = viewModel.restoreFullBackup(restoreInputJson)
                                isRestoringJson = false
                                res.onSuccess {
                                    restoreSuccessResult = it
                                    viewModel.emitMessage("Restore berhasil")
                                }.onFailure {
                                    viewModel.emitMessage(it.message ?: "Gagal memulihkan database")
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        selectedRestoreUri = null
                    }
                ) {
                    Text("Batal")
                }
            }
        )
    }
}
