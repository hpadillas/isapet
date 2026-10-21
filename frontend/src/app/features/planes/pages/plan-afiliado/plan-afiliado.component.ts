import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of, throwError } from 'rxjs';
import { fechaLocalHoy, formatearPesos, mensajeError, sumarDias } from '../../../../core/format/moneda';
import { ConfirmacionDialogComponent } from '../../../../core/ui/confirmacion-dialog.component';
import { AfiliadoTabsComponent } from '../../../afiliados/components/afiliado-tabs/afiliado-tabs.component';
import { AfiliadoDetalle, CrearMascotaResponse } from '../../../afiliados/models/afiliado.models';
import { AfiliadoApiService } from '../../../afiliados/services/afiliado-api.service';
import { CancelacionPlan, PlanAfiliado } from '../../models/afiliacion.models';
import { Plan } from '../../models/plan.models';
import { AfiliacionApiService } from '../../services/afiliacion-api.service';
import { PlanApiService } from '../../services/plan-api.service';

type ConfirmacionPlan = {
  tipo: 'primera' | 'mascotas' | 'cambio';
  planNuevo: string;
  mascotas: string[];
  fechaActivacion: string;
  precioNuevo: number | null;
  /** Último día del plan actual (solo en un cambio de plan). */
  finPlanActual: string | null;
  finPrimerPeriodo: string | null;
};

/** Suma meses a una fecha 'yyyy-MM-dd', ajustando al último día en los meses cortos (como el backend). */
function sumarMeses(fecha: string, meses: number): string {
  const [anio, mes, dia] = fecha.split('-').map(Number);
  const ultimoDia = new Date(Date.UTC(anio, mes - 1 + meses + 1, 0)).getUTCDate();
  return new Date(Date.UTC(anio, mes - 1 + meses, Math.min(dia, ultimoDia))).toISOString().slice(0, 10);
}

function formatoFecha(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-');
  return `${dia}/${mes}/${anio}`;
}

