import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, output, signal, viewChild } from '@angular/core';

/**
 * Lecteur PDF intégré (pdf.js) : pages rendues au fil du défilement, et pourcentage réellement lu
 * (pages restées visibles au moins une seconde / nombre de pages) envoyé au parent.
 */
@Component({
  selector: 'app-pdf-viewer',
  standalone: true,
  template: `
    <div class="pdfv">
      <div class="pdfv__bar">
        <span>{{ total() ? 'Page ' + current() + ' / ' + total() : 'Chargement du PDF…' }}</span>
        @if (total()) {
          <span class="pdfv__read"><span class="pdfv__dot"></span>{{ readPercent() }} % lu</span>
          <span class="pdfv__zoom">
            <button type="button" (click)="zoom(-0.15)" aria-label="Dézoomer" title="Dézoomer">−</button>
            <button type="button" (click)="zoom(0.15)" aria-label="Zoomer" title="Zoomer">+</button>
          </span>
        }
      </div>
      @if (error()) { <div class="pdfv__error">Le PDF n'a pas pu être affiché.</div> }
      <div class="pdfv__pages" #scroller tabindex="0" aria-label="Pages du PDF"></div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .pdfv { border: 1px solid var(--lms-border, var(--cui-border-color)); border-radius: 12px; overflow: hidden; background: var(--cui-tertiary-bg); }
    .pdfv__bar { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; padding: .5rem .8rem; font-size: .82rem;
      background: var(--cui-body-bg); border-bottom: 1px solid var(--lms-border, var(--cui-border-color)); }
    .pdfv__read { display: inline-flex; align-items: center; gap: .35rem; font-weight: 600; color: var(--cui-primary); }
    .pdfv__dot { width: 7px; height: 7px; border-radius: 50%; background: var(--cui-primary); }
    .pdfv__zoom { margin-left: auto; display: inline-flex; gap: .25rem; }
    .pdfv__zoom button { width: 28px; height: 28px; border-radius: 8px; border: 1px solid var(--lms-border, var(--cui-border-color));
      background: var(--cui-body-bg); color: var(--cui-body-color); font-weight: 700; line-height: 1; }
    .pdfv__pages { height: min(72vh, 820px); overflow: auto; padding: 1rem; display: flex; flex-direction: column; align-items: center; gap: .9rem; }
    .pdfv__error { padding: 1rem; color: var(--cui-danger); }
    :host ::ng-deep .pdfv__page { position: relative; background: #fff; box-shadow: 0 2px 10px rgba(16, 38, 84, .12); border-radius: 4px; flex-shrink: 0; max-width: 100%; }
    :host ::ng-deep .pdfv__page canvas { display: block; width: 100%; height: 100%; border-radius: 4px; }
    :host ::ng-deep .pdfv__page--seen::after { content: '✓'; position: absolute; top: 6px; right: 8px; font-size: .75rem; color: var(--cui-success); }
  `]
})
export class PdfViewerComponent implements AfterViewInit, OnDestroy {
  /** URL (blob) du PDF déjà chargé avec le jeton */
  src = input.required<string>();
  /** Pourcentage déjà lu lors des visites précédentes (ne fait jamais reculer l'affichage) */
  initialPercent = input(0);
  /** Pourcentage lu (pages vues / total), émis à chaque nouvelle page vue */
  progress = output<number>();

  readonly total = signal(0);
  readonly current = signal(1);
  readonly readPercent = signal(0);
  readonly error = signal(false);

  private scroller = viewChild.required<ElementRef<HTMLDivElement>>('scroller');
  private pdf: any = null;
  private pages: HTMLDivElement[] = [];
  private rendered = new Set<number>();
  private seen = new Set<number>();
  private timers = new Map<number, ReturnType<typeof setTimeout>>();
  private observer?: IntersectionObserver;
  private scale = 1;
  private destroyed = false;
  private ready = false;

  constructor() {
    effect(() => {
      const url = this.src();
      if (this.ready && url) void this.open(url);
    });
  }

