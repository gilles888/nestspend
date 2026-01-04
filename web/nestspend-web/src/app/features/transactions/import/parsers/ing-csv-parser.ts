import * as Papa from 'papaparse';
import { BankImportParser } from './bank-import-parser.interface';
import { NormalizedImportedTransaction, BankType, ParseResult } from '../models/import.models';
import {
  parseAmountFlexible,
  parseDateFlexible,
  normalizeName,
  detectDelimiter,
  findHeaderLineIndex,
  detectColumnsWithAliases,
  combineDescriptions,
} from './parse-utils';

/**
 * Parser for ING CSV bank exports.
 * Enhanced to handle Belgian ING exports with:
 * - Various date formats (dd/MM/yyyy)
 * - European amount format with comma decimals
 * - Multiple description columns (Libellés, Détails du mouvement, Message)
 */
export class IngCsvParser implements BankImportParser {
  // Known ING column headers
  private static readonly KNOWN_HEADERS = [
    'numéro de compte',
    'nom du compte',
    'compte contrepartie',
    'numéro de mouvement',
    'date comptable',
    'date valeur',
    'montant',
    'devise',
    'libellés',
    'détails du mouvement',
    'message',
    // Dutch variants
    'rekening',
    'naam / omschrijving',
    'tegenrekening',
    'bedrag',
    'af bij',
  ];

  getBankType(): BankType {
    return 'ING';
  }

  canHandle(fileName: string, mimeType: string, firstLines: string[]): boolean {
    const isCsvFile =
      fileName.toLowerCase().endsWith('.csv') ||
      mimeType === 'text/csv' ||
      mimeType === 'application/csv';

    if (!isCsvFile) {
      return false;
    }

    // Check if file name contains ING identifiers or account pattern
    const lowerFileName = fileName.toLowerCase();
    if (lowerFileName.includes('ing') || /be\d{2}\s?\d{4}\s?\d{4}\s?\d{4}/.test(lowerFileName)) {
      return true;
    }

    // Check if first lines contain ING-specific headers
    const allContent = firstLines.join(' ').toLowerCase();
    return IngCsvParser.KNOWN_HEADERS.some((header) => allContent.includes(header));
  }

  parse(fileContent: string): ParseResult {
    const allLines = fileContent.split('\n');
    const rawPreview = allLines.slice(0, 15);
    const transactions: NormalizedImportedTransaction[] = [];
    let errorLines = 0;

    // Detect delimiter
    const delimiter = detectDelimiter(fileContent);

    // Find the header line (in case of preamble)
    const headerLineIndex = findHeaderLineIndex(allLines, delimiter);

    // Extract content starting from header
    const contentFromHeader = allLines.slice(headerLineIndex).join('\n');

    // Parse CSV with PapaParse
    const parseResult = Papa.parse<string[]>(contentFromHeader, {
      delimiter,
      skipEmptyLines: true,
    });

    const rows = parseResult.data;
    if (rows.length < 2) {
      return {
        bankType: 'ING',
        transactions: [],
        totalLines: rows.length,
        successfulLines: 0,
        errorLines: 0,
        rawPreview,
      };
    }

    // Detect column indices using aliases
    const headers = rows[0].map((h) => h.toLowerCase().trim());
    const columnMap = this.detectIngColumns(headers);

    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      if (row.length < 2 || row.every((cell) => !cell.trim())) {
        continue; // Skip empty rows
      }

      const rawLine = row.join(delimiter);

      try {
        const transaction = this.parseRow(row, columnMap, rawLine, headers);
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
      bankType: 'ING',
      transactions,
      totalLines: rows.length - 1,
      successfulLines: transactions.filter((t) => t.status !== 'ERROR').length,
      errorLines,
      rawPreview,
    };
  }

