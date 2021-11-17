package de.jotschi.jvm.loom.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import org.junit.Test;

/**
 * NIO Server test which uses the non-async API.
 */
public class NioServerExampleSelectorPerThreadTest {

  private final static int PORT = 8080;

  private final static int THREAD_COUNT = 100;

  @Test
  @SuppressWarnings("preview")
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

    // Open our selector channel
    Selector selector = Selector.open();

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
            // Now create a virtual thread which will handle the connection read/write
            Thread.startVirtualThread(() -> {
              System.out.println("Running thread");
              try {
                while (true) {

                    while (selectorIt.hasNext()) {
                      System.out.println("Got virt key");
                      SelectionKey virtCurrentKey = selectorIt.next();
                      selectorIt.remove();

                      // skip any invalidated keys
                      if (!virtCurrentKey.isValid()) {
                        continue;
                      }

                      if (virtCurrentKey.isReadable()) {
                        System.out.println("Handle read");
                        try {
                          client.handleRead();

                          String request = client.readMessage();
                          if (request.endsWith("*/*")) {
                            System.out.println("handle write");
                            client.sendMessage(createResponse());
                            client.handleWrite();
                            client.disconnect();
                          }
                        } catch (Exception e) {
                          e.printStackTrace();
                        }
                      }

                    }
                  }
              } catch (Throwable t) {
                t.printStackTrace();
              }
            });
          }

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

  private static void accept(SelectionKey acceptKey) throws IOException {

    // 'Accept' selection keys contain a reference to the parent server-socket channel rather than their own socket
    ServerSocketChannel channel = (ServerSocketChannel) acceptKey.channel();

    // Accept the socket's connection
    SocketChannel socket = channel.accept();
    System.out.println("Accepted connection");

    // We also want this socket to be non-blocking so we don't need to follow the thread-per-socket model
    socket.configureBlocking(false);

    System.out.println("Registered selector key");
    SelectionKey virtKey = socket.register(acceptKey.selector(), SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    // We are only interested in events for reads for our selector.
    virtKey.interestOps(SelectionKey.OP_READ);
    // You can get the IPV6 Address (if available) of the connected user like so:
    String ipAddress = socket.socket().getInetAddress().getHostAddress();

    // Here you can bind an object to the key as an attachment should you so desire.
    // This could be a reference to an object or anything else.
    virtKey.attach(new Client(ipAddress, socket, virtKey));

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
