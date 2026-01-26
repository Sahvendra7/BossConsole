package ai.rever.boss.plugin.panel.adminrolemanagement

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Shield

/**
 * Panel info for Admin Role Management
 *
 * This panel allows administrators to:
 * - View all users in the system
 * - Assign roles to users
 * - Remove roles from users
 * - Delete users
 *
 * Access Control:
 * - Only accessible to users with 'admin' role
 * - RLS policies enforce server-side authorization
 */
object AdminRoleManagementInfo : PanelInfo {
    override val id = PanelId("admin-role-management", 22)
    override val displayName = "Admin: Roles"
    override val icon = FeatherIcons.Shield
    override val defaultSlotPosition = right.top.bottom
}
