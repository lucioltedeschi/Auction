package com.example.clase4;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductRequestActivity extends AppCompatActivity {

    private static final int REQ_FOTO_BASE = 3100;
    private static final int TOTAL_FOTOS_REQUERIDAS = 6;

    private EditText edtDescripcionCatalogo;
    private EditText edtDescripcionCompleta;
    private EditText edtHistoria;
    private EditText edtArtista;
    private EditText edtPrecioBaseSugerido;
    private TextView[] txtFotos;
    private String[] fotosBase64;
    private CheckBox chkPropiedad;
    private CheckBox chkOrigenLicito;
    private TextView txtMensajeSolicitud;
    private TextView txtMisSolicitudes;
    private Button btnEnviarSolicitud;
    private Button btnVolverSolicitud;
    private Button btnActualizarMisSolicitudes;

    // Auction picker
    private LinearLayout selectorSubastaContainer;
    private TextView txtSubastaSeleccionada;
    private int subastaSeleccionadaId = 0;
    private final List<int[]> subastas = new ArrayList<>(); // {id}
    private final List<String> subastaLabels = new ArrayList<>();

    private int userId;
    private String token;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_request);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#071827"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#071827"));

        edtDescripcionCatalogo = findViewById(R.id.edtDescripcionCatalogo);
        edtDescripcionCompleta = findViewById(R.id.edtDescripcionCompleta);
        edtHistoria            = findViewById(R.id.edtHistoria);
        edtArtista             = findViewById(R.id.edtArtista);
        edtPrecioBaseSugerido  = findViewById(R.id.edtPrecioBaseSugerido);
        chkPropiedad           = findViewById(R.id.chkPropiedad);
        chkOrigenLicito        = findViewById(R.id.chkOrigenLicito);
        txtMensajeSolicitud    = findViewById(R.id.txtMensajeSolicitud);
        txtMisSolicitudes      = findViewById(R.id.txtMisSolicitudes);
        btnEnviarSolicitud     = findViewById(R.id.btnEnviarSolicitud);
        btnVolverSolicitud     = findViewById(R.id.btnVolverSolicitud);
        btnActualizarMisSolicitudes = findViewById(R.id.btnActualizarMisSolicitudes);

        // Auction selector views (may not exist in older XML — guarded with null checks)
        selectorSubastaContainer = findViewById(R.id.selectorSubastaContainer);
        txtSubastaSeleccionada   = findViewById(R.id.txtSubastaSeleccionada);

        fotosBase64 = new String[TOTAL_FOTOS_REQUERIDAS];
        txtFotos = new TextView[]{
                findViewById(R.id.txtFoto1), findViewById(R.id.txtFoto2),
                findViewById(R.id.txtFoto3), findViewById(R.id.txtFoto4),
                findViewById(R.id.txtFoto5), findViewById(R.id.txtFoto6)
        };

        Button[] botonesFotos = new Button[]{
                findViewById(R.id.btnFoto1), findViewById(R.id.btnFoto2),
                findViewById(R.id.btnFoto3), findViewById(R.id.btnFoto4),
                findViewById(R.id.btnFoto5), findViewById(R.id.btnFoto6)
        };

        for (int i = 0; i < botonesFotos.length; i++) {
            final int indice = i;
            if (botonesFotos[i] != null)
                botonesFotos[i].setOnClickListener(v -> seleccionarFoto(indice));
        }

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = preferences.getInt("userId", 0);
        token  = preferences.getString("token", "");

        btnEnviarSolicitud.setOnClickListener(v -> validarYEnviarSolicitud());
        if (btnVolverSolicitud != null) btnVolverSolicitud.setOnClickListener(v -> finish());
        if (btnActualizarMisSolicitudes != null)
            btnActualizarMisSolicitudes.setOnClickListener(v -> cargarMisSolicitudes());

        // Wire auction selector
        if (selectorSubastaContainer != null)
            selectorSubastaContainer.setOnClickListener(v -> mostrarPickerSubasta());

        cargarSubastasDisponibles();
        cargarMisSolicitudes();
    }

    // ── LOAD AVAILABLE AUCTIONS ───────────────────────────────────────────────

    private void cargarSubastasDisponibles() {
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/auctions");
                c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + token);
                if (c.getResponseCode() == 200) {
                    JSONArray list = new JSONArray(leerRespuesta(c.getInputStream()));
                    subastas.clear();
                    subastaLabels.clear();
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject a = list.getJSONObject(i);
                        String estado = a.optString("estado", "");
                        if (!"abierta".equals(estado) && !"programada".equals(estado)) continue;
                        int id = a.optInt("id", 0);
                        String ubicacion = a.optString("ubicacion", "Subasta");
                        String fecha = a.optString("fecha", "");
                        subastas.add(new int[]{id});
                        subastaLabels.add("#" + id + " – " + ubicacion + (fecha.length() >= 10 ? "  " + fecha.substring(0, 10) : ""));
                    }
                    mainHandler.post(this::actualizarLabelSubasta);
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private void actualizarLabelSubasta() {
        if (txtSubastaSeleccionada == null) return;
        if (subastaSeleccionadaId == 0) {
            txtSubastaSeleccionada.setText(subastas.isEmpty()
                    ? "No hay subastas disponibles"
                    : "Tocá para elegir subasta (opcional)");
        } else {
            for (int i = 0; i < subastas.size(); i++) {
                if (subastas.get(i)[0] == subastaSeleccionadaId) {
                    txtSubastaSeleccionada.setText(subastaLabels.get(i));
                    return;
                }
            }
        }
    }

    private void mostrarPickerSubasta() {
        if (subastas.isEmpty()) return;
        String[] opciones = subastaLabels.toArray(new String[0]);
        // Agregar opción "Ninguna" al inicio
        String[] opcionesConNinguna = new String[opciones.length + 1];
        opcionesConNinguna[0] = "Sin preferencia";
        System.arraycopy(opciones, 0, opcionesConNinguna, 1, opciones.length);

        new android.app.AlertDialog.Builder(this)
                .setTitle("Elegir subasta destino")
                .setItems(opcionesConNinguna, (d, which) -> {
                    if (which == 0) {
                        subastaSeleccionadaId = 0;
                    } else {
                        subastaSeleccionadaId = subastas.get(which - 1)[0];
                    }
                    actualizarLabelSubasta();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ── LOAD MY REQUESTS ──────────────────────────────────────────────────────

    private void cargarMisSolicitudes() {
        txtMisSolicitudes.setText("Cargando mis consignaciones...");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/products");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String respuesta = leerRespuesta(inputStream);

                if (statusCode == 200) {
                    JSONArray solicitudes = new JSONArray(respuesta);
                    mainHandler.post(() -> mostrarMisSolicitudes(solicitudes));
                } else {
                    JSONObject err = new JSONObject(respuesta);
                    mainHandler.post(() -> txtMisSolicitudes.setText(err.optString("error", "Error")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> txtMisSolicitudes.setText("Sin conexión"));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarMisSolicitudes(JSONArray solicitudes) {
        if (solicitudes.length() == 0) {
            txtMisSolicitudes.setText("Todavía no enviaste consignaciones.");
            return;
        }
        try {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < solicitudes.length(); i++) {
                JSONObject item = solicitudes.getJSONObject(i);
                builder.append("#").append(item.optInt("id", 0))
                        .append(" – ").append(item.optString("descripcionCatalogo", "-"))
                        .append("\nEstado: ").append(item.optString("estadoAprobacion", "-"));

                int subastaId = item.optInt("subastaPreferida", 0);
                if (subastaId > 0)
                    builder.append("  ·  Subasta destino: #").append(subastaId);

                String motivo = item.optString("motivoRechazo", "");
                if (!motivo.isEmpty() && !"null".equals(motivo))
                    builder.append("\nMotivo rechazo: ").append(motivo);

                builder.append("\n\n");
            }
            txtMisSolicitudes.setText(builder.toString());
        } catch (Exception e) {
            txtMisSolicitudes.setText("Error mostrando consignaciones.");
        }
    }

    // ── PHOTO PICKER ──────────────────────────────────────────────────────────

    private void seleccionarFoto(int indice) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_FOTO_BASE + indice);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        int indice = requestCode - REQ_FOTO_BASE;
        if (indice < 0 || indice >= TOTAL_FOTOS_REQUERIDAS) return;
        try {
            fotosBase64[indice] = leerImagenBase64(data.getData());
            txtFotos[indice].setText("✓ Foto " + (indice + 1) + " cargada");
        } catch (Exception e) {
            txtMensajeSolicitud.setText("No se pudo leer la foto.");
        }
    }

    private String leerImagenBase64(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while (is != null && (n = is.read(buf)) != -1) out.write(buf, 0, n);
        if (is != null) is.close();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    // ── VALIDATE & SEND ───────────────────────────────────────────────────────

    private void validarYEnviarSolicitud() {
        String descripcionCatalogo = edtDescripcionCatalogo.getText().toString().trim();
        String descripcionCompleta = edtDescripcionCompleta.getText().toString().trim();
        String historia            = edtHistoria.getText().toString().trim();
        String artista             = edtArtista.getText().toString().trim();
        String precioBaseSugerido  = edtPrecioBaseSugerido.getText().toString().trim();
        JSONArray fotos = new JSONArray();

        if (descripcionCatalogo.isEmpty()) { txtMensajeSolicitud.setText("Ingresá un título corto para el catálogo."); return; }
        if (descripcionCompleta.isEmpty())  { txtMensajeSolicitud.setText("Ingresá una descripción completa."); return; }

        for (int i = 0; i < TOTAL_FOTOS_REQUERIDAS; i++) {
            if (fotosBase64[i] == null) { txtMensajeSolicitud.setText("Seleccioná las 6 fotos mínimas del bien."); return; }
            fotos.put(fotosBase64[i]);
        }

        if (!precioBaseSugerido.isEmpty()) {
            try { Double.parseDouble(precioBaseSugerido); }
            catch (Exception e) { txtMensajeSolicitud.setText("El precio base sugerido debe ser numérico."); return; }
        }

        if (!chkPropiedad.isChecked())   { txtMensajeSolicitud.setText("Debés declarar que el bien te pertenece."); return; }
        if (!chkOrigenLicito.isChecked()){ txtMensajeSolicitud.setText("Debés declarar el origen lícito del bien."); return; }

        txtMensajeSolicitud.setText("");
        btnEnviarSolicitud.setEnabled(false);
        btnEnviarSolicitud.setText("Enviando...");

        enviarSolicitud(descripcionCatalogo, descripcionCompleta, historia, artista, precioBaseSugerido, fotos);
    }

    private void enviarSolicitud(String descCat, String descComp, String historia,
                                  String artista, String precioBase, JSONArray fotos) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/products");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("duenio", userId);
                body.put("descripcionCatalogo", descCat);
                body.put("descripcionCompleta", descComp);
                body.put("historia", historia);
                body.put("artistaDiseniador", artista);
                if (!precioBase.isEmpty()) body.put("precioBaseSugerido", Double.parseDouble(precioBase));
                body.put("fotos", fotos);
                body.put("declaracionPropiedad", "si");
                body.put("origenLicito", "si");
                if (subastaSeleccionadaId > 0) body.put("subastaId", subastaSeleccionadaId);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = connection.getResponseCode();
                InputStream is = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                JSONObject json = new JSONObject(leerRespuesta(is));

                if (statusCode == 202 || statusCode == 201 || statusCode == 200) {
                    String mensaje = json.optString("mensaje", "Solicitud enviada correctamente");
                    mainHandler.post(() -> {
                        btnEnviarSolicitud.setEnabled(true);
                        btnEnviarSolicitud.setText("Enviar solicitud");
                        txtMensajeSolicitud.setTextColor(android.graphics.Color.parseColor("#16A34A"));
                        txtMensajeSolicitud.setText(mensaje);
                        limpiarFormulario();
                        cargarMisSolicitudes();
                    });
                } else {
                    String error = json.optString("error", "No se pudo enviar");
                    mainHandler.post(() -> {
                        btnEnviarSolicitud.setEnabled(true);
                        btnEnviarSolicitud.setText("Enviar solicitud");
                        txtMensajeSolicitud.setTextColor(android.graphics.Color.parseColor("#DC2626"));
                        txtMensajeSolicitud.setText(error);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnEnviarSolicitud.setEnabled(true);
                    btnEnviarSolicitud.setText("Enviar solicitud");
                    txtMensajeSolicitud.setTextColor(android.graphics.Color.parseColor("#DC2626"));
                    txtMensajeSolicitud.setText("Sin conexión con el servidor.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void limpiarFormulario() {
        edtDescripcionCatalogo.setText("");
        edtDescripcionCompleta.setText("");
        edtHistoria.setText("");
        edtArtista.setText("");
        edtPrecioBaseSugerido.setText("");
        fotosBase64 = new String[TOTAL_FOTOS_REQUERIDAS];
        for (int i = 0; i < txtFotos.length; i++) txtFotos[i].setText("Foto " + (i + 1) + " pendiente");
        chkPropiedad.setChecked(false);
        chkOrigenLicito.setChecked(false);
        subastaSeleccionadaId = 0;
        actualizarLabelSubasta();
    }

    private String leerRespuesta(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
