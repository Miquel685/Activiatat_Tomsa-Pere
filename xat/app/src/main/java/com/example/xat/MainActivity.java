package com.example.xat;

// Importamos un montón de librerías: para los diálogos, colores, PDFs, hilos y seguridad
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.io.*;
import java.nio.charset.Charset;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.net.ssl.*;

// Clase principal donde pasa toda la magia de la pantalla
public class MainActivity extends AppCompatActivity {
    // Definimos el socket SSL (el "túnel" seguro) para hablar con el servidor
    private SSLSocket socket;
    // Buffers para leer (in) y escribir (out) texto de forma eficiente
    private BufferedReader in;
    private BufferedWriter out;
    // Usamos UTF-8 para que las tildes y las "ñ" no salgan raras
    private final Charset charset = Charset.forName("UTF-8");
    // Nombre del usuario por defecto si no pone nada
    private String userNick = "anonimus";

    // Estas son las vistas de los circulitos que cambian de color según la conexión
    private StatusView statusView, statusViewChat;

    // Variables para las llaves RSA: la pública (la compartes) y la privada (la guardas tú)
    private PublicKey publicKey;
    private PrivateKey privateKey;
    // Un mapa (como una agenda) para guardar las llaves públicas de los otros usuarios
    private Map<String, String> contactesLlaves = new HashMap<>();

    // Variables para manejar los diferentes "fragmentos" o pantallas que ocultamos/mostramos
    private View layoutHost, layoutNick, layoutChat, layoutAutor;

    // Elementos donde el usuario escribe (inputs) y donde lee (chat)
    private EditText etHost, etPort, etNick, etMessage;
    private TextView tvChatArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Cargamos el diseño XML de la actividad
        setContentView(R.layout.activity_main);

        // Buscamos los componentes en el XML mediante su ID para poder usarlos en Java
        statusView = findViewById(R.id.connectionStatus);
        statusViewChat = findViewById(R.id.connectionStatusChat);

        // Vinculamos los contenedores de pantalla
        layoutHost = findViewById(R.id.layoutHost);
        layoutNick = findViewById(R.id.layoutNick);
        layoutChat = findViewById(R.id.layoutChat);
        layoutAutor = findViewById(R.id.layoutAutor);

        // Vinculamos los cuadros de texto y el área de chat
        etHost = findViewById(R.id.etHost);
        etPort = findViewById(R.id.etPort);
        etNick = findViewById(R.id.etNick);
        etMessage = findViewById(R.id.etMessage);
        tvChatArea = findViewById(R.id.tvChatArea);

        // Generamos el par de llaves criptográficas nada más abrir la app
        generarClausRSA();

        // Esto es para que el botón de Modo Oscuro cambie el tema del sistema (luz/noche)
        View.OnClickListener toggleDark = v -> {
            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        };

        // Asignamos la función de cambio de tema a los botones correspondientes
        Button btnDark1 = findViewById(R.id.btnDarkModeMain);
        if (btnDark1 != null) btnDark1.setOnClickListener(toggleDark);

        Button btnDark2 = findViewById(R.id.btnDarkMode);
        if (btnDark2 != null) btnDark2.setOnClickListener(toggleDark);

        // Programamos el botón de CONNECTAR: pilla la IP y el Puerto e intenta la conexión
        Button btnConnect = findViewById(R.id.btnConnect);
        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> {
                String host = etHost.getText().toString().trim();
                String portStr = etPort.getText().toString().trim();
                // Si no están vacíos, lanzamos el método de conexión
                if (!host.isEmpty() && !portStr.isEmpty()) {
                    conectarAlServidor(host, Integer.parseInt(portStr));
                } else {
                    Toast.makeText(this, "Omple els camps de connexió", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Botón para ir a la pantalla de FAQ (Ayuda)
        Button btnVerAutor = findViewById(R.id.btnVerAutor);
        if (btnVerAutor != null) {
            btnVerAutor.setOnClickListener(v -> {
                layoutHost.setVisibility(View.GONE); // Escondemos el host
                layoutAutor.setVisibility(View.VISIBLE); // Mostramos FAQ
            });
        }

        // Botón BEGIN: para entrar al chat con tu Nick
        Button btnBegin = findViewById(R.id.btnBegin);
        if (btnBegin != null) {
            btnBegin.setOnClickListener(v -> {
                String user = etNick.getText().toString().trim();
                // Si está vacío, le ponemos el nick de anónimo
                if (user.isEmpty()) user = "anonimus";
                userNick = user;
                // Si el socket está listo, mandamos el comando de LOGIN
                if (out != null) enviarLogin(user);
            });
        }

        // Botón SEND: manda lo que has escrito al chat general
        Button btnSend = findViewById(R.id.btnSend);
        if (btnSend != null) {
            btnSend.setOnClickListener(v -> {
                String msg = etMessage.getText().toString().trim();
                // Solo enviamos si hay texto y estamos conectados
                if (!msg.isEmpty() && out != null) {
                    enviarMissatgeNormal(msg);
                }
            });
        }

        // Botón para generar el PDF de la conversación
        Button btnPdf = findViewById(R.id.btnPdf);
        if (btnPdf != null) {
            btnPdf.setOnClickListener(v -> generarInformePDF());
        }

        // Botón EXIT: para cerrar la sesión y volver atrás
        Button btnExit = findViewById(R.id.btnExit);
        if (btnExit != null) btnExit.setOnClickListener(v -> tancarSessio());

        // Botón para volver desde la pantalla de FAQ
        Button btnExitAutor = findViewById(R.id.btnExitAutor);
        if (btnExitAutor != null) {
            btnExitAutor.setOnClickListener(v -> {
                layoutAutor.setVisibility(View.GONE);
                layoutHost.setVisibility(View.VISIBLE);
            });
        }
    }

    // Método para conectar: se hace en un hilo aparte (Thread) para que la pantalla no se congele
    private void conectarAlServidor(String host, int port) {
        new Thread(() -> {
            try {
                // Configuramos SSL para que confíe en el servidor (aunque el certificado sea de prueba)
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                }}, new SecureRandom());

                // Creamos el socket seguro y hacemos el "apretón de manos" (handshake)
                SSLSocketFactory factory = sc.getSocketFactory();
                socket = (SSLSocket) factory.createSocket(host, port);
                socket.startHandshake();

                // Inicializamos los flujos para leer y escribir texto
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), charset));

