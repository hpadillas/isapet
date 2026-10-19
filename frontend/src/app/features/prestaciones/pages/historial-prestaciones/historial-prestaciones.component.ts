import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { fechaLocalHoy, formatearPesos, mensajeError } from '../../../../core/format/moneda';
import { AfiliadoTabsComponent } from '../../../afiliados/components/afiliado-tabs/afiliado-tabs.component';
import { AfiliadoDetalle, CrearMascotaResponse } from '../../../afiliados/models/afiliado.models';
import { AfiliadoApiService } from '../../../afiliados/services/afiliado-api.service';
import { Bono } from '../../../bonos/models/bono.models';
import { BonoApiService } from '../../../bonos/services/bono-api.service';
import { ServicioVeterinario } from '../../../planes/models/afiliacion.models';
import { SimulacionApiService } from '../../../planes/services/simulacion-api.service';
import { Prestacion } from '../../models/prestacion.models';
import { PrestacionApiService } from '../../services/prestacion-api.service';

@Component({
  selector: 'app-historial-prestaciones',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, AfiliadoTabsComponent],
  templateUrl: './historial-prestaciones.component.html'
})
export class HistorialPrestacionesComponent implements OnInit {
  private readonly afiliadoApi = inject(AfiliadoApiService);
  private readonly simulacionApi = inject(SimulacionApiService);
  private readonly bonoApi = inject(BonoApiService);
  private readonly prestacionApi = inject(PrestacionApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly formatearPesos = formatearPesos;
  readonly hoy = fechaLocalHoy();
  readonly filtroMascota = new FormControl<number | null>(null);
  readonly prestacionForm = new FormGroup({
    mascotaId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    servicioId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    fecha: new FormControl(fechaLocalHoy(), { nonNullable: true, validators: [Validators.required] }),
    prestador: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(150)] }),
    observaciones: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
    bonoId: new FormControl<number | null>(null)
  });

  afiliadoId = 0;
  afiliado: AfiliadoDetalle | null = null;
  mascotas: CrearMascotaResponse[] = [];
  servicios: ServicioVeterinario[] = [];
  bonos: Bono[] = [];
  prestaciones: Prestacion[] = [];
  cargando = true;
  filtrando = false;
  guardando = false;
  mostrarFormulario = false;
  error = '';
  errorFormulario = '';
  exito = '';

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.afiliadoId = Number(params.get('id'));
      this.cargar();
    });
    this.filtroMascota.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.consultarHistorial());
    // Un bono solo sirve para su mascota y servicio: al cambiarlos, se descarta el bono elegido.
    this.prestacionForm.controls.mascotaId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.prestacionForm.controls.bonoId.setValue(null));
    this.prestacionForm.controls.servicioId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.prestacionForm.controls.bonoId.setValue(null));
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({
      afiliado: this.afiliadoApi.obtenerAfiliado(this.afiliadoId),
      mascotas: this.afiliadoApi.listarMascotas(this.afiliadoId),
      servicios: this.simulacionApi.listarServicios(),
      bonos: this.bonoApi.listar(this.afiliadoId),
      prestaciones: this.prestacionApi.listar(this.afiliadoId)
    }).subscribe({
      next: resultado => {
        this.afiliado = resultado.afiliado;
        this.mascotas = resultado.mascotas;
        this.servicios = resultado.servicios;
        this.bonos = resultado.bonos;
        this.prestaciones = resultado.prestaciones;
        this.cargando = false;
      },
      error: (error: HttpErrorResponse) => {
        this.error = error.status === 404
          ? 'No se encontró el afiliado solicitado.'
          : mensajeError(error, 'No se pudo cargar el historial de prestaciones.');
        this.cargando = false;
      }
    });
  }

  consultarHistorial(): void {
    this.filtrando = true;
    this.prestacionApi.listar(this.afiliadoId, this.filtroMascota.value).subscribe({
      next: prestaciones => {
        this.prestaciones = prestaciones;
        this.filtrando = false;
      },
      error: error => {
        this.error = mensajeError(error, 'No se pudo consultar el historial.');
        this.filtrando = false;
      }
    });
  }

  get bonosAplicables(): Bono[] {
    const { mascotaId, servicioId } = this.prestacionForm.getRawValue();
    return this.bonos.filter(bono =>
      bono.estado === 'DISPONIBLE' && bono.mascotaId === mascotaId && bono.servicioId === servicioId);
  }

  abrirFormulario(): void {
    this.exito = '';
    this.errorFormulario = '';
    this.prestacionForm.reset({ mascotaId: this.filtroMascota.value, servicioId: null, fecha: fechaLocalHoy(), prestador: '', observaciones: '', bonoId: null });
    this.mostrarFormulario = true;
  }

  registrar(): void {
    this.errorFormulario = '';
    const valor = this.prestacionForm.getRawValue();
    if (this.prestacionForm.invalid || valor.mascotaId === null || valor.servicioId === null) {
      this.prestacionForm.markAllAsTouched();
      this.errorFormulario = 'Completa mascota, servicio, fecha y prestador.';
      return;
    }
    if (valor.fecha > this.hoy) {
      this.errorFormulario = 'La fecha de la prestación no puede ser futura.';
      return;
    }
    this.guardando = true;
    this.prestacionApi.registrar(this.afiliadoId, {
      mascotaId: valor.mascotaId,
      servicioId: valor.servicioId,
      fecha: valor.fecha,
      prestador: valor.prestador.trim(),
      observaciones: valor.observaciones.trim() || null,
      bonoId: valor.bonoId
    }).subscribe({
      next: prestacion => {
        this.exito = `Prestación de ${prestacion.servicioNombre} para ${prestacion.mascotaNombre} registrada.`;
        this.guardando = false;
        this.mostrarFormulario = false;
        if (prestacion.bonoId !== null) {
          this.bonos = this.bonos.map(bono => bono.id === prestacion.bonoId ? { ...bono, estado: 'USADO' } : bono);
        }
        this.consultarHistorial();
      },
      error: error => {
        this.errorFormulario = mensajeError(error, 'No se pudo registrar la prestación. Inténtalo nuevamente.');
        this.guardando = false;
      }
    });
  }
}
