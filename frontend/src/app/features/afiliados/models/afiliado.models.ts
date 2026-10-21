export interface CrearAfiliadoRequest {
  numeroIdentificacion: string;
  nombres: string;
  apellidos: string;
  celular: string;
  correo: string;
  direccion: string;
  /** Autorización previa, expresa e informada del titular (Ley 1581 de 2012). */
  autorizaTratamientoDatos: boolean;
}

export interface CrearAfiliadoResponse {
  id: number;
  numeroIdentificacion: string;
  nombres: string;
  apellidos: string;
  celular: string;
  correo: string;
  direccion: string;
}

export interface ActualizarAfiliadoRequest {
  nombres: string;
  apellidos: string;
  celular: string;
  correo: string;
  direccion: string;
}

export interface AfiliadoDetalle extends CrearAfiliadoResponse {
}

/** Estado del plan que se ve y se filtra en la lista de afiliados. */
export type EstadoPlanAfiliado = 'SIN_PLAN' | 'POR_ACTIVAR' | 'ACTIVO' | 'SUSPENDIDO';

export interface AfiliadoListaItem extends AfiliadoDetalle {
  cantidadMascotas: number;
  /** Nombre del plan, o null si no tiene. */
  plan: string | null;
  estadoPlan: EstadoPlanAfiliado;
}

export interface Pagina<T> {
  content: T[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface CrearMascotaRequest {
  nombre: string;
  especie: string;
  raza: string;
  edad: number;
  color: string;
}

export interface CrearMascotaResponse extends CrearMascotaRequest {
  id: number;
  afiliadoId: number;
}

export interface ResumenEliminacionAfiliado {
  afiliadoId: number;
  numeroIdentificacion: string;
  nombreCompleto: string;
  plan: string | null;
  mascotas: string[];
  atenciones: number;
  bonos: number;
  cuotas: number;
}

export interface ApiErrorResponse {
  status: number;
  message: string;
  errors: string[];
}

export const ESPECIES_MASCOTA = [
  'Perro',
  'Gato',
  'Ave',
  'Conejo',
  'Hámster',
  'Pez',
  'Tortuga',
  'Otro'
] as const;