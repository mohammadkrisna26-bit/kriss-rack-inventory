package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
        desc: String,
        isActive: Boolean
    ) -> Unit,
    onPersistImage: (Uri, (String) -> Unit) -> Unit
) {
    val isEditMode = productWithLocation != null
    val p = productWithLocation?.product

    var articleNumber by remember { mutableStateOf(p?.articleNumber ?: initialArticle) }
    var productName by remember { mutableStateOf(p?.name ?: "") }
    var stockQuantityText by remember { mutableStateOf((p?.stockQuantity ?: 0).toString()) }
    var description by remember { mutableStateOf(p?.description ?: "") }
    var isActive by remember { mutableStateOf(p?.isActive ?: true) }
    var currentImageUri by remember { mutableStateOf(p?.imageUri) }

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
        if (uri != null) {
            onPersistImage(uri) { savedPath ->
                currentImageUri = savedPath
            }
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

                // Image Preview & Change / Remove Buttons
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentImageUri.isNullOrEmpty()) {
                        AsyncImage(
                            model = currentImageUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                )
                            ) {
                                Text("Ganti", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { currentImageUri = null },
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Hapus Foto", fontSize = 11.sp)
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload Foto Produk", fontSize = 12.sp)
                            }
                        }
                    }
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

                // Section Dropdown
                ExposedDropdownMenuBox(
                    expanded = sectionExpanded,
                    onExpandedChange = { sectionExpanded = !sectionExpanded }
                ) {
                    val currentSection = sections.firstOrNull { it.id == selectedSectionId }
                    OutlinedTextField(
                        value = if (currentSection != null) "${currentSection.code} - ${currentSection.name}" else "Pilih Section",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Section (Wajib)") },
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
                                text = { Text("${sec.code} - ${sec.name} (${sec.address})") },
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
                                    currentImageUri,
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
}
