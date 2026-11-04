<?php
/**
 * RADAR STOCK - Endpoint de Actualización Rápida de Stock
 * Método: POST (Solo Administrador)
 * Cuerpo: JSON { "producto_id": 1, "cantidad": 10 }
 * Acción: Suma la cantidad al stock_actual dentro de una transacción protegida.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar que la petición sea POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones POST.', 405);
}

// Proteger ruta: solo Administrador
requerirRol(['Administrador']);

// Leer cuerpo JSON
$cuerpo = file_get_contents('php://input');
$datos = json_decode($cuerpo, true);

if (!is_array($datos)) {
    responderError('Cuerpo de la petición inválido. Se espera JSON.', 400);
}

// Validar producto_id (entero > 0)
if (!isset($datos['producto_id']) || !is_numeric($datos['producto_id']) || (int) $datos['producto_id'] <= 0) {
    responderError('El campo producto_id es obligatorio y debe ser un entero positivo.', 400);
}
$productoId = (int) $datos['producto_id'];

// Validar cantidad (entero > 0)
if (!isset($datos['cantidad']) || !is_numeric($datos['cantidad']) || (int) $datos['cantidad'] <= 0) {
    responderError('El campo cantidad es obligatorio y debe ser un entero mayor que 0.', 400);
}
$cantidad = (int) $datos['cantidad'];

try {
    $pdo = getDBConnection();

    // Iniciar transacción para garantizar consistencia atómica y bloqueo FOR UPDATE
    $pdo->beginTransaction();

    $sqlSelect = 'SELECT id, nombre_producto, stock_actual FROM productos WHERE id = ? FOR UPDATE';
    $stmtSelect = $pdo->prepare($sqlSelect);
    $stmtSelect->execute([$productoId]);
    $producto = $stmtSelect->fetch();

    if (!$producto) {
        $pdo->rollBack();
        responderError('Producto no encontrado.', 404);
    }

    $stockAnterior = (int) $producto['stock_actual'];
    $nuevoStock = $stockAnterior + $cantidad;

    $sqlUpdate = 'UPDATE productos SET stock_actual = ? WHERE id = ?';
    $stmtUpdate = $pdo->prepare($sqlUpdate);
    $stmtUpdate->execute([$nuevoStock, $productoId]);

    // Confirmar cambios
    $pdo->commit();

    responderJson([
        'estado'            => 'ok',
        'producto_id'       => $productoId,
        'nombre_producto'   => $producto['nombre_producto'],
        'stock_anterior'    => $stockAnterior,
        'cantidad_agregada' => $cantidad,
        'stock_actual'      => $nuevoStock,
        'stock_nuevo'       => $nuevoStock
    ], 200);

} catch (Exception $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Error en stock.php: ' . $e->getMessage());
    responderError('Error al actualizar el stock: ' . $e->getMessage(), 500);
}

