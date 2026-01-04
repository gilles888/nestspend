import { Component, EventEmitter, Input, OnInit, Output, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { DialogModule } from 'primeng/dialog';
import { StepsModule } from 'primeng/steps';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { FileUploadModule } from 'primeng/fileupload';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { MessageModule } from 'primeng/message';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TooltipModule } from 'primeng/tooltip';
import { MenuItem, MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';

import { AccountsService } from '../../../core/api/services/accounts.service';
import { AccountResponse } from '../../../core/api/models/account-response';
import { ImportTransactionsService } from './import-transactions.service';
import { BankParserFactory } from './parsers/bank-parser-factory';
import { BankType, NormalizedImportedTransaction, ImportStep } from './models/import.models';
import { ImportTransactionResponse } from '../../../core/api/models/import-transaction-response';

@Component({
  selector: 'app-import-transactions-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CurrencyPipe,
    DatePipe,
    TranslateModule,
    DialogModule,
    StepsModule,
    ButtonModule,
    SelectModule,
    FileUploadModule,
    TableModule,
    TagModule,
    MessageModule,
    ProgressSpinnerModule,
    TooltipModule,
    ToastModule,
  ],
  providers: [MessageService],
  templateUrl: './import-transactions-dialog.html',
})
export class ImportTransactionsDialogComponent implements OnInit {
  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Output() importCompleted = new EventEmitter<void>();

  // State signals
  loading = signal(false);
  accounts = signal<AccountResponse[]>([]);
  selectedAccountId = signal<string | null>(null);
  selectedBankType = signal<BankType | null>(null);
  activeStepIndex = signal(0);
  importResult = signal<ImportTransactionResponse | null>(null);

  // Wizard steps configuration
  steps: MenuItem[] = [];

  // Computed values
  canProceedStep1 = computed(
    () => this.selectedAccountId() !== null && this.selectedBankType() !== null
  );
  canProceedStep2 = computed(
    () => this.importService.state().parseResult !== null
  );
  canProceedStep3 = computed(() => {
    const stats = this.importService.getParseStats();
    return stats.ok > 0 || stats.warnings > 0;
  });

  // File upload state
  uploadedFile = signal<File | null>(null);
  rawPreview = signal<string[]>([]);

  constructor(
    private accountsService: AccountsService,
    private translateService: TranslateService,
    private messageService: MessageService,
    public importService: ImportTransactionsService,
    private parserFactory: BankParserFactory
  ) {}

  ngOnInit(): void {
    this.initSteps();
    this.loadAccounts();
  }

  private initSteps(): void {
    this.steps = [
      { label: this.translateService.instant('import.step1Title') },
      { label: this.translateService.instant('import.step2Title') },
      { label: this.translateService.instant('import.step3Title') },
      { label: this.translateService.instant('import.step4Title') },
      { label: this.translateService.instant('import.step5Title') },
    ];
  }

