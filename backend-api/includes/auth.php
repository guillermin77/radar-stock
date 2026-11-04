<?php
/**
 * RADAR STOCK - Funciones de Autenticación y Autorización
 */

require_once __DIR__ . '/db.php';
require_once __DIR__ . '/respuesta.php';

/**
 * Obtiene el valor del encabezado Authorization desde la petición HTTP
 *
 * @return string|null
 */
function obtenerAuthorizationHeader(): ?string {
    if (!empty($_SERVER['HTTP_AUTHORIZATION'])) {
        return trim($_SERVER['HTTP_AUTHORIZATION']);
    }
    if (!empty($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
        return trim($_SERVER['REDIRECT_HTTP_AUTHORIZATION']);
    }
    if (function_exists('apache_request_headers')) {
        $headers = apache_request_headers();
        $headers = array_change_key_case($headers, CASE_LOWER);
        if (!empty($headers['authorization'])) {
            return trim($headers['authorization']);
        }
    }
    return null;
}

/**
 * Extrae el Bearer token del encabezado Authorization
 *
 * @return string|null
 */
function obtenerBearerToken(): ?string {
    $header = obtenerAuthorizationHeader();
    if (!empty($header)) {
        if (preg_match('/Bearer\s+(\S+)/i', $header, $matches)) {
            return $matches[1];
        }
    }
    return null;
}

/**
 * Valida el token recibido en la cabecera Authorization (Bearer).
 * Verifica que exista en la base de datos y que no haya expirado.
 * Si es inválido o falta, responde HTTP 401 y termina la ejecución.
 *
 * @return array Datos del usuario autenticado (id, nombre, correo, rol)
 */
function autenticar(): array {
    $token = obtenerBearerToken();

    if (empty($token)) {
        responderError('No autorizado: Token de autorización no proporcionado.', 401);
    }

    try {
        $pdo = getDBConnection();
        $sql = 'SELECT id, nombre, correo, rol, token_expira 
                FROM usuarios 
                WHERE token = ? 
                LIMIT 1';
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$token]);
        $usuario = $stmt->fetch();

        if (!$usuario) {
            responderError('No autorizado: Token inválido.', 401);
        }

        // Verificar si el token ya expiró
        if (empty($usuario['token_expira']) || strtotime($usuario['token_expira']) <= time()) {
            responderError('No autorizado: La sesión ha expirado.', 401);
        }

        return [
            'id'     => (int) $usuario['id'],
            'nombre' => $usuario['nombre'],
            'correo' => $usuario['correo'],
            'rol'    => $usuario['rol']
        ];

    } catch (Exception $e) {
        error_log('Error en autenticar(): ' . $e->getMessage());
        responderError('Error interno al verificar la sesión.', 500);
    }
}

/**
 * Comprueba que el usuario tenga uno de los roles permitidos.
 * Si no está permitido, responde HTTP 403 y termina la ejecución.
 *
 * @param array $rolesPermitidos Lista de roles permitidos (ej. ['Administrador'])
 * @param array|null $usuario Opcional, datos del usuario. Si es null, llama a autenticar()
 * @return array Datos del usuario autorizado
 */
function requerirRol(array $rolesPermitidos, ?array $usuario = null): array {
    if ($usuario === null) {
        $usuario = autenticar();
    }

    if (!in_array($usuario['rol'], $rolesPermitidos, true)) {
        responderError('Acceso denegado: No tienes permisos para acceder a este recurso.', 403);
    }

    return $usuario;
}

