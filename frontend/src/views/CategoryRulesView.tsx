import { useEffect, useState } from 'react'
import { apiGet, apiPut } from '../api'
import CrtPanel from '../components/CrtPanel'

interface YnabCategory {
  id: string
  name: string
  categoryGroupName: string
}

interface CategoryRule {
  id?: number
  ynabCategoryId: string
  ynabCategoryName: string
  userDescription: string
}

type LoadStatus = 'loading' | 'error' | 'loaded'

export default function CategoryRulesView() {
  const [categories, setCategories] = useState<YnabCategory[]>([])
  const [descriptions, setDescriptions] = useState<Record<string, string>>({})
  const [saved, setSaved] = useState(false)
  const [loadStatus, setLoadStatus] = useState<LoadStatus>('loading')
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- resets loading state before async fetch
    setLoadStatus('loading')
    Promise.all([
      apiGet<YnabCategory[]>('/api/ynab/categories'),
      apiGet<CategoryRule[]>('/api/config/categories'),
    ])
      .then(([cats, rules]) => {
        setCategories(cats)
        const desc: Record<string, string> = {}
        for (const rule of rules) {
          desc[rule.ynabCategoryId] = rule.userDescription
        }
        setDescriptions(desc)
        setLoadStatus('loaded')
      })
      .catch((err: unknown) => {
        setLoadError(err instanceof Error ? err.message : 'Failed to load categories')
        setLoadStatus('error')
      })
  }, [])

  function handleSave() {
    const rules: CategoryRule[] = categories.map((cat) => ({
      ynabCategoryId: cat.id,
      ynabCategoryName: cat.name,
      userDescription: descriptions[cat.id] ?? '',
    }))
    apiPut('/api/config/categories', rules).then(() => setSaved(true))
  }

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
      <div className="cf-panel" style={{ marginBottom: 'var(--cf-s3)' }}>
        <h1 style={{ marginBottom: 0, borderBottom: 'none', paddingBottom: 0 }}>Category Rules</h1>
      </div>

      {loadStatus === 'loading' && (
        <CrtPanel data-testid="categories-loading" style={{ flex: 1 }}>
          <div className="cf-budget-standby" aria-label="categories loading">
            <span>
              <span aria-hidden="true">&gt; </span>LOADING CATEGORIES...
            </span>
            <span className="cf-budget-cursor" aria-hidden="true">
              _
            </span>
          </div>
        </CrtPanel>
      )}

      {loadStatus === 'error' && (
        <CrtPanel data-testid="categories-error" style={{ flex: 1 }}>
          <div className="cf-budget-error" role="alert">
            <span>
              <span aria-hidden="true">&gt; </span>LOAD FAILED
            </span>
            <span>&nbsp;</span>
            <span>&nbsp;&nbsp;{loadError}</span>
            <span>&nbsp;&nbsp;CHECK CONNECTION AND RETRY</span>
          </div>
        </CrtPanel>
      )}

      {loadStatus === 'loaded' && (
        <div data-testid="categories-loaded" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          <div className="cf-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
            <span className="cf-panel-label">AI Classification Rules</span>
            {categories.length === 0 ? (
              <CrtPanel style={{ flex: 1 }}>
                <p className="cf-terminal-empty">No categories — connect YNAB first</p>
              </CrtPanel>
            ) : (
              <CrtPanel style={{ flex: 1, overflowY: 'auto' }}>
                <table className="cf-data-table">
                  <thead>
                    <tr>
                      <th>Category</th>
                      <th>Group</th>
                      <th>AI hint description</th>
                    </tr>
                  </thead>
                  <tbody>
                    {categories.map((cat) => (
                      <tr key={cat.id}>
                        <td>{cat.name}</td>
                        <td>{cat.categoryGroupName}</td>
                        <td>
                          <input
                            aria-label={`Description for ${cat.name}`}
                            value={descriptions[cat.id] ?? ''}
                            onChange={(e) =>
                              setDescriptions({ ...descriptions, [cat.id]: e.target.value })
                            }
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CrtPanel>
            )}
          </div>
          <div className="cf-btn-row">
            <button onClick={handleSave}>Save</button>
            {saved && <span className="cf-saved">✓ Saved</span>}
          </div>
        </div>
      )}
    </div>
  )
}
