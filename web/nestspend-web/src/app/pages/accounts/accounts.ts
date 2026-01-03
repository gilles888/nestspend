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

import { AccountsService } from '../../core/api/services/accounts.service';
import { AccountResponse } from '../../core/api/models/account-response';

// Default account type for new accounts
const DEFAULT_ACCOUNT_TYPE: 'CASH' | 'BANK' | 'CARD' = 'BANK';

// Account type configuration (icon and color for display)
const ACCOUNT_TYPES: { value: 'CASH' | 'BANK' | 'CARD'; icon: string; color: string }[] = [
  { value: 'CASH', icon: 'pi-money-bill', color: '#22c55e' },
  { value: 'BANK', icon: 'pi-building-columns', color: '#3b82f6' },
  { value: 'CARD', icon: 'pi-credit-card', color: '#8b5cf6' },
];

@Component({
  selector: 'app-accounts',
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
  ],
  providers: [ConfirmationService, MessageService],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class AccountsComponent implements OnInit {
  loading = signal(false);
  accounts = signal<AccountResponse[]>([]);

  // Dialog state
  dialogVisible = signal(false);
  dialogMode = signal<'add' | 'edit'>('add');
  selectedAccount = signal<AccountResponse | null>(null);

  // Form
  accountForm!: FormGroup;

  // Type options for dropdown (labels come from translation in the template)
  typeOptions = ACCOUNT_TYPES.map((type) => ({
    value: type.value,
    icon: type.icon,
    color: type.color,
  }));

  constructor(
    private accountsService: AccountsService,
    private confirmationService: ConfirmationService,
    private messageService: MessageService,
    private translateService: TranslateService,
    private fb: FormBuilder
  ) {
    this.initForm();
  }

  ngOnInit(): void {
    this.loadAccounts();
  }

  private initForm(): void {
    this.accountForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(80)]],
      type: [DEFAULT_ACCOUNT_TYPE, Validators.required],
    });
  }

  getTypeInfo(type: string | undefined): { icon: string; color: string } {
    const found = ACCOUNT_TYPES.find((t) => t.value === type);
    return found || { icon: 'pi-wallet', color: '#6b7280' };
  }

  async loadAccounts(): Promise<void> {
    this.loading.set(true);
    try {
      const accounts = await this.accountsService.getAllAccounts();
      this.accounts.set(Array.isArray(accounts) ? accounts : []);
    } catch (error) {
      console.error('Error loading accounts:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('accounts.loadError'),
      });
    } finally {
      this.loading.set(false);
    }
  }

  openAddDialog(): void {
    this.dialogMode.set('add');
    this.selectedAccount.set(null);
    this.accountForm.reset({
      name: '',
      type: DEFAULT_ACCOUNT_TYPE,
    });
    this.dialogVisible.set(true);
  }

  openEditDialog(account: AccountResponse): void {
    this.dialogMode.set('edit');
    this.selectedAccount.set(account);
    this.accountForm.patchValue({
      name: account.name ?? '',
      type: account.type ?? DEFAULT_ACCOUNT_TYPE,
    });
    this.dialogVisible.set(true);
  }

  closeDialog(): void {
    this.dialogVisible.set(false);
    this.selectedAccount.set(null);
  }

  isNameUnique(name: string): boolean {
    const currentId = this.selectedAccount()?.id;
    return !this.accounts().some(
      (a) => a.name?.toLowerCase() === name.toLowerCase() && a.id !== currentId
    );
  }

  async saveAccount(): Promise<void> {
    if (this.accountForm.invalid) {
      this.accountForm.markAllAsTouched();
      return;
    }

    const formValue = this.accountForm.value;

    // Validate unique name
    if (!this.isNameUnique(formValue.name)) {
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('accounts.nameExists'),
      });
      return;
    }

    const body = {
      name: formValue.name.trim(),
      type: formValue.type,
    };

    try {
      if (this.dialogMode() === 'add') {
        await this.accountsService.createAccount({ body });
        this.messageService.add({
          severity: 'success',
          summary: this.translateService.instant('common.success') || 'Success',
          detail: this.translateService.instant('accounts.createSuccess'),
        });
      } else {
        const id = this.selectedAccount()?.id;
        if (id) {
          await this.accountsService.updateAccount({ id, body });
          this.messageService.add({
            severity: 'success',
            summary: this.translateService.instant('common.success') || 'Success',
            detail: this.translateService.instant('accounts.updateSuccess'),
          });
        }
      }

      this.closeDialog();
      await this.loadAccounts();
    } catch (error) {
      console.error('Error saving account:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('accounts.saveError'),
      });
    }
  }

  confirmDelete(account: AccountResponse): void {
    this.confirmationService.confirm({
      message: this.translateService.instant('accounts.confirmDeleteMessage', { name: account.name }),
      header: this.translateService.instant('accounts.confirmDeleteHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.deleteAccount(account),
    });
  }

  private async deleteAccount(account: AccountResponse): Promise<void> {
    try {
      if (account.id) {
        await this.accountsService.deleteAccount({ id: account.id });
        this.messageService.add({
          severity: 'success',
          summary: this.translateService.instant('common.success') || 'Success',
          detail: this.translateService.instant('accounts.deleteSuccess'),
        });
        await this.loadAccounts();
      }
    } catch (error) {
      console.error('Error deleting account:', error);
      this.messageService.add({
        severity: 'error',
        summary: this.translateService.instant('common.error') || 'Error',
        detail: this.translateService.instant('accounts.deleteError'),
      });
    }
  }
}
