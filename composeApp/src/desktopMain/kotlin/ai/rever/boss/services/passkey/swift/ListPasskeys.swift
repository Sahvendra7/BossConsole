import Security
import Foundation

/**
 * List all BOSS passkeys stored in keychain
 * Used by BOSS macOS client for passkey management
 * 
 * Usage: swift ListPasskeys.swift
 */

func listBOSSPasskeys() -> [String] {
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: "ai.rever.boss.touchid",
        kSecReturnAttributes as String: true,
        kSecMatchLimit as String: kSecMatchLimitAll
    ]
    
    var items: CFTypeRef?
    let status = SecItemCopyMatching(query as CFDictionary, &items)
    
    guard status == errSecSuccess,
          let existingItems = items as? [[String: Any]] else {
        if status == errSecItemNotFound {
            print("No BOSS passkeys found in keychain")
            return []
        } else {
            print("Error querying keychain: \(status)")
            return []
        }
    }
    
    var credentialIds: [String] = []
    for item in existingItems {
        if let account = item[kSecAttrAccount as String] as? String,
           account.hasPrefix("BOSS_PASSKEY_") {
            let credentialId = String(account.dropFirst("BOSS_PASSKEY_".count))
            credentialIds.append(credentialId)
        }
    }
    
    return credentialIds
}

let passkeys = listBOSSPasskeys()
if passkeys.isEmpty {
    print("EMPTY")
} else {
    for passkey in passkeys {
        print("PASSKEY:\(passkey)")
    }
}