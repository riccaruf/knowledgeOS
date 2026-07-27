import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import Keycloak from 'keycloak-js';

import { ApiService } from './core/api.service';
import { MeResponse } from './core/models';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private keycloak = inject(Keycloak);
  private api = inject(ApiService);

  me = signal<MeResponse | null>(null);

  constructor() {
    this.api.me().subscribe((me) => this.me.set(me));
  }

  get isDocumentManager(): boolean {
    return this.keycloak.hasRealmRole('DOCUMENT_MANAGER');
  }

  logout(): void {
    this.keycloak.logout();
  }
}