  ngAfterViewInit(): void {
    this.ready = true;
    void this.open(this.src());
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.observer?.disconnect();
    this.timers.forEach(t => clearTimeout(t));
    void this.pdf?.destroy?.();
  }

  zoom(delta: number): void {
    this.scale = Math.min(2.5, Math.max(0.6, this.scale + delta));
    void this.layout();
  }

  private async open(url: string): Promise<void> {
    try {
      const pdfjs: any = await import('pdfjs-dist');
      pdfjs.GlobalWorkerOptions.workerSrc = 'assets/pdfjs/pdf.worker.min.mjs';
      this.pdf = await pdfjs.getDocument({ url }).promise;
      if (this.destroyed) return;
      this.total.set(this.pdf.numPages);
      this.readPercent.set(Math.max(0, Math.min(100, this.initialPercent())));
      await this.layout();
    } catch (e) {
      // Cause la plus fréquente : le worker assets/pdfjs/pdf.worker.min.mjs n'est pas servi
      // (ng serve lancé avant l'ajout de l'asset dans angular.json → relancer ng serve)
      console.error('Lecteur PDF :', e);
      this.error.set(true);
    }
  }

  /** Une case par page, à la bonne taille ; le contenu n'est dessiné qu'à l'approche de l'écran. */
  private async layout(): Promise<void> {
    if (!this.pdf) return;
    const box = this.scroller().nativeElement;
    const first = await this.pdf.getPage(1);
    const base = first.getViewport({ scale: 1 });
    const width = Math.min(box.clientWidth - 32, 980) * this.scale;
    const height = width * base.height / base.width;

    this.observer?.disconnect();
    box.innerHTML = '';
    this.pages = [];
    this.rendered.clear();
    for (let i = 1; i <= this.pdf.numPages; i++) {
      const page = document.createElement('div');
      page.className = 'pdfv__page' + (this.seen.has(i) ? ' pdfv__page--seen' : '');
      page.style.width = `${width}px`;
      page.style.height = `${height}px`;
      page.dataset['page'] = String(i);
      box.appendChild(page);
      this.pages.push(page);
    }

    this.observer = new IntersectionObserver(entries => {
      for (const e of entries) {
        const n = Number((e.target as HTMLElement).dataset['page']);
        if (e.isIntersecting) {
          void this.render(n, width);
          if (e.intersectionRatio >= 0.5) {
            this.current.set(n);
            // Page comptée comme lue si elle reste à l'écran au moins une seconde
            if (!this.seen.has(n) && !this.timers.has(n)) this.timers.set(n, setTimeout(() => this.markSeen(n), 1000));
          }
        } else if (this.timers.has(n)) {
          clearTimeout(this.timers.get(n)!);
          this.timers.delete(n);
        }
      }
    }, { root: box, rootMargin: '300px 0px', threshold: [0, 0.5] });
    this.pages.forEach(p => this.observer!.observe(p));
  }

  private async render(n: number, width: number): Promise<void> {
    if (this.rendered.has(n) || !this.pdf) return;
    this.rendered.add(n);
    try {
      const page = await this.pdf.getPage(n);
      const unscaled = page.getViewport({ scale: 1 });
      const ratio = window.devicePixelRatio || 1;
      const viewport = page.getViewport({ scale: (width / unscaled.width) * ratio });
      const canvas = document.createElement('canvas');
      canvas.width = Math.floor(viewport.width);
      canvas.height = Math.floor(viewport.height);
      await page.render({ canvas, canvasContext: canvas.getContext('2d')!, viewport }).promise;
      if (!this.destroyed) this.pages[n - 1]?.appendChild(canvas);
    } catch {
      this.rendered.delete(n);
    }
  }

  private markSeen(n: number): void {
    this.timers.delete(n);
    if (this.seen.has(n) || this.destroyed) return;
    this.seen.add(n);
    this.pages[n - 1]?.classList.add('pdfv__page--seen');
    const percent = Math.round(this.seen.size * 100 / Math.max(1, this.total()));
    if (percent > this.readPercent()) this.readPercent.set(percent);
    this.progress.emit(percent);
  }
}
