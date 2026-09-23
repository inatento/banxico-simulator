# 010 — Reconexión tras caída a media transacción

## Contexto

Hoy, si la conexión se cae a mitad de un abono/orden, la sesión completa se pierde — no hay
ningún mecanismo de recuperación de estado. Esto es distinto del reenvío de la spec 003 (que
cubre "minos pide que se reenvíe algo ya mandado, dentro de una sesión viva") — aquí el caso es
que la **conexión misma se corta** a medio enviar/recibir, y luego minos (u otra instancia)
vuelve a conectar.

## Requisitos

1. Simular una caída de conexión en un punto específico de una transacción en curso (ej. a medio
   mandar un `Abonos` grande particionado — depende de la spec 001), y luego una reconexión.
2. Definir y probar qué comportamiento es correcto para Banxico en ese escenario: ¿la transacción
   se considera perdida y hay que reiniciarla desde cero, o hay algún mecanismo de continuidad
   (ligado al historial de reenvío de la spec 003) que permita retomarla en la sesión nueva?

## Fuera de alcance

- Recuperación de estado del lado de minos — igual que en las specs 002/003, no se simula su
  lógica interna, solo se observa cómo reacciona.
- Persistencia de estado de sesión más allá del proceso del simulador (si el simulador mismo se
  reinicia, no solo la conexión TCP) — eso sería un escenario más amplio, fuera de esta spec.

## Diseño propuesto

- Reutilizar el mecanismo de corte abrupto en punto nombrado de la spec 005 ("cortarEn") para
  posicionar la caída exactamente a medio enviar/recibir un mensaje partido.
- Tras la caída, permitir disparar una reconexión nueva (misma sesión lógica de prueba, nueva
  conexión TCP) y verificar el estado resultante contra lo que se esperaría en un escenario real.

## Preguntas abiertas

- ¿Qué identifica "la misma transacción" entre la sesión cortada y la reconexión — la clave de
  rastreo, el folio, algo más? Necesario antes de poder verificar continuidad.
- ¿Existe ya, en el protocolo real, algún mecanismo pensado para esto (más allá de `Reenvio`
  dentro de una sesión viva), o una caída de conexión simplemente implica reiniciar la
  transacción desde cero en el mundo real también?

## Criterios de aceptación

- [ ] Se puede forzar una caída de conexión en un punto exacto a medio enviar/recibir una
      transacción, y luego reconectar como parte de la misma corrida de prueba.
- [ ] El comportamiento resultante (transacción perdida vs. recuperable) queda documentado como
      el comportamiento esperado y correcto, no solo observado.
