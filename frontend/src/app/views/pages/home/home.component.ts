import { AfterViewInit, Component, ElementRef, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../../services/auth.service';
import { BrandIntroComponent } from '../../../shared/brand-intro/brand-intro.component';

// Vrai dès que l'intro a été jouée depuis le chargement de l'application
let introPlayed = false;

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink, IconDirective, BrandIntroComponent],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements AfterViewInit, OnDestroy {
  private host = inject(ElementRef<HTMLElement>);
  /** Slogan de la marque (intro et pied de page) */
  readonly slogan = 'Votre plateforme intelligente pour l\'e‑learning';
  private observer?: IntersectionObserver;

  // L'intro de marque est jouée une fois à l'ouverture de l'application (jamais en navigation, ni si l'utilisateur réduit les animations)
  private readonly skipIntroOnLoad = this.introAlreadySeen();
  showIntro = signal(!this.skipIntroOnLoad);
  introDone = signal(this.skipIntroOnLoad);
  openFaq = signal<number | null>(0);

  /** Parcours affiché dans « Comment ça marche » */
  parcours = signal<'student' | 'teacher'>('student');

  readonly navLinks = [
    { id: 'pour-qui', label: 'Pour qui' },
    { id: 'methode', label: 'Comment ça marche' },
    { id: 'outils', label: 'Fonctionnalités' },
    { id: 'faq', label: 'FAQ' }
  ];

  readonly audiences = [
    {
      icon: 'cilUser', title: 'Étudiants',
      text: 'Suivez vos formations, entraînez-vous et restez en contact avec vos formateurs.',
      points: ['Catalogue de formations gratuites et payantes', 'Quiz et TP corrigés, assistant IA', 'Messagerie, réunions en visio et enregistrements', 'Tableau de bord de votre progression']
    },
    {
      icon: 'cilBook', title: 'Enseignants',
      text: 'Créez vos cours, laissez l\'IA préparer les évaluations et accompagnez vos étudiants.',
      points: ['Studio de création de cours', 'Quiz et TP générés par niveau', 'Réunions en visio planifiées avec rappels', 'Suivi pédagogique, avis et revenus']
    },
    {
      icon: 'cilSettings', title: 'Administrateurs',
      text: 'Gérez les comptes, les formations et supervisez toute la plateforme.',
      points: ['Utilisateurs, rôles et formateurs', 'Statistiques globales et graphiques', 'Journal d\'activité du système', 'Suivi financier des ventes']
    }
  ];

  readonly steps = {
    student: [
      { title: 'Choisissez votre formation', text: 'Parcourez le catalogue, consultez les avis des autres étudiants et inscrivez-vous, gratuitement ou par paiement sécurisé.' },
      { title: 'Apprenez à votre rythme', text: 'PDF, vidéos, audios et liens dans un même lecteur ; l\'assistant IA explique et résume chaque notion à la demande.' },
      { title: 'Entraînez-vous et échangez', text: 'Passez les quiz et les TP corrigés, écrivez à votre formateur et rejoignez ses réunions en visio.' }
    ],
    teacher: [
      { title: 'Créez votre cours', text: 'Organisez-le en chapitres et en leçons, puis ajoutez vos supports : un seul ou plusieurs par leçon.' },
      { title: 'Générez avec l\'IA', text: 'Choisissez le chapitre, les niveaux de difficulté et les sources : quiz et TP sont prêts en moins d\'une minute.' },
      { title: 'Animez et suivez', text: 'Planifiez vos réunions en visio, répondez aux messages et suivez la progression de chaque étudiant.' }
    ]
  };

  readonly stats = [
    { value: '3', label: 'espaces dédiés' },
    { value: '6', label: 'types de supports acceptés' },
    { value: '< 1 min', label: 'pour générer quiz et TP' },
    { value: 'Temps réel', label: 'messages et notifications' }
  ];

  readonly formats = [
    { icon: 'cilFile', label: 'Documents PDF' },
    { icon: 'cilMediaPlay', label: 'Vidéos et YouTube' },
    { icon: 'cilSpeech', label: 'Audios MP3' },
    { icon: 'cilExternalLink', label: 'Pages web' },
    { icon: 'cilListNumbered', label: 'Quiz QCM' },
    { icon: 'cilTask', label: 'TP corrigés' },
    { icon: 'cilLightbulb', label: 'Assistant IA' },
    { icon: 'cilVideo', label: 'Réunions en visio' },
    { icon: 'cilMediaRecord', label: 'Enregistrements' },
    { icon: 'cilChatBubble', label: 'Messagerie' },
    { icon: 'cilBell', label: 'Notifications' },
    { icon: 'cilStar', label: 'Avis et notes' },
    { icon: 'cilChartLine', label: 'Suivi de progression' },
    { icon: 'cilCreditCard', label: 'Paiement sécurisé' },
    { icon: 'cilCloudDownload', label: 'Export des résultats' },
    { icon: 'cilPeople', label: 'Gestion des rôles' },
    { icon: 'cilSpeedometer', label: 'Supervision' },
    { icon: 'cilUser', label: 'Profils avec photo' }
  ];

  readonly faqs = [
    {
      q: 'D\'où viennent les questions des quiz ?',
      a: 'Uniquement des supports ajoutés au chapitre : PDF, transcription des vidéos et des audios, pages web. L\'enseignant choisit même les sources à utiliser.'
    },
    {
      q: 'Comment se passent les réunions en visio ?',
      a: 'L\'enseignant planifie la réunion (date, sujet) : les étudiants inscrits sont notifiés, puis reçoivent un rappel 1 h avant. Dans la salle : caméra, micro, partage d\'écran et chat, directement dans le navigateur.'
    },
    {
      q: 'Peut-on revoir une réunion ?',
      a: 'Oui : l\'enseignant ou un étudiant peut l\'enregistrer. La vidéo est ensuite disponible pour tous les participants dans l\'onglet « Enregistrements ».'
    },
    {
      q: 'Comment contacter mon formateur ?',
      a: 'Par la messagerie intégrée : l\'historique des conversations est conservé et chaque nouveau message arrive en temps réel, avec une notification.'
    },
    {
      q: 'Comment fonctionne le paiement des formations ?',
      a: 'Les formations payantes se règlent par carte via Stripe, un service sécurisé : la plateforme ne stocke jamais vos numéros de carte. L\'accès au cours est ouvert dès que le paiement est confirmé.'
    },
    {
      q: 'Que peut faire l\'assistant IA ?',
      a: 'Expliquer une notion, résumer un document ou une vidéo, répondre à vos questions sur le cours… et vous laisser télécharger ses réponses en PDF ou en texte.'
    },
    {
      q: 'L\'IA peut-elle se tromper ?',
      a: 'Oui, comme toute IA. Chaque question est livrée avec sa correction pour que l\'enseignant la relise avant de la partager avec ses étudiants.'
    },
    {
      q: 'Qui peut créer des cours ?',
      a: 'Les enseignants. Les étudiants consultent les formations, passent les quiz, donnent leur avis et utilisent l\'assistant IA ; les administrateurs gèrent la plateforme.'
    }
  ];

  constructor(public authService: AuthService, private router: Router) {}

  ngAfterViewInit(): void {
    this.setupReveal();
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  /** Fin de l'intro de marque : le logo est arrivé dans la barre de navigation. */
  finishIntro(): void {
    this.introDone.set(true);
    this.showIntro.set(false);
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

  private introAlreadySeen(): boolean {
    const seen = introPlayed || !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    introPlayed = true;
    return seen;
  }
}
