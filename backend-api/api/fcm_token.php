<?php
/**
 * RADAR STOCK - Endpoint de Registro de Token FCM del Dispositivo
 * Método: POST
 * Cuerpo: JSON { "fcm_token": "..." }
 * Acceso: Cualquier usuario autenticado (se guarda para notificaciones push)
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar que la petición sea POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones POST.', 405);
}

// Autenticar usuario
$usuario = autenticar();
$usuarioId = (int) $usuario['id'];

// Leer cuerpo JSON
$cuerpo = file_get_contents('php://input');
$datos = json_decode($cuerpo, true);

if (!is_array($datos) || empty(trim($datos['fcm_token'] ?? ''))) {
    responderError('Se requiere el parámetro fcm_token.', 400);
}

$fcmToken = trim($datos['fcm_token']);

try {
    $pdo = getDBConnection();

    // Guardar el token FCM en la fila del usuario autenticado
    $stmt = $pdo->prepare('UPDATE usuarios SET fcm_token = ? WHERE id = ?');
    $stmt->execute([$fcmToken, $usuarioId]);

    responderJson([
        'estado'  => 'ok',
        'mensaje' => 'Token FCM registrado exitosamente.'
    ], 200);

} catch (Exception $e) {
    error_log('Error en fcm_token.php: ' . $e->getMessage());
    responderError('Error interno al registrar el token del dispositivo.', 500);
}

