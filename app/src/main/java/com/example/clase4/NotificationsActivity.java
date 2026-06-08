package com.example.clase4;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationsActivity extends AppCompatActivity {

    private TextView txtMensajeNotificaciones;
    private LinearLayout contenedorNotificaciones;

    private int userId;
    private String token;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#071827"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#071827"));

        txtMensajeNotificaciones = findViewById(R.id.txtMensajeNotificaciones);
        contenedorNotificaciones = findViewById(R.id.contenedorNotificaciones);

        findViewById(R.id.btnBackNotificaciones).setOnClickListener(v -> finish());

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = preferences.getInt("userId", 0);
        token = preferences.getString("token", "");

        cargarNotificaciones();
    }

    private void cargarNotificaciones() {
        txtMensajeNotificaciones.setText("Cargando notificaciones...");
        contenedorNotificaciones.removeAllViews();

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/notifications");
                connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = connection.getResponseCode();

                InputStream inputStream;

                if (statusCode >= 200 && statusCode < 300) {
                    inputStream = connection.getInputStream();
                } else {
                    inputStream = connection.getErrorStream();
                }

                String respuesta = leerRespuesta(inputStream);

                if (statusCode == 200) {
                    JSONArray notificaciones = new JSONArray(respuesta);
                    mainHandler.post(() -> mostrarNotificaciones(notificaciones));
                } else {
                    JSONObject errorJson = new JSONObject(respuesta);
                    String error = errorJson.optString("error", "Error al cargar notificaciones");
                    mainHandler.post(() -> txtMensajeNotificaciones.setText(error));
                }

            } catch (Exception e) {
                mainHandler.post(() -> txtMensajeNotificaciones.setText("No se pudo conectar con el servidor."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void mostrarNotificaciones(JSONArray notificaciones) {
        contenedorNotificaciones.removeAllViews();

        if (notificaciones.length() == 0) {
            txtMensajeNotificaciones.setText("No tenés notificaciones.");
            return;
        }

        txtMensajeNotificaciones.setText("Notificaciones encontradas: " + notificaciones.length());

        try {
            for (int i = 0; i < notificaciones.length(); i++) {
                JSONObject notificacion = notificaciones.getJSONObject(i);

                int id = notificacion.optInt("id", 0);
                String titulo = notificacion.optString("titulo", "-");
                String mensaje = notificacion.optString("mensaje", "-");
                String fechaHora = formatearFechaHora(notificacion.optString("fechaHora", "-"));
                String leida = notificacion.optString("leida", "no");

                View card = crearCardNotificacion(id, titulo, mensaje, fechaHora, leida);
                contenedorNotificaciones.addView(card);
            }

        } catch (Exception e) {
            txtMensajeNotificaciones.setText("Error mostrando notificaciones.");
        }
    }

    private View crearCardNotificacion(
            int id,
            String titulo,
            String mensaje,
            String fechaHora,
            String leida
    ) {
        int pad = (int)(18 * getResources().getDisplayMetrics().density);
        int marginBottom = (int)(16 * getResources().getDisplayMetrics().density);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundResource(R.drawable.bg_card_premium);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, marginBottom);
        card.setLayoutParams(params);
        card.setElevation(4);

        // Header row: title + chip
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView txtTitulo = new TextView(this);
        txtTitulo.setText(titulo);
        txtTitulo.setTextSize(16);
        txtTitulo.setTextColor(Color.parseColor("#071827"));
        txtTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        txtTitulo.setLayoutParams(titleParams);

        TextView txtEstado = new TextView(this);
        if (leida.equals("si")) {
            txtEstado.setText("LEÍDA");
            txtEstado.setBackgroundResource(R.drawable.bg_button_outline);
            txtEstado.setTextColor(Color.parseColor("#64748B"));
        } else {
            txtEstado.setText("NUEVA");
            txtEstado.setBackgroundResource(R.drawable.bg_gold_chip);
            txtEstado.setTextColor(Color.parseColor("#071827"));
        }
        txtEstado.setTextSize(10);
        txtEstado.setTypeface(null, android.graphics.Typeface.BOLD);
        txtEstado.setPadding(16, 6, 16, 6);

        headerRow.addView(txtTitulo);
        headerRow.addView(txtEstado);

        TextView txtMensaje = new TextView(this);
        txtMensaje.setText(mensaje);
        txtMensaje.setTextSize(14);
        txtMensaje.setTextColor(Color.parseColor("#475569"));
        txtMensaje.setPadding(0, 10, 0, 8);
        txtMensaje.setLineSpacing(4, 1.0f);

        TextView txtDetalle = new TextView(this);
        txtDetalle.setText(fechaHora);
        txtDetalle.setTextSize(12);
        txtDetalle.setTextColor(Color.parseColor("#A8872F"));

        card.addView(headerRow);
        card.addView(txtMensaje);
        card.addView(txtDetalle);

        return card;
    }

    private String leerRespuesta(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder respuesta = new StringBuilder();
        String linea;
        while ((linea = reader.readLine()) != null) {
            respuesta.append(linea);
        }
        return respuesta.toString();
    }

    private String formatearFechaHora(String raw) {
        if (raw == null || raw.equals("-")) return "-";
        raw = raw.trim();
        if (raw.startsWith("date ")) raw = raw.substring(5).trim();
        try {
            String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            java.text.SimpleDateFormat inFmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
            String fecha = outFmt.format(inFmt.parse(datePart));
            if (raw.length() > 10) {
                String after = raw.substring(10);
                if (after.startsWith("T") || after.startsWith(" ")) after = after.substring(1);
                String hora = after.length() >= 5 ? after.substring(0, 5) : after;
                return fecha + " " + hora;
            }
            return fecha;
        } catch (Exception e) { return raw; }
    }
}