import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.*;

public class ChatServidor {
    private static final List<ClienteHandler> clientes = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, String> llavesPublicas = new HashMap<>();

    public static void main(String[] args) {
        int puerto = 5000;
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream("server.jks"), "123456".toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, "123456".toCharArray());
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);

            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(puerto);
            System.out.println("Servidor actiu al port " + puerto);

            while (true) {
                SSLSocket socketCliente = (SSLSocket) serverSocket.accept();
                ClienteHandler handler = new ClienteHandler(socketCliente);
                clientes.add(handler);
                new Thread(handler).start();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static synchronized void registrarLlave(String nick, String llaveBase64) {
        llavesPublicas.put(nick, llaveBase64);
        broadcast("KEY_UPDATE:" + nick + ":" + llaveBase64, null);
    }

    public static synchronized void enviarLlavesACliente(ClienteHandler cliente) {
        for (Map.Entry<String, String> entry : llavesPublicas.entrySet()) {
            cliente.enviar("KEY_UPDATE:" + entry.getKey() + ":" + entry.getValue());
        }
    }

    public static void broadcast(String mensaje, ClienteHandler emisor) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                if (cliente != emisor) cliente.enviar(mensaje);
            }
        }
    }

    public static void eliminarCliente(ClienteHandler cliente) {
        clientes.remove(cliente);
        llavesPublicas.remove(cliente.getNick());
    }
}