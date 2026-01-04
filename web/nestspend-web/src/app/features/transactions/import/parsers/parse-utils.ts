/**
 * Utility functions for parsing bank transaction data.
 * Enhanced to support Belgian bank exports (Belfius, ING) with various encodings and formats.
 */

/**
 * Column aliases for flexible header detection.
 * Each key is a normalized column name, values are possible aliases.
 */
export const COLUMN_ALIASES: Record<string, string[]> = {
  date: [
    'date',
    'datum',
    'date comptable',
    'date de comptabilisation',
    'boekingsdatum',
    'date valeur',
    'valutadatum',
  ],
  amount: [
    'montant',
    'bedrag',
    'amount',
    'montant de la transaction',
  ],
  counterparty: [
    'nom contrepartie contient',
    'nom de la contrepartie',
    'contrepartie',
    'tegenpartij',
    'begunstigde',
    'naam / omschrijving',
    'name',
  ],
  description: [
    'communications',
    'communication',
    'mededeling',
    'description',
    'libellés',
    'libelles',
    'transaction',
    'détails du mouvement',
    'details du mouvement',
    'message',
  ],
  iban: [
    'compte de la contrepartie',
    'tegenrekening',
    'compte contrepartie',
    'counter account',
    'iban',
  ],
  currency: [
    'devise',
    'currency',
    'munt',
  ],
  reference: [
    'référence',
    'referentie',
    'reference',
    'numéro de transaction',
    'numéro de mouvement',
  ],
  account: [
    'compte',
    'rekening',
    'numéro de compte',
    'account',
  ],
};

/**
 * Minimum required columns for a valid header line.
 * Used to detect the header row when there's a preamble.
 */
export const REQUIRED_HEADER_COLUMNS = ['compte', 'rekening', 'montant', 'bedrag', 'date', 'datum', 'amount'];

/**
 * Parse an amount string to a number.
 * Enhanced to handle Belgian bank formats with:
 * - European format: 1.234,56 or 1234,56 or -1.234,56
 * - Spaces between sign and number: "- 44,97" → -44.97
 * - Currency symbols: €, EUR
 * - Thousands separators: 1.234.567,89
 *
 * @param amountStr Raw amount string from bank file
 * @returns Parsed number or null if invalid
 */
export function parseAmountFlexible(amountStr: string): number | null {
  if (!amountStr) return null;

  let cleaned = amountStr.trim();

  // Remove currency symbols and "EUR"
  cleaned = cleaned.replace(/[€$£]/g, '').replace(/\bEUR\b/gi, '').trim();

  // Handle sign with space: "- 44,97" → "-44,97" or "+ 2,00" → "+2,00"
  cleaned = cleaned.replace(/^(-|\+)\s+/, '$1');

  // Remove all spaces
  cleaned = cleaned.replace(/\s/g, '');

  // Handle European format (comma as decimal separator)
  // Check if it's European format: has comma but decimal part looks right
  if (cleaned.includes(',')) {
    // If both . and , exist, assume . is thousands separator
    if (cleaned.includes('.')) {
      // Format like 1.234,56 - remove thousand separators
      cleaned = cleaned.replace(/\./g, '').replace(',', '.');
    } else {
      // Just comma: 1234,56 → 1234.56
      cleaned = cleaned.replace(',', '.');
    }
  }

  const amount = parseFloat(cleaned);
  return isNaN(amount) ? null : amount;
}

/**
 * Parse a date string to a Date object.
 * Enhanced to handle Belgian bank date formats:
 * - DD/MM/YYYY (most common)
 * - DD-MM-YYYY
 * - DD.MM.YYYY
 * - YYYY-MM-DD
 * - YYYYMMDD
 *
 * @param dateStr Raw date string from bank file
 * @returns Parsed Date or null if invalid
 */
