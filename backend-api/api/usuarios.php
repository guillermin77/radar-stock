<?php
/**
 * RADAR STOCK - Endpoint de Gestión de Usuarios
 * Acceso: EXCLUSIVO para rol Administrador (Token Bearer verificado en servidor)
 * 
 * Métodos soportados:
 * - GET: Listar usuarios (id, nombre, correo, rol, activo, fecha_creacion). Nunca expone password ni token.
 * - POST: Crear nuevo usuario (nombre, correo, password >= 8 chars, rol [Administrador, Vendedor]).
 * - PUT: 
 *     a) Edición general: nombre, correo, rol.
 *     b) accion = 'activar' / 'desactivar': Cambia estado activo. Al desactivar borra token.
 *     c) accion = 'resetear_password': Asigna nueva clave >= 8 chars y borra token.
 * 
 * Reglas de negocio:
 * 1. Un administrador no puede desactivarse ni cambiarse el rol a sí mismo.
 * 2. Siempre debe quedar al menos un Administrador activo en el sistema.
 * 3. Los usuarios nunca se eliminan de la base de datos para preservar el historial de ventas.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

// Validar método HTTP
$metodo = $_SERVER['REQUEST_METHOD'];
if (!in_array($metodo, ['GET', 'POST', 'PUT'], true)) {
    if ($metodo === 'DELETE') {
        responderError('Método no permitido. Los usuarios no se eliminan para conservar el historial de ventas; deben desactivarse con PUT (accion = "desactivar").', 405);
    }
    responderError('Método no permitido. Este endpoint solo acepta peticiones GET, POST y PUT.', 405);
}

// Proteger la ruta: solo rol Administrador autenticado
$adminActual = requerirRol(['Administrador']);
$adminId = (int) $adminActual['id'];

try {
    $pdo = getDBConnection();

    // ==========================================================
    // GET: Listado de usuarios
    // ==========================================================
    if ($metodo === 'GET') {
        $sql = 'SELECT id, nombre, correo, rol, activo, fecha_creacion 
                FROM usuarios 
                ORDER BY nombre ASC';
        $stmt = $pdo->query($sql);
        $filas = $stmt->fetchAll();

        $usuarios = [];
        foreach ($filas as $f) {
            $usuarios[] = [
                'id'             => (int) $f['id'],
                'nombre'         => $f['nombre'],
                'correo'         => $f['correo'],
                'rol'            => $f['rol'],
                'activo'         => isset($f['activo']) ? (int) $f['activo'] : 1,
                'fecha_creacion' => $f['fecha_creacion'] ?? null
            ];
        }

        responderJson($usuarios, 200);
    }

    // ==========================================================
    // POST: Creación de usuario
    // ==========================================================
    if ($metodo === 'POST') {
        $cuerpo = file_get_contents('php://input');
        $datos = json_decode($cuerpo, true);

        if (!is_array($datos)) {
            responderError('El cuerpo de la petición debe ser un JSON válido.', 400);
        }

        $nombre = isset($datos['nombre']) ? trim($datos['nombre']) : '';
        $correo = isset($datos['correo']) ? strtolower(trim($datos['correo'])) : '';
        $password = isset($datos['password']) ? (string) $datos['password'] : '';
        $rolRaw = isset($datos['rol']) ? trim($datos['rol']) : '';

        // Validar nombre
        if ($nombre === '') {
            responderError('El nombre del usuario es obligatorio.', 400);
        }

        // Validar formato de correo
        if ($correo === '' || !filter_var($correo, FILTER_VALIDATE_EMAIL)) {
            responderError('El formato del correo electrónico no es válido.', 400);
        }

        // Validar longitud de contraseña (mínimo 8 caracteres)
        if (strlen($password) < 8) {
            responderError('La contraseña debe tener al menos 8 caracteres.', 400);
        }

        // Normalizar y validar rol
        $rol = '';
        if (strcasecmp($rolRaw, 'Administrador') === 0) {
            $rol = 'Administrador';
        } elseif (strcasecmp($rolRaw, 'Vendedor') === 0) {
            $rol = 'Vendedor';
        } else {
            responderError('El rol especificado no es válido. Opciones permitidas: Administrador, Vendedor.', 400);
        }

        // Verificar unicidad del correo electrónico
        $checkStmt = $pdo->prepare('SELECT id FROM usuarios WHERE correo = ? LIMIT 1');
        $checkStmt->execute([$correo]);
        if ($checkStmt->fetch()) {
            responderError('El correo electrónico ya se encuentra registrado.', 409);
        }

        // Hashear contraseña con bcrypt
        $passwordHash = password_hash($password, PASSWORD_BCRYPT);

        // Insertar usuario activo
        $insertSql = 'INSERT INTO usuarios (nombre, correo, password, rol, activo, fecha_creacion) 
                      VALUES (?, ?, ?, ?, 1, NOW())';
        $insertStmt = $pdo->prepare($insertSql);
        $insertStmt->execute([$nombre, $correo, $passwordHash, $rol]);

        $nuevoId = (int) $pdo->lastInsertId();

        responderJson([
            'estado'  => 'ok',
            'mensaje' => 'Usuario creado exitosamente.',
            'usuario' => [
                'id'             => $nuevoId,
                'nombre'         => $nombre,
                'correo'         => $correo,
                'rol'            => $rol,
                'activo'         => 1,
                'fecha_creacion' => date('Y-m-d H:i:s')
            ]
        ], 201);
    }

    // ==========================================================
    // PUT: Edición, Activar / Desactivar, Resetear Password
    // ==========================================================
    if ($metodo === 'PUT') {
        $cuerpo = file_get_contents('php://input');
        $datos = json_decode($cuerpo, true);

        if (!is_array($datos)) {
            responderError('El cuerpo de la petición debe ser un JSON válido.', 400);
        }

        // Obtener ID del usuario destino
        $idTarget = isset($datos['id']) ? (int) $datos['id'] : (isset($_GET['id']) ? (int) $_GET['id'] : 0);
        if ($idTarget <= 0) {
            responderError('ID de usuario inválido o no especificado.', 400);
        }

        // Verificar existencia del usuario destino
        $targetStmt = $pdo->prepare('SELECT id, nombre, correo, rol, activo FROM usuarios WHERE id = ? LIMIT 1');
        $targetStmt->execute([$idTarget]);
        $targetUser = $targetStmt->fetch();

        if (!$targetUser) {
            responderError('Usuario no encontrado.', 404);
        }

        $targetRol = $targetUser['rol'];
        $targetActivo = isset($targetUser['activo']) ? (int) $targetUser['activo'] : 1;
        $accion = isset($datos['accion']) ? strtolower(trim($datos['accion'])) : '';

        // Helper para contar otros administradores activos
        $contarOtrosAdminsActivos = function() use ($pdo, $idTarget): int {
            $stmt = $pdo->prepare("SELECT COUNT(*) FROM usuarios WHERE rol = 'Administrador' AND activo = 1 AND id != ?");
            $stmt->execute([$idTarget]);
            return (int) $stmt->fetchColumn();
        };

        // ------------------------------------------------------
        // SUB-CASO 1: Desactivar usuario
        // ------------------------------------------------------
        if ($accion === 'desactivar') {
            if ($idTarget === $adminId) {
                responderError('No puedes desactivar tu propia cuenta de administrador.', 403);
            }

            if ($targetRol === 'Administrador' && $targetActivo === 1) {
                if ($contarOtrosAdminsActivos() === 0) {
                    responderError('No se puede desactivar al único administrador activo del sistema.', 400);
                }
            }

            // Desactivar e invalidar sesión
            $upd = $pdo->prepare('UPDATE usuarios SET activo = 0, token = NULL, token_expira = NULL WHERE id = ?');
            $upd->execute([$idTarget]);

            responderJson([
                'estado'  => 'ok',
                'mensaje' => 'Usuario desactivado exitosamente. Se ha cerrado su sesión.'
            ], 200);
        }

        // ------------------------------------------------------
        // SUB-CASO 2: Activar usuario
        // ------------------------------------------------------
        if ($accion === 'activar') {
            $upd = $pdo->prepare('UPDATE usuarios SET activo = 1 WHERE id = ?');
            $upd->execute([$idTarget]);

            responderJson([
                'estado'  => 'ok',
                'mensaje' => 'Usuario activado exitosamente.'
            ], 200);
        }

        // ------------------------------------------------------
        // SUB-CASO 3: Resetear contraseña por el Administrador
        // ------------------------------------------------------
        if ($accion === 'resetear_password') {
            $nuevaClave = isset($datos['password_nueva']) 
                ? (string) $datos['password_nueva'] 
                : (isset($datos['password']) ? (string) $datos['password'] : '');

            if (strlen($nuevaClave) < 8) {
                responderError('La nueva contraseña debe tener al menos 8 caracteres.', 400);
            }

            $hashClave = password_hash($nuevaClave, PASSWORD_BCRYPT);

            // Actualizar contraseña e invalidar token del usuario
            $upd = $pdo->prepare('UPDATE usuarios SET password = ?, token = NULL, token_expira = NULL WHERE id = ?');
            $upd->execute([$hashClave, $idTarget]);

            responderJson([
                'estado'  => 'ok',
                'mensaje' => 'Contraseña reseteada exitosamente. Se ha invalidado la sesión del usuario.'
            ], 200);
        }

        // ------------------------------------------------------
        // SUB-CASO 4: Edición de nombre, correo y rol
        // ------------------------------------------------------
        if ($accion === '' || $accion === 'editar') {
            $nombre = isset($datos['nombre']) ? trim($datos['nombre']) : $targetUser['nombre'];
            $correo = isset($datos['correo']) ? strtolower(trim($datos['correo'])) : strtolower($targetUser['correo']);
            $rolRaw = isset($datos['rol']) ? trim($datos['rol']) : $targetRol;

            if ($nombre === '') {
                responderError('El nombre del usuario no puede estar vacío.', 400);
            }

            if ($correo === '' || !filter_var($correo, FILTER_VALIDATE_EMAIL)) {
                responderError('El formato del correo electrónico no es válido.', 400);
            }

            // Normalizar y validar rol
            $nuevoRol = '';
            if (strcasecmp($rolRaw, 'Administrador') === 0) {
                $nuevoRol = 'Administrador';
            } elseif (strcasecmp($rolRaw, 'Vendedor') === 0) {
                $nuevoRol = 'Vendedor';
            } else {
                responderError('El rol especificado no es válido. Opciones permitidas: Administrador, Vendedor.', 400);
            }

            // Regla: no quitarse el rol de Administrador a sí mismo
            if ($idTarget === $adminId && $nuevoRol !== 'Administrador') {
                responderError('No puedes quitarte el rol de Administrador a ti mismo.', 403);
            }

            // Regla: no dejar el sistema sin al menos un administrador activo
            if ($targetRol === 'Administrador' && $nuevoRol !== 'Administrador' && $targetActivo === 1) {
                if ($contarOtrosAdminsActivos() === 0) {
                    responderError('No se puede cambiar el rol del único administrador activo del sistema.', 400);
                }
            }

            // Verificar si el correo ya pertenece a otro usuario
            $checkCorreo = $pdo->prepare('SELECT id FROM usuarios WHERE correo = ? AND id != ? LIMIT 1');
            $checkCorreo->execute([$correo, $idTarget]);
            if ($checkCorreo->fetch()) {
                responderError('El correo electrónico ya se encuentra registrado por otro usuario.', 409);
            }

            // Si cambió de rol, invalidar su sesión actual por seguridad
            if ($nuevoRol !== $targetRol) {
                $upd = $pdo->prepare('UPDATE usuarios SET nombre = ?, correo = ?, rol = ?, token = NULL, token_expira = NULL WHERE id = ?');
                $upd->execute([$nombre, $correo, $nuevoRol, $idTarget]);
            } else {
                $upd = $pdo->prepare('UPDATE usuarios SET nombre = ?, correo = ?, rol = ? WHERE id = ?');
                $upd->execute([$nombre, $correo, $nuevoRol, $idTarget]);
            }

            responderJson([
                'estado'  => 'ok',
                'mensaje' => 'Usuario actualizado exitosamente.',
                'usuario' => [
                    'id'     => $idTarget,
                    'nombre' => $nombre,
                    'correo' => $correo,
                    'rol'    => $nuevoRol,
                    'activo' => $targetActivo
                ]
            ], 200);
        }

        responderError("Acción '$accion' no reconocida. Opciones permitidas: activar, desactivar, resetear_password o editar.", 400);
    }

} catch (Exception $e) {
    error_log('Error en api/usuarios.php: ' . $e->getMessage());
    responderError('Error interno del servidor al procesar la solicitud de usuarios.', 500);
}
