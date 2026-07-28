import { useState } from 'react'
import api from '../api'

export default function Register({ onSwitch }) {
  const [form, setForm] = useState({ username: '', password: '', systemRole: 'SYSTEM_MEMBER' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  const submit = async e => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      await api.post('/auth/register', form)
      setSuccess(true)
    } catch (err) {
      setError(err.response?.status === 409 ? 'Username already taken' : 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  if (success) return (
    <div style={s.root}>
      <div style={s.panel}>
        <div style={s.logo}>
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="7" fill="#4f7ef8"/>
            <rect x="7" y="8" width="14" height="2.5" rx="1.25" fill="white"/>
            <rect x="7" y="12.75" width="10" height="2.5" rx="1.25" fill="white" opacity="0.7"/>
            <rect x="7" y="17.5" width="7" height="2.5" rx="1.25" fill="white" opacity="0.4"/>
          </svg>
          <span style={s.logoText}>FlowDesk</span>
        </div>
        <div style={{ ...s.form, alignItems: 'center', gap: 16, textAlign: 'center' }}>
          <div style={{ width: 48, height: 48, borderRadius: '50%', background: 'rgba(34,197,94,0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#22c55e" strokeWidth="2.5"><polyline points="20 6 9 17 4 12"/></svg>
          </div>
          <div>
            <p style={{ fontWeight: 600, color: 'var(--text)', marginBottom: 4 }}>Account created</p>
            <p style={{ color: 'var(--text-2)', fontSize: 13 }}>You can now sign in to FlowDesk</p>
          </div>
          <button className="btn btn-primary" onClick={onSwitch} style={{ width: '100%', justifyContent: 'center', height: 40 }}>
            Go to sign in
          </button>
        </div>
      </div>
    </div>
  )

  return (
    <div style={s.root}>
      <div style={s.panel}>
        <div style={s.logo}>
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none">
            <rect width="28" height="28" rx="7" fill="#4f7ef8"/>
            <rect x="7" y="8" width="14" height="2.5" rx="1.25" fill="white"/>
            <rect x="7" y="12.75" width="10" height="2.5" rx="1.25" fill="white" opacity="0.7"/>
            <rect x="7" y="17.5" width="7" height="2.5" rx="1.25" fill="white" opacity="0.4"/>
          </svg>
          <span style={s.logoText}>FlowDesk</span>
        </div>

        <div style={s.heading}>
          <h1 style={s.h1}>Create an account</h1>
          <p style={s.sub}>Get started with FlowDesk</p>
        </div>

        {error && (
          <div className="alert-error" onClick={() => setError('')}>
            {error}
            <span style={{ fontSize: 16, lineHeight: 1 }}>×</span>
          </div>
        )}

        <form onSubmit={submit} style={s.form}>
          <div className="field">
            <label>Username</label>
            <input className="input" placeholder="Choose a username" value={form.username}
              onChange={e => setForm({ ...form, username: e.target.value })} required autoFocus />
          </div>
          <div className="field">
            <label>Password</label>
            <input className="input" type="password" placeholder="Choose a password" value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })} required />
          </div>

          <div className="field">
            <label>Account type</label>
            <div style={s.roleGrid}>
              {[
                { value: 'SYSTEM_ADMIN', title: 'Admin', desc: 'Create and manage workspaces' },
                { value: 'SYSTEM_MEMBER', title: 'Member', desc: 'Join workspaces by invitation' }
              ].map(r => (
                <label key={r.value} style={{ ...s.roleCard, ...(form.systemRole === r.value ? s.roleCardActive : {}) }}>
                  <input type="radio" value={r.value} checked={form.systemRole === r.value}
                    onChange={e => setForm({ ...form, systemRole: e.target.value })}
                    style={{ display: 'none' }} />
                  <span style={s.roleTitle}>{r.title}</span>
                  <span style={s.roleDesc}>{r.desc}</span>
                </label>
              ))}
            </div>
          </div>

          <button className="btn btn-primary" type="submit" disabled={loading}
            style={{ width: '100%', justifyContent: 'center', height: 40, marginTop: 4 }}>
            {loading ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <div style={s.footer}>
          <span style={{ color: 'var(--text-3)' }}>Already have an account?</span>
          <button style={s.link} onClick={onSwitch}>Sign in</button>
        </div>
      </div>
    </div>
  )
}

const s = {
  root: { minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg)', padding: 24 },
  panel: { width: '100%', maxWidth: 400, display: 'flex', flexDirection: 'column', gap: 24 },
  logo: { display: 'flex', alignItems: 'center', gap: 10 },
  logoText: { fontSize: 18, fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.02em' },
  heading: { display: 'flex', flexDirection: 'column', gap: 4 },
  h1: { fontSize: 22, fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.03em' },
  sub: { color: 'var(--text-2)', fontSize: 14 },
  form: { display: 'flex', flexDirection: 'column', gap: 16, background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 24 },
  roleGrid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 },
  roleCard: { display: 'flex', flexDirection: 'column', gap: 3, padding: '10px 12px', borderRadius: 'var(--radius)', border: '1px solid var(--border)', cursor: 'pointer', transition: 'border-color 0.15s, background 0.15s' },
  roleCardActive: { borderColor: 'var(--accent)', background: 'var(--accent-bg)' },
  roleTitle: { fontWeight: 600, color: 'var(--text)', fontSize: 13 },
  roleDesc: { color: 'var(--text-3)', fontSize: 12 },
  footer: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, fontSize: 13 },
  link: { background: 'none', border: 'none', color: 'var(--accent)', cursor: 'pointer', fontWeight: 500, fontSize: 13, padding: 0 }
}
