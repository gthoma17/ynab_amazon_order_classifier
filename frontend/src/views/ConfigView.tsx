import { useEffect, useState } from 'react'
import { apiGet, apiPost, apiPostWithBody, apiPut } from '../api'
import IndicatorPanel from '../components/IndicatorPanel'
import RadioGroup from '../components/RadioGroup'

interface ApiKeysResponse {
  ynabToken: string | null
  ynabBudgetId: string | null
  fastmailApiToken: string | null
  geminiKey: string | null
}

interface ApiKeyValues {
  ynabToken: string
  ynabBudgetId: string
  fastmailApiToken: string
  geminiKey: string
}

interface ProbeResult {
  success: boolean
  message: string
}

type ProbeStatus = 'idle' | 'testing' | 'success' | 'error'

interface ProbeState {
  status: ProbeStatus
  message: string
}

const idleProbe: ProbeState = { status: 'idle', message: '' }

const emptyKeys: ApiKeyValues = {
  ynabToken: '',
  ynabBudgetId: '',
  fastmailApiToken: '',
  geminiKey: '',
}

interface Budget {
  id: string
  name: string
}

type BudgetsStatus = 'idle' | 'loading' | 'loaded' | 'error'

type ScheduleType =
  | 'EVERY_N_SECONDS'
  | 'EVERY_N_MINUTES'
  | 'HOURLY'
  | 'EVERY_N_HOURS'
  | 'DAILY'
  | 'WEEKLY'

interface ScheduleConfig {
  type: ScheduleType
  secondInterval?: number | null
  minuteInterval?: number | null
  hourInterval?: number | null
  hour?: number | null
  minute?: number
  dayOfWeek?: string | null
}

interface ProcessingConfigResponse {
  orderCap: number
  startFromDate: string | null
  installedAt: string | null
  scheduleConfig: ScheduleConfig | null
}

interface DryRunResult {
  id: number
  orderId: number | null
  orderDate: string
  totalAmount: string
  items: string[]
  ynabTransactionId: string | null
  proposedCategoryId: string | null
  proposedCategoryName: string | null
  errorMessage: string | null
  runAt: string
}

const DAYS_OF_WEEK = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
const HOURS = Array.from({ length: 24 }, (_, i) => i)
const MINUTES = [0, 15, 30, 45]

