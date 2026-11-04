import { Component, ElementRef, OnDestroy, OnInit, ViewChild, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpEventType } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, filter, firstValueFrom } from 'rxjs';
import { IconDirective } from '@coreui/icons-angular';
import { ChatLine, MeetingService, MeetingView, Participant } from '../../services/meeting.service';
import { RealtimeService } from '../../services/realtime.service';
import { AvatarComponent } from '../../shared/avatar.component';
import { SrcObjectDirective } from '../../shared/src-object.directive';

/** Participant distant affiché dans la grille. */
interface PeerTile extends Participant {
  stream: MediaStream | null;
  connection: RTCPeerConnectionState | 'new';
}

/** Connexion WebRTC avec un participant. */
interface PeerLink {
  pc: RTCPeerConnection;
  audio?: RTCRtpSender;
  video?: RTCRtpSender;
  remote: MediaStream;
  pending: RTCIceCandidateInit[];
}

/**
 * Salle de réunion en visio (WebRTC) : caméra, micro, partage d'écran, chat, participants
 * et enregistrement (vidéo composée de tous les participants + son mixé, envoyée au serveur à la fin).
 * La vidéo circule directement entre navigateurs ; le serveur ne relaie que la signalisation (STOMP).
 */
@Component({
  selector: 'app-meeting-room',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, IconDirective, AvatarComponent, SrcObjectDirective],
  templateUrl: './meeting-room.component.html',
  styleUrl: './meeting-room.component.scss'
})
export class MeetingRoomComponent implements OnInit, OnDestroy {
  meetingId = 0;
  meeting = signal<MeetingView | null>(null);
  error = signal('');
  mediaWarning = signal('');
  notice = signal('');
  joined = signal(false);

  self = signal<Participant | null>(null);
  peers = signal<PeerTile[]>([]);
  localStream = signal<MediaStream | null>(null);
  micOn = signal(false);
  camOn = signal(false);
  sharing = signal(false);

  recording = signal(false);
  recordSeconds = signal(0);
  uploadProgress = signal<number | null>(null);

  chatOpen = signal(true);
  peopleOpen = signal(false);
  messages = signal<ChatLine[]>([]);
  unread = signal(0);
  draft = signal('');
  clock = signal('');

  /** Personnes qui enregistrent en ce moment (bandeau visible par tous). */
  readonly recorders = computed(() => {
    const names = this.peers().filter(p => p.recording).map(p => p.name);
    if (this.recording()) names.unshift('vous');
    return names;
  });
  readonly tileCount = computed(() => this.peers().length + 1);

  @ViewChild('stage') private stage?: ElementRef<HTMLElement>;
  @ViewChild('chatList') private chatList?: ElementRef<HTMLElement>;

  private cameraStream: MediaStream | null = null;
  private screenStream: MediaStream | null = null;
  private links = new Map<number, PeerLink>();
  private iceServers: RTCIceServer[] = [];
  private subs: Subscription[] = [];
  private timers: ReturnType<typeof setInterval>[] = [];

  // Enregistrement
  private recorder: MediaRecorder | null = null;
  private chunks: Blob[] = [];
  private drawTimer: ReturnType<typeof setInterval> | null = null;
  private audioCtx: AudioContext | null = null;
  private audioDest: MediaStreamAudioDestinationNode | null = null;
  private mixedTracks = new Set<string>();
  private recordStartedAt = 0;

