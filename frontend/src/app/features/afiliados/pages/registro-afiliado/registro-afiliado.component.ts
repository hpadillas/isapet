import { HttpErrorResponse } from '@angular/common/http';
import { NOMBRE_PERSONA, SOLO_NUMEROS, SOLO_PALABRAS } from '../../../../core/validation/campos';
import { PermitirCaracteresDirective } from '../../../../core/validation/permitir-caracteres.directive';
import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PoliticaPrivacidad } from '../../../privacidad/privacidad.models';
import { PrivacidadApiService } from '../../../privacidad/privacidad-api.service';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import {
  ApiErrorResponse,
  CrearAfiliadoRequest,
  CrearAfiliadoResponse,
  CrearMascotaRequest,
  CrearMascotaResponse,
  ESPECIES_MASCOTA
} from '../../models/afiliado.models';
import { AfiliadoApiService } from '../../services/afiliado-api.service';

type AfiliadoControls = {
  numeroIdentificacion: FormControl<string>;
  nombres: FormControl<string>;
  apellidos: FormControl<string>;
  celular: FormControl<string>;
  correo: FormControl<string>;
  direccion: FormControl<string>;
  autorizaTratamientoDatos: FormControl<boolean>;
};

type MascotaControls = {
  nombre: FormControl<string>;
  especie: FormControl<string>;
  raza: FormControl<string>;
  edad: FormControl<number | null>;
  color: FormControl<string>;
};

const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length > 0 ? null : { notBlank: true };

const integer: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.value === null || Number.isInteger(control.value) ? null : { integer: true };
const emailWithDomain = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-registro-afiliado',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, PermitirCaracteresDirective],
  templateUrl: './registro-afiliado.component.html',
  styleUrl: './registro-afiliado.component.scss'
})
export class RegistroAfiliadoComponent implements OnInit {
  private readonly privacidadApi = inject(PrivacidadApiService);
  /** Aviso de privacidad que se muestra al titular antes de autorizar (Ley 1581 de 2012, art. 12). */
  politica: PoliticaPrivacidad | null = null;

  ngOnInit(): void {
    this.privacidadApi.politica().subscribe({ next: politica => this.politica = politica });
  }

  private readonly afiliadoApi = inject(AfiliadoApiService);

  readonly especies = ESPECIES_MASCOTA;
  readonly mensajePalabras = 'Solo puede contener letras y espacios.';

