import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, forkJoin, of, throwError } from 'rxjs';
import { formatearPesos, mensajeError } from '../../../../core/format/moneda';
import { AfiliadoTabsComponent } from '../../../afiliados/components/afiliado-tabs/afiliado-tabs.component';
import { AfiliadoDetalle } from '../../../afiliados/models/afiliado.models';
import { AfiliadoApiService } from '../../../afiliados/services/afiliado-api.service';
import { PlanAfiliado, ServicioVeterinario } from '../../../planes/models/afiliacion.models';
import { AfiliacionApiService } from '../../../planes/services/afiliacion-api.service';
import { SimulacionApiService } from '../../../planes/services/simulacion-api.service';
import { Bono, CotizacionBono } from '../../models/bono.models';
import { BonoApiService } from '../../services/bono-api.service';

@Component({
  selector: 'app-bonos-afiliado',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, AfiliadoTabsComponent],
  templateUrl: './bonos-afiliado.component.html'
})
export class BonosAfiliadoComponent implements OnInit {
  private readonly afiliadoApi = inject(AfiliadoApiService);
  private readonly afiliacionApi = inject(AfiliacionApiService);
  private readonly simulacionApi = inject(SimulacionApiService);
  private readonly bonoApi = inject(BonoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly formatearPesos = formatearPesos;
  readonly compraForm = new FormGroup({
    mascotaId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    servicioId: new FormControl<number | null>(null, { validators: [Validators.required] })
  });

  afiliadoId = 0;
  afiliado: AfiliadoDetalle | null = null;
  plan: PlanAfiliado | null = null;
  servicios: ServicioVeterinario[] = [];
  bonos: Bono[] = [];
  cotizacion: CotizacionBono | null = null;
  cargando = true;
  cotizando = false;
  comprando = false;
  error = '';
  errorCompra = '';
  exito = '';

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.afiliadoId = Number(params.get('id'));
      this.cargar();
    });
    this.compraForm.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.cotizar());
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      afiliado: this.afiliadoApi.obtenerAfiliado(this.afiliadoId),
      plan: this.afiliacionApi.obtenerPlan(this.afiliadoId).pipe(
        catchError((error: HttpErrorResponse) => error.status === 404 ? of(null) : throwError(() => error))),
      servicios: this.simulacionApi.listarServicios(),
      bonos: this.bonoApi.listar(this.afiliadoId)
    }).subscribe({
      next: ({ afiliado, plan, servicios, bonos }) => {
        this.afiliado = afiliado;
        this.plan = plan;
        this.servicios = servicios;
        this.bonos = bonos;
        this.cargando = false;
      },
      error: (error: HttpErrorResponse) => {
        this.error = error.status === 404
          ? 'No se encontró el afiliado solicitado.'
          : mensajeError(error, 'No se pudo cargar la información de bonos.');
        this.cargando = false;
      }
    });
  }

  cotizar(): void {
    const { mascotaId, servicioId } = this.compraForm.getRawValue();
    this.cotizacion = null;
    this.errorCompra = '';
    if (mascotaId === null || servicioId === null) {
      return;
    }
    this.cotizando = true;
    this.bonoApi.cotizar(this.afiliadoId, mascotaId, servicioId).subscribe({
      next: cotizacion => {
        this.cotizacion = cotizacion;
        this.cotizando = false;
      },
      error: error => {
        this.errorCompra = mensajeError(error, 'No se pudo calcular el valor del bono.');
        this.cotizando = false;
      }
    });
  }

  comprar(): void {
    const { mascotaId, servicioId } = this.compraForm.getRawValue();
    if (mascotaId === null || servicioId === null || !this.cotizacion) {
      this.compraForm.markAllAsTouched();
      return;
    }
    this.comprando = true;
    this.exito = '';
    this.bonoApi.comprar(this.afiliadoId, { mascotaId, servicioId }).subscribe({
      next: bono => {
        this.bonos = [bono, ...this.bonos];
        this.exito = `Bono ${bono.codigo} comprado por ${formatearPesos(bono.valorPagado)}.`;
        this.comprando = false;
        this.compraForm.reset({ mascotaId: null, servicioId: null }, { emitEvent: false });
        this.cotizacion = null;
        this.refrescarPlan();
      },
      error: error => {
        this.errorCompra = mensajeError(error, 'No se pudo comprar el bono. Inténtalo nuevamente.');
        this.comprando = false;
      }
    });
  }

  private refrescarPlan(): void {
    this.afiliacionApi.obtenerPlan(this.afiliadoId).subscribe({ next: plan => this.plan = plan, error: () => undefined });
  }
}
