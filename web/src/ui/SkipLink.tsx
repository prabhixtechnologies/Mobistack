export function SkipLink({ href = "#main-content", label = "Skip to main content" }: { href?: string; label?: string }) {
  return (
    <a className="skip-link" href={href}>
      {label}
    </a>
  );
}
