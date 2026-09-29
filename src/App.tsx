import { Navigate, Route, Routes } from 'react-router-dom'

export default function App() {
  return (
    <Routes>
      <Route path="*" element={<Navigate to="/visit-records" replace />} />
    </Routes>
  )
}
