import { useEffect, useState } from 'react'
import { apiGet, apiPostWithBody, apiPut } from '../api'
import CrtPanel from '../components/CrtPanel'
import IndicatorPanel from '../components/IndicatorPanel'
import RadioGroup from '../components/RadioGroup'
import SplitFlapSlot from '../components/SplitFlapSlot'

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
  hourlyMinuteOffset?: number | null
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
  const [signalSourcesMessage, setSignalSourcesMessage] = useState<string | null>(null)
  const [aiEngineMessage, setAiEngineMessage] = useState<string | null>(null)
  const [fastmailProbe, setFastmailProbe] = useState<ProbeState>(idleProbe)
  const [geminiProbe, setGeminiProbe] = useState<ProbeState>(idleProbe)

  const [budgets, setBudgets] = useState<Budget[]>([])
  const [budgetsStatus, setBudgetsStatus] = useState<BudgetsStatus>('idle')
  const [budgetsError, setBudgetsError] = useState('')
  const [highlightedBudgetIndex, setHighlightedBudgetIndex] = useState(-1)

  const [orderCap, setOrderCap] = useState(0)
  const [startFromDate, setStartFromDate] = useState('')
  const [scheduleType, setScheduleType] = useState<ScheduleType>('EVERY_N_HOURS')
  const [secondInterval, setSecondInterval] = useState(10)
  const [minuteInterval, setMinuteInterval] = useState(30)
  const [hourInterval, setHourInterval] = useState(5)
  const [hourlyMinuteOffset, setHourlyMinuteOffset] = useState(0)
  const [scheduleHour, setScheduleHour] = useState(9)
  const [scheduleMinute, setScheduleMinute] = useState(0)
  const [scheduleDow, setScheduleDow] = useState('MON')
  const [processingMessage, setProcessingMessage] = useState<string | null>(null)

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
        setHourlyMinuteOffset(sc.hourlyMinuteOffset ?? 0)
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
    setHighlightedBudgetIndex(-1)
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

  // Auto-reset save slot messages after 5 seconds
  useEffect(() => {
    if (!signalSourcesMessage) return
    const t = setTimeout(() => setSignalSourcesMessage(null), 5000)
    return () => clearTimeout(t)
  }, [signalSourcesMessage])

  useEffect(() => {
    if (!aiEngineMessage) return
    const t = setTimeout(() => setAiEngineMessage(null), 5000)
    return () => clearTimeout(t)
  }, [aiEngineMessage])

  useEffect(() => {
    if (!processingMessage) return
    const t = setTimeout(() => setProcessingMessage(null), 5000)
    return () => clearTimeout(t)
  }, [processingMessage])

  function handleSaveSignalSources() {
    apiPut('/api/config/keys', {
      ynabToken: keys.ynabToken,
      ynabBudgetId: keys.ynabBudgetId,
      fastmailApiToken: keys.fastmailApiToken,
    }).then(() => {
      setSignalSourcesMessage('✓  SAVED')
      setFastmailProbe(idleProbe)
    })
  }

  function handleSaveAiEngine() {
    apiPut('/api/config/keys', {
      geminiKey: keys.geminiKey,
    }).then(() => {
      setAiEngineMessage('✓  SAVED')
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
      hourlyMinuteOffset: scheduleType === 'HOURLY' ? hourlyMinuteOffset : null,
    }
    apiPut('/api/config/processing', {
      orderCap,
      startFromDate: startFromDate || null,
      scheduleConfig,
    }).then(() => {
      setProcessingMessage('✓  SAVED')
    })
  }

  function handleTest(
    endpoint: string,
    body: Record<string, string>,
    setProbe: (state: ProbeState) => void,
  ) {
    setProbe({ status: 'testing', message: '' })
    apiPostWithBody<ProbeResult>(`/api/config/probe/${endpoint}`, body)
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

  function handleBudgetKeyDown(e: React.KeyboardEvent) {
    if (displayBudgetsStatus !== 'loaded' || displayBudgets.length === 0) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setHighlightedBudgetIndex((prev) => Math.min(prev + 1, displayBudgets.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlightedBudgetIndex((prev) => Math.max(prev - 1, 0))
    } else if (e.key === 'Enter' && highlightedBudgetIndex >= 0) {
      setKeys({ ...keys, ynabBudgetId: displayBudgets[highlightedBudgetIndex].id })
    }
  }

  const budgetListboxActiveDescendant =
    displayBudgetsStatus === 'loaded'
      ? (() => {
          const id =
            highlightedBudgetIndex >= 0
              ? displayBudgets[highlightedBudgetIndex]?.id
              : keys.ynabBudgetId
          return id ? `budget-option-${id}` : undefined
        })()
      : undefined

  return (
    <div>
      <div className="cf-panel cf-view-header">
        <h1>Configuration</h1>
        <p>
          <em>&ldquo;Test Connection&rdquo; tests the credentials currently in the fields.</em>
        </p>
      </div>

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
                className="cf-credential-input"
                value={keys.ynabToken}
                onChange={(e) => setKeys({ ...keys, ynabToken: e.target.value })}
              />
            </div>
            <div className="cf-form-row">
              <label id="budget-selector-label">Budget</label>
              <CrtPanel
                className="cf-budget-selector"
                data-testid="budget-selector-screen"
                role="listbox"
                aria-labelledby="budget-selector-label"
                aria-activedescendant={budgetListboxActiveDescendant}
                tabIndex={
                  displayBudgetsStatus === 'loaded' && displayBudgets.length > 0 ? 0 : undefined
                }
                onKeyDown={handleBudgetKeyDown}
              >
                {displayBudgetsStatus === 'idle' ? (
                  <div className="cf-budget-standby">
                    <span>
                      <span aria-hidden="true">&gt; </span>AWAITING TOKEN
                    </span>
                    <span className="cf-budget-cursor" aria-hidden="true">
                      _
                    </span>
                  </div>
                ) : displayBudgetsStatus === 'loading' ? (
                  <div className="cf-budget-standby" aria-label="budgets loading">
                    <span>
                      <span aria-hidden="true">&gt; </span>FETCHING BUDGETS...
                    </span>
                    <span className="cf-budget-cursor" aria-hidden="true">
                      _
                    </span>
                  </div>
                ) : displayBudgetsStatus === 'error' ? (
                  <div className="cf-budget-error" role="alert">
                    <span>
                      <span aria-hidden="true">&gt; </span>CONNECTION FAILED
                    </span>
                    <span>&nbsp;</span>
                    <span>&nbsp;&nbsp;{displayBudgetsError}</span>
                    <span>&nbsp;&nbsp;CHECK YNAB TOKEN AND RETRY</span>
                  </div>
                ) : displayBudgets.length === 0 ? (
                  <div className="cf-budget-empty">
                    <span>NO BUDGETS FOUND</span>
                  </div>
                ) : (
                  <>
                    <div className="cf-budget-header">SELECT BUDGET</div>
                    <div className="cf-budget-divider" aria-hidden="true">
                      --------------------------------
                    </div>
                    <div className="cf-budget-list">
                      {displayBudgets.map((b, index) => (
                        <div
                          key={b.id}
                          id={`budget-option-${b.id}`}
                          role="option"
                          aria-selected={keys.ynabBudgetId === b.id}
                          data-testid={
                            keys.ynabBudgetId === b.id
                              ? 'budget-option-selected'
                              : `budget-option-${b.id}`
                          }
                          className={[
                            'cf-budget-option',
                            keys.ynabBudgetId === b.id ? 'cf-budget-option--selected' : '',
                            highlightedBudgetIndex === index ? 'cf-budget-option--highlighted' : '',
                          ]
                            .filter(Boolean)
                            .join(' ')}
                          onClick={() => {
                            setKeys({ ...keys, ynabBudgetId: b.id })
                            setHighlightedBudgetIndex(index)
                          }}
                        >
                          <span aria-hidden="true" className="cf-budget-prompt">
                            {keys.ynabBudgetId === b.id ? '>' : '\u00a0'}
                          </span>
                          <span className="cf-budget-name">{b.name}</span>
                          {keys.ynabBudgetId === b.id && (
                            <span aria-hidden="true" className="cf-budget-sel">
                              [SEL]
                            </span>
                          )}
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </CrtPanel>
            </div>
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
                className="cf-credential-input"
                value={keys.fastmailApiToken}
                onChange={(e) => setKeys({ ...keys, fastmailApiToken: e.target.value })}
              />
            </div>
            <div className="cf-test-control">
              <button
                onClick={() =>
                  handleTest(
                    'fastmail',
                    { fastmailApiToken: keys.fastmailApiToken },
                    setFastmailProbe,
                  )
                }
                disabled={!keys.fastmailApiToken || fastmailProbe.status === 'testing'}
              >
                Test FastMail
              </button>
              <IndicatorPanel
                label="FastMail"
                state={fastmailProbe.status}
                message={fastmailProbe.message}
                readoutAriaLabel="FastMail probe result"
              />
            </div>
          </section>
        </div>

        {/* Save Signal Sources */}
        <div className="cf-btn-row">
          <button onClick={handleSaveSignalSources}>Save Signal Sources</button>
          <SplitFlapSlot
            message={signalSourcesMessage}
            testId="signal-sources-saved-slot"
            messageTestId="signal-sources-saved-message"
          />
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
              className="cf-credential-input"
              value={keys.geminiKey}
              onChange={(e) => setKeys({ ...keys, geminiKey: e.target.value })}
            />
          </div>
          <div className="cf-test-control">
            <button
              onClick={() => handleTest('gemini', { geminiKey: keys.geminiKey }, setGeminiProbe)}
              disabled={!keys.geminiKey || geminiProbe.status === 'testing'}
            >
              Test Gemini
            </button>
            <IndicatorPanel
              label="Gemini"
              state={geminiProbe.status}
              message={geminiProbe.message}
              readoutAriaLabel="Gemini probe result"
            />
          </div>
          <div className="cf-btn-row">
            <button onClick={handleSaveAiEngine}>Save AI Engine</button>
            <SplitFlapSlot
              message={aiEngineMessage}
              testId="ai-engine-saved-slot"
              messageTestId="ai-engine-saved-message"
            />
          </div>
        </section>
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

            {(() => {
              const nActive =
                scheduleType === 'EVERY_N_HOURS' ||
                scheduleType === 'EVERY_N_MINUTES' ||
                scheduleType === 'EVERY_N_SECONDS'
              const hourlyActive = scheduleType === 'HOURLY'
              const timeActive = scheduleType === 'DAILY' || scheduleType === 'WEEKLY'
              const dayActive = scheduleType === 'WEEKLY'
              const warningMessage =
                scheduleType === 'EVERY_N_SECONDS'
                  ? '⚠  NOT RECOMMENDED FOR PRODUCTION · DEV / TEST ONLY'
                  : null

              const nValue =
                scheduleType === 'EVERY_N_SECONDS'
                  ? secondInterval
                  : scheduleType === 'EVERY_N_MINUTES'
                    ? minuteInterval
                    : hourInterval
              const nMax = scheduleType === 'EVERY_N_HOURS' ? 23 : 59

              function handleNChange(e: React.ChangeEvent<HTMLInputElement>) {
                const v = parseInt(e.target.value, 10) || 1
                if (scheduleType === 'EVERY_N_SECONDS') setSecondInterval(v)
                else if (scheduleType === 'EVERY_N_MINUTES') setMinuteInterval(v)
                else setHourInterval(v)
              }

              return (
                <div className="cf-sync-schedule">
                  <div className="cf-sync-mode">
                    <RadioGroup<ScheduleType>
                      name="scheduleType"
                      ariaLabel="Frequency"
                      value={scheduleType}
                      onChange={setScheduleType}
                      testIdPrefix="schedule-mode"
                      options={[
                        { value: 'WEEKLY', label: 'Weekly' },
                        { value: 'DAILY', label: 'Daily' },
                        { value: 'HOURLY', label: 'Hourly' },
                        { value: 'EVERY_N_HOURS', label: 'Every N hours' },
                        { value: 'EVERY_N_MINUTES', label: 'Every N minutes' },
                        { value: 'EVERY_N_SECONDS', label: 'Every N seconds' },
                      ]}
                    />
                  </div>

                  <div className="cf-sync-params">
                    <div className="cf-sync-param-row">
                      <span
                        className="cf-param-lamp"
                        data-active={nActive ? 'true' : undefined}
                        aria-hidden="true"
                        data-testid="schedule-lamp-n"
                      />
                      <div className={`cf-form-row${nActive ? ' cf-sync-param--active' : ''}`}>
                        <label htmlFor="scheduleN">N</label>
                        <input
                          id="scheduleN"
                          type="number"
                          min={1}
                          max={nMax}
                          value={nActive ? nValue : ''}
                          disabled={!nActive}
                          onChange={handleNChange}
                          data-testid="schedule-param-n"
                        />
                      </div>
                    </div>

                    <div className="cf-sync-param-row">
                      <span
                        className="cf-param-lamp"
                        data-active={hourlyActive ? 'true' : undefined}
                        aria-hidden="true"
                        data-testid="schedule-lamp-offset"
                      />
                      <div className={`cf-form-row${hourlyActive ? ' cf-sync-param--active' : ''}`}>
                        <label>Offset</label>
                        <RadioGroup<string>
                          name="hourlyMinuteOffset"
                          ariaLabel="Minute offset"
                          value={String(hourlyMinuteOffset)}
                          onChange={(v) => setHourlyMinuteOffset(Number(v))}
                          disabled={!hourlyActive}
                          columns="repeat(4, 48px)"
                          className="cf-radio-group--time"
                          testId="schedule-param-offset"
                          options={MINUTES.map((m) => ({
                            value: String(m),
                            label: ':' + String(m).padStart(2, '0'),
                          }))}
                        />
                      </div>
                    </div>

                    <div className="cf-sync-param-row">
                      <span
                        className="cf-param-lamp"
                        data-active={timeActive ? 'true' : undefined}
                        aria-hidden="true"
                        data-testid="schedule-lamp-hour"
                      />
                      <div className={`cf-form-row${timeActive ? ' cf-sync-param--active' : ''}`}>
                        <label>Hour</label>
                        <RadioGroup<string>
                          name="scheduleHour"
                          ariaLabel="Hour"
                          value={String(scheduleHour)}
                          onChange={(v) => setScheduleHour(Number(v))}
                          disabled={!timeActive}
                          columns="repeat(12, 48px)"
                          className="cf-radio-group--time"
                          testId="schedule-param-hour"
                          options={HOURS.map((h) => ({
                            value: String(h),
                            label: String(h).padStart(2, '0'),
                          }))}
                        />
                      </div>
                    </div>

                    <div className="cf-sync-param-row">
                      <span
                        className="cf-param-lamp"
                        data-active={timeActive ? 'true' : undefined}
                        aria-hidden="true"
                        data-testid="schedule-lamp-min"
                      />
                      <div className={`cf-form-row${timeActive ? ' cf-sync-param--active' : ''}`}>
                        <label>Min</label>
                        <RadioGroup<string>
                          name="scheduleMinute"
                          ariaLabel="Minute"
                          value={String(scheduleMinute)}
                          onChange={(v) => setScheduleMinute(Number(v))}
                          disabled={!timeActive}
                          columns="repeat(4, 48px)"
                          className="cf-radio-group--time"
                          testId="schedule-param-min"
                          options={MINUTES.map((m) => ({
                            value: String(m),
                            label: ':' + String(m).padStart(2, '0'),
                          }))}
                        />
                      </div>
                    </div>

                    <div className="cf-sync-param-row">
                      <span
                        className="cf-param-lamp"
                        data-active={dayActive ? 'true' : undefined}
                        aria-hidden="true"
                        data-testid="schedule-lamp-day"
                      />
                      <div className={`cf-form-row${dayActive ? ' cf-sync-param--active' : ''}`}>
                        <label>Day</label>
                        <RadioGroup<string>
                          name="scheduleDow"
                          ariaLabel="Day of week"
                          value={scheduleDow}
                          onChange={setScheduleDow}
                          disabled={!dayActive}
                          columns={7}
                          testId="schedule-param-day"
                          options={DAYS_OF_WEEK.map((d) => ({ value: d, label: d }))}
                        />
                      </div>
                    </div>

                    <div className="cf-sync-warning">
                      <SplitFlapSlot
                        message={warningMessage}
                        color="red"
                        testId="schedule-warning-slot"
                        messageTestId="schedule-warning-message"
                      />
                    </div>
                  </div>
                </div>
              )
            })()}
          </fieldset>

          <div className="cf-btn-row">
            <button onClick={handleSaveProcessingConfig}>Save processing settings</button>
            <SplitFlapSlot
              message={processingMessage}
              testId="processing-saved-slot"
              messageTestId="processing-saved-message"
            />
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

          <CrtPanel
            aria-live="polite"
            style={{ marginTop: 'var(--cf-s3)', minHeight: '320px', overflowY: 'auto' }}
          >
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
          </CrtPanel>
        </section>
      </div>
    </div>
  )
}
