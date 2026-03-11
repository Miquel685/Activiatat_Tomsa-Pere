import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.util.*;

public class ChatServidor {
    // Lista de hilos de clientes conectados
    private static final List<ClienteHandler> clientes = Collections.synchronizedList(new ArrayList<>());
    // Mapa para guardar las llaves públicas de cada usuario conectado
    private static final Map<String, String> llavesPublicas = new HashMap<>();
    private static final String USER_FILE = "usuarios.txt"; // Fitxer usuari:password

    public static void main(String[] args) {
        int puerto = 5000;
        try {
            // Configurar SSL con el KeyStore generado
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream("server.jks"), "123456".toCharArray());

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, "123456".toCharArray());

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(kmf.getKeyManagers(), null, null);

            SSLServerSocketFactory ssf = sc.getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(puerto);

            System.out.println("Servidor SSL UltraSegur actiu al port " + puerto);

            while (true) {
                SSLSocket socketCliente = (SSLSocket) serverSocket.accept();
                ClienteHandler handler = new ClienteHandler(socketCliente);
                clientes.add(handler);
                new Thread(handler).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Valida credenciales contra el fichero de texto
    public static boolean validarUsuario(String user, String pass) {
        try (BufferedReader br = new BufferedReader(new FileReader(USER_FILE))) {
            String linea;
            String loginRequerit = user + ":" + pass;
            while ((linea = br.readLine()) != null) {
                if (linea.trim().equals(loginRequerit)) return true;
            }
        } catch (IOException e) {
            System.err.println("Error fitxer usuaris: " + e.getMessage());
        }
        return false;
    }

    // Registra la llave pública de un usuario y la anuncia a los demás
    public static synchronized void registrarLlave(String nick, String llaveBase64) {
        llavesPublicas.put(nick, llaveBase64);
        broadcast("KEY_UPDATE:" + nick + ":" + llaveBase64, null);
    }

    public static void broadcast(String mensaje, ClienteHandler emisor) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                if (cliente != emisor) cliente.enviar(mensaje);
            }
        }
    }

    // Envía un mensaje privado (ultraseguro) a un destinatario específico
    public static void enviarPrivado(String destino, String mensajeCifrado, String remitente) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                if (cliente.getNick().equals(destino)) {
                    cliente.enviar("ULTRA_MSG:" + remitente + ":" + mensajeCifrado);
                    break;
                }
            }
        }
    }

    public static void eliminarCliente(ClienteHandler cliente) {
        clientes.remove(cliente);
        llavesPublicas.remove(cliente.getNick());
    }
}