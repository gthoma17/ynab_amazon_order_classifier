import { useEffect, useState } from 'react'
import { apiGet } from '../api'

interface SyncLog {
  id: number
  source: string
  lastRun: string
  status: string
  message: string | null
}

function statusClass(status: string): string {
  switch (status.toUpperCase()) {
    case 'SUCCESS':
      return 'cf-status cf-status-success'
    case 'FAIL':
      return 'cf-status cf-status-fail'
    default:
      return 'cf-status cf-status-matched'
  }
}

export default function LogsView() {
  const [logs, setLogs] = useState<SyncLog[]>([])

  useEffect(() => {
    apiGet<SyncLog[]>('/api/logs').then(setLogs)
  }, [])

  return (
    <div>
      <h1>Sync Logs</h1>
      <div className="cf-crt" data-testid="logs-table">
        {logs.length === 0 ? (
          <p className="cf-terminal-empty">No logs</p>
        ) : (
          <table className="cf-data-table">
            <thead>
              <tr>
                <th>Source</th>
                <th>Last Run</th>
                <th>Status</th>
                <th>Message</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id} data-testid="log-row">
                  <td>
                    <span className="cf-source-badge">{log.source}</span>
                  </td>
                  <td>{log.lastRun}</td>
                  <td>
                    <span className={statusClass(log.status)}>{log.status}</span>
                  </td>
                  <td>{log.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
