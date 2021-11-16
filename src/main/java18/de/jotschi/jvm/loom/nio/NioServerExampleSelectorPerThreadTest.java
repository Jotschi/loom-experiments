package de.jotschi.jvm.loom.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.concurrent.ThreadFactory;

import org.junit.Test;

/**
 * NIO Server test which uses the non-async API.
 */
public class NioServerExampleSelectorPerThreadTest {

  private final static int PORT = 8080;

  private final static int THREAD_COUNT = 100;

  @Test
  public void testServer() throws IOException {

    InetSocketAddress addr = new InetSocketAddress("localhost", PORT);

    // Open a new ServerSocketChannel so we can listen for connections
    ServerSocketChannel acceptor = ServerSocketChannel.open();

    // Configure the socket to be non-blocking as part of the new-IO library (NIO)
    acceptor.configureBlocking(false);

    // Bind our socket to the local port (5555)
    acceptor.socket().bind(addr);

    // Reuse the address so more than one connection can come in
    acceptor.socket().setReuseAddress(true);

    System.err.println("Server running on http://127.0.0.1:" + PORT + '/');

    ThreadFactory executor = Thread.ofVirtual().factory();

    // for (int i = 0; i < THREAD_COUNT; i++) {
    // executor.newThread(() -> {
    // try {
    // Open our selector channel
    Selector selector = SelectorProvider.provider().openSelector();

    // Register an "Accept" event on our selector service which will let us know when sockets connect to our channel
    SelectionKey acceptKey = acceptor.register(selector, SelectionKey.OP_ACCEPT);

    // Set our key's interest OPs to "Accept"
    acceptKey.interestOps(SelectionKey.OP_ACCEPT);

    while (true) {

      selector.select();

      Iterator<SelectionKey> selectorIt = selector.selectedKeys().iterator();
      while (selectorIt.hasNext()) {
        SelectionKey currentKey = selectorIt.next();
        selectorIt.remove();

        // skip any invalidated keys
        if (!currentKey.isValid()) {
          continue;
        }

        // Get a reference to one of our custom objects
        Client client = (Client) currentKey.attachment();
        try {
          if (currentKey.isAcceptable()) {
            accept(currentKey);
          }

          if (currentKey.isReadable()) {
            client.handleRead();

            String request = client.readMessage();
            if (request.endsWith("*/*")) {
              client.sendMessage(createResponse());
              client.handleWrite();
              client.disconnect();
            }
          }

          // if (currentKey.isWritable()) {
          // System.out.println("Writing");
          // client.handleWrite();
          // client.disconnect();
          // }
        } catch (Exception e) {
          e.printStackTrace();
          // Disconnect the user if we have any errors during processing, you can add your own custom logic here
          client.disconnect();
        }

      }

    }
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // }).start();
    // }

    // System.in.read();
    // System.out.println("Wait for termination. Press enter to stop server.");
  }

  private static void accept(SelectionKey key) throws IOException {
    // 'Accept' selection keys contain a reference to the parent server-socket channel rather than their own socket
    ServerSocketChannel channel = (ServerSocketChannel) key.channel();

    // Accept the socket's connection
    SocketChannel socket = channel.accept();

    // You can get the IPV6 Address (if available) of the connected user like so:
    String ipAddress = socket.socket().getInetAddress().getHostAddress();

    // We also want this socket to be non-blocking so we don't need to follow the thread-per-socket model
    socket.configureBlocking(false);

    // Let's also register this socket to our selector:
    // We are going to listen for two events (Read and Write).
    // These events tell us when the socket has bytes available to read, or if the buffer is available to write
    SelectionKey k = socket.register(key.selector(), SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    // We are only interested in events for reads for our selector.
    k.interestOps(SelectionKey.OP_READ);

    // Here you can bind an object to the key as an attachment should you so desire.
    // This could be a reference to an object or anything else.
    k.attach(new Client(ipAddress, socket, k));
  }

  private String createResponse() {
    return """
      HTTP/1.1 200 OK
      Content-Length: 13
      Content-Type: text/plain; charset=utf-8

      Hello World!
      """;
  }

  private void log(int threadNr, String str) {
    System.out.println("[" + threadNr + "] " + str);
  }

  static class Client {

    ByteBuffer bufferIn;
    ByteBuffer bufferOut;

    SelectionKey key;
    SocketChannel socket;
    String ipAddress;

    public Client(String ipAddress, SocketChannel socket, SelectionKey key) {
      this.ipAddress = ipAddress;
      this.socket = socket;
      this.key = key;

      bufferIn = ByteBuffer.allocate(4096);
      bufferOut = ByteBuffer.allocate(4096);
    }

    public String readMessage() {
      return new String(bufferIn.array()).trim();
    }

    public void sendMessage(String message) {
      bufferOut.put(message.getBytes());
    }

    public int handleRead() throws IOException {
      int bytesIn = 0;
      bytesIn = socket.read(bufferIn);
      if (bytesIn == -1) {
        throw new IOException("Socket closed");
      }
      if (bytesIn > 0) {
        bufferIn.flip();
        bufferIn.mark();
        bufferIn.compact();
      }
      return bytesIn;
    }

    public int handleWrite() throws IOException {
      bufferOut.flip();
      int bytesOut = socket.write(bufferOut);
      bufferOut.compact();
      // If we weren't able to write the entire buffer out, make sure we alert the selector
      // so we can be notified when we are able to write more bytes to the socket
      if (bufferOut.hasRemaining()) {
        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
      } else {
        key.interestOps(SelectionKey.OP_READ);
      }
      return bytesOut;
    }

    public void disconnect() {
      try {
        socket.close();
        key.cancel();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }
}
