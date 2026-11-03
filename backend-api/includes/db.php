<?php
/**
 * RADAR STOCK - Conector a Base de Datos MySQL con PDO
 */

// Desactivar display_errors para evitar salidas inesperadas antes del JSON
ini_set('display_errors', '0');

// Registrar errores de PHP en un archivo de log
ini_set('log_errors', '1');
ini_set('error_log', __DIR__ . '/../error.log');
error_reporting(E_ALL);

// Zona horaria para ventas y operaciones
date_default_timezone_set('America/Santiago');

require_once __DIR__ . '/respuesta.php';

/**
 * Devuelve una conexión PDO con utf8mb4, errores como excepciones y modo asociativo
 *
 * @return PDO
 */
function getDBConnection(): PDO {
    static $pdo = null;

    if ($pdo === null) {
        $configFile = __DIR__ . '/../config/config.php';
        if (!file_exists($configFile)) {
            error_log('Error crítico: Falta el archivo de configuración config.php en ' . $configFile);
            responderError('Error de configuración: No se encontró backend-api/config/config.php. Crea una copia a partir de config.example.php.', 500);
        }

        require_once $configFile;

        $charset = defined('DB_CHARSET') ? DB_CHARSET : 'utf8mb4';
        $dsn = "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=" . $charset;

        $opciones = [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ];

        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, $opciones);
        } catch (PDOException $e) {
            error_log('Error de conexión PDO: ' . $e->getMessage());
            responderError('Error al conectar con la base de datos: ' . $e->getMessage(), 500);
        }
    }

    return $pdo;
}
