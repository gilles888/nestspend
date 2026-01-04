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
 * Parser for Belfius CSV bank exports.
 * Enhanced to handle:
 * - Preamble lines before the header (e.g., account info, balance)
 * - Various encodings (UTF-8, Windows-1252)
 * - Belgian date and amount formats
 */
export class BelfiusCsvParser implements BankImportParser {
  // Known Belfius column headers (French/Dutch/English)
  private static readonly KNOWN_HEADERS = [
    'date comptable',
    'date de comptabilisation',
    'boekingsdatum',
    'montant',
    'bedrag',
    'contrepartie',
    'tegenpartij',
    'communication',
    'communications',
    'mededeling',
    'compte',
    'rekening',
    'compte de la contrepartie',
    'numéro de transaction',
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

    // Check if any of the first lines contain Belfius-specific headers
    // This handles files with preamble
    const allContent = firstLines.join(' ').toLowerCase();
    return BelfiusCsvParser.KNOWN_HEADERS.some((header) => allContent.includes(header));
  }

  parse(fileContent: string): ParseResult {
    const allLines = fileContent.split('\n');
    const rawPreview = allLines.slice(0, 15);
    const transactions: NormalizedImportedTransaction[] = [];
    let errorLines = 0;

    // Detect delimiter
    const delimiter = detectDelimiter(fileContent);

    // Find the header line (skip preamble)
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
        bankType: 'BELFIUS',
        transactions: [],
        totalLines: rows.length,
        successfulLines: 0,
        errorLines: 0,
        rawPreview,
      };
    }

    // Detect column indices from header row using aliases
    const headers = rows[0].map((h) => h.toLowerCase().trim());
    const columnMap = detectColumnsWithAliases(headers);

    // Process data rows
    for (let i = 1; i < rows.length; i++) {
      const row = rows[i];
      if (row.length < 2 || row.every((cell) => !cell.trim())) {
        continue; // Skip empty rows
      }

      const rawLine = row.join(delimiter);

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

  private parseRow(
    row: string[],
    columnMap: Record<string, number>,
    rawLine: string
  ): NormalizedImportedTransaction | null {
    // Extract values using column map
    const dateStr = columnMap['date'] !== undefined ? row[columnMap['date']]?.trim() : '';
    const amountStr = columnMap['amount'] !== undefined ? row[columnMap['amount']]?.trim() : '';
    const counterpartyRaw =
      columnMap['counterparty'] !== undefined ? row[columnMap['counterparty']]?.trim() : '';
    const descriptionRaw =
      columnMap['description'] !== undefined ? row[columnMap['description']]?.trim() : '';
    const iban = columnMap['iban'] !== undefined ? row[columnMap['iban']]?.trim() : undefined;
    const externalId =
      columnMap['reference'] !== undefined ? row[columnMap['reference']]?.trim() : undefined;

    // Normalize counterparty and description
    const counterparty = normalizeName(counterpartyRaw);
    const description = normalizeName(descriptionRaw);

    // Parse date using flexible parser
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

    // Parse amount using flexible parser
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

    // Determine type and absolute amount
    const type: 'EXPENSE' | 'INCOME' = amount < 0 ? 'EXPENSE' : 'INCOME';
    const amountCents = Math.abs(Math.round(amount * 100));

    // Combine counterparty and description if needed
    const finalDescription = counterparty
      ? description
      : combineDescriptions(descriptionRaw);

    return {
      importedDate: date,
      amountCents,
      type,
      counterparty: counterparty || '',
      description: finalDescription || '',
      iban: iban || undefined,
      externalId: externalId || undefined,
      rawLine,
      status: 'OK',
    };
  }
}
