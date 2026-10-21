-- Cancelación programada: último día en que el afiliado conserva el plan (fin del periodo ya pagado).
ALTER TABLE afiliacion ADD COLUMN fecha_fin DATE;
ALTER TABLE afiliacion ADD CONSTRAINT ck_afiliacion_fin_posterior CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio);

-- Una cuota puede anularse (cancelación dentro del plazo de pago). Una cuota anulada no ocupa su periodo:
-- si el afiliado vuelve a afiliarse, ese periodo puede generar una cuota nueva.
ALTER TABLE cuota_plan DROP CONSTRAINT ck_cuota_plan_estado;
ALTER TABLE cuota_plan ADD CONSTRAINT ck_cuota_plan_estado CHECK (estado IN ('PENDIENTE', 'PAGADA', 'ANULADA'));
ALTER TABLE cuota_plan ADD COLUMN anulada_en TIMESTAMP WITH TIME ZONE;
ALTER TABLE cuota_plan ADD CONSTRAINT ck_cuota_plan_anulada CHECK ((estado = 'ANULADA') = (anulada_en IS NOT NULL));
ALTER TABLE cuota_plan DROP CONSTRAINT uk_cuota_plan_periodo;
CREATE UNIQUE INDEX uk_cuota_plan_periodo_vigente ON cuota_plan (afiliado_id, periodo_desde) WHERE estado <> 'ANULADA';

ALTER TABLE notificacion DROP CONSTRAINT ck_notificacion_tipo;
ALTER TABLE notificacion ADD CONSTRAINT ck_notificacion_tipo CHECK (tipo IN (
    'BIENVENIDA', 'PLAN_ACTIVADO', 'ATENCIONES_AGOTADAS', 'FIN_PERIODO',
    'PLAN_CANCELADO', 'PLAN_SUSPENDIDO', 'PLAN_REACTIVADO'));
