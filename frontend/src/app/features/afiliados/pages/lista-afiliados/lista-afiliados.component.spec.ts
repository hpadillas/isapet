import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AfiliadoListaItem } from '../../models/afiliado.models';
import { ListaAfiliadosComponent } from './lista-afiliados.component';

describe('ListaAfiliadosComponent', () => {
  let http: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<ListaAfiliadosComponent>>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListaAfiliadosComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ListaAfiliadosComponent);
  });

  afterEach(() => http.verify());

  it('carga y muestra afiliados con conteo de mascotas', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados?page=0&size=10').flush(page([affiliate(1, 'Ana', 2)]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ana');
    expect(fixture.nativeElement.textContent).toContain('ana@example.com');
    expect(fixture.nativeElement.querySelector('.pet-count')?.textContent).toContain('2');
  });

  it('envía búsqueda por texto y reinicia la página', fakeAsync(() => {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados?page=0&size=10').flush(page([]));
    fixture.detectChanges();

    fixture.componentInstance.busqueda.setValue('ana@example.com');
    tick(251);
    const request = http.expectOne('/api/v1/afiliados?page=0&size=10&q=ana%40example.com');
    expect(request.request.method).toBe('GET');
    request.flush(page([affiliate(1, 'Ana', 1)]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ana');
  }));

  it('muestra el plan con su estado y ofrece asignar uno a quien no tiene', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados?page=0&size=10').flush(page([
      affiliate(1, 'Ana', 1, { plan: 'Básico', estadoPlan: 'SUSPENDIDO' }),
      affiliate(2, 'Luis', 0)
    ]));
    fixture.detectChanges();

    const filas = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(filas[0].querySelector('[data-label="Plan"]').textContent).toContain('Básico');
    expect(filas[0].querySelector('.plan-status.status-danger').textContent).toContain('Suspendido');
    expect(filas[1].querySelector('.assign-plan').textContent).toContain('Sin plan · Asignar');
    // Una sola página: no hay paginación.
    expect(fixture.nativeElement.querySelector('.pagination-bar')).toBeNull();
  });

  it('filtra por estado del plan desde la primera página', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados?page=0&size=10').flush(page([affiliate(1, 'Ana', 1)]));
    fixture.detectChanges();

    const porActivar = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.status-filter'))
      .find(boton => boton.textContent?.includes('Por activar'))!;
    porActivar.click();
    http.expectOne('/api/v1/afiliados?page=0&size=10&estado=POR_ACTIVAR').flush(page([]));
    fixture.detectChanges();

    expect(porActivar.getAttribute('aria-pressed')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('Ningún afiliado está en «Por activar».');

    fixture.componentInstance.limpiarFiltros();
    http.expectOne('/api/v1/afiliados?page=0&size=10').flush(page([]));
  });

  it('muestra el estado vacío si no hay coincidencias', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados?page=0&size=10').flush(page([]));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aún no hay afiliados registrados');
  });

  function page(content: unknown[]) {
    return { content, page: { size: 10, number: 0, totalElements: content.length, totalPages: content.length ? 1 : 0 } };
  }

  function affiliate(id: number, nombres: string, cantidadMascotas: number, cambios: Partial<AfiliadoListaItem> = {}): AfiliadoListaItem {
    return {
      id,
      numeroIdentificacion: `ID-${id}`,
      nombres,
      apellidos: 'Lopez',
      celular: '3001234567',
      correo: 'ana@example.com',
      direccion: 'Calle 1',
      cantidadMascotas,
      plan: null,
      estadoPlan: 'SIN_PLAN',
      ...cambios
    };
  }
});
