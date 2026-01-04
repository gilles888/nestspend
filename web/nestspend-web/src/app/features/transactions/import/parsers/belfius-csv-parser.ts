import * as Papa from 'papaparse';
import { BankImportParser } from './bank-import-parser.interface';
import { NormalizedImportedTransaction, BankType, ParseResult } from '../models/import.models';

/**
 * Parser for Belfius CSV bank exports.
 * Belfius CSV format typically has semicolon separators and columns like:
 * - Date comptable
 * - Montant de la transaction
 * - Nom de la contrepartie
 * - Communication
 */
export class BelfiusCsvParser implements BankImportParser {
  // Known Belfius column headers (French/Dutch/English)
  private static readonly KNOWN_HEADERS = [
    'date comptable',
    'boekingsdatum',
    'montant',
    'bedrag',
    'contrepartie',
    'tegenpartij',
    'communication',
    'mededeling',
    'compte',
    'rekening',
    'compte de la contrepartie',
  ];

  getBankType(): BankType {
    return 'BELFIUS';
  }

  canHandle(fileName: string, mimeType: string, firstLines: string[]): boolean {
    // Check file extension
    const isCsvFile =
      fileName.toLowerCase().endsWith('.csv') ||
      mimeType === 'text/csv' ||
      mimeType === 'application/csv';

    if (!isCsvFile) {
      return false;
    }

    // Check if first lines contain Belfius-specific headers
    const headerLine = firstLines[0]?.toLowerCase() || '';
    return BelfiusCsvParser.KNOWN_HEADERS.some((header) => headerLine.includes(header));
  }

  parse(fileContent: string): ParseResult {
    const rawPreview = fileContent.split('\n').slice(0, 10);
    const transactions: NormalizedImportedTransaction[] = [];
    let errorLines = 0;

    // Parse CSV with PapaParse
    const parseResult = Papa.parse<string[]>(fileContent, {
      delimiter: ';',
      skipEmptyLines: true,
    });

    if (parseResult.errors.length > 0 || parseResult.data.length < 2) {
      // Try comma delimiter as fallback
      const commaResult = Papa.parse<string[]>(fileContent, {
        delimiter: ',',
        skipEmptyLines: true,
      });
      if (commaResult.data.length > parseResult.data.length) {
        parseResult.data = commaResult.data;
        parseResult.errors = commaResult.errors;
      }
    }

    const rows = parseResult.data;
    if (rows.length < 2) {
      return {
        bankType: 'BELFIUS',
        transactions: [],
        totalLines: rows.length,
        successfulLines: 0,
        errorLines: 0,
        rawPreview,
      };
    }

    // Detect column indices from header row
    const headers = rows[0].map((h) => h.toLowerCase().trim());
    const columnMap = this.detectColumns(headers);

    // Process data rows
    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      const rawLine = row.join(';');

      try {
        const transaction = this.parseRow(row, columnMap, rawLine);
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
      bankType: 'BELFIUS',
      transactions,
      totalLines: rows.length - 1, // Exclude header
      successfulLines: transactions.filter((t) => t.status !== 'ERROR').length,
      errorLines,
      rawPreview,
    };
  }

  private detectColumns(headers: string[]): Record<string, number> {
    const map: Record<string, number> = {};

    headers.forEach((header, index) => {
      // Date columns
      if (
        header.includes('date comptable') ||
        header.includes('boekingsdatum') ||
        header === 'date'
      ) {
        map['date'] = index;
      }
      // Amount columns
      if (
        header.includes('montant') ||
        header.includes('bedrag') ||
        header.includes('amount') ||
        header.includes('transactie')
      ) {
        map['amount'] = index;
      }
      // Counterparty columns
      if (
        header.includes('contrepartie') ||
        header.includes('tegenpartij') ||
        header.includes('nom') ||
        header.includes('begunstigde')
      ) {
        map['counterparty'] = index;
      }
      // Communication columns
      if (
        header.includes('communication') ||
        header.includes('mededeling') ||
        header.includes('description')
      ) {
        map['description'] = index;
      }
      // IBAN columns
      if (header.includes('compte de la contrepartie') || header.includes('tegenrekening')) {
        map['iban'] = index;
      }
      // Reference columns
      if (
        header.includes('référence') ||
        header.includes('referentie') ||
        header.includes('reference')
      ) {
        map['reference'] = index;
      }
    });

    return map;
  }

