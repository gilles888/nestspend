import { BelfiusCsvParser } from '../parsers/belfius-csv-parser';
import { IngCsvParser } from '../parsers/ing-csv-parser';
import { BankParserFactory } from '../parsers/bank-parser-factory';
import {
  parseAmountFlexible,
  parseDateFlexible,
  findHeaderLineIndex,
  detectDelimiter,
  detectColumnsWithAliases,
} from '../parsers/parse-utils';
import {
  BELFIUS_CSV_WITH_PREAMBLE,
  BELFIUS_TAB_SPLIT_AMOUNT,
  ING_CSV_EXPORT,
  SIMPLE_TRANSACTIONS_CSV,
} from '../__fixtures__/bank-csv-fixtures';

describe('Parse Utils', () => {
  describe('parseAmountFlexible', () => {
    it('should parse European format with comma decimal', () => {
      expect(parseAmountFlexible('-44,97')).toBe(-44.97);
      expect(parseAmountFlexible('2500,00')).toBe(2500);
      expect(parseAmountFlexible('1234,56')).toBe(1234.56);
    });

    it('should handle sign with space', () => {
      expect(parseAmountFlexible('- 44,97')).toBe(-44.97);
      expect(parseAmountFlexible('+ 100,00')).toBe(100);
      expect(parseAmountFlexible('- 12,50')).toBe(-12.5);
    });

    it('should handle thousands separator', () => {
      expect(parseAmountFlexible('1.234,56')).toBe(1234.56);
      expect(parseAmountFlexible('-5.000,00')).toBe(-5000);
      expect(parseAmountFlexible('1.234.567,89')).toBe(1234567.89);
    });

    it('should handle currency symbols', () => {
      expect(parseAmountFlexible('€ 100,00')).toBe(100);
      expect(parseAmountFlexible('-€44,97')).toBe(-44.97);
      expect(parseAmountFlexible('100,00 EUR')).toBe(100);
    });

    it('should return null for invalid input', () => {
      expect(parseAmountFlexible('')).toBeNull();
      expect(parseAmountFlexible('abc')).toBeNull();
    });
  });

  describe('parseDateFlexible', () => {
    it('should parse DD/MM/YYYY format', () => {
      const date = parseDateFlexible('03/01/2026');
      expect(date?.getDate()).toBe(3);
      expect(date?.getMonth()).toBe(0); // January
      expect(date?.getFullYear()).toBe(2026);
    });

    it('should parse DD-MM-YYYY format', () => {
      const date = parseDateFlexible('15-06-2025');
      expect(date?.getDate()).toBe(15);
      expect(date?.getMonth()).toBe(5); // June
      expect(date?.getFullYear()).toBe(2025);
    });

    it('should parse YYYY-MM-DD format', () => {
      const date = parseDateFlexible('2026-01-03');
      expect(date?.getDate()).toBe(3);
      expect(date?.getMonth()).toBe(0);
      expect(date?.getFullYear()).toBe(2026);
    });

    it('should return null for invalid dates', () => {
      expect(parseDateFlexible('')).toBeNull();
      expect(parseDateFlexible('invalid')).toBeNull();
    });
  });

  describe('detectDelimiter', () => {
    it('should detect semicolon delimiter', () => {
      const content = 'Col1;Col2;Col3\nVal1;Val2;Val3';
      expect(detectDelimiter(content)).toBe(';');
    });

    it('should detect comma delimiter', () => {
      const content = 'Col1,Col2,Col3\nVal1,Val2,Val3';
      expect(detectDelimiter(content)).toBe(',');
    });

    it('should detect tab delimiter', () => {
      const content = 'Col1\tCol2\tCol3\nVal1\tVal2\tVal3';
      expect(detectDelimiter(content)).toBe('\t');
    });
  });

  describe('findHeaderLineIndex', () => {
    it('should find header after preamble', () => {
      const lines = BELFIUS_CSV_WITH_PREAMBLE.split('\n');
      const index = findHeaderLineIndex(lines, ';');
      expect(index).toBe(4); // After preamble lines
    });

    it('should return 0 for file without preamble', () => {
      const lines = SIMPLE_TRANSACTIONS_CSV.split('\n');
      const index = findHeaderLineIndex(lines, ';');
      expect(index).toBe(0);
    });

    it('should find header in tab-delimited file with preamble', () => {
      const lines = BELFIUS_TAB_SPLIT_AMOUNT.split('\n');
      const index = findHeaderLineIndex(lines, '\t');
      expect(index).toBe(4); // After preamble lines
    });
  });

  describe('detectColumnsWithAliases', () => {
    it('should detect date column with various names', () => {
      const headers = ['compte', 'date de comptabilisation', 'montant'];
      const map = detectColumnsWithAliases(headers);
      expect(map['date']).toBe(1);
    });

    it('should detect amount column', () => {
      const headers = ['compte', 'date', 'montant', 'devise'];
      const map = detectColumnsWithAliases(headers);
      expect(map['amount']).toBe(2);
    });
  });
});

