package mx.endcom.hermes.banxicosim.spei.messages;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import mx.endcom.hermes.banxicosim.wire.ByteReader;
import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Tras {@code MsjCatalogos}, minos SIEMPRE manda {@code Reenvio} (código 207, AES simple sin
 * firmar ni particionar — ver {@code core/spei/message/out/ReenvioMessage.java} y
 * {@code SpeiOutputEncryptedMessage}) preguntando desde qué byte debe reenviar; el simulador debe
 * responder {@code FinReenvio} (código 32, AES simple — ver
 * {@code core/spei/message/in/FinReenvioMessage.java:29-37}) para que la sesión quede operativa.
 * Esto no está en el resumen de fases de la spec técnica pero SÍ es necesario en el código real
 * (verificado: {@code processMsjCatalogosMessage} llama a {@code sendReenvio} incondicionalmente).
 *
 * <p>v1 simplifica: siempre responde "no hay nada que reenviar" (contadores y saldos en cero) —
 * suficiente para una sesión de prueba que arranca limpia en cada corrida. No implementa
 * reenvío real de mensajes previos.</p>
 */
public final class ReenvioCodec {

	private ReenvioCodec() {
	}

	public record Reenvio(LocalDateTime timestamp, int processedBytes) {
	}

	public static Reenvio parse(byte[] plaintextBody) {
		ByteReader r = new ByteReader(plaintextBody);
		LocalDateTime ts = r.readDateTime();
		int processedBytes = r.readIntBE();
		return new Reenvio(ts, processedBytes);
	}

	public static byte[] buildFinReenvioBody() {
		return new ByteWriter()
				.writeDateTime(LocalDateTime.now())
				.writeIntBE(0) // bytesServerToClient
				.writeMoney(BigDecimal.ZERO) // totalBalance
				.writeIntBE(0) // bytesClientToServer
				.writeMoney(BigDecimal.ZERO) // reservedBalance
				.toByteArray();
	}
}