  private readonly unloadHandler = () => this.realtime.publish('/app/meeting.leave', { meetingId: this.meetingId });

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private meetings: MeetingService,
    private realtime: RealtimeService
  ) {}

  ngOnInit(): void {
    this.meetingId = Number(this.route.snapshot.paramMap.get('id'));
    this.meetings.get(this.meetingId).subscribe({
      next: m => {
        this.meeting.set(m);
        if (!m.canJoin) {
          this.error.set(m.status === 'ENDED'
            ? 'Cette réunion est terminée.'
            : `La salle ouvre 15 minutes avant le début (${new Date(m.joinOpensAt).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })}).`);
          return;
        }
        void this.start();
      },
      error: err => this.error.set(err.error?.message || 'Réunion introuvable ou accès refusé.')
    });
  }

  ngOnDestroy(): void {
    if (this.recorder && this.recorder.state !== 'inactive') {
      // Sortie brutale (changement de page) : l'enregistrement en cours est quand même envoyé
      void this.stopRecording();
    }
    this.teardown();
  }

  // ── Démarrage ───────────────────────────────────────────────

  private async start(): Promise<void> {
    try {
      const servers = await firstValueFrom(this.meetings.iceServers());
      this.iceServers = servers.map(s => ({ urls: s.urls, ...(s.username ? { username: s.username, credential: s.credential ?? '' } : {}) }));
    } catch {
      this.iceServers = [{ urls: 'stun:stun.l.google.com:19302' }];
    }
    await this.openMedia();

    this.subs.push(this.realtime.meeting$.pipe(filter(e => e?.meetingId === this.meetingId)).subscribe(e => void this.onEvent(e)));
    this.subs.push(this.realtime.connects$.subscribe(() => this.sendJoin()));
    this.realtime.start();
    if (this.realtime.connected()) this.sendJoin();

    window.addEventListener('beforeunload', this.unloadHandler);
    this.timers.push(setInterval(() => this.tickClock(), 1000));
    this.tickClock();
  }

  /** Caméra + micro ; à défaut micro seul ; à défaut, simple spectateur (chat, écoute). */
  private async openMedia(): Promise<void> {
    const audio = { echoCancellation: true, noiseSuppression: true, autoGainControl: true };
    try {
      this.cameraStream = await navigator.mediaDevices.getUserMedia({ audio, video: { width: { ideal: 640 }, height: { ideal: 360 } } });
    } catch {
      try {
        this.cameraStream = await navigator.mediaDevices.getUserMedia({ audio });
        this.mediaWarning.set('Caméra indisponible : vous participez avec le micro uniquement.');
      } catch {
        this.cameraStream = null;
        this.mediaWarning.set('Caméra et micro indisponibles (autorisation refusée ?) : vous pouvez voir, écouter et écrire dans le chat.');
      }
    }
    this.micOn.set(!!this.audioTrack());
    this.camOn.set(!!this.cameraVideoTrack());
    this.localStream.set(this.cameraStream);
  }

  private sendJoin(): void {
    // Reconnexion : on repart de zéro, la salle du serveur nous a oubliés
    this.closeAllLinks();
    this.peers.set([]);
    this.joined.set(false);
    this.realtime.publish('/app/meeting.join', { meetingId: this.meetingId });
  }

  // ── Événements de la salle ──────────────────────────────────

  private async onEvent(e: any): Promise<void> {
    switch (e.type) {
      case 'welcome':
        this.joined.set(true);
        this.error.set('');
        this.self.set(e.self);
        this.messages.set(e.chat ?? []);
        this.peers.set((e.peers as Participant[]).map(p => ({ ...p, stream: null, connection: 'new' as const })));
        this.sendState();
        // Le nouvel arrivant appelle chacun des participants déjà présents
        for (const p of e.peers as Participant[]) await this.call(p.userId);
        this.scrollChat();
        break;
      case 'peer-joined':
        this.upsertPeer(e.peer);
        break;
      case 'peer-left':
        this.closeLink(e.userId);
        this.peers.update(list => list.filter(p => p.userId !== e.userId));
        break;
      case 'signal':
        await this.onSignal(e.from, e.kind, e.data);
        break;
      case 'state':
        if (e.peer.userId === this.self()?.userId) this.self.set(e.peer);
        else this.upsertPeer(e.peer);
        break;
      case 'chat':
        this.messages.update(list => [...list, e.message]);
        if (!this.chatOpen() && e.message.userId !== this.self()?.userId) this.unread.update(n => n + 1);
        this.scrollChat();
        break;
      case 'error':
        this.error.set(e.message);
        break;
    }
  }

  private upsertPeer(p: Participant): void {
    this.peers.update(list => {
      const existing = list.find(x => x.userId === p.userId);
      return existing
        ? list.map(x => (x.userId === p.userId ? { ...x, ...p } : x))
        : [...list, { ...p, stream: null, connection: 'new' as const }];
    });
  }

  // ── WebRTC ──────────────────────────────────────────────────

  private link(userId: number): PeerLink {
    const existing = this.links.get(userId);
    if (existing) return existing;
    const pc = new RTCPeerConnection({ iceServers: this.iceServers });
    const entry: PeerLink = { pc, remote: new MediaStream(), pending: [] };
    pc.onicecandidate = ev => { if (ev.candidate) this.signal(userId, 'ice', ev.candidate.toJSON()); };
    pc.ontrack = ev => {
      if (!entry.remote.getTracks().includes(ev.track)) entry.remote.addTrack(ev.track);
      const stream = new MediaStream(entry.remote.getTracks());
      this.peers.update(list => list.map(p => (p.userId === userId ? { ...p, stream } : p)));
      if (this.recording() && ev.track.kind === 'audio') this.mixTrack(ev.track);
    };
    pc.onconnectionstatechange = () => {
      this.peers.update(list => list.map(p => (p.userId === userId ? { ...p, connection: pc.connectionState } : p)));
    };
    this.links.set(userId, entry);
    return entry;
  }

  /** Appel sortant : offre avec une piste audio et une piste vidéo (remplaçables sans renégociation). */
  private async call(userId: number): Promise<void> {
    const entry = this.link(userId);
    entry.audio = entry.pc.addTransceiver(this.audioTrack() ?? 'audio', { direction: 'sendrecv' }).sender;
    entry.video = entry.pc.addTransceiver(this.outgoingVideoTrack() ?? 'video', { direction: 'sendrecv' }).sender;
    const offer = await entry.pc.createOffer();
    await entry.pc.setLocalDescription(offer);
    this.signal(userId, 'offer', entry.pc.localDescription?.toJSON());
  }

  private async onSignal(from: number, kind: string, data: any): Promise<void> {
    try {
      if (kind === 'offer') {
        const entry = this.link(from);
        await entry.pc.setRemoteDescription(data);
        // Nos pistes sont attachées aux transceivers créés par l'offre
        for (const tx of entry.pc.getTransceivers()) {
          const trackKind = tx.receiver.track.kind;
          if (trackKind === 'audio' && !entry.audio) {
            tx.direction = 'sendrecv';
            await tx.sender.replaceTrack(this.audioTrack());
            entry.audio = tx.sender;
          } else if (trackKind === 'video' && !entry.video) {
            tx.direction = 'sendrecv';
            await tx.sender.replaceTrack(this.outgoingVideoTrack());
            entry.video = tx.sender;
          }
        }
        const answer = await entry.pc.createAnswer();
        await entry.pc.setLocalDescription(answer);
        this.signal(from, 'answer', entry.pc.localDescription?.toJSON());
        await this.flushCandidates(entry);
      } else if (kind === 'answer') {
        const entry = this.links.get(from);
        if (!entry) return;
        await entry.pc.setRemoteDescription(data);
        await this.flushCandidates(entry);
      } else if (kind === 'ice') {
        const entry = this.link(from);
        if (entry.pc.remoteDescription) await entry.pc.addIceCandidate(data);
        else entry.pending.push(data);
      }
    } catch (err) {
      console.warn('Signalisation WebRTC', kind, err);
    }
  }

  private async flushCandidates(entry: PeerLink): Promise<void> {
    for (const c of entry.pending.splice(0)) {
      try { await entry.pc.addIceCandidate(c); } catch { /* candidat expiré */ }
    }
  }

  private signal(to: number, kind: string, data: unknown): void {
    this.realtime.publish('/app/meeting.signal', { meetingId: this.meetingId, to, kind, data });
  }

  private closeLink(userId: number): void {
    const entry = this.links.get(userId);
    if (!entry) return;
    entry.pc.close();
    this.links.delete(userId);
  }

  private closeAllLinks(): void {
    [...this.links.keys()].forEach(id => this.closeLink(id));
  }

  // ── Commandes ───────────────────────────────────────────────

  toggleMic(): void {
    const track = this.audioTrack();
    if (!track) {
      this.mediaWarning.set('Aucun micro disponible.');
      return;
    }
    track.enabled = !track.enabled;
    this.micOn.set(track.enabled);
    this.sendState();
  }

  toggleCam(): void {
    const track = this.cameraVideoTrack();
    if (!track) {
      this.mediaWarning.set('Aucune caméra disponible.');
      return;
    }
    track.enabled = !track.enabled;
    this.camOn.set(track.enabled);
    this.sendState();
  }

  async toggleShare(): Promise<void> {
    if (this.sharing()) {
      await this.stopShare();
      return;
    }
    try {
      this.screenStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
    } catch {
      return; // partage refusé par l'utilisateur
    }
    const track = this.screenStream.getVideoTracks()[0];
    track.onended = () => void this.stopShare();
    for (const entry of this.links.values()) await entry.video?.replaceTrack(track);
    this.localStream.set(this.screenStream);
    this.sharing.set(true);
    this.sendState();
  }

  private async stopShare(): Promise<void> {
    if (!this.sharing()) return;
    this.screenStream?.getTracks().forEach(t => t.stop());
    this.screenStream = null;
    for (const entry of this.links.values()) await entry.video?.replaceTrack(this.cameraVideoTrack());
    this.localStream.set(this.cameraStream);
    this.sharing.set(false);
    this.sendState();
  }

  toggleRecord(): void {
    if (this.recording()) void this.stopRecording();
    else this.startRecording();
  }

  toggleChat(): void {
    this.chatOpen.update(v => !v);
    this.peopleOpen.set(false);
    if (this.chatOpen()) {
      this.unread.set(0);
      this.scrollChat();
    }
  }

  togglePeople(): void {
    this.peopleOpen.update(v => !v);
    if (this.peopleOpen()) this.chatOpen.set(false);
  }

  sendChat(): void {
    const text = this.draft().trim();
    if (!text) return;
    if (this.realtime.publish('/app/meeting.chat', { meetingId: this.meetingId, text })) this.draft.set('');
  }

  onChatKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendChat();
    }
  }

  async leave(): Promise<void> {
    if (this.recording()) await this.stopRecording();
    this.teardown();
    void this.router.navigate(['/meetings'], { queryParams: this.recordingSaved ? { tab: 'recordings' } : {} });
  }

  private sendState(): void {
    this.realtime.publish('/app/meeting.state', {
      meetingId: this.meetingId, audio: this.micOn(), video: this.camOn() || this.sharing(),
      screen: this.sharing(), recording: this.recording()
    });
  }

  // ── Enregistrement : grille de toutes les vidéos + son de tous les participants ──

  private recordingSaved = false;

  private startRecording(): void {
    if (typeof MediaRecorder === 'undefined') {
      this.error.set("L'enregistrement n'est pas pris en charge par ce navigateur (utilisez Chrome, Edge ou Firefox).");
      return;
    }
    const canvas = document.createElement('canvas');
    canvas.width = 1280;
    canvas.height = 720;
    const ctx = canvas.getContext('2d')!;
    this.drawTimer = setInterval(() => this.drawFrame(ctx, canvas), 1000 / 20);

    this.audioCtx = new AudioContext();
    this.audioDest = this.audioCtx.createMediaStreamDestination();
    this.mixedTracks.clear();
    const local = this.audioTrack();
    if (local) this.mixTrack(local);
    for (const entry of this.links.values()) entry.remote.getAudioTracks().forEach(t => this.mixTrack(t));

    const stream = new MediaStream([...canvas.captureStream(20).getVideoTracks(), ...this.audioDest.stream.getAudioTracks()]);
    const mimeType = ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'].find(t => MediaRecorder.isTypeSupported(t));
    this.recorder = new MediaRecorder(stream, { ...(mimeType ? { mimeType } : {}), videoBitsPerSecond: 1_500_000 });
    this.chunks = [];
    this.recorder.ondataavailable = ev => { if (ev.data.size > 0) this.chunks.push(ev.data); };
    this.recorder.start(1000);
    this.recordStartedAt = Date.now();
    this.recordSeconds.set(0);
    this.recording.set(true);
    this.timers.push(setInterval(() => {
      if (this.recording()) this.recordSeconds.set(Math.floor((Date.now() - this.recordStartedAt) / 1000));
    }, 1000));
    this.sendState();
  }

  /** Arrêt : la vidéo est assemblée puis envoyée au serveur (barre de progression). */
  private stopRecording(): Promise<void> {
    const recorder = this.recorder;
    if (!recorder || recorder.state === 'inactive') return Promise.resolve();
    this.recording.set(false);
    this.sendState();
    return new Promise(resolve => {
      recorder.onstop = () => {
        if (this.drawTimer) clearInterval(this.drawTimer);
        this.drawTimer = null;
        void this.audioCtx?.close();
        this.audioCtx = null;
        this.audioDest = null;
        this.recorder = null;
        const blob = new Blob(this.chunks, { type: 'video/webm' });
        const duration = (Date.now() - this.recordStartedAt) / 1000;
        this.chunks = [];
        this.upload(blob, duration).then(resolve);
      };
      recorder.stop();
    });
  }

  private upload(blob: Blob, duration: number): Promise<void> {
    return new Promise(resolve => {
      this.uploadProgress.set(0);
      this.meetings.uploadRecording(this.meetingId, blob, duration).subscribe({
        next: ev => {
          if (ev.type === HttpEventType.UploadProgress && ev.total) this.uploadProgress.set(Math.round(ev.loaded * 100 / ev.total));
          if (ev.type === HttpEventType.Response) {
            this.uploadProgress.set(null);
            this.recordingSaved = true;
            this.notice.set('Enregistrement sauvegardé : retrouvez-le dans Réunions › Enregistrements.');
            resolve();
          }
        },
        error: err => {
          this.uploadProgress.set(null);
          this.error.set(err.error?.message || "L'enregistrement n'a pas pu être envoyé.");
          resolve();
        }
      });
    });
  }

  private mixTrack(track: MediaStreamTrack): void {
    if (!this.audioCtx || !this.audioDest || this.mixedTracks.has(track.id)) return;
    this.mixedTracks.add(track.id);
    this.audioCtx.createMediaStreamSource(new MediaStream([track])).connect(this.audioDest);
  }

  /** Une image de l'enregistrement : toutes les tuiles de la salle en grille, avec les noms. */
  private drawFrame(ctx: CanvasRenderingContext2D, canvas: HTMLCanvasElement): void {
    const tiles = Array.from(this.stage?.nativeElement.querySelectorAll<HTMLElement>('.tile') ?? []);
    ctx.fillStyle = '#0f172a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    if (!tiles.length) return;
    const cols = Math.ceil(Math.sqrt(tiles.length));
    const rows = Math.ceil(tiles.length / cols);
    const gap = 8;
    const w = (canvas.width - gap * (cols + 1)) / cols;
    const h = (canvas.height - gap * (rows + 1)) / rows;
    tiles.forEach((tile, i) => {
      const x = gap + (i % cols) * (w + gap);
      const y = gap + Math.floor(i / cols) * (h + gap);
      ctx.fillStyle = '#1e293b';
      ctx.fillRect(x, y, w, h);
      const video = tile.querySelector('video');
      const showVideo = video && tile.dataset['video'] === 'on' && video.videoWidth > 0;
      if (showVideo) {
        const scale = Math.max(w / video!.videoWidth, h / video!.videoHeight);
        const vw = video!.videoWidth * scale;
        const vh = video!.videoHeight * scale;
        ctx.save();
        ctx.beginPath();
        ctx.rect(x, y, w, h);
        ctx.clip();
        ctx.drawImage(video!, x + (w - vw) / 2, y + (h - vh) / 2, vw, vh);
        ctx.restore();
      } else {
        ctx.fillStyle = '#4f46e5';
        ctx.beginPath();
        ctx.arc(x + w / 2, y + h / 2, Math.min(w, h) / 6, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = '#ffffff';
        ctx.font = `bold ${Math.round(Math.min(w, h) / 8)}px sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(tile.dataset['initials'] || '?', x + w / 2, y + h / 2);
      }
      const name = tile.dataset['name'] || '';
      ctx.font = '600 20px sans-serif';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'alphabetic';
      const labelWidth = ctx.measureText(name).width + 20;
      ctx.fillStyle = 'rgba(0,0,0,0.55)';
      ctx.fillRect(x + 10, y + h - 40, labelWidth, 30);
      ctx.fillStyle = '#ffffff';
      ctx.fillText(name, x + 20, y + h - 18);
    });
  }

  // ── Outils ──────────────────────────────────────────────────

  private audioTrack(): MediaStreamTrack | null {
    return this.cameraStream?.getAudioTracks()[0] ?? null;
  }

  private cameraVideoTrack(): MediaStreamTrack | null {
    return this.cameraStream?.getVideoTracks()[0] ?? null;
  }

  private outgoingVideoTrack(): MediaStreamTrack | null {
    return this.screenStream?.getVideoTracks()[0] ?? this.cameraVideoTrack();
  }

  initials(name: string | null | undefined): string {
    return (name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('') || '?';
  }

  formatSeconds(total: number): string {
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    return (h ? `${h}:` : '') + `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }

  private tickClock(): void {
    const m = this.meeting();
    if (!m) return;
    const diff = Math.floor((Date.now() - new Date(m.startAt).getTime()) / 1000);
    this.clock.set(diff < 0 ? `Début dans ${this.formatSeconds(-diff)}` : `En cours depuis ${this.formatSeconds(diff)}`);
  }

  private scrollChat(): void {
    setTimeout(() => {
      const el = this.chatList?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  private teardown(): void {
    window.removeEventListener('beforeunload', this.unloadHandler);
    if (this.joined()) this.realtime.publish('/app/meeting.leave', { meetingId: this.meetingId });
    this.joined.set(false);
    this.closeAllLinks();
    this.cameraStream?.getTracks().forEach(t => t.stop());
    this.screenStream?.getTracks().forEach(t => t.stop());
    this.cameraStream = null;
    this.screenStream = null;
    this.timers.forEach(t => clearInterval(t));
    this.timers = [];
    this.subs.forEach(s => s.unsubscribe());
    this.subs = [];
  }
}
