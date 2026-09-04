package mx.endcom.hermes.banxicosim.validation;

import java.util.List;
import java.util.Set;

/**
 * Catálogo de campos por tipo de pago — los 35 tipos reales del catálogo SPEI (códigos 0-36, se
 * saltan 13 y 14).
 *
 * <p><b>Esto excede a propósito el alcance que
 * <a href="../../../../../../../../../HERMES-MKI-VOBEDA/ADRs/ADR_005_Simulador-SPEI-protocolo-real.md">ADR-005</a>
 * fijó para v1</b> (4 tipos representativos: 01, 02, 05, 12). Fue una decisión explícita del dueño
 * de este repo, tomada el 2026-09-03, para ampliar la validación de contenido de {@code OrdenTopoV}
 * a los 35 tipos completos — no una corrección silenciosa de esa ADR. El ADR-005 en sí **no se ha
 * actualizado** y esta ampliación **no se ha confirmado con Pedro** (dueño de la decisión original).
 * Ver README.md, sección "Tipos de pago disponibles para probar", para el detalle de la divergencia
 * pendiente de reconciliar formalmente.</p>
 *
 * <p>Fuente de verdad: {@code judeca/src/main/resources/properties/pagos/pagos.properties}
 * (ISO-8859-1; claves {@code tipo.NN.campos.all}, {@code tipo.NN.campos.optional} y
 * {@code tipo.NN.nombre}) y {@code judeca/.../validator/CamposOrdenesValidator.java}. Los nombres
 * de campo y su orden se copiaron literalmente de {@code campos.all}; los opcionales, literalmente
 * de {@code campos.optional} (conjunto vacío cuando esa clave no existe para el tipo, p. ej. 00 y
 * 17). No se infirió ni se completó nada a mano.</p>
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

	DEVOLUCION_NO_ACREDITADA(0, List.of(
			"cveRastreoPgOriginal", "causaDevolucion"),
			Set.of()),

	TERCERO_A_TERCERO(1, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica", "referenciaCobranza"),
			Set.of("rfcBeneficiario", "iva", "referenciaCobranza")),

	TERCERO_A_VENTANILLA(2, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "conceptoPg", "cvePg", "iva"),
			Set.of("iva")),

	TERCERO_A_TERCERO_VOSTRO(3, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario",
			"nombreBeneficiario2", "rfcBeneficiario2", "tipoCtaBeneficiario2", "ctaBeneficiario2",
			"conceptoPg", "conceptoPg2", "iva", "referenciaNumerica"),
			Set.of("nombreBeneficiario2", "rfcBeneficiario2", "tipoCtaBeneficiario2",
					"ctaBeneficiario2", "conceptoPg2", "iva")),

	TERCERO_A_PARTICIPANTE(4, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"tipoOperacion", "conceptoPg", "iva", "referenciaNumerica"),
			Set.of("rfcOrdenante", "iva")),

	PARTICIPANTE_A_TERCERO(5, List.of(
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica"),
			Set.of("rfcBeneficiario", "iva")),

	PARTICIPANTE_A_TERCERO_VOSTRO(6, List.of(
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario",
			"nombreBeneficiario2", "rfcBeneficiario2", "tipoCtaBeneficiario2", "ctaBeneficiario2",
			"conceptoPg", "conceptoPg2", "iva", "referenciaNumerica"),
			Set.of("nombreBeneficiario2", "rfcBeneficiario2", "tipoCtaBeneficiario2",
					"ctaBeneficiario2", "conceptoPg2", "iva")),

	PARTICIPANTE_A_PARTICIPANTE(7, List.of(
			"tipoOperacion", "conceptoPg", "iva", "referenciaNumerica"),
			Set.of("iva")),

	TERCERO_A_TERCERO_FSW(8, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica", "referenciaCobranza"),
			Set.of("tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante", "tipoCtaBeneficiario",
					"ctaBeneficiario", "rfcBeneficiario", "iva", "referenciaCobranza")),

	TERCERO_A_TERCERO_VOSTRO_FSW(9, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario",
			"nombreBeneficiario2", "rfcBeneficiario2", "tipoCtaBeneficiario2", "ctaBeneficiario2",
			"conceptoPg", "conceptoPg2", "iva", "referenciaNumerica"),
			Set.of("tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante", "tipoCtaBeneficiario",
					"ctaBeneficiario", "nombreBeneficiario2", "rfcBeneficiario2",
					"tipoCtaBeneficiario2", "ctaBeneficiario2", "conceptoPg2", "iva")),

	PARTICIPANTE_A_TERCERO_FSW(10, List.of(
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica"),
			Set.of("tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario", "iva")),

	PARTICIPANTE_A_TERCERO_VOSTRO_FSW(11, List.of(
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario",
			"nombreBeneficiario2", "rfcBeneficiario2", "tipoCtaBeneficiario2", "ctaBeneficiario2",
			"conceptoPg", "conceptoPg2", "iva", "referenciaNumerica"),
			// pagos.properties lista conceptoPg y referenciaNumerica como "opcionales" para el
			// tipo 11 (tipo.11.campos.optional), a pesar de estar en campos.all — copiado
			// literal, no es un error de trascripción.
			Set.of("tipoCtaBeneficiario", "ctaBeneficiario", "nombreBeneficiario2",
					"rfcBeneficiario2", "tipoCtaBeneficiario2", "ctaBeneficiario2", "conceptoPg",
					"conceptoPg2", "iva", "referenciaNumerica")),

	NOMINA(12, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica", "referenciaCobranza"),
			Set.of("rfcBeneficiario", "iva", "referenciaCobranza")),

	PAGO_FACTURA(15, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "referenciaNumerica", "infoFacturas"),
			Set.of("rfcBeneficiario")),

	DEVOLUCION_EXTEMPORANEA_NO_ACREDITADA(16, List.of(
			"folioInstruccionOriginal", "folioPgOriginal", "fechaOrdenTransOriginal",
			"cveRastreoPgOriginal", "refNumericaOriginal", "tipoCtaOrdenanteOriginal",
			"ctaOrdenantePgOriginal", "causaDevolucion", "conceptoPgOriginal", "montoPgOriginal",
			"montoIntereses"),
			Set.of("folioInstruccionOriginal", "folioPgOriginal")),

	DEVOLUCION_ACREDITADA(17, List.of(
			"cveRastreoPgOriginal"),
			Set.of()),

	DEVOLUCION_EXTEMPORANEA_ACREDITADA(18, List.of(
			"folioInstruccionOriginal", "folioPgOriginal", "fechaOrdenTransOriginal",
			"cveRastreoPgOriginal", "refNumericaOriginal", "tipoCtaOrdenanteOriginal",
			"ctaOrdenantePgOriginal", "conceptoPgOriginal", "montoPgOriginal"),
			Set.of("folioInstruccionOriginal", "folioPgOriginal")),

	CODI_COBRO_PRESENCIAL_UNICO(19, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"numeroCelularOrdenante", "digitoVerifDispositivoOrd",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"numeroCelularBeneficiario", "digitoVerifiBeneficiario",
			"conceptoPg", "folioEsquemaCobroDigital", "referenciaNumerica",
			"pgComisionTransferencia", "montoComisionTransf"),
			Set.of("rfcBeneficiario")),

	CODI_COBRO_NO_PRESENCIAL_UNICO(20, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"numeroCelularOrdenante", "digitoVerifDispositivoOrd",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"numSerieCerComerEnvioCobro", "digVeriCertificado",
			"conceptoPg", "folioEsquemaCobroDigital", "referenciaNumerica",
			"pgComisionTransferencia", "montoComisionTransf"),
			Set.of("rfcBeneficiario")),

	CODI_COBRO_NO_PRESENCIAL_RECURRENTE(21, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"numeroCelularOrdenante", "digitoVerifDispositivoOrd",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"numSerieCerComerEnvioCobro", "digVeriCertificado",
			"conceptoPg", "folioEsquemaCobroDigital", "referenciaNumerica", "fechaHoraLimitePg",
			"pgComisionTransferencia", "montoComisionTransf"),
			Set.of("rfcBeneficiario")),

	CODI_COBRO_NO_PRESENCIAL_MIXTO_TERCERO(22, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"numeroCelularOrdenante", "digitoVerifDispositivoOrd",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"nombreBeneficiario2", "rfcBeneficiario2", "tipoCtaBeneficiario2", "ctaBeneficiario2",
			"numSerieCerComerEnvioCobro", "digVeriCertificado",
			"conceptoPg", "folioEsquemaCobroDigital", "referenciaNumerica", "fechaHoraLimitePg",
			"pgComisionTransferencia", "montoComisionTransf"),
			Set.of("rfcBeneficiario", "rfcBeneficiario2")),

	DEVOLUCION_ESPECIAL_ACREDITADA(23, List.of(
			"cveRastreoPgOriginal", "indicadoraBenefRecursos", "montoPgOriginal"),
			Set.of("indicadoraBenefRecursos")),

	DEVOLUCION_EXTEMPORANEA_ESPECIAL_ACREDITADA(24, List.of(
			"folioInstruccionOriginal", "folioPgOriginal", "fechaOrdenTransOriginal",
			"cveRastreoPgOriginal", "refNumericaOriginal", "indicadoraBenefRecursos",
			"tipoCtaOrdenanteOriginal", "ctaOrdenantePgOriginal", "conceptoPgOriginal",
			"montoPgOriginal"),
			Set.of("folioInstruccionOriginal", "folioPgOriginal", "indicadoraBenefRecursos")),

	TERCERO_A_TERCERO_FSW_CLS(25, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "conceptoPg", "iva", "referenciaNumerica",
			"referenciaCobranza", "uetrSwift", "campoSwift1", "campoSwift2"),
			Set.of("tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante", "iva",
					"referenciaCobranza", "campoSwift1", "campoSwift2")),

	TERCERO_A_TERCERO_VOSTRO_FSW_CLS(26, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreBeneficiario", "nombreBeneficiario2", "conceptoPg", "conceptoPg2", "iva",
			"referenciaNumerica", "uetrSwift", "campoSwift1", "campoSwift2"),
			Set.of("tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante", "nombreBeneficiario2",
					"conceptoPg2", "iva", "campoSwift1", "campoSwift2")),

	PARTICIPANTE_A_TERCERO_FSW_CLS(27, List.of(
			"nombreBeneficiario", "conceptoPg", "iva", "referenciaNumerica",
			"uetrSwift", "campoSwift1", "campoSwift2"),
			Set.of("iva", "campoSwift1", "campoSwift2")),

	PARTICIPANTE_A_TERCERO_VOSTRO_FSW_CLS(28, List.of(
			"nombreBeneficiario", "nombreBeneficiario2", "conceptoPg", "conceptoPg2", "iva",
			"referenciaNumerica", "uetrSwift", "campoSwift1", "campoSwift2"),
			// pagos.properties marca conceptoPg y referenciaNumerica como opcionales aquí también
			// (mismo patrón atípico que el tipo 11) — copiado literal.
			Set.of("nombreBeneficiario2", "conceptoPg", "conceptoPg2", "iva",
					"referenciaNumerica", "campoSwift1", "campoSwift2")),

	PARTICIPANTE_A_PARTICIPANTE_FSW_CLS(29, List.of(
			"nombreBeneficiario", "conceptoPg", "iva", "referenciaNumerica",
			"uetrSwift", "campoSwift1", "campoSwift2"),
			Set.of("iva", "campoSwift1", "campoSwift2")),

	TERCERO_INDIRECTO_A_TERCERO(30, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreParticipanteOrdenante", "ctaParticipanteOrdenante", "rfcParticipanteOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica", "referenciaCobranza"),
			Set.of("rfcBeneficiario", "iva", "referenciaCobranza", "referenciaNumerica")),

	TERCERO_INDIRECTO_A_PARTICIPANTE(31, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreParticipanteOrdenante", "ctaParticipanteOrdenante", "rfcParticipanteOrdenante",
			"tipoOperacion", "conceptoPg", "iva", "referenciaNumerica"),
			Set.of("iva")),

	CODI_INDIRECTO_PRESENCIAL_UNICO(32, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"numeroCelularOrdenante", "digitoVerifDispositivoOrd",
			"nombreParticipanteOrdenante", "ctaParticipanteOrdenante", "rfcParticipanteOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"numeroCelularBeneficiario", "digitoVerifDispositivoBeneficiario",
			"conceptoPg", "folioEsquemaCobroDigital", "referenciaNumerica",
			"pgComisionTransferencia", "montoComisionTransf"),
			Set.of("rfcBeneficiario", "pgComisionTransferencia", "montoComisionTransf")),

	CODI_INDIRECTO_NO_PRESENCIAL_UNICO(33, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"numeroCelularOrdenante", "digitoVerifDispositivoOrd",
			"nombreParticipanteOrdenante", "ctaParticipanteOrdenante", "rfcParticipanteOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"numeroCelularBeneficiario", "digitoVerifiBeneficiario",
			"conceptoPg", "folioEsquemaCobroDigital", "referenciaNumerica",
			"pgComisionTransferencia", "montoComisionTransf"),
			Set.of("rfcBeneficiario", "pgComisionTransferencia", "montoComisionTransf")),

	CODI_INDIRECTO_NO_PRESENCIAL_RECURRENTE(34, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"numeroCelularOrdenante", "digitoVerifDispositivoOrd",
			"nombreParticipanteOrdenante", "ctaParticipanteOrdenante", "rfcParticipanteOrdenante",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"numSerieCerComercioProveedor", "digitoVerifCertificadoComercio",
			"conceptoPg", "folioEsquemaCobroDigital", "referenciaNumerica", "fechaHoraLimitePg",
			"pgComisionTransferencia", "montoComisionTransf"),
			Set.of("rfcBeneficiario", "fechaHoraLimitePg", "pgComisionTransferencia",
					"montoComisionTransf")),

	REMESA_SALIENTE(35, List.of(
			"nombreOrdenante", "tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreClienteEmisorIndirecto", "tipoCtaClienteEmisorIndirecto",
			"ctaClienteEmisorIndirecto", "rfcClienteEmisorIndirecto",
			"nombreParticipanteEmisorIndirecto", "ctaParticipanteEmisorIndirecto",
			"rfcParticipanteEmisorIndirecto",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica",
			"identificadorRemesa", "pais", "divisa", "nombreBeneficiarioRemesa",
			"nombreProveeServiRemesaExtra", "nombreProveeServiRemesaNac",
			"nombreClienteReceptorIndirecto", "tipoCambio"),
			Set.of("tipoCtaOrdenante", "ctaOrdenante", "rfcOrdenante", "nombreBeneficiario",
					"tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario", "iva",
					"tipoCambio")),

	REMESA_ENTRANTE(36, List.of(
			"nombreOrdenante", "ctaOrdenante", "rfcOrdenante",
			"nombreClienteEmisorIndirecto", "tipoCtaClienteEmisorIndirecto",
			"ctaClienteEmisorIndirecto", "rfcClienteEmisorIndirecto",
			"nombreParticipanteEmisorIndirecto", "ctaParticipanteEmisorIndirecto",
			"rfcParticipanteEmisorIndirecto",
			"nombreBeneficiario", "tipoCtaBeneficiario", "ctaBeneficiario", "rfcBeneficiario",
			"conceptoPg", "iva", "referenciaNumerica",
			"identificadorRemesa", "pais", "divisa", "nombreBeneficiarioRemesa",
			"nombreProveeServiRemesaExtra", "nombreProveeServiRemesaNac",
			"nombreClienteDistribuidorIndirecto", "tipoCambio"),
			Set.of("ctaOrdenante", "rfcOrdenante", "iva", "tipoCambio"));

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
