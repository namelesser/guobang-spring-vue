export interface TransportRecord {
  id: number;
  source: string;
  file_name: string | null;
  image_id: string;
  record_date: string | null;
  order_no: string | null;
  sender: string | null;
  receiver: string | null;
  company: string | null;
  plate_no: string | null;
  net_weight: number | null;
  freight_rate: number | null;
  detour_surcharge: number | null;
  total_cost: number | null;
  reviewed: number;
  reviewed_at: string | null;
  review_note: string;
  note: string;
  ocr_status: string;
  ocr_text: string;
  created_at: string;
  updated_at: string;
  first_image_id?: string;
}

export interface ImageAsset {
  id: number;
  file_name: string;
  mime_type: string;
  size: number;
  created_at: string;
  thumbnail_base64?: string;
  image_base64?: string;
  record_date?: string;
  order_no?: string;
  ocr_status?: string;
  ocr_text?: string;
  record_id?: number;
}

export interface FreightRate {
  id: number;
  origin: string;
  destination: string;
  sender: string | null;
  price_per_ton: number;
  effective_from: string;
  effective_to: string | null;
  note: string;
  created_at: string;
}

export interface CollectionItem {
  id: number;
  category: string;
  value: string;
}

export interface RecordSummary {
  total_trips: number;
  total_weight: number;
  total_freight: number;
  reviewed_count: number;
  unreviewed_count: number;
}

export interface RecordsResponse {
  records: TransportRecord[];
  total: number;
  summary: RecordSummary;
}

export interface ImagesResponse {
  images: ImageAsset[];
  total: number;
}

export interface RatesResponse {
  rates: FreightRate[];
  items?: FreightRate[];
  total?: number;
}

export interface CollectionsResponse {
  collections: Record<string, CollectionItem[]>;
}

export interface CollectionItemsResponse {
  items: CollectionItem[];
  total: number;
  collections?: Record<string, CollectionItem[]>;
}

export interface UnreviewedRecordsResponse {
  records: TransportRecord[];
  total: number;
  limit: number;
}

export interface RateLookupResponse {
  found: boolean;
  rate: FreightRate | null;
}

export interface ImageDetailResponse {
  image_base64: string;
}

export interface RereviewResponse {
  updated_records: number;
}

export interface ReportParams {
  month?: string;
  company?: string;
}

export interface ReportRow {
  company: string;
  total_trips: number;
  total_weight: number;
  total_freight: number;
}

export interface ReportResponse {
  records: ReportRow[];
  total_trips: number;
  total_weight: number;
  total_freight: number;
}

export interface DataQualityCheck {
  id: string;
  label: string;
  count: number;
}

export interface DataQualityResponse {
  future_dates: TransportRecord[];
  missing_images: TransportRecord[];
  missing_weights: TransportRecord[];
  missing_rates: TransportRecord[];
  stale_ocr_tasks: TransportRecord[];
  duplicate_orders: TransportRecord[];
  sender_company_mismatch: TransportRecord[];
  collection_checks: Array<{ field: string; label: string; items: TransportRecord[] }>;
  report?: {
    future_dates: TransportRecord[];
    missing_images: TransportRecord[];
    missing_weights: TransportRecord[];
    missing_rates: TransportRecord[];
    stale_ocr_tasks: TransportRecord[];
    duplicate_orders: TransportRecord[];
    sender_company_mismatch: TransportRecord[];
    receiver_not_in_collection: TransportRecord[];
    plate_no_not_in_collection: TransportRecord[];
    collection_checks: Array<{ field: string; label: string; items: TransportRecord[] }>;
  };
}

export interface RecordCreateData {
  record_date?: string | null;
  order_no?: string;
  sender?: string;
  receiver?: string;
  company?: string;
  plate_no?: string;
  net_weight?: number;
  freight_rate?: number;
  detour_surcharge?: number;
  total_cost?: number;
  note?: string;
  image_id?: string;
}

export interface RecordUpdateData extends Partial<RecordCreateData> {
  reviewed?: number;
  reviewed_at?: string;
  review_note?: string;
}

export interface OcrScanResponse {
  id: number;
  record_id: number;
  image_id: number;
  status: string;
}

export interface RateCreateData {
  origin: string;
  destination: string;
  sender?: string;
  price_per_ton: number;
  effective_from: string;
  effective_to?: string | null;
  note?: string;
}

export interface RateUpdateData extends Partial<RateCreateData> {}

export interface CollectionCreateData {
  category: string;
  value: string;
}

export interface CollectionUpdateData {
  value: string;
}

export interface ImageUpdateData {
  image_base64?: string;
  file_name?: string;
}

export interface RecordFilters {
  month?: string;
  reviewed?: string;
  source?: string;
  plate?: string;
  sender?: string;
  receiver?: string;
  company?: string;
  order_no?: string;
  limit?: string;
  offset?: string;
}

export interface ImageFilters {
  month?: string;
  ocr_status?: string;
  file_name?: string;
  order_no?: string;
  limit?: string;
  offset?: string;
}
