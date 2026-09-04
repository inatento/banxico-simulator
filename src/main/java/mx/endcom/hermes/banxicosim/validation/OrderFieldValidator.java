package mx.endcom.hermes.banxicosim.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Replica, para los 35 tipos de pago reales del catálogo SPEI (códigos 0-36, se saltan 13 y 14),
 * las reglas de {@code judeca.validator.CamposOrdenesValidator} y el catálogo de
 * {@code judeca/.../pagos/pagos.properties} y {@code judeca/.../validator/validator.properties}
 * (ver PaymentType para las citas línea por línea de campos por tipo).
 *
 * <p><b>Esto excede a propósito el alcance de v1 de ADR-005</b> (que cubría sólo 01/02/05/12) —
 * ver la nota de clase en {@link PaymentType} y el README.md para el detalle de la divergencia,
 * que sigue pendiente de reconciliar formalmente con esa ADR.</p>
 *
 * <p>Catálogos externos (institución, tipo de cuenta, causa de devolución, tipo de operación): en
 * judeca real estos NO están hardcodeados — se cargan en memoria en tiempo de ejecución desde un
 * sistema externo ("radamanto" empuja catálogos vía {@code RadamantoServiceImpl.asignarCatalogos}).
 * El simulador no tiene ese sistema, así que estos catálogos son configurables aquí con una lista
 * vacía por defecto (= acepta cualquier entero válido); lo que SÍ se replica fielmente son las
 * reglas de formato/longitud/regex y las ramas condicionales por tipo de pago, que sí están fijas
 * en el código de judeca.</p>
 *
 * <p><b>Limitación conocida y documentada (no oculta):</b> los campos "*Original" de las
 * devoluciones ({@code cveRastreoPgOriginal, fechaOrdenTransOriginal, montoPgOriginal,
 * folioInstruccionOriginal, folioPgOriginal, refNumericaOriginal, tipoCtaOrdenanteOriginal,
 * ctaOrdenantePgOriginal, conceptoPgOriginal}) se validan en judeca real contra una "orden
 * original" almacenada (sobrecarga de 2 argumentos de {@code validarCampo}, líneas 497-644 de
 * CamposOrdenesValidator.java) que el simulador no persiste — el simulador no tiene una base de
 * órdenes previas contra la cual comparar una devolución entrante. Se replica el formato propio de
 * cada campo (cuando judeca lo valida) pero NO la comparación cruzada contra la orden original.
 * Ver comentarios por campo abajo para el detalle exacto de qué se porta y qué no.</p>
 */
public final class OrderFieldValidator {

	// regex.monto (validator.properties:10): 1-12 dígitos enteros, punto opcional, 0-2 decimales.
	private static final Pattern MONTO = Pattern.compile("[0-9]{1,12}\\.?[0-9]{0,2}");

	// validator.properties:11-13
	private static final Pattern RFC_MORAL = Pattern.compile(
			"[A-ZÑ&]{3}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|1[0-9]|2[0-9]|3[0-1])[A-Z0-9]{3}");
	private static final Pattern RFC_FISICA = Pattern.compile(
			"[A-ZÑ&]{4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|1[0-9]|2[0-9]|3[0-1])[A-Z0-9]{3}");
	private static final Pattern CURP = Pattern.compile(
			"[A-ZÑ&][AEIOU][A-ZÑ&]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|1[0-9]|2[0-9]|3[0-1])[HM]"
			+ "(AS|BC|BS|CC|CS|CH|CL|CM|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)"
			+ "[B-DF-HJ-NP-TV-Z]{3}[0-9A-Z][0-9]");

	private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
	// CamposOrdenesValidator.java:447 (SimpleDateFormat("yyyy-MM-dd'T'HH:mm")).
	private static final DateTimeFormatter FECHA_HORA_LIMITE =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