  private parseRow(
    row: string[],
    columnMap: Record<string, number>,
    rawLine: string
  ): NormalizedImportedTransaction | null {
    // Extract values using column map
    const dateStr = columnMap['date'] !== undefined ? row[columnMap['date']]?.trim() : '';
    const amountStr = columnMap['amount'] !== undefined ? row[columnMap['amount']]?.trim() : '';
    const counterparty =
      columnMap['counterparty'] !== undefined ? row[columnMap['counterparty']]?.trim() : '';
    const description =
      columnMap['description'] !== undefined ? row[columnMap['description']]?.trim() : '';
    const iban = columnMap['iban'] !== undefined ? row[columnMap['iban']]?.trim() : undefined;
    const externalId =
      columnMap['reference'] !== undefined ? row[columnMap['reference']]?.trim() : undefined;

    // Parse date
    const date = this.parseDate(dateStr);
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

    // Parse amount
    const amount = this.parseAmount(amountStr);
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

    // Determine type and absolute amount
    const type: 'EXPENSE' | 'INCOME' = amount < 0 ? 'EXPENSE' : 'INCOME';
    const amountCents = Math.abs(Math.round(amount * 100));

    return {
      importedDate: date,
      amountCents,
      type,
      counterparty: counterparty || '',
      description: description || '',
      iban: iban || undefined,
      externalId: externalId || undefined,
      rawLine,
      status: 'OK',
    };
  }

  private parseDate(dateStr: string): Date | null {
    if (!dateStr) return null;

    // Try different date formats
    // Belfius commonly uses DD/MM/YYYY
    const formats = [
      /^(\d{2})\/(\d{2})\/(\d{4})$/, // DD/MM/YYYY
      /^(\d{4})-(\d{2})-(\d{2})$/, // YYYY-MM-DD
      /^(\d{2})-(\d{2})-(\d{4})$/, // DD-MM-YYYY
    ];

    for (const format of formats) {
      const match = dateStr.match(format);
      if (match) {
        if (format === formats[0]) {
          // DD/MM/YYYY
          const day = parseInt(match[1], 10);
          const month = parseInt(match[2], 10) - 1;
          const year = parseInt(match[3], 10);
          return new Date(year, month, day);
        } else if (format === formats[1]) {
          // YYYY-MM-DD
          return new Date(dateStr);
        } else if (format === formats[2]) {
          // DD-MM-YYYY
          const day = parseInt(match[1], 10);
          const month = parseInt(match[2], 10) - 1;
          const year = parseInt(match[3], 10);
          return new Date(year, month, day);
        }
      }
    }

    return null;
  }

  private parseAmount(amountStr: string): number | null {
    if (!amountStr) return null;

    // Belfius often uses European format: 1.234,56 or -1.234,56
    // Also handle: 1234.56 or -1234.56
    let cleaned = amountStr.trim();

    // Remove any currency symbols and spaces
    cleaned = cleaned.replace(/[€$\s]/g, '');

    // Check if it's European format (comma as decimal separator)
    if (cleaned.includes(',') && !cleaned.includes('.')) {
      cleaned = cleaned.replace(',', '.');
    } else if (cleaned.includes(',') && cleaned.includes('.')) {
      // Format like 1.234,56 - remove thousand separators
      cleaned = cleaned.replace(/\./g, '').replace(',', '.');
    }

    const amount = parseFloat(cleaned);
    return isNaN(amount) ? null : amount;
  }
}
