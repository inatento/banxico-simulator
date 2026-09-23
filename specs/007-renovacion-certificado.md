# 007 — Renovación de certificado (`PideCrtNvo`)

## Contexto

Ya implementado y verificado desde la Fase 2 original (README, "Estado de avance por fase"):
`AraSession.handlePideCrtNvo` (`AraSession.java:163-179`) atiende `PideCrtNvo` (op `0x50`) y
responde `RegCrtNvoFmt` (op `0xC3`, `AraSession.java:181-200`) con el certificado propio si el
número solicitado coincide, o `CrtNoExiste` (op `0xC2`, `AraSession.java:203-207`) si no. Lo que
falta no es código — es un **escenario de prueba repetible** que lo ejercite deliberadamente
contra minos real o el arnés Python, en vez de haber sido validado solo una vez al construirlo.

## Requisitos

1. Poder disparar, como escenario de prueba, una solicitud `PideCrtNvo` pidiendo el certificado
   propio del simulador — confirmar que la respuesta `RegCrtNvoFmt` sigue siendo correcta con la
   identidad actual (recordemos que la identidad se regenera en cada VM nueva — ver el incidente
   de huellas de certificado no coincidentes documentado en esta misma conversación).
2. Poder disparar una solicitud `PideCrtNvo` pidiendo un número de certificado **que no existe**,
   y confirmar que la respuesta es `CrtNoExiste`, no un error no manejado.

## Fuera de alcance

- La lógica de minos sobre cuándo decide pedir un certificado nuevo (vencimiento, rotación, etc.)
  — eso es interno a minos, no se simula.

## Diseño propuesto

- Ninguno nuevo del lado del simulador — la implementación ya existe. Esta spec es sobre
  **cobertura de prueba**, no sobre construir algo. El trabajo real es: un escenario en el arnés
  Python que dispare ambos casos (número propio / número inexistente) y verifique la respuesta,
  para que quede como prueba de regresión repetible en vez de haberse verificado una sola vez.

## Preguntas abiertas

- ¿Vale la pena también probar que minos, al recibir `RegCrtNvoFmt`, efectivamente registra el
  certificado nuevo? Eso requeriría inspeccionar el estado de minos después, no solo la respuesta
  del simulador — puede que exceda lo que el simulador puede verificar por sí mismo.

## Criterios de aceptación

- [ ] Escenario de prueba repetible (arnés Python) para `PideCrtNvo` con número propio → confirma
      `RegCrtNvoFmt` con el certificado correcto de la identidad actual.
- [ ] Escenario de prueba repetible para `PideCrtNvo` con número inexistente → confirma
      `CrtNoExiste`.
