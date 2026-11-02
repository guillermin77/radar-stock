# 🛒 Radar Stock

**Radar Stock** es una aplicación móvil Android de inventario y registro de ventas rápidas especialmente diseñada para almacenes de barrio y pequeños comercios (proyecto de especialidad). Su propósito es agilizar las ventas en el mostrador mediante botones táctiles rápidos, prevenir quiebres de inventario mediante alertas tempranas y mitigar mermas por productos próximos a vencer o caducados.

---

## 🛠️ Stack Tecnológico

El proyecto utiliza un stack tecnológico fijo y optimizado para entornos de comercio local:

* **Aplicación Móvil:** Android nativa en **Kotlin**, interfaz de usuario en **XML con ViewBinding** y **RecyclerView**. Arquitectura limpia estructurada en paquetes. Se distribuye y entrega como archivo APK instalable. *(Sin Jetpack Compose)*.
* **Backend:** API REST en **PHP** utilizando **PDO** y sentencias preparadas para máxima seguridad contra inyecciones SQL. Todas las respuestas se emiten estrictamente en formato **JSON** (con tiempos de respuesta inferiores a 2 segundos). Alojado en **Hostinger**.
* **Base de Datos:** **MySQL**. El archivo `database/radar_stock_schema.sql` es la única fuente de verdad para tablas, columnas, tipos e índices.
* **Landing Page:** Sitio web informativo y de descarga construido en **HTML5, CSS3 y JavaScript** estándar.

---

## 📁 Estructura del Repositorio

```text
radar-stock/
├── android-app/          # Proyecto de Android Studio (paquete com.radarstock.app)
│   ├── adapters/         # Adaptadores para RecyclerView (productos, ventas)
│   ├── models/           # Modelos de datos en Kotlin
│   ├── network/          # Cliente HTTP y llamadas a la API
│   ├── utils/            # Utilidades, formateadores y constantes
│   └── views/            # Activities y Fragments con ViewBinding en XML
├── backend-api/          # API REST en PHP
│   ├── api/              # Endpoints públicos (una ruta por archivo)
│   ├── includes/         # Conexión PDO, respuestas JSON y lógica compartida
│   └── config/           # Configuración de base de datos (config.php / config.example.php)
├── landing/              # Sitio web promocional y zona de descarga de APK
│   ├── css/              # Hojas de estilo
│   ├── js/               # Lógica interactiva
│   ├── img/              # Recursos gráficos y capturas
│   └── downloads/        # Archivo APK para distribución directa
├── database/             # Scripts SQL (esquema principal y datos de prueba)
│   ├── radar_stock_schema.sql
│   └── 02_datos_prueba.sql
├── docs/                 # Documentación técnica, pautas e informes
│   └── evidencias/       # Capturas y respaldos de pruebas por etapa
├── .gitignore            # Exclusiones de Git (seguridad y compilación)
└── README.md             # Documentación general del repositorio
```

---

## 🚀 Cómo ejecutar en local

*(Sección pendiente para completar después de finalizar los módulos)*
