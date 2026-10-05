import { Link } from "react-router-dom";

export function AdminHomePage(): JSX.Element {
  return (
    <section className="admin-page" data-testid="admin-home">
      <h1 className="admin-page__title">Administration</h1>
      <p className="admin-page__lede">
        Manage Capsa from this console. The available sections are listed below.
      </p>
      <ul className="admin-page__sections">
        <li className="admin-page__section">
          <Link to="/admin-ui/invitations/new" className="admin-page__section-link">
            Create invitation
          </Link>
          <p className="admin-page__section-hint">
            Issue a new invitation for a USER or ADMIN role.
          </p>
        </li>
      </ul>
    </section>
  );
}