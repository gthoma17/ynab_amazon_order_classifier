import { useEffect, useState } from 'react'
import { apiGet, apiPost, apiPostWithBody, apiPut } from '../api'

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

// ── Processing guardrails ──────────────────────────────────────────────────────

type ScheduleType = 'EVERY_N_SECONDS' | 'EVERY_N_MINUTES' | 'HOURLY' | 'EVERY_N_HOURS' | 'DAILY' | 'WEEKLY'

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

// ── Dry run ───────────────────────────────────────────────────────────────────

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

  // Processing config
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

  // Dry run
  const [dryRunStartFrom, setDryRunStartFrom] = useState('')
  const [dryRunStatus, setDryRunStatus] = useState<'idle' | 'running' | 'done' | 'error'>('idle')
  const [dryRunResults, setDryRunResults] = useState<DryRunResult[]>([])
  const [dryRunError, setDryRunError] = useState('')

  useEffect(() => {
    let cancelled = false

    apiGet<ApiKeysResponse>('/api/config/keys').then((data) => {
      if (cancelled) return
      // Use functional form: only populate a field if it is currently empty so
      // that a slow response (e.g. during JVM warm-up in CI) never overwrites
      // values the user has already typed into the form.
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

    // Default dry-run start to 1 month ago
    const oneMonthAgo = new Date()
    oneMonthAgo.setMonth(oneMonthAgo.getMonth() - 1)
    setDryRunStartFrom(oneMonthAgo.toISOString().split('T')[0])

    // Load any previous dry-run results
    apiGet<DryRunResult[]>('/api/config/dry-run/results')
      .then((results) => { if (!cancelled) setDryRunResults(results) })
      .catch(() => {})

    return () => { cancelled = true }
  }, [])

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

  function handleTest(
    endpoint: string,
    setProbe: (state: ProbeState) => void
  ) {
    setProbe({ status: 'testing', message: '' })
    apiPost<ProbeResult>(`/api/config/probe/${endpoint}`)
      .then((result) =>
        setProbe({
          status: result.success ? 'success' : 'error',
          message: result.message,
        })
      )
      .catch((err: unknown) =>
        setProbe({
          status: 'error',
          message: err instanceof Error ? err.message : 'Unexpected error',
        })
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
      <p>
        <em>
          "Test Connection" checks saved credentials. Save before testing new
          values.
        </em>
      </p>

      <section>
        <h2>YNAB</h2>
        <div>
          <label htmlFor="ynabToken">YNAB Token</label>
          <input
            id="ynabToken"
            value={keys.ynabToken}
            onChange={(e) => setKeys({ ...keys, ynabToken: e.target.value })}
          />
        </div>
        <div>
          <label htmlFor="ynabBudgetId">Budget ID</label>
          <input
            id="ynabBudgetId"
            value={keys.ynabBudgetId}
            onChange={(e) => setKeys({ ...keys, ynabBudgetId: e.target.value })}
          />
        </div>
        <button
          onClick={() => handleTest('ynab', setYnabProbe)}
          disabled={!keys.ynabToken || ynabProbe.status === 'testing'}
        >
          {ynabProbe.status === 'testing' ? 'Testing…' : 'Test YNAB'}
        </button>
        {ynabProbe.status === 'success' && (
          <span aria-label="YNAB probe result">✓ {ynabProbe.message}</span>
        )}
        {ynabProbe.status === 'error' && (
          <span aria-label="YNAB probe result">✗ {ynabProbe.message}</span>
        )}
      </section>

      <section>
        <h2>FastMail</h2>
        <div>
          <label htmlFor="fastmailApiToken">FastMail API Token</label>
          <input
            id="fastmailApiToken"
            type="password"
            value={keys.fastmailApiToken}
            onChange={(e) =>
              setKeys({ ...keys, fastmailApiToken: e.target.value })
            }
          />
        </div>
        <button
          onClick={() => handleTest('fastmail', setFastmailProbe)}
          disabled={
            !keys.fastmailApiToken ||
            fastmailProbe.status === 'testing'
          }
        >
          {fastmailProbe.status === 'testing' ? 'Testing…' : 'Test FastMail'}
        </button>
        {fastmailProbe.status === 'success' && (
          <span aria-label="FastMail probe result">
            ✓ {fastmailProbe.message}
          </span>
        )}
        {fastmailProbe.status === 'error' && (
          <span aria-label="FastMail probe result">
            ✗ {fastmailProbe.message}
          </span>
        )}
      </section>

      <section>
        <h2>Gemini</h2>
        <div>
          <label htmlFor="geminiKey">Gemini Key</label>
          <input
            id="geminiKey"
            value={keys.geminiKey}
            onChange={(e) => setKeys({ ...keys, geminiKey: e.target.value })}
          />
        </div>
        <button
          onClick={() => handleTest('gemini', setGeminiProbe)}
          disabled={!keys.geminiKey || geminiProbe.status === 'testing'}
        >
          {geminiProbe.status === 'testing' ? 'Testing…' : 'Test Gemini'}
        </button>
        {geminiProbe.status === 'success' && (
          <span aria-label="Gemini probe result">✓ {geminiProbe.message}</span>
        )}
        {geminiProbe.status === 'error' && (
          <span aria-label="Gemini probe result">✗ {geminiProbe.message}</span>
        )}
      </section>

      <button onClick={handleSave}>Save</button>
      {saved && <p>Saved</p>}

      {/* ── Processing guardrails ──────────────────────────────────────────── */}
      <section>
        <h2>Processing Settings</h2>

        <div>
          <label htmlFor="orderCap">
            Max orders per run (0 = unlimited)
          </label>
          <input
            id="orderCap"
            type="number"
            min={0}
            value={orderCap}
            onChange={(e) => setOrderCap(parseInt(e.target.value, 10) || 0)}
          />
        </div>

        <div>
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

          <label htmlFor="scheduleType">Frequency</label>
          <select
            id="scheduleType"
            value={scheduleType}
            onChange={(e) => setScheduleType(e.target.value as ScheduleType)}
          >
            <option value="HOURLY">Every hour</option>
            <option value="EVERY_N_HOURS">Every N hours</option>
            <option value="EVERY_N_MINUTES">Every N minutes</option>
            <option value="EVERY_N_SECONDS">Every N seconds</option>
            <option value="DAILY">Daily</option>
            <option value="WEEKLY">Weekly</option>
          </select>

          {scheduleType === 'EVERY_N_SECONDS' && (
            <div>
              <label htmlFor="secondInterval">Every N seconds</label>
              <input
                id="secondInterval"
                type="number"
                min={1}
                max={59}
                value={secondInterval}
                onChange={(e) =>
                  setSecondInterval(parseInt(e.target.value, 10) || 1)
                }
              />
              <p role="alert">
                ⚠ Not recommended for production — intended for development and
                testing only.
              </p>
            </div>
          )}

          {scheduleType === 'EVERY_N_MINUTES' && (
            <div>
              <label htmlFor="minuteInterval">Every N minutes</label>
              <input
                id="minuteInterval"
                type="number"
                min={1}
                max={59}
                value={minuteInterval}
                onChange={(e) =>
                  setMinuteInterval(parseInt(e.target.value, 10) || 1)
                }
              />
            </div>
          )}

          {scheduleType === 'EVERY_N_HOURS' && (
            <div>
              <label htmlFor="hourInterval">Every N hours</label>
              <input
                id="hourInterval"
                type="number"
                min={1}
                max={23}
                value={hourInterval}
                onChange={(e) =>
                  setHourInterval(parseInt(e.target.value, 10) || 1)
                }
              />
            </div>
          )}

          {(scheduleType === 'DAILY' || scheduleType === 'WEEKLY') && (
            <>
              <div>
                <label htmlFor="scheduleHour">Hour</label>
                <select
                  id="scheduleHour"
                  value={scheduleHour}
                  onChange={(e) =>
                    setScheduleHour(parseInt(e.target.value, 10))
                  }
                >
                  {HOURS.map((h) => (
                    <option key={h} value={h}>
                      {String(h).padStart(2, '0')}:00
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor="scheduleMinute">Minute</label>
                <select
                  id="scheduleMinute"
                  value={scheduleMinute}
                  onChange={(e) =>
                    setScheduleMinute(parseInt(e.target.value, 10))
                  }
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
            <div>
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

        <button onClick={handleSaveProcessingConfig}>
          Save processing settings
        </button>
        {processingConfigSaved && <p>Processing settings saved</p>}
      </section>

      {/* ── Dry run ────────────────────────────────────────────────────────── */}
      <section>
        <h2>Dry Run</h2>
        <p>
          <em>
            Preview what would be written to YNAB — no live changes are made.
            Order cap applies. Gemini is called for classification.
          </em>
        </p>

        <div>
          <label htmlFor="dryRunStartFrom">Dry-run start from</label>
          <input
            id="dryRunStartFrom"
            type="date"
            value={dryRunStartFrom}
            onChange={(e) => setDryRunStartFrom(e.target.value)}
          />
        </div>

        <button
          onClick={handleDryRun}
          disabled={dryRunStatus === 'running'}
        >
          {dryRunStatus === 'running' ? 'Running…' : 'Run Dry Run'}
        </button>

        {dryRunStatus === 'error' && (
          <p role="alert">{dryRunError}</p>
        )}

        {(dryRunStatus === 'done' || dryRunResults.length > 0) && (
          <div aria-live="polite">
            <h3>Dry Run Results ({dryRunResults.length} order{dryRunResults.length !== 1 ? 's' : ''})</h3>
            {dryRunResults.length === 0 ? (
              <p>No orders matched.</p>
            ) : (
              <table>
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
          </div>
        )}
      </section>
    </div>
  )
}
