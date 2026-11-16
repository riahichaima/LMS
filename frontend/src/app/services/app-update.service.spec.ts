import { TestBed } from '@angular/core/testing';
import { NavigationStart, Router } from '@angular/router';
import { SwUpdate, VersionEvent } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { AppUpdateService } from './app-update.service';
import swConfig from '../../../ngsw-config.json';

class FakeSwUpdate {
  isEnabled = true;
  versionUpdates = new Subject<VersionEvent>();
  unrecoverable = new Subject<{ type: 'UNRECOVERABLE_STATE'; reason: string }>();
  activateUpdate = vi.fn(() => Promise.resolve(true));
}

describe('AppUpdateService (application installable)', () => {
  let sw: FakeSwUpdate;
  let routerEvents: Subject<unknown>;
  let service: AppUpdateService;
  let reload: ReturnType<typeof vi.fn<() => void>>;

  beforeEach(() => {
    sw = new FakeSwUpdate();
    routerEvents = new Subject();
    TestBed.configureTestingModule({
      providers: [
        { provide: SwUpdate, useValue: sw },
        { provide: Router, useValue: { events: routerEvents } },
      ],
    });
    service = TestBed.inject(AppUpdateService);
    reload = vi.fn<() => void>();
    (service as unknown as { reload: () => void }).reload = reload;
    vi.spyOn(history, 'replaceState').mockImplementation(() => undefined);
    service.start();
  });

  afterEach(() => vi.restoreAllMocks());

  it('ne recharge jamais au milieu d\'une page quand une nouvelle version est prête', () => {
    sw.versionUpdates.next({ type: 'VERSION_READY' } as VersionEvent);
    expect(reload).not.toHaveBeenCalled();
    expect(sw.activateUpdate).not.toHaveBeenCalled();
  });

  it('applique la nouvelle version au changement de page suivant', async () => {
    sw.versionUpdates.next({ type: 'VERSION_READY' } as VersionEvent);
    routerEvents.next(new NavigationStart(1, '/student/courses'));
    await Promise.resolve();
    await Promise.resolve();
    expect(sw.activateUpdate).toHaveBeenCalledTimes(1);
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('change de page normalement s\'il n\'y a pas de nouvelle version', () => {
    routerEvents.next(new NavigationStart(1, '/dashboard'));
    expect(sw.activateUpdate).not.toHaveBeenCalled();
    expect(reload).not.toHaveBeenCalled();
  });

  it('recharge si le cache du service worker devient incohérent', () => {
    sw.unrecoverable.next({ type: 'UNRECOVERABLE_STATE', reason: 'test' });
    expect(reload).toHaveBeenCalledTimes(1);
  });
});

describe('ngsw-config.json (ce que l\'application garde en cache)', () => {
  const files = swConfig.assetGroups.flatMap((g) => g.resources.files);

  it('ne met jamais en cache la configuration env.js (adresse de l\'API, régénérée à chaque mise en ligne)', () => {
    expect(files).toContain('!/assets/env.js');
  });

  it('ne met jamais en cache les réponses de l\'API (données toujours fraîches)', () => {
    expect(swConfig.dataGroups).toEqual([]);
    expect(files.some((f) => /api|auth/i.test(f))).toBe(false);
  });

  it('garde la coquille de l\'application pour un démarrage rapide', () => {
    expect(files).toEqual(expect.arrayContaining(['/index.html', '/manifest.webmanifest']));
  });
});
