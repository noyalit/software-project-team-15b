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
import CompanyPage from '../screens/CompanyPage';
import AdminEventQueuesPage from '../screens/AdminEventQueuesPage';
import MyEventsPage from '../screens/MyEventsPage';
import OrdersPage from '../screens/OrdersPage';
import OrderDetailsPage from '../screens/OrderDetailsPage';
import CheckoutPage from '../screens/CheckoutPage';
import WaitQueuePage from '../screens/WaitQueuePage';
import SiteQueuePage from '../screens/SiteQueuePage';
import CompanyOrdersPage from '../screens/CompanyOrdersPage';
import CompanySalesReportPage from '../screens/CompanySalesReportPage';
import HierarchyReportPage from '../screens/HierarchyReportPage';
import NotificationsPage from '../screens/NotificationsPage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/events" element={<EventSearchPage />} />
        <Route path="/events/:eventId" element={<EventDetailsPage />} />
        <Route path="/orders" element={<OrdersPage />} />
        <Route path="/orders/:orderId" element={<OrderDetailsPage />} />
        <Route path="/checkout/:orderId" element={<CheckoutPage />} />
        <Route path="/queue/:eventId" element={<WaitQueuePage />} />
        <Route path="/site-queue" element={<SiteQueuePage />} />

        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/me" element={<ProfilePage />} />
        <Route path="/notifications" element={<NotificationsPage />} />

        <Route path="/companies/me" element={<MyCompaniesPage />} />
        <Route path="/companies/new" element={<CreateCompanyPage />} />
        <Route path="/companies/:companyId" element={<CompanyPage />} />
        <Route path="/my-events" element={<MyEventsPage />} />
        <Route path="/company-orders" element={<CompanyOrdersPage />} />
        <Route path="/company-sales" element={<CompanySalesReportPage />} />
        <Route path="/hierarchy-report" element={<HierarchyReportPage />} />
        <Route path="/admin/queues" element={<AdminQueuesPage />} />
        <Route path="/admin/companies" element={<AdminCompaniesPage />} />
        <Route path="/admin/members" element={<AdminMembersPage />} />
        <Route path="/admin/orders" element={<AdminOrdersPage />} />
        <Route path="/admin/event-queues" element={<AdminEventQueuesPage />} />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
