import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { IconSetService } from '@coreui/icons-angular';
import { iconSubset } from '../../icons/icon-subset';
import { environment } from '../../../environments/environment';
import { AiAssistantComponent } from './ai-assistant.component';

/** Recherche dans tous les cours (RAG) : premier onglet de l'assistant de l'administration, réponse avec sources. */
describe('Assistant IA : recherche dans tous les cours (RAG)', () => {
  let http: HttpTestingController;
  const api = environment.apiUrl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AiAssistantComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), IconSetService]
    });
    TestBed.inject(IconSetService).icons = { ...iconSubset };
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(AiAssistantComponent);
    fixture.detectChanges();
    http.expectOne(`${api}/api/ai/rag/status`).flush({
      ready: true, mode: 'embeddings', chunks: 42, courses: 3, built_at: '2026-11-18T10:00:00+00:00',
      unreadable: [], embedding_model: 'text-embedding-3-small'
    });
    fixture.detectChanges();
    return fixture;
  }

  it('ouvre l\'onglet RAG en premier et affiche l\'état de l\'index', () => {
    const page: HTMLElement = render().nativeElement;
    expect(page.textContent).toContain('Recherche dans tous les cours (RAG)');
    expect(page.textContent).toContain('42 passages issus de 3 cours');
    expect(page.textContent).toContain('text-embedding-3-small');
    // Plus de mot « indexation » pour l'agent document, qui n'utilise pas de RAG
    expect(page.textContent).not.toContain('Indexation d\'un document');
  });

  it('affiche la réponse avec ses sources numérotées', () => {
    const fixture = render();
    fixture.componentInstance.ragQuestion = 'Quels cours parlent du jeton JWT ?';
    fixture.componentInstance.askRag();
    const req = http.expectOne(`${api}/api/ai/rag/ask`);
    expect(req.request.body).toEqual({ question: 'Quels cours parlent du jeton JWT ?' });
    req.flush({
      mode: 'embeddings', answer: 'Le cours Java avancé explique le jeton JWT [1].',
      sources: [{ n: 1, course_id: 7, course: 'Java avancé', chapter: 'Sécurité', lesson: 'JWT', resource: 'cours.pdf',
                  score: 0.82, excerpt: 'Le jeton JWT est signé et vérifié à chaque requête.' }]
    });
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent!;
    expect(text).toContain('Le cours Java avancé explique le jeton JWT [1].');
    expect(text).toContain('[1] Java avancé');
    expect(text).toContain('similarité cosinus');
  });

  it('refuse une question trop courte sans appeler le serveur', () => {
    const fixture = render();
    fixture.componentInstance.ragQuestion = 'a';
    fixture.componentInstance.askRag();
    http.expectNone(`${api}/api/ai/rag/ask`);
    expect(fixture.componentInstance.ragError()).toContain('au moins 3 caractères');
  });
});
