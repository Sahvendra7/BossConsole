// BossWebAuthn.swift
//
// Native macOS platform-authenticator bridge for the fluck browser.
//
// Compiled to `libBossWebAuthn.dylib` (see the `compileWebAuthnDylib` Gradle task)
// and loaded IN-PROCESS by the JVM via JNA (see MacPlatformPasskeys.kt). It must run
// in-process because:
//   1. ASAuthorizationWebBrowserPublicKeyCredentialManager checks the
//      `com.apple.developer.web-browser.public-key-credential` entitlement against
//      the *calling process's* code signature — a spawned `swift` subprocess would
//      carry no entitlement.
//   2. ASAuthorizationController anchors its system UI to the app's own NSWindow.
//
// These browser-passkey APIs are Swift-only (ASPublicKeyCredentialClientData is a
// Swift struct with no Objective-C bridge), which is why this is Swift rather than
// the Objective-C used elsewhere. C-ABI entry points are exported with @_cdecl.
//
// All request/response payloads are JSON UTF-8 C strings. Binary WebAuthn fields
// are base64url (no padding), matching WebAuthnScripts.kt on the page side.
//
// Requires macOS 13.5+ (the browser client-data ceremony APIs —
// ASPublicKeyCredentialClientData / createCredentialAssertionRequest(clientData:)
// — are 13.5+). On older systems every entry point degrades to "unavailable"/error
// so the Kotlin layer falls back to Chromium's own WebAuthn (USB security keys),
// preserving prior behavior.

import Foundation
import AuthenticationServices
import AppKit

// MARK: - base64url helpers

private func b64urlDecode(_ s: String) -> Data? {
    var str = s.replacingOccurrences(of: "-", with: "+")
        .replacingOccurrences(of: "_", with: "/")
    while str.count % 4 != 0 { str += "=" }
    return Data(base64Encoded: str)
}

private func b64urlEncode(_ d: Data) -> String {
    return d.base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
}

private func jsonString(_ obj: [String: Any]) -> String {
    guard let data = try? JSONSerialization.data(withJSONObject: obj),
          let s = String(data: data, encoding: .utf8) else {
        return "{\"ok\":false,\"error\":\"UnknownError\",\"message\":\"serialize failed\"}"
    }
    return s
}

private func parse(_ cstr: UnsafePointer<CChar>?) -> [String: Any]? {
    guard let cstr = cstr else { return nil }
    let s = String(cString: cstr)
    guard let data = s.data(using: .utf8),
          let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        return nil
    }
    return obj
}

private func out(_ s: String) -> UnsafeMutablePointer<CChar>? {
    return strdup(s)
}

// MARK: - ceremony bookkeeping (delegates must outlive the async call)

