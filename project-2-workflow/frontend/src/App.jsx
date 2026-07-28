import { useState } from 'react'
import './App.css'
import Login from './pages/Login'
import Register from './pages/Register'
import Workspaces from './pages/Workspaces'
import Board from './pages/Board'

export default function App() {
  const [screen, setScreen] = useState(localStorage.getItem('wf_token') ? 'workspaces' : 'login')
  const [workspaceId, setWorkspaceId] = useState(null)

  const logout = () => { localStorage.removeItem('wf_token'); setScreen('login'); setWorkspaceId(null) }

  if (screen === 'workspaces' && workspaceId) return <Board workspaceId={workspaceId} onBack={() => setWorkspaceId(null)} />
  if (screen === 'workspaces') return <Workspaces onSelect={setWorkspaceId} onLogout={logout} />
  if (screen === 'register') return <Register onSwitch={() => setScreen('login')} />
  return <Login onLogin={() => setScreen('workspaces')} onRegister={() => setScreen('register')} />
}
