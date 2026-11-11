-- ==========================================================
-- RADAR STOCK - Migración 10: Tipo 'ajuste' en movimientos_stock e índices
-- Compatible con MySQL 5.7+, MySQL 8.0+ y MariaDB 10.3+
-- ==========================================================

-- 1. Ampliar el ENUM del campo tipo para soportar 'ajuste' sin perder datos existentes
ALTER TABLE movimientos_stock 
MODIFY COLUMN tipo ENUM('ingreso', 'conteo', 'ajuste') NOT NULL;

-- 2. Índice por fecha para optimizar consultas cronológicas
-- Detecta dinámicamente si existe la columna local_id para anteponerla en el índice
SET @col_local_exists = (
    SELECT COUNT(*) 
    FROM information_schema.columns 
    WHERE table_schema = DATABASE() 
      AND table_name = 'movimientos_stock' 
      AND column_name = 'local_id'
);

SET @idx_fecha_exists = (
    SELECT COUNT(*) 
    FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
      AND table_name = 'movimientos_stock' 
      AND index_name = 'idx_movimientos_fecha'
);

SET @sql_idx_fecha = IF(@idx_fecha_exists = 0,
    IF(@col_local_exists > 0,
        'CREATE INDEX idx_movimientos_fecha ON movimientos_stock (local_id, fecha)',
        'CREATE INDEX idx_movimientos_fecha ON movimientos_stock (fecha)'
    ),
    'SELECT "Índice idx_movimientos_fecha ya existe"'
);
PREPARE stmt_idx_fecha FROM @sql_idx_fecha;
EXECUTE stmt_idx_fecha;
DEALLOCATE PREPARE stmt_idx_fecha;

-- 3. Índice por (producto_id, fecha)
SET @idx_prod_fecha_exists = (
    SELECT COUNT(*) 
    FROM information_schema.statistics 
    WHERE table_schema = DATABASE() 
      AND table_name = 'movimientos_stock' 
      AND index_name = 'idx_movimientos_producto_fecha'
);

SET @sql_idx_prod_fecha = IF(@idx_prod_fecha_exists = 0,
    IF(@col_local_exists > 0,
        'CREATE INDEX idx_movimientos_producto_fecha ON movimientos_stock (local_id, producto_id, fecha)',
        'CREATE INDEX idx_movimientos_producto_fecha ON movimientos_stock (producto_id, fecha)'
    ),
    'SELECT "Índice idx_movimientos_producto_fecha ya existe"'
);
PREPARE stmt_idx_prod_fecha FROM @sql_idx_prod_fecha;
EXECUTE stmt_idx_prod_fecha;
DEALLOCATE PREPARE stmt_idx_prod_fecha;