@available(macOS 13.5, *)
private final class Ceremony: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    let sem = DispatchSemaphore(value: 0)
    var controller: ASAuthorizationController?

    // `result` is settled exactly once by whichever of {delegate callback, timeout}
    // fires first; `settled`/`lock` guard against the race between them.
    private let lock = NSLock()
    private var settled = false
    private var _result = "{\"ok\":false,\"error\":\"NotAllowedError\",\"message\":\"no result\"}"

    // ASAuthorizationController.delegate is weak, so we retain live ceremonies here.
    private static let liveLock = NSLock()
    private static var live = Set<Ceremony>()
    static func retain(_ c: Ceremony) { liveLock.lock(); live.insert(c); liveLock.unlock() }
    static func release(_ c: Ceremony) { liveLock.lock(); live.remove(c); liveLock.unlock() }

    /// Settle the ceremony's RESULT once. Safe to call from the delegate (main thread)
    /// or the timeout (background thread); later calls are ignored. Does NOT drop the
    /// retain — the Ceremony (and its controller) must stay alive until the system is
    /// definitively done with the auth sheet, i.e. until a delegate callback fires.
    func complete(_ json: String) {
        lock.lock()
        if settled { lock.unlock(); return }
        settled = true
        _result = json
        lock.unlock()
        sem.signal()
    }

    /// Delegate-path completion: settle (no-op if the timeout settled first) and drop
    /// the retain — the system has finished with the controller.
    private func finish(_ json: String) {
        complete(json)
        Ceremony.release(self)
    }

    func snapshotResult() -> String {
        lock.lock(); defer { lock.unlock() }
        return _result
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        return NSApp.keyWindow ?? NSApp.mainWindow ?? NSApp.windows.first ?? NSWindow()
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithAuthorization authorization: ASAuthorization) {
        if let a = authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialAssertion {
            finish(jsonString([
                "ok": true,
                "credentialId": b64urlEncode(a.credentialID),
                "clientDataJSON": b64urlEncode(a.rawClientDataJSON),
                "authenticatorData": b64urlEncode(a.rawAuthenticatorData),
                "signature": b64urlEncode(a.signature),
                "userHandle": a.userID.isEmpty ? NSNull() : b64urlEncode(a.userID)
            ]))
        } else if let r = authorization.credential as? ASAuthorizationPlatformPublicKeyCredentialRegistration {
            var obj: [String: Any] = [
                "ok": true,
                "credentialId": b64urlEncode(r.credentialID),
                "clientDataJSON": b64urlEncode(r.rawClientDataJSON)
            ]
            if let att = r.rawAttestationObject { obj["attestationObject"] = b64urlEncode(att) }
            finish(jsonString(obj))
        } else {
            finish(jsonString(["ok": false, "error": "NotAllowedError",
                               "message": "unexpected credential type"]))
        }
    }

    func authorizationController(controller: ASAuthorizationController,
                                 didCompleteWithError error: Error) {
        let ns = error as NSError
        // A registration blocked by excludedCredentials must surface as InvalidStateError
        // (WebAuthn), which RPs special-case as "already registered". Apple reports this
        // as ASAuthorizationError.matchedExcludedCredential (code 1006, macOS 15+); all
        // other failures — including user cancellation — map to NotAllowedError per spec.
        let name = (ns.domain == ASAuthorizationError.errorDomain && ns.code == 1006)
            ? "InvalidStateError" : "NotAllowedError"
        finish(jsonString(["ok": false, "error": name, "message": ns.localizedDescription]))
    }
}

/// After the JS side has been settled by a timeout, tear the ceremony down without
/// deallocating a controller the system sheet may still be presenting: cancel on the
/// main queue (the delegate then fires canceled → drops the retain), with a delayed
/// forced release as a backstop in case no callback ever arrives.
@available(macOS 13.5, *)
private func abandonAfterTimeout(_ ceremony: Ceremony) {
    ceremony.complete(jsonString(["ok": false, "error": "NotAllowedError",
                                  "message": "Passkey request timed out."]))
    DispatchQueue.main.async { ceremony.controller?.cancel() }
    DispatchQueue.global().asyncAfter(deadline: .now() + 60) { Ceremony.release(ceremony) }
}

/// Clamp a page-supplied WebAuthn timeout (ms) to a sane wait window (seconds).
/// Floor of 15s per the WebAuthn-recommended client range — tiny RP timeouts must not
/// abandon a sheet the user is actively interacting with.
private func waitSeconds(from req: [String: Any]) -> Double {
    let ms = (req["timeout"] as? NSNumber)?.doubleValue ?? 0
    return ms > 0 ? min(max(ms / 1000.0, 15), 120) : 120
}

// MARK: - availability + authorization

@available(macOS 13.3, *)
private func manager() -> ASAuthorizationWebBrowserPublicKeyCredentialManager {
    return ASAuthorizationWebBrowserPublicKeyCredentialManager()
}

/// Whether the managed web-browser passkey entitlement is actually PROVISIONED for the
/// running process — i.e. an `embedded.provisionprofile` that grants it is present.
///
/// This is the load-bearing no-regression guard. Listing the entitlement key in
/// BOSS.entitlements (and `codesign --entitlements`) is NOT sufficient for a Developer
/// ID app: the OS only honors the managed capability when a matching provisioning
/// profile granting it is embedded at `Contents/embedded.provisionprofile`. Without one
/// (current releases, dev/unsigned runs), `authorizationStateForPlatformCredentials`
/// could report `.notDetermined`, which would otherwise bake `isUVPAA()=true` and steer
/// relying parties into a platform path that dead-ends. Gating availability on this
/// probe makes an unprovisioned build a strict no-op regardless of the reported state.
///
/// Computed once — the embedded profile can't change during the process lifetime.
private let webBrowserPasskeyEntitlementProvisioned: Bool = {
    let key = "com.apple.developer.web-browser.public-key-credential"
    let profileURL = Bundle.main.bundleURL.appendingPathComponent("Contents/embedded.provisionprofile")
    guard let data = try? Data(contentsOf: profileURL) else { return false }
    // A .provisionprofile is a CMS (PKCS#7) blob wrapping an XML plist; extract the plist
    // payload by range rather than pulling in CMSDecoder. isoLatin1 maps every byte, so
    // the binary CMS envelope never fails the String conversion.
    guard let raw = String(data: data, encoding: .isoLatin1),
          let start = raw.range(of: "<?xml"),
          let end = raw.range(of: "</plist>"),
          let plistData = String(raw[start.lowerBound..<end.upperBound]).data(using: .isoLatin1),
          let plist = try? PropertyListSerialization.propertyList(from: plistData, options: [], format: nil) as? [String: Any],
          let entitlements = plist["Entitlements"] as? [String: Any] else {
        return false
    }
    return entitlements[key] != nil
}()

