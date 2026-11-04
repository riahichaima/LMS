import { AfterViewInit, Component, ElementRef, OnDestroy, inject, input, output, viewChild } from '@angular/core';
import { gsap } from 'gsap';

interface Orbit {
  radius: number;   // en % de la taille de la scène
  duration: number; // secondes par tour
  reverse: boolean;
  dots: { angle: number; size: number; star: boolean }[];
}

/**
 * Intro de marque : fond bleu nuit, traits lumineux, particules en orbite, révélation du logo LMS
 * puis du slogan lettre par lettre ; à la fin, le logo rejoint sa place dans la barre de navigation.
 * Timeline GSAP : tout est arrêté et nettoyé à la destruction (gsap.context().revert()).
 */
@Component({
  selector: 'app-brand-intro',
  standalone: true,
  templateUrl: './brand-intro.component.html',
  styleUrl: './brand-intro.component.scss'
})
export class BrandIntroComponent implements AfterViewInit, OnDestroy {
  logoSrc = input.required<string>();
  slogan = input.required<string>();
  /** Logo de la barre de navigation : destination finale du logo animé. */
  target = input<HTMLElement | null>(null);
  /** La page d'accueil peut s'afficher (début de la transition finale). */
  reveal = output<void>();
  /** Intro terminée : le composant peut être retiré. */
  finished = output<void>();

  private host = inject<ElementRef<HTMLElement>>(ElementRef);
  private logo = viewChild.required<ElementRef<HTMLImageElement>>('logo');
  private ctx?: gsap.Context;
  private tl?: gsap.core.Timeline;
  private revealed = false;
  private done = false;

  /** Orbites générées une fois (positions fixes : pas de recalcul pendant l'animation). */
  readonly orbits: Orbit[] = [
    { radius: 33, duration: 16, reverse: false, dots: this.dots(7, 0) },
    { radius: 41, duration: 24, reverse: true, dots: this.dots(9, 20) },
    { radius: 49, duration: 34, reverse: false, dots: this.dots(6, 45) }
  ];

  /** Lettres du slogan regroupées par mot (un mot n'est jamais coupé sur mobile). */
  words(): string[][] {
    return this.slogan().split(' ').map(w => Array.from(w));
  }

  ngAfterViewInit(): void {
    const root = this.host.nativeElement;
    this.ctx = gsap.context(() => this.build(root), root);
  }

  ngOnDestroy(): void {
    this.ctx?.revert();
  }

  /** Clic ou touche : on passe directement à la transition finale. */
  skip(): void {
    if (!this.tl || this.done) return;
    const exit = this.tl.labels['exit'];
    if (this.tl.time() < exit) this.tl.seek(exit);
  }

