import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { QueryResponse } from '../core/models';

interface ChatTurn {
  question: string;
  response: QueryResponse;
}

@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.scss',
})
export class ChatPageComponent {
  private api = inject(ApiService);

  question = '';
  asking = signal(false);
  error = signal<string | null>(null);
  turns = signal<ChatTurn[]>([]);

  ask(): void {
    const question = this.question.trim();
    if (!question || this.asking()) return;

    this.asking.set(true);
    this.error.set(null);
    this.api.query(question).subscribe({
      next: (response) => {
        this.turns.update((current) => [...current, { question, response }]);
        this.question = '';
        this.asking.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.detail ?? 'Richiesta fallita.');
        this.asking.set(false);
      },
    });
  }

  confidenceLabel(confidence: number): string {
    if (confidence >= 0.75) return 'alta';
    if (confidence >= 0.4) return 'media';
    return 'bassa';
  }
}
