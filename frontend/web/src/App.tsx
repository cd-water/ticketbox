import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { EventDetailPage } from '@/pages/event-detail-page'
import { EventListPage } from '@/pages/event-list-page'
import { LoginPage } from '@/pages/login-page'
import { OrderDetailPage } from '@/pages/order-detail-page'
import { OrderListPage } from '@/pages/order-list-page'
import { RegisterPage } from '@/pages/register-page'
import { SeatMapPage } from '@/pages/seat-map-page'
import { RequireAuth } from '@/components/require-auth'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<EventListPage />} />
        <Route path="/events/:id" element={<EventDetailPage />} />
        <Route path="/performances/:id/seats" element={<SeatMapPage />} />
        <Route
          path="/orders"
          element={
            <RequireAuth>
              <OrderListPage />
            </RequireAuth>
          }
        />
        <Route
          path="/orders/:id"
          element={
            <RequireAuth>
              <OrderDetailPage />
            </RequireAuth>
          }
        />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
