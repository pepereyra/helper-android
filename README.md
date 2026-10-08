# Helper · App Android

Aplicación Android para que repartidores en bicicleta, bici eléctrica o moto pidan un mecánico que vaya hasta donde están cuando el vehículo se rompe en la calle. El mecánico disponible ve los pedidos cercanos que puede atender y elige cuál toma.

Trabajo Práctico Obligatorio de **Desarrollo de Aplicaciones I** (UADE, 2026).

- **Integrante:** Pedro Pereyra
- **Estado:** preentrega de análisis y diseño. La implementación empieza cuando se apruebe.
- **Diseño en Figma:** https://www.figma.com/design/a2JwsgaQa5AQpH7jMkVZvw
- **Backend que consume:** https://github.com/pepereyra/mechanic-helper (microservicios Spring Boot, Desarrollo de Aplicaciones II)

## Alcance de la versión 1

| ID | Requisito | Usuario |
|---|---|---|
| RF01 | Registrar mi vehículo (bici, bici eléctrica o moto) | Repartidor |
| RF02 | Pedir asistencia desde donde estoy, también sin conexión | Repartidor |
| RF03 | Seguir mi pedido, con el último estado conocido si no hay señal | Repartidor |
| RF04 | Tomar un pedido cercano | Mecánico |

## Tecnologías previstas

Kotlin, Jetpack Compose con Material 3, Navigation Compose, ViewModel con StateFlow, Coroutines y Flow, Room (fuente de verdad local, offline first), Retrofit con OkHttp y kotlinx.serialization, WorkManager (sincronización de pendientes), DataStore, Fused Location Provider, Maps Compose y Hilt. Tests con JUnit, MockK y Turbine.

## Arquitectura

MVVM con principios de Clean Architecture en tres capas:

- **Presentación:** pantallas en Compose y un ViewModel por pantalla que expone un `UiState` (carga, contenido, vacío, error y sin conexión).
- **Dominio:** modelos (`Vehicle`, `VehicleType`, `ProblemType`, `Assistance`), reglas y casos de uso (`RegisterVehicle`, `RequestAssistance`, `ObserveAssistance`, `TakeOffer`), más las interfaces de repositorio.
- **Datos:** repositorios que combinan Room, Retrofit, DataStore y la ubicación, y un `SyncWorker` de WorkManager que envía lo pendiente cuando vuelve la red.

## Forma de trabajo

- **Ramas:** GitHub Flow. `main` siempre compila; cada requisito o cambio va en una rama corta (`feature/rf02-pedir-asistencia`, `fix/…`, `docs/…`) y entra por pull request.
- **Commits:** pequeños y frecuentes, con Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`), en español y en imperativo.
- **Pull requests:** uno por funcionalidad, con descripción, capturas cuando cambia la UI y tests que pasan antes de unir a `main`.
