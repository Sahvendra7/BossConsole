# BossEditor

BossEditor is an external dependency providing IDE-like code editor features for the main BOSS application.

**Repository**: [risa-labs-inc/BossEditor](https://github.com/risa-labs-inc/BossEditor)
**Maven Artifact**: `com.risaboss:bosseditor-compose-desktop`
**Dependency**: Declared in `composeApp/build.gradle.kts`

## LSP Integration

Language Server Protocol support for multi-language editing.

**Capabilities**:
- Code completion
- Go-to-definition
- Find references
- Diagnostics (errors, warnings)
- Semantic token highlighting

## PSI Code Analysis

Kotlin navigation using kotlin-compiler-embeddable for native Kotlin support without external LSP.

**Important Notes**:
- `parseKotlinFile(fileName, content)` returns non-nullable `KtFile`
- `parseFile(file)` returns nullable `KtFile?`
- K1 API deprecation warnings are intentional (awaiting K2 Analysis API stability)
- `composeApp/` also depends on `kotlin-compiler-embeddable` directly for its own PSI code

## Advanced Editor Features

**Visual Enhancements**:
- Code minimap with visual navigation
- Fixed header display during scrolling (sticky scroll)
- Contextual breadcrumb navigation
- Bracket pair colorization (rainbow brackets)
- Git blame line annotations
- Type and parameter hints (inlay hints)
- Highlight symbol occurrences

## Editor Settings

**Configuration**:
- Settings persisted to `~/.boss/editor-settings.json` (or `~/.boss_debug/` in dev mode)
- LSP settings persisted to `~/.boss/lsp-settings.json`

**Available Settings**:
- Font family and size
- Line numbers, minimap, breadcrumbs toggles
- Tab size and spaces vs tabs
- Word wrap
- Rainbow brackets
- Sticky scroll
- Inlay hints
