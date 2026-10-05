import { Navigate, Route, Routes } from "react-router-dom";
import { AdminGateOutlet } from "./components/AdminGateOutlet";
import { AdminHomePage } from "./pages/AdminHomePage";
import { InvitationNewPage } from "./pages/InvitationNewPage";

export function AppRoutes(): JSX.Element {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/admin-ui" replace />} />
      <Route path="/admin-ui" element={<AdminGateOutlet />}>
        <Route index element={<AdminHomePage />} />
        <Route path="invitations/new" element={<InvitationNewPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/admin-ui" replace />} />
    </Routes>
  );
}