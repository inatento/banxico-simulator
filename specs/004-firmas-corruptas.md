# 004 — Escenarios de firma corrupta (ambos sentidos)

## Contexto

Hoy solo existe un camino de "firma inválida" del lado B→H: `AbonosCodec.buildAbonoV` con
`signProperly=false` manda 32 bytes en cero como firma de relleno (`new byte[32]`) — prueba que
la ruta estructural no depende de la firma, pero no prueba una firma **corrupta con formato
válido** (mismo tamaño/codificación que una firma real, contenido inválido) — que es un caso
distinto y más realista de qué tan bien detecta minos una firma que no es simplemente "vacía".

## Requisitos — alcance decidido (2026-09-21)

Contemplar firma corrupta (formato válido, contenido inválido) en **ambos sentidos**:

1. **B→H:** el simulador manda un `Abonos`/`OrdenTopoV` (donde aplique) con una firma del tamaño
   y codificación correctos, pero criptográficamente inválida (ej. firmar con una llave distinta
   a la que minos tiene registrada, o alterar un byte de una firma real) — para confirmar que
   minos la rechaza correctamente en vez de aceptarla o tronar.
2. **H→B:** vía el arnés Python ("minos falso", ver spec 003), mandar una orden con firma
   corrupta pero bien formada, y confirmar que el simulador la rechaza con el motivo correcto
   (no con una excepción no manejada).

## Fuera de alcance

- Firma vacía/de relleno (`new byte[32]`) — ya existe, no es el objeto de esta spec.
- Ataques de firma criptográfica reales (forjar una firma válida sin la llave privada) — esto es
  simplemente "firma que no valida", no un ejercicio de criptoanálisis.

## Diseño propuesto

- Agregar una tercera variante además de "firma real"/"firma vacía": "firma corrupta" — mismo
  tamaño de bytes que una firma real (256 bytes para RSA-2048), pero generada alterando un byte de
  una firma real o firmando con una llave que no es la registrada.
- Del lado H→B, el arnés Python necesita la misma capacidad simétrica.

## Preguntas abiertas

- ¿"Corrupta" para esta prueba significa "un byte alterado de una firma real" (case más común en
  la práctica — corrupción en tránsito) o "firmada con una llave incorrecta pero coherente"
  (más relevante para probar el matching de certificados)? Podrían ser dos casos distintos, no
  necesariamente uno solo.

## Estado de implementación (2026-09-21)

Parte B→H lista en código: `AbonosCodec.SignatureMode` ahora tiene `VALIDA`/`VACIA`/`CORRUPTA`
(antes solo booleano válido/relleno) — `CORRUPTA` firma de verdad y luego voltea un byte,
disparable vía `POST /abonos` con `"firma":"corrupta"`. **Compilado, no probado todavía contra
minos real.** Parte H→B sigue bloqueada por la falta del arnés (ver `specs/README.md`).

## Criterios de aceptación

- [ ] Un `Abonos` con firma corrupta (no vacía) se manda y se puede confirmar en el log de minos
      que la rechazó por firma inválida, no que la aceptó ni que tronó con una excepción no
      manejada.
- [ ] Una orden entrante con firma corrupta (vía arnés Python) es rechazada por el simulador con
      el motivo correcto, registrado en `/test-runs/{id}/events`.
