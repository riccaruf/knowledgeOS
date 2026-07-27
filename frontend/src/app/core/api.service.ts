import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { appConfig } from './app-config';
import {
  DocumentSummaryResponse,
  MeResponse,
  PageResponse,
  QueryResponse,
  UploadDocumentResponse,
} from './models';

/**
 * Client verso il backend KnowledgeOS (04_API_SPECIFICATION.md).
 * Il Bearer token viene allegato automaticamente dall'interceptor
 * keycloak-angular (includeBearerTokenInterceptor) per le richieste verso
 * apiBaseUrl, configurato in app.config.ts.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private baseUrl = appConfig.apiBaseUrl;

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${this.baseUrl}/api/v1/me`);
  }

  listDocuments(page = 0, size = 20): Observable<PageResponse<DocumentSummaryResponse>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<DocumentSummaryResponse>>(`${this.baseUrl}/api/v1/documents`, { params });
  }

  uploadDocument(file: File, title: string, category?: string, department?: string): Observable<UploadDocumentResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    if (category) formData.append('category', category);
    if (department) formData.append('department', department);
    return this.http.post<UploadDocumentResponse>(`${this.baseUrl}/api/v1/documents`, formData);
  }

  ingestionStatus(documentId: string, versionId: string): Observable<{ status: string; error: string | null }> {
    return this.http.get<{ status: string; error: string | null }>(
      `${this.baseUrl}/api/v1/documents/${documentId}/versions/${versionId}/ingestion-status`,
    );
  }

  deleteDocument(documentId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/documents/${documentId}`);
  }

  query(question: string, category?: string[]): Observable<QueryResponse> {
    return this.http.post<QueryResponse>(`${this.baseUrl}/api/v1/query`, {
      question,
      filters: category && category.length ? { category } : null,
    });
  }
}
