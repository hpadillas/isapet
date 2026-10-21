import { MedioPago } from '../../pagos/models/pago.models';

export type EstadoBono = 'PENDIENTE_PAGO' | 'DISPONIBLE' | 'USADO' | 'ANULADO';

export const ETIQUETA_ESTADO_BONO: Record<EstadoBono, string> = {
  PENDIENTE_PAGO: 'Pendiente de pago',
  DISPONIBLE: 'Disponible',
  USADO: 'Usado',
  ANULADO: 'Anulado'
};

export interface Bono {
  id: number;
  codigo: string;
  mascotaId: number;
  mascotaNombre: string;
  servicioId: number;
  servicioNombre: string;
  tipoServicio: string;
  planCodigo: string;
  valorBase: number;
  porcentajeDescuento: number;
  valorPagado: number;
  fechaCompra: string;
  estado: EstadoBono;
}

export interface CotizacionBono {
  mascotaId: number;
  mascotaNombre: string;
  servicioId: number;
  servicioNombre: string;
  planCodigo: string;
  valorBase: number;
  porcentajeDescuento: number;
  valorAPagar: number;
  ahorro: number;
}

export interface CompraBonoRequest {
  mascotaId: number;
  /** Medio con el que pagó en ISAPET; los pagos en línea usan la pasarela. */
  medioPago: MedioPago;
  servicioId: number;
}
