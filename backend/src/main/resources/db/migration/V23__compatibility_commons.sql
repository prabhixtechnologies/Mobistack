-- The compatibility graph becomes a commons, shared by every shop.
--
-- Today `brands`, `device_models`, `compatibility_groups` and `compatibility_group_devices` are all
-- keyed by `shop_id`. Every shop maintains its own private copy of a fact that is true for everyone:
-- whether a given screen fits a given phone. A hundred shops therefore do a hundred times the work for
-- a hundredth of the coverage, and the dataset's only real value — that it is complete because everyone
-- contributed — is destroyed by the very scoping meant to protect it.
--
-- Compatibility is not tenant data. It is not private, not competitive, and not per-shop. Stock levels
-- are all three, and those stay exactly where they are.
--
-- These tables have no `shop_id` at all, and that absence is the design. Any signed-in identity may
-- read them without belonging to a shop, which is also what makes the commons usable as the free half
-- of the product: someone can look up what fits before they ever create a workspace.
--
-- The per-shop tables are left untouched. They keep working, nothing is migrated behind anyone's back,
-- and a shop adopts the commons by linking its variants to `catalog_components` — see the FK added at
-- the bottom of this file. A rewrite that moved existing rows would have to guess which of a hundred
-- shops' conflicting private opinions is the true one, which is precisely what the review queue is for.

-- ---------------------------------------------------------------------------------------------------
-- The graph
-- ---------------------------------------------------------------------------------------------------

CREATE TABLE catalog_brands
(
    id              UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name            VARCHAR(80) NOT NULL,
    normalized_name TEXT GENERATED ALWAYS AS (fixflow_normalize(name)) STORED,
    logo_url        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT      NOT NULL DEFAULT 0
);

-- Global uniqueness, unlike `brands` which is unique per shop. One Samsung, not one per shop.
CREATE UNIQUE INDEX uq_catalog_brands_name ON catalog_brands (lower(name));