  /**
   * ING-specific column detection with extended aliases.
   */
  private detectIngColumns(headers: string[]): Record<string, number> {
    // First use the generic alias detection
    const map = detectColumnsWithAliases(headers);

    // ING-specific additional mappings
    headers.forEach((header, index) => {
      const h = header.toLowerCase().trim();

      // Date - ING specific
      if (map['date'] === undefined) {
        if (h.includes('date comptable') || h.includes('date valeur') || h === 'datum') {
          map['date'] = index;
        }
      }

      // Amount - ING specific
      if (map['amount'] === undefined) {
        if (h === 'montant' || h === 'bedrag') {
          map['amount'] = index;
        }
      }

      // ING has multiple description columns - capture them all
      if (h.includes('libellés') || h === 'libelles') {
        map['libelles'] = index;
      }
      if (h.includes('détails du mouvement') || h.includes('details du mouvement')) {
        map['details'] = index;
      }
      if (h === 'message') {
        map['message'] = index;
      }

      // Counterparty IBAN
      if (map['iban'] === undefined) {
        if (h.includes('compte contrepartie') || h.includes('tegenrekening')) {
          map['iban'] = index;
        }
      }

      // Reference
      if (map['reference'] === undefined) {
        if (h.includes('numéro de mouvement') || h.includes('nummer')) {
          map['reference'] = index;
        }
      }
    });

    return map;
  }

  private parseRow(
    row: string[],
    columnMap: Record<string, number>,
    rawLine: string,
    _headers: string[]
  ): NormalizedImportedTransaction | null {
    // Extract date
    const dateStr = columnMap['date'] !== undefined ? row[columnMap['date']]?.trim() : '';

    // Extract amount
    let amountStr = columnMap['amount'] !== undefined ? row[columnMap['amount']]?.trim() : '';

    // Handle split amount: if amount column is followed by a decimal part
    // This handles cases where "100,00" is split into "100" and "0" due to delimiter confusion
    if (columnMap['amount'] !== undefined) {
      const amountIndex = columnMap['amount'];
      // Bounds check: ensure next column exists
      if (amountIndex + 1 < row.length) {
        const nextValue = row[amountIndex + 1]?.trim();
        
        // Check if next column looks like decimal digits (1-2 digits, not a currency or other data)
        if (nextValue && /^\d{1,2}$/.test(nextValue) && !amountStr.includes(',') && !amountStr.includes('.')) {
          // Combine: "100" + "0" → "100,0" (European format)
          amountStr = `${amountStr},${nextValue}`;
        }
      }
    }

    // Extract counterparty from various possible columns
    const counterpartyRaw =
      columnMap['counterparty'] !== undefined ? row[columnMap['counterparty']]?.trim() : '';

    // Extract and combine description from multiple ING columns
    const libelles = columnMap['libelles'] !== undefined ? row[columnMap['libelles']]?.trim() : '';
    const details = columnMap['details'] !== undefined ? row[columnMap['details']]?.trim() : '';
    const message = columnMap['message'] !== undefined ? row[columnMap['message']]?.trim() : '';
    const descriptionRaw =
      columnMap['description'] !== undefined ? row[columnMap['description']]?.trim() : '';

    const iban = columnMap['iban'] !== undefined ? row[columnMap['iban']]?.trim() : undefined;
    const externalId =
      columnMap['reference'] !== undefined ? row[columnMap['reference']]?.trim() : undefined;

    // Normalize names
    const counterparty = normalizeName(counterpartyRaw);

    // Combine all description fields intelligently
    const description = combineDescriptions(libelles, details, message, descriptionRaw);

    // Parse date
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

    // Parse amount
    const amount = parseAmountFlexible(amountStr);
    if (amount === null || amount === 0) {
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

    // Determine type based on amount sign
    const type: 'EXPENSE' | 'INCOME' = amount < 0 ? 'EXPENSE' : 'INCOME';
    const amountCents = Math.abs(Math.round(amount * 100));

    // Use description as counterparty if counterparty is empty
    const finalCounterparty = counterparty || description.split(' - ')[0] || '';

    return {
      importedDate: date,
      amountCents,
      type,
      counterparty: finalCounterparty,
      description: description || '',
      iban: iban || undefined,
      externalId: externalId || undefined,
      rawLine,
      status: 'OK',
    };
  }
}
