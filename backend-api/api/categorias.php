<?php
/**
 * RADAR STOCK - Endpoint de Categorías
 * Método: GET (Cualquier rol)
 * Retorna: Lista ordenada de categorías que incluye las 6 categorías base más las creadas en productos, sin duplicados.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar que la petición sea GET
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET.', 405);
}

// Requiere sesión activa con cualquier rol
autenticar();

// Categorías base obligatorias del negocio
$categoriasBase = [
    'Bebidas',
    'Lácteos',
    'Abarrotes',
    'Panadería',
    'Aseo',
    'Snacks'
];

try {
    $pdo = getDBConnection();

    // Consultar categorías registradas en productos que no sean nulas ni vacías
    $sql = 'SELECT DISTINCT categoria 
            FROM productos 
            WHERE categoria IS NOT NULL AND TRIM(categoria) != \'\'';
    $stmt = $pdo->query($sql);
    $categoriasBD = $stmt->fetchAll(PDO::FETCH_COLUMN);

    // Unir base y BD sin duplicados
    $combinadas = array_merge($categoriasBase, $categoriasBD);

    // Normalizar duplicados sin distinción de mayúsculas/minúsculas pero conservando formato legible
    $unicas = [];
    $vistos = [];
    foreach ($combinadas as $cat) {
        $catTrim = trim($cat);
        $clave = mb_strtolower($catTrim, 'UTF-8');
        if (!isset($vistos[$clave])) {
            $vistos[$clave] = true;
            $unicas[] = $catTrim;
        }
    }

    // Ordenar alfabéticamente
    natcasesort($unicas);
    $resultado = array_values($unicas);

    responderJson($resultado, 200);

} catch (Exception $e) {
    error_log('Error en categorias.php: ' . $e->getMessage());
    responderError('Error interno al obtener las categorías: ' . $e->getMessage(), 500);
}

