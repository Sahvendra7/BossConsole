// Simple Hello World Edge Function
console.log('DEBUG: Function file loading started')

console.log('DEBUG: About to start Deno.serve')

// Use Deno.serve for regular edge functions (not --main-service)
Deno.serve(async (req: Request): Promise<Response> => {
  console.log('Hello World function called:', req.method, req.url)
  
  const { method, url } = req
  const urlObj = new URL(url)
  
  // Handle CORS preflight
  if (method === 'OPTIONS') {
    return new Response(null, {
      status: 200,
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
        'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      },
    })
  }
  
  // Get query parameters
  const name = urlObj.searchParams.get('name') || 'World'
  const timestamp = new Date().toISOString()
  
  const responseData = {
    message: `Hello, ${name}! 🎉`,
    timestamp,
    method,
    path: urlObj.pathname,
    userAgent: req.headers.get('user-agent'),
    environment: 'Supabase Edge Functions on Kubernetes',
    customImage: 'risashivang/edge-runtime:configurable-path',
    configurable_path: '/home/deno/functions',
    success: true
  }
  
  return new Response(JSON.stringify(responseData, null, 2), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Access-Control-Allow-Origin': '*',
    },
  })
})

console.log('DEBUG: Deno.serve setup completed')