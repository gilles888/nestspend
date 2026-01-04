import { Injectable, signal } from '@angular/core';
import { TransactionsService } from '../../../core/api/services/transactions.service';
import { ImportTransactionItem } from '../../../core/api/models/import-transaction-item';
import { ImportTransactionResponse } from '../../../core/api/models/import-transaction-response';
import { BankParserFactory } from './parsers/bank-parser-factory';
import {
  NormalizedImportedTransaction,
  BankType,
  ParseResult,
  ImportWizardState,
  ImportStep,
} from './models/import.models';

/**
 * Encodings to try when reading bank files.
 * Belgian banks often use Windows-1252 (cp1252) encoding.
 */
const ENCODINGS_TO_TRY = ['UTF-8', 'windows-1252', 'ISO-8859-1'];

/**
 * Service for orchestrating the import wizard workflow.
 */
@Injectable({
  providedIn: 'root',
})
export class ImportTransactionsService {
  // Wizard state
  private readonly _state = signal<ImportWizardState>({
    currentStep: 'bank-account',
    bankType: null,
    accountId: null,
    file: null,
    parseResult: null,
    existingKeys: new Set(),
    transactionsToImport: [],
  });

  readonly state = this._state.asReadonly();

  constructor(
    private transactionsService: TransactionsService,
    private parserFactory: BankParserFactory
  ) {}

  /**
   * Reset the wizard state.
   */
  reset(): void {
    this._state.set({
      currentStep: 'bank-account',
      bankType: null,
      accountId: null,
      file: null,
      parseResult: null,
      existingKeys: new Set(),
      transactionsToImport: [],
    });
  }

  /**
   * Set the current step.
   */
  setStep(step: ImportStep): void {
    this._state.update((s) => ({ ...s, currentStep: step }));
  }

  /**
   * Set the selected bank type.
   */
  setBankType(bankType: BankType): void {
    this._state.update((s) => ({ ...s, bankType }));
  }

  /**
   * Set the selected account ID.
   */
  setAccountId(accountId: string): void {
    this._state.update((s) => ({ ...s, accountId }));
  }

  /**
   * Parse a file and update the state.
   * @param file File to parse
   * @returns Promise that resolves when parsing is complete
   */
  async parseFile(file: File): Promise<ParseResult> {
    const state = this._state();
    const fileContent = await this.readFileWithEncodingDetection(file);

    // Detect or use specified bank type
    const detectedBankType =
      state.bankType || this.parserFactory.detectBankType(file.name, file.type, fileContent);

    const parseResult = this.parserFactory.parseFile(
      file.name,
      file.type,
      fileContent,
      detectedBankType !== 'UNKNOWN' ? detectedBankType : undefined
    );

    this._state.update((s) => ({
      ...s,
      file,
      parseResult,
      bankType: parseResult.bankType,
      transactionsToImport: parseResult.transactions.filter((t) => t.status !== 'ERROR'),
    }));

    return parseResult;
  }

  /**
   * Check for duplicates against existing transactions.
   */
  async checkDuplicates(): Promise<void> {
    const state = this._state();
    if (!state.accountId || !state.parseResult) {
      return;
    }

    const transactions = state.parseResult.transactions.filter((t) => t.status !== 'ERROR');
    if (transactions.length === 0) {
      return;
    }

    // Calculate date range
    const dates = transactions.map((t) => t.importedDate);
    const minDate = new Date(Math.min(...dates.map((d) => d.getTime())));
    const maxDate = new Date(Math.max(...dates.map((d) => d.getTime())));

    // Build deduplication keys
    const keys = transactions.map((t) => this.buildDeduplicationKey(t));

    try {
      const response = await this.transactionsService.checkExisting({
        body: {
          accountId: state.accountId,
          from: this.formatDate(minDate),
          to: this.formatDate(maxDate),
          keys,
        },
      });

      const existingKeys = new Set(response.existingKeys || []);

      // Mark transactions as duplicates
      const updatedTransactions = transactions.map((t) => {
        const key = this.buildDeduplicationKey(t);
        if (existingKeys.has(key)) {
          return {
            ...t,
            status: 'WARNING' as const,
            errorMessage: 'Duplicate - already exists',
          };
        }
        return t;
      });

      this._state.update((s) => ({
        ...s,
        existingKeys,
        transactionsToImport: updatedTransactions.filter(
          (t) => t.status === 'OK' || t.status === 'WARNING'
        ),
      }));
    } catch (error) {
      console.error('Error checking duplicates:', error);
      // If check fails, proceed without deduplication
    }
  }

  /**
   * Execute the import of selected transactions.
   * @param skipDuplicates Whether to skip duplicate transactions
   * @returns Import response
   */
  async executeImport(skipDuplicates: boolean = true): Promise<ImportTransactionResponse> {
    const state = this._state();
    if (!state.accountId) {
      throw new Error('Account ID is required');
    }

    let transactionsToImport = state.transactionsToImport;
    if (skipDuplicates) {
      transactionsToImport = transactionsToImport.filter((t) => t.status !== 'WARNING');
    }

    const items: ImportTransactionItem[] = transactionsToImport.map((t) => ({
      txDate: this.formatDate(t.importedDate),
      type: t.type,
      amountCents: t.amountCents,
      merchant: t.counterparty || undefined,
      note: t.description || undefined,
      counterpartyIban: t.iban || undefined,
      externalId: t.externalId || undefined,
    }));

    const response = await this.transactionsService.importTransactions({
      body: {
        accountId: state.accountId,
        items,
      },
    });

    return response;
  }

  /**
   * Get statistics about the parsed transactions.
   */
  getParseStats(): {
    total: number;
    ok: number;
    warnings: number;
    errors: number;
    duplicates: number;
  } {
    const state = this._state();
    const transactions = state.parseResult?.transactions || [];
    const existingKeys = state.existingKeys;

    const ok = transactions.filter((t) => t.status === 'OK').length;
    const warnings = transactions.filter((t) => t.status === 'WARNING').length;
    const errors = transactions.filter((t) => t.status === 'ERROR').length;
    const duplicates = transactions.filter((t) => {
      const key = this.buildDeduplicationKey(t);
      return existingKeys.has(key);
    }).length;

    return {
      total: transactions.length,
      ok,
      warnings,
      errors,
      duplicates,
    };
  }

  /**
   * Build a deduplication key for a transaction.
   */
  private buildDeduplicationKey(transaction: NormalizedImportedTransaction): string {
    const date = this.formatDate(transaction.importedDate);
    const merchant = (transaction.counterparty || '').toLowerCase().trim();
    return `${date}|${transaction.amountCents}|${merchant}`;
  }

  /**
   * Format a date as YYYY-MM-DD.
   */
  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /**
   * Read file content with encoding detection.
   * Tries multiple encodings to find one that works without errors.
   * Belgian bank files often use Windows-1252 encoding.
   */
  private async readFileWithEncodingDetection(file: File): Promise<string> {
    // Try each encoding
    for (const encoding of ENCODINGS_TO_TRY) {
      try {
        const content = await this.readFileWithEncoding(file, encoding);
        // Check if content looks valid (no replacement characters)
        if (!content.includes('\uFFFD')) {
          return content;
        }
      } catch {
        // Try next encoding
        continue;
      }
    }

    // Fallback to UTF-8 if nothing else works
    return this.readFileWithEncoding(file, 'UTF-8');
  }

  /**
   * Read file content with a specific encoding.
   */
  private readFileWithEncoding(file: File, encoding: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = () => reject(reader.error);
      reader.readAsText(file, encoding);
    });
  }
}
