<?php
/**
 * RADAR STOCK - Endpoint de Listado de Usuarios (Solo Administrador)
 * Método: GET
 * Retorna: Lista de usuarios (id, nombre, correo, rol)
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar que la petición sea GET
if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET.', 405);
}

// Proteger la ruta: solo rol Administrador
requerirRol(['Administrador']);

try {
    $pdo = getDBConnection();

    // Consultar usuarios omitiendo campos sensibles (password y token)
    $sql = 'SELECT id, nombre, correo, rol FROM usuarios ORDER BY nombre ASC';
    $stmt = $pdo->query($sql);
    $filas = $stmt->fetchAll();

    $usuarios = [];
    foreach ($filas as $f) {
        $usuarios[] = [
            'id'     => (int) $f['id'],
            'nombre' => $f['nombre'],
            'correo' => $f['correo'],
            'rol'    => $f['rol']
        ];
    }

    responderJson($usuarios, 200);

} catch (Exception $e) {
    error_log('Error en usuarios.php: ' . $e->getMessage());
    responderError('Error interno al obtener los usuarios.', 500);
}

