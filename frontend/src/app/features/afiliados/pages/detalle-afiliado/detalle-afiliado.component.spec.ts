import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { DetalleAfiliadoComponent } from './detalle-afiliado.component';

describe('DetalleAfiliadoComponent', () => {
  let fixture: ComponentFixture<DetalleAfiliadoComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DetalleAfiliadoComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: '7' })), queryParamMap: of(convertToParamMap({})) } }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DetalleAfiliadoComponent);
  });

  afterEach(() => {
    // El bloque de autorización de datos personales consulta su propio estado.
    http.match(req => req.url.endsWith('/autorizacion-datos')).forEach(req => req.flush({
      registrada: true, versionPolitica: '1.0', otorgadaEn: '2026-10-21T10:00:00Z', registradaPor: 'admin', medio: 'Formulario'
    }));
    http.verify();
  });

  it('muestra el detalle y mensaje cuando el afiliado no tiene mascotas', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados/7').flush(affiliate());
    http.expectOne('/api/v1/afiliados/7/mascotas').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ana Lopez');
    expect(fixture.nativeElement.textContent).toContain('Este afiliado no tiene mascotas registradas.');
  });

  it('agrega mascota asociada al afiliado actual y la presenta en la lista', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados/7').flush(affiliate());
    http.expectOne('/api/v1/afiliados/7/mascotas').flush([]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.pet-heading .primary-button').click();
    fixture.detectChanges();
    fixture.componentInstance.mascotaForm.setValue({ nombre: 'Rocky', especie: 'Perro', raza: 'Labrador', edad: 3, color: 'Café' });
    fixture.componentInstance.guardarMascota();

    const request = http.expectOne('/api/v1/afiliados/7/mascotas');
    expect(request.request.body).toEqual({ nombre: 'Rocky', especie: 'Perro', raza: 'Labrador', edad: 3, color: 'Café' });
    request.flush({ id: 12, afiliadoId: 7, ...request.request.body });
    fixture.detectChanges();

    expect(fixture.componentInstance.mascotas.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Rocky');
  });

  it('edita mascota existente sin enviar afiliado propietario', () => {
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados/7').flush(affiliate());
    http.expectOne('/api/v1/afiliados/7/mascotas').flush([
      { id: 12, afiliadoId: 7, nombre: 'Rocky', especie: 'Perro', raza: 'Labrador', edad: 3, color: 'Café' }
    ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.inline-action').click();
    fixture.detectChanges();
    fixture.componentInstance.mascotaForm.controls.edad.setValue(4);
    fixture.componentInstance.guardarMascota();
    const request = http.expectOne('/api/v1/mascotas/12');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body.afiliadoId).toBeUndefined();
    request.flush({ id: 12, afiliadoId: 7, nombre: 'Rocky', especie: 'Perro', raza: 'Labrador', edad: 4, color: 'Café' });
    fixture.detectChanges();
    expect(fixture.componentInstance.mascotas[0].edad).toBe(4);
  });

  it('cancelar confirmación no ejecuta DELETE', () => {
    spyOn(window, 'confirm').and.returnValue(false);
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados/7').flush(affiliate());
    http.expectOne('/api/v1/afiliados/7/mascotas').flush([
      pet(12, 'Rocky'), pet(13, 'Luna')
    ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.danger-action').click();

    expect(window.confirm).toHaveBeenCalled();
    expect(fixture.componentInstance.mascotas.length).toBe(2);
    http.expectNone(request => request.method === 'DELETE');
  });

  it('confirma, elimina por DELETE y quita solo esa mascota de la lista', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados/7').flush(affiliate());
    http.expectOne('/api/v1/afiliados/7/mascotas').flush([
      pet(12, 'Rocky'), pet(13, 'Luna')
    ]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.danger-action').click();
    const request = http.expectOne('/api/v1/mascotas/12');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(fixture.componentInstance.afiliado?.id).toBe(7);
    expect(fixture.componentInstance.mascotas.map(mascota => mascota.id)).toEqual([13]);
    expect(fixture.nativeElement.textContent).toContain('Mascota eliminada correctamente.');
    expect(fixture.nativeElement.textContent).toContain('Luna');
    expect(fixture.nativeElement.textContent).not.toContain('Rocky');
  });

  it('muestra un mensaje amigable si la mascota ya no existe', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados/7').flush(affiliate());
    http.expectOne('/api/v1/afiliados/7/mascotas').flush([pet(12, 'Rocky')]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.danger-action').click();
    http.expectOne('/api/v1/mascotas/12').flush(
      { status: 404, message: 'Mascota no encontrada' },
      { status: 404, statusText: 'Not Found' }
    );
    fixture.detectChanges();

    expect(fixture.componentInstance.mascotas.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('No se encontró la mascota.');
  });

  it('muestra un mensaje amigable si falla el servidor al eliminar', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    fixture.detectChanges();
    http.expectOne('/api/v1/afiliados/7').flush(affiliate());
    http.expectOne('/api/v1/afiliados/7/mascotas').flush([pet(12, 'Rocky')]);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.danger-action').click();
    http.expectOne('/api/v1/mascotas/12').flush(
      { status: 500, message: 'Error inesperado' },
      { status: 500, statusText: 'Server Error' }
    );
    fixture.detectChanges();

    expect(fixture.componentInstance.mascotas.length).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('No se pudo eliminar la mascota');
  });

  function affiliate() {
    return { id: 7, numeroIdentificacion: '100', nombres: 'Ana', apellidos: 'Lopez', celular: '3001234567', correo: 'ana@example.com', direccion: 'Calle 1' };
  }

  function pet(id: number, nombre: string) {
    return { id, afiliadoId: 7, nombre, especie: 'Perro', raza: 'Criollo', edad: 3, color: 'Negro' };
  }
});
