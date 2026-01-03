import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { SelectModule } from 'primeng/select';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

import { TransactionsService } from '../../core/api/services/transactions.service';
import { CategoriesService } from '../../core/api/services/categories.service';
import { AccountsService } from '../../core/api/services/accounts.service';
import { TransactionResponse } from '../../core/api/models/transaction-response';
import { CategoryResponse } from '../../core/api/models/category-response';
import { AccountResponse } from '../../core/api/models/account-response';

@Component({
  selector: 'app-transactions',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    DatePipe,
    FormsModule,
    ReactiveFormsModule,
    TranslateModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    DatePickerModule,
    InputNumberModule,
    ConfirmDialogModule,
    ToastModule,
    TagModule,
    TooltipModule,
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './transactions.html',
  styleUrl: './transactions.scss',
})
export class TransactionsComponent implements OnInit {
  loading = signal(false);
  transactions = signal<TransactionResponse[]>([]);
  categories = signal<CategoryResponse[]>([]);
  accounts = signal<AccountResponse[]>([]);

  // Dialog state
  dialogVisible = signal(false);
  dialogMode = signal<'add' | 'edit'>('add');
  selectedTransaction = signal<TransactionResponse | null>(null);

  // Filter state
  filterFrom = signal<Date | null>(null);
  filterTo = signal<Date | null>(null);
  filterType = signal<string | null>(null);
  filterCategoryId = signal<string | null>(null);

  // Form
  transactionForm!: FormGroup;

  constructor(
    private transactionsService: TransactionsService,
    private categoriesService: CategoriesService,
    private accountsService: AccountsService,
    private confirmationService: ConfirmationService,
    private messageService: MessageService,
    private translateService: TranslateService,
    private fb: FormBuilder
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    this.loadData();
  }

  private initForm(): void {
    this.transactionForm = this.fb.group({
      txDate: [new Date(), Validators.required],
      type: ['EXPENSE', Validators.required],
      amountCents: [0, [Validators.required, Validators.min(1)]],
      categoryId: ['', Validators.required],
      accountId: ['', Validators.required],
      merchant: [''],
      note: [''],
    });
  }

