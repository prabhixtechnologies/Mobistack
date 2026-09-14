export type NavIconName =
  | "home"
  | "cart"
  | "wrench"
  | "box"
  | "truck"
  | "users"
  | "store"
  | "link"
  | "chart"
  | "move"
  | "people"
  | "grid"
  | "card"
  | "upload"
  | "bell"
  | "chat"
  | "shield"
  | "settings"
  | "crown"
  | "search"
  | "sun"
  | "moon"
  | "logout"
  | "lock"
  | "key"
  | "user"
  | "chevronRight"
  | "chevronDown"
  | "panelLeft"
  | "plus"
  | "close"
  | "check"
  | "alert"
  | "info"
  | "refresh"
  | "trash"
  | "edit"
  | "pulse"
  | "server"
  | "globe";

export function NavIcon({ name }: { name: NavIconName }) {
  switch (name) {
    case "home":
      return <path d="M4 11.5 12 4l8 7.5V20a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1Z" />;
    case "cart":
      return <path d="M3 5h2l.4 2M7 13h10l3-8H5.4M7 13l-1.6 6h13M9 21a1 1 0 1 0 0-2 1 1 0 0 0 0 2Zm8 0a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z" />;
    case "wrench":
      return <path d="M14.7 6.3a4 4 0 0 0-5.6 5.6L4 17l3 3 5.1-5.1a4 4 0 0 0 5.6-5.6l-2.8 1.4Z" />;
    case "box":
      return <path d="M3 7.5 12 3l9 4.5v9L12 21 3 16.5Zm0 0 9 4.5 9-4.5M12 12v9" />;
    case "truck":
      return <path d="M3 7h11v10H3Zm11 3h4l3 3v4h-7ZM6 20a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Zm11 0a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z" />;
    case "users":
      return <path d="M16 19v-1a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v1m16 0v-1a4 4 0 0 0-3-3.87M12 11a4 4 0 1 0-4-4 4 4 0 0 0 4 4Zm6-1a3 3 0 1 0-2-5" />;
    case "store":
      return <path d="M4 7h16l-1 5H5Zm1 5h14v7H5Zm3 2v3h8v-3" />;
    case "link":
      return <path d="M10 13a4 4 0 0 1 0-6l2-2a4 4 0 0 1 6 6l-1 1m-3-1a4 4 0 0 1 0 6l-2 2a4 4 0 0 1-6-6l1-1" />;
    case "chart":
      return <path d="M4 19V5m0 14h16M8 15v-4m4 4V8m4 7v-2" />;
    case "move":
      return <path d="M7 7h10v10H7Zm-3 5h3m10 0h3M12 4v3m0 10v3" />;
    case "people":
      return <path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4Zm-8 8v-1a5 5 0 0 1 8-4 5 5 0 0 1 8 4v1" />;
    case "grid":
      return <path d="M4 4h7v7H4Zm9 0h7v7h-7ZM4 13h7v7H4Zm9 0h7v7h-7Z" />;
    case "card":
      return <path d="M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Zm0 2h18" />;
    case "upload":
      return <path d="M12 16V5m0 0 4 4m-4-4L8 9M5 19h14" />;
    case "bell":
      return <path d="M6 9a6 6 0 1 1 12 0c0 7 2 7 2 7H4s2 0 2-7Zm5 11a2 2 0 0 0 2 0" />;
    case "chat":
      return <path d="M5 18 3 21V7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2Z" />;
    case "shield":
      return <path d="M12 3 5 6v6c0 4.5 3 8.5 7 9.5 4-1 7-5 7-9.5V6Z" />;
    case "settings":
      return <path d="M12 15a3 3 0 1 0-3-3 3 3 0 0 0 3 3Zm8-3a1.5 1.5 0 0 0 .1-.6l2-1.5-2-3.5-2.3.5a7 7 0 0 0-1-1L17 3h-4l-.8 2.4a7 7 0 0 0-1 1L9 5.9 7 9.4l2 1.5a6 6 0 0 0 0 1.2l-2 1.5 2 3.5 2.3-.5a7 7 0 0 0 1 1L13 21h4l.8-2.4a7 7 0 0 0 1-1l2.3.5 2-3.5-2-1.5a1.5 1.5 0 0 0 .1-.6Z" />;
    case "crown":
      return <path d="M4 16 6 8l4 4 2-6 2 6 4-4 2 8H4Z" />;
    case "search":
      return <path d="m20 20-4.3-4.3M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" />;
    case "sun":
      return <path d="M12 4V2m0 20v-2M4 12H2m20 0h-2m-2.05-5.95L16.6 7.4M7.4 16.6l-1.35 1.35M6.05 6.05 7.4 7.4m9.2 9.2 1.35 1.35M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8Z" />;
    case "moon":
      return <path d="M20 14.5A8.5 8.5 0 1 1 9.5 4 7 7 0 0 0 20 14.5Z" />;
    case "logout":
      return <path d="M10 17H6a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h4m4 10 5-5-5-5m5 5H10" />;
    case "lock":
      return <path d="M6 11h12v9a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1Zm2 0V8a4 4 0 0 1 8 0v3m-4 4v3" />;
    case "key":
      return <path d="M15 7a3 3 0 1 1-2.4 4.8L11 13H9v2H7v2H4v-3l5.2-5.2A3 3 0 0 1 15 7Z" />;
    case "user":
      return <path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4Zm-7 9v-1a5 5 0 0 1 5-5h4a5 5 0 0 1 5 5v1" />;
    case "chevronRight":
      return <path d="m9 6 6 6-6 6" />;
    case "chevronDown":
      return <path d="m6 9 6 6 6-6" />;
    case "panelLeft":
      return <path d="M4 5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1Zm6-1v16" />;
    case "plus":
      return <path d="M12 5v14M5 12h14" />;
    case "close":
      return <path d="M6 6l12 12M18 6 6 18" />;
    case "check":
      return <path d="m5 13 4 4L19 7" />;
    case "alert":
      return <path d="M12 9v4m0 3h.01M10.3 4.3 2.6 17.6A1.5 1.5 0 0 0 3.9 20h16.2a1.5 1.5 0 0 0 1.3-2.4L13.7 4.3a1.5 1.5 0 0 0-2.6 0Z" />;
    case "info":
      return <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-13h.01M11 12h1v5h1" />;
    case "refresh":
      return <path d="M20 12a8 8 0 1 1-2.3-5.7M20 4v4h-4" />;
    case "trash":
      return <path d="M4 7h16M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2m-9 0 1 13a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1l1-13" />;
    case "edit":
      return <path d="M4 20h4L20 8l-4-4L4 16Zm10-14 4 4" />;
    case "pulse":
      return <path d="M3 12h4l2-6 3 12 2.5-6H21" />;
    case "server":
      return <path d="M4 5h16v5H4Zm0 9h16v5H4Zm3-6.5h.01M7 16.5h.01" />;
    case "globe":
      return (
        <>
          <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z" />
          <path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18" />
        </>
      );
  }
}

export function Icon({ name, className }: { name: NavIconName; className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <NavIcon name={name} />
    </svg>
  );
}
