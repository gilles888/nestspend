import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { CheckboxModule } from 'primeng/checkbox';
import { SliderModule } from 'primeng/slider';
import { InputNumberModule } from 'primeng/inputnumber';

import { ClassificationRulesService } from '../../core/api/services/classification-rules.service';
import { CategoriesService } from '../../core/api/services/categories.service';
import { ClassificationRuleResponse } from '../../core/api/models/classification-rule-response';
import { CategoryResponse } from '../../core/api/models/category-response';

@Component({
  selector: 'app-classification-rules',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    TranslateModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    ConfirmDialogModule,
    ToastModule,
    TooltipModule,
    SelectModule,
    TagModule,
    CheckboxModule,
    SliderModule,
    InputNumberModule,
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './classification-rules.html',
  styleUrl: './classification-rules.scss',
})
export class ClassificationRulesComponent implements OnInit {
  loading = signal(false);
  rules = signal<ClassificationRuleResponse[]>([]);
  categories = signal<CategoryResponse[]>([]);

  // Dialog state
  dialogVisible = signal(false);
  dialogMode = signal<'add' | 'edit'>('add');
  selectedRule = signal<ClassificationRuleResponse | null>(null);

  // Form
  ruleForm!: FormGroup;

  // Field options
  fieldOptions = [
    { label: 'Merchant', value: 'MERCHANT' },
    { label: 'Communication', value: 'COMMUNICATION' },
    { label: 'IBAN', value: 'IBAN' },
  ];

  // Match type options
  matchTypeOptions = [
    { label: 'Contains', value: 'CONTAINS' },
    { label: 'Starts With', value: 'STARTS_WITH' },
    { label: 'Regex', value: 'REGEX' },
  ];

  constructor(
    private classificationRulesService: ClassificationRulesService,
    private categoriesService: CategoriesService,
    private confirmationService: ConfirmationService,
    private messageService: MessageService,
    private translateService: TranslateService,
    private fb: FormBuilder
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    this.loadCategories();
    this.loadRules();
  }

  private initForm(): void {
    this.ruleForm = this.fb.group({
      field: ['MERCHANT', Validators.required],
      matchType: ['CONTAINS', Validators.required],
      pattern: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(255)]],
      categoryId: ['', Validators.required],
      enabled: [true],
      priority: [100, [Validators.required, Validators.min(0)]],
      confidence: [80, [Validators.required, Validators.min(0), Validators.max(100)]],
    });
  }

  async loadCategories(): Promise<void> {
    try {
      const response = await this.categoriesService.getAllCategories$Response();
      let categories = response.body;

      // Handle Blob response from OpenAPI spec (same pattern as categories page)
      if (categories instanceof Blob) {
        const text = await categories.text();
        categories = JSON.parse(text);
      }

      this.categories.set(Array.isArray(categories) ? categories : []);
    } catch (error) {
      console.error('Error loading categories:', error);
      this.messageService.add({
        severity: 'warn',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('categories.loadError'),
      });
    }
  }

  async loadRules(): Promise<void> {
    this.loading.set(true);
    try {
      const rules = await this.classificationRulesService.getAllRules();
      this.rules.set(Array.isArray(rules) ? rules : []);
    } catch (error) {
      console.error('Error loading classification rules:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('classificationRules.loadError'),
      });
    } finally {
      this.loading.set(false);
    }
  }

  getCategoryOptions(): { label: string; value: string }[] {
    return this.categories().map((c) => ({
      label: c.name || 'Unknown',
      value: c.id || '',
    }));
  }

  openAddDialog(): void {
    this.dialogMode.set('add');
    this.selectedRule.set(null);
    this.ruleForm.reset({
      field: 'MERCHANT',
      matchType: 'CONTAINS',
      pattern: '',
      categoryId: '',
      enabled: true,
      priority: 100,
      confidence: 80,
    });
    this.dialogVisible.set(true);
  }

  openEditDialog(rule: ClassificationRuleResponse): void {
    this.dialogMode.set('edit');
    this.selectedRule.set(rule);
    this.ruleForm.patchValue({
      field: rule.field ?? 'MERCHANT',
      matchType: rule.matchType ?? 'CONTAINS',
      pattern: rule.pattern ?? '',
      categoryId: rule.categoryId ?? '',
      enabled: rule.enabled ?? true,
      priority: rule.priority ?? 100,
      confidence: rule.confidence ?? 80,
    });
    this.dialogVisible.set(true);
  }

  closeDialog(): void {
    this.dialogVisible.set(false);
    this.selectedRule.set(null);
  }

  async saveRule(): Promise<void> {
    if (this.ruleForm.invalid) {
      this.ruleForm.markAllAsTouched();
      return;
    }

    const formValue = this.ruleForm.value;

    const body = {
      field: formValue.field as 'MERCHANT' | 'COMMUNICATION' | 'IBAN',
      matchType: formValue.matchType as 'CONTAINS' | 'STARTS_WITH' | 'REGEX',
      pattern: formValue.pattern.trim(),
      categoryId: formValue.categoryId,
      enabled: formValue.enabled,
      priority: formValue.priority,
      confidence: formValue.confidence,
    };

    try {
      if (this.dialogMode() === 'add') {
        await this.classificationRulesService.createRule({ body });
        this.messageService.add({
          severity: 'success',
          summary: this.translateService.instant('common.success') || 'Success',
          detail: this.translateService.instant('classificationRules.createSuccess'),
        });
      } else {
        const id = this.selectedRule()?.id;
        if (id) {
          await this.classificationRulesService.updateRule({ id, body });
          this.messageService.add({
            severity: 'success',
            summary: this.translateService.instant('common.success') || 'Success',
            detail: this.translateService.instant('classificationRules.updateSuccess'),
          });
        }
      }

      this.closeDialog();
      await this.loadRules();
    } catch (error) {
      console.error('Error saving rule:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('classificationRules.saveError'),
      });
    }
  }

  confirmDelete(rule: ClassificationRuleResponse): void {
    this.confirmationService.confirm({
      message: this.translateService.instant('classificationRules.confirmDeleteMessage', {
        pattern: rule.pattern,
      }),
      header: this.translateService.instant('classificationRules.confirmDeleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteRule(rule),
    });
  }

  private async deleteRule(rule: ClassificationRuleResponse): Promise<void> {
    try {
      if (rule.id) {
        await this.classificationRulesService.deleteRule({ id: rule.id });
        this.messageService.add({
          severity: 'success',
          summary: this.translateService.instant('common.success') || 'Success',
          detail: this.translateService.instant('classificationRules.deleteSuccess'),
        });
        await this.loadRules();
      }
    } catch (error) {
      console.error('Error deleting rule:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('classificationRules.deleteError'),
      });
    }
  }

  getFieldLabel(field: string | undefined): string {
    const option = this.fieldOptions.find((o) => o.value === field);
    return option?.label || field || '-';
  }

  getMatchTypeLabel(matchType: string | undefined): string {
    const option = this.matchTypeOptions.find((o) => o.value === matchType);
    return option?.label || matchType || '-';
  }

  getConfidenceSeverity(
    confidence: number | undefined
  ): 'success' | 'warn' | 'danger' | 'secondary' {
    if (confidence === undefined) return 'secondary';
    if (confidence >= 80) return 'success';
    if (confidence >= 60) return 'warn';
    return 'danger';
  }

  getConfidenceLabel(confidence: number | undefined): string {
    if (confidence === undefined) return '-';
    if (confidence >= 80) return 'HIGH';
    if (confidence >= 60) return 'MEDIUM';
    return 'LOW';
  }
}
