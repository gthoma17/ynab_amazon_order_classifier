import type { HTMLAttributes } from 'react'

interface CrtPanelProps extends HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode
}

export default function CrtPanel({ children, className, ...rest }: CrtPanelProps) {
  const cls = ['cf-crt', className].filter(Boolean).join(' ')
  return (
    <div className={cls} {...rest}>
      {children}
    </div>
  )
}
