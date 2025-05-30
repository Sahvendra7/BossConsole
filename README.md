This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop, Server.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that's common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple's CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you're sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* `/server` is for the Ktor server application.

* `/shared` is for the code that will be shared between all targets in the project.
  The most important subfolder is `commonMain`. If preferred, you can add code to the platform-specific folders here too.


Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [GitHub](https://github.com/JetBrains/compose-multiplatform/issues).

You can open the web application by running the `:composeApp:wasmJsBrowserDevelopmentRun` Gradle task.

# JxBrowser Configuration

## License Key Configuration

The JxBrowser license key can be configured in several ways (in order of precedence):

### 1. Environment Variable (Recommended for Production)
```bash
export JXBROWSER_LICENSE_KEY="your-license-key-here"
```

### 2. System Property
```bash
java -Djxbrowser.license.key="your-license-key-here" -jar your-app.jar
```

### 3. Local Properties File (Recommended for Development)
Add to `local.properties` in the project root:
```properties
jxbrowser.license.key=your-license-key-here
```

**Note:** The `local.properties` file is already in `.gitignore` and should never be committed to version control.

### 4. Gradle Build Configuration
You can also inject the license key at build time through Gradle:

```kotlin
// In build.gradle.kts
tasks.withType<JavaExec> {
    systemProperty("jxbrowser.license.key", findProperty("jxbrowser.license.key") ?: "")
}
```

Then run with:
```bash
./gradlew run -Pjxbrowser.license.key="your-license-key-here"
```

## Security Best Practices

1. **Never commit license keys to version control**
2. **Use environment variables or secure vaults in production**
3. **Remove the hardcoded fallback key before deploying to production**
4. **Consider using a secrets management service for enterprise deployments**

## Other Configuration Options

- `jxbrowser.default.url`: Set the default URL (default: "https://www.risalabs.ai")

## Example local.properties
```properties
# JxBrowser configuration
jxbrowser.license.key=YOUR_LICENSE_KEY_HERE
jxbrowser.default.url=https://www.example.com
``` 