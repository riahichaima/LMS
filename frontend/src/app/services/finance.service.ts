import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PaymentView } from './payment.service';

// Portefeuilles et retraits VIRTUELS (simulation PFE) — montants en USD

export type WithdrawalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'PAID';

export interface WithdrawalView {
  id: number;
  teacherId: number;
  teacherName: string;
  teacherEmail: string;
  amount: number;
  status: WithdrawalStatus;
  requestedAt: string;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  adminNote?: string | null;
  transferredBy?: string | null;
  transferredAt?: string | null;
  transferReference?: string | null;
}

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
  availableBalance: number;
  pendingBalance: number;
  totalWithdrawn: number;
  minWithdrawal: number;
  currency: string;
  salesByCourse: CourseSales[];
  recentSales: PaymentView[];
  withdrawals: WithdrawalView[];
}

export interface FinanceSummary {
  paidPayments: number;
  grossRevenue: number;
  platformCommission: number;
  teacherEarnings: number;
  pendingWithdrawals: number;
  totalWithdrawn: number;
  currency: string;
}

export interface TeacherWalletView {
  teacherId: number;
  teacherName: string;
  teacherEmail: string;
  sales: number;
  totalEarned: number;
  availableBalance: number;
  pendingBalance: number;
  totalWithdrawn: number;
}

export interface TransactionView {
  id: number;
  walletType: 'TEACHER' | 'PLATFORM';
  walletOwner: string;
  type: string;
  amount: number;
  availableAfter: number;
  paymentId?: number | null;
  withdrawalId?: number | null;
  description: string;
  createdAt: string;
}

export interface PlatformWalletView {
  availableBalance: number;
  totalCommission: number;
  teacherFundsHeld: number;
  totalTeacherEarnings: number;
  totalPaidOut: number;
  grossRevenue: number;
  paidPayments: number;
  transfersToSimulate: number;
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

  requestWithdrawal(amount: number): Observable<WithdrawalView> {
    return this.http.post<WithdrawalView>(`${this.apiUrl}/api/teacher/withdrawals`, { amount });
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

  getWithdrawals(): Observable<WithdrawalView[]> {
    return this.http.get<WithdrawalView[]>(`${this.apiUrl}/api/admin/finance/withdrawals`);
  }

  approveWithdrawal(id: number, note?: string): Observable<WithdrawalView> {
    return this.http.put<WithdrawalView>(`${this.apiUrl}/api/admin/finance/withdrawals/${id}/approve`, { note: note || null });
  }

  rejectWithdrawal(id: number, note?: string): Observable<WithdrawalView> {
    return this.http.put<WithdrawalView>(`${this.apiUrl}/api/admin/finance/withdrawals/${id}/reject`, { note: note || null });
  }

  // ── Super administrateur ──
  getPlatformWallet(): Observable<PlatformWalletView> {
    return this.http.get<PlatformWalletView>(`${this.apiUrl}/api/super-admin/finance/platform-wallet`);
  }

  getTransactions(): Observable<TransactionView[]> {
    return this.http.get<TransactionView[]>(`${this.apiUrl}/api/super-admin/finance/transactions`);
  }

  getTransfers(): Observable<WithdrawalView[]> {
    return this.http.get<WithdrawalView[]>(`${this.apiUrl}/api/super-admin/finance/transfers`);
  }

  simulateTransfer(withdrawalId: number): Observable<WithdrawalView> {
    return this.http.post<WithdrawalView>(`${this.apiUrl}/api/super-admin/finance/transfers/${withdrawalId}/simulate`, {});
  }
}
