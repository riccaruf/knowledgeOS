import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'documents', pathMatch: 'full' },
  {
    path: 'documents',
    loadComponent: () => import('./documents/documents-page.component').then((m) => m.DocumentsPageComponent),
  },
  {
    path: 'chat',
    loadComponent: () => import('./chat/chat-page.component').then((m) => m.ChatPageComponent),
  },
  { path: '**', redirectTo: 'documents' },
];
