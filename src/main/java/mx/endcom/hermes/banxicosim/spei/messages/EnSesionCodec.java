package mx.endcom.hermes.banxicosim.spei.messages;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Construye el cuerpo de {@code EnSesionMessage} (código 13), verificado campo por campo contra
 * {@code core/spei/dto/in/EnSesion.java#loadProperties} y {@code EnSesion.build()} del repo minos.
 *
 * <p>Estructura de entidades (ver {@code EnSesion.build()}, líneas 538-556): los arreglos
 * {@code entitiesCodes/entitiesNames/authorizedCertificatesByEntity/entitiesStatus/receptionsStatus}
 * van uno por entidad; {@code authorizedCertificatesByEntity[i]} es la CANTIDAD de certificados de
 * esa entidad (no un booleano); {@code certificatesNumbers/certificatesStatus} son un arreglo
 * PLANO de todos los certificados de todas las entidades, consumidos secuencialmente en el mismo
 * orden que las entidades. minos identifica "su propia" entidad comparando
 * {@code entitiesCodes[i] == Spei.entityCode} (su propio código configurado) — si esa entidad no
 * aparece en la lista, {@code Spei.myEntity} queda nulo y minos truena más adelante al armar
 * OrdenTopoV. Por eso el simulador SIEMPRE declara dos entidades: la propia (Banxico/simulador,
 * dueña de {@code serverCertificateNumber}) y la de minos (con el código que el operador configuró
 * en {@code minos.entityCode}).</p>
 *
 * <p>La matriz "internal certificate levels" ({@code internalCertificatesLevels}/{@code certificatesLevels})
 * sólo se consulta para certificados de la entidad cuyo código == Spei.entityCode (ver
 * {@code EnSesion.build()} línea 545-546 y {@code getCertificateLevels}). El simulador la deja
 * vacía (tamaños en 0): no aporta nada verificable para v1 y minos no la usa si está vacía.</p>
 */
public final class EnSesionCodec {

	private EnSesionCodec() {
	}

	public record EntityCert(int entityCode, String entityName, String certificateNumber) {
	}

	public static byte[] buildBody(
			LocalDate operationDate,
			int maxMessageLength,
			int maxCertificationLength,
			EntityCert ownEntity,
			EntityCert minosEntity,
			byte[] symmetricKeyPlaceholder,
			int cdeLength) {

		ByteWriter w = new ByteWriter();
		w.writeDateTime(LocalDateTime.now());
		w.writeIntBE(maxMessageLength);
		w.writeIntBE(maxCertificationLength);

		EntityCert[] entities = {ownEntity, minosEntity};
		w.writeIntBE(entities.length);
		for (EntityCert e : entities) {
			w.writeIntBE(e.entityCode());
		}
		for (EntityCert e : entities) {
			w.writeCString(e.entityName());
		}
		for (EntityCert e : entities) {
			w.writeChar((char) 1); // authorizedCertificatesByEntity: 1 certificado por entidad
		}
		for (EntityCert e : entities) {
			w.writeChar('A'); // entitiesStatus: activo (valor no validado por minos en el flujo revisado)
		}
		for (EntityCert e : entities) {
			w.writeChar('A'); // receptionsStatus: activo
		}

		w.writeIntBE(entities.length); // validCertificateSize (1 cert x 2 entidades)
		for (EntityCert e : entities) {
			w.writeCString(e.certificateNumber());
		}
		for (EntityCert e : entities) {
			w.writeChar('A'); // certificatesStatus
		}

		w.writeIntBE(0); // internalValidCertificateSize (ver nota de clase)
		// internalCertificatesLevels[]: 0 elementos, nada que escribir
		w.writeIntBE(0); // internalCertificatesLevelSize
		// certificatesLevels[]: 0 elementos

		w.writeMoney(BigDecimal.ZERO); // beginningBalance
		w.writeShortBE((short) 0); // maxChangeOfBalance
		w.writeMoney(BigDecimal.ZERO); // maxBalanceWithPriority
		w.writeDate(operationDate);
		w.writeCString(ownEntity.certificateNumber()); // serverCertificateNumber: el del simulador

		w.writeIntBE(symmetricKeyPlaceholder.length);
		w.writeBytes(symmetricKeyPlaceholder);
		w.writeIntBE(cdeLength);

		return w.toByteArray();
	}
}
