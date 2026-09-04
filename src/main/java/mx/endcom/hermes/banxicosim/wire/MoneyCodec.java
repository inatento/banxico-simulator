package mx.endcom.hermes.banxicosim.wire;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Codifica/decodifica el formato de 8 bytes que minos usa para montos ("SpeiMoney").
 *
 * Verificado contra {@code core/spei/type/SpeiMoney.java} del código fuente real de minos:
 * <ul>
 *   <li>Los 8 bytes son dos enteros big-endian de 4 bytes cada uno: {@code left} y {@code right}.</li>
 *   <li>Para decodificar: se concatena {@code String.format("%d%09d", left, right)} — es decir,
 *       el entero {@code left} seguido del entero {@code right} rellenado a 9 dígitos con ceros —
 *       y luego se inserta un punto decimal a 2 posiciones del final de esa cadena de dígitos.</li>
 *   <li>Para codificar (ver {@code SpeiMoney(String)}): se toma el monto como cadena de dígitos
 *       sin punto (p.ej. "1234.56" -&gt; "123456"); si esa cadena de dígitos cabe en 9 dígitos
 *       (&lt;= 999999999) todo va en {@code right} y {@code left} queda en 0; si no, los dígitos
 *       se parten dejando los últimos 9 en {@code right} y el resto en {@code left}.</li>
 * </ul>
 * Nota: este esquema permite representar más de 2 decimales en teoría (el "punto" siempre se
 * inserta a 2 posiciones del final de la cadena completa), pero en la práctica todos los montos
 * SPEI usan 2 decimales, que es lo único que este codec expone.
 */
public final class MoneyCodec {

	private MoneyCodec() {
	}

	public static byte[] encode(BigDecimal amount) {
		BigDecimal scaled = amount.setScale(2, RoundingMode.DOWN);
		// Cadena de puros dígitos (sin signo, sin punto): p.ej. 1234.56 -> "123456"
		String digits = scaled.movePointRight(2).toBigInteger().abs().toString();
		digits = digits.replaceFirst("^0+(?=.)", ""); // quita ceros a la izquierda, deja al menos 1 dígito
		int left;
		int right;
		if (digits.length() <= 9) {
			left = 0;
			right = Integer.parseInt(digits);
		} else {
			int splitAt = digits.length() - 9;
			left = Integer.parseInt(digits.substring(0, splitAt));
			right = Integer.parseInt(digits.substring(splitAt));
		}
		return new ByteWriter().writeIntBE(left).writeIntBE(right).toByteArray();
	}

	public static BigDecimal decode(byte[] eightBytes) {
		if (eightBytes.length != 8) {
			throw new IllegalArgumentException("Se esperaban 8 bytes, se recibieron " + eightBytes.length);
		}
		int left = ((eightBytes[0] & 0xFF) << 24) | ((eightBytes[1] & 0xFF) << 16)
				| ((eightBytes[2] & 0xFF) << 8) | (eightBytes[3] & 0xFF);
		int right = ((eightBytes[4] & 0xFF) << 24) | ((eightBytes[5] & 0xFF) << 16)
				| ((eightBytes[6] & 0xFF) << 8) | (eightBytes[7] & 0xFF);
		String digits = String.format("%d%09d", left, right);
		String withPoint = digits.substring(0, digits.length() - 2) + "." + digits.substring(digits.length() - 2);
		return new BigDecimal(withPoint).setScale(2, RoundingMode.DOWN);
	}

	/** Para pruebas: monto cero, útil como relleno en campos de saldo que minos no valida en v1. */
	public static byte[] zero() {
		return new byte[8];
	}
}
