/* eslint-disable */

/**
 * Future event response
 */
export interface FutureEventResponse {
  /**
   * UUID of the event
   */
  id?: string;

  /**
   * Name of the event
   */
  name?: string;

  /**
   * Amount in cents
   */
  amountCents?: number;

  /**
   * Transaction type
   */
  type?: string;

  /**
   * Recurrence periodicity
   */
  periodicity?: string;

  /**
   * Start date of the recurring event
   */
  startDate?: string;

  /**
   * End date of the recurring event (optional)
   */
  endDate?: string;
}
