-- spec-to-rest generated migration. Apply via `npx prisma migrate deploy`.
-- Each statement is wrapped in an implicit transaction by Prisma.


CREATE TABLE url_mappings (
    id BIGSERIAL NOT NULL,
    code TEXT NOT NULL,
    url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    click_count INTEGER NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT NOW() NOT NULL,
    CONSTRAINT pk_url_mappings PRIMARY KEY (id),
    CONSTRAINT ck_url_mappings_0 CHECK (length(code) >= 6),
    CONSTRAINT ck_url_mappings_1 CHECK (length(code) <= 10),
    CONSTRAINT ck_url_mappings_2 CHECK (code ~ '^[a-zA-Z0-9]+$'),
    CONSTRAINT ck_url_mappings_3 CHECK (length(url) > 0),
    CONSTRAINT ck_url_mappings_4 CHECK (click_count >= 0)
);


