import { useEffect, useRef, useState } from 'react'

interface SplitFlapSlotProps {
  message: string | null
  color?: 'green' | 'red'
  testId?: string
  messageTestId?: string
}

type Phase = 'idle' | 'flip-out' | 'flip-in' | 'shown'

export default function SplitFlapSlot({
  message,
  color = 'green',
  testId,
  messageTestId,
}: SplitFlapSlotProps) {
  const [phase, setPhase] = useState<Phase>(message ? 'shown' : 'idle')
  const [displayed, setDisplayed] = useState<string | null>(message)
  const prevMessageRef = useRef(message)

  useEffect(() => {
    if (message === prevMessageRef.current) return
    prevMessageRef.current = message

    setPhase('flip-out')

    const t1 = setTimeout(() => {
      setDisplayed(message)
      setPhase('flip-in')
    }, 100)

    const t2 = setTimeout(() => {
      setPhase(message ? 'shown' : 'idle')
    }, 200)

    return () => {
      clearTimeout(t1)
      clearTimeout(t2)
    }
  }, [message])

  const isIdle = displayed === null

  return (
    <div className="cf-splitflap" data-testid={testId}>
      <div
        className={`cf-splitflap-face${phase === 'flip-out' ? ' cf-splitflap--flip-out' : phase === 'flip-in' ? ' cf-splitflap--flip-in' : ''}`}
        data-color={isIdle ? undefined : color}
      >
        {isIdle ? (
          <span className="cf-splitflap-idle" aria-hidden="true" />
        ) : (
          <span className="cf-splitflap-message" data-testid={messageTestId}>
            {displayed}
          </span>
        )}
      </div>
    </div>
  )
}
