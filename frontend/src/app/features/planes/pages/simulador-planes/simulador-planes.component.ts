import { Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { formatearPesos, mensajeError } from '../../../../core/format/moneda';
import { OpcionPlanSimulada, ServicioVeterinario, SimulacionPlanResponse } from '../../models/afiliacion.models';
import { SimulacionApiService } from '../../services/simulacion-api.service';

@Component({
  selector: 'app-simulador-planes',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './simulador-planes.component.html'
})
export class SimuladorPlanesComponent implements OnInit {
  private readonly api = inject(SimulacionApiService);

  readonly formatearPesos = formatearPesos;
  readonly cantidadMascotas = new FormControl(1, {
    nonNullable: true,
    validators: [Validators.required, Validators.min(1), Validators.max(20)]
  });
  readonly cantidadesForm = new FormGroup<Record<string, FormControl<number>>>({});
  readonly form = new FormGroup({ cantidadMascotas: this.cantidadMascotas, cantidades: this.cantidadesForm });

  servicios: ServicioVeterinario[] = [];
  resultado: SimulacionPlanResponse | null = null;
  cargandoServicios = true;
  simulando = false;
  error = '';

  ngOnInit(): void {
    this.api.listarServicios().subscribe({
      next: servicios => {
        this.servicios = servicios;
        for (const servicio of servicios) {
          this.cantidadesForm.addControl(String(servicio.id), new FormControl(0, {
            nonNullable: true,
            validators: [Validators.min(0), Validators.max(30)]
          }));
        }
        this.cargandoServicios = false;
        this.simular();
      },
      error: error => {
        this.error = mensajeError(error, 'No se pudieron cargar los servicios veterinarios.');
        this.cargandoServicios = false;
      }
    });
  }

  simular(): void {
    this.error = '';
    if (this.cantidadMascotas.invalid || this.cantidadesForm.invalid) {
      this.cantidadMascotas.markAsTouched();
      this.cantidadesForm.markAllAsTouched();
      this.error = 'Revisa los datos: entre 1 y 20 mascotas y hasta 30 usos por servicio.';
      return;
    }
    const servicios = Object.entries(this.cantidadesForm.getRawValue())
      .filter(([, cantidad]) => Number(cantidad) > 0)
      .map(([servicioId, cantidad]) => ({ servicioId: Number(servicioId), cantidadMensual: Number(cantidad) }));

    this.simulando = true;
    this.api.simular({ cantidadMascotas: this.cantidadMascotas.value, servicios }).subscribe({
      next: resultado => {
        this.resultado = resultado;
        this.simulando = false;
      },
      error: error => {
        this.error = mensajeError(error, 'No se pudo calcular la simulación. Inténtalo nuevamente.');
        this.simulando = false;
      }
    });
  }

  control(servicio: ServicioVeterinario): FormControl<number> {
    return this.cantidadesForm.controls[String(servicio.id)];
  }

  beneficiosConDescuento(opcion: OpcionPlanSimulada) {
    return opcion.beneficios.filter(beneficio => beneficio.porcentajeDescuento > 0);
  }
}
