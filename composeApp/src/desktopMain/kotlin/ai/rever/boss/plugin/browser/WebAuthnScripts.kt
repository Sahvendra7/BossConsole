package ai.rever.boss.plugin.browser

/**
 * Page-side JavaScript shim that routes WebAuthn ceremonies to a native platform
 * authenticator (macOS iCloud Keychain passkeys via
 * ASAuthorizationWebBrowserPublicKeyCredentialManager).
 *
 * Why this exists: JxBrowser's embedded Chromium on macOS exposes no platform
 * authenticator — `navigator.credentials.get/create` for a platform passkey never
 * produces a Touch ID / iCloud Keychain prompt, and
 * `PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable()` resolves to
 * `false`. This shim wraps those APIs, hands the request to the host over the
 * injected `window.__bossWebAuthn` bridge ([WebAuthnBridge]), and reconstructs a
 * spec-shaped `PublicKeyCredential` from the native result.
 *
 * Fallback: if the host reports no native authenticator (non-macOS, OS too old,
 * entitlement not granted, or the user declined access) the shim calls the original
 * Chromium implementation, so USB security keys keep working exactly as before.
 *
 * The shim is injected at document-start on the top-level frame via `InjectJsCallback`
 * (see [WebAuthnBridge.install], wired from BrowserFunctions + BrowserServiceImpl),
 * before page scripts run, so a relying party that feature-detects `isUVPAA()` sees
 * `true`.
 *
 * Known limitation: the value the shim resolves is a plain JS object shaped like a
 * `PublicKeyCredential`, not a real one — relying parties that do
 * `instanceof PublicKeyCredential` / `instanceof AuthenticatorAssertionResponse` will
 * not match. Fields (`rawId`, `response.*`, `getClientExtensionResults()`) are present.
 */
internal object WebAuthnScripts {

    // The script only varies by the baked-in PLATFORM_AVAILABLE boolean, so memoize the
    // two variants instead of rebuilding ~5KB on every browser/frame injection.
    private val memo = java.util.concurrent.ConcurrentHashMap<Boolean, String>()

    /**
     * [platformAvailable] is resolved on the host side (macOS version + class
     * availability + authorization state) and baked in so the page needs no async
     * round-trip for `isUVPAA()`.
     */
    fun shim(platformAvailable: Boolean): String =
        memo.getOrPut(platformAvailable) { build(platformAvailable) }

