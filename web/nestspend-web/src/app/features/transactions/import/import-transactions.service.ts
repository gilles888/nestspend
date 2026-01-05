import { Injectable, signal } from '@angular/core';
import { TransactionsService } from '../../../core/api/services/transactions.service';
import { ClassificationService } from '../../../core/api/services/classification.service';
import { ClassificationRulesService } from '../../../core/api/services/classification-rules.service';
import { CategoriesService } from '../../../core/api/services/categories.service';
import { ImportTransactionItem } from '../../../core/api/models/import-transaction-item';
import { ImportTransactionResponse } from '../../../core/api/models/import-transaction-response';
import { TransactionToClassify } from '../../../core/api/models/transaction-to-classify';
import { CategoryResponse } from '../../../core/api/models/category-response';
import { BankParserFactory } from './parsers/bank-parser-factory';
import {
  NormalizedImportedTransaction,
  BankType,
  ParseResult,
  ImportWizardState,
  ImportStep,
  ClassificationSuggestionInfo,
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

  // Categories cache for displaying names
  private categoriesMap: Map<string, CategoryResponse> = new Map();
  
  // Track if we've already initialized default rules
  private defaultRulesInitialized = false;

  constructor(
    private transactionsService: TransactionsService,
    private classificationService: ClassificationService,
    private classificationRulesService: ClassificationRulesService,
    private categoriesService: CategoriesService,
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
    // Clear categories cache to force reload on next import
    this.categoriesMap.clear();
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

    // Apply classification suggestions to parsed transactions
    const transactionsWithSuggestions = await this.applyClassificationSuggestions(
      parseResult.transactions
    );

    const updatedParseResult = {
      ...parseResult,
      transactions: transactionsWithSuggestions,
    };

    this._state.update((s) => ({
      ...s,
      file,
      parseResult: updatedParseResult,
      bankType: parseResult.bankType,
      transactionsToImport: transactionsWithSuggestions.filter((t) => t.status !== 'ERROR'),
    }));

    return updatedParseResult;
  }

  /**
   * Initialize default classification rules if none exist.
   * This is called automatically before the first classification attempt.
   */
  private async initializeDefaultRulesIfNeeded(): Promise<void> {
    if (this.defaultRulesInitialized) {
      return; // Already checked this session
    }

    try {
      // Call the backend endpoint to initialize default rules if needed
      // Use the generated OpenAPI service for type-safety and correct URL
      const response = await this.classificationRulesService.initializeDefaultRules();
      
      if (response.rulesCreated && response.rulesCreated > 0) {
        console.log(`Initialized ${response.rulesCreated} default classification rules`);
      }
      
      this.defaultRulesInitialized = true;
    } catch (error) {
      // Non-blocking error - rules might already exist or endpoint not available
      console.warn('Could not initialize default rules:', error instanceof Error ? error.message : error);
      this.defaultRulesInitialized = true; // Don't retry
    }
  }

  /**
   * Apply classification suggestions to transactions.
   */
  private async applyClassificationSuggestions(
    transactions: NormalizedImportedTransaction[]
  ): Promise<NormalizedImportedTransaction[]> {
    // Always load categories first (needed for dropdown even without suggestions)
    await this.loadCategories();

    if (transactions.length === 0) {
      return transactions;
    }

    try {
      // Initialize default rules if this is the first classification attempt
      await this.initializeDefaultRulesIfNeeded();
      
      // Build request for classification API
      const transactionsToClassify: TransactionToClassify[] = transactions.map((t) => ({
        merchant: t.counterparty || undefined,
        communication: t.description || undefined,
        iban: t.iban || undefined,
        amount: t.amountCents,
        date: this.formatDate(t.importedDate),
      }));

      // Call classification API
      const response = await this.classificationService.suggest({
        body: { transactions: transactionsToClassify },
      });

      // Apply suggestions to transactions
      const suggestions = response.suggestions || [];
      return transactions.map((t, index) => {
        const suggestion = suggestions[index];
        if (suggestion && suggestion.categoryId) {
          const category = this.categoriesMap.get(suggestion.categoryId);
          return {
            ...t,
            suggestion: {
              categoryId: suggestion.categoryId,
              categoryName: category?.name,
              confidence: suggestion.confidence,
              confidenceLabel: suggestion.confidenceLabel,
              ruleId: suggestion.ruleId,
            } as ClassificationSuggestionInfo,
            // Pre-fill selected category with the suggestion (user can modify later)
            selectedCategoryId: suggestion.categoryId,
            selectedCategoryName: category?.name,
          };
        }
        return t;
      });
    } catch (error) {
      // Classification failures are non-blocking - import can proceed without suggestions
      // Log detailed error for debugging purposes
      console.warn('Auto-classification unavailable:', error instanceof Error ? error.message : error);
      // Return transactions without suggestions if classification fails
      return transactions;
    }
  }

  /**
   * Load categories and cache them.
   * @param forceReload Force reload even if already cached
   */
  async loadCategories(forceReload: boolean = false): Promise<void> {
    if (this.categoriesMap.size > 0 && !forceReload) {
      return; // Already loaded
    }

    try {
      const response = await this.categoriesService.getAllCategories$Response();
      let categories = response.body;
      // Handle Blob response (can happen with some ng-openapi-gen configurations)
      if (categories instanceof Blob) {
        const text = await categories.text();
        categories = JSON.parse(text);
      }
      this.categoriesMap.clear();
      const categoriesArray = Array.isArray(categories) ? categories : [];
      for (const cat of categoriesArray) {
        if (cat.id) {
          this.categoriesMap.set(cat.id, cat);
        }
      }
    } catch (error) {
      // Category loading failures are non-blocking - suggestions will show IDs instead of names
      console.warn('Failed to load categories for name display:', error instanceof Error ? error.message : error);
    }
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
      // Use selected category (may have been modified by user, or pre-filled from suggestion)
      categoryId: t.selectedCategoryId || undefined,
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
   * Update the selected category for a transaction.
   * @param transactionIndex Index of the transaction in the parsed list
   * @param categoryId Category ID to assign
   * @param categoryName Category name for display
   */
  updateTransactionCategory(transactionIndex: number, categoryId: string | null, categoryName: string | null): void {
    this._state.update((s) => {
      if (!s.parseResult) return s;

      const updatedTransactions = s.parseResult.transactions.map((t, idx) => {
        if (idx === transactionIndex) {
          return {
            ...t,
            selectedCategoryId: categoryId || undefined,
            selectedCategoryName: categoryName || undefined,
          };
        }
        return t;
      });

      const updatedParseResult = {
        ...s.parseResult,
        transactions: updatedTransactions,
      };

      return {
        ...s,
        parseResult: updatedParseResult,
        transactionsToImport: updatedTransactions.filter((t) => t.status !== 'ERROR'),
      };
    });
  }

  /**
   * Get all available categories (for dropdown).
   */
  getCategories(): CategoryResponse[] {
    return Array.from(this.categoriesMap.values());
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
    categorized: number;
    highConfidence: number;
    mediumConfidence: number;
    lowConfidence: number;
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

    // Classification statistics
    const categorized = transactions.filter((t) => t.suggestion?.categoryId).length;
    const highConfidence = transactions.filter(
      (t) => t.suggestion?.confidenceLabel === 'HIGH'
    ).length;
    const mediumConfidence = transactions.filter(
      (t) => t.suggestion?.confidenceLabel === 'MEDIUM'
    ).length;
    const lowConfidence = transactions.filter(
      (t) => t.suggestion?.confidenceLabel === 'LOW'
    ).length;

    return {
      total: transactions.length,
      ok,
      warnings,
      errors,
      duplicates,
      categorized,
      highConfidence,
      mediumConfidence,
      lowConfidence,
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
