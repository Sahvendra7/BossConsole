# BOSS Release Notes

This directory contains detailed release notes for each version of BOSS.

## Quick Links

- [Latest Release](https://github.com/risa-labs-inc/BossConsole-Releases/releases/latest)
- [All Releases](https://github.com/risa-labs-inc/BossConsole-Releases/releases)

## Release Index

<!-- RELEASE_INDEX_START -->
| Version | Date | Summary |
|---------|------|---------|
| [v8.15.9](v8.15.9.md) | 2026-01-09 | Tab management improvements: fixed drag reorder positioning, added auto-scroll, and resolved macOS Chromium permissions |
| [v8.12.12](v8.12.12.md) | 2024-10 | Secret management with user-level viewing, role-based sharing with audit trails, and database migration consolidation |
| [v8.12.11](v8.12.11.md) | 2024-10 | Fixed bottom bar to correctly display and update with active tabs, and cleaned up top bar by hiding unimplemented components |
| [v8.12.16](v8.12.16.md) | 2024-10 | Fixed critical macOS update installation failure and hardened update script security against command injection vulnerabilities |
| [v8.12.15](v8.12.15.md) | 2024-10 | User feedback for expired and already-used magic links, eliminating silent authentication failures |
| [v8.12.14](v8.12.14.md) | 2024-10 | Fixed embedded browser for Settings passkey registration and added auto-click capability |
| [v8.12.13](v8.12.13.md) | 2024-10 | Improved passkey selection UX with better credential identifiers and fixed error message cross-contamination between authentication flows |
| [v8.12.19](v8.12.19.md) | 2024-10 | Cross-platform default browser support allowing BOSS to be set as the system's default web browser with automatic URL handling |
| [v8.12.18](v8.12.18.md) | 2025-10 | Multiple window support with independent instances, browser-standard keyboard shortcuts, and event-driven focus management |
| [v8.12.17](v8.12.17.md) | 2025-10 | Optional automatic update checking with configurable intervals and improved time display accuracy |
| [v8.12.23](v8.12.23.md) | 2024-10 | Critical browser stability fixes: resolved JxBrowser SIGSEGV crashes and auto-window creation bug on macOS |
| [v8.12.22](v8.12.22.md) | 2024-12 | Terminal rendering improvements and deep link handler fixes: resolved blank display bug, empty line rendering, and external URL handling when BOSS is set as default browser |
| [v8.12.21](v8.12.21.md) | 2024-10 | Secret Auto-Fill Integration with Fluck browser, enabling seamless credential management with real-time synchronization across all components |
| [v8.12.24](v8.12.24.md) | 2024-12 | Native macOS MenuBar integration for improved platform-native look and feel |
| [v8.12.26](v8.12.26.md) | 2025-12 | Interactive home screen with actionable tip cards and URL event bus for proper external link handling |
| [v8.12.25](v8.12.25.md) | 2025-12 | Plugin lifecycle system with reset functionality and tab cloning fix |
| [v8.12.29](v8.12.29.md) | 2025-12 | Enhanced form autofill with React 16+ compatibility and proper event dispatching |
| [v8.12.28](v8.12.28.md) | 2025-12 | Network reliability improvements with separate HttpClient configurations for API calls and downloads with optimized timeouts |
| [v8.12.27](v8.12.27.md) | 2025-12 | Fixed hyperlinks opening in detached OS windows by intercepting popups to open as BOSS tabs instead |
| [v8.12.31](v8.12.31.md) | 2025-12 | Browser tab state management and favicon loading reliability improvements with URL bar isolation and enhanced caching |
| [v8.12.30](v8.12.30.md) | 2025-12 | Console panel improvements with complete log history, Fluck-style UI, multi-line text selection, and Terminal emoji support |
| [v8.13.0](v8.13.0.md) | 2025-11 | Complete Bookmarks Plugin with favorites, collections, search, multi-workspace targeting, and comprehensive context menus |
| [v8.13.2](v8.13.2.md) | 2025-11 | Intelligent popup handling for OAuth and mail links with smart detection and proper window management |
| [v8.13.1](v8.13.1.md) | 2025-11 | Fixed tab bar layout to span full width of main panel |
| [v8.13.10](v8.13.10.md) | 2025-11 | CI/CD reliability improvements with fixed duplicate tag creation in release workflow |
| [v8.13.9](v8.13.9.md) | 2025-11 | CI/CD reliability improvements: prevented race conditions in tagging and consolidated workflow to eliminate duplicate runs |
| [v8.13.8](v8.13.8.md) | 2025-11 | Maintenance release with no functional changes |
| [v8.13.7](v8.13.7.md) | 2025-11 | Fixed critical macOS notarization issue that prevented proper app signing and distribution |
| [v8.13.6](v8.13.6.md) | 2025-11 | Comprehensive CLI functionality with Homebrew installation support, smart argument detection, and automatic version management |
| [v8.13.5](v8.13.5.md) | 2025-11 | Fixed keyboard shortcuts triggering while typing in browser input fields |
| [v8.13.4](v8.13.4.md) | 2025-11 | New keyboard shortcuts management tab in Settings for comprehensive view and control of all application shortcuts |
| [v8.13.3](v8.13.3.md) | 2025-11 | Version selection and downgrade support, Top of Mind plugin enhancements with nested sections and favicons, and dynamic tab bar improvements |
| [v8.13.18](v8.13.18.md) | 2024-12 | Fixed browser URL bar regression where Enter key navigated to autocomplete suggestion instead of typed text |
| [v8.13.17](v8.13.17.md) | 2024-12 | Build system stability fixes, browser URL bar usability improvements, and macOS build dependency resolution |
| [v8.13.14](v8.13.14.md) | 2024-12 | Browser usability enhancements with favicons in quick switcher, faster link clicks, URL bar text fixes, and authentication infrastructure cleanup |
| [v8.13.12](v8.13.12.md) | 2024-12 | Focus Mode with keyboard shortcuts for distraction-free work by hiding UI panels |
| [v8.13.11](v8.13.11.md) | 2024-12 | Authentication stability, adaptive window sizing for laptops, standardized search bars, and CLI improvements |
| [v8.13.32](v8.13.32.md) | 2025-12 | Performance monitoring system, IntelliJ-style run configurations with smart detection, and project persistence across sessions |
| [v8.13.36](v8.13.36.md) | 2025-12 | Drag-and-drop support for tabs: reorder within windows and move between windows, with improved lifecycle management |
| [v8.13.35](v8.13.35.md) | 2025-12 | Fixed critical context menu actions bug in tabs caused by trailing lambda parameter ordering issue |
| [v8.13.34](v8.13.34.md) | 2025-12 | Browser stability improvements with automatic recovery, configurable settings, and terminal project folder support |
| [v8.13.33](v8.13.33.md) | 2025-12 | Critical browser stability improvements: Reset Browser feature, thread safety fixes, and streamlined Linux update authentication |
| [v8.14.10](v8.14.10.md) | 2025-12 | Improved new user experience with better project selection workflows and updated plugin icons |
| [v8.14.9](v8.14.9.md) | 2025-12 | New Dashboard feature with recent items and templates, HTTP client memory leak fix, and performance optimizations |
| [v8.14.8](v8.14.8.md) | 2025-12 | BossEditor Phase 19 polish, terminal file hyperlink line:column navigation, browser URL recovery fixes, and focus mode default changes |
| [v8.14.7](v8.14.7.md) | 2026-01 | Maintenance release with no functional changes |
| [v8.14.6](v8.14.6.md) | 2025-12 | Offline screen with retry functionality on startup and terminal integration updates |
| [v8.14.5](v8.14.5.md) | 2025-12 | BossEditor improvements: navigation feedback, large document performance optimization, enhanced error handling, and comprehensive test coverage |
| [v8.14.4](v8.14.4.md) | 2025-12 | BossEditor enabled by default with complete custom editor implementation: LSP integration, code completion, multi-caret editing, minimap, rainbow brackets, and 40+ syntax lexers |
| [v8.14.3](v8.14.3.md) | 2025-12 | Enhanced terminal link handling with new "Existing Split" option for opening links in specific split locations |
| [v8.14.2](v8.14.2.md) | 2025-12 | Browser stability improvements: BOSS-styled JavaScript dialogs, file:// URL support, and fixed mouse button handling |
| [v8.14.1](v8.14.1.md) | 2025-12 | Terminal link click prompts with preferences, Reset Terminal feature, enhanced New Tab Dialog with file browsing, and browser error handling improvements |
| [v8.14.0](v8.14.0.md) | 2025-12 | Runner terminal integration with sidebar panel, unified execution service, and play/stop controls |
| [keylocker-tools](vkeylocker-tools.md) | 2025-07-28 | CI/CD workflow improvements and repository cleanup |
| [v8.15.8](v8.15.8.md) | 2026-01-07 | Browser navigation stability, Linux ARM64 support, UI improvements, and 139 build warnings resolved |
| [v8.15.7](v8.15.7.md) | 2026-01-06 | Fixed sidebar panel positioning for bottom slots |
| [v8.15.6](v8.15.6.md) | 2026-01-06 | AI integration: BossTerm AI Assistant with Welcome Wizard and terminal context menu for AI coding assistants |
| [v8.15.5](v8.15.5.md) | 2026-01-06 | Project creation wizard with templates, improved deleted project handling, and terminal AI assistant context menu |
| [v8.15.4](v8.15.4.md) | 2026-01-05 | Browser URL recovery revert, dashboard improvements, and text editor dependency updates |
| [v8.15.3](v8.15.3.md) | 2026-01-05 | Fixed browser URL recovery crash caused by stale object reference |
| [v8.15.2](v8.15.2.md) | 2026-01-05 | Per-window project isolation and panel mapping fixes |
| [v8.15.1](v8.15.1.md) | 2026-01-05 | Added {claudeContinueFlag} placeholder for conditional Claude Code continuation flags in workspace configurations |
| [v8.15.0](v8.15.0.md) | 2026-01-02 | BOSS-branded Chromium support and complete LSP integration for BossEditor |
| [v8.13.31](v8.13.31.md) | 2025-12 | Terminal links open in BOSS browser, Linux browser session persistence fix, and enhanced Linux package installation |
| [v8.13.30](v8.13.30.md) | 2025-12 | Terminal tab title sync, Linux dock icon fix, configurable title bar, and macOS menu hang fix |
| [v8.13.29](v8.13.29.md) | 2025-12 | Fixed missing packaging dependencies for ARM64 Linux builds and updated BossTerm to 1.0.48 |
| [v8.13.28](v8.13.28.md) | 2025-12 | Terminal tab management with state persistence, background color fixes, and dependency updates (Kotlin 2.3.0, Logback 1.5.23) |
| [v8.13.27](v8.13.27.md) | 2025-12 | JxBrowser update to 8.15.0 with version consistency fixes and banner styling improvements |
| [v8.13.26](v8.13.26.md) | 2025-12 | Terminal migration to BossTerm library, ARM64 Linux support, native binary signing for macOS, and window maximized by default |
| [v8.13.23](v8.13.23.md) | 2024-12 | Dependency updates including Clikt 5.0, JUnit 6.0, Kotlin 2.3.0-RC, and Ktor 3.3.2 |
| [v8.13.22](v8.13.22.md) | 2024-12 | Window management enhancements with title bar double-click maximize and panel minimum size constraints |
| [v8.13.21](v8.13.21.md) | 2024-12 | Fixed critical JxBrowser 'closed object' crash with thread-safe disposal mechanism |
| [v8.13.20](v8.13.20.md) | 2024-12 | Download management with auto-closing redirect tabs and Kotlin compiler deprecation warnings resolved |
| [v8.13.19](v8.13.19.md) | 2024-12 | Gradle configuration cache compatibility enabled for 10-50% faster builds with proper task input/output tracking |
| [v8.12.34](v8.12.34.md) | 2025-12 | Workspace management and tab navigation improvements: fresh workspace for new windows, auto-scroll to active tab, and auto-show New Tab Dialog |
| [v8.12.33](v8.12.33.md) | 2025-12 | Browser reliability and performance improvements with favicon caching and JxBrowser initialization retry mechanism |
| [v8.12.32](v8.12.32.md) | 2025-12 | Browser usability improvements with enhanced context menu, better URL bar interaction, and improved window focus handling |
| [v8.12.20](v8.12.20.md) | 2024-12 | Fixed critical version downgrade bug with build system improvements and CI/CD verification |
<!-- RELEASE_INDEX_END -->

## Commit Conventions

| Type | Description |
|------|-------------|
| `feat:` | New feature |
| `fix:` | Bug fix |
| `refactor:` | Code refactoring |
| `perf:` | Performance improvement |
| `docs:` | Documentation |
| `deps:` | Dependency updates |

Scopes: `browser`, `terminal`, `tabs`, `panel`, `workflow`, etc.

---

*Release notes are automatically generated using [Claude Code](https://claude.ai/claude-code).*
