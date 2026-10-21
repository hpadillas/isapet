import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  CrearAfiliadoRequest,
  CrearAfiliadoResponse,
  CrearMascotaResponse,
  ESPECIES_MASCOTA
} from '../../models/afiliado.models';
import { RegistroAfiliadoComponent } from './registro-afiliado.component';

describe('RegistroAfiliadoComponent', () => {
  let fixture: ComponentFixture<RegistroAfiliadoComponent>;
  let component: RegistroAfiliadoComponent;
  let httpTesting: HttpTestingController;

  const afiliadoResponse: CrearAfiliadoResponse = {
    id: 7,
    numeroIdentificacion: '100',
    nombres: 'Juan',
    apellidos: 'Perez',
    celular: '3001234567',
    correo: 'juan@example.com',
    direccion: 'Calle 1'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegistroAfiliadoComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();

    fixture = TestBed.createComponent(RegistroAfiliadoComponent);
    component = fixture.componentInstance;
    httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpTesting.expectOne('/api/v1/privacidad').flush(politica());
    fixture.detectChanges();
  });

  afterEach(() => httpTesting.verify());

  it('muestra el aviso de privacidad y exige la autorización del titular (Ley 1581 de 2012)', () => {
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Tratamiento de datos personales');
    expect(texto).toContain('ISAPET');
    expect(texto).toContain('NIT 900.000.000-1');
    expect(texto).toContain('Registrar y administrar la afiliación.');
    expect(texto).toContain('datos@isapet.co');
    expect(texto).toContain('10 días hábiles');

    component.afiliadoForm.patchValue({ ...affiliateRequest(), autorizaTratamientoDatos: false });
    component.registrarAfiliado();
    httpTesting.expectNone('/api/v1/afiliados');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sin la autorización del titular no se puede registrar el afiliado.');
  });

  it('no envía el afiliado si los campos obligatorios están vacíos', () => {
    component.registrarAfiliado();
    fixture.detectChanges();

    expect(component.afiliadoForm.invalid).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Este campo es obligatorio.');
    httpTesting.expectNone('/api/v1/afiliados');
  });

  it('valida formato de correo antes de enviar', () => {
    component.afiliadoForm.patchValue({
      numeroIdentificacion: '100',
      nombres: 'Juan',
      apellidos: 'Perez',
      celular: '3001234567',
      correo: 'usuario@gmail',
      direccion: 'Calle 1'
    });

    expect(component.afiliadoForm.invalid).toBeTrue();
  });

  it('acepta correos con formato válido y rechaza los formatos inválidos indicados', () => {
    const correo = component.afiliadoForm.controls.correo;
    for (const value of ['usuario@gmail.com', 'nombre.apellido@dominio.com', 'usuario123@empresa.co']) {
      correo.setValue(value);
      expect(correo.valid).withContext(value).toBeTrue();
    }
    for (const value of ['usuario@', '@gamil.com', 'usuario@gmail', 'usuario.com', 'usuario @gmail.com']) {
      correo.setValue(value);
      expect(correo.invalid).withContext(value).toBeTrue();
    }
  });

  it('muestra mensajes amigables para caracteres no permitidos', () => {
    component.afiliadoForm.patchValue({
      numeroIdentificacion: '123-456', nombres: 'Juan2', apellidos: 'Pérez-2',
      celular: '300 123 4567', correo: 'usuario@gmail', direccion: 'Calle 1'
    });
    component.afiliadoForm.markAllAsTouched();
    fixture.detectChanges();

    const messages = fixture.nativeElement.textContent as string;
    expect(messages).toContain('El documento solo puede contener números.');
    expect(messages).toContain('El nombre solo puede contener letras, espacios y el número 6.');
    expect(messages).toContain('El apellido solo puede contener letras y espacios.');
    expect(messages).toContain('El celular solo puede contener números.');
    expect(messages).toContain('Ingresa un correo electrónico válido.');
  });

  it('aplica caracteres permitidos a documento, nombres, apellidos y celular', () => {
    component.afiliadoForm.patchValue({
      numeroIdentificacion: '123456789', nombres: '6', apellidos: 'De la Cruz',
      celular: '3001234567', correo: 'usuario@gmail.com', direccion: 'Calle 10 # 20-30', autorizaTratamientoDatos: true
    });
    expect(component.afiliadoForm.valid).toBeTrue();

    component.afiliadoForm.controls.nombres.setValue('María José');
    component.afiliadoForm.controls.apellidos.setValue('Ñusta Pérez');
    expect(component.afiliadoForm.controls.nombres.valid).toBeTrue();
    expect(component.afiliadoForm.controls.apellidos.valid).toBeTrue();

    component.afiliadoForm.controls.nombres.setValue('Juan123');
    component.afiliadoForm.controls.apellidos.setValue('Pérez-2');
    component.afiliadoForm.controls.numeroIdentificacion.setValue('123-456');
    component.afiliadoForm.controls.celular.setValue('300 123 4567');
    expect(component.afiliadoForm.controls.nombres.invalid).toBeTrue();
    expect(component.afiliadoForm.controls.apellidos.invalid).toBeTrue();
    expect(component.afiliadoForm.controls.numeroIdentificacion.invalid).toBeTrue();
    expect(component.afiliadoForm.controls.celular.invalid).toBeTrue();
  });

  it('solo el nombre de persona admite el 6; apellidos y datos de la mascota solo admiten palabras', () => {
    component.afiliadoForm.controls.apellidos.setValue('6');
    expect(component.afiliadoForm.controls.apellidos.invalid).toBeTrue();

    const { nombre, raza, color } = component.mascotaForm.controls;
    nombre.setValue('Señor Bigotes');
    raza.setValue('Criollo');
    color.setValue('Café claro');
    expect(nombre.valid && raza.valid && color.valid).toBeTrue();

    for (const valor of ['Max2', '6', 'Max!']) {
      nombre.setValue(valor);
      expect(nombre.invalid).withContext(valor).toBeTrue();
    }
    raza.setValue('Labrador 2');
    color.setValue('#000000');
    expect(raza.invalid).toBeTrue();
    expect(color.invalid).toBeTrue();
  });

  it('permite la dirección con puntuación y numeración habitual', () => {
    component.afiliadoForm.controls.direccion.setValue('Cra. 12 # 10-25 Apt 302');
    expect(component.afiliadoForm.controls.direccion.valid).toBeTrue();
  });

  it('crea el afiliado sin enviar ni crear una mascota', () => {
    component.afiliadoForm.patchValue(affiliateRequest());
    component.registrarAfiliado();

    const request = httpTesting.expectOne('/api/v1/afiliados');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(affiliateRequest());
    request.flush(afiliadoResponse);

    expect(component.afiliado?.id).toBe(7);
    expect(component.mascotas.length).toBe(0);
    expect(component.mostrarFormularioMascota).toBeFalse();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mascotas del afiliado');
    httpTesting.expectNone(request => request.url.includes('/mascotas'));
  });

  it('muestra el catálogo aprobado y permite registrar varias mascotas del mismo afiliado', () => {
    component.afiliado = afiliadoResponse;
    fixture.detectChanges();
    component.abrirFormularioMascota();
    fixture.detectChanges();

    const select = fixture.nativeElement.querySelector('#especie') as HTMLSelectElement;
    expect(Array.from(select.options).slice(1).map(option => option.value)).toEqual([...ESPECIES_MASCOTA]);

    component.mascotaForm.patchValue({ nombre: 'Rocky', especie: 'Perro', raza: 'Labrador', edad: 3, color: 'Café' });
    component.registrarMascota();
    const firstRequest = httpTesting.expectOne('/api/v1/afiliados/7/mascotas');
    expect(firstRequest.request.body).toEqual({ nombre: 'Rocky', especie: 'Perro', raza: 'Labrador', edad: 3, color: 'Café' });
    firstRequest.flush(petResponse(11, 'Rocky', 'Perro', 'Labrador', 3, 'Café'));

    component.abrirFormularioMascota();
    component.mascotaForm.patchValue({ nombre: 'Luna', especie: 'Gato', raza: 'Criollo', edad: 2, color: 'Gris' });
    component.registrarMascota();
    const secondRequest = httpTesting.expectOne('/api/v1/afiliados/7/mascotas');
    secondRequest.flush(petResponse(12, 'Luna', 'Gato', 'Criollo', 2, 'Gris'));

    expect(component.mascotas.map(mascota => mascota.nombre)).toEqual(['Rocky', 'Luna']);
    expect(component.mascotas.every(mascota => mascota.afiliadoId === 7)).toBeTrue();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.pet-table tbody tr').length).toBe(2);
  });

  it('rechaza mascota sin campos y edades fraccionarias', () => {
    component.afiliado = afiliadoResponse;
    component.registrarMascota();
    expect(component.mascotaForm.invalid).toBeTrue();
    httpTesting.expectNone(request => request.url.includes('/mascotas'));

    component.mascotaForm.patchValue({ nombre: 'Mora', especie: 'Gato', raza: 'Criollo', edad: 2.5, color: 'Gris' });
    expect(component.mascotaForm.controls.edad.invalid).toBeTrue();
    component.registrarMascota();
    httpTesting.expectNone(request => request.url.includes('/mascotas'));
  });

  it('muestra el conflicto de identificación devuelto por el backend', () => {
    component.afiliadoForm.patchValue(affiliateRequest());
    component.registrarAfiliado();
    httpTesting.expectOne('/api/v1/afiliados').flush(
      { status: 409, message: 'Duplicado', errors: [] },
      { status: 409, statusText: 'Conflict' }
    );

    expect(component.errorAfiliado).toContain('Ya existe');
  });

  it('muestra los detalles de validación HTTP 400 devueltos por el backend', () => {
    component.afiliadoForm.patchValue(affiliateRequest());
    component.registrarAfiliado();
    httpTesting.expectOne('/api/v1/afiliados').flush(
      { status: 400, message: 'Datos inválidos', errors: ['correo: El correo no es válido.'] },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(component.errorAfiliado).toContain('Revisa los datos');
    expect(component.detalleErroresAfiliado).toEqual(['El correo no es válido.']);
  });

  it('muestra los errores 404 y 500 de mascota sin detalles técnicos', () => {
    component.afiliado = afiliadoResponse;
    component.abrirFormularioMascota();
    component.mascotaForm.patchValue({ nombre: 'Mora', especie: 'Gato', raza: 'Criollo', edad: 2, color: 'Gris' });
    component.registrarMascota();
    httpTesting.expectOne('/api/v1/afiliados/7/mascotas').flush(
      { status: 404, message: 'No existe', errors: [] },
      { status: 404, statusText: 'Not Found' }
    );
    expect(component.errorMascota).toContain('No se encontró el afiliado');

    component.registrarMascota();
    httpTesting.expectOne('/api/v1/afiliados/7/mascotas').flush(
      { status: 500, message: 'Interno', errors: [] },
      { status: 500, statusText: 'Internal Server Error' }
    );
    expect(component.errorMascota).toContain('error del servidor');
    expect(component.errorMascota).not.toContain('Interno');
  });

  function affiliateRequest(): CrearAfiliadoRequest {
    return {
      numeroIdentificacion: '100',
      nombres: 'Juan',
      apellidos: 'Perez',
      celular: '3001234567',
      correo: 'juan@example.com',
      direccion: 'Calle 1',
      autorizaTratamientoDatos: true
    };
  }

  function politica() {
    return {
      version: '1.0', vigenteDesde: '2026-10-21', responsable: 'ISAPET', nit: '900.000.000-1', direccion: 'Calle 10',
      correo: 'datos@isapet.co', telefono: '6011234567', datosRecolectados: ['Identificación'],
      finalidades: ['Registrar y administrar la afiliación.'], derechos: ['Conocer, actualizar y rectificar.'],
      canalAtencion: 'datos@isapet.co', diasHabilesConsultas: 10, diasHabilesReclamos: 15
    };
  }

  function petResponse(
    id: number,
    nombre: string,
    especie: string,
    raza: string,
    edad: number,
    color: string
  ): CrearMascotaResponse {
    return { id, afiliadoId: 7, nombre, especie, raza, edad, color };
  }
});
