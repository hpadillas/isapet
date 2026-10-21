export type CoberturaPrestacion = 'PLAN' | 'BONO' | 'PARTICULAR';

export interface Prestacion {
  id: number;
  fecha: string;
  mascotaId: number;
  mascotaNombre: string;
  servicioId: number;
  servicioNombre: string;
  tipoServicio: string;
  prestador: string;
  observaciones: string | null;
  cobertura: CoberturaPrestacion;
  bonoId: number | null;
  bonoCodigo: string | null;
  valorPagadoBono: number | null;
}

export interface RegistrarPrestacionRequest {
  mascotaId: number;
  servicioId: number;
  fecha: string;
  prestador: string;
  observaciones: string | null;
  bonoId: number | null;
}
