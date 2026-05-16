


CREATE TABLE url_mappings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL,
    url TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    click_count INTEGER NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_url_mappings_0 CHECK (click_count >= 0)
);



