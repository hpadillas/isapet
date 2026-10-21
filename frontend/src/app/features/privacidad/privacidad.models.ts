/** Aviso de privacidad y política de tratamiento de datos (Ley 1581 de 2012). */
export interface PoliticaPrivacidad {
  version: string;
  vigenteDesde: string;
  responsable: string;
  nit: string;
  direccion: string;
  correo: string;
  telefono: string;
  datosRecolectados: string[];
  finalidades: string[];
  derechos: string[];
  canalAtencion: string;
  diasHabilesConsultas: number;
  diasHabilesReclamos: number;
}

/** Prueba de la autorización del titular (arts. 9 y 17-b). */
export interface AutorizacionDatos {
  registrada: boolean;
  versionPolitica: string | null;
  otorgadaEn: string | null;
  registradaPor: string | null;
  medio: string | null;
}
