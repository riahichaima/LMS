import { AfterViewInit, Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../../services/auth.service';

// Vrai dès que l'intro a été jouée depuis le chargement de l'application
let introPlayed = false;

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink, IconDirective],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements AfterViewInit, OnDestroy {
  private host = inject(ElementRef<HTMLElement>);
  private introLogo = viewChild<ElementRef<HTMLImageElement>>('introLogo');
  private navLogo = viewChild<ElementRef<HTMLImageElement>>('navLogo');
  private timers: ReturnType<typeof setTimeout>[] = [];
  private observer?: IntersectionObserver;

  // L'intro (logo qui arrive de loin puis se place dans la barre) est jouée à l'ouverture de l'application
  private readonly skipIntroOnLoad = this.introAlreadySeen();
  showIntro = signal(!this.skipIntroOnLoad);
  introDone = signal(this.skipIntroOnLoad);
  openFaq = signal<number | null>(0);

  readonly navLinks = [
    { id: 'pour-qui', label: 'Pour qui' },
    { id: 'methode', label: 'Comment ça marche' },
    { id: 'outils', label: 'Outils' },
    { id: 'faq', label: 'FAQ' }
  ];

  readonly audiences = [
    {
      icon: 'cilUser', title: 'Étudiants',
      text: 'Suivez vos formations et entraînez-vous avec des exercices adaptés à votre niveau.',
      points: ['Catalogue de formations', 'Quiz et TP avec corrections', 'Assistant IA pour vos questions']
    },
    {
      icon: 'cilBook', title: 'Enseignants',
      text: 'Créez vos cours à partir de vos supports et laissez l\'IA préparer les évaluations.',
      points: ['Studio de création de cours', 'Quiz et TP générés par niveau', 'Corrigés prêts à l\'emploi']
    },
    {
      icon: 'cilSettings', title: 'Administrateurs',
      text: 'Gérez les comptes, les rôles et les formations de toute la plateforme.',
      points: ['Gestion des utilisateurs et des rôles', 'Attribution des formateurs', 'Statistiques de la plateforme']
    }
  ];

  readonly steps = [
    { title: 'Créez votre cours', text: 'Organisez-le en chapitres et en leçons, puis ajoutez vos supports : un seul ou plusieurs par leçon.' },
    { title: 'Générez avec l\'IA', text: 'Choisissez le chapitre, les niveaux de difficulté et les sources à utiliser : quiz et TP sont prêts en moins d\'une minute.' },
    { title: 'Partagez et corrigez', text: 'Chaque question et chaque exercice est accompagné de sa correction, que vous pouvez relire avant de la partager.' }
  ];

  readonly stats = [
    { value: '6', label: 'types de supports acceptés' },
    { value: '3', label: 'niveaux de difficulté' },
    { value: '< 1 min', label: 'pour générer quiz et TP' },
    { value: '3', label: 'espaces dédiés' }
  ];

  readonly formats = [
    { icon: 'cilFile', label: 'Documents PDF' },
    { icon: 'cilMediaPlay', label: 'Vidéos MP4' },
    { icon: 'cilSpeech', label: 'Audios MP3' },
    { icon: 'cilShareBoxed', label: 'Vidéos YouTube' },
    { icon: 'cilExternalLink', label: 'Pages web' },
    { icon: 'cilNotes', label: 'Textes' },
    { icon: 'cilListNumbered', label: 'Quiz QCM' },
    { icon: 'cilTask', label: 'Sujets de TP' },
    { icon: 'cilCheck', label: 'Corrections' },
    { icon: 'cilLightbulb', label: 'Assistant IA' },
    { icon: 'cilChartPie', label: 'Suivi de progression' },
    { icon: 'cilPeople', label: 'Gestion des rôles' }
  ];

  readonly faqs = [
    {
      q: 'D\'où viennent les questions des quiz ?',
      a: 'Uniquement des supports que vous avez ajoutés au chapitre : PDF, transcription des vidéos et des audios, pages web. Vous choisissez même les sources à utiliser.'
    },
    {
      q: 'Quels formats puis-je importer ?',
      a: 'Des PDF, des vidéos (MP4, WebM, MOV…), des audios (MP3, WAV, M4A…), des liens YouTube, des pages web et du texte. Une leçon peut en combiner plusieurs.'
    },
    {
      q: 'L\'IA peut-elle se tromper ?',
      a: 'Oui, comme toute IA. Chaque question est livrée avec sa correction pour que l\'enseignant la relise avant de la partager avec ses étudiants.'
    },
    {
      q: 'Combien de temps prend une génération ?',
      a: 'En général moins d\'une minute pour un quiz et un TP sur plusieurs niveaux. Une vidéo est analysée une seule fois, puis réutilisée.'
    },
    {
      q: 'Qui peut créer des cours ?',
      a: 'Les enseignants et les administrateurs. Les étudiants consultent les formations, passent les quiz et utilisent l\'assistant IA.'
    },
    {
      q: 'Puis-je modifier un cours après sa création ?',
      a: 'Oui : vous pouvez ajouter, remplacer ou retirer des supports à tout moment, sans perdre les quiz et TP déjà générés.'
    }
  ];

  constructor(public authService: AuthService, private router: Router) {}

  ngAfterViewInit(): void {
    this.setupReveal();
    if (!this.showIntro()) return;
    // Phase 1 (CSS) : le logo arrive de loin et grandit jusqu'à occuper la page.
    // Phase 2 : il rejoint sa place dans la barre de navigation.
    this.timers.push(setTimeout(() => this.moveLogoToNavbar(), 2900));
  }

  ngOnDestroy(): void {
    this.timers.forEach(clearTimeout);
    this.observer?.disconnect();
  }

  skipIntro(): void {
    this.finishIntro();
  }

  goToSpace(): void {
    this.router.navigateByUrl(this.authService.getHomeRoute());
  }

  scrollTo(id: string): void {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  toggleFaq(index: number): void {
    this.openFaq.update(current => (current === index ? null : index));
  }

  // Les blocs marqués .reveal apparaissent en douceur quand ils entrent à l'écran
  private setupReveal(): void {
    const blocks: HTMLElement[] = Array.from(this.host.nativeElement.querySelectorAll('.reveal'));
    if (typeof IntersectionObserver === 'undefined' || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      blocks.forEach(b => b.classList.add('revealed'));
      return;
    }
    this.observer = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('revealed');
          this.observer?.unobserve(entry.target);
        }
      }
    }, { threshold: 0.12 });
    blocks.forEach(b => this.observer!.observe(b));
  }

  private moveLogoToNavbar(): void {
    const from = this.introLogo()?.nativeElement;
    const to = this.navLogo()?.nativeElement;
    if (!from || !to || typeof from.animate !== 'function') {
      this.finishIntro();
      return;
    }
    const a = from.getBoundingClientRect();
    const b = to.getBoundingClientRect();
    const scale = b.height / a.height;
    const dx = b.left + b.width / 2 - (a.left + a.width / 2);
    const dy = b.top + b.height / 2 - (a.top + a.height / 2);
    // Le logo est déjà à l'échelle 1 (fin de la phase CSS) : on fige cet état avant de le déplacer
    from.style.animation = 'none';
    from.animate(
      [
        { transform: 'translate(0, 0) scale(1)' },
        { transform: `translate(${dx}px, ${dy}px) scale(${scale})` }
      ],
      { duration: 900, easing: 'cubic-bezier(.65,0,.35,1)', fill: 'forwards' }
    );
    this.introDone.set(true); // le fond blanc s'efface et la page apparaît pendant le déplacement
    this.timers.push(setTimeout(() => this.finishIntro(), 950));
  }

  private finishIntro(): void {
    this.introDone.set(true);
    this.showIntro.set(false);
  }

  private introAlreadySeen(): boolean {
    const seen = introPlayed || !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    introPlayed = true;
    return seen;
  }
}
