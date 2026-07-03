-- V4: Keep existing databases in sync with RecordService insert/export fields.
ALTER TABLE records ADD COLUMN IF NOT EXISTS driver TEXT;
