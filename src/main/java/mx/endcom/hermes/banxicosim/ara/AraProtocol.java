package mx.endcom.hermes.banxicosim.ara;

/**
 * Códigos de operación del socket ARA (intercambio de certificados y login), verificados contra
 * {@code core/ara/message/ToAraInputMessage.java} (lo que minos reconoce como entrada — lo que el
 * simulador debe mandar) y {@code core/ara/message/out/*Message.java} (lo que minos manda y el
 * simulador debe parsear) del repo minos.
 */
public final class AraProtocol {

	private AraProtocol() {
	}

	/** Destino por defecto de minos para ARA (AraMessage.ARA_SERVICE = 0). No se valida; se imita. */
	public static final int DESTINATION = 0x00;

	// --- Mensajes que minos manda y el simulador recibe (parsea) ---
	public static final int OP_CONN_USR = 0x10;
	public static final int OP_PIDE_CRT_NVO = 0x50;
	public static final int OP_LOGOUT = 0x00;

	// --- Mensajes que el simulador manda y minos recibe (parsea) ---
	public static final int OP_ID_USUARIO_ALEAT = 0xB7;
	public static final int OP_ID_FMA_ALEAT = 0x4C; // minos -> simulador (respuesta al reto)
	public static final int OP_REV_BRDCST = 0x13;
	public static final int OP_CRT_NO_EXISTE = 0xC2;
	public static final int OP_REG_CRT_NVO_FMT = 0xC3;
	public static final int OP_AUT_NO_CONN = 0xC5;
	public static final int OP_LOGGED = 0xFD;
	public static final int OP_OPR_NO_PERMIT = 0xCB;
	public static final int OP_TIPO_DESC = 0x32;
}
