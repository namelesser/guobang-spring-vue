#!/usr/bin/env python3
"""从 S3 兼容对象存储回填图片到 PostgreSQL BYTEA 列。"""

import os

import boto3
from botocore.config import Config
import psycopg2
import psycopg2.extras

ENDPOINT = os.getenv("S3_ENDPOINT", "http://100.64.0.4:9000")
BUCKET = os.getenv("S3_BUCKET", "logistics-system")
REGION = os.getenv("S3_REGION", "us-east-1")
DB_DSN = os.getenv(
    "DB_DSN", "host=127.0.0.1 port=5432 dbname=transport user=transport"
)


def make_s3_client():
    access_key = os.getenv("S3_ACCESS_KEY_ID", "")
    secret_key = os.getenv("S3_SECRET_ACCESS_KEY", "")
    if not access_key or not secret_key:
        raise RuntimeError("缺少 S3_ACCESS_KEY_ID 或 S3_SECRET_ACCESS_KEY")
    return boto3.client(
        "s3",
        endpoint_url=ENDPOINT,
        region_name=REGION,
        aws_access_key_id=access_key,
        aws_secret_access_key=secret_key,
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
    cur.execute(
        """
        SELECT id, object_key, mime_type
        FROM images
        WHERE object_key IS NOT NULL AND object_key != ''
          AND (data IS NULL OR octet_length(data) = 0)
        ORDER BY id
        """
    )
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
            data = s3.get_object(Bucket=BUCKET, Key=key)["Body"].read()
            cur.execute(
                "UPDATE images SET data = %s WHERE id = %s",
                (psycopg2.Binary(data), image_id),
            )
            success += 1
            if success % 20 == 0:
                conn.commit()
                print(f"  已下载 {success}/{len(rows)}")
        except Exception as error:
            failed += 1
            print(f"  失败 id={image_id} key={key}: {error}")

    conn.commit()
    print(f"完成: 成功={success}, 失败={failed}")
    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
