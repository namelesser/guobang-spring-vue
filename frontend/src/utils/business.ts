export function today(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export function monthOptions(count = 12) {
  const months = [];
  const now = new Date();
  for (let i = 0; i < count; i++) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
    months.push({ label: value, value });
  }
  return months;
}

export function fmtNum(v: unknown): string {
  return v == null || v === '' ? '' : Number(v).toFixed(2);
}

export function firstImageId(record: Record<string, unknown>): string {
  return (
    (record.first_image_id as string) ||
    String(record.image_id || '')
      .split(',')[0]
      .trim()
  );
}

export async function downloadExport(url: string, filename: string): Promise<void> {
  const response = await fetch(url, { credentials: 'include' });
  const blob = await response.blob();
  const a = document.createElement('a');
  const objectUrl = URL.createObjectURL(blob);
  a.href = objectUrl;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(objectUrl);
}
