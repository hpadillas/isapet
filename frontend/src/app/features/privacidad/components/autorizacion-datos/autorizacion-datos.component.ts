import { DatePipe } from '@angular/common';
import { Component, inject, Input, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { mensajeError } from '../../../../core/format/moneda';
import { ConfirmacionDialogComponent } from '../../../../core/ui/confirmacion-dialog.component';
import { AutorizacionDatos } from '../../privacidad.models';
import { PrivacidadApiService } from '../../privacidad-api.service';

/**
 * Estado de la autorización de tratamiento de datos de un afiliado. Para afiliados registrados antes de
 * este requisito permite dejar la prueba, previa confirmación de que el titular autorizó.
 */
@Component({
  selector: 'app-autorizacion-datos',
  standalone: true,
  imports: [DatePipe, RouterLink, ConfirmacionDialogComponent],
  template: `
    @if (autorizacion) {
      @if (autorizacion.registrada) {
        <p class="consent ok">
          <strong>Datos personales:</strong> autorización registrada el {{ autorizacion.otorgadaEn | date: 'dd/MM/yyyy HH:mm' }}
          (política v{{ autorizacion.versionPolitica }}, por {{ autorizacion.registradaPor }}).
          <a routerLink="/privacidad" target="_blank" rel="noopener">Ver política</a>
        </p>
      } @else {
        <p class="consent missing">
          <span><strong>Sin autorización de tratamiento de datos registrada</strong> (Ley 1581 de 2012).</span>
          <button type="button" (click)="confirmando = true; error = ''">Registrar autorización</button>
        </p>
      }
    }
    @if (confirmando) {
      <app-confirmacion-dialog
        titulo="Registrar autorización de datos personales"
        textoConfirmar="El titular autorizó"
        [procesando]="guardando"
        [error]="error"
        (confirmado)="registrar()"
        (cancelado)="confirmando = false">
        <p>Confirma que el titular leyó el <a routerLink="/privacidad" target="_blank" rel="noopener">aviso de privacidad</a>
          y autorizó de forma previa, expresa e informada a ISAPET el tratamiento de sus datos personales.</p>
        <p>Quedará registrado con la fecha, la versión de la política y tu usuario como prueba.</p>
      </app-confirmacion-dialog>
    }
  `,
  styles: [`
    .consent { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; margin: 14px 0 0;
      padding: 10px 12px; border-radius: 6px; font-size: 12px; }
    .consent.ok { color: #285b3a; background: #eff8f1; }
    .consent.missing { color: #6b5212; background: #fdf7e7; border: 1px solid #e6cf97; }
    .consent a { color: inherit; font-weight: 700; }
    .consent button { min-height: 32px; padding: 0 10px; border: 1px solid #d9b75e; border-radius: 5px; color: #6b5212;
      background: #fff; font-size: 11px; font-weight: 700; cursor: pointer; }
  `]
})
export class AutorizacionDatosComponent implements OnInit {
  private readonly api = inject(PrivacidadApiService);

  @Input({ required: true }) afiliadoId!: number;

  autorizacion: AutorizacionDatos | null = null;
  confirmando = false;
  guardando = false;
  error = '';

  ngOnInit(): void {
    this.api.autorizacion(this.afiliadoId).subscribe({ next: autorizacion => this.autorizacion = autorizacion });
  }

  registrar(): void {
    this.guardando = true;
    this.api.registrarAutorizacion(this.afiliadoId).subscribe({
      next: autorizacion => {
        this.autorizacion = autorizacion;
        this.guardando = false;
        this.confirmando = false;
      },
      error: error => {
        this.error = mensajeError(error, 'No se pudo registrar la autorización.');
        this.guardando = false;
      }
    });
  }
}
