/* eslint-disable */

/**
 * Single data point in budget projection
 */
export interface ProjectionDataPoint {
  /**
   * Date of this data point
   */
  date?: string;

  /**
   * Projected total income in cents for this period
   */
  projectedIncomeCents?: number;

  /**
   * Projected total expenses in cents for this period
   */
  projectedExpenseCents?: number;

  /**
   * Projected balance in cents at this date
   */
  projectedBalanceCents?: number;
}
