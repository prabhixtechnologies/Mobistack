# Workspaces

`shops` is the workspace row. Same UUID. Do not rename `shop_id` FKs.

Access is `User → WorkspaceMembership → Workspace`. `users.shop_id` is the last-selected workspace.

Join-by-code creates a PENDING membership. Owners approve from People. Invites carry a hashed token accepted at `POST /api/v1/invitations/accept`.
