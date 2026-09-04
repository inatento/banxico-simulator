# Simulador SPEI ("Banxico falso")

Servidor TCP que emula a Banxico del lado de la conexión que `minos` (módulo de Hermes MK I) ya
sabe hacer, para poder probar `minos` en desarrollo/pruebas sin depender del ambiente real de
Banxico. Implementa el **protocolo real, cifrado (RSA + AES-CBC) y firmado** — no un atajo — según
[ADR-005](../HERMES-MKI-VOBEDA/ADRs/ADR_005_Simulador-SPEI-protocolo-real.md). Java 17, Maven, sin
frameworks — dos servidores de sockets simples.

Ver [AGENTS.md](AGENTS.md) para el mapa de arquitectura y las convenciones del repo.

## Compilar

Requisitos: JDK 17, Maven 3.8+.

```bash
mvn -DskipTests package
```

Esto genera `target/banxico-simulator.jar` (jar ejecutable con todas las dependencias incluidas).

## Configurar

```bash
cp config/simulator.properties.example config/simulator.properties
```

Edita `config/simulator.properties` si hace falta — los valores por defecto (puertos 6001/6002,
códigos de entidad de ejemplo) sirven para arrancar, pero **dos cosas hay que ajustar siempre**:

1. **`minos.entityCode`** debe coincidir exactamente con el código de entidad que tu instancia de
   minos tiene configurado internamente (`Spei.entityCode`) — si no coincide, minos no podrá
   resolver su propia entidad al procesar `EnSesion` y fallará más adelante al intentar mandar
   órdenes.
2. **El certificado público real de esa instancia de minos**, en
   `config/minos-public-cert.pem` (formato PEM, `-----BEGIN CERTIFICATE-----`). Es **obligatorio**:
   minos desencripta el reto RSA de ARA (`IdUsuarioAleat`) y la llave de sesión de `ClvSim` con su
   propia llave privada, así que el simulador necesita la llave pública correspondiente para poder
   cifrar hacia minos — exactamente como en la vida real, donde Banxico ya tiene registrado el
   certificado del participante de antemano; no se negocia en este socket. Cómo obtenerlo depende
   de dónde minos tiene guardada su llave — normalmente se puede exportar desde la tabla del
   certificado SPEI que usa `SpeiServiceEntity.defaultKey` en la base de datos de minos.

   Si arrancas sin este archivo, el simulador funciona igual para la Fase 1 (conexión básica),
   pero el login ARA y el desafío `ClvSim` **no completarán** (queda documentado con una
   advertencia en el log al arrancar).

## Correr

```bash
java -jar target/banxico-simulator.jar
```

```
==================================================================
 Simulador SPEI (Banxico falso) listo
 Puerto SPEI: 6001   Puerto ARA: 6002
 Comandos de consola: 'abono' | 'abono-invalido' | 'salir'
==================================================================
```

La primera vez que arranca genera su propia identidad (llaves RSA + certificado X.509
autofirmado) en `data/identity/` y la reutiliza en corridas siguientes. La base de datos de
pruebas (H2) vive en `data/banxicosim.mv.db`.

## Apuntar una instancia de minos en pruebas hacia el simulador

minos es quien abre la conexión hacia afuera en ambos sockets (nunca al revés) — ver
`SpeiSocketServiceImpl.initializeSocket()` y `AraSocketServiceImpl.initializeSocket()` en el
código de minos. Apuntarlo al simulador es **sólo cambio de configuración**, no de código: ajusta
en la base de datos de minos las entidades `SpeiServiceEntity` y `AraServiceEntity` (tabla de
servicios de minos) para que:

- `host` apunte a la máquina donde corre el simulador (`localhost` si es la misma máquina).
- `ports` sea el rango de puertos que incluya el puerto del simulador, en formato `"inicio:fin"`
  (ver `BanxicoServiceEntity.portRange()` en minos — por ejemplo `"6001:6001"` para el socket SPEI
  y `"6002:6002"` para ARA, si usas los puertos por defecto de este simulador).

## Tipos de pago disponibles para probar

El simulador valida (Fase 4, `OrdenTopoV`) sólo el subconjunto de tipos de pago que
[ADR-005](../HERMES-MKI-VOBEDA/ADRs/ADR_005_Simulador-SPEI-protocolo-real.md) definió como alcance
representativo de v1 — **no los 35 tipos reales del catálogo SPEI** (códigos 0-36, con huecos).
La razón: cubrir los 35 hubiera significado replicar de memoria el catálogo completo de
`judeca.CamposOrdenesValidator`/`pagos.properties` sin necesidad real de prueba para todos —
estos 4 alcanzan para verificar el flujo de validación de campos de punta a punta. Ver
`validation/PaymentType.java` para el detalle de campos por tipo (orden exacto, cuáles son
opcionales) y la nota sobre por qué el string de "detalle" no repite los 5 campos comunes.

