export type EstadoCuota = 'PENDIENTE' | 'PAGADA' | 'ANULADA';
export type MedioPago = 'EFECTIVO' | 'TARJETA' | 'TRANSFERENCIA' | 'PSE';

export const MEDIOS_PAGO: { valor: MedioPago; etiqueta: string }[] = [
  { valor: 'EFECTIVO', etiqueta: 'Efectivo' },
  { valor: 'TARJETA', etiqueta: 'Tarjeta' },
  { valor: 'TRANSFERENCIA', etiqueta: 'Transferencia' }
];

/** Medios disponibles en la pasarela en línea. */
export const MEDIOS_EN_LINEA: { valor: MedioPago; etiqueta: string }[] = [
  { valor: 'TARJETA', etiqueta: 'Tarjeta débito o crédito' },
  { valor: 'PSE', etiqueta: 'PSE (débito desde cuenta bancaria)' }
];

export type EstadoPago = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO' | 'ANULADO';
export type ConceptoPago = 'CUOTA_PLAN' | 'BONO';
export type CanalPago = 'MANUAL' | 'PASARELA';

export function etiquetaMedio(medio: MedioPago | string | null): string {
  if (medio === null) return 'No registrado';
  if (medio === 'PSE') return 'PSE';
  return MEDIOS_PAGO.find(opcion => opcion.valor === medio)?.etiqueta ?? medio;
}

/** Transacción del libro de pagos. */
export interface Pago {
  id: number;
  referencia: string;
  afiliadoId: number;
  afiliado: string;
  concepto: ConceptoPago;
  descripcion: string;
  cuotaId: number | null;
  bonoId: number | null;
  monto: number;
  medioPago: MedioPago | null;
  canal: CanalPago;
  estado: EstadoPago;
  fechaPago: string | null;
  creadoEn: string;
  confirmadoEn: string | null;
  transaccionPasarela: string | null;
  detalle: string | null;
}

export interface PagoEnLinea {
  referencia: string;
  urlPago: string;
  monto: number;
  descripcion: string;
}

export interface SesionPago {
  referencia: string;
  afiliadoId: number;
  afiliado: string;
  concepto: ConceptoPago;
  descripcion: string;
  monto: number;
  estado: EstadoPago;
}

export interface ConciliacionPagos {
  desde: string;
  hasta: string;
  pagos: Pago[];
  totalAprobado: number;
  totalPendiente: number;
  totalRechazado: number;
  aprobadoPorMedio: Record<string, number>;
}

export interface FiltrosConciliacion {
  desde: string;
  hasta: string;
  estado: EstadoPago | '';
  concepto: ConceptoPago | '';
  medio: MedioPago | '';
  canal: CanalPago | '';
}

export interface CuotaPlan {
  id: number;
  planCodigo: string;
  planNombre: string;
  periodoDesde: string;
  periodoHasta: string;
  valor: number;
  estado: EstadoCuota;
  /** Último día para pagar sin que el plan se suspenda; null en la cuota de activación. */
  fechaLimitePago: string | null;
  vencida: boolean;
  /** Primera cuota: pagarla activa el plan. */
  deActivacion: boolean;
  fechaPago: string | null;
  medioPago: MedioPago | null;
}

export interface HistorialPagos {
  totalPagado: number;
  totalPendiente: number;
  cuotas: CuotaPlan[];
}

export interface RegistrarPagoRequest {
  fechaPago: string;
  medioPago: MedioPago;
}
