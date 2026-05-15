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
    CONSTRAINT ck_url_mappings_0 CHECK (click_count >= 0)
);


