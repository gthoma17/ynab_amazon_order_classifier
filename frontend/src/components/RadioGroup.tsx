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
}

export default function RadioGroup<T extends string>({
  options,
  value,
  onChange,
  name,
  ariaLabel,
}: RadioGroupProps<T>) {
  return (
    <div className="cf-radio-group" role="radiogroup" aria-label={ariaLabel}>
      {options.map((opt) => {
        const selected = opt.value === value
        return (
          <label
            key={opt.value}
            className={`cf-radio-option${selected ? ' cf-radio-option--selected' : ''}`}
            data-selected={selected ? 'true' : undefined}
          >
            <input
              type="radio"
              name={name}
              value={opt.value}
              checked={selected}
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
