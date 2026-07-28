import { useEffect, useState } from 'react'
import api from '../api'

export default function AuditLog() {
  const [resources, setResources] = useState([])
  const [logs, setLogs] = useState({})
  const [expanded, setExpanded] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.get('/resources').then(res => {
      setResources(res.data)
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [])

  const toggle = async (id) => {
    if (expanded === id) { setExpanded(null); return }
    setExpanded(id)
    if (!logs[id]) {
      const res = await api.get(`/resources/${id}/audit-logs`)
      setLogs(prev => ({ ...prev, [id]: res.data }))
    }
  }

  const formatTime = iso => {
    const d = new Date(iso)
    return d.toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  }

  const actionColor = action => {
    if (action.includes('CREATED')) return { color: '#065f46', bg: '#d1fae5' }
    if (action.includes('TERMINATED')) return { color: '#991b1b', bg: '#fee2e2' }
    if (action.includes('STOPPED')) return { color: '#374151', bg: '#f3f4f6' }
    return { color: '#1e40af', bg: '#dbeafe' }
  }

  return (
    <div>
      <div style={s.topbar}>
        <div>
          <h1 style={s.pageTitle}>Audit Log</h1>
          <p style={s.pageSub}>Track all actions performed on your resources</p>
        </div>
      </div>

      {loading ? (
        <div style={s.loading}>Loading...</div>
      ) : resources.length === 0 ? (
        <div style={s.empty}>
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#cbd5e1" strokeWidth="1.5"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          <p style={s.emptyTitle}>No resources yet</p>
          <p style={s.emptySub}>Create a resource to start seeing audit logs</p>
        </div>
      ) : (
        <div style={s.tableWrap}>
          <table style={s.table}>
            <thead>
              <tr>
                {['Resource', 'Type', 'Region', 'Status', 'Owner', ''].map(h => (
                  <th key={h} style={s.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {resources.map(r => (
                <>
                  <tr key={r.id} style={s.tr} onClick={() => toggle(r.id)}>
                    <td style={s.td}>
                      <div style={s.resourceName}>{r.name}</div>
                      <div style={s.resourceId}>{r.id?.slice(0, 8)}...</div>
                    </td>
                    <td style={s.td}>{r.type}</td>
                    <td style={s.td}><span style={s.regionBadge}>{r.region}</span></td>
                    <td style={s.td}>{r.status}</td>
                    <td style={s.td}>{r.createdBy}</td>
                    <td style={s.td}>
                      <button style={s.expandBtn}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                          style={{ transform: expanded === r.id ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}>
                          <polyline points="6 9 12 15 18 9"/>
                        </svg>
                        {expanded === r.id ? 'Hide' : 'View logs'}
                      </button>
                    </td>
                  </tr>
                  {expanded === r.id && (
                    <tr key={`${r.id}-logs`}>
                      <td colSpan={6} style={s.logsTd}>
                        {!logs[r.id] ? (
                          <div style={s.logsLoading}>Loading logs...</div>
                        ) : logs[r.id].length === 0 ? (
                          <div style={s.logsEmpty}>No audit logs for this resource</div>
                        ) : (
                          <div style={s.logsWrap}>
                            <div style={s.logsHeader}>
                              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                              {logs[r.id].length} event{logs[r.id].length !== 1 ? 's' : ''}
                            </div>
                            <div style={s.timeline}>
                              {logs[r.id].map((log, i) => {
                                const ac = actionColor(log.action)
                                return (
                                  <div key={log.id} style={s.timelineItem}>
                                    <div style={s.timelineDot} />
                                    {i < logs[r.id].length - 1 && <div style={s.timelineLine} />}
                                    <div style={s.timelineContent}>
                                      <span style={{ ...s.actionBadge, color: ac.color, background: ac.bg }}>{log.action}</span>
                                      <span style={s.timelineBy}>by <strong>{log.performedBy}</strong></span>
                                      <span style={s.timelineTime}>{formatTime(log.performedAt)}</span>
                                    </div>
                                  </div>
                                )
                              })}
                            </div>
                          </div>
                        )}
                      </td>
                    </tr>
                  )}
                </>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

const s = {
  topbar: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.75rem' },
  pageTitle: { fontSize: '1.4rem', fontWeight: '700', color: '#0f172a', margin: '0 0 0.2rem', letterSpacing: '-0.02em' },
  pageSub: { color: '#64748b', fontSize: '0.875rem', margin: 0 },
  loading: { color: '#64748b', padding: '2rem', textAlign: 'center' },
  tableWrap: { background: '#fff', border: '1px solid #e2e8f0', borderRadius: '10px', overflow: 'hidden' },
  table: { width: '100%', borderCollapse: 'collapse' },
  th: { padding: '0.75rem 1rem', textAlign: 'left', fontSize: '0.75rem', fontWeight: '600', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em', background: '#f8fafc', borderBottom: '1px solid #e2e8f0' },
  tr: { borderBottom: '1px solid #f1f5f9', cursor: 'pointer' },
  td: { padding: '0.9rem 1rem', fontSize: '0.875rem', color: '#374151', verticalAlign: 'middle' },
  resourceName: { fontWeight: '600', color: '#0f172a', fontSize: '0.875rem' },
  resourceId: { fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.15rem', fontFamily: 'monospace' },
  regionBadge: { background: '#f1f5f9', color: '#475569', padding: '0.2rem 0.55rem', borderRadius: '6px', fontSize: '0.78rem', fontWeight: '500', fontFamily: 'monospace' },
  expandBtn: { display: 'flex', alignItems: 'center', gap: '0.35rem', background: 'none', border: '1px solid #e2e8f0', borderRadius: '6px', padding: '0.3rem 0.65rem', fontSize: '0.8rem', color: '#475569', cursor: 'pointer', fontWeight: '500' },
  logsTd: { padding: 0, background: '#f8fafc', borderBottom: '1px solid #e2e8f0' },
  logsLoading: { padding: '1rem 1.5rem', color: '#94a3b8', fontSize: '0.875rem' },
  logsEmpty: { padding: '1rem 1.5rem', color: '#94a3b8', fontSize: '0.875rem' },
  logsWrap: { padding: '1rem 1.5rem' },
  logsHeader: { display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', color: '#64748b', fontWeight: '500', marginBottom: '0.75rem' },
  timeline: { display: 'flex', flexDirection: 'column', gap: '0.6rem' },
  timelineItem: { display: 'flex', alignItems: 'center', gap: '0.75rem', position: 'relative' },
  timelineDot: { width: '8px', height: '8px', borderRadius: '50%', background: '#3b82f6', flexShrink: 0 },
  timelineLine: { position: 'absolute', left: '3.5px', top: '14px', width: '1px', height: '100%', background: '#e2e8f0' },
  timelineContent: { display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' },
  actionBadge: { padding: '0.2rem 0.6rem', borderRadius: '6px', fontSize: '0.78rem', fontWeight: '600' },
  timelineBy: { fontSize: '0.82rem', color: '#64748b' },
  timelineTime: { fontSize: '0.78rem', color: '#94a3b8', marginLeft: 'auto' },
  empty: { padding: '4rem 2rem', textAlign: 'center', background: '#fff', border: '1px solid #e2e8f0', borderRadius: '10px' },
  emptyTitle: { fontWeight: '600', color: '#374151', margin: '0.75rem 0 0.35rem', fontSize: '0.95rem' },
  emptySub: { color: '#94a3b8', fontSize: '0.875rem', margin: 0 },
}
