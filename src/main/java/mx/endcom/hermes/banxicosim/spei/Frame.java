package mx.endcom.hermes.banxicosim.spei;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import mx.endcom.hermes.banxicosim.wire.ByteWriter;

/**
 * Un mensaje crudo del socket SPEI principal: encabezado de 4 bytes + cuerpo.
 *
 * Framing verificado contra {@code core/spei/message/in/protocol/SpeiInputHeaderMessage.java}
 * (offsets DESTINATION_INDEX=0, OPERATION_INDEX=1, BODY_SIZE_INDEX=2-3) y
 * {@code core/spei/socket/SpeiInputListener.java#readMessage} del repo minos.
 */
public record Frame(int destination, int operation, byte[] body) {

	public static Frame read(DataInputStream in) throws IOException {
		byte[] header = new byte[4];
		in.readFully(header);
		int destination = header[0] & 0xFF;
		int operation = header[1] & 0xFF;
		int bodySize = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
		byte[] body = new byte[bodySize];
		if (bodySize > 0) {
			in.readFully(body);
		}
		return new Frame(destination, operation, body);
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

	public static Frame of(int operation, byte[] body) {
		return new Frame(SpeiProtocol.DESTINATION, operation, body);
	}
}
