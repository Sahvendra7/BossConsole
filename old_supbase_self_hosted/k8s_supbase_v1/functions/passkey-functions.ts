// Consolidated Passkey Functions - Single File for Kubernetes Deployment
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { encode as base64UrlEncode, decode as base64UrlDecode } from "https://deno.land/std@0.168.0/encoding/base64url.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

// ============================================================================
// DATABASE MODULE (from database.ts)
// ============================================================================

// Initialize Supabase client
const supabaseUrl = Deno.env.get('SUPABASE_URL')!
const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

console.log('Initializing Supabase client with URL:', supabaseUrl)
console.log('Service key length:', supabaseServiceKey?.length || 0)

const supabase = createClient(supabaseUrl, supabaseServiceKey)

console.log('Supabase client initialized successfully')

// Simple CBOR parser for extracting public key from attestation object
function extractPublicKeyFromAttestation(attestationObject: Uint8Array): Uint8Array | null {
  try {
    console.log('Extracting public key from attestation object, length:', attestationObject.length)
    console.log('First 50 bytes:', Array.from(attestationObject.slice(0, 50)).map(b => b.toString(16).padStart(2, '0')).join(' '))
    
    // The attestation object is CBOR encoded
    // It contains authData which has the public key at the end
    // Format: rpIdHash(32) + flags(1) + signCount(4) + aaguid(16) + credIdLen(2) + credId + COSE_Key
    
    // Look for CBOR byte string marker 0x58 followed by length, then find COSE key patterns
    // The COSE EC2 key uses negative integers for coordinate names:
    // -2 (0x21) for x coordinate, -3 (0x22) for y coordinate
    
    // Search for byte patterns more flexibly
    for (let i = 0; i < attestationObject.length - 35; i++) {
      // Look for x coordinate pattern: 0x21 (negative 2) followed by byte string
      if (attestationObject[i] === 0x21) {
        console.log(`Found -2 (x coord marker) at position ${i}`)
        
        // Check for byte string pattern after the key
        let xStart = -1, xLength = 0
        if (i + 1 < attestationObject.length && attestationObject[i + 1] === 0x58) {
          // CBOR byte string with explicit length
          if (i + 2 < attestationObject.length && attestationObject[i + 2] === 0x20) {
            xStart = i + 3
            xLength = 32
          }
        } else if (i + 1 < attestationObject.length && attestationObject[i + 1] === 0x40) {
          // CBOR byte string with 0-length (though unlikely for coordinates)
          xStart = i + 2
          xLength = 0
        }
        
        if (xStart > 0 && xLength === 32) {
          const xCoord = attestationObject.slice(xStart, xStart + xLength)
          console.log(`X coordinate found at ${xStart}, length ${xLength}`)
          
          // Look for y coordinate (-3 = 0x22) after x coordinate
          const ySearchStart = xStart + xLength
          console.log(`Searching for y coordinate from ${ySearchStart} to ${attestationObject.length - 32}`)
          for (let j = ySearchStart; j <= attestationObject.length - 35; j++) {
            if (attestationObject[j] === 0x22) {
              console.log(`Found -3 (y coord marker) at position ${j}`)
              
              let yStart = -1, yLength = 0
              if (j + 1 < attestationObject.length && attestationObject[j + 1] === 0x58) {
                if (j + 2 < attestationObject.length && attestationObject[j + 2] === 0x20) {
                  yStart = j + 3
                  yLength = 32
                }
              }
              
              if (yStart > 0 && yLength === 32) {
                const yCoord = attestationObject.slice(yStart, yStart + yLength)
                console.log(`Y coordinate found at ${yStart}, length ${yLength}`)
                
                // Validate coordinates (not all zeros)
                const xSum = xCoord.reduce((sum, byte) => sum + byte, 0)
                const ySum = yCoord.reduce((sum, byte) => sum + byte, 0)
                
                if (xSum > 0 && ySum > 0) {
                  console.log('Found valid EC2 coordinates')
                  console.log('X coordinate sum:', xSum, 'Y coordinate sum:', ySum)
                  
                  // Build uncompressed EC public key
                  const publicKey = new Uint8Array(65)
                  publicKey[0] = 0x04 // Uncompressed indicator
                  publicKey.set(xCoord, 1)  // x coordinate  
                  publicKey.set(yCoord, 33) // y coordinate
                  
                  console.log('Successfully extracted', publicKey.length, 'byte EC public key')
                  return publicKey
                }
              }
            }
          }
        }
      }
    }
    
    console.log('No valid COSE EC2 key pattern found in attestation')
    return null
    
  } catch (error) {
    console.error('Public key extraction error:', error)
    return null
  }
}

export interface PasskeyRecord {
  id: string
  user_id: string
  credential_id: string
  public_key: string
  display_name: string
  transports: string[]
  created_at: number
  last_used_at?: number
  active: boolean
}


export async function verifyChallenge(challenge: string, type: 'registration' | 'authentication') {
  console.log('🔍 verifyChallenge called with:', {
    challenge: challenge.substring(0, 20) + '...',
    type
  })
  try {
    const { data: challengeData, error } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('challenge', challenge)
      .eq('type', type)
      .single()
    
    console.log('🔍 verifyChallenge result:', { found: !!challengeData, error: error?.message })

    if (error || !challengeData) {
      console.error('Challenge verification failed:', error)
      return { success: false, error: 'Invalid or expired challenge' }
    }

    const expiresAt = new Date(challengeData.expires_at)
    if (expiresAt < new Date()) {
      return { success: false, error: 'Challenge expired' }
    }

    return { success: true, challengeData }
  } catch (error) {
    console.error('Challenge verification error:', error)
    return { success: false, error: 'Challenge verification failed' }
  }
}

export async function verifyAndConsumeChallenge(challenge: string, type: 'registration' | 'authentication') {
  console.log('🔥 verifyAndConsumeChallenge called with:', {
    challenge: challenge.substring(0, 20) + '...',
    type
  })

  try {
    const { data, error } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('challenge', challenge)
      .eq('type', type)
      .gt('expires_at', new Date().toISOString())
      .single()

    if (error || !data) {
      console.error('Challenge not found or expired:', error)
      return { success: false, error: 'Invalid or expired challenge' }
    }

    // Delete the challenge after successful verification
    await supabase
      .from('passkey_challenges')
      .delete()
      .eq('id', data.id)

    console.log('Challenge verified and consumed successfully')
    return { success: true, challenge: data }
  } catch (error) {
    console.error('Exception verifying challenge:', error)
    return { success: false, error: error.message }
  }
}

export async function storePasskeyInDB(passkey: Omit<PasskeyRecord, 'id' | 'created_at' | 'active'>) {
  console.log('storePasskeyInDB called with credential:', passkey.credential_id)
  console.log('Full passkey data:', JSON.stringify(passkey, null, 2))

  try {
    const insertData = {
      ...passkey,
      created_at: Date.now(),
      active: true
    }
    
    console.log('About to insert:', JSON.stringify(insertData, null, 2))
    
    const { data, error } = await supabase
      .from('user_passkeys')
      .insert(insertData)
      .select()

    console.log('Insert result - data:', data)
    console.log('Insert result - error:', error)

    if (error) {
      console.error('Database error storing passkey:', error)
      return { success: false, error: error.message }
    }

    console.log('Passkey stored successfully - returned data:', JSON.stringify(data, null, 2))
    return { success: true, data }
  } catch (error) {
    console.error('Exception storing passkey:', error)
    return { success: false, error: error.message }
  }
}

export async function getUserPasskeys(userId: string) {
  console.log('Getting passkeys for user:', userId)

  try {
    const { data, error } = await supabase
      .from('user_passkeys')
      .select('*')
      .eq('user_id', userId)
      .eq('active', true)

    if (error) {
      console.error('Database error getting passkeys:', error)
      return { success: false, error: error.message }
    }

    console.log(`Found ${data?.length || 0} existing passkeys`)
    return { success: true, passkeys: data }
  } catch (error) {
    console.error('Exception getting passkeys:', error)
    return { success: false, error: error.message }
  }
}

export async function findPasskeyByCredentialId(credentialId: string) {
  console.log('Finding passkey by credential ID:', credentialId)

  try {
    const { data, error } = await supabase
      .from('user_passkeys')
      .select('*')
      .eq('credential_id', credentialId)
      .eq('active', true)
      .single()

    if (error || !data) {
      console.error('Passkey not found:', error)
      return { success: false, error: 'Passkey not found' }
    }

    console.log('Found passkey for user:', data.user_id)
    return { success: true, passkey: data }
  } catch (error) {
    console.error('Exception finding passkey:', error)
    return { success: false, error: error.message }
  }
}

// ============================================================================
// CRYPTO UTILITIES MODULE (from crypto-utils.ts)
// ============================================================================

function convertDERSignatureToRaw(derSignature: Uint8Array): Uint8Array {
  // DER SEQUENCE: 30 <length> 02 <r-length> <r> 02 <s-length> <s>
  console.log('DER conversion: Input length', derSignature.length, 'bytes:', Array.from(derSignature.slice(0, 10)).map(b => b.toString(16).padStart(2, '0')).join(' '))
  
  if (derSignature[0] !== 0x30) {
    throw new Error('Invalid DER signature: does not start with SEQUENCE')
  }
  
  let offset = 2 // Skip SEQUENCE tag and length
  
  // Parse r
  if (derSignature[offset] !== 0x02) {
    throw new Error('Invalid DER signature: r component not found')
  }
  offset++ // Skip INTEGER tag
  const rLength = derSignature[offset++]
  let r = derSignature.slice(offset, offset + rLength)
  
  // Remove leading zero if present (DER encoding adds it for positive numbers)
  if (r.length === 33 && r[0] === 0x00) {
    r = r.slice(1)
  }
  
  offset += rLength
  
  // Parse s
  if (derSignature[offset] !== 0x02) {
    throw new Error('Invalid DER signature: s component not found')
  }
  offset++ // Skip INTEGER tag
  const sLength = derSignature[offset++]
  let s = derSignature.slice(offset, offset + sLength)
  
  // Remove leading zero if present
  if (s.length === 33 && s[0] === 0x00) {
    s = s.slice(1)
  }
  
  // Pad to 32 bytes if needed
  const rPadded = new Uint8Array(32)
  const sPadded = new Uint8Array(32)
  rPadded.set(r, 32 - r.length)
  sPadded.set(s, 32 - s.length)
  
  // Combine r and s
  const rawSignature = new Uint8Array(64)
  rawSignature.set(rPadded, 0)
  rawSignature.set(sPadded, 32)
  
  console.log('DER conversion: Output length', rawSignature.length, 'first 10 bytes:', Array.from(rawSignature.slice(0, 10)).map(b => b.toString(16).padStart(2, '0')).join(' '))
  
  return rawSignature
}

