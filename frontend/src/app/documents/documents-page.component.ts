import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import Keycloak from 'keycloak-js';

import { ApiService } from '../core/api.service';
import { DocumentSummaryResponse } from '../core/models';

@Component({
  selector: 'app-documents-page',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './documents-page.component.html',
  styleUrl: './documents-page.component.scss',
})
export class DocumentsPageComponent {
  private api = inject(ApiService);
  private keycloak = inject(Keycloak);

  documents = signal<DocumentSummaryResponse[]>([]);
  loading = signal(true);
  uploading = signal(false);
  uploadError = signal<string | null>(null);
  deletingId = signal<string | null>(null);

  title = '';
  category = '';
  department = '';
  selectedFile: File | null = null;

  private pollTimers = new Map<string, ReturnType<typeof setInterval>>();

  constructor() {
    this.refresh();
  }

  get canUpload(): boolean {
    return this.keycloak.hasRealmRole('DOCUMENT_MANAGER');
  }

  get canDelete(): boolean {
    return this.keycloak.hasRealmRole('TENANT_ADMIN');
  }

  refresh(): void {
    this.loading.set(true);
    this.api.listDocuments().subscribe({
      next: (page) => {
        this.documents.set(page.content);
        this.loading.set(false);
        page.content.forEach((doc) => this.trackIngestionIfPending(doc));
      },
      error: () => this.loading.set(false),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    if (this.selectedFile && !this.title) {
      this.title = this.selectedFile.name.replace(/\.pdf$/i, '');
    }
  }

  upload(): void {
    if (!this.selectedFile || !this.title.trim()) {
      this.uploadError.set('Seleziona un file PDF e indica un titolo.');
      return;
    }
    this.uploading.set(true);
    this.uploadError.set(null);
    this.api.uploadDocument(this.selectedFile, this.title.trim(), this.category || undefined, this.department || undefined)
      .subscribe({
        next: () => {
          this.uploading.set(false);
          this.selectedFile = null;
          this.title = '';
          this.category = '';
          this.department = '';
          this.refresh();
        },
        error: (err) => {
          this.uploading.set(false);
          this.uploadError.set(err?.error?.detail ?? 'Caricamento fallito.');
        },
      });
  }

  deleteDocument(doc: DocumentSummaryResponse): void {
    const confirmed = confirm(
      `Eliminare definitivamente "${doc.title}"? Il file e i relativi dati indicizzati (chunk/embedding) verranno rimossi dal sistema.`,
    );
    if (!confirmed) return;

    this.deletingId.set(doc.id);
    this.api.deleteDocument(doc.id).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.refresh();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.uploadError.set(err?.error?.detail ?? 'Eliminazione fallita.');
      },
    });
  }

  private trackIngestionIfPending(doc: DocumentSummaryResponse): void {
    const version = doc.currentVersion;
    if (!version) return;
    if (version.ingestionStatus === 'PROCESSED' || version.ingestionStatus === 'FAILED') return;
    if (this.pollTimers.has(doc.id)) return;

    const timer = setInterval(() => {
      this.api.ingestionStatus(doc.id, version.id).subscribe((status) => {
        if (status.status === 'PROCESSED' || status.status === 'FAILED') {
          clearInterval(timer);
          this.pollTimers.delete(doc.id);
          this.refresh();
        }
      });
    }, 3000);
    this.pollTimers.set(doc.id, timer);
  }
}
