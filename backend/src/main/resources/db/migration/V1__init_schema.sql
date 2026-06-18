-- V1: Initial schema
-- 创建运价表
CREATE TABLE IF NOT EXISTS freight_rates (
    id SERIAL PRIMARY KEY,
    origin TEXT NOT NULL,
    destination TEXT NOT NULL,
    price_per_ton NUMERIC NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    note TEXT DEFAULT '',
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP,
    sender TEXT
);

-- 创建运输记录表
CREATE TABLE IF NOT EXISTS records (
    id SERIAL PRIMARY KEY,
    source TEXT NOT NULL,
    file_name TEXT,
    image_id TEXT DEFAULT '',
    record_date DATE,
    order_no TEXT,
    sender TEXT,
    receiver TEXT,
    company TEXT,
    plate_no TEXT,
    net_weight NUMERIC,
    freight_rate NUMERIC,
    detour_surcharge NUMERIC DEFAULT 0,
    total_cost NUMERIC,
    reviewed INTEGER DEFAULT 0,
    reviewed_at TIMESTAMP,
    review_note TEXT DEFAULT '',
    note TEXT DEFAULT '',
    ocr_status TEXT DEFAULT '',
    ocr_text TEXT DEFAULT '',
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP,
    updated_at TIMESTAMP DEFAULT LOCALTIMESTAMP
);

-- 创建图片表
CREATE TABLE IF NOT EXISTS images (
    id SERIAL PRIMARY KEY,
    file_name TEXT NOT NULL,
    data BYTEA NOT NULL DEFAULT ''::bytea,
    mime_type TEXT DEFAULT 'image/jpeg',
    size INTEGER DEFAULT 0,
    thumbnail_data BYTEA,
    thumbnail_mime_type TEXT DEFAULT 'image/jpeg',
    thumbnail_updated_at TIMESTAMP,
    storage_backend TEXT DEFAULT 'db',
    object_key TEXT,
    thumbnail_object_key TEXT,
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP
);

-- 创建记录-图片关联表
CREATE TABLE IF NOT EXISTS record_images (
    id SERIAL PRIMARY KEY,
    record_id INTEGER NOT NULL REFERENCES records(id) ON DELETE CASCADE,
    image_id INTEGER NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP,
    UNIQUE(record_id, image_id)
);

-- 创建集合表（下拉选项）
CREATE TABLE IF NOT EXISTS collections (
    id SERIAL PRIMARY KEY,
    category TEXT NOT NULL,
    value TEXT NOT NULL,
    UNIQUE(category, value)
);

-- 创建OCR任务表
CREATE TABLE IF NOT EXISTS ocr_tasks (
    id SERIAL PRIMARY KEY,
    record_id INTEGER NOT NULL REFERENCES records(id) ON DELETE CASCADE,
    image_id INTEGER NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    file_name TEXT DEFAULT '',
    mime_type TEXT DEFAULT 'image/jpeg',
    priority INTEGER DEFAULT 0,
    status TEXT DEFAULT 'pending',
    retry_count INTEGER DEFAULT 0,
    error_message TEXT DEFAULT '',
    created_at TIMESTAMP DEFAULT LOCALTIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_records_date ON records(record_date);
CREATE INDEX IF NOT EXISTS idx_records_reviewed ON records(reviewed);
CREATE INDEX IF NOT EXISTS idx_records_source ON records(source);
CREATE INDEX IF NOT EXISTS idx_records_plate ON records(plate_no);
CREATE INDEX IF NOT EXISTS idx_records_image_id ON records(image_id);
CREATE INDEX IF NOT EXISTS idx_records_reviewed_id ON records(reviewed, id);
CREATE INDEX IF NOT EXISTS idx_records_date_reviewed ON records(record_date, reviewed);
CREATE INDEX IF NOT EXISTS idx_collections_cat ON collections(category);
CREATE INDEX IF NOT EXISTS idx_rates_lookup ON freight_rates(origin, destination, effective_from, effective_to);
CREATE INDEX IF NOT EXISTS idx_images_created ON images(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_record_images_record ON record_images(record_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_record_images_image ON record_images(image_id, record_id);
CREATE INDEX IF NOT EXISTS idx_ocr_tasks_status_priority ON ocr_tasks(status, priority, id);
CREATE INDEX IF NOT EXISTS idx_ocr_tasks_record ON ocr_tasks(record_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ocr_tasks_active_pair ON ocr_tasks(record_id, image_id) WHERE status IN ('pending', 'processing');
CREATE UNIQUE INDEX IF NOT EXISTS idx_records_order_company_unique ON records(order_no, company) WHERE NULLIF(order_no, '') IS NOT NULL AND NULLIF(company, '') IS NOT NULL;