  readonly afiliadoForm = new FormGroup<AfiliadoControls>({
    numeroIdentificacion: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.pattern(SOLO_NUMEROS), Validators.maxLength(30)]
    }),
    nombres: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.pattern(NOMBRE_PERSONA), Validators.maxLength(100)]
    }),
    apellidos: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.pattern(SOLO_PALABRAS), Validators.maxLength(100)]
    }),
    celular: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.pattern(SOLO_NUMEROS), Validators.maxLength(20)]
    }),
    correo: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.email, Validators.pattern(emailWithDomain), Validators.maxLength(254)]
    }),
    direccion: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.maxLength(200)]
    }),
    autorizaTratamientoDatos: new FormControl(false, {
      nonNullable: true,
      validators: [Validators.requiredTrue]
    })
  });

  readonly mascotaForm = new FormGroup<MascotaControls>({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.pattern(SOLO_PALABRAS), Validators.maxLength(100)]
    }),
    especie: new FormControl('', {
      nonNullable: true,
      validators: [notBlank]
    }),
    raza: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.pattern(SOLO_PALABRAS), Validators.maxLength(100)]
    }),
    edad: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0), Validators.max(100), integer]
    }),
    color: new FormControl('', {
      nonNullable: true,
      validators: [notBlank, Validators.pattern(SOLO_PALABRAS), Validators.maxLength(50)]
    })
  });

  afiliado: CrearAfiliadoResponse | null = null;
  mascotas: CrearMascotaResponse[] = [];
  afiliadoEnviando = false;
  mascotaEnviando = false;
  mostrarFormularioMascota = false;
  finalizado = false;
  errorAfiliado = '';
  detalleErroresAfiliado: string[] = [];
  errorMascota = '';
  detalleErroresMascota: string[] = [];

  mensajeCampo(control: AbstractControl, maxLength?: number, patternMessage?: string): string | null {
    if (!control.touched) {
      return null;
    }
    if (control.hasError('notBlank') || control.hasError('required')) {
      return 'Este campo es obligatorio.';
    }
    if (control.hasError('email')) {
      return 'Ingresa un correo electrónico válido.';
    }
    if (control.hasError('pattern')) {
      if (control === this.afiliadoForm.controls.correo) {
        return 'Ingresa un correo electrónico válido.';
      }
      return patternMessage ?? 'El formato contiene caracteres no permitidos.';
    }
    if (control.hasError('maxlength')) {
      return `No puede superar los ${maxLength} caracteres.`;
    }
    if (control.hasError('min')) {
      return 'La edad no puede ser negativa.';
    }
    if (control.hasError('max')) {
      return 'La edad no puede superar los 100 años.';
    }
    if (control.hasError('integer')) {
      return 'La edad debe ser un número entero.';
    }
    if (control.hasError('pattern')) {
      return 'Selecciona una especie válida.';
    }
    return null;
  }

  registrarAfiliado(): void {
    this.errorAfiliado = '';
    this.detalleErroresAfiliado = [];
    if (this.afiliadoForm.invalid) {
      this.afiliadoForm.markAllAsTouched();
      return;
    }

    this.afiliadoEnviando = true;
    const request: CrearAfiliadoRequest = this.afiliadoForm.getRawValue();
    this.afiliadoApi.crearAfiliado(request).subscribe({
      next: response => {
        this.afiliado = response;
        this.afiliadoEnviando = false;
      },
      error: error => {
        this.presentarError(error, 'afiliado');
        this.afiliadoEnviando = false;
      }
    });
  }

  abrirFormularioMascota(): void {
    this.errorMascota = '';
    this.detalleErroresMascota = [];
    this.mostrarFormularioMascota = true;
  }

  cancelarMascota(): void {
    this.mascotaForm.reset();
    this.mostrarFormularioMascota = false;
    this.errorMascota = '';
    this.detalleErroresMascota = [];
  }

  registrarMascota(): void {
    this.errorMascota = '';
    this.detalleErroresMascota = [];
    if (this.mascotaForm.invalid || !this.afiliado) {
      this.mascotaForm.markAllAsTouched();
      return;
    }

    const value = this.mascotaForm.getRawValue();
    if (value.edad === null || !Number.isInteger(value.edad)) {
      this.mascotaForm.controls.edad.markAsTouched();
      return;
    }

    const request: CrearMascotaRequest = {
      nombre: value.nombre,
      especie: value.especie,
      raza: value.raza,
      edad: value.edad,
      color: value.color
    };

    this.mascotaEnviando = true;
    this.afiliadoApi.crearMascota(this.afiliado.id, request).subscribe({
      next: mascota => {
        this.mascotas = [...this.mascotas, mascota];
        this.mascotaForm.reset();
        this.mostrarFormularioMascota = false;
        this.mascotaEnviando = false;
      },
      error: error => {
        this.presentarError(error, 'mascota');
        this.mascotaEnviando = false;
      }
    });
  }

  finalizarRegistro(): void {
    this.mostrarFormularioMascota = false;
    this.finalizado = true;
  }

  nuevoRegistro(): void {
    this.afiliado = null;
    this.mascotas = [];
    this.afiliadoForm.reset();
    this.mascotaForm.reset();
    this.mostrarFormularioMascota = false;
    this.finalizado = false;
    this.errorAfiliado = '';
    this.detalleErroresAfiliado = [];
    this.errorMascota = '';
    this.detalleErroresMascota = [];
  }

  private presentarError(error: HttpErrorResponse, tipo: 'afiliado' | 'mascota'): void {
    const payload = error.error as Partial<ApiErrorResponse> | null;
    const details = Array.isArray(payload?.errors)
      ? payload.errors.map(detail => detail.includes(':') ? detail.slice(detail.indexOf(':') + 1).trim() : detail)
      : payload?.message ? [payload.message] : [];

    let message: string;
    if (error.status === 400) {
      message = 'Revisa los datos ingresados.';
    } else if (error.status === 409) {
      message = 'Ya existe un afiliado con ese número de identificación.';
    } else if (error.status === 404) {
      message = 'No se encontró el afiliado para asociar la mascota.';
    } else if (error.status === 500) {
      message = 'Ocurrió un error del servidor. Inténtalo más tarde.';
    } else if (error.status === 0) {
      message = 'No se pudo conectar con el servidor. Verifica que esté disponible.';
    } else {
      message = 'No se pudo completar el registro. Inténtalo nuevamente.';
    }

    if (tipo === 'afiliado') {
      this.errorAfiliado = message;
      this.detalleErroresAfiliado = details;
    } else {
      this.errorMascota = message;
      this.detalleErroresMascota = details;
    }
  }
}
