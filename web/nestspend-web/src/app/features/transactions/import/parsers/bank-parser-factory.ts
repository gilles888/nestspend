import { Injectable } from '@angular/core';
import { BankImportParser } from './bank-import-parser.interface';
import { BelfiusCsvParser } from './belfius-csv-parser';
import { BelfiusTxtParser } from './belfius-txt-parser';
import { KeytradeCsvParser } from './keytrade-csv-parser';
import { IngCsvParser } from './ing-csv-parser';
import { IngTxtParser } from './ing-txt-parser';
import { BankType, ParseResult, NormalizedImportedTransaction } from '../models/import.models';
import * as Papa from 'papaparse';
import {
  parseAmountFlexible,
  parseDateFlexible,
  detectDelimiter,
  findHeaderLineIndex,
  detectColumnsWithAliases,
} from './parse-utils';

/**
 * Factory for creating bank parsers and auto-detecting file format.
 * Enhanced with robust preamble and encoding support.
 */
@Injectable({
  providedIn: 'root',
})
export class BankParserFactory {
  private readonly parsers: BankImportParser[] = [
    new BelfiusCsvParser(),
    new BelfiusTxtParser(),
    new KeytradeCsvParser(),
    new IngCsvParser(),
    new IngTxtParser(),
  ];

  /**
   * Auto-detect the bank type from file content.
   * Checks more lines to handle files with preamble.
   * @param fileName File name
   * @param mimeType MIME type
   * @param fileContent File content (first portion for analysis)
   * @returns Detected bank type or 'UNKNOWN'
   */
  detectBankType(fileName: string, mimeType: string, fileContent: string): BankType {
    // Check more lines to handle preamble
    const firstLines = fileContent.split('\n').slice(0, 20);

    for (const parser of this.parsers) {
      if (parser.canHandle(fileName, mimeType, firstLines)) {
        return parser.getBankType();
      }
    }

    return 'UNKNOWN';
  }

  /**
   * Get the appropriate parser for a bank type.
   * @param bankType Bank type
   * @param fileName File name to determine parser variant
   * @returns Parser instance or null if not found
   */
  getParser(bankType: BankType, fileName: string): BankImportParser | null {
    const lowerFileName = fileName.toLowerCase();
    const isCsv = lowerFileName.endsWith('.csv');
    const isTxt = lowerFileName.endsWith('.txt');

    for (const parser of this.parsers) {
      if (parser.getBankType() === bankType) {
        // For CSV files, prefer CSV parsers; for TXT files, prefer TXT parsers
        const parserClass = parser.constructor.name.toLowerCase();
        if ((isCsv && parserClass.includes('csv')) || (isTxt && parserClass.includes('txt'))) {
          return parser;
        }
      }
    }

    // Fallback: return first matching parser for the bank type
    return this.parsers.find((p) => p.getBankType() === bankType) || null;
  }

  /**
   * Parse a file using auto-detection or specified bank type.
   * @param fileName File name
   * @param mimeType MIME type
   * @param fileContent File content
   * @param bankType Optional bank type override
   * @returns Parse result
   */
  parseFile(
    fileName: string,
    mimeType: string,
    fileContent: string,
    bankType?: BankType
  ): ParseResult {
    const detectedBankType = bankType || this.detectBankType(fileName, mimeType, fileContent);

    if (detectedBankType === 'UNKNOWN') {
      // Try generic CSV parsing with enhanced utilities
      return this.parseGenericCsv(fileContent);
    }

    const parser = this.getParser(detectedBankType, fileName);
    if (!parser) {
      return this.parseGenericCsv(fileContent);
    }

    return parser.parse(fileContent);
  }

