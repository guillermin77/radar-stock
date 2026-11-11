<?php
/**
 * RADAR STOCK - Endpoint CRUD de Productos
 *
 * GET    (Cualquier rol): Lista de productos o producto por id / código de barras.
 *                         Parámetros opcionales: id, codigo, buscar, categoria, pagina, limite.
 * POST   (Solo Administrador): Crea un nuevo producto.
 * PUT    (Solo Administrador): Modifica un producto existente (excepto stock_actual).
 * DELETE (Solo Administrador): Elimina un producto si no tiene ventas registradas.
 */

require_once __DIR__ . '/../includes/db.php';
require_once __DIR__ . '/../includes/respuesta.php';
require_once __DIR__ . '/../includes/auth.php';

$metodo = $_SERVER['REQUEST_METHOD'];

/**
 * Normaliza y formatea una fila de base de datos a los tipos requeridos
 */
function formatearProducto(array $fila): array {
    return [
        'id'                => (int) $fila['id'],
        'codigo_barras'     => $fila['codigo_barras'] !== null ? (string) $fila['codigo_barras'] : null,
        'nombre_producto'   => (string) $fila['nombre_producto'],
        'categoria'         => $fila['categoria'] !== null ? (string) $fila['categoria'] : null,
        'precio_venta'      => (float) $fila['precio_venta'],
        'stock_actual'      => (int) $fila['stock_actual'],
        'stock_minimo'      => (int) $fila['stock_minimo'],
        'stock_deseado'     => (int) ($fila['stock_deseado'] ?? 0),
        'fecha_vencimiento' => $fila['fecha_vencimiento'] !== null ? (string) $fila['fecha_vencimiento'] : null
    ];
}

