import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fechaLocalHoy, sumarDias } from '../../../../core/format/moneda';
import { Plan } from '../../models/plan.models';
import { ListaPlanesComponent } from './lista-planes.component';

describe('ListaPlanesComponent', () => {
  let fixture: ComponentFixture<ListaPlanesComponent>;
  let component: ListaPlanesComponent;
  let http: HttpTestingController;
  const hoy = fechaLocalHoy();
  const ayer = sumarDias(hoy, -1);
  const manana = sumarDias(hoy, 1);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ListaPlanesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ListaPlanesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/planes').flush([plan()]);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function texto(): string {
    // Intl separa "$" del número con un espacio no separable.
    return (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
  }

  function boton(etiqueta: string): HTMLButtonElement {
    const botones = Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[];
    const encontrado = botones.find(b => b.textContent?.trim() === etiqueta);
    if (!encontrado) {
      throw new Error(`No se encontró el botón "${etiqueta}"`);
    }
    return encontrado;
  }

  it('el formulario de creación se despliega con el botón "Crear plan" y se puede cancelar', () => {
    expect(fixture.nativeElement.querySelector('#plan-code')).toBeNull();

    boton('+ Crear plan').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#plan-code')).not.toBeNull();

    boton('Cancelar').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#plan-code')).toBeNull();
  });

  it('cada tarjeta tiene "Ver detalle" y "Editar plan" y muestra el cambio programado', () => {
    expect(boton('Ver detalle')).toBeTruthy();
    expect(boton('Editar plan')).toBeTruthy();
    expect(texto()).toContain('$ 60.000');
    expect(texto()).toContain('Cambio programado desde el');
  });

  it('el detalle muestra la vigencia de cada versión y lo que cambió', () => {
    boton('Ver detalle').click();
    http.expectOne('/api/v1/admin/planes/1').flush(plan());
    fixture.detectChanges();

    expect(texto()).toContain('Vigente hoy');
    expect(texto()).toContain('Programada');
    expect(texto()).toContain(`Del ${formato(ayer)} al ${formato(sumarDias(manana, -1))}`);
    expect(texto()).toContain(`Desde el ${formato(manana)}, sin fecha de fin`);
    expect(texto()).toContain('Precio: $ 60.000 → $ 70.000');
    expect(texto()).toContain('Atenciones incluidas: 2 → 3');
  });

  it('al editar pide confirmación con la vigencia y guarda nombre y nueva versión', () => {
    boton('Editar plan').click();
    http.expectOne('/api/v1/admin/planes/1').flush(plan());
    fixture.detectChanges();

    // La última versión empieza mañana: las nuevas condiciones deben empezar después.
    expect(component.fechaMinimaVersion).toBe(sumarDias(manana, 1));
    component.editarPlanForm.patchValue({ nombre: 'Básico Plus', precioMensual: '80000' });
    component.guardarEdicion();
    fixture.detectChanges();

    expect(texto()).toContain('¿Guardar los cambios del plan?');
    expect(texto()).toContain('Básico → Básico Plus');
    expect(texto()).toContain('Precio: $ 70.000 → $ 80.000');
    expect(texto()).toContain(`Rigen desde el ${formato(sumarDias(manana, 1))}`);

    component.confirmarEdicion();
    const renombrar = http.expectOne('/api/v1/admin/planes/1');
    expect(renombrar.request.method).toBe('PATCH');
    expect(renombrar.request.body).toEqual({ nombre: 'Básico Plus' });
    renombrar.flush({ ...plan(), nombre: 'Básico Plus' });

    const version = http.expectOne('/api/v1/admin/planes/1/versiones');
    expect(version.request.body).toEqual({
      precioMensual: 80000, maximoMascotas: 1, limiteAtenciones: 3, vigenteDesde: sumarDias(manana, 1)
    });
    version.flush({ ...plan(), nombre: 'Básico Plus' });
    fixture.detectChanges();

    expect(component.modo).toBe('detalle');
    expect(texto()).toContain('Plan Básico Plus actualizado');
  });

  it('eliminar la versión programada del medio deja la vigente hasta el inicio de la tercera', () => {
    const enDiezDias = sumarDias(hoy, 10);
    const conTres: Plan = { ...plan(), versiones: [
      ...plan().versiones.map(v => v.id === 2 ? { ...v, vigenteHastaExclusiva: enDiezDias } : v),
      { id: 3, precioMensual: 80000, maximoMascotas: 2, limiteAtenciones: 3, atencionesIlimitadas: false,
        vigenteDesde: enDiezDias, vigenteHastaExclusiva: null }
    ] };
    boton('Ver detalle').click();
    http.expectOne('/api/v1/admin/planes/1').flush(conTres);
    fixture.detectChanges();

    // Solo las programadas (versiones 2 y 3) se pueden eliminar.
    const botonesEliminar = (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[])
      .filter(b => b.textContent?.trim() === 'Eliminar versión');
    expect(botonesEliminar.length).toBe(2);

    const segunda = component.historial(conTres).find(item => item.numero === 2)!;
    component.prepararEliminacionVersion(segunda);
    http.expectOne('/api/v1/admin/planes/1/versiones/2/eliminacion')
      .flush({ versionId: 2, vigenteDesde: manana, afiliadosConCambioProgramado: 1 });
    fixture.detectChanges();

    expect(texto()).toContain('¿Eliminar esta versión programada?');
    expect(texto()).toContain(`del ${formato(ayer)} al ${formato(sumarDias(enDiezDias, -1))}`);
    expect(texto()).toContain('1 afiliado tiene programado el cambio a este plan');

    component.eliminarVersion();
    const peticion = http.expectOne('/api/v1/admin/planes/1/versiones/2');
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush({ ...conTres, versiones: [
      { ...conTres.versiones[0], vigenteHastaExclusiva: enDiezDias }, conTres.versiones[2]
    ] });
    fixture.detectChanges();

    expect(component.confirmacionEliminacionVersion).toBeNull();
    expect(texto()).toContain(`Del ${formato(ayer)} al ${formato(sumarDias(enDiezDias, -1))}`);
  });

  it('el detalle muestra qué incluye el plan y los descuentos por servicio', () => {
    boton('Ver detalle').click();
    http.expectOne('/api/v1/admin/planes/1').flush(plan());
    fixture.detectChanges();

    expect(texto()).toContain('Qué incluye');
    expect(texto()).toContain('2 atenciones incluidas por periodo');
    expect(texto()).toContain('Cubre hasta 1 mascota');
    expect(texto()).toContain('Mensualidad de $ 60.000');
    expect(texto()).toContain(`Desde el ${formato(manana)} cambia: Precio: $ 60.000 → $ 70.000 · Atenciones incluidas: 2 → 3`);
    expect(texto()).toContain('Consulta general');
    expect(texto()).toContain('30%');
    expect(texto()).toContain('$ 42.000');
    expect(texto()).toContain('Sin descuento');
  });

  it('no abre la confirmación si no hay cambios', () => {
    boton('Editar plan').click();
    http.expectOne('/api/v1/admin/planes/1').flush(plan());
    fixture.detectChanges();

    component.guardarEdicion();

    expect(component.confirmacionEdicion).toBeNull();
    expect(component.error).toBe('No hay cambios para guardar.');
  });

  function formato(fecha: string): string {
    const [anio, mes, dia] = fecha.split('-');
    return `${dia}/${mes}/${anio}`;
  }

  function plan(): Plan {
    return {
      id: 1, codigo: 'BASICO', nombre: 'Básico', activo: true,
      beneficios: [
        { servicioId: 1, codigo: 'CONSULTA_GENERAL', nombre: 'Consulta general', tipo: 'CONSULTA',
          valorBase: 60000, porcentajeDescuento: 30, valorConPlan: 42000 },
        { servicioId: 5, codigo: 'CIRUGIA', nombre: 'Cirugía programada', tipo: 'PROCEDIMIENTO',
          valorBase: 750000, porcentajeDescuento: 0, valorConPlan: 750000 }
      ],
      versiones: [
        { id: 1, precioMensual: 60000, maximoMascotas: 1, limiteAtenciones: 2, atencionesIlimitadas: false,
          vigenteDesde: ayer, vigenteHastaExclusiva: manana },
        { id: 2, precioMensual: 70000, maximoMascotas: 1, limiteAtenciones: 3, atencionesIlimitadas: false,
          vigenteDesde: manana, vigenteHastaExclusiva: null }
      ]
    };
  }
});
