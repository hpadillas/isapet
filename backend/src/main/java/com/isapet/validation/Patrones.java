package com.isapet.validation;

/** Expresiones regulares compartidas por las validaciones de los campos de texto. */
public final class Patrones {

    /** Campos numéricos escritos como texto (documento, celular): solo dígitos. */
    public static final String SOLO_NUMEROS = "^[0-9]+$";

    /** Campos de palabras: letras (incluidas tildes y ñ) y espacios; sin números ni símbolos. */
    public static final String SOLO_PALABRAS = "^[\\p{L}\\p{M} ]+$";

    /**
     * Nombres de persona: igual que {@link #SOLO_PALABRAS}, pero admite el número 6, porque en Colombia
     * hay personas registradas con ese nombre.
     */
    public static final String NOMBRE_PERSONA = "^[\\p{L}\\p{M}6 ]+$";

    private Patrones() {
    }
}
