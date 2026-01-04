import { Injectable } from '@angular/core';
import { BankImportParser } from './bank-import-parser.interface';
import { BelfiusCsvParser } from './belfius-csv-parser';
import { BelfiusTxtParser } from './belfius-txt-parser';
import { KeytradeCsvParser } from './keytrade-csv-parser';
import { IngCsvParser } from './ing-csv-parser';
import { IngTxtParser } from './ing-txt-parser';
import { BankType, ParseResult, NormalizedImportedTransaction } from '../models/import.models';
import * as Papa from 'papaparse';

/**
 * Factory for creating bank parsers and auto-detecting file format.
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
   * @param fileName File name
   * @param mimeType MIME type
   * @param fileContent File content (first portion for analysis)
   * @returns Detected bank type or 'UNKNOWN'
   */
  detectBankType(fileName: string, mimeType: string, fileContent: string): BankType {
    const firstLines = fileContent.split('\n').slice(0, 5);

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
      // Try generic CSV parsing
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
   */
  private parseGenericCsv(fileContent: string): ParseResult {
    const rawPreview = fileContent.split('\n').slice(0, 10);
    const transactions: NormalizedImportedTransaction[] = [];
    let errorLines = 0;

    // Try different delimiters
    let parseResult = Papa.parse<string[]>(fileContent, {
      delimiter: ';',
      skipEmptyLines: true,
    });

    if (parseResult.data.length < 2) {
      parseResult = Papa.parse<string[]>(fileContent, {
        delimiter: ',',
        skipEmptyLines: true,
      });
    }

    if (parseResult.data.length < 2) {
      parseResult = Papa.parse<string[]>(fileContent, {
        delimiter: '\t',
        skipEmptyLines: true,
      });
    }

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

    // Try to detect column indices from headers
    const headers = rows[0].map((h) => h.toLowerCase().trim());
    const columnMap = this.detectGenericColumns(headers);

    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      const rawLine = row.join(';');

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

  private detectGenericColumns(headers: string[]): Record<string, number> {
    const map: Record<string, number> = {};

    headers.forEach((header, index) => {
      // Date variations
      if (header.includes('date') || header.includes('datum') || header.includes('comptable')) {
        if (map['date'] === undefined) map['date'] = index;
      }
      // Amount variations
      if (
        header.includes('amount') ||
        header.includes('montant') ||
        header.includes('bedrag') ||
        header.includes('sum')
      ) {
        if (map['amount'] === undefined) map['amount'] = index;
      }
      // Description/counterparty variations
      if (
        header.includes('description') ||
        header.includes('omschrijving') ||
        header.includes('communication') ||
        header.includes('counterparty') ||
        header.includes('contrepartie') ||
        header.includes('name') ||
        header.includes('naam')
      ) {
        if (map['counterparty'] === undefined) map['counterparty'] = index;
        else if (map['description'] === undefined) map['description'] = index;
      }
    });

    return map;
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

    const date = this.parseGenericDate(dateStr);
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

    const amount = this.parseGenericAmount(amountStr);
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

  private parseGenericDate(dateStr: string): Date | null {
    if (!dateStr) return null;

    // Try common date formats
    const formats = [
      /^(\d{2})\/(\d{2})\/(\d{4})$/, // DD/MM/YYYY
      /^(\d{4})-(\d{2})-(\d{2})$/, // YYYY-MM-DD
      /^(\d{2})-(\d{2})-(\d{4})$/, // DD-MM-YYYY
      /^(\d{4})(\d{2})(\d{2})$/, // YYYYMMDD
    ];

    for (const format of formats) {
      const match = dateStr.match(format);
      if (match) {
        if (format === formats[0] || format === formats[2]) {
          const day = parseInt(match[1], 10);
          const month = parseInt(match[2], 10) - 1;
          const year = parseInt(match[3], 10);
          return new Date(year, month, day);
        } else if (format === formats[1]) {
          return new Date(dateStr);
        } else if (format === formats[3]) {
          const year = parseInt(match[1], 10);
          const month = parseInt(match[2], 10) - 1;
          const day = parseInt(match[3], 10);
          return new Date(year, month, day);
        }
      }
    }

    // Try Date.parse as last resort
    const parsed = Date.parse(dateStr);
    return isNaN(parsed) ? null : new Date(parsed);
  }

  private parseGenericAmount(amountStr: string): number | null {
    if (!amountStr) return null;

    let cleaned = amountStr.trim().replace(/[€$£\s]/g, '');

    // Handle European format (comma as decimal separator)
    if (cleaned.includes(',') && !cleaned.includes('.')) {
      cleaned = cleaned.replace(',', '.');
    } else if (cleaned.includes(',') && cleaned.includes('.')) {
      // 1.234,56 format - remove thousand separators
      cleaned = cleaned.replace(/\./g, '').replace(',', '.');
    }

    const amount = parseFloat(cleaned);
    return isNaN(amount) ? null : amount;
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
