-- ==========================================================
-- RADAR STOCK - Eliminación de los 5000 productos masivos de prueba
-- Elimina únicamente los registros de prueba masiva (Demo 0001 a Demo 5000)
-- Preserva intactos los 10 productos iniciales del catálogo.
-- ==========================================================

DELETE FROM productos 
WHERE nombre_producto LIKE 'Demo %';

