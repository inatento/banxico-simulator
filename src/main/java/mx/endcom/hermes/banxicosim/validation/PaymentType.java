package mx.endcom.hermes.banxicosim.validation;

import java.util.List;
import java.util.Set;

/**
 * Catálogo de campos por tipo de pago, para el subconjunto en alcance de v1 (ADR-005):
 * 01 Tercero a Tercero, 02 Tercero a Ventanilla, 05 Participante a Tercero, 12 Nómina.
 *
 * <p>Fuente de verdad: {@code judeca/src/main/resources/properties/pagos/pagos.properties}
 * (claves {@code tipo.NN.campos.all} y {@code tipo.NN.campos.optional}) y
 * {@code judeca/.../validator/CamposOrdenesValidator.java}, extraídos línea por línea
 * (ver investigación de esta tarea). NO se copian los 35 tipos restantes.</p>
 *
 * <p><b>Decisión de diseño (donde la spec no fue 100% explícita):</b> el campo "detalle" que
 * minos manda en {@code OrdenTopoV} por cada orden (bytes separados por 0x00, ver
 * {@code OrdenTopoV.java:167}: {@code order.getDetails().replace("|", "\0")}) sólo contiene los
 * campos <em>específicos del tipo de pago</em> ({@code campos.all} de pagos.properties) — NO los
 * 5 campos "comunes" ({@code tipoPg, cveRastreo, cveEmisor, cveReceptor, monto}) que
 * {@code TiposPagosUtil.obtenerCampos()} agrega para el validador de judeca. La razón: esos 5
 * campos ya viajan en otras partes del wire de {@code OrdenTopoV} (tipo de pago y monto por
 * orden, clave de rastreo por orden, claves de institución a nivel de todo el paquete) — sería
 * redundante repetirlos dentro del string de detalle. El simulador valida esos 5 por separado,
 * contra los campos del wire que ya trae OrdenTopoV, y valida el resto (los de esta tabla) contra
 * el contenido del string de detalle partido por 0x00, en el orden aquí declarado.</p>
 */
public enum PaymentType {

	TERCERO_A_TERCERO(1, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica", "referenciaCobranza"),
			Set.of("rfcBeneficiario", "iva", "referenciaCobranza")),

	TERCERO_A_VENTANILLA(2, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "conceptoPg", "cvePg", "iva"),
			Set.of("iva")),

	PARTICIPANTE_A_TERCERO(5, List.of(
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica"),
			Set.of("rfcBeneficiario", "iva")),

	NOMINA(12, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica", "referenciaCobranza"),
			Set.of("rfcBeneficiario", "iva", "referenciaCobranza"));

	private final int code;
	private final List<String> fieldsInOrder;
	private final Set<String> optionalFields;

	PaymentType(int code, List<String> fieldsInOrder, Set<String> optionalFields) {
		this.code = code;
		this.fieldsInOrder = fieldsInOrder;
		this.optionalFields = optionalFields;
	}

	public int code() {
		return code;
	}

	public List<String> fieldsInOrder() {
		return fieldsInOrder;
	}

	public boolean isOptional(String field) {
		return optionalFields.contains(field);
	}

	public static PaymentType byCode(int code) {
		for (PaymentType t : values()) {
			if (t.code == code) {
				return t;
			}
		}
		return null;
	}

	public static boolean isInScope(int code) {
		return byCode(code) != null;
	}
}
