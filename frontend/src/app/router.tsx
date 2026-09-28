import { lazy, Suspense, type ComponentType } from 'react'
import { createBrowserRouter } from 'react-router'
import { LoginPage } from '../features/auth/LoginPage'
import { RequireAuth, RequirePermission, RequireRole } from '../features/auth/RequireAuth'
import { AppLayout } from './AppLayout'
import { DashboardPage } from './DashboardPage'
import { NotFoundPage } from './NotFoundPage'
import { LoadingState } from '../shared/ui/States'

function lazyPage<K extends string>(load: () => Promise<Record<K, ComponentType>>, name: K) {
  return lazy(() => load().then((module) => ({ default: module[name] })))
}

// Route-level code splitting: each module is downloaded the first time it is opened (the dashboard,
// login and shell stay in the main bundle because every session starts there).
const AuditPage = lazyPage(() => import('../features/audit/AuditPage'), 'AuditPage')
const BranchesAdminPage = lazyPage(() => import('../features/branches/BranchesAdminPage'), 'BranchesAdminPage')
const CustomerDetailPage = lazyPage(() => import('../features/customers/CustomerDetailPage'), 'CustomerDetailPage')
const CustomersPage = lazyPage(() => import('../features/customers/CustomersPage'), 'CustomersPage')
const CatalogPage = lazyPage(() => import('../features/inventory/CatalogPage'), 'CatalogPage')
const InventoryLayout = lazyPage(() => import('../features/inventory/InventoryLayout'), 'InventoryLayout')
const MovementsPage = lazyPage(() => import('../features/inventory/MovementsPage'), 'MovementsPage')
const NotificationsPage = lazyPage(() => import('../features/notifications/NotificationsPage'), 'NotificationsPage')
const ProductDetailPage = lazyPage(() => import('../features/inventory/ProductDetailPage'), 'ProductDetailPage')
const ReceptionPage = lazyPage(() => import('../features/repairs/ReceptionPage'), 'ReceptionPage')
const RepairDetailPage = lazyPage(() => import('../features/repairs/RepairDetailPage'), 'RepairDetailPage')
const ReceiptPage = lazyPage(() => import('../features/repairs/ReceiptPage'), 'ReceiptPage')
const RepairsPage = lazyPage(() => import('../features/repairs/RepairsPage'), 'RepairsPage')
const StockOverviewPage = lazyPage(() => import('../features/inventory/StockOverviewPage'), 'StockOverviewPage')
const StockPage = lazyPage(() => import('../features/inventory/StockPage'), 'StockPage')
const TransferDetailPage = lazyPage(() => import('../features/transfers/TransferDetailPage'), 'TransferDetailPage')
const TransferPage = lazyPage(() => import('../features/transfers/TransferPage'), 'TransferPage')
const AgendaPage = lazyPage(() => import('../features/service/AgendaPage'), 'AgendaPage')
const MyVisitsPage = lazyPage(() => import('../features/service/MyVisitsPage'), 'MyVisitsPage')
const PublicRequestPage = lazyPage(() => import('../features/service/PublicRequestPage'), 'PublicRequestPage')
const LegacyPortalRedirect = lazyPage(() => import('../features/service/PublicRequestPage'), 'LegacyPortalRedirect')
const PortalSettingsPage = lazyPage(() => import('../features/service/PortalSettingsPage'), 'PortalSettingsPage')
const PublicStatusPage = lazyPage(() => import('../features/service/PublicStatusPage'), 'PublicStatusPage')
const SchedulesPage = lazyPage(() => import('../features/service/SchedulesPage'), 'SchedulesPage')
const ServiceRequestDetailPage = lazyPage(() => import('../features/service/ServiceRequestDetailPage'), 'ServiceRequestDetailPage')
const ServiceRequestsPage = lazyPage(() => import('../features/service/ServiceRequestsPage'), 'ServiceRequestsPage')
const StaffRequestPage = lazyPage(() => import('../features/service/StaffRequestPage'), 'StaffRequestPage')
const VisitDetailPage = lazyPage(() => import('../features/service/VisitDetailPage'), 'VisitDetailPage')
const UsersAdminPage = lazyPage(() => import('../features/users/UsersAdminPage'), 'UsersAdminPage')

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  // Public (no session): the home-service portal by its shareable slug and the status of one request
  // (FR-SRV-001/002, BR-SRV-009). The old address without a slug forwards to the current portal.
  { path: '/solicitar/:slug', element: <Suspense fallback={<LoadingState />}><PublicRequestPage /></Suspense> },
  { path: '/solicitar-servicio', element: <Suspense fallback={<LoadingState />}><LegacyPortalRedirect /></Suspense> },
  { path: '/solicitud/:ref', element: <Suspense fallback={<LoadingState />}><PublicStatusPage /></Suspense> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      {
        path: 'inventory',
        element: (
          <RequirePermission allowed="readInventory">
            <InventoryLayout />
          </RequirePermission>
        ),
        children: [
          { index: true, element: <StockPage /> },
          { path: 'overview', element: <StockOverviewPage /> },
          { path: 'movements', element: <MovementsPage /> },
          { path: 'catalog', element: <CatalogPage /> },
          { path: 'products/:productId', element: <ProductDetailPage /> },
        ],
      },
      {
        path: 'repairs',
        element: (
          <RequirePermission allowed="readRepairs">
            <RepairsPage />
          </RequirePermission>
        ),
      },
      {
        path: 'repairs/new',
        element: (
          <RequirePermission allowed="receiveRepairs">
            <ReceptionPage />
          </RequirePermission>
        ),
      },
      {
        // Printable reception receipt: counter roles only (the order GET is authorized as usual).
        path: 'repairs/:orderId/receipt',
        element: (
          <RequirePermission allowed="receiveRepairs">
            <ReceiptPage />
          </RequirePermission>
        ),
      },
      {
        path: 'repairs/:orderId',
        element: (
          <RequirePermission allowed="readRepairs">
            <RepairDetailPage />
          </RequirePermission>
        ),
      },
      {
        path: 'customers',
        element: (
          <RequirePermission allowed="manageCustomers">
            <CustomersPage />
          </RequirePermission>
        ),
      },
      {
        path: 'customers/:customerId',
        element: (
          <RequirePermission allowed="manageCustomers">
            <CustomerDetailPage />
          </RequirePermission>
        ),
      },
      {
        path: 'service-requests',
        element: (
          <RequirePermission allowed="handleServiceRequests">
            <ServiceRequestsPage />
          </RequirePermission>
        ),
      },
      {
        path: 'service-requests/new',
        element: (
          <RequirePermission allowed="handleServiceRequests">
            <StaffRequestPage />
          </RequirePermission>
        ),
      },
      {
        path: 'service-requests/portal',
        element: (
          <RequireRole role="ADMIN">
            <PortalSettingsPage />
          </RequireRole>
        ),
      },
      {
        path: 'service-requests/:requestId',
        element: (
          <RequirePermission allowed="handleServiceRequests">
            <ServiceRequestDetailPage />
          </RequirePermission>
        ),
      },
      {
        path: 'agenda',
        element: (
          <RequirePermission allowed="handleServiceRequests">
            <AgendaPage />
          </RequirePermission>
        ),
      },
      {
        path: 'agenda/horarios',
        element: (
          <RequirePermission allowed="manageSchedules">
            <SchedulesPage />
          </RequirePermission>
        ),
      },
      {
        path: 'my-visits',
        element: (
          <RequirePermission allowed="ownVisits">
            <MyVisitsPage />
          </RequirePermission>
        ),
      },
      {
        path: 'visits/:visitId',
        element: (
          <RequirePermission allowed="readVisits">
            <VisitDetailPage />
          </RequirePermission>
        ),
      },
      {
        path: 'transfers',
        element: (
          <RequirePermission allowed="transfer">
            <TransferPage />
          </RequirePermission>
        ),
      },
      {
        path: 'transfers/:transferId',
        element: (
          <RequirePermission allowed="transfer">
            <TransferDetailPage />
          </RequirePermission>
        ),
      },
      {
        path: 'notifications',
        element: (
          <RequirePermission allowed="readNotifications">
            <NotificationsPage />
          </RequirePermission>
        ),
      },
      {
        path: 'audit',
        element: (
          <RequirePermission allowed="readAudit">
            <AuditPage />
          </RequirePermission>
        ),
      },
      {
        path: 'admin/branches',
        element: (
          <RequireRole role="ADMIN">
            <BranchesAdminPage />
          </RequireRole>
        ),
      },
      {
        path: 'admin/users',
        element: (
          <RequireRole role="ADMIN">
            <UsersAdminPage />
          </RequireRole>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