  private async loadAccounts(): Promise<void> {
    try {
      const response = await this.accountsService.getAllAccounts$Response();
      let accounts = response.body;
      if (accounts instanceof Blob) {
        const text = await accounts.text();
        accounts = JSON.parse(text);
      }
      this.accounts.set(Array.isArray(accounts) ? accounts : []);
    } catch (error) {
      console.error('Error loading accounts:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error'),
        detail: this.translateService.instant('import.loadAccountsError'),
      });
    }
  }

  getAccountOptions(): { label: string; value: string }[] {
    return this.accounts().map((a) => ({
      label: a.name || '',
      value: a.id || '',
    }));
  }

  getBankTypeOptions(): { label: string; value: BankType }[] {
    return this.parserFactory.getAvailableBankTypes();
  }

  onAccountChange(accountId: string): void {
    this.selectedAccountId.set(accountId);
    this.importService.setAccountId(accountId);
  }

  onBankTypeChange(bankType: BankType): void {
    this.selectedBankType.set(bankType);
    this.importService.setBankType(bankType);
  }

  async onFileSelect(event: { files: File[] }): Promise<void> {
    if (event.files.length === 0) return;

    const file = event.files[0];
    this.uploadedFile.set(file);
    this.loading.set(true);

    try {
      const parseResult = await this.importService.parseFile(file);
      this.rawPreview.set(parseResult.rawPreview);

      // Auto-detect bank type if not already set
      if (!this.selectedBankType() && parseResult.bankType !== 'UNKNOWN') {
        this.selectedBankType.set(parseResult.bankType);
      }

      this.messageService.add({
        severity: 'success',
        summary: this.translateService.instant('common.success'),
        detail: this.translateService.instant('import.fileParseSuccess', {
          count: parseResult.successfulLines,
        }),
      });
    } catch (error) {
      console.error('Error parsing file:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error'),
        detail: this.translateService.instant('import.fileParseError'),
      });
    } finally {
      this.loading.set(false);
    }
  }

  async nextStep(): Promise<void> {
    const currentIndex = this.activeStepIndex();

    // Validate current step before proceeding
    if (currentIndex === 0 && !this.canProceedStep1()) {
      this.messageService.add({
        severity: 'warn',
        summary: this.translateService.instant('common.info'),
        detail: this.translateService.instant('import.selectBankAndAccount'),
      });
      return;
    }

    if (currentIndex === 1 && !this.canProceedStep2()) {
      this.messageService.add({
        severity: 'warn',
        summary: this.translateService.instant('common.info'),
        detail: this.translateService.instant('import.uploadFileFirst'),
      });
      return;
    }

    // Handle step-specific actions
    if (currentIndex === 2) {
      // Moving to deduplication step - check for duplicates
      this.loading.set(true);
      try {
        await this.importService.checkDuplicates();
      } catch (error) {
        console.error('Error checking duplicates:', error);
      } finally {
        this.loading.set(false);
      }
    }

    if (currentIndex === 3) {
      // Moving to final step - execute import
      await this.executeImport();
      return;
    }

    this.activeStepIndex.set(currentIndex + 1);
  }

  previousStep(): void {
    const currentIndex = this.activeStepIndex();
    if (currentIndex > 0) {
      this.activeStepIndex.set(currentIndex - 1);
    }
  }

  private async executeImport(): Promise<void> {
    this.loading.set(true);
    try {
      const result = await this.importService.executeImport(true);
      this.importResult.set(result);
      this.activeStepIndex.set(4); // Move to results step

      this.messageService.add({
        severity: 'success',
        summary: this.translateService.instant('common.success'),
        detail: this.translateService.instant('import.importSuccess', {
          created: result.createdCount,
          skipped: result.skippedCount,
        }),
      });
    } catch (error) {
      console.error('Error executing import:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error'),
        detail: this.translateService.instant('import.importError'),
      });
    } finally {
      this.loading.set(false);
    }
  }

  close(): void {
    this.importService.reset();
    this.uploadedFile.set(null);
    this.rawPreview.set([]);
    this.selectedAccountId.set(null);
    this.selectedBankType.set(null);
    this.activeStepIndex.set(0);
    this.importResult.set(null);
    this.visible = false;
    this.visibleChange.emit(false);
  }

  finish(): void {
    this.importCompleted.emit();
    this.close();
  }

  getStatusSeverity(status: string): 'success' | 'warn' | 'danger' {
    switch (status) {
      case 'OK':
        return 'success';
      case 'WARNING':
        return 'warn';
      case 'ERROR':
        return 'danger';
      default:
        return 'success';
    }
  }

  getTypeSeverity(type: string): 'success' | 'danger' {
    return type === 'INCOME' ? 'success' : 'danger';
  }

  formatCentsToAmount(cents: number): number {
    return cents / 100;
  }

  getParsedTransactions(): NormalizedImportedTransaction[] {
    return this.importService.state().parseResult?.transactions || [];
  }

  getTransactionsToImport(): NormalizedImportedTransaction[] {
    return this.importService.state().transactionsToImport || [];
  }

  getStats(): { total: number; ok: number; warnings: number; errors: number; duplicates: number } {
    return this.importService.getParseStats();
  }
}
