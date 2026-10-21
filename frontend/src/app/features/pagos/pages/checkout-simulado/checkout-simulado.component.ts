import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { formatearPesos, mensajeError } from '../../../../core/format/moneda';
import { MEDIOS_EN_LINEA, MedioPago, SesionPago } from '../../models/pago.models';
import { PagoApiService } from '../../services/pago-api.service';

/**
 * Página de pago de la pasarela simulada (entorno de pruebas). Representa la página del proveedor:
 * no pide datos de tarjeta; quien prueba elige el medio y si el pago se aprueba o se rechaza, y el
 * resultado llega a ISAPET por el webhook firmado, como con una pasarela real.
 */
@Component({
  selector: 'app-checkout-simulado',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <main class="feature-page">
      <div class="content checkout">
        <section class="gateway" aria-labelledby="gateway-heading">
          <header class="gateway-header">
            <span class="gateway-logo" aria-hidden="true">₱</span>
            <div>
              <h1 id="gateway-heading">Pasarela de pagos</h1>
              <p>Entorno de pruebas · simulación</p>
            </div>
            <span class="lock" aria-label="Conexión segura">🔒</span>
          </header>

          @if (error) {
            <section class="notice error-notice" role="alert">{{ error }}</section>
          }

          @if (sesion; as s) {
            <dl class="summary">
              <div><dt>Comercio</dt><dd>ISAPET</dd></div>
              <div><dt>Concepto</dt><dd>{{ s.descripcion }}</dd></div>
              <div><dt>Pagador</dt><dd>{{ s.afiliado }}</dd></div>
              <div><dt>Referencia</dt><dd>{{ s.referencia }}</dd></div>
              <div class="total"><dt>Total a pagar</dt><dd>{{ formatearPesos(s.monto) }}</dd></div>
            </dl>

            @if (s.estado !== 'PENDIENTE') {
              <p class="notice">Este pago ya fue procesado: <strong>{{ s.estado }}</strong>.</p>
              <button class="submit-button" type="button" (click)="volver(s, s.estado)">Volver a ISAPET</button>
            } @else {
              <fieldset class="methods">
                <legend>Medio de pago</legend>
                @for (medio of medios; track medio.valor) {
                  <label class="method"><input type="radio" [value]="medio.valor" [formControl]="medio$" /> {{ medio.etiqueta }}</label>
                }
              </fieldset>
              <p class="test-note">Simulación: no ingreses datos de tarjetas. ISAPET nunca recibe ni guarda datos de tarjetas;
                en producción los captura directamente la pasarela.</p>
              <div class="actions">
                <button class="danger-button" type="button" [disabled]="procesando" (click)="responder(s, false)">Rechazar pago</button>
                <button class="submit-button" type="button" [disabled]="procesando" (click)="responder(s, true)">
                  {{ procesando ? 'Procesando…' : 'Aprobar y pagar ' + formatearPesos(s.monto) }}
                </button>
              </div>
            }
          } @else if (!error) {
            <p class="state-message" role="status">Cargando sesión de pago…</p>
          }
        </section>
      </div>
    </main>
  `,
  styles: [`
    .checkout { max-width: 520px; }
    .gateway { margin-top: 10px; padding: 24px; border: 1px solid #c9cfe0; border-radius: 10px; background: #fff;
      box-shadow: 0 10px 30px rgb(30 40 80 / 10%); }
    .gateway-header { display: flex; align-items: center; gap: 12px; margin-bottom: 18px; padding-bottom: 14px; border-bottom: 1px solid #e3e6ef; }
    .gateway-header h1 { margin: 0; color: #1f2a5a; font: 700 18px/1.2 system-ui, sans-serif; }
    .gateway-header p { margin: 2px 0 0; color: #6a7190; font-size: 12px; }
    .gateway-logo { width: 38px; height: 38px; display: grid; place-items: center; border-radius: 8px; color: #fff; background: #3346a8; font-weight: 800; }
    .lock { margin-left: auto; }
    .summary { display: grid; gap: 8px; margin: 0 0 18px; }
    .summary div { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; }
    .summary dt { color: #6a7190; }
    .summary dd { margin: 0; color: #1f2a5a; font-weight: 600; text-align: right; }
    .summary .total { padding-top: 10px; border-top: 1px dashed #c9cfe0; }
    .summary .total dd { font-size: 20px; font-weight: 800; }
    .methods { margin: 0 0 12px; padding: 0; border: 0; }
    .methods legend { margin-bottom: 8px; color: #1f2a5a; font-size: 13px; font-weight: 700; }
    .method { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; padding: 11px 12px; border: 1px solid #c9cfe0; border-radius: 6px; cursor: pointer; font-size: 13px; }
    .test-note { margin: 0 0 16px; padding: 10px 12px; border-radius: 6px; color: #5b4a12; background: #fdf7e7; font-size: 11px; }
    .actions { display: flex; justify-content: space-between; gap: 10px; }
    .actions .submit-button { background: #3346a8; }
  `]
})
export class CheckoutSimuladoComponent implements OnInit {
  private readonly api = inject(PagoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly formatearPesos = formatearPesos;
  readonly medios = MEDIOS_EN_LINEA;
  readonly medio$ = new FormControl<MedioPago>('TARJETA', { nonNullable: true });
  sesion: SesionPago | null = null;
  procesando = false;
  error = '';

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.api.sesionSimulada(params.get('referencia') ?? '').subscribe({
        next: sesion => this.sesion = sesion,
        error: error => this.error = mensajeError(error, 'No se encontró la sesión de pago.')
      });
    });
  }

  responder(sesion: SesionPago, aprobado: boolean): void {
    this.procesando = true;
    this.error = '';
    this.api.resultadoSimulado(sesion.referencia, aprobado, this.medio$.value).subscribe({
      next: pago => this.volver(sesion, pago.estado),
      error: error => {
        this.error = mensajeError(error, 'La pasarela no pudo procesar el pago.');
        this.procesando = false;
      }
    });
  }

  /** Regresa a ISAPET, a la pantalla desde donde se inició el pago, con el resultado. */
  volver(sesion: SesionPago, estado: string): void {
    const pantalla = sesion.concepto === 'CUOTA_PLAN' ? 'pagos' : 'bonos';
    void this.router.navigate(['/afiliados', sesion.afiliadoId, pantalla], {
      queryParams: { pago: estado, ref: sesion.referencia }
    });
  }
}
