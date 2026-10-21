import { Bono } from '../../bonos/models/bono.models';
import { CuotaPlan } from '../../pagos/models/pago.models';
import { Prestacion } from '../../prestaciones/models/prestacion.models';

export type TipoServicio = 'CONSULTA' | 'PROCEDIMIENTO';

export interface ServicioVeterinario {
  id: number;
  codigo: string;
  nombre: string;
  tipo: TipoServicio;
  valorBase: number;
}

export interface BeneficioServicio {
  servicioId: number;
  codigo: string;
  nombre: string;
  tipo: TipoServicio;
  valorBase: number;
  porcentajeDescuento: number;
  valorConPlan: number;
}

export interface ServicioEstimado {
  servicioId: number;
  cantidadMensual: number;
}

export interface SimulacionPlanRequest {
  cantidadMascotas: number;
  servicios: ServicioEstimado[];
}

export interface OpcionPlanSimulada {
  planId: number;
  codigo: string;
  nombre: string;
  precioMensual: number;
  maximoMascotas: number;
  limiteAtenciones: number | null;
  atencionesIlimitadas: boolean;
  aplica: boolean;
  motivoNoAplica: string | null;
  beneficios: BeneficioServicio[];
  costoServiciosSinPlan: number;
  costoServiciosConPlan: number;
  valorMensualEstimado: number;
  ahorroMensualEstimado: number;
  recomendado: boolean;
}

export interface SimulacionPlanResponse {
  fechaReferencia: string;
  cantidadMascotas: number;
  opciones: OpcionPlanSimulada[];
}

export interface MascotaCubierta {
  id: number;
  nombre: string;
  especie: string;
}

export interface PlanAfiliado {
  afiliadoId: number;
  planId: number;
  codigo: string;
  nombre: string;
  planActivo: boolean;
  fechaInicio: string;
  precioMensual: number;
  maximoMascotas: number;
  limiteAtenciones: number | null;
  atencionesIlimitadas: boolean;
  versionVigenteDesde: string;
  mascotasCubiertas: MascotaCubierta[];
  beneficios: BeneficioServicio[];
  periodoDesde: string;
  periodoHasta: string;
  atencionesIncluidasUsadas: number;
  atencionesIncluidasRestantes: number | null;
  atencionesIncluidasPeriodo: Prestacion[];
  bonosPeriodo: Bono[];
  cambioProgramado: CambioPlanProgramado | null;
  /** PENDIENTE_ACTIVACION: asignado, se activa al pagar la primera cuota. */
  estado: 'PENDIENTE_ACTIVACION' | 'ACTIVO' | 'SUSPENDIDO';
  /** Último día del plan si el afiliado lo canceló. */
  fechaFin: string | null;
  cuotaPendiente: CuotaPlan | null;
}

export interface CancelacionPlan {
  plan: string;
  inmediata: boolean;
  ultimoDia: string;
  motivo: string;
  valorAnulado: number;
  deudaPendiente: number;
}

export interface CambioPlanProgramado {
  planId: number;
  codigo: string;
  nombre: string;
  fechaActivacion: string;
  precioMensual: number | null;
  mascotas: MascotaCubierta[];
}

export interface AsignarPlanRequest {
  planId: number;
  mascotaIds: number[];
}
