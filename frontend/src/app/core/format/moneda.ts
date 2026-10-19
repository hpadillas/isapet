const formatoPesos = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  maximumFractionDigits: 0
});

export function formatearPesos(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : formatoPesos.format(valor);
}

export function fechaLocalHoy(): string {
  const hoy = new Date();
  const mes = String(hoy.getMonth() + 1).padStart(2, '0');
  const dia = String(hoy.getDate()).padStart(2, '0');
  return `${hoy.getFullYear()}-${mes}-${dia}`;
}

/** Mensaje legible a partir del ErrorResponse del backend. */
export function mensajeError(error: unknown, porDefecto: string): string {
  const respuesta = error as { status?: number; error?: { message?: string; errors?: string[] } | null };
  if (respuesta?.status === 0) {
    return 'No se pudo conectar con el servidor.';
  }
  const detalle = respuesta?.error?.errors?.length ? ` ${respuesta.error.errors.join(' ')}` : '';
  return respuesta?.error?.message ? `${respuesta.error.message}${detalle}` : porDefecto;
}
