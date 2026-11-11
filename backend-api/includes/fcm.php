<?php
/**
 * RADAR STOCK - Servicio de Notificaciones Push Remotas (Firebase Cloud Messaging v1)
 * Envía notificaciones a través de la API oficial HTTP v1 de Google FCM
 * utilizando autenticación con cuenta de servicio y JWT (openssl).
 */

/**
 * Genera un Access Token OAuth 2.0 de Google usando la clave privada de la cuenta de servicio.
 * Guarda en caché el token mientras esté vigente para no repetir autenticaciones.
 *
 * @param string $credentialsPath Ruta al archivo JSON de credenciales
 * @return string|null Access Token de Google o null en caso de error
 */
function obtenerGoogleAccessToken(string $credentialsPath): ?string {
    static $cachedToken = null;
    static $tokenExpiry = 0;

    if ($cachedToken !== null && time() < ($tokenExpiry - 60)) {
        return $cachedToken;
    }

    if (!file_exists($credentialsPath)) {
        error_log("FCM: Archivo de credenciales no encontrado en $credentialsPath");
        return null;
    }

    $credJson = file_get_contents($credentialsPath);
    $creds = json_decode($credJson, true);

    if (!isset($creds['private_key']) || !isset($creds['client_email'])) {
        error_log("FCM: Estructura de credenciales JSON inválida.");
        return null;
    }

    $now = time();
    $header = ['alg' => 'RS256', 'typ' => 'JWT'];
    $claimSet = [
        'iss'   => $creds['client_email'],
        'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
        'aud'   => 'https://oauth2.googleapis.com/token',
        'exp'   => $now + 3600,
        'iat'   => $now
    ];

    $base64UrlHeader = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode(json_encode($header)));
    $base64UrlClaim = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode(json_encode($claimSet)));
    $signaturePayload = "$base64UrlHeader.$base64UrlClaim";

    $signature = '';
    $success = openssl_sign($signaturePayload, $signature, $creds['private_key'], OPENSSL_ALGO_SHA256);
    if (!$success) {
        error_log("FCM: Error al firmar el JWT con openssl.");
        return null;
    }

    $base64UrlSignature = str_replace(['+', '/', '='], ['-', '_', ''], base64_encode($signature));
    $jwt = "$signaturePayload.$base64UrlSignature";

    // Solicitar Access Token a Google OAuth2
    $ch = curl_init('https://oauth2.googleapis.com/token');
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query([
        'grant_type' => 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        'assertion'  => $jwt
    ]));
    curl_setopt($ch, CURLOPT_TIMEOUT, 10);
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    if ($httpCode !== 200 || !$response) {
        error_log("FCM: Error al obtener access token de Google. HTTP $httpCode: $response");
        return null;
    }

    $data = json_decode($response, true);
    if (!isset($data['access_token'])) {
        return null;
    }

    $cachedToken = $data['access_token'];
    $tokenExpiry = $now + (int) ($data['expires_in'] ?? 3600);

    return $cachedToken;
}

/**
 * Envía una notificación push remota a todos los administradores activos con token FCM registrado.
 *
 * @param PDO $pdo Conexión a la base de datos
 * @param array $alertasNuevas Lista de alertas detectadas en la venta
 */
function enviarNotificacionesFcmAlertas(PDO $pdo, array $alertasNuevas): void {
    if (empty($alertasNuevas)) {
        return;
    }

    try {
        $credPath = __DIR__ . '/../config/firebase_credentials.json';
        if (!file_exists($credPath)) {
            return;
        }

        $creds = json_decode(file_get_contents($credPath), true);
        $projectId = $creds['project_id'] ?? 'radar-stock';

        $accessToken = obtenerGoogleAccessToken($credPath);
        if (!$accessToken) {
            return;
        }

        // Obtener tokens de administradores activos
        $stmt = $pdo->query("SELECT fcm_token FROM usuarios WHERE rol = 'Administrador' AND activo = 1 AND fcm_token IS NOT NULL AND fcm_token != ''");
        $tokens = $stmt->fetchAll(PDO::FETCH_COLUMN);

        if (empty($tokens)) {
            return;
        }

        $primera = $alertasNuevas[0];
        $total = count($alertasNuevas);
        $titulo = $total === 1 
            ? "⚠️ Alerta de Stock: {$primera['nombre_producto']}" 
            : "⚠️ $total productos requieren reposición";
        $cuerpo = $total === 1 
            ? $primera['mensaje'] 
            : "Se registraron alertas críticas de stock tras una venta.";

        $url = "https://fcm.googleapis.com/v1/projects/{$projectId}/messages:send";

        foreach ($tokens as $token) {
            $payload = [
                'message' => [
                    'token' => $token,
                    'notification' => [
                        'title' => $titulo,
                        'body'  => $cuerpo
                    ],
                    'data' => [
                        'tipo'        => (string) ($primera['tipo'] ?? 'alerta'),
                        'producto_id' => (string) ($primera['producto_id'] ?? '')
                    ],
                    'android' => [
                        'priority' => 'high',
                        'notification' => [
                            'sound'      => 'default',
                            'channel_id' => 'canal_alertas_stock'
                        ]
                    ]
                ]
            ];

            $ch = curl_init($url);
            curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
            curl_setopt($ch, CURLOPT_POST, true);
            curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($payload));
            curl_setopt($ch, CURLOPT_HTTPHEADER, [
                'Authorization: Bearer ' . $accessToken,
                'Content-Type: application/json'
            ]);
            curl_setopt($ch, CURLOPT_TIMEOUT, 5);
            $res = curl_exec($ch);
            curl_close($ch);
        }
    } catch (Exception $e) {
        error_log('FCM Error en enviarNotificacionesFcmAlertas: ' . $e->getMessage());
    }
}

