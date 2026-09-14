package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.CloudConnectionStatus
import com.example.data.model.AppUser

@Composable
fun CloudStatusBar(
    cloudStatus: CloudConnectionStatus,
    currentUser: AppUser?,
    onOpenLogs: () -> Unit,
    onOpenUserManagement: () -> Unit,
    onSwitchAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Cloud Status & User Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSwitchAccount() }
            ) {
                // Cloud Status Dot / Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            when (cloudStatus) {
                                CloudConnectionStatus.CONNECTED -> Color(0xFFE8F5E9)
                                CloudConnectionStatus.SYNCING -> Color(0xFFE3F2FD)
                                CloudConnectionStatus.OFFLINE -> Color(0xFFECEFF1)
                                CloudConnectionStatus.NOT_CONFIGURED -> Color(0xFFFFF3E0)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when (cloudStatus) {
                        CloudConnectionStatus.CONNECTED -> {
                            Icon(
                                Icons.Default.CloudDone,
                                contentDescription = "Cloud Aktif",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        CloudConnectionStatus.SYNCING -> {
                            CircularProgressIndicator(
                                color = Color(0xFF1976D2),
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        CloudConnectionStatus.OFFLINE -> {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = Color(0xFF78909C),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        CloudConnectionStatus.NOT_CONFIGURED -> {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = "Sinkronisasi...",
                                tint = Color(0xFFEF6C00),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentUser?.fullName ?: "Tamu (Belum Login)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (currentUser != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (currentUser.isAdmin) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = currentUser.role,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentUser.isAdmin) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (currentUser != null) {
                            if (currentUser.isAdmin) "Hak Akses: Seluruh Departemen"
                            else "Tanggung Jawab: ${currentUser.departmentDisplay}"
                        } else "Ketuk untuk masuk akun",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Right: Action buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Activity Log Button
                IconButton(
                    onClick = onOpenLogs,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Log Cloud",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Admin Manage Users Button
                if (currentUser?.isAdmin == true) {
                    IconButton(
                        onClick = onOpenUserManagement,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ManageAccounts,
                            contentDescription = "Kelola Karyawan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Switch / Logout Button
                IconButton(
                    onClick = onSwitchAccount,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (currentUser != null) Icons.Default.Logout else Icons.Default.Person,
                        contentDescription = "Ganti Akun",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