| Código | Nombre |
|---|---|
| 01 | Tercero a Tercero |
| 02 | Tercero a Ventanilla |
| 05 | Participante a Tercero |
| 12 | Nómina |

Cualquier otro código de tipo de pago en un `OrdenTopoV` entrante es tratado por
`OrderFieldValidator` como fuera de catálogo — si una prueba futura necesita ampliar el
subconjunto, hay que agregar el tipo a `PaymentType` citando `pagos.properties` línea por línea
(ver AGENTS.md &sect;4/&sect;5, estas reglas no se reinventan de memoria).

## API de control y flujo de pruebas

Además de los dos sockets del protocolo SPEI/ARA (que sólo minos consume) y la consola de
stdin, el simulador expone una **API de control HTTP** — un tercer puerto, sin relación con el
protocolo real, pensado para disparar y observar corridas de prueba con clientes HTTP simples en
vez de la consola. Está implementada con `com.sun.net.httpserver.HttpServer` del JDK (cero
dependencias nuevas, ver AGENTS.md &sect;4). Puerto configurable en `config/simulator.properties`
(`control.port`, default `8089`).

| Método | Ruta | Qué hace |
|---|---|---|
| `GET` | `/health` | Liveness del proceso. `{"status":"ok"}` |
| `GET` | `/session` | Estado de la sesión SPEI más reciente: fase de handshake alcanzada, si sigue viva, día operativo declarado en `EnSesion`. `{"session":null}` si nunca se ha conectado minos — nunca un 500. |
| `POST` | `/abonos/validos` | Dispara un abono de prueba válido — mismo camino que el comando de consola `abono` (`SpeiSession.sendTestAbono(true)`). 409 con detalle si no hay sesión SPEI viva. |
| `POST` | `/abonos/invalidos` | Igual, con contenido deliberadamente inválido (RFC roto) — equivalente a `abono-invalido`. |
| `GET` | `/test-runs` | Lista las corridas de prueba registradas en H2 (tabla `test_run`), más reciente primero. |
| `GET` | `/test-runs/{id}/events` | Lista los mensajes enviados/recibidos de una corrida (tabla `test_event`), en orden cronológico. |

Ejemplo de `GET /session` con una sesión viva:

```json
{"session":{"runId":1001,"channel":"SPEI","remoteAddress":"/127.0.0.1:54321",
  "alive":true,"fase":"VIVA","diaOperativo":"2026-09-03"}}
```

El contrato completo de cada endpoint (forma exacta de la respuesta, casos de error) está en los
comentarios de los propios requests ejecutables — no se duplica aquí, ver
[`httpclient/`](httpclient/).

### Flujo de prueba end-to-end

