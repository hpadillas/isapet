-- El plan se activa cuando se paga su primera cuota. Mientras tanto la afiliación queda pendiente de
-- activación: sin atenciones incluidas ni compra de bonos. Las afiliaciones existentes ya están activas.
ALTER TABLE afiliacion ADD COLUMN pendiente_activacion BOOLEAN NOT NULL DEFAULT FALSE;

-- La cuota de activación no vence ni suspende el plan: si no se paga, el plan simplemente no se activa.
-- Al pagarla, su periodo pasa a empezar en la fecha de activación.
ALTER TABLE cuota_plan ADD COLUMN de_activacion BOOLEAN NOT NULL DEFAULT FALSE;
