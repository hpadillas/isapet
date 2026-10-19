import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Prestacion, RegistrarPrestacionRequest } from '../models/prestacion.models';

@Injectable({ providedIn: 'root' })
export class PrestacionApiService {
  private readonly http = inject(HttpClient);

  listar(afiliadoId: number, mascotaId: number | null = null): Observable<Prestacion[]> {
    const params = mascotaId === null ? new HttpParams() : new HttpParams().set('mascotaId', mascotaId);
    return this.http.get<Prestacion[]>(`/api/v1/afiliados/${afiliadoId}/prestaciones`, { params });
  }

  registrar(afiliadoId: number, request: RegistrarPrestacionRequest): Observable<Prestacion> {
    return this.http.post<Prestacion>(`/api/v1/afiliados/${afiliadoId}/prestaciones`, request);
  }
}
