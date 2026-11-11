<?php
/**
 * RADAR STOCK - Funciones compartidas para auditoría de ventas por vendedor
 */

/**
 * Detecta dinámicamente si una tabla contiene la columna `local_id` (soporte multi-local).
 */
function tieneColumnaLocalTabla(PDO $pdo, string $tabla): bool {
    static $cache = [];
    if (array_key_exists($tabla, $cache)) {
        return $cache[$tabla];
    }

    $stmt = $pdo->prepare(
        'SELECT COUNT(*) 
         FROM information_schema.columns 
         WHERE table_schema = DATABASE() AND table_name = ? AND column_name = \'local_id\''
    );
    $stmt->execute([$tabla]);
    $cache[$tabla] = ((int) $stmt->fetchColumn() > 0);
    return $cache[$tabla];
}

/**
 * Obtiene el local_id del usuario autenticado desde la base de datos (nunca del cliente).
 * Retorna null si el esquema actual no utiliza multi-local.
 */
function obtenerLocalUsuarioAutenticado(PDO $pdo, int $usuarioId): ?int {
    if (!tieneColumnaLocalTabla($pdo, 'usuarios')) {
        return null;
    }

    $stmt = $pdo->prepare('SELECT local_id FROM usuarios WHERE id = ? LIMIT 1');
    $stmt->execute([$usuarioId]);
    $valor = $stmt->fetchColumn();
    return $valor !== false && $valor !== null ? (int) $valor : 1;
}

/**
 * Valida el parámetro opcional usuario_id recibido por query string.
 */
function validarParametroUsuarioId(): ?int {
    if (!isset($_GET['usuario_id']) || trim((string) $_GET['usuario_id']) === '') {
        return null;
    }

    $raw = trim((string) $_GET['usuario_id']);
    if (!ctype_digit($raw) || (int) $raw <= 0) {
        responderError("El parámetro 'usuario_id' debe ser un número entero mayor a 0.", 400);
    }

    return (int) $raw;
}

/**
 * Obtiene los datos del vendedor verificando que exista y pertenezca al mismo local del Administrador.
 */
function validarYObtenerVendedor(PDO $pdo, int $usuarioId, ?int $localIdAdmin): array {
    $sql = 'SELECT id, nombre, rol, activo FROM usuarios WHERE id = ?';
    $params = [$usuarioId];

    if ($localIdAdmin !== null && tieneColumnaLocalTabla($pdo, 'usuarios')) {
        $sql .= ' AND local_id = ?';
        $params[] = $localIdAdmin;
    }

    $sql .= ' LIMIT 1';
    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $fila = $stmt->fetch();

    if (!$fila) {
        responderError('Vendedor no encontrado.', 404);
    }

    return [
        'id'     => (int) $fila['id'],
        'nombre' => (string) $fila['nombre'],
        'rol'    => (string) $fila['rol'],
        'activo' => isset($fila['activo']) ? (int) $fila['activo'] : 1,
    ];
}

