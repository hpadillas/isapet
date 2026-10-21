import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { mensajeError } from '../../../../core/format/moneda';
import { ConfirmacionDialogComponent } from '../../../../core/ui/confirmacion-dialog.component';
import { ResumenEliminacionAfiliado } from '../../models/afiliado.models';
import { AfiliadoApiService } from '../../services/afiliado-api.service';

/**
 * Botón "Eliminar afiliado" con su confirmación. Antes de confirmar muestra exactamente lo que se
 * borrará, en especial la historia clínica de las mascotas, que no se puede recuperar.
 */
@Component({
  selector: 'app-eliminar-afiliado',
  standalone: true,
  imports: [ConfirmacionDialogComponent],
  template: `
    <button type="button" [class]="variante === 'enlace' ? 'link-danger' : 'button-danger'" [disabled]="cargando"
            [attr.aria-label]="'Eliminar afiliado ' + (nombre ?? '')" (click)="abrir()">
      {{ cargando ? 'Cargando…' : variante === 'enlace' ? 'Eliminar' : 'Eliminar afiliado' }}
    </button>
    @if (errorCarga) {
      <span class="load-error" role="alert">{{ errorCarga }}</span>
    }

    @if (resumen) {
      <app-confirmacion-dialog
        titulo="¿Eliminar este afiliado?"
        textoConfirmar="Sí, eliminar definitivamente"
        [peligro]="true"
        [procesando]="eliminando"
        [error]="errorEliminar"
        (confirmado)="eliminar()"
        (cancelado)="cerrar()">
        <p>Vas a eliminar a <strong>{{ resumen.nombreCompleto }}</strong> (identificación {{ resumen.numeroIdentificacion }}).
          <strong>Esta acción no se puede deshacer.</strong></p>
        <p>También se borrará de forma permanente:</p>
        <ul>
          <li>
            @if (resumen.mascotas.length) {
              {{ resumen.mascotas.length }} {{ resumen.mascotas.length === 1 ? 'mascota' : 'mascotas' }}
              ({{ resumen.mascotas.join(', ') }}) <strong>con su historia clínica</strong>:
              {{ resumen.atenciones }} {{ resumen.atenciones === 1 ? 'atención registrada' : 'atenciones registradas' }}.
            } @else {
              No tiene mascotas registradas.
            }
          </li>
          @if (resumen.plan) {
            <li>Su afiliación al plan {{ resumen.plan }}.</li>
          }
          @if (resumen.bonos) {
            <li>{{ resumen.bonos }} {{ resumen.bonos === 1 ? 'bono' : 'bonos' }}, incluidos los que no se han usado.</li>
          }
          @if (resumen.cuotas) {
            <li>El historial de pagos: {{ resumen.cuotas }} {{ resumen.cuotas === 1 ? 'cuota' : 'cuotas' }}.</li>
          }
        </ul>
        <label class="confirm-check">
          <input type="checkbox" [checked]="entendido" (change)="entendido = $any($event.target).checked" />
          Entiendo que la historia clínica de las mascotas se perderá.
        </label>
      </app-confirmacion-dialog>
    }
  `,
  styles: [`
    :host { display: inline-flex; flex-direction: column; gap: 4px; }
    .button-danger { min-height: 42px; padding: 0 15px; border: 1px solid #e3b3a8; border-radius: 5px; color: #9b3424; background: #fff; font-size: 12px; font-weight: 700; cursor: pointer; }
    .button-danger:hover:not(:disabled) { border-color: #b3412e; background: #fff4f1; }
    .link-danger { padding: 0; border: 0; color: #7b8a83; background: none; font-size: 11px; font-weight: 700; cursor: pointer; }
    .link-danger:hover:not(:disabled), .link-danger:focus-visible { color: #b3412e; text-decoration: underline; }
    button:disabled { cursor: not-allowed; opacity: .6; }
    .load-error { color: #8d3026; font-size: 11px; }
    ul { margin: 8px 0 14px; padding-left: 20px; }
    li { margin-bottom: 6px; }
    .confirm-check { display: flex; align-items: flex-start; gap: 8px; padding: 10px 12px; border-radius: 5px; background: #fff4f1; color: #8d3026; font-weight: 650; cursor: pointer; }
    .confirm-check input { margin-top: 2px; accent-color: #b3412e; }
  `]
})
export class EliminarAfiliadoComponent {
  private readonly api = inject(AfiliadoApiService);

  @Input({ required: true }) afiliadoId!: number;
  @Input() nombre?: string;
  @Input() variante: 'boton' | 'enlace' = 'boton';
  /** Emite el nombre del afiliado eliminado. */
  @Output() readonly eliminado = new EventEmitter<string>();

  resumen: ResumenEliminacionAfiliado | null = null;
  cargando = false;
  eliminando = false;
  entendido = false;
  errorCarga = '';
  errorEliminar = '';

  abrir(): void {
    this.cargando = true;
    this.errorCarga = '';
    this.api.resumenEliminacion(this.afiliadoId).subscribe({
      next: resumen => {
        this.resumen = resumen;
        this.entendido = false;
        this.errorEliminar = '';
        this.cargando = false;
      },
      error: (error: HttpErrorResponse) => {
        this.errorCarga = mensajeError(error, 'No se pudo preparar la eliminación.');
        this.cargando = false;
      }
    });
  }

  eliminar(): void {
    if (!this.resumen) {
      return;
    }
    if (!this.entendido) {
      this.errorEliminar = 'Marca la casilla para confirmar que entiendes que se perderá la historia clínica.';
      return;
    }
    const nombre = this.resumen.nombreCompleto;
    this.eliminando = true;
    this.errorEliminar = '';
    this.api.eliminarAfiliado(this.afiliadoId).subscribe({
      next: () => {
        this.eliminando = false;
        this.resumen = null;
        this.eliminado.emit(nombre);
      },
      error: (error: HttpErrorResponse) => {
        this.errorEliminar = mensajeError(error, 'No se pudo eliminar el afiliado. Inténtalo nuevamente.');
        this.eliminando = false;
      }
    });
  }

  cerrar(): void {
    this.resumen = null;
  }
}
