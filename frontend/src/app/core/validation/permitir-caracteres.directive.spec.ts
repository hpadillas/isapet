import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { PermitirCaracteresDirective } from './permitir-caracteres.directive';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, PermitirCaracteresDirective],
  template: `
    <input id="letras" appPermitir="letras" [formControl]="letras" />
    <input id="nombre" appPermitir="nombre" [formControl]="nombre" />
    <input id="numeros" appPermitir="numeros" [formControl]="numeros" />
  `
})
class FormularioPruebaComponent {
  readonly letras = new FormControl('', { nonNullable: true });
  readonly nombre = new FormControl('', { nonNullable: true });
  readonly numeros = new FormControl('', { nonNullable: true });
}

describe('PermitirCaracteresDirective', () => {
  let fixture: ComponentFixture<FormularioPruebaComponent>;

  beforeEach(() => {
    fixture = TestBed.createComponent(FormularioPruebaComponent);
    fixture.detectChanges();
  });

  /** Simula escribir o pegar un texto en el campo. */
  function escribir(id: string, texto: string): HTMLInputElement {
    const input = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    input.value = texto;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    return input;
  }

  it('en campos de palabras descarta números y símbolos pero conserva tildes, ñ y espacios', () => {
    const input = escribir('letras', 'Señor Bigotes2 Café#1');

    expect(input.value).toBe('Señor Bigotes Café');
    expect(fixture.componentInstance.letras.value).toBe('Señor Bigotes Café');
  });

  it('en el nombre de persona solo admite el 6 como número', () => {
    expect(escribir('nombre', '6').value).toBe('6');
    expect(escribir('nombre', 'Juan 6 y 7').value).toBe('Juan 6 y ');
  });

  it('en campos numéricos descarta todo lo que no sea dígito', () => {
    const input = escribir('numeros', '+57 300-123 abc');

    expect(input.value).toBe('57300123');
    expect(fixture.componentInstance.numeros.value).toBe('57300123');
  });
});