  async loadData(): Promise<void> {
    this.loading.set(true);
    try {
      const [transactionsResponse, categoriesResponse, accountsResponse] = await Promise.all([
        this.loadTransactions(),
        this.categoriesService.getAllCategories$Response(),
        this.accountsService.getAllAccounts$Response(),
      ]);

      // Handle Blob responses from OpenAPI spec
      let categories = categoriesResponse.body;
      if (categories instanceof Blob) {
        const text = await categories.text();
        categories = JSON.parse(text);
      }

      let accounts = accountsResponse.body;
      if (accounts instanceof Blob) {
        const text = await accounts.text();
        accounts = JSON.parse(text);
      }

      this.transactions.set(transactionsResponse);
      this.categories.set(Array.isArray(categories) ? categories : []);
      this.accounts.set(Array.isArray(accounts) ? accounts : []);
    } catch (error) {
      console.error('Error loading data:', error);
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to load data',
      });
    } finally {
      this.loading.set(false);
    }
  }

  private async loadTransactions(): Promise<TransactionResponse[]> {
    const params: {
      from?: string;
      to?: string;
      type?: 'EXPENSE' | 'INCOME';
      categoryId?: string;
    } = {};

    if (this.filterFrom()) {
      params.from = this.formatDateForApi(this.filterFrom()!);
    }
    if (this.filterTo()) {
      params.to = this.formatDateForApi(this.filterTo()!);
    }
    if (this.filterType()) {
      params.type = this.filterType() as 'EXPENSE' | 'INCOME';
    }
    if (this.filterCategoryId()) {
      params.categoryId = this.filterCategoryId()!;
    }

    const response = await this.transactionsService.getAllTransactions$Response(params);
    let transactions = response.body;

    // Handle Blob response from OpenAPI spec
    if (transactions instanceof Blob) {
      const text = await transactions.text();
      transactions = JSON.parse(text);
    }

    return Array.isArray(transactions) ? transactions : [];
  }

  async applyFilters(): Promise<void> {
    this.loading.set(true);
    try {
      const transactions = await this.loadTransactions();
      this.transactions.set(transactions);
    } catch (error) {
      console.error('Error applying filters:', error);
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to apply filters',
      });
    } finally {
      this.loading.set(false);
    }
  }

  async clearFilters(): Promise<void> {
    this.filterFrom.set(null);
    this.filterTo.set(null);
    this.filterType.set(null);
    this.filterCategoryId.set(null);
    await this.applyFilters();
  }

  openAddDialog(): void {
    this.dialogMode.set('add');
    this.selectedTransaction.set(null);
    this.transactionForm.reset({
      txDate: new Date(),
      type: 'EXPENSE',
      amountCents: 0,
      categoryId: '',
      accountId: '',
      merchant: '',
      note: '',
    });
    this.dialogVisible.set(true);
  }

  openEditDialog(transaction: TransactionResponse): void {
    this.dialogMode.set('edit');
    this.selectedTransaction.set(transaction);
    this.transactionForm.patchValue({
      txDate: transaction.txDate ? new Date(transaction.txDate) : new Date(),
      type: transaction.type,
      amountCents: (transaction.amountCents ?? 0) / 100,
      categoryId: transaction.categoryId,
      accountId: transaction.accountId,
      merchant: transaction.merchant ?? '',
      note: transaction.note ?? '',
    });
    this.dialogVisible.set(true);
  }

  closeDialog(): void {
    this.dialogVisible.set(false);
    this.selectedTransaction.set(null);
  }

  async saveTransaction(): Promise<void> {
    if (this.transactionForm.invalid) {
      this.transactionForm.markAllAsTouched();
      return;
    }

    const formValue = this.transactionForm.value;
    const txDate = this.formatDateForApi(formValue.txDate);

    const body = {
      txDate,
      type: formValue.type as 'EXPENSE' | 'INCOME',
      amountCents: Math.round(formValue.amountCents * 100),
      categoryId: formValue.categoryId,
      accountId: formValue.accountId,
      merchant: formValue.merchant || undefined,
      note: formValue.note || undefined,
    };

    try {
      if (this.dialogMode() === 'add') {
        await this.transactionsService.createTransaction({ body });
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Transaction created successfully',
        });
      } else {
        const id = this.selectedTransaction()?.id;
        if (id) {
          await this.transactionsService.updateTransaction({ id, body });
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: 'Transaction updated successfully',
          });
        }
      }

      this.closeDialog();
      await this.applyFilters();
    } catch (error) {
      console.error('Error saving transaction:', error);
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to save transaction',
      });
    }
  }

  confirmDelete(transaction: TransactionResponse): void {
    this.confirmationService.confirm({
      message: 'Are you sure you want to delete this transaction?',
      header: 'Confirm Deletion',
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteTransaction(transaction),
    });
  }

  private async deleteTransaction(transaction: TransactionResponse): Promise<void> {
    try {
      if (transaction.id) {
        await this.transactionsService.deleteTransaction({ id: transaction.id });
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Transaction deleted successfully',
        });
        await this.applyFilters();
      }
    } catch (error) {
      console.error('Error deleting transaction:', error);
      this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to delete transaction',
      });
    }
  }

  formatCentsToAmount(cents: number | undefined): number {
    return (cents ?? 0) / 100;
  }

  private formatDateForApi(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  getTypeSeverity(type: string | undefined): 'success' | 'danger' {
    return type === 'INCOME' ? 'success' : 'danger';
  }

  getCategoryOptions(): { label: string; value: string }[] {
    return this.categories().map((c) => ({
      label: c.name ?? '',
      value: c.id ?? '',
    }));
  }

  getAccountOptions(): { label: string; value: string }[] {
    return this.accounts().map((a) => ({
      label: a.name ?? '',
      value: a.id ?? '',
    }));
  }

  getFilterCategoryOptions(): { label: string; value: string | null }[] {
    return [
      { label: this.translateService.instant('transactions.allCategories'), value: null },
      ...this.categories().map((c) => ({
        label: c.name ?? '',
        value: c.id ?? '',
      })),
    ];
  }

  getFilterTypeOptions(): { label: string; value: string | null }[] {
    return [
      { label: this.translateService.instant('transactions.allTypes'), value: null },
      { label: this.translateService.instant('transactions.expense'), value: 'EXPENSE' },
      { label: this.translateService.instant('transactions.income'), value: 'INCOME' },
    ];
  }

  getTypeOptions(): { label: string; value: string }[] {
    return [
      { label: this.translateService.instant('transactions.expense'), value: 'EXPENSE' },
      { label: this.translateService.instant('transactions.income'), value: 'INCOME' },
    ];
  }
}
