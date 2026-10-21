import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CrearAfiliadoRequest,
  CrearAfiliadoResponse,
  ActualizarAfiliadoRequest,
  AfiliadoDetalle,
  AfiliadoListaItem,
  CrearMascotaRequest,
  CrearMascotaResponse,
  EstadoPlanAfiliado,
  Pagina,
  ResumenEliminacionAfiliado
} from '../models/afiliado.models';

@Injectable({ providedIn: 'root' })
export class AfiliadoApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/v1/afiliados';

  crearAfiliado(request: CrearAfiliadoRequest): Observable<CrearAfiliadoResponse> {
    return this.http.post<CrearAfiliadoResponse>(this.endpoint, request);
  }

  listarAfiliados(busqueda = '', page = 0, estado: EstadoPlanAfiliado | '' = '', size = 10): Observable<Pagina<AfiliadoListaItem>> {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (busqueda.trim()) query.set('q', busqueda.trim());
    if (estado) query.set('estado', estado);
    return this.http.get<Pagina<AfiliadoListaItem>>(`${this.endpoint}?${query.toString()}`);
  }

  resumenEliminacion(afiliadoId: number): Observable<ResumenEliminacionAfiliado> {
    return this.http.get<ResumenEliminacionAfiliado>(`${this.endpoint}/${afiliadoId}/eliminacion`);
  }

  eliminarAfiliado(afiliadoId: number): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${afiliadoId}`);
  }

  obtenerAfiliado(afiliadoId: number): Observable<AfiliadoDetalle> {
    return this.http.get<AfiliadoDetalle>(`${this.endpoint}/${afiliadoId}`);
  }

  actualizarAfiliado(afiliadoId: number, request: ActualizarAfiliadoRequest): Observable<AfiliadoDetalle> {
    return this.http.put<AfiliadoDetalle>(`${this.endpoint}/${afiliadoId}`, request);
  }

  listarMascotas(afiliadoId: number): Observable<CrearMascotaResponse[]> {
    return this.http.get<CrearMascotaResponse[]>(`${this.endpoint}/${afiliadoId}/mascotas`);
  }

  crearMascota(afiliadoId: number, request: CrearMascotaRequest): Observable<CrearMascotaResponse> {
    return this.http.post<CrearMascotaResponse>(`${this.endpoint}/${afiliadoId}/mascotas`, request);
  }

  actualizarMascota(mascotaId: number, request: CrearMascotaRequest): Observable<CrearMascotaResponse> {
    return this.http.put<CrearMascotaResponse>(`/api/v1/mascotas/${mascotaId}`, request);
  }

  eliminarMascota(mascotaId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/mascotas/${mascotaId}`);
  }
}