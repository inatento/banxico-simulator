package mx.endcom.hermes.banxicosim.spei.messages;

import java.util.List;

import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Cuerpo de {@code MsjCatalogosMessage} (código 31), verificado contra
 * {@code core/spei/dto/in/MsjCatalogos.java#loadProperties} y
 * {@code MsjCatalogosMessage.java:43-63} (orden real de campos) del repo minos.
 *
 * <p>v1 mandaba todos los catálogos vacíos (cero elementos en cada arreglo): el manejador de
 * minos ({@code SpeiInputManagerServiceImpl#processMsjCatalogosMessage}) sólo copia las listas a
 * variables estáticas y dispara un {@code Reenvio} — no valida contenido de catálogo alguno para
 * que la sesión avance, así que el cuerpo vacío basta para el handshake. Spec 008 agrega
 * {@link #buildPopulatedBody} para poder mandar catálogos con contenido real (institución, tipo
 * de cuenta) y confirmar si minos reacciona distinto — sigue sin haber evidencia de que lo haga,
 * ver spec 008 "Preguntas abiertas".</p>
 */
public final class MsjCatalogosCodec {

	private MsjCatalogosCodec() {
	}

	/** Una fila de catálogo: id, nombre, formato de columnas, nombres de columnas, y cuántos
	 *  renglones de datos declara (spec 008 no puebla los renglones en sí -- {@code
	 *  totalIterations}/{@code shortValues}/etc. quedan en 0, ver clase {@code MsjCatalogos} de
	 *  minos -- solo el encabezado del catálogo). */
	public record Catalog(short id, String name, String format, String columns, int rowCount) {
	}

	public static byte[] buildEmptyBody() {
		return buildPopulatedBody(List.of());
	}

	/** Arma el cuerpo con catálogos reales en el encabezado (id/nombre/formato/columnas/renglones
	 *  declarados), dejando los arreglos de valores (iteraciones, shortValues, intValues,
	 *  stringValues, charValues) en 0 -- suficiente para el alcance mínimo de la spec 008
	 *  (catálogos de institución/tipo de cuenta poblados, sin necesidad de poblar cada valor). */
	public static byte[] buildPopulatedBody(List<Catalog> catalogs) {
		ByteWriter w = new ByteWriter();
		w.writeIntBE(catalogs.size()); // totalCatalogues
		for (Catalog c : catalogs) {
			w.writeShortBE(c.id());
		}
		for (Catalog c : catalogs) {
			w.writeCString(c.name());
		}
		for (Catalog c : catalogs) {
			w.writeCString(c.format());
		}
		for (Catalog c : catalogs) {
			w.writeCString(c.columns());
		}
		for (Catalog c : catalogs) {
			w.writeIntBE(c.rowCount());
		}
		w.writeIntBE(0); // totalIterations
		w.writeIntBE(0); // totalShortValues
		w.writeIntBE(0); // totalIntValues
		w.writeIntBE(0); // totalStringValues
		w.writeIntBE(0); // totalCharValues
		return w.toByteArray();
	}

	/** Catálogos sintéticos de institución y tipo de cuenta (spec 008, alcance mínimo) -- valores
	 *  de prueba, no datos reales de producción (ver AGENTS.md &sect;6). */
	public static List<Catalog> syntheticCatalogs() {
		return List.of(
				new Catalog((short) 1, "INSTITUCION", "N", "clave,nombre", 0),
				new Catalog((short) 2, "TIPO_CUENTA", "N", "clave,descripcion", 0));
	}
}
