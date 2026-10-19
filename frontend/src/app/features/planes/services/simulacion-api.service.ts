import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ServicioVeterinario, SimulacionPlanRequest, SimulacionPlanResponse } from '../models/afiliacion.models';

@Injectable({ providedIn: 'root' })
export class SimulacionApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = '/api/v1/simulaciones';

  listarServicios(): Observable<ServicioVeterinario[]> {
    return this.http.get<ServicioVeterinario[]>(`${this.endpoint}/servicios`);
  }

  simular(request: SimulacionPlanRequest): Observable<SimulacionPlanResponse> {
    return this.http.post<SimulacionPlanResponse>(`${this.endpoint}/planes`, request);
  }
}
