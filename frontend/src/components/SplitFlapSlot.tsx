import { useEffect, useRef, useState } from 'react'

interface SplitFlapSlotProps {
  message: string | null
  color?: 'green' | 'red'
  testId?: string
  messageTestId?: string
}

interface CharState {
  char: string
  phase: 'idle' | 'flip-out' | 'flip-in' | 'shown'
}

const CHAR_DELAY = 45 // ms between each character flip start
const HALF_FLIP = 75 // ms for each half of the flip (out or in)
const FULL_FLIP = HALF_FLIP * 2

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

    animationRef.current.forEach(clearTimeout)
    animationRef.current = []

    if (message === null) {
      const t = setTimeout(() => {
        setDisplayed(null)
        setCharStates([])
      }, 0)
      animationRef.current.push(t)
      return
    }

    const targetMessage = message.padEnd(20, ' ')
    const targetChars = targetMessage.split('')

    // Initialize every cell as blank — no character is visible until its flap completes
    const initialStates: CharState[] = targetChars.map(() => ({
      char: ' ',
      phase: 'idle',
    }))

    const t0 = setTimeout(() => {
      setDisplayed(targetMessage) // make display div (and its data-testid) visible
      setCharStates(initialStates)
    }, 0)
    animationRef.current.push(t0)

    targetChars.forEach((targetChar, index) => {
      const flipStart = index * CHAR_DELAY

      // Phase 1: blank char folds away (rotateX 0 → -90°)
      const t1 = setTimeout(() => {
        setCharStates((prev) => {
          const next = [...prev]
          next[index] = { char: ' ', phase: 'flip-out' }
          return next
        })
      }, flipStart)

      // Phase 2: element is edge-on and invisible — swap text, fold new char in (-90 → 0°)
      const t2 = setTimeout(() => {
        setCharStates((prev) => {
          const next = [...prev]
          next[index] = { char: targetChar, phase: 'flip-in' }
          return next
        })
      }, flipStart + HALF_FLIP)

      // Phase 3: animation complete, character is fully visible
      const t3 = setTimeout(() => {
        setCharStates((prev) => {
          const next = [...prev]
          next[index] = { char: targetChar, phase: 'shown' }
          return next
        })
      }, flipStart + FULL_FLIP)

      animationRef.current.push(t1, t2, t3)
    })

    return () => {
      animationRef.current.forEach(clearTimeout)
      animationRef.current = []
    }
  }, [message])

  const isIdle = displayed === null

  const phaseClass = (phase: CharState['phase']) => {
    if (phase === 'flip-out') return 'cf-splitflap-char--flip-out'
    if (phase === 'flip-in') return 'cf-splitflap-char--flip-in'
    return ''
  }

  return (
    <div className="cf-splitflap" data-testid={testId}>
      {isIdle ? (
        <div className="cf-splitflap-idle-container">
          <span className="cf-splitflap-idle" aria-hidden="true" />
        </div>
      ) : (
        <div className="cf-splitflap-display" data-testid={messageTestId}>
          {/* Visually-hidden span gives immediate accessible text and allows tests
              to assert message content without waiting for the full animation */}
          <span className="cf-visually-hidden">{displayed?.trim()}</span>
          {charStates.map((state, index) => (
            <div
              key={index}
              className={`cf-splitflap-char ${phaseClass(state.phase)}`}
              data-color={color}
              aria-hidden="true"
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
