<?php
/**
 * RADAR STOCK - Endpoint de Listado de Productos
 * Método: GET
 * Retorna: Lista de productos en formato JSON con precios y stock como números
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';

// Validar que la petición sea GET
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET.', 405);
}

try {
    $pdo = getDBConnection();

    $sql = 'SELECT id, nombre_producto, categoria, stock_actual, stock_minimo, precio_venta, fecha_vencimiento 
            FROM productos 
            ORDER BY nombre_producto ASC';

    $stmt = $pdo->query($sql);
    $filas = $stmt->fetchAll();

    // Mapeo garantizando tipos numéricos reales (int y float)
    $productos = [];
    foreach ($filas as $f) {
        $productos[] = [
            'id'                => (int) $f['id'],
            'nombre_producto'   => $f['nombre_producto'],
            'categoria'         => $f['categoria'],
            'stock_actual'      => (int) $f['stock_actual'],
            'stock_minimo'      => (int) $f['stock_minimo'],
            'precio_venta'      => (float) $f['precio_venta'],
            'fecha_vencimiento' => $f['fecha_vencimiento']
        ];
    }

    responderJson($productos, 200);

} catch (Exception $e) {
    error_log('Error en productos.php: ' . $e->getMessage());
    responderError('Error al obtener la lista de productos: ' . $e->getMessage(), 500);
}

