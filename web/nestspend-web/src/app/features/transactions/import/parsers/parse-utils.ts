/**
 * Utility functions for parsing bank transaction data.
 */

/**
 * Parse an amount string to a number.
 * Handles different formats:
 * - European: 1.234,56 or 1234,56
 * - US: 1,234.56 or 1234.56
 * - With currency symbols: €1.234,56, $1,234.56
 *
 * @param amountStr Raw amount string from bank file
 * @returns Parsed number or null if invalid
 */
export function parseAmount(amountStr: string): number | null {
  if (!amountStr) return null;

  // Remove currency symbols and whitespace
  let cleaned = amountStr.trim().replace(/[€$£\s]/g, '');

  // Handle European format (comma as decimal separator)
  if (cleaned.includes(',') && !cleaned.includes('.')) {
    cleaned = cleaned.replace(',', '.');
  } else if (cleaned.includes(',') && cleaned.includes('.')) {
    // Format like 1.234,56 - remove thousand separators and convert decimal
    cleaned = cleaned.replace(/\./g, '').replace(',', '.');
  }

  const amount = parseFloat(cleaned);
  return isNaN(amount) ? null : amount;
}

/**
 * Parse a date string to a Date object.
 * Handles common bank date formats:
 * - DD/MM/YYYY
 * - DD-MM-YYYY
 * - DD.MM.YYYY
 * - YYYY-MM-DD
 * - YYYYMMDD
 *
 * @param dateStr Raw date string from bank file
 * @returns Parsed Date or null if invalid
 */
export function parseDate(dateStr: string): Date | null {
  if (!dateStr) return null;

  const trimmed = dateStr.trim();

  // Try different date formats
  const formats: Array<{
    regex: RegExp;
    parse: (match: RegExpMatchArray) => Date;
  }> = [
    {
      // DD/MM/YYYY
      regex: /^(\d{2})\/(\d{2})\/(\d{4})$/,
      parse: (m) => new Date(parseInt(m[3], 10), parseInt(m[2], 10) - 1, parseInt(m[1], 10)),
    },
    {
      // DD-MM-YYYY
      regex: /^(\d{2})-(\d{2})-(\d{4})$/,
      parse: (m) => new Date(parseInt(m[3], 10), parseInt(m[2], 10) - 1, parseInt(m[1], 10)),
    },
    {
      // DD.MM.YYYY
      regex: /^(\d{2})\.(\d{2})\.(\d{4})$/,
      parse: (m) => new Date(parseInt(m[3], 10), parseInt(m[2], 10) - 1, parseInt(m[1], 10)),
    },
    {
      // YYYY-MM-DD
      regex: /^(\d{4})-(\d{2})-(\d{2})$/,
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
