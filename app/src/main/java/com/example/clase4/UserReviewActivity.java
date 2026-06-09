package com.example.clase4;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
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

public class UserReviewActivity extends AppCompatActivity {

    private static final String[] CATEGORIAS = {"comun", "especial", "plata", "oro", "platino"};

    private TextView txtReviewNombre;
    private TextView txtReviewDocumento;
    private TextView txtReviewEstado;
    private TextView txtReviewCategoriaActual;
    private TextView txtReviewInfo;
    private ImageView imgReviewDniFrente;
    private ImageView imgReviewDniDorso;
    private Spinner spReviewCategoria;
    private Button btnReviewAprobar;
    private Button btnReviewRechazar;

    private int userId;
    private String documento = "-";
    private String token;
    private boolean enviando = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_review);

        getWindow().setStatusBarColor(Color.parseColor("#071827"));
        getWindow().setNavigationBarColor(Color.parseColor("#F3F0E8"));

        txtReviewNombre = findViewById(R.id.txtReviewNombre);
        txtReviewDocumento = findViewById(R.id.txtReviewDocumento);
        txtReviewEstado = findViewById(R.id.txtReviewEstado);
        txtReviewCategoriaActual = findViewById(R.id.txtReviewCategoriaActual);
        txtReviewInfo = findViewById(R.id.txtReviewInfo);
        imgReviewDniFrente = findViewById(R.id.imgReviewDniFrente);
        imgReviewDniDorso = findViewById(R.id.imgReviewDniDorso);
        spReviewCategoria = findViewById(R.id.spReviewCategoria);
        btnReviewAprobar = findViewById(R.id.btnReviewAprobar);
        btnReviewRechazar = findViewById(R.id.btnReviewRechazar);

        spReviewCategoria.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, CATEGORIAS));

        SharedPreferences prefs = getSharedPreferences("sesion", MODE_PRIVATE);
        token = prefs.getString("token", "");

        userId = getIntent().getIntExtra("userId", 0);
        documento = textoSeguro(getIntent().getStringExtra("documento"));

        // Pintamos de inmediato lo que ya recibimos para que la pantalla no quede vacía
        String nombre = textoSeguro(getIntent().getStringExtra("nombre"));
        String apellido = textoSeguro(getIntent().getStringExtra("apellido"));
        txtReviewNombre.setText((nombre + " " + apellido).trim().isEmpty() ? "Usuario #" + userId : (nombre + " " + apellido).trim());
        txtReviewDocumento.setText("DNI " + documento);
        seleccionarCategoria(getIntent().getStringExtra("categoria"));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnReviewAprobar.setOnClickListener(v -> confirmarAprobacion());
        btnReviewRechazar.setOnClickListener(v -> confirmarRechazo());

        if (userId <= 0) {
            txtReviewInfo.setText("No se recibió el usuario a revisar.");
            return;
        }
        cargarDetalleUsuario();
    }

    // ── CARGA DE DATOS ───────────────────────────────────────────────────────────

    private void cargarDetalleUsuario() {
        if (token == null || token.trim().isEmpty()) {
            txtReviewInfo.setText("Falta token de admin. Ingresá nuevamente con 20000111 / 1234.");
            return;
        }
        txtReviewInfo.setText("Cargando información del usuario…");

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/admin/users/" + userId + "/document");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String respuesta = leerRespuesta(inputStream);

                if (statusCode >= 200 && statusCode < 300) {
                    JSONObject usuario = new JSONObject(respuesta);
                    mainHandler.post(() -> mostrarDetalleUsuario(usuario));
                } else {
                    String detalle;
                    try { detalle = new JSONObject(respuesta).optString("error", respuesta); }
                    catch (Exception e) { detalle = respuesta; }
                    final String msg = detalle;
                    mainHandler.post(() -> txtReviewInfo.setText("No se pudo cargar el usuario: " + msg));
                }
            } catch (Exception e) {
                mainHandler.post(() -> txtReviewInfo.setText("No se pudo conectar con el servidor."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarDetalleUsuario(JSONObject u) {
        String nombre = u.optString("nombre", "-");
        String apellido = u.optString("apellido", "-");
        documento = u.optString("documento", documento);
        String estado = u.optString("estado", "-");
        String admitido = u.optString("admitido", "-");
        String categoria = u.optString("categoria", "-");

        txtReviewNombre.setText((nombre + " " + apellido).trim());
        txtReviewDocumento.setText("DNI " + documento);
        txtReviewCategoriaActual.setText(categoria.toUpperCase());
        seleccionarCategoria(categoria);
        pintarEstadoChip(estado, admitido);

        txtReviewInfo.setText(
                "Nombre\n" + nombre + " " + apellido + "\n\n" +
                "Documento\n" + documento + "\n\n" +
                "Email\n" + textoCampo(u.optString("email", "")) + "\n\n" +
                "Teléfono\n" + textoCampo(u.optString("telefono", "")) + "\n\n" +
                "Dirección\n" + textoCampo(u.optString("direccion", "")) + "\n\n" +
                "Alta\n" + formatearFecha(u.optString("fechaAlta", "")) + "\n\n" +
                "Estado\n" + estado + "\n\n" +
                "Admitido\n" + admitido + "\n\n" +
                "Categoría actual\n" + categoria
        );

        cargarImagen(u.optString("fotoDniFrenteBase64", ""), imgReviewDniFrente, "Frente del DNI");
        cargarImagen(u.optString("fotoDniDorsoBase64", ""), imgReviewDniDorso, "Dorso del DNI");
    }

    private void pintarEstadoChip(String estado, String admitido) {
        boolean habilitado = "activo".equalsIgnoreCase(estado) && "si".equalsIgnoreCase(admitido);
        if (habilitado) {
            txtReviewEstado.setText("HABILITADO");
            txtReviewEstado.setBackgroundResource(R.drawable.bg_success_chip);
            txtReviewEstado.setTextColor(Color.parseColor("#166534"));
        } else if ("rechazado".equalsIgnoreCase(estado)) {
            txtReviewEstado.setText("RECHAZADO");
            txtReviewEstado.setBackgroundResource(R.drawable.bg_danger_chip);
            txtReviewEstado.setTextColor(Color.parseColor("#991B1B"));
        } else {
            txtReviewEstado.setText("PENDIENTE");
            txtReviewEstado.setBackgroundResource(R.drawable.bg_danger_chip);
            txtReviewEstado.setTextColor(Color.parseColor("#991B1B"));
        }
    }

    // ── IMÁGENES ─────────────────────────────────────────────────────────────────

    private void cargarImagen(String base64, ImageView imageView, String titulo) {
        if (base64 == null || base64.trim().isEmpty() || "null".equals(base64)) {
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageResource(R.drawable.ic_photo_placeholder);
            imageView.setColorFilter(Color.parseColor("#CBD5E1"));
            imageView.setOnClickListener(null);
            return;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setImageResource(R.drawable.ic_photo_placeholder);
                imageView.setColorFilter(Color.parseColor("#CBD5E1"));
                return;
            }
            imageView.setColorFilter(null);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setImageBitmap(bitmap);
            imageView.setOnClickListener(v -> mostrarImagenAmpliada(bitmap, titulo));
        } catch (Exception e) {
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setImageResource(R.drawable.ic_photo_placeholder);
            imageView.setColorFilter(Color.parseColor("#CBD5E1"));
        }
    }

    private void mostrarImagenAmpliada(Bitmap bitmap, String titulo) {
        ImageView zoom = new ImageView(this);
        zoom.setImageBitmap(bitmap);
        zoom.setAdjustViewBounds(true);
        zoom.setScaleType(ImageView.ScaleType.FIT_CENTER);
        zoom.setBackgroundColor(Color.parseColor("#0B0B0B"));
        int pad = dp(8);
        zoom.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setView(zoom)
                .setPositiveButton("Cerrar", null)
                .show();
    }

    // ── ACCIONES ─────────────────────────────────────────────────────────────────

    private void confirmarAprobacion() {
        String categoria = categoriaSeleccionada();
        new AlertDialog.Builder(this)
                .setTitle("Aprobar usuario")
                .setMessage("Vas a habilitar a este usuario con categoría \"" + categoria + "\". ¿Confirmás?")
                .setPositiveButton("APROBAR", (d, w) -> enviarVerificacion("si"))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmarRechazo() {
        new AlertDialog.Builder(this)
                .setTitle("Rechazar usuario")
                .setMessage("El usuario quedará rechazado y no podrá operar. ¿Confirmás?")
                .setPositiveButton("RECHAZAR", (d, w) -> enviarVerificacion("no"))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void enviarVerificacion(String admitido) {
        if (enviando) return;
        if (token == null || token.trim().isEmpty()) {
            FeedbackDialog.error(this, "Falta token de admin. Ingresá nuevamente con 20000111 / 1234.");
            return;
        }
        enviando = true;
        setBotonesHabilitados(false);

        JSONObject body = new JSONObject();
        try {
            body.put("admitido", admitido);
            body.put("categoria", categoriaSeleccionada());
        } catch (Exception ignored) {
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/admin/users/" + userId + "/verification");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String respuesta = leerRespuesta(inputStream);
                JSONObject json = new JSONObject(respuesta);
                boolean ok = statusCode >= 200 && statusCode < 300;
                String mensaje = json.optString(
                        ok ? "mensaje" : "error",
                        ok ? "Operación realizada." : "No se pudo completar la operación.");

                mainHandler.post(() -> {
                    enviando = false;
                    if (ok) {
                        FeedbackDialog.ok(UserReviewActivity.this, mensaje);
                        setResult(RESULT_OK);
                        mainHandler.postDelayed(this::finish, 700);
                    } else {
                        setBotonesHabilitados(true);
                        FeedbackDialog.error(UserReviewActivity.this, mensaje);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    enviando = false;
                    setBotonesHabilitados(true);
                    FeedbackDialog.error(UserReviewActivity.this, "No se pudo conectar con el servidor.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void setBotonesHabilitados(boolean habilitado) {
        btnReviewAprobar.setEnabled(habilitado);
        btnReviewRechazar.setEnabled(habilitado);
        btnReviewAprobar.setAlpha(habilitado ? 1f : 0.5f);
        btnReviewRechazar.setAlpha(habilitado ? 1f : 0.5f);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────────

    private void seleccionarCategoria(String categoria) {
        if (categoria == null) return;
        String c = categoria.toLowerCase().trim();
        for (int i = 0; i < CATEGORIAS.length; i++) {
            if (CATEGORIAS[i].equals(c)) {
                spReviewCategoria.setSelection(i);
                return;
            }
        }
    }

    private String categoriaSeleccionada() {
        Object sel = spReviewCategoria.getSelectedItem();
        return sel != null ? sel.toString() : "comun";
    }

    private String textoSeguro(String s) {
        return (s == null || "null".equals(s)) ? "" : s;
    }

    private String textoCampo(String s) {
        return (s == null || s.trim().isEmpty() || "null".equals(s)) ? "—" : s;
    }

    private String formatearFecha(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equals(raw)) return "—";
        try {
            String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            java.text.SimpleDateFormat inFmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
            return outFmt.format(inFmt.parse(datePart));
        } catch (Exception e) {
            return raw;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String leerRespuesta(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder respuesta = new StringBuilder();
        String linea;
        while ((linea = reader.readLine()) != null) respuesta.append(linea);
        return respuesta.toString();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
