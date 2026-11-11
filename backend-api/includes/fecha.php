<?php
/**
 * RADAR STOCK - Utilidades de Fecha y Hora
 * Zona horaria de negocio: America/Santiago (Chile)
 * Maneja automáticamente cambios de horario de verano (UTC-3) e invierno (UTC-4).
 */

/**
 * Obtiene la fecha y hora actual calculada en la zona horaria 'America/Santiago'
 * en formato estándar 'Y-m-d H:i:s'.
 *
 * Reutiliza el mismo patrón y formato de ventas.php para registrar movimientos de stock.
 *
 * @return string Fecha y hora actual en Chile (ej. '2026-11-09 22:45:00')
 */
function obtenerFechaChile(): string {
    $dt = new DateTime('now', new DateTimeZone('America/Santiago'));
    return $dt->format('Y-m-d H:i:s');
}

/**
 * Valida si una cadena tiene formato exacto YYYY-MM-DD y corresponde a una fecha de calendario real.
 */
function esFechaIsoValida(string $fecha): bool {
    if (!preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', $fecha, $matches)) {
        return false;
    }
    return checkdate((int) $matches[2], (int) $matches[3], (int) $matches[1]);
}

/**
 * Calcula los límites de fecha inicio y fin en zona horaria 'America/Santiago'
 * siguiendo exactamente la misma lógica de api/resumen.php.
 *
 * @param string|null $rangoParam 'hoy', '7dias', 'mes' (por defecto 'hoy')
 * @param string|null $desdeParam Fecha inicial YYYY-MM-DD (anula $rangoParam si está presente)
 * @param string|null $hastaParam Fecha final YYYY-MM-DD (anula $rangoParam si está presente)
 * @return array{periodo: string, inicio: string, fin: string}
 */
function calcularRangoFechasChile(?string $rangoParam, ?string $desdeParam, ?string $hastaParam): array {
    $tz = new DateTimeZone('America/Santiago');

    $desde = $desdeParam !== null && trim($desdeParam) !== '' ? trim($desdeParam) : null;
    $hasta = $hastaParam !== null && trim($hastaParam) !== '' ? trim($hastaParam) : null;

    if ($desde !== null || $hasta !== null) {
        if (empty($desde) || empty($hasta)) {
            responderError("Para un rango personalizado debe proporcionar tanto 'desde' como 'hasta' con formato YYYY-MM-DD.", 400);
        }

        if (!esFechaIsoValida($desde)) {
            responderError("El parámetro 'desde' tiene un formato de fecha inválido. Se espera YYYY-MM-DD.", 400);
        }

        if (!esFechaIsoValida($hasta)) {
            responderError("El parámetro 'hasta' tiene un formato de fecha inválido. Se espera YYYY-MM-DD.", 400);
        }

        if ($desde > $hasta) {
            responderError("La fecha de inicio (desde: $desde) no puede ser mayor que la fecha de término (hasta: $hasta).", 400);
        }

        return [
            'periodo' => 'personalizado',
            'inicio'  => "$desde 00:00:00",
            'fin'     => "$hasta 23:59:59",
        ];
    }

    $rango = $rangoParam !== null && trim($rangoParam) !== '' ? strtolower(trim($rangoParam)) : 'hoy';

    switch ($rango) {
        case 'hoy':
            return [
                'periodo' => 'hoy',
                'inicio'  => (new DateTime('today', $tz))->format('Y-m-d 00:00:00'),
                'fin'     => (new DateTime('today', $tz))->format('Y-m-d 23:59:59'),
            ];

        case '7dias':
            return [
                'periodo' => '7dias',
                'inicio'  => (new DateTime('today', $tz))->modify('-6 days')->format('Y-m-d 00:00:00'),
                'fin'     => (new DateTime('today', $tz))->format('Y-m-d 23:59:59'),
            ];

        case 'mes':
            return [
                'periodo' => 'mes',
                'inicio'  => (new DateTime('first day of this month', $tz))->format('Y-m-d 00:00:00'),
                'fin'     => (new DateTime('last day of this month', $tz))->format('Y-m-d 23:59:59'),
            ];

        default:
            responderError("Parámetro 'rango' inválido. Los valores permitidos son: 'hoy', '7dias' o 'mes', o especificar 'desde' y 'hasta'.", 400);
            exit;
    }
}
