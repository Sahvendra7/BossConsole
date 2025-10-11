import Security
import Foundation

/**
 * Delete a passkey from macOS Keychain
 * Used by BOSS macOS client for passkey management
 * 
 * Usage: swift DeletePasskey.swift "credentialId"
 */

// Get the credential ID from command line argument
guard CommandLine.arguments.count > 1 else {
    print("ERROR: Missing credential ID argument")
    exit(1)
}

let credentialId = CommandLine.arguments[1]

func deletePasskeyFromKeychain(credentialId: String) -> Bool {
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrAccount as String: "BOSS_PASSKEY_\(credentialId)",
        kSecAttrService as String: "ai.rever.boss.touchid"
    ]
    
    let status = SecItemDelete(query as CFDictionary)
    
    if status == errSecSuccess {
        print("SUCCESS: Passkey deleted from keychain")
        return true
    } else if status == errSecItemNotFound {
        print("WARNING: Passkey not found in keychain (may already be deleted)")
        return true
    } else {
        print("ERROR: Failed to delete passkey from keychain, status: \(status)")
        return false
    }
}

let success = deletePasskeyFromKeychain(credentialId: credentialId)
print(success ? "SUCCESS" : "FAILED")