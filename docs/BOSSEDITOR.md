# BossEditor Module

The `bosseditor/` module is a standalone code editor providing IDE-like features for the main BOSS application.

## LSP Integration

Language Server Protocol support for multi-language editing.

**Key Files**:
- `DesktopLspClient.kt` - LSP transport and client communication
- `LspConfiguration.kt` - Language server configuration
- Providers for completion, navigation, diagnostics, semantic tokens
- `LspSettings.kt` - Settings UI

**Capabilities**:
- Code completion
- Go-to-definition
- Find references
- Diagnostics (errors, warnings)
- Semantic token highlighting

## PSI Code Analysis

Kotlin navigation using kotlin-compiler-embeddable for native Kotlin support without external LSP.

**Key Files**:
- `PSIBootstrap.kt` - Initializes IntelliJ Platform infrastructure
- `PSIThreadBridge.kt` - Thread-safe PSI access with ReadAction wrappers
- `NavigationService.kt` - Go-to-definition implementation
- `ReferenceService.kt` - Find references functionality
- `SemanticHighlighter.kt` - Semantic syntax highlighting
- `ProjectIndexer.kt` - Symbol indexing for navigation

**Important Notes**:
- `parseKotlinFile(fileName, content)` returns non-nullable `KtFile`
- `parseFile(file)` returns nullable `KtFile?`
- K1 API deprecation warnings are intentional (awaiting K2 Analysis API stability)

## Advanced Editor Features

**Visual Enhancements**:
- `Minimap.kt` - Code minimap with visual navigation
- `StickyScroll.kt` - Fixed header display during scrolling
- `Breadcrumbs.kt` - Contextual breadcrumb navigation
- `RainbowBrackets.kt` - Bracket pair colorization
- `GitBlame.kt` - Git blame line annotations
- `InlayHints.kt` - Type and parameter hints
- `MarkOccurrences.kt` - Highlight symbol occurrences

## Editor Settings

**Configuration**:
- `EditorSettings.kt` - Comprehensive configuration options
- `EditorSettingsManager.kt` - Settings persistence (`~/.boss/editor-settings.json`)

**Available Settings**:
- Font family and size
- Line numbers, minimap, breadcrumbs toggles
- Tab size and spaces vs tabs
- Word wrap
- Rainbow brackets
- Sticky scroll
- Inlay hints
