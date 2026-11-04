-- ==========================================================
-- RADAR STOCK - Migración 06: Código de Operación en Ventas
-- Agrega columna para identificador único de operación (UUID de 36 caracteres)
-- con índice único (MySQL permite múltiples valores NULL).
-- ==========================================================

ALTER TABLE ventas
    ADD COLUMN codigo_operacion VARCHAR(36) NULL AFTER total_venta,
    ADD UNIQUE INDEX idx_ventas_codigo_operacion (codigo_operacion);

