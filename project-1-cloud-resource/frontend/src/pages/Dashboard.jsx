import { useEffect, useState } from 'react'
import api from '../api'
import AuditLog from './AuditLog'

const STATUS_META = {
  PENDING:    { color: '#92400e', bg: '#fef3c7', dot: '#f59e0b' },
  RUNNING:    { color: '#065f46', bg: '#d1fae5', dot: '#10b981' },
  STOPPED:    { color: '#374151', bg: '#f3f4f6', dot: '#9ca3af' },
  TERMINATED: { color: '#991b1b', bg: '#fee2e2', dot: '#ef4444' },
}

const TYPE_ICONS = {
  EC2: <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>,
  S3:  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>,
}

function getUser() {
  const token = localStorage.getItem('token')
  if (!token) return { username: 'User', role: '' }
  try {
    const p = JSON.parse(atob(token.split('.')[1]))
    return { username: p.sub, role: p.role || '' }
  } catch { return { username: 'User', role: '' } }
}

function StatusBadge({ status }) {
  const m = STATUS_META[status] || { color: '#374151', bg: '#f3f4f6', dot: '#9ca3af' }
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.35rem', padding: '0.25rem 0.65rem', borderRadius: '999px', fontSize: '0.75rem', fontWeight: '600', color: m.color, background: m.bg }}>
      <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: m.dot, display: 'inline-block' }} />
      {status}
    </span>
  )
}

