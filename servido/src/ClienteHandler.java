import java.io.*;
import java.nio.charset.Charset;
import javax.net.ssl.SSLSocket;

public class ClienteHandler implements Runnable {
    private final SSLSocket socket;
    private BufferedReader entrada;
    private BufferedWriter salida;
    private String nick = "anonimus";
    private boolean autenticado = false;
    private final Charset charset = Charset.forName("UTF-8");

    public ClienteHandler(SSLSocket socket) { this.socket = socket; }
    public String getNick() { return nick; }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
            salida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), charset));
            String linea;
            while ((linea = entrada.readLine()) != null) {
                if (!autenticado) procesarAutenticacion(linea);
                else procesarComandos(linea);
            }
        } catch (IOException e) {
        } finally {
            ChatServidor.eliminarCliente(this);
            cerrar();
        }
    }

    private void procesarAutenticacion(String linea) {
        if (linea.startsWith("LOGIN:")) {
            String user = linea.substring(6).trim();
            if (user.isEmpty()) user = "anonimus";
            this.autenticado = true;
            this.nick = user;
            enviar("LOGIN_OK");
            ChatServidor.broadcast("--- " + nick + " connectat ---", this);
            ChatServidor.enviarLlavesACliente(this);
        }
    }

    private void procesarComandos(String linea) {
        if (linea.startsWith("SET_KEY:")) {
            ChatServidor.registrarLlave(this.nick, linea.substring(8));
        } else {
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

    private void cerrar() { try { socket.close(); } catch (IOException ignored) {} }
}