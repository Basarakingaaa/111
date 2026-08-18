ALTER TABLE app_user
    ALTER COLUMN github_id DROP NOT NULL,
    ALTER COLUMN github_login DROP NOT NULL,
    ADD COLUMN username VARCHAR(64),
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN auth_type VARCHAR(16) NOT NULL DEFAULT 'GITHUB';

CREATE UNIQUE INDEX uq_app_user_username_lower
    ON app_user (LOWER(username))
    WHERE username IS NOT NULL;

ALTER TABLE app_user ADD CONSTRAINT ck_app_user_auth_identity CHECK (
    (auth_type = 'GITHUB'
        AND github_id IS NOT NULL
        AND github_login IS NOT NULL
        AND username IS NULL
        AND password_hash IS NULL)
    OR
    (auth_type = 'LOCAL'
        AND github_id IS NULL
        AND github_login IS NULL
        AND username IS NOT NULL
        AND password_hash IS NOT NULL)
);
