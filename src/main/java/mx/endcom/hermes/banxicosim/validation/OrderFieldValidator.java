package mx.endcom.hermes.banxicosim.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Replica, para los 4 tipos de pago en alcance de v1, las reglas de
 * {@code judeca.validator.CamposOrdenesValidator} y el catálogo de
 * {@code judeca/.../pagos/pagos.properties} y {@code judeca/.../validator/validator.properties}
 * (ver PaymentType para las citas línea por línea).
 *
 * <p>Catálogos externos (institución, tipo de cuenta, causa de devolución): en judeca real estos
 * NO están hardcodeados — se cargan en memoria en tiempo de ejecución desde un sistema externo
 * ("radamanto" empuja catálogos vía {@code RadamantoServiceImpl.asignarCatalogos}). El simulador
 * no tiene ese sistema, así que estos catálogos son configurables aquí con una lista por defecto
 * razonable; lo que SÍ se replica fielmente son las reglas de formato/longitud/regex, que sí
 * están fijas en el código de judeca.</p>
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

	/** Catálogos configurables (ver nota de clase). Vacíos = "acepta cualquier entero válido". */
	private java.util.Set<Integer> validTipoCuenta = java.util.Set.of();
	private java.util.Set<Integer> validInstitucion = java.util.Set.of();

	public void setValidTipoCuenta(java.util.Set<Integer> values) {
		this.validTipoCuenta = values;
	}

	public void setValidInstitucion(java.util.Set<Integer> values) {
		this.validInstitucion = values;
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
			errors.add("tipoPg " + ctx.paymentTypeCode() + " fuera de alcance del simulador (v1 sólo valida 01/02/05/12)");
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

		for (int i = 0; i < fields.size(); i++) {
			String fieldName = fields.get(i);
			String value = i < detailFields.length ? detailFields[i] : "";
			boolean present = i < detailFields.length;
			if (!present || value.isEmpty()) {
				if (!type.isOptional(fieldName)) {
					errors.add(fieldName + ": esta vacio");
				}
				continue; // igual que judeca: opcional + ausente/vacío = no se valida más a fondo
			}
			validateField(fieldName, value, errors);
		}
		return errors;
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

	private void validateField(String fieldName, String value, List<String> errors) {
		switch (fieldName) {
			case "nombreOrdenante", "nombreBeneficiario" -> {
				// CamposOrdenesValidator.java:176-183 (01/02/05/12 no están en la lista de excepción)
				if (value.length() > 120) {
					errors.add(fieldName + ": excede longitud de 120");
				}
			}
			case "tipoCtaOrdenante", "tipoCtaBeneficiario" -> {
				// CamposOrdenesValidator.java:193-199
				Integer n = parseUnsignedInt(fieldName, value, errors);
				if (n != null && !validTipoCuenta.isEmpty() && !validTipoCuenta.contains(n)) {
					errors.add(fieldName + ": tipo de cuenta " + n + " no esta en el catalogo");
				}
			}
			case "ctaOrdenante", "ctaBeneficiario" -> {
				// CamposOrdenesValidator.java:212-219
				if (value.length() > 40) {
					errors.add(fieldName + ": excede longitud de 40");
				}
			}
			case "rfcOrdenante", "rfcBeneficiario" -> validateRfcOrCurp(fieldName, value, errors);
			case "conceptoPg" -> {
				// CamposOrdenesValidator.java:291-303 (01/02/05/12 usan el límite por defecto, no el de 40->210)
				if (value.length() > 40) {
					errors.add(fieldName + ": excede longitud de 40");
				}
			}
			case "iva" -> {
				// CamposOrdenesValidator.java:127-145 (mismo formato que "monto")
				if (!MONTO.matcher(value).matches()) {
					errors.add("iva: no cumple el formato esperado (regex.monto)");
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
			default -> errors.add(fieldName + ": campo no reconocido por el simulador (fuera de alcance v1)");
		}
	}

	private void validateRfcOrCurp(String fieldName, String value, List<String> errors) {
		// CamposOrdenesValidator.java:259-289 (rama por defecto: 01/02/05/12 no están en el bypass de 30-36)
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
