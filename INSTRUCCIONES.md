# 🩻 X-Ray Evaluator — Guía de Uso

## Requisitos previos

Antes de empezar, asegurate de tener instalado:
- **Java 17 o superior** → verificar con `java -version`
- **Apache Maven** → verificar con `mvn -version`

---

## 1. Cómo iniciar el servidor

Abrí una terminal y ejecutá los siguientes comandos:

```bash
# 1. Ir al directorio de la aplicación
cd java/xrays_evaluation

# 2. Iniciar el servidor
mvn spring-boot:run
```

✅ El servidor está listo cuando veas este mensaje en la terminal:

```
Started XRayApplication in X.XXX seconds
Tomcat started on port 8080
```

> ⚠️ **Importante:** El modelo ONNX (`models/xrays_evaluation_model_medium_v1.onnx`) debe estar en la carpeta `java/xrays_evaluation/models/`. Si no está, copialo desde `python/xrays_evaluation/models/YOLO/`.

---

## 2. Interfaz Web (Demo Visual)

1. Abrí tu navegador (Chrome, Firefox, Edge).
2. Ingresá a: **[http://localhost:8080](http://localhost:8080)**
3. **Arrastrá y soltá** tu archivo, o hacé clic en el área de carga.
4. Seleccioná un archivo **JPEG, PNG o PDF** con la radiografía.
5. Hacé clic en el botón 🔬 **Analizar Radiografía**.
6. Verás el resultado: **Normal** ✅ o **Anomalía** ⚠️ con el nivel de confianza.

---

## 3. Postman

### Paso 1 — Importar la colección
1. Abrí Postman.
2. Clic en **Import** (botón arriba a la izquierda).
3. Seleccioná el archivo `XRays_Evaluation_API.postman_collection.json` ubicado en `java/xrays_evaluation/`.
4. La colección **"XRays Evaluation API"** aparecerá en tu barra lateral.

---

### Paso 2 — Elegir el tipo de request según tu archivo

La colección tiene **3 endpoints**. Usá el que se adapte a tu caso:

| # | Endpoint | Cuándo usarlo |
|---|---|---|
| 1 | `POST /api/xrays/evaluate` | Tenés la radiografía como imagen **JPEG o PNG** |
| 2 | **`POST /api/xrays/evaluate/pdf`** | Tenés la radiografía como **archivo PDF** ⭐ Recomendado |
| 3 | `POST /api/xrays/evaluate/pdf/base64` | Tenés el PDF ya convertido a **Base64** |

---

### Opción A — Enviar imagen (JPEG/PNG) como Base64

1. Convertí tu imagen a Base64 con PowerShell:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\ruta\a\imagen.jpg"))
   ```
2. En Postman, seleccioná el request **"1. Evaluate X-Ray (Imagen en Base64)"**.
3. En la pestaña **Variables** de la colección, pegá el texto Base64 en el campo `imageBase64`.
4. Hacé clic en **Send**.

---

### Opción B — Enviar PDF directamente ⭐ (más fácil)

1. En Postman, seleccioná el request **"2. Evaluate X-Ray (PDF como archivo)"**.
2. En la pestaña **Body** → asegurate que esté en modo `form-data`.
3. En el campo `file`, cambiá el tipo de `Text` a **File** (clic en el menú desplegable).
4. Hacé clic en **Select Files** y elegí tu archivo PDF.
5. Hacé clic en **Send**.

---

### Respuesta esperada

```json
{
    "label": "Normal",
    "confidence": 0.9999,
    "classId": 1,
    "modelUsed": "YOLO11m-cls (ONNX)"
}
```

| Campo | Descripción |
|---|---|
| `label` | `"Normal"` o `"Anomaly"` |
| `confidence` | Confianza del modelo (0.0 a 1.0) |
| `classId` | `1` = Normal, `0` = Anomalía |
| `modelUsed` | Identificador del modelo utilizado |

---

## 4. Detener el servidor

Cuando termines, presioná **Ctrl + C** en la terminal donde corre el servidor.

---

## 5. Estructura del proyecto

```
java/xrays_evaluation/
├── models/
│   └── xrays_evaluation_model_medium_v1.onnx   ← Modelo de IA
├── src/
│   └── main/
│       ├── java/com/genia/xrays/
│       │   ├── controller/XRayController.java   ← Endpoints REST
│       │   ├── service/InferenceService.java     ← Lógica de inferencia
│       │   └── service/PdfConverterService.java  ← Conversión de PDF
│       └── resources/static/
│           └── index.html                        ← Interfaz web demo
├── pom.xml                                       ← Configuración Maven
└── XRays_Evaluation_API.postman_collection.json  ← Colección Postman
```

---

## 6. Solución de problemas

### ❌ "Port 8080 was already in use"
Significa que ya hay una instancia del servidor corriendo. Ejecutá esto en PowerShell para liberar el puerto y reiniciar:

```powershell
# Matar todos los procesos Java
taskkill /F /IM java.exe

# Esperar 2 segundos y reiniciar
cd java/xrays_evaluation
mvn spring-boot:run
```

### ❌ "No se encuentra el modelo ONNX"
Copiá el modelo desde la carpeta Python:
```powershell
cp python/xrays_evaluation/models/YOLO/xrays_evaluation_model_medium_v1.onnx java/xrays_evaluation/models/
```

### ❌ "Error 500" al enviar un PDF
El PDF puede estar dañado o protegido. Probá con otro archivo PDF.