                // Cuando hay cambios de UI (colores, pantallas), hay que usar runOnUiThread
                runOnUiThread(() -> {
                    statusView.setColor(Color.GREEN); // Ponemos el círculo en verde
                    if (statusViewChat != null) statusViewChat.setColor(Color.GREEN);

                    // Esperamos un segundo y cambiamos a la pantalla de elegir Nick
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        layoutHost.setVisibility(View.GONE);
                        layoutNick.setVisibility(View.VISIBLE);
                    }, 1000);
                });
                // Lanzamos el hilo que se queda escuchando mensajes
                iniciarEscoltador();
            } catch (Exception e) {
                // Si falla, ponemos el círculo en rojo y avisamos
                runOnUiThread(() -> {
                    statusView.setColor(Color.RED);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start(); // ¡No olvides el .start() o el hilo nunca se ejecutará!
    }

    // Este hilo lee todo lo que llega del servidor en bucle
    private void iniciarEscoltador() {
        new Thread(() -> {
            try {
                String linea;
                // Mientras el servidor nos mande líneas de texto...
                while ((linea = in.readLine()) != null) {
                    final String msg = linea;
                    runOnUiThread(() -> {
                        // Si el server dice que el login es correcto, pasamos al chat
                        if (msg.equals("LOGIN_OK")) {
                            layoutNick.setVisibility(View.GONE);
                            layoutChat.setVisibility(View.VISIBLE);
                            enviarClauPublica(); // Mandamos nuestra llave RSA al servidor
                        }
                        // Si recibimos una llave nueva de otro usuario, la guardamos
                        else if (msg.startsWith("KEY_UPDATE:")) {
                            String[] p = msg.split(":");
                            contactesLlaves.put(p[1], p[2]);
                        }
                        // Por defecto, pintamos el mensaje en el área de chat
                        else {
                            tvChatArea.append(msg + "\n");
                        }
                    });
                }
            } catch (IOException e) {}
        }).start();
    }

    // Cerramos el socket y volvemos a la pantalla de inicio
    private void tancarSessio() {
        new Thread(() -> {
            try {
                if (socket != null) socket.close();
                runOnUiThread(() -> {
                    statusView.setColor(Color.GRAY); // Color gris = desconectado
                    layoutChat.setVisibility(View.GONE);
                    layoutHost.setVisibility(View.VISIBLE);
                });
            } catch (IOException e) {}
        }).start();
    }

    // Generamos un PDF a partir del texto que hay en pantalla
    private void generarInformePDF() {
        PdfDocument doc = new PdfDocument();
        // Tamaño A4 estándar
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = doc.startPage(info);

        // "Pintamos" el encabezado y el cuerpo del chat en el PDF
        page.getCanvas().drawText("Conversa de Xat - " + userNick, 10, 20, new android.graphics.Paint());
        page.getCanvas().drawText(tvChatArea.getText().toString(), 10, 40, new android.graphics.Paint());
        doc.finishPage(page);

        try {
            // Buscamos la carpeta de Descargas del móvil
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File f = new File(path, "xat.pdf");

            // Escribimos el archivo en el almacenamiento
            doc.writeTo(new FileOutputStream(f));
            Toast.makeText(this, "PDF generat a la carpeta Descarregues", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error creant el PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        doc.close();
    }

    // Genera el par de llaves RSA (Asimétricas) de 2048 bits
    private void generarClausRSA() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            publicKey = kp.getPublic(); // La que daremos a los demás
            privateKey = kp.getPrivate(); // La que nos permite leer mensajes a nosotros
        } catch (Exception e) {}
    }

    // Envía el comando LOGIN al servidor con tu nombre
    private void enviarLogin(String u) {
        new Thread(() -> {
            try {
                out.write("LOGIN:" + u + "\n");
                out.flush(); // .flush() obliga a que el texto salga por el cable ya mismo
            } catch (IOException e) {}
        }).start();
    }

    // Codifica la llave pública en Base64 (texto) para que el servidor pueda repartirla
    private void enviarClauPublica() {
        new Thread(() -> {
            try {
                String keyBase64 = Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
                out.write("SET_KEY:" + keyBase64 + "\n");
                out.flush();
            } catch (IOException e) {}
        }).start();
    }

    // Envía un mensaje normal. Lo pintamos en nuestro chat y lo mandamos al server
    private void enviarMissatgeNormal(String m) {
        new Thread(() -> {
            try {
                out.write(m + "\n");
                out.flush();
                runOnUiThread(() -> {
                    tvChatArea.append("Tu: " + m + "\n");
                    etMessage.setText(""); // Limpiamos el cajón de escribir
                });
            } catch (IOException e) {}
        }).start();
    }
}