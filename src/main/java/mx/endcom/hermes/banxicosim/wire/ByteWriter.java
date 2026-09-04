package mx.endcom.hermes.banxicosim.wire;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Escritor de campos binarios en el formato exacto que minos espera, verificado
 * contra {@code util/impl/BytesUtil.java} y {@code core/spei/type/Spei{Date,DateTime,Money}.java}
 * del código fuente real de minos (repo minos, 2026-08-31):
 *
 * <ul>
 *   <li>Enteros: big-endian, sin signo aparente (short=2 bytes, int=4 bytes).</li>
 *   <li>Cadenas: terminadas en byte 0x00, codificadas en ISO-8859-1
 *       (ver {@code core/ara/message/AraMessage.java#readString} y
 *       {@code core/spei/message/SpeiMessage.java}, que usan
 *       {@code StandardCharsets.ISO_8859_1}). minos también hay casos con
 *       {@code String.getBytes()} (plataforma por defecto) en {@code BytesUtil.stringToBytesWithEnd};
 *       para bytes ASCII (base64, hex, dígitos) da el mismo resultado que ISO-8859-1.</li>
 *   <li>Fecha (4 bytes): día(1) + mes(1) + año(2, BE).</li>
 *   <li>Fecha-hora (7 bytes): día(1) + mes(1) + año(2, BE) + hora(1) + minuto(1) + segundo(1).</li>
 *   <li>Dinero (8 bytes): ver {@link MoneyCodec}.</li>
 * </ul>
 */
public final class ByteWriter {

	private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

	public ByteWriter() {
	}

	public ByteWriter writeByte(int value) {
		buf.write(value & 0xFF);
		return this;
	}

	public ByteWriter writeChar(char value) {
		buf.write(value & 0xFF);
		return this;
	}

	public ByteWriter writeShortBE(short value) {
		buf.write((value >> 8) & 0xFF);
		buf.write(value & 0xFF);
		return this;
	}

	public ByteWriter writeShortBE(int value) {
		return writeShortBE((short) value);
	}

	public ByteWriter writeIntBE(int value) {
		buf.write((value >>> 24) & 0xFF);
		buf.write((value >>> 16) & 0xFF);
		buf.write((value >>> 8) & 0xFF);
		buf.write(value & 0xFF);
		return this;
	}

	/** Cadena terminada en 0x00, ISO-8859-1 (ver AraMessage.readString / SpeiMessage lectura de strings). */
	public ByteWriter writeCString(String value) {
		try {
			buf.write(value.getBytes(StandardCharsets.ISO_8859_1));
			buf.write(0x00);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	public ByteWriter writeBytes(byte[] bytes) {
		try {
			buf.write(bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	/** Fecha SPEI: día(1) + mes(1) + año(2, BE). Ver SpeiDate/BytesUtil.dateStrToBytes. */
	public ByteWriter writeDate(LocalDate date) {
		writeByte(date.getDayOfMonth());
		writeByte(date.getMonthValue());
		writeShortBE((short) date.getYear());
		return this;
	}

	/** Fecha-hora SPEI: día(1)+mes(1)+año(2,BE)+hora(1)+min(1)+seg(1). Ver SpeiDateTime.of/now. */
	public ByteWriter writeDateTime(LocalDateTime dt) {
		writeByte(dt.getDayOfMonth());
		writeByte(dt.getMonthValue());
		writeShortBE((short) dt.getYear());
		writeByte(dt.getHour());
		writeByte(dt.getMinute());
		writeByte(dt.getSecond());
		return this;
	}

	/** Monto SPEI (8 bytes). Ver {@link MoneyCodec}. */
	public ByteWriter writeMoney(BigDecimal amount) {
		writeBytes(MoneyCodec.encode(amount));
		return this;
	}

	public byte[] toByteArray() {
		return buf.toByteArray();
	}

	public int size() {
		return buf.size();
	}
}
