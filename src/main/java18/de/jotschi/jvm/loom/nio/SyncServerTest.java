package de.jotschi.jvm.loom.nio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class SyncServerTest {

	private static final int THREAD_COUNT = 4;

	@Test
	public void testServer() throws IOException {
		try (ServerSocket serverSocket = new ServerSocket(8080)) {
			while (true) {
				try {
					Socket client = serverSocket.accept();
					Thread.startVirtualThread(() -> {
						try {
							// System.out.println("Accepting connection");
							ByteBuffer buffer = ByteBuffer.allocate(512);
							handleClient(buffer, client);
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Test
	public void testPooledServer() throws IOException {
		AtomicLong counter = new AtomicLong();
		try (ServerSocket serverSocket = new ServerSocket(8080)) {
			for (int i = 0; i < THREAD_COUNT; i++) {
				Thread.startVirtualThread(() -> {
					System.out.format("Starting virtual thread %n", counter.incrementAndGet());
					ByteBuffer buffer = ByteBuffer.allocate(512);
					while (true) {
						try {
							Socket client = serverSocket.accept();
							try {
								// System.out.println("Accepting connection");
								handleClient(buffer, client);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
		}
		System.out.println("Press enter to stop server");
		System.in.read();
	}

	private void handleClient(ByteBuffer buffer, Socket client) throws IOException {

		ReadableByteChannel inputChannel = Channels.newChannel(client.getInputStream());
		inputChannel.read(buffer);
		buffer.flip();
		String message = new String(buffer.array()).trim();
		// System.out.println("Got: " + message);
		if (message.endsWith("*/*")) {
			String response = createResponse();
			ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes(Charset.defaultCharset()));
			WritableByteChannel outputChannel = Channels.newChannel(client.getOutputStream());
			outputChannel.write(responseBuffer);
			buffer.clear();
			outputChannel.close();
		}
		// inputChannel.close();
		client.close();

	}

	private String createResponse() {
		return """
				HTTP/1.1 200 OK
				Content-Length: 13
				Content-Type: text/plain; charset=utf-8

				Hello World!
				""";
	}

}