1. Arranca el simulador (`java -jar target/banxico-simulator.jar`).
2. Apunta una instancia de minos de pruebas hacia él (sección "Apuntar una instancia de minos en
   pruebas hacia el simulador" arriba) — es minos quien abre la conexión, nunca al revés.
3. Espera a que el handshake real complete — verifica con `GET /session` hasta ver
   `"alive":true` y `"fase":"VIVA"`.
4. Usa los archivos de [`httpclient/`](httpclient/) para disparar abonos de prueba y consultar
   corridas: `httpclient/01-salud-y-sesion.http` (salud y estado de sesión),
   `httpclient/02-abonos.http` (disparar abonos válidos/inválidos),
   `httpclient/03-corridas-de-prueba.http` (listar corridas y sus eventos).
5. Revisa el detalle completo de cada corrida con `GET /test-runs/{id}/events`, o directamente
   contra `data/banxicosim.mv.db` con cualquier cliente SQL compatible con H2.

## Estado de avance por fase

Las 6 fases del plan (`02_especificacion_tecnica.md` &sect;9). **Verificado de punta a punta**
con un arnés de prueba en Python que actúa como "minos falso" (genera su propia identidad RSA,
completa el login ARA real, el reto `ClvSim`, `EnSesion`/`MsjCatalogos`, manda una `OrdenTopoV`
válida y otra deliberadamente inválida, y dispara un `Abonos`) — no sólo compilado, sino corrido
contra el jar real.

| Fase | Estado | Notas |
|---|---|---|
| 1 — Servidor TCP + framing + Conexion/Greeting | **Completa** | Verificada con un cliente TCP crudo y con el arnés completo. |
| 2 — Servidor ARA + certificado autofirmado + ClvSim/RespClvSim | **Completa** | Login ARA (ConnUsr → IdUsuarioAleat → IdFmaAleat → Logged) y `PideCrtNvo`/`RegCrtNvoFmt`/`CrtNoExiste` verificados. Requiere el certificado público de minos configurado (ver arriba). |
| 3 — EnSesion + MsjCatalogos (sesión "viva") | **Completa** | Incluye `Reenvio`/`FinReenvio` e `InicioSesionCifrada`, que el código real de minos exige tras `MsjCatalogos` aunque la spec técnica no los liste explícitamente (ver `ReenvioCodec.java` y `SpeiSession.handleInicioSesionCifrada`). |
| 4 — Recepción y validación de OrdenTopoV + AcuseRecibo | **Completa** para los 4 tipos de pago en alcance (01, 02, 05, 12). Verificada aceptando una orden válida y rechazando una con RFC inválido. |
| 5 — Envío de Abonos (válidos/inválidos) | **Completa**, disparo manual por consola (`abono` / `abono-invalido`) — no hay todavía un catálogo amplio de escenarios inválidos, sólo el de RFC roto usado para la verificación. |
| 6 — Persistencia H2 de corridas de prueba | **Completa**. Cada mensaje enviado/recibido en cada sesión queda en `test_event` (con `test_run` como cabecera de corrida), consultable con cualquier cliente SQL sobre `data/banxicosim.mv.db`. |

### Lo que falta / simplificaciones conocidas de v1

- **No hay reensamblado real de mensajes particionados multi-parte.** El simulador siempre manda
  sus mensajes completos en una sola parte (aprovechando que declara un `maxMessageLength` grande
  en `EnSesion`, así minos nunca necesita partir lo que le manda). Si una prueba futura necesita
  forzar partición real, hay que implementar reensamblado en `WireFraming`/`SpeiSession`.
- **Catálogos de institución / tipo de cuenta:** `judeca.CamposOrdenesValidator` los recibe de un
  sistema externo en tiempo de ejecución (no están hardcodeados ni en judeca ni aquí) —
  `OrderFieldValidator` los deja configurables pero vacíos por defecto (acepta cualquier entero
  válido). Si una prueba necesita rechazar por catálogo, hay que poblar
  `OrderFieldValidator.setValidInstitucion/setValidTipoCuenta`.
- **`Abonos` de prueba:** sólo hay un escenario válido y uno inválido (RFC roto) cableados en
  `SpeiSession.sendTestAbono`. Ampliar el catálogo de escenarios es sencillo (mismo patrón).
- **Sin suite de pruebas automatizada** (JUnit) todavía — ver AGENTS.md &sect;3.

### Decisiones donde la especificación no era 100% explícita

- **Orden de campos del "detalle" de una orden** (`OrdenTopoV`): se asume que el string de
  detalle (separado por 0x00 en el wire) contiene sólo los campos *específicos* del tipo de pago
  (catálogo `pagos.properties`), no los 5 campos "comunes" (`tipoPg`, `cveRastreo`, `cveEmisor`,
  `cveReceptor`, `monto`) — esos ya viajan en otras partes del wire de `OrdenTopoV`. Ver comentario
  en `validation/PaymentType.java`.
- **Contenido firmado en la fase ARA de `RegCrtNvoFmt`/`CrtNoExiste`:** el código de minos que
  arma estos mensajes salientes (`AraSignedMessage.signMessage`) y el que los parsea como
  entrantes (`AraSignedMessage` con `header,body`) no son simétricos — el que parsea espera un
  byte de relleno entre el contenido y la firma que el que construye no pone. El simulador replica
  la fórmula de *análisis* (con el relleno), porque es minos quien recibe y parsea estos mensajes.
  Ver `ara/AraWireFraming.java`.
- **Qué firma exactamente minos en `RespClvSim`:** no está en ningún documento — se encontró
  leyendo `SpeiInputManagerServiceImpl.processClvSimMessage`: firma los 32 bytes crudos de la
  llave simétrica de sesión completa (antes de partirla en llave+IV), con su propia llave privada.
  Ver `spei/messages/ClvSimCodec.java`.
- **Certificado de "servidor" (`serverCertificateNumber` en `EnSesion`):** debe ser el número de
  certificado del simulador (no el de minos) — es la llave que minos usará para cifrar hacia el
  simulador en `InicioSesionCifrada` y para verificar las firmas de mensajes que el simulador le
  manda. minos también necesita su propia entidad declarada en `EnSesion` con su propio número de
  certificado, o no puede resolver `Spei.myEntity` y truena más adelante — por eso `EnSesion`
  siempre declara exactamente dos entidades (la propia y la de minos).

## Estructura del repositorio

Ver [AGENTS.md](AGENTS.md) &sect;2.
