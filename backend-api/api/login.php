<?php
/**
 * RADAR STOCK - Endpoint de Inicio de Sesión
 * Método: POST
 * Cuerpo: JSON { "correo": "...", "password": "..." }
 * Retorna: { "token": "...", "usuario": { "id": 1, "nombre": "...", "rol": "..." } }
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';

// Validar que la petición sea POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones POST.', 405);
}

// Leer cuerpo JSON
$cuerpo = file_get_contents('php://input');
$datos = json_decode($cuerpo, true);

$correo = isset($datos['correo']) ? trim($datos['correo']) : '';
$password = isset($datos['password']) ? (string) $datos['password'] : '';

// Si faltan datos básicos, responder con el mismo mensaje genérico 401
if (empty($correo) || empty($password)) {
    responderError('Correo o contraseña incorrectos.', 401);
}

try {
    $pdo = getDBConnection();

    // Buscar al usuario por correo incluyendo campo activo
    $sql = 'SELECT id, nombre, correo, password, rol, activo FROM usuarios WHERE correo = ? LIMIT 1';
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$correo]);
    $usuario = $stmt->fetch();

    // Validar existencia, contraseña con password_verify y estado activo
    // Si no existe, la contraseña no coincide o está inactivo (activo = 0), se responde con el mismo mensaje genérico
    if (!$usuario || !password_verify($password, $usuario['password']) || (isset($usuario['activo']) && (int) $usuario['activo'] === 0)) {
        responderError('Correo o contraseña incorrectos.', 401);
    }

    // Generar token criptográficamente seguro (64 caracteres hex)
    $token = bin2hex(random_bytes(32));

    // Vencimiento de 12 horas a partir de ahora
    $tokenExpira = date('Y-m-d H:i:s', strtotime('+12 hours'));

    // Guardar el token y su expiración en la base de datos
    $updateSql = 'UPDATE usuarios SET token = ?, token_expira = ? WHERE id = ?';
    $updateStmt = $pdo->prepare($updateSql);
    $updateStmt->execute([$token, $tokenExpira, $usuario['id']]);

    // Responder con token y datos de usuario (sin password ni token_expira)
    responderJson([
        'token'   => $token,
        'usuario' => [
            'id'     => (int) $usuario['id'],
            'nombre' => $usuario['nombre'],
            'rol'    => $usuario['rol']
        ]
    ], 200);

} catch (Exception $e) {
    error_log('Error en login.php: ' . $e->getMessage());
    responderError('Error interno del servidor al procesar el inicio de sesión.', 500);
}
