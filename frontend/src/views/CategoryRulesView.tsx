import { useEffect, useState } from 'react'
import { apiGet, apiPut } from '../api'

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

export default function CategoryRulesView() {
  const [categories, setCategories] = useState<YnabCategory[]>([])
  const [descriptions, setDescriptions] = useState<Record<string, string>>({})
  const [saved, setSaved] = useState(false)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)

  useEffect(() => {
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
      })
      .catch(() => setLoadError(true))
      .finally(() => setLoading(false))
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
    <div className="view">
      <h1>Category Rules</h1>

      {loading && (
        <div className="empty-state">
          <p className="empty-state__body">Loading categories…</p>
        </div>
      )}

      {!loading && loadError && (
        <div className="panel">
          <div className="panel-body">
            <p className="alert alert--error">
              Failed to load YNAB categories. Make sure your YNAB token and budget are configured on
              the <a href="/">Configuration</a> page, then reload.
            </p>
          </div>
        </div>
      )}

      {!loading && !loadError && categories.length === 0 && (
        <div className="empty-state">
          <p className="empty-state__title">No categories loaded</p>
          <p className="empty-state__body">
            Configure your YNAB token and budget on the <a href="/">Configuration</a> page, then
            return here to add descriptions that guide AI classification.
          </p>
        </div>
      )}

      {!loading && !loadError && categories.length > 0 && (
        <>
          <div className="panel">
            <table>
              <thead>
                <tr>
                  <th>Category</th>
                  <th>Group</th>
                  <th>Description</th>
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
          </div>
          <div className="action-row">
            <button onClick={handleSave}>Save</button>
            {saved && <span className="save-badge">Saved</span>}
          </div>
        </>
      )}
    </div>
  )
}
