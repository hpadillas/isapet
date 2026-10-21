import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { FinanzasComponent } from './finanzas.component';

describe('FinanzasComponent', () => {
  let fixture: ComponentFixture<FinanzasComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FinanzasComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FinanzasComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('consulta los últimos 30 días y muestra totales y el origen de cada pago', () => {
    const peticion = http.expectOne(req => req.url === '/api/v1/admin/pagos');
    expect(peticion.request.params.get('desde')).toBeTruthy();
    expect(peticion.request.params.has('estado')).toBeFalse();
    peticion.flush({
      desde: '2026-09-21', hasta: '2026-10-21', totalAprobado: 102000, totalPendiente: 60000, totalRechazado: 0,
      aprobadoPorMedio: { EFECTIVO: 60000, PSE: 42000 },
      pagos: [{
        id: 1, referencia: 'PG-1', afiliadoId: 7, afiliado: 'Ana Lopez', concepto: 'BONO',
        descripcion: 'Bono BN-000003 · Consulta general · Rocky', cuotaId: null, bonoId: 3, monto: 42000, medioPago: 'PSE',
        canal: 'PASARELA', estado: 'APROBADO', fechaPago: '2026-10-21', creadoEn: '2026-10-21T10:00:00Z',
        confirmadoEn: '2026-10-21T10:01:00Z', transaccionPasarela: 'SIM-1', detalle: null
      }]
    });
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('$ 102.000');
    expect(texto).toContain('Efectivo: $ 60.000');
    expect(texto).toContain('Bono BN-000003 · Consulta general · Rocky');
    expect(texto).toContain('En línea');
    expect(texto).toContain('Pasarela: SIM-1');
  });

  it('envía solo los filtros elegidos', () => {
    http.expectOne(req => req.url === '/api/v1/admin/pagos').flush(vacio());
    fixture.componentInstance.filtros.patchValue({ estado: 'RECHAZADO', canal: 'PASARELA' });
    fixture.componentInstance.consultar();

    const peticion = http.expectOne(req => req.url === '/api/v1/admin/pagos');
    expect(peticion.request.params.get('estado')).toBe('RECHAZADO');
    expect(peticion.request.params.get('canal')).toBe('PASARELA');
    expect(peticion.request.params.has('medio')).toBeFalse();
    peticion.flush(vacio());
  });

  function vacio() {
    return { desde: '2026-09-21', hasta: '2026-10-21', pagos: [], totalAprobado: 0, totalPendiente: 0, totalRechazado: 0, aprobadoPorMedio: {} };
  }
});
