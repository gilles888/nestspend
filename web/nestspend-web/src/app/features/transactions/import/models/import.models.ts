/**
 * Classification suggestion for a transaction
 */
export interface ClassificationSuggestionInfo {
  /** Suggested category ID */
  categoryId?: string;
  /** Suggested category name */
  categoryName?: string;
  /** Confidence score (0-100) */
  confidence?: number;
  /** Confidence label */
  confidenceLabel?: 'HIGH' | 'MEDIUM' | 'LOW';
  /** Rule ID that matched */
  ruleId?: string;
}

/**
 * Represents a single transaction row parsed from a bank file.
 * This is the normalized internal structure before converting to API format.
 */
export interface NormalizedImportedTransaction {
  /** Transaction date */
  importedDate: Date;
  /** Amount in cents - positive value */
  amountCents: number;
  /** Transaction type determined from amount sign or file data */
  type: 'EXPENSE' | 'INCOME';
  /** Counterparty name / merchant */
  counterparty: string;
  /** Communication / description from bank statement */
  description: string;
  /** Counterparty IBAN if available */
  iban?: string;
  /** External reference ID from bank */
  externalId?: string;
  /** Original raw line for debugging */
  rawLine: string;
  /** Validation status */
  status: 'OK' | 'WARNING' | 'ERROR';
  /** Error/warning message if any */
  errorMessage?: string;
  /** Classification suggestion from auto-categorization */
  suggestion?: ClassificationSuggestionInfo;
}

/**
 * Supported bank types for import
 */
export type BankType = 'BELFIUS' | 'KEYTRADE' | 'ING' | 'UNKNOWN';

/**
 * Result from parsing a bank file
 */
export interface ParseResult {
  /** Detected or specified bank type */
  bankType: BankType;
  /** Parsed transactions */
  transactions: NormalizedImportedTransaction[];
  /** Total number of lines processed */
  totalLines: number;
  /** Number of lines successfully parsed */
  successfulLines: number;
  /** Number of lines with errors */
  errorLines: number;
  /** Raw preview of first lines (for display) */
  rawPreview: string[];
}

/**
 * Import wizard step
 */
export type ImportStep = 'bank-account' | 'upload' | 'preview' | 'deduplication' | 'result';

/**
 * Import wizard state
 */
export interface ImportWizardState {
  currentStep: ImportStep;
  bankType: BankType | null;
  accountId: string | null;
  file: File | null;
  parseResult: ParseResult | null;
  existingKeys: Set<string>;
  transactionsToImport: NormalizedImportedTransaction[];
}
