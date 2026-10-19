import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of, throwError } from 'rxjs';
import { formatearPesos, mensajeError } from '../../../../core/format/moneda';
import { AfiliadoTabsComponent } from '../../../afiliados/components/afiliado-tabs/afiliado-tabs.component';
import { AfiliadoDetalle, CrearMascotaResponse } from '../../../afiliados/models/afiliado.models';
import { AfiliadoApiService } from '../../../afiliados/services/afiliado-api.service';
import { PlanAfiliado } from '../../models/afiliacion.models';
import { Plan } from '../../models/plan.models';
import { AfiliacionApiService } from '../../services/afiliacion-api.service';
import { PlanApiService } from '../../services/plan-api.service';

@Component({
  selector: 'app-plan-afiliado',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AfiliadoTabsComponent],
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

  guardar(): void {
    this.errorFormulario = '';
    if (this.planControl.invalid || this.planControl.value === null) {
      this.planControl.markAsTouched();
      this.errorFormulario = 'Selecciona un plan.';
      return;
    }
    if (this.mascotasSeleccionadas.size === 0) {
      this.errorFormulario = 'Selecciona al menos una mascota cubierta.';
      return;
    }
    this.guardando = true;
    this.afiliacionApi.asignarPlan(this.afiliadoId, {
      planId: this.planControl.value,
      mascotaIds: [...this.mascotasSeleccionadas]
    }).subscribe({
      next: plan => {
        this.plan = plan;
        this.editando = false;
        this.guardando = false;
        this.exito = `Plan ${plan.nombre} asignado correctamente.`;
      },
      error: error => {
        this.errorFormulario = mensajeError(error, 'No se pudo asignar el plan. Inténtalo nuevamente.');
        this.guardando = false;
      }
    });
  }

  private prepararFormulario(): void {
    this.planControl.setValue(this.plan?.planId ?? null);
    this.mascotasSeleccionadas = new Set(this.plan?.mascotasCubiertas.map(mascota => mascota.id) ?? []);
    this.errorFormulario = '';
  }
}
