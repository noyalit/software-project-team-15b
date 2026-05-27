import { Navigate, Route, Routes } from 'react-router-dom';
import AppShell from '../ui/AppShell';
import HomePage from '../screens/HomePage';
import EventSearchPage from '../screens/EventSearchPage';
import EventDetailsPage from '../screens/EventDetailsPage';
import MyCompaniesPage from '../screens/MyCompaniesPage';
import LoginPage from '../screens/LoginPage';
import RegisterPage from '../screens/RegisterPage';
import ProfilePage from '../screens/ProfilePage';
import AdminQueuesPage from '../screens/AdminQueuesPage';
import AdminCompaniesPage from '../screens/AdminCompaniesPage';
import AdminMembersPage from '../screens/AdminMembersPage';
import AdminOrdersPage from '../screens/AdminOrdersPage';
import CreateCompanyPage from '../screens/CreateCompanyPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/events" element={<EventSearchPage />} />
        <Route path="/events/:eventId" element={<EventDetailsPage />} />

        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/me" element={<ProfilePage />} />

        <Route path="/companies/me" element={<MyCompaniesPage />} />
        <Route path="/companies/new" element={<CreateCompanyPage />} />
        <Route path="/admin/queues" element={<AdminQueuesPage />} />
        <Route path="/admin/companies" element={<AdminCompaniesPage />} />
        <Route path="/admin/members" element={<AdminMembersPage />} />
        <Route path="/admin/orders" element={<AdminOrdersPage />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
