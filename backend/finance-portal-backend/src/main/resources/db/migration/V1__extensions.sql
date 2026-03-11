-- Enable pgcrypto extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Verify extension
SELECT extname, extversion FROM pg_extension WHERE extname = 'pgcrypto';
