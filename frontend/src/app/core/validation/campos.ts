import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Campos numéricos escritos como texto (documento, celular): solo dígitos. */
export const SOLO_NUMEROS = /^[0-9]+$/;

/** Campos de palabras: letras (incluidas tildes y ñ) y espacios; sin números ni símbolos. */
export const SOLO_PALABRAS = /^[\p{L}\p{M} ]+$/u;

/**
 * Nombres de persona: igual que SOLO_PALABRAS, pero admite el número 6, porque en Colombia hay
 * personas registradas con ese nombre.
 */
export const NOMBRE_PERSONA = /^[\p{L}\p{M}6 ]+$/u;

/** Para inputs type="number": el valor debe ser un número entero (vacío lo controla `required`). */
export const entero: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.value === null || control.value === '' || Number.isInteger(control.value) ? null : { integer: true };
