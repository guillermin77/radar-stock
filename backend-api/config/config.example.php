<?php
/**
 * RADAR STOCK - Configuración de Base de Datos y Entorno
 * 
 * Copia este archivo como "config.php" en esta misma carpeta
 * y reemplaza los valores con las credenciales reales de tu servidor MySQL.
 */

// Desactivar impresión de errores de PHP en pantalla para no romper respuestas JSON
ini_set('display_errors', '0');
ini_set('log_errors', '1');
error_reporting(E_ALL);

// Zona horaria para registro de ventas y fechas
date_default_timezone_set('America/Santiago');

// Credenciales de la base de datos MySQL
define('DB_HOST', 'localhost');
define('DB_NAME', 'radar_stock_db');
define('DB_USER', 'tu_usuario');
define('DB_PASS', 'tu_contraseña');
define('DB_CHARSET', 'utf8mb4');

