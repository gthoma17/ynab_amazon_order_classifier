import { useEffect, useState } from 'react'
import { apiGet } from '../api'
import SequencePrinter, { type ColumnDef } from '../components/SequencePrinter.tsx'

interface SyncLog {
  id: number
  source: string
  lastRun: string
  status: string
  message: string | null
}

const columns: ColumnDef<SyncLog>[] = [
  { key: 'source', header: 'Source', width: 100 },
  { key: 'lastRun', header: 'Last Run', width: 152 },
  {
    key: 'status',
    header: 'Status',
    width: 72,
    render: (row) => (
      <span data-status={row.status.toUpperCase()}>{row.status.toUpperCase()}</span>
    ),
  },
  { key: 'message', header: 'Message' },
]

export default function LogsView() {
  const [logs, setLogs] = useState<SyncLog[]>([])

  useEffect(() => {
    apiGet<SyncLog[]>('/api/logs').then((data) => setLogs([...data].reverse()))
  }, [])

  return (
    <div>
      <div className="cf-panel" style={{ marginBottom: 'var(--cf-s3)' }}>
        <h1 style={{ marginBottom: 0, borderBottom: 'none', paddingBottom: 0 }}>Sync Logs</h1>
      </div>
      <SequencePrinter columns={columns} entries={logs} data-testid="logs-table" />
    </div>
  )
}
