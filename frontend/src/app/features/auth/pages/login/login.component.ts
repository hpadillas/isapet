import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly form = new FormGroup({
    username: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(100)]
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(72)]
    })
  });

  error = '';
  submitting = false;

  ngOnInit(): void {
    this.auth.checkSession().subscribe({
      next: session => {
        if (session?.roles.includes('ADMIN')) {
          void this.router.navigateByUrl(this.returnUrl());
        }
      },
      error: () => this.auth.clearSession()
    });
  }

  submit(): void {
    this.error = '';
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    this.auth.login(this.form.getRawValue()).subscribe({
      next: session => {
        this.submitting = false;
        if (!session.roles.includes('ADMIN')) {
          this.error = 'Tu cuenta no tiene permisos de administrador.';
          this.auth.logout().subscribe({ error: () => this.auth.clearSession() });
          return;
        }
        void this.router.navigateByUrl(this.returnUrl());
      },
      error: (error: unknown) => {
        this.submitting = false;
        this.error = this.loginError(error);
      }
    });
  }

  private returnUrl(): string {
    const requestedUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    if (requestedUrl?.startsWith('/') && !requestedUrl.startsWith('//')) {
      return requestedUrl;
    }
    return '/';
  }

  private loginError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) return 'El usuario o la contraseña no son correctos.';
      if (error.status === 403) return 'No se pudo validar el token de seguridad. Inténtalo nuevamente.';
      if (error.status === 0) return 'No se pudo conectar con el servidor. Inténtalo nuevamente.';
    }
    return 'No se pudo iniciar sesión. Inténtalo nuevamente.';
  }
}
