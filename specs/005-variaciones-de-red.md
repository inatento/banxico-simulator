# 005 — Variaciones de red simulables desde la API de control

## Contexto

Además de los escenarios de contenido/protocolo (specs 001-004), se busca poder variar las
condiciones de red bajo las que corre una prueba, configurable desde la misma API de control.

## Requisitos

Variaciones a soportar, todas configurables por corrida vía parámetros nuevos en la API de
control (no requieren tocar la lógica de protocolo en `SpeiSession`/`AraSession` — viven en una
capa que envuelve el socket antes de que el protocolo lo use):

| Variación | Qué es | Cómo se implementaría |
|---|---|---|
| Latencia fija/jitter | Retraso constante o variable antes de mandar cada frame. | Decorador sobre el `OutputStream` del socket que duerme (`Thread.sleep`) antes de escribir — `{"latenciaMs": N, "jitterMs": M}`. |
| Pérdida de paquetes | Descartar un frame en vez de mandarlo, con probabilidad `p`. | Mismo decorador, descarta en vez de escribir. Depende de la spec 001 (particionado real) para tener sentido — sin partición real, "perder un paquete" es indistinguible de cortar la conexión. |
| Reordenamiento | Los frames llegan en orden distinto al que se mandaron. | Buffer de frames pendientes con retraso aleatorio por frame antes de liberarlos — requiere que el reensamblado (spec 001) tolere llegada fuera de orden. |
| Duplicación | El mismo frame llega más de una vez. | El decorador reescribe el mismo frame N veces con probabilidad `p` — útil para probar idempotencia del receptor. |
| Corte abrupto en momento específico | Cerrar el socket deliberadamente en un punto nombrado del flujo (no aleatorio). | Parámetro de corrida (`{"cortarEn": "post-ClvSim"}`, etc.) en vez de un decorador continuo — el punto de corte es parte del escenario, no de las "condiciones de red". |
| **Throttling asimétrico** | Ver explicación abajo. | Dos instancias del decorador de ancho de banda, una por dirección del socket, cada una con su propio límite. |
| **MTU reducido / fragmentación forzada** | Ver explicación abajo. | **No** es un decorador a nivel aplicación — vive en el socket TCP real o en el SO. Ver nota de alcance abajo. |

### Throttling asimétrico — qué es

"Throttling" es limitar el ancho de banda disponible (bytes/segundo) para simular una conexión
lenta. "Asimétrico" significa que el límite **no es el mismo en las dos direcciones** — por
ejemplo, minos podría tener mucho ancho de banda para *enviar* hacia Banxico pero poco para
*recibir* de vuelta (o viceversa), que es exactamente el patrón típico de conexiones reales
(muchas conexiones son más rápidas de bajada que de subida). Simularlo permite probar cómo se
comporta el protocolo cuando un lado puede "hablar" más rápido de lo que el otro puede
"escuchar" — un caso real y común, no solo una lentitud pareja en ambos sentidos.

Se implementaría con **dos** decoradores de ancho de banda independientes, uno envolviendo el
lado de escritura (out) del socket y otro el de lectura (in), cada uno con su propio límite
configurable — a diferencia del throttling simétrico, que sería un solo límite compartido.

### MTU reducido / fragmentación forzada — qué es

MTU (Maximum Transmission Unit) es el tamaño máximo de un paquete a nivel de red (típicamente
1500 bytes en Ethernet). Cuando un mensaje de aplicación es más grande que el MTU, el sistema
operativo/la pila TCP lo **fragmenta automáticamente** en varios paquetes IP para transportarlo —
esto es distinto y más bajo nivel que el particionado de la spec 001 (que es particionado a
**nivel de aplicación/protocolo SPEI**, decidido por el propio simulador). Reducir el MTU
artificialmente fuerza fragmentación real a nivel de red incluso para mensajes que el simulador
cree que está mandando "de una pieza".

**Nota de alcance:** a diferencia de las demás variaciones de esta tabla, esto no se puede lograr
con un decorador de aplicación — requiere tocar `SO_SNDBUF`/opciones de socket real, o
configuración del sistema operativo (`tc`/`netem` en la VM Linux donde corre el simulador). Vale
la pena decidir explícitamente si entra en el alcance de "lo que la API de control puede
configurar", o si se documenta como configuración de infraestructura aparte, fuera de la API.

## Fuera de alcance

- Variaciones que dependan de hardware real de red (switches, routers) — todo lo de esta spec es
  simulable en software, sobre un único host.

## Preguntas abiertas

- ¿MTU/fragmentación entra al alcance de la API de control (aceptando que implica tocar el SO de
  la VM), o se documenta aparte como configuración de infraestructura?
- ¿Los parámetros de red se fijan al iniciar una corrida (estáticos durante toda la prueba) o se
  pueden cambiar a media corrida (ej. "sube la latencia después del mensaje 3")?

## Criterios de aceptación

- [ ] Cada variación de la tabla (excepto MTU, ver pregunta abierta) es configurable como
      parámetro de una corrida disparada por la API de control, sin tocar código entre corridas.
- [ ] Las variaciones son combinables entre sí en una misma corrida (ej. latencia + pérdida +
      reordenamiento simultáneos).
