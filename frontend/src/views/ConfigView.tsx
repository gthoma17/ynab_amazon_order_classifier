import { useEffect, useState } from 'react'
import { apiGet, apiPut } from '../api'

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
    apiPut('/api/config/keys', keys).then(() => setSaved(true))
  }

  return (
    <div>
      <h1>API Keys</h1>
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
      <div>
        <label htmlFor="fastmailUser">FastMail User</label>
        <input
          id="fastmailUser"
          value={keys.fastmailUser}
          onChange={(e) => setKeys({ ...keys, fastmailUser: e.target.value })}
        />
      </div>
      <div>
        <label htmlFor="fastmailToken">FastMail Token</label>
        <input
          id="fastmailToken"
          value={keys.fastmailToken}
          onChange={(e) => setKeys({ ...keys, fastmailToken: e.target.value })}
        />
      </div>
      <div>
        <label htmlFor="geminiKey">Gemini Key</label>
        <input
          id="geminiKey"
          value={keys.geminiKey}
          onChange={(e) => setKeys({ ...keys, geminiKey: e.target.value })}
        />
      </div>
      <button onClick={handleSave}>Save</button>
      {saved && <p>Saved</p>}
    </div>
  )
}
