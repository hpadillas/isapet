import { Component, Input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-afiliado-tabs',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="affiliate-tabs" aria-label="Secciones del afiliado">
      <a [routerLink]="['/afiliados', afiliadoId]" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">Datos y mascotas</a>
      <a [routerLink]="['/afiliados', afiliadoId, 'plan']" routerLinkActive="active">Plan</a>
      <a [routerLink]="['/afiliados', afiliadoId, 'bonos']" routerLinkActive="active">Bonos</a>
      <a [routerLink]="['/afiliados', afiliadoId, 'prestaciones']" routerLinkActive="active">Prestaciones</a>
    </nav>
  `
})
export class AfiliadoTabsComponent {
  @Input({ required: true }) afiliadoId!: number;
}
