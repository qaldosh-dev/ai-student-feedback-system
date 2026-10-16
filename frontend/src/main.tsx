import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router';
import BrowsePage from './pages/BrowsePage';
import RoleAreaPage from './pages/RoleAreaPage';
import RegisterPage from './pages/RegisterPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import AccountSetupPage from './pages/AccountSetupPage';
import ScheduleDetailsPage from './pages/ScheduleDetailsPage';
import ScheduleUploadsPage from './pages/ScheduleUploadsPage';
import VerifiedCoursesPage from './pages/VerifiedCoursesPage';
import ReviewTargetPage from './pages/ReviewTargetPage';
import ReviewStatusPage from './pages/ReviewStatusPage';
import MyReviewsPage from './pages/MyReviewsPage';
import TeacherFeedbackPage from './pages/TeacherFeedbackPage';
import TeacherAnalyticsPage from './pages/TeacherAnalyticsPage';
import TeacherProfilePage from './pages/TeacherProfilePage';
import CourseDetailsPage from './pages/CourseDetailsPage';
import PublicFeedbackPage from './pages/PublicFeedbackPage';
import PreviousRatingsPage from './pages/PreviousRatingsPage';
import ProtectedRoute from './components/ProtectedRoute';
import SiteLayout from './components/SiteLayout';
import { AuthProvider } from './auth/AuthContext';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<SiteLayout />}>
            <Route path="/" element={<BrowsePage />} />
            <Route path="/teachers/:teacherId" element={<TeacherProfilePage />} />
            <Route path="/courses/:courseId" element={<CourseDetailsPage />} />
            <Route path="/teachers/:teacherId/courses/:courseId/feedback" element={<PublicFeedbackPage />} />
            <Route path="/teachers/:teacherId/courses/:courseId/ratings/history" element={<PreviousRatingsPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/verify-email" element={<VerifyEmailPage />} />
            <Route path="/account-setup" element={<ProtectedRoute><AccountSetupPage /></ProtectedRoute>} />
            <Route path="/student" element={<ProtectedRoute requiredRole="STUDENT"><RoleAreaPage key="student" role="STUDENT" /></ProtectedRoute>} />
            <Route path="/student/schedules/:uploadId" element={<ProtectedRoute requiredRole="STUDENT"><ScheduleDetailsPage /></ProtectedRoute>} />
            <Route path="/student/schedules" element={<ProtectedRoute requiredRole="STUDENT"><ScheduleUploadsPage /></ProtectedRoute>} />
            <Route path="/student/verified-courses" element={<ProtectedRoute requiredRole="STUDENT"><VerifiedCoursesPage /></ProtectedRoute>} />
            <Route path="/student/reviews/new" element={<ProtectedRoute requiredRole="STUDENT"><ReviewTargetPage /></ProtectedRoute>} />
            <Route path="/student/reviews" element={<ProtectedRoute requiredRole="STUDENT"><MyReviewsPage /></ProtectedRoute>} />
            <Route path="/student/reviews/:reviewId/status" element={<ProtectedRoute requiredRole="STUDENT"><ReviewStatusPage /></ProtectedRoute>} />
            <Route path="/teacher" element={<ProtectedRoute requiredRole="TEACHER"><RoleAreaPage key="teacher" role="TEACHER" /></ProtectedRoute>} />
            <Route path="/teacher/feedback" element={<ProtectedRoute requiredRole="TEACHER"><TeacherFeedbackPage /></ProtectedRoute>} />
            <Route path="/teacher/analytics" element={<ProtectedRoute requiredRole="TEACHER"><TeacherAnalyticsPage /></ProtectedRoute>} />
            <Route path="/admin" element={<ProtectedRoute requiredRole="ADMIN"><RoleAreaPage key="admin" role="ADMIN" /></ProtectedRoute>} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