// Threading note: `authorizationStateForPlatformCredentials` is a state-property read
// (not UI) and manager construction is a plain init, so we read them on the CALLING
// thread. We deliberately do NOT force these onto the main queue: boss_webauthn_available
// is reached synchronously from browser creation (WebAuthnBridge.install), and a
// blocking main-queue hop from an off-main creation thread risks a cross-thread
// deadlock. Only the UI-presenting consent request (requestAuthorizationForPublicKeyCredentials)
// is dispatched to the main queue, via async (never a blocking sync). [device-QA: confirm]

@_cdecl("boss_webauthn_available")
public func boss_webauthn_available() -> Int32 {
    // The browser client-data ceremony APIs require macOS 13.5+; gate the whole
    // feature on that so isUVPAA() only reports true when we can actually complete.
    guard #available(macOS 13.5, *) else { return 0 }
    // The managed entitlement must actually be provisioned (profile embedded), else
    // the OS won't honor it — report unavailable so isUVPAA() stays false and we fall
    // back, independent of whatever authorization state an unprovisioned build reports.
    guard webBrowserPasskeyEntitlementProvisioned else { return 0 }
    // Available unless the OS has affirmatively denied passkey access to this app.
    return manager().authorizationStateForPlatformCredentials == .denied ? 0 : 1
}

/// Blocking; MUST be called off the main thread (it awaits a main-queue callback).
/// Returns {"state":"authorized|denied|notDetermined"}.
@_cdecl("boss_webauthn_authorize")
public func boss_webauthn_authorize() -> UnsafeMutablePointer<CChar>? {
    guard #available(macOS 13.5, *) else { return out(jsonString(["state": "denied"])) }
    return out(jsonString(["state": authorizeState()]))
}

@available(macOS 13.5, *)
private func authorizeState() -> String {
    switch manager().authorizationStateForPlatformCredentials {
    case .authorized: return "authorized"
    case .denied: return "denied"
    default: break
    }
    // Consent request can present UI → issue it on the main queue; await off-main.
    let sem = DispatchSemaphore(value: 0)
    let stateLock = NSLock()
    var state = "notDetermined"
    DispatchQueue.main.async {
        manager().requestAuthorizationForPublicKeyCredentials { newState in
            stateLock.lock()
            switch newState {
            case .authorized: state = "authorized"
            case .denied: state = "denied"
            default: state = "notDetermined"
            }
            stateLock.unlock()
            sem.signal()
        }
    }
    // Bounded wait: an unbounded wait would hang this executor thread while the caller
    // (WebAuthnBridge) holds the app-wide single-flight permit, wedging every future
    // ceremony until restart. 60s is ample for a yes/no consent sheet; on timeout
    // return notDetermined so the ceremony declines and the permit is released.
    if sem.wait(timeout: .now() + 60) == .timedOut { return "notDetermined" }
    stateLock.lock(); defer { stateLock.unlock() }
    return state
}

// MARK: - ceremonies

