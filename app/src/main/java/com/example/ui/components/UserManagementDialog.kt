package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.entity.DepartmentEntity
import com.example.data.model.AppUser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserManagementDialog(
    users: List<AppUser>,
    departments: List<DepartmentEntity>,
    onDismissRequest: () -> Unit,
    onCreateEmployee: (name: String, email: String, pass: String, role: String, deptIds: List<Long>, deptNames: List<String>, (Boolean, String) -> Unit) -> Unit,
    onUpdateEmployee: (user: AppUser, (Boolean, String) -> Unit) -> Unit
) {
    var isAddingNew by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<AppUser?>(null) }

    // Add New Form States
    var newName by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newRole by remember { mutableStateOf("KARYAWAN") }
    val selectedDeptIds = remember { mutableStateListOf<Long>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

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
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Kelola Akun Karyawan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Atur hak akses & penugasan departemen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                if (!isAddingNew && editingUser == null) {
                    // Top Action Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Pengguna (${users.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                newName = ""
                                newEmail = ""
                                newPassword = ""
                                newRole = "KARYAWAN"
                                selectedDeptIds.clear()
                                errorMessage = null
                                isAddingNew = true
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TAMBAH KARYAWAN", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users, key = { it.id }) { user ->
                            UserItemCard(
                                user = user,
                                onEditClick = { editingUser = user }
                            )
                        }
                    }
                } else if (isAddingNew) {
                    // FORM TAMBAH KARYAWAN
                    Text(
                        text = "Tambah Karyawan Baru",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it; errorMessage = null },
                                label = { Text("Nama Lengkap Karyawan") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = newEmail,
                                onValueChange = { newEmail = it; errorMessage = null },
                                label = { Text("Email Perusahaan") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        item {
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it; errorMessage = null },
                                label = { Text("Kata Sandi (min 6 karakter)") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        item {
                            Text("Peran (Role):", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = newRole == "KARYAWAN",
                                    onClick = { newRole = "KARYAWAN" },
                                    label = { Text("Karyawan") }
                                )
                                FilterChip(
                                    selected = newRole == "ADMIN",
                                    onClick = { newRole = "ADMIN" },
                                    label = { Text("Admin (Akses Penuh)") }
                                )
                            }
                        }

                        if (newRole != "ADMIN") {
                            item {
                                Text("Tanggung Jawab Departemen:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = "Pilih satu atau beberapa departemen yang dapat dikelola:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    departments.forEach { dept ->
                                        val isSelected = selectedDeptIds.contains(dept.id)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isSelected) selectedDeptIds.remove(dept.id)
                                                else selectedDeptIds.add(dept.id)
                                            },
                                            label = { Text(dept.name) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isAddingNew = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("BATAL")
                        }

                        Button(
                            onClick = {
                                if (newName.isBlank() || newEmail.isBlank() || newPassword.isBlank()) {
                                    errorMessage = "Semua kolom wajib diisi"
                                    return@Button
                                }
                                if (newPassword.length < 6) {
                                    errorMessage = "Kata sandi minimal 6 karakter"
                                    return@Button
                                }
                                val deptNames = departments.filter { selectedDeptIds.contains(it.id) }.map { it.name }
                                isSubmitting = true
                                errorMessage = null
                                onCreateEmployee(
                                    newName.trim(),
                                    newEmail.trim(),
                                    newPassword,
                                    newRole,
                                    selectedDeptIds.toList(),
                                    deptNames
                                ) { success, msg ->
                                    isSubmitting = false
                                    if (success) {
                                        isAddingNew = false
                                    } else {
                                        errorMessage = msg
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isSubmitting) "Menyimpan..." else "SIMPAN")
                        }
                    }
                } else if (editingUser != null) {
                    // EDIT USER FORM
                    val u = editingUser!!
                    var editRole by remember(u) { mutableStateOf(u.role) }
                    var editActive by remember(u) { mutableStateOf(u.isActive) }
                    val editDeptIds = remember(u) { mutableStateListOf(*u.assignedDepartmentIds.toTypedArray()) }
                    var isEditingSubmitting by remember { mutableStateOf(false) }

                    Text(
                        text = "Ubah Akses: ${u.fullName}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text("Email: ${u.email}", style = MaterialTheme.typography.bodyMedium)
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Status Akun Aktif:")
                                Switch(checked = editActive, onCheckedChange = { editActive = it })
                            }
                        }

                        item {
                            Text("Peran (Role):", style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = editRole == "KARYAWAN",
                                    onClick = { editRole = "KARYAWAN" },
                                    label = { Text("Karyawan") }
                                )
                                FilterChip(
                                    selected = editRole == "ADMIN",
                                    onClick = { editRole = "ADMIN" },
                                    label = { Text("Admin (Akses Penuh)") }
                                )
                            }
                        }

                        if (editRole != "ADMIN") {
                            item {
                                Text("Tanggung Jawab Departemen:", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    departments.forEach { dept ->
                                        val isSelected = editDeptIds.contains(dept.id)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isSelected) editDeptIds.remove(dept.id)
                                                else editDeptIds.add(dept.id)
                                            },
                                            label = { Text(dept.name) },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingUser = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("BATAL")
                        }

                        Button(
                            onClick = {
                                val deptNames = departments.filter { editDeptIds.contains(it.id) }.map { it.name }
                                val updated = u.copy(
                                    role = editRole,
                                    isActive = editActive,
                                    assignedDepartmentIds = if (editRole == "ADMIN") emptyList() else editDeptIds.toList(),
                                    assignedDepartmentNames = if (editRole == "ADMIN") listOf("Semua Departemen") else deptNames
                                )
                                isEditingSubmitting = true
                                onUpdateEmployee(updated) { success, _ ->
                                    isEditingSubmitting = false
                                    if (success) editingUser = null
                                }
                            },
                            enabled = !isEditingSubmitting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isEditingSubmitting) "Menyimpan..." else "SIMPAN PERUBAHAN")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserItemCard(
    user: AppUser,
    onEditClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (user.isAdmin) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (user.isAdmin) Icons.Default.Shield else Icons.Default.Badge,
                    contentDescription = null,
                    tint = if (user.isAdmin) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.fullName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (user.isAdmin) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = user.role,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (user.isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    if (!user.isActive) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "NONAKTIF",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = user.email,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Tanggung Jawab: ${user.departmentDisplay}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(onClick = onEditClick) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Karyawan", modifier = Modifier.size(18.dp))
            }
        }
    }
}
