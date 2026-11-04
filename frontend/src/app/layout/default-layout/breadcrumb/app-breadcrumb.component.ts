import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { IconDirective } from '@coreui/icons-angular';
import { AuthService } from '../../../services/auth.service';

interface Crumb {
  label: string;
  url: string;
}

/**
 * Fil d'Ariane des espaces connectés : « Accueil » ramène au tableau de bord du rôle connecté
 * (espace super admin pour le super administrateur), puis les titres des pages traversées.
 */
@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  imports: [RouterLink, IconDirective],
  template: `
    <nav class="crumbs" aria-label="Fil d'Ariane">
      <ol>
        <li>
          <a [routerLink]="home()" class="crumbs__home" [class.crumbs__home--current]="crumbs().length === 0">
            <svg cIcon name="cilHome" size="sm"></svg><span>Accueil</span>
          </a>
        </li>
        @for (c of crumbs(); track c.url; let last = $last) {
          <li class="crumbs__sep" aria-hidden="true"><svg cIcon name="cilChevronRight" size="sm"></svg></li>
          <li>
            @if (last) {
              <span class="crumbs__current" aria-current="page">{{ c.label }}</span>
            } @else {
              <a [routerLink]="c.url">{{ c.label }}</a>
            }
          </li>
        }
      </ol>
    </nav>
  `,
  styles: [`
    :host { display: block; }
    .crumbs ol { display: flex; flex-wrap: wrap; align-items: center; gap: .15rem; margin: 0; padding: .55rem 0; list-style: none; font-size: .86rem; }
    .crumbs a { display: inline-flex; align-items: center; gap: .35rem; padding: .2rem .55rem; border-radius: 999px;
      color: var(--cui-secondary-color); text-decoration: none; transition: background .15s ease, color .15s ease; }
    .crumbs a:hover { color: var(--cui-primary); background: rgba(var(--cui-primary-rgb), .08); }
    .crumbs__home { font-weight: 500; }
    .crumbs__home--current { color: var(--cui-primary) !important; background: rgba(var(--cui-primary-rgb), .08); }
    .crumbs__sep { display: inline-flex; color: var(--cui-tertiary-color, #9aa4b8); }
    .crumbs__sep svg { width: 12px; height: 12px; }
    .crumbs__current { padding: .2rem .55rem; border-radius: 999px; font-weight: 600; color: var(--cui-emphasis-color);
      background: var(--cui-tertiary-bg); }
  `]
})
export class AppBreadcrumbComponent {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private auth = inject(AuthService);

  /** Accueil des espaces connectés : le tableau de bord du rôle. */
  home(): string {
    return this.auth.isSuperAdmin() ? '/super-admin' : '/dashboard';
  }

  readonly crumbs = toSignal(
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd),
      startWith(null),
      map(() => this.build(this.route.snapshot))
    ),
    { initialValue: [] as Crumb[] }
  );

  private build(root: ActivatedRouteSnapshot): Crumb[] {
    const crumbs: Crumb[] = [];
    const segments: string[] = [];
    let node: ActivatedRouteSnapshot | null = root;
    while (node) {
      segments.push(...node.url.map(s => s.path));
      const title = node.routeConfig?.data?.['title'] as string | undefined;
      const url = '/' + segments.join('/');
      // Le titre du cadre (« Accueil ») et la page d'accueil du rôle sont déjà représentés par le lien Accueil
      if (title && node.url.length > 0 && url !== this.home() && !crumbs.some(c => c.url === url)) {
        crumbs.push({ label: title, url });
      }
      node = node.firstChild;
    }
    return crumbs;
  }
}
