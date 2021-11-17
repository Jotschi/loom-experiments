package de.jotschi.jvm.loom.nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("preview")
public class SyncServerTest {

  private static final int ACCEPTOR_THREAD_COUNT = 32;

  @Test
  @Ignore
  public void testServer() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(8080)) {
      while (true) {
        try {
          Socket client = serverSocket.accept();
          Thread.startVirtualThread(() -> {
            try {
              // System.out.println("Accepting connection");

              handleClient(client);
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
      for (int i = 0; i < ACCEPTOR_THREAD_COUNT; i++) {
        new Thread(() -> {
          System.out.format("Starting virtual thread %n", counter.incrementAndGet());
          while (true) {
            try {
              Socket client = serverSocket.accept();
              if (client.isConnected()) {
                Thread.startVirtualThread(() -> {
                  try {
                    handleClient2(client);
                  } catch (Throwable t) {
                    t.printStackTrace();
                  }
                });
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }).start();
      }
      System.out.println("Press enter to stop server");
      System.in.read();
    }
  }

  AtomicInteger handledConnections = new AtomicInteger(0);

  private void handleClient2(Socket client) throws IOException {
    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
    BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));

    String inputLine;
    while ((inputLine = in.readLine()) != null) {
      if (inputLine.endsWith("*/*")) {
        out.println(createResponse());
        break;
      }
    }
    int count = handledConnections.incrementAndGet();
    if (count % 20_000 == 0) {
      System.out.println("Connections: " + count);
    }
    in.close();
    out.close();
    client.close();
  }

  private void handleClient(Socket client) throws IOException {
    ByteBuffer buffer = ByteBuffer.allocate(512);
    ReadableByteChannel inputChannel = Channels.newChannel(client.getInputStream());
    int r = inputChannel.read(buffer);
    if (r == -1) {
      // System.out.println("Got -1");
      // inputChannel.close();
      // client.close();
      // return;
    }
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
      inputChannel.close();
      int count = handledConnections.incrementAndGet();
      if (count % 1000 == 0) {
        System.out.println("Connections: " + count);
      }
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
