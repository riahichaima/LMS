import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PaymentView } from './payment.service';

// Ventes et portefeuilles VIRTUELS (simulation PFE) : 60 % enseignant, 40 % plateforme — montants en USD

export interface CourseSales {
  courseId: number;
  courseTitle: string;
  sales: number;
  revenue: number;
  earnings: number;
}

export interface TeacherEarningsView {
  totalSales: number;
  totalRevenue: number;
  totalEarnings: number;
  currency: string;
  salesByCourse: CourseSales[];
  recentSales: PaymentView[];
}

export interface FinanceSummary {
  paidPayments: number;
  grossRevenue: number;
  platformCommission: number;
  teacherEarnings: number;
  currency: string;
}

export interface TeacherWalletView {
  teacherId: number;
  teacherName: string;
  teacherEmail: string;
  sales: number;
  totalEarned: number;
}

export interface TransactionView {
  id: number;
  walletType: 'TEACHER' | 'PLATFORM';
  walletOwner: string;
  type: string;
  amount: number;
  availableAfter: number;
  paymentId?: number | null;
  description: string;
  createdAt: string;
}

export interface PlatformWalletView {
  totalCommission: number;
  totalTeacherEarnings: number;
  grossRevenue: number;
  paidPayments: number;
  currency: string;
}

@Injectable({ providedIn: 'root' })
export class FinanceService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  // ── Enseignant (ses propres revenus uniquement) ──
  getMyEarnings(): Observable<TeacherEarningsView> {
    return this.http.get<TeacherEarningsView>(`${this.apiUrl}/api/teacher/earnings`);
  }

  // ── Administrateur ──
  getSummary(): Observable<FinanceSummary> {
    return this.http.get<FinanceSummary>(`${this.apiUrl}/api/admin/finance/summary`);
  }

  getPayments(): Observable<PaymentView[]> {
    return this.http.get<PaymentView[]>(`${this.apiUrl}/api/admin/finance/payments`);
  }

  getTeacherWallets(): Observable<TeacherWalletView[]> {
    return this.http.get<TeacherWalletView[]>(`${this.apiUrl}/api/admin/finance/teacher-earnings`);
  }

  // ── Super administrateur ──
  getPlatformWallet(): Observable<PlatformWalletView> {
    return this.http.get<PlatformWalletView>(`${this.apiUrl}/api/super-admin/finance/platform-wallet`);
  }

  getTransactions(): Observable<TransactionView[]> {
    return this.http.get<TransactionView[]>(`${this.apiUrl}/api/super-admin/finance/transactions`);
  }
}
