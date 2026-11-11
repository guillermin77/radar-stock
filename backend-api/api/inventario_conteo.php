<?php
/**
 * RADAR STOCK - Endpoint de Conteo Físico de Inventario
 *
 * POST (Solo Administrador): Actualiza el stock de una lista de productos en una sola transacción,
 * registra movimientos tipo 'conteo' con la diferencia (contado - sistema) y devuelve un resumen.
 * Si ocurre cualquier fallo, revierte todos los cambios (rollback).
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

// Acepta tanto un array directo [ {...}, {...} ] como un objeto con clave items: { "items": [ {...} ] }
$items = isset($datos['items']) && is_array($datos['items']) ? $datos['items'] : $datos;

if (!is_array($items) || empty($items)) {
    responderError('La lista de productos para el conteo no puede estar vacía.', 400);
}

// 3. Validación previa de cada elemento
$idsVistos = [];
$itemsValidados = [];

foreach ($items as $indice => $item) {
    if (!is_array($item)) {
        responderError("El elemento en la posición {$indice} no tiene un formato válido.", 400);
    }

    if (!isset($item['producto_id']) || !is_numeric($item['producto_id']) || (int) $item['producto_id'] <= 0) {
        responderError("El campo 'producto_id' en la posición {$indice} es obligatorio y debe ser un número entero mayor a 0.", 400);
    }
    $prodId = (int) $item['producto_id'];

    if (isset($idsVistos[$prodId])) {
        responderError("El producto con ID {$prodId} se encuentra duplicado en la lista enviada.", 400);
    }
    $idsVistos[$prodId] = true;

    if (!isset($item['contado']) || !is_numeric($item['contado'])) {
        responderError("El campo 'contado' para el producto ID {$prodId} es obligatorio y debe ser numérico.", 400);
    }

    $contado = (int) $item['contado'];
    if ((float) $item['contado'] != $contado || $contado < 0) {
        responderError("El stock 'contado' para el producto ID {$prodId} debe ser un número entero mayor o igual a 0.", 400);
    }

    $itemsValidados[] = [
        'producto_id' => $prodId,
        'contado'     => $contado
    ];
}

// Ordenar por producto_id ascendente para prevenir interbloqueos (deadlocks) en la transacción
usort($itemsValidados, function ($a, $b) {
    return $a['producto_id'] <=> $b['producto_id'];
});

// 4. Proceso transaccional atómico
try {
    $pdo = getDBConnection();
    $pdo->beginTransaction();

    $stmtBuscar = $pdo->prepare('SELECT id, nombre_producto, stock_actual FROM productos WHERE id = ? FOR UPDATE');
    $stmtUpdate = $pdo->prepare('UPDATE productos SET stock_actual = ? WHERE id = ?');
    $stmtMov = $pdo->prepare(
        'INSERT INTO movimientos_stock (producto_id, usuario_id, tipo, cantidad, stock_antes, stock_despues, fecha) 
         VALUES (?, ?, \'conteo\', ?, ?, ?, ?)'
    );

    $detalles = [];
    $totalModificados = 0;

    foreach ($itemsValidados as $item) {
        $pId = $item['producto_id'];
        $contado = $item['contado'];

        $stmtBuscar->execute([$pId]);
        $prod = $stmtBuscar->fetch();

        if (!$prod) {
            $pdo->rollBack();
            responderError("El producto con ID {$pId} no existe en la base de datos.", 404);
        }

        $stockAntes = (int) $prod['stock_actual'];
        $stockDespues = $contado;
        $diferencia = $contado - $stockAntes; // Cantidad: contado menos sistema

        // Actualizar stock_actual al valor contado
        $stmtUpdate->execute([$stockDespues, $pId]);

        // Registrar movimiento de auditoría en hora de Chile (patrón ventas.php)
        $stmtMov->execute([
            $pId,
            $admin['id'],
            $diferencia,
            $stockAntes,
            $stockDespues,
            obtenerFechaChile()
        ]);

        $cambio = ($diferencia !== 0);
        if ($cambio) {
            $totalModificados++;
        }

        $detalles[] = [
            'producto_id'      => $pId,
            'nombre_producto'  => $prod['nombre_producto'],
            'stock_anterior'   => $stockAntes,
            'stock_contado'    => $stockDespues,
            'diferencia'       => $diferencia,
            'modificado'       => $cambio
        ];
    }

    $pdo->commit();

    responderJson([
        'mensaje'           => 'Conteo de inventario procesado exitosamente.',
        'total_procesados'  => count($itemsValidados),
        'total_modificados' => $totalModificados,
        'detalles'          => $detalles
    ], 200);

} catch (Throwable $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Error en inventario_conteo.php: ' . $e->getMessage());
    responderError('Ocurrió un error interno al procesar el conteo de inventario.', 500);
}

