import { Navigate, Route, Routes } from 'react-router-dom'
import VisitRecordsPage from './pages/VisitRecordsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/visit-records" element={<VisitRecordsPage />} />
      <Route path="*" element={<Navigate to="/visit-records" replace />} />
    </Routes>
  )
}
