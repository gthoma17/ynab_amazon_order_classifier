import { useState } from 'react'
import { apiPostWithBody } from '../api'

const GITHUB_ISSUES_URL = 'https://github.com/gthoma17/budget-sortbot/issues/new'
const GITHUB_BASE_URL = `${GITHUB_ISSUES_URL}?body=`
const MAX_GITHUB_URL_LENGTH = 8192
const TRUNCATION_NOTE = '\n\n_[Log content truncated — use `docker logs` for full output]_'

interface HelpReportResponse {
  body: string
  sanitized: boolean
  truncated: boolean
}

export default function GetHelpView() {
  const [description, setDescription] = useState('')
  const [includeSyncLogs, setIncludeSyncLogs] = useState(true)
  const [includeAppLogs, setIncludeAppLogs] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reportBody, setReportBody] = useState<string | null>(null)
  const [sanitized, setSanitized] = useState(false)
  const [truncated, setTruncated] = useState(false)
  const [logsInserted, setLogsInserted] = useState(false)
  const [showWarning, setShowWarning] = useState(false)

  const logsRequested = includeSyncLogs || includeAppLogs

  const handleDescriptionChange = (value: string) => {
    setDescription(value)
    // Reset inserted state when the description is edited so the user re-inserts
    // with fresh content if the description changes after insertion.
    setLogsInserted(false)
    setReportBody(null)
    setSanitized(false)
    setTruncated(false)
  }

  const handleInsertLogs = async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await apiPostWithBody<HelpReportResponse>('/api/help/report', {
        description,
        includeSyncLogs,
        includeAppLogs,
      })
      setReportBody(response.body)
      setSanitized(response.sanitized)
      setTruncated(response.truncated)
      setLogsInserted(true)
    } catch {
      setError('Failed to retrieve logs. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleOpenIssue = () => {
    if (logsRequested && !logsInserted) {
      setShowWarning(true)
      return
    }
    openGithubIssue()
  }

  const openGithubIssue = () => {
    const body = reportBody ?? `## Problem Description\n\n${description.trim()}\n`
    const encodedBody = encodeURIComponent(body)
    const encodedNote = encodeURIComponent(TRUNCATION_NOTE)

    let url: string
    if (truncated) {
      // Backend already truncated the body to fit the URL budget (leaving room for the note)
      url = GITHUB_BASE_URL + encodedBody + encodedNote
    } else {
      url = GITHUB_BASE_URL + encodedBody
      // Fallback: if somehow the URL is still too long, truncate the encoded body
      if (url.length > MAX_GITHUB_URL_LENGTH) {
        const maxEncodedBodyLen = MAX_GITHUB_URL_LENGTH - GITHUB_BASE_URL.length - encodedNote.length
        const trimmed = encodedBody.slice(0, maxEncodedBodyLen).replace(/%[0-9A-Fa-f]{0,1}$/, '')
        url = GITHUB_BASE_URL + trimmed + encodedNote
      }
    }
    window.open(url, '_blank', 'noopener,noreferrer')
  }

  const isInsertDisabled = !description.trim() || loading
  const isOpenDisabled = !description.trim() || loading

  return (
    <div>
      <h1>Get Help</h1>
      <p>Something not working? Open a pre-filled GitHub issue and we&apos;ll help.</p>

      <div role="note" aria-label="Redaction notice">
        <strong>Privacy:</strong> Sensitive values like API keys are automatically redacted
        before the issue is opened. Click &ldquo;Insert Logs&rdquo; to preview the sanitized
        content before submitting.
      </div>

      <div>
        <label htmlFor="description">
          Describe the problem <span aria-hidden="true">*</span>
        </label>
        <textarea
          id="description"
          value={description}
          onChange={(e) => handleDescriptionChange(e.target.value)}
          rows={6}
          placeholder="Describe what happened and what you expected to happen"
        />
      </div>

      <div>
        <label>
          <input
            type="checkbox"
            checked={includeSyncLogs}
            onChange={(e) => { setIncludeSyncLogs(e.target.checked); setLogsInserted(false); setReportBody(null) }}
          />
          {' '}Include recent sync log entries (recommended)
        </label>
      </div>

      <div>
        <label>
          <input
            type="checkbox"
            checked={includeAppLogs}
            onChange={(e) => { setIncludeAppLogs(e.target.checked); setLogsInserted(false); setReportBody(null) }}
          />
          {' '}Include full application logs
        </label>
      </div>

      {logsRequested && (
        <div>
          <button
            onClick={handleInsertLogs}
            disabled={isInsertDisabled}
            aria-disabled={isInsertDisabled}
          >
            {loading ? 'Fetching logs…' : 'Insert Logs'}
          </button>
          {logsInserted && <span role="status"> ✓ Logs inserted</span>}
        </div>
      )}

      {reportBody !== null && (
        <div>
          <label htmlFor="reportPreview">
            Issue body preview{sanitized ? ' — sensitive values redacted' : ''}
            {truncated ? ' — content truncated to fit GitHub URL limit' : ''}
          </label>
          <textarea
            id="reportPreview"
            aria-label="Report body preview"
            value={reportBody}
            readOnly
            rows={12}
          />
        </div>
      )}

      {sanitized && (
        <p role="status">Sensitive values (API keys, tokens) were removed from your report.</p>
      )}

      {error && <p role="alert">{error}</p>}

      {showWarning && (
        <div role="dialog" aria-modal="true" aria-label="Logs not inserted warning">
          <p>
            You&apos;ve selected log options but haven&apos;t clicked &ldquo;Insert Logs&rdquo; yet.
            Click &ldquo;Insert Logs&rdquo; first to preview and confirm that sensitive information
            has been redacted, then open the issue.
          </p>
          <button onClick={() => setShowWarning(false)}>Go Back</button>
          <button onClick={() => { setShowWarning(false); openGithubIssue() }}>Open Anyway</button>
        </div>
      )}

      <button
        onClick={handleOpenIssue}
        disabled={isOpenDisabled}
        aria-disabled={isOpenDisabled}
      >
        {loading ? 'Preparing…' : 'Open Issue'}
      </button>

      <p>
        <small>
          No data is sent anywhere by this app. Clicking &ldquo;Open Issue&rdquo; opens GitHub in
          your browser with a pre-filled issue — you control final submission.
        </small>
      </p>
    </div>
  )
}