export async function verifyWebAuthnSignature(
  publicKeyB64: string,
  authenticatorDataB64: string,
  clientDataJSONB64: string,
  signatureB64: string
): Promise<boolean> {
  try {
    console.log('SIGNATURE VERIFICATION DEBUG: Starting verification...')
    console.log('SIGNATURE VERIFICATION DEBUG: Input public key base64:', publicKeyB64)
    console.log('SIGNATURE VERIFICATION DEBUG: Input authenticator data base64:', authenticatorDataB64)
    console.log('SIGNATURE VERIFICATION DEBUG: Input signature base64:', signatureB64)
    
    // Decode base64url data
    const publicKeyBytes = base64UrlDecode(publicKeyB64)
    const authenticatorData = base64UrlDecode(authenticatorDataB64)
    const clientDataJSON = base64UrlDecode(clientDataJSONB64)
    const signature = base64UrlDecode(signatureB64)
    
    console.log('SIGNATURE VERIFICATION DEBUG: Decoded public key length:', publicKeyBytes.length)
    console.log('SIGNATURE VERIFICATION DEBUG: Decoded public key first 20 bytes:', Array.from(publicKeyBytes.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join(' '))
    console.log('SIGNATURE VERIFICATION DEBUG: Decoded authenticator data length:', authenticatorData.length)
    console.log('SIGNATURE VERIFICATION DEBUG: Decoded signature length:', signature.length)
    
    // Create the signed data (authenticatorData + hash of clientDataJSON)
    const clientDataHash = await crypto.subtle.digest('SHA-256', clientDataJSON)
    const signedData = new Uint8Array(authenticatorData.length + clientDataHash.byteLength)
    signedData.set(authenticatorData, 0)
    signedData.set(new Uint8Array(clientDataHash), authenticatorData.length)
    
    // Import the public key (handle both SPKI and raw point formats)
    let publicKey;
    try {
      console.log('SIGNATURE VERIFICATION DEBUG: Attempting SPKI import...')
      // Try SPKI format first
      publicKey = await crypto.subtle.importKey(
        'spki',
        publicKeyBytes,
        {
          name: 'ECDSA',
          namedCurve: 'P-256'
        },
        false,
        ['verify']
      )
      console.log('SIGNATURE VERIFICATION DEBUG: SPKI import successful')
    } catch (spkiError) {
      console.log('SIGNATURE VERIFICATION DEBUG: SPKI import failed, trying raw format...')
      console.log('SIGNATURE VERIFICATION DEBUG: SPKI error:', spkiError.message)
      try {
        // Try raw uncompressed point format (0x04 + x + y)
        if (publicKeyBytes.length === 65 && publicKeyBytes[0] === 0x04) {
          console.log('SIGNATURE VERIFICATION DEBUG: Public key appears to be uncompressed EC format')
          publicKey = await crypto.subtle.importKey(
            'raw',
            publicKeyBytes,
            {
              name: 'ECDSA',
              namedCurve: 'P-256'
            },
            false,
            ['verify']
          )
          console.log('SIGNATURE VERIFICATION DEBUG: Raw format import successful')
        } else {
          console.log('SIGNATURE VERIFICATION DEBUG: Invalid raw format - length:', publicKeyBytes.length, 'first byte:', publicKeyBytes[0])
          throw new Error('Invalid public key format')
        }
      } catch (rawError) {
        console.error('SIGNATURE VERIFICATION DEBUG: Failed to import public key in both formats')
        console.error('SIGNATURE VERIFICATION DEBUG: SPKI error:', spkiError.message)
        console.error('SIGNATURE VERIFICATION DEBUG: Raw error:', rawError.message)
        throw new Error('Unable to import public key')
      }
    }
    
    // Convert DER signature to raw format if needed
    let processedSignature = signature
    console.log('SIGNATURE VERIFICATION DEBUG: Original signature first 10 bytes:', Array.from(signature.slice(0, 10)).map(b => b.toString(16).padStart(2, '0')).join(' '))
    
    // Check if signature is in DER format (starts with 0x30)
    if (signature.length > 6 && signature[0] === 0x30) {
      console.log('SIGNATURE VERIFICATION DEBUG: Signature appears to be in DER format, converting to raw...')
      try {
        processedSignature = convertDERSignatureToRaw(signature)
        console.log('SIGNATURE VERIFICATION DEBUG: Converted signature length:', processedSignature.length)
        console.log('SIGNATURE VERIFICATION DEBUG: Converted signature first 10 bytes:', Array.from(processedSignature.slice(0, 10)).map(b => b.toString(16).padStart(2, '0')).join(' '))
      } catch (conversionError) {
        console.error('SIGNATURE VERIFICATION DEBUG: DER conversion failed:', conversionError)
        // Continue with original signature
      }
    } else {
      console.log('SIGNATURE VERIFICATION DEBUG: Signature appears to be in raw format')
    }
    
    // Verify the signature
    console.log('SIGNATURE VERIFICATION DEBUG: About to verify signature with crypto.subtle.verify')
    console.log('SIGNATURE VERIFICATION DEBUG: Signed data length:', signedData.length)
    console.log('SIGNATURE VERIFICATION DEBUG: Signed data first 20 bytes:', Array.from(signedData.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join(' '))
    
    const isValid = await crypto.subtle.verify(
      {
        name: 'ECDSA',
        hash: 'SHA-256'
      },
      publicKey,
      processedSignature,
      signedData
    )
    
    console.log('SIGNATURE VERIFICATION DEBUG: Signature verification result:', isValid)
    if (!isValid) {
      console.error('SIGNATURE VERIFICATION DEBUG: VERIFICATION FAILED')
      console.error('SIGNATURE VERIFICATION DEBUG: This means either:')
      console.error('SIGNATURE VERIFICATION DEBUG: 1. Wrong public key stored during registration')
      console.error('SIGNATURE VERIFICATION DEBUG: 2. Different device private key (iCloud sync issue)')
      console.error('SIGNATURE VERIFICATION DEBUG: 3. Signature format mismatch')
      console.error('SIGNATURE VERIFICATION DEBUG: 4. Signed data construction error')
    }
    return isValid
    
  } catch (error) {
    console.error('Error verifying WebAuthn signature:', error)
    return false
  }
}

function base64UrlDecode(str: string): Uint8Array {
  // Add padding if needed
  const padded = str + '='.repeat((4 - str.length % 4) % 4)
  // Convert base64url to base64
  const base64 = padded.replace(/-/g, '+').replace(/_/g, '/')
  // Decode base64
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

// ============================================================================
// AUTHENTICATION HANDLERS MODULE (from auth-handlers.ts)
// ============================================================================

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

export async function handlePasskeyAuthenticationComplete(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    console.log('Passkey authentication completion request')
    
    const body = parsedBody || await req.json()
    console.log('Request body:', JSON.stringify(body, null, 2))
    
    if (!body.challenge || !body.credentialId || !body.authenticatorData || !body.signature || !body.clientDataJSON) {
      return new Response(
        JSON.stringify({ success: false, error: 'Missing required authentication data' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }
    
    // Verify the stored challenge
    const { data: challengeData, error: challengeError } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('challenge', body.challenge)
      .eq('type', 'authentication')
      .gte('expires_at', new Date().toISOString())
      .single()

    if (challengeError || !challengeData) {
      console.error('Challenge verification failed:', challengeError)
      return new Response(
        JSON.stringify({ success: false, error: 'Invalid or expired challenge' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // Find the credential and associated user using the function
    const passkeyResult = await findPasskeyByCredentialId(body.credentialId)
    
    if (!passkeyResult.success || !passkeyResult.passkey) {
      console.error('Credential not found:', passkeyResult.error)
      return new Response(
        JSON.stringify({ success: false, error: 'Invalid credential' }),
        { status: 404, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }
    
    const passkeyRecord = passkeyResult.passkey

    // Basic clientDataJSON verification
    try {
      const clientDataJSONBytes = base64UrlDecode(body.clientDataJSON)
      const clientDataJSON = JSON.parse(new TextDecoder().decode(clientDataJSONBytes))
      
      if (clientDataJSON.challenge !== body.challenge) {
        console.error('Challenge mismatch in clientDataJSON')
        return new Response(
          JSON.stringify({ success: false, error: 'Challenge verification failed' }),
          { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }

      if (clientDataJSON.type !== 'webauthn.get') {
        console.error('Invalid clientDataJSON type:', clientDataJSON.type)
        return new Response(
          JSON.stringify({ success: false, error: 'Invalid authentication type' }),
          { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }
    } catch (error) {
      console.error('ClientDataJSON parsing/verification failed:', error)
      return new Response(
        JSON.stringify({ success: false, error: 'Invalid client data' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // Verify authenticator data and signature using working implementation
    try {
      const authenticatorDataBytes = base64UrlDecode(body.authenticatorData)
      
      if (authenticatorDataBytes.length < 37) {
        console.error('Invalid authenticator data length:', authenticatorDataBytes.length)
        return new Response(
          JSON.stringify({ success: false, error: 'Invalid authenticator data' }),
          { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }

      const flags = authenticatorDataBytes[32]
      const userPresent = (flags & 0x01) !== 0
      
      if (!userPresent) {
        console.error('User present flag not set')
        return new Response(
          JSON.stringify({ success: false, error: 'User presence verification failed' }),
          { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }

      // CRITICAL: Perform cryptographic signature verification
      console.log('AUTHENTICATION DEBUG: Performing cryptographic signature verification...')
      console.log('AUTHENTICATION DEBUG: Stored public key:', passkeyRecord.public_key)
      console.log('AUTHENTICATION DEBUG: Credential ID:', body.credentialId)
      console.log('AUTHENTICATION DEBUG: Authenticator data:', body.authenticatorData)
      console.log('AUTHENTICATION DEBUG: Signature:', body.signature)
      console.log('AUTHENTICATION DEBUG: ClientDataJSON:', body.clientDataJSON)
      
      // Decode and log the actual public key bytes for comparison
      try {
        const storedPublicKeyBytes = base64UrlDecode(passkeyRecord.public_key)
        console.log('AUTHENTICATION DEBUG: Stored public key length:', storedPublicKeyBytes.length)
        console.log('AUTHENTICATION DEBUG: Stored public key first 20 bytes:', Array.from(storedPublicKeyBytes.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join(' '))
        if (storedPublicKeyBytes.length === 65) {
          console.log('AUTHENTICATION DEBUG: Public key format appears to be uncompressed EC (65 bytes)')
        } else {
          console.log('AUTHENTICATION DEBUG: Public key format unknown, length:', storedPublicKeyBytes.length)
        }
      } catch (e) {
        console.error('AUTHENTICATION DEBUG: Failed to decode stored public key:', e)
      }
      
      // Pass the base64url encoded clientDataJSON directly - verification function will decode it
      const signatureValid = await verifyWebAuthnSignature(
        passkeyRecord.public_key,
        body.authenticatorData,
        body.clientDataJSON,
        body.signature
      )
      
      if (!signatureValid) {
        console.error('SECURITY: Cryptographic signature verification failed for credential:', body.credentialId)
        return new Response(
          JSON.stringify({ success: false, error: 'Signature verification failed' }),
          { status: 403, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }
      
      console.log('SUCCESS: Cryptographic signature verification passed for credential:', body.credentialId)
      
    } catch (error) {
      console.error('Authenticator data verification failed:', error)
      return new Response(
        JSON.stringify({ success: false, error: 'Invalid authenticator data' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // Update last used timestamp
    const { error: updateError } = await supabase
      .from('user_passkeys')
      .update({ last_used_at: Date.now() })
      .eq('id', passkeyRecord.id)

    if (updateError) {
      console.error('Failed to update credential last used timestamp:', updateError)
    }

    // Clean up used challenge
    await supabase
      .from('passkey_challenges')
      .delete()
      .eq('challenge', body.challenge)
      .eq('type', 'authentication')

    console.log('Passkey authentication completed successfully for user:', passkeyRecord.user_id)
    
    // Get user email from the passkey record's display_name or from database lookup
    let userEmail = null
    try {
      // Try to get email from user_passkeys table via display_name or user lookup
      const { data: userLookup, error: lookupError } = await supabase
        .from('auth.users')
        .select('email')
        .eq('id', passkeyRecord.user_id)
        .single()
      
      if (!lookupError && userLookup) {
        userEmail = userLookup.email
      } else {
        console.log('Could not fetch user email, using display_name from passkey record')
        // Fallback: Use display_name from passkey record if it's an email
        if (passkeyRecord.display_name && passkeyRecord.display_name.includes('@')) {
          userEmail = passkeyRecord.display_name
        }
      }
    } catch (e) {
      console.error('Failed to fetch user email:', e)
      // Final fallback: Use display_name if it looks like an email
      if (passkeyRecord.display_name && passkeyRecord.display_name.includes('@')) {
        userEmail = passkeyRecord.display_name
      }
    }

    // Create proper Supabase session using admin API after passkey verification
    let sessionTokens = null
    if (userEmail) {
      try {
        console.log('Creating Supabase session via admin API for:', userEmail)
        
        // Step 1: Generate magic link using direct admin API call
        const generateLinkResponse = await fetch('https://api.risaboss.com/auth/v1/admin/generate_link', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            type: 'magiclink',
            email: userEmail
          })
        })
        
        if (!generateLinkResponse.ok) {
          throw new Error(`Generate link failed: ${generateLinkResponse.status}`)
        }
        
        const linkData = await generateLinkResponse.json()
        console.log('Admin generate link successful, token:', linkData.hashed_token?.substring(0, 10) + '...')
        
        // Step 2: Programmatically verify the token to get real session tokens
        const verifyUrl = `http://api.risaboss.com/verify?token=${linkData.hashed_token}&type=magiclink&redirect_to=boss://auth/verify`
        const verifyResponse = await fetch(verifyUrl, {
          method: 'GET',
          redirect: 'manual' // Don't follow redirects, we want to parse the Location header
        })
        
        if (verifyResponse.status === 303) {
          const location = verifyResponse.headers.get('location')
          console.log('Verify successful, parsing session tokens from redirect...')
          
          if (location) {
            // Parse tokens from the redirect URL fragment
            const url = new URL(location)
            const fragment = url.hash.substring(1) // Remove #
            const params = new URLSearchParams(fragment)
            
            const accessToken = params.get('access_token')
            const refreshToken = params.get('refresh_token')
            const expiresAt = params.get('expires_at')
            const expiresIn = params.get('expires_in')
            
            if (accessToken && refreshToken) {
              // Now get the user data using the access token
              console.log('Fetching user data with access token...')
              const userResponse = await fetch('https://api.risaboss.com/auth/v1/user', {
                method: 'GET',
                headers: {
                  'Authorization': `Bearer ${accessToken}`,
                  'Content-Type': 'application/json'
                }
              })
              
              let userData = null
              if (userResponse.ok) {
                userData = await userResponse.json()
                console.log('✅ User data fetched successfully:', userData.id, userData.email)
              } else {
                console.error('❌ Failed to fetch user data:', userResponse.status)
              }
              
              sessionTokens = {
                sessionToken: null,
                accessToken: accessToken,
                refreshToken: refreshToken,
                expiresAt: expiresAt ? parseInt(expiresAt) * 1000 : null, // Convert to milliseconds
                expiresIn: expiresIn ? parseInt(expiresIn) : null,
                user: userData // Include user data
              }
              console.log('✅ Real Supabase session tokens extracted successfully!')
            } else {
              console.error('❌ Failed to extract tokens from redirect URL')
            }
          }
        } else {
          console.error('Verify request failed:', verifyResponse.status)
        }
        
      } catch (sessionError) {
        console.error('Exception creating admin session:', sessionError)
      }
    }

    const response = {
      success: true,
      userId: sessionTokens?.user?.id || passkeyRecord.user_id,
      email: sessionTokens?.user?.email || userEmail,
      // Include real Supabase session tokens
      sessionToken: sessionTokens?.sessionToken || null,
      accessToken: sessionTokens?.accessToken || null,
      refreshToken: sessionTokens?.refreshToken || null,
      expiresAt: sessionTokens?.expiresAt || null,
      sessionMethod: sessionTokens ? 'admin_generated' : null,
      // Include user data for session creation
      user: sessionTokens?.user || null
    }

    console.log('Passkey authentication completed successfully for user:', passkeyRecord.user_id)

    return new Response(
      JSON.stringify(response),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
    
  } catch (error) {
    console.error('Authentication error:', error)
    return new Response(JSON.stringify({ 
      success: false, 
      error: 'Authentication failed' 
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    })
  }
}

export async function handlePasskeyAuthenticationChallenge(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    console.log('Passkey authentication challenge request')
    
    const body = parsedBody || await req.json()
    console.log('Request body:', JSON.stringify(body, null, 2))
    
    if (!body.challenge) {
      return new Response(
        JSON.stringify({ error: 'Missing required field: challenge' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    let allowedCredentials = []
    let resolvedUserId = null

    if (body.email) {
      console.log('Resolving email to user ID:', body.email)
      
      // Use Supabase auth admin API to find user by email
      const { data: userData, error: userError } = await supabase.auth.admin.listUsers()
      
      if (userError) {
        console.error('Failed to list users:', userError)
        return new Response(
          JSON.stringify({ error: 'Failed to query users' }),
          { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }
      
      const user = userData.users.find(u => u.email === body.email)
      if (!user) {
        console.error('User not found with email:', body.email)
        return new Response(
          JSON.stringify({ error: 'User not found' }),
          { status: 404, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }
      
      resolvedUserId = user.id
      console.log('Resolved email to user ID:', resolvedUserId)

      // Use the getUserPasskeys function instead of direct query
      const passkeysResult = await getUserPasskeys(resolvedUserId)
      
      if (!passkeysResult.success) {
        console.error('Error fetching user passkeys:', passkeysResult.error)
        return new Response(
          JSON.stringify({ error: 'Failed to fetch user credentials' }),
          { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }
      
      // Map to only get credential_id and transports
      const userPasskeys = passkeysResult.passkeys?.map(pk => ({
        credential_id: pk.credential_id,
        transports: pk.transports
      }))

      if (!userPasskeys || userPasskeys.length === 0) {
        return new Response(
          JSON.stringify({ error: 'No passkeys found for user' }),
          { status: 404, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }

      allowedCredentials = userPasskeys.map((pk: any) => ({
        id: pk.credential_id,
        type: 'public-key',
        transports: pk.transports || ['internal']
      }))
    }

    // Store challenge temporarily (expires in 5 minutes)
    const challengeData = {
      user_id: resolvedUserId || null,
      challenge: body.challenge,
      type: 'authentication',
      session_id: body.sessionId || null,
      expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
      created_at: new Date().toISOString()
    }

    console.log('🔍 [EDGE DEBUG] About to store challenge data:', JSON.stringify(challengeData, null, 2))

    const { error: challengeStoreError } = await supabase
      .from('passkey_challenges')
      .insert(challengeData)

    if (challengeStoreError) {
      console.error('❌ [EDGE ERROR] Error storing challenge:', challengeStoreError)
      return new Response(
        JSON.stringify({ error: 'Failed to store challenge' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    console.log('✅ [EDGE SUCCESS] Challenge stored successfully in database')

    const challengeResponse: any = {
      challenge: body.challenge,
      timeout: 60000,
      rpId: 'api.risaboss.com',
      userVerification: 'preferred'
    }

    if (body.email && allowedCredentials.length > 0) {
      challengeResponse.allowCredentials = allowedCredentials
    }

    console.log('Authentication challenge created successfully:', {
      userId: resolvedUserId || 'usernameless',
      allowedCredentialsCount: allowedCredentials.length
    })

    return new Response(
      JSON.stringify(challengeResponse),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
    
  } catch (error) {
    console.error('Passkey authentication challenge error:', error)
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
  }
}

// ============================================================================
// REGISTRATION HANDLERS MODULE (from registration-handlers.ts)
// ============================================================================

export async function handlePasskeyRegistrationChallenge(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    console.log('Passkey registration challenge request')
    
    const body = parsedBody || await req.json()
    console.log('Request body:', JSON.stringify(body, null, 2))
    
    if (!body.userId || !body.displayName || !body.challenge) {
      return new Response(
        JSON.stringify({ error: 'Missing required fields: userId, displayName, challenge' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // Store challenge temporarily (expires in 5 minutes)
    const challengeData = {
      user_id: body.userId,
      challenge: body.challenge,
      type: 'registration',
      expires_at: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
      created_at: new Date().toISOString()
    }

    const { error: challengeStoreError } = await supabase
      .from('passkey_challenges')
      .insert(challengeData)

    if (challengeStoreError) {
      console.error('Error storing challenge:', challengeStoreError)
      return new Response(
        JSON.stringify({ error: 'Failed to store challenge' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    const challengeResponse = {
      challenge: body.challenge,
      timeout: 60000,
      rpId: 'api.risaboss.com',
      rpName: 'BOSS',
      attestation: 'none',
      authenticatorSelection: body.authenticatorSelection || {
        authenticatorAttachment: 'platform',
        residentKey: 'preferred',
        requireResidentKey: false,
        userVerification: 'preferred'
      },
      excludeCredentials: null
    }

    console.log('Registration challenge created successfully for user:', body.userId)

    return new Response(
      JSON.stringify(challengeResponse),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
    
  } catch (error) {
    console.error('Passkey registration challenge error:', error)
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
  }
}

export async function handlePasskeyRegistrationComplete(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    console.log('=== Passkey Registration Complete ===')
    
    const requestBody = parsedBody || await req.json()
    console.log('Request body:', JSON.stringify(requestBody, null, 2))
    
    const { userId, challenge, credentialId, publicKey, attestationObject, clientDataJSON, transports } = requestBody
    
    if (!userId || !challenge || !credentialId || !attestationObject) {
      return new Response(JSON.stringify({ 
        success: false, 
        error: 'Missing required fields: userId, challenge, credentialId, attestationObject' 
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      })
    }
    
    // Extract the actual public key from the attestation object
    let actualPublicKey: string
    try {
      const attestationBytes = base64UrlDecode(attestationObject)
      console.log('REGISTRATION DEBUG: attestationObject length:', attestationBytes.length)
      console.log('REGISTRATION DEBUG: attestationObject first 20 bytes:', Array.from(attestationBytes.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join(' '))
      
      const extractedKey = extractPublicKeyFromAttestation(attestationBytes)
      
      if (extractedKey) {
        actualPublicKey = base64UrlEncode(extractedKey)
        console.log('REGISTRATION DEBUG: Successfully extracted public key from attestation object')
        console.log('REGISTRATION DEBUG: Extracted public key length:', extractedKey.length)
        console.log('REGISTRATION DEBUG: Extracted public key first 20 bytes:', Array.from(extractedKey.slice(0, 20)).map(b => b.toString(16).padStart(2, '0')).join(' '))
        console.log('REGISTRATION DEBUG: Public key base64url:', actualPublicKey)
      } else {
        if (publicKey) {
          // Use provided public key only if it was explicitly sent
          actualPublicKey = publicKey
          console.log('REGISTRATION DEBUG: Using provided public key')
        } else {
          console.error('REGISTRATION DEBUG: No public key could be extracted from attestation object')
          return new Response(JSON.stringify({ 
            success: false, 
            error: 'Failed to extract public key from attestation object' 
          }), {
            status: 400,
            headers: { 'Content-Type': 'application/json', ...corsHeaders }
          })
        }
      }
    } catch (error) {
      console.error('REGISTRATION DEBUG: Public key extraction failed:', error)
      if (publicKey) {
        actualPublicKey = publicKey
        console.log('REGISTRATION DEBUG: Using provided public key as fallback due to error')
      } else {
        console.error('REGISTRATION DEBUG: No fallback public key available')
        return new Response(JSON.stringify({ 
          success: false, 
          error: 'Failed to extract or receive valid public key' 
        }), {
          status: 400,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        })
      }
    }
    
    // Verify the challenge
    const challengeResult = await verifyAndConsumeChallenge(challenge, 'registration')
    if (!challengeResult.success) {
      console.error('Challenge verification failed:', challengeResult.error)
      return new Response(JSON.stringify({ 
        success: false, 
        error: challengeResult.error 
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      })
    }
    
    // Generate meaningful display name based on authenticator info
    function generateDisplayName(transports: string[], userAgent: string | null): string {
      // Check if it's a platform authenticator (internal transport)
      if (transports.includes('internal')) {
        // Detect platform based on user agent
        if (userAgent) {
          if (userAgent.includes('Mac')) {
            return 'Touch ID (macOS)'
          } else if (userAgent.includes('Windows')) {
            return 'Windows Hello'
          } else if (userAgent.includes('iPhone') || userAgent.includes('iPad')) {
            return 'Face ID / Touch ID (iOS)'
          } else if (userAgent.includes('Android')) {
            return 'Biometric (Android)'
          }
        }
        return 'Platform Authenticator'
      }
      
      // Cross-platform authenticator
      if (transports.includes('usb')) {
        return 'USB Security Key'
      }
      if (transports.includes('nfc')) {
        return 'NFC Authenticator'
      }
      if (transports.includes('ble')) {
        return 'Bluetooth Authenticator'
      }
      
      // Check for common authenticator apps based on user agent
      if (userAgent) {
        if (userAgent.includes('Chrome')) {
          return 'Chrome Passkey'
        } else if (userAgent.includes('Safari')) {
          return 'Safari Passkey'
        } else if (userAgent.includes('Firefox')) {
          return 'Firefox Passkey'
        } else if (userAgent.includes('Edge')) {
          return 'Edge Passkey'
        }
      }
      
      return 'Cross-Device Authenticator'
    }

    const userAgent = req.headers.get('user-agent')
    
    // For mobile registration flows, check if user agent indicates iPhone/iPad
    let displayName = generateDisplayName(transports || ['internal'], userAgent)
    let finalTransports = transports || ['internal']
    
    // Override display name and transports if this is clearly from mobile registration
    if (userAgent && (userAgent.includes('iPhone') || userAgent.includes('iPad'))) {
      displayName = 'Face ID / Touch ID (iOS)'
      // Remove 'internal' transport for iPhone registrations - they're cross-device only
      finalTransports = finalTransports.filter(t => t !== 'internal')
      if (finalTransports.length === 0 || !finalTransports.includes('hybrid')) {
        finalTransports = ['hybrid'] // Ensure hybrid transport for cross-device
      }
    } else if (userAgent && userAgent.includes('Android')) {
      displayName = 'Biometric (Android)'
      // Remove 'internal' transport for Android registrations - they're cross-device only
      finalTransports = finalTransports.filter(t => t !== 'internal')
      if (finalTransports.length === 0 || !finalTransports.includes('hybrid')) {
        finalTransports = ['hybrid']
      }
    }
    
    console.log('Final display name:', displayName, 'User agent:', userAgent, 'Transports:', finalTransports)

    // Store the passkey in the database
    const passkey = {
      user_id: userId,
      credential_id: credentialId,
      public_key: actualPublicKey,
      display_name: displayName,
      transports: finalTransports, // Use corrected transports
      last_used_at: Date.now()
    }
    
    const storeResult = await storePasskeyInDB(passkey)
    if (!storeResult.success) {
      return new Response(JSON.stringify({ 
        success: false, 
        error: storeResult.error 
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      })
    }
    
    console.log('Registration successful for user:', userId)
    
    // Safely access the stored credential
    const storedCredential = storeResult.data?.[0] || null
    if (!storedCredential) {
      console.error('No credential returned from database insert')
      return new Response(JSON.stringify({ 
        success: false, 
        error: 'Failed to store credential in database' 
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      })
    }
    
    return new Response(JSON.stringify({
      success: true,
      credential: storedCredential
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    })
    
  } catch (error) {
    console.error('Registration completion error:', error)
    return new Response(JSON.stringify({ 
      success: false, 
      error: 'Registration failed' 
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    })
  }
}

// ============================================================================
// MANAGEMENT HANDLERS MODULE (from management-handlers.ts)
// ============================================================================

export async function handlePasskeyManagementList(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    console.log('Passkey management list request')
    
    const body = parsedBody || await req.json()
    
    if (!body.userId) {
      return new Response(
        JSON.stringify({ success: false, error: 'Missing required field: userId' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // Use the getUserPasskeys function instead of direct query
    const passkeysResult = await getUserPasskeys(body.userId)
    
    if (!passkeysResult.success) {
      console.error('Error fetching user passkeys:', passkeysResult.error)
      return new Response(
        JSON.stringify({ success: false, error: 'Failed to fetch user credentials' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }
    
    // Sort by created_at descending (newest first)
    const userPasskeys = passkeysResult.passkeys?.sort((a, b) => 
      new Date(b.created_at).getTime() - new Date(a.created_at).getTime()
    )

    console.log(`Found ${userPasskeys?.length || 0} passkeys for user:`, body.userId)

    return new Response(
      JSON.stringify({
        success: true,
        data: userPasskeys || []
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
    
  } catch (error) {
    console.error('Passkey management list error:', error)
    return new Response(
      JSON.stringify({ success: false, error: 'Internal server error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
  }
}

export async function handlePasskeyManagementDelete(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    console.log('Passkey management delete request')
    
    const body = parsedBody || await req.json()
    
    if (!body.userId || !body.credentialId) {
      return new Response(
        JSON.stringify({ success: false, error: 'Missing required fields: userId, credentialId' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    const { error: deleteError } = await supabase
      .from('user_passkeys')
      .update({ active: false })
      .eq('user_id', body.userId)
      .eq('credential_id', body.credentialId)
      .eq('active', true)

    if (deleteError) {
      console.error('Failed to delete passkey:', deleteError)
      return new Response(
        JSON.stringify({ success: false, error: 'Failed to delete passkey' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    console.log('Passkey deleted successfully for user:', body.userId, 'credential:', body.credentialId)

    return new Response(
      JSON.stringify({
        success: true,
        message: 'Passkey deleted successfully'
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
    
  } catch (error) {
    console.error('Passkey management delete error:', error)
    return new Response(
      JSON.stringify({ success: false, error: 'Internal server error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
  }
}

// ============================================================================
// DESKTOP WEBAUTHN HANDLERS
// ============================================================================

export async function handleDesktopWebAuthn(req: Request): Promise<Response> {
  const url = new URL(req.url)
  const challenge = url.searchParams.get('challenge')
  const userId = url.searchParams.get('userId')
  const email = url.searchParams.get('email')
  const displayName = url.searchParams.get('displayName') || email
  const sessionId = url.searchParams.get('sessionId')
  const rpId = url.searchParams.get('rpId') || 'api.risaboss.com'
  const rpName = url.searchParams.get('rpName') || 'BOSS'

  if (!challenge || !userId || !email || !sessionId) {
    return new Response(
      JSON.stringify({ error: 'Missing required parameters: challenge, userId, email, sessionId' }),
      { 
        status: 400, 
        headers: { 'Content-Type': 'application/json', ...corsHeaders } 
      }
    )
  }

  // Verify the challenge is valid
  const challengeVerification = await verifyChallenge(challenge, 'registration')
  if (!challengeVerification.success) {
    return new Response(
      getDesktopErrorHTML('Invalid or expired challenge'),
      { 
        status: 400, 
        headers: { 'Content-Type': 'text/html', ...corsHeaders } 
      }
    )
  }

  // Return the WebAuthn registration HTML page
  const html = getDesktopWebAuthnHTML(challenge, userId, email, sessionId, rpId, rpName)
  return new Response(html, {
    status: 200,
    headers: { 'Content-Type': 'text/html', ...corsHeaders }
  })
}

function getDesktopWebAuthnHTML(challenge: string, userId: string, email: string, sessionId: string, rpId: string, rpName: string): string {
  return `<!DOCTYPE html>
<html>
<head>
    <title>🔐 BOSS Passkey Registration</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 40px; background: #f5f5f5; }
        .container { max-width: 500px; margin: 0 auto; background: white; padding: 40px; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); text-align: center; }
        .logo { font-size: 48px; margin-bottom: 20px; }
        .title { font-size: 24px; font-weight: 600; color: #333; margin-bottom: 10px; }
        .subtitle { color: #666; margin-bottom: 20px; }
        .email { font-weight: 500; color: #007AFF; margin-bottom: 30px; }
        .button { background: #007AFF; color: white; border: none; padding: 15px 30px; border-radius: 8px; font-size: 16px; font-weight: 500; cursor: pointer; margin-bottom: 20px; }
        .button:hover { background: #0056CC; }
        .button:disabled { background: #ccc; cursor: not-allowed; }
        .status { margin-top: 20px; padding: 15px; border-radius: 8px; }
        .status.success { background: #d4edda; color: #155724; }
        .status.error { background: #f8d7da; color: #721c24; }
    </style>
</head>
<body>
    <div class="container">
        <div class="logo">🔐</div>
        <div class="title">BOSS Passkey Registration</div>
        <div class="subtitle">Creating your passkey for ${email}...</div>
        <div class="subtitle">Please use your biometric authentication when prompted.</div>
        
        <button id="registerBtn" class="button">Create Passkey</button>
        <div id="status"></div>
    </div>

    <script>
        const challenge = '${challenge}';
        const userId = '${userId}';
        const email = '${email}';
        const sessionId = '${sessionId}';
        const rpId = '${rpId}';
        const rpName = '${rpName}';

        function base64urlToBuffer(base64url) {
            try {
                const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
                const binary = atob(base64);
                const buffer = new Uint8Array(binary.length);
                for (let i = 0; i < binary.length; i++) {
                    buffer[i] = binary.charCodeAt(i);
                }
                return buffer.buffer;
            } catch (error) {
                console.error('Error converting base64url to buffer:', error);
                throw error;
            }
        }

        function bufferToBase64url(buffer) {
            const binary = String.fromCharCode(...new Uint8Array(buffer));
            return btoa(binary).replace(/[+]/g, '-').replace(/[/]/g, '_').replace(/=/g, '');
        }

        document.getElementById('registerBtn').addEventListener('click', async () => {
            const button = document.getElementById('registerBtn');
            const status = document.getElementById('status');
            
            button.disabled = true;
            button.textContent = 'Creating Passkey...';
            status.innerHTML = '';

            try {
                const userIdBuffer = new TextEncoder().encode(userId);

                const publicKeyCredentialCreationOptions = {
                    challenge: base64urlToBuffer(challenge),
                    rp: { id: rpId, name: rpName },
                    user: {
                        id: userIdBuffer,
                        name: email,
                        displayName: email
                    },
                    pubKeyCredParams: [
                        { alg: -7, type: "public-key" },  // ES256
                        { alg: -257, type: "public-key" } // RS256
                    ],
                    authenticatorSelection: {
                        authenticatorAttachment: "platform",
                        userVerification: "required",
                        residentKey: "required"
                    },
                    timeout: 300000,
                    attestation: "none"
                };

                console.log('Creating credential with options:', publicKeyCredentialCreationOptions);

                const credential = await navigator.credentials.create({
                    publicKey: publicKeyCredentialCreationOptions
                });

                console.log('Credential created successfully:', credential);

                if (!credential || !credential.id || !credential.response) {
                    throw new Error('Invalid credential created by WebAuthn API');
                }

                const registrationData = {
                    userId: userId,
                    challenge: challenge,
                    credentialId: credential.id,
                    attestationObject: bufferToBase64url(credential.response.attestationObject),
                    clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
                    transports: credential.response.getTransports?.() || ["internal", "hybrid"]
                };

                console.log('Sending registration data to server...');

                const response = await fetch(window.location.origin + window.location.pathname + '?op=reg-complete', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'apikey': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2FsaG9zdCIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzU0Nzg1MDU0LCJleHAiOjE3ODYzMjEwNTR9.UR-amMvudG2h3iBBzBfRPjH6psOhyWYrrq3yhc_s-s4'
                    },
                    body: JSON.stringify(registrationData)
                });

                const result = await response.json();
                console.log('Registration result:', result);

                if (result.success) {
                    status.innerHTML = '<div class="status success">✅ Registration completed successfully!</div>';
                    button.textContent = 'Passkey Created';
                    
                    // Close the window after a short delay
                    setTimeout(() => {
                        window.close();
                    }, 2000);
                } else {
                    status.innerHTML = '<div class="status error">❌ Registration failed: ' + (result.error || 'Unknown error') + '</div>';
                    button.disabled = false;
                    button.textContent = 'Try Again';
                }

            } catch (error) {
                console.error('Registration error:', error);
                status.innerHTML = '<div class="status error">❌ Registration failed: ' + error.message + '</div>';
                button.disabled = false;
                button.textContent = 'Try Again';
            }
        });

        // Auto-click the button to start registration immediately
        setTimeout(() => {
            document.getElementById('registerBtn').click();
        }, 1000);
    </script>
</body>
</html>`;
}

function getDesktopErrorHTML(errorMessage: string): string {
  return `<!DOCTYPE html>
<html>
<head>
    <title>BOSS Passkey Registration - Error</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 40px; background: #f5f5f5; }
        .container { max-width: 500px; margin: 0 auto; background: white; padding: 40px; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); text-align: center; }
        .error { color: #dc3545; font-size: 18px; margin-bottom: 20px; }
        h1 { color: #333; margin-bottom: 30px; }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔐 BOSS Passkey Registration</h1>
        <div class="error">❌ ${errorMessage}</div>
    </div>
</body>
</html>`;
}

// ============================================================================
// MOBILE REGISTRATION HANDLERS
// ============================================================================

export async function handleMobileRegistration(req: Request): Promise<Response> {
  try {
    const url = new URL(req.url)
    const challenge = url.searchParams.get('challenge')
    const email = url.searchParams.get('email') 
    const sessionId = url.searchParams.get('sessionId')
    const rpId = url.searchParams.get('rpId') || 'api.risaboss.com'
    const rpName = url.searchParams.get('rpName') || 'BOSS'
    
    console.log('Mobile registration request:', {
      challenge: challenge?.substring(0, 10) + '...',
      email,
      sessionId,
      rpId,
      rpName
    })

    if (!challenge || !email || !sessionId) {
      return new Response(
        JSON.stringify({ error: 'Missing required parameters: challenge, email, sessionId' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // Verify challenge exists and is valid
    const { data: challengeData, error: challengeError } = await supabase
      .from('passkey_challenges')
      .select('*')
      .eq('challenge', challenge)
      .eq('type', 'registration')
      .gt('expires_at', new Date().toISOString())
      .single()

    if (challengeError || !challengeData) {
      console.error('Invalid or expired challenge:', challengeError)
      return new Response(getMobileErrorHTML('Invalid or expired registration link'), {
        status: 400,
        headers: { 'Content-Type': 'text/html', ...corsHeaders }
      })
    }

    // Look up user by email
    const { data: userData, error: userError } = await supabase.auth.admin.listUsers()
    const user = userData?.users?.find(u => u.email === email)
    
    if (!user) {
      console.error('User not found for email:', email)
      return new Response(getMobileErrorHTML('User not found'), {
        status: 404,
        headers: { 'Content-Type': 'text/html', ...corsHeaders }
      })
    }

    // Update challenge with session info
    await supabase
      .from('passkey_challenges')
      .update({ 
        session_id: sessionId,
        status: 'in_progress'
      })
      .eq('challenge', challenge)

    // Return mobile registration HTML page
    return new Response(getMobileRegistrationHTML(challenge, user.id, email, sessionId, rpId, rpName), {
      status: 200,
      headers: { 'Content-Type': 'text/html', ...corsHeaders }
    })
    
  } catch (error) {
    console.error('Mobile registration error:', error)
    return new Response(getMobileErrorHTML('Internal server error'), {
      status: 500,
      headers: { 'Content-Type': 'text/html', ...corsHeaders }
    })
  }
}

export async function handleSessionStatus(req: Request): Promise<Response> {
  try {
    const url = new URL(req.url)
    const sessionId = url.searchParams.get('sessionId')
    
    if (!sessionId) {
      return new Response(
        JSON.stringify({ error: 'Missing sessionId parameter' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // Check session status
    const { data: challengeData, error } = await supabase
      .from('passkey_challenges')
      .select('status, type')
      .eq('session_id', sessionId)
      .eq('type', 'registration')
      .single()

    if (error) {
      return new Response(
        JSON.stringify({ success: false, status: 'not_found' }),
        { status: 404, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    return new Response(
      JSON.stringify({ 
        success: true, 
        status: challengeData.status || 'pending',
        session_id: sessionId 
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
    
  } catch (error) {
    console.error('Session status error:', error)
    return new Response(
      JSON.stringify({ success: false, error: 'Internal server error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
  }
}

export async function handleSessionComplete(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    const url = new URL(req.url)
    const body = parsedBody || await req.json().catch(() => ({}))
    const sessionId = url.searchParams.get('sessionId') || body.sessionId
    const userEmail = url.searchParams.get('email') || body.email
    
    // Helper function to get user email from user_id if email is missing
    const getUserEmail = async (userId: string): Promise<string> => {
      try {
        const { data: user } = await supabase.auth.admin.getUserById(userId)
        return user?.user?.email || 'unknown@example.com'
      } catch (e) {
        console.warn('Failed to get user email for ID:', userId, e)
        return 'unknown@example.com'
      }
    }
    
    if (!sessionId) {
      return new Response(
        JSON.stringify({ error: 'Missing sessionId parameter' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    console.log('Session completion for:', sessionId, 'with data:', body)
    
    // For authentication sessions, also store the result for desktop polling
    if (body.status === 'completed' && body.userId) {
      console.log('Storing authentication completion for desktop polling...')
      console.log('Looking for challenge with sessionId:', sessionId)
      
      // Try to find the challenge by sessionId first, then by challenge field if provided
      let challengeData = null
      let challengeError = null
      
      if (sessionId) {
        const sessionResult = await supabase
          .from('passkey_challenges')
          .select('challenge, type, session_id, user_id, created_at')
          .eq('session_id', sessionId)
          .single()
        challengeData = sessionResult.data
        challengeError = sessionResult.error
      }
      
      // If not found by sessionId and we have a challenge field, try that
      if (!challengeData && body.challenge) {
        const challengeResult = await supabase
          .from('passkey_challenges')
          .select('challenge, type, session_id, user_id, created_at')
          .eq('challenge', body.challenge)
          .single()
        challengeData = challengeResult.data
        challengeError = challengeResult.error
      }
      
      console.log('Challenge query result:', { challengeData, challengeError })
      
      if (challengeError) {
        console.error('Failed to find challenge for session:', sessionId, 'error:', challengeError)
        console.log('Challenge not found by sessionId, but we have challenge in request body. Storing completion directly.')
        
        // Store completion directly using the challenge from request body
        if (body.challenge && body.userId) {
          try {
            // Generate JWT tokens for cross-device authentication
            let sessionTokens = null
            try {
              const { data: userData, error: userError } = await supabase.auth.admin.getUserById(body.userId)
              
              if (!userError && userData.user) {
                // Generate JWT tokens using proper signing
                const jwtSecret = Deno.env.get('SUPABASE_JWT_SECRET') || 'your-super-secret-jwt-key-at-least-32-characters-long-for-production'
                const now = Math.floor(Date.now() / 1000)
                const expiresIn = 60 * 60 // 1 hour
                
                const payload = {
                  aud: 'authenticated',
                  exp: now + expiresIn,
                  iat: now,
                  iss: 'https://api.risaboss.com',
                  sub: userData.user.id,
                  email: userData.user.email,
                  role: 'authenticated',
                  app_metadata: {
                    provider: 'email',
                    providers: ['email']
                  },
                  user_metadata: {},
                  amr: ['pwd']
                }
                
                const encoder = new TextEncoder()
                const keyData = encoder.encode(jwtSecret)
                const key = await crypto.subtle.importKey(
                  'raw',
                  keyData,
                  { name: 'HMAC', hash: 'SHA-256' },
                  false,
                  ['sign']
                )
                
                const header = { alg: 'HS256', typ: 'JWT' }
                const encodedHeader = btoa(JSON.stringify(header)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
                const encodedPayload = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
                
                const data = encoder.encode(encodedHeader + '.' + encodedPayload)
                const signatureBytes = await crypto.subtle.sign('HMAC', key, data)
                const signature = btoa(String.fromCharCode(...new Uint8Array(signatureBytes)))
                  .replace(/\+/g, '-')
                  .replace(/\//g, '_')
                  .replace(/=/g, '')
                
                const accessToken = encodedHeader + '.' + encodedPayload + '.' + signature
                
                // Generate refresh token with longer expiry (30 days)
                const refreshExpiresIn = 30 * 24 * 60 * 60 // 30 days
                const refreshPayload = {
                  ...payload,
                  exp: now + refreshExpiresIn,
                  type: 'refresh'
                }
                
                const encodedRefreshPayload = btoa(JSON.stringify(refreshPayload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
                const refreshData = encoder.encode(encodedHeader + '.' + encodedRefreshPayload)
                const refreshSignatureBytes = await crypto.subtle.sign('HMAC', key, refreshData)
                const refreshSignature = btoa(String.fromCharCode(...new Uint8Array(refreshSignatureBytes)))
                  .replace(/\+/g, '-')
                  .replace(/\//g, '_')
                  .replace(/=/g, '')
                
                const refreshToken = encodedHeader + '.' + encodedRefreshPayload + '.' + refreshSignature
                
                console.log('JWT tokens generated for cross-device auth completion')
                sessionTokens = {
                  accessToken: accessToken,
                  refreshToken: refreshToken,
                  expiresAt: (now + expiresIn) * 1000
                }
              }
            } catch (tokenError) {
              console.error('Exception generating JWT tokens for session completion:', tokenError)
            }
            
            const { error: storeError } = await supabase
              .from('completed_authentications')
              .insert({
                challenge: body.challenge,
                user_id: body.userId,
                email: userEmail || await getUserEmail(body.userId),
                session_token: sessionTokens?.accessToken || 'mobile-auth-completed',
                access_token: sessionTokens?.accessToken || null,
                refresh_token: sessionTokens?.refreshToken || null,
                expires_at_timestamp: sessionTokens?.expiresAt ? new Date(sessionTokens.expiresAt) : new Date(Date.now() + 5 * 60 * 1000)
              })
            
            if (storeError) {
              console.error('Failed to store direct auth completion:', storeError)
              return new Response(
                JSON.stringify({ 
                  success: false, 
                  error: 'Failed to store authentication completion' 
                }),
                { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
              )
            } else {
              console.log('✅ Direct authentication completion stored successfully')
              return new Response(
                JSON.stringify({ 
                  success: true, 
                  message: 'Session completed successfully (direct storage)' 
                }),
                { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
              )
            }
          } catch (directStoreErr) {
            console.error('Error in direct auth completion storage:', directStoreErr)
          }
        }
        
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: `Challenge not found for sessionId: ${sessionId}` 
          }),
          { status: 404, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
        
      } else if (!challengeData) {
        console.error('No challenge data found for session:', sessionId)
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: 'No challenge data found' 
          }),
          { status: 404, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      } else if (challengeData.type !== 'authentication') {
        console.log('Session is for', challengeData.type, 'not authentication, skipping store')
        return new Response(
          JSON.stringify({ 
            success: false, 
            error: `Challenge is for ${challengeData.type}, not authentication` 
          }),
          { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      } else {
        console.log('Found authentication challenge:', challengeData.challenge.substring(0, 10) + '...')
        
        // Store completed authentication for desktop polling
        try {
          // Cross-device authentication completed successfully
          // Note: Session establishment should be handled client-side
          let sessionTokens = null
          
          const { error: storeError } = await supabase
            .from('completed_authentications')
            .insert({
              challenge: challengeData.challenge,
              user_id: body.userId,
              email: userEmail || await getUserEmail(body.userId), // Use actual email from URL params or lookup by user_id
              session_token: sessionTokens?.accessToken || 'mobile-auth-completed',
              access_token: sessionTokens?.accessToken || null,
              refresh_token: sessionTokens?.refreshToken || null,
              expires_at_timestamp: sessionTokens?.expiresAt ? new Date(sessionTokens.expiresAt) : new Date(Date.now() + 5 * 60 * 1000)
            })
          
          if (storeError) {
            console.error('Failed to store auth completion:', storeError)
          } else {
            console.log('Authentication completion stored for desktop polling')
          }
        } catch (storeErr) {
          console.error('Error storing auth completion:', storeErr)
        }
      }
    }

    return new Response(
      JSON.stringify({ 
        success: true, 
        message: 'Session completed successfully' 
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
    
  } catch (error) {
    console.error('Session complete error:', error)
    return new Response(
      JSON.stringify({ success: false, error: 'Internal server error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
  }
}

function getMobileRegistrationHTML(challenge: string, userId: string, email: string, sessionId: string, rpId: string, rpName: string): string {
  return `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Add BOSS Passkey</title>
    <style>
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
            margin: 0; padding: 20px; background: #f5f5f5;
            display: flex; flex-direction: column; align-items: center; min-height: 100vh;
        }
        .container { 
            background: white; border-radius: 12px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.1);
            max-width: 400px; width: 100%; text-align: center;
        }
        .logo { font-size: 24px; font-weight: bold; color: #333; margin-bottom: 16px; }
        .title { font-size: 20px; margin-bottom: 8px; color: #333; }
        .subtitle { color: #666; margin-bottom: 24px; }
        .email { background: #f8f9fa; padding: 8px 12px; border-radius: 6px; margin-bottom: 24px; }
        .button { 
            background: #007AFF; color: white; border: none; padding: 16px 24px; 
            border-radius: 8px; font-size: 16px; cursor: pointer; width: 100%;
        }
        .button:disabled { background: #ccc; cursor: not-allowed; }
        .status { margin-top: 16px; padding: 12px; border-radius: 6px; }
        .status.success { background: #d4edda; color: #155724; }
        .status.error { background: #f8d7da; color: #721c24; }
    </style>
</head>
<body>
    <div class="container">
        <div class="logo">🔐 BOSS</div>
        <div class="title">Add Passkey</div>
        <div class="subtitle">Secure your BOSS account with Touch ID or Face ID</div>
        <div class="email">${email}</div>
        
        <button id="registerBtn" class="button">Add Passkey</button>
        <div id="status"></div>
    </div>

    <script>
        const challenge = '${challenge}';
        const userId = '${userId}';
        const email = '${email}';
        const sessionId = '${sessionId}';
        const rpId = '${rpId}';
        const rpName = '${rpName}';

        function base64urlToBuffer(base64url) {
            try {
                console.log('Converting base64url to buffer:', base64url?.substring(0, 20) + '...');
                let base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
                
                // Add padding if needed
                while (base64.length % 4) {
                    base64 += '=';
                }
                
                console.log('Base64 with padding:', base64.substring(0, 20) + '...');
                const binary = atob(base64);
                const buffer = new Uint8Array(binary.length);
                for (let i = 0; i < binary.length; i++) {
                    buffer[i] = binary.charCodeAt(i);
                }
                console.log('Buffer created, length:', buffer.length);
                return buffer.buffer;
            } catch (error) {
                console.error('Error converting base64url to buffer:', error, 'Input:', base64url);
                throw error;
            }
        }

        function bufferToBase64url(buffer) {
            const binary = String.fromCharCode(...new Uint8Array(buffer));
            return btoa(binary).replace(/[+]/g, '-').replace(/[/]/g, '_').replace(/=/g, '');
        }

        document.getElementById('registerBtn').addEventListener('click', async () => {
            const button = document.getElementById('registerBtn');
            const status = document.getElementById('status');
            
            button.disabled = true;
            button.textContent = 'Creating Passkey...';
            status.innerHTML = '';

            try {
                // Generate user ID buffer
                const userIdBuffer = new TextEncoder().encode(userId);

                const publicKeyCredentialCreationOptions = {
                    challenge: base64urlToBuffer(challenge),
                    rp: { id: rpId, name: rpName },
                    user: {
                        id: userIdBuffer,
                        name: email,
                        displayName: email
                    },
                    pubKeyCredParams: [
                        { alg: -7, type: "public-key" },  // ES256
                        { alg: -257, type: "public-key" } // RS256
                    ],
                    authenticatorSelection: {
                        authenticatorAttachment: "cross-platform",
                        userVerification: "preferred",
                        residentKey: "preferred"
                    },
                    timeout: 300000,
                    attestation: "none"
                };

                console.log('Creating credential with options:', publicKeyCredentialCreationOptions);

                const credential = await navigator.credentials.create({
                    publicKey: publicKeyCredentialCreationOptions
                });

                console.log('Credential created successfully:', credential);
                console.log('Credential ID:', credential?.id);
                console.log('Credential response:', credential?.response);
                console.log('Public key available:', !!credential?.response?.publicKey);

                // Validate credential before proceeding
                if (!credential || !credential.id || !credential.response) {
                    throw new Error('Invalid credential created by WebAuthn API');
                }

                // Public key will be extracted from attestationObject on server side

                // Prepare registration data
                const registrationData = {
                    userId: userId,
                    challenge: challenge,
                    credentialId: credential.id,
                    attestationObject: bufferToBase64url(credential.response.attestationObject),
                    clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
                    transports: credential.response.getTransports?.() || []
                };

                console.log('Sending registration data:', {
                    userId: registrationData.userId,
                    challenge: registrationData.challenge?.substring(0, 10) + '...',
                    credentialId: registrationData.credentialId?.substring(0, 20) + '...',
                    publicKey: !!registrationData.publicKey,
                    attestationObject: !!registrationData.attestationObject,
                    clientDataJSON: !!registrationData.clientDataJSON,
                    transports: registrationData.transports
                });

                // Complete registration
                const response = await fetch(window.location.origin + window.location.pathname + '?op=reg-complete', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'apikey': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2FsaG9zdCIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzU0Nzg1MDU0LCJleHAiOjE3ODYzMjEwNTR9.UR-amMvudG2h3iBBzBfRPjH6psOhyWYrrq3yhc_s-s4'
                    },
                    body: JSON.stringify(registrationData)
                });

                const result = await response.json();
                console.log('Registration result:', result);

                if (result.success) {
                    // Update session status
                    await fetch(window.location.origin + window.location.pathname + '?op=session-complete&sessionId=' + sessionId, {
                        method: 'POST'
                    });

                    status.className = 'status success';
                    status.innerHTML = '✅ Passkey added successfully!<br>You can now close this page.';
                    button.textContent = 'Complete';
                } else {
                    throw new Error(result.error || 'Registration failed');
                }

            } catch (error) {
                console.error('Registration error:', error);
                status.className = 'status error';
                status.innerHTML = '❌ Failed to add passkey: ' + error.message;
                button.disabled = false;
                button.textContent = 'Try Again';
            }
        });
    </script>
</body>
</html>`;
}

function getMobileErrorHTML(message: string): string {
  return `
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BOSS Passkey Error</title>
    <style>
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
            margin: 0; padding: 20px; background: #f5f5f5;
            display: flex; flex-direction: column; align-items: center; min-height: 100vh;
        }
        .container { 
            background: white; border-radius: 12px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.1);
            max-width: 400px; width: 100%; text-align: center;
        }
        .logo { font-size: 24px; font-weight: bold; color: #333; margin-bottom: 16px; }
        .error { color: #721c24; background: #f8d7da; padding: 16px; border-radius: 8px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="logo">🔐 BOSS</div>
        <div class="error">❌ ${message}</div>
    </div>
</body>
</html>`;
}

// ============================================================================
// MOBILE AUTHENTICATION HANDLER
// ============================================================================

export async function handleMobileAuthentication(req: Request): Promise<Response> {
  const url = new URL(req.url)
  const challenge = url.searchParams.get('challenge')
  const email = url.searchParams.get('email')
  const sessionId = url.searchParams.get('sessionId')
  const rpId = url.searchParams.get('rpId') || 'api.risaboss.com'
  const credentialId = url.searchParams.get('credentialId')

  if (!challenge || !email || !sessionId || !credentialId) {
    return new Response(
      getMobileErrorHTML('Missing required parameters for mobile authentication'),
      { 
        status: 400, 
        headers: { 'Content-Type': 'text/html', ...corsHeaders } 
      }
    )
  }

  // Verify challenge is valid (but don't consume it - desktop needs it for polling)
  const challengeVerification = await verifyChallenge(challenge, 'authentication')
  if (!challengeVerification.success) {
    return new Response(
      getMobileErrorHTML('Invalid or expired authentication challenge'),
      { 
        status: 400, 
        headers: { 'Content-Type': 'text/html', ...corsHeaders } 
      }
    )
  }

  // Challenge should already have sessionId from auth-challenge creation
  console.log('Mobile auth processing challenge with sessionId:', sessionId)

  try {
    // Get user credentials for this email
    const { data: user, error: userError } = await supabase.auth.admin.listUsers()
    if (userError) {
      console.error('Error finding user:', userError)
      return new Response(
        getMobileErrorHTML('Authentication failed - user not found'),
        { 
          status: 400, 
          headers: { 'Content-Type': 'text/html', ...corsHeaders } 
        }
      )
    }

    const targetUser = user.users.find(u => u.email === email)
    if (!targetUser) {
      console.error('User not found with email:', email)
      return new Response(
        getMobileErrorHTML('Authentication failed - user not found'),
        { 
          status: 400, 
          headers: { 'Content-Type': 'text/html', ...corsHeaders } 
        }
      )
    }
    
    console.log('Target user found:', targetUser.id, 'email:', targetUser.email)

    // Get user's passkey credentials using helper function
    const passkeyResult = await findPasskeyByCredentialId(credentialId)
    
    console.log('Passkey result:', passkeyResult.success, 'user_id:', passkeyResult.passkey?.user_id, 'active:', passkeyResult.passkey?.active)
    console.log('Comparison: passkey.user_id !== targetUser.id?', passkeyResult.passkey?.user_id, '!==', targetUser.id, '=', passkeyResult.passkey?.user_id !== targetUser.id)
    
    if (!passkeyResult.success || !passkeyResult.passkey || passkeyResult.passkey.user_id !== targetUser.id || !passkeyResult.passkey.active) {
      console.error('Error finding credential:', passkeyResult.error)
      return new Response(
        getMobileErrorHTML('Authentication failed - credential not found'),
        { 
          status: 400, 
          headers: { 'Content-Type': 'text/html', ...corsHeaders } 
        }
      )
    }
    
    const credentials = passkeyResult.passkey

    // Return mobile authentication HTML page
    const html = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BOSS Passkey Authentication</title>
    <style>
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
            margin: 0; padding: 20px; background: #f5f5f5;
            display: flex; flex-direction: column; align-items: center; min-height: 100vh;
        }
        .container { 
            background: white; border-radius: 12px; padding: 32px; box-shadow: 0 4px 12px rgba(0,0,0,0.1);
            max-width: 400px; width: 100%; text-align: center;
        }
        .logo { font-size: 24px; font-weight: bold; color: #333; margin-bottom: 16px; }
        .subtitle { color: #666; margin-bottom: 24px; }
        .email { color: #333; font-weight: 500; margin-bottom: 20px; }
        .auth-button { 
            background: #007AFF; color: white; border: none; padding: 12px 24px; 
            border-radius: 8px; font-size: 16px; cursor: pointer; width: 100%; margin: 8px 0;
        }
        .auth-button:hover { background: #0056b3; }
        .status { padding: 16px; margin: 16px 0; border-radius: 8px; }
        .success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .error { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .loading { background: #e2e3e5; color: #383d41; border: 1px solid #d6d8db; }
        .credential-info { 
            background: #f8f9fa; padding: 12px; border-radius: 6px; 
            font-size: 14px; color: #666; margin-bottom: 16px; 
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="logo">🔐 BOSS</div>
        <div class="subtitle">Authenticate with your passkey</div>
        <div class="email">${email}</div>
        <div class="credential-info">
            Credential: ${credentials.display_name}<br>
            Created: ${new Date(credentials.created_at).toLocaleDateString()}
        </div>
        
        <button class="auth-button" onclick="authenticate()" id="authButton">
            🔐 Authenticate with Passkey
        </button>
        
        <div id="status" style="display: none;" class="status"></div>
    </div>

    <script>
        const challenge = '${challenge}';
        const credentialId = '${credentialId}';
        const sessionId = '${sessionId}';
        const rpId = '${rpId}';
        
        function showStatus(message, type = 'loading') {
            const status = document.getElementById('status');
            status.className = 'status ' + type;
            status.textContent = message;
            status.style.display = 'block';
        }
        
        // Helper function to convert base64url to ArrayBuffer
        function base64urlToBuffer(base64url) {
            const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
            const binary = atob(base64);
            const buffer = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) {
                buffer[i] = binary.charCodeAt(i);
            }
            return buffer.buffer;
        }
        
        async function authenticate() {
            const button = document.getElementById('authButton');
            button.disabled = true;
            button.textContent = 'Authenticating...';
            
            console.log('Starting mobile authentication flow - Direct WebAuthn');
            showStatus('🔐 Authenticating with passkey...', 'loading');
            
            try {
                // Directly trigger WebAuthn authentication (like registration does)
                const publicKeyCredentialRequestOptions = {
                    challenge: base64urlToBuffer(challenge),
                    rpId: rpId,
                    allowCredentials: [{
                        id: base64urlToBuffer(credentialId),
                        type: 'public-key',
                        transports: ['internal', 'hybrid']
                    }],
                    userVerification: 'preferred',
                    timeout: 300000
                };
                
                console.log('Requesting WebAuthn authentication with options:', publicKeyCredentialRequestOptions);
                
                // Get the credential assertion
                const credential = await navigator.credentials.get({
                    publicKey: publicKeyCredentialRequestOptions
                });
                
                console.log('Credential assertion received:', credential);
                
                if (!credential || !credential.response) {
                    throw new Error('Authentication failed - no credential returned');
                }
                
                // Prepare authentication data
                const authData = {
                    credentialId: credential.id,
                    challenge: challenge,
                    authenticatorData: bufferToBase64url(credential.response.authenticatorData),
                    clientDataJSON: bufferToBase64url(credential.response.clientDataJSON),
                    signature: bufferToBase64url(credential.response.signature),
                    userHandle: credential.response.userHandle ? bufferToBase64url(credential.response.userHandle) : null
                };
                
                console.log('Sending authentication data to server');
                
                // Complete authentication
                const response = await fetch('https://api.risaboss.com/functions/v1/passkey-functions?op=auth-complete', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'apikey': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2FsaG9zdCIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzU0Nzg1MDU0LCJleHAiOjE3ODYzMjEwNTR9.UR-amMvudG2h3iBBzBfRPjH6psOhyWYrrq3yhc_s-s4'
                    },
                    body: JSON.stringify(authData)
                });
                
                const result = await response.json();
                console.log('Authentication result:', result);
                
                if (result.success) {
                    showStatus('✅ Authentication successful!', 'success');
                    button.textContent = 'Authentication Complete';
                    
                    // Success feedback
                    document.body.style.background = 'linear-gradient(135deg, #d4edda 0%, #c3e6cb 100%)';
                    
                    // Create manual trigger button
                    const manualButton = document.createElement('button');
                    manualButton.className = 'auth-button';
                    manualButton.textContent = '🔗 Manually Trigger Session Complete';
                    manualButton.style.marginTop = '10px';
                    manualButton.style.backgroundColor = '#28a745';
                    
                    // Add detailed status div
                    const debugDiv = document.createElement('div');
                    debugDiv.style.marginTop = '20px';
                    debugDiv.style.padding = '10px';
                    debugDiv.style.backgroundColor = '#f8f9fa';
                    debugDiv.style.borderRadius = '6px';
                    debugDiv.style.fontSize = '12px';
                    debugDiv.style.textAlign = 'left';
                    debugDiv.innerHTML = 
                        '<strong>Debug Info:</strong><br>' +
                        'SessionId: ' + (sessionId || 'NOT SET') + '<br>' +
                        'UserId: ' + (result.userId || 'NOT SET') + '<br>' +
                        'Challenge: ' + (challenge?.substring(0, 20) || 'NOT SET') + '...<br>' +
                        'Status: Ready for session-complete';
                    
                    document.querySelector('.container').appendChild(debugDiv);
                    document.querySelector('.container').appendChild(manualButton);
                    
                    // Auto-trigger session-complete
                    async function triggerSessionComplete() {
                        console.log('=== TRIGGERING SESSION-COMPLETE ===');
                        console.log('SessionId:', sessionId);
                        console.log('UserId:', result.userId);
                        console.log('Challenge:', challenge);
                        
                        if (!sessionId) {
                            console.error('ERROR: No sessionId available');
                            debugDiv.innerHTML += '<br><span style="color: red;">ERROR: No sessionId available</span>';
                            return;
                        }
                        
                        try {
                            debugDiv.innerHTML += '<br>Calling session-complete...';
                            
                            const sessionResponse = await fetch('https://api.risaboss.com/functions/v1/passkey-functions', {
                                method: 'POST',
                                headers: { 
                                    'Content-Type': 'application/json',
                                    'apikey': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxvY2FsaG9zdCIsInJvbGUiOiJhbm9uIiwiaWF0IjoxNzU0Nzg1MDU0LCJleHAiOjE3ODYzMjEwNTR9.UR-amMvudG2h3iBBzBfRPjH6psOhyWYrrq3yhc_s-s4'
                                },
                                body: JSON.stringify({
                                    op: 'session-complete',
                                    sessionId: sessionId,
                                    userId: result.userId,
                                    challenge: challenge,
                                    status: 'completed'
                                })
                            });
                            
                            console.log('Session-complete HTTP status:', sessionResponse.status);
                            const sessionResult = await sessionResponse.text();
                            console.log('Session-complete response:', sessionResult);
                            
                            if (sessionResponse.ok) {
                                debugDiv.innerHTML += '<br><span style="color: green;">✅ Session-complete SUCCESS: ' + sessionResult + '</span>';
                            } else {
                                debugDiv.innerHTML += '<br><span style="color: red;">❌ Session-complete HTTP ERROR: ' + sessionResponse.status + ' - ' + sessionResult + '</span>';
                            }
                        } catch (sessionError) {
                            console.error('Session-complete network error:', sessionError);
                            debugDiv.innerHTML += '<br><span style="color: red;">❌ Session-complete NETWORK ERROR: ' + sessionError.message + '</span>';
                        }
                    }
                    
                    // Auto-trigger
                    console.log('Auto-triggering session-complete...');
                    triggerSessionComplete();
                    
                    // Manual trigger
                    manualButton.onclick = triggerSessionComplete;
                    
                    // Close window or show completion message after short delay
                    setTimeout(() => {
                        if (window.opener) {
                            window.close();
                        } else {
                            // Just show completion message, don't redirect
                            showStatus('✅ Authentication complete! You can close this tab.', 'success');
                        }
                    }, 1500);
                } else {
                    throw new Error(result.error || 'Authentication failed');
                }
                
            } catch (error) {
                console.error('Authentication error:', error);
                showStatus('❌ Authentication failed: ' + error.message, 'error');
                button.disabled = false;
                button.textContent = 'Try Again';
            }
        }
        
        // Helper function to convert ArrayBuffer to base64url
        function bufferToBase64url(buffer) {
            const bytes = new Uint8Array(buffer);
            let binary = '';
            for (let i = 0; i < bytes.byteLength; i++) {
                binary += String.fromCharCode(bytes[i]);
            }
            return btoa(binary)
                .replace(/[+]/g, '-')
                .replace(/[/]/g, '_')
                .replace(/=/g, '');
        }
    </script>
</body>
</html>`;

    return new Response(html, {
      headers: { 'Content-Type': 'text/html', ...corsHeaders }
    })

  } catch (error) {
    console.error('Mobile authentication error:', error)
    return new Response(
      getMobileErrorHTML('Authentication failed: ' + error.message),
      { 
        status: 500, 
        headers: { 'Content-Type': 'text/html', ...corsHeaders } 
      }
    )
  }
}

export async function handleAuthenticationStatus(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    console.log('=== Authentication Status Check ===')
    
    const body = parsedBody || await req.json()
    console.log('Status check request:', JSON.stringify(body, null, 2))
    
    if (!body.challenge) {
      return new Response(
        JSON.stringify({ success: false, error: 'Missing challenge parameter' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // First try to ensure the completed_authentications table exists
    try {
      await supabase.rpc('create_completed_authentications_table_if_not_exists')
    } catch (tableError) {
      console.log('Note: Could not ensure table exists via RPC, proceeding with direct query')
    }

    // Check if there's a completed authentication for this challenge
    const { data: completedAuth, error: authError } = await supabase
      .from('completed_authentications')
      .select('*')
      .eq('challenge', body.challenge)
      .single()

    if (authError) {
      if (authError.code === 'PGRST116') {
        // No authentication found yet - still pending
        return new Response(
          JSON.stringify({ 
            success: false, 
            status: 'pending',
            message: 'Authentication not completed yet' 
          }),
          { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      } else if (authError.code === '42501' || authError.message?.includes('does not exist')) {
        // Table doesn't exist yet - create it on demand
        console.log('Creating completed_authentications table on demand...')
        
        try {
          const createTableSQL = `
            CREATE TABLE IF NOT EXISTS completed_authentications (
              id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
              challenge TEXT NOT NULL UNIQUE,
              user_id UUID NOT NULL,
              email TEXT,
              session_token TEXT,
              access_token TEXT,
              refresh_token TEXT,
              expires_at BIGINT,
              created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
              expires_at_timestamp TIMESTAMP WITH TIME ZONE DEFAULT (NOW() + INTERVAL '5 minutes')
            );
            
            ALTER TABLE completed_authentications ENABLE ROW LEVEL SECURITY;
            
            CREATE POLICY IF NOT EXISTS "Service role can manage completed authentications"
            ON completed_authentications
            FOR ALL
            TO service_role
            USING (true)
            WITH CHECK (true);
          `
          
          // Since we can't execute raw SQL directly, return pending for now
          // The table creation will be handled by proper migration deployment
          return new Response(
            JSON.stringify({ 
              success: false, 
              status: 'pending',
              message: 'Authentication not completed yet (initializing)' 
            }),
            { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
          )
          
        } catch (createError) {
          console.error('Failed to create table:', createError)
          return new Response(
            JSON.stringify({ 
              success: false, 
              status: 'pending',
              message: 'Authentication not completed yet' 
            }),
            { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
          )
        }
      } else {
        console.error('Database error checking authentication status:', authError)
        return new Response(
          JSON.stringify({ success: false, error: 'Failed to check authentication status' }),
          { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }
    }

    // Authentication found - return the result
    console.log('Authentication completed for challenge:', body.challenge)
    
    // Clean up the completed authentication record
    await supabase
      .from('completed_authentications')
      .delete()
      .eq('challenge', body.challenge)

    return new Response(
      JSON.stringify({
        success: true,
        userId: completedAuth.user_id,
        email: completedAuth.email,
        sessionToken: completedAuth.session_token,
        accessToken: completedAuth.access_token,
        refreshToken: completedAuth.refresh_token,
        expiresAt: completedAuth.expires_at_timestamp ? new Date(completedAuth.expires_at_timestamp).getTime() : null
      }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )

  } catch (error) {
    console.error('Authentication status check error:', error)
    return new Response(
      JSON.stringify({ success: false, error: 'Internal server error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
  }
}




export async function handleStoreAuthResult(req: Request, parsedBody: any = null): Promise<Response> {
  try {
    console.log('=== Store Authentication Result ===')
    
    const body = parsedBody || await req.json()
    console.log('Store auth result request:', JSON.stringify(body, null, 2))
    
    if (!body.challenge || !body.userId) {
      return new Response(
        JSON.stringify({ success: false, error: 'Missing required fields: challenge, userId' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    // Try to store the authentication result for desktop polling
    let insertError = null
    try {
      const { error } = await supabase
        .from('completed_authentications')
        .insert({
          challenge: body.challenge,
          user_id: body.userId,
          email: body.email,
          session_token: body.sessionToken,
          access_token: body.accessToken,
          refresh_token: body.refreshToken,
          expires_at: body.expiresAt,
          expires_at_timestamp: new Date(Date.now() + 5 * 60 * 1000) // Expire in 5 minutes
        })
      insertError = error
    } catch (tableError) {
      insertError = tableError
    }

    if (insertError) {
      console.error('Failed to store authentication result:', insertError)
      // If it's a table doesn't exist error, just return success for now
      // The authentication still completed successfully in the browser
      if (insertError.code === '42501' || insertError.message?.includes('does not exist')) {
        console.log('Table does not exist yet, but authentication completed successfully')
        return new Response(
          JSON.stringify({ success: true, message: 'Authentication completed (table not available)' }),
          { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
        )
      }
      
      return new Response(
        JSON.stringify({ success: false, error: 'Failed to store authentication result' }),
        { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      )
    }

    console.log('Authentication result stored successfully for challenge:', body.challenge.substring(0, 10) + '...')
    
    return new Response(
      JSON.stringify({ success: true, message: 'Authentication result stored' }),
      { status: 200, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )

  } catch (error) {
    console.error('Store auth result error:', error)
    return new Response(
      JSON.stringify({ success: false, error: 'Internal server error' }),
      { status: 500, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
    )
  }
}

// ============================================================================
// MAIN HANDLER (from index.ts)
// ============================================================================

export default async function handler(req: Request): Promise<Response> {
  const url = new URL(req.url)
  
  if (req.method === 'OPTIONS') {
    return new Response(null, {
      status: 200,
      headers: corsHeaders
    })
  }
  
  // Health check for root path
  if (url.pathname === '/' && req.method === 'GET') {
    return new Response(JSON.stringify({ 
      success: true, 
      message: 'Passkey Functions server is healthy',
      timestamp: new Date().toISOString(),
      available_operations: [
        'auth-challenge', 'auth-complete',
        'register-challenge', 'register-complete', 
        'mobile-register', 'session-status', 'session-complete',
        'list', 'delete'
      ]
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    })
  }
  
  let operation = url.searchParams.get('operation') || url.searchParams.get('op')
  let parsedBody = null
  
  // Always try to parse the body for operation, especially for POST requests
  if (req.method === 'POST') {
    try {
      parsedBody = await req.json()
      operation = operation || parsedBody.op || parsedBody.operation
    } catch (e) {
      // Ignore JSON parse errors for non-JSON requests
      console.log('Failed to parse JSON body:', e.message)
    }
  }
  
  // Fallback to pathname only if no operation found
  if (!operation || operation === '') {
    operation = url.pathname.slice(1)
  }
  
  console.log(`🔵 Passkey Functions Handler - Operation: ${operation}`)
  
  try {
    switch (operation) {
      case 'auth-challenge':
        return await handlePasskeyAuthenticationChallenge(req, parsedBody)
      case 'auth-complete':
        return await handlePasskeyAuthenticationComplete(req, parsedBody)
      case 'register-challenge':
      case 'reg-challenge':
        return await handlePasskeyRegistrationChallenge(req, parsedBody)
      case 'register-complete':
      case 'reg-complete':
        return await handlePasskeyRegistrationComplete(req, parsedBody)
      case 'mobile-register':
        return await handleMobileRegistration(req)
      case 'mobile-auth':
        return await handleMobileAuthentication(req)
      case 'session-status':
        return await handleSessionStatus(req)
      case 'session-complete':
        return await handleSessionComplete(req, parsedBody)
      case 'desktop-webauthn':
        return await handleDesktopWebAuthn(req)
      case 'auth-status':
        return await handleAuthenticationStatus(req, parsedBody)
      case 'store-auth-result':
        return await handleStoreAuthResult(req, parsedBody)
      case 'list':
        return await handlePasskeyManagementList(req, parsedBody)
      case 'delete':
        return await handlePasskeyManagementDelete(req, parsedBody)
      default:
        return new Response(JSON.stringify({ 
          success: false, 
          error: 'Unknown operation',
          available_operations: [
            'auth-challenge', 'auth-complete',
            'register-challenge', 'register-complete', 
            'mobile-register', 'mobile-auth', 'session-status', 'session-complete',
            'desktop-webauthn', 'list', 'delete'
          ]
        }), {
          status: 404,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        })
    }
  } catch (error) {
    console.error('Passkey function error:', error)
    console.error('Error stack:', error.stack)
    console.error('Error message:', error.message)
    return new Response(JSON.stringify({ 
      success: false, 
      error: 'Internal server error',
      details: error.message,
      operation: operation
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', ...corsHeaders }
    })
  }
}

// ============================================================================
// MAIN ENTRY POINT
// ============================================================================

console.log('Starting Passkey Functions server...')
serve(handler, { port: 8000 })