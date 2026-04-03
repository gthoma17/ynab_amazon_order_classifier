import { useEffect, useState } from 'react'
import { apiGet, apiPost, apiPut } from '../api'

interface ApiKeysResponse {
  ynabToken: string | null
  ynabBudgetId: string | null
  fastmailUser: string | null
  fastmailToken: string | null
  geminiKey: string | null
}

interface ApiKeyValues {
  ynabToken: string
  ynabBudgetId: string
  fastmailUser: string
  fastmailToken: string
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
  fastmailUser: '',
  fastmailToken: '',
  geminiKey: '',
}

export default function ConfigView() {
  const [keys, setKeys] = useState<ApiKeyValues>(emptyKeys)
  const [saved, setSaved] = useState(false)
  const [ynabProbe, setYnabProbe] = useState<ProbeState>(idleProbe)
  const [fastmailProbe, setFastmailProbe] = useState<ProbeState>(idleProbe)
  const [geminiProbe, setGeminiProbe] = useState<ProbeState>(idleProbe)

  useEffect(() => {
    apiGet<ApiKeysResponse>('/api/config/keys').then((data) => {
      setKeys({
        ynabToken: data.ynabToken ?? '',
        ynabBudgetId: data.ynabBudgetId ?? '',
        fastmailUser: data.fastmailUser ?? '',
        fastmailToken: data.fastmailToken ?? '',
        geminiKey: data.geminiKey ?? '',
      })
    })
  }, [])

  function handleSave() {
    apiPut('/api/config/keys', keys).then(() => {
      setSaved(true)
      setYnabProbe(idleProbe)
      setFastmailProbe(idleProbe)
      setGeminiProbe(idleProbe)
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
          <label htmlFor="fastmailUser">FastMail User</label>
          <input
            id="fastmailUser"
            value={keys.fastmailUser}
            onChange={(e) =>
              setKeys({ ...keys, fastmailUser: e.target.value })
            }
          />
        </div>
        <div>
          <label htmlFor="fastmailToken">FastMail Token</label>
          <input
            id="fastmailToken"
            value={keys.fastmailToken}
            onChange={(e) =>
              setKeys({ ...keys, fastmailToken: e.target.value })
            }
          />
        </div>
        <button
          onClick={() => handleTest('fastmail', setFastmailProbe)}
          disabled={
            !keys.fastmailUser ||
            !keys.fastmailToken ||
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
    </div>
  )
}
