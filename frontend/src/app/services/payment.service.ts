import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

// Aucune clé Stripe côté Angular : le backend crée la session Checkout et renvoie son URL
export interface CheckoutResponse {
  checkoutUrl: string;
  sessionId: string;
  paymentId: number;
}

export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED' | 'CANCELLED' | 'REFUNDED';

export interface PaymentStatusView {
  paymentId: number;
  status: PaymentStatus;
  courseId: number;
  courseTitle: string;
  amount: number;
  currency: string;
  enrolled: boolean;
  failureMessage?: string | null;
}

export interface PaymentView {
  id: number;
  status: PaymentStatus;
  courseId: number;
  courseTitle: string;
  studentId: number;
  studentName: string;
  studentEmail: string;
  teacherId: number;
  teacherName: string;
  amount: number;
  teacherAmount?: number | null;
  platformCommission?: number | null;
  currency: string;
  stripeSessionId?: string;
  createdAt: string;
  paidAt?: string | null;
}

export interface PaymentConfig {
  enabled: boolean;
  currency: string;
  testMode: boolean;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getConfig(): Observable<PaymentConfig> {
    return this.http.get<PaymentConfig>(`${this.apiUrl}/api/payments/config`);
  }

  // Le prix n'est pas envoyé : le backend le relit en base
  createCheckout(courseId: number): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.apiUrl}/api/payments/checkout/${courseId}`, {});
  }

  getSessionStatus(sessionId: string): Observable<PaymentStatusView> {
    return this.http.get<PaymentStatusView>(`${this.apiUrl}/api/payments/session/${encodeURIComponent(sessionId)}`);
  }

  cancelSession(sessionId: string): Observable<PaymentStatusView> {
    return this.http.post<PaymentStatusView>(`${this.apiUrl}/api/payments/session/${encodeURIComponent(sessionId)}/cancel`, {});
  }

  getMyPayments(): Observable<PaymentView[]> {
    return this.http.get<PaymentView[]>(`${this.apiUrl}/api/payments/my`);
  }
}
