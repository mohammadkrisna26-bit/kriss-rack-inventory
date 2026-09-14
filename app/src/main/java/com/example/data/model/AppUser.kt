package com.example.data.model

data class AppUser(
    val id: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "KARYAWAN", // "ADMIN" or "KARYAWAN"
    val assignedDepartmentIds: List<Long> = emptyList(),
    val assignedDepartmentNames: List<String> = emptyList(),
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isAdmin: Boolean get() = role.equals("ADMIN", ignoreCase = true)

    val departmentDisplay: String
        get() {
            if (isAdmin) return "Semua Departemen (Admin)"
            if (assignedDepartmentNames.isEmpty()) return "Belum Ditugaskan"
            return assignedDepartmentNames.joinToString(", ")
        }

    fun canManageDepartment(deptId: Long): Boolean {
        if (!isActive) return false
        if (isAdmin) return true
        return assignedDepartmentIds.contains(deptId)
    }
}
