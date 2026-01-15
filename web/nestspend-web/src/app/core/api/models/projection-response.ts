/* eslint-disable */

import { ProjectionDataPoint } from './projection-data-point';

/**
 * Budget projection response with projected balances over time
 */
export interface ProjectionResponse {
  /**
   * Start date of the projection period
   */
  startDate?: string;

  /**
   * End date of the projection period
   */
  endDate?: string;

  /**
   * Initial balance in cents at projection start
   */
  initialBalanceCents?: number;

  /**
   * Projected balance in cents at projection end
   */
  finalBalanceCents?: number;

  /**
   * Monthly projection data points
   */
  dataPoints?: Array<ProjectionDataPoint>;
}