try {
    $pdo = getDBConnection();

    // ==========================================================
    // 1. GET: Obtener lista o detalle de un producto
    // ==========================================================
    if ($metodo === 'GET') {
        // Requiere sesión activa con cualquier rol
        autenticar();

        // Búsqueda por ID directo
        if (isset($_GET['id']) && is_numeric($_GET['id'])) {
            $id = (int) $_GET['id'];
            $sql = 'SELECT id, codigo_barras, nombre_producto, categoria, precio_venta, 
                           stock_actual, stock_minimo, stock_deseado, fecha_vencimiento 
                    FROM productos 
                    WHERE id = ? 
                    LIMIT 1';
            $stmt = $pdo->prepare($sql);
            $stmt->execute([$id]);
            $fila = $stmt->fetch();

            if (!$fila) {
                responderError('Producto no encontrado.', 404);
            }

            responderJson(formatearProducto($fila), 200);
        }

        // Búsqueda por código de barras exacto (?codigo=XXXX)
        if (isset($_GET['codigo']) && trim($_GET['codigo']) !== '') {
            $codigo = trim($_GET['codigo']);
            $sql = 'SELECT id, codigo_barras, nombre_producto, categoria, precio_venta, 
                           stock_actual, stock_minimo, stock_deseado, fecha_vencimiento 
                    FROM productos 
                    WHERE codigo_barras = ? 
                    LIMIT 1';
            $stmt = $pdo->prepare($sql);
            $stmt->execute([$codigo]);
            $fila = $stmt->fetch();

            if (!$fila) {
                responderError('Producto no encontrado con el código de barras especificado.', 404);
            }

            responderJson(formatearProducto($fila), 200);
        }

        // Filtros opcionales para el listado general
        $buscar = isset($_GET['buscar']) ? trim($_GET['buscar']) : '';
        $categoria = isset($_GET['categoria']) ? trim($_GET['categoria']) : '';
        $orden = isset($_GET['orden']) ? trim($_GET['orden']) : '';
        $pagina = isset($_GET['pagina']) ? max(1, (int) $_GET['pagina']) : 1;
        $limite = isset($_GET['limite']) ? min(100, max(1, (int) $_GET['limite'])) : 50;
        $offset = ($pagina - 1) * $limite;

        $condiciones = [];
        $parametros = [];

        if ($buscar !== '') {
            $condiciones[] = '(productos.nombre_producto LIKE :buscar1 OR productos.codigo_barras LIKE :buscar2 OR productos.categoria LIKE :buscar3)';
            $parametros[':buscar1'] = '%' . $buscar . '%';
            $parametros[':buscar2'] = '%' . $buscar . '%';
            $parametros[':buscar3'] = '%' . $buscar . '%';
        }

        if ($categoria !== '') {
            $condiciones[] = 'productos.categoria = :categoria';
            $parametros[':categoria'] = $categoria;
        }

        $where = !empty($condiciones) ? 'WHERE ' . implode(' AND ', $condiciones) : '';

        // Ordenamiento: si orden=vendidos, más unidades vendidas primero, luego nombre
        if ($orden === 'vendidos') {
            $joinVentas = 'LEFT JOIN (
                SELECT producto_id, COALESCE(SUM(cantidad), 0) AS total_vendido 
                FROM detalle_ventas 
                GROUP BY producto_id
            ) dv ON productos.id = dv.producto_id';
            $orderBy = 'ORDER BY COALESCE(dv.total_vendido, 0) DESC, productos.nombre_producto ASC';
        } else {
            $joinVentas = '';
            $orderBy = 'ORDER BY productos.nombre_producto ASC';
        }

        $sql = "SELECT productos.id, productos.codigo_barras, productos.nombre_producto, productos.categoria, productos.precio_venta, 
                       productos.stock_actual, productos.stock_minimo, productos.stock_deseado, productos.fecha_vencimiento 
                FROM productos 
                $joinVentas 
                $where 
                $orderBy 
                LIMIT :limite OFFSET :offset";

        $stmt = $pdo->prepare($sql);

        // Enlazar parámetros de búsqueda
        foreach ($parametros as $clave => $valor) {
            $stmt->bindValue($clave, $valor, PDO::PARAM_STR);
        }

        // Enlazar paginación como enteros
        $stmt->bindValue(':limite', $limite, PDO::PARAM_INT);
        $stmt->bindValue(':offset', $offset, PDO::PARAM_INT);

        $stmt->execute();
        $filas = $stmt->fetchAll();

        $productos = array_map('formatearProducto', $filas);
        responderJson($productos, 200);
    }

    // ==========================================================
    // 2. POST: Crear un nuevo producto (Solo Administrador)
    // ==========================================================
    if ($metodo === 'POST') {
        $admin = requerirRol(['Administrador']);

        $cuerpo = file_get_contents('php://input');
        $datos = json_decode($cuerpo, true);

        if (!is_array($datos)) {
            responderError('Cuerpo de la petición inválido. Se espera JSON.', 400);
        }

        // Validación: Nombre obligatorio
        $nombre = trim($datos['nombre_producto'] ?? $datos['nombre'] ?? '');
        if ($nombre === '') {
            responderError('El nombre del producto es obligatorio.', 400);
        }

        // Validación: Precio de venta >= 0
        if (!isset($datos['precio_venta']) || !is_numeric($datos['precio_venta']) || (float) $datos['precio_venta'] < 0) {
            responderError('El precio de venta debe ser un número mayor o igual a 0.', 400);
        }
        $precioVenta = (float) $datos['precio_venta'];

        // Validación: Stocks enteros >= 0
        $stockActual = isset($datos['stock_actual']) ? (int) $datos['stock_actual'] : 0;
        if ($stockActual < 0) {
            responderError('El stock actual debe ser un número entero mayor o igual a 0.', 400);
        }

        $stockMinimo = isset($datos['stock_minimo']) ? (int) $datos['stock_minimo'] : 5;
        if ($stockMinimo < 0) {
            responderError('El stock mínimo debe ser un número entero mayor o igual a 0.', 400);
        }

        $stockDeseado = isset($datos['stock_deseado']) ? (int) $datos['stock_deseado'] : 0;
        if ($stockDeseado < 0) {
            responderError('El stock deseado debe ser un número entero mayor o igual a 0.', 400);
        }

        // Validación: Código de barras opcional (si viene vacío se guarda estrictamente como NULL, nunca '')
        $codigoBarrasRaw = trim($datos['codigo_barras'] ?? '');
        $codigoBarras = $codigoBarrasRaw !== '' ? $codigoBarrasRaw : null;

        if ($codigoBarras !== null) {
            $stmtCod = $pdo->prepare('SELECT id FROM productos WHERE codigo_barras = ? LIMIT 1');
            $stmtCod->execute([$codigoBarras]);
            if ($stmtCod->fetch()) {
                responderError('El código de barras ya se encuentra registrado en otro producto.', 409);
            }
        }

        // Categoría opcional
        $categoria = !empty(trim($datos['categoria'] ?? '')) ? trim($datos['categoria']) : null;

        // Validación: Fecha de vencimiento válida o NULL
        $fechaVencimiento = !empty(trim($datos['fecha_vencimiento'] ?? '')) ? trim($datos['fecha_vencimiento']) : null;
        if ($fechaVencimiento !== null) {
            $fechaObj = DateTime::createFromFormat('Y-m-d', $fechaVencimiento);
            if (!$fechaObj || $fechaObj->format('Y-m-d') !== $fechaVencimiento) {
                responderError('La fecha de vencimiento debe tener formato válido YYYY-MM-DD.', 400);
            }
        }

        try {
            $pdo->beginTransaction();

            $sql = 'INSERT INTO productos 
                    (codigo_barras, nombre_producto, categoria, precio_venta, stock_actual, stock_minimo, stock_deseado, fecha_vencimiento) 
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)';
            $stmt = $pdo->prepare($sql);
            $stmt->execute([
                $codigoBarras,
                $nombre,
                $categoria,
                $precioVenta,
                $stockActual,
                $stockMinimo,
                $stockDeseado,
                $fechaVencimiento
            ]);

            $nuevoId = (int) $pdo->lastInsertId();

            // Si el producto se crea con stock inicial > 0, registrar movimiento inicial como 'ingreso' (patrón ventas.php)
            if ($stockActual > 0) {
                $stmtMov = $pdo->prepare(
                    'INSERT INTO movimientos_stock 
                     (producto_id, usuario_id, tipo, cantidad, stock_antes, stock_despues, fecha) 
                     VALUES (?, ?, \'ingreso\', ?, 0, ?, ?)'
                );
                $stmtMov->execute([
                    $nuevoId,
                    $admin['id'],
                    $stockActual,
                    $stockActual,
                    obtenerFechaChile()
                ]);
            }

            $pdo->commit();

            // Consultar el producto recién insertado
            $stmtSelect = $pdo->prepare('SELECT * FROM productos WHERE id = ?');
            $stmtSelect->execute([$nuevoId]);
            $productoCreado = $stmtSelect->fetch();

            responderJson(formatearProducto($productoCreado), 201);
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            error_log('Error en POST productos.php: ' . $e->getMessage());
            responderError('Ocurrió un error interno al crear el producto.', 500);
        }
    }

    // ==========================================================
    // 3. PUT: Modificar producto (Solo Administrador)
    // Si cambia stock_actual, registra auditoría tipo 'ajuste' en movimientos_stock
    // ==========================================================
    if ($metodo === 'PUT') {
        $admin = requerirRol(['Administrador']);

        $cuerpo = file_get_contents('php://input');
        $datos = json_decode($cuerpo, true);

        if (!is_array($datos)) {
            responderError('Cuerpo de la petición inválido. Se espera JSON.', 400);
        }

        $id = isset($_GET['id']) ? (int) $_GET['id'] : (int) ($datos['id'] ?? 0);
        if ($id <= 0) {
            responderError('El ID del producto es obligatorio para editar.', 400);
        }

        // Validación: Nombre obligatorio
        $nombre = trim($datos['nombre_producto'] ?? $datos['nombre'] ?? '');
        if ($nombre === '') {
            responderError('El nombre del producto es obligatorio.', 400);
        }

        // Validación: Precio de venta >= 0
        if (!isset($datos['precio_venta']) || !is_numeric($datos['precio_venta']) || (float) $datos['precio_venta'] < 0) {
            responderError('El precio de venta debe ser un número mayor o igual a 0.', 400);
        }
        $precioVenta = (float) $datos['precio_venta'];

        // Validación: Stocks mínimos y deseados >= 0
        $stockMinimo = isset($datos['stock_minimo']) ? (int) $datos['stock_minimo'] : 5;
        if ($stockMinimo < 0) {
            responderError('El stock mínimo debe ser un número entero mayor o igual a 0.', 400);
        }

        $stockDeseado = isset($datos['stock_deseado']) ? (int) $datos['stock_deseado'] : 0;
        if ($stockDeseado < 0) {
            responderError('El stock deseado debe ser un número entero mayor o igual a 0.', 400);
        }

        // Validación: Código de barras opcional (si viene vacío se guarda estrictamente como NULL, nunca '')
        $codigoBarrasRaw = trim($datos['codigo_barras'] ?? '');
        $codigoBarras = $codigoBarrasRaw !== '' ? $codigoBarrasRaw : null;

        if ($codigoBarras !== null) {
            $stmtCod = $pdo->prepare('SELECT id FROM productos WHERE codigo_barras = ? AND id != ? LIMIT 1');
            $stmtCod->execute([$codigoBarras, $id]);
            if ($stmtCod->fetch()) {
                responderError('El código de barras ya se encuentra registrado en otro producto.', 409);
            }
        }

        // Categoría opcional
        $categoria = !empty(trim($datos['categoria'] ?? '')) ? trim($datos['categoria']) : null;

        // Validación: Fecha de vencimiento válida o NULL
        $fechaVencimiento = !empty(trim($datos['fecha_vencimiento'] ?? '')) ? trim($datos['fecha_vencimiento']) : null;
        if ($fechaVencimiento !== null) {
            $fechaObj = DateTime::createFromFormat('Y-m-d', $fechaVencimiento);
            if (!$fechaObj || $fechaObj->format('Y-m-d') !== $fechaVencimiento) {
                responderError('La fecha de vencimiento debe tener formato válido YYYY-MM-DD.', 400);
            }
        }

        // Validación de stock_actual si se proporciona en el payload
        $nuevoStock = null;
        if (isset($datos['stock_actual']) && $datos['stock_actual'] !== null && trim((string)$datos['stock_actual']) !== '') {
            if (!is_numeric($datos['stock_actual']) || (int) $datos['stock_actual'] < 0) {
                responderError('El stock actual debe ser un número entero mayor o igual a 0.', 400);
            }
            $nuevoStock = (int) $datos['stock_actual'];
        }

        try {
            $pdo->beginTransaction();

            // Bloquear producto para lectura y actualización consistente
            $stmtExiste = $pdo->prepare('SELECT id, stock_actual, nombre_producto FROM productos WHERE id = ? FOR UPDATE');
            $stmtExiste->execute([$id]);
            $productoExistente = $stmtExiste->fetch();
            if (!$productoExistente) {
                $pdo->rollBack();
                responderError('Producto no encontrado.', 404);
            }

            $stockAntes = (int) $productoExistente['stock_actual'];
            $stockCambia = ($nuevoStock !== null && $nuevoStock !== $stockAntes);
            $stockDespues = $stockCambia ? $nuevoStock : $stockAntes;

            // Actualizar campos del producto (incluyendo stock_actual si cambió)
            $sql = 'UPDATE productos SET 
                        codigo_barras = ?,
                        nombre_producto = ?,
                        categoria = ?,
                        precio_venta = ?,
                        stock_actual = ?,
                        stock_minimo = ?,
                        stock_deseado = ?,
                        fecha_vencimiento = ? 
                    WHERE id = ?';
            $stmt = $pdo->prepare($sql);
            $stmt->execute([
                $codigoBarras,
                $nombre,
                $categoria,
                $precioVenta,
                $stockDespues,
                $stockMinimo,
                $stockDeseado,
                $fechaVencimiento,
                $id
            ]);

            // Si el Administrador modificó el stock, registrar auditoría 'ajuste' en la misma transacción (patrón ventas.php)
            if ($stockCambia) {
                $diferencia = $stockDespues - $stockAntes;
                $stmtMov = $pdo->prepare(
                    'INSERT INTO movimientos_stock 
                     (producto_id, usuario_id, tipo, cantidad, stock_antes, stock_despues, fecha) 
                     VALUES (?, ?, \'ajuste\', ?, ?, ?, ?)'
                );
                $stmtMov->execute([
                    $id,
                    $admin['id'],
                    $diferencia,
                    $stockAntes,
                    $stockDespues,
                    obtenerFechaChile()
                ]);
            }

            $pdo->commit();

            // Consultar el producto actualizado
            $stmtSelect = $pdo->prepare('SELECT * FROM productos WHERE id = ?');
            $stmtSelect->execute([$id]);
            $productoActualizado = $stmtSelect->fetch();

            responderJson(formatearProducto($productoActualizado), 200);

        } catch (Throwable $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            error_log('Error en PUT productos.php: ' . $e->getMessage());
            responderError('Ocurrió un error interno al actualizar el producto.', 500);
        }
    }

    // ==========================================================
    // 4. DELETE: Eliminar producto (Solo Administrador)
    // Valida que no tenga ventas registradas en detalle_ventas
    // ==========================================================
    if ($metodo === 'DELETE') {
        requerirRol(['Administrador']);

        $cuerpo = file_get_contents('php://input');
        $datos = json_decode($cuerpo, true);

        $id = isset($_GET['id']) ? (int) $_GET['id'] : (int) ($datos['id'] ?? 0);
        if ($id <= 0) {
            responderError('El ID del producto es obligatorio para eliminar.', 400);
        }

        // Verificar que exista
        $stmtExiste = $pdo->prepare('SELECT id FROM productos WHERE id = ? LIMIT 1');
        $stmtExiste->execute([$id]);
        if (!$stmtExiste->fetch()) {
            responderError('Producto no encontrado.', 404);
        }

        // Verificar si tiene registros en detalle_ventas
        $stmtVentas = $pdo->prepare('SELECT COUNT(*) FROM detalle_ventas WHERE producto_id = ?');
        $stmtVentas->execute([$id]);
        $totalVentas = (int) $stmtVentas->fetchColumn();

        if ($totalVentas > 0) {
            responderError('No se puede eliminar: el producto tiene ventas registradas.', 409);
        }

        // Proceder a eliminar
        $stmtDel = $pdo->prepare('DELETE FROM productos WHERE id = ?');
        $stmtDel->execute([$id]);

        responderJson([
            'estado'  => 'ok',
            'mensaje' => 'Producto eliminado exitosamente.'
        ], 200);
    }

    // Método no soportado
    responderError('Método no permitido. Solo se aceptan peticiones GET, POST, PUT o DELETE.', 405);

} catch (Exception $e) {
    error_log('Error en productos.php: ' . $e->getMessage());
    responderError('Error interno en el servidor.', 500);
}
