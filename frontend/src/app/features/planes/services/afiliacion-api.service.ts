import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AsignarPlanRequest, PlanAfiliado } from '../models/afiliacion.models';

@Injectable({ providedIn: 'root' })
export class AfiliacionApiService {
  private readonly http = inject(HttpClient);

  obtenerPlan(afiliadoId: number): Observable<PlanAfiliado> {
    return this.http.get<PlanAfiliado>(`/api/v1/afiliados/${afiliadoId}/plan`);
  }

  asignarPlan(afiliadoId: number, request: AsignarPlanRequest): Observable<PlanAfiliado> {
    return this.http.put<PlanAfiliado>(`/api/v1/afiliados/${afiliadoId}/plan`, request);
  }
}
