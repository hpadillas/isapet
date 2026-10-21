package com.isapet.service;

import com.isapet.dto.PoliticaPrivacidadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Aviso de privacidad y política de tratamiento de datos personales de ISAPET.
 * Cumple el deber de informar al titular al pedir su autorización (Ley 1581 de 2012, art. 12) y el
 * contenido mínimo de la política (Decreto 1074 de 2015, que compila el Decreto 1377 de 2013).
 * Los datos de contacto del responsable se configuran con variables PRIVACIDAD_*.
 * Si cambia el contenido, se sube la versión: cada autorización guarda la versión que el titular aceptó.
 */
@Component
public class PoliticaPrivacidad {

    public static final String VERSION = "1.0";
    public static final LocalDate VIGENTE_DESDE = LocalDate.of(2026, 10, 21);
    static final int DIAS_HABILES_CONSULTAS = 10;
    static final int DIAS_HABILES_RECLAMOS = 15;

    private final String responsable;
    private final String nit;
    private final String direccion;
    private final String correo;
    private final String telefono;

    public PoliticaPrivacidad(
            @Value("${app.privacidad.responsable:ISAPET}") String responsable,
            @Value("${app.privacidad.nit:Por definir}") String nit,
            @Value("${app.privacidad.direccion:Por definir}") String direccion,
            @Value("${app.privacidad.correo:protecciondatos@isapet.local}") String correo,
            @Value("${app.privacidad.telefono:Por definir}") String telefono) {
        this.responsable = responsable;
        this.nit = nit;
        this.direccion = direccion;
        this.correo = correo;
        this.telefono = telefono;
    }

    public PoliticaPrivacidadResponse politica() {
        return new PoliticaPrivacidadResponse(
                VERSION,
                VIGENTE_DESDE,
                responsable,
                nit,
                direccion,
                correo,
                telefono,
                List.of(
                        "Identificación, nombres y apellidos.",
                        "Datos de contacto: celular, correo electrónico y dirección.",
                        "Datos de las mascotas afiliadas y su historia clínica veterinaria (atenciones, prestadores y observaciones).",
                        "Historial de mensualidades, bonos y pagos. ISAPET no recibe ni almacena datos de tarjetas: "
                                + "los pagos en línea los procesa directamente la pasarela de pagos."),
                List.of(
                        "Registrar y administrar la afiliación del titular y de sus mascotas a los planes de ISAPET.",
                        "Gestionar el cobro de mensualidades y bonos, y conciliar los pagos recibidos.",
                        "Registrar y consultar la historia clínica veterinaria de las mascotas para prestar el servicio.",
                        "Enviar al correo registrado comunicaciones del servicio: bienvenida, activación, vencimiento, "
                                + "suspensión, reactivación y cancelación del plan.",
                        "Atender las consultas, reclamos y solicitudes del titular.",
                        "Cumplir obligaciones legales, contables y tributarias."),
                List.of(
                        "Conocer, actualizar y rectificar sus datos personales, incluidos los parciales, inexactos o incompletos.",
                        "Solicitar prueba de la autorización otorgada a ISAPET.",
                        "Ser informado, previa solicitud, del uso que se ha dado a sus datos.",
                        "Presentar quejas ante la Superintendencia de Industria y Comercio por infracciones a la Ley 1581 de 2012.",
                        "Revocar la autorización y solicitar la supresión de sus datos cuando no se respeten los principios, "
                                + "derechos y garantías constitucionales y legales.",
                        "Acceder en forma gratuita a sus datos personales que hayan sido objeto de tratamiento."),
                correo,
                DIAS_HABILES_CONSULTAS,
                DIAS_HABILES_RECLAMOS);
    }
}
