package com.example.xat;

import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.net.ssl.*;

public class MainActivity extends AppCompatActivity {
    private SSLSocket socket; // Requeriment UT6: Ús de SSLSocket
    private BufferedReader in;
    private BufferedWriter out;
    private final Charset charset = Charset.forName("UTF-8");
    private String userNick = "Anònim";
    private StatusView statusView; // Component personalitzat UT4

    // Criptografia Asimètrica (Requeriment Servidor/Client)
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private Map<String, String> contactesLlaves = new HashMap<>(); // Guarda nicks i llaves públiques

    // Vistes de layout
    private View layoutHost, layoutNick, layoutChat, layoutAutor;
    // Elements de la UI
    private EditText etHost, etPort, etNick, etPassword, etMessage;
    private TextView tvChatArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.connectionStatus); // UT4: Cercle de color


        layoutHost = findViewById(R.id.layoutHost);
        layoutNick = findViewById(R.id.layoutNick);
        layoutChat = findViewById(R.id.layoutChat);
        layoutAutor = findViewById(R.id.layoutAutor);

        // Vincular controls (IDs definits als teus fragments)
        etHost = findViewById(R.id.etHost);
        etPort = findViewById(R.id.etPort);
        etNick = findViewById(R.id.etNick);
        etPassword = findViewById(R.id.etPassword); // Requeriment Login
        etMessage = findViewById(R.id.etMessage);
        tvChatArea = findViewById(R.id.tvChatArea);

        // Generar claus RSA al iniciar l'app
        generarClausRSA();

        // BOTÓ CONNECT + (Lògica ChatCliente)
        Button btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(v -> {
            String host = etHost.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            if (!host.isEmpty() && !portStr.isEmpty()) {
                conectarAlServidor(host, Integer.parseInt(portStr));
            } else {
                Toast.makeText(this, "Emplena els camps", Toast.LENGTH_SHORT).show();
            }
        });

        // Navegació Ajuda/FAQ (Substitueix Autor UT5)
        Button btnVerAutor = findViewById(R.id.btnVerAutor);
        if (btnVerAutor != null) {
            btnVerAutor.setOnClickListener(v -> {
                layoutHost.setVisibility(View.GONE);
                layoutAutor.setVisibility(View.VISIBLE);
            });
        }

        // BOTÓ BEGIN
        Button btnBegin = findViewById(R.id.btnBegin);
        btnBegin.setOnClickListener(v -> {
            String user = etNick.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();
            if (!user.isEmpty() && !pass.isEmpty()) {
                userNick = user;
                enviarLogin(user, pass);
            }
        });

        // BOTÓ SEND + (Lògica d'enviament)
        Button btnSend = findViewById(R.id.btnSend);
        btnSend.setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (!msg.isEmpty() && out != null) {
                enviarMissatgeNormal(msg);
            }
        });

        // Botó Exportar PDF (UT5)
        Button btnPdf = findViewById(R.id.btnPdf);
        if (btnPdf != null) btnPdf.setOnClickListener(v -> generarInformePDF());

        // BOTÓ EXIT + (tancament de socket)
        Button btnExit = findViewById(R.id.btnExit);
        btnExit.setOnClickListener(v -> tancarSessio());

        Button btnExitAutor = findViewById(R.id.btnExitAutor);
        if (btnExitAutor != null) {
            btnExitAutor.setOnClickListener(v -> {
                layoutAutor.setVisibility(View.GONE);
                layoutHost.setVisibility(View.VISIBLE);
            });
        }
    }

    // MÈTODE conectarAlServidor + CANVIAR COLOR CERCLE, SEGONS L'ESTAT DE LA CONNEXIÓ {
    private void conectarAlServidor(String host, int port) {
        new Thread(() -> {
            try {
                // Configuració SSL per acceptar certificats (UT6)
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, new TrustManager[]{new X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                }}, new SecureRandom());

                SSLSocketFactory factory = sc.getSocketFactory();
                socket = (SSLSocket) factory.createSocket(host, port);
                socket.startHandshake();

                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), charset));

                runOnUiThread(() -> {
                    statusView.setColor(Color.GREEN); // UT4: Cercle verd
                    layoutHost.setVisibility(View.GONE);
                    layoutNick.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Connexió SSL Segura", Toast.LENGTH_SHORT).show();
                });
                iniciarEscoltador();
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusView.setColor(Color.RED); // UT4: Cercle vermell
                    Toast.makeText(this, "Error de connexió", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // MÈTODE iniciarEscoltador
    private void iniciarEscoltador() {
        new Thread(() -> {
            try {
                String linea;
                while ((linea = in.readLine()) != null) {
                    String msg = linea;
                    runOnUiThread(() -> {
                        if (msg.equals("LOGIN_OK")) {
                            layoutNick.setVisibility(View.GONE);
                            layoutChat.setVisibility(View.VISIBLE);
                            enviarClauPublica(); // Enviem clau RSA al servidor
                        } else if (msg.equals("LOGIN_ERROR")) {
                            Toast.makeText(this, "Credencials incorrectes", Toast.LENGTH_SHORT).show();
                        } else if (msg.startsWith("KEY_UPDATE:")) {
                            String[] p = msg.split(":");
                            contactesLlaves.put(p[1], p[2]); // Guardem clau pública d'altre usuari
                        } else if (msg.startsWith("ULTRA_MSG:")) {
                            desxifrarMissatgeUltra(msg); // Desxifrar missatge asimètric
                        } else {
                            tvChatArea.append(msg + "\n");
                        }
                    });
                }
            } catch (IOException e) { Log.e("XAT", "Sessió finalitzada"); }
        }).start();
    }

    // MÈTODE tancarSessio
    private void tancarSessio() {
        new Thread(() -> {
            try {
                if (socket != null) socket.close();
                runOnUiThread(() -> {
                    statusView.setColor(Color.GRAY);
                    layoutChat.setVisibility(View.GONE);
                    layoutHost.setVisibility(View.VISIBLE);
                });
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    // --- FUNCIONS ADDICIONALS REQUERIMENTS ---

    private void generarClausRSA() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            publicKey = kp.getPublic();
            privateKey = kp.getPrivate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void enviarLogin(String u, String p) {
        new Thread(() -> {
            try {
                out.write("LOGIN:" + u + ":" + p + "\n");
                out.flush();
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void enviarClauPublica() {
        new Thread(() -> {
            try {
                String keyBase64 = Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
                out.write("SET_KEY:" + keyBase64 + "\n");
                out.flush();
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void enviarMissatgeNormal(String m) {
        new Thread(() -> {
            try {
                out.write(m + "\n");
                out.flush();
                runOnUiThread(() -> {
                    tvChatArea.append("Tu: " + m + "\n");
                    etMessage.setText("");
                });
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void desxifrarMissatgeUltra(String msg) {
        try {
            String[] p = msg.split(":");
            byte[] xifrat = Base64.decode(p[2], Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            String desxifrat = new String(cipher.doFinal(xifrat));
            tvChatArea.append("[ULTRA] " + p[1] + ": " + desxifrat + "\n");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void generarInformePDF() {
        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = doc.startPage(info);
        page.getCanvas().drawText("Conversa de Xat - " + userNick, 10, 20, new android.graphics.Paint());
        page.getCanvas().drawText(tvChatArea.getText().toString(), 10, 40, new android.graphics.Paint());
        doc.finishPage(page);
        try {
            File f = new File(getExternalFilesDir(null), "xat.pdf");
            doc.writeTo(new FileOutputStream(f));
            Toast.makeText(this, "PDF generat a Documents de l'app", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { e.printStackTrace(); }
        doc.close();
    }
}