    private fun build(platformAvailable: Boolean): String = """
(function() {
  if (window.__bossWebAuthnInstalled) return;
  window.__bossWebAuthnInstalled = true;

  var bridge = window.__bossWebAuthn;
  var PLATFORM_AVAILABLE = $platformAvailable;

  // No native authenticator (non-macOS, OS too old, or entitlement not granted) or no
  // bridge: leave navigator.credentials.* and PublicKeyCredential completely untouched
  // so Chromium's own WebAuthn (USB security keys) behaves exactly as before.
  if (!PLATFORM_AVAILABLE || !bridge) return;

  // Normalize any BufferSource (ArrayBuffer, TypedArray, or DataView) to bytes.
  // `new Uint8Array(dataView)` does NOT yield the underlying bytes, so a plain
  // `new Uint8Array(buf)` would mishandle a DataView challenge/id that Chromium accepts.
  function toBytes(x) {
    return ArrayBuffer.isView(x)
      ? new Uint8Array(x.buffer, x.byteOffset, x.byteLength)
      : new Uint8Array(x);
  }

  var b64u = {
    enc: function(buf) {
      var bytes = toBytes(buf);
      var bin = '';
      for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
      return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+${'$'}/, '');
    },
    dec: function(str) {
      if (str == null) return null;
      var s = str.replace(/-/g, '+').replace(/_/g, '/');
      while (s.length % 4) s += '=';
      var bin = atob(s);
      var bytes = new Uint8Array(bin.length);
      for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      return bytes.buffer;
    }
  };

  // request-id -> {resolve, reject}
  var pending = {};
  var counter = 0;

  // Native side settles a ceremony by calling back into the page here.
  window.__bossWebAuthnSettle = function(id, ok, payloadJson) {
    var entry = pending[id];
    if (!entry) return;
    delete pending[id];
    var payload;
    try { payload = JSON.parse(payloadJson); } catch (e) { payload = {}; }
    if (ok) entry.resolve(payload); else entry.reject(payload);
  };

  function callNative(op, requestObj) {
    return new Promise(function(resolve, reject) {
      var id = 'wa_' + (++counter);
      pending[id] = { resolve: resolve, reject: reject };
      var accepted;
      try {
        accepted = bridge.request(id, op, JSON.stringify(requestObj));
      } catch (e) {
        delete pending[id];
        reject({ error: 'UnknownError', message: String(e) });
        return;
      }
      // Host returns false to decline this ceremony (let caller fall back).
      if (accepted === false || accepted === 'false') {
        delete pending[id];
        reject({ error: '__BOSS_FALLBACK__' });
      }
    });
  }

  function domError(payload) {
    var name = (payload && payload.error) || 'NotAllowedError';
    var msg = (payload && payload.message) || 'The operation is not allowed.';
    try { return new DOMException(msg, name); } catch (e) { var err = new Error(msg); err.name = name; return err; }
  }

  var origGet = navigator.credentials.get.bind(navigator.credentials);
  var origCreate = navigator.credentials.create.bind(navigator.credentials);

  function wantsPlatform(pk, isCreate) {
    if (!PLATFORM_AVAILABLE || !pk) return false;
    if (isCreate) {
      var sel = pk.authenticatorSelection || {};
      // "cross-platform" explicitly wants a roaming key (USB) — let Chromium handle it.
      if (sel.authenticatorAttachment === 'cross-platform') return false;
      // Apple's platform authenticator only mints ES256 (alg -7). If the RP's
      // pubKeyCredParams excludes -7, an accepted ceremony would produce a credential
      // the RP rejects — decline instead so a USB key (via Chromium) can satisfy it.
      // An absent/empty list defaults to accepting ES256.
      var params = pk.pubKeyCredParams || [];
      if (params.length > 0 && !params.some(function(p) { return p && p.alg === -7; })) return false;
      return true;
    }
    // Assertion: if every listed credential declares transports that exclude the
    // platform (internal/hybrid), it's a roaming-key-only request — let Chromium
    // handle it so USB/NFC/BLE security keys keep working.
    var allow = pk.allowCredentials || [];
    if (allow.length > 0) {
      var anyPlatform = allow.some(function(c) {
        var t = c.transports;
        return !t || t.length === 0 || t.indexOf('internal') >= 0 || t.indexOf('hybrid') >= 0;
      });
      if (!anyPlatform) return false;
    }
    return true;
  }

  navigator.credentials.get = function(options) {
    options = options || {};
    var pk = options.publicKey;
    if (!pk || options.mediation === 'conditional' || !wantsPlatform(pk, false)) {
      return origGet(options);
    }
    var req = {
      rpId: pk.rpId || location.hostname,
      challenge: b64u.enc(pk.challenge),
      origin: location.origin,
      userVerification: (pk.userVerification || 'preferred'),
      timeout: pk.timeout || 0,
      allowCredentials: (pk.allowCredentials || []).map(function(c) { return b64u.enc(c.id); })
    };
    return callNative('get', req).then(function(r) {
      return buildAssertion(r);
    }, function(payload) {
      if (payload && payload.error === '__BOSS_FALLBACK__') return origGet(options);
      throw domError(payload);
    });
  };

  navigator.credentials.create = function(options) {
    options = options || {};
    var pk = options.publicKey;
    if (!pk || !wantsPlatform(pk, true)) {
      return origCreate(options);
    }
    var req = {
      rpId: (pk.rp && pk.rp.id) || location.hostname,
      rpName: (pk.rp && pk.rp.name) || '',
      challenge: b64u.enc(pk.challenge),
      origin: location.origin,
      userVerification: (pk.authenticatorSelection && pk.authenticatorSelection.userVerification) || 'preferred',
      residentKey: (pk.authenticatorSelection && pk.authenticatorSelection.residentKey) || '',
      timeout: pk.timeout || 0,
      user: {
        id: b64u.enc(pk.user.id),
        name: pk.user.name || '',
        displayName: pk.user.displayName || ''
      },
      excludeCredentials: (pk.excludeCredentials || []).map(function(c) { return b64u.enc(c.id); })
    };
    return callNative('create', req).then(function(r) {
      return buildAttestation(r);
    }, function(payload) {
      if (payload && payload.error === '__BOSS_FALLBACK__') return origCreate(options);
      throw domError(payload);
    });
  };

  function baseCredential(r) {
    var rawId = b64u.dec(r.credentialId);
    return {
      id: r.credentialId,
      rawId: rawId,
      type: 'public-key',
      authenticatorAttachment: 'platform',
      getClientExtensionResults: function() { return {}; }
    };
  }

  function buildAssertion(r) {
    var cred = baseCredential(r);
    cred.response = {
      clientDataJSON: b64u.dec(r.clientDataJSON),
      authenticatorData: b64u.dec(r.authenticatorData),
      signature: b64u.dec(r.signature),
      userHandle: r.userHandle ? b64u.dec(r.userHandle) : null
    };
    return cred;
  }

  function buildAttestation(r) {
    var cred = baseCredential(r);
    cred.response = {
      clientDataJSON: b64u.dec(r.clientDataJSON),
      attestationObject: b64u.dec(r.attestationObject),
      getAuthenticatorData: function() { return r.authenticatorData ? b64u.dec(r.authenticatorData) : null; },
      getPublicKey: function() { return r.publicKey ? b64u.dec(r.publicKey) : null; },
      getPublicKeyAlgorithm: function() { return r.publicKeyAlgorithm || -7; },
      getTransports: function() { return ['internal', 'hybrid']; }
    };
    return cred;
  }

  // Feature detection: report platform authenticator availability.
  if (window.PublicKeyCredential) {
    if (PLATFORM_AVAILABLE) {
      PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable = function() {
        return Promise.resolve(true);
      };
    }
    if (typeof PublicKeyCredential.isConditionalMediationAvailable !== 'function') {
      PublicKeyCredential.isConditionalMediationAvailable = function() { return Promise.resolve(false); };
    }
  }
})();
""".trimIndent()
}
