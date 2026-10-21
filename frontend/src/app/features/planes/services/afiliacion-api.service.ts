import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AsignarPlanRequest, CancelacionPlan, PlanAfiliado } from '../models/afiliacion.models';

@Injectable({ providedIn: 'root' })
export class AfiliacionApiService {
  private readonly http = inject(HttpClient);

  obtenerPlan(afiliadoId: number): Observable<PlanAfiliado> {
    return this.http.get<PlanAfiliado>(`/api/v1/afiliados/${afiliadoId}/plan`);
  }

  asignarPlan(afiliadoId: number, request: AsignarPlanRequest): Observable<PlanAfiliado> {
    return this.http.put<PlanAfiliado>(`/api/v1/afiliados/${afiliadoId}/plan`, request);
  }

  simularCancelacion(afiliadoId: number): Observable<CancelacionPlan> {
    return this.http.get<CancelacionPlan>(`/api/v1/afiliados/${afiliadoId}/plan/cancelacion`);
  }

  cancelarPlan(afiliadoId: number): Observable<CancelacionPlan> {
    return this.http.post<CancelacionPlan>(`/api/v1/afiliados/${afiliadoId}/plan/cancelacion`, {});
  }

  deshacerCancelacion(afiliadoId: number): Observable<PlanAfiliado> {
    return this.http.delete<PlanAfiliado>(`/api/v1/afiliados/${afiliadoId}/plan/cancelacion`);
  }

  cancelarCambioProgramado(afiliadoId: number): Observable<PlanAfiliado> {
    return this.http.delete<PlanAfiliado>(`/api/v1/afiliados/${afiliadoId}/plan/cambio-programado`);
  }
}