	// Tipos donde CamposOrdenesValidator.java:171,188,207 deja pasar sin validar un campo de
	// nombre/tipoCta/cta cuyo valor es "0" o vacío (participación indirecta/remesas y devoluciones,
	// que no siempre traen ordenante/beneficiario "reales").
	private static final Set<Integer> ZERO_OR_BLANK_BYPASS_TYPES = Set.of(0, 16, 17, 18, 23, 24, 35, 36);
	// CamposOrdenesValidator.java:226: tipos de participación indirecta/remesas donde un RFC/CURP
	// puede venir "ND" o vacío en vez de un RFC real.
	private static final Set<Integer> RFC_ND_BYPASS_TYPES = Set.of(30, 31, 32, 33, 34, 35, 36);
	// CamposOrdenesValidator.java:399,414: tipos CoDi (directos e indirectos) donde la comisión por
	// transferencia es opcional en la práctica aunque pagos.properties no la liste como tal.
	private static final Set<Integer> COMISION_BYPASS_TYPES = Set.of(19, 20, 21, 22, 32, 33, 34);
	// CamposOrdenesValidator.java:293: estos tipos usan límite de 210 para conceptoPg en vez de 40.
	// OJO: el código anterior de este simulador (sólo 01/02/05/12) traía un comentario que decía
	// "01/02/05/12 usan el límite por defecto, no el de 40->210" -- eso es CORRECTO para 01/05/12,
	// pero INCORRECTO para 02: el tipo 2 SÍ está en Arrays.asList(2,3,4,6,7,9) de judeca. Se corrige
	// aquí; ver reporte de esta tarea.
	private static final Set<Integer> CONCEPTOPG_210_TYPES = Set.of(2, 3, 4, 6, 7, 9);

	// Campos de los grupos nombre/tipoCta/cta sujetos al bypass de "0"/vacío de arriba.
	// CamposOrdenesValidator.java:161-219 (uniones de los 3 case-groups del switch).
	private static final Set<String> ZERO_BYPASSABLE_FIELDS = Set.of(
			"nombreOrdenante", "nombreBeneficiario", "nombreBeneficiario2",
			"nombreClienteEmisorIndirecto", "nombreParticipanteEmisorIndirecto",
			"nombreProveeServiRemesaExtra", "nombreProveeServiRemesaNac",
			"nombreClienteReceptorIndirecto", "nombreClienteDistribuidorIndirecto",
			"tipoCtaOrdenante", "tipoCtaBeneficiario", "tipoCtaBeneficiario2",
			"tipoCtaClienteEmisorIndirecto",
			"ctaOrdenante", "ctaBeneficiario", "ctaBeneficiario2", "ctaParticipanteOrdenante",
			"ctaClienteEmisorIndirecto", "ctaParticipanteEmisorIndirecto");

	// CamposOrdenesValidator.java:220-225 (case-group de RFC/CURP).
	private static final Set<String> RFC_FIELDS = Set.of(
			"rfcOrdenante", "rfcBeneficiario", "rfcBeneficiario2", "rfcParticipanteOrdenante",
			"rfcClienteEmisorIndirecto", "rfcParticipanteEmisorIndirecto");

	private static final Set<String> COMISION_FIELDS =
			Set.of("pgComisionTransferencia", "montoComisionTransf");

	// Campos "sin validación de formato" en judeca real, por dos motivos distintos (documentado
	// campo por campo dentro de validateField): (a) el nombre en pagos.properties no coincide con
	// ningún "case" del switch de CamposOrdenesValidator.java (typo o simplemente no existe el
	// case), o (b) el campo sólo se valida comparándolo contra una "orden original" que el
	// simulador no persiste. En ambos casos judeca real nunca lanza una excepción por su formato.
	private static final Set<String> NOOP_FIELDS = Set.of(
			"nombreParticipanteOrdenante", "digitoVerifDispositivoBeneficiario",
			"digitoVerifCertificadoComercio", "identificadorRemesa", "pais", "divisa",
			"nombreBeneficiarioRemesa", "tipoCambio",
			"folioInstruccionOriginal", "folioPgOriginal", "refNumericaOriginal",
			"tipoCtaOrdenanteOriginal", "ctaOrdenantePgOriginal", "conceptoPgOriginal");

	// CamposOrdenesValidator.java:367-377: sólo registran en log si vienen vacíos (no rechazan).
	private static final Set<String> DIGITO3_FIELDS =
			Set.of("digitoVerifDispositivoOrd", "digitoVerifiBeneficiario", "digVeriCertificado");

	// CamposOrdenesValidator.java:387-397: mismo patrón (log, no rechazo, si vienen vacíos).
	private static final Set<String> NUMSERIE20_FIELDS =
			Set.of("numSerieCerComerEnvioCobro", "numSerieCerComercioProveedor", "folioEsquemaCobroDigital");

