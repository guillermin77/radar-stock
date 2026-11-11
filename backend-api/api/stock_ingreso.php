<?php
/**
 * RADAR STOCK - Endpoint de Ingreso de Stock
 *
 * POST (Solo Administrador): Suma stock_actual atómicamente y registra auditoría en movimientos_stock.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

$metodo = $_SERVER['REQUEST_METHOD'];

if ($metodo !== 'POST') {
    responderError('Método no permitido. Utilice POST.', 405);
}

// 1. Autorización: Exclusivo para Administrador (devuelve 403 para otros roles)
$admin = requerirRol(['Administrador']);

// 2. Lectura y decodificación del cuerpo JSON
$cuerpo = file_get_contents('php://input');
$datos = json_decode($cuerpo, true);

if (!is_array($datos)) {
    responderError('Cuerpo de la petición inválido. Se espera JSON.', 400);
}

// 3. Validación de producto_id
if (!isset($datos['producto_id']) || !is_numeric($datos['producto_id']) || (int) $datos['producto_id'] <= 0) {
    responderError('El ID del producto es obligatorio y debe ser un número entero mayor a 0.', 400);
}
$productoId = (int) $datos['producto_id'];

// 4. Validación de cantidad: entera mayor a 0 y menor a 100000
if (!isset($datos['cantidad']) || !is_numeric($datos['cantidad'])) {
    responderError('La cantidad es obligatoria y debe ser numérica.', 400);
}

$cantidad = (int) $datos['cantidad'];
// Verificar que sea un entero estricto y cumpla el rango 0 < cantidad < 100000
if ((float) $datos['cantidad'] != $cantidad || $cantidad <= 0 || $cantidad >= 100000) {
    responderError('La cantidad debe ser un número entero mayor a 0 y menor a 100000.', 400);
}

// 5. Proceso transaccional
try {
    $pdo = getDBConnection();
    $pdo->beginTransaction();

    // Bloquear y consultar el producto para lectura consistente
    $stmtProd = $pdo->prepare('SELECT id, nombre_producto, stock_actual FROM productos WHERE id = ? FOR UPDATE');
    $stmtProd->execute([$productoId]);
    $producto = $stmtProd->fetch();

    if (!$producto) {
        $pdo->rollBack();
        responderError('Producto no encontrado.', 404);
    }

    $stockAntes = (int) $producto['stock_actual'];
    $stockDespues = $stockAntes + $cantidad;

    // Actualizar stock_actual con UPDATE atómico
    $stmtUpdate = $pdo->prepare('UPDATE productos SET stock_actual = stock_actual + ? WHERE id = ?');
    $stmtUpdate->execute([$cantidad, $productoId]);

    // Registrar en tabla de movimientos_stock con fecha explícita en hora de Chile (patrón ventas.php)
    $stmtMov = $pdo->prepare(
        'INSERT INTO movimientos_stock (producto_id, usuario_id, tipo, cantidad, stock_antes, stock_despues, fecha) 
         VALUES (?, ?, \'ingreso\', ?, ?, ?, ?)'
    );
    $stmtMov->execute([
        $productoId,
        $admin['id'],
        $cantidad,
        $stockAntes,
        $stockDespues,
        obtenerFechaChile()
    ]);

    $movimientoId = (int) $pdo->lastInsertId();

    $pdo->commit();

    responderJson([
        'mensaje'           => 'Ingreso de stock registrado exitosamente.',
        'movimiento_id'     => $movimientoId,
        'producto_id'       => $productoId,
        'nombre_producto'   => $producto['nombre_producto'],
        'cantidad_ingresada'=> $cantidad,
        'stock_anterior'    => $stockAntes,
        'stock_actual'      => $stockDespues
    ], 200);

} catch (Throwable $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Error en stock_ingreso.php: ' . $e->getMessage());
    responderError('Ocurrió un error interno al registrar el ingreso de stock.', 500);
}

