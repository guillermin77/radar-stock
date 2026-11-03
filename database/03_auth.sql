-- ==========================================================
-- RADAR STOCK - Migración 03: Columnas de autenticación
-- Agrega soporte de tokens para sesiones en la app y usuario Vendedor
-- ==========================================================

-- 1. Modificar tabla usuarios para soporte de autenticación basada en tokens
ALTER TABLE usuarios
    ADD COLUMN token VARCHAR(64) NULL AFTER rol,
    ADD COLUMN token_expira DATETIME NULL AFTER token,
    ADD INDEX idx_usuarios_token (token);

-- 2. Usuario Vendedor de prueba
-- Correo: vendedor@radarstock.cl
-- Contraseña en texto plano: vendedor123
-- Hash generado con password_hash('vendedor123', PASSWORD_BCRYPT)
INSERT INTO usuarios (nombre, correo, password, rol) VALUES
('Vendedor Radar Stock', 'vendedor@radarstock.cl', '$2y$10$z9BQn86IMfQtBcxNGjlLSecZqxZ2DNonJ4xWf332HxS5FYtn41FNK', 'Vendedor')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

