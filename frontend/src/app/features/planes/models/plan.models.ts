import { BeneficioServicio } from './afiliacion.models';

export interface VersionPlan {
  id: number;
  precioMensual: number;
  maximoMascotas: number;
  limiteAtenciones: number | null;
  atencionesIlimitadas: boolean;
  vigenteDesde: string;
  vigenteHastaExclusiva: string | null;
}

export interface Plan {
  id: number;
  codigo: string;
  nombre: string;
  activo: boolean;
  versiones: VersionPlan[];
  /** Descuento del plan en cada servicio (valor del bono con plan). */
  beneficios: BeneficioServicio[];
}

export interface CrearPlanRequest {
  codigo: string;
  nombre: string;
  precioMensual: number;
  maximoMascotas: number;
  limiteAtenciones: number | null;
  vigenteDesde: string;
}

export interface ImpactoEliminacionVersion {
  versionId: number;
  vigenteDesde: string;
  afiliadosConCambioProgramado: number;
}

export interface ActualizarPlanRequest {
  nombre: string;
}

export interface CrearVersionPlanRequest {
  precioMensual: number;
  maximoMascotas: number;
  limiteAtenciones: number | null;
  vigenteDesde: string;
}

export interface ApiErrorResponse {
  status?: number;
  message?: string;
  errors?: string[];
}
