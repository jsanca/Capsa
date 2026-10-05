import { NavLink, Outlet } from "react-router-dom";

export function AdminShell(): JSX.Element {
  return (
    <div className="admin-shell" data-testid="admin-shell">
      <header className="admin-shell__header">
        <div className="admin-shell__brand">Capsa Admin</div>
        <nav className="admin-shell__nav" aria-label="Admin navigation">
          <NavLink
            to="/admin-ui"
            end
            className={({ isActive }) =>
              "admin-shell__nav-link" + (isActive ? " admin-shell__nav-link--active" : "")
            }
          >
            Overview
          </NavLink>
          <NavLink
            to="/admin-ui/invitations/new"
            className={({ isActive }) =>
              "admin-shell__nav-link" + (isActive ? " admin-shell__nav-link--active" : "")
            }
          >
            Invitations
          </NavLink>
        </nav>
      </header>
      <main className="admin-shell__main" id="admin-content" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  );
}