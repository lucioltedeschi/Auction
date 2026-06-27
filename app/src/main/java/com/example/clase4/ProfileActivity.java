package com.example.clase4;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileActivity extends AppCompatActivity {

    // Header
    private TextView txtProfileInitials;
    private TextView txtProfileName;
    private TextView txtProfileSubtitle;
    private Button   btnActualizarPerfil;

    // Status badge
    private TextView txtEstadoPerfil;

    // Data rows
    private TextView txtRowDoc;
    private TextView txtRowEmail;
    private TextView txtRowTelefono;
    private TextView txtRowDireccion;
    private TextView txtRowEstado;
    private TextView txtRowAdmitido;
    private TextView txtRowCategoria;

    private Button btnCerrarSesion;

    private int    userId;
    private String token;

    // Pre-fill values for edit dialog
    private String currentTelefono = "";
    private String currentDireccion = "";
    private String currentEmail = "";

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        getWindow().setStatusBarColor(Color.parseColor("#F3F0E8"));
        getWindow().setNavigationBarColor(Color.parseColor("#F3F0E8"));
        SystemBars.configure(this, "#F3F0E8", true, "#F3F0E8", true);
        BottomNavHelper.configurar(this);

        txtProfileInitials = findViewById(R.id.txtProfileInitials);
        txtProfileName     = findViewById(R.id.txtProfileName);
        txtProfileSubtitle = findViewById(R.id.txtProfileSubtitle);
        btnActualizarPerfil = findViewById(R.id.btnActualizarPerfil);
        txtEstadoPerfil    = findViewById(R.id.txtEstadoPerfil);
        txtRowDoc          = findViewById(R.id.txtRowDoc);
        txtRowEmail        = findViewById(R.id.txtRowEmail);
        txtRowTelefono     = findViewById(R.id.txtRowTelefono);
        txtRowDireccion    = findViewById(R.id.txtRowDireccion);
        txtRowEstado       = findViewById(R.id.txtRowEstado);
        txtRowAdmitido     = findViewById(R.id.txtRowAdmitido);
        txtRowCategoria    = findViewById(R.id.txtRowCategoria);
        btnCerrarSesion    = findViewById(R.id.btnCerrarSesion);

        SharedPreferences prefs = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = prefs.getInt("userId", 0);
        token  = prefs.getString("token", "");

        btnActualizarPerfil.setOnClickListener(v -> mostrarDialogoEdicion());
        btnCerrarSesion.setOnClickListener(v -> cerrarSesion());

        cargarPerfil();
    }

    // ── LOAD ──────────────────────────────────────────────────────────────────

    private void cargarPerfil() {
        txtProfileName.setText("Cargando...");
        txtEstadoPerfil.setVisibility(View.GONE);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/users/" + userId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = connection.getResponseCode();
                InputStream is = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String respuesta = leerRespuesta(is);

                if (statusCode == 200) {
                    JSONObject usuario = new JSONObject(respuesta);
                    mainHandler.post(() -> mostrarPerfil(usuario));
                } else {
                    String error = new JSONObject(respuesta).optString("error", "Error al cargar perfil");
                    mainHandler.post(() -> txtProfileName.setText(error));
                }
            } catch (Exception e) {
                mainHandler.post(() -> txtProfileName.setText("Sin conexión"));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    // ── DISPLAY ───────────────────────────────────────────────────────────────

    private void mostrarPerfil(JSONObject u) {
        try {
            String nombre    = u.optString("nombre", "");
            String apellido  = u.optString("apellido", "");
            String documento = u.optString("documento", "—");
            currentEmail     = u.optString("email", "");
            currentTelefono  = u.optString("telefono", "");
            currentDireccion = u.optString("direccion", "");
            String estado    = u.optString("estado", "—");
            String admitido  = u.optString("admitido", "—");
            String categoria = u.optString("categoria", "—");

            // Header
            String fullName = (nombre + " " + apellido).trim();
            txtProfileName.setText(fullName.isEmpty() ? "Sin nombre" : fullName);

            // Initials
            String initials = "";
            if (!nombre.isEmpty()) initials += nombre.charAt(0);
            if (!apellido.isEmpty()) initials += apellido.charAt(0);
            txtProfileInitials.setText(initials.isEmpty() ? "?" : initials.toUpperCase());

            // Subtitle: categoría + estado
            String subtitle = categoria.equals("—") ? "" : categoria.substring(0, 1).toUpperCase() + categoria.substring(1).toLowerCase();
            if (!estado.equals("—")) {
                subtitle += subtitle.isEmpty() ? estado : "  -  " + estado;
            }
            txtProfileSubtitle.setText(subtitle);

            // Data rows
            txtRowDoc.setText(documento);
            txtRowEmail.setText(currentEmail.isEmpty() ? "—" : currentEmail);
            txtRowTelefono.setText(currentTelefono.isEmpty() ? "—" : currentTelefono);
            txtRowDireccion.setText(currentDireccion.isEmpty() ? "—" : currentDireccion);
            txtRowEstado.setText(capitalize(estado));
            txtRowCategoria.setText(capitalize(categoria));

            // Admitido chip
            boolean habilitado = "si".equalsIgnoreCase(admitido);
            txtRowAdmitido.setText(habilitado ? "Sí" : "No");
            txtRowAdmitido.setTextColor(Color.parseColor(habilitado ? "#16A34A" : "#DC2626"));

            // Estado badge
            boolean activo = "activo".equalsIgnoreCase(estado) && habilitado;
            txtEstadoPerfil.setVisibility(View.VISIBLE);
            if (activo) {
                txtEstadoPerfil.setText("Habilitado para participar en subastas");
                txtEstadoPerfil.setTextColor(Color.parseColor("#166534"));
                txtEstadoPerfil.setBackgroundResource(R.drawable.bg_success_chip);
            } else {
                txtEstadoPerfil.setText("No habilitado para participar en subastas");
                txtEstadoPerfil.setTextColor(Color.parseColor("#991B1B"));
                txtEstadoPerfil.setBackgroundResource(R.drawable.bg_danger_chip);
            }

        } catch (Exception e) {
            txtProfileName.setText("Error mostrando perfil");
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty() || s.equals("—")) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    // ── EDIT DIALOG ───────────────────────────────────────────────────────────

    private void mostrarDialogoEdicion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar perfil");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);

        EditText edtTelefono = new EditText(this);
        edtTelefono.setHint("Teléfono");
        edtTelefono.setText(currentTelefono);
        edtTelefono.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        EditText edtDireccion = new EditText(this);
        edtDireccion.setHint("Domicilio");
        edtDireccion.setText(currentDireccion);

        EditText edtEmail = new EditText(this);
        edtEmail.setHint("Email");
        edtEmail.setText(currentEmail);
        edtEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(12));
        edtTelefono.setLayoutParams(p);

        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p2.setMargins(0, 0, 0, dp(12));
        edtDireccion.setLayoutParams(p2);

        layout.addView(edtTelefono);
        layout.addView(edtDireccion);
        layout.addView(edtEmail);

        builder.setView(layout);
        builder.setPositiveButton("Guardar", (dialog, which) ->
                actualizarPerfil(
                        edtTelefono.getText().toString().trim(),
                        edtDireccion.getText().toString().trim(),
                        edtEmail.getText().toString().trim()));
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    private void actualizarPerfil(String telefono, String direccion, String email) {
        txtEstadoPerfil.setVisibility(View.VISIBLE);
        txtEstadoPerfil.setText("Guardando...");
        txtEstadoPerfil.setTextColor(Color.parseColor("#475569"));
        txtEstadoPerfil.setBackground(null);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/users/" + userId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                if (!telefono.isEmpty()) body.put("telefono", telefono);
                if (!direccion.isEmpty()) body.put("direccion", direccion);
                if (!email.isEmpty())    body.put("email", email);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                InputStream is = code >= 200 && code < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                JSONObject json = new JSONObject(leerRespuesta(is));

                if (code == 200) {
                    mainHandler.post(() -> cargarPerfil());
                } else {
                    String error = json.optString("error", "No se pudo actualizar");
                    mainHandler.post(() -> {
                        txtEstadoPerfil.setText(error);
                        txtEstadoPerfil.setTextColor(Color.parseColor("#DC2626"));
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    txtEstadoPerfil.setText("Sin conexión");
                    txtEstadoPerfil.setTextColor(Color.parseColor("#DC2626"));
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────────

    private void cerrarSesion() {
        getSharedPreferences("sesion", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── UTIL ──────────────────────────────────────────────────────────────────

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String leerRespuesta(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
