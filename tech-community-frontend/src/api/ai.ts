import { getStoredToken } from './http'
import type { ChatStream } from './types'

export interface ChatReq {
  sessionId: string
  question: string
}

export async function streamChat(
  req: ChatReq,
  onChunk: (chunk: ChatStream) => void,
  signal?: AbortSignal
) {
  const token = getStoredToken()
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { satoken: token } : {})
    },
    body: JSON.stringify(req),
    signal
  })

  if (!response.ok || !response.body) {
    throw new Error('AI 响应连接失败')
  }

  const decoder = new TextDecoder('utf-8')
  const reader = response.body.getReader()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      emitSseBlock(block, onChunk)
    }
  }

  if (buffer.trim()) {
    emitSseBlock(buffer, onChunk)
  }
}

function emitSseBlock(block: string, onChunk: (chunk: ChatStream) => void) {
  const data = block
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  if (!data || data === '[DONE]') {
    return
  }

  const chunk = JSON.parse(data) as ChatStream
  onChunk(chunk)
}
