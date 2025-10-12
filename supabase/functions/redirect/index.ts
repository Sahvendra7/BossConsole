/**
 * Redirect Edge Function
 *
 * Converts Supabase magic link URLs to boss:// deep links for the BOSS application.
 * Used in email templates to redirect users from email clients to the desktop app.
 *
 * Routes:
 * - GET /?token=<token>&type=<type> - Redirect with explicit token (simple)
 * - GET /?url=<supabase-confirmation-url> - Redirect with full Supabase URL (recommended)
 * - GET /health - Health check
 *
 * Usage in email template:
 * Wrap {{ .ConfirmationURL }} with redirect function:
 * https://api.risaboss.com/functions/v1/redirect?url={{ .ConfirmationURL }}
 *
 * This extracts the token from Supabase's URL and converts to boss://auth/verify
 */

import { OpenAPIHono } from "@hono/zod-openapi"
import { cors } from "hono/cors"

const app = new OpenAPIHono().basePath("/redirect")

// CORS configuration
app.use("*", cors({
  origin: "*",
  allowMethods: ["GET", "OPTIONS"],
  allowHeaders: ["Content-Type"],
  maxAge: 600,
}))

/**
 * Generates HTML page that auto-redirects to boss:// deep link
 * Design matches the magic-link email template
 */
function generateRedirectPage(deepLink: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Opening BOSS App</title>
    <style>
        body {
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background-color: #1a1a1a;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
        }
        .container {
            width: 100%;
            max-width: 600px;
            background-color: #2B2B2B;
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
            margin: 20px;
        }
        .header {
            padding: 40px 40px 30px;
            text-align: center;
            border-bottom: 1px solid #4D4D4D;
        }
        .header h1 {
            margin: 0;
            color: #F2F2F2;
            font-size: 28px;
            font-weight: 600;
            letter-spacing: -0.5px;
        }
        .header p {
            margin: 8px 0 0;
            color: #AAAAAA;
            font-size: 14px;
            letter-spacing: 0.5px;
        }
        .content {
            padding: 40px;
            text-align: center;
        }
        .content h2 {
            margin: 0 0 16px;
            color: #F2F2F2;
            font-size: 24px;
            font-weight: 600;
        }
        .content p {
            margin: 0 0 24px;
            color: #AAAAAA;
            font-size: 16px;
            line-height: 1.5;
        }
        .spinner {
            border: 3px solid #4D4D4D;
            border-radius: 50%;
            border-top: 3px solid #3592C4;
            width: 50px;
            height: 50px;
            animation: spin 1s linear infinite;
            margin: 30px auto;
        }
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        .button {
            display: inline-block;
            padding: 16px 48px;
            background-color: #3592C4;
            color: #FFFFFF;
            text-decoration: none;
            border-radius: 6px;
            font-size: 16px;
            font-weight: 600;
            letter-spacing: 0.3px;
            box-shadow: 0 2px 8px rgba(53, 146, 196, 0.3);
            transition: background-color 0.2s;
        }
        .button:hover {
            background-color: #2d7aa8;
        }
        .notice {
            margin: 30px 40px;
            padding: 16px 20px;
            background-color: #3C3F41;
            border-radius: 6px;
            border-left: 3px solid #43A047;
        }
        .notice p:first-child {
            margin: 0;
            color: #F2F2F2;
            font-size: 13px;
            font-weight: 600;
        }
        .notice p:last-child {
            margin: 8px 0 0;
            color: #AAAAAA;
            font-size: 13px;
            line-height: 1.5;
        }
        .footer {
            padding: 30px 40px;
            text-align: center;
            border-top: 1px solid #4D4D4D;
        }
        .footer p:first-child {
            margin: 0;
            color: #AAAAAA;
            font-size: 13px;
            line-height: 1.5;
        }
        .footer p:last-child {
            margin: 12px 0 0;
            color: #666666;
            font-size: 12px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>BOSS Console</h1>
            <p>Business Operating System as Service</p>
        </div>
        <div class="content">
            <h2>Opening BOSS App</h2>
            <div class="spinner"></div>
            <p>Redirecting you to the BOSS application...</p>
            <p style="margin-bottom: 16px; color: #AAAAAA; font-size: 14px;">If the app doesn't open automatically:</p>
            <a href="${deepLink}" class="button">Open BOSS App</a>
        </div>
        <div class="notice">
            <p>🔒 Secure Authentication</p>
            <p>You're being securely redirected to your BOSS application. This link is valid for one-time use only.</p>
        </div>
        <div class="footer">
            <p>BOSS Console - Business Operating System as Service</p>
            <p>© 2025 BOSS. All rights reserved.</p>
        </div>
    </div>
    <script>
        // Single redirect after a brief delay to show the page
        setTimeout(() => {
            window.location.href = '${deepLink}';
        }, 1000);
    </script>
</body>
</html>`
}

// Health check endpoint
app.get("/health", (c) => {
  return c.json({ status: "healthy", timestamp: new Date().toISOString() }, 200)
})

// Main redirect endpoint
app.get("/", (c) => {
  // Get token directly from query param (simple case)
  let token = c.req.query("token")
  let type = c.req.query("type") || "magiclink"

  // If no token found, try to extract from full Supabase confirmation URL
  // This handles the case where the entire Supabase URL is passed as a query param
  if (!token) {
    const url = c.req.query("url")
    if (url) {
      try {
        const parsedUrl = new URL(url)
        token = parsedUrl.searchParams.get("token") || undefined
        type = parsedUrl.searchParams.get("type") || "magiclink"
      } catch (_e) {
        // Invalid URL format
      }
    }
  }

  if (!token) {
    return c.json({
      error: "Missing 'token' parameter",
      usage: "/?token=<token>&type=<type> OR /?url=<supabase-confirmation-url>",
      example: "/?token=abc123&type=magiclink"
    }, 400)
  }

  // Build deep link
  const deepLink = `boss://auth/verify?token=${encodeURIComponent(token)}&type=${encodeURIComponent(type)}`

  // Return HTML redirect page
  const html = generateRedirectPage(deepLink)
  return c.html(html)
})

// 404 handler
app.notFound((c) => {
  return c.json({ error: "Not Found" }, 404)
})

// Global error handler
app.onError((err, c) => {
  console.error("Global error:", err)
  return c.json({ error: err.message }, 500)
})

Deno.serve(app.fetch)
