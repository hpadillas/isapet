import { Directive, ElementRef, HostListener, inject, Input } from '@angular/core';
import { NgControl } from '@angular/forms';

export type CaracteresPermitidos = 'letras' | 'nombre' | 'numeros';

/** Caracteres que se eliminan en cada caso; coinciden con SOLO_PALABRAS, NOMBRE_PERSONA y SOLO_NUMEROS. */
const NO_PERMITIDOS: Record<CaracteresPermitidos, RegExp> = {
  letras: /[^\p{L}\p{M} ]/gu,
  nombre: /[^\p{L}\p{M}6 ]/gu,
  numeros: /[^0-9]/g
};

/**
 * Impide escribir o pegar caracteres no permitidos en un campo de texto: se descartan al momento.
 * Los validadores del formulario y del backend se mantienen como segunda capa.
 *
 * Uso: `<input appPermitir="letras" formControlName="raza" />`
 */
@Directive({
  selector: 'input[appPermitir]',
  standalone: true
})
export class PermitirCaracteresDirective {
  @Input({ required: true }) appPermitir!: CaracteresPermitidos;

  private readonly elemento = inject<ElementRef<HTMLInputElement>>(ElementRef);
  private readonly control = inject(NgControl, { optional: true, self: true });

  @HostListener('input')
  alEscribir(): void {
    const input = this.elemento.nativeElement;
    const original = input.value;
    const limpio = original.replace(NO_PERMITIDOS[this.appPermitir], '');
    if (limpio === original) {
      return;
    }
    // Conserva la posición del cursor descontando los caracteres eliminados.
    const cursor = Math.max(0, (input.selectionStart ?? limpio.length) - (original.length - limpio.length));
    input.value = limpio;
    input.setSelectionRange(cursor, cursor);
    this.control?.control?.setValue(limpio);
  }
}
