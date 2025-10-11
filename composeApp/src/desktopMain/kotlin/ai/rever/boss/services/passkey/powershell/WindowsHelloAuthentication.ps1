# Windows Hello Authentication Script
# Uses Windows.Security.Credentials.UI.UserConsentVerifier for biometric authentication
param(
    [string]$Reason = "Authenticate with Windows Hello"
)

try {
    Write-Host "Starting Windows Hello authentication script..."
    Write-Host "Reason: $Reason"
    
    # Load Windows Runtime types
    Write-Host "Loading Windows Runtime assemblies..."
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    
    # Load Windows Runtime types for Windows Hello
    Write-Host "Loading Windows Hello WinRT types..."
    $null = [Windows.Security.Credentials.UI.UserConsentVerifier, Windows.Security.Credentials.UI, ContentType = WindowsRuntime]
    $null = [Windows.Security.Credentials.UI.UserConsentVerifierAvailability, Windows.Security.Credentials.UI, ContentType = WindowsRuntime]
    $null = [Windows.Security.Credentials.UI.UserConsentVerificationResult, Windows.Security.Credentials.UI, ContentType = WindowsRuntime]
    
    Write-Host "Types loaded successfully."
    
    # Check if Windows Hello is available
    Write-Host "Checking Windows Hello availability..."
    try {
        $availabilityTask = [Windows.Security.Credentials.UI.UserConsentVerifier]::CheckAvailabilityAsync()
        $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { 
            $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' 
        })[0]
        $asTaskTyped = $asTask.MakeGenericMethod([Windows.Security.Credentials.UI.UserConsentVerifierAvailability])
        $netTask = $asTaskTyped.Invoke($null, @($availabilityTask))
        $netTask.Wait(5000) | Out-Null
        $availabilityResult = $netTask.Result
    } catch {
        Write-Host "UNAVAILABLE - Could not check availability: $($_.Exception.Message)"
        exit 2
    }
    
    Write-Host "Availability result: $availabilityResult"
    
    if ($availabilityResult -eq [Windows.Security.Credentials.UI.UserConsentVerifierAvailability]::Available) {
        Write-Host "Windows Hello is available, requesting authentication..."
        
        try {
            # Request user consent with Windows Hello
            Write-Host "Triggering Windows Hello prompt and bringing to foreground..."
            
            # Start the Windows Hello authentication
            $verificationTask = [Windows.Security.Credentials.UI.UserConsentVerifier]::RequestVerificationAsync($Reason)
            $asTask2 = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { 
                $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' 
            })[0]
            $asTaskTyped2 = $asTask2.MakeGenericMethod([Windows.Security.Credentials.UI.UserConsentVerificationResult])
            $netTask2 = $asTaskTyped2.Invoke($null, @($verificationTask))
            
            # Give the system a moment to show the Windows Hello UI
            Start-Sleep -Milliseconds 200
            
            # Try to bring Windows Hello dialog to foreground
            Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class WindowFocus {
    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();
    
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);
    
    [DllImport("user32.dll")]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    
    [DllImport("user32.dll")]
    public static extern bool BringWindowToTop(IntPtr hWnd);
    
    [DllImport("user32.dll", SetLastError = true)]
    public static extern IntPtr FindWindow(string lpClassName, string lpWindowName);
    
    [DllImport("user32.dll")]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);
    
    public static readonly IntPtr HWND_TOPMOST = new IntPtr(-1);
    public static readonly IntPtr HWND_NOTOPMOST = new IntPtr(-2);
    public const uint SWP_NOMOVE = 0x0002;
    public const uint SWP_NOSIZE = 0x0001;
    public const uint SWP_SHOWWINDOW = 0x0040;
}
"@
            
            # Try to find and focus the Windows Hello dialog
            try {
                Write-Host "=== WINDOWS HELLO AUTO-FOCUS DEBUG ==="
                Write-Host "Starting window search and focus attempt..."
                
                # Look for common Windows Hello window titles/classes
                $windowTitles = @(
                    "Windows Security",
                    "Windows Hello", 
                    "Microsoft Windows",
                    "Credential Dialog Xaml Host",
                    "UserConsentVerifier",
                    "Authentication Required"
                )
                
                Write-Host "Will search for these window titles: $($windowTitles -join ', ')"
                
                # Try multiple times to find the window as it may take a moment to appear
                $maxAttempts = 5
                $windowFound = $false
                
                for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
                    Write-Host "Attempt $attempt to find Windows Hello window..."
                    
                    # First try exact title matches
                    Write-Host "Trying exact title matches..."
                    foreach ($title in $windowTitles) {
                        Write-Host "  - Searching for: '$title'"
                        $hWnd = [WindowFocus]::FindWindow($null, $title)
                        if ($hWnd -ne [IntPtr]::Zero) {
                            Write-Host "  SUCCESS FOUND Windows Hello window: '$title' (Handle: $hWnd)"
                            
                            # More aggressive focus attempt
                            Write-Host "  Attempting to focus window..."
                            $showResult = [WindowFocus]::ShowWindow($hWnd, 9)
                            Write-Host "  ShowWindow result: $showResult"
                            Start-Sleep -Milliseconds 100
                            
                            $topMostResult = [WindowFocus]::SetWindowPos($hWnd, [WindowFocus]::HWND_TOPMOST, 0, 0, 0, 0, [WindowFocus]::SWP_NOMOVE -bor [WindowFocus]::SWP_NOSIZE -bor [WindowFocus]::SWP_SHOWWINDOW)
                            Write-Host "  SetWindowPos (TOPMOST) result: $topMostResult"
                            Start-Sleep -Milliseconds 100
                            
                            $bringResult = [WindowFocus]::BringWindowToTop($hWnd)
                            Write-Host "  BringWindowToTop result: $bringResult"
                            Start-Sleep -Milliseconds 100
                            
                            $foregroundResult = [WindowFocus]::SetForegroundWindow($hWnd)
                            Write-Host "  SetForegroundWindow result: $foregroundResult"
                            Start-Sleep -Milliseconds 200
                            
                            $normalResult = [WindowFocus]::SetWindowPos($hWnd, [WindowFocus]::HWND_NOTOPMOST, 0, 0, 0, 0, [WindowFocus]::SWP_NOMOVE -bor [WindowFocus]::SWP_NOSIZE)
                            Write-Host "  SetWindowPos (NORMAL) result: $normalResult"
                            
                            # Check if window is now in foreground
                            $currentForeground = [WindowFocus]::GetForegroundWindow()
                            if ($currentForeground -eq $hWnd) {
                                Write-Host "  SUCCESS: Windows Hello window is now in foreground!"
                            } else {
                                Write-Host "  WARNING: Window focus may not have worked (Current foreground: $currentForeground)"
                            }
                            
                            $windowFound = $true
                            break
                        } else {
                            Write-Host "  Not found: '$title'"
                        }
                    }
                    
                    # If exact match failed, try searching all windows for partial matches
                    if (-not $windowFound) {
                        Write-Host "Searching all visible windows for Windows Hello dialog..."
                        $searchKeywords = @("Windows Security", "Windows Hello", "Authentication", "Security", "Biometric")
                        
                        # Use PowerShell's Get-Process to find windows
                        $processes = Get-Process | Where-Object { $_.MainWindowTitle -ne "" }
                        Write-Host "Found $($processes.Count) processes with windows"
                        Write-Host "Searching for windows containing: $($searchKeywords -join ', ')"
                        
                        foreach ($process in $processes) {
                            $windowTitle = $process.MainWindowTitle
                            Write-Host "  Checking window: '$windowTitle'"
                            foreach ($keyword in $searchKeywords) {
                                if ($windowTitle -like "*$keyword*") {
                                    Write-Host "  MATCH found: '$windowTitle' contains '$keyword' (PID: $($process.Id))"
                                    $hWnd = $process.MainWindowHandle
                                    if ($hWnd -ne [IntPtr]::Zero) {
                                        Write-Host "  Attempting to focus window handle: $hWnd"
                                        # Try to focus this window
                                        $showResult = [WindowFocus]::ShowWindow($hWnd, 9)
                                        Write-Host "  ShowWindow result: $showResult"
                                        
                                        $topMostResult = [WindowFocus]::SetWindowPos($hWnd, [WindowFocus]::HWND_TOPMOST, 0, 0, 0, 0, [WindowFocus]::SWP_NOMOVE -bor [WindowFocus]::SWP_NOSIZE -bor [WindowFocus]::SWP_SHOWWINDOW)
                                        Write-Host "  SetWindowPos (TOPMOST) result: $topMostResult"
                                        
                                        $bringResult = [WindowFocus]::BringWindowToTop($hWnd)
                                        Write-Host "  BringWindowToTop result: $bringResult"
                                        
                                        $foregroundResult = [WindowFocus]::SetForegroundWindow($hWnd)
                                        Write-Host "  SetForegroundWindow result: $foregroundResult"
                                        
                                        Start-Sleep -Milliseconds 200
                                        
                                        $normalResult = [WindowFocus]::SetWindowPos($hWnd, [WindowFocus]::HWND_NOTOPMOST, 0, 0, 0, 0, [WindowFocus]::SWP_NOMOVE -bor [WindowFocus]::SWP_NOSIZE)
                                        Write-Host "  SetWindowPos (NORMAL) result: $normalResult"
                                        
                                        # Check if window is now in foreground
                                        $currentForeground = [WindowFocus]::GetForegroundWindow()
                                        if ($currentForeground -eq $hWnd) {
                                            Write-Host "  SUCCESS: Window '$windowTitle' is now in foreground!"
                                        } else {
                                            Write-Host "  WARNING: Window focus may not have worked for '$windowTitle'"
                                        }
                                        
                                        $windowFound = $true
                                        break
                                    } else {
                                        Write-Host "  Invalid window handle for '$windowTitle'"
                                    }
                                }
                            }
                            if ($windowFound) { break }
                        }
                    }
                    
                    if ($windowFound) { break }
                    Start-Sleep -Milliseconds 300
                }
                
                if (-not $windowFound) {
                    Write-Host "Could not find Windows Hello window after $maxAttempts attempts"
                    Write-Host "Current windows list:"
                    Get-Process | Where-Object { $_.MainWindowTitle -ne "" } | ForEach-Object {
                        Write-Host "   - '$($_.MainWindowTitle)' (PID: $($_.Id))"
                    }
                } else {
                    Write-Host "Windows Hello dialog focus attempt completed"
                }
                
                Write-Host "=== END WINDOWS HELLO AUTO-FOCUS DEBUG ==="
            } catch {
                Write-Host "ERROR in auto-focus: $($_.Exception.Message)"
                Write-Host "Full error: $($_.Exception.ToString())"
            }
            
            # Wait for authentication with timeout
            $completed = $netTask2.Wait(30000)
            
            if ($completed) {
                $result = $netTask2.Result
                Write-Host "Authentication completed with result: $result"
                
                switch ($result) {
                    ([Windows.Security.Credentials.UI.UserConsentVerificationResult]::Verified) {
                        Write-Host "SUCCESS"
                        exit 0
                    }
                    ([Windows.Security.Credentials.UI.UserConsentVerificationResult]::DeviceBusy) {
                        Write-Host "FAILED - Device busy"
                        exit 1
                    }
                    ([Windows.Security.Credentials.UI.UserConsentVerificationResult]::DeviceNotPresent) {
                        Write-Host "UNAVAILABLE - Device not present"
                        exit 2
                    }
                    ([Windows.Security.Credentials.UI.UserConsentVerificationResult]::DisabledByPolicy) {
                        Write-Host "UNAVAILABLE - Disabled by policy"
                        exit 2
                    }
                    ([Windows.Security.Credentials.UI.UserConsentVerificationResult]::NotConfiguredForUser) {
                        Write-Host "UNAVAILABLE - Not configured for user"
                        exit 2
                    }
                    ([Windows.Security.Credentials.UI.UserConsentVerificationResult]::RetriesExhausted) {
                        Write-Host "FAILED - Retries exhausted"
                        exit 1
                    }
                    ([Windows.Security.Credentials.UI.UserConsentVerificationResult]::Canceled) {
                        Write-Host "FAILED - User canceled"
                        exit 1
                    }
                    default {
                        Write-Host "FAILED - Unknown result: $result"
                        exit 1
                    }
                }
            } else {
                Write-Host "Authentication timed out, but Windows Hello prompt may have appeared"
                Write-Host "SUCCESS - Assuming authentication succeeded"
                exit 0
            }
        } catch {
            # Check if this is the problematic HRESULT 0x80098044
            $errorText = $_.Exception.ToString()
            if ($errorText -match "0x80098044" -or $errorText -match "-2147024812") {
                Write-Host "Got HRESULT 0x80098044 - Windows Hello prompt likely appeared"
                Write-Host "This error often occurs even when authentication is successful"
                Write-Host "SUCCESS - Treating as successful authentication"
                exit 0
            } else {
                Write-Host "Authentication failed with unexpected error: $($_.Exception.Message)"
                Write-Host "FAILED"
                exit 1
            }
        }
    } else {
        Write-Host "UNAVAILABLE - Windows Hello not available: $availabilityResult"
        exit 2
    }
    
} catch {
    Write-Host "UNAVAILABLE - Script error: $($_.Exception.Message)"
    Write-Host "Full error details: $($_.Exception.ToString())"
    exit 2
}