  private build(root: HTMLElement): void {
    const q = gsap.utils.selector(root);
    const letters = q('.bi-letter');
    const ringLength = 2 * Math.PI * 47; // circonférence du cercle SVG (r = 47)
    // Le slogan entier apparaît en ~1 s, quelle que soit sa longueur
    const stagger = Math.min(.028, 1 / Math.max(letters.length, 1));

    // États de départ
    gsap.set(q('.bi-lines'), { opacity: 0 });
    gsap.set(q('.bi-trail'), { strokeDashoffset: 0 });
    gsap.set(q('.bi-orbit'), { opacity: 0, scale: .7 });
    gsap.set(q('.bi-ring-draw'), { strokeDasharray: ringLength, strokeDashoffset: ringLength });
    gsap.set(q('.bi-disc'), { opacity: 0, scale: .55 });
    gsap.set(q('.bi-glow'), { opacity: 0, scale: .6 });
    gsap.set(this.logo().nativeElement, { opacity: 0, scale: .82, clipPath: 'circle(0% at 50% 50%)' });
    gsap.set(letters, { opacity: 0, y: 18, scale: .82 });
    gsap.set(q('.bi-flash'), { opacity: 0, scale: .2 });

    // Mouvements continus (tués avec le contexte)
    q('.bi-orbit').forEach((el, i) => {
      const o = this.orbits[i];
      gsap.to(el, { rotation: o.reverse ? -360 : 360, duration: o.duration, ease: 'none', repeat: -1 });
    });
    gsap.to(q('.bi-star i'), { opacity: .25, scale: .6, duration: 1.1, ease: 'sine.inOut', repeat: -1, yoyo: true, stagger: { each: .17, from: 'random' } });
    // Le halo (1er élément) avance avec la 1re traînée
    gsap.to(q('.bi-trail'), { strokeDashoffset: -1000, duration: 6, ease: 'none', repeat: -1, delay: (i: number) => Math.max(0, i - 1) * .8 });
    gsap.to(q('.bi-float'), { y: -8, duration: 2.4, ease: 'sine.inOut', repeat: -1, yoyo: true });

    // Séquence
    const tl = gsap.timeline({ defaults: { ease: 'power3.out' } });
    this.tl = tl;
    tl.fromTo(q('.bi-backdrop'), { opacity: .6 }, { opacity: 1, duration: .6, ease: 'power1.out' }, 0)
      // 0,3 s — traits lumineux
      .to(q('.bi-lines'), { opacity: 1, duration: 1.2, ease: 'power2.out' }, .3)
      .fromTo(q('.bi-streak'), { xPercent: -120, opacity: 0 }, { xPercent: 120, opacity: 1, duration: 1.6, ease: 'power2.inOut', stagger: .35 }, .3)
      // 0,6 s — particules et étoiles
      .to(q('.bi-orbit'), { opacity: 1, scale: 1, duration: 1.1, stagger: .12, ease: 'expo.out' }, .6)
      // 1,0 s — révélation du logo
      .to(q('.bi-disc'), { opacity: 1, scale: 1, duration: .8, ease: 'back.out(1.4)' }, 1)
      .to(q('.bi-ring-draw'), { strokeDashoffset: 0, duration: .9, ease: 'power2.inOut' }, 1)
      .to(this.logo().nativeElement, { opacity: 1, scale: 1, clipPath: 'circle(75% at 50% 50%)', duration: .8, ease: 'power2.out' }, 1)
      // 1,8 s — logo visible, halo
      .to(q('.bi-glow'), { opacity: 1, scale: 1, duration: .7, ease: 'sine.out' }, 1.6)
      .fromTo(q('.bi-sheen'), { xPercent: -160 }, { xPercent: 160, duration: .9, ease: 'power2.inOut' }, 1.8)
      // 2,0 s — slogan lettre par lettre
      .to(letters, {
        opacity: 1, y: 0, scale: 1, duration: .45, ease: 'back.out(2)', stagger
      }, 2)
      // Petit éclat de lumière sur chaque lettre au moment où elle apparaît
      .fromTo(letters, { color: '#ffffff' }, { color: '#eaf3ff', duration: .5, ease: 'sine.out', stagger }, 2)
      .addLabel('exit', 3.5)
      // 3,5 s — éclair de lumière, la page apparaît, le logo rejoint la barre
      .to(q('.bi-flash'), { opacity: .85, scale: 1.6, duration: .35, ease: 'power2.out' }, 'exit')
      .call(() => this.emitReveal(), [], 'exit+=0.1')
      .add(() => this.flyToNavbar(tl), 'exit+=0.1')
      .to(q('.bi-flash'), { opacity: 0, scale: 3, duration: .7, ease: 'power2.in' }, 'exit+=0.35')
      .to(q('.bi-slogan, .bi-orbits, .bi-lines, .bi-streaks, .bi-disc, .bi-glow, .bi-ring'), { opacity: 0, duration: .45, ease: 'power2.in' }, 'exit+=0.1')
      .to(q('.bi-backdrop'), { opacity: 0, duration: .8, ease: 'power2.inOut' }, 'exit+=0.25')
      .call(() => this.finish(), [], 'exit+=1.1');
  }

  /** Le logo animé glisse jusqu'à la position et la taille du logo de la barre de navigation. */
  private flyToNavbar(tl: gsap.core.Timeline): void {
    const img = this.logo().nativeElement;
    const target = this.target();
    if (!target) {
      tl.to(img, { opacity: 0, scale: .6, duration: .5, ease: 'power2.in' }, tl.time());
      return;
    }
    const a = img.getBoundingClientRect();
    const b = target.getBoundingClientRect();
    const scale = b.height / a.height;
    tl.to(img, {
      x: `+=${b.left + b.width / 2 - (a.left + a.width / 2)}`,
      y: `+=${b.top + b.height / 2 - (a.top + a.height / 2)}`,
      scale: scale,
      duration: .9,
      ease: 'power3.inOut'
    }, tl.time());
  }

  private emitReveal(): void {
    if (this.revealed) return;
    this.revealed = true;
    this.reveal.emit();
  }

  private finish(): void {
    if (this.done) return;
    this.done = true;
    this.emitReveal();
    this.finished.emit();
  }

  /** Points répartis sur une orbite, quelques-uns en étoiles (scintillement). */
  private dots(count: number, offset: number): Orbit['dots'] {
    return Array.from({ length: count }, (_, i) => ({
      angle: offset + (360 / count) * i + ((i * 37) % 17),
      size: 2 + ((i * 7) % 4),
      star: i % 3 === 0
    }));
  }
}
