import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EliminarAfiliadoComponent } from './eliminar-afiliado.component';

describe('EliminarAfiliadoComponent', () => {
  let fixture: ComponentFixture<EliminarAfiliadoComponent>;
  let component: EliminarAfiliadoComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EliminarAfiliadoComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EliminarAfiliadoComponent);
    component = fixture.componentInstance;
    component.afiliadoId = 7;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function abrirConfirmacion(): string {
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    http.expectOne('/api/v1/afiliados/7/eliminacion').flush({
      afiliadoId: 7, numeroIdentificacion: '100', nombreCompleto: 'Ana Lopez', plan: 'Básico',
      mascotas: ['Rocky', 'Luna'], atenciones: 3, bonos: 1, cuotas: 2
    });
    fixture.detectChanges();
    return fixture.nativeElement.textContent as string;
  }

  it('advierte que se borrará la historia clínica de las mascotas antes de eliminar', () => {
    const texto = abrirConfirmacion();

    expect(texto).toContain('¿Eliminar este afiliado?');
    expect(texto).toContain('Esta acción no se puede deshacer.');
    expect(texto).toContain('2 mascotas (Rocky, Luna) con su historia clínica');
    expect(texto).toContain('3 atenciones registradas');
    expect(texto).toContain('El historial de pagos: 2 cuotas');
  });

  it('no elimina hasta que se confirma haber entendido la pérdida de la historia clínica', () => {
    const eliminados: string[] = [];
    component.eliminado.subscribe(nombre => eliminados.push(nombre));
    abrirConfirmacion();

    component.eliminar();
    http.expectNone('/api/v1/afiliados/7');
    expect(component.errorEliminar).toContain('Marca la casilla');

    component.entendido = true;
    component.eliminar();
    const peticion = http.expectOne('/api/v1/afiliados/7');
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null, { status: 204, statusText: 'No Content' });

    expect(eliminados).toEqual(['Ana Lopez']);
    expect(component.resumen).toBeNull();
  });
});
