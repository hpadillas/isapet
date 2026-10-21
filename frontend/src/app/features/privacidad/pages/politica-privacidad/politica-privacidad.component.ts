import { DatePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { mensajeError } from '../../../../core/format/moneda';
import { PoliticaPrivacidad } from '../../privacidad.models';
import { PrivacidadApiService } from '../../privacidad-api.service';

/** Política de tratamiento de datos personales: pública, para que cualquier titular la consulte. */
@Component({
  selector: 'app-politica-privacidad',
  standalone: true,
  imports: [DatePipe],
  template: `
    <main class="feature-page">
      <div class="content privacy-page">
        @if (error) {
          <section class="notice error-notice" role="alert">{{ error }}</section>
        } @else if (!politica) {
          <p class="state-message" role="status">Cargando política…</p>
        } @else {
          <header class="page-heading">
            <p class="eyebrow"><span aria-hidden="true"></span> ISAPET <span class="eyebrow-divider">/</span> PRIVACIDAD</p>
            <h1>Política de tratamiento de datos personales</h1>
            <p class="intro">Versión {{ politica.version }} · vigente desde el {{ politica.vigenteDesde | date: 'dd/MM/yyyy' }} ·
              Ley 1581 de 2012 y Decreto 1074 de 2015.</p>
          </header>

          <section class="panel">
            <h2>1. Responsable del tratamiento</h2>
            <dl class="facts">
              <div><dt>Responsable</dt><dd>{{ politica.responsable }}</dd></div>
              <div><dt>NIT</dt><dd>{{ politica.nit }}</dd></div>
              <div><dt>Dirección</dt><dd>{{ politica.direccion }}</dd></div>
              <div><dt>Correo</dt><dd>{{ politica.correo }}</dd></div>
              <div><dt>Teléfono</dt><dd>{{ politica.telefono }}</dd></div>
            </dl>
          </section>

          <section class="panel">
            <h2>2. Datos que se recolectan</h2>
            <ul>@for (dato of politica.datosRecolectados; track dato) { <li>{{ dato }}</li> }</ul>
            <p>ISAPET no solicita datos sensibles ni datos de niños, niñas o adolescentes. Si alguna pregunta versara sobre ese
              tipo de datos, responderla es facultativo.</p>
          </section>

          <section class="panel">
            <h2>3. Tratamiento y finalidades</h2>
            <p>Los datos se recolectan, almacenan, consultan, actualizan y usan únicamente para:</p>
            <ul>@for (finalidad of politica.finalidades; track finalidad) { <li>{{ finalidad }}</li> }</ul>
          </section>

          <section class="panel">
            <h2>4. Derechos del titular</h2>
            <ul>@for (derecho of politica.derechos; track derecho) { <li>{{ derecho }}</li> }</ul>
          </section>

          <section class="panel">
            <h2>5. Cómo ejercer sus derechos</h2>
            <p>Escriba a <strong>{{ politica.canalAtencion }}</strong> indicando su identificación, la solicitud y los datos de contacto
              para la respuesta.</p>
            <ul>
              <li><strong>Consultas</strong>: se responden en máximo {{ politica.diasHabilesConsultas }} días hábiles (art. 14).</li>
              <li><strong>Reclamos</strong> (corrección, actualización, supresión o incumplimiento): se responden en máximo
                {{ politica.diasHabilesReclamos }} días hábiles (art. 15).</li>
              <li>Si no está conforme con la respuesta, puede acudir a la Superintendencia de Industria y Comercio.</li>
            </ul>
          </section>

          <section class="panel">
            <h2>6. Autorización y vigencia</h2>
            <p>El tratamiento se realiza con la autorización previa, expresa e informada del titular, que ISAPET conserva como
              prueba con la fecha y la versión de esta política. Los datos se conservan mientras el titular esté afiliado y
              durante el tiempo que exijan las obligaciones legales; al eliminarse el afiliado, se suprimen sus datos y los de
              sus mascotas.</p>
          </section>
        }
      </div>
    </main>
  `,
  styles: [`
    .privacy-page { max-width: 820px; }
    .panel h2 { margin: 0 0 10px; font-size: 16px; color: var(--ink); }
    .panel ul { margin: 0 0 8px; padding-left: 20px; line-height: 1.7; font-size: 13px; color: #344b45; }
    .panel p { margin: 0 0 8px; font-size: 13px; line-height: 1.6; color: #344b45; }
  `]
})
export class PoliticaPrivacidadComponent implements OnInit {
  private readonly api = inject(PrivacidadApiService);

  politica: PoliticaPrivacidad | null = null;
  error = '';

  ngOnInit(): void {
    this.api.politica().subscribe({
      next: politica => this.politica = politica,
      error: error => this.error = mensajeError(error, 'No se pudo cargar la política de privacidad.')
    });
  }
}
