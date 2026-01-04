import { NormalizedImportedTransaction, BankType, ParseResult } from '../models/import.models';

/**
 * Interface for bank-specific file parsers.
 * Uses Strategy pattern to support different bank formats.
 */
export interface BankImportParser {
  /**
   * Get the bank type this parser handles
   */
  getBankType(): BankType;

  /**
   * Check if this parser can handle the given file based on filename, MIME type, and content.
   * @param fileName Name of the file
   * @param mimeType MIME type of the file
   * @param firstLines First few lines of the file content
   */
  canHandle(fileName: string, mimeType: string, firstLines: string[]): boolean;

  /**
   * Parse the file content and return normalized transactions.
   * @param fileContent Full file content as string
   */
  parse(fileContent: string): ParseResult;
}
