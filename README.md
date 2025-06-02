# Blood Pressure Check

Aplicación Android para el registro y seguimiento de la presión arterial, desarrollada en Kotlin con Firebase.

## Características

- 🔐 Autenticación con Google
- 📊 Registro de presión arterial (sistólica, diastólica, pulso)
- 📝 Notas opcionales para cada registro
- 📱 Interfaz moderna con Material Design 3
- ☁️ Almacenamiento en la nube con Firebase Firestore
- 🔄 Sincronización en tiempo real de registros
- 👤 Registros individuales por usuario

## Requisitos Técnicos

- Android Studio Hedgehog | 2023.1.1 o superior
- JDK 17
- Android SDK 34
- Gradle 8.6
- Dispositivo Android con Google Play Services

## Configuración del Proyecto

1. Clona el repositorio
2. Abre el proyecto en Android Studio
3. Configura Firebase:
   - Crea un proyecto en [Firebase Console](https://console.firebase.google.com)
   - Agrega una aplicación Android con el package name `com.mrgomez.bloodpressurecheck`
   - Descarga el archivo `google-services.json` y colócalo en la carpeta `app/`
   - Habilita la autenticación con Google en Firebase Console
   - Configura las reglas de Firestore:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /registro_medico_usuarios/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == request.resource.data.userId;
    }
  }
}
```

4. Sincroniza el proyecto con Gradle
5. Ejecuta la aplicación

## Estructura del Proyecto

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/mrgomez/bloodpressurecheck/
│   │   │   ├── LoginActivity.kt       # Manejo de autenticación
│   │   │   ├── MainActivity.kt        # Pantalla principal
│   │   │   ├── BloodPressureAdapter.kt # Adaptador para la lista
│   │   │   └── model/
│   │   │       └── BloodPressureRecord.kt # Modelo de datos
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_login.xml
│   │       │   ├── activity_main.xml
│   │       │   └── item_blood_pressure.xml
│   │       └── values/
│   │           └── strings.xml
└── google-services.json
```

## Uso

1. Inicia sesión con tu cuenta de Google
2. En la pantalla principal:
   - Ingresa los valores de presión sistólica y diastólica
   - Opcionalmente, ingresa el pulso y notas
   - Presiona "Guardar Registro"
3. Los registros se mostrarán en orden cronológico inverso
4. Usa el botón flotante para limpiar el formulario
5. Usa el menú para cerrar sesión

## Tecnologías Utilizadas

- Kotlin
- Firebase Authentication
- Firebase Firestore
- Material Design 3
- ViewBinding
- RecyclerView
- Coroutines (para operaciones asíncronas)

## Contribución

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## Licencia

Este proyecto está bajo la Licencia MIT - ver el archivo [LICENSE](LICENSE) para más detalles.

## Contacto

Mario Gómez - [@mrgomez](https://github.com/mrgomez)

Link del proyecto: [https://github.com/mrgomez/BloodPressureCheck](https://github.com/mrgomez/BloodPressureCheck) 