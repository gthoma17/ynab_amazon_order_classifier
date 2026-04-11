import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'

export interface ColumnDef<T> {
  key: string
  header: string
  /**
   * Fixed pixel width. Omit on the last column to fill remaining space.
   */
  width?: number
  render?: (row: T) => ReactNode
}

interface SequencePrinterProps<T extends { id: number }> {
  columns: ColumnDef<T>[]
  entries: T[]
  className?: string
  'data-testid'?: string
}

function colStyle(width: number | undefined): CSSProperties {
  return width != null
    ? { width, flexShrink: 0 }
    : { flex: '1 1 auto', overflow: 'hidden', textOverflow: 'ellipsis' }
}

const LINE_H = 24 // must match .cf-ser-entry line-height
const VISIBLE_LINES = 15 // entries that animate into view

export default function SequencePrinter<T extends { id: number }>({
  columns,
  entries,
  className,
  ...rest
}: SequencePrinterProps<T>) {
  const feedRef = useRef<HTMLDivElement>(null)
  const [printed, setPrinted] = useState(false)

  const hasEntries = entries.length > 0
  const printing = hasEntries && !printed

  // Collapse the spacer once the paper has finished advancing
  useEffect(() => {
    if (!printing || !feedRef.current) return
    const feed = feedRef.current
    const onEnd = () => setPrinted(true)
    feed.addEventListener('animationend', onEnd)
    return () => feed.removeEventListener('animationend', onEnd)
  }, [printing])

  const renderEntry = (entry: T) => (
    <div key={entry.id} className="cf-ser-entry" data-testid="ser-entry">
      {columns.map((col) => {
        const raw = (entry as Record<string, unknown>)[col.key]
        return (
          <span key={col.key} className="cf-ser-col" style={colStyle(col.width)}>
            {col.render
              ? col.render(entry)
              : raw != null
                ? String(raw)
                : '\u2014'}
          </span>
        )
      })}
    </div>
  )

  // Dynamic offset: only animate through as many entries as exist (up to VISIBLE_LINES)
  const animatedCount = Math.min(VISIBLE_LINES, entries.length)
  const feedStyle: CSSProperties | undefined = printing
    ? ({ '--cf-ser-offset': `${animatedCount * LINE_H}px` } as CSSProperties)
    : undefined

  return (
    <div className={`cf-ser${className ? ` ${className}` : ''}`} {...rest}>
      <div className="cf-ser-label-strip" aria-hidden="true">
        {columns.map((col) => (
          <span key={col.key} className="cf-ser-col" style={colStyle(col.width)}>
            {col.header}
          </span>
        ))}
      </div>

      <div className="cf-ser-slot" role="log" aria-label="Sequence of events recorder">
        <div className={`cf-ser-paper${printing ? ' cf-ser-paper--printing' : ''}`}>
          <div
            ref={feedRef}
            className={`cf-ser-feed${printing ? ' cf-ser-feed--printing' : ''}`}
            style={feedStyle}
          >
            {/* Animated batch: these scroll into view from above during the
                paper-advance animation */}
            {entries.slice(0, VISIBLE_LINES).map(renderEntry)}

            {/* Spacer: same height as the slot. Shows "NO ENTRIES" when idle,
                scrolls out the bottom during animation, removed after. */}
            {!printed && (
              <div className="cf-ser-spacer">
                &mdash;&ensp;NO ENTRIES&ensp;&mdash;
              </div>
            )}

            {/* Remaining entries: positioned below the spacer during animation
                (invisible), contiguous with the animated batch after spacer
                is removed */}
            {entries.slice(VISIBLE_LINES).map(renderEntry)}
          </div>
        </div>
      </div>
    </div>
  )
}
