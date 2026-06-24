import { request } from '@/service/request';
import type {
  RecordsResponse,
  TransportRecord,
  RecordCreateData,
  RecordUpdateData,
  ImagesResponse,
  ImageDetailResponse,
  ImageUpdateData,
  ImageFilters,
  RecordFilters,
  RatesResponse,
  RateCreateData,
  RateUpdateData,
  RateLookupResponse,
  CollectionsResponse,
  CollectionItemsResponse,
  CollectionCreateData,
  CollectionUpdateData,
  UnreviewedRecordsResponse,
  RereviewResponse,
  ReportParams,
  ReportResponse,
  DataQualityResponse,
  OcrScanResponse,
  OcrStatusResponse,
  OcrSummaryResponse
} from './types';

export async function fetchRecords(params: RecordFilters, signal?: AbortSignal): Promise<RecordsResponse> {
  const { data } = await request({
    url: '/api/records',
    method: 'get',
    params,
    signal
  });
  return data;
}

export async function fetchRecord(id: number): Promise<{ record: TransportRecord }> {
  const { data } = await request({
    url: `/api/records/id/${id}`,
    method: 'get'
  });
  return data;
}

export async function createRecord(recordData: RecordCreateData): Promise<void> {
  const { data } = await request({
    url: '/api/records',
    method: 'post',
    data: recordData
  });
  return data;
}

export async function updateRecord(id: number, recordData: RecordUpdateData): Promise<void> {
  const { data } = await request({
    url: `/api/records/${id}`,
    method: 'put',
    data: recordData
  });
  return data;
}

export async function deleteRecord(id: number): Promise<void> {
  const { data } = await request({
    url: `/api/records/${id}`,
    method: 'delete'
  });
  return data;
}

export async function reviewRecord(id: number, note: string): Promise<void> {
  const { data } = await request({
    url: `/api/records/${id}/review`,
    method: 'post',
    data: { review_note: note }
  });
  return data;
}

export async function fetchUnreviewedRecords(): Promise<UnreviewedRecordsResponse> {
  const { data } = await request({
    url: '/api/records/unreviewed/list',
    method: 'get'
  });
  return data;
}

export async function fetchImages(params: ImageFilters, signal?: AbortSignal): Promise<ImagesResponse> {
  const { data } = await request({
    url: '/api/images',
    method: 'get',
    params,
    signal
  });
  return data;
}

export async function fetchImage(id: number): Promise<ImageDetailResponse> {
  const { data } = await request({
    url: `/api/images/${id}`,
    method: 'get'
  });
  return data;
}

export async function deleteImage(id: number): Promise<void> {
  const { data } = await request({
    url: `/api/images/${id}`,
    method: 'delete'
  });
  return data;
}

export async function ocrScan(formData: FormData): Promise<OcrScanResponse> {
  const { data } = await request({
    url: '/api/ocr/scan',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return data;
}

export async function fetchOcrStatus(recordId: number): Promise<OcrStatusResponse> {
  const { data } = await request({
    url: '/api/ocr/status',
    method: 'get',
    params: { record_id: recordId }
  });
  return data;
}

export async function fetchOcrSummary(): Promise<OcrSummaryResponse> {
  const { data } = await request({
    url: '/api/admin/ocr/summary',
    method: 'get'
  });
  return data;
}

export async function fetchRates(params?: Record<string, string | number>): Promise<RatesResponse> {
  const { data } = await request({
    url: '/api/rates',
    method: 'get',
    params: params || {}
  });
  return data;
}

export async function createRate(rateData: RateCreateData): Promise<void> {
  const { data } = await request({
    url: '/api/rates',
    method: 'post',
    data: rateData
  });
  return data;
}

export async function updateRate(id: number, rateData: RateUpdateData): Promise<void> {
  const { data } = await request({
    url: `/api/rates/${id}`,
    method: 'put',
    data: rateData
  });
  return data;
}

export async function deleteRate(id: number): Promise<void> {
  const { data } = await request({
    url: `/api/rates/${id}`,
    method: 'delete'
  });
  return data;
}

export async function fetchCollections(
  category?: string,
  params?: Record<string, string | number>
): Promise<CollectionItemsResponse> {
  const { data } = await request({
    url: '/api/collections',
    method: 'get',
    params: { ...(category ? { category } : {}), ...params }
  });
  return data;
}

export async function createCollection(collectionData: CollectionCreateData): Promise<void> {
  const { data } = await request({
    url: '/api/collections',
    method: 'post',
    data: collectionData
  });
  return data;
}

export async function deleteCollection(id: number): Promise<void> {
  const { data } = await request({
    url: `/api/collections/${id}`,
    method: 'delete'
  });
  return data;
}

export async function fetchReport(params: ReportParams): Promise<ReportResponse> {
  const { data } = await request({
    url: '/api/report/monthly',
    method: 'get',
    params
  });
  return data;
}

export async function fetchDataQuality(): Promise<DataQualityResponse> {
  const { data } = await request({
    url: '/api/data-quality',
    method: 'get'
  });
  return data;
}

export async function updateImage(id: number, imageData: ImageUpdateData): Promise<void> {
  const { data } = await request({
    url: `/api/images/${id}`,
    method: 'put',
    data: imageData
  });
  return data;
}

export async function reocrImage(id: number): Promise<void> {
  const { data } = await request({
    url: `/api/images/${id}/reocr`,
    method: 'post'
  });
  return data;
}

export async function rereviewImage(id: number): Promise<RereviewResponse> {
  const { data } = await request({
    url: `/api/images/${id}/rereview`,
    method: 'post'
  });
  return data;
}

export async function lookupRate(params: {
  origin: string;
  destination: string;
  date?: string;
}): Promise<RateLookupResponse> {
  const { data } = await request({
    url: '/api/rates/lookup',
    method: 'get',
    params
  });
  return data;
}

export async function fetchAllCollections(): Promise<CollectionsResponse> {
  const { data } = await request({
    url: '/api/collections/all',
    method: 'get'
  });
  return data;
}

export async function updateCollection(id: number, collectionData: CollectionUpdateData): Promise<void> {
  const { data } = await request({
    url: `/api/collections/${id}`,
    method: 'put',
    data: collectionData
  });
  return data;
}
