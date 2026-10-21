import { AsyncPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { formatearPesos, mensajeError } from '../../core/format/moneda';
import { AlertaInicio, ResumenInicio, TipoAlerta } from './inicio.models';

/** Cada tipo de alerta: etiqueta, a qué pestaña del afiliado lleva y su nivel de urgencia. */
const ALERTAS: Record<TipoAlerta, { etiqueta: string; accion: string; seccion: string; clase: string }> = {
  SUSPENDIDO: { etiqueta: 'Suspendido', accion: 'Registrar pago', seccion: 'pagos', clase: 'danger' },
  PENDIENTE_ACTIVACION: { etiqueta: 'Por activar', accion: 'Cobrar activación', seccion: 'pagos', clase: 'warning' },
  CUOTA_POR_VENCER: { etiqueta: 'Cuota por vencer', accion: 'Ir a pagos', seccion: 'pagos', clase: 'warning' },
  CAMBIO_PROGRAMADO: { etiqueta: 'Cambio de plan', accion: 'Ver plan', seccion: 'plan', clase: 'info' },
  CANCELACION_PROGRAMADA: { etiqueta: 'Cancelación', accion: 'Ver plan', seccion: 'plan', clase: 'info' }
};

const FECHA_LARGA = new Intl.DateTimeFormat('es-CO', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [AsyncPipe, ReactiveFormsModule, RouterLink],
  templateUrl: './inicio.component.html'
})
export class InicioComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  readonly formatearPesos = formatearPesos;
  readonly busqueda = new FormControl('', { nonNullable: true });
  resumen: ResumenInicio | null = null;
  cargando = true;
  error = '';

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.http.get<ResumenInicio>('/api/v1/admin/resumen').subscribe({
      next: resumen => {
        this.resumen = resumen;
        this.cargando = false;
      },
      error: error => {
        this.error = mensajeError(error, 'No se pudo cargar el resumen.');
        this.cargando = false;
      }
    });
  }

  buscar(): void {
    const consulta = this.busqueda.value.trim();
    void this.router.navigate(['/afiliados'], consulta ? { queryParams: { q: consulta } } : {});
  }

  get saludo(): string {
    const hora = new Date().getHours();
    return hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
  }

  /** "martes, 21 de octubre de 2026" a partir de la fecha del servidor (yyyy-MM-dd). */
  fechaLarga(fecha: string): string {
    const [anio, mes, dia] = fecha.split('-').map(Number);
    const texto = FECHA_LARGA.format(new Date(anio, mes - 1, dia));
    return texto.charAt(0).toUpperCase() + texto.slice(1);
  }

  configuracion(alerta: AlertaInicio) {
    return ALERTAS[alerta.tipo];
  }
}
