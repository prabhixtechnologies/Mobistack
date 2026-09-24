-- One tenant word. shop_id and workspace_id were the same key: both point at shops.id.
-- Thirty-two tables already said shop_id. These six said workspace_id.
--
-- The JSON field stays workspaceId. Shipped clients, including the mobile model that reads
-- either name, keep working. Only the database column and the four table names change.

ALTER TABLE public.billing_orders RENAME COLUMN workspace_id TO shop_id;
ALTER TABLE public.workspace_entitlements RENAME COLUMN workspace_id TO shop_id;
ALTER TABLE public.workspace_invitations RENAME COLUMN workspace_id TO shop_id;
ALTER TABLE public.workspace_memberships RENAME COLUMN workspace_id TO shop_id;
ALTER TABLE public.workspace_subscriptions RENAME COLUMN workspace_id TO shop_id;
ALTER TABLE public.sharing_group_members RENAME COLUMN workspace_id TO shop_id;

ALTER TABLE public.workspace_entitlements RENAME TO shop_entitlements;
ALTER TABLE public.workspace_invitations RENAME TO shop_invitations;
ALTER TABLE public.workspace_memberships RENAME TO shop_memberships;
ALTER TABLE public.workspace_subscriptions RENAME TO shop_subscriptions;
