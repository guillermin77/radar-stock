<?php
/**
 * RADAR STOCK - Endpoint de Alertas de Stock y Vencimiento
 * Método: GET (Cualquier rol autenticado: Administrador, Vendedor, Colaborador)
 * 
 * Parámetros opcionales:
 * - tipo: 'agotado', 'critico', 'bajo', 'por_vencer', 'vencido'
 * - pagina: entero >= 1 (por defecto 1)
 * - limite: entero entre 1 y 100 (por defecto 50)
 * - resumen: si es 1, devuelve únicamente los conteos {agotados, criticos, bajos, por_vencer, vencidos, total}
 * 
 * Reglas de negocio:
 * 1. Calcula las alertas mediante consultas SQL optimizadas con fecha America/Santiago.
 * 2. Cada producto genera UNA sola alerta de stock (la más grave: agotado > critico > bajo)
 *    y aparte una alerta de vencimiento si corresponde (vencido o por_vencer).
 * 3. Orden por gravedad: vencido, agotado, critico, por_vencer, bajo; luego por nombre_producto.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar método HTTP
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET.', 405);
}

// Autenticar usuario con cualquier rol activo
$usuario = autenticar();

// Obtener fecha actual en zona horaria America/Santiago
$tz = new DateTimeZone('America/Santiago');
$hoy = (new DateTime('today', $tz))->format('Y-m-d');

// Parámetros de la consulta
$tipoFiltro = isset($_GET['tipo']) ? strtolower(trim($_GET['tipo'])) : '';
$esResumen = isset($_GET['resumen']) && $_GET['resumen'] === '1';
$pagina = isset($_GET['pagina']) ? max(1, (int) $_GET['pagina']) : 1;
$limite = isset($_GET['limite']) ? min(100, max(1, (int) $_GET['limite'])) : 50;
$offset = ($pagina - 1) * $limite;

// Validar tipo si fue proporcionado
$tiposPermitidos = ['agotado', 'critico', 'bajo', 'por_vencer', 'vencido'];
if ($tipoFiltro !== '' && !in_array($tipoFiltro, $tiposPermitidos, true)) {
    responderError("El tipo de alerta '$tipoFiltro' no es válido. Opciones: " . implode(', ', $tiposPermitidos), 400);
}

try {
    $pdo = getDBConnection();
    $hoySql = $pdo->quote($hoy);

    // ==========================================================
    // Subconsulta SQL que consolida Alertas de Stock y Vencimiento
    // ==========================================================
    // Regla de Stock (la más grave):
    // - agotado: stock_actual = 0
    // - critico: stock_actual > 0 AND stock_deseado > 0 AND stock_actual <= (stock_deseado * 0.25)
    // - bajo: stock_actual <= stock_minimo (si no cayó en las anteriores)
    // Regla de Vencimiento:
    // - solo productos con stock_actual > 0 y fecha_vencimiento no nula
    // - vencido: fecha_vencimiento < hoy
    // - por_vencer: fecha_vencimiento >= hoy y faltan 15 días o menos
    $subconsultaSql = "
        SELECT 
            id AS producto_id,
            nombre_producto,
            CASE 
                WHEN stock_actual = 0 THEN 'agotado'
                WHEN stock_actual > 0 AND stock_deseado > 0 AND stock_actual <= (stock_deseado * 0.25) THEN 'critico'
                WHEN stock_actual <= stock_minimo THEN 'bajo'
                ELSE NULL
            END AS tipo,
            stock_actual,
            stock_minimo,
            stock_deseado,
            fecha_vencimiento,
            NULL AS dias_para_vencer
        FROM productos
        WHERE stock_actual = 0 
           OR (stock_actual > 0 AND stock_deseado > 0 AND stock_actual <= (stock_deseado * 0.25))
           OR (stock_actual <= stock_minimo)

        UNION ALL

        SELECT 
            id AS producto_id,
            nombre_producto,
            CASE 
                WHEN fecha_vencimiento < $hoySql THEN 'vencido'
                WHEN fecha_vencimiento >= $hoySql AND DATEDIFF(fecha_vencimiento, $hoySql) <= 15 THEN 'por_vencer'
                ELSE NULL
            END AS tipo,
            stock_actual,
            stock_minimo,
            stock_deseado,
            fecha_vencimiento,
            DATEDIFF(fecha_vencimiento, $hoySql) AS dias_para_vencer
        FROM productos
        WHERE stock_actual > 0 
          AND fecha_vencimiento IS NOT NULL
          AND (fecha_vencimiento < $hoySql OR (fecha_vencimiento >= $hoySql AND DATEDIFF(fecha_vencimiento, $hoySql) <= 15))
    ";

    // ==========================================================
    // CASO 1: Si resumen=1, devuelve conteos consolidados
    // ==========================================================
    if ($esResumen) {
        $sqlConteos = "
            SELECT 
                COALESCE(SUM(CASE WHEN tipo = 'agotado' THEN 1 ELSE 0 END), 0) AS agotados,
                COALESCE(SUM(CASE WHEN tipo = 'critico' THEN 1 ELSE 0 END), 0) AS criticos,
                COALESCE(SUM(CASE WHEN tipo = 'bajo' THEN 1 ELSE 0 END), 0) AS bajos,
                COALESCE(SUM(CASE WHEN tipo = 'por_vencer' THEN 1 ELSE 0 END), 0) AS por_vencer,
                COALESCE(SUM(CASE WHEN tipo = 'vencido' THEN 1 ELSE 0 END), 0) AS vencidos,
                COUNT(*) AS total
            FROM ($subconsultaSql) AS todas_alertas
            WHERE tipo IS NOT NULL
        ";

        $stmt = $pdo->query($sqlConteos);
        $conteos = $stmt->fetch();

        responderJson([
            'agotados'   => (int) ($conteos['agotados'] ?? 0),
            'criticos'   => (int) ($conteos['criticos'] ?? 0),
            'bajos'      => (int) ($conteos['bajos'] ?? 0),
            'por_vencer' => (int) ($conteos['por_vencer'] ?? 0),
            'vencidos'   => (int) ($conteos['vencidos'] ?? 0),
            'total'      => (int) ($conteos['total'] ?? 0)
        ], 200);
    }

    // ==========================================================
    // CASO 2: Listado de alertas paginado y ordenado por gravedad
    // ==========================================================
    $clausulaWhere = 'WHERE tipo IS NOT NULL';

    if ($tipoFiltro !== '') {
        $tipoSql = $pdo->quote($tipoFiltro);
        $clausulaWhere .= " AND tipo = $tipoSql";
    }

    $limiteInt = (int) $limite;
    $offsetInt = (int) $offset;

    $sqlLista = "
        SELECT 
            producto_id,
            nombre_producto,
            tipo,
            stock_actual,
            stock_minimo,
            stock_deseado,
            fecha_vencimiento,
            dias_para_vencer
        FROM ($subconsultaSql) AS todas_alertas
        $clausulaWhere
        ORDER BY 
            CASE tipo
                WHEN 'vencido' THEN 1
                WHEN 'agotado' THEN 2
                WHEN 'critico' THEN 3
                WHEN 'por_vencer' THEN 4
                WHEN 'bajo' THEN 5
                ELSE 6
            END ASC,
            nombre_producto ASC
        LIMIT $limiteInt OFFSET $offsetInt
    ";

    $stmt = $pdo->query($sqlLista);
    $filas = $stmt->fetchAll();

    $alertas = [];
    foreach ($filas as $fila) {
        $tipo = $fila['tipo'];
        $nombre = $fila['nombre_producto'];
        $stockActual = (int) $fila['stock_actual'];
        $stockMinimo = (int) $fila['stock_minimo'];
        $stockDeseado = (int) $fila['stock_deseado'];
        $fechaVenc = $fila['fecha_vencimiento'];
        $diasVencer = $fila['dias_para_vencer'] !== null ? (int) $fila['dias_para_vencer'] : null;

        // Construir mensaje según la regla
        $fechaFormateada = !empty($fechaVenc) ? (new DateTime($fechaVenc))->format('d-m-Y') : '';
        $mensaje = '';

        switch ($tipo) {
            case 'agotado':
                $mensaje = "Sin stock: $nombre se agotó.";
                break;
            case 'critico':
                $mensaje = "Stock crítico: $nombre está por agotarse ($stockActual unidades).";
                break;
            case 'bajo':
                $mensaje = "Stock bajo: $nombre tiene $stockActual unidades (mínimo $stockMinimo). Conviene reponer.";
                break;
            case 'por_vencer':
                if ($diasVencer === 0) {
                    $mensaje = "Por vencer: $nombre vence hoy ($fechaFormateada).";
                } else {
                    $mensaje = "Por vencer: $nombre vence en $diasVencer días ($fechaFormateada).";
                }
                break;
            case 'vencido':
                $mensaje = "Vencido: $nombre venció el $fechaFormateada. Retíralo de la estantería.";
                break;
            default:
                $mensaje = "$nombre requiere atención.";
        }

        $alertas[] = [
            'producto_id'       => (int) $fila['producto_id'],
            'nombre_producto'   => $nombre,
            'tipo'              => $tipo,
            'mensaje'           => $mensaje,
            'stock_actual'      => $stockActual,
            'stock_minimo'      => $stockMinimo,
            'stock_deseado'     => $stockDeseado,
            'fecha_vencimiento' => $fechaVenc ?: null,
            'dias_para_vencer'  => $diasVencer
        ];
    }

    responderJson($alertas, 200);

} catch (Exception $e) {
    error_log('Error en api/alertas.php: ' . $e->getMessage());
    responderError('Error interno al obtener las alertas.', 500);
}
