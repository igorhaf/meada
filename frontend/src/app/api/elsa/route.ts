import { NextRequest } from 'next/server'
import { MEADA_SYSTEM_PROMPT } from '@/lib/meada-prompt'

export async function POST(req: NextRequest) {
  const { message, session_id } = await req.json()

  const claudioEndpoint = process.env.CLAUDE_ADDRESS ?? 'http://claudio.local/v1/messages'

  const upstream = await fetch(claudioEndpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'x-api-key': '123456789',
    },
    body: JSON.stringify({
      model: 'claude-haiku-4-5',
      max_tokens: 1024,
      stream: true,
      system: MEADA_SYSTEM_PROMPT,
      session_key: session_id,
      messages: [{ role: 'user', content: message }],
    }),
  })

  if (!upstream.ok) {
    return new Response(JSON.stringify({ error: 'upstream error' }), { status: 502 })
  }

  return new Response(upstream.body, {
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
    },
  })
}
