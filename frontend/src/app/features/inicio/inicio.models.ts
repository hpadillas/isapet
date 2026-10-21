export type TipoAlerta = 'SUSPENDIDO' | 'PENDIENTE_ACTIVACION' | 'CUOTA_POR_VENCER' | 'CAMBIO_PROGRAMADO' | 'CANCELACION_PROGRAMADA';

export interface AlertaInicio {
  tipo: TipoAlerta;
  afiliadoId: number;
  afiliado: string;
  mensaje: string;
  fecha: string;
}

export interface AfiliadoReciente {
  id: number;
  nombreCompleto: string;
  numeroIdentificacion: string;
  plan: string | null;
}

export interface ResumenInicio {
  fecha: string;
  totalAfiliados: number;
  afiliadosConPlan: number;
  planesActivos: number;
  cuotasPendientes: number;
  valorPendiente: number;
  afiliadosSuspendidos: number;
  atencionesDelMes: number;
  alertas: AlertaInicio[];
  ultimosAfiliados: AfiliadoReciente[];
}
