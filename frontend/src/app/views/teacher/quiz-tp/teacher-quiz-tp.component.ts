import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { TeacherAiContentComponent } from '../ai-content/teacher-ai-content.component';
import { TeacherAssessmentsComponent } from '../assessments/teacher-assessments.component';
import { TeacherFilesComponent } from './teacher-files.component';

export type QuizTpTab = 'list' | 'generate' | 'files';

/**
 * Page unique « Quiz & TP » de l'enseignant : liste avec statistiques et corrections par niveau,
 * génération avec l'IA et création manuelle, réunies dans des onglets (adresse : ?tab=list|generate|manual).
 */
@Component({
  selector: 'app-teacher-quiz-tp',
  standalone: true,
  imports: [IconDirective, PageHeaderComponent, TeacherAiContentComponent, TeacherAssessmentsComponent, TeacherFilesComponent],
  template: `
    <app-page-header title="Quiz & TP" icon="cilTask"
                     subtitle="Quiz et TP générés avec l'IA (corrections par niveau) et sujets que vous importez depuis votre ordinateur." />

    <ul class="nav nav-pills lms-tabs quiz-tabs mb-4" role="tablist">
      <li class="nav-item">
        <button type="button" role="tab" class="nav-link" [class.active]="tab() === 'list'" [attr.aria-selected]="tab() === 'list'" (click)="go('list')">
          <svg cIcon name="cilList" size="sm"></svg>Quiz & TP de l'IA
        </button>
      </li>
      <li class="nav-item">
        <button type="button" role="tab" class="nav-link" [class.active]="tab() === 'generate'" [attr.aria-selected]="tab() === 'generate'" (click)="go('generate')">
          <svg cIcon name="cilLightbulb" size="sm"></svg>Générer avec l'IA
        </button>
      </li>
      <li class="nav-item">
        <button type="button" role="tab" class="nav-link" [class.active]="tab() === 'files'" [attr.aria-selected]="tab() === 'files'" (click)="go('files')">
          <svg cIcon name="cilCloudUpload" size="sm"></svg>Importer un fichier
        </button>
      </li>
    </ul>

    @switch (tab()) {
      @case ('generate') { <app-teacher-assessments /> }
      @case ('files') { <app-teacher-files /> }
      @default { <app-teacher-ai-content (navigate)="go($event)" /> }
    }
  `,
  styles: [`.quiz-tabs .nav-link { padding: .5rem 1.1rem; }`]
})
export class TeacherQuizTpComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  tab = signal<QuizTpTab>('list');

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const t = params.get('tab');
      this.tab.set(t === 'generate' || t === 'files' ? t : 'list');
    });
  }

  go(tab: QuizTpTab): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab: tab === 'list' ? null : tab } });
  }
}
