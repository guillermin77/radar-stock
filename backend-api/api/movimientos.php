<?php
/**
 * RADAR STOCK - Endpoint de Historial de Movimientos de Stock
 *
 * GET (Solo Administrador): Consulta auditoría de movimientos de stock
 * (ingresos, conteos físicos y ajustes manuales) con filtros, paginación y búsqueda.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

$metodo = $_SERVER['REQUEST_METHOD'];

if ($metodo !== 'GET') {
    responderError('Método no permitido. Utilice GET.', 405);
}

// 1. Autorización: Exclusivo para Administrador (403 para otros roles, 401 si no hay token)
$admin = requerirRol(['Administrador']);

try {
    $pdo = getDBConnection();

    // 2. Validación de parámetros de consulta
    $condiciones = [];
    $parametros = [];

    // Filtro por producto_id opcional
    if (isset($_GET['producto_id']) && trim((string)$_GET['producto_id']) !== '') {
        if (!is_numeric($_GET['producto_id']) || (int) $_GET['producto_id'] <= 0) {
            responderError('El ID del producto debe ser un número entero mayor a 0.', 400);
        }
        $condiciones[] = 'm.producto_id = :producto_id';
        $parametros[':producto_id'] = (int) $_GET['producto_id'];
    }

    // Filtro de búsqueda textual 'q' (por nombre de producto o código de barras)
    if (isset($_GET['q']) && trim($_GET['q']) !== '') {
        $q = trim($_GET['q']);
        $condiciones[] = '(p.nombre_producto LIKE :q_nombre OR p.codigo_barras LIKE :q_codigo)';
        $parametros[':q_nombre'] = '%' . $q . '%';
        $parametros[':q_codigo'] = '%' . $q . '%';
    }

    // Filtro por tipo de movimiento (ingreso, conteo, ajuste)
    if (isset($_GET['tipo']) && trim($_GET['tipo']) !== '') {
        $tipo = strtolower(trim($_GET['tipo']));
        $tiposPermitidos = ['ingreso', 'conteo', 'ajuste'];
        if (!in_array($tipo, $tiposPermitidos, true)) {
            responderError('El tipo de movimiento no es válido. Opciones permitidas: ingreso, conteo, ajuste.', 400);
        }
        $condiciones[] = 'm.tipo = :tipo';
        $parametros[':tipo'] = $tipo;
    }

    // Filtros de fecha 'desde' y 'hasta' interpretados en zona horaria America/Santiago
    $tzSantiago = new DateTimeZone('America/Santiago');

    if (isset($_GET['desde']) && trim($_GET['desde']) !== '') {
        $desdeStr = trim($_GET['desde']);
        $dtDesde = DateTime::createFromFormat('Y-m-d', $desdeStr, $tzSantiago);
        if (!$dtDesde || $dtDesde->format('Y-m-d') !== $desdeStr) {
            responderError('El parámetro "desde" debe tener un formato de fecha válido (YYYY-MM-DD).', 400);
        }
        $dtDesde->setTime(0, 0, 0);
        $condiciones[] = 'm.fecha >= :desde';
        $parametros[':desde'] = $dtDesde->format('Y-m-d H:i:s');
    }

    if (isset($_GET['hasta']) && trim($_GET['hasta']) !== '') {
        $hastaStr = trim($_GET['hasta']);
        $dtHasta = DateTime::createFromFormat('Y-m-d', $hastaStr, $tzSantiago);
        if (!$dtHasta || $dtHasta->format('Y-m-d') !== $hastaStr) {
            responderError('El parámetro "hasta" debe tener un formato de fecha válido (YYYY-MM-DD).', 400);
        }
        $dtHasta->setTime(23, 59, 59);
        $condiciones[] = 'm.fecha <= :hasta';
        $parametros[':hasta'] = $dtHasta->format('Y-m-d H:i:s');
    }

    // Validar coherencia de rango si ambos están presentes
    if (isset($dtDesde) && isset($dtHasta) && $dtDesde > $dtHasta) {
        responderError('La fecha de inicio ("desde") no puede ser posterior a la fecha final ("hasta").', 400);
    }

    // Soporte multi-local: si existe la columna local_id en movimientos_stock, filtrar estrictamente por el del usuario
    $colLocalQuery = $pdo->query(
        "SELECT COUNT(*) FROM information_schema.columns 
         WHERE table_schema = DATABASE() AND table_name = 'movimientos_stock' AND column_name = 'local_id'"
    );
    $tieneLocal = ($colLocalQuery && (int)$colLocalQuery->fetchColumn() > 0);
    if ($tieneLocal) {
        $localUsuario = isset($admin['local_id']) ? (int)$admin['local_id'] : 1;
        $condiciones[] = 'm.local_id = :local_id';
        $parametros[':local_id'] = $localUsuario;
    }

    // Paginación
    $pagina = isset($_GET['pagina']) ? max(1, (int)$_GET['pagina']) : 1;
    $porPagina = isset($_GET['por_pagina']) ? min(100, max(1, (int)$_GET['por_pagina'])) : 30;
    $offset = ($pagina - 1) * $porPagina;

    // Construir cláusula WHERE
    $whereSql = !empty($condiciones) ? ' WHERE ' . implode(' AND ', $condiciones) : '';

    // 3. Conteo total de registros coincidentes
    $sqlTotal = "SELECT COUNT(*) 
                 FROM movimientos_stock m
                 INNER JOIN productos p ON m.producto_id = p.id
                 LEFT JOIN usuarios u ON m.usuario_id = u.id
                 {$whereSql}";
    $stmtTotal = $pdo->prepare($sqlTotal);
    $stmtTotal->execute($parametros);
    $totalRegistros = (int) $stmtTotal->fetchColumn();

    // 4. Consulta de datos paginados
    $sqlDatos = "SELECT 
                    m.id,
                    DATE_FORMAT(m.fecha, '%Y-%m-%d %H:%i:%s') AS fecha,
                    m.producto_id,
                    p.nombre_producto,
                    p.codigo_barras,
                    COALESCE(u.nombre, 'Sistema') AS usuario_nombre,
                    m.tipo,
                    m.stock_antes,
                    m.stock_despues,
                    (m.stock_despues - m.stock_antes) AS diferencia
                 FROM movimientos_stock m
                 INNER JOIN productos p ON m.producto_id = p.id
                 LEFT JOIN usuarios u ON m.usuario_id = u.id
                 {$whereSql}
                 ORDER BY m.fecha DESC, m.id DESC
                 LIMIT :limit OFFSET :offset";

    $stmtDatos = $pdo->prepare($sqlDatos);

    // Asignar parámetros con tipado explícito para LIMIT y OFFSET
    foreach ($parametros as $clave => $valor) {
        $stmtDatos->bindValue($clave, $valor);
    }
    $stmtDatos->bindValue(':limit', $porPagina, PDO::PARAM_INT);
    $stmtDatos->bindValue(':offset', $offset, PDO::PARAM_INT);
    $stmtDatos->execute();

    $filas = $stmtDatos->fetchAll();

    // 5. Formatear resultados
    $movimientos = [];
    foreach ($filas as $fila) {
        $movimientos[] = [
            'id'              => (int) $fila['id'],
            'fecha'           => (string) $fila['fecha'],
            'producto_id'     => (int) $fila['producto_id'],
            'nombre_producto' => (string) $fila['nombre_producto'],
            'codigo_barras'   => $fila['codigo_barras'] !== null ? (string) $fila['codigo_barras'] : null,
            'usuario'         => (string) $fila['usuario_nombre'],
            'tipo'            => (string) $fila['tipo'],
            'stock_antes'     => (int) $fila['stock_antes'],
            'stock_despues'   => (int) $fila['stock_despues'],
            'diferencia'      => (int) $fila['diferencia']
        ];
    }

    $totalPaginas = $porPagina > 0 ? (int) ceil($totalRegistros / $porPagina) : 1;

    responderJson([
        'pagina'        => $pagina,
        'por_pagina'    => $porPagina,
        'total'         => $totalRegistros,
        'total_paginas' => $totalPaginas,
        'movimientos'   => $movimientos
    ], 200);

} catch (Throwable $e) {
    error_log('Error en movimientos.php: ' . $e->getMessage());
    responderError('Ocurrió un error interno al consultar el historial de movimientos.', 500);
}

