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
  const [displayedColor, setDisplayedColor] = useState<'green' | 'red'>(color)
  const [charStates, setCharStates] = useState<CharState[]>(() => {
    if (message === null) return []
    const padded = message.padEnd(Math.max(20, message.length), ' ')
    return padded.split('').map((char) => ({ char, phase: 'shown' as const }))
  })
  const prevMessageRef = useRef(message)
  const animationRef = useRef<number[]>([])
  // Set to true by the cleanup function so StrictMode's cleanup→remount cycle is
  // distinguished from a same-message re-render (e.g. colour-only change).
  const cleanupCalledRef = useRef(false)

  useEffect(() => {
    const wasCleanedUp = cleanupCalledRef.current
    cleanupCalledRef.current = false

    if (message === prevMessageRef.current && !wasCleanedUp) return

    const oldMessage = prevMessageRef.current
    prevMessageRef.current = message

    animationRef.current.forEach(clearTimeout)
    animationRef.current = []

    const targetPadded =
      message === null ? null : message.padEnd(Math.max(20, message.length), ' ')
    const targetChars = targetPadded?.split('') ?? []

    const oldPadded =
      oldMessage === null ? '' : oldMessage.padEnd(Math.max(20, oldMessage.length), ' ')
    const oldChars = oldPadded.split('')

    const wasIdle = oldMessage === null
    const numPositions = Math.max(oldChars.length, targetChars.length)

    if (wasIdle) {
      // ── Null → Message ─────────────────────────────────────────────────
      // Initialize blank slots, then flip each char in (existing behaviour).
      const blankStates: CharState[] = targetChars.map(() => ({ char: ' ', phase: 'idle' }))

      const t0 = setTimeout(() => {
        setDisplayed(targetPadded)
        setDisplayedColor(color)
        setCharStates(blankStates)
      }, 0)
      animationRef.current.push(t0)

      targetChars.forEach((newChar, index) => {
        const flipStart = index * CHAR_DELAY

        const t1 = setTimeout(() => {
          setCharStates((prev) => {
            const next = [...prev]
            next[index] = { char: ' ', phase: 'flip-out' }
            return next
          })
        }, flipStart)

        const t2 = setTimeout(() => {
          setCharStates((prev) => {
            const next = [...prev]
            next[index] = { char: newChar, phase: 'flip-in' }
            return next
          })
        }, flipStart + HALF_FLIP)

        const t3 = setTimeout(() => {
          setCharStates((prev) => {
            const next = [...prev]
            next[index] = { char: newChar, phase: 'shown' }
            return next
          })
        }, flipStart + FULL_FLIP)

        animationRef.current.push(t1, t2, t3)
      })
    } else {
      // ── Message → Message  or  Message → Null ──────────────────────────
      // Reset each position to the committed old char so the flip-out starts
      // from a clean 'shown' state even if a previous animation was interrupted.
      const initialStates: CharState[] = Array.from({ length: numPositions }, (_, i) => ({
        char: oldChars[i] ?? ' ',
        phase: 'shown',
      }))
      setCharStates(initialStates)

      // Update accessible text and colour for the incoming message immediately.
      setDisplayed(targetPadded)
      if (targetPadded !== null) setDisplayedColor(color)

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

      // If transitioning to idle, clear the char grid once the last flip finishes.
      if (targetPadded === null) {
        const lastFlipEnd = (numPositions - 1) * CHAR_DELAY + FULL_FLIP
        const tClear = setTimeout(() => {
          setCharStates([])
        }, lastFlipEnd)
        animationRef.current.push(tClear)
      }
    }

    return () => {
      animationRef.current.forEach(clearTimeout)
      animationRef.current = []
      cleanupCalledRef.current = true
    }
  }, [message, color])

  // Show idle dashes only when there are no character slots to render.
  const isIdle = charStates.length === 0

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
        <div className="cf-splitflap-display" data-testid={displayed !== null ? messageTestId : undefined}>
          {/* Visually-hidden span gives immediate accessible text and allows tests
              to assert message content without waiting for the full animation */}
          <span className="cf-visually-hidden">{displayed?.trim()}</span>
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
      )}
    </div>
  )
}
