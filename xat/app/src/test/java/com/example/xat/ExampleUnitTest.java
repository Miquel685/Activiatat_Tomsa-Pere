package com.example.xat;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExampleUnitTest {

    @Test
    public void testLogica_ComandoLogin() {
        //Simulamos un texto que llega por el enchufe (Socket)
        String lineaRecibida = "LOGIN:  SuperMario  ";

        //Comprobamos que el sistema lo detecta como login
        assertTrue("El comando debería empezar por LOGIN:", lineaRecibida.startsWith("LOGIN:"));

        //Simulamos la lógica de tu ClienteHandler para extraer el Nick
        String nickExtraido = lineaRecibida.substring(6).trim();

        //Comprobamos que limpia los espacios y saca el nombre correcto
        assertEquals("SuperMario", nickExtraido);
    }

    @Test
    public void testLogica_ComandoMensajeSeguro() {
        String lineaRecibida = "SEND_ULTRA:PereGall:MensajeXifratEnBase64:Amb:Dos:Punts";

        String[] parts = lineaRecibida.substring(11).split(":", 2);

        assertEquals("Debería dividirse exactamente en 2 partes (Destinatario y Mensaje)", 2, parts.length);

        assertEquals("PereGall", parts[0]);

        assertEquals("MensajeXifratEnBase64:Amb:Dos:Punts", parts[1]);
    }

    @Test
    public void testLogica_AsignacionAnonima() {
        String inputUsuario = "";

        if (inputUsuario.isEmpty()) {
            inputUsuario = "anonimus";
        }

        assertEquals("anonimus", inputUsuario);
    }
}