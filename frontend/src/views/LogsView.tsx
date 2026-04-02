import { useEffect, useState } from 'react'
import { apiGet } from '../api'

interface SyncLog {
  id: number
  source: string
  lastRun: string
  status: string
  message: string | null
}

export default function LogsView() {
  const [logs, setLogs] = useState<SyncLog[]>([])

  useEffect(() => {
    apiGet<SyncLog[]>('/api/logs').then(setLogs)
  }, [])

  return (
    <div>
      <h1>Sync Logs</h1>
      {logs.length === 0 ? (
        <p>No logs</p>
      ) : (
        <table>
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
              <tr key={log.id}>
                <td>{log.source}</td>
                <td>{log.lastRun}</td>
                <td>{log.status}</td>
                <td>{log.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
