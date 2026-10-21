import { AsyncPipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [AsyncPipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  logoutError = '';

  ngOnInit(): void {
    this.auth.checkSession().subscribe({ error: () => this.auth.clearSession() });
  }

  logout(): void {
    this.logoutError = '';
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => {
        this.logoutError = 'No se pudo cerrar la sesión. Comprueba la conexión e inténtalo nuevamente.';
      }
    });
  }
}
