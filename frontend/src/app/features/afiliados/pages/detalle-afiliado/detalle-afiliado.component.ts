import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { AfiliadoDetalle, ApiErrorResponse, CrearMascotaRequest, CrearMascotaResponse, ESPECIES_MASCOTA } from '../../models/afiliado.models';
import { AfiliadoApiService } from '../../services/afiliado-api.service';
import { AfiliadoTabsComponent } from '../../components/afiliado-tabs/afiliado-tabs.component';

const integerValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.value === null || Number.isInteger(control.value) ? null : { integer: true };

@Component({
  selector: 'app-detalle-afiliado',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, AfiliadoTabsComponent],
  templateUrl: './detalle-afiliado.component.html',
  styleUrl: './detalle-afiliado.component.scss'
})
export class DetalleAfiliadoComponent implements OnInit {
  private readonly api = inject(AfiliadoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly especies = ESPECIES_MASCOTA;
  readonly mascotaForm = new FormGroup({
    nombre: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    especie: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    raza: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(100)] }),
    edad: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0), Validators.max(100), integerValidator] }),
    color: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(50)] })
  });

  afiliado: AfiliadoDetalle | null = null;
  mascotas: CrearMascotaResponse[] = [];
  afiliadoId = 0;
  cargando = true;
  guardando = false;
  eliminandoMascotaId: number | null = null;
  mostrarFormulario = false;
  editandoMascotaId: number | null = null;
  error = '';
  errorMascotas = '';
  errorFormulario = '';
  exito = '';

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.afiliadoId = Number(params.get('id'));
      this.cargar();
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.exito = params.get('actualizado') ? 'Datos del afiliado actualizados correctamente.' : '';
    });
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    forkJoin({ afiliado: this.api.obtenerAfiliado(this.afiliadoId), mascotas: this.api.listarMascotas(this.afiliadoId) }).subscribe({
      next: resultado => {
        this.afiliado = resultado.afiliado;
        this.mascotas = resultado.mascotas;
        this.cargando = false;
      },
      error: (error: HttpErrorResponse) => {
        this.error = error.status === 404
          ? 'No se encontró el afiliado solicitado.'
          : 'No se pudo cargar la información del afiliado. Inténtalo nuevamente.';
        this.cargando = false;
      }
    });
  }

  agregarMascota(): void {
    this.editandoMascotaId = null;
    this.errorFormulario = '';
    this.exito = '';
    this.mascotaForm.reset();
    this.mostrarFormulario = true;
  }

  editarMascota(mascota: CrearMascotaResponse): void {
    this.editandoMascotaId = mascota.id;
    this.errorFormulario = '';
    this.exito = '';
    this.mascotaForm.setValue({ nombre: mascota.nombre, especie: mascota.especie, raza: mascota.raza, edad: mascota.edad, color: mascota.color });
    this.mostrarFormulario = true;
  }

  cancelarEdicion(): void {
    this.mostrarFormulario = false;
    this.editandoMascotaId = null;
    this.mascotaForm.reset();
    this.errorFormulario = '';
  }

  eliminarMascota(mascota: CrearMascotaResponse): void {
    if (!window.confirm(`¿Eliminar a ${mascota.nombre}? Esta acción no se puede deshacer.`)) {
      return;
    }

    this.errorMascotas = '';
    this.exito = '';
    this.eliminandoMascotaId = mascota.id;
    this.api.eliminarMascota(mascota.id).subscribe({
      next: () => {
        this.mascotas = this.mascotas.filter(current => current.id !== mascota.id);
        this.exito = 'Mascota eliminada correctamente.';
        this.eliminandoMascotaId = null;
      },
      error: (error: HttpErrorResponse) => {
        this.errorMascotas = error.status === 404
          ? 'No se encontró la mascota. Puede que ya haya sido eliminada.'
          : error.status === 500
            ? 'No se pudo eliminar la mascota por un error del servidor. Inténtalo nuevamente.'
            : error.status === 0
              ? 'No se pudo conectar con el servidor.'
              : 'No se pudo eliminar la mascota. Inténtalo nuevamente.';
        this.eliminandoMascotaId = null;
      }
    });
  }

  guardarMascota(): void {
    this.errorFormulario = '';
    this.exito = '';
    if (this.mascotaForm.invalid) {
      this.mascotaForm.markAllAsTouched();
      return;
    }
    const value = this.mascotaForm.getRawValue();
    if (value.edad === null || !Number.isInteger(value.edad)) {
      this.mascotaForm.controls.edad.markAsTouched();
      return;
    }
    const request: CrearMascotaRequest = { ...value, edad: value.edad };
    this.guardando = true;
    const operation = this.editandoMascotaId === null
      ? this.api.crearMascota(this.afiliadoId, request)
      : this.api.actualizarMascota(this.editandoMascotaId, request);
    operation.subscribe({
      next: mascota => {
        if (this.editandoMascotaId === null) {
          this.mascotas = [...this.mascotas, mascota];
          this.exito = 'Mascota agregada correctamente.';
        } else {
          this.mascotas = this.mascotas.map(current => current.id === mascota.id ? mascota : current);
          this.exito = 'Datos de la mascota actualizados.';
        }
        this.guardando = false;
        this.mostrarFormulario = false;
        this.editandoMascotaId = null;
      },
      error: error => {
        const payload = error.error as Partial<ApiErrorResponse> | null;
        this.errorFormulario = error.status === 404
          ? 'No se encontró el afiliado o la mascota.'
          : error.status === 400 && Array.isArray(payload?.errors)
            ? payload.errors.join(' ')
            : error.status === 0
              ? 'No se pudo conectar con el servidor.'
              : 'No se pudo guardar la mascota. Inténtalo nuevamente.';
        this.guardando = false;
      }
    });
  }

}
