import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { IconSetService } from '@coreui/icons-angular';
import { of } from 'rxjs';
import { iconSubset } from '../../../icons/icon-subset';
import { AuthService } from '../../../services/auth.service';
import { adminNavItems, superAdminNavItems, teacherNavItems } from '../../../layout/default-layout/_nav';
import { TeacherQuizTpComponent } from './teacher-quiz-tp.component';

/** L'administration crée des cours pour un formateur et génère des quiz et TP avec l'IA pour tous les cours. */
describe('Pédagogie de l\'administration', () => {
  const urls = (items: { url?: unknown }[]) => items.map(i => i.url).filter(Boolean);

  it('le menu de l\'admin et du super admin donne accès au studio et aux quiz et TP avec l\'IA', () => {
    for (const nav of [adminNavItems, superAdminNavItems]) {
      expect(nav.some(i => i.title && i.name === 'Pédagogie')).toBe(true);
      expect(urls(nav)).toContain('/teacher/courses');
      expect(urls(nav)).toContain('/teacher/quiz-tp');
    }
  });

  it('le menu de l\'enseignant ne montre aucune page d\'administration', () => {
    expect(urls(teacherNavItems).some(u => String(u).startsWith('/admin'))).toBe(false);
  });

  function render(isAdmin: boolean, tab: string | null) {
    TestBed.configureTestingModule({
      imports: [TeacherQuizTpComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(), IconSetService,
        { provide: AuthService, useValue: { isAdmin: () => isAdmin, userRole: () => (isAdmin ? 'ADMIN' : 'TEACHER') } },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(tab ? { tab } : {})) } },
        { provide: Router, useValue: { navigate: vi.fn() } }
      ]
    });
    TestBed.inject(IconSetService).icons = { ...iconSubset };
    const fixture = TestBed.createComponent(TeacherQuizTpComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('l\'admin génère avec l\'IA mais n\'a pas l\'onglet d\'import des sujets et des copies', () => {
    const fixture = render(true, 'files');
    const page: HTMLElement = fixture.nativeElement;
    expect(page.textContent).toContain('Quiz & TP avec l\'IA');
    expect(page.textContent).toContain('Générer avec l\'IA');
    expect(page.textContent).not.toContain('Importer un fichier');
    // Une adresse ?tab=files revient à la liste pour l'administration
    expect(fixture.componentInstance.tab()).toBe('list');
  });

  it('l\'enseignant garde ses trois onglets, dont l\'import de fichiers', () => {
    const fixture = render(false, 'files');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('[role="tab"]').length).toBe(3);
    expect(fixture.componentInstance.tab()).toBe('files');
  });
});
