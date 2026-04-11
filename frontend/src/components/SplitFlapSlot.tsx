import { useEffect, useRef, useState } from 'react'

interface SplitFlapSlotProps {
  message: string | null
  color?: 'green' | 'red' | 'yellow'
  testId?: string
  messageTestId?: string
}

interface CharState {
  char: string
  phase: 'flip-out' | 'flip-in' | 'shown'
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
  // Treat null as "" — always display a full-width padded slot, never collapse.
  const resolved = message ?? ''
  const [displayed, setDisplayed] = useState<string>(resolved)
  const [displayedColor, setDisplayedColor] = useState<'green' | 'red' | 'yellow'>(color)
  const [charStates, setCharStates] = useState<CharState[]>(() => {
    const padded = resolved.padEnd(Math.max(20, resolved.length), ' ')
    return padded.split('').map((char) => ({ char, phase: 'shown' as const }))
  })
  const prevMessageRef = useRef(resolved)
  const animationRef = useRef<number[]>([])
  // Set to true by the cleanup function so StrictMode's cleanup→remount cycle is
  // distinguished from a same-message re-render (e.g. colour-only change).
  const cleanupCalledRef = useRef(false)

  useEffect(() => {
    const wasCleanedUp = cleanupCalledRef.current
    cleanupCalledRef.current = false

    const resolvedMessage = message ?? ''

    if (resolvedMessage === prevMessageRef.current && !wasCleanedUp) return

    const oldMessage = prevMessageRef.current
    prevMessageRef.current = resolvedMessage

    animationRef.current.forEach(clearTimeout)
    animationRef.current = []

    const targetPadded = resolvedMessage.padEnd(Math.max(20, resolvedMessage.length), ' ')
    const targetChars = targetPadded.split('')

    const oldPadded = oldMessage.padEnd(Math.max(20, oldMessage.length), ' ')
    const oldChars = oldPadded.split('')

    const numPositions = Math.max(oldChars.length, targetChars.length)

    // Reset each position to the committed old char so the flip-out starts
    // from a clean 'shown' state even if a previous animation was interrupted.
    const initialStates: CharState[] = Array.from({ length: numPositions }, (_, i) => ({
      char: oldChars[i] ?? ' ',
      phase: 'shown',
    }))
    setCharStates(initialStates)

    // Update accessible text and colour for the incoming message immediately.
    setDisplayed(targetPadded)
    setDisplayedColor(color)

    for (let index = 0; index < numPositions; index++) {
      const oldChar = oldChars[index] ?? ' '
      const newChar = targetChars[index] ?? ' '
      const flipStart = index * CHAR_DELAY

      // Phase 1 — old char folds away
      const t1 = setTimeout(() => {
        setCharStates((prev) => {
          const next = [...prev]
          next[index] = { char: oldChar, phase: 'flip-out' }
          return next
        })
      }, flipStart)

      // Phase 2 — new char (or space) folds in
      const t2 = setTimeout(() => {
        setCharStates((prev) => {
          const next = [...prev]
          next[index] = { char: newChar, phase: 'flip-in' }
          return next
        })
      }, flipStart + HALF_FLIP)

      // Phase 3 — animation complete
      const t3 = setTimeout(() => {
        setCharStates((prev) => {
          const next = [...prev]
          next[index] = { char: newChar, phase: 'shown' }
          return next
        })
      }, flipStart + FULL_FLIP)

      animationRef.current.push(t1, t2, t3)
    }

    return () => {
      animationRef.current.forEach(clearTimeout)
      animationRef.current = []
      cleanupCalledRef.current = true
    }
  }, [message, color])

  const phaseClass = (phase: CharState['phase']) => {
    if (phase === 'flip-out') return 'cf-splitflap-char--flip-out'
    if (phase === 'flip-in') return 'cf-splitflap-char--flip-in'
    return ''
  }

  return (
    <div className="cf-splitflap" data-testid={testId}>
      <div
        className="cf-splitflap-display"
        data-testid={message !== null ? messageTestId : undefined}
      >
        {/* Visually-hidden span gives immediate accessible text and allows tests
            to assert message content without waiting for the full animation */}
        <span className="cf-visually-hidden">{displayed.trim()}</span>
        {charStates.map((state, index) => (
          <div
            key={index}
            className={`cf-splitflap-char ${phaseClass(state.phase)}`}
            data-color={displayedColor}
            aria-hidden="true"
          >
            <span className="cf-splitflap-char-content">{state.char}</span>
            <div className="cf-splitflap-char-divider" aria-hidden="true" />
          </div>
        ))}
      </div>
    </div>
  )
}
