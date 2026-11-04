import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Réunions en visio : planification, salle (WebRTC), enregistrements. */

export type MeetingStatus = 'SCHEDULED' | 'LIVE' | 'ENDED';

export interface MeetingView {
  id: number;
  courseId: number;
  courseTitle: string;
  teacherId: number;
  teacherName: string;
  title: string;
  description: string | null;
  startAt: string;
  endAt: string;
  durationMinutes: number;
  status: MeetingStatus;
  canJoin: boolean;
  canEdit: boolean;
  participantsOnline: number;
  recordingCount: number;
  joinOpensAt: string;
}

export interface MeetingRequest {
  courseId: number;
  title: string;
  description: string | null;
  startAt: string; // ISO-8601 (UTC)
  durationMinutes: number;
}

export interface RecordingView {
  id: number;
  meetingId: number;
  meetingTitle: string;
  courseTitle: string;
  recordedById: number;
  recordedByName: string;
  sizeBytes: number;
  durationSeconds: number | null;
  mimeType: string;
  createdAt: string;
  canDelete: boolean;
}

export interface IceServerConfig {
  urls: string[];
  username: string | null;
  credential: string | null;
}

export interface Participant {
  userId: number;
  name: string;
  role: string;
  avatarUrl: string | null;
  audio: boolean;
  video: boolean;
  screen: boolean;
  recording: boolean;
}

export interface ChatLine {
  id: string;
  userId: number;
  name: string;
  text: string;
  at: string;
}

@Injectable({ providedIn: 'root' })
export class MeetingService {
  private base = `${environment.apiUrl}/api/meetings`;

  constructor(private http: HttpClient) {}

  list(): Observable<MeetingView[]> {
    return this.http.get<MeetingView[]>(this.base);
  }

  get(id: number): Observable<MeetingView> {
    return this.http.get<MeetingView>(`${this.base}/${id}`);
  }

  create(request: MeetingRequest): Observable<MeetingView> {
    return this.http.post<MeetingView>(this.base, request);
  }

  update(id: number, request: MeetingRequest): Observable<MeetingView> {
    return this.http.put<MeetingView>(`${this.base}/${id}`, request);
  }

  cancel(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  iceServers(): Observable<IceServerConfig[]> {
    return this.http.get<IceServerConfig[]>(`${this.base}/ice-servers`);
  }

  recordings(meetingId?: number): Observable<RecordingView[]> {
    return this.http.get<RecordingView[]>(`${this.base}/recordings`, { params: meetingId ? { meetingId } : {} });
  }

  /** Envoi avec suivi de progression (barre d'avancement). */
  uploadRecording(meetingId: number, blob: Blob, durationSeconds: number): Observable<HttpEvent<RecordingView>> {
    const form = new FormData();
    form.append('file', blob, `reunion-${meetingId}.webm`);
    form.append('durationSeconds', String(Math.round(durationSeconds)));
    return this.http.post<RecordingView>(`${this.base}/${meetingId}/recordings`, form, { reportProgress: true, observe: 'events' });
  }

  recordingFile(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/recordings/${id}/file`, { responseType: 'blob' });
  }

  deleteRecording(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/recordings/${id}`);
  }
}
