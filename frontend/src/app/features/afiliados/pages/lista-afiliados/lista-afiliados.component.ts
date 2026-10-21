import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EliminarAfiliadoComponent } from '../../components/eliminar-afiliado/eliminar-afiliado.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { EstadoPlanAfiliado } from '../../models/afiliado.models';
import { AfiliadosStore } from '../../services/afiliados.store';

/** Etiqueta y color de cada estado del plan en la lista. */
const ESTADOS: Record<EstadoPlanAfiliado, { etiqueta: string; clase: string }> = {
  ACTIVO: { etiqueta: 'Activo', clase: 'ok' },
  POR_ACTIVAR: { etiqueta: 'Por activar', clase: 'warning' },
  SUSPENDIDO: { etiqueta: 'Suspendido', clase: 'danger' },
  SIN_PLAN: { etiqueta: 'Sin plan', clase: 'muted' }
};

@Component({
  selector: 'app-lista-afiliados',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, EliminarAfiliadoComponent],
  templateUrl: './lista-afiliados.component.html',
  styleUrl: './lista-afiliados.component.scss'
})
export class ListaAfiliadosComponent implements OnInit {
  private readonly store = inject(AfiliadosStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);

  readonly busqueda = new FormControl('', { nonNullable: true });
  readonly pagina = this.store.pagina;
  readonly cargando = this.store.loading;
  readonly error = this.store.error;
  readonly filtros: { valor: EstadoPlanAfiliado | ''; etiqueta: string; clase: string }[] = [
    { valor: '', etiqueta: 'Todos', clase: '' },
    ...(Object.keys(ESTADOS) as EstadoPlanAfiliado[]).map(valor => ({ valor, ...ESTADOS[valor] }))
  ];
  /** Filtro por estado del plan; vacío = todos. */
  estado: EstadoPlanAfiliado | '' = '';
  /** Nombre del último afiliado eliminado (desde aquí o desde su ficha). */
  afiliadoEliminado: string = history.state?.afiliadoEliminado ?? '';

  ngOnInit(): void {
    // Búsqueda que llega desde el panel de inicio (/afiliados?q=...).
    const consulta = this.route.snapshot.queryParamMap.get('q');
    if (consulta) {
      this.busqueda.setValue(consulta, { emitEvent: false });
    }
    this.busqueda.valueChanges.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.cargar(0));
    this.cargar(0);
  }

  cargar(page: number): void {
    this.store.cargarAfiliados(this.busqueda.value, page, this.estado);
  }

  filtrar(estado: EstadoPlanAfiliado | ''): void {
    if (this.estado === estado) return;
    this.estado = estado;
    this.cargar(0);
  }

  configuracionEstado(estado: EstadoPlanAfiliado) {
    return ESTADOS[estado] ?? ESTADOS.SIN_PLAN;
  }

  etiquetaEstado(estado: EstadoPlanAfiliado): string {
    return ESTADOS[estado].etiqueta;
  }

  alEliminarAfiliado(nombre: string): void {
    this.afiliadoEliminado = nombre;
    const pagina = this.pagina();
    // Si era el único de la última página, vuelve a la anterior.
    const quedaVacia = pagina !== null && pagina.content.length === 1 && pagina.page.number > 0;
    this.cargar(quedaVacia ? pagina.page.number - 1 : pagina?.page.number ?? 0);
  }

  limpiarFiltros(): void {
    this.estado = '';
    this.busqueda.setValue('', { emitEvent: false });
    this.cargar(0);
  }
}
