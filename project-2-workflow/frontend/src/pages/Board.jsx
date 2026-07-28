import { useEffect, useState } from 'react'
import api from '../api'

const STATUSES = ['TODO', 'IN_PROGRESS', 'REVIEW', 'DONE']

const STATUS_META = {
  TODO:        { label: 'To Do',       cls: 'badge-gray',   dot: '#555d70' },
  IN_PROGRESS: { label: 'In Progress', cls: 'badge-blue',   dot: '#4f7ef8' },
  REVIEW:      { label: 'Review',      cls: 'badge-amber',  dot: '#f59e0b' },
  DONE:        { label: 'Done',        cls: 'badge-green',  dot: '#22c55e' },
}

function getUsername() {
  const token = localStorage.getItem('wf_token')
  if (!token) return null
  try { return JSON.parse(atob(token.split('.')[1])).sub } catch { return null }
}

function Avatar({ name, size = 28 }) {
  return (
    <div style={{ width: size, height: size, borderRadius: '50%', background: 'var(--accent-bg)', color: 'var(--accent)', fontWeight: 700, fontSize: size * 0.4, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
      {name?.charAt(0).toUpperCase()}
    </div>
  )
}

export default function Board({ workspaceId, onBack }) {
  const [tasks, setTasks] = useState([])
  const [members, setMembers] = useState([])
  const [orgUsers, setOrgUsers] = useState([])
  const [tab, setTab] = useState('board')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ title: '', description: '', assignee: '' })
  const [inviteUsername, setInviteUsername] = useState('')
  const [inviteRole, setInviteRole] = useState('MEMBER')
  const [myRole, setMyRole] = useState(null)
  const [error, setError] = useState('')
  const [workspaceName, setWorkspaceName] = useState('')
  const currentUser = getUsername()

  const loadTasks = async () => {
    try { const res = await api.get(`/workspaces/${workspaceId}/tasks`); setTasks(res.data) }
    catch { setError('Failed to load tasks') }
  }

  const loadMembers = async () => {
    try {
      const res = await api.get(`/workspaces/${workspaceId}/members`)
      setMembers(res.data)
      setMyRole(res.data.find(m => m.username === currentUser)?.role || 'MEMBER')
    } catch { setError('Failed to load members') }
  }

  const loadOrgUsers = async () => {
    try { const res = await api.get('/users'); setOrgUsers(res.data) } catch {}
  }

  useEffect(() => { loadTasks(); loadMembers(); loadOrgUsers() }, [workspaceId])

  const createTask = async e => {
    e.preventDefault()
    try {
      await api.post(`/workspaces/${workspaceId}/tasks`, form)
      setShowForm(false)
      setForm({ title: '', description: '', assignee: '' })
      loadTasks()
    } catch (err) { setError(err.response?.data?.message || 'Failed to create task') }
  }

  const updateStatus = async (taskId, status) => {
    try { await api.patch(`/workspaces/${workspaceId}/tasks/${taskId}/status`, { status }); loadTasks() }
    catch (err) { setError(err.response?.data?.message || 'Invalid status transition') }
  }

  const deleteTask = async taskId => {
    try { await api.delete(`/workspaces/${workspaceId}/tasks/${taskId}`); loadTasks() }
    catch { setError('Only workspace admins can delete tasks') }
  }

  const inviteMember = async e => {
    e.preventDefault()
    try {
      await api.post(`/workspaces/${workspaceId}/members`, { username: inviteUsername, role: inviteRole })
      setInviteUsername(''); setInviteRole('MEMBER')
      loadMembers(); loadOrgUsers()
    } catch (err) { setError(err.response?.data?.message || 'Failed to add member') }
  }

  const removeMember = async username => {
    try { await api.delete(`/workspaces/${workspaceId}/members/${username}`); loadMembers(); loadOrgUsers() }
    catch (err) { setError(err.response?.data?.message || 'Failed to remove member') }
  }

  const byStatus = s => tasks.filter(t => t.status === s)
  const isAdmin = myRole === 'ADMIN'
  const nonMembers = orgUsers.filter(u => !members.map(m => m.username).includes(u))

  return (
    <div style={s.root}>
      {/* Top bar */}
      <header style={s.header}>
        <div style={s.headerLeft}>
          <button className="btn btn-ghost btn-sm" onClick={onBack} style={{ gap: 4 }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M19 12H5M12 5l-7 7 7 7"/></svg>
            Back
          </button>
          <div style={s.dividerV}/>
          <div style={s.logo}>
            <svg width="22" height="22" viewBox="0 0 28 28" fill="none">
              <rect width="28" height="28" rx="7" fill="#4f7ef8"/>
              <rect x="7" y="8" width="14" height="2.5" rx="1.25" fill="white"/>
              <rect x="7" y="12.75" width="10" height="2.5" rx="1.25" fill="white" opacity="0.7"/>
              <rect x="7" y="17.5" width="7" height="2.5" rx="1.25" fill="white" opacity="0.4"/>
            </svg>
            <span style={s.logoText}>FlowDesk</span>
          </div>
        </div>

        <nav style={s.tabs}>
          {[['board', 'Board'], ['members', 'Members']].map(([id, label]) => (
            <button key={id} style={{ ...s.tab, ...(tab === id ? s.tabActive : {}) }} onClick={() => setTab(id)}>
              {label}
              {id === 'members' && members.length > 0 && (
                <span style={s.tabCount}>{members.length}</span>
              )}
            </button>
          ))}
        </nav>

        <div style={s.headerRight}>
          <Avatar name={currentUser} size={30} />
          <div>
            <p style={s.headerUser}>{currentUser}</p>
            <span className={`badge ${isAdmin ? 'badge-blue' : 'badge-gray'}`} style={{ fontSize: 10 }}>
              {isAdmin ? 'Admin' : 'Member'}
            </span>
          </div>
        </div>
      </header>

      <div style={s.body}>
        {error && (
          <div className="alert-error" onClick={() => setError('')} style={{ marginBottom: 16 }}>
            {error} <span style={{ fontSize: 16 }}>×</span>
          </div>
        )}

        {/* ── Board tab ── */}
        {tab === 'board' && (
          <>
            <div style={s.boardBar}>
              <div>
                <h2 style={s.pageTitle}>Task Board</h2>
                <p style={s.pageSub}>{tasks.length} task{tasks.length !== 1 ? 's' : ''}</p>
              </div>
              <button className="btn btn-primary" onClick={() => setShowForm(!showForm)}>
                + New task
              </button>
            </div>

            {showForm && (
              <form onSubmit={createTask} style={s.formCard}>
                <p style={s.formTitle}>New task</p>
                <div style={s.formRow}>
                  <div className="field" style={{ flex: 2 }}>
                    <label>Title</label>
                    <input className="input" placeholder="Task title" value={form.title}
                      onChange={e => setForm({ ...form, title: e.target.value })} required autoFocus />
                  </div>
                  <div className="field" style={{ flex: 2 }}>
                    <label>Description</label>
                    <input className="input" placeholder="Optional description" value={form.description}
                      onChange={e => setForm({ ...form, description: e.target.value })} />
                  </div>
                  <div className="field" style={{ flex: 1 }}>
                    <label>Assignee</label>
                    <select className="input" value={form.assignee}
                      onChange={e => setForm({ ...form, assignee: e.target.value })}>
                      <option value="">Unassigned</option>
                      {members.map(m => <option key={m.username} value={m.username}>{m.username}</option>)}
                    </select>
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button className="btn btn-primary" type="submit">Add task</button>
                  <button className="btn btn-ghost" type="button" onClick={() => setShowForm(false)}>Cancel</button>
                </div>
              </form>
            )}

            <div style={s.board}>
              {STATUSES.map(status => {
                const meta = STATUS_META[status]
                const col = byStatus(status)
                return (
                  <div key={status} style={s.column}>
                    <div style={s.colHeader}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ ...s.dot, background: meta.dot }}/>
                        <span style={s.colTitle}>{meta.label}</span>
                      </div>
                      <span style={s.colCount}>{col.length}</span>
                    </div>

                    <div style={s.colBody}>
                      {col.map(task => (
                        <div key={task.id} style={s.taskCard}>
                          <div style={s.taskTop}>
                            <p style={s.taskTitle}>{task.title}</p>
                            {isAdmin && (
                              <button style={s.deleteBtn} onClick={() => deleteTask(task.id)} title="Delete task">
                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><path d="M18 6L6 18M6 6l12 12"/></svg>
                              </button>
                            )}
                          </div>
                          {task.description && <p style={s.taskDesc}>{task.description}</p>}
                          {task.assignee && (
                            <div style={s.assigneeRow}>
                              <Avatar name={task.assignee} size={18} />
                              <span style={s.assigneeName}>{task.assignee}</span>
                            </div>
                          )}
                          <select style={s.statusSelect} value={task.status}
                            onChange={e => updateStatus(task.id, e.target.value)}>
                            {STATUSES.map(st => (
                              <option key={st} value={st}>{STATUS_META[st].label}</option>
                            ))}
                          </select>
                        </div>
                      ))}
                      {col.length === 0 && <p style={s.colEmpty}>No tasks</p>}
                    </div>
                  </div>
                )
              })}
            </div>
          </>
        )}

        {/* ── Members tab ── */}
        {tab === 'members' && (
          <div style={s.membersWrap}>
            <div style={s.boardBar}>
              <div>
                <h2 style={s.pageTitle}>Members</h2>
                <p style={s.pageSub}>{members.length} member{members.length !== 1 ? 's' : ''}</p>
              </div>
            </div>

            {isAdmin && nonMembers.length > 0 && (
              <form onSubmit={inviteMember} style={s.formCard}>
                <p style={s.formTitle}>Invite member</p>
                <div style={s.formRow}>
                  <div className="field" style={{ flex: 2 }}>
                    <label>User</label>
                    <select className="input" value={inviteUsername}
                      onChange={e => setInviteUsername(e.target.value)} required>
                      <option value="">Select a user…</option>
                      {nonMembers.map(u => <option key={u} value={u}>{u}</option>)}
                    </select>
                  </div>
                  <div className="field" style={{ flex: 1 }}>
                    <label>Role</label>
                    <select className="input" value={inviteRole}
                      onChange={e => setInviteRole(e.target.value)}>
                      <option value="MEMBER">Member</option>
                      <option value="ADMIN">Admin</option>
                    </select>
                  </div>
                  <div style={{ alignSelf: 'flex-end' }}>
                    <button className="btn btn-primary" type="submit">Invite</button>
                  </div>
                </div>
              </form>
            )}

            {isAdmin && nonMembers.length === 0 && (
              <div style={{ ...s.formCard, color: 'var(--text-2)', fontSize: 13 }}>
                All organisation users are already members of this workspace.
              </div>
            )}

            <div style={s.memberList}>
              {members.map(m => (
                <div key={m.username} style={s.memberRow}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                    <Avatar name={m.username} size={36} />
                    <div>
                      <p style={s.memberName}>
                        {m.username}
                        {m.username === currentUser && <span style={s.youTag}> · you</span>}
                      </p>
                      <span className={`badge ${m.role === 'ADMIN' ? 'badge-blue' : 'badge-gray'}`}>
                        {m.role}
                      </span>
                    </div>
                  </div>
                  {isAdmin && m.username !== currentUser && (
                    <button className="btn btn-danger btn-sm" onClick={() => removeMember(m.username)}>
                      Remove
                    </button>
                  )}
                </div>
              ))}
            </div>

            {!isAdmin && (
              <p style={{ color: 'var(--text-3)', fontSize: 13, marginTop: 16 }}>
                Only workspace admins can invite or remove members.
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

const s = {
  root: { display: 'flex', flexDirection: 'column', minHeight: '100vh', background: 'var(--bg)' },
  header: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', height: 56, background: 'var(--surface)', borderBottom: '1px solid var(--border)', gap: 16, flexShrink: 0 },
  headerLeft: { display: 'flex', alignItems: 'center', gap: 12 },
  dividerV: { width: 1, height: 20, background: 'var(--border)' },
  logo: { display: 'flex', alignItems: 'center', gap: 7 },
  logoText: { fontSize: 15, fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.02em' },
  tabs: { display: 'flex', gap: 2 },
  tab: { padding: '0 14px', height: 36, borderRadius: 'var(--radius)', background: 'none', border: 'none', color: 'var(--text-2)', cursor: 'pointer', fontWeight: 500, fontSize: 13, display: 'flex', alignItems: 'center', gap: 6, transition: 'background 0.15s, color 0.15s' },
  tabActive: { background: 'var(--surface-2)', color: 'var(--text)' },
  tabCount: { background: 'var(--surface-3)', color: 'var(--text-2)', borderRadius: 999, padding: '1px 6px', fontSize: 11, fontWeight: 600 },
  headerRight: { display: 'flex', alignItems: 'center', gap: 10 },
  headerUser: { fontSize: 13, fontWeight: 600, color: 'var(--text)', lineHeight: 1.2 },
  body: { flex: 1, padding: '28px 32px', overflowX: 'auto' },
  boardBar: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 20, gap: 12 },
  pageTitle: { fontSize: 18, fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.02em', marginBottom: 2 },
  pageSub: { color: 'var(--text-3)', fontSize: 13 },
  formCard: { background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: '20px 24px', marginBottom: 24, display: 'flex', flexDirection: 'column', gap: 16 },
  formTitle: { fontWeight: 600, color: 'var(--text)', fontSize: 14 },
  formRow: { display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-start' },
  board: { display: 'grid', gridTemplateColumns: 'repeat(4, minmax(220px, 1fr))', gap: 16, minWidth: 900 },
  column: { background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', display: 'flex', flexDirection: 'column', minHeight: 480 },
  colHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 16px', borderBottom: '1px solid var(--border)' },
  dot: { width: 8, height: 8, borderRadius: '50%', flexShrink: 0 },
  colTitle: { fontSize: 13, fontWeight: 600, color: 'var(--text)' },
  colCount: { background: 'var(--surface-3)', color: 'var(--text-2)', borderRadius: 999, padding: '1px 7px', fontSize: 11, fontWeight: 600 },
  colBody: { flex: 1, padding: '12px 10px', display: 'flex', flexDirection: 'column', gap: 8 },
  colEmpty: { color: 'var(--text-3)', fontSize: 12, textAlign: 'center', marginTop: 24 },
  taskCard: { background: 'var(--surface-2)', border: '1px solid var(--border)', borderRadius: 'var(--radius)', padding: '12px 12px 10px' },
  taskTop: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 6, marginBottom: 4 },
  taskTitle: { fontWeight: 600, color: 'var(--text)', fontSize: 13, lineHeight: 1.4 },
  deleteBtn: { background: 'none', border: 'none', color: 'var(--text-3)', cursor: 'pointer', padding: 2, borderRadius: 4, display: 'flex', alignItems: 'center', flexShrink: 0, transition: 'color 0.15s' },
  taskDesc: { color: 'var(--text-2)', fontSize: 12, marginBottom: 6, lineHeight: 1.4 },
  assigneeRow: { display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 },
  assigneeName: { fontSize: 12, color: 'var(--text-2)' },
  statusSelect: { width: '100%', height: 28, padding: '0 8px', borderRadius: 6, background: 'var(--surface-3)', color: 'var(--text-2)', border: '1px solid var(--border)', fontSize: 12, cursor: 'pointer', appearance: 'none', backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10' viewBox='0 0 24 24' fill='none' stroke='%238b92a5' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E\")", backgroundRepeat: 'no-repeat', backgroundPosition: 'right 8px center', paddingRight: 24 },
  membersWrap: { maxWidth: 640 },
  memberList: { display: 'flex', flexDirection: 'column', gap: 2 },
  memberRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', borderRadius: 'var(--radius)', transition: 'background 0.15s' },
  memberName: { fontSize: 14, fontWeight: 600, color: 'var(--text)', marginBottom: 4 },
  youTag: { color: 'var(--text-3)', fontWeight: 400, fontSize: 12 }
}
