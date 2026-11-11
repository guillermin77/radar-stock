-- ==========================================================
-- RADAR STOCK - Migración 07: Campo activo en tabla usuarios
-- Compatible con MySQL 5.7+, MySQL 8.0+ y MariaDB 10.3+
-- ==========================================================

ALTER TABLE usuarios 
ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1 
AFTER rol;

