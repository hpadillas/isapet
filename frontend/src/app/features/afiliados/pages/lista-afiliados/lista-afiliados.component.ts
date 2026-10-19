import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { AfiliadosStore } from '../../services/afiliados.store';

@Component({
  selector: 'app-lista-afiliados',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './lista-afiliados.component.html',
  styleUrl: './lista-afiliados.component.scss'
})
export class ListaAfiliadosComponent implements OnInit {
  private readonly store = inject(AfiliadosStore);
  private readonly destroyRef = inject(DestroyRef);

  readonly busqueda = new FormControl('', { nonNullable: true });
  readonly pagina = this.store.pagina;
  readonly cargando = this.store.loading;
  readonly error = this.store.error;

  ngOnInit(): void {
    this.busqueda.valueChanges.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.cargar(0));
    this.cargar(0);
  }

  cargar(page: number): void {
    this.store.cargarAfiliados(this.busqueda.value, page);
  }

  limpiarBusqueda(): void {
    this.busqueda.setValue('');
    this.cargar(0);
  }
}
