import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { CheckoutSimuladoComponent } from './checkout-simulado.component';

describe('CheckoutSimuladoComponent', () => {
  let fixture: ComponentFixture<CheckoutSimuladoComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CheckoutSimuladoComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ referencia: 'PG-ABC' })) } }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CheckoutSimuladoComponent);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/pasarela-simulada/PG-ABC').flush({
      referencia: 'PG-ABC', afiliadoId: 7, afiliado: 'Ana Lopez', concepto: 'CUOTA_PLAN',
      descripcion: 'Cuota plan Básico del 20/10/2026 al 19/11/2026', monto: 60000, estado: 'PENDIENTE'
    });
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('muestra el resumen del pago sin pedir datos de tarjeta', () => {
    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('Pasarela de pagos');
    expect(texto).toContain('Cuota plan Básico');
    expect(texto).toContain('$ 60.000');
    expect(texto).toContain('no ingreses datos de tarjetas');
    expect(fixture.nativeElement.querySelectorAll('input[type="text"], input[type="number"]').length).toBe(0);
  });

  it('al aprobar, notifica el resultado y vuelve a los pagos del afiliado', () => {
    const navegar = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const aprobar = (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .find(boton => boton.textContent?.includes('Aprobar'))!;

    aprobar.click();
    const resultado = http.expectOne('/api/v1/admin/pasarela-simulada/PG-ABC/resultado');
    expect(resultado.request.body).toEqual({ aprobado: true, medioPago: 'TARJETA' });
    resultado.flush({ estado: 'APROBADO' });

    expect(navegar).toHaveBeenCalledWith(['/afiliados', 7, 'pagos'], { queryParams: { pago: 'APROBADO', ref: 'PG-ABC' } });
  });
});
