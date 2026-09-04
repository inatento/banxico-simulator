package mx.endcom.hermes.banxicosim.ara;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Mensaje crudo del socket ARA: mismo framing de encabezado que el socket SPEI principal
 * (destino(1) + operación(1) + tamañoCuerpo BE(2)) — ver {@code core/ara/message/AraHeaderMessage.java}
 * del repo minos (mismos offsets DESTINATION_INDEX/OPERATION_INDEX/BODY_SIZE_INDEX).
 */
public record AraFrame(int destination, int operation, byte[] body) {

	public static AraFrame read(DataInputStream in) throws IOException {
		byte[] header = new byte[4];
		in.readFully(header);
		int destination = header[0] & 0xFF;
		int operation = header[1] & 0xFF;
		int bodySize = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
		byte[] body = new byte[bodySize];
		if (bodySize > 0) {
			in.readFully(body);
		}
		return new AraFrame(destination, operation, body);
	}

	public byte[] toBytes() {
		ByteWriter w = new ByteWriter();
		w.writeByte(destination);
		w.writeByte(operation);
		w.writeShortBE((short) body.length);
		w.writeBytes(body);
		return w.toByteArray();
	}

	public void writeTo(DataOutputStream out) throws IOException {
		out.write(toBytes());
		out.flush();
	}

	public static AraFrame of(int operation, byte[] body) {
		return new AraFrame(AraProtocol.DESTINATION, operation, body);
	}
}
