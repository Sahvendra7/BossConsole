# Dynamic Plugins Migration Plan

## Overview

Convert all sidebar plugins (except Plugin Manager) from statically bundled modules to externally loaded dynamic plugins, following the pattern established by the ChatGPT Fluck plugin.

---

## 1. Current State Analysis

### Sidebar Panel Plugins (17 to migrate)

| # | Plugin ID | Current Module | Complexity | Dependencies |
|---|-----------|----------------|------------|--------------|
| 1 | `bookmarks-panel` | plugin-panel-bookmarks | Medium | FaviconLoader, ContextMenu, DialogProvider |
| 2 | `ai.rever.boss.plugin.downloads` | plugin-panel-downloads | Medium | DownloadDataProvider |
| 3 | `codebase-panel` | plugin-panel-codebase | High | FileSystemProvider, CodeEditorLauncher |
| 4 | `terminal-panel` | plugin-panel-terminal | High | TerminalContentProvider, ComponentFactory |
| 5 | `ai.rever.boss.plugin.console` | plugin-panel-console | Low | None |
| 6 | `ai.rever.boss.plugin.performance` | plugin-panel-performance | Low | PerformanceDataProvider (static) |
| 7 | `ai.rever.boss.plugin.run-configurations` | plugin-panel-run-configurations | Medium | WindowContextProvider |
| 8 | `topofmind-panel` | plugin-panel-topofmind | Medium | TabCollectionProvider |
| 9 | `ai.rever.boss.plugin.git-status` | plugin-panel-git-status | Medium | GitDataProvider, WindowIdProvider |
| 10 | `ai.rever.boss.plugin.git-log` | plugin-panel-git-log | Medium | GitDataProvider |
| 11 | `ai.rever.boss.plugin.secret-manager` | plugin-panel-secret-manager | High | SecretDataProvider, UserManagementProvider |
| 12 | `ai.rever.boss.plugin.user-secret-list` | plugin-panel-user-secret-list | Medium | SecretDataProvider, SecretChangeEvents |
| 13 | `admin-role-management-panel` | plugin-panel-admin-role-management | High | UserManagementProvider, AuthDataProvider |
| 14 | `role-creation-panel` | plugin-panel-role-creation | High | RoleManagementProvider |
| 15 | `llmrpa-panel` | plugin-panel-llmrpa | High | ComponentFactory, RPA services |
| 16 | `rparecorder-panel` | plugin-panel-rparecorder | High | ComponentFactory, RPA services |
| 17 | `rpaengine-panel` | plugin-panel-rpaengine | High | ComponentFactory, RPA services |

### Already External
- `ai.rever.boss.plugin.chatgpt` (ChatGPT Fluck) - Reference implementation

### Excluded from Migration
- `ai.rever.boss.plugin-manager` (Plugin Manager) - Must remain bundled to manage other plugins

---

## 2. New Service APIs Required

To support dynamic plugins, we need to expose services through `PluginContext`:

### Required Service Interfaces (add to plugin-api)

```kotlin
// Already exists
interface BrowserService { ... }

// New services needed
interface DownloadService {
    fun getActiveDownloads(): StateFlow<List<DownloadInfo>>
    fun pauseDownload(id: String)
    fun resumeDownload(id: String)
    fun cancelDownload(id: String)
}

interface FileSystemService {
    fun getWorkspaceRoot(): String?
    fun listFiles(path: String): List<FileInfo>
    fun readFile(path: String): String
    fun watchDirectory(path: String, callback: (FileChangeEvent) -> Unit): Disposable
}

interface TerminalService {
    fun createTerminal(config: TerminalConfig): TerminalInstance
    fun getActiveTerminals(): List<TerminalInstance>
}

interface GitService {
    fun isGitRepository(path: String): Boolean
    fun getStatus(path: String): GitStatus
    fun getLog(path: String, limit: Int): List<GitCommit>
    fun stage(path: String, files: List<String>)
    fun commit(path: String, message: String)
}

interface SecretService {
    fun listSecrets(): List<SecretInfo>
    fun getSecret(name: String): String?
    fun setSecret(name: String, value: String)
    fun deleteSecret(name: String)
}

interface UserManagementService {
    fun getCurrentUser(): UserInfo?
    fun listUsers(): List<UserInfo>
    fun listRoles(): List<RoleInfo>
    fun assignRole(userId: String, roleId: String)
}

interface PerformanceService {
    fun getMetrics(): StateFlow<PerformanceMetrics>
    fun startProfiling()
    fun stopProfiling(): ProfilingResult
}

interface BookmarkService {
    fun getBookmarks(): StateFlow<List<Bookmark>>
    fun addBookmark(url: String, title: String)
    fun removeBookmark(id: String)
    fun updateBookmark(id: String, title: String?, url: String?)
}
```

