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
import { ColorPickerModule } from 'primeng/colorpicker';
import { SelectModule } from 'primeng/select';

import { CategoriesService } from '../../core/api/services/categories.service';
import { CategoryResponse } from '../../core/api/models/category-response';

// Default categories to seed when a user has none
const DEFAULT_CATEGORIES = [
  { name: 'Food & Dining', icon: 'pi-shopping-cart', color: '#22c55e' },
  { name: 'Transportation', icon: 'pi-car', color: '#3b82f6' },
  { name: 'Housing', icon: 'pi-home', color: '#8b5cf6' },
  { name: 'Utilities', icon: 'pi-bolt', color: '#f59e0b' },
  { name: 'Healthcare', icon: 'pi-heart', color: '#ef4444' },
  { name: 'Entertainment', icon: 'pi-play', color: '#ec4899' },
  { name: 'Shopping', icon: 'pi-shopping-bag', color: '#14b8a6' },
  { name: 'Education', icon: 'pi-book', color: '#6366f1' },
  { name: 'Salary', icon: 'pi-wallet', color: '#10b981' },
  { name: 'Investments', icon: 'pi-chart-line', color: '#0ea5e9' },
  { name: 'Other', icon: 'pi-ellipsis-h', color: '#6b7280' },
];

// Available icons for category selection
const AVAILABLE_ICONS = [
  { name: 'pi-shopping-cart', label: 'Shopping Cart' },
  { name: 'pi-car', label: 'Car' },
  { name: 'pi-home', label: 'Home' },
  { name: 'pi-bolt', label: 'Utilities' },
  { name: 'pi-heart', label: 'Heart' },
  { name: 'pi-play', label: 'Play' },
  { name: 'pi-shopping-bag', label: 'Shopping Bag' },
  { name: 'pi-book', label: 'Book' },
  { name: 'pi-wallet', label: 'Wallet' },
  { name: 'pi-chart-line', label: 'Chart' },
  { name: 'pi-ellipsis-h', label: 'Other' },
  { name: 'pi-gift', label: 'Gift' },
  { name: 'pi-briefcase', label: 'Briefcase' },
  { name: 'pi-plane', label: 'Plane' },
  { name: 'pi-building', label: 'Building' },
  { name: 'pi-phone', label: 'Phone' },
  { name: 'pi-wifi', label: 'WiFi' },
  { name: 'pi-star', label: 'Star' },
  { name: 'pi-tag', label: 'Tag' },
  { name: 'pi-money-bill', label: 'Money' },
];

@Component({
  selector: 'app-categories',
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
    ColorPickerModule,
    SelectModule,
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './categories.html',
  styleUrl: './categories.scss',
})
export class CategoriesComponent implements OnInit {
  loading = signal(false);
  categories = signal<CategoryResponse[]>([]);

  // Dialog state
  dialogVisible = signal(false);
  dialogMode = signal<'add' | 'edit'>('add');
  selectedCategory = signal<CategoryResponse | null>(null);

  // Form
  categoryForm!: FormGroup;

  // Icon options
  iconOptions = AVAILABLE_ICONS.map((icon) => ({
    label: icon.label,
    value: icon.name,
  }));

  constructor(
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
  }

