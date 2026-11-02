-- ==========================================================
-- RADAR STOCK - Datos de Prueba Iniciales
-- 10 Productos representativos de almacén + 1 Usuario Admin
-- ==========================================================

-- 1. Usuario Administrador de Prueba
-- Contraseña en texto plano: admin123
-- Cifrada con password_hash('admin123', PASSWORD_BCRYPT)
INSERT INTO usuarios (nombre, correo, password, rol) VALUES
('Administrador Radar Stock', 'admin@radarstock.cl', '$2y$10$H1HrLMeRCODj.J.NWcpageUWGr8k4X.IQFc7BOc.aOUarcVT3YBGS', 'Administrador')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);

-- 2. Productos de Almacén de Barrio
-- Cubre las 6 categorías: Bebidas, Lácteos, Abarrotes, Panadería, Aseo, Snacks
-- Incluye casos para alertas: stock bajo el mínimo, sin stock, y vencimiento <= 15 días

INSERT INTO productos (codigo_barras, nombre_producto, categoria, precio_venta, stock_actual, stock_minimo, stock_deseado, fecha_vencimiento) VALUES
-- 1. Bebidas: Estado normal
('7801610000011', 'Bebida Coca-Cola Original 1.5L', 'Bebidas', 1800.00, 15, 6, 24, DATE_ADD(CURDATE(), INTERVAL 180 DAY)),

-- 2. Lácteos: Stock bajo el mínimo (3 <= 8) y por vencer en 6 días (<= 15 días)
('7802900000022', 'Leche Entera Colun 1 Litro', 'Lácteos', 1100.00, 3, 8, 20, DATE_ADD(CURDATE(), INTERVAL 6 DAY)),

-- 3. Lácteos: Stock crítico (2 <= 25% de 20 = 5) y por vencer en 3 días
('7801230000033', 'Yogurt Batido Frutilla Soprole 120g', 'Lácteos', 450.00, 2, 5, 20, DATE_ADD(CURDATE(), INTERVAL 3 DAY)),

-- 4. Panadería: Stock bajo (4 <= 10) y por vencer en 2 días
('7809990000044', 'Pan Hallulla Granel Bolsa 1kg', 'Panadería', 1990.00, 4, 10, 30, DATE_ADD(CURDATE(), INTERVAL 2 DAY)),

-- 5. Panadería: Estado normal
('7801110000055', 'Pan de Molde Blanco Ideal 560g', 'Panadería', 2350.00, 12, 4, 15, DATE_ADD(CURDATE(), INTERVAL 25 DAY)),

-- 6. Abarrotes: Estado normal
('7804440000066', 'Arroz Grado 1 Tucapel 1kg', 'Abarrotes', 1650.00, 18, 5, 25, DATE_ADD(CURDATE(), INTERVAL 365 DAY)),

-- 7. Abarrotes: Sin stock (stock_actual = 0) para probar bloqueo de botón en ventas
('7805550000077', 'Fideos Spaghetti N°5 Lucchetti 400g', 'Abarrotes', 990.00, 0, 6, 24, DATE_ADD(CURDATE(), INTERVAL 400 DAY)),

-- 8. Snacks: Estado normal
('7806660000088', 'Papas Fritas Marco Polo 200g', 'Snacks', 1790.00, 8, 4, 16, DATE_ADD(CURDATE(), INTERVAL 90 DAY)),

-- 9. Snacks: Stock crítico (1 <= 25% de 12) y por vencer en 8 días
('7807770000099', 'Galletas Morocha Costa 120g', 'Snacks', 750.00, 1, 5, 12, DATE_ADD(CURDATE(), INTERVAL 8 DAY)),

-- 10. Aseo: Sin fecha de caducidad (NULL) y stock normal
('7808880000100', 'Lavaloza Quix Limón 750ml', 'Aseo', 2200.00, 10, 3, 15, NULL)
ON DUPLICATE KEY UPDATE nombre_producto = VALUES(nombre_producto);

