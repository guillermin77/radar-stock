<?php
/**
 * RADAR STOCK - Exportación CSV de Auditoría de Ventas por Vendedor
 *
 * GET (Solo Administrador):
 * Devuelve un archivo CSV (UTF-8 con BOM, separador ';') con el detalle de ventas
 * según los filtros usuario_id, rango (hoy, 7dias, mes) o desde/hasta (YYYY-MM-DD).
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';
require_once __DIR__ . '/../includes/ventas_vendedor_helper.php';

const MAX_FILAS_EXPORT_CSV = 5000;

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET.', 405);
}

// 1. Autorización: Exclusivo para Administrador
$admin = requerirRol(['Administrador']);

/**
 * Neutraliza caracteres iniciales peligrosos para prevenir inyección de fórmulas en Excel.
 */
function limpiarCeldaCsv(string $valor): string {
    if ($valor !== '' && in_array($valor[0], ['=', '+', '-', '@', "\t", "\r"], true)) {
        return "'" . $valor;
    }
    return $valor;
}

try {
    $pdo = getDBConnection();

    // 2. Validar filtros
    $usuarioId = validarParametroUsuarioId();
    $rangoParam = isset($_GET['rango']) ? (string) $_GET['rango'] : null;
    $desdeParam = isset($_GET['desde']) ? (string) $_GET['desde'] : null;
    $hastaParam = isset($_GET['hasta']) ? (string) $_GET['hasta'] : null;

    $rango = calcularRangoFechasChile($rangoParam, $desdeParam, $hastaParam);
    $fechaInicio = $rango['inicio'];
    $fechaFin = $rango['fin'];

    // 3. Multi-local desde el token del servidor
    $localIdAdmin = obtenerLocalUsuarioAutenticado($pdo, (int) $admin['id']);
    $ventasTieneLocal = ($localIdAdmin !== null && tieneColumnaLocalTabla($pdo, 'ventas'));
    $usuariosTieneLocal = ($localIdAdmin !== null && tieneColumnaLocalTabla($pdo, 'usuarios'));

    if ($usuarioId !== null) {
        validarYObtenerVendedor($pdo, $usuarioId, $localIdAdmin);
    }

    // 4. Construir condiciones y parámetros
    $condiciones = ['v.fecha_venta BETWEEN :inicio AND :fin'];
    $parametros = [
        ':inicio' => $fechaInicio,
        ':fin'    => $fechaFin,
    ];

    if ($usuarioId !== null) {
        $condiciones[] = 'v.usuario_id = :usuario_id';
        $parametros[':usuario_id'] = $usuarioId;
    }
    if ($ventasTieneLocal) {
        $condiciones[] = 'v.local_id = :v_local_id';
        $parametros[':v_local_id'] = $localIdAdmin;
    }
    if ($usuariosTieneLocal) {
        $condiciones[] = 'u.local_id = :u_local_id';
        $parametros[':u_local_id'] = $localIdAdmin;
    }

    $whereSql = implode(' AND ', $condiciones);

    // 5. Verificar límite razonable de filas antes de generar el CSV
    $sqlConteo = "SELECT COUNT(*) 
                  FROM detalle_ventas dv
                  INNER JOIN ventas v ON dv.venta_id = v.id
                  INNER JOIN usuarios u ON v.usuario_id = u.id
                  WHERE {$whereSql}";
    $stmtConteo = $pdo->prepare($sqlConteo);
    $stmtConteo->execute($parametros);
    $totalFilas = (int) $stmtConteo->fetchColumn();

    if ($totalFilas > MAX_FILAS_EXPORT_CSV) {
        responderError(
            'La exportación excede el máximo permitido de ' . MAX_FILAS_EXPORT_CSV . " filas (se encontraron {$totalFilas}). Acota el rango de fechas o selecciona un vendedor específico.",
            400
        );
    }

    // 6. Consultar datos completos para CSV
    $sqlDatos = "SELECT 
                     DATE_FORMAT(v.fecha_venta, '%d-%m-%Y %H:%i') AS fecha_hora,
                     u.nombre AS vendedor,
                     v.id AS venta_id,
                     p.nombre_producto AS producto,
                     dv.cantidad,
                     dv.precio_unitario,
                     (dv.cantidad * dv.precio_unitario) AS subtotal,
                     v.total_venta
                 FROM detalle_ventas dv
                 INNER JOIN ventas v ON dv.venta_id = v.id
                 INNER JOIN usuarios u ON v.usuario_id = u.id
                 INNER JOIN productos p ON dv.producto_id = p.id
                 WHERE {$whereSql}
                 ORDER BY v.fecha_venta DESC, v.id DESC, dv.id ASC";
    $stmtDatos = $pdo->prepare($sqlDatos);
    $stmtDatos->execute($parametros);
    $filas = $stmtDatos->fetchAll();

    // 7. Emitir cabeceras CSV (UTF-8 con BOM y separador ';')
    $tzChile = new DateTimeZone('America/Santiago');
    $selloTiempo = (new DateTime('now', $tzChile))->format('Ymd_His');
    $sufijoVendedor = $usuarioId !== null ? "_u{$usuarioId}" : '_todos';
    $nombreArchivo = "ventas_vendedor{$sufijoVendedor}_{$selloTiempo}.csv";

    header('Content-Type: text/csv; charset=UTF-8');
    header('Content-Disposition: attachment; filename="' . $nombreArchivo . '"');
    header('Cache-Control: no-store, no-cache, must-revalidate');
    header('Pragma: no-cache');

    $salida = fopen('php://output', 'wb');
    // BOM UTF-8 para correcta apertura de tildes y ñ en Excel en español
    fwrite($salida, "\xEF\xBB\xBF");

    fputcsv($salida, [
        'Fecha y hora',
        'Vendedor',
        'ID de venta',
        'Producto',
        'Cantidad',
        'Precio unitario',
        'Subtotal',
        'Total de la venta',
    ], ';');

    foreach ($filas as $fila) {
        fputcsv($salida, [
            (string) $fila['fecha_hora'],
            limpiarCeldaCsv((string) $fila['vendedor']),
            (int) $fila['venta_id'],
            limpiarCeldaCsv((string) $fila['producto']),
            (int) $fila['cantidad'],
            round((float) $fila['precio_unitario'], 2),
            round((float) $fila['subtotal'], 2),
            round((float) $fila['total_venta'], 2),
        ], ';');
    }

    fclose($salida);
    exit;

} catch (Throwable $e) {
    error_log('Error en api/ventas_vendedor_export.php: ' . $e->getMessage());
    if (!headers_sent()) {
        responderError('Ocurrió un error interno al exportar las ventas a CSV.', 500);
    }
}

