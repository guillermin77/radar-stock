-- ==========================================================
-- RADAR STOCK - Instalación Limpia para Producción (Hostinger)
-- Ejecutar en una base de datos vacía ya creada en el panel de hosting
-- (Sin CREATE DATABASE ni USE)
-- ==========================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. Tabla de Usuarios y Roles (Autenticación y Sesiones)
DROP TABLE IF EXISTS detalle_ventas;
DROP TABLE IF EXISTS ventas;
DROP TABLE IF EXISTS productos;
DROP TABLE IF EXISTS usuarios;

CREATE TABLE usuarios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    correo VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    rol ENUM('Administrador', 'Vendedor', 'Colaborador') DEFAULT 'Vendedor',
    token VARCHAR(64) DEFAULT NULL,
    token_expira DATETIME DEFAULT NULL,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_usuarios_token (token),
    INDEX idx_usuarios_correo (correo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Tabla de Productos (Inventario, Mínimos, Deseados y Caducidad)
CREATE TABLE productos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    codigo_barras VARCHAR(50) UNIQUE DEFAULT NULL,
    nombre_producto VARCHAR(150) NOT NULL,
    categoria VARCHAR(50) DEFAULT NULL,
    precio_venta DECIMAL(10,2) NOT NULL,
    stock_actual INT NOT NULL DEFAULT 0,
    stock_minimo INT NOT NULL DEFAULT 5,
    stock_deseado INT NOT NULL DEFAULT 0,
    fecha_vencimiento DATE DEFAULT NULL,
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_productos_nombre (nombre_producto),
    INDEX idx_productos_categoria (categoria),
    INDEX idx_productos_vencimiento (fecha_vencimiento),
    INDEX idx_productos_stock (stock_actual, stock_minimo, stock_deseado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Tabla de Ventas (Transacciones con Idempotencia por UUID)
CREATE TABLE ventas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    codigo_operacion VARCHAR(64) UNIQUE DEFAULT NULL,
    usuario_id INT DEFAULT NULL,
    total_venta DECIMAL(10,2) NOT NULL,
    fecha_venta TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL,
    INDEX idx_ventas_fecha (fecha_venta),
    INDEX idx_ventas_codigo_operacion (codigo_operacion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Detalle de Ventas (Renglones por Transacción)
CREATE TABLE detalle_ventas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    venta_id INT NOT NULL,
    producto_id INT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (venta_id) REFERENCES ventas(id) ON DELETE CASCADE,
    FOREIGN KEY (producto_id) REFERENCES productos(id),
    INDEX idx_detalle_venta (venta_id),
    INDEX idx_detalle_producto (producto_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- ==========================================================
-- 5. Usuario Administrador Inicial
-- IMPORTANTE: Reemplazar el texto 'REEMPLAZAR_HASH' por el hash bcrypt
-- generado con password_hash('TuClaveSegura', PASSWORD_BCRYPT) antes o después de importar.
-- ==========================================================
INSERT INTO usuarios (nombre, correo, password, rol) VALUES
('Administrador Radar Stock', 'admin@radarstock.cl', 'REEMPLAZAR_HASH', 'Administrador');

