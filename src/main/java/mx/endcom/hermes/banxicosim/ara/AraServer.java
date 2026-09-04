package mx.endcom.hermes.banxicosim.ara;

import java.net.ServerSocket;
import java.net.Socket;
import java.security.PublicKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mx.endcom.hermes.banxicosim.crypto.SimulatorIdentity;
import mx.endcom.hermes.banxicosim.persistence.H2Store;

/**
 * Servidor TCP del socket ARA (Fase 2). minos abre la conexión hacia este puerto — el simulador
 * sólo escucha y acepta (ver spec técnica &sect;1: minos siempre es el cliente TCP en ambos
 * sockets).
 */
public final class AraServer implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(AraServer.class);

	private final int port;
	private final SimulatorIdentity identity;
	private final PublicKey minosPublicKey;
	private final String minosCertificateNumber;
	private final String minosCertificatePem;
	private final H2Store store;
	private final ExecutorService pool = Executors.newCachedThreadPool();
	private volatile boolean running = true;
	private ServerSocket serverSocket;

	public AraServer(int port, SimulatorIdentity identity, PublicKey minosPublicKey,
			String minosCertificateNumber, String minosCertificatePem, H2Store store) {
		this.port = port;
		this.identity = identity;
		this.minosPublicKey = minosPublicKey;
		this.minosCertificateNumber = minosCertificateNumber;
		this.minosCertificatePem = minosCertificatePem;
		this.store = store;
	}

	@Override
	public void run() {
		try (ServerSocket server = new ServerSocket(port)) {
			this.serverSocket = server;
			logger.info("[ARA] Escuchando en el puerto {}", port);
			while (running) {
				Socket socket = server.accept();
				pool.submit(new AraSession(socket, identity, minosPublicKey, minosCertificateNumber,
						minosCertificatePem, store));
			}
		} catch (Exception e) {
			if (running) {
				logger.error("[ARA] Servidor detenido con error: {}", e.getMessage(), e);
			}
		}
	}

	public void stop() {
		running = false;
		pool.shutdownNow();
		try {
			if (serverSocket != null) {
				serverSocket.close();
			}
		} catch (Exception ignored) {
			// cierre en curso, no hay nada más que hacer
		}
	}
}
