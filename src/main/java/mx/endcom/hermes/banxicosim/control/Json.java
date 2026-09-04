package mx.endcom.hermes.banxicosim.control;

import java.util.List;
import java.util.Map;

/**
 * Serializador JSON mínimo, escrito a mano -- sin agregar una dependencia nueva (ver AGENTS.md
 * &sect;4, "sin frameworks"). Sólo soporta los tipos que necesita la API de control:
 * {@link Map} (objeto), {@link List} (arreglo), {@link String}, {@link Number}, {@link Boolean}
 * y {@code null}. No es un serializador de propósito general -- si algún endpoint necesita más,
 * hay que ampliarlo aquí, no reintroducir un tipo nuevo por fuera.
 */
final class Json {

	private Json() {
	}

	static String write(Object value) {
		StringBuilder sb = new StringBuilder();
		writeValue(value, sb);
		return sb.toString();
	}

	private static void writeValue(Object value, StringBuilder sb) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof Map<?, ?> map) {
			writeObject(map, sb);
		} else if (value instanceof List<?> list) {
			writeArray(list, sb);
		} else if (value instanceof String s) {
			writeString(s, sb);
		} else if (value instanceof Number || value instanceof Boolean) {
			sb.append(value);
		} else {
			// Enums u otros tipos simples: se serializan por su representación de texto.
			writeString(value.toString(), sb);
		}
	}

	private static void writeObject(Map<?, ?> map, StringBuilder sb) {
		sb.append('{');
		boolean first = true;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			writeString(String.valueOf(entry.getKey()), sb);
			sb.append(':');
			writeValue(entry.getValue(), sb);
		}
		sb.append('}');
	}

	private static void writeArray(List<?> list, StringBuilder sb) {
		sb.append('[');
		boolean first = true;
		for (Object item : list) {
			if (!first) {
				sb.append(',');
			}
			first = false;
			writeValue(item, sb);
		}
		sb.append(']');
	}

	private static void writeString(String s, StringBuilder sb) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append('"');
	}
}
