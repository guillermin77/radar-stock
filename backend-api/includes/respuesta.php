<?php
/**
 * RADAR STOCK - Funciones de Respuesta JSON
 */

// Mantener display_errors desactivado para no romper el formato JSON
ini_set('display_errors', '0');

/**
 * Envía una respuesta estructurada en formato JSON con cabecera UTF-8 y código HTTP indicado
 *
 * @param mixed $datos Datos o contenido a serializar (array, objeto o primitivo)
 * @param int $codigo Código de estado HTTP (200 por defecto, 400, 404, 500, etc.)
 */
function responderJson($datos, int $codigo = 200): void {
    http_response_code($codigo);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($datos, JSON_UNESCAPED_UNICODE | JSON_NUMERIC_CHECK);
    exit;
}

/**
 * Envía un mensaje de error en JSON y lo registra en log_path
 *
 * @param string $mensaje Descripción del error
 * @param int $codigo Código de estado HTTP
 */
function responderError(string $mensaje, int $codigo = 500): void {
    // Registrar el error en log_path
    $logPath = defined('LOG_PATH') ? LOG_PATH : __DIR__ . '/../error.log';
    $fecha = date('Y-m-d H:i:s');
    $metodo = $_SERVER['REQUEST_METHOD'] ?? 'CLI';
    $uri = $_SERVER['REQUEST_URI'] ?? '';

    if ($codigo >= 400) {
        $lineaLog = "[$fecha] [$metodo $uri] HTTP $codigo: $mensaje" . PHP_EOL;
        @error_log($lineaLog, 3, $logPath);
    }

    // En entorno de producción, los errores 500 no exponen detalles sensibles de la base de datos
    $mensajeCliente = $mensaje;
    if (defined('ENTORNO') && ENTORNO === 'produccion' && $codigo >= 500) {
        $mensajeCliente = 'Error interno del servidor. Intente nuevamente más tarde.';
    }

    responderJson([
        'estado'  => 'error',
        'mensaje' => $mensajeCliente
    ], $codigo);
}
