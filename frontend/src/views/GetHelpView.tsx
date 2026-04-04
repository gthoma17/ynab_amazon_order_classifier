import { useState } from 'react'
import { apiPostWithBody } from '../api'

const GITHUB_ISSUES_URL = 'https://github.com/gthoma17/budget-sortbot/issues/new'
const MAX_GITHUB_URL_LENGTH = 8000

interface HelpReportResponse {
  body: string
  sanitized: boolean
}

export default function GetHelpView() {
  const [description, setDescription] = useState('')
  const [includeSyncLogs, setIncludeSyncLogs] = useState(true)
  const [includeAppLogs, setIncludeAppLogs] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sanitized, setSanitized] = useState(false)

  const handleSubmit = async () => {
    setLoading(true)
    setError(null)
    setSanitized(false)
    try {
      const response = await apiPostWithBody<HelpReportResponse>('/api/help/report', {
        description,
        includeSyncLogs,
        includeAppLogs,
      })
      if (response.sanitized) {
        setSanitized(true)
      }
      const body = response.body
      const encodedBody = encodeURIComponent(body)
      const baseUrl = `${GITHUB_ISSUES_URL}?body=`
      let url = baseUrl + encodedBody
      if (url.length > MAX_GITHUB_URL_LENGTH) {
        const note = '\n\n_[truncated — use `docker logs` for full output]_'
        const encodedNote = encodeURIComponent(note)
        const maxEncodedBodyLen = MAX_GITHUB_URL_LENGTH - baseUrl.length - encodedNote.length
        // Remove any trailing partial percent-encoded sequence at the cut point
        const trimmedEncoded = encodedBody.slice(0, maxEncodedBodyLen).replace(/%[0-9A-Fa-f]{0,2}$/, '')
        url = baseUrl + trimmedEncoded + encodedNote
      }
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch {
      setError('Failed to generate report. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const isDisabled = !description.trim() || loading

  return (
    <div>
      <h1>Get Help</h1>
      <p>Something not working? Open a report and we&apos;ll help.</p>

      <div>
        <label htmlFor="description">
          Describe the problem <span aria-hidden="true">*</span>
        </label>
        <textarea
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={6}
          placeholder="Describe what happened and what you expected to happen"
        />
      </div>

      <div>
        <label>
          <input
            type="checkbox"
            checked={includeSyncLogs}
            onChange={(e) => setIncludeSyncLogs(e.target.checked)}
          />
          {' '}Include recent sync log entries (recommended)
        </label>
      </div>

      <div>
        <label>
          <input
            type="checkbox"
            checked={includeAppLogs}
            onChange={(e) => setIncludeAppLogs(e.target.checked)}
          />
          {' '}Include full application logs
        </label>
      </div>

      {sanitized && (
        <p role="status">Sensitive values were removed from your report.</p>
      )}

      {error && <p role="alert">{error}</p>}

      <button onClick={handleSubmit} disabled={isDisabled} aria-disabled={isDisabled}>
        {loading ? 'Preparing report…' : 'Get Help'}
      </button>

      <p>
        <small>
          No data is sent anywhere by this app. Clicking &ldquo;Get Help&rdquo; opens GitHub in
          your browser with a pre-filled issue — you control final submission.
        </small>
      </p>
    </div>
  )
}
