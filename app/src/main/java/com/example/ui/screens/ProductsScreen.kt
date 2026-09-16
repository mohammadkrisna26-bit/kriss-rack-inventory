package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.ProductWithLocation
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.EditProductDialog
import com.example.ui.components.ImageDetailDialog
import com.example.ui.components.MoveSectionDialog
import com.example.ui.components.ProductCard
import com.example.ui.components.ProductDetailDialog
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.PhotoFilter
import com.example.ui.viewmodel.ProductSortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier
) {
    val departments by viewModel.allDepartments.collectAsState()
    val sections by viewModel.allSections.collectAsState()
    val products by viewModel.filteredProducts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val selectedDeptId by viewModel.selectedDeptFilter.collectAsState()
    val selectedSecId by viewModel.selectedSectionFilter.collectAsState()
    val photoFilter by viewModel.photoFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    var showScannerDialog by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var selectedProductForDetail by remember { mutableStateOf<ProductWithLocation?>(null) }
    var selectedProductForEdit by remember { mutableStateOf<ProductWithLocation?>(null) }
    var selectedProductForMove by remember { mutableStateOf<ProductWithLocation?>(null) }
    var selectedImageForPreview by remember { mutableStateOf<Pair<String?, String>?>(null) }

    var deptMenuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddProductDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar & Scan Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Cari Artikel, Nama, atau Komuditi...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Hapus")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("products_search_input")
                        )

                        IconButton(
                            onClick = { showScannerDialog = true },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(12.dp))
                                .size(52.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan",
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Departemen Filter Dropdown Chip
                        Box {
                            val activeDeptName = departments.firstOrNull { it.id == selectedDeptId }?.name ?: "Semua Dept"
                            FilterChip(
                                selected = selectedDeptId != null,
                                onClick = { deptMenuOpen = true },
                                label = { Text(activeDeptName, fontSize = 12.sp) }
                            )

                            DropdownMenu(
                                expanded = deptMenuOpen,
                                onDismissRequest = { deptMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Semua Departemen") },
                                    onClick = {
                                        viewModel.selectedDeptFilter.value = null
                                        viewModel.selectedSectionFilter.value = null
                                        deptMenuOpen = false
                                    }
                                )
                                departments.forEach { dept ->
                                    DropdownMenuItem(
                                        text = { Text(dept.name) },
                                        onClick = {
                                            viewModel.selectedDeptFilter.value = dept.id
                                            deptMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        // Photo Filter Chips
                        FilterChip(
                            selected = photoFilter == PhotoFilter.WITH_PHOTO,
                            onClick = {
                                viewModel.photoFilter.value = if (photoFilter == PhotoFilter.WITH_PHOTO) PhotoFilter.ALL else PhotoFilter.WITH_PHOTO
                            },
                            label = { Text("Ada Foto", fontSize = 12.sp) }
                        )

                        FilterChip(
                            selected = photoFilter == PhotoFilter.WITHOUT_PHOTO,
                            onClick = {
                                viewModel.photoFilter.value = if (photoFilter == PhotoFilter.WITHOUT_PHOTO) PhotoFilter.ALL else PhotoFilter.WITHOUT_PHOTO
                            },
                            label = { Text("Belum Ada Foto", fontSize = 12.sp) }
                        )

                        // Sort Menu Chip
                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { sortMenuOpen = true },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            when (sortOption) {
                                                ProductSortOption.ARTICLE_ASC -> "Artikel A-Z"
                                                ProductSortOption.ARTICLE_DESC -> "Artikel Z-A"
                                                ProductSortOption.NAME_ASC -> "Nama A-Z"
                                                ProductSortOption.DATE_DESC -> "Terbaru"
                                            },
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            )

                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Terbaru Diperbarui") },
                                    onClick = {
                                        viewModel.sortOption.value = ProductSortOption.DATE_DESC
                                        sortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nomor Artikel (0-9)") },
                                    onClick = {
                                        viewModel.sortOption.value = ProductSortOption.ARTICLE_ASC
                                        sortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nomor Artikel (9-0)") },
                                    onClick = {
                                        viewModel.sortOption.value = ProductSortOption.ARTICLE_DESC
                                        sortMenuOpen = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Nama Produk (A-Z)") },
                                    onClick = {
                                        viewModel.sortOption.value = ProductSortOption.NAME_ASC
                                        sortMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Products Count & Results
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Menampilkan ${products.size} produk",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Barang '$searchQuery' tidak ditemukan di Komuditi mana pun."
                            else "Tidak ada produk sesuai filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = { showAddProductDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TAMBAH PRODUK BARU")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(products, key = { it.product.id }) { item ->
                        ProductCard(
                            productWithLocation = item,
                            canEdit = viewModel.canUserManageDepartment(item.product.departmentId),
                            onDetailClick = { selectedProductForDetail = item },
                            onEditClick = { selectedProductForEdit = item },
                            onMoveClick = { selectedProductForMove = item },
                            onImageClick = { selectedImageForPreview = Pair(item.product.primaryImageUri, item.product.name) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // Barcode Scanner
    if (showScannerDialog) {
        BarcodeScannerDialog(
            onDismissRequest = { showScannerDialog = false },
            onBarcodeScanned = { code ->
                showScannerDialog = false
                viewModel.setSearchQuery(code)
            }
        )
    }

    // Add Product Dialog
    if (showAddProductDialog) {
        EditProductDialog(
            productWithLocation = null,
            initialDepartmentId = selectedDeptId,
            departments = departments,
            sections = sections,
            onDismissRequest = { showAddProductDialog = false },
            onSave = { article, name, deptId, secId, stockQuantity, img1, img2, img3, desc, _ ->
                viewModel.createProduct(
                    articleNumber = article,
                    name = name,
                    departmentId = deptId,
                    sectionId = secId,
                    stockQuantity = stockQuantity,
                    imageUri = img1,
                    imageUri2 = img2,
                    imageUri3 = img3,
                    description = desc
                ) { success ->
                    if (success) {
                        showAddProductDialog = false
                    }
                }
            },
            onPersistImage = { uri, cb -> viewModel.persistImage(uri, cb) },
            onPersistBitmap = { bmp, cb -> viewModel.persistBitmap(bmp, cb) }
        )
    }

    // Edit Product Dialog
    selectedProductForEdit?.let { item ->
        EditProductDialog(
            productWithLocation = item,
            departments = departments,
            sections = sections,
            onDismissRequest = { selectedProductForEdit = null },
            onSave = { article, name, deptId, secId, stockQuantity, img1, img2, img3, desc, isActive ->
                viewModel.updateProduct(
                    id = item.product.id,
                    article = article,
                    name = name,
                    departmentId = deptId,
                    sectionId = secId,
                    stockQuantity = stockQuantity,
                    imageUri = img1,
                    imageUri2 = img2,
                    imageUri3 = img3,
                    description = desc,
                    isActive = isActive
                ) { success ->
                    if (success) selectedProductForEdit = null
                }
            },
            onPersistImage = { uri, cb -> viewModel.persistImage(uri, cb) },
            onPersistBitmap = { bmp, cb -> viewModel.persistBitmap(bmp, cb) }
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

    // Detail Dialog
    selectedProductForDetail?.let { item ->
        ProductDetailDialog(
            productWithLocation = item,
            canEdit = viewModel.canUserManageDepartment(item.product.departmentId),
            onDismissRequest = { selectedProductForDetail = null },
            onEditClick = {
                selectedProductForDetail = null
                selectedProductForEdit = item
            },
            onChangePhotoClick = {
                selectedProductForDetail = null
                selectedProductForEdit = item
            },
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

    // Image Zoom Dialog
    selectedImageForPreview?.let { (uri, title) ->
        ImageDetailDialog(
            imageUri = uri,
            title = title,
            onDismissRequest = { selectedImageForPreview = null }
        )
    }
}