---

## 3. GitHub Repos to Create

Each plugin will have its own repo under `risa-labs-inc`:

| # | Repo Name | Plugin ID | Priority |
|---|-----------|-----------|----------|
| 1 | `boss-plugin-bookmarks` | `ai.rever.boss.plugin.bookmarks` | Phase 1 |
| 2 | `boss-plugin-downloads` | `ai.rever.boss.plugin.downloads` | Phase 1 |
| 3 | `boss-plugin-console` | `ai.rever.boss.plugin.console` | Phase 1 |
| 4 | `boss-plugin-performance` | `ai.rever.boss.plugin.performance` | Phase 1 |
| 5 | `boss-plugin-codebase` | `ai.rever.boss.plugin.codebase` | Phase 2 |
| 6 | `boss-plugin-terminal` | `ai.rever.boss.plugin.terminal` | Phase 2 |
| 7 | `boss-plugin-run-configs` | `ai.rever.boss.plugin.run-configs` | Phase 2 |
| 8 | `boss-plugin-topofmind` | `ai.rever.boss.plugin.topofmind` | Phase 2 |
| 9 | `boss-plugin-git-status` | `ai.rever.boss.plugin.git-status` | Phase 2 |
| 10 | `boss-plugin-git-log` | `ai.rever.boss.plugin.git-log` | Phase 2 |
| 11 | `boss-plugin-secret-manager` | `ai.rever.boss.plugin.secret-manager` | Phase 3 |
| 12 | `boss-plugin-user-secrets` | `ai.rever.boss.plugin.user-secrets` | Phase 3 |
| 13 | `boss-plugin-admin-roles` | `ai.rever.boss.plugin.admin-roles` | Phase 3 |
| 14 | `boss-plugin-role-creation` | `ai.rever.boss.plugin.role-creation` | Phase 3 |
| 15 | `boss-plugin-llm-rpa` | `ai.rever.boss.plugin.llm-rpa` | Phase 4 |
| 16 | `boss-plugin-rpa-recorder` | `ai.rever.boss.plugin.rpa-recorder` | Phase 4 |
| 17 | `boss-plugin-rpa-engine` | `ai.rever.boss.plugin.rpa-engine` | Phase 4 |

---

## 4. Local Workspace Structure

```
~/Development/boss_plugins/
├── boss-plugin-bookmarks/
├── boss-plugin-downloads/
├── boss-plugin-console/
├── boss-plugin-performance/
├── boss-plugin-codebase/
├── boss-plugin-terminal/
├── boss-plugin-run-configs/
├── boss-plugin-topofmind/
├── boss-plugin-git-status/
├── boss-plugin-git-log/
├── boss-plugin-secret-manager/
├── boss-plugin-user-secrets/
├── boss-plugin-admin-roles/
├── boss-plugin-role-creation/
├── boss-plugin-llm-rpa/
├── boss-plugin-rpa-recorder/
└── boss-plugin-rpa-engine/
```

---

## 5. Plugin Template Structure

Each plugin repo will follow this structure:

```
boss-plugin-{name}/
├── .github/
│   └── workflows/
│       └── release.yml          # Auto-build on tag
├── .gitignore
├── README.md
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
└── src/
    └── main/
        ├── kotlin/
        │   └── ai/rever/boss/plugin/{name}/
        │       ├── {Name}Plugin.kt           # Main plugin class
        │       ├── {Name}Component.kt        # Panel component
        │       └── {Name}Info.kt             # Panel info
        └── resources/
            └── META-INF/
                └── boss-plugin/
                    └── plugin.json           # Manifest
```

---

## 6. BossConsole Changes

### Files to Modify

| File | Changes |
|------|---------|
| `composeApp/src/commonMain/kotlin/ai/rever/boss/components/plugin/DefaultPlugin.kt` | Remove static registrations for migrated plugins |
| `plugins/plugin-api/src/commonMain/kotlin/ai/rever/boss/plugin/api/PluginContext.kt` | Add new service accessors |
| `composeApp/src/desktopMain/kotlin/ai/rever/boss/components/plugin/DefaultPluginContext.kt` | Implement new service providers |
| `plugins/plugin-api/build.gradle.kts` | May need new dependencies |
| `build.gradle.kts` (root) | Remove bundled plugin modules from dependencies |
| `settings.gradle.kts` | Remove plugin module includes |

### DefaultPlugin.kt Changes

**Remove** (approximately 400+ lines):
- All static panel registrations except Plugin Manager
- Remove sandboxed context creation for each panel
- Remove auth-based dynamic registration (will be handled by plugin itself)
- Remove git-based conditional registration

**Keep**:
- Plugin Manager registration
- Dynamic plugin loading from `~/.boss/plugins/`
- Core infrastructure (sandboxManager, panelRegistry, tabRegistry)

