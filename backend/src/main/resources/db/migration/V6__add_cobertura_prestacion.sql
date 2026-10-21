-- Cada prestación indica cómo se cubrió: con una de las atenciones mensuales incluidas en el plan
-- (sin costo), con un bono comprado al agotarlas, o de forma particular (valor pleno).
ALTER TABLE prestacion ADD COLUMN cobertura VARCHAR(20);

-- Las prestaciones existentes no consumían atenciones incluidas.
UPDATE prestacion
SET cobertura = CASE WHEN bono_id IS NULL THEN 'PARTICULAR' ELSE 'BONO' END;

ALTER TABLE prestacion
    ALTER COLUMN cobertura SET NOT NULL,
    ADD CONSTRAINT ck_prestacion_cobertura CHECK (cobertura IN ('PLAN', 'BONO', 'PARTICULAR')),
    ADD CONSTRAINT ck_prestacion_cobertura_bono CHECK ((cobertura = 'BONO') = (bono_id IS NOT NULL));
