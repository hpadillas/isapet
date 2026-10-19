import { computed, inject, Injectable, signal } from '@angular/core';
import { AfiliadoApiService } from './afiliado-api.service';
import { AfiliadoListaItem, AfiliadoDetalle, CrearAfiliadoRequest, Pagina } from '../models/afiliado.models';

export interface AfiliadosState {
  pagina: Pagina<AfiliadoListaItem> | null;
  loading: boolean;
  error: string | null;
  selectedAfiliado: AfiliadoDetalle | null;
}

@Injectable({ providedIn: 'root' })
export class AfiliadosStore {
  private api = inject(AfiliadoApiService);

  // El Estado interno
  private state = signal<AfiliadosState>({
    pagina: null,
    loading: false,
    error: null,
    selectedAfiliado: null
  });

  // Selectores expuestos a los componentes (readonly)
  readonly pagina = computed(() => this.state().pagina);
  readonly loading = computed(() => this.state().loading);
  readonly error = computed(() => this.state().error);
  readonly selectedAfiliado = computed(() => this.state().selectedAfiliado);

  // Acciones (Mutan el estado)
  async cargarAfiliados(busqueda: string = '', page: number = 0) {
    this.state.update(s => ({ ...s, loading: true, error: null }));
    
    this.api.listarAfiliados(busqueda, page).subscribe({
      next: (paginaResult) => {
        this.state.update(s => ({ 
          ...s, 
          pagina: paginaResult, 
          loading: false 
        }));
      },
      error: (err) => {
        this.state.update(s => ({ ...s, error: 'Error al cargar afiliados', loading: false }));
      }
    });
  }

  async cargarDetalle(id: number) {
    this.state.update(s => ({ ...s, loading: true, error: null, selectedAfiliado: null }));
    
    this.api.obtenerAfiliado(id).subscribe({
      next: (detalle) => {
        this.state.update(s => ({ ...s, selectedAfiliado: detalle, loading: false }));
      },
      error: (err) => {
        this.state.update(s => ({ ...s, error: 'Error al cargar detalle', loading: false }));
      }
    });
  }

  // Agrega más acciones (crear, actualizar, etc.) siguiendo el mismo patrón.
}
