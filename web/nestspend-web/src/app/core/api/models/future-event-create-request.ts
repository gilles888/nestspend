/* eslint-disable */

/**
 * Request to create a new future event
 */
export interface FutureEventCreateRequest {
  /**
   * Name of the event
   */
  name: string;

  /**
   * Amount in cents
   */
  amountCents: number;

  /**
   * Transaction type (INCOME or EXPENSE)
   */
  type: string;

  /**
   * Recurrence periodicity (WEEKLY, MONTHLY, QUARTERLY, YEARLY)
   */
  periodicity: string;

  /**
   * Start date of the recurring event
   */
  startDate: string;

  /**
   * End date of the recurring event (optional)
   */
  endDate?: string;
}
