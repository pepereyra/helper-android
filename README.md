# Helper · App Android

Aplicación Android para que repartidores en bicicleta, bici eléctrica o moto pidan ayuda cuando el vehículo se les rompe en la calle. Pueden pedir un mecánico que vaya hasta donde están o ir a un taller cercano. Los mecánicos ven los pedidos cercanos que pueden atender y eligen cuál toman. El primero que acepta se queda con el pedido.

Trabajo Práctico Obligatorio de **Desarrollo de Aplicaciones I** (UADE, 2026).

- **Integrante:** Pedro Pereyra, con todos los roles: arquitectura, UI/UX, persistencia, integración, testing y documentación.
- **Estado:** entrega final de implementación. La preentrega de análisis y diseño fue aprobada con devolución, y esa devolución está incorporada (ver [Cambios respecto de la preentrega](#cambios-respecto-de-la-preentrega)).
- **Diseño en Figma:** https://www.figma.com/design/a2JwsgaQa5AQpH7jMkVZvw
- **Backend que consume:** https://github.com/pepereyra/mechanic-helper (microservicios Spring Boot de Desarrollo de Aplicaciones II)

## El problema

Un repartidor que se queda con la cadena cortada, una rueda pinchada o la moto sin arrancar pierde horas de trabajo y muchas veces está en un lugar incómodo o inseguro. Hoy la salida es buscar una bicicletería en el mapa, llamar a alguien o volver empujando. La app le permite pedir ayuda en pocos toques desde donde está, aun con mala señal, y saber en todo momento si alguien la tomó y cuándo llega.

## Requisitos funcionales alcanzados

| ID | Requisito | Usuario | Estado | Cómo se cumple el criterio de aceptación |
|---|---|---|---|---|
| RF01 | Registrar mi vehículo (bici, bici eléctrica o moto) | Repartidor | Cumplido | "Mis vehículos" lista los vehículos y "Agregar vehículo" los registra en el servidor y en Room. La moto exige patente y la lista se ve sin conexión |
| RF02 | Pedir asistencia desde donde estoy, también sin conexión | Repartidor | Cumplido | Ubicación del GPS, o un punto marcado a mano en el mapa si el permiso está denegado. Hay chips de problema según el tipo de vehículo y se elige entre ayuda a domicilio o ir a un taller. Sin red, el pedido queda **"Todavía no pediste ayuda"**: WorkManager lo envía al volver la conexión con la misma `Idempotency-Key` y vence a los 15 minutos |
| RF03 | Seguir mi pedido, con el último estado conocido si no hay señal | Repartidor | Cumplido | Un stepper muestra Pedido, Aceptado, En curso y Terminado. Se ven el mecánico, su puntaje, el tiempo de llegada y la ruta en el mapa, junto con "Llamar" y el historial de estados. Sin red aparece un banner con la hora del último dato. Al terminar se califica al mecánico |
| RF04 | Tomar un pedido cercano | Mecánico | Cumplido | "Pedidos cerca" lista las ofertas con distancia, tiempo de llegada y vencimiento, con las acciones Tomar y Pasar. Si otro ganó antes aparece "Otro mecánico ya lo tomó". Después vienen la navegación paso a paso y los botones Iniciar y Terminar |

Además:
- **Cuentas y roles.** Login con email y contraseña, con JWT y renovación automática. El servidor decide el rol.
- **Validación de mecánicos.** Un mecánico nuevo ve "Tu cuenta está en revisión" hasta que un admin lo aprueba desde la consola web del backend.
- **Talleres.** Un taller se registra con su dirección fija y su teléfono.

## Funcionalidades por pantalla

| Rol | Pantallas |
|---|---|
| Ambos | Login y alta de cuenta, "Completá tu perfil", Perfil con cierre de sesión |
| Repartidor | Pedir ayuda (mapa con mecánicos cerca, vehículo, problema, a domicilio o taller), seguimiento con stepper y mapa, historial del pedido, calificación, Mis vehículos y Agregar vehículo |
| Mecánico | Pedidos cerca con disponibilidad, oferta con mapa, trabajo en curso con navegación a pantalla completa y modo viaje, Iniciar y Terminar, cuenta en revisión |
| Estados | Carga, contenido, vacío, error con Reintentar, sin conexión, permiso de ubicación denegado, pedido sin enviar y pedido vencido |

La navegación es una barra inferior por rol: Ayuda, Mis vehículos y Perfil para el repartidor, y Pedidos, Trabajo y Perfil para el mecánico.

## Arquitectura final

MVVM con principios de Clean Architecture, en dos módulos Gradle:

```
UI (Compose) → ViewModel (StateFlow<UiState>) → Use case → Repository (interfaz) → Room / Retrofit / DataStore / GPS
   :app                :app                     :domain        :domain              :app (data)
```

- **`:domain`** es Kotlin puro, sin dependencias de Android, y por eso se testea con JUnit solo. Contiene:
  - los modelos (`Vehicle`, `Assistance`, `MechanicJob`, `Route`, `Session`, `MechanicKind`);
  - las interfaces de repositorio;
  - la regla de pedidos pendientes, `PendingRequestPolicy`: vencen a los 15 minutos, y si pasaron 5 minutos y el repartidor se movió más de 300 m hay que reconfirmar la ubicación;
  - los casos de uso: `RequestAssistance`, `SendPendingAssistance`, `ConfirmNewLocation`, `ObserveAssistance`, `RefreshAssistance`, `CancelAssistance`, `RegisterVehicle`, `TakeOffer`, `AdvanceJob`, `Login` y `Register`.
- **`:app`, capa de datos (`data/`)**:
  - **Repositorios:** cada uno combina la fuente local y la remota.
  - **Room:** es la fuente de verdad local, con vehículos, pedidos, ofertas e historial de estados.
  - **Retrofit + OkHttp:** un `AuthInterceptor` agrega el token y un `TokenAuthenticator` lo renueva ante un `401`.
  - **DataStore:** guarda la sesión.
  - **`ConnectivityObserver`:** detecta la red.
  - **`FusedLocationProvider`:** da la ubicación.
  - **`SendPendingWorker`:** con WorkManager, envía los pedidos pendientes cuando vuelve la red.
- **`:app`, presentación (`ui/`)**:
  - pantallas en Jetpack Compose con Material 3;
  - un ViewModel por pantalla con Hilt, que expone un `UiState`;
  - mapas con osmdroid sobre OpenStreetMap.

### Offline first

| Situación | Qué pasa |
|---|---|
| Con conexión | Los datos vienen del backend y se guardan en Room; la pantalla observa Room |
| Pierde la conexión | Se muestra lo último guardado, con un banner y la hora del último dato. Un pedido nuevo queda `SIN_ENVIAR`, con Reintentar, Llamar y Cancelar |
| Recupera la conexión | WorkManager envía lo pendiente con la misma `Idempotency-Key`, así que un reintento no duplica el pedido. Una notificación avisa "ya pedimos ayuda" |
| Información vieja | Un pedido sin enviar vence a los 15 minutos y no se manda solo: la app pregunta si todavía hace falta ayuda. El servidor también rechaza pedidos viejos con `422` |
| Sin datos locales | Estado vacío con la acción para empezar (por ejemplo, "Agregar vehículo") |
| Requiere red | Aceptar una oferta y cancelar un pedido ya enviado: sin señal, esos botones se deshabilitan y explican por qué |

## Tecnologías

| Tecnología | Para qué |
|---|---|
| Kotlin, Jetpack Compose, Material 3 | UI declarativa con estados explícitos |
| ViewModel, StateFlow, Coroutines, Flow | Estado de pantalla que sobrevive a la rotación, y datos reactivos desde Room |
| Hilt | Inyección de dependencias en ViewModels y Workers; la navegación entre secciones es una barra inferior por rol |
| Room | Fuente de verdad local para el modo sin conexión |
| Retrofit, OkHttp, kotlinx.serialization | API REST del backend, con token y renovación |
| WorkManager | Envío confiable de pedidos pendientes cuando vuelve la red |
| DataStore | Sesión y preferencias |
| Fused Location Provider | Ubicación del repartidor y del mecánico |
| osmdroid (OpenStreetMap) | Mapas sin clave de API |
| JUnit, kotlinx-coroutines-test | Tests del dominio |
| GitHub Actions | Compila, corre los tests y genera el APK de debug en cada PR |

## Cómo ejecutarla

Requisitos: Android Studio reciente con JDK 17 o 21, y un emulador o teléfono con Android 8.0 (API 26) o superior.

1. **Levantar el backend.** En una copia de [mechanic-helper](https://github.com/pepereyra/mechanic-helper), correr `docker compose up -d --build --wait`. Así quedan los servicios, Kafka y la consola web en http://localhost:8080, que también hace de puerta de entrada para la app.
2. **Abrir este repo en Android Studio** y correr la configuración `app` en un emulador. Por defecto, la app habla con `http://10.0.2.2:8080/`, que es el localhost de la máquina vista desde el emulador.
3. **Con un teléfono real o el despliegue público,** pasar la URL al compilar:
   ```bash
   ./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.0.10:8080/
   ```
4. **Probar el circuito completo:**
   1. Crear una cuenta de repartidor y una de mecánico (perfil de mecánico móvil o de taller).
   2. Aprobar al mecánico desde la consola web, en Cuentas y después Ver pendientes, con el admin `admin@helper.local` / `admin-demo-123`.
   3. Con el mecánico disponible, pedir ayuda desde el repartidor.
   4. Tomar el pedido desde el mecánico, iniciarlo y terminarlo.
   5. Calificar al mecánico desde el repartidor.

Los tests y el APK, desde la terminal:

```bash
./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug
```

El APK de debug también queda como artefacto en cada corrida de CI (Actions, "Android CI", `helper-debug-apk`).

## Decisiones relevantes

- **El mecánico elige.** No hay asignación automática: el pedido se ofrece a los mecánicos cercanos y compatibles, y el primero que acepta gana. El servidor lo garantiza con una actualización condicional, y al segundo le responde `409`.
- **El servidor autoriza.** El rol sale del token y la app solo lo usa para decidir qué pantallas mostrar. Un mecánico sin aprobar recibe `403` aunque modifique la app.
- **Estados locales honestos.** `SIN_ENVIAR` y `VENCIDO` existen solo en el teléfono y se muestran como tales ("Todavía no pediste ayuda"). Todos los demás estados vienen del servidor.
- **Dominio sin Android.** Las reglas, como el vencimiento o la reconfirmación de ubicación, están en `:domain` y se prueban sin emulador.
- **Mapas con OpenStreetMap,** para no depender de una clave de Google Maps en la corrección.

## Cambios respecto de la preentrega

- **Login con contraseña y roles en el servidor.** En la preentrega estaba fuera de alcance. Se sumó por la devolución del docente, que pidió autenticación y validación de mecánicos del lado del servidor.
- **Pedidos offline con vencimiento** a los 15 minutos y reconfirmación de ubicación, también por la devolución: un pedido que se manda horas después ya no sirve.
- **Talleres además de mecánicos móviles.** El repartidor elige si quiere ayuda a domicilio o ir a un taller. Esto amplía el RF02 sin agregar requisitos nuevos.
- **Mapas con OpenStreetMap** en lugar de Maps Compose, por la clave de API.

## Limitaciones conocidas

- **Tests.** Hay tests unitarios del dominio (casos de uso y reglas) y CI. No hay tests instrumentados de Compose ni tests de ViewModel o Room.
- **Tráfico sin cifrar.** La app usa HTTP sin TLS contra el backend local o de laboratorio. En producción iría HTTPS.
- **Sin notificaciones push.** El mecánico ve las ofertas nuevas al refrescar la pantalla o al volver a ella.
- **Teléfono del mecánico.** Los mecánicos registrados antes de que se agregara el teléfono no lo tienen, y para ellos no aparece "Llamar".
- **Administración.** Aprobar mecánicos se hace desde la consola web del backend, no desde la app.
- **Pagos.** No hay pagos dentro de la app.

## Forma de trabajo

- **Ramas:** GitHub Flow. `main` siempre compila; cada cambio va en una rama corta (`feature/…`, `fix/…`, `docs/…`) y entra por pull request con CI en verde.
- **Commits:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`), en español y en imperativo.
- **Pull requests:** uno por funcionalidad, con descripción y capturas cuando cambia la UI.
