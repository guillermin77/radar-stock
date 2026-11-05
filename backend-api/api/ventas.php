<?php
/**
 * RADAR STOCK - Endpoint de Registro de Ventas
 * Método: POST (Cualquier rol autenticado: Administrador, Vendedor, Colaborador)
 * Cuerpo: JSON { "codigo_operacion": "uuid-opcional", "items": [ { "producto_id": 1, "cantidad": 2 }, ... ] }
 *
 * Reglas de negocio:
 * 1. Valida items no vacío, cantidades enteros > 0 y consolida productos repetidos.
 * 2. Idempotencia: si ya existe una venta con codigo_operacion, responde con esa misma venta sin duplicar ni descontar stock.
 * 3. Procesa productos en orden de ID ascendente para prevenir deadlocks.
 * 4. Descuenta stock atómicamente con 'AND stock_actual >= ?'. Si falta stock, revierte todo y responde 409.
 * 5. Precios calculados exclusivamente en el servidor desde la base de datos (nunca desde el cliente).
 * 6. Fecha calculada en PHP con la zona horaria 'America/Santiago'.
 * 7. Al confirmar la venta, devuelve el campo 'alertas_nuevas' con las alertas de stock (agotado, critico, bajo) de los productos vendidos.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar que la petición sea POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones POST.', 405);
}

// Requiere sesión activa con cualquier rol
$usuario = autenticar();

// Leer cuerpo JSON
$cuerpo = file_get_contents('php://input');
$datos = json_decode($cuerpo, true);

if (!is_array($datos)) {
    responderError('Cuerpo de la petición inválido. Se espera JSON.', 400);
}

// 1. Validar lista de items
if (!isset($datos['items']) || !is_array($datos['items']) || empty($datos['items'])) {
    responderError('La venta debe incluir al menos un producto en la lista de items.', 400);
}

// Consolidar productos repetidos y validar enteros mayores a 0
$itemsConsolidados = [];
foreach ($datos['items'] as $index => $item) {
    if (!isset($item['producto_id']) || !is_numeric($item['producto_id']) || (int) $item['producto_id'] <= 0) {
        responderError("El item en la posición $index debe contener un producto_id válido mayor a 0.", 400);
    }

    if (!isset($item['cantidad']) || !is_numeric($item['cantidad']) || (int) $item['cantidad'] <= 0) {
        responderError("La cantidad del producto en la posición $index debe ser un entero mayor a 0.", 400);
    }

    $pid = (int) $item['producto_id'];
    $cant = (int) $item['cantidad'];

    $itemsConsolidados[$pid] = ($itemsConsolidados[$pid] ?? 0) + $cant;
}

// Código de operación opcional (UUID)
$codigoOperacion = !empty(trim($datos['codigo_operacion'] ?? '')) ? trim($datos['codigo_operacion']) : null;

try {
    $pdo = getDBConnection();

    // 2. Idempotencia: Verificar si ya existe una venta con este código de operación
    if ($codigoOperacion !== null) {
        $stmtExiste = $pdo->prepare('SELECT id, total_venta, fecha_venta FROM ventas WHERE codigo_operacion = ? LIMIT 1');
        $stmtExiste->execute([$codigoOperacion]);
        $ventaExistente = $stmtExiste->fetch();

        if ($ventaExistente) {
            // Responder con la venta ya registrada sin duplicar ni descontar stock nuevamente
            responderJson([
                'venta_id'       => (int) $ventaExistente['id'],
                'total'          => (float) $ventaExistente['total_venta'],
                'fecha'          => $ventaExistente['fecha_venta'],
                'alertas_nuevas' => []
            ], 200);
        }
    }

    // 3. Iniciar Transacción Atómica
    $pdo->beginTransaction();

    // Ordenar productos por ID ascendente para evitar bloqueos mutuos (deadlocks)
    ksort($itemsConsolidados, SORT_NUMERIC);

    $totalVenta = 0.0;
    $detallesParaInsertar = [];

    // Preparar sentencias
    $stmtConsultarProducto = $pdo->prepare('SELECT id, nombre_producto, precio_venta, stock_actual, stock_minimo, stock_deseado FROM productos WHERE id = ? FOR UPDATE');
    $stmtDescontarStock = $pdo->prepare('UPDATE productos SET stock_actual = stock_actual - ? WHERE id = ? AND stock_actual >= ?');

    foreach ($itemsConsolidados as $productoId => $cantidad) {
        // Consultar producto y bloquear fila
        $stmtConsultarProducto->execute([$productoId]);
        $producto = $stmtConsultarProducto->fetch();

        if (!$producto) {
            $pdo->rollBack();
            responderError("Producto con ID $productoId no existe en el catálogo.", 404);
        }

        // Descontar stock con validación de suficiencia
        $stmtDescontarStock->execute([$cantidad, $productoId, $cantidad]);

        if ($stmtDescontarStock->rowCount() === 0) {
            $pdo->rollBack();
            responderError("Stock insuficiente: {$producto['nombre_producto']} (disponible: {$producto['stock_actual']})", 409);
        }

        $precioUnitario = (float) $producto['precio_venta'];
        $subtotal = $precioUnitario * $cantidad;
        $totalVenta += $subtotal;

        $detallesParaInsertar[] = [
            'producto_id'     => $productoId,
            'cantidad'        => $cantidad,
            'precio_unitario' => $precioUnitario
        ];
    }

    // 4. Calcular fecha y hora en PHP con zona horaria America/Santiago
    $dt = new DateTime('now', new DateTimeZone('America/Santiago'));
    $fechaVenta = $dt->format('Y-m-d H:i:s');

    // Guardar encabezado de la venta
    $stmtVenta = $pdo->prepare('INSERT INTO ventas (usuario_id, total_venta, codigo_operacion, fecha_venta) VALUES (?, ?, ?, ?)');
    $stmtVenta->execute([
        $usuario['id'],
        $totalVenta,
        $codigoOperacion,
        $fechaVenta
    ]);

    $ventaId = (int) $pdo->lastInsertId();

    // Guardar detalle de la venta
    $stmtDetalle = $pdo->prepare('INSERT INTO detalle_ventas (venta_id, producto_id, cantidad, precio_unitario) VALUES (?, ?, ?, ?)');
    foreach ($detallesParaInsertar as $det) {
        $stmtDetalle->execute([
            $ventaId,
            $det['producto_id'],
            $det['cantidad'],
            $det['precio_unitario']
        ]);
    }

    // Confirmar la transacción
    $pdo->commit();

    // 5. Consultar alertas de stock para los productos que se acaban de vender
    $alertasNuevas = [];
    if (!empty($detallesParaInsertar)) {
        $idsVendidos = array_values(array_unique(array_column($detallesParaInsertar, 'producto_id')));
        $placeholders = implode(',', array_fill(0, count($idsVendidos), '?'));

        $sqlAlertas = "SELECT id AS producto_id, nombre_producto, stock_actual, stock_minimo, stock_deseado, fecha_vencimiento
                       FROM productos
                       WHERE id IN ($placeholders)";
        $stmtAlertas = $pdo->prepare($sqlAlertas);
        $stmtAlertas->execute($idsVendidos);
        $productosVendidos = $stmtAlertas->fetchAll();

        foreach ($productosVendidos as $p) {
            $stockActual = (int) $p['stock_actual'];
            $stockMinimo = (int) $p['stock_minimo'];
            $stockDeseado = (int) $p['stock_deseado'];
            $nombre = $p['nombre_producto'];

            $tipo = null;
            $mensaje = '';

            if ($stockActual === 0) {
                $tipo = 'agotado';
                $mensaje = "Sin stock: $nombre se agotó.";
            } elseif ($stockActual > 0 && $stockDeseado > 0 && $stockActual <= ($stockDeseado * 0.25)) {
                $tipo = 'critico';
                $mensaje = "Stock crítico: $nombre está por agotarse ($stockActual unidades).";
            } elseif ($stockActual <= $stockMinimo) {
                $tipo = 'bajo';
                $mensaje = "Stock bajo: $nombre tiene $stockActual unidades (mínimo $stockMinimo). Conviene reponer.";
            }

            if ($tipo !== null) {
                $alertasNuevas[] = [
                    'producto_id'       => (int) $p['producto_id'],
                    'nombre_producto'   => $nombre,
                    'tipo'              => $tipo,
                    'mensaje'           => $mensaje,
                    'stock_actual'      => $stockActual,
                    'stock_minimo'      => $stockMinimo,
                    'stock_deseado'     => $stockDeseado,
                    'fecha_vencimiento' => $p['fecha_vencimiento'] ?: null,
                    'dias_para_vencer'  => null
                ];
            }
        }

        // Orden por gravedad (agotado, critico, bajo) y luego por nombre
        usort($alertasNuevas, function($a, $b) {
            $prioridad = ['agotado' => 1, 'critico' => 2, 'bajo' => 3];
            $cmp = ($prioridad[$a['tipo']] ?? 99) <=> ($prioridad[$b['tipo']] ?? 99);
            if ($cmp !== 0) return $cmp;
            return strcmp($a['nombre_producto'], $b['nombre_producto']);
        });
    }

    // Responder con los datos de la venta confirmada y alertas nuevas
    responderJson([
        'venta_id'       => $ventaId,
        'total'          => (float) $totalVenta,
        'fecha'          => $fechaVenta,
        'alertas_nuevas' => $alertasNuevas
    ], 201);

} catch (Exception $e) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('Error en ventas.php: ' . $e->getMessage());
    responderError('Error interno al registrar la venta: ' . $e->getMessage(), 500);
}
