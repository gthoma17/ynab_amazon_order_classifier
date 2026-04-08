import { useEffect, useRef, useState } from 'react'

interface SplitFlapSlotProps {
  message: string | null
  color?: 'green' | 'red'
  testId?: string
  messageTestId?: string
}

interface CharState {
  char: string
  phase: 'idle' | 'flipping' | 'shown'
}

export default function SplitFlapSlot({
  message,
  color = 'green',
  testId,
  messageTestId,
}: SplitFlapSlotProps) {
  const [displayed, setDisplayed] = useState<string | null>(message)
  const [charStates, setCharStates] = useState<CharState[]>([])
  const prevMessageRef = useRef(message)
  const animationRef = useRef<number[]>([])

  useEffect(() => {
    if (message === prevMessageRef.current) return
    prevMessageRef.current = message

    // Clear any existing animations
    animationRef.current.forEach(clearTimeout)
    animationRef.current = []

    if (message === null) {
      // Flip back to idle state - schedule to avoid sync setState in effect
      const t = setTimeout(() => {
        setDisplayed(null)
        setCharStates([])
      }, 0)
      animationRef.current.push(t)
      return
    }

    // Pad message to consistent width for better visual effect
    const targetMessage = message.padEnd(20, ' ')
    const targetChars = targetMessage.split('')

    // Initialize all characters as idle
    const initialStates: CharState[] = targetChars.map((char) => ({
      char,
      phase: 'idle',
    }))

    // Schedule the state updates to avoid sync setState in effect
    const t0 = setTimeout(() => {
      setCharStates(initialStates)
      setDisplayed(targetMessage)
    }, 0)
    animationRef.current.push(t0)

    // Flip each character sequentially with mechanical timing
    targetChars.forEach((_, index) => {
      const flipDelay = index * 45 // 45ms between each character flip
      const flipDuration = 150 // Each flip takes 150ms

      // Start flip
      const t1 = setTimeout(() => {
        setCharStates((prev) => {
          const next = [...prev]
          next[index] = { ...next[index], phase: 'flipping' }
          return next
        })
      }, flipDelay)

      // End flip
      const t2 = setTimeout(() => {
        setCharStates((prev) => {
          const next = [...prev]
          next[index] = { ...next[index], phase: 'shown' }
          return next
        })
      }, flipDelay + flipDuration)

      animationRef.current.push(t1, t2)
    })

    return () => {
      animationRef.current.forEach(clearTimeout)
      animationRef.current = []
    }
  }, [message])

  const isIdle = displayed === null

  return (
    <div className="cf-splitflap" data-testid={testId}>
      {isIdle ? (
        <div className="cf-splitflap-idle-container">
          <span className="cf-splitflap-idle" aria-hidden="true" />
        </div>
      ) : (
        <div className="cf-splitflap-display" data-testid={messageTestId}>
          {charStates.map((state, index) => (
            <div
              key={index}
              className={`cf-splitflap-char ${state.phase === 'flipping' ? 'cf-splitflap-char--flipping' : ''}`}
              data-color={color}
            >
              <span className="cf-splitflap-char-content">{state.char}</span>
              <div className="cf-splitflap-char-divider" aria-hidden="true" />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
