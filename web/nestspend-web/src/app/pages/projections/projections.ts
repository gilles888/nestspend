import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';

import { TableModule } from 'primeng/table';
import { ChartModule } from 'primeng/chart';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { DatePickerModule } from 'primeng/datepicker';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';

import { FutureEventsService } from '../../core/api/services/future-events.service';
import { FutureEventResponse } from '../../core/api/models/future-event-response';
import { FutureEventCreateRequest } from '../../core/api/models/future-event-create-request';
import { FutureEventUpdateRequest } from '../../core/api/models/future-event-update-request';
import { ProjectionResponse } from '../../core/api/models/projection-response';
import { ProjectionDataPoint } from '../../core/api/models/projection-data-point';

interface EventForm {
  name: string;
  amountCents: number;
  type: string;
  periodicity: string;
  startDate: Date | null;
  endDate: Date | null;
}

@Component({
  selector: 'app-projections',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    DatePipe,
    TranslateModule,
    FormsModule,
    TableModule,
    ChartModule,
    ButtonModule,
    SelectModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    DatePickerModule,
    ConfirmDialogModule,
    ToastModule,
    TooltipModule,
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './projections.html',
  styleUrl: './projections.scss',
})
export class ProjectionsComponent implements OnInit {
  loading = signal(false);
  eventsLoading = signal(false);
  projectionData = signal<ProjectionResponse | null>(null);
  futureEvents = signal<FutureEventResponse[]>([]);
  errorMessage = signal<string | null>(null);

  // Projection period options
  selectedMonths = signal<number>(6);
  monthOptions = [
    { label: '1 month', value: 1 },
    { label: '3 months', value: 3 },
    { label: '6 months', value: 6 },
    { label: '12 months', value: 12 },
  ];

  // Event dialog
  eventDialogVisible = signal(false);
  editingEvent = signal<FutureEventResponse | null>(null);
  eventForm: EventForm = this.getEmptyForm();
  submitting = signal(false);

  typeOptions = [
    { label: 'Income', value: 'INCOME' },
    { label: 'Expense', value: 'EXPENSE' },
  ];

  periodicityOptions = [
    { label: 'Weekly', value: 'WEEKLY' },
    { label: 'Monthly', value: 'MONTHLY' },
    { label: 'Quarterly', value: 'QUARTERLY' },
    { label: 'Yearly', value: 'YEARLY' },
  ];

  // Chart data
  chartData = computed(() => {
    const data = this.projectionData();
    if (!data || !data.dataPoints || data.dataPoints.length === 0) {
      return null;
    }

    const labels = data.dataPoints.map((dp) => {
      if (!dp.date) {
        return '';
      }
      const date = new Date(dp.date);
      return date.toLocaleDateString('default', { month: 'short', year: 'numeric' });
    });

    return {
      labels,
      datasets: [
        {
          label: 'Balance',
          data: data.dataPoints.map((dp) => (dp.projectedBalanceCents ?? 0) / 100),
          fill: true,
          borderColor: '#3B82F6',
          backgroundColor: 'rgba(59, 130, 246, 0.1)',
          tension: 0.4,
        },
        {
          label: 'Income',
          data: data.dataPoints.map((dp) => (dp.projectedIncomeCents ?? 0) / 100),
          fill: false,
          borderColor: '#10B981',
          tension: 0.4,
        },
        {
          label: 'Expenses',
          data: data.dataPoints.map((dp) => (dp.projectedExpenseCents ?? 0) / 100),
          fill: false,
          borderColor: '#EF4444',
          tension: 0.4,
        },
      ],
    };
  });

  chartOptions = {
    plugins: {
      legend: {
        position: 'top',
        labels: {
          usePointStyle: true,
          padding: 20,
        },
      },
      tooltip: {
        callbacks: {
          label: (context: { dataset: { label: string }; raw: number }) => {
            return `${context.dataset.label}: €${context.raw.toFixed(2)}`;
          },
        },
      },
    },
    responsive: true,
    maintainAspectRatio: false,
    scales: {
      y: {
        beginAtZero: false,
        ticks: {
          callback: (value: number) => `€${value}`,
        },
      },
    },
    animation: {
      duration: 800,
    },
  };

  constructor(
    private futureEventsService: FutureEventsService,
    private translateService: TranslateService,
    private confirmationService: ConfirmationService,
    private messageService: MessageService
  ) {
    this.initOptions();
  }

  ngOnInit(): void {
    this.loadProjections();
    this.loadFutureEvents();
  }

  private initOptions(): void {
    this.translateService
      .get([
        'projections.monthsLabel',
        'futureEvents.income',
        'futureEvents.expense',
        'futureEvents.weekly',
        'futureEvents.monthly',
        'futureEvents.quarterly',
        'futureEvents.yearly',
      ])
      .subscribe((t) => {
        this.monthOptions = [
          { label: `1 ${t['projections.monthsLabel'] || 'month'}`, value: 1 },
          { label: `3 ${t['projections.monthsLabel'] || 'months'}`, value: 3 },
          { label: `6 ${t['projections.monthsLabel'] || 'months'}`, value: 6 },
          { label: `12 ${t['projections.monthsLabel'] || 'months'}`, value: 12 },
        ];
        this.typeOptions = [
          { label: t['futureEvents.income'] || 'Income', value: 'INCOME' },
          { label: t['futureEvents.expense'] || 'Expense', value: 'EXPENSE' },
        ];
        this.periodicityOptions = [
          { label: t['futureEvents.weekly'] || 'Weekly', value: 'WEEKLY' },
          { label: t['futureEvents.monthly'] || 'Monthly', value: 'MONTHLY' },
          { label: t['futureEvents.quarterly'] || 'Quarterly', value: 'QUARTERLY' },
          { label: t['futureEvents.yearly'] || 'Yearly', value: 'YEARLY' },
        ];
      });
  }

