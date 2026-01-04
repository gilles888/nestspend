import { BankImportParser } from './bank-import-parser.interface';
import { NormalizedImportedTransaction, BankType, ParseResult } from '../models/import.models';

/**
 * Parser for ING TXT bank exports.
 * ING TXT format is typically tab-separated.
 */
export class IngTxtParser implements BankImportParser {
  getBankType(): BankType {
    return 'ING';
  }

  canHandle(fileName: string, mimeType: string, firstLines: string[]): boolean {
    const isTxtFile =
      fileName.toLowerCase().endsWith('.txt') ||
      mimeType === 'text/plain' ||
      mimeType === 'application/txt';

    if (!isTxtFile) {
      return false;
    }

    // Check if first lines contain ING-specific patterns
    const firstLinesLower = firstLines.join(' ').toLowerCase();
    const ingPatterns = ['ing', 'rekening', 'tegenrekening', 'af bij', 'mutatiesoort'];
    return ingPatterns.some((pattern) => firstLinesLower.includes(pattern));
  }

  parse(fileContent: string): ParseResult {
    const rawPreview = fileContent.split('\n').slice(0, 10);
    const lines = fileContent.split('\n').filter((line) => line.trim() !== '');
    const transactions: NormalizedImportedTransaction[] = [];
    let errorLines = 0;

    if (lines.length < 2) {
      return {
        bankType: 'ING',
        transactions: [],
        totalLines: lines.length,
        successfulLines: 0,
        errorLines: 0,
        rawPreview,
      };
    }

    // Try to detect delimiter
    const delimiter = this.detectDelimiter(lines[0]);

    const headers = lines[0].split(delimiter).map((h) => h.toLowerCase().trim());
    const columnMap = this.detectColumns(headers);

    for (let i = 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;

      const fields = line.split(delimiter);
      const rawLine = line;

      try {
        const transaction = this.parseRow(fields, columnMap, rawLine);
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
      totalLines: lines.length - 1,
      successfulLines: transactions.filter((t) => t.status !== 'ERROR').length,
      errorLines,
      rawPreview,
    };
  }

  private detectDelimiter(headerLine: string): string {
    const tabCount = (headerLine.match(/\t/g) || []).length;
    const semicolonCount = (headerLine.match(/;/g) || []).length;
    const commaCount = (headerLine.match(/,/g) || []).length;

    if (tabCount >= semicolonCount && tabCount >= commaCount) {
      return '\t';
    } else if (semicolonCount >= commaCount) {
      return ';';
    }
    return ',';
  }

  private detectColumns(headers: string[]): Record<string, number> {
    const map: Record<string, number> = {};

    headers.forEach((header, index) => {
      if (header.includes('datum') || header.includes('date')) {
        map['date'] = index;
      }
      if (header.includes('bedrag') || header.includes('amount')) {
        map['amount'] = index;
      }
      if (header.includes('af bij') || header.includes('af/bij')) {
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
      if (header.includes('tegenrekening')) {
        map['iban'] = index;
      }
    });

    return map;
  }

  private parseRow(
    fields: string[],
    columnMap: Record<string, number>,
    rawLine: string
  ): NormalizedImportedTransaction | null {
    const dateStr = columnMap['date'] !== undefined ? fields[columnMap['date']]?.trim() : '';
    const amountStr = columnMap['amount'] !== undefined ? fields[columnMap['amount']]?.trim() : '';
    const debitCredit =
      columnMap['debitCredit'] !== undefined ? fields[columnMap['debitCredit']]?.trim() : '';
    const counterparty =
      columnMap['counterparty'] !== undefined ? fields[columnMap['counterparty']]?.trim() : '';
    const description =
      columnMap['description'] !== undefined ? fields[columnMap['description']]?.trim() : '';
    const iban = columnMap['iban'] !== undefined ? fields[columnMap['iban']]?.trim() : undefined;

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

    const formats = [
      /^(\d{4})(\d{2})(\d{2})$/,
      /^(\d{2})-(\d{2})-(\d{4})$/,
      /^(\d{2})\/(\d{2})\/(\d{4})$/,
      /^(\d{4})-(\d{2})-(\d{2})$/,
    ];

    for (const format of formats) {
      const match = dateStr.match(format);
      if (match) {
        if (format === formats[0]) {
          const year = parseInt(match[1], 10);
          const month = parseInt(match[2], 10) - 1;
          const day = parseInt(match[3], 10);
          return new Date(year, month, day);
        } else if (format === formats[1] || format === formats[2]) {
          const day = parseInt(match[1], 10);
          const month = parseInt(match[2], 10) - 1;
          const year = parseInt(match[3], 10);
          return new Date(year, month, day);
        } else if (format === formats[3]) {
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
