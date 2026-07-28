import { useEffect, useState } from 'react'
import api from '../api'

function getUsername() {
  const token = localStorage.getItem('wf_token')
  if (!token) return null
  try { return JSON.parse(atob(token.split('.')[1])).sub } catch { return null }
}

function getSystemRole() {
  const token = localStorage.getItem('wf_token')
  if (!token) return null
  try {
    const roles = JSON.parse(atob(token.split('.')[1])).roles || []
    return roles.includes('ROLE_SYSTEM_ADMIN') ? 'SYSTEM_ADMIN' : 'SYSTEM_MEMBER'
  } catch { return null }
}

export default function Workspaces({ onSelect, onLogout }) {
  const [workspaces, setWorkspaces] = useState([])
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [creating, setCreating] = useState(false)
  const username = getUsername()
  const isAdmin = getSystemRole() === 'SYSTEM_ADMIN'

  const load = async () => {
    try {
      const res = await api.get('/workspaces')
      setWorkspaces(res.data)
    } catch { setError('Failed to load workspaces') }
  }

  useEffect(() => { load() }, [])

  const create = async e => {
    e.preventDefault()
    if (!name.trim()) return
    setCreating(true)
    try {
      await api.post('/workspaces', { name: name.trim() })
      setName('')
      load()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create workspace')
    } finally {
      setCreating(false)
    }
  }

  return (
    <div style={s.root}>
      {/* Sidebar */}
      <aside style={s.sidebar}>
        <div style={s.sidebarTop}>
          <div style={s.logo}>
            <svg width="26" height="26" viewBox="0 0 28 28" fill="none">
              <rect width="28" height="28" rx="7" fill="#4f7ef8"/>
              <rect x="7" y="8" width="14" height="2.5" rx="1.25" fill="white"/>
              <rect x="7" y="12.75" width="10" height="2.5" rx="1.25" fill="white" opacity="0.7"/>
              <rect x="7" y="17.5" width="7" height="2.5" rx="1.25" fill="white" opacity="0.4"/>
            </svg>
            <span style={s.logoText}>FlowDesk</span>
          </div>
        </div>

        <div style={s.sidebarSection}>
          <p style={s.sectionLabel}>Workspaces</p>
          {workspaces.length === 0
            ? <p style={s.emptyNav}>No workspaces yet</p>
            : workspaces.map(w => (
              <button key={w.id} style={s.navItem} onClick={() => onSelect(w.id)}>
                <span style={s.wsIcon}>{w.name.charAt(0).toUpperCase()}</span>
                <span style={s.wsNavName}>{w.name}</span>
              </button>
            ))
          }
        </div>

        <div style={s.sidebarBottom}>
          <div style={s.userRow}>
            <div style={s.avatar}>{username?.charAt(0).toUpperCase()}</div>
            <div style={s.userMeta}>
              <span style={s.userName}>{username}</span>
              <span className={`badge ${isAdmin ? 'badge-blue' : 'badge-gray'}`}>
                {isAdmin ? 'Admin' : 'Member'}
              </span>
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={onLogout} style={{ width: '100%', justifyContent: 'center', marginTop: 8 }}>
            Sign out
          </button>
        </div>
      </aside>

      {/* Main */}
      <main style={s.main}>
        <div style={s.topbar}>
          <div>
            <h1 style={s.pageTitle}>Workspaces</h1>
            <p style={s.pageSub}>Select a workspace to open its board</p>
          </div>
          {isAdmin && (
            <form onSubmit={create} style={s.createForm}>
              <input className="input" style={{ width: 220 }} placeholder="New workspace name"
                value={name} onChange={e => setName(e.target.value)} required />
              <button className="btn btn-primary" type="submit" disabled={creating}>
                {creating ? 'Creating…' : '+ New workspace'}
              </button>
            </form>
          )}
        </div>

        {error && (
          <div className="alert-error" onClick={() => setError('')} style={{ marginBottom: 24 }}>
            {error} <span style={{ fontSize: 16 }}>×</span>
          </div>
        )}

        {workspaces.length === 0 ? (
          <div style={s.empty}>
            <div style={s.emptyIcon}>
              <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="var(--text-3)" strokeWidth="1.5">
                <rect x="3" y="3" width="18" height="18" rx="3"/>
                <path d="M3 9h18M9 21V9"/>
              </svg>
            </div>
            <p style={s.emptyTitle}>{isAdmin ? 'Create your first workspace' : 'No workspaces yet'}</p>
            <p style={s.emptyDesc}>
              {isAdmin ? 'Use the form above to create a workspace and invite your team.' : 'You\'ll be added here once an admin invites you to a workspace.'}
            </p>
          </div>
        ) : (
          <div style={s.grid}>
            {workspaces.map(w => (
              <button key={w.id} style={s.card} onClick={() => onSelect(w.id)}>
                <div style={s.cardIcon}>{w.name.charAt(0).toUpperCase()}</div>
                <div style={s.cardBody}>
                  <p style={s.cardName}>{w.name}</p>
                  <p style={s.cardHint}>Open board →</p>
                </div>
              </button>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}

const s = {
  root: { display: 'flex', minHeight: '100vh', background: 'var(--bg)' },
  sidebar: { width: 240, background: 'var(--surface)', borderRight: '1px solid var(--border)', display: 'flex', flexDirection: 'column', flexShrink: 0 },
  sidebarTop: { padding: '20px 16px 16px', borderBottom: '1px solid var(--border)' },
  logo: { display: 'flex', alignItems: 'center', gap: 8 },
  logoText: { fontSize: 16, fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.02em' },
  sidebarSection: { flex: 1, padding: '16px 8px', overflowY: 'auto' },
  sectionLabel: { fontSize: 11, fontWeight: 600, color: 'var(--text-3)', letterSpacing: '0.06em', textTransform: 'uppercase', padding: '0 8px', marginBottom: 6 },
  emptyNav: { fontSize: 13, color: 'var(--text-3)', padding: '6px 8px' },
  navItem: { display: 'flex', alignItems: 'center', gap: 8, width: '100%', padding: '7px 8px', borderRadius: 'var(--radius)', background: 'none', border: 'none', color: 'var(--text-2)', cursor: 'pointer', textAlign: 'left', transition: 'background 0.15s, color 0.15s' },
  wsIcon: { width: 24, height: 24, borderRadius: 6, background: 'var(--accent-bg)', color: 'var(--accent)', fontSize: 12, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 },
  wsNavName: { fontSize: 13, fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  sidebarBottom: { padding: 12, borderTop: '1px solid var(--border)' },
  userRow: { display: 'flex', alignItems: 'center', gap: 10, padding: '6px 4px' },
  avatar: { width: 32, height: 32, borderRadius: '50%', background: 'var(--accent-bg)', color: 'var(--accent)', fontWeight: 700, fontSize: 13, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 },
  userMeta: { display: 'flex', flexDirection: 'column', gap: 3, overflow: 'hidden' },
  userName: { fontSize: 13, fontWeight: 600, color: 'var(--text)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  main: { flex: 1, padding: '40px 48px', overflowY: 'auto' },
  topbar: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 32, gap: 16, flexWrap: 'wrap' },
  pageTitle: { fontSize: 22, fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.03em', marginBottom: 4 },
  pageSub: { color: 'var(--text-2)', fontSize: 14 },
  createForm: { display: 'flex', gap: 8, alignItems: 'center' },
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 16 },
  card: { display: 'flex', alignItems: 'center', gap: 14, padding: '18px 20px', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', cursor: 'pointer', textAlign: 'left', transition: 'border-color 0.15s, background 0.15s' },
  cardIcon: { width: 40, height: 40, borderRadius: 10, background: 'var(--accent-bg)', color: 'var(--accent)', fontWeight: 700, fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 },
  cardBody: { overflow: 'hidden' },
  cardName: { fontWeight: 600, color: 'var(--text)', fontSize: 14, marginBottom: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  cardHint: { color: 'var(--text-3)', fontSize: 12 },
  empty: { display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12, paddingTop: 80, textAlign: 'center' },
  emptyIcon: { width: 64, height: 64, borderRadius: 16, background: 'var(--surface)', border: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  emptyTitle: { fontWeight: 600, color: 'var(--text)', fontSize: 16 },
  emptyDesc: { color: 'var(--text-2)', fontSize: 14, maxWidth: 340 }
}
