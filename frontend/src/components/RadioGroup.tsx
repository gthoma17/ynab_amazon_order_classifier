interface RadioOption<T extends string> {
  value: T
  label: string
}

interface RadioGroupProps<T extends string> {
  options: RadioOption<T>[]
  value: T
  onChange: (value: T) => void
  name: string
  ariaLabel?: string
  testIdPrefix?: string
  testId?: string
  disabled?: boolean
  columns?: number | string
  className?: string
}

export default function RadioGroup<T extends string>({
  options,
  value,
  onChange,
  name,
  ariaLabel,
  testIdPrefix,
  testId,
  disabled,
  columns,
  className,
}: RadioGroupProps<T>) {
  const gridStyle = columns
    ? { gridTemplateColumns: typeof columns === 'string' ? columns : `repeat(${columns}, 1fr)` }
    : undefined
  return (
    <div
      className={`cf-radio-group${disabled ? ' cf-radio-group--disabled' : ''}${className ? ` ${className}` : ''}`}
      role="radiogroup"
      aria-label={ariaLabel}
      data-testid={testId}
      style={gridStyle}
    >
      {options.map((opt) => {
        const selected = opt.value === value
        return (
          <label
            key={opt.value}
            className={`cf-radio-option${selected ? ' cf-radio-option--selected' : ''}`}
            data-selected={selected ? 'true' : undefined}
            data-testid={testIdPrefix ? `${testIdPrefix}-${opt.value}` : undefined}
          >
            <input
              type="radio"
              name={name}
              value={opt.value}
              checked={selected}
              disabled={disabled}
              onChange={() => onChange(opt.value)}
              className="cf-radio-input"
            />
            <span
              className="cf-radio-lamp"
              data-selected={selected ? 'true' : undefined}
              aria-hidden="true"
            />
            <span className="cf-radio-label">{opt.label}</span>
          </label>
        )
      })}
    </div>
  )
}
