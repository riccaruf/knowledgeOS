import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../core/api.service';
import { QueryResponse } from '../core/models';

interface ChatTurn {
  question: string;
  response: QueryResponse;
  modelUsed: string;
}

@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [FormsModule, DecimalPipe],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.scss',
})
export class ChatPageComponent implements OnInit {
  private api = inject(ApiService);

  question = '';
  asking = signal(false);
  error = signal<string | null>(null);
  turns = signal<ChatTurn[]>([]);

  availableModels = signal<string[]>([]);
  selectedModel = signal<string>('');
  defaultModel = signal<string>('');
  showSettings = signal(false);
  modelsLoading = signal(true);

  ngOnInit(): void {
    this.api.getModels().subscribe({
      next: (res) => {
        this.availableModels.set(res.models);
        this.defaultModel.set(res.defaultModel);
        this.selectedModel.set(res.defaultModel);
        this.modelsLoading.set(false);
      },
      error: () => {
        this.modelsLoading.set(false);
      },
    });
  }

  ask(): void {
    const question = this.question.trim();
    if (!question || this.asking()) return;

    const model = this.selectedModel();
    this.asking.set(true);
    this.error.set(null);
    this.api.query(question, undefined, model).subscribe({
      next: (response) => {
        this.turns.update((current) => [...current, { question, response, modelUsed: model }]);
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
