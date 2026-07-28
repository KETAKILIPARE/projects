import { useState } from 'react'
import api from '../api'

export default function Login({ onLogin, onRegister }) {
  const [form, setForm] = useState({ username: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async e => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const res = await api.post('/auth/login', form)
      localStorage.setItem('wf_token', res.data.token)
      onLogin()
    } catch {
      setError('Invalid username or password')
    } finally {
      setLoading(false)
    }
  }

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
          <h1 style={s.h1}>Welcome back</h1>
          <p style={s.sub}>Sign in to your workspace</p>
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
            <input className="input" placeholder="Enter your username" value={form.username}
              onChange={e => setForm({ ...form, username: e.target.value })} required autoFocus />
          </div>
          <div className="field">
            <label>Password</label>
            <input className="input" type="password" placeholder="Enter your password" value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })} required />
          </div>
          <button className="btn btn-primary" type="submit" disabled={loading}
            style={{ width: '100%', justifyContent: 'center', height: 40, marginTop: 4 }}>
            {loading ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <div style={s.footer}>
          <span style={{ color: 'var(--text-3)' }}>Don't have an account?</span>
          <button style={s.link} onClick={onRegister}>Create account</button>
        </div>
      </div>
    </div>
  )
}

const s = {
  root: { minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg)', padding: 24 },
  panel: { width: '100%', maxWidth: 380, display: 'flex', flexDirection: 'column', gap: 24 },
  logo: { display: 'flex', alignItems: 'center', gap: 10 },
  logoText: { fontSize: 18, fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.02em' },
  heading: { display: 'flex', flexDirection: 'column', gap: 4 },
  h1: { fontSize: 22, fontWeight: 700, color: 'var(--text)', letterSpacing: '-0.03em' },
  sub: { color: 'var(--text-2)', fontSize: 14 },
  form: { display: 'flex', flexDirection: 'column', gap: 16, background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)', padding: 24 },
  footer: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, fontSize: 13 },
  link: { background: 'none', border: 'none', color: 'var(--accent)', cursor: 'pointer', fontWeight: 500, fontSize: 13, padding: 0 }
}
