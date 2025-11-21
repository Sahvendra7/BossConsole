# Threading and Coroutines Best Practices

**CRITICAL**: Proper threading is essential for responsive UI and preventing freezes. This document covers threading best practices learned from production issues.

## Core Rules

1. **Never block the UI thread** - No `Thread.sleep()`, blocking I/O, or long computations
2. **Use appropriate dispatchers** - `Dispatchers.Main` for UI, `Dispatchers.IO` for I/O, `Dispatchers.Default` for CPU work
3. **Use coroutines for resource cleanup** - Always dispose resources on background threads
4. **Use `delay()` not `Thread.sleep()`** - Non-blocking delays in coroutines
5. **Document delays** - Explain why delays are needed (e.g., RPC queue draining)

## Dispatcher Usage Guide

| Operation | Dispatcher | Pattern |
|-----------|-----------|---------|
| UI update | `Dispatchers.Main` | Direct call or `withContext` |
| File I/O | `Dispatchers.IO` | `CoroutineScope(Dispatchers.IO).launch {}` |
| Network | `Dispatchers.IO` | `CoroutineScope(Dispatchers.IO).launch {}` |
| Database | `Dispatchers.IO` | `CoroutineScope(Dispatchers.IO).launch {}` |
| Browser cleanup | `Dispatchers.IO` | `CoroutineScope(Dispatchers.IO).launch {}` |
| Heavy compute | `Dispatchers.Default` | `CoroutineScope(Dispatchers.Default).launch {}` |
| Delay | Any | `delay(ms)` never `Thread.sleep(ms)` |

## UI Thread Rules

### NEVER do these on the UI thread:
- ❌ `Thread.sleep()` - Blocks UI thread, causes freezes
- ❌ Blocking I/O operations (file read/write, network calls)
- ❌ Long computations (>16ms drops frames, >100ms feels laggy)
- ❌ Resource cleanup that takes time (browser disposal, database cleanup)

### ONLY do these on the UI thread:
- ✅ Quick UI updates and state changes
- ✅ Composable recomposition
- ✅ Layout and drawing operations
- ✅ Fast synchronous operations (<16ms)

## Common Patterns

### Background Resource Disposal

✅ **CORRECT**:
```kotlin
fun dispose() {
    if (!isDisposed) {
        isDisposed = true
        // Dispose on background thread to avoid blocking UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
                browserViewState?.let { disposeBrowserViewState(it) }

                // Non-blocking coroutine delay
                delay(50)  // Allow RPC queue to drain

                browser?.let { disposeBrowser(it) }
            } catch (e: Exception) {
                println("Error disposing browser: ${e.message}")
            }
        }
    }
}
```

❌ **INCORRECT**:
```kotlin
fun dispose() {
    if (!isDisposed) {
        isDisposed = true
        try {
            browserViewState?.let { disposeBrowserViewState(it) }

            // ❌ BLOCKS UI THREAD - causes 50ms freeze!
            Thread.sleep(50)

            browser?.let { disposeBrowser(it) }
        } catch (e: Exception) {
            println("Error disposing browser: ${e.message}")
        }
    }
}
```

## Disposal and Cleanup Best Practices

1. **Use coroutines for resource cleanup**:
   ```kotlin
   CoroutineScope(Dispatchers.IO).launch {
       // Heavy cleanup work here
   }
   ```

2. **Use `delay()` instead of `Thread.sleep()`**:
   ```kotlin
   delay(50)              // ✅ Non-blocking
   Thread.sleep(50)       // ❌ Blocks thread
   ```

3. **Handle exceptions in cleanup**:
   ```kotlin
   try {
       cleanup()
   } catch (e: Exception) {
       println("Cleanup error: ${e.message}")
   }
   ```

4. **Document why delays are needed**:
   ```kotlin
   // Wait 50ms for JxBrowser's RPC queue to drain
   // This prevents race condition in SharedMemoryTransport
   delay(50)
   ```

5. **Don't wait for async cleanup to complete**:
   - `dispose()` should return immediately
   - Let cleanup happen in background
   - UI stays responsive

## JxBrowser-Specific Patterns

JxBrowser requires careful threading due to internal RPC (Remote Procedure Call) architecture.

### Browser Disposal Pattern

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    // Dispose BrowserViewState first
    browserViewState?.let { disposeBrowserViewState(it) }

    // Wait for RPC message queue to drain
    // Without this, browser.close() tears down RPC while messages are pending
    delay(50)

    // Now safe to close browser
    browser?.let { disposeBrowser(it) }
}
```

**Why the delay?**
- `browser.close()` immediately tears down RPC connections
- JxBrowser's `SharedMemoryTransport` may have queued RPC messages
- If RPC observer becomes null before messages process → NullPointerException
- 50ms delay allows queue to drain gracefully

**Reference:** See `Fluck.kt:336-357` for complete implementation

## Real-World Case Study: Fluck.kt Browser Disposal

### Problem (commit 31c6ea3)

```kotlin
fun dispose() {
    browserViewState?.let { disposeBrowserViewState(it) }
    Thread.sleep(50)  // ❌ BLOCKED UI THREAD
    browser?.let { disposeBrowser(it) }
}
```

**Impact:**
- 50ms UI freeze every time a tab closed
- Multiple rapid tab closures = multiple 50ms freezes
- Poor user experience, especially on slower systems
- User perception: "App feels sluggish"

### Solution (commit 40bf0b2)

```kotlin
fun dispose() {
    if (!isDisposed) {
        isDisposed = true
        CoroutineScope(Dispatchers.IO).launch {  // ✅ Background thread
            try {
                browserViewState?.let { disposeBrowserViewState(it) }
                delay(50)  // ✅ Non-blocking coroutine delay
                browser?.let { disposeBrowser(it) }
            } catch (e: Exception) {
                println("Error disposing browser: ${e.message}")
            }
        }
    }
}
```

**Results:**
- ✅ UI thread never blocks during tab closure
- ✅ `dispose()` returns immediately (microseconds)
- ✅ Browser cleanup happens asynchronously
- ✅ Smooth, responsive UI even when closing many tabs

**Lesson:** Always profile UI responsiveness when adding cleanup code.

## Testing for Threading Issues

### Manual Testing
1. Close multiple tabs rapidly - should be instantaneous, no lag
2. Monitor UI responsiveness during heavy operations
3. Watch for frame drops or stuttering
4. Test on slower hardware if possible

### Code Review Checklist
- [ ] No `Thread.sleep()` calls in UI-accessible code
- [ ] No blocking I/O on main thread
- [ ] Resource cleanup uses `Dispatchers.IO`
- [ ] Delays use `delay()` not `Thread.sleep()`
- [ ] Long operations happen in background coroutines

### Search Patterns
```bash
# Find potential threading issues
git grep "Thread.sleep"        # Should be rare/zero
git grep "\.sleep("            # Catch variations
git grep "blockingGet"         # Blocking calls
```

## IntelliJ IDEA Inspections

Enable these inspections to catch threading issues:

1. **"Inappropriate blocking method call"**
   - Detects blocking calls on coroutine dispatchers
   - Catches `Thread.sleep()` in suspend functions

2. **"Possibly blocking call in non-blocking context"**
   - Warns about blocking I/O in coroutines
   - Suggests `Dispatchers.IO` for I/O operations

3. **"Slow operations on UI thread"** (Android)
   - While this is for Android, the principle applies
   - Watch for file I/O, network, and long computations

## Remember

**When in doubt, move work off the UI thread. It's easier to optimize later than to debug UI freezes.**
