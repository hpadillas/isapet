-- Las versiones comerciales siguen siendo inmutables, pero una versión programada (que aún no empieza)
-- se puede eliminar: todavía no se le ha aplicado a ningún afiliado. La versión anterior queda vigente
-- hasta el inicio de la siguiente, porque la vigencia se deduce de las fechas de inicio.
-- CURRENT_DATE usa la zona horaria de la sesión, que el backend fija en la del negocio (America/Bogota).
CREATE OR REPLACE FUNCTION impedir_modificacion_version_plan()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.vigente_desde > CURRENT_DATE THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Las versiones comerciales vigentes o históricas son inmutables.'
        USING ERRCODE = '55000';
END;
$$;
