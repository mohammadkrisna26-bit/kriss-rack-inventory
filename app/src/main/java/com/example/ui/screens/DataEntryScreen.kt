package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import android.graphics.Bitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entity.SectionEntity
import com.example.data.local.model.ProductWithLocation
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.DuplicateArticleDialog
import com.example.ui.components.ImageDetailDialog
import com.example.ui.components.MoveSectionDialog
import com.example.ui.components.PhotoSourcePickerDialog
import com.example.ui.components.ProductCard
import com.example.ui.components.ProductDetailDialog
import com.example.ui.components.ProductImageSlotItem
import com.example.ui.viewmodel.InventoryViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataEntryScreen(
    viewModel: InventoryViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val departments by viewModel.allDepartments.collectAsState()
    val sections by viewModel.allSections.collectAsState()
    val entryDeptId by viewModel.entryDepartmentId.collectAsState()
    val entrySecId by viewModel.entrySectionId.collectAsState()
    val currentSectionInfo by viewModel.currentSelectedSection.collectAsState()
    val sectionProductCount by viewModel.entrySectionProductCount.collectAsState()
    val allProducts by viewModel.allProducts.collectAsState()

    val duplicateArticle by viewModel.duplicateArticleFound.collectAsState()
    val duplicatePrompt by viewModel.duplicateArticlePrompt.collectAsState()
    val successMessage by viewModel.entrySuccessMessage.collectAsState()
    val isShowingNewForm by viewModel.isShowingNewProductForm.collectAsState()

    val rapidArticle by viewModel.rapidInputArticle.collectAsState()
    val rapidName by viewModel.rapidInputName.collectAsState()
    val rapidStock by viewModel.rapidInputStock.collectAsState()
    val rapidDesc by viewModel.rapidInputDescription.collectAsState()
    val rapidImageUri by viewModel.rapidInputImageUri.collectAsState()
    val rapidImageUri2 by viewModel.rapidInputImageUri2.collectAsState()
    val rapidImageUri3 by viewModel.rapidInputImageUri3.collectAsState()
    var activeSlotForPicker by remember { mutableStateOf<Int?>(null) }

    // Filter products in this active section
    val sectionProducts = remember(allProducts, entrySecId) {
        if (entrySecId != null && entrySecId!! > 0) {
            allProducts.filter { it.product.sectionId == entrySecId }
        } else {
            emptyList()
        }
    }

    var showScannerDialog by remember { mutableStateOf(false) }
    var showSectionPickerSheet by remember { mutableStateOf(false) }
    var rawInputText by remember { mutableStateOf("") }

    var selectedProductForDetail by remember { mutableStateOf<ProductWithLocation?>(null) }
    var selectedProductForMove by remember { mutableStateOf<ProductWithLocation?>(null) }
    var selectedImageForPreview by remember { mutableStateOf<Pair<String?, String>?>(null) }

    // Auto-dismiss green success banner after 4 seconds
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            delay(4000)
            // viewModel auto resets or user continues
        }
    }

    // Photo picker for rapid product
    val canManageDept = entryDeptId == null || viewModel.canUserManageDepartment(entryDeptId!!)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.persistImage(uri) { savedPath ->
                when (activeSlotForPicker) {
                    1 -> viewModel.setRapidImageUri(savedPath)
                    2 -> viewModel.setRapidImageUri2(savedPath)
                    3 -> viewModel.setRapidImageUri3(savedPath)
                }
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.persistBitmap(bitmap) { savedPath ->
                when (activeSlotForPicker) {
                    1 -> viewModel.setRapidImageUri(savedPath)
                    2 -> viewModel.setRapidImageUri2(savedPath)
                    3 -> viewModel.setRapidImageUri3(savedPath)
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePictureLauncher.launch(null)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Top App Bar
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MODE PENDATAAN CEPAT",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Pendataan barang rak/display per section",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Active Section Header Banner & Switcher Button
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentSectionInfo != null) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SECTION AKTIF PENDATAAN",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Button Ganti Section
                        Button(
                            onClick = { showSectionPickerSheet = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("change_section_button")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GANTI SECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (currentSectionInfo != null) {
                        val sec = currentSectionInfo!!.section
                        val deptName = departments.firstOrNull { it.id == sec.departmentId }?.name ?: ""

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = sec.code,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$deptName • ${sec.address}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Kode Komuditi: ${sec.code}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Product counter in this section badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "📦 $sectionProductCount barang terdata di Section ini",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Belum ada Section yang dipilih. Silakan pilih Departemen dan Section untuk memulai pendataan barang.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showSectionPickerSheet = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Pilih Section Sekarang")
                        }
                    }
                }
            }
        }

        // Green Success Feedback Banner
        if (successMessage != null) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFD1FAE5)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = successMessage!!,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // (Duplicate notification is shown via pop-up DuplicateArticleDialog)

        // PRIMARY RAPID INPUT SECTION
        if (entrySecId != null && entrySecId!! > 0 && duplicateArticle == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Input / Scan Artikel Baru",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ketik atau scan barcode nomor artikel untuk mendata ke Section ${currentSectionInfo?.section?.code ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Article input row with SCAN BARCODE button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = rawInputText,
                                onValueChange = { rawInputText = it },
                                label = { Text("Nomor Artikel / Barcode") },
                                placeholder = { Text("Contoh: 10000001") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (rawInputText.isNotBlank() && canManageDept) {
                                            viewModel.processArticleScanOrInput(rawInputText)
                                            rawInputText = ""
                                        }
                                    }
                                ),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("rapid_article_input")
                            )

                            Button(
                                onClick = { showScannerDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("rapid_scan_barcode_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SCAN", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (rawInputText.isNotBlank() && canManageDept) {
                                    viewModel.processArticleScanOrInput(rawInputText)
                                    rawInputText = ""
                                }
                            },
                            enabled = rawInputText.isNotBlank() && canManageDept,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("PROSES NOMOR ARTIKEL")
                        }
                    }
                }
            }
        }

        // EXPANDED NEW PRODUCT FORM (when article not in DB)
        if (isShowingNewForm && duplicateArticle == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lengkapi Data Barang Baru",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { viewModel.cancelNewProductForm() }) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Prefilled Article
                        OutlinedTextField(
                            value = rapidArticle,
                            onValueChange = { viewModel.setRapidArticle(it) },
                            label = { Text("Nomor Artikel (Wajib)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Name field (Required)
                        OutlinedTextField(
                            value = rapidName,
                            onValueChange = { viewModel.setRapidName(it) },
                            label = { Text("Nama Produk (Wajib)") },
                            placeholder = { Text("Contoh: Kunci Inggris 10 inch") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rapid_product_name_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stock quantity field (Required)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = rapidStock,
                                onValueChange = { newVal ->
                                    if (newVal.isEmpty() || newVal.all { it.isDigit() }) {
                                        viewModel.setRapidStock(newVal)
                                    }
                                },
                                label = { Text("Jumlah Stok (Wajib)") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("rapid_product_stock_input")
                            )

                            OutlinedButton(
                                onClick = {
                                    val cur = rapidStock.toIntOrNull() ?: 0
                                    if (cur > 0) viewModel.setRapidStock((cur - 1).toString())
                                },
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val cur = rapidStock.toIntOrNull() ?: 0
                                    viewModel.setRapidStock((cur + 1).toString())
                                },
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Description field
                        OutlinedTextField(
                            value = rapidDesc,
                            onValueChange = { viewModel.setRapidDescription(it) },
                            label = { Text("Deskripsi (Opsional)") },
                            placeholder = { Text("Spesifikasi atau letak rak display spesifik") },
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3 Slots for Product Images
                        Text(
                            text = "Foto Produk (Maksimal 3 Gambar)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Slot yang belum digunakan tetap kosong. Pengguna tidak wajib mengisi semua slot.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProductImageSlotItem(
                                slotNumber = 1,
                                imageUri = rapidImageUri,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSlotForPicker = 1 },
                                onDelete = { viewModel.setRapidImageUri(null) }
                            )
                            ProductImageSlotItem(
                                slotNumber = 2,
                                imageUri = rapidImageUri2,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSlotForPicker = 2 },
                                onDelete = { viewModel.setRapidImageUri2(null) }
                            )
                            ProductImageSlotItem(
                                slotNumber = 3,
                                imageUri = rapidImageUri3,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSlotForPicker = 3 },
                                onDelete = { viewModel.setRapidImageUri3(null) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val parsedRapidStock = rapidStock.toIntOrNull()
                        val isRapidValid = canManageDept &&
                            rapidArticle.isNotBlank() &&
                            rapidName.isNotBlank() &&
                            parsedRapidStock != null &&
                            parsedRapidStock >= 0

                        // Save & Continue Button
                        Button(
                            onClick = { viewModel.saveRapidProduct() },
                            enabled = isRapidValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_and_continue_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SIMPAN & LANJUTKAN", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // List of items in current active section
        if (entrySecId != null && entrySecId!! > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Barang di Komuditi Ini (${sectionProducts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (sectionProducts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "Belum ada barang di Komuditi ini. Silakan mulai ketik atau scan nomor artikel di atas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(sectionProducts, key = { it.product.id }) { item ->
                    ProductCard(
                        productWithLocation = item,
                        canEdit = viewModel.canUserManageDepartment(item.product.departmentId),
                        onDetailClick = { selectedProductForDetail = item },
                        onMoveClick = { selectedProductForMove = item },
                        onImageClick = { selectedImageForPreview = Pair(item.product.primaryImageUri, item.product.name) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    // --- QUICK KOMUDITI SWITCHER MODAL BOTTOM SHEET ---
    if (showSectionPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSectionPickerSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "PILIH KOMUDITI DISPLAY",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pilih Komuditi untuk berpindah lokasi pendataan barang tanpa keluar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    departments.forEach { dept ->
                        val deptSections = sections.filter { it.departmentId == dept.id }
                        item {
                            Text(
                                text = dept.name.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )
                        }

                        if (deptSections.isEmpty()) {
                            item {
                                Text(
                                    text = "Belum ada komuditi di departemen ini",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        } else {
                            items(deptSections) { sec ->
                                val isSelected = sec.id == entrySecId
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.selectEntrySection(sec)
                                            showSectionPickerSheet = false
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Komuditi ${sec.code}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "Alamat: ${sec.address}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (isSelected) {
                                            Text(
                                                text = "AKTIF",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Barcode Scanner Dialog
    if (showScannerDialog) {
        BarcodeScannerDialog(
            onDismissRequest = { showScannerDialog = false },
            onBarcodeScanned = { scanned ->
                showScannerDialog = false
                viewModel.processArticleScanOrInput(scanned)
            }
        )
    }

    // Detail Dialog
    selectedProductForDetail?.let { item ->
        ProductDetailDialog(
            productWithLocation = item,
            canEdit = viewModel.canUserManageDepartment(item.product.departmentId),
            onDismissRequest = { selectedProductForDetail = null },
            onEditClick = { selectedProductForDetail = null },
            onChangePhotoClick = { selectedProductForDetail = null },
            onMoveSectionClick = {
                selectedProductForDetail = null
                selectedProductForMove = item
            },
            onDeleteClick = {
                viewModel.deleteProduct(item.product.id) {
                    selectedProductForDetail = null
                }
            },
            onViewLargePhotoClick = { uri ->
                selectedImageForPreview = Pair(uri, item.product.name)
            },
            onUpdateStock = { newStock ->
                viewModel.updateProductStock(item.product.id, newStock) { success ->
                    if (success) {
                        selectedProductForDetail = null
                    }
                }
            }
        )
    }

    // Move Section Dialog
    selectedProductForMove?.let { item ->
        MoveSectionDialog(
            productWithLocation = item,
            departments = departments,
            sections = sections,
            onDismissRequest = { selectedProductForMove = null },
            onConfirmMove = { targetSectionId, notes ->
                viewModel.moveProduct(item.product.id, targetSectionId, notes) { success ->
                    if (success) selectedProductForMove = null
                }
            }
        )
    }

    // Image Zoom Dialog
    selectedImageForPreview?.let { (uri, title) ->
        ImageDetailDialog(
            imageUri = uri,
            title = title,
            onDismissRequest = { selectedImageForPreview = null }
        )
    }

    // DUPLICATE ARTICLE POP-UP DIALOG
    duplicatePrompt?.let { prompt ->
        DuplicateArticleDialog(
            prompt = prompt,
            onDismissRequest = { viewModel.dismissDuplicatePrompt() },
            onKeepDifferentLocation = { viewModel.proceedWithDifferentLocation() },
            onMergeWithExisting = { targetProductId, addedStock ->
                viewModel.mergeStockWithExisting(targetProductId, addedStock)
            }
        )
    }

    // PHOTO SOURCE PICKER (CAMERA VS GALLERY)
    if (activeSlotForPicker != null) {
        val slot = activeSlotForPicker!!
        val currentUri = when (slot) {
            1 -> rapidImageUri
            2 -> rapidImageUri2
            3 -> rapidImageUri3
            else -> null
        }
        PhotoSourcePickerDialog(
            title = "Pilih Gambar $slot",
            hasExistingPhoto = !currentUri.isNullOrEmpty(),
            onDismissRequest = { activeSlotForPicker = null },
            onCameraClick = {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            },
            onGalleryClick = {
                photoPickerLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onDeletePhotoClick = {
                when (slot) {
                    1 -> viewModel.setRapidImageUri(null)
                    2 -> viewModel.setRapidImageUri2(null)
                    3 -> viewModel.setRapidImageUri3(null)
                }
                activeSlotForPicker = null
            }
        )
    }
}
