import { DatePipe, KeyValuePipe, TitleCasePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { fechaLocalHoy, formatearPesos, mensajeError, sumarDias } from '../../../../core/format/moneda';
import {
  CanalPago,
  ConceptoPago,
  ConciliacionPagos,
  EstadoPago,
  etiquetaMedio,
  FiltrosConciliacion,
  MedioPago,
  Pago
} from '../../models/pago.models';
import { PagoApiService } from '../../services/pago-api.service';

/** Conciliación del área financiera: cada pago con su origen, medio y estado, con totales y exportación. */
@Component({
  selector: 'app-finanzas',
  standalone: true,
  imports: [DatePipe, KeyValuePipe, TitleCasePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './finanzas.component.html'
})
export class FinanzasComponent implements OnInit {
  private readonly api = inject(PagoApiService);

  readonly formatearPesos = formatearPesos;
  readonly etiquetaMedio = etiquetaMedio;
  readonly filtros = new FormGroup({
    desde: new FormControl(sumarDias(fechaLocalHoy(), -30), { nonNullable: true }),
    hasta: new FormControl(fechaLocalHoy(), { nonNullable: true }),
    estado: new FormControl<EstadoPago | ''>('', { nonNullable: true }),
    concepto: new FormControl<ConceptoPago | ''>('', { nonNullable: true }),
    medio: new FormControl<MedioPago | ''>('', { nonNullable: true }),
    canal: new FormControl<CanalPago | ''>('', { nonNullable: true })
  });

  conciliacion: ConciliacionPagos | null = null;
  cargando = false;
  error = '';

  ngOnInit(): void {
    this.consultar();
  }

  consultar(): void {
    this.cargando = true;
    this.error = '';
    const filtros: FiltrosConciliacion = this.filtros.getRawValue();
    this.api.conciliacion(filtros).subscribe({
      next: conciliacion => {
        this.conciliacion = conciliacion;
        this.cargando = false;
      },
      error: error => {
        this.error = mensajeError(error, 'No se pudo consultar la conciliación.');
        this.cargando = false;
      }
    });
  }

  limpiar(): void {
    this.filtros.reset();
    this.consultar();
  }

  /** Descarga los pagos consultados en CSV (separado por punto y coma, para Excel en español). */
  exportar(): void {
    const pagos = this.conciliacion?.pagos ?? [];
    const encabezado = ['Referencia', 'Fecha registro', 'Fecha pago', 'Afiliado', 'Concepto', 'Origen', 'Monto', 'Medio',
      'Canal', 'Estado', 'Transacción pasarela', 'Detalle'];
    const filas = pagos.map((pago: Pago) => [
      pago.referencia, pago.creadoEn.slice(0, 10), pago.fechaPago ?? '', pago.afiliado,
      pago.concepto === 'CUOTA_PLAN' ? 'Cuota del plan' : 'Bono', pago.descripcion, String(pago.monto),
      etiquetaMedio(pago.medioPago), pago.canal === 'PASARELA' ? 'En línea' : 'En ISAPET', pago.estado,
      pago.transaccionPasarela ?? '', pago.detalle ?? ''
    ]);
    const csv = [encabezado, ...filas]
      .map(fila => fila.map(valor => `"${valor.replace(/"/g, '""')}"`).join(';'))
      .join('\r\n');
    // El BOM hace que Excel reconozca las tildes (UTF-8).
    const archivo = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
    const enlace = document.createElement('a');
    enlace.href = URL.createObjectURL(archivo);
    enlace.download = `conciliacion-pagos-${this.conciliacion?.desde}-a-${this.conciliacion?.hasta}.csv`;
    enlace.click();
    URL.revokeObjectURL(enlace.href);
  }
}
