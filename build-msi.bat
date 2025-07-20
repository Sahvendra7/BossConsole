@echo off
echo ========================================
echo       BOSS Simple MSI Builder
echo ========================================
echo.

:: Check Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java 17+ required
    pause
    exit /b 1
)

:: Clean first to avoid issues
echo [INFO] Cleaning previous builds...
call gradlew.bat clean

if errorlevel 1 (
    echo [ERROR] Clean failed
    pause
    exit /b 1
)

:: Build MSI with minimal configuration
echo [INFO] Building MSI installer...
call gradlew.bat :composeApp:packageMsi

if errorlevel 1 (
    echo [ERROR] MSI build failed
    echo.
    echo Trying alternative approach...
    call gradlew.bat :composeApp:createDistributable
    
    if errorlevel 1 (
        echo [ERROR] All builds failed
        pause
        exit /b 1
    )
    
    echo [INFO] Created distributable package instead
    pause
    exit /b 0
)

:: Find and copy MSI
for /r "composeApp\build" %%f in (*.msi) do (
    copy "%%f" "%~dp0BOSS-Simple-Installer.msi" >nul
    echo [SUCCESS] Created: BOSS-Simple-Installer.msi
    echo.
    echo ✅ MSI installer ready!
    echo 📦 File: BOSS-Simple-Installer.msi
    echo 🛠️  Simplified configuration to avoid installation errors
    echo.
    goto :done
)

echo [WARNING] MSI not found, checking for other distributables...
if exist "composeApp\build\compose\binaries\main" (
    echo [INFO] Found build artifacts in compose\binaries\main
    dir "composeApp\build\compose\binaries\main" /s
)

:done
pause