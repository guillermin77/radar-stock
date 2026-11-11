<?php
/**
 * RADAR STOCK - Endpoint de Auditoría de Ventas por Vendedor
 *
 * GET (Solo Administrador):
 * - Con usuario_id: devuelve métricas del vendedor, top 10 productos y lista paginada de ventas con detalle.
 * - Sin usuario_id: devuelve comparativo de todos los usuarios con ventas en el período y total general.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';
require_once __DIR__ . '/../includes/ventas_vendedor_helper.php';

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET.', 405);
}

// 1. Autorización: Exclusivo para Administrador (401 si falta/expiró token, 403 otros roles)
$admin = requerirRol(['Administrador']);

try {
    $pdo = getDBConnection();

    // 2. Validar parámetros de entrada
    $usuarioId = validarParametroUsuarioId();
    $rangoParam = isset($_GET['rango']) ? (string) $_GET['rango'] : null;
    $desdeParam = isset($_GET['desde']) ? (string) $_GET['desde'] : null;
    $hastaParam = isset($_GET['hasta']) ? (string) $_GET['hasta'] : null;

    $rango = calcularRangoFechasChile($rangoParam, $desdeParam, $hastaParam);
    $fechaInicio = $rango['inicio'];
    $fechaFin = $rango['fin'];

    // Validar paginación
    $pagina = 1;
    if (isset($_GET['pagina']) && trim((string) $_GET['pagina']) !== '') {
        $rawPag = trim((string) $_GET['pagina']);
        if (!ctype_digit($rawPag) || (int) $rawPag < 1) {
            responderError("El parámetro 'pagina' debe ser un número entero mayor o igual a 1.", 400);
        }
        $pagina = (int) $rawPag;
    }

    $porPagina = 30;
    if (isset($_GET['por_pagina']) && trim((string) $_GET['por_pagina']) !== '') {
        $rawPorPag = trim((string) $_GET['por_pagina']);
        if (!ctype_digit($rawPorPag) || (int) $rawPorPag < 1 || (int) $rawPorPag > 100) {
            responderError("El parámetro 'por_pagina' debe ser un número entero entre 1 y 100.", 400);
        }
        $porPagina = (int) $rawPorPag;
    }
    $offset = ($pagina - 1) * $porPagina;

    // 3. Multi-local: tomar local_id del usuario autenticado desde el servidor
    $localIdAdmin = obtenerLocalUsuarioAutenticado($pdo, (int) $admin['id']);
    $ventasTieneLocal = ($localIdAdmin !== null && tieneColumnaLocalTabla($pdo, 'ventas'));
    $usuariosTieneLocal = ($localIdAdmin !== null && tieneColumnaLocalTabla($pdo, 'usuarios'));

    // ==========================================================
    // MODO A: Detalle de un vendedor específico (con usuario_id)
    // ==========================================================
    if ($usuarioId !== null) {
        $vendedor = validarYObtenerVendedor($pdo, $usuarioId, $localIdAdmin);

        $condLocalSql = $ventasTieneLocal ? ' AND v.local_id = :local_id' : '';
        $paramsBase = [
            ':usuario_id' => $usuarioId,
            ':inicio'     => $fechaInicio,
            ':fin'        => $fechaFin,
        ];
        if ($ventasTieneLocal) {
            $paramsBase[':local_id'] = $localIdAdmin;
        }

        // Métricas globales del vendedor en el período
        $sqlMetricas = "SELECT 
                            COALESCE(SUM(v.total_venta), 0) AS total_vendido,
                            COUNT(v.id) AS cantidad_ventas
                        FROM ventas v
                        WHERE v.usuario_id = :usuario_id
                          AND v.fecha_venta BETWEEN :inicio AND :fin
                          {$condLocalSql}";
        $stmtMetricas = $pdo->prepare($sqlMetricas);
        $stmtMetricas->execute($paramsBase);
        $metricas = $stmtMetricas->fetch();

        $totalVendido = (float) ($metricas['total_vendido'] ?? 0);
        $cantidadVentas = (int) ($metricas['cantidad_ventas'] ?? 0);
        $ticketPromedio = $cantidadVentas > 0 ? round($totalVendido / $cantidadVentas, 2) : 0.0;

        // Top 10 productos más vendidos por este vendedor en el período
        $sqlTop = "SELECT 
                       p.nombre_producto AS nombre,
                       COALESCE(SUM(dv.cantidad), 0) AS unidades,
                       COALESCE(SUM(dv.cantidad * dv.precio_unitario), 0) AS monto
                   FROM detalle_ventas dv
                   INNER JOIN ventas v ON dv.venta_id = v.id
                   INNER JOIN productos p ON dv.producto_id = p.id
                   WHERE v.usuario_id = :usuario_id
                     AND v.fecha_venta BETWEEN :inicio AND :fin
                     {$condLocalSql}
                   GROUP BY p.id, p.nombre_producto
                   ORDER BY unidades DESC, monto DESC, p.nombre_producto ASC
                   LIMIT 10";
        $stmtTop = $pdo->prepare($sqlTop);
        $stmtTop->execute($paramsBase);
        $topFilas = $stmtTop->fetchAll();

        $productosMasVendidos = [];
        foreach ($topFilas as $fila) {
            $productosMasVendidos[] = [
                'nombre'   => (string) $fila['nombre'],
                'unidades' => (int) $fila['unidades'],
                'monto'    => (float) $fila['monto'],
            ];
        }

        // Lista paginada de ventas del vendedor
        $sqlVentas = "SELECT 
                          v.id,
                          DATE_FORMAT(v.fecha_venta, '%Y-%m-%d %H:%i:%s') AS fecha,
                          v.total_venta AS total,
                          COALESCE((SELECT SUM(dv.cantidad) FROM detalle_ventas dv WHERE dv.venta_id = v.id), 0) AS cantidad_items
                      FROM ventas v
                      WHERE v.usuario_id = :usuario_id
                        AND v.fecha_venta BETWEEN :inicio AND :fin
                        {$condLocalSql}
                      ORDER BY v.fecha_venta DESC, v.id DESC
                      LIMIT :limit OFFSET :offset";
        $stmtVentas = $pdo->prepare($sqlVentas);
        foreach ($paramsBase as $clave => $valor) {
            $stmtVentas->bindValue($clave, $valor);
        }
        $stmtVentas->bindValue(':limit', $porPagina, PDO::PARAM_INT);
        $stmtVentas->bindValue(':offset', $offset, PDO::PARAM_INT);
        $stmtVentas->execute();
        $ventasFilas = $stmtVentas->fetchAll();

        // Consultar el detalle de todas las ventas de esta página en una sola consulta
        $detallesPorVenta = [];
        if (!empty($ventasFilas)) {
            $ventaIds = array_map(static function ($f) {
                return (int) $f['id'];
            }, $ventasFilas);
            $placeholders = implode(',', array_fill(0, count($ventaIds), '?'));

            $sqlDetalles = "SELECT 
                                dv.venta_id,
                                p.nombre_producto AS producto,
                                dv.cantidad,
                                dv.precio_unitario,
                                (dv.cantidad * dv.precio_unitario) AS subtotal
                            FROM detalle_ventas dv
                            INNER JOIN productos p ON dv.producto_id = p.id
                            WHERE dv.venta_id IN ({$placeholders})
                            ORDER BY dv.id ASC";
            $stmtDetalles = $pdo->prepare($sqlDetalles);
            $stmtDetalles->execute($ventaIds);
            foreach ($stmtDetalles->fetchAll() as $det) {
                $vid = (int) $det['venta_id'];
                $detallesPorVenta[$vid][] = [
                    'producto'        => (string) $det['producto'],
                    'cantidad'        => (int) $det['cantidad'],
                    'precio_unitario' => (float) $det['precio_unitario'],
                    'subtotal'        => (float) $det['subtotal'],
                ];
            }
        }

        $ventas = [];
        foreach ($ventasFilas as $vf) {
            $vid = (int) $vf['id'];
            $ventas[] = [
                'id'             => $vid,
                'fecha'          => (string) $vf['fecha'],
                'total'          => (float) $vf['total'],
                'cantidad_items' => (int) $vf['cantidad_items'],
                'detalles'       => $detallesPorVenta[$vid] ?? [],
            ];
        }

        $totalPaginas = $porPagina > 0 ? (int) ceil($cantidadVentas / $porPagina) : 1;

        responderJson([
            'estado'                 => 'exito',
            'periodo'                => $rango['periodo'],
            'fecha_inicio'           => $fechaInicio,
            'fecha_fin'              => $fechaFin,
            'vendedor'               => $vendedor,
            'total_vendido'          => $totalVendido,
            'cantidad_ventas'        => $cantidadVentas,
            'ticket_promedio'        => $ticketPromedio,
            'productos_mas_vendidos' => $productosMasVendidos,
            'pagina'                 => $pagina,
            'por_pagina'             => $porPagina,
            'total'                  => $cantidadVentas,
            'total_paginas'          => $totalPaginas,
            'ventas'                 => $ventas,
        ], 200);
    }

    // ==========================================================
    // MODO B: Comparativo de todos los vendedores (sin usuario_id)
    // ==========================================================
    $condLocalSql = '';
    $paramsComparativo = [
        ':inicio' => $fechaInicio,
        ':fin'    => $fechaFin,
    ];
    if ($ventasTieneLocal) {
        $condLocalSql .= ' AND v.local_id = :v_local_id';
        $paramsComparativo[':v_local_id'] = $localIdAdmin;
    }
    if ($usuariosTieneLocal) {
        $condLocalSql .= ' AND u.local_id = :u_local_id';
        $paramsComparativo[':u_local_id'] = $localIdAdmin;
    }

    // Incluye todos los usuarios con ventas en el período (activos o desactivados)
    $sqlComparativo = "SELECT 
                           u.id AS usuario_id,
                           u.nombre,
                           u.rol,
                           u.activo,
                           COUNT(v.id) AS cantidad_ventas,
                           COALESCE(SUM(v.total_venta), 0) AS total_vendido
                       FROM ventas v
                       INNER JOIN usuarios u ON v.usuario_id = u.id
                       WHERE v.fecha_venta BETWEEN :inicio AND :fin
                         {$condLocalSql}
                       GROUP BY u.id, u.nombre, u.rol, u.activo
                       ORDER BY total_vendido DESC, cantidad_ventas DESC, u.nombre ASC";
    $stmtComp = $pdo->prepare($sqlComparativo);
    $stmtComp->execute($paramsComparativo);
    $filasComp = $stmtComp->fetchAll();

    $vendedores = [];
    $totalGeneral = 0.0;
    $cantidadVentasTotal = 0;

    foreach ($filasComp as $fila) {
        $cant = (int) $fila['cantidad_ventas'];
        $monto = (float) $fila['total_vendido'];
        $ticket = $cant > 0 ? round($monto / $cant, 2) : 0.0;

        $totalGeneral += $monto;
        $cantidadVentasTotal += $cant;

        $vendedores[] = [
            'usuario_id'      => (int) $fila['usuario_id'],
            'nombre'          => (string) $fila['nombre'],
            'rol'             => (string) $fila['rol'],
            'activo'          => isset($fila['activo']) ? (int) $fila['activo'] : 1,
            'cantidad_ventas' => $cant,
            'total_vendido'   => $monto,
            'ticket_promedio' => $ticket,
        ];
    }

    responderJson([
        'estado'                => 'exito',
        'periodo'               => $rango['periodo'],
        'fecha_inicio'          => $fechaInicio,
        'fecha_fin'             => $fechaFin,
        'total_general'         => $totalGeneral,
        'cantidad_ventas_total' => $cantidadVentasTotal,
        'vendedores'            => $vendedores,
    ], 200);

} catch (Throwable $e) {
    error_log('Error en api/ventas_vendedor.php: ' . $e->getMessage());
    responderError('Ocurrió un error interno al consultar las ventas por vendedor.', 500);
}

