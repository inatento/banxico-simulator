package mx.endcom.hermes.banxicosim.spei.messages;

import java.math.BigDecimal;
import java.security.PrivateKey;
import java.time.LocalDate;
import java.time.LocalDateTime;

import mx.endcom.hermes.banxicosim.crypto.RsaCipher;
import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Construye el payload de {@code AbonosMessage} (código 25, simulador -&gt; minos, abono
 * entrante) — Fase 5. Verificado contra {@code core/spei/dto/in/Abonos.java#build()} +
 * {@code AbonosMessage.java#loadProperties} (líneas 55-76) y el registro embebido
 * {@code core/spei/message/in/AbonoInceptionMessage.java#loadProperties} (líneas 43-78) del repo
 * minos. La envoltura (cifrado+firma+prefijo) se arma aparte con
 * {@code WireFraming.buildEncryptedSignedPartitioned}.
 *
 * <p><b>Detalle no documentado en la spec técnica, encontrado en el código:</b>
 * {@code AbonosMessage.loadProperties} lee el {@code serverTimestamp} con
 * {@code readDateTimeParticion}, que primero descarta 4 bytes de "basura" antes de leer la
 * fecha-hora de 7 bytes (ver {@code SpeiMessage.java:362-368}) — a diferencia de
 * {@code AcuseRecibo}/{@code OrdenTopoV}, que usan {@code readDateTime} normal sin ese
 * relleno.</p>
 *
 * <p>v1 manda exactamente 1 "abonoT" (entidad receptora) y 1 "abonoV" (detalle de pago), lo
 * suficiente para demostrar la ruta de abono simple, válido o inválido a propósito.</p>
 *
 * <p><b>Desalineamiento de 4 bytes (encontrado y corregido el 2026-09-18, reportado por
 * Miguel):</b> un {@code NegativeArraySizeException} en {@code Abonos.build()} salía idéntico en
 * abonos con contenido distinto -- señal de un desalineamiento estructural, no de datos. Rastreando
 * la cadena real de clases de minos ({@code SpeiInputEncryptedPartitionedMessage} →
 * {@code SpeiInputEncryptedAndSignedPartitionedMessage.verifySignature()} → {@code AbonosMessage
 * .loadProperties()}) se confirmó que minos <b>no usa el body ya desenvuelto</b> que calcula
 * {@code SpeiInputSignedMessage} (ese cálculo se descarta) -- {@code loadProperties()} lee
 * directo del {@code bodyStream} al que sólo se le consumió el prefijo de {@code totalSize}
 * (2 bytes). Eso significa que los "4 bytes de basura" que descarta {@code readDateTimeParticion}
 * son, en el minos real de hoy, el campo {@code signatureSize}(4) de la envoltura firmada
 * ({@code WireFraming.signedBlock()}) -- no un padding propio del payload. Antes de esta
 * corrección, {@code buildPayload()} <b>también</b> agregaba su propio padding de 4 bytes al
 * inicio, duplicando el descarte y corriendo cada campo posterior 4 bytes.
 * <b>minos es el ambiente real (habla con Banxico en producción) y no se toca por esto</b> -- el
 * comportamiento correcto para este simulador es igualar lo que minos consume hoy, aunque
 * técnicamente sea una inconsistencia en su propio código (no propagar el body desenvuelto). Se
 * quitó el padding propio del payload; el {@code signatureSize} de la envoltura hace ese papel.</p>
 *
 * <p><b>Límite real de {@code detail}:</b> tanto el tamaño interno del detalle dentro de
 * {@code AbonoV} como {@code detailSizes[0]} a nivel {@code Abonos} son campos {@code short} en
 * el wire real ({@code readArrayShort}). Un {@code detail} de más de {@code Short.MAX_VALUE}
 * bytes desbordaría ese cast en silencio -- ambos puntos validan el límite en vez de truncar
 * (esto no fue la causa del bug de arriba, pero es un riesgo real aparte).</p>
 */
public final class AbonosCodec {

	private AbonosCodec() {
	}

	public record AbonoTRef(int entityIndex, int entityCode, int instructionFolio, short internalFolio) {
	}

	/**
	 * Qué firma poner en el {@code AbonoV} (spec 004 -- escenarios de firma). {@code VALIDA} es el
	 * único camino usado antes de la spec 004 ({@code signProperly=true}); {@code VACIA} es el
	 * relleno de 32 ceros que ya existía ({@code signProperly=false}); {@code CORRUPTA} es nueva:
	 * una firma real (mismo tamaño/codificación que una válida) con un byte alterado -- distingue
	 * "estructura sin firma" (VACIA) de "firma con formato correcto pero inválida" (CORRUPTA), que
	 * es el caso más realista de corrupción en tránsito.
	 */
	public enum SignatureMode {
		VALIDA, VACIA, CORRUPTA
	}

	/** Un registro AbonoV embebido: quién manda, quién recibe, monto, tipo de pago y detalle. */
	public record AbonoVSpec(
			LocalDate operationDate,
			int indexIssuer,
			int issuerId,
			int indexRecipient,
			int receptionId,
			int idPack,
			int idCertificate,
			boolean priority,
			BigDecimal amount,
			int paymentType,
			String trackingKey,
			String detail,
			SignatureMode signatureMode) {
	}

	/** Arma el registro AbonoV completo, incluida su propia firma (real o de relleno — ver
	 *  {@code AbonoInceptionMessage}: la verificación de esta firma NO bloquea el mensaje, sólo
	 *  se registra {@code signVerified=false} si falla). */
	public static byte[] buildAbonoV(AbonoVSpec spec, PrivateKey signingKey) throws Exception {
		ByteWriter head = new ByteWriter();
		// sizeSign se escribe al final, una vez que sabemos el tamaño real de la firma.
		ByteWriter body = new ByteWriter();
		body.writeDateTime(LocalDateTime.now());
		body.writeDate(spec.operationDate());
		body.writeChar((char) spec.indexIssuer());
		body.writeIntBE(spec.issuerId());
		body.writeChar((char) spec.indexRecipient());
		body.writeIntBE(spec.receptionId());
		body.writeIntBE(spec.idPack());
		body.writeChar((char) spec.idCertificate());
		body.writeChar((char) (spec.priority() ? 1 : 0));
		body.writeIntBE(1); // ordersQuantity: una sola orden por abonoV en v1
		body.writeShortBE((short) 1); // internalsIds[0]
		// arrayAmounts: formato "izquierdas luego derechas" (ver OrdenTopoVCodec, misma convención)
		byte[] money = mx.endcom.hermes.banxicosim.wire.MoneyCodec.encode(spec.amount());
		body.writeBytes(new byte[]{money[0], money[1], money[2], money[3]}); // left
		body.writeBytes(new byte[]{money[4], money[5], money[6], money[7]}); // right
		body.writeShortBE((short) spec.paymentType());
		byte[] detailBytes = spec.detail().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
		// detailBytes.length se manda dos veces en el wire real: aquí como short (campo interno del
		// AbonoV) y abajo como int (detailTotalSize a nivel Abonos). El campo short es el límite real
		// -- un detail de más de Short.MAX_VALUE bytes desborda a negativo al castear y corrompe el
		// mensaje en silencio (Abonos.build() del lado de minos revienta con NegativeArraySizeException
		// al leer ese negativo como tamaño de arreglo). Se valida en vez de truncar.
		if (detailBytes.length > Short.MAX_VALUE) {
			throw new IllegalArgumentException("detail de AbonoV excede " + Short.MAX_VALUE
					+ " bytes (" + detailBytes.length + ") -- el campo de tamaño en el wire es un short.");
		}
		body.writeShortBE((short) detailBytes.length);
		body.writeCString(spec.trackingKey());
		body.writeIntBE(detailBytes.length);
		body.writeBytes(detailBytes);
		body.writeIntBE(0); // errorCodeStringSize: sin código de error en v1

		byte[] signable = body.toByteArray();
		byte[] signatureBytes = switch (spec.signatureMode()) {
			// RSASSA-PSS/SHA-512, no SHA256withRSA -- ver RsaCipher.SIGN_ALGO_PSS: minos verifica
			// la firma de AbonoV (AbonoInceptionMessage) con CipherBase64.verifySignantureRSASSA(...,
			// true), que es RSASSA-PSS/SHA-512, no el SHA256withRSA que este código pedía antes.
			case VALIDA -> RsaCipher.signRSASSAPSS(signable, signingKey);
			case VACIA -> new byte[32]; // firma de relleno: prueba la ruta estructural, no la criptográfica
			// Firma real (mismo tamaño/codificación que VALIDA) con un byte alterado -- spec 004:
			// distinta de VACIA porque tiene formato correcto, solo el contenido es inválido.
			case CORRUPTA -> corrupt(RsaCipher.signRSASSAPSS(signable, signingKey));
		};

		return new ByteWriter()
				.writeIntBE(signatureBytes.length)
				.writeBytes(signable)
				.writeBytes(signatureBytes)
				.toByteArray();
	}

	/** Voltea el último byte de una firma real -- mismo tamaño, contenido inválido (spec 004). */
	private static byte[] corrupt(byte[] realSignature) {
		byte[] copy = realSignature.clone();
		copy[copy.length - 1] ^= 0x01;
		return copy;
	}

	public static byte[] buildPayload(
			LocalDate operationDate,
			int folio,
			AbonoTRef abonoT,
			BigDecimal amountTopoT,
			byte[] abonoVBytes,
			BigDecimal amountTopoV,
			BigDecimal balance,
			BigDecimal reservedBalance) {

		ByteWriter w = new ByteWriter();
		// NO llevar un padding propio de 4 bytes aquí (se quitó el 2026-09-18 -- ver nota de clase
		// "Desalineamiento de 4 bytes"). AbonosMessage.loadProperties() del lado de minos NO usa el
		// body ya desenvuelto por SpeiInputSignedMessage -- lee directo del bodyStream que solo tiene
		// consumido el prefijo de totalSize (2 bytes), así que los "4 bytes de basura" que descarta
		// readDateTimeParticion() son en realidad el campo signatureSize(4) de WireFraming.signedBlock(),
		// no un padding propio del payload. Agregar uno aquí duplica el descarte y desalinea todo lo
		// que sigue por exactamente 4 bytes.
		w.writeDateTime(LocalDateTime.now());
		w.writeDate(operationDate);
		w.writeIntBE(folio);

		w.writeIntBE(1); // abonosT: una sola referencia de entidad receptora
		w.writeChar((char) abonoT.entityIndex());
		w.writeIntBE(abonoT.entityCode());
		w.writeIntBE(abonoT.instructionFolio());
		w.writeShortBE(abonoT.internalFolio());
		w.writeMoney(amountTopoT);

		w.writeIntBE(1); // abonosV: un solo detalle de pago
		// detailSizes[0] en el wire real es un short (ver Abonos.build()/AbonosMessage.loadProperties
		// del lado de minos: readArrayShort). Un abonoVBytes.length que exceda Short.MAX_VALUE
		// desborda a negativo al castear, y minos revienta con NegativeArraySizeException al intentar
		// `new byte[detailSizes[i]]` -- exactamente el crash reportado por Miguel el 2026-09-18 al
		// mandar un abono con un detail largo. Se valida en vez de truncar en silencio.
		if (abonoVBytes.length > Short.MAX_VALUE) {
			throw new IllegalArgumentException("abonoV excede " + Short.MAX_VALUE
					+ " bytes (" + abonoVBytes.length + ") -- detailSizes en el wire real es un short.");
		}
		w.writeShortBE((short) abonoVBytes.length);
		w.writeIntBE(abonoVBytes.length); // detailTotalSize
		w.writeBytes(abonoVBytes);
		w.writeMoney(amountTopoV);
		w.writeMoney(balance);
		w.writeMoney(reservedBalance);

		return w.toByteArray();
	}
}
