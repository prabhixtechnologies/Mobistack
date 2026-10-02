import { NavLink } from "react-router-dom";
import { Icon } from "./navIcons";

/** Route-backed team navigation: links survive reload, back, and sharing. */
export function TeamNav() {
  return (
    <nav className="tabs team-nav" aria-label="Team sections">
      <NavLink to="/members" className={({ isActive }) => `tab${isActive ? " tab--active" : ""}`}>
        <Icon name="people" />
        Members & invites
      </NavLink>
      <NavLink to="/users" className={({ isActive }) => `tab${isActive ? " tab--active" : ""}`}>
        <Icon name="key" />
        Roles & access
      </NavLink>
    </nav>
  );
}