describe('BelfiusCsvParser', () => {
  let parser: BelfiusCsvParser;

  beforeEach(() => {
    parser = new BelfiusCsvParser();
  });

  describe('canHandle', () => {
    it('should handle Belfius CSV files with preamble', () => {
      const lines = BELFIUS_CSV_WITH_PREAMBLE.split('\n').slice(0, 10);
      expect(parser.canHandle('export.csv', 'text/csv', lines)).toBe(true);
    });

    it('should not handle non-CSV files', () => {
      expect(parser.canHandle('export.txt', 'text/plain', ['test'])).toBe(false);
    });
  });

  describe('parse', () => {
    it('should parse Belfius CSV with preamble', () => {
      const result = parser.parse(BELFIUS_CSV_WITH_PREAMBLE);

      expect(result.bankType).toBe('BELFIUS');
      expect(result.transactions.length).toBe(4);
      expect(result.successfulLines).toBe(4);
      expect(result.errorLines).toBe(0);
    });

    it('should correctly parse amounts with European format', () => {
      const result = parser.parse(BELFIUS_CSV_WITH_PREAMBLE);
      const transactions = result.transactions.filter((t) => t.status === 'OK');

      // First transaction: -5000,00 EUR
      expect(transactions[0].amountCents).toBe(500000);
      expect(transactions[0].type).toBe('EXPENSE');

      // Second transaction: -44,97 EUR
      expect(transactions[1].amountCents).toBe(4497);
      expect(transactions[1].type).toBe('EXPENSE');

      // Third transaction: 2500,00 EUR (income)
      expect(transactions[2].amountCents).toBe(250000);
      expect(transactions[2].type).toBe('INCOME');
    });

    it('should handle amount with space after sign', () => {
      const result = parser.parse(BELFIUS_CSV_WITH_PREAMBLE);
      const transactions = result.transactions.filter((t) => t.status === 'OK');

      // Fourth transaction: "- 12,50" EUR
      expect(transactions[3].amountCents).toBe(1250);
      expect(transactions[3].type).toBe('EXPENSE');
    });

    it('should parse dates correctly', () => {
      const result = parser.parse(BELFIUS_CSV_WITH_PREAMBLE);
      const transactions = result.transactions.filter((t) => t.status === 'OK');

      // 02/01/2026
      expect(transactions[0].importedDate.getDate()).toBe(2);
      expect(transactions[0].importedDate.getMonth()).toBe(0);
      expect(transactions[0].importedDate.getFullYear()).toBe(2026);
    });
  });
});

describe('IngCsvParser', () => {
  let parser: IngCsvParser;

  beforeEach(() => {
    parser = new IngCsvParser();
  });

  describe('canHandle', () => {
    it('should handle ING CSV files', () => {
      const lines = ING_CSV_EXPORT.split('\n').slice(0, 5);
      expect(parser.canHandle('BE74377054695307.csv', 'text/csv', lines)).toBe(true);
    });
  });

  describe('parse', () => {
    it('should parse ING CSV export', () => {
      const result = parser.parse(ING_CSV_EXPORT);

      expect(result.bankType).toBe('ING');
      expect(result.transactions.length).toBe(4);
      expect(result.successfulLines).toBe(4);
    });

    it('should correctly parse ING amounts', () => {
      const result = parser.parse(ING_CSV_EXPORT);
      const transactions = result.transactions.filter((t) => t.status === 'OK');

      // First: -2,90 EUR
      expect(transactions[0].amountCents).toBe(290);
      expect(transactions[0].type).toBe('EXPENSE');

      // Third: 1500,00 EUR (income)
      expect(transactions[2].amountCents).toBe(150000);
      expect(transactions[2].type).toBe('INCOME');
    });

    it('should combine description columns', () => {
      const result = parser.parse(ING_CSV_EXPORT);
      const transactions = result.transactions.filter((t) => t.status === 'OK');

      // Should combine Libellés, Détails, Message
      expect(transactions[1].description).toContain('Paiement');
      expect(transactions[1].description).toContain('SUPERMARCHE SA');
    });
  });
});

describe('BankParserFactory', () => {
  let factory: BankParserFactory;

  beforeEach(() => {
    factory = new BankParserFactory();
  });

  describe('detectBankType', () => {
    it('should detect Belfius from content with preamble', () => {
      const type = factory.detectBankType('export.csv', 'text/csv', BELFIUS_CSV_WITH_PREAMBLE);
      expect(type).toBe('BELFIUS');
    });

    it('should detect ING from content', () => {
      const type = factory.detectBankType('BE74377054695307.csv', 'text/csv', ING_CSV_EXPORT);
      expect(type).toBe('ING');
    });
  });

  describe('parseFile', () => {
    it('should parse Belfius CSV with auto-detection', () => {
      const result = factory.parseFile('export.csv', 'text/csv', BELFIUS_CSV_WITH_PREAMBLE);
      expect(result.bankType).toBe('BELFIUS');
      expect(result.successfulLines).toBeGreaterThan(0);
    });

    it('should parse ING CSV with auto-detection', () => {
      const result = factory.parseFile('BE74377054695307.csv', 'text/csv', ING_CSV_EXPORT);
      expect(result.bankType).toBe('ING');
      expect(result.successfulLines).toBeGreaterThan(0);
    });

    it('should parse simple transactions CSV', () => {
      const result = factory.parseFile('Transactions.csv', 'text/csv', SIMPLE_TRANSACTIONS_CSV);
      expect(result.successfulLines).toBe(3);
    });
  });
});
