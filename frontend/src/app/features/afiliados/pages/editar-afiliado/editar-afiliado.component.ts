import { HttpErrorResponse } from '@angular/common/http';
import { PermitirCaracteresDirective } from '../../../../core/validation/permitir-caracteres.directive';
import { NOMBRE_PERSONA, SOLO_NUMEROS, SOLO_PALABRAS } from '../../../../core/validation/campos';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AfiliadoDetalle, ActualizarAfiliadoRequest, ApiErrorResponse } from '../../models/afiliado.models';
import { AfiliadoApiService } from '../../services/afiliado-api.service';

const emailWithDomain = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length > 0 ? null : { notBlank: true };

@Component({
  selector: 'app-editar-afiliado',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, PermitirCaracteresDirective],
  templateUrl: './editar-afiliado.component.html',
  styleUrl: './editar-afiliado.component.scss'
})
export class EditarAfiliadoComponent implements OnInit {
  private readonly api = inject(AfiliadoApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly form = new FormGroup({
    nombres: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.pattern(NOMBRE_PERSONA), Validators.maxLength(100)] }),
    apellidos: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.pattern(SOLO_PALABRAS), Validators.maxLength(100)] }),
    celular: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.pattern(SOLO_NUMEROS), Validators.maxLength(20)] }),
    correo: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.email, Validators.pattern(emailWithDomain), Validators.maxLength(254)] }),
    direccion: new FormControl('', { nonNullable: true, validators: [notBlank, Validators.maxLength(200)] })
  });

  afiliado: AfiliadoDetalle | null = null;
  cargando = true;
  guardando = false;
  error = '';

  fieldMessage(control: FormControl<string>, field: 'nombres' | 'apellidos' | 'celular' | 'correo' | 'direccion'): string | null {
    if (!control.touched) return null;
    if (control.hasError('required') || control.hasError('notBlank')) return 'Este campo es obligatorio.';
    if (control.hasError('email')) return 'Ingresa un correo electrónico válido.';
    if (control.hasError('pattern')) {
      if (field === 'correo') return 'Ingresa un correo electrónico válido.';
      return field === 'celular'
        ? 'El celular solo puede contener números.'
        : field === 'nombres'
          ? 'El nombre solo puede contener letras, espacios y el número 6.'
          : 'El apellido solo puede contener letras y espacios.';
    }
    const maximum = field === 'celular' ? 20 : field === 'correo' ? 254 : field === 'direccion' ? 200 : 100;
    return control.hasError('maxlength') ? `No puede superar los ${maximum} caracteres.` : null;
  }

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = Number(params.get('id'));
      this.api.obtenerAfiliado(id).subscribe({
        next: afiliado => {
          this.afiliado = afiliado;
          this.form.setValue({
            nombres: afiliado.nombres,
            apellidos: afiliado.apellidos,
            celular: afiliado.celular,
            correo: afiliado.correo,
            direccion: afiliado.direccion
          });
          this.cargando = false;
        },
        error: error => {
          this.error = error.status === 404 ? 'No se encontró el afiliado solicitado.' : 'No se pudo cargar el afiliado.';
          this.cargando = false;
        }
      });
    });
  }

  guardar(): void {
    this.error = '';
    if (!this.afiliado) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const request: ActualizarAfiliadoRequest = this.form.getRawValue();
    this.guardando = true;
    this.api.actualizarAfiliado(this.afiliado.id, request).subscribe({
      next: () => {
        this.guardando = false;
        void this.router.navigate(['/afiliados', this.afiliado?.id], { queryParams: { actualizado: 1 } });
      },
      error: (error: HttpErrorResponse) => {
        const payload = error.error as Partial<ApiErrorResponse> | null;
        this.error = error.status === 404
          ? 'No se encontró el afiliado solicitado.'
          : error.status === 400 && Array.isArray(payload?.errors)
            ? payload.errors.join(' ')
            : error.status === 409
              ? 'Los datos generan un conflicto con otro registro.'
              : 'No se pudieron guardar los cambios. Inténtalo nuevamente.';
        this.guardando = false;
      }
    });
  }
}
