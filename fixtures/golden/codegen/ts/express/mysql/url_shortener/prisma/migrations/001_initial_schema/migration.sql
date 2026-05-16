-- spec-to-rest generated migration. Apply via `npx prisma migrate deploy`.
-- Each statement is wrapped in an implicit transaction by Prisma.


CREATE TABLE url_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    click_count INT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_url_mappings PRIMARY KEY (id),
    CONSTRAINT ck_url_mappings_0 CHECK (click_count >= 0)
);


