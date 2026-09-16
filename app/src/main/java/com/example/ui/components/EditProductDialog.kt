package com.example.ui.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.model.ProductWithLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProductDialog(
    productWithLocation: ProductWithLocation?,
    initialArticle: String = "",
    initialDepartmentId: Long? = null,
    initialSectionId: Long? = null,
    departments: List<DepartmentEntity>,
    sections: List<SectionEntity>,
    onDismissRequest: () -> Unit,
    onSave: (
        article: String,
        name: String,
        deptId: Long,
        secId: Long,
        stockQuantity: Int,
        imageUri: String?,
        imageUri2: String?,
        imageUri3: String?,
        desc: String,
        isActive: Boolean
    ) -> Unit,
    onPersistImage: (Uri, (String) -> Unit) -> Unit,
    onPersistBitmap: ((Bitmap, (String) -> Unit) -> Unit)? = null
) {
    val isEditMode = productWithLocation != null
    val p = productWithLocation?.product

    var articleNumber by remember { mutableStateOf(p?.articleNumber ?: initialArticle) }
    var productName by remember { mutableStateOf(p?.name ?: "") }
    var stockQuantityText by remember { mutableStateOf((p?.stockQuantity ?: 0).toString()) }
    var description by remember { mutableStateOf(p?.description ?: "") }
    var isActive by remember { mutableStateOf(p?.isActive ?: true) }

    // 3 Image Slots
    var imageUri1 by remember { mutableStateOf(p?.imageUri) }
    var imageUri2 by remember { mutableStateOf(p?.imageUri2) }
    var imageUri3 by remember { mutableStateOf(p?.imageUri3) }
    var activeSlotForPicker by remember { mutableStateOf<Int?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingSlotForAction by remember { mutableStateOf<Int?>(null) }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }

    var selectedDeptId by remember {
        mutableStateOf(p?.departmentId ?: initialDepartmentId ?: departments.firstOrNull()?.id ?: 1L)
    }

    val availableSections = remember(selectedDeptId, sections) {
        sections.filter { it.departmentId == selectedDeptId }
    }

    var selectedSectionId by remember {
        mutableStateOf(
            p?.sectionId ?: initialSectionId ?: availableSections.firstOrNull()?.id ?: sections.firstOrNull()?.id ?: 1L
        )
    }

    var deptExpanded by remember { mutableStateOf(false) }
    var sectionExpanded by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val slot = pendingSlotForAction
        if (uri != null && slot != null) {
            onPersistImage(uri) { savedPath ->
                when (slot) {
                    1 -> imageUri1 = savedPath
                    2 -> imageUri2 = savedPath
                    3 -> imageUri3 = savedPath
                }
            }
        }
        pendingSlotForAction = null
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val slot = pendingSlotForAction
        val file = pendingCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            val savedPath = file.absolutePath
            when (slot) {
                1 -> imageUri1 = savedPath
                2 -> imageUri2 = savedPath
                3 -> imageUri3 = savedPath
            }
        } else {
            com.example.util.ImageCaptureHelper.cleanupIfEmpty(file)
        }
        pendingCameraFile = null
        pendingSlotForAction = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val (file, uri) = com.example.util.ImageCaptureHelper.createCameraDestination(context)
                pendingCameraFile = file
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
                com.example.util.ImageCaptureHelper.cleanupIfEmpty(pendingCameraFile)
                pendingCameraFile = null
                pendingSlotForAction = null
            }
        } else {
            Toast.makeText(context, "Izin kamera diperlukan untuk mengambil foto produk", Toast.LENGTH_SHORT).show()
            com.example.util.ImageCaptureHelper.cleanupIfEmpty(pendingCameraFile)
            pendingCameraFile = null
            pendingSlotForAction = null
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "EDIT PRODUK" else "TAMBAH PRODUK",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

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
                        imageUri = imageUri1,
                        modifier = Modifier.weight(1f),
                        onClick = { activeSlotForPicker = 1 },
                        onDelete = { imageUri1 = null }
                    )
                    ProductImageSlotItem(
                        slotNumber = 2,
                        imageUri = imageUri2,
                        modifier = Modifier.weight(1f),
                        onClick = { activeSlotForPicker = 2 },
                        onDelete = { imageUri2 = null }
                    )
                    ProductImageSlotItem(
                        slotNumber = 3,
                        imageUri = imageUri3,
                        modifier = Modifier.weight(1f),
                        onClick = { activeSlotForPicker = 3 },
                        onDelete = { imageUri3 = null }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Article Field
                OutlinedTextField(
                    value = articleNumber,
                    onValueChange = { articleNumber = it },
                    label = { Text("Nomor Artikel (Wajib)") },
                    placeholder = { Text("Contoh: 10000001") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Name Field
                OutlinedTextField(
                    value = productName,
                    onValueChange = { productName = it },
                    label = { Text("Nama Produk (Wajib)") },
                    placeholder = { Text("Contoh: Kunci Inggris Krisbow") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Stock Quantity Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockQuantityText,
                        onValueChange = { newVal ->
                            if (newVal.isEmpty() || newVal.all { it.isDigit() }) {
                                stockQuantityText = newVal
                            }
                        },
                        label = { Text("Jumlah Stok (Wajib)") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedButton(
                        onClick = {
                            val current = stockQuantityText.toIntOrNull() ?: 0
                            if (current > 0) stockQuantityText = (current - 1).toString()
                        },
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val current = stockQuantityText.toIntOrNull() ?: 0
                            stockQuantityText = (current + 1).toString()
                        },
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Department Dropdown
                ExposedDropdownMenuBox(
                    expanded = deptExpanded,
                    onExpandedChange = { deptExpanded = !deptExpanded }
                ) {
                    val currentDept = departments.firstOrNull { it.id == selectedDeptId }
                    OutlinedTextField(
                        value = currentDept?.name ?: "Pilih Departemen",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Departemen (Wajib)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = deptExpanded,
                        onDismissRequest = { deptExpanded = false }
                    ) {
                        departments.forEach { dept ->
                            DropdownMenuItem(
                                text = { Text(dept.name) },
                                onClick = {
                                    selectedDeptId = dept.id
                                    val newAvailable = sections.filter { it.departmentId == dept.id }
                                    selectedSectionId = newAvailable.firstOrNull()?.id ?: 0L
                                    deptExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Komuditi Dropdown
                ExposedDropdownMenuBox(
                    expanded = sectionExpanded,
                    onExpandedChange = { sectionExpanded = !sectionExpanded }
                ) {
                    val currentSection = sections.firstOrNull { it.id == selectedSectionId }
                    OutlinedTextField(
                        value = if (currentSection != null) "${currentSection.code} • ${currentSection.address}" else "Pilih Komuditi / Alamat",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Komuditi / Alamat (Wajib)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = sectionExpanded,
                        onDismissRequest = { sectionExpanded = false }
                    ) {
                        availableSections.forEach { sec ->
                            DropdownMenuItem(
                                text = { Text("${sec.code} • ${sec.address}") },
                                onClick = {
                                    selectedSectionId = sec.id
                                    sectionExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Description Field
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi (Opsional)") },
                    placeholder = { Text("Ukuran, spesifikasi, atau info tambahan") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal")
                    }

                    val parsedStock = stockQuantityText.toIntOrNull()
                    val isFormValid = articleNumber.isNotBlank() &&
                        productName.isNotBlank() &&
                        selectedSectionId > 0 &&
                        parsedStock != null &&
                        parsedStock >= 0

                    Button(
                        onClick = {
                            if (isFormValid && parsedStock != null) {
                                onSave(
                                    articleNumber.trim(),
                                    productName.trim(),
                                    selectedDeptId,
                                    selectedSectionId,
                                    parsedStock,
                                    imageUri1,
                                    imageUri2,
                                    imageUri3,
                                    description.trim(),
                                    isActive
                                )
                            }
                        },
                        enabled = isFormValid,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SIMPAN")
                    }
                }
            }
        }
    }

    if (activeSlotForPicker != null) {
        val slot = activeSlotForPicker!!
        val currentUri = when (slot) {
            1 -> imageUri1
            2 -> imageUri2
            3 -> imageUri3
            else -> null
        }
        PhotoSourcePickerDialog(
            title = "Pilih Gambar $slot",
            hasExistingPhoto = !currentUri.isNullOrEmpty(),
            onDismissRequest = { activeSlotForPicker = null },
            onCameraClick = {
                val targetSlot = slot
                pendingSlotForAction = targetSlot
                activeSlotForPicker = null
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    try {
                        val (file, uri) = com.example.util.ImageCaptureHelper.createCameraDestination(context)
                        pendingCameraFile = file
                        takePictureLauncher.launch(uri)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
                        com.example.util.ImageCaptureHelper.cleanupIfEmpty(pendingCameraFile)
                        pendingCameraFile = null
                        pendingSlotForAction = null
                    }
                } else {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            },
            onGalleryClick = {
                val targetSlot = slot
                pendingSlotForAction = targetSlot
                activeSlotForPicker = null
                try {
                    photoPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal membuka galeri: ${e.message}", Toast.LENGTH_SHORT).show()
                    pendingSlotForAction = null
                }
            },
            onDeletePhotoClick = {
                when (slot) {
                    1 -> imageUri1 = null
                    2 -> imageUri2 = null
                    3 -> imageUri3 = null
                }
                activeSlotForPicker = null
            }
        )
    }
}

@Composable
fun ProductImageSlotItem(
    slotNumber: Int,
    imageUri: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!imageUri.isNullOrEmpty()) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (imageUri.isNullOrEmpty()) androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ) else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUri.isNullOrEmpty()) {
                val imageModel = remember(imageUri) {
                    if (imageUri.startsWith("/")) java.io.File(imageUri) else imageUri
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Gambar $slotNumber",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Slot number badge
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "Gambar $slotNumber",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Delete quick button on slot
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hapus Foto",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                // Empty slot indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Gambar $slotNumber",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Kosong",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
