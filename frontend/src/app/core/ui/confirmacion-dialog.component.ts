import { AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild } from '@angular/core';

/**
 * Ventana modal para confirmar acciones importantes. El contenido (qué va a pasar) se proyecta dentro.
 * Escape o "Cancelar" la cierran sin hacer nada.
 */
@Component({
  selector: 'app-confirmacion-dialog',
  standalone: true,
  template: `
    <div class="backdrop" (click)="cancelar()"></div>
    <section class="dialog" role="alertdialog" aria-modal="true" [attr.aria-labelledby]="'dlg-title'" [class.danger]="peligro">
      <h2 id="dlg-title">{{ titulo }}</h2>
      <div class="body"><ng-content /></div>
      @if (error) {
        <p class="error" role="alert">{{ error }}</p>
      }
      <footer>
        <button type="button" class="cancel" [disabled]="procesando" (click)="cancelar()">Cancelar</button>
        <button #confirmButton type="button" class="confirm" [disabled]="procesando || deshabilitarConfirmar" (click)="confirmado.emit()">
          {{ procesando ? 'Procesando…' : textoConfirmar }}
        </button>
      </footer>
    </section>
  `,
  styles: [`
    :host { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 16px; }
    .backdrop { position: absolute; inset: 0; background: rgb(15 35 30 / 55%); }
    .dialog { position: relative; width: min(100%, 520px); max-height: calc(100vh - 32px); overflow-y: auto; padding: 24px; border-radius: 8px; background: #fff; box-shadow: 0 18px 50px rgb(0 0 0 / 25%); font-family: inherit; }
    .dialog.danger { border-top: 4px solid #b3412e; }
    h2 { margin: 0 0 14px; color: #17352f; font-size: 18px; }
    .body { color: #344b45; font-size: 13px; line-height: 1.55; }
    .error { margin: 14px 0 0; padding: 10px 12px; border-radius: 5px; color: #8d3026; background: #fff4f1; font-size: 12px; }
    footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 22px; }
    button { min-height: 40px; padding: 0 16px; border-radius: 5px; font-size: 12px; font-weight: 700; cursor: pointer; }
    button:disabled { cursor: not-allowed; opacity: .6; }
    .cancel { border: 1px solid #d8e0d9; color: #10473d; background: #fff; }
    .confirm { border: 0; color: #fff; background: #155c4e; }
    .danger .confirm { background: #b3412e; }
  `]
})
export class ConfirmacionDialogComponent implements AfterViewInit {
  @Input({ required: true }) titulo = '';
  @Input() textoConfirmar = 'Confirmar';
  @Input() peligro = false;
  @Input() procesando = false;
  @Input() deshabilitarConfirmar = false;
  @Input() error = '';
  @Output() readonly confirmado = new EventEmitter<void>();
  @Output() readonly cancelado = new EventEmitter<void>();

  @ViewChild('confirmButton') private confirmButton?: ElementRef<HTMLButtonElement>;

  ngAfterViewInit(): void {
    // En acciones peligrosas el foco queda en "Cancelar" para evitar confirmar con un Enter accidental.
    const host = this.confirmButton?.nativeElement.parentElement;
    const destino = this.peligro ? host?.querySelector<HTMLButtonElement>('.cancel') : this.confirmButton?.nativeElement;
    destino?.focus();
  }

  @HostListener('document:keydown.escape')
  cancelar(): void {
    if (!this.procesando) {
      this.cancelado.emit();
    }
  }
}
