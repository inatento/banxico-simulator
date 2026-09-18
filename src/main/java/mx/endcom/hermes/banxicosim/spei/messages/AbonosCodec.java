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
 * relleno. El simulador reproduce ese relleno fielmente para que el mensaje no se desalinee.</p>
 *
 * <p>v1 manda exactamente 1 "abonoT" (entidad receptora) y 1 "abonoV" (detalle de pago), lo
 * suficiente para demostrar la ruta de abono simple, válido o inválido a propósito.</p>
 */
public final class AbonosCodec {

	private AbonosCodec() {
	}

	public record AbonoTRef(int entityIndex, int entityCode, int instructionFolio, short internalFolio) {
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
			boolean signProperly) {
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
		// detailsArraySizes[0]: longitud en BYTES tras codificar a ISO-8859-1, no spec.detail().length()
		// (conteo de caracteres/UTF-16) -- para texto ASCII ambos coinciden numéricamente, pero
		// detailsArraySizes debe reflejar el tamaño real del blob que se manda, no el conteo de
		// caracteres de la cadena Java original. Ver AbonoInceptionMessage.java:59 (minos) para el
		// campo equivalente.
		body.writeShortBE((short) detailBytes.length);
		body.writeCString(spec.trackingKey());
		body.writeIntBE(detailBytes.length);
		body.writeBytes(detailBytes);
		body.writeIntBE(0); // errorCodeStringSize: sin código de error en v1

		byte[] signable = body.toByteArray();
		byte[] signatureBytes;
		if (spec.signProperly()) {
			signatureBytes = RsaCipher.sign(signable, signingKey);
		} else {
			signatureBytes = new byte[32]; // firma de relleno: prueba la ruta estructural, no la criptográfica
		}

		return new ByteWriter()
				.writeIntBE(signatureBytes.length)
				.writeBytes(signable)
				.writeBytes(signatureBytes)
				.toByteArray();
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
		w.writeIntBE(0); // 4 bytes de relleno que minos descarta (readDateTimeParticion), ver nota de clase
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
		w.writeShortBE((short) abonoVBytes.length);
		w.writeIntBE(abonoVBytes.length); // detailTotalSize
		w.writeBytes(abonoVBytes);
		w.writeMoney(amountTopoV);
		w.writeMoney(balance);
		w.writeMoney(reservedBalance);

		return w.toByteArray();
	}
}
