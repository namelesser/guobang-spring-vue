-- V2: Add updated_at trigger and additional indexes

-- 创建 updated_at 自动更新触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = LOCALTIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为 records 表添加触发器
CREATE TRIGGER update_records_updated_at
    BEFORE UPDATE ON records
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 添加额外的索引
CREATE INDEX IF NOT EXISTS idx_records_order_no ON records(order_no);
CREATE INDEX IF NOT EXISTS idx_records_company ON records(company);
CREATE INDEX IF NOT EXISTS idx_records_sender ON records(sender);
CREATE INDEX IF NOT EXISTS idx_records_receiver ON records(receiver);
CREATE INDEX IF NOT EXISTS idx_records_created_at ON records(created_at DESC);