CREATE TABLE catalog_devices
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    brand_id        UUID         NOT NULL REFERENCES catalog_brands (id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    normalized_name TEXT GENERATED ALWAYS AS (fixflow_normalize(name)) STORED,
    -- Two phones sold under one name with different panels are different devices for this purpose,
    -- which is why the variant is part of the identity rather than a note.
    variant         VARCHAR(40),
    model_code      VARCHAR(60),
    release_year    INT,
    -- How often this device is looked up. Drives search ordering, so the common repair is the first
    -- result rather than the alphabetically luckiest one.
    lookup_count    BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_catalog_devices_identity
    ON catalog_devices (brand_id, lower(name), lower(coalesce(variant, '')));
CREATE INDEX ix_catalog_devices_search ON catalog_devices (normalized_name);

CREATE TABLE catalog_device_aliases
(
    id               UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    device_id        UUID         NOT NULL REFERENCES catalog_devices (id) ON DELETE CASCADE,
    alias            VARCHAR(120) NOT NULL,
    normalized_alias TEXT GENERATED ALWAYS AS (fixflow_normalize(alias)) STORED,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       UUID
);

CREATE UNIQUE INDEX uq_catalog_device_aliases ON catalog_device_aliases (lower(alias));

-- A part, described once for everyone. Not a product: a product is something a shop sells, with a
-- price and a supplier and a margin. A component is the thing itself — "iPhone 11 LCD assembly" — and
-- twenty shops selling it are selling the same component.
CREATE TABLE catalog_components
(
    id              UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    -- Matches `categories.code`, which is a stable string rather than a per-shop id, so a component
    -- can name its category without borrowing one shop's category row.
    category_code   VARCHAR(64)  NOT NULL,
    name            VARCHAR(160) NOT NULL,
    normalized_name TEXT GENERATED ALWAYS AS (fixflow_normalize(name)) STORED,
    description     TEXT,
    -- Panel type, connector count, colour — whatever distinguishes two parts that fit the same phone.
    -- JSONB because the meaningful attributes differ per category and pinning them into columns would
    -- mean a migration every time a new kind of part appears.
    attributes      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_catalog_components_identity
    ON catalog_components (category_code, lower(name));
CREATE INDEX ix_catalog_components_search ON catalog_components (normalized_name);

-- The edge, and the whole point of the exercise: this component fits that device.
CREATE TABLE catalog_fitments
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    component_id  UUID        NOT NULL REFERENCES catalog_components (id) ON DELETE CASCADE,
    device_id     UUID        NOT NULL REFERENCES catalog_devices (id) ON DELETE CASCADE,
    fit_quality   VARCHAR(24) NOT NULL DEFAULT 'EXACT',

    -- Not a boolean. "Someone said so once" and "forty shops fitted it and two disputed it" are
    -- different claims, and a reader deciding whether to order the part needs to tell them apart.
    confirmations INT         NOT NULL DEFAULT 0,
    disputes      INT         NOT NULL DEFAULT 0,
    -- Set when a reviewer with the authority to settle it has looked. Distinct from confirmations,
    -- which are a crowd count.
    verified_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    verified_at   TIMESTAMPTZ,
    -- Kept visible rather than deleted. An edge under dispute is information; a missing edge is not,
    -- and deleting it invites the same wrong claim to be re-added next week.
    disputed      BOOLEAN     NOT NULL DEFAULT FALSE,

    contributed_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_catalog_fitment_quality
        CHECK (fit_quality IN ('EXACT', 'COMPATIBLE', 'REQUIRES_MODIFICATION')),
    CONSTRAINT uq_catalog_fitment UNIQUE (component_id, device_id)
);

CREATE INDEX ix_catalog_fitments_device ON catalog_fitments (device_id);
CREATE INDEX ix_catalog_fitments_component ON catalog_fitments (component_id);

-- ---------------------------------------------------------------------------------------------------
-- Who may write to it
-- ---------------------------------------------------------------------------------------------------

-- Reputation, earned rather than assigned. A newcomer's edits queue for review; someone with a track
-- record writes directly. Without this the commons has two bad options: trust everyone, and it fills
-- with wrong claims nobody can correct at scale, or trust nobody, and every contribution waits on a
-- reviewer who does not exist yet.
CREATE TABLE catalog_contributors
(
    user_id        UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    accepted_count INT         NOT NULL DEFAULT 0,
    rejected_count INT         NOT NULL DEFAULT 0,
    -- Contributions apply immediately. Granted by a reviewer, not computed, because the threshold is a
    -- judgement about a person and an automatic promotion is a thing to game.
    trusted        BOOLEAN     NOT NULL DEFAULT FALSE,
    trusted_at     TIMESTAMPTZ,
    trusted_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    -- Contributions are refused outright. Separate from untrusted: an untrusted contributor is new, a
    -- banned one is known.
    banned         BOOLEAN     NOT NULL DEFAULT FALSE,
    banned_reason  VARCHAR(255),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT      NOT NULL DEFAULT 0
);

-- A proposed change, as data. One table with a payload rather than a parallel "proposed" copy of every
-- catalog table: the review queue's job is identical whatever is being proposed, and a second set of
-- tables would drift from the first the first time a column is added.
CREATE TABLE catalog_contributions
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    kind          VARCHAR(32) NOT NULL,
    -- Null when the proposal creates something. Set when it changes or disputes an existing row.
    target_id     UUID,
    payload       JSONB       NOT NULL,
    -- Why. A dispute with no reason cannot be judged by anyone but its author.
    reason        VARCHAR(500),

    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    submitted_by  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reviewed_by   UUID REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at   TIMESTAMPTZ,
    review_note   VARCHAR(500),
    -- What the proposal produced when it applied, so an accepted contribution can be traced to its row.
    applied_id    UUID,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    UUID,
    updated_by    UUID,
    version       BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_catalog_contribution_kind CHECK (kind IN (
        'ADD_DEVICE', 'ADD_COMPONENT', 'ADD_FITMENT', 'DISPUTE_FITMENT', 'CONFIRM_FITMENT')),
    CONSTRAINT ck_catalog_contribution_status CHECK (status IN (
        'PENDING', 'APPLIED', 'REJECTED'))
);

-- The review queue, oldest first. Partial, because the queue is the only thing anyone reads this
-- table for in bulk and PENDING rows are a small fraction of the total once it is in use.
CREATE INDEX ix_catalog_contributions_queue
    ON catalog_contributions (created_at) WHERE status = 'PENDING';
CREATE INDEX ix_catalog_contributions_author
    ON catalog_contributions (submitted_by, created_at DESC);

-- ---------------------------------------------------------------------------------------------------
-- The join that makes both halves worth more together
-- ---------------------------------------------------------------------------------------------------

-- "I have 12 of this part, and it fits these 40 models" is one query once a shop's variant points at a
-- catalog component. Nullable and unenforced: a shop that has not linked anything keeps working
-- exactly as before, and linking is a per-variant decision rather than a migration.
ALTER TABLE product_variants
    ADD COLUMN catalog_component_id UUID REFERENCES catalog_components (id) ON DELETE SET NULL;

CREATE INDEX ix_product_variants_catalog_component
    ON product_variants (catalog_component_id) WHERE catalog_component_id IS NOT NULL;

-- Settling a dispute and promoting a contributor are platform decisions, not shop ones, so they are
-- guarded by a permission the shop roles do not grant. COMPATIBILITY_APPROVE already exists and is
-- about a shop's own private groups; this is deliberately a different authority.
INSERT INTO permissions (code, resource, action, description)
VALUES ('COMMONS_REVIEW', 'COMMONS', 'REVIEW',
        'Review commons contributions and promote contributors')
ON CONFLICT (code) DO NOTHING;