	// Unión de todos los campos donde "vacío" NUNCA debe producir el error genérico "esta vacio"
	// (aunque pagos.properties no los liste en campos.optional), porque judeca real usa
	// log.debug en vez de lanzar una excepción para ellos.
	private static final Set<String> BLANK_TOLERANT_FIELDS = buildBlankTolerantFields();

	private static Set<String> buildBlankTolerantFields() {
		Set<String> s = new HashSet<>(NOOP_FIELDS);
		s.addAll(DIGITO3_FIELDS);
		s.addAll(NUMSERIE20_FIELDS);
		s.add("numeroCelularOrdenante"); // CamposOrdenesValidator.java:358-366
		s.add("numeroCelularBeneficiario"); // CamposOrdenesValidator.java:378-386
		return Set.copyOf(s);
	}

	/** Catálogos configurables (ver nota de clase). Vacíos = "acepta cualquier entero válido". */
	private Set<Integer> validTipoCuenta = Set.of();
	private Set<Integer> validInstitucion = Set.of();
	private Set<Integer> validCausaDevolucion = Set.of();
	private Set<Integer> validTipoOperacion = Set.of();

	public void setValidTipoCuenta(Set<Integer> values) {
		this.validTipoCuenta = values;
	}

	public void setValidInstitucion(Set<Integer> values) {
		this.validInstitucion = values;
	}

	public void setValidCausaDevolucion(Set<Integer> values) {
		this.validCausaDevolucion = values;
	}

	public void setValidTipoOperacion(Set<Integer> values) {
		this.validTipoOperacion = values;
	}

	public record OrderContext(
			int paymentTypeCode,
			String trackingKey,
			BigDecimal amount,
			int issuerInstitutionCode,
			int receiverInstitutionCode) {
	}

	/**
	 * Valida una orden completa: los 5 campos "comunes" (tomados de otras partes del wire, ver
	 * PaymentType, nota de clase) más los campos específicos del tipo, tomados del string de
	 * detalle ya partido por 0x00 en el orden de {@link PaymentType#fieldsInOrder()}.
	 *
	 * @return lista de errores; vacía si la orden es válida.
	 */
	public List<String> validate(OrderContext ctx, String[] detailFields) {
		List<String> errors = new ArrayList<>();

		PaymentType type = PaymentType.byCode(ctx.paymentTypeCode());
		if (type == null) {
			errors.add("tipoPg " + ctx.paymentTypeCode()
					+ " fuera del catalogo SPEI (los codigos validos son 0-36, sin 13 ni 14)");
			return errors;
		}

		validateCommon(ctx, errors);

		List<String> fields = type.fieldsInOrder();
		if (detailFields.length != fields.size()) {
			errors.add(String.format(
					"tipoPg %02d: se esperaban %d campos de detalle, se recibieron %d",
					type.code(), fields.size(), detailFields.length));
			// seguimos validando lo que se pueda, con relleno vacío, para reportar todos los problemas
		}

		// Mapa de valores crudos por nombre de campo -- necesario para reglas que dependen de otro
		// campo de la misma orden (p. ej. montoComisionTransf depende de pgComisionTransferencia,
		// CamposOrdenesValidator.java:422-440).
		Map<String, String> rawValues = new LinkedHashMap<>();
		for (int i = 0; i < fields.size(); i++) {
			rawValues.put(fields.get(i), i < detailFields.length ? detailFields[i] : "");
		}

		for (int i = 0; i < fields.size(); i++) {
			String fieldName = fields.get(i);
			boolean present = i < detailFields.length;
			String value = present ? detailFields[i] : "";

			if (isValueBypassed(fieldName, value, type.code())) {
				continue; // judeca: bypass de "0"/"ND"/vacio para ciertos campos+tipos (ver arriba)
			}
			if (!present || value.isEmpty()) {
				if (!type.isOptional(fieldName) && !BLANK_TOLERANT_FIELDS.contains(fieldName)) {
					errors.add(fieldName + ": esta vacio");
				}
				continue; // igual que judeca: opcional/tolerante + ausente/vacío = no se valida más a fondo
			}
			validateField(fieldName, value, type.code(), rawValues, errors);
		}
		return errors;
	}

