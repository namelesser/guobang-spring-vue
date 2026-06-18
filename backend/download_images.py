#!/usr/bin/env python3
"""
从京东云 OSS 下载图片到 PostgreSQL BYTEA 列。
运行前确保 PostgreSQL 在 127.0.0.1:5432 (database: transport)。
"""
import psycopg2
import psycopg2.extras
import boto3
from botocore.config import Config

# JD Cloud OSS 配置
ENDPOINT = "https://s3.cn-north-1.jdcloud-oss.com"
BUCKET = "logistics-system"
REGION = "cn-north-1"
ACCESS_KEY = "JDC_7D68D49AE8F79192E476D2BB4B31"
SECRET_KEY = "96FEA7A84CE0DCEFF4EC2A58B1EC2E1D"

DB_DSN = "host=127.0.0.1 port=5432 dbname=transport user=transport"


def make_s3_client():
    return boto3.client(
        "s3",
        endpoint_url=ENDPOINT,
        region_name=REGION,
        aws_access_key_id=ACCESS_KEY,
        aws_secret_access_key=SECRET_KEY,
        config=Config(
            signature_version="s3v4",
            s3={"addressing_style": "path"},
            request_checksum_calculation="when_required",
            response_checksum_validation="when_required",
        ),
    )


def main():
    conn = psycopg2.connect(DB_DSN)
    conn.autocommit = False
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)

    # 找出有 object_key 但没有 data 的图片
    cur.execute("""
        SELECT id, object_key, mime_type
        FROM images
        WHERE object_key IS NOT NULL AND object_key != ''
          AND (data IS NULL OR octet_length(data) = 0)
        ORDER BY id
    """)
    rows = cur.fetchall()
    print(f"待下载图片: {len(rows)}")

    if not rows:
        print("没有需要下载的图片")
        return

    s3 = make_s3_client()
    success = 0
    failed = 0

    for row in rows:
        image_id = row["id"]
        key = row["object_key"]
        try:
            resp = s3.get_object(Bucket=BUCKET, Key=key)
            data = resp["Body"].read()
            cur.execute(
                "UPDATE images SET data = %s WHERE id = %s",
                (psycopg2.Binary(data), image_id),
            )
            success += 1
            if success % 20 == 0:
                conn.commit()
                print(f"  已下载 {success}/{len(rows)}")
        except Exception as e:
            failed += 1
            print(f"  失败 id={image_id} key={key}: {e}")

    conn.commit()
    print(f"完成: 成功={success}, 失败={failed}")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
