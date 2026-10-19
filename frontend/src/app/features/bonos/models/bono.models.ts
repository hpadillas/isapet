export type EstadoBono = 'DISPONIBLE' | 'USADO';

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
  bonosDisponiblesMes: number | null;
}

export interface CompraBonoRequest {
  mascotaId: number;
  servicioId: number;
}