	private boolean isValueBypassed(String fieldName, String value, int typeCode) {
		boolean blankOrZero = value == null || value.isEmpty() || value.equals("0");
		if (blankOrZero && ZERO_BYPASSABLE_FIELDS.contains(fieldName)
				&& ZERO_OR_BLANK_BYPASS_TYPES.contains(typeCode)) {
			return true;
		}
		if (blankOrZero && COMISION_FIELDS.contains(fieldName)
				&& COMISION_BYPASS_TYPES.contains(typeCode)) {
			return true;
		}
		boolean blankOrNd = value == null || value.isEmpty() || value.equals("ND");
		return blankOrNd && RFC_FIELDS.contains(fieldName) && RFC_ND_BYPASS_TYPES.contains(typeCode);
	}

	private void validateCommon(OrderContext ctx, List<String> errors) {
		// tipoPg ya se resolvió arriba (PaymentType.byCode). cveRastreo: CamposOrdenesValidator.java:118-126
		if (isBlank(ctx.trackingKey())) {
			errors.add("cveRastreo: esta vacio");
		} else if (ctx.trackingKey().length() > 30) {
			errors.add("cveRastreo: excede longitud de 30");
		}
		// monto: CamposOrdenesValidator.java:127-145
		if (ctx.amount() == null) {
			errors.add("monto: esta vacio");
		} else if (!MONTO.matcher(ctx.amount().toPlainString()).matches()) {
			errors.add("monto: no cumple el formato esperado (regex.monto)");
		}
		// cveEmisor / cveReceptor: CamposOrdenesValidator.java:146-160 (catálogo institución)
		if (!validInstitucion.isEmpty() && !validInstitucion.contains(ctx.issuerInstitutionCode())) {
			errors.add("cveEmisor: institucion " + ctx.issuerInstitutionCode() + " no esta en el catalogo");
		}
		if (!validInstitucion.isEmpty() && !validInstitucion.contains(ctx.receiverInstitutionCode())) {
			errors.add("cveReceptor: institucion " + ctx.receiverInstitutionCode() + " no esta en el catalogo");
		}
	}

