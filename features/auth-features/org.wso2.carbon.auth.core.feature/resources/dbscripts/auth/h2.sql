CREATE TABLE IF NOT EXISTS AUTH_OAUTH2_CLIENTS (
            CLIENT_ID VARCHAR(256),
            CLIENT_SECRET VARCHAR(512),
            REDIRECT_URI VARCHAR(512) DEFAULT NULL,
            PRIMARY KEY (CLIENT_ID)
);