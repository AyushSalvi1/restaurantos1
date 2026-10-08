import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'

import { AppShell } from '@/components/layout/app-shell'
import { ErrorPage, RedirectIfAuthenticated, RequireAdmin, RequireAuth } from '@/components/route-guards'
import { LoadingPanel } from '@/components/ui/skeleton'
import { LoginPage } from '@/pages/auth/login'
import { RegisterPage } from '@/pages/auth/register'
import { ForgotPasswordPage } from '@/pages/auth/forgot-password'
import { ResetPasswordPage } from '@/pages/auth/reset-password'
import { OnboardingPage } from '@/pages/onboarding'

/**
 * Feature routes are code-split: the dashboard is the common entry point and the rest of the product
 * is large enough that shipping it all in the first chunk measurably delays time to interactive.
 */
const DashboardPage = lazy(() => import('@/pages/dashboard'))
const TodayPage = lazy(() => import('@/pages/today'))
const TasksPage = lazy(() => import('@/pages/tasks'))
const TaskDetailPage = lazy(() => import('@/pages/task-detail'))
const GoalsPage = lazy(() => import('@/pages/goals'))
const HabitsPage = lazy(() => import('@/pages/habits'))
const CalendarPage = lazy(() => import('@/pages/calendar'))
const FocusPage = lazy(() => import('@/pages/focus'))
const LearningPage = lazy(() => import('@/pages/learning'))
const FinancePage = lazy(() => import('@/pages/finance'))
const JournalPage = lazy(() => import('@/pages/journal'))
const KnowledgePage = lazy(() => import('@/pages/knowledge'))
const AssistantPage = lazy(() => import('@/pages/assistant'))
const AnalyticsPage = lazy(() => import('@/pages/analytics'))
const SearchPage = lazy(() => import('@/pages/search'))
const NotificationsPage = lazy(() => import('@/pages/notifications'))
const SettingsPage = lazy(() => import('@/pages/settings'))
const LifeGraphPage = lazy(() => import('@/pages/life-graph'))
const AdminPage = lazy(() => import('@/pages/admin'))

function Lazy({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<LoadingPanel />}>{children}</Suspense>
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/app" replace />} />

      <Route
        path="/login"
        element={
          <RedirectIfAuthenticated>
            <LoginPage />
          </RedirectIfAuthenticated>
        }
      />
      <Route
        path="/register"
        element={
          <RedirectIfAuthenticated>
            <RegisterPage />
          </RedirectIfAuthenticated>
        }
      />
      <Route
        path="/forgot-password"
        element={
          <RedirectIfAuthenticated>
            <ForgotPasswordPage />
          </RedirectIfAuthenticated>
        }
      />
      <Route
        path="/reset-password"
        element={
          <RedirectIfAuthenticated>
            <ResetPasswordPage />
          </RedirectIfAuthenticated>
        }
      />

      <Route path="/onboarding" element={<OnboardingPage />} />

      <Route element={<RequireAuth />}>
        <Route path="/app" element={<AppShell />}>
          <Route index element={<Lazy><DashboardPage /></Lazy>} />
          <Route path="today" element={<Lazy><TodayPage /></Lazy>} />
          <Route path="tasks" element={<Lazy><TasksPage /></Lazy>} />
          <Route path="tasks/:taskId" element={<Lazy><TaskDetailPage /></Lazy>} />
          <Route path="goals" element={<Lazy><GoalsPage /></Lazy>} />
          <Route path="habits" element={<Lazy><HabitsPage /></Lazy>} />
          <Route path="calendar" element={<Lazy><CalendarPage /></Lazy>} />
          <Route path="focus" element={<Lazy><FocusPage /></Lazy>} />
          <Route path="learning" element={<Lazy><LearningPage /></Lazy>} />
          <Route path="finance" element={<Lazy><FinancePage /></Lazy>} />
          <Route path="journal" element={<Lazy><JournalPage /></Lazy>} />
          <Route path="knowledge" element={<Lazy><KnowledgePage /></Lazy>} />
          <Route path="assistant" element={<Lazy><AssistantPage /></Lazy>} />
          <Route path="analytics" element={<Lazy><AnalyticsPage /></Lazy>} />
          <Route path="search" element={<Lazy><SearchPage /></Lazy>} />
          <Route path="notifications" element={<Lazy><NotificationsPage /></Lazy>} />
          <Route path="settings" element={<Lazy><SettingsPage /></Lazy>} />
          <Route path="health" element={<Lazy><LifeGraphPage /></Lazy>} />
          <Route element={<RequireAdmin />}>
            <Route path="admin" element={<Lazy><AdminPage /></Lazy>} />
          </Route>
        </Route>
      </Route>

      <Route
        path="*"
        element={
          <ErrorPage
            title="Page not found"
            message="The address you followed does not match any page in LIFEOS."
          />
        }
      />
    </Routes>
  )
}
