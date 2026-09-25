-- A shop asks to share a fitment group's compatibility by typing the group's code.
-- The request stays pending until an owner or admin of the group admits the shop.

ALTER TABLE sharing_groups ADD COLUMN join_code VARCHAR(16);

CREATE UNIQUE INDEX uq_sharing_groups_join_code
    ON sharing_groups (lower(join_code))
    WHERE join_code IS NOT NULL;

CREATE TABLE group_join_requests (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES sharing_groups (id) ON DELETE CASCADE,
    shop_id UUID NOT NULL REFERENCES shops (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_group_join_status CHECK (status IN ('PENDING', 'REJECTED', 'CANCELLED', 'ADMITTED'))
);

CREATE UNIQUE INDEX uq_group_join_open
    ON group_join_requests (group_id, shop_id)
    WHERE status = 'PENDING';

CREATE INDEX ix_group_join_shop ON group_join_requests (shop_id, status);
