

CREATE TABLE url_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(255) NOT NULL,
    url VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    click_count INT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_url_mappings PRIMARY KEY (id),
    CONSTRAINT ck_url_mappings_0 CHECK (char_length(code) >= 6),
    CONSTRAINT ck_url_mappings_1 CHECK (code REGEXP '^[a-zA-Z0-9]+$'),
    CONSTRAINT ck_url_mappings_2 CHECK (char_length(url) > 0),
    CONSTRAINT ck_url_mappings_3 CHECK (click_count >= 0)
);