@Component({
  selector: 'app-plan-afiliado',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, AfiliadoTabsComponent, ConfirmacionDialogComponent],
  templateUrl: './plan-afiliado.component.html'
})
export class PlanAfiliadoComponent implements OnInit {
  private readonly afiliadoApi = inject(AfiliadoApiService);
  private readonly afiliacionApi = inject(AfiliacionApiService);
  private readonly planApi = inject(PlanApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly formatearPesos = formatearPesos;
  readonly planControl = new FormControl<number | null>(null, { validators: [Validators.required] });
  readonly form = new FormGroup({ planId: this.planControl });

  afiliadoId = 0;
  afiliado: AfiliadoDetalle | null = null;
  mascotas: CrearMascotaResponse[] = [];
  planes: Plan[] = [];
  plan: PlanAfiliado | null = null;
  mascotasSeleccionadas = new Set<number>();
  cargando = true;
  guardando = false;
  editando = false;
  error = '';
  errorFormulario = '';
  exito = '';
  /** Resumen del cambio que se va a guardar; mientras exista, se muestra la confirmación. */
  confirmacion: ConfirmacionPlan | null = null;
  confirmandoCancelacion = false;
  errorDialogo = '';
  /** Resultado previsto de cancelar el plan; mientras exista, se muestra la confirmación. */
  cancelacionPrevista: CancelacionPlan | null = null;
  consultandoCancelacion = false;
  confirmandoDeshacerCancelacion = false;

  /** Porcentaje de las atenciones incluidas del periodo que ya se usaron (0 si el plan es ilimitado). */
  get porcentajeUsoPeriodo(): number {
    if (!this.plan?.limiteAtenciones) {
      return 0;
    }
    return Math.min(100, Math.round(this.plan.atencionesIncluidasUsadas * 100 / this.plan.limiteAtenciones));
  }

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.afiliadoId = Number(params.get('id'));
      this.cargar();
    });
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      afiliado: this.afiliadoApi.obtenerAfiliado(this.afiliadoId),
      mascotas: this.afiliadoApi.listarMascotas(this.afiliadoId),
      planes: this.planApi.listarPlanes(),
      plan: this.afiliacionApi.obtenerPlan(this.afiliadoId).pipe(
        catchError((error: HttpErrorResponse) => error.status === 404 ? of(null) : throwError(() => error)))
    }).subscribe({
      next: ({ afiliado, mascotas, planes, plan }) => {
        this.afiliado = afiliado;
        this.mascotas = mascotas;
        this.planes = planes.filter(candidato => candidato.activo && candidato.versiones.length > 0);
        this.plan = plan;
        this.editando = plan === null;
        this.prepararFormulario();
        this.cargando = false;
      },
      error: (error: HttpErrorResponse) => {
        this.error = error.status === 404
          ? 'No se encontró el afiliado solicitado.'
          : mensajeError(error, 'No se pudo cargar el plan del afiliado.');
        this.cargando = false;
      }
    });
  }

  cambiarPlan(): void {
    this.exito = '';
    this.prepararFormulario();
    this.editando = true;
  }

  cancelar(): void {
    this.editando = false;
    this.errorFormulario = '';
  }

  alternarMascota(mascotaId: number, seleccionada: boolean): void {
    if (seleccionada) {
      this.mascotasSeleccionadas.add(mascotaId);
    } else {
      this.mascotasSeleccionadas.delete(mascotaId);
    }
  }

  get planElegido(): Plan | undefined {
    return this.planes.find(candidato => candidato.id === this.planControl.value);
  }

  get maximoMascotasElegido(): number | null {
    const versiones = this.planElegido?.versiones ?? [];
    return versiones.length ? versiones[versiones.length - 1].maximoMascotas : null;
  }

  /** Valida el formulario y abre la confirmación con las fechas y condiciones del cambio. */
  guardar(): void {
    this.errorFormulario = '';
    const elegido = this.planElegido;
    if (this.planControl.invalid || !elegido) {
      this.planControl.markAsTouched();
      this.errorFormulario = 'Selecciona un plan.';
      return;
    }
    if (this.mascotasSeleccionadas.size === 0) {
      this.errorFormulario = 'Selecciona al menos una mascota cubierta.';
      return;
    }
    const mascotas = this.mascotas
      .filter(mascota => this.mascotasSeleccionadas.has(mascota.id))
      .map(mascota => mascota.nombre);
    const hoy = fechaLocalHoy();

    if (!this.plan) {
      this.confirmacion = { tipo: 'primera', planNuevo: elegido.nombre, mascotas, fechaActivacion: hoy,
        precioNuevo: this.precioEn(elegido, hoy), finPlanActual: null, finPrimerPeriodo: sumarDias(sumarMeses(hoy, 1), -1) };
    } else if (elegido.id === this.plan.planId) {
      this.confirmacion = { tipo: 'mascotas', planNuevo: elegido.nombre, mascotas, fechaActivacion: hoy,
        precioNuevo: null, finPlanActual: null, finPrimerPeriodo: null };
    } else {
      const activacion = sumarDias(this.plan.periodoHasta, 1);
      this.confirmacion = { tipo: 'cambio', planNuevo: elegido.nombre, mascotas, fechaActivacion: activacion,
        precioNuevo: this.precioEn(elegido, activacion), finPlanActual: this.plan.periodoHasta,
        finPrimerPeriodo: sumarDias(sumarMeses(activacion, 1), -1) };
    }
    this.errorDialogo = '';
  }

  confirmarGuardar(): void {
    const planId = this.planControl.value;
    const confirmacion = this.confirmacion;
    if (planId === null || !confirmacion) {
      return;
    }
    this.guardando = true;
    this.errorDialogo = '';
    this.afiliacionApi.asignarPlan(this.afiliadoId, {
      planId,
      mascotaIds: [...this.mascotasSeleccionadas]
    }).subscribe({
      next: plan => {
        this.plan = plan;
        this.editando = false;
        this.guardando = false;
        this.confirmacion = null;
        this.exito = confirmacion.tipo === 'cambio'
          ? `Cambio programado: el plan ${confirmacion.planNuevo} se activará el ${formatoFecha(confirmacion.fechaActivacion)}.`
          : confirmacion.tipo === 'mascotas'
            ? 'Mascotas cubiertas actualizadas.'
            : `Plan ${plan.nombre} asignado. Se activará al pagar la primera cuota en la pestaña Pagos.`;
      },
      error: error => {
        this.errorDialogo = mensajeError(error, 'No se pudo guardar el plan. Inténtalo nuevamente.');
        this.guardando = false;
      }
    });
  }

  prepararCancelacionPlan(): void {
    this.exito = '';
    this.errorDialogo = '';
    this.consultandoCancelacion = true;
    this.afiliacionApi.simularCancelacion(this.afiliadoId).subscribe({
      next: prevista => {
        this.cancelacionPrevista = prevista;
        this.consultandoCancelacion = false;
      },
      error: error => {
        this.error = mensajeError(error, 'No se pudo preparar la cancelación del plan.');
        this.consultandoCancelacion = false;
      }
    });
  }

  confirmarCancelacionPlan(): void {
    this.guardando = true;
    this.errorDialogo = '';
    this.afiliacionApi.cancelarPlan(this.afiliadoId).subscribe({
      next: cancelacion => {
        this.guardando = false;
        this.cancelacionPrevista = null;
        if (cancelacion.inmediata) {
          this.plan = null;
          this.editando = true;
          this.prepararFormulario();
          this.exito = `El plan ${cancelacion.plan} quedó cancelado hoy.`
            + (cancelacion.valorAnulado > 0 ? ` Se anuló la cuota pendiente de ${formatearPesos(cancelacion.valorAnulado)}.` : '')
            + (cancelacion.deudaPendiente > 0 ? ` Quedan ${formatearPesos(cancelacion.deudaPendiente)} en cuotas vencidas por pagar.` : '');
        } else {
          this.exito = `Cancelación registrada: el plan ${cancelacion.plan} sigue activo hasta el ${formatoFecha(cancelacion.ultimoDia)}.`;
          this.afiliacionApi.obtenerPlan(this.afiliadoId).subscribe(plan => this.plan = plan);
        }
      },
      error: error => {
        this.errorDialogo = mensajeError(error, 'No se pudo cancelar el plan.');
        this.guardando = false;
      }
    });
  }

  deshacerCancelacion(): void {
    this.guardando = true;
    this.errorDialogo = '';
    this.afiliacionApi.deshacerCancelacion(this.afiliadoId).subscribe({
      next: plan => {
        this.plan = plan;
        this.guardando = false;
        this.confirmandoDeshacerCancelacion = false;
        this.exito = `Se deshizo la cancelación. El plan ${plan.nombre} sigue activo normalmente.`;
      },
      error: error => {
        this.errorDialogo = mensajeError(error, 'No se pudo deshacer la cancelación.');
        this.guardando = false;
      }
    });
  }

  cancelarCambioProgramado(): void {
    this.guardando = true;
    this.errorDialogo = '';
    this.afiliacionApi.cancelarCambioProgramado(this.afiliadoId).subscribe({
      next: plan => {
        this.plan = plan;
        this.guardando = false;
        this.confirmandoCancelacion = false;
        this.exito = `Se canceló el cambio de plan. ${plan.nombre} sigue vigente.`;
      },
      error: error => {
        this.errorDialogo = mensajeError(error, 'No se pudo cancelar el cambio de plan.');
        this.guardando = false;
      }
    });
  }

  nombresMascotas(mascotas: { nombre: string }[]): string {
    return mascotas.map(mascota => mascota.nombre).join(', ');
  }

  /** Precio mensual de la versión del plan vigente en la fecha indicada. */
  private precioEn(plan: Plan, fecha: string): number | null {
    const vigentes = plan.versiones.filter(version => version.vigenteDesde <= fecha);
    return vigentes.length ? vigentes[vigentes.length - 1].precioMensual : null;
  }

  private prepararFormulario(): void {
    this.planControl.setValue(this.plan?.planId ?? null);
    this.mascotasSeleccionadas = new Set(this.plan?.mascotasCubiertas.map(mascota => mascota.id) ?? []);
    this.errorFormulario = '';
  }
}
