import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Bono, CompraBonoRequest, CotizacionBono } from '../models/bono.models';

@Injectable({ providedIn: 'root' })
export class BonoApiService {
  private readonly http = inject(HttpClient);

  listar(afiliadoId: number): Observable<Bono[]> {
    return this.http.get<Bono[]>(`/api/v1/afiliados/${afiliadoId}/bonos`);
  }

  cotizar(afiliadoId: number, mascotaId: number, servicioId: number): Observable<CotizacionBono> {
    const params = new HttpParams().set('mascotaId', mascotaId).set('servicioId', servicioId);
    return this.http.get<CotizacionBono>(`/api/v1/afiliados/${afiliadoId}/bonos/cotizacion`, { params });
  }

  comprar(afiliadoId: number, request: CompraBonoRequest): Observable<Bono> {
    return this.http.post<Bono>(`/api/v1/afiliados/${afiliadoId}/bonos`, request);
  }
}