### Estimated Lines Changed

| File | Lines Removed | Lines Added | Net |
|------|---------------|-------------|-----|
| DefaultPlugin.kt | ~600 | ~50 | -550 |
| PluginContext.kt | 0 | ~150 | +150 |
| DefaultPluginContext.kt | 0 | ~300 | +300 |
| Various build files | ~100 | 0 | -100 |
| **Total** | ~700 | ~500 | **-200** |

---

## 7. Implementation Phases

### Phase 1: Infrastructure & Simple Plugins (Week 1)
1. Add service interfaces to plugin-api
2. Implement services in DefaultPluginContext
3. Migrate simple plugins:
   - Console
   - Performance
   - Bookmarks
   - Downloads

### Phase 2: Core Functionality Plugins (Week 2)
1. Migrate medium-complexity plugins:
   - CodeBase
   - Terminal
   - Run Configurations
   - Top of Mind
   - Git Status
   - Git Log

### Phase 3: Admin/Security Plugins (Week 3)
1. Migrate auth-dependent plugins:
   - Secret Manager
   - User Secrets
   - Admin Roles
   - Role Creation

### Phase 4: RPA Plugins (Week 4)
1. Migrate RPA plugins (complex dependencies):
   - LLM RPA
   - RPA Recorder
   - RPA Engine

### Phase 5: Cleanup & Polish (Week 5)
1. Remove bundled plugin modules from BossConsole
2. Update documentation
3. Create plugin development guide
4. Publish all plugins to store

---

## 8. Build & Test Commands

### Create Plugin Repo
```bash
# Create repo
gh repo create risa-labs-inc/boss-plugin-{name} --public --description "BOSS {Name} Plugin"

# Clone locally
cd ~/Development/boss_plugins
git clone git@github.com:risa-labs-inc/boss-plugin-{name}.git

# Initialize from template
cp -r template/* boss-plugin-{name}/
cd boss-plugin-{name}
./gradlew build
```

### Build Plugin
```bash
cd ~/Development/boss_plugins/boss-plugin-{name}
./gradlew clean build
```

### Install for Testing
```bash
# Copy to BOSS plugins directory
cp build/libs/boss-plugin-{name}-*.jar ~/.boss/plugins/

# Or use publish script
./scripts/publish-plugin boss-plugin-{name}
```

### Run BossConsole
```bash
cd ~/Development/BossConsole
./gradlew run
```

### Verify Plugin Loaded
Check logs for:
```
INFO boss - [SYSTEM] DynamicPluginManager: Loading plugin | {pluginId=ai.rever.boss.plugin.{name}}
INFO boss - [SYSTEM] {Name}Plugin: Registration complete!
```

---

## 9. Rollback Plan

If issues arise during migration:

1. **Partial Rollback**: Keep both static and dynamic registration, controlled by feature flag
2. **Full Rollback**: Revert DefaultPlugin.kt changes via git
3. **Plugin Rollback**: Remove JAR from `~/.boss/plugins/` to disable individual plugins

### Feature Flag Implementation
```kotlin
// In DefaultPlugin.kt
val useDynamicPlugins = System.getProperty("boss.plugins.dynamic", "true").toBoolean()

if (!useDynamicPlugins) {
    // Register plugins statically (legacy mode)
    registerStaticPlugins()
}
// Dynamic plugins always load from ~/.boss/plugins/
```

---

## 10. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Service API breaks existing plugins | Medium | High | Comprehensive interface design, versioning |
| Performance degradation | Low | Medium | Lazy loading, parallel initialization |
| Plugin isolation issues | Medium | Medium | Sandbox testing, error boundaries |
| Missing dependencies at runtime | Medium | High | Strict dependency declaration, testing |
| Auth-based plugins security | Low | High | Careful permission checking in services |

---

## 11. Success Criteria

1. All 17 plugins load dynamically from `~/.boss/plugins/`
2. No functionality regression from static version
3. Plugin Manager can install/uninstall all plugins
4. Plugins can be updated independently
5. BossConsole bundle size reduced by removing plugin modules
6. Clean separation between core and plugins

---

## 12. Questions to Resolve

1. **Shared UI Components**: Should plugin-ui-core remain bundled or also become dynamic?
2. **Auth Flow**: How do plugins access auth state? Through service or events?
3. **Tab Plugins**: Should tab types (Fluck Tab, Code Editor Tab, Terminal Tab) also be dynamic?
4. **Versioning**: How to handle API version mismatches between host and plugins?
5. **Development Flow**: How to develop plugins alongside BossConsole changes?

---

## Approval

Please review this plan and provide feedback on:
- Phase prioritization
- Service API design
- Risk mitigation strategies
- Any plugins that should remain bundled

Once approved, I will begin implementation starting with Phase 1.
