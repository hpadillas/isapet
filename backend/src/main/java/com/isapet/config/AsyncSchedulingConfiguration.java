package com.isapet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita el envío asíncrono de notificaciones y la tarea diaria de recordatorios. */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfiguration {
}
