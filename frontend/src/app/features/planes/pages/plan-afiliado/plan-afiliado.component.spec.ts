import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CuotaPlan } from '../../../pagos/models/pago.models';
import { CancelacionPlan, PlanAfiliado } from '../../models/afiliacion.models';
import { PlanAfiliadoComponent } from './plan-afiliado.component';

describe('PlanAfiliadoComponent', () => {
  let fixture: ComponentFixture<PlanAfiliadoComponent>;
  let component: PlanAfiliadoComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PlanAfiliadoComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '7' })) } }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PlanAfiliadoComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => http.verify());

  function cargar(plan: PlanAfiliado): void {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados/7').flush({ id: 7, numeroIdentificacion: '100', nombres: 'Ana', apellidos: 'Lopez',
      celular: '300', correo: 'ana@example.com', direccion: 'Calle 1' });
    http.expectOne('/api/v1/afiliados/7/mascotas').flush([]);
    http.expectOne('/api/v1/admin/planes').flush([]);
    http.expectOne('/api/v1/afiliados/7/plan').flush(plan);
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
  }

  it('avisa la suspensión por falta de pago con la fecha límite vencida', () => {
    cargar(plan({ estado: 'SUSPENDIDO', cuotaPendiente: cuota({ vencida: true }) }));

    expect(texto()).toContain('Plan suspendido por falta de pago.');
    expect(texto()).toContain('debía pagarse hasta el 24/11/2026');
  });

  it('un plan recién asignado queda pendiente de activación hasta pagar la primera cuota', () => {
    cargar(plan({ estado: 'PENDIENTE_ACTIVACION', cuotaPendiente: cuota({ deActivacion: true, fechaLimitePago: null }) }));

    expect(texto()).toContain('Plan pendiente de activación.');
    expect(texto()).toContain('Se activa cuando se pague la primera cuota de $ 60.000');
    expect(texto()).not.toContain('o el plan se suspende');
  });

  it('avisa la cuota pendiente dentro del plazo de pago', () => {
    cargar(plan({ cuotaPendiente: cuota() }));

    expect(texto()).toContain('Debe pagarse hasta el 24/11/2026 o el plan se suspende.');
  });

  it('cancelar con el periodo pagado muestra hasta cuándo sigue el plan', () => {
    cargar(plan());

    component.prepararCancelacionPlan();
    http.expectOne('/api/v1/afiliados/7/plan/cancelacion').flush(cancelacion({ inmediata: false, ultimoDia: '2026-12-19' }));
    fixture.detectChanges();

    expect(texto()).toContain('¿Cancelar el plan Básico?');
    expect(texto()).toContain('sigue activo hasta el 19/12/2026');

    component.confirmarCancelacionPlan();
    const peticion = http.expectOne(req => req.method === 'POST' && req.url === '/api/v1/afiliados/7/plan/cancelacion');
    peticion.flush(cancelacion({ inmediata: false, ultimoDia: '2026-12-19' }));
    http.expectOne('/api/v1/afiliados/7/plan').flush(plan({ fechaFin: '2026-12-19' }));
    fixture.detectChanges();

    expect(texto()).toContain('Cancelación programada.');
    expect(texto()).toContain('Deshacer cancelación');
  });

  it('cancelar dentro del plazo sin pagar termina hoy y anula la cuota', () => {
    cargar(plan({ cuotaPendiente: cuota() }));

    component.prepararCancelacionPlan();
    http.expectOne('/api/v1/afiliados/7/plan/cancelacion')
      .flush(cancelacion({ inmediata: true, ultimoDia: '2026-11-21', valorAnulado: 60000 }));
    fixture.detectChanges();

    expect(texto()).toContain('termina hoy, 21/11/2026');
    expect(texto()).toContain('Se anulará la cuota pendiente de $ 60.000');

    component.confirmarCancelacionPlan();
    http.expectOne(req => req.method === 'POST').flush(cancelacion({ inmediata: true, ultimoDia: '2026-11-21', valorAnulado: 60000 }));
    fixture.detectChanges();

    expect(component.plan).toBeNull();
    expect(texto()).toContain('Se anuló la cuota pendiente de $ 60.000');
  });

  function cuota(cambios: Partial<CuotaPlan> = {}): CuotaPlan {
    return { id: 3, planCodigo: 'BASICO', planNombre: 'Básico', periodoDesde: '2026-11-20', periodoHasta: '2026-12-19',
      valor: 60000, estado: 'PENDIENTE', fechaLimitePago: '2026-11-24', vencida: false, deActivacion: false, fechaPago: null,
      medioPago: null, ...cambios };
  }

  function cancelacion(cambios: Partial<CancelacionPlan>): CancelacionPlan {
    return { plan: 'Básico', inmediata: false, ultimoDia: '2026-12-19', motivo: 'Motivo', valorAnulado: 0, deudaPendiente: 0, ...cambios };
  }

  function plan(cambios: Partial<PlanAfiliado> = {}): PlanAfiliado {
    return {
      afiliadoId: 7, planId: 1, codigo: 'BASICO', nombre: 'Básico', planActivo: true, fechaInicio: '2026-10-20',
      precioMensual: 60000, maximoMascotas: 1, limiteAtenciones: 2, atencionesIlimitadas: false, versionVigenteDesde: '2026-10-19',
      mascotasCubiertas: [], beneficios: [], periodoDesde: '2026-11-20', periodoHasta: '2026-12-19',
      atencionesIncluidasUsadas: 0, atencionesIncluidasRestantes: 2, atencionesIncluidasPeriodo: [], bonosPeriodo: [],
      cambioProgramado: null, estado: 'ACTIVO', fechaFin: null, cuotaPendiente: null, ...cambios
    };
  }
});