export default function ConfigView() {
  const [keys, setKeys] = useState<ApiKeyValues>(emptyKeys)
  const [saved, setSaved] = useState(false)
  const [ynabProbe, setYnabProbe] = useState<ProbeState>(idleProbe)
  const [fastmailProbe, setFastmailProbe] = useState<ProbeState>(idleProbe)
  const [geminiProbe, setGeminiProbe] = useState<ProbeState>(idleProbe)

  const [budgets, setBudgets] = useState<Budget[]>([])
  const [budgetsStatus, setBudgetsStatus] = useState<BudgetsStatus>('idle')
  const [budgetsError, setBudgetsError] = useState('')

  const [orderCap, setOrderCap] = useState(0)
  const [startFromDate, setStartFromDate] = useState('')
  const [scheduleType, setScheduleType] = useState<ScheduleType>('EVERY_N_HOURS')
  const [secondInterval, setSecondInterval] = useState(10)
  const [minuteInterval, setMinuteInterval] = useState(30)
  const [hourInterval, setHourInterval] = useState(5)
  const [scheduleHour, setScheduleHour] = useState(0)
  const [scheduleMinute, setScheduleMinute] = useState(0)
  const [scheduleDow, setScheduleDow] = useState('MON')
  const [processingConfigSaved, setProcessingConfigSaved] = useState(false)

  const [dryRunStartFrom, setDryRunStartFrom] = useState(() => {
    const oneMonthAgo = new Date()
    oneMonthAgo.setMonth(oneMonthAgo.getMonth() - 1)
    return oneMonthAgo.toISOString().split('T')[0]
  })
  const [dryRunStatus, setDryRunStatus] = useState<'idle' | 'running' | 'done' | 'error'>('idle')
  const [dryRunResults, setDryRunResults] = useState<DryRunResult[]>([])
  const [dryRunError, setDryRunError] = useState('')

  useEffect(() => {
    let cancelled = false

    apiGet<ApiKeysResponse>('/api/config/keys').then((data) => {
      if (cancelled) return
      setKeys((current) => ({
        ynabToken: current.ynabToken || (data.ynabToken ?? ''),
        ynabBudgetId: current.ynabBudgetId || (data.ynabBudgetId ?? ''),
        fastmailApiToken: current.fastmailApiToken || (data.fastmailApiToken ?? ''),
        geminiKey: current.geminiKey || (data.geminiKey ?? ''),
      }))
    })

    apiGet<ProcessingConfigResponse>('/api/config/processing').then((data) => {
      if (cancelled) return
      setOrderCap(data.orderCap ?? 0)
      setStartFromDate(data.startFromDate ?? '')
      const sc = data.scheduleConfig
      if (sc) {
        setScheduleType(sc.type)
        setSecondInterval(sc.secondInterval ?? 10)
        setMinuteInterval(sc.minuteInterval ?? 30)
        setHourInterval(sc.hourInterval ?? 5)
        setScheduleHour(sc.hour ?? 0)
        setScheduleMinute(sc.minute ?? 0)
        setScheduleDow(sc.dayOfWeek ?? 'MON')
      }
    })

    apiGet<DryRunResult[]>('/api/config/dry-run/results')
      .then((results) => {
        if (!cancelled) setDryRunResults(results)
      })
      .catch(() => {})

    return () => {
      cancelled = true
    }
  }, [])

  const displayBudgets = keys.ynabToken && budgetsStatus === 'loaded' ? budgets : []
  const displayBudgetsStatus: BudgetsStatus = keys.ynabToken ? budgetsStatus : 'idle'
  const displayBudgetsError = keys.ynabToken ? budgetsError : ''

  useEffect(() => {
    if (!keys.ynabToken) {
      return
    }

    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect -- clears stale budgets and signals loading state before async fetch
    setBudgets([])
    setBudgetsStatus('loading')
    setBudgetsError('')

    const params = new URLSearchParams({ token: keys.ynabToken })
    apiGet<Budget[]>(`/api/ynab/budgets?${params.toString()}`)
      .then((data) => {
        if (!cancelled) {
          setBudgets(data)
          setBudgetsStatus('loaded')
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setBudgetsError(err instanceof Error ? err.message : 'Failed to load budgets')
          setBudgetsStatus('error')
        }
      })

    return () => {
      cancelled = true
    }
  }, [keys.ynabToken])

  function handleSave() {
    apiPut('/api/config/keys', keys).then(() => {
      setSaved(true)
      setYnabProbe(idleProbe)
      setFastmailProbe(idleProbe)
      setGeminiProbe(idleProbe)
    })
  }

  function handleSaveProcessingConfig() {
    const scheduleConfig: ScheduleConfig = {
      type: scheduleType,
      secondInterval: scheduleType === 'EVERY_N_SECONDS' ? secondInterval : null,
      minuteInterval: scheduleType === 'EVERY_N_MINUTES' ? minuteInterval : null,
      hourInterval: scheduleType === 'EVERY_N_HOURS' ? hourInterval : null,
      hour: scheduleType === 'DAILY' || scheduleType === 'WEEKLY' ? scheduleHour : null,
      minute: scheduleType === 'DAILY' || scheduleType === 'WEEKLY' ? scheduleMinute : 0,
      dayOfWeek: scheduleType === 'WEEKLY' ? scheduleDow : null,
    }
    apiPut('/api/config/processing', {
      orderCap,
      startFromDate: startFromDate || null,
      scheduleConfig,
    }).then(() => {
      setProcessingConfigSaved(true)
    })
  }

  function handleTest(endpoint: string, setProbe: (state: ProbeState) => void) {
    setProbe({ status: 'testing', message: '' })
    apiPost<ProbeResult>(`/api/config/probe/${endpoint}`)
      .then((result) =>
        setProbe({
          status: result.success ? 'success' : 'error',
          message: result.message,
        }),
      )
      .catch((err: unknown) =>
        setProbe({
          status: 'error',
          message: err instanceof Error ? err.message : 'Unexpected error',
        }),
      )
  }

  function handleDryRun() {
    setDryRunStatus('running')
    setDryRunError('')
    apiPostWithBody<DryRunResult[]>('/api/config/dry-run', {
      startFromDate: dryRunStartFrom || null,
    })
      .then((results) => {
        setDryRunResults(results)
        setDryRunStatus('done')
      })
      .catch((err: unknown) => {
        setDryRunStatus('error')
        setDryRunError(err instanceof Error ? err.message : 'Dry run failed')
      })
  }

  return (
    <div>
      <h1>API Keys</h1>
      <p style={{ marginBottom: 'var(--cf-s3)' }}>
        <em>
          &ldquo;Test Connection&rdquo; checks saved credentials. Save before testing new values.
        </em>
      </p>

      {/* ── SIGNAL SOURCES panel ──────────────────────────────────────────── */}
      <div className="cf-panel">
        <span className="cf-panel-label">Signal Sources</span>

        {/* YNAB sub-panel */}
        <div className="cf-panel" style={{ marginBottom: 'var(--cf-s3)' }}>
          <span className="cf-panel-label">YNAB</span>
          <section aria-label="YNAB credentials">
            <div className="cf-form-row">
              <label htmlFor="ynabToken">YNAB Token</label>
              <input
                id="ynabToken"
                type="text"
                value={keys.ynabToken}
                onChange={(e) => setKeys({ ...keys, ynabToken: e.target.value })}
              />
            </div>
            <div className="cf-form-row">
              <label htmlFor="ynabBudgetId">Budget</label>
              {displayBudgetsStatus === 'loading' && (
                <span aria-label="budgets loading">Loading budgets…</span>
              )}
              {displayBudgetsStatus === 'error' && <span role="alert">{displayBudgetsError}</span>}
              <select
                id="ynabBudgetId"
                value={keys.ynabBudgetId}
                onChange={(e) => setKeys({ ...keys, ynabBudgetId: e.target.value })}
                disabled={
                  !keys.ynabToken ||
                  displayBudgetsStatus !== 'loaded' ||
                  displayBudgets.length === 0
                }
              >
                {!keys.ynabToken ? (
                  <option value="">Enter a YNAB token first</option>
                ) : displayBudgetsStatus === 'loaded' && displayBudgets.length === 0 ? (
                  <option value="">No budgets found</option>
                ) : (
                  <>
                    <option value="">Select a budget…</option>
                    {displayBudgets.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.name}
                      </option>
                    ))}
                  </>
                )}
              </select>
            </div>
            <div className="cf-btn-row">
              <button
                onClick={() => handleTest('ynab', setYnabProbe)}
                disabled={!keys.ynabToken || ynabProbe.status === 'testing'}
              >
                {ynabProbe.status === 'testing' ? 'Testing…' : 'Test YNAB'}
              </button>
            </div>
            <IndicatorPanel
              label="YNAB"
              state={ynabProbe.status}
              message={ynabProbe.message}
              readoutAriaLabel="YNAB probe result"
            />
          </section>
        </div>

        {/* FastMail sub-panel */}
        <div className="cf-panel" style={{ marginBottom: 0 }}>
          <span className="cf-panel-label">FastMail</span>
          <section aria-label="FastMail credentials">
            <div className="cf-form-row">
              <label htmlFor="fastmailApiToken">FastMail API Token</label>
              <input
                id="fastmailApiToken"
                type="text"
                value={keys.fastmailApiToken}
                onChange={(e) => setKeys({ ...keys, fastmailApiToken: e.target.value })}
              />
            </div>
            <div className="cf-btn-row">
              <button
                onClick={() => handleTest('fastmail', setFastmailProbe)}
                disabled={!keys.fastmailApiToken || fastmailProbe.status === 'testing'}
              >
                {fastmailProbe.status === 'testing' ? 'Testing…' : 'Test FastMail'}
              </button>
            </div>
            <IndicatorPanel
              label="FastMail"
              state={fastmailProbe.status}
              message={fastmailProbe.message}
              readoutAriaLabel="FastMail probe result"
            />
          </section>
        </div>
      </div>

      {/* ── AI ENGINE panel ───────────────────────────────────────────────── */}
      <div className="cf-panel">
        <span className="cf-panel-label">AI Engine</span>
        <section aria-label="Gemini credentials">
          <div className="cf-form-row">
            <label htmlFor="geminiKey">Gemini Key</label>
            <input
              id="geminiKey"
              type="text"
              value={keys.geminiKey}
              onChange={(e) => setKeys({ ...keys, geminiKey: e.target.value })}
            />
          </div>
          <div className="cf-btn-row">
            <button
              onClick={() => handleTest('gemini', setGeminiProbe)}
              disabled={!keys.geminiKey || geminiProbe.status === 'testing'}
            >
              {geminiProbe.status === 'testing' ? 'Testing…' : 'Test Gemini'}
            </button>
          </div>
          <IndicatorPanel
            label="Gemini"
            state={geminiProbe.status}
            message={geminiProbe.message}
            readoutAriaLabel="Gemini probe result"
          />
        </section>
      </div>

      {/* Save API keys */}
      <div className="cf-btn-row" style={{ marginBottom: 'var(--cf-s4)' }}>
        <button onClick={handleSave}>Save</button>
        {saved && <span className="cf-saved">Saved</span>}
      </div>

      {/* ── PROCESSING panel ──────────────────────────────────────────────── */}
      <div className="cf-panel">
        <span className="cf-panel-label">Processing</span>
        <section aria-label="Processing settings">
          <h2>Processing Settings</h2>

          <div className="cf-form-row">
            <label htmlFor="orderCap">Max orders per run (0 = unlimited)</label>
            <input
              id="orderCap"
              type="number"
              min={0}
              value={orderCap}
              onChange={(e) => setOrderCap(parseInt(e.target.value, 10) || 0)}
            />
          </div>

          <div className="cf-form-row">
            <label htmlFor="startFromDate">Start from date</label>
            <input
              id="startFromDate"
              type="date"
              value={startFromDate}
              onChange={(e) => setStartFromDate(e.target.value)}
            />
          </div>

          <fieldset>
            <legend>Sync schedule</legend>

            <div className="cf-form-row">
              <label>Frequency</label>
              <RadioGroup<ScheduleType>
                name="scheduleType"
                ariaLabel="Frequency"
                value={scheduleType}
                onChange={setScheduleType}
                options={[
                  { value: 'HOURLY', label: 'Every hour' },
                  { value: 'EVERY_N_HOURS', label: 'Every N hours' },
                  { value: 'EVERY_N_MINUTES', label: 'Every N minutes' },
                  { value: 'EVERY_N_SECONDS', label: 'Every N seconds' },
                  { value: 'DAILY', label: 'Daily' },
                  { value: 'WEEKLY', label: 'Weekly' },
                ]}
              />
            </div>

            {scheduleType === 'EVERY_N_SECONDS' && (
              <div>
                <div className="cf-form-row">
                  <label htmlFor="secondInterval">Every N seconds</label>
                  <input
                    id="secondInterval"
                    type="number"
                    min={1}
                    max={59}
                    value={secondInterval}
                    onChange={(e) => setSecondInterval(parseInt(e.target.value, 10) || 1)}
                  />
                </div>
                <p role="alert">
                  ⚠ Not recommended for production — intended for development and testing only.
                </p>
              </div>
            )}

            {scheduleType === 'EVERY_N_MINUTES' && (
              <div className="cf-form-row">
                <label htmlFor="minuteInterval">Every N minutes</label>
                <input
                  id="minuteInterval"
                  type="number"
                  min={1}
                  max={59}
                  value={minuteInterval}
                  onChange={(e) => setMinuteInterval(parseInt(e.target.value, 10) || 1)}
                />
              </div>
            )}

            {scheduleType === 'EVERY_N_HOURS' && (
              <div className="cf-form-row">
                <label htmlFor="hourInterval">Every N hours</label>
                <input
                  id="hourInterval"
                  type="number"
                  min={1}
                  max={23}
                  value={hourInterval}
                  onChange={(e) => setHourInterval(parseInt(e.target.value, 10) || 1)}
                />
              </div>
            )}

            {(scheduleType === 'DAILY' || scheduleType === 'WEEKLY') && (
              <>
                <div className="cf-form-row">
                  <label htmlFor="scheduleHour">Hour</label>
                  <select
                    id="scheduleHour"
                    value={scheduleHour}
                    onChange={(e) => setScheduleHour(parseInt(e.target.value, 10))}
                  >
                    {HOURS.map((h) => (
                      <option key={h} value={h}>
                        {String(h).padStart(2, '0')}:00
                      </option>
                    ))}
                  </select>
                </div>
                <div className="cf-form-row">
                  <label htmlFor="scheduleMinute">Minute</label>
                  <select
                    id="scheduleMinute"
                    value={scheduleMinute}
                    onChange={(e) => setScheduleMinute(parseInt(e.target.value, 10))}
                  >
                    {MINUTES.map((m) => (
                      <option key={m} value={m}>
                        :{String(m).padStart(2, '0')}
                      </option>
                    ))}
                  </select>
                </div>
              </>
            )}

            {scheduleType === 'WEEKLY' && (
              <div className="cf-form-row">
                <label htmlFor="scheduleDow">Day of week</label>
                <select
                  id="scheduleDow"
                  value={scheduleDow}
                  onChange={(e) => setScheduleDow(e.target.value)}
                >
                  {DAYS_OF_WEEK.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </fieldset>

          <div className="cf-btn-row">
            <button onClick={handleSaveProcessingConfig}>Save processing settings</button>
            {processingConfigSaved && <span className="cf-saved">✓ Processing settings saved</span>}
          </div>
        </section>
      </div>

      {/* ── DRY RUN panel ─────────────────────────────────────────────────── */}
      <div className="cf-panel">
        <span className="cf-panel-label">Dry Run</span>
        <section aria-label="Dry run">
          <h2>Dry Run</h2>
          <p style={{ marginBottom: 'var(--cf-s2)', fontSize: '12px' }}>
            <em>
              Preview what would be written to YNAB — no live changes are made. Order cap applies.
              Gemini is called for classification.
            </em>
          </p>

          <div className="cf-form-row">
            <label htmlFor="dryRunStartFrom">Dry-run start from</label>
            <input
              id="dryRunStartFrom"
              type="date"
              value={dryRunStartFrom}
              onChange={(e) => setDryRunStartFrom(e.target.value)}
            />
          </div>

          <div className="cf-btn-row">
            <button onClick={handleDryRun} disabled={dryRunStatus === 'running'}>
              {dryRunStatus === 'running' ? 'Running…' : 'Run Dry Run'}
            </button>
          </div>

          {dryRunStatus === 'error' && <p role="alert">{dryRunError}</p>}

          <div className="cf-crt" aria-live="polite" style={{ marginTop: 'var(--cf-s3)' }}>
            {dryRunStatus === 'idle' && dryRunResults.length === 0 ? (
              <p className="cf-terminal-standby">-- STANDING BY --</p>
            ) : (
              <>
                <h3>
                  Dry Run Results ({dryRunResults.length} order
                  {dryRunResults.length !== 1 ? 's' : ''})
                </h3>
                {dryRunResults.length === 0 ? (
                  <p
                    className="cf-terminal-empty"
                    style={{ fontSize: '16px', padding: 'var(--cf-s3) 0' }}
                  >
                    No orders matched.
                  </p>
                ) : (
                  <table className="cf-data-table" style={{ marginTop: 'var(--cf-s2)' }}>
                    <thead>
                      <tr>
                        <th>Order date</th>
                        <th>Amount</th>
                        <th>Items</th>
                        <th>Matched transaction</th>
                        <th>Proposed category</th>
                        <th>Note</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dryRunResults.map((r) => (
                        <tr key={r.id} aria-label={`dry-run-row-${r.id}`}>
                          <td>{new Date(r.orderDate).toLocaleDateString()}</td>
                          <td>${r.totalAmount}</td>
                          <td>{r.items.join(', ')}</td>
                          <td>{r.ynabTransactionId ?? '—'}</td>
                          <td>{r.proposedCategoryName ?? r.proposedCategoryId ?? '—'}</td>
                          <td>{r.errorMessage ?? ''}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}
