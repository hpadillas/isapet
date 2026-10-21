import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { concat, last, Observable } from 'rxjs';
import { fechaLocalHoy, sumarDias } from '../../../../core/format/moneda';
import { ConfirmacionDialogComponent } from '../../../../core/ui/confirmacion-dialog.component';
import { PermitirCaracteresDirective } from '../../../../core/validation/permitir-caracteres.directive';
import { SOLO_PALABRAS } from '../../../../core/validation/campos';
import {
  ApiErrorResponse,
  CrearPlanRequest,
  CrearVersionPlanRequest,
  Plan,
  VersionPlan
} from '../../models/plan.models';
import { PlanApiService } from '../../services/plan-api.service';

type EstadoVersion = 'vigente' | 'programada' | 'historica';

/** Versión del historial con su estado respecto de hoy y lo que cambió frente a la anterior. */
interface VersionHistorial {
  version: VersionPlan;
  numero: number;
  estado: EstadoVersion;
  cambios: string[];
}

/** Lo que se muestra antes de eliminar una versión programada. */
interface ConfirmacionEliminacionVersion {
  item: VersionHistorial;
  /** Versión que queda vigente en el hueco que deja la eliminada (la inmediatamente anterior). */
  anterior: VersionHistorial | null;
  /** Nueva vigencia de la versión anterior una vez eliminada esta. */
  nuevaVigenciaAnterior: string | null;
  afiliadosConCambioProgramado: number;
}

interface ConfirmacionEdicion {
  nombreAnterior: string;
  nombreNuevo: string | null;
  condiciones: CrearVersionPlanRequest | null;
  cambios: string[];
  /** Último día de las condiciones actuales cuando empiecen las nuevas. */
  finCondicionesActuales: string | null;
}

const CODIGO = /^[A-Za-z0-9_-]+$/;
const PRECIO = /^[1-9]\d{0,13}$/;
const ENTERO_POSITIVO = /^[1-9]\d{0,9}$/;

@Component({
  selector: 'app-lista-planes',
  standalone: true,
  imports: [ReactiveFormsModule, ConfirmacionDialogComponent, PermitirCaracteresDirective],
  templateUrl: './lista-planes.component.html'
})
export class ListaPlanesComponent implements OnInit {
  private readonly api = inject(PlanApiService);

