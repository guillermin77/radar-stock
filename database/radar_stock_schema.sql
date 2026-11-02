-- ==========================================================
-- RADAR STOCK - Esquema de Base de Datos
-- Compatible con entornos locales (XAMPP) y hosting (Hostinger)
-- ==========================================================

-- 1. Tabla de Usuarios y Roles (Administradores, Vendedores, Colaboradores)
CREATE TABLE IF NOT EXISTS usuarios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    correo VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    rol ENUM('Administrador', 'Vendedor', 'Colaborador') DEFAULT 'Vendedor',
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tabla de Productos (Control de stock y caducidad)
CREATE TABLE IF NOT EXISTS productos (
    id INT AUTO_INCREMENT PRIMARY KEY,
    codigo_barras VARCHAR(50) UNIQUE,
    nombre_producto VARCHAR(150) NOT NULL,
    categoria VARCHAR(50),
    precio_venta DECIMAL(10,2) NOT NULL,
    stock_actual INT NOT NULL DEFAULT 0,
    stock_minimo INT NOT NULL DEFAULT 5, -- Alerta temprana para evitar quiebres
    stock_deseado INT NOT NULL DEFAULT 0, -- Stock ideal para alertas y reposición
    fecha_vencimiento DATE DEFAULT NULL, -- Control de mermas por caducidad
    actualizado_en TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_productos_nombre (nombre_producto),
    INDEX idx_productos_vencimiento (fecha_vencimiento)
);

-- 3. Tabla de Ventas (Módulo de salida rápida)
CREATE TABLE IF NOT EXISTS ventas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT,
    total_venta DECIMAL(10,2) NOT NULL,
    fecha_venta TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_ventas_fecha (fecha_venta)
);

-- 4. Detalle de la Venta (Productos asociados a cada transacción)
CREATE TABLE IF NOT EXISTS detalle_ventas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    venta_id INT,
    producto_id INT,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (venta_id) REFERENCES ventas(id) ON DELETE CASCADE,
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);