export default function Dashboard({ onLogout }) {
  const [resources, setResources] = useState([])
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState({ name: '', type: 'EC2', region: 'us-east-1' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [filter, setFilter] = useState('ALL')
  const [page, setPage] = useState('resources')
  const user = getUser()

  const load = async () => {
    try { const res = await api.get('/resources'); setResources(res.data) }
    catch { setError('Failed to load resources') }
  }

  useEffect(() => { load() }, [])

  const create = async e => {
    e.preventDefault()
    setLoading(true)
    try {
      await api.post('/resources', form)
      setShowModal(false)
      setForm({ name: '', type: 'EC2', region: 'us-east-1' })
      load()
    } catch { setError('Failed to create resource') }
    finally { setLoading(false) }
  }

  const terminate = async id => {
    if (!confirm('Terminate this resource? This cannot be undone.')) return
    try { await api.delete(`/resources/${id}`); load() }
    catch { setError('Failed to terminate resource') }
  }

  const updateStatus = async (id, status) => {
    try { await api.patch(`/resources/${id}/status`, { status }); load() }
    catch { setError('Failed to update status') }
  }

  const filtered = filter === 'ALL' ? resources : resources.filter(r => r.status === filter)

  const counts = {
    total: resources.length,
    running: resources.filter(r => r.status === 'RUNNING').length,
    stopped: resources.filter(r => r.status === 'STOPPED').length,
    pending: resources.filter(r => r.status === 'PENDING').length,
  }

  return (
    <div style={s.shell}>
      {/* Sidebar */}
      <aside style={s.sidebar}>
        <div style={s.sidebarLogo}>
          <div style={s.logoIcon}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
              <path d="M3 15a4 4 0 004 4h9a5 5 0 10-.1-9.999 5.002 5.002 0 10-9.78 2.096A4.001 4.001 0 003 15z"/>
            </svg>
          </div>
          <span style={s.logoText}>CloudOps</span>
        </div>

        <nav style={s.nav}>
          <div style={{ ...s.navItem, ...(page === 'resources' ? s.navActive : {}) }} onClick={() => setPage('resources')}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M3 15a4 4 0 004 4h9a5 5 0 10-.1-9.999 5.002 5.002 0 10-9.78 2.096A4.001 4.001 0 003 15z"/></svg>
            Resources
          </div>
          {user.role === 'ADMIN' && (
            <div style={{ ...s.navItem, ...(page === 'audit' ? s.navActive : {}) }} onClick={() => setPage('audit')}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
              Audit Log
            </div>
          )}
        </nav>

        <div style={s.sidebarFooter}>
          <div style={s.userInfo}>
            <div style={s.avatar}>{user.username[0]?.toUpperCase()}</div>
            <div>
              <div style={s.userName}>{user.username}</div>
              <div style={s.userRole}>{user.role}</div>
            </div>
          </div>
          <button style={s.logoutBtn} onClick={onLogout}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
          </button>
        </div>
      </aside>

      {/* Main */}
      <main style={s.main}>
        {page === 'audit' ? <AuditLog /> : (
        <>
        <div style={s.topbar}>
          <div>
            <h1 style={s.pageTitle}>Resources</h1>
            <p style={s.pageSub}>Manage your cloud infrastructure</p>
          </div>
          {user.role !== 'VIEWER' && (
          <button style={s.primaryBtn} onClick={() => setShowModal(true)}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            New Resource
          </button>
          )}
        </div>

        {error && (
          <div style={s.errorBanner}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#dc2626" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            {error}
            <button style={s.dismissBtn} onClick={() => setError('')}>✕</button>
          </div>
        )}

        {/* Stats */}
        <div style={s.statsRow}>
          {[
            { label: 'Total', value: counts.total, color: '#1d4ed8' },
            { label: 'Running', value: counts.running, color: '#059669' },
            { label: 'Stopped', value: counts.stopped, color: '#6b7280' },
          ].map(stat => (
            <div key={stat.label} style={s.statCard}>
              <div style={{ ...s.statValue, color: stat.color }}>{stat.value}</div>
              <div style={s.statLabel}>{stat.label}</div>
            </div>
          ))}
        </div>

        {/* Filter tabs */}
        <div style={s.tabs}>
          {['ALL', 'RUNNING', 'STOPPED', 'TERMINATED'].map(f => (
            <button key={f} style={{ ...s.tab, ...(filter === f ? s.tabActive : {}) }} onClick={() => setFilter(f)}>
              {f === 'ALL' ? 'All resources' : f.charAt(0) + f.slice(1).toLowerCase()}
              <span style={{ ...s.tabCount, ...(filter === f ? s.tabCountActive : {}) }}>
                {f === 'ALL' ? resources.length : resources.filter(r => r.status === f).length}
              </span>
            </button>
          ))}
        </div>

        {/* Table */}
        <div style={s.tableWrap}>
          <table style={s.table}>
            <thead>
              <tr>
                {['Name', 'Type', 'Region', 'Status', 'Owner', 'Actions'].map(h => (
                  <th key={h} style={s.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((r, i) => (
                <tr key={r.id} style={{ ...s.tr, background: i % 2 === 0 ? '#fff' : '#fafafa' }}>
                  <td style={s.td}>
                    <div style={s.resourceName}>{r.name}</div>
                    <div style={s.resourceId}>{r.awsResourceId || r.id?.slice(0, 8) + '...'}</div>
                  </td>
                  <td style={s.td}>
                    <div style={s.typeCell}>
                      <span style={s.typeIcon}>{TYPE_ICONS[r.type]}</span>
                      {r.type}
                    </div>
                  </td>
                  <td style={s.td}><span style={s.regionBadge}>{r.region}</span></td>
                  <td style={s.td}><StatusBadge status={r.status} /></td>
                  <td style={s.td}><span style={s.ownerCell}>{r.createdBy}</span></td>
                  <td style={s.td}>
                    <div style={s.actions}>
                      {r.status !== 'TERMINATED' && user.role !== 'VIEWER' && (
                        <>
                          <select style={s.statusSelect} value={r.status}
                            onChange={e => updateStatus(r.id, e.target.value)}>
                            <option value="RUNNING">Running</option>
                            <option value="STOPPED">Stopped</option>
                            <option value="TERMINATED">Terminated</option>
                          </select>
                          <button style={s.dangerBtn} onClick={() => terminate(r.id)}>Terminate</button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filtered.length === 0 && (
            <div style={s.empty}>
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#cbd5e1" strokeWidth="1.5"><path d="M3 15a4 4 0 004 4h9a5 5 0 10-.1-9.999 5.002 5.002 0 10-9.78 2.096A4.001 4.001 0 003 15z"/></svg>
              <p style={s.emptyTitle}>No resources found</p>
              <p style={s.emptySub}>{filter !== 'ALL' ? `No ${filter.toLowerCase()} resources` : 'Create your first resource to get started'}</p>
            </div>
          )}
        </div>
        </>)}
      </main>

      {/* Modal */}
      {showModal && (
        <div style={s.overlay} onClick={() => setShowModal(false)}>
          <div style={s.modal} onClick={e => e.stopPropagation()}>
            <div style={s.modalHeader}>
              <h2 style={s.modalTitle}>New Resource</h2>
              <button style={s.closeBtn} onClick={() => setShowModal(false)}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
              </button>
            </div>
            <form onSubmit={create} style={s.modalForm}>
              <div style={s.field}>
                <label style={s.label}>Resource Name</label>
                <input style={s.input} placeholder="e.g. prod-web-server" value={form.name}
                  onChange={e => setForm({ ...form, name: e.target.value })} required autoFocus />
              </div>
              <div style={s.field}>
                <label style={s.label}>Resource Type</label>
                <select style={s.input} value={form.type} onChange={e => setForm({ ...form, type: e.target.value })}>
                  <option value="EC2">EC2 — Virtual Machine</option>
                  <option value="S3">S3 — Object Storage</option>
                </select>
              </div>
              <div style={s.field}>
                <label style={s.label}>Region</label>
                <select style={s.input} value={form.region} onChange={e => setForm({ ...form, region: e.target.value })}>
                  {['us-east-1','us-west-2','eu-west-1','eu-central-1','ap-southeast-1','ap-northeast-1'].map(r => (
                    <option key={r} value={r}>{r}</option>
                  ))}
                </select>
              </div>
              <div style={s.modalActions}>
                <button type="button" style={s.cancelBtn} onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" style={{ ...s.primaryBtn, opacity: loading ? 0.7 : 1 }} disabled={loading}>
                  {loading ? 'Creating...' : 'Create Resource'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

const s = {
  shell: { display: 'flex', minHeight: '100vh', background: '#f8fafc', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif' },
  sidebar: { width: '220px', background: '#fff', borderRight: '1px solid #e2e8f0', display: 'flex', flexDirection: 'column', flexShrink: 0, position: 'fixed', top: 0, left: 0, height: '100vh' },
  sidebarLogo: { display: 'flex', alignItems: 'center', gap: '0.6rem', padding: '1.25rem 1.25rem 1rem' },
  logoIcon: { width: '32px', height: '32px', background: 'linear-gradient(135deg, #1d4ed8, #3b82f6)', borderRadius: '7px', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 },
  logoText: { fontWeight: '700', fontSize: '1rem', color: '#0f172a', letterSpacing: '-0.02em' },
  nav: { padding: '0.5rem 0.75rem', flex: 1, display: 'flex', flexDirection: 'column', gap: '2px' },
  navItem: { display: 'flex', alignItems: 'center', gap: '0.6rem', padding: '0.55rem 0.75rem', borderRadius: '7px', fontSize: '0.875rem', color: '#64748b', cursor: 'pointer', fontWeight: '500' },
  navActive: { background: '#eff6ff', color: '#1d4ed8' },
  sidebarFooter: { padding: '1rem', borderTop: '1px solid #f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' },
  userInfo: { display: 'flex', alignItems: 'center', gap: '0.6rem' },
  avatar: { width: '30px', height: '30px', borderRadius: '50%', background: 'linear-gradient(135deg, #1d4ed8, #3b82f6)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.8rem', fontWeight: '700', flexShrink: 0 },
  userName: { fontSize: '0.8rem', fontWeight: '600', color: '#0f172a' },
  userRole: { fontSize: '0.7rem', color: '#94a3b8', textTransform: 'capitalize' },
  logoutBtn: { background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', padding: '0.25rem', borderRadius: '6px', display: 'flex' },
  main: { marginLeft: '220px', flex: 1, padding: '2rem 2.5rem', minWidth: 0 },
  topbar: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.75rem' },
  pageTitle: { fontSize: '1.4rem', fontWeight: '700', color: '#0f172a', margin: '0 0 0.2rem', letterSpacing: '-0.02em' },
  pageSub: { color: '#64748b', fontSize: '0.875rem', margin: 0 },
  primaryBtn: { display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.6rem 1.1rem', background: 'linear-gradient(135deg, #1d4ed8, #3b82f6)', color: '#fff', border: 'none', borderRadius: '8px', fontSize: '0.875rem', fontWeight: '600', cursor: 'pointer' },
  errorBanner: { display: 'flex', alignItems: 'center', gap: '0.5rem', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '8px', padding: '0.75rem 1rem', color: '#dc2626', fontSize: '0.875rem', marginBottom: '1.25rem' },
  dismissBtn: { marginLeft: 'auto', background: 'none', border: 'none', color: '#dc2626', cursor: 'pointer', fontSize: '0.8rem' },
  statsRow: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem', marginBottom: '1.5rem' },
  statCard: { background: '#fff', border: '1px solid #e2e8f0', borderRadius: '10px', padding: '1.1rem 1.25rem' },
  statValue: { fontSize: '1.75rem', fontWeight: '700', letterSpacing: '-0.03em', lineHeight: 1 },
  statLabel: { fontSize: '0.8rem', color: '#64748b', marginTop: '0.3rem', fontWeight: '500' },
  tabs: { display: 'flex', gap: '0.25rem', marginBottom: '1rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0' },
  tab: { display: 'flex', alignItems: 'center', gap: '0.4rem', padding: '0.6rem 0.85rem', background: 'none', border: 'none', borderBottom: '2px solid transparent', cursor: 'pointer', fontSize: '0.85rem', fontWeight: '500', color: '#64748b', marginBottom: '-1px' },
  tabActive: { color: '#1d4ed8', borderBottomColor: '#1d4ed8' },
  tabCount: { background: '#f1f5f9', color: '#64748b', borderRadius: '999px', padding: '0.1rem 0.45rem', fontSize: '0.75rem', fontWeight: '600' },
  tabCountActive: { background: '#eff6ff', color: '#1d4ed8' },
  tableWrap: { background: '#fff', border: '1px solid #e2e8f0', borderRadius: '10px', overflow: 'hidden' },
  table: { width: '100%', borderCollapse: 'collapse' },
  th: { padding: '0.75rem 1rem', textAlign: 'left', fontSize: '0.75rem', fontWeight: '600', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.05em', background: '#f8fafc', borderBottom: '1px solid #e2e8f0' },
  tr: { borderBottom: '1px solid #f1f5f9', transition: 'background 0.1s' },
  td: { padding: '0.9rem 1rem', fontSize: '0.875rem', color: '#374151', verticalAlign: 'middle' },
  resourceName: { fontWeight: '600', color: '#0f172a', fontSize: '0.875rem' },
  resourceId: { fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.15rem', fontFamily: 'monospace' },
  typeCell: { display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#374151' },
  typeIcon: { color: '#64748b', display: 'flex' },
  regionBadge: { background: '#f1f5f9', color: '#475569', padding: '0.2rem 0.55rem', borderRadius: '6px', fontSize: '0.78rem', fontWeight: '500', fontFamily: 'monospace' },
  ownerCell: { color: '#64748b' },
  actions: { display: 'flex', alignItems: 'center', gap: '0.5rem' },
  statusSelect: { padding: '0.35rem 0.6rem', borderRadius: '6px', border: '1px solid #e2e8f0', background: '#fff', fontSize: '0.8rem', color: '#374151', cursor: 'pointer' },
  dangerBtn: { padding: '0.35rem 0.75rem', borderRadius: '6px', background: '#fff', border: '1px solid #fecaca', color: '#dc2626', fontSize: '0.8rem', fontWeight: '500', cursor: 'pointer' },
  empty: { padding: '4rem 2rem', textAlign: 'center' },
  emptyTitle: { fontWeight: '600', color: '#374151', margin: '0.75rem 0 0.35rem', fontSize: '0.95rem' },
  emptySub: { color: '#94a3b8', fontSize: '0.875rem', margin: 0 },
  overlay: { position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 50, backdropFilter: 'blur(2px)' },
  modal: { background: '#fff', borderRadius: '12px', width: '100%', maxWidth: '460px', boxShadow: '0 20px 60px rgba(0,0,0,0.15)', margin: '1rem' },
  modalHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1.25rem 1.5rem', borderBottom: '1px solid #f1f5f9' },
  modalTitle: { fontSize: '1rem', fontWeight: '700', color: '#0f172a', margin: 0 },
  closeBtn: { background: 'none', border: 'none', cursor: 'pointer', color: '#94a3b8', display: 'flex', padding: '0.25rem', borderRadius: '6px' },
  modalForm: { padding: '1.5rem', display: 'flex', flexDirection: 'column', gap: '1.1rem' },
  field: { display: 'flex', flexDirection: 'column', gap: '0.4rem' },
  label: { fontSize: '0.85rem', fontWeight: '500', color: '#374151' },
  input: { padding: '0.65rem 0.85rem', borderRadius: '8px', border: '1.5px solid #e2e8f0', fontSize: '0.9rem', color: '#0f172a', outline: 'none', background: '#fff' },
  modalActions: { display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', paddingTop: '0.5rem' },
  cancelBtn: { padding: '0.6rem 1.1rem', borderRadius: '8px', background: '#fff', border: '1px solid #e2e8f0', color: '#374151', fontSize: '0.875rem', fontWeight: '500', cursor: 'pointer' },
}
