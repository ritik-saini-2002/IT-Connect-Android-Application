package com.example.ritik_2.data.model

object Permissions {
    fun forRole(role: String): List<String> = when (role) {
        "Administrator" -> listOf(
            "create_user","delete_user","modify_user","view_all_users",
            "manage_roles","view_analytics","system_settings","manage_companies",
            "access_all_data","export_data","manage_permissions","access_admin_panel",
            "submit_complaints","view_all_complaints","resolve_complaints"
        )
        "Manager"   -> listOf("view_team_users","modify_team_user","view_team_analytics",
            "assign_projects","approve_requests","view_reports",
            "submit_complaints","view_department_complaints","resolve_complaints")
        "HR"        -> listOf("view_all_users","modify_user","view_hr_analytics",
            "manage_employees","access_personal_data","generate_reports",
            "submit_complaints","view_all_complaints","resolve_complaints")
        "Team Lead" -> listOf("view_team_users","assign_tasks","view_team_performance",
            "approve_leave","submit_complaints","view_team_complaints")
        "Employee"  -> listOf("view_profile","edit_profile","view_assigned_projects",
            "submit_reports","submit_complaints","view_own_complaints")
        "Intern"    -> listOf("view_profile","edit_basic_profile",
            "view_assigned_tasks","submit_complaints")
        else        -> listOf("view_profile")
    }

    val ALL_ROLES = listOf("Administrator","Manager","HR","Team Lead","Employee","Intern")
}