  async loadProjections(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);

    try {
      const response = await this.futureEventsService.getProjections({ months: this.selectedMonths() });
      this.projectionData.set(response);
    } catch (error) {
      this.errorMessage.set('projections.loadError');
      console.error('Projections load error:', error);
    } finally {
      this.loading.set(false);
    }
  }

  async loadFutureEvents(): Promise<void> {
    this.eventsLoading.set(true);

    try {
      const events = await this.futureEventsService.getAllFutureEvents();
      this.futureEvents.set(events);
    } catch (error) {
      console.error('Failed to load future events:', error);
    } finally {
      this.eventsLoading.set(false);
    }
  }

  onMonthsChange(months: number): void {
    this.selectedMonths.set(months);
    this.loadProjections();
  }

  // Event CRUD operations
  openNewEventDialog(): void {
    this.editingEvent.set(null);
    this.eventForm = this.getEmptyForm();
    this.eventDialogVisible.set(true);
  }

  openEditEventDialog(event: FutureEventResponse): void {
    this.editingEvent.set(event);
    this.eventForm = {
      name: event.name ?? '',
      amountCents: (event.amountCents ?? 0) / 100,
      type: event.type ?? 'EXPENSE',
      periodicity: event.periodicity ?? 'MONTHLY',
      startDate: event.startDate ? new Date(event.startDate) : null,
      endDate: event.endDate ? new Date(event.endDate) : null,
    };
    this.eventDialogVisible.set(true);
  }

  closeEventDialog(): void {
    this.eventDialogVisible.set(false);
    this.editingEvent.set(null);
    this.eventForm = this.getEmptyForm();
  }

  async saveEvent(): Promise<void> {
    if (!this.isFormValid() || !this.eventForm.startDate) return;

    this.submitting.set(true);

    try {
      const amountCents = Math.round(this.eventForm.amountCents * 100);
      const startDate = this.eventForm.startDate.toISOString().split('T')[0];
      const endDate = this.eventForm.endDate ? this.eventForm.endDate.toISOString().split('T')[0] : undefined;

      const editingEventValue = this.editingEvent();
      if (editingEventValue?.id) {
        const request: FutureEventUpdateRequest = {
          name: this.eventForm.name,
          amountCents,
          type: this.eventForm.type,
          periodicity: this.eventForm.periodicity,
          startDate,
          endDate,
        };
        await this.futureEventsService.updateFutureEvent({
          id: editingEventValue.id,
          body: request,
        });
        this.showSuccess('futureEvents.updateSuccess');
      } else {
        const request: FutureEventCreateRequest = {
          name: this.eventForm.name,
          amountCents,
          type: this.eventForm.type,
          periodicity: this.eventForm.periodicity,
          startDate,
          endDate,
        };
        await this.futureEventsService.createFutureEvent({ body: request });
        this.showSuccess('futureEvents.createSuccess');
      }

      this.closeEventDialog();
      await this.loadFutureEvents();
      await this.loadProjections();
    } catch (error) {
      this.showError('futureEvents.saveError');
      console.error('Save event error:', error);
    } finally {
      this.submitting.set(false);
    }
  }

  confirmDeleteEvent(event: FutureEventResponse): void {
    this.translateService
      .get(['futureEvents.confirmDeleteHeader', 'futureEvents.confirmDeleteMessage'], { name: event.name })
      .subscribe((t) => {
        this.confirmationService.confirm({
          header: t['futureEvents.confirmDeleteHeader'],
          message: t['futureEvents.confirmDeleteMessage'],
          icon: 'pi pi-exclamation-triangle',
          acceptButtonStyleClass: 'p-button-danger',
          accept: () => this.deleteEvent(event),
        });
      });
  }

  async deleteEvent(event: FutureEventResponse): Promise<void> {
    try {
      await this.futureEventsService.deleteFutureEvent({ id: event.id! });
      this.showSuccess('futureEvents.deleteSuccess');
      await this.loadFutureEvents();
      await this.loadProjections();
    } catch (error) {
      this.showError('futureEvents.deleteError');
      console.error('Delete event error:', error);
    }
  }

  private getEmptyForm(): EventForm {
    return {
      name: '',
      amountCents: 0,
      type: 'EXPENSE',
      periodicity: 'MONTHLY',
      startDate: new Date(),
      endDate: null,
    };
  }

  isFormValid(): boolean {
    return (
      this.eventForm.name.length >= 2 &&
      this.eventForm.name.length <= 120 &&
      this.eventForm.amountCents > 0 &&
      this.eventForm.startDate !== null
    );
  }

  formatCentsToAmount(cents: number | undefined): number {
    return (cents ?? 0) / 100;
  }

  getPeriodicityLabel(periodicity: string | undefined): string {
    const option = this.periodicityOptions.find((o) => o.value === periodicity);
    return option?.label ?? periodicity ?? '';
  }

  getTypeLabel(type: string | undefined): string {
    const option = this.typeOptions.find((o) => o.value === type);
    return option?.label ?? type ?? '';
  }

  private showSuccess(key: string): void {
    this.translateService.get(['common.success', key]).subscribe((t) => {
      this.messageService.add({
        severity: 'success',
        summary: t['common.success'],
        detail: t[key],
      });
    });
  }

  private showError(key: string): void {
    this.translateService.get(['common.error', key]).subscribe((t) => {
      this.messageService.add({
        severity: 'error',
        summary: t['common.error'],
        detail: t[key],
      });
    });
  }
}
