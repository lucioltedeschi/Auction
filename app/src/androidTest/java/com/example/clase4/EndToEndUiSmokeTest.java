package com.example.clase4;

import android.content.Context;
import android.view.View;
import android.widget.EditText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class EndToEndUiSmokeTest {

    @Before
    public void prepararSesionVisual() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("sesion", Context.MODE_PRIVATE).edit()
                .putInt("userId", 1)
                .putString("token", "ui-smoke")
                .putString("nombre", "Prueba")
                .putString("apellido", "Visual")
                .putString("categoria", "oro")
                .putBoolean("esAdmin", false)
                .commit();
    }

    @Test
    public void pantallasPrincipalesRenderizanSusAcciones() {
        comprobarVista(HomeActivity.class, R.id.btnSolicitarSubasta);
        comprobarVista(SubastasActivity.class, R.id.scrollSubastas);
        comprobarVista(ProductRequestActivity.class, R.id.btnEnviarSolicitud);
        comprobarVista(PaymentMethodsActivity.class, R.id.btnNuevaCuentaBancaria);
        comprobarVista(PurchasesActivity.class, R.id.txtMensajeCompras);
        comprobarVista(FinesActivity.class, R.id.txtMensajeMultas);
        comprobarVista(NotificationsActivity.class, R.id.txtMensajeNotificaciones);
        comprobarVista(HistoryActivity.class, R.id.txtMensajeHistorial);
        comprobarVista(ProfileActivity.class, R.id.btnActualizarPerfil);
        comprobarVista(RegistroActivity.class, R.id.btnRegistrar);
        comprobarVista(PendingVerificationActivity.class, R.id.btnVolverLoginPendiente);
        comprobarVista(AdminActivity.class, R.id.btnAdminActualizarPendientes);
    }

    @Test
    public void loginVacioExplicaQueFaltaCompletar() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.btnIngresar).performClick();
                EditText documento = activity.findViewById(R.id.edtDocumento);
                EditText clave = activity.findViewById(R.id.edtClave);
                assertNotNull(documento.getError());
                documento.setText("30123456");
                activity.findViewById(R.id.btnIngresar).performClick();
                assertNotNull(clave.getError());
            });
        }
    }

    private <T extends android.app.Activity> void comprobarVista(Class<T> activityClass, int viewId) {
        try (ActivityScenario<T> scenario = ActivityScenario.launch(activityClass)) {
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                assertNotNull("Falta la acción principal en " + activityClass.getSimpleName(), view);
                assertTrue("La acción principal quedó sin tamaño en " + activityClass.getSimpleName(),
                        view.getWidth() > 0 && view.getHeight() > 0);
            });
        }
    }
}
