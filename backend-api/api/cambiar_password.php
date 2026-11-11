<?php
/**
 * RADAR STOCK - Endpoint de Cambio de Contraseña Propia
 * Acceso: Cualquier usuario autenticado (Administrador o Vendedor)
 * Método: POST o PUT
 * Cuerpo JSON: { "password_actual": "...", "password_nueva": "..." }
 * 
 * Reglas de negocio:
 * 1. Cada usuario solo puede cambiar su propia contraseña (identificado por su Bearer token).
 * 2. Verifica la contraseña actual mediante password_verify.
 * 3. La nueva contraseña debe tener un mínimo de 8 caracteres.
 * 4. Guarda la nueva clave con password_hash (bcrypt).
 * 5. Invalida inmediatamente el token actual y la sesión para forzar un nuevo inicio de sesión.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar método HTTP
$metodo = $_SERVER['REQUEST_METHOD'];
if ($metodo !== 'POST' && $metodo !== 'PUT') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones POST o PUT.', 405);
}

// Autenticar usuario con cualquier rol activo
$usuario = autenticar();
$usuarioId = (int) $usuario['id'];

// Leer datos del cuerpo JSON
$cuerpo = file_get_contents('php://input');
$datos = json_decode($cuerpo, true);

if (!is_array($datos)) {
    responderError('El cuerpo de la petición debe ser un JSON válido.', 400);
}

$passActual = isset($datos['password_actual']) ? (string) $datos['password_actual'] : '';
$passNueva = isset($datos['password_nueva']) ? (string) $datos['password_nueva'] : '';

// Validar campos obligatorios
if ($passActual === '' || $passNueva === '') {
    responderError('Debes proporcionar tanto la contraseña actual como la nueva contraseña.', 400);
}

// Validar longitud mínima de 8 caracteres
if (strlen($passNueva) < 8) {
    responderError('La nueva contraseña debe tener al menos 8 caracteres.', 400);
}

try {
    $pdo = getDBConnection();

    // Obtener la contraseña hasheada actual del usuario
    $sql = 'SELECT password FROM usuarios WHERE id = ? LIMIT 1';
    $stmt = $pdo->prepare($sql);
    $stmt->execute([$usuarioId]);
    $usuarioDb = $stmt->fetch();

    if (!$usuarioDb) {
        responderError('Usuario no encontrado.', 404);
    }

    // Verificar que la contraseña actual sea la correcta
    if (!password_verify($passActual, $usuarioDb['password'])) {
        responderError('La contraseña actual es incorrecta.', 400);
    }

    // Generar el nuevo hash seguro
    $nuevoHash = password_hash($passNueva, PASSWORD_BCRYPT);

    // Guardar el nuevo hash e invalidar el token de sesión
    $updateSql = 'UPDATE usuarios SET password = ?, token = NULL, token_expira = NULL WHERE id = ?';
    $updateStmt = $pdo->prepare($updateSql);
    $updateStmt->execute([$nuevoHash, $usuarioId]);

    responderJson([
        'estado'  => 'ok',
        'mensaje' => 'Contraseña actualizada exitosamente. Tu sesión ha finalizado; por favor inicia sesión nuevamente con tu nueva contraseña.'
    ], 200);

} catch (Exception $e) {
    error_log('Error en cambiar_password.php: ' . $e->getMessage());
    responderError('Error interno del servidor al actualizar la contraseña.', 500);
}

