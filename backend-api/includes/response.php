<?php
/**
 * RADAR STOCK - Funciones auxiliares para respuestas JSON de la API
 */

/**
 * Envía una respuesta exitosa en formato JSON y termina la ejecución
 *
 * @param mixed $data Datos a devolver (array u objeto)
 * @param int $statusCode Código de estado HTTP (200 por defecto, 201 creado, etc.)
 */
function sendJsonResponse($data, int $statusCode = 200): void {
    http_response_code($statusCode);
    header('Content-Type: application/json; charset=UTF-8');
    
    // Si $data no es array envolver en estructura estándar si corresponde
    if (!is_array($data)) {
        $data = ['data' => $data];
    }
    
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_NUMERIC_CHECK);
    exit;
}

/**
 * Envía una respuesta de error estandarizada en formato JSON y termina la ejecución
 *
 * @param string $message Mensaje explicativo del error
 * @param int $statusCode Código de estado HTTP (400, 401, 403, 404, 500)
 * @param array $extra Datos adicionales opcionales
 */
function sendJsonError(string $message, int $statusCode = 400, array $extra = []): void {
    http_response_code($statusCode);
    header('Content-Type: application/json; charset=UTF-8');
    
    $response = array_merge([
        'success' => false,
        'error'   => $message
    ], $extra);
    
    echo json_encode($response, JSON_UNESCAPED_UNICODE);
    exit;
}

