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

  useEffect(() => {
    Promise.all([
      apiGet<YnabCategory[]>('/api/ynab/categories'),
      apiGet<CategoryRule[]>('/api/config/categories'),
    ]).then(([cats, rules]) => {
      setCategories(cats)
      const desc: Record<string, string> = {}
      for (const rule of rules) {
        desc[rule.ynabCategoryId] = rule.userDescription
      }
      setDescriptions(desc)
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
    <div>
      <h1>Category Rules</h1>
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
                  onChange={(e) => setDescriptions({ ...descriptions, [cat.id]: e.target.value })}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button onClick={handleSave}>Save</button>
      {saved && <p>Saved</p>}
    </div>
  )
}
