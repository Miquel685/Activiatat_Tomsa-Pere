package com.example.xat;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testPantallaConexion_RellenarDatosYConectar() {
        //Verificar que los elementos principales de la pantalla "Host" son visibles
        onView(withId(R.id.etHost)).check(matches(isDisplayed()));
        onView(withId(R.id.etPort)).check(matches(isDisplayed()));
        onView(withId(R.id.btnConnect)).check(matches(isDisplayed()));

        //Escribir la IP (por ejemplo, el localhost del emulador)
        onView(withId(R.id.etHost))
                .perform(typeText("10.0.2.2"), closeSoftKeyboard());

        //Escribir el puerto
        onView(withId(R.id.etPort))
                .perform(typeText("5000"), closeSoftKeyboard());

        //Hacer clic en el botón de CONNECT
        onView(withId(R.id.btnConnect)).perform(click());
    }
}