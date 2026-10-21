import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ConciliacionPagos,
  CuotaPlan,
  FiltrosConciliacion,
  HistorialPagos,
  MedioPago,
  Pago,
  PagoEnLinea,
  RegistrarPagoRequest,
  SesionPago
} from '../models/pago.models';

@Injectable({ providedIn: 'root' })
export class PagoApiService {
  private readonly http = inject(HttpClient);

  historial(afiliadoId: number): Observable<HistorialPagos> {
    return this.http.get<HistorialPagos>(`/api/v1/afiliados/${afiliadoId}/pagos`);
  }

  registrarPago(afiliadoId: number, cuotaId: number, request: RegistrarPagoRequest): Observable<CuotaPlan> {
    return this.http.post<CuotaPlan>(`/api/v1/afiliados/${afiliadoId}/pagos/${cuotaId}`, request);
  }

  transacciones(afiliadoId: number): Observable<Pago[]> {
    return this.http.get<Pago[]>(`/api/v1/afiliados/${afiliadoId}/transacciones`);
  }

  pagarCuotaEnLinea(afiliadoId: number, cuotaId: number): Observable<PagoEnLinea> {
    return this.http.post<PagoEnLinea>(`/api/v1/afiliados/${afiliadoId}/pagos/${cuotaId}/en-linea`, {});
  }

  comprarBonoEnLinea(afiliadoId: number, mascotaId: number, servicioId: number): Observable<PagoEnLinea> {
    return this.http.post<PagoEnLinea>(`/api/v1/afiliados/${afiliadoId}/bonos/en-linea`, { mascotaId, servicioId });
  }

  conciliacion(filtros: FiltrosConciliacion): Observable<ConciliacionPagos> {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(filtros)) {
      if (valor) {
        params = params.set(clave, valor);
      }
    }
    return this.http.get<ConciliacionPagos>('/api/v1/admin/pagos', { params });
  }

  // Pasarela simulada (entorno de pruebas)

  sesionSimulada(referencia: string): Observable<SesionPago> {
    return this.http.get<SesionPago>(`/api/v1/admin/pasarela-simulada/${referencia}`);
  }

  resultadoSimulado(referencia: string, aprobado: boolean, medioPago: MedioPago): Observable<Pago> {
    return this.http.post<Pago>(`/api/v1/admin/pasarela-simulada/${referencia}/resultado`, { aprobado, medioPago });
  }
}
