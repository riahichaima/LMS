import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AlertComponent,
  BadgeComponent,
  ButtonDirective,
  CardBodyComponent,
  CardComponent,
  CardHeaderComponent,
  SpinnerComponent
} from '@coreui/angular';
import { IconDirective } from '@coreui/icons-angular';
import { AssignmentSubmission, LearningService, StudentAssignment } from '../../services/learning.service';
import { MarkdownPipe } from '../../shared/markdown.pipe';

/**
 * TP de l'étudiant : énoncés complets (exemples, code de départ), réponse (texte ou code),
 * correction par l'IA, puis solution de référence visible après la soumission.
 */
@Component({
  selector: 'app-assignment-player',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, AlertComponent, BadgeComponent, ButtonDirective, CardComponent,
    CardBodyComponent, CardHeaderComponent, SpinnerComponent, IconDirective, MarkdownPipe
  ],
  templateUrl: './assignment-player.component.html',
  styleUrl: './assignment-player.component.scss'
})
export class AssignmentPlayerComponent implements OnInit {
  assignment = signal<StudentAssignment | null>(null);
  result = signal<AssignmentSubmission | null>(null);
  loading = signal(true);
  submitting = signal(false);
  error = signal('');
  answer = '';

  constructor(private route: ActivatedRoute, private learningService: LearningService) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.learningService.getAssignment(id).subscribe({
      next: (a) => {
        this.assignment.set(a);
        this.loading.set(false);
        if (a.lastSubmission) {
          this.result.set(a.lastSubmission);
          this.answer = a.lastSubmission.answer;
        } else {
          this.answer = this.starterCode(a.description);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err.error?.message || 'Impossible de charger ce TP.');
      }
    });
  }

  submit(): void {
    const a = this.assignment();
    if (!a || !this.answer.trim() || this.submitting()) return;
    this.submitting.set(true);
    this.error.set('');
    this.learningService.submitAssignment(a.id, this.answer).subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.result.set(res);
        setTimeout(() => document.getElementById('correction')?.scrollIntoView({ behavior: 'smooth' }));
      },
      error: (err) => {
        this.submitting.set(false);
        this.error.set(err.error?.message || "La correction par l'IA a échoué. Réessayez.");
      }
    });
  }

  retry(): void {
    this.result.set(null);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  onTab(event: KeyboardEvent): void {
    // Tabulation dans la zone de code au lieu de changer de champ
    if (event.key !== 'Tab') return;
    event.preventDefault();
    const el = event.target as HTMLTextAreaElement;
    const { selectionStart: start, selectionEnd: end } = el;
    this.answer = this.answer.slice(0, start) + '    ' + this.answer.slice(end);
    setTimeout(() => el.setSelectionRange(start + 4, start + 4));
  }

  // Pré-remplit la réponse avec les codes de départ des exercices
  private starterCode(description: string): string {
    const blocks: string[] = [];
    const sections = (description || '').split(/^### /m).filter(s => s.trim());
    sections.forEach((section, i) => {
      const starter = section.match(/Code de départ :\s*```[\w+-]*\n([\s\S]*?)```/);
      if (starter) blocks.push(`# Exercice ${i + 1}\n${starter[1].trimEnd()}`);
    });
    return blocks.join('\n\n');
  }
}
