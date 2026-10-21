import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AutorizacionDatos, PoliticaPrivacidad } from './privacidad.models';

@Injectable({ providedIn: 'root' })
export class PrivacidadApiService {
  private readonly http = inject(HttpClient);

  politica(): Observable<PoliticaPrivacidad> {
    return this.http.get<PoliticaPrivacidad>('/api/v1/privacidad');
  }

  autorizacion(afiliadoId: number): Observable<AutorizacionDatos> {
    return this.http.get<AutorizacionDatos>(`/api/v1/afiliados/${afiliadoId}/autorizacion-datos`);
  }

  registrarAutorizacion(afiliadoId: number): Observable<AutorizacionDatos> {
    return this.http.post<AutorizacionDatos>(`/api/v1/afiliados/${afiliadoId}/autorizacion-datos`, { autorizaTratamientoDatos: true });
  }
}
