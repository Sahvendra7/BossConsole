# Dynamic Plugins Migration - Full Plan

## Goal
Convert ALL sidebar panel plugins to dynamic plugins (except Plugin Manager).

## Status

### Already Completed (Phase 1)
1. **Console** - `boss-plugin-console` - DONE
2. **Performance** - `boss-plugin-performance` - DONE
3. **Downloads** - `boss-plugin-downloads` - DONE

### In Progress
4. **Bookmarks** - `boss-plugin-bookmarks` - Repo created, needs implementation

### Remaining Plugins (15 total)
5. plugin-panel-admin-role-management → `boss-plugin-admin-role-management`
6. plugin-panel-codebase → `boss-plugin-codebase`
7. plugin-panel-fluck → `boss-plugin-fluck`
8. plugin-panel-git-log → `boss-plugin-git-log`
9. plugin-panel-git-status → `boss-plugin-git-status`
10. plugin-panel-llmrpa → `boss-plugin-llmrpa`
11. plugin-panel-role-creation → `boss-plugin-role-creation`
12. plugin-panel-rpaengine → `boss-plugin-rpaengine`
13. plugin-panel-rparecorder → `boss-plugin-rparecorder`
14. plugin-panel-run-configurations → `boss-plugin-run-configurations`
15. plugin-panel-secret-manager → `boss-plugin-secret-manager`
16. plugin-panel-terminal → `boss-plugin-terminal`
17. plugin-panel-topofmind → `boss-plugin-topofmind`
18. plugin-panel-user-secret-list → `boss-plugin-user-secret-list`

### Excluded
- **Plugin Manager** - Stays bundled (per requirement)

## Implementation Order
Process in batches for efficiency:

### Batch 1: Simple UI Plugins (no complex host dependencies)
- Bookmarks (finish)
- Codebase
- Git Log
- Git Status
- Top of Mind

### Batch 2: RPA/Automation Plugins
- LLMRPA
- RPA Engine
- RPA Recorder
- Run Configurations

### Batch 3: Security/Admin Plugins
- Admin Role Management
- Role Creation
- Secret Manager
- User Secret List

### Batch 4: Complex Plugins
- Terminal (may need special host services)
- Fluck (ChatGPT integration - may already exist)

## For Each Plugin
1. Create GitHub repo: `gh repo create risa-labs-inc/boss-plugin-<name> --public`
2. Clone to ~/Development/boss_plugin/<name>
3. Create project structure:
   - build.gradle.kts
   - settings.gradle.kts
   - gradle wrapper
   - plugin.json manifest
   - DynamicPlugin entry point
   - Adapted source files
4. Build JAR: `./gradlew buildPluginJar`
5. Commit and push

## Dependencies to Document
- Window-scoped services (BookmarkDataProvider, WorkspaceDataProvider)
- Terminal integration (BossTerm)
- RPA engine integration
- Secret vault access
- Admin permissions

## Output
- All JARs in ~/Development/boss_plugin/<name>/build/libs/
- Optionally copy to ~/.boss/plugins/ for testing
