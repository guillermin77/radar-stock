<?php
/**
 * RADAR STOCK - Resumen de Ventas
 * Método: GET (Solo Administrador)
 * 
 * Parámetros:
 * - periodo: 'hoy', '7dias', 'mes' (por defecto 'hoy')
 * - O bien rango personalizado: 'desde' y 'hasta' con formato 'YYYY-MM-DD'
 * 
 * Reglas:
 * 1. Fechas calculadas en PHP con la zona horaria 'America/Santiago'.
 * 2. Sentencias preparadas para todas las consultas.
 * 3. Valida formato de fechas y que 'desde' no sea mayor que 'hasta'.
 * 4. Devuelve: total_vendido, cantidad_ventas y los 10 productos más vendidos (nombre, unidades, monto).
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar método HTTP
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET.', 405);
}

// Validar autenticación y rol (Solo Administrador)
$usuario = requerirRol(['Administrador']);

// Configurar zona horaria de Chile
$tz = new DateTimeZone('America/Santiago');

$desdeParam = isset($_GET['desde']) ? trim($_GET['desde']) : null;
$hastaParam = isset($_GET['hasta']) ? trim($_GET['hasta']) : null;
$periodoParam = isset($_GET['periodo']) ? strtolower(trim($_GET['periodo'])) : null;

$fechaInicio = '';
$fechaFin = '';
$nombrePeriodo = '';

// Función auxiliar para validar fecha en formato YYYY-MM-DD
function esFechaValida(string $fecha): bool {
    if (!preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', $fecha, $matches)) {
        return false;
    }
    return checkdate((int) $matches[2], (int) $matches[3], (int) $matches[1]);
}

// 1. Caso: Rango personalizado mediante 'desde' y/o 'hasta'
if ($desdeParam !== null || $hastaParam !== null) {
    if (empty($desdeParam) || empty($hastaParam)) {
        responderError("Para un rango personalizado debe proporcionar tanto 'desde' como 'hasta' con formato YYYY-MM-DD.", 400);
    }

    if (!esFechaValida($desdeParam)) {
        responderError("El parámetro 'desde' tiene un formato de fecha inválido. Se espera YYYY-MM-DD.", 400);
    }

    if (!esFechaValida($hastaParam)) {
        responderError("El parámetro 'hasta' tiene un formato de fecha inválido. Se espera YYYY-MM-DD.", 400);
    }

    // Validar que desde no sea mayor que hasta
    if ($desdeParam > $hastaParam) {
        responderError("La fecha de inicio (desde: $desdeParam) no puede ser mayor que la fecha de término (hasta: $hastaParam).", 400);
    }

    $fechaInicio = "$desdeParam 00:00:00";
    $fechaFin = "$hastaParam 23:59:59";
    $nombrePeriodo = 'personalizado';

} else {
    // 2. Caso: Períodos predefinidos (hoy, 7dias, mes)
    $periodo = $periodoParam ?: 'hoy';
    $nombrePeriodo = $periodo;

    switch ($periodo) {
        case 'hoy':
            $fechaInicio = (new DateTime('today', $tz))->format('Y-m-d 00:00:00');
            $fechaFin = (new DateTime('today', $tz))->format('Y-m-d 23:59:59');
            break;

        case '7dias':
            // Últimos 7 días incluyendo hoy (ej. desde hace 6 días a las 00:00:00 hasta hoy a las 23:59:59)
            $fechaInicio = (new DateTime('today', $tz))->modify('-6 days')->format('Y-m-d 00:00:00');
            $fechaFin = (new DateTime('today', $tz))->format('Y-m-d 23:59:59');
            break;

        case 'mes':
            // Desde el primer día del mes actual a las 00:00:00 hasta el último día del mes a las 23:59:59
            $fechaInicio = (new DateTime('first day of this month', $tz))->format('Y-m-d 00:00:00');
            $fechaFin = (new DateTime('last day of this month', $tz))->format('Y-m-d 23:59:59');
            break;

        default:
            responderError("Parámetro 'periodo' inválido. Los valores permitidos son: 'hoy', '7dias' o 'mes', o especificar 'desde' y 'hasta'.", 400);
    }
}

try {
    $pdo = getDBConnection();

    // 1. Total vendido y cantidad de ventas en el período
    $sqlResumen = 'SELECT 
                       COALESCE(SUM(total_venta), 0) AS total_vendido,
                       COUNT(id) AS cantidad_ventas
                   FROM ventas
                   WHERE fecha_venta BETWEEN :inicio AND :fin';

    $stmtResumen = $pdo->prepare($sqlResumen);
    $stmtResumen->execute([
        ':inicio' => $fechaInicio,
        ':fin'    => $fechaFin
    ]);
    $resumen = $stmtResumen->fetch();

    $totalVendido = (float) ($resumen['total_vendido'] ?? 0);
    $cantidadVentas = (int) ($resumen['cantidad_ventas'] ?? 0);

    // 2. Los 10 productos más vendidos del período (ordenados por unidades y monto)
    $sqlTop = 'SELECT 
                   p.nombre_producto AS nombre,
                   COALESCE(SUM(dv.cantidad), 0) AS unidades,
                   COALESCE(SUM(dv.cantidad * dv.precio_unitario), 0) AS monto
               FROM detalle_ventas dv
               INNER JOIN ventas v ON dv.venta_id = v.id
               INNER JOIN productos p ON dv.producto_id = p.id
               WHERE v.fecha_venta BETWEEN :inicio AND :fin
               GROUP BY p.id, p.nombre_producto
               ORDER BY unidades DESC, monto DESC, p.nombre_producto ASC
               LIMIT 10';

    $stmtTop = $pdo->prepare($sqlTop);
    $stmtTop->execute([
        ':inicio' => $fechaInicio,
        ':fin'    => $fechaFin
    ]);
    $productosFilas = $stmtTop->fetchAll();

    $productosMasVendidos = [];
    foreach ($productosFilas as $fila) {
        $productosMasVendidos[] = [
            'nombre'   => (string) $fila['nombre'],
            'unidades' => (int) $fila['unidades'],
            'monto'    => (float) $fila['monto']
        ];
    }

    // Respuesta JSON exitosa
    responderJson([
        'estado'                 => 'exito',
        'periodo'                => $nombrePeriodo,
        'fecha_inicio'           => $fechaInicio,
        'fecha_fin'              => $fechaFin,
        'total_vendido'          => $totalVendido,
        'cantidad_ventas'        => $cantidadVentas,
        'productos_mas_vendidos' => $productosMasVendidos
    ], 200);

} catch (Exception $e) {
    error_log('Error en api/resumen.php: ' . $e->getMessage());
    responderError('Error interno al obtener el resumen de ventas.', 500);
}

