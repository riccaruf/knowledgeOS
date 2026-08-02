export interface DocumentVersionResponse {
  id: string;
  versionLabel: string;
  ingestionStatus: 'PENDING' | 'PROCESSING' | 'PROCESSED' | 'FAILED';
  uploadedAt: string;
}

export interface DocumentSummaryResponse {
  id: string;
  title: string;
  category: string | null;
  department: string | null;
  tags: string[];
  lifecycleStatus: string;
  currentVersion: DocumentVersionResponse | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UploadDocumentResponse {
  documentId: string;
  versionId: string;
  ingestionStatus: string;
}

export interface QuerySourceResponse {
  documentId: string;
  documentTitle: string;
  versionLabel: string;
  page: number;
  section: string | null;
  excerpt: string;
  relevanceScore: number;
}

export interface QueryResponse {
  answer: string;
  confidence: number;
  sources: QuerySourceResponse[];
  conversationId: string;
  queryLogId: string;
}

export interface ModelsResponse {
  models: string[];
  defaultModel: string;
}

export interface MeResponse {
  id: string;
  email: string;
  displayName: string;
  tenantId: string;
  roles: string[];
}
