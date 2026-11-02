<?php
/**
 * RADAR STOCK - Conector a Base de Datos MySQL con PDO
 */

require_once __DIR__ . '/response.php';

// Verificar que exista el archivo de configuración real
$configFile = __DIR__ . '/../config/config.php';
if (!file_exists($configFile)) {
    sendJsonError('Error de configuración: Falta el archivo backend-api/config/config.php. Crea una copia a partir de config.example.php.', 500);
}

require_once $configFile;

/**
 * Obtiene la conexión activa a la base de datos MySQL mediante PDO (Singleton)
 *
 * @return PDO
 */
function getDBConnection(): PDO {
    static $pdo = null;

    if ($pdo === null) {
        $dsn = sprintf(
            'mysql:host=%s;dbname=%s;charset=%s',
            DB_HOST,
            DB_NAME,
            DB_CHARSET
        );

        $options = [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ];

        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
        } catch (PDOException $e) {
            // No exponer contraseñas ni detalles internos en el mensaje
            sendJsonError('No se pudo conectar a la base de datos: ' . $e->getMessage(), 500);
        }
    }

    return $pdo;
}