/// Blocking assertion (navigator.credentials.get). Must be called off the main thread.
@_cdecl("boss_webauthn_get")
public func boss_webauthn_get(_ reqJson: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>? {
    guard #available(macOS 13.5, *) else {
        return out(jsonString(["ok": false, "error": "NotSupportedError", "message": "macOS < 13.5"]))
    }
    guard let req = parse(reqJson),
          let rpId = req["rpId"] as? String,
          let challengeB64 = req["challenge"] as? String,
          let challenge = b64urlDecode(challengeB64),
          let origin = req["origin"] as? String else {
        return out(jsonString(["ok": false, "error": "SyntaxError", "message": "bad request"]))
    }

    let ceremony = Ceremony()
    Ceremony.retain(ceremony)
    DispatchQueue.main.async {
        let clientData = ASPublicKeyCredentialClientData(challenge: challenge, origin: origin)
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        let request = provider.createCredentialAssertionRequest(clientData: clientData)

        if let allow = req["allowCredentials"] as? [String], !allow.isEmpty {
            request.allowedCredentials = allow.compactMap { b64urlDecode($0) }
                .map { ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: $0) }
        }
        switch req["userVerification"] as? String {
        case "required": request.userVerificationPreference = .required
        case "discouraged": request.userVerificationPreference = .discouraged
        default: request.userVerificationPreference = .preferred
        }

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = ceremony
        controller.presentationContextProvider = ceremony
        ceremony.controller = controller
        controller.performRequests()
    }
    if ceremony.sem.wait(timeout: .now() + waitSeconds(from: req)) == .timedOut {
        abandonAfterTimeout(ceremony)
    }
    return out(ceremony.snapshotResult())
}

/// Blocking registration (navigator.credentials.create). Must be called off the main thread.
@_cdecl("boss_webauthn_create")
public func boss_webauthn_create(_ reqJson: UnsafePointer<CChar>?) -> UnsafeMutablePointer<CChar>? {
    guard #available(macOS 13.5, *) else {
        return out(jsonString(["ok": false, "error": "NotSupportedError", "message": "macOS < 13.5"]))
    }
    guard let req = parse(reqJson),
          let rpId = req["rpId"] as? String,
          let challengeB64 = req["challenge"] as? String,
          let challenge = b64urlDecode(challengeB64),
          let origin = req["origin"] as? String,
          let user = req["user"] as? [String: Any],
          let userIdB64 = user["id"] as? String,
          let userId = b64urlDecode(userIdB64),
          let userName = user["name"] as? String else {
        return out(jsonString(["ok": false, "error": "SyntaxError", "message": "bad request"]))
    }

    let ceremony = Ceremony()
    Ceremony.retain(ceremony)
    DispatchQueue.main.async {
        let clientData = ASPublicKeyCredentialClientData(challenge: challenge, origin: origin)
        let provider = ASAuthorizationPlatformPublicKeyCredentialProvider(relyingPartyIdentifier: rpId)
        let request = provider.createCredentialRegistrationRequest(
            clientData: clientData, name: userName, userID: userId)

        // Duplicate-passkey prevention: honor the RP's excludeCredentials. On the
        // browser path this property comes from the
        // ASAuthorizationWebBrowserPlatformPublicKeyCredentialRegistrationRequest
        // protocol (macOS 13.5+), so it's enforced on every version we support.
        if let exclude = req["excludeCredentials"] as? [String], !exclude.isEmpty {
            request.excludedCredentials = exclude.compactMap { b64urlDecode($0) }
                .map { ASAuthorizationPlatformPublicKeyCredentialDescriptor(credentialID: $0) }
        }

        switch req["userVerification"] as? String {
        case "required": request.userVerificationPreference = .required
        case "discouraged": request.userVerificationPreference = .discouraged
        default: request.userVerificationPreference = .preferred
        }

        // Known limitations (documented alongside the ES256-only routing in
        // WebAuthnScripts): Apple's platform provider always mints a DISCOVERABLE
        // (resident) credential, so the RP's residentKey/requireResidentKey preference
        // is not honored — an RP asking for "discouraged" still gets a resident key
        // (spec-allowed, just not preferred). The RP's attestation preference is also
        // not forwarded (platform passkeys are effectively attestation "none"). Neither
        // matters for the platform-passkey RPs this targets; revisit if that changes.

        let controller = ASAuthorizationController(authorizationRequests: [request])
        controller.delegate = ceremony
        controller.presentationContextProvider = ceremony
        ceremony.controller = controller
        controller.performRequests()
    }
    if ceremony.sem.wait(timeout: .now() + waitSeconds(from: req)) == .timedOut {
        abandonAfterTimeout(ceremony)
    }
    return out(ceremony.snapshotResult())
}

@_cdecl("boss_webauthn_free")
public func boss_webauthn_free(_ p: UnsafeMutablePointer<CChar>?) {
    if let p = p { free(p) }
}
