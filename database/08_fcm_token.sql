-- ==========================================================
-- RADAR STOCK - Migración 08: Columna fcm_token para notificaciones remotas
-- ==========================================================

ALTER TABLE usuarios 
ADD COLUMN fcm_token VARCHAR(255) DEFAULT NULL 
AFTER token_expira;

