/* eslint-disable */

import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';

import { BaseService } from '../base-service';
import { ApiConfiguration } from '../api-configuration';
import { StrictHttpResponse } from '../strict-http-response';

import { FutureEventResponse } from '../models/future-event-response';
import { FutureEventCreateRequest } from '../models/future-event-create-request';
import { FutureEventUpdateRequest } from '../models/future-event-update-request';
import { ProjectionResponse } from '../models/projection-response';

/**
 * Manage recurring future events and budget projections
 */
@Injectable({ providedIn: 'root' })
export class FutureEventsService extends BaseService {
  constructor(config: ApiConfiguration, http: HttpClient) {
    super(config, http);
  }

  /** Path part for operation `getAllFutureEvents()` */
  static readonly GetAllFutureEventsPath = '/api/future-events';

  /**
   * Get all future events for the current user's household.
   */
  getAllFutureEvents$Response(context?: HttpContext): Observable<StrictHttpResponse<FutureEventResponse[]>> {
    return this.http.get<FutureEventResponse[]>(`${this.rootUrl}${FutureEventsService.GetAllFutureEventsPath}`, {
      observe: 'response',
      context,
    }) as Observable<StrictHttpResponse<FutureEventResponse[]>>;
  }

  getAllFutureEvents(context?: HttpContext): Promise<FutureEventResponse[]> {
    return firstValueFrom(this.getAllFutureEvents$Response(context)).then(
      (r: StrictHttpResponse<FutureEventResponse[]>) => r.body
    );
  }

  /** Path part for operation `getFutureEvent()` */
  static readonly GetFutureEventPath = '/api/future-events/{id}';

  /**
   * Get a specific future event by ID.
   */
  getFutureEvent$Response(params: { id: string }, context?: HttpContext): Observable<StrictHttpResponse<FutureEventResponse>> {
    return this.http.get<FutureEventResponse>(`${this.rootUrl}/api/future-events/${params.id}`, {
      observe: 'response',
      context,
    }) as Observable<StrictHttpResponse<FutureEventResponse>>;
  }

  getFutureEvent(params: { id: string }, context?: HttpContext): Promise<FutureEventResponse> {
    return firstValueFrom(this.getFutureEvent$Response(params, context)).then(
      (r: StrictHttpResponse<FutureEventResponse>) => r.body
    );
  }

  /** Path part for operation `createFutureEvent()` */
  static readonly CreateFutureEventPath = '/api/future-events';

  /**
   * Create a new future event.
   */
  createFutureEvent$Response(params: { body: FutureEventCreateRequest }, context?: HttpContext): Observable<StrictHttpResponse<FutureEventResponse>> {
    return this.http.post<FutureEventResponse>(`${this.rootUrl}${FutureEventsService.CreateFutureEventPath}`, params.body, {
      observe: 'response',
      context,
    }) as Observable<StrictHttpResponse<FutureEventResponse>>;
  }

  createFutureEvent(params: { body: FutureEventCreateRequest }, context?: HttpContext): Promise<FutureEventResponse> {
    return firstValueFrom(this.createFutureEvent$Response(params, context)).then(
      (r: StrictHttpResponse<FutureEventResponse>) => r.body
    );
  }

  /** Path part for operation `updateFutureEvent()` */
  static readonly UpdateFutureEventPath = '/api/future-events/{id}';

  /**
   * Update an existing future event.
   */
  updateFutureEvent$Response(params: { id: string; body: FutureEventUpdateRequest }, context?: HttpContext): Observable<StrictHttpResponse<FutureEventResponse>> {
    return this.http.put<FutureEventResponse>(`${this.rootUrl}/api/future-events/${params.id}`, params.body, {
      observe: 'response',
      context,
    }) as Observable<StrictHttpResponse<FutureEventResponse>>;
  }

  updateFutureEvent(params: { id: string; body: FutureEventUpdateRequest }, context?: HttpContext): Promise<FutureEventResponse> {
    return firstValueFrom(this.updateFutureEvent$Response(params, context)).then(
      (r: StrictHttpResponse<FutureEventResponse>) => r.body
    );
  }

  /** Path part for operation `deleteFutureEvent()` */
  static readonly DeleteFutureEventPath = '/api/future-events/{id}';

  /**
   * Delete a future event.
   */
  deleteFutureEvent$Response(params: { id: string }, context?: HttpContext): Observable<StrictHttpResponse<void>> {
    return this.http.delete<void>(`${this.rootUrl}/api/future-events/${params.id}`, {
      observe: 'response',
      context,
    }) as Observable<StrictHttpResponse<void>>;
  }

  deleteFutureEvent(params: { id: string }, context?: HttpContext): Promise<void> {
    return firstValueFrom(this.deleteFutureEvent$Response(params, context)).then(() => undefined);
  }

  /** Path part for operation `getProjections()` */
  static readonly GetProjectionsPath = '/api/future-events/projections';

  /**
   * Get budget projections for 1-12 months.
   */
  getProjections$Response(params: { months?: number }, context?: HttpContext): Observable<StrictHttpResponse<ProjectionResponse>> {
    let httpParams = new HttpParams();
    if (params.months !== undefined) {
      httpParams = httpParams.set('months', params.months.toString());
    }
    
    return this.http.get<ProjectionResponse>(`${this.rootUrl}${FutureEventsService.GetProjectionsPath}`, {
      observe: 'response',
      params: httpParams,
      context,
    }) as Observable<StrictHttpResponse<ProjectionResponse>>;
  }

  getProjections(params: { months?: number }, context?: HttpContext): Promise<ProjectionResponse> {
    return firstValueFrom(this.getProjections$Response(params, context)).then(
      (r: StrictHttpResponse<ProjectionResponse>) => r.body
    );
  }
}
