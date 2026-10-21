import { DatePipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { fechaLocalHoy, formatearPesos, mensajeError } from '../../../../core/format/moneda';
import { ConfirmacionDialogComponent } from '../../../../core/ui/confirmacion-dialog.component';
import { AfiliadoTabsComponent } from '../../../afiliados/components/afiliado-tabs/afiliado-tabs.component';
import { AfiliadoDetalle } from '../../../afiliados/models/afiliado.models';
import { AfiliadoApiService } from '../../../afiliados/services/afiliado-api.service';
import { CuotaPlan, etiquetaMedio, HistorialPagos, MEDIOS_PAGO, MedioPago, Pago } from '../../models/pago.models';
import { PagoApiService } from '../../services/pago-api.service';

@Component({
  selector: 'app-historial-pagos',
  standalone: true,
  imports: [DatePipe, TitleCasePipe, ReactiveFormsModule, RouterLink, AfiliadoTabsComponent, ConfirmacionDialogComponent],
  templateUrl: './historial-pagos.component.html'
})
export class HistorialPagosComponent implements OnInit {
  private readonly afiliadoApi = inject(AfiliadoApiService);
  private readonly pagoApi = inject(PagoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly formatearPesos = formatearPesos;
  readonly mediosPago = MEDIOS_PAGO;
  readonly etiquetaMedio = etiquetaMedio;
  readonly hoy = fechaLocalHoy();
  readonly pagoForm = new FormGroup({
    fechaPago: new FormControl(fechaLocalHoy(), { nonNullable: true, validators: [Validators.required] }),
    medioPago: new FormControl<MedioPago | null>(null, { validators: [Validators.required] })
  });

  afiliadoId = 0;
  afiliado: AfiliadoDetalle | null = null;
  historial: HistorialPagos | null = null;
  /** Libro de pagos del afiliado: mensualidades y bonos, manuales o en línea. */
  transacciones: Pago[] = [];
  /** Cuota cuyo pago en línea se está abriendo. */
  abriendoPasarelaId: number | null = null;
  /** Cuota cuyo pago se está registrando (abre la ventana de confirmación). */
  cuotaAPagar: CuotaPlan | null = null;
  cargando = true;
  guardando = false;
  error = '';
  errorPago = '';
  exito = '';
  avisoRechazo = '';

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.afiliadoId = Number(params.get('id'));
      this.cargar();
    });
    // Resultado al volver de la pasarela (?pago=APROBADO&ref=...).
    const resultado = this.route.snapshot.queryParamMap.get('pago');
    const referencia = this.route.snapshot.queryParamMap.get('ref');
    if (resultado === 'APROBADO') {
      this.exito = `Pago en línea ${referencia} aprobado por la pasarela. La cuota quedó pagada (si era la de activación, el plan ya está activo).`;
    } else if (resultado === 'RECHAZADO') {
      this.avisoRechazo = `La pasarela rechazó el pago ${referencia}. La cuota sigue pendiente; puedes intentarlo de nuevo.`;
    }
  }

  pagarEnLinea(cuota: CuotaPlan): void {
    this.abriendoPasarelaId = cuota.id;
    this.exito = '';
    this.avisoRechazo = '';
    this.pagoApi.pagarCuotaEnLinea(this.afiliadoId, cuota.id).subscribe({
      next: sesion => void this.router.navigateByUrl(sesion.urlPago),
      error: error => {
        this.error = mensajeError(error, 'No se pudo abrir la pasarela de pagos.');
        this.abriendoPasarelaId = null;
      }
    });
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      afiliado: this.afiliadoApi.obtenerAfiliado(this.afiliadoId),
      historial: this.pagoApi.historial(this.afiliadoId),
      transacciones: this.pagoApi.transacciones(this.afiliadoId)
    }).subscribe({
      next: ({ afiliado, historial, transacciones }) => {
        this.afiliado = afiliado;
        this.historial = historial;
        this.transacciones = transacciones;
        this.cargando = false;
      },
      error: (error: HttpErrorResponse) => {
        this.error = error.status === 404
          ? 'No se encontró el afiliado solicitado.'
          : mensajeError(error, 'No se pudo cargar el historial de pagos.');
        this.cargando = false;
      }
    });
  }

  abrirPago(cuota: CuotaPlan): void {
    this.exito = '';
    this.errorPago = '';
    this.pagoForm.reset({ fechaPago: fechaLocalHoy(), medioPago: null });
    this.cuotaAPagar = cuota;
  }

  confirmarPago(): void {
    const { fechaPago, medioPago } = this.pagoForm.getRawValue();
    if (!this.cuotaAPagar) {
      return;
    }
    if (medioPago === null || !fechaPago) {
      this.errorPago = 'Selecciona la fecha y el medio de pago.';
      return;
    }
    if (fechaPago > this.hoy) {
      this.errorPago = 'La fecha del pago no puede ser futura.';
      return;
    }
    const cuota = this.cuotaAPagar;
    this.guardando = true;
    this.errorPago = '';
    this.pagoApi.registrarPago(this.afiliadoId, cuota.id, { fechaPago, medioPago }).subscribe({
      next: pagada => {
        this.guardando = false;
        this.cuotaAPagar = null;
        this.exito = cuota.deActivacion
          ? `Pago de ${formatearPesos(pagada.valor)} registrado: el plan quedó activo desde el ${pagada.periodoDesde}.`
          : `Pago de ${formatearPesos(pagada.valor)} registrado para el periodo que inicia el ${pagada.periodoDesde}.`;
        this.pagoApi.historial(this.afiliadoId).subscribe(historial => this.historial = historial);
        this.pagoApi.transacciones(this.afiliadoId).subscribe(transacciones => this.transacciones = transacciones);
      },
      error: (error: HttpErrorResponse) => {
        this.errorPago = mensajeError(error, 'No se pudo registrar el pago. Inténtalo nuevamente.');
        this.guardando = false;
      }
    });
  }
}
