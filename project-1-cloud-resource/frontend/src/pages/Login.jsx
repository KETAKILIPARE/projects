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
      localStorage.setItem('token', res.data.token)
      onLogin()
    } catch {
      setError('Invalid username or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={s.page}>
      <div style={s.card}>
        <div style={s.logo}>
          <div style={s.logoIcon}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
              <path d="M3 15a4 4 0 004 4h9a5 5 0 10-.1-9.999 5.002 5.002 0 10-9.78 2.096A4.001 4.001 0 003 15z"/>
            </svg>
          </div>
          <span style={s.logoText}>CloudOps</span>
        </div>
        <h1 style={s.heading}>Sign in to your account</h1>
        <p style={s.sub}>Manage your cloud infrastructure</p>

        <form onSubmit={submit} style={s.form}>
          <div style={s.field}>
            <label style={s.label}>Username</label>
            <input style={s.input} placeholder="Enter your username" value={form.username}
              onChange={e => setForm({ ...form, username: e.target.value })} required autoFocus />
          </div>
          <div style={s.field}>
            <label style={s.label}>Password</label>
            <input style={s.input} type="password" placeholder="Enter your password" value={form.password}
              onChange={e => setForm({ ...form, password: e.target.value })} required />
          </div>
          {error && (
            <div style={s.errorBox}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#dc2626" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              {error}
            </div>
          )}
          <button style={{ ...s.btn, opacity: loading ? 0.7 : 1 }} type="submit" disabled={loading}>
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <p style={s.footer}>
          Don't have an account?{' '}
          <button style={s.link} onClick={onRegister}>Create one</button>
        </p>
      </div>
    </div>
  )
}

const s = {
  page: { minHeight: '100vh', background: '#f1f5f9', display: 'flex', alignItems: 'center', justifyContent: 'center', fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif' },
  card: { background: '#fff', borderRadius: '12px', padding: '2.5rem', width: '100%', maxWidth: '400px', boxShadow: '0 1px 3px rgba(0,0,0,0.08), 0 4px 24px rgba(0,0,0,0.06)' },
  logo: { display: 'flex', alignItems: 'center', gap: '0.6rem', marginBottom: '1.75rem' },
  logoIcon: { width: '36px', height: '36px', background: 'linear-gradient(135deg, #1d4ed8, #3b82f6)', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  logoText: { fontWeight: '700', fontSize: '1.1rem', color: '#0f172a', letterSpacing: '-0.02em' },
  heading: { fontSize: '1.4rem', fontWeight: '700', color: '#0f172a', margin: '0 0 0.35rem', letterSpacing: '-0.02em' },
  sub: { color: '#64748b', fontSize: '0.9rem', margin: '0 0 1.75rem' },
  form: { display: 'flex', flexDirection: 'column', gap: '1.1rem' },
  field: { display: 'flex', flexDirection: 'column', gap: '0.4rem' },
  label: { fontSize: '0.85rem', fontWeight: '500', color: '#374151' },
  input: { padding: '0.65rem 0.85rem', borderRadius: '8px', border: '1.5px solid #e2e8f0', fontSize: '0.95rem', color: '#0f172a', outline: 'none', transition: 'border-color 0.15s', background: '#fff' },
  errorBox: { display: 'flex', alignItems: 'center', gap: '0.5rem', background: '#fef2f2', border: '1px solid #fecaca', borderRadius: '8px', padding: '0.65rem 0.85rem', color: '#dc2626', fontSize: '0.875rem' },
  btn: { padding: '0.7rem', borderRadius: '8px', background: 'linear-gradient(135deg, #1d4ed8, #3b82f6)', color: '#fff', border: 'none', fontSize: '0.95rem', fontWeight: '600', cursor: 'pointer', marginTop: '0.25rem', letterSpacing: '0.01em' },
  footer: { textAlign: 'center', marginTop: '1.5rem', color: '#64748b', fontSize: '0.875rem' },
  link: { background: 'none', border: 'none', color: '#1d4ed8', cursor: 'pointer', fontWeight: '500', fontSize: '0.875rem', padding: 0 }
}