  private initForm(): void {
    this.categoryForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
      icon: ['pi-tag'],
      color: ['#6366f1'],
    });
  }

  async loadCategories(): Promise<void> {
    this.loading.set(true);
    try {
      const response = await this.categoriesService.getAllCategories$Response();
      let categories = response.body;

      // Handle Blob response from OpenAPI spec
      if (categories instanceof Blob) {
        const text = await categories.text();
        categories = JSON.parse(text);
      }

      this.categories.set(Array.isArray(categories) ? categories : []);
    } catch (error) {
      console.error('Error loading categories:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('categories.loadError'),
      });
    } finally {
      this.loading.set(false);
    }
  }

  openAddDialog(): void {
    this.dialogMode.set('add');
    this.selectedCategory.set(null);
    this.categoryForm.reset({
      name: '',
      icon: 'pi-tag',
      color: '#6366f1',
    });
    this.dialogVisible.set(true);
  }

  openEditDialog(category: CategoryResponse): void {
    this.dialogMode.set('edit');
    this.selectedCategory.set(category);
    this.categoryForm.patchValue({
      name: category.name ?? '',
      icon: category.icon ?? 'pi-tag',
      color: category.color ?? '#6366f1',
    });
    this.dialogVisible.set(true);
  }

  closeDialog(): void {
    this.dialogVisible.set(false);
    this.selectedCategory.set(null);
  }

  isNameUnique(name: string): boolean {
    const currentId = this.selectedCategory()?.id;
    return !this.categories().some(
      (c) => c.name?.toLowerCase() === name.toLowerCase() && c.id !== currentId
    );
  }

  async saveCategory(): Promise<void> {
    if (this.categoryForm.invalid) {
      this.categoryForm.markAllAsTouched();
      return;
    }

    const formValue = this.categoryForm.value;

    // Validate unique name
    if (!this.isNameUnique(formValue.name)) {
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('categories.nameExists'),
      });
      return;
    }

    const body = {
      name: formValue.name.trim(),
      icon: formValue.icon || undefined,
      color: formValue.color || undefined,
    };

    try {
      if (this.dialogMode() === 'add') {
        await this.categoriesService.createCategory({ body });
        this.messageService.add({
          severity: 'success',
          summary: this.translateService.instant('common.success') || 'Success',
          detail: this.translateService.instant('categories.createSuccess'),
        });
      } else {
        const id = this.selectedCategory()?.id;
        if (id) {
          await this.categoriesService.updateCategory({ id, body });
          this.messageService.add({
            severity: 'success',
            summary: this.translateService.instant('common.success') || 'Success',
            detail: this.translateService.instant('categories.updateSuccess'),
          });
        }
      }

      this.closeDialog();
      await this.loadCategories();
    } catch (error) {
      console.error('Error saving category:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('categories.saveError'),
      });
    }
  }

  confirmDelete(category: CategoryResponse): void {
    this.confirmationService.confirm({
      message: this.translateService.instant('categories.confirmDeleteMessage', { name: category.name }),
      header: this.translateService.instant('categories.confirmDeleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteCategory(category),
    });
  }

  private async deleteCategory(category: CategoryResponse): Promise<void> {
    try {
      if (category.id) {
        await this.categoriesService.deleteCategory({ id: category.id });
        this.messageService.add({
          severity: 'success',
          summary: this.translateService.instant('common.success') || 'Success',
          detail: this.translateService.instant('categories.deleteSuccess'),
        });
        await this.loadCategories();
      }
    } catch (error) {
      console.error('Error deleting category:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('categories.deleteError'),
      });
    }
  }

  async generateDefaultCategories(): Promise<void> {
    this.loading.set(true);
    let createdCount = 0;
    let skippedCount = 0;

    try {
      for (const category of DEFAULT_CATEGORIES) {
        try {
          await this.categoriesService.createCategory({
            body: {
              name: category.name,
              icon: category.icon,
              color: category.color,
            },
          });
          createdCount++;
        } catch (error: unknown) {
          // Skip categories that already exist (conflict error) or other errors
          skippedCount++;
          console.warn(`Skipped category "${category.name}":`, error);
        }
      }

      if (createdCount > 0) {
        this.messageService.add({
          severity: 'success',
          summary: this.translateService.instant('common.success') || 'Success',
          detail: this.translateService.instant('categories.generateSuccess'),
        });
      } else if (skippedCount > 0) {
        this.messageService.add({
          severity: 'info',
          summary: this.translateService.instant('common.info') || 'Info',
          detail: this.translateService.instant('categories.categoriesExist'),
        });
      }

      await this.loadCategories();
    } catch (error) {
      console.error('Error generating default categories:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('categories.generateError'),
      });
    } finally {
      this.loading.set(false);
    }
  }
}
