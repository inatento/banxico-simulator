package mx.endcom.hermes.banxicosim.spei.messages;

import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Cuerpo de {@code MsjCatalogosMessage} (código 31), verificado contra
 * {@code core/spei/dto/in/MsjCatalogos.java#loadProperties} (llamado desde
 * {@code MsjCatalogosMessage.java:43-63}) del repo minos.
 *
 * <p>v1 manda todos los catálogos vacíos (cero elementos en cada arreglo): el manejador de minos
 * ({@code SpeiInputManagerServiceImpl#processMsjCatalogosMessage}) sólo copia las listas a
 * variables estáticas y dispara un {@code Reenvio} — no valida contenido de catálogo alguno para
 * que la sesión avance. Si una prueba futura necesita catálogos reales (tipos de cuenta,
 * instituciones, etc.), agregarlos aquí siguiendo el mismo orden de campos.</p>
 */
public final class MsjCatalogosCodec {

	private MsjCatalogosCodec() {
	}

	public static byte[] buildEmptyBody() {
		ByteWriter w = new ByteWriter();
		w.writeIntBE(0); // totalCatalogues (+ catalogueIds/Names/Format/Columns/Rows: 0 elementos)
		w.writeIntBE(0); // totalIterations
		w.writeIntBE(0); // totalShortValues
		w.writeIntBE(0); // totalIntValues
		w.writeIntBE(0); // totalStringValues
		w.writeIntBE(0); // totalCharValues
		return w.toByteArray();
	}
}
