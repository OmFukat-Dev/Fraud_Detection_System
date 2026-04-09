/// <reference types="vite/client" />
import { useEffect } from 'react'

export function useSse(onMessage: (data: any) => void) {
  useEffect(() => {
    const url = import.meta.env.VITE_SSE_URL
    if (!url) return

    const es = new EventSource(url)
    es.onmessage = (evt) => {
      try {
        onMessage(JSON.parse(evt.data))
      } catch {
        onMessage(evt.data)
      }
    }
    return () => es.close()
  }, [onMessage])
}