  /**
   * Fallback generic CSV parser for unknown formats.
   * Enhanced with preamble detection and flexible parsing.
   */
  private parseGenericCsv(fileContent: string): ParseResult {
    const allLines = fileContent.split('\n');
    const rawPreview = allLines.slice(0, 15);
    const transactions: NormalizedImportedTransaction[] = [];
    let errorLines = 0;

    // Detect delimiter
    const delimiter = detectDelimiter(fileContent);

    // Find header line (skip preamble)
    const headerLineIndex = findHeaderLineIndex(allLines, delimiter);

    // Extract content from header
    const contentFromHeader = allLines.slice(headerLineIndex).join('\n');

    // Parse CSV
    const parseResult = Papa.parse<string[]>(contentFromHeader, {
      delimiter,
      skipEmptyLines: true,
    });

    const rows = parseResult.data;
    if (rows.length < 2) {
      return {
        bankType: 'UNKNOWN',
        transactions: [],
        totalLines: rows.length,
        successfulLines: 0,
        errorLines: 0,
        rawPreview,
      };
    }

    // Detect columns using aliases
    const headers = rows[0].map((h) => h.toLowerCase().trim());
    const columnMap = detectColumnsWithAliases(headers);

    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      if (row.length < 2 || row.every((cell) => !cell.trim())) {
        continue; // Skip empty rows
      }

      const rawLine = row.join(delimiter);

      try {
        const transaction = this.parseGenericRow(row, columnMap, rawLine);
        if (transaction) {
          transactions.push(transaction);
        } else {
          errorLines++;
        }
      } catch {
        errorLines++;
        transactions.push({
          importedDate: new Date(),
          amountCents: 0,
          type: 'EXPENSE',
          counterparty: '',
          description: '',
          rawLine,
          status: 'ERROR',
          errorMessage: 'Failed to parse row',
        });
      }
    }

    return {
      bankType: 'UNKNOWN',
      transactions,
      totalLines: rows.length - 1,
      successfulLines: transactions.filter((t) => t.status !== 'ERROR').length,
      errorLines,
      rawPreview,
    };
  }

  private parseGenericRow(
    row: string[],
    columnMap: Record<string, number>,
    rawLine: string
  ): NormalizedImportedTransaction | null {
    const dateStr = columnMap['date'] !== undefined ? row[columnMap['date']]?.trim() : '';
    const amountStr = columnMap['amount'] !== undefined ? row[columnMap['amount']]?.trim() : '';
    const counterparty =
      columnMap['counterparty'] !== undefined ? row[columnMap['counterparty']]?.trim() : '';
    const description =
      columnMap['description'] !== undefined ? row[columnMap['description']]?.trim() : '';

    // Use flexible date parser
    const date = parseDateFlexible(dateStr);
    if (!date) {
      return {
        importedDate: new Date(),
        amountCents: 0,
        type: 'EXPENSE',
        counterparty: counterparty || '',
        description: description || '',
        rawLine,
        status: 'ERROR',
        errorMessage: `Invalid date: ${dateStr}`,
      };
    }

    // Use flexible amount parser
    const amount = parseAmountFlexible(amountStr);
    if (amount === null) {
      return {
        importedDate: date,
        amountCents: 0,
        type: 'EXPENSE',
        counterparty: counterparty || '',
        description: description || '',
        rawLine,
        status: 'ERROR',
        errorMessage: `Invalid amount: ${amountStr}`,
      };
    }

    const type: 'EXPENSE' | 'INCOME' = amount < 0 ? 'EXPENSE' : 'INCOME';
    const amountCents = Math.abs(Math.round(amount * 100));

    return {
      importedDate: date,
      amountCents,
      type,
      counterparty: counterparty || '',
      description: description || '',
      rawLine,
      status: 'OK',
    };
  }

  /**
   * Get available bank types for manual selection.
   */
  getAvailableBankTypes(): { value: BankType; label: string }[] {
    return [
      { value: 'BELFIUS', label: 'Belfius' },
      { value: 'KEYTRADE', label: 'Keytrade' },
      { value: 'ING', label: 'ING' },
    ];
  }
}
