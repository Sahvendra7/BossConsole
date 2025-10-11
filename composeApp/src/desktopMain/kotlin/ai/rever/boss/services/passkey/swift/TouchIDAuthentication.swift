import LocalAuthentication
import Foundation

/**
 * Touch ID / Face ID authentication using LocalAuthentication framework
 * Used by BOSS macOS client for biometric authentication
 * 
 * Usage: swift TouchIDAuthentication.swift "Your reason here"
 */

// Get the reason from command line argument or use default
let reason = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "Authenticate for BOSS"

let context = LAContext()
var error: NSError?

guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
    print("UNAVAILABLE")
    exit(1)
}

let semaphore = DispatchSemaphore(value: 0)
var authSuccess = false

context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, 
                     localizedReason: reason) { success, error in
    authSuccess = success
    if let error = error {
        print("ERROR: \(error.localizedDescription)")
    }
    semaphore.signal()
}

semaphore.wait()
print(authSuccess ? "SUCCESS" : "FAILED")