	private void validateField(String fieldName, String value, int typeCode,
			Map<String, String> rawValues, List<String> errors) {
		switch (fieldName) {
			case "nombreOrdenante", "nombreBeneficiario", "nombreBeneficiario2",
					"nombreClienteEmisorIndirecto", "nombreParticipanteEmisorIndirecto",
					"nombreProveeServiRemesaExtra", "nombreProveeServiRemesaNac",
					"nombreClienteReceptorIndirecto", "nombreClienteDistribuidorIndirecto" -> {
				// CamposOrdenesValidator.java:161-183 (el bypass de "0"/vacío para
				// 00,16,17,18,23,24,35,36 ya se resolvió en isValueBypassed antes de llegar aquí)
				if (value.length() > 120) {
					errors.add(fieldName + ": excede longitud de 120");
				}
			}
			case "tipoCtaOrdenante", "tipoCtaBeneficiario", "tipoCtaBeneficiario2",
					"tipoCtaClienteEmisorIndirecto" -> {
				// CamposOrdenesValidator.java:184-199
				Integer n = parseUnsignedInt(fieldName, value, errors);
				if (n != null && !validTipoCuenta.isEmpty() && !validTipoCuenta.contains(n)) {
					errors.add(fieldName + ": tipo de cuenta " + n + " no esta en el catalogo");
				}
			}
			case "ctaOrdenante", "ctaBeneficiario", "ctaBeneficiario2", "ctaParticipanteOrdenante",
					"ctaClienteEmisorIndirecto", "ctaParticipanteEmisorIndirecto" -> {
				// CamposOrdenesValidator.java:201-219
				if (value.length() > 40) {
					errors.add(fieldName + ": excede longitud de 40");
				}
			}
			case "rfcOrdenante", "rfcBeneficiario", "rfcBeneficiario2", "rfcParticipanteOrdenante",
					"rfcClienteEmisorIndirecto", "rfcParticipanteEmisorIndirecto" ->
					validateRfcOrCurp(fieldName, value, errors);
			case "conceptoPg" -> {
				// CamposOrdenesValidator.java:291-303: tipos 2,3,4,6,7,9 usan límite de 210, el
				// resto (incluidos 01/05/12) usan 40. (Corrección respecto a la v1 de 4 tipos: el
				// comentario anterior decía que 02 usaba el límite de 40 -- ver nota en
				// CONCEPTOPG_210_TYPES arriba.)
				int max = CONCEPTOPG_210_TYPES.contains(typeCode) ? 210 : 40;
				if (value.length() > max) {
					errors.add("conceptoPg: excede longitud de " + max);
				}
			}
			case "conceptoPg2" -> {
				// CamposOrdenesValidator.java:304-312
				if (value.length() > 40) {
					errors.add("conceptoPg2: excede longitud de 40");
				}
			}
			case "iva", "montoIntereses" -> {
				// CamposOrdenesValidator.java:127-145 (mismo formato que "monto")
				if (!MONTO.matcher(value).matches()) {
					errors.add(fieldName + ": no cumple el formato esperado (regex.monto)");
				}
			}
			case "referenciaNumerica" -> {
				// CamposOrdenesValidator.java:313-321
				if (value.length() > 7) {
					errors.add("referenciaNumerica: excede longitud de 7");
				}
			}
			case "referenciaCobranza" -> {
				// CamposOrdenesValidator.java:322-330
				if (value.length() > 40) {
					errors.add("referenciaCobranza: excede longitud de 40");
				}
			}
			case "cvePg" -> {
				// CamposOrdenesValidator.java:483-491
				if (value.length() > 10) {
					errors.add("cvePg: excede longitud de 10");
				}
			}
			case "tipoOperacion" -> {
				// CamposOrdenesValidator.java:331-339 (catalogosUtil.consultarTipoOperacion)
				Integer n = parseUnsignedInt(fieldName, value, errors);
				if (n != null && !validTipoOperacion.isEmpty() && !validTipoOperacion.contains(n)) {
					errors.add("tipoOperacion: valor " + n + " no esta en el catalogo");
				}
			}
			case "causaDevolucion" -> {
				// CamposOrdenesValidator.java:340-348 (catalogosUtil.consultarCausaDevolucion)
				Integer n = parseUnsignedInt(fieldName, value, errors);
				if (n != null && !validCausaDevolucion.isEmpty() && !validCausaDevolucion.contains(n)) {
					errors.add("causaDevolucion: valor " + n + " no esta en el catalogo");
				}
			}
			case "indicadoraBenefRecursos" -> {
				// CamposOrdenesValidator.java:349-357
				if (!value.equals("01") && !value.equals("02")) {
					errors.add("indicadoraBenefRecursos: debe ser 01 o 02");
				}
			}
			case "numeroCelularOrdenante" -> {
				// CamposOrdenesValidator.java:358-366 (vacío sólo se registra en log, no rechaza --
				// ya resuelto en BLANK_TOLERANT_FIELDS)
				if (value.length() > 10) {
					errors.add("numeroCelularOrdenante: excede longitud de 10");
				}
			}
			case "digitoVerifDispositivoOrd", "digitoVerifiBeneficiario", "digVeriCertificado" -> {
				// CamposOrdenesValidator.java:367-377 (vacío sólo se registra en log)
				if (value.length() > 3) {
					errors.add(fieldName + ": excede longitud de 3");
				}
			}
			case "numeroCelularBeneficiario" -> {
				// CamposOrdenesValidator.java:378-386 (vacío sólo se registra en log)
				if (value.length() > 20) {
					errors.add("numeroCelularBeneficiario: excede longitud de 20");
				}
			}
			case "numSerieCerComerEnvioCobro", "numSerieCerComercioProveedor",
					"folioEsquemaCobroDigital" -> {
				// CamposOrdenesValidator.java:387-397 (vacío sólo se registra en log)
				if (value.length() > 20) {
					errors.add(fieldName + ": excede longitud de 20");
				}
			}
			case "pgComisionTransferencia" -> {
				// CamposOrdenesValidator.java:398-412 (el bypass de "0"/vacío para
				// 19/20/21/22/32/33/34 ya se resolvió en isValueBypassed)
				if (!value.equals("1") && !value.equals("2")) {
					errors.add("pgComisionTransferencia: debe ser 1 o 2");
				}
			}
			case "montoComisionTransf" -> validateMontoComisionTransf(value, rawValues, errors);
			case "fechaHoraLimitePg" -> {
				// CamposOrdenesValidator.java:443-454 (formato yyyy-MM-dd'T'HH:mm)
				try {
					LocalDateTime.parse(value, FECHA_HORA_LIMITE);
				} catch (DateTimeParseException e) {
					errors.add("fechaHoraLimitePg: no corresponde al formato yyyy-MM-ddTHH:mm");
				}
			}
			case "uetrSwift" -> {
				// CamposOrdenesValidator.java:455-463
				if (value.length() > 36) {
					errors.add("uetrSwift: excede longitud de 36");
				}
			}
			case "campoSwift1", "campoSwift2" -> {
				// CamposOrdenesValidator.java:464-473
				if (value.length() > 40) {
					errors.add(fieldName + ": excede longitud de 40");
				}
			}
			case "infoFacturas" -> {
				// CamposOrdenesValidator.java:474-482
				if (value.length() > 1022) {
					errors.add("infoFacturas: excede longitud de 1022");
				}
			}
			case "fechaOrdenTransOriginal" -> {
				// CamposOrdenesValidator.java:528-544 (sobrecarga de 2 órdenes): valida que sea una
				// fecha (los primeros 10 caracteres se parsean como LocalDate ISO, yyyy-MM-dd) y que
				// coincida con diaOperativo de la orden original. El simulador NO tiene una orden
				// original que consultar -- se porta sólo la parte de formato, no la comparación.
				if (value.length() < 10) {
					errors.add("fechaOrdenTransOriginal: no tiene una fecha valida (formato yyyy-MM-dd)");
				} else {
					try {
						LocalDate.parse(value.substring(0, 10));
					} catch (DateTimeParseException e) {
						errors.add("fechaOrdenTransOriginal: no tiene una fecha valida (formato yyyy-MM-dd)");
					}
				}
			}
			case "montoPgOriginal" -> {
				// CamposOrdenesValidator.java:558-585: mismo formato que "monto" (regex.monto, sin
				// comas), más comparación contra el monto de la orden original -- misma limitación
				// que arriba, el simulador no tiene la orden original para comparar.
				if (value.contains(",")) {
					errors.add("montoPgOriginal: el monto no puede llevar comas");
				} else if (!MONTO.matcher(value).matches()) {
					errors.add("montoPgOriginal: no cumple el formato esperado (regex.monto)");
				}
			}
			case "cveRastreoPgOriginal" -> {
				// CamposOrdenesValidator.java:545-557: judeca no impone límite de longitud aquí,
				// sólo no-vacío (ya resuelto arriba) y comparación contra el cveRastreo de la orden
				// original, que el simulador no tiene para comparar. Sin más validación.
			}
			case "nombreParticipanteOrdenante" -> {
				// INCONSISTENCIA ENCONTRADA (documentada, no inventada): pagos.properties usa el
				// nombre "nombreParticipanteOrdenante" (tipos 30-34), pero el switch de
				// CamposOrdenesValidator.java:164 tiene el case "nombrePartipanteOrdenante" (typo,
				// falta "ci") -- nunca hace match. En judeca real este campo NUNCA entra al grupo de
				// validación de "nombre" (ni el límite de 120 ni el bypass de "0"): sólo se exige
				// que la clave exista en el JSON (isNull), sin validación de formato. Se replica
				// fielmente: sin validación de longitud aquí.
			}
			case "digitoVerifDispositivoBeneficiario" -> {
				// INCONSISTENCIA ENCONTRADA: pagos.properties usa este nombre para el tipo 32, pero
				// CamposOrdenesValidator.java:367-369 sólo tiene case para
				// digitoVerifDispositivoOrd/digitoVerifiBeneficiario/digVeriCertificado -- ninguno
				// coincide. Cae al "default: break;" de judeca real. Sin validación de formato.
			}
			case "digitoVerifCertificadoComercio" -> {
				// INCONSISTENCIA ENCONTRADA: mismo caso que arriba, para el tipo 34 (nombre distinto
				// de "digVeriCertificado", que sí tiene case). Sin validación en judeca real.
			}
			case "identificadorRemesa", "pais", "divisa", "nombreBeneficiarioRemesa", "tipoCambio" -> {
				// INCONSISTENCIA ENCONTRADA: ninguno de estos 5 nombres (usados por los tipos de
				// remesa 35/36) tiene un "case" en CamposOrdenesValidator.java -- se confirmó
				// leyendo el archivo completo. Caen al "default: break;" de judeca real: sin
				// validación de formato/longitud más allá de la presencia (ya resuelta arriba).
			}
			case "folioInstruccionOriginal", "folioPgOriginal" -> {
				// CamposOrdenesValidator.java:502-527 (sobrecarga de 2 órdenes): en judeca real
				// NUNCA se lanza una excepción para estos dos campos -- sólo se compara (log.debug)
				// contra folioInstruccion/folioPg de la orden original, y el simulador no tiene una
				// orden original que comparar. Se aceptan tal cual, sin límite de longitud (judeca
				// tampoco impone uno).
			}
			case "refNumericaOriginal", "tipoCtaOrdenanteOriginal", "ctaOrdenantePgOriginal",
					"conceptoPgOriginal" -> {
				// CamposOrdenesValidator.java:586-637: mismo patrón -- nunca lanzan excepción en
				// judeca real (sólo log.debug de discrepancias contra la orden original). Se
				// aceptan tal cual.
			}
			default -> errors.add(fieldName + ": campo no reconocido por el simulador");
		}
	}

