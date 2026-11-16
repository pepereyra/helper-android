# Pruebas de campo (DA I)

Las 8 pruebas que prometimos en la respuesta a la devolución de la preentrega, corridas el 16/11/2026.

## Cómo se corrieron

- **Dispositivos:**
  - Repartidor: emulador Pixel 10 Pro (Android 16).
  - Mecánico: un segundo emulador con su propia cuenta ("Bicicleteria Demo").
  - Segundo mecánico (pruebas 2 y 5): la cuenta "Taller Prueba" desde la API, con su propio token.
- **Backend:** `mechanic-helper` en `main`, levantado con `docker compose` en la misma máquina (no contra OpenShift).
- **Ubicación:** fija con `adb emu geo fix`. El repartidor está en el Obelisco.
- **Sin red:** se usó el modo avión del emulador.
- **Evidencia:** capturas de pantalla y, donde suma, la consulta a la base o la respuesta de la API (los `.txt`).

## Resultado

| # | Prueba | Resultado | Evidencia |
|---|---|---|---|
| 1 | Pedido con GPS: el mecánico más cercano recibe la oferta | ✅ Se ofreció a los 2 más cercanos (0 m), ordenados por tiempo de llegada por calle. | `p1-*` |
| 2 | Dos mecánicos tocan "Tomar" al mismo tiempo | ✅ Gana uno (la API respondió 200 con `MATCHED`). El emulador muestra "Otro mecánico ya lo tomó.". En la base, una oferta `ACCEPTED` y las demás `LOST`. | `p2-*` |
| 3 | Pedido en modo avión; vuelve la red antes de 15 min | ✅ Queda "Todavía no pediste ayuda". Al volver la red se envía solo y una sola vez: 1 fila por `Idempotency-Key`, confirmada en el teléfono a las 07:11:02 y creada en el servidor a las 07:11:21. | `p3-*` |
| 4 | Pedido en modo avión que se deja vencer | ✅ No se envía nunca y la app pregunta "¿Todavía necesitás ayuda?". **Encontró un error:** con la pantalla abierta seguía diciendo "Todavía no pediste ayuda" después de los 15 min, porque sin red el worker no corre y la base no cambia (`p4-3`). Ya está corregido: el seguimiento se reevalúa cada 15 s (`p4-4`) y hay un test que lo cubre. | `p4-*` |
| 5 | Se corta la red durante el seguimiento | ✅ Aparece "Sin conexión. Este es el último estado conocido, de las 07:14". Mientras tanto el mecánico empezó el trabajo, y al volver la red se ve "En curso". | `p5-*` |
| 6 | Se cierra la app con un pedido pendiente y se reinicia el teléfono | ✅ Después de reiniciar, el pedido sigue en "Todavía no pediste ayuda", con el mismo vencimiento. Al volver la red se envía. | `p6-*` |
| 7 | El repartidor cancela mientras el mecánico acepta | ✅ Los dos tocaron a la vez. El servidor registró `MATCHED` y 6 s después `CANCELLED`. El repartidor ve "Cancelado" y al mecánico se le avisa que el pedido ya no está activo. Los tres coinciden. | `p7-*` |
| 8 | Un mecánico no aprobado intenta ponerse disponible | ✅ La app muestra "Tu cuenta está en revisión". Desde la API, ponerse disponible y ver ofertas responden 403. | `p8-*` |

## Diferencias con lo que habíamos prometido

- Fueron 2 emuladores más un mecánico por API, en lugar de 2 teléfonos físicos y 1 emulador.
- Se corrió contra el backend local con `docker compose`, no contra el despliegue en OpenShift.
- Hay capturas en lugar de video.
