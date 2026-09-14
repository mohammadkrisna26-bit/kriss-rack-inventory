package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.DepartmentEntity
import com.example.data.local.entity.SectionEntity
import com.example.data.local.model.DepartmentWithStats
import com.example.data.local.model.SectionWithStats
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EditDepartmentDialog
import com.example.ui.components.EditSectionDialog
import com.example.ui.viewmodel.InventoryViewModel

@Composable
fun DepartmentsSectionsScreen(
    viewModel: InventoryViewModel,
    onActivateSectionForEntry: (SectionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Section, 1: Departemen

    val departments by viewModel.allDepartments.collectAsState()
    val departmentsWithStats by viewModel.departmentsWithStats.collectAsState()
    val sectionsWithStats by viewModel.sectionsWithStats.collectAsState()

    var showAddSectionDialog by remember { mutableStateOf(false) }
    var showAddDeptDialog by remember { mutableStateOf(false) }

    var sectionToEdit by remember { mutableStateOf<SectionEntity?>(null) }
    var sectionToDelete by remember { mutableStateOf<SectionWithStats?>(null) }

    var deptToEdit by remember { mutableStateOf<DepartmentEntity?>(null) }
    var deptToDelete by remember { mutableStateOf<DepartmentWithStats?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTabIndex == 0) showAddSectionDialog = true
                    else showAddDeptDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 60.dp)
                    .testTag("add_dept_sec_fab")
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = if (selectedTabIndex == 0) "Tambah Section" else "Tambah Departemen"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tabs
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SECTION (${sectionsWithStats.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DEPARTEMEN (${departments.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }

            // Tab Content
            if (selectedTabIndex == 0) {
                // --- SECTION LIST ---
                if (sectionsWithStats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Belum ada Section terdaftar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { showAddSectionDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tambah Section Baru")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 90.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sectionsWithStats, key = { it.section.id }) { item ->
                            SectionCard(
                                item = item,
                                onActivate = { onActivateSectionForEntry(item.section) },
                                onEdit = { sectionToEdit = item.section },
                                onDelete = { sectionToDelete = item }
                            )
                        }
                    }
                }
            } else {
                // --- DEPARTMENT LIST ---
                if (departmentsWithStats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Belum ada Departemen terdaftar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { showAddDeptDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tambah Departemen")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 90.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(departmentsWithStats, key = { it.department.id }) { item ->
                            DepartmentCard(
                                item = item,
                                onEdit = { deptToEdit = item.department },
                                onDelete = { deptToDelete = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // Add Section Dialog
    if (showAddSectionDialog) {
        EditSectionDialog(
            section = null,
            departments = departments,
            onDismissRequest = { showAddSectionDialog = false },
            onSave = { code, name, address, deptId, desc ->
                viewModel.createSection(code, name, address, deptId, desc) { success ->
                    if (success) showAddSectionDialog = false
                }
            }
        )
    }

    // Edit Section Dialog
    sectionToEdit?.let { sec ->
        EditSectionDialog(
            section = sec,
            departments = departments,
            onDismissRequest = { sectionToEdit = null },
            onSave = { code, name, address, deptId, desc ->
                viewModel.updateSection(sec.id, code, name, address, deptId, desc) { success ->
                    if (success) sectionToEdit = null
                }
            }
        )
    }

    // Delete Section Confirm Dialog
    sectionToDelete?.let { secStats ->
        ConfirmDialog(
            title = "Hapus Section",
            message = if (secStats.productCount > 0) {
                "PERINGATAN: Section '${secStats.section.code}' (${secStats.section.address}) masih memiliki ${secStats.productCount} produk. Pindahkan atau hapus semua produk terlebih dahulu."
            } else {
                "Apakah Anda yakin ingin menghapus Section '${secStats.section.code}' (${secStats.section.address})?"
            },
            confirmText = if (secStats.productCount > 0) "TUTUP" else "HAPUS",
            isDestructive = secStats.productCount == 0,
            onConfirm = {
                if (secStats.productCount == 0) {
                    viewModel.deleteSection(secStats.section.id) {
                        sectionToDelete = null
                    }
                } else {
                    sectionToDelete = null
                }
            },
            onDismissRequest = { sectionToDelete = null }
        )
    }

    // Add Department Dialog
    if (showAddDeptDialog) {
        EditDepartmentDialog(
            department = null,
            onDismissRequest = { showAddDeptDialog = false },
            onSave = { name, desc ->
                viewModel.createDepartment(name, desc) { success ->
                    if (success) showAddDeptDialog = false
                }
            }
        )
    }

    // Edit Department Dialog
    deptToEdit?.let { dept ->
        EditDepartmentDialog(
            department = dept,
            onDismissRequest = { deptToEdit = null },
            onSave = { name, desc ->
                viewModel.updateDepartment(dept.id, name, desc) { success ->
                    if (success) deptToEdit = null
                }
            }
        )
    }

    // Delete Department Confirm Dialog
    deptToDelete?.let { deptStats ->
        ConfirmDialog(
            title = "Hapus Departemen",
            message = if (deptStats.sectionCount > 0 || deptStats.productCount > 0) {
                "PERINGATAN: Departemen '${deptStats.department.name}' masih memiliki ${deptStats.sectionCount} Section dan ${deptStats.productCount} produk terkait. Hapus Section dan produk terkait terlebih dahulu."
            } else {
                "Apakah Anda yakin ingin menghapus Departemen '${deptStats.department.name}'?"
            },
            confirmText = if (deptStats.sectionCount > 0 || deptStats.productCount > 0) "TUTUP" else "HAPUS",
            isDestructive = deptStats.sectionCount == 0 && deptStats.productCount == 0,
            onConfirm = {
                if (deptStats.sectionCount == 0 && deptStats.productCount == 0) {
                    viewModel.deleteDepartment(deptStats.department.id) {
                        deptToDelete = null
                    }
                } else {
                    deptToDelete = null
                }
            },
            onDismissRequest = { deptToDelete = null }
        )
    }
}

@Composable
private fun SectionCard(
    item: SectionWithStats,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = item.section.code,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = item.section.address,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.departmentName} • ${item.section.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${item.productCount} barang",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (item.section.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.section.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onActivate,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PENDATAAN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Section", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus Section", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartmentCard(
    item: DepartmentWithStats,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.department.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.department.description.isNotBlank()) {
                        Text(
                            text = item.department.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Departemen", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus Departemen", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${item.sectionCount} Section",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${item.productCount} Produk",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
