<?php
/**
 * RADAR STOCK - Endpoint de Salud (Healthcheck)
 * Método: GET
 * Respuesta: {"estado":"ok"} o error en JSON
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';

// Validar que la petición sea GET
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET.', 405);
}

try {
    $pdo = getDBConnection();
    
    // Ejecutar consulta de comprobación
    $stmt = $pdo->query('SELECT 1');
    
    responderJson([
        'estado' => 'ok'
    ], 200);

} catch (Exception $e) {
    error_log('Error en salud.php: ' . $e->getMessage());
    responderError('Fallo en la conexión a la base de datos: ' . $e->getMessage(), 500);
}