  readonly crearPlanForm = new FormGroup({
    codigo: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(30), Validators.pattern(CODIGO)] }),
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(SOLO_PALABRAS), Validators.maxLength(100)] }),
    precioMensual: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(PRECIO)] }),
    maximoMascotas: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(ENTERO_POSITIVO), Validators.max(2147483647)] }),
    limiteAtenciones: new FormControl('', { nonNullable: true, validators: [Validators.pattern(ENTERO_POSITIVO), Validators.max(2147483647)] }),
    vigenteDesde: new FormControl(fechaLocalHoy(), { nonNullable: true, validators: [Validators.required] })
  });

  readonly editarPlanForm = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(SOLO_PALABRAS), Validators.maxLength(100)] }),
    precioMensual: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(PRECIO)] }),
    maximoMascotas: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(ENTERO_POSITIVO), Validators.max(2147483647)] }),
    limiteAtenciones: new FormControl('', { nonNullable: true, validators: [Validators.pattern(ENTERO_POSITIVO), Validators.max(2147483647)] }),
    vigenteDesde: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });

  readonly fechaConsulta = new FormControl(fechaLocalHoy(), { nonNullable: true, validators: [Validators.required] });

  planes: Plan[] = [];
  /** Plan abierto abajo del catálogo, en modo detalle o edición. */
  planSeleccionado: Plan | null = null;
  modo: 'detalle' | 'editar' | null = null;
  mostrarCrear = false;
  versionAplicable: VersionPlan | null = null;
  /** Primera fecha válida para que empiecen nuevas condiciones del plan en edición. */
  fechaMinimaVersion = fechaLocalHoy();
  confirmacionEdicion: ConfirmacionEdicion | null = null;
  confirmandoDesactivacion = false;
  confirmacionEliminacionVersion: ConfirmacionEliminacionVersion | null = null;
  /** Versión cuyo impacto se está consultando antes de abrir la confirmación. */
  preparandoEliminacionId: number | null = null;
  eliminandoVersion = false;
  cargando = false;
  cargandoDetalle = false;
  consultandoVersion = false;
  creandoPlan = false;
  guardandoEdicion = false;
  desactivandoPlan = false;
  error = '';
  errorConsulta = '';
  errorDialogo = '';
  exito = '';

  ngOnInit(): void {
    this.cargarPlanes();
  }

  cargarPlanes(): void {
    this.cargando = true;
    this.error = '';
    this.api.listarPlanes().subscribe({
      next: planes => {
        this.planes = planes;
        this.cargando = false;
      },
      error: (error: unknown) => {
        this.error = this.mensajeError(error);
        this.cargando = false;
      }
    });
  }

  // --- Tarjetas del catálogo ------------------------------------------------------------

  versionVigente(plan: Plan): VersionPlan | null {
    return plan.versiones.find(version => this.estadoVersion(version) === 'vigente') ?? null;
  }

  versionProgramada(plan: Plan): VersionPlan | null {
    return plan.versiones.find(version => this.estadoVersion(version) === 'programada') ?? null;
  }

  abrirCrear(): void {
    this.mostrarCrear = true;
    this.exito = '';
    this.error = '';
  }

  cerrarCrear(): void {
    this.mostrarCrear = false;
    this.crearPlanForm.reset({ codigo: '', nombre: '', precioMensual: '', maximoMascotas: '', limiteAtenciones: '', vigenteDesde: fechaLocalHoy() });
  }

  verDetalle(planId: number): void {
    this.abrirPlan(planId, 'detalle');
  }

  editar(planId: number): void {
    this.abrirPlan(planId, 'editar');
  }

  cerrarPlan(): void {
    this.planSeleccionado = null;
    this.modo = null;
  }

  private abrirPlan(planId: number, modo: 'detalle' | 'editar'): void {
    this.cargandoDetalle = true;
    this.error = '';
    this.exito = '';
    this.versionAplicable = null;
    this.errorConsulta = '';
    this.api.obtenerPlan(planId).subscribe({
      next: plan => {
        this.mostrarPlan(plan, modo);
        this.cargandoDetalle = false;
      },
      error: (error: unknown) => {
        this.error = this.mensajeError(error);
        this.cargandoDetalle = false;
      }
    });
  }

  private mostrarPlan(plan: Plan, modo: 'detalle' | 'editar'): void {
    this.planSeleccionado = plan;
    this.modo = modo;
    if (modo === 'editar') {
      this.prepararEdicion(plan);
    }
  }

  // --- Crear ----------------------------------------------------------------------------

  crearPlan(): void {
    this.error = '';
    this.exito = '';
    if (this.crearPlanForm.invalid) {
      this.crearPlanForm.markAllAsTouched();
      return;
    }

    const form = this.crearPlanForm.getRawValue();
    const request: CrearPlanRequest = {
      codigo: form.codigo.trim().toUpperCase(),
      nombre: form.nombre.trim(),
      precioMensual: Number(form.precioMensual),
      maximoMascotas: Number(form.maximoMascotas),
      limiteAtenciones: form.limiteAtenciones === '' ? null : Number(form.limiteAtenciones),
      vigenteDesde: form.vigenteDesde
    };

    this.creandoPlan = true;
    this.api.crearPlan(request).subscribe({
      next: plan => {
        this.agregarOActualizarPlan(plan);
        this.cerrarCrear();
        this.mostrarPlan(plan, 'detalle');
        this.exito = `El plan ${plan.nombre} se creó correctamente.`;
        this.creandoPlan = false;
      },
      error: (error: unknown) => {
        this.error = this.mensajeError(error);
        this.creandoPlan = false;
      }
    });
  }

  // --- Editar ---------------------------------------------------------------------------

  private prepararEdicion(plan: Plan): void {
    const base = this.ultimaVersion(plan);
    const hoy = fechaLocalHoy();
    // Una versión nueva debe empezar después de la última publicada y no puede ser retroactiva.
    const despuesDeLaUltima = base ? sumarDias(base.vigenteDesde, 1) : hoy;
    this.fechaMinimaVersion = despuesDeLaUltima > hoy ? despuesDeLaUltima : hoy;
    this.editarPlanForm.reset({
      nombre: plan.nombre,
      precioMensual: base ? String(base.precioMensual) : '',
      maximoMascotas: base ? String(base.maximoMascotas) : '',
      limiteAtenciones: base?.limiteAtenciones == null ? '' : String(base.limiteAtenciones),
      vigenteDesde: this.fechaMinimaVersion
    });
  }

  guardarEdicion(): void {
    const plan = this.planSeleccionado;
    this.error = '';
    this.exito = '';
    if (!plan) {
      return;
    }
    if (this.editarPlanForm.invalid) {
      this.editarPlanForm.markAllAsTouched();
      return;
    }

    const form = this.editarPlanForm.getRawValue();
    const nombre = form.nombre.trim();
    const nuevas: CrearVersionPlanRequest = {
      precioMensual: Number(form.precioMensual),
      maximoMascotas: Number(form.maximoMascotas),
      limiteAtenciones: form.limiteAtenciones === '' ? null : Number(form.limiteAtenciones),
      vigenteDesde: form.vigenteDesde
    };
    const base = this.ultimaVersion(plan);
    const cambios = base ? this.diferencias(base, nuevas) : [];
    const cambiaNombre = nombre !== plan.nombre;

    if (!cambiaNombre && cambios.length === 0) {
      this.error = 'No hay cambios para guardar.';
      return;
    }
    if (cambios.length && nuevas.vigenteDesde < this.fechaMinimaVersion) {
      this.editarPlanForm.controls.vigenteDesde.markAsTouched();
      this.error = `Las nuevas condiciones deben empezar a partir del ${this.formatearFecha(this.fechaMinimaVersion)}.`;
      return;
    }

    this.errorDialogo = '';
    this.confirmacionEdicion = {
      nombreAnterior: plan.nombre,
      nombreNuevo: cambiaNombre ? nombre : null,
      condiciones: cambios.length ? nuevas : null,
      cambios,
      finCondicionesActuales: cambios.length ? sumarDias(nuevas.vigenteDesde, -1) : null
    };
  }

  confirmarEdicion(): void {
    const plan = this.planSeleccionado;
    const confirmacion = this.confirmacionEdicion;
    if (!plan || !confirmacion) {
      return;
    }
    const pasos: Observable<Plan>[] = [];
    if (confirmacion.nombreNuevo) {
      pasos.push(this.api.actualizarPlan(plan.id, { nombre: confirmacion.nombreNuevo }));
    }
    if (confirmacion.condiciones) {
      pasos.push(this.api.publicarVersion(plan.id, confirmacion.condiciones));
    }

    this.guardandoEdicion = true;
    this.errorDialogo = '';
    concat(...pasos).pipe(last()).subscribe({
      next: actualizado => {
        this.agregarOActualizarPlan(actualizado);
        this.mostrarPlan(actualizado, 'detalle');
        this.confirmacionEdicion = null;
        this.guardandoEdicion = false;
        this.exito = confirmacion.condiciones
          ? `Plan ${actualizado.nombre} actualizado. Las nuevas condiciones rigen desde el ${this.formatearFecha(confirmacion.condiciones.vigenteDesde)}.`
          : `El plan ahora se llama ${actualizado.nombre}.`;
      },
      error: (error: unknown) => {
        this.errorDialogo = this.mensajeError(error);
        this.guardandoEdicion = false;
        // Si el nombre ya se guardó y falló la versión, se refleja lo que sí quedó guardado.
        this.api.obtenerPlan(plan.id).subscribe(actual => {
          this.agregarOActualizarPlan(actual);
          this.planSeleccionado = actual;
        });
      }
    });
  }

  // --- Detalle --------------------------------------------------------------------------

  /** Historial de versiones, la más reciente primero, con su vigencia y lo que cambió. */
  historial(plan: Plan): VersionHistorial[] {
    return plan.versiones
      .map((version, indice) => ({
        version,
        numero: indice + 1,
        estado: this.estadoVersion(version),
        cambios: indice === 0 ? [] : this.diferencias(plan.versiones[indice - 1], version)
      }))
      .reverse();
  }

  /** Consulta el impacto y abre la confirmación para eliminar una versión programada. */
  prepararEliminacionVersion(item: VersionHistorial): void {
    const plan = this.planSeleccionado;
    if (!plan) {
      return;
    }
    const indice = plan.versiones.findIndex(version => version.id === item.version.id);
    const anterior = indice > 0 ? plan.versiones[indice - 1] : null;
    const siguiente = plan.versiones[indice + 1] ?? null;

    this.error = '';
    this.exito = '';
    this.preparandoEliminacionId = item.version.id;
    this.api.impactoEliminacionVersion(plan.id, item.version.id).subscribe({
      next: impacto => {
        this.preparandoEliminacionId = null;
        this.errorDialogo = '';
        this.confirmacionEliminacionVersion = {
          item,
          anterior: anterior ? { version: anterior, numero: indice, estado: this.estadoVersion(anterior), cambios: [] } : null,
          nuevaVigenciaAnterior: anterior
            ? this.vigencia({ ...anterior, vigenteHastaExclusiva: siguiente?.vigenteDesde ?? null })
            : null,
          afiliadosConCambioProgramado: impacto.afiliadosConCambioProgramado
        };
      },
      error: (error: unknown) => {
        this.preparandoEliminacionId = null;
        this.error = this.mensajeError(error);
      }
    });
  }

  eliminarVersion(): void {
    const plan = this.planSeleccionado;
    const confirmacion = this.confirmacionEliminacionVersion;
    if (!plan || !confirmacion) {
      return;
    }
    this.eliminandoVersion = true;
    this.errorDialogo = '';
    this.api.eliminarVersion(plan.id, confirmacion.item.version.id).subscribe({
      next: actualizado => {
        this.agregarOActualizarPlan(actualizado);
        this.planSeleccionado = actualizado;
        this.versionAplicable = null;
        this.confirmacionEliminacionVersion = null;
        this.eliminandoVersion = false;
        this.exito = `Se eliminó la versión que iba a regir desde el ${this.formatearFecha(confirmacion.item.version.vigenteDesde)}.`
          + (confirmacion.nuevaVigenciaAnterior ? ` La versión ${confirmacion.anterior?.numero} ahora rige: ${confirmacion.nuevaVigenciaAnterior.toLowerCase()}.` : '');
      },
      error: (error: unknown) => {
        this.errorDialogo = this.mensajeError(error);
        this.eliminandoVersion = false;
      }
    });
  }

  consultarVersionAplicable(): void {
    if (!this.planSeleccionado || this.fechaConsulta.invalid) {
      this.fechaConsulta.markAsTouched();
      return;
    }
    this.errorConsulta = '';
    this.versionAplicable = null;
    this.consultandoVersion = true;
    this.api.obtenerVersionAplicable(this.planSeleccionado.id, this.fechaConsulta.value).subscribe({
      next: version => {
        this.versionAplicable = version;
        this.consultandoVersion = false;
      },
      error: (error: unknown) => {
        this.errorConsulta = this.mensajeError(error);
        this.consultandoVersion = false;
      }
    });
  }

  desactivarPlan(): void {
    const plan = this.planSeleccionado;
    if (!plan?.activo) {
      return;
    }
    this.desactivandoPlan = true;
    this.errorDialogo = '';
    this.api.desactivarPlan(plan.id).subscribe({
      next: desactivado => {
        this.agregarOActualizarPlan(desactivado);
        this.planSeleccionado = desactivado;
        this.confirmandoDesactivacion = false;
        this.exito = `El plan ${desactivado.nombre} se desactivó. Su historial comercial se conservó.`;
        this.desactivandoPlan = false;
      },
      error: (error: unknown) => {
        this.errorDialogo = this.mensajeError(error);
        this.desactivandoPlan = false;
      }
    });
  }

  // --- Formato --------------------------------------------------------------------------

  formatearPrecio(precio: number): string {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(precio);
  }

  formatearFecha(fecha: string): string {
    const [anio, mes, dia] = fecha.split('-');
    return `${dia}/${mes}/${anio}`;
  }

  vigencia(version: VersionPlan): string {
    const desde = this.formatearFecha(version.vigenteDesde);
    return version.vigenteHastaExclusiva
      ? `Del ${desde} al ${this.formatearFecha(sumarDias(version.vigenteHastaExclusiva, -1))}`
      : `Desde el ${desde}, sin fecha de fin`;
  }

  atenciones(limite: number | null): string {
    return limite === null ? 'ilimitadas' : String(limite);
  }

  private estadoVersion(version: VersionPlan): EstadoVersion {
    const hoy = fechaLocalHoy();
    if (version.vigenteDesde > hoy) {
      return 'programada';
    }
    return version.vigenteHastaExclusiva !== null && version.vigenteHastaExclusiva <= hoy ? 'historica' : 'vigente';
  }

  private ultimaVersion(plan: Plan): VersionPlan | null {
    return plan.versiones.length ? plan.versiones[plan.versiones.length - 1] : null;
  }

  private diferencias(
    anterior: Pick<VersionPlan, 'precioMensual' | 'maximoMascotas' | 'limiteAtenciones'>,
    nueva: Pick<VersionPlan, 'precioMensual' | 'maximoMascotas' | 'limiteAtenciones'>): string[] {
    const cambios: string[] = [];
    if (anterior.precioMensual !== nueva.precioMensual) {
      cambios.push(`Precio: ${this.formatearPrecio(anterior.precioMensual)} → ${this.formatearPrecio(nueva.precioMensual)}`);
    }
    if (anterior.maximoMascotas !== nueva.maximoMascotas) {
      cambios.push(`Máximo de mascotas: ${anterior.maximoMascotas} → ${nueva.maximoMascotas}`);
    }
    if (anterior.limiteAtenciones !== nueva.limiteAtenciones) {
      cambios.push(`Atenciones incluidas: ${this.atenciones(anterior.limiteAtenciones)} → ${this.atenciones(nueva.limiteAtenciones)}`);
    }
    return cambios;
  }

  private agregarOActualizarPlan(plan: Plan): void {
    const index = this.planes.findIndex(item => item.id === plan.id);
    if (index === -1) {
      this.planes = [...this.planes, plan].sort((first, second) => first.codigo.localeCompare(second.codigo));
      return;
    }
    this.planes = this.planes.map(item => item.id === plan.id ? plan : item);
  }

  private mensajeError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return 'La sesión expiró. Inicia sesión nuevamente para continuar.';
      }
      if (error.status === 403) {
        return 'Acceso denegado. Verifica que la sesión tenga el rol ADMIN y que el token CSRF sea válido.';
      }
      if (error.status === 0) {
        return 'No se pudo conectar con el servidor. Inténtalo nuevamente.';
      }

      const response = error.error as ApiErrorResponse | null;
      const message = typeof response?.message === 'string' ? response.message : '';
      const details = Array.isArray(response?.errors)
        ? response.errors.filter((item): item is string => typeof item === 'string')
        : [];
      if (message && details.length) {
        return `${message} ${details.join(' ')}`;
      }
      if (message) {
        return message;
      }
      if (details.length) {
        return details.join(' ');
      }
    }
    return 'Ocurrió un error inesperado. Inténtalo nuevamente.';
  }
}
