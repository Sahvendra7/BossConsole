package ai.rever.boss.plugin.panel.rolecreation

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.PlusCircle

/**
 * Panel info for Role Creation
 *
 * This panel allows administrators to:
 * - Create new roles dynamically (table-based, not ENUM)
 * - Create new permissions dynamically (table-based, not ENUM)
 * - Assign permissions to roles
 * - Remove permissions from roles
 * - View all roles and their permissions
 *
 * Access Control:
 * - Only accessible to users with 'admin' role
 * - RLS policies enforce server-side authorization
 */
object RoleCreationInfo : PanelInfo {
    override val id = PanelId("role-creation", 23)
    override val displayName = "Admin: Create Roles"
    override val icon = FeatherIcons.PlusCircle
    override val defaultSlotPosition = right.top.bottom
}
