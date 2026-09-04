package mx.endcom.hermes.banxicosim.wire;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lector de campos binarios, contraparte de {@link ByteWriter}. Mismas convenciones que minos
 * (ver {@code core/spei/message/SpeiMessage.java} y {@code core/ara/message/AraMessage.java}).
 */
public final class ByteReader {

	private final ByteArrayInputStream in;

	public ByteReader(byte[] data) {
		this.in = new ByteArrayInputStream(data);
	}

	public int remaining() {
		return in.available();
	}

	public int readUnsignedByte() {
		int b = in.read();
		if (b < 0) {
			throw new IllegalStateException("Fin de flujo inesperado");
		}
		return b;
	}

	public char readChar() {
		return (char) readUnsignedByte();
	}

	public short readShortBE() {
		int a = readUnsignedByte();
		int b = readUnsignedByte();
		return (short) ((a << 8) | b);
	}

	public int readUnsignedShortBE() {
		return readShortBE() & 0xFFFF;
	}

	public int readIntBE() {
		int a = readUnsignedByte();
		int b = readUnsignedByte();
		int c = readUnsignedByte();
		int d = readUnsignedByte();
		return (a << 24) | (b << 16) | (c << 8) | d;
	}

	/** Cadena terminada en 0x00, ISO-8859-1. */
	public String readCString() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) > 0) {
			out.write(b);
		}
		if (b < 0) {
			throw new IllegalStateException("Fin de flujo antes de encontrar terminador 0x00");
		}
		return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
	}

	public byte[] readBytes(int n) {
		if (n == 0) {
			// ByteArrayInputStream.read(buf) regresa -1 (no 0) si el stream ya se agotó,
			// incluso pidiendo longitud 0 -- caso real al final de un mensaje sin cola opcional.
			return new byte[0];
		}
		byte[] buf = new byte[n];
		try {
			int read = in.read(buf);
			if (read != n) {
				throw new IllegalStateException("Se esperaban " + n + " bytes, se leyeron " + read);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return buf;
	}

	public LocalDate readDate() {
		int day = readUnsignedByte();
		int month = readUnsignedByte();
		int year = readUnsignedShortBE();
		return LocalDate.of(year, month, day);
	}

	public LocalDateTime readDateTime() {
		int day = readUnsignedByte();
		int month = readUnsignedByte();
		int year = readUnsignedShortBE();
		int hour = readUnsignedByte();
		int minute = readUnsignedByte();
		int second = readUnsignedByte();
		return LocalDateTime.of(year, month, day, hour, minute, second);
	}

	public BigDecimal readMoney() {
		return MoneyCodec.decode(readBytes(8));
	}

	public short[] readShortArray(int n) {
		short[] arr = new short[n];
		for (int i = 0; i < n; i++) {
			arr[i] = readShortBE();
		}
		return arr;
	}

	public int[] readIntArray(int n) {
		int[] arr = new int[n];
		for (int i = 0; i < n; i++) {
			arr[i] = readIntBE();
		}
		return arr;
	}

	public char[] readCharArray(int n) {
		char[] arr = new char[n];
		for (int i = 0; i < n; i++) {
			arr[i] = readChar();
		}
		return arr;
	}

	public String[] readCStringArray(int n) {
		String[] arr = new String[n];
		for (int i = 0; i < n; i++) {
			arr[i] = readCString();
		}
		return arr;
	}
}
