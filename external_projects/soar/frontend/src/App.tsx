import { Navigate, Route, Routes } from 'react-router-dom'

import { useAuth } from './auth'
import Dashboard from './pages/Dashboard'
import Login from './pages/Login'

export default function App() {
  const { user, loading } = useAuth()

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-[#787774]">
        Carregando…
      </div>
    )
  }

  return (
    <Routes>
      <Route path="/login" element={user ? <Navigate to="/" replace /> : <Login />} />
      <Route path="/" element={user ? <Dashboard /> : <Navigate to="/login" replace />} />
      <Route path="/p/:pageId" element={user ? <Dashboard /> : <Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
