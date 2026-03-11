import java.io.*;
import java.nio.charset.Charset;
import java.util.Base64;
import javax.net.ssl.SSLSocket;

public class ClienteHandler implements Runnable {
    private final SSLSocket socket;
    private BufferedReader entrada;
    private BufferedWriter salida;
    private String nick = "Anònim";
    private boolean autenticado = false;
    private final Charset charset = Charset.forName("UTF-8");

    public ClienteHandler(SSLSocket socket) {
        this.socket = socket;
    }

    public String getNick() { return nick; }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
            salida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), charset));

            String linea;
            while ((linea = entrada.readLine()) != null) {
                if (!autenticado) {
                    procesarAutenticacion(linea);
                } else {
                    procesarComandos(linea);
                }
            }
        } catch (IOException e) {
            System.out.println("Client desconnectat: " + nick);
        } finally {
            ChatServidor.eliminarCliente(this);
            cerrar();
        }
    }

    private void procesarAutenticacion(String linea) {
        if (linea.startsWith("LOGIN:")) {
            String[] parts = linea.substring(6).split(":");
            if (parts.length == 2 && ChatServidor.validarUsuario(parts[0], parts[1])) {
                this.autenticado = true;
                this.nick = parts[0];
                enviar("LOGIN_OK");
                ChatServidor.broadcast("--- " + nick + " connectat ---", this);
            } else {
                enviar("LOGIN_ERROR");
            }
        }
    }

    private void procesarComandos(String linea) {
        // Enviar clau pública: SET_KEY:Base64Key
        if (linea.startsWith("SET_KEY:")) {
            String key = linea.substring(8);
            ChatServidor.registrarLlave(this.nick, key);
        }
        // Enviar missatge ultrasegur: SEND_ULTRA:destinatari:MensajeBase64
        else if (linea.startsWith("SEND_ULTRA:")) {
            String[] parts = linea.substring(11).split(":", 2);
            if (parts.length == 2) {
                ChatServidor.enviarPrivado(parts[0], parts[1], this.nick);
            }
        }
        // Missatge broadcast normal
        else {
            ChatServidor.broadcast(this.nick + ": " + linea, this);
        }
    }

    public void enviar(String mensaje) {
        try {
            salida.write(mensaje);
            salida.newLine();
            salida.flush();
        } catch (IOException e) { cerrar(); }
    }

    private void cerrar() {
        try { socket.close(); } catch (IOException ignored) {}
    }
}