	private void validateMontoComisionTransf(String value, Map<String, String> rawValues, List<String> errors) {
		// CamposOrdenesValidator.java:413-442 (el bypass de "0"/vacío para 19/20/21/22/32/33/34 ya
		// se resolvió en isValueBypassed). El valor válido depende del campo hermano
		// pgComisionTransferencia, que siempre precede a este en el orden de pagos.properties.
		String pgComision = rawValues.getOrDefault("pgComisionTransferencia", "");
		if (pgComision.isEmpty()) {
			errors.add("montoComisionTransf: pgComisionTransferencia esta vacio y es necesario para validar este campo");
			return;
		}
		if (pgComision.equals("1")) {
			if (!value.equals("0")) {
				errors.add("montoComisionTransf: debe ser 0 cuando pgComisionTransferencia=1");
			}
		} else if (pgComision.equals("2")) {
			if (!MONTO.matcher(value).matches()) {
				errors.add("montoComisionTransf: no cumple el formato esperado (regex.monto)");
			}
		}
		// si pgComisionTransferencia no es "1" ni "2", ese campo ya reportó su propio error y
		// judeca real tampoco valida montoComisionTransf en ese caso (ningún branch matchea).
	}

	private void validateRfcOrCurp(String fieldName, String value, List<String> errors) {
		// CamposOrdenesValidator.java:220-289: el bypass de "ND"/vacío para 30-36 ya se resolvió en
		// isValueBypassed (línea 226-229 del archivo real); esta es la validación de formato que
		// aplica igual en ambas ramas del if/else de judeca (líneas 236-258 y 266-288 son idénticas).
		if (value.length() > 18) {
			errors.add(fieldName + ": excede longitud de 18");
			return;
		}
		boolean isCurp = CURP.matcher(value).matches();
		boolean isRfcFisica = RFC_FISICA.matcher(value).matches();
		boolean isRfcMoral = RFC_MORAL.matcher(value).matches();
		if (!isCurp && !isRfcFisica && !isRfcMoral) {
			errors.add(fieldName + ": no tiene la estructura de un CURP o RFC");
			return;
		}
		String datePart = null;
		if (isCurp || isRfcFisica) {
			if (value.length() >= 10) {
				datePart = value.substring(4, 10);
			}
		} else {
			if (value.length() >= 9) {
				datePart = value.substring(3, 9);
			}
		}
		if (datePart == null || !isValidYyMmDd(datePart)) {
			errors.add(fieldName + ": no tiene una fecha valida");
		}
	}

	private boolean isValidYyMmDd(String s) {
		try {
			LocalDate.parse(s, YYMMDD);
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	private Integer parseUnsignedInt(String fieldName, String value, List<String> errors) {
		try {
			return Integer.parseUnsignedInt(value);
		} catch (NumberFormatException e) {
			errors.add(fieldName + ": no es un entero valido");
			return null;
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isEmpty();
	}
}
