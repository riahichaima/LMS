import { Component, OnInit, computed, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CommentView, FeedbackKind, FeedbackService, ReactionSummary } from '../services/feedback.service';
import { AvatarComponent } from './avatar.component';

/**
 * J'aime / Je n'aime pas + commentaires, sur un cours entier (kind="course")
 * ou sous une ressource : PDF, vidéo, audio, lien, texte (kind="resource").
 */
@Component({
  selector: 'app-feedback-thread',
  standalone: true,
  imports: [CommonModule, FormsModule, AvatarComponent],
  template: `
    @if (feedback(); as f) {
      <div class="fb-bar">
        <button type="button" class="fb-btn" [class.active-like]="f.myReaction === 'LIKE'" (click)="react('LIKE')"
                [disabled]="!f.canReact || busy()" [attr.aria-pressed]="f.myReaction === 'LIKE'"
                [attr.aria-label]="'J\\'aime ' + target()" [title]="f.canReact ? 'J\\'aime' : 'Réservé aux étudiants inscrits'">
          👍 <span>{{ kind() === 'course' ? "J'aime" : '' }} {{ f.likes }}</span>
        </button>
        <button type="button" class="fb-btn" [class.active-dislike]="f.myReaction === 'DISLIKE'" (click)="react('DISLIKE')"
                [disabled]="!f.canReact || busy()" [attr.aria-pressed]="f.myReaction === 'DISLIKE'"
                [attr.aria-label]="'Je n\\'aime pas ' + target()" [title]="f.canReact ? 'Je n\\'aime pas' : 'Réservé aux étudiants inscrits'">
          👎 <span>{{ kind() === 'course' ? "Je n'aime pas" : '' }} {{ f.dislikes }}</span>
        </button>
        <button type="button" class="fb-btn" (click)="toggle()" [attr.aria-expanded]="open()">
          💬 {{ f.commentCount }} commentaire{{ f.commentCount > 1 ? 's' : '' }}
          <span class="ms-1">{{ open() ? '▴' : '▾' }}</span>
        </button>
      </div>

      @if (open()) {
        <div class="fb-comments">
          @for (c of comments(); track c.id) {
            <div class="fb-comment" [class.teacher]="c.fromTeacher">
              <app-avatar [name]="c.authorName" [url]="c.authorAvatarUrl" [size]="30" />
              <div class="flex-grow-1 min-w-0">
                <div class="d-flex justify-content-between gap-2">
                  <span class="small">
                    <strong>{{ c.authorName }}</strong>
                    @if (c.fromTeacher) { <span class="badge bg-primary ms-1">Formateur</span> }
                    <span class="text-body-secondary ms-1">{{ c.createdAt | date:'dd/MM/yyyy HH:mm' }}</span>
                  </span>
                  @if (c.canDelete) {
                    <button type="button" class="btn btn-link btn-sm p-0 text-danger" (click)="remove(c)" aria-label="Supprimer le commentaire">Supprimer</button>
                  }
                </div>
                <div class="fb-text">{{ c.content }}</div>
              </div>
            </div>
          } @empty {
            <div class="small text-body-secondary">Aucun commentaire. Soyez le premier à donner votre avis !</div>
          }

          @if (f.canComment) {
            <form class="fb-form" (ngSubmit)="send()">
              <textarea class="form-control form-control-sm" rows="2" maxlength="1000" name="draft"
                        [placeholder]="kind() === 'course' ? 'Écrire un commentaire sur ce cours…' : 'Écrire un commentaire sur ce contenu…'"
                        [ngModel]="draft()" (ngModelChange)="draft.set($event)" aria-label="Votre commentaire"></textarea>
              <button type="submit" class="btn btn-sm btn-primary" [disabled]="busy() || !draft().trim()">Publier</button>
            </form>
          }
          @if (error()) { <div class="small text-danger">{{ error() }}</div> }
        </div>
      }
    }
  `,
  styles: [`
    .fb-bar { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
    .fb-btn { display: inline-flex; align-items: center; gap: 4px; border: 1px solid var(--cui-border-color); background: var(--cui-body-bg);
              color: var(--cui-body-color); border-radius: 999px; padding: 4px 12px; font-size: .85rem; }
    .fb-btn:hover:not(:disabled) { border-color: var(--cui-primary); }
    .fb-btn:disabled { opacity: .75; cursor: default; }
    .fb-btn.active-like { border-color: var(--cui-success); background: rgba(var(--cui-success-rgb), .12); }
    .fb-btn.active-dislike { border-color: var(--cui-danger); background: rgba(var(--cui-danger-rgb), .12); }
    .fb-comments { margin-top: 10px; padding: 10px 12px; border-radius: 8px; background: var(--cui-tertiary-bg); display: flex; flex-direction: column; gap: 8px; }
    .fb-comment { display: flex; gap: 10px; padding: 8px 10px; border-radius: 8px; background: var(--cui-body-bg); }
    .fb-comment.teacher { border-left: 3px solid var(--cui-primary); }
    .min-w-0 { min-width: 0; }
    .fb-text { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .9rem; margin-top: 2px; }
    .fb-form { display: flex; gap: 8px; align-items: flex-end; }
  `]
})
export class FeedbackThreadComponent implements OnInit {
  kind = input<FeedbackKind>('resource');
  targetId = input.required<number>();
  /** Ouvrir directement la liste des commentaires. */
  startOpen = input(false);

  feedback = signal<ReactionSummary | null>(null);
  comments = signal<CommentView[]>([]);
  open = signal(false);
  busy = signal(false);
  draft = signal('');
  error = signal('');

  readonly target = computed(() => (this.kind() === 'course' ? 'ce cours' : 'ce contenu'));

  constructor(private service: FeedbackService) {}

  ngOnInit(): void {
    this.service.summary(this.kind(), this.targetId()).subscribe({ next: f => this.feedback.set(f), error: () => {} });
    if (this.startOpen()) {
      this.open.set(true);
      this.loadComments();
    }
  }

  react(type: 'LIKE' | 'DISLIKE'): void {
    this.busy.set(true);
    this.error.set('');
    this.service.react(this.kind(), this.targetId(), type).subscribe({
      next: f => { this.busy.set(false); this.feedback.set(f); },
      error: err => { this.busy.set(false); this.error.set(err.error?.message || 'Action impossible.'); }
    });
  }

  toggle(): void {
    this.open.update(v => !v);
    if (this.open()) this.loadComments();
  }

  send(): void {
    const content = this.draft().trim();
    if (!content) return;
    this.busy.set(true);
    this.error.set('');
    this.service.addComment(this.kind(), this.targetId(), content).subscribe({
      next: c => {
        this.busy.set(false);
        this.draft.set('');
        this.comments.update(list => [...list, c]);
        this.bumpCount(1);
      },
      error: err => { this.busy.set(false); this.error.set(err.error?.message || "Le commentaire n'a pas pu être publié."); }
    });
  }

  remove(c: CommentView): void {
    if (!confirm('Supprimer ce commentaire ?')) return;
    this.service.deleteComment(this.kind(), c.id).subscribe({
      next: () => { this.comments.update(list => list.filter(x => x.id !== c.id)); this.bumpCount(-1); },
      error: err => this.error.set(err.error?.message || 'Suppression impossible.')
    });
  }

  private loadComments(): void {
    this.service.comments(this.kind(), this.targetId()).subscribe({ next: list => this.comments.set(list), error: () => {} });
  }

  private bumpCount(delta: number): void {
    this.feedback.update(f => (f ? { ...f, commentCount: Math.max(0, f.commentCount + delta) } : f));
  }
}
