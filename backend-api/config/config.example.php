<?php
/**
 * RADAR STOCK - Configuración de Base de Datos y Entorno (Plantilla de Ejemplo)
 * 
 * Copia este archivo como "config.php" en esta misma carpeta (config/)
 * y coloca los valores de conexión de tu servidor local o de Hostinger.
 */

// Entorno de ejecución: 'desarrollo' o 'produccion'
define('ENTORNO', 'produccion');

// Credenciales de Base de Datos MySQL / MariaDB
define('DB_HOST', 'localhost');
define('DB_NAME', 'radar_stock_db');
define('DB_USER', 'root');
define('DB_PASS', '');
define('DB_CHARSET', 'utf8mb4');

// Ruta del archivo de registro de errores (logs)
// Por defecto se guarda en backend-api/error.log
// En Hostinger puedes mantener esta ruta relativa protegida por .htaccess
// o especificar una ruta absoluta fuera de public_html (ej. '/home/u123456789/logs/error.log')
define('LOG_PATH', __DIR__ . '/../error.log');
