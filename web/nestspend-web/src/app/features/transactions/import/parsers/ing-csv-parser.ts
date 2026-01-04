import * as Papa from 'papaparse';
import { BankImportParser } from './bank-import-parser.interface';
import { NormalizedImportedTransaction, BankType, ParseResult } from '../models/import.models';

/**
 * Parser for ING CSV bank exports.
 * ING CSV format typically has columns like:
 * - Datum
 * - Bedrag
 * - Naam / Omschrijving
 * - Tegenrekening
 */
export class IngCsvParser implements BankImportParser {
  private static readonly KNOWN_HEADERS = [
    'ing',
    'rekening',
    'naam / omschrijving',
    'tegenrekening',
    'code',
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

    // Check if first lines contain ING-specific headers
    const firstLinesLower = firstLines.join(' ').toLowerCase();
    return (
      firstLinesLower.includes('ing') ||
      IngCsvParser.KNOWN_HEADERS.some((header) => firstLinesLower.includes(header))
    );
  }

  parse(fileContent: string): ParseResult {
    const rawPreview = fileContent.split('\n').slice(0, 10);
    const transactions: NormalizedImportedTransaction[] = [];
    let errorLines = 0;

    const parseResult = Papa.parse<string[]>(fileContent, {
      delimiter: ';',
      skipEmptyLines: true,
    });

    // Try comma delimiter as fallback
    if (parseResult.errors.length > 0 || parseResult.data.length < 2) {
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
        bankType: 'ING',
        transactions: [],
        totalLines: rows.length,
        successfulLines: 0,
        errorLines: 0,
        rawPreview,
      };
    }

    const headers = rows[0].map((h) => h.toLowerCase().trim());
    const columnMap = this.detectColumns(headers);

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
      bankType: 'ING',
      transactions,
      totalLines: rows.length - 1,
      successfulLines: transactions.filter((t) => t.status !== 'ERROR').length,
      errorLines,
      rawPreview,
    };
  }

  private detectColumns(headers: string[]): Record<string, number> {
    const map: Record<string, number> = {};

    headers.forEach((header, index) => {
      if (header.includes('datum') || header.includes('date')) {
        map['date'] = index;
      }
      if (header.includes('bedrag') || header.includes('amount') || header.includes('montant')) {
        map['amount'] = index;
      }
      // ING uses "Af Bij" or similar for debit/credit indicator
      if (header.includes('af bij') || header.includes('af/bij') || header.includes('debit')) {
        map['debitCredit'] = index;
      }
      if (
        header.includes('naam / omschrijving') ||
        header.includes('name') ||
        header.includes('omschrijving')
      ) {
        map['counterparty'] = index;
      }
      if (header.includes('mededelingen') || header.includes('communication')) {
        map['description'] = index;
      }
      if (header.includes('tegenrekening') || header.includes('counter account')) {
        map['iban'] = index;
      }
      if (
        header.includes('mutatiesoort') ||
        header.includes('type') ||
        header.includes('code')
      ) {
        map['transactionType'] = index;
      }
    });

    return map;
  }

  private parseRow(
    row: string[],
    columnMap: Record<string, number>,
    rawLine: string
  ): NormalizedImportedTransaction | null {
    const dateStr = columnMap['date'] !== undefined ? row[columnMap['date']]?.trim() : '';
    const amountStr = columnMap['amount'] !== undefined ? row[columnMap['amount']]?.trim() : '';
    const debitCredit =
      columnMap['debitCredit'] !== undefined ? row[columnMap['debitCredit']]?.trim() : '';
    const counterparty =
      columnMap['counterparty'] !== undefined ? row[columnMap['counterparty']]?.trim() : '';
    const description =
      columnMap['description'] !== undefined ? row[columnMap['description']]?.trim() : '';
    const iban = columnMap['iban'] !== undefined ? row[columnMap['iban']]?.trim() : undefined;

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

    let amount = this.parseAmount(amountStr);
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

    // ING sometimes uses a separate column for debit/credit
    // "Af" means debit (expense), "Bij" means credit (income)
    let type: 'EXPENSE' | 'INCOME';
    if (debitCredit) {
      const dc = debitCredit.toLowerCase();
      if (dc === 'af' || dc.includes('debit') || dc === '-') {
        type = 'EXPENSE';
        amount = Math.abs(amount);
      } else if (dc === 'bij' || dc.includes('credit') || dc === '+') {
        type = 'INCOME';
        amount = Math.abs(amount);
      } else {
        type = amount < 0 ? 'EXPENSE' : 'INCOME';
      }
    } else {
      type = amount < 0 ? 'EXPENSE' : 'INCOME';
    }

    const amountCents = Math.abs(Math.round(amount * 100));

    return {
      importedDate: date,
      amountCents,
      type,
      counterparty: counterparty || '',
      description: description || '',
      iban: iban || undefined,
      rawLine,
      status: 'OK',
    };
  }

  private parseDate(dateStr: string): Date | null {
    if (!dateStr) return null;

    // ING often uses YYYYMMDD or DD-MM-YYYY
    const formats = [
      /^(\d{4})(\d{2})(\d{2})$/, // YYYYMMDD
      /^(\d{2})-(\d{2})-(\d{4})$/, // DD-MM-YYYY
      /^(\d{2})\/(\d{2})\/(\d{4})$/, // DD/MM/YYYY
      /^(\d{4})-(\d{2})-(\d{2})$/, // YYYY-MM-DD
    ];

    for (const format of formats) {
      const match = dateStr.match(format);
      if (match) {
        if (format === formats[0]) {
          // YYYYMMDD
          const year = parseInt(match[1], 10);
          const month = parseInt(match[2], 10) - 1;
          const day = parseInt(match[3], 10);
          return new Date(year, month, day);
        } else if (format === formats[1] || format === formats[2]) {
          // DD-MM-YYYY or DD/MM/YYYY
          const day = parseInt(match[1], 10);
          const month = parseInt(match[2], 10) - 1;
          const year = parseInt(match[3], 10);
          return new Date(year, month, day);
        } else if (format === formats[3]) {
          // YYYY-MM-DD
          return new Date(dateStr);
        }
      }
    }

    return null;
  }

  private parseAmount(amountStr: string): number | null {
    if (!amountStr) return null;

    let cleaned = amountStr.trim().replace(/[€$\s]/g, '');

    if (cleaned.includes(',') && !cleaned.includes('.')) {
      cleaned = cleaned.replace(',', '.');
    } else if (cleaned.includes(',') && cleaned.includes('.')) {
      cleaned = cleaned.replace(/\./g, '').replace(',', '.');
    }

    const amount = parseFloat(cleaned);
    return isNaN(amount) ? null : amount;
  }
}
