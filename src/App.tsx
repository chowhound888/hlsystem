import { Navigate, Route, Routes } from 'react-router-dom'
import VisitRecordsPage from './pages/VisitRecordsPage'
import VisitRecordDetailPage from './pages/VisitRecordDetailPage'

export default function App() {
  return (
    <Routes>
      <Route path="/visit-records" element={<VisitRecordsPage />} />
      <Route path="/visit-records/:id" element={<VisitRecordDetailPage />} />
      <Route path="*" element={<Navigate to="/visit-records" replace />} />
    </Routes>
  )
}
