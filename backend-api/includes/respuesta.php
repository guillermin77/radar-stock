<?php
/**
 * RADAR STOCK - Funciones de Respuesta JSON
 */

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
 * Envía un mensaje de error en JSON
 *
 * @param string $mensaje Descripción del error
 * @param int $codigo Código de estado HTTP
 */
function responderError(string $mensaje, int $codigo = 500): void {
    responderJson([
        'estado'  => 'error',
        'mensaje' => $mensaje
    ], $codigo);
}