export function parseDateFlexible(dateStr: string): Date | null {
  if (!dateStr) return null;

  const trimmed = dateStr.trim();

  // Try different date formats
  const formats: Array<{
    regex: RegExp;
    parse: (match: RegExpMatchArray) => Date;
  }> = [
    {
      // DD/MM/YYYY
      regex: /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/,
      parse: (m) => new Date(parseInt(m[3], 10), parseInt(m[2], 10) - 1, parseInt(m[1], 10)),
    },
    {
      // DD-MM-YYYY
      regex: /^(\d{1,2})-(\d{1,2})-(\d{4})$/,
      parse: (m) => new Date(parseInt(m[3], 10), parseInt(m[2], 10) - 1, parseInt(m[1], 10)),
    },
    {
      // DD.MM.YYYY
      regex: /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/,
      parse: (m) => new Date(parseInt(m[3], 10), parseInt(m[2], 10) - 1, parseInt(m[1], 10)),
    },
    {
      // YYYY-MM-DD
      regex: /^(\d{4})-(\d{1,2})-(\d{1,2})$/,
      parse: (m) => new Date(parseInt(m[1], 10), parseInt(m[2], 10) - 1, parseInt(m[3], 10)),
    },
    {
      // YYYY/MM/DD
      regex: /^(\d{4})\/(\d{1,2})\/(\d{1,2})$/,
      parse: (m) => new Date(parseInt(m[1], 10), parseInt(m[2], 10) - 1, parseInt(m[3], 10)),
    },
    {
      // YYYYMMDD
      regex: /^(\d{4})(\d{2})(\d{2})$/,
      parse: (m) => new Date(parseInt(m[1], 10), parseInt(m[2], 10) - 1, parseInt(m[3], 10)),
    },
  ];

  for (const format of formats) {
    const match = trimmed.match(format.regex);
    if (match) {
      const date = format.parse(match);
      // Validate the parsed date
      if (!isNaN(date.getTime())) {
        return date;
      }
    }
  }

  // Try Date.parse as last resort
  const parsed = Date.parse(trimmed);
  return isNaN(parsed) ? null : new Date(parsed);
}

/**
 * Normalize a merchant/counterparty name.
 * Removes excess whitespace and trims.
 *
 * @param name Raw name string
 * @returns Normalized name
 */
export function normalizeName(name: string | undefined | null): string {
  if (!name) return '';
  return name.trim().replace(/\s+/g, ' ');
}

/**
 * Detect the CSV delimiter by analyzing the content.
 * Supports semicolon, comma, and tab delimiters.
 * @param content File content
 * @returns Detected delimiter (';', ',', or '\t')
 */
export function detectDelimiter(content: string): string {
  const firstLines = content.split('\n').slice(0, 15);
  let semicolonCount = 0;
  let commaCount = 0;
  let tabCount = 0;

  for (const line of firstLines) {
    semicolonCount += (line.match(/;/g) || []).length;
    commaCount += (line.match(/,/g) || []).length;
    tabCount += (line.match(/\t/g) || []).length;
  }

  // Check for tab delimiter first (Excel exports often use tabs)
  if (tabCount > semicolonCount && tabCount > commaCount) {
    return '\t';
  }

  // Belgian banks typically use semicolon
  return semicolonCount >= commaCount ? ';' : ',';
}

/**
 * Find the header line index in a file that may have a preamble.
 * Detects the first line containing required column headers.
 *
 * @param lines Array of lines from the file
 * @param delimiter CSV delimiter
 * @returns Index of the header line, or 0 if not found
 */
export function findHeaderLineIndex(lines: string[], delimiter: string): number {
  for (let i = 0; i < Math.min(lines.length, 20); i++) {
    const line = lines[i].toLowerCase();
    const columns = line.split(delimiter).map((c) => c.trim());

    // Check if this line contains enough expected header columns
    let matchCount = 0;
    for (const col of columns) {
      for (const required of REQUIRED_HEADER_COLUMNS) {
        if (col.includes(required)) {
          matchCount++;
          break;
        }
      }
    }

    // If we found at least 2 matching columns, this is likely the header
    if (matchCount >= 2) {
      return i;
    }
  }

  return 0; // Default to first line
}

/**
 * Detect column indices from headers using aliases.
 * More robust than exact matching.
 *
 * @param headers Array of header strings (lowercase)
 * @returns Map of column type to index
 */
export function detectColumnsWithAliases(headers: string[]): Record<string, number> {
  const map: Record<string, number> = {};

  headers.forEach((header, index) => {
    const normalizedHeader = header.toLowerCase().trim();

    // Check each column type
    for (const [columnType, aliases] of Object.entries(COLUMN_ALIASES)) {
      if (map[columnType] !== undefined) continue; // Already found

      for (const alias of aliases) {
        if (normalizedHeader.includes(alias) || normalizedHeader === alias) {
          map[columnType] = index;
          break;
        }
      }
    }
  });

  return map;
}

/**
 * Extract description/merchant from multiple columns.
 * Concatenates non-empty values intelligently.
 *
 * @param values Array of potential description values
 * @returns Combined description string
 */
export function combineDescriptions(...values: (string | undefined | null)[]): string {
  const nonEmpty = values
    .filter((v) => v && v.trim())
    .map((v) => v!.trim());

  // Remove duplicates
  const unique = [...new Set(nonEmpty)];

  return unique.join(' - ').replace(/\s+/g, ' ').trim();
}

// Legacy exports for backward compatibility
export const parseAmount = parseAmountFlexible;
export const parseDate = parseDateFlexible;
