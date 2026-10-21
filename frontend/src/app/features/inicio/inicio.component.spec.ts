import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { InicioComponent } from './inicio.component';
import { ResumenInicio } from './inicio.models';

describe('InicioComponent', () => {
  let fixture: ComponentFixture<InicioComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InicioComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InicioComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function texto(): string {
    return (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
  }

  it('muestra cifras, alertas con su acción y los últimos afiliados', () => {
    http.expectOne('/api/v1/admin/resumen').flush(resumen());
    fixture.detectChanges();

    expect(texto()).toMatch(/Buenos días|Buenas tardes|Buenas noches/);
    expect(texto()).toContain('Miércoles, 21 de octubre de 2026');
    expect(texto()).toContain('Requiere atención 2');
    expect(texto()).toContain('$ 120.000');
    expect(texto()).toContain('2 cuotas pendientes');
    expect(texto()).toContain('Luis Prueba');
    expect(texto()).toContain('Plan suspendido');

    const enlaces = Array.from(fixture.nativeElement.querySelectorAll('.alert-item a')) as HTMLAnchorElement[];
    expect(enlaces.map(a => a.getAttribute('href'))).toEqual(['/afiliados/2/pagos', '/afiliados/1/plan']);
    expect(enlaces.map(a => a.textContent?.trim())).toEqual(['Registrar pago', 'Ver plan']);

    expect(texto()).toContain('Ana Prueba');
    expect(texto()).toContain('Sin plan · Asignar plan →');
    expect(texto()).toContain('Plan Básico');
  });

  it('indica cuando no hay nada pendiente', () => {
    http.expectOne('/api/v1/admin/resumen').flush({ ...resumen(), alertas: [] });
    fixture.detectChanges();

    expect(texto()).toContain('Todo al día');
    expect(fixture.nativeElement.querySelector('.attention')).toBeNull();
  });

  it('la búsqueda abre el listado de afiliados filtrado', () => {
    http.expectOne('/api/v1/admin/resumen').flush(resumen());
    const router = TestBed.inject(Router);
    const navegar = spyOn(router, 'navigate').and.resolveTo(true);

    fixture.componentInstance.busqueda.setValue('  Padilla ');
    fixture.componentInstance.buscar();

    expect(navegar).toHaveBeenCalledWith(['/afiliados'], { queryParams: { q: 'Padilla' } });
    expect(fixture.nativeElement.querySelector('a[href="/afiliados/registro"]')?.textContent).toContain('+ Nuevo afiliado');
  });

  function resumen(): ResumenInicio {
    return {
      fecha: '2026-10-21', totalAfiliados: 3, afiliadosConPlan: 2, planesActivos: 3, cuotasPendientes: 2,
      valorPendiente: 120000, afiliadosSuspendidos: 1, atencionesDelMes: 4,
      alertas: [
        { tipo: 'SUSPENDIDO', afiliadoId: 2, afiliado: 'Luis Prueba', mensaje: 'Plan suspendido: la cuota venció.', fecha: '2026-10-15' },
        { tipo: 'CANCELACION_PROGRAMADA', afiliadoId: 1, afiliado: 'Eva Prueba', mensaje: 'Canceló el plan.', fecha: '2026-11-19' }
      ],
      ultimosAfiliados: [
        { id: 3, nombreCompleto: 'Ana Prueba', numeroIdentificacion: '100', plan: null },
        { id: 2, nombreCompleto: 'Luis Prueba', numeroIdentificacion: '200', plan: 'Básico' }
      ]
    };
  }
});
