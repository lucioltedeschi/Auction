package com.example.clase4;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductRequestActivity extends AppCompatActivity {

    private static final int REQ_FOTO_BASE = 3100;
    private static final int REQ_FOTOS_MULTIPLES = 3099;
    private static final int TOTAL_FOTOS_REQUERIDAS = 6;

    private EditText edtDescripcionCatalogo;
    private EditText edtDescripcionCompleta;
    private EditText edtHistoria;
    private EditText edtArtista;
    private EditText edtPrecioBaseSugerido;
    private TextView[] txtFotos;
    private TextView txtProgresoFotos;
    private String[] fotosBase64;
    private CheckBox chkPropiedad;
    private CheckBox chkOrigenLicito;
    private TextView txtMensajeSolicitud;
    private TextView txtMisSolicitudes;
    private LinearLayout contenedorMisSolicitudes;
    private Button btnEnviarSolicitud;
    private Button btnVolverSolicitud;
    private Button btnActualizarMisSolicitudes;

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
        SystemBars.configure(this, "#071827", false, "#071827", false);

        edtDescripcionCatalogo = findViewById(R.id.edtDescripcionCatalogo);
        edtDescripcionCompleta = findViewById(R.id.edtDescripcionCompleta);
        edtHistoria = findViewById(R.id.edtHistoria);
        edtArtista = findViewById(R.id.edtArtista);
        edtPrecioBaseSugerido = findViewById(R.id.edtPrecioBaseSugerido);
        chkPropiedad = findViewById(R.id.chkPropiedad);
        chkOrigenLicito = findViewById(R.id.chkOrigenLicito);
        txtMensajeSolicitud = findViewById(R.id.txtMensajeSolicitud);
        txtProgresoFotos = findViewById(R.id.txtProgresoFotos);
        txtMisSolicitudes = findViewById(R.id.txtMisSolicitudes);
        contenedorMisSolicitudes = findViewById(R.id.contenedorMisSolicitudes);
        btnEnviarSolicitud = findViewById(R.id.btnEnviarSolicitud);
        btnVolverSolicitud = findViewById(R.id.btnVolverSolicitud);
        btnActualizarMisSolicitudes = findViewById(R.id.btnActualizarMisSolicitudes);

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

        botonesFotos[0].setOnClickListener(v -> seleccionarFotosMultiples());
        for (int i = 1; i < botonesFotos.length; i++) {
            final int indice = i;
            if (botonesFotos[i] != null) {
                botonesFotos[i].setOnClickListener(v -> seleccionarFoto(indice));
            }
        }

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = preferences.getInt("userId", 0);
        token = preferences.getString("token", "");

        btnEnviarSolicitud.setOnClickListener(v -> validarYEnviarSolicitud());
        if (btnVolverSolicitud != null) btnVolverSolicitud.setOnClickListener(v -> finish());
        if (btnActualizarMisSolicitudes != null) {
            btnActualizarMisSolicitudes.setOnClickListener(v -> cargarMisSolicitudes());
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMisSolicitudes();
    }

    private void cargarMisSolicitudes() {
        txtMisSolicitudes.setText("Cargando mis consignaciones...");
        if (contenedorMisSolicitudes != null) contenedorMisSolicitudes.removeAllViews();

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
                mainHandler.post(() -> txtMisSolicitudes.setText("Sin conexión con el servidor."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarMisSolicitudes(JSONArray solicitudes) {
        if (contenedorMisSolicitudes != null) contenedorMisSolicitudes.removeAllViews();

        if (solicitudes.length() == 0) {
            txtMisSolicitudes.setText("Todavia no enviaste consignaciones.");
            return;
        }

        txtMisSolicitudes.setText("");
        try {
            for (int i = 0; i < solicitudes.length(); i++) {
                JSONObject item = solicitudes.getJSONObject(i);
                contenedorMisSolicitudes.addView(crearCardSolicitud(item));
            }
        } catch (Exception e) {
            txtMisSolicitudes.setText("Error mostrando consignaciones.");
        }
    }

    private View crearCardSolicitud(JSONObject item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.bg_metric_box);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardParams);

        int id = item.optInt("id", 0);
        String estado = item.optString("estadoAprobacion", "");
        String estadoTexto = item.optString("estadoDescripcion", estadoLegible(estado));

        card.addView(crearTexto("#" + id + " - " + item.optString("descripcionCatalogo", "-"), "#071827", 16, true));
        card.addView(crearTexto("Estado: " + estadoTexto, "#475569", 13, false));
        card.addView(crearTexto(descripcionEstado(estado), "#64748B", 12, false));

        double sugerido = item.optDouble("precioBaseSugerido", 0);
        if (sugerido > 0) {
            card.addView(crearTexto("Precio sugerido por vos: $" + String.format("%.2f", sugerido), "#0F766E", 13, true));
        }

        double precio = item.optDouble("precioBasePropuesto", 0);
        double comision = item.optDouble("comisionPropuesta", 0);
        String condiciones = item.optString("condicionesPropuestas", "");
        if (precio > 0 || comision > 0 || (!condiciones.isEmpty() && !"null".equals(condiciones))) {
            String detalle = "Condiciones de la empresa";
            if (precio > 0) detalle += "\nPrecio base: $" + String.format("%.2f", precio);
            if (comision > 0) detalle += "\nComision: $" + String.format("%.2f", comision);
            if (!condiciones.isEmpty() && !"null".equals(condiciones)) detalle += "\n" + condiciones;
            card.addView(crearTexto(detalle, "#334155", 13, false));
        }

        String motivo = item.optString("motivoRechazo", "");
        if (!motivo.isEmpty() && !"null".equals(motivo)) {
            card.addView(crearTexto("Motivo: " + motivo, "#991B1B", 13, false));
        }

        String deposito = item.optString("ubicacionDeposito", "");
        String seguro = item.optString("seguro", "");
        if ((!deposito.isEmpty() && !"null".equals(deposito)) || (!seguro.isEmpty() && !"null".equals(seguro))) {
            String custodia = "Custodia del bien";
            if (!deposito.isEmpty() && !"null".equals(deposito)) custodia += "\nDeposito: " + deposito;
            if (!seguro.isEmpty() && !"null".equals(seguro)) custodia += "\nSeguro: " + seguro;
            card.addView(crearTexto(custodia, "#475569", 13, false));
        }

        if ("propuesta_enviada".equals(estado)) {
            LinearLayout acciones = new LinearLayout(this);
            acciones.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams accionesParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            accionesParams.setMargins(0, dp(10), 0, 0);
            acciones.setLayoutParams(accionesParams);

            Button aceptar = crearBotonRespuesta("ACEPTAR", true);
            Button rechazar = crearBotonRespuesta("RECHAZAR", false);
            aceptar.setOnClickListener(v -> confirmarRespuestaPropuesta(id, "aceptar"));
            rechazar.setOnClickListener(v -> confirmarRespuestaPropuesta(id, "rechazar"));

            acciones.addView(aceptar);
            acciones.addView(rechazar);
            card.addView(acciones);
        }

        return card;
    }

    private void seleccionarFoto(int indice) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_FOTO_BASE + indice);
    }

    private void seleccionarFotosMultiples() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQ_FOTOS_MULTIPLES);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_FOTOS_MULTIPLES) {
            try {
                int cargadas = 0;
                ClipData seleccion = data.getClipData();
                if (seleccion != null) {
                    int total = Math.min(seleccion.getItemCount(), TOTAL_FOTOS_REQUERIDAS);
                    for (int i = 0; i < total; i++) {
                        fotosBase64[i] = leerImagenBase64(seleccion.getItemAt(i).getUri());
                        txtFotos[i].setText("Foto " + (i + 1) + " cargada");
                        cargadas++;
                    }
                } else if (data.getData() != null) {
                    fotosBase64[0] = leerImagenBase64(data.getData());
                    txtFotos[0].setText("Foto 1 cargada");
                    cargadas = 1;
                }
                actualizarProgresoFotos();
                if (cargadas < TOTAL_FOTOS_REQUERIDAS) {
                    FeedbackDialog.info(this, "Fotos pendientes", "Se cargaron " + cargadas + " fotos. Completá las restantes antes de enviar la consignación.");
                }
            } catch (Exception e) {
                mostrarErrorSolicitud("No se pudieron procesar las fotos seleccionadas.");
            }
            return;
        }

        if (data.getData() == null) return;
        int indice = requestCode - REQ_FOTO_BASE;
        if (indice < 0 || indice >= TOTAL_FOTOS_REQUERIDAS) return;
        try {
            fotosBase64[indice] = leerImagenBase64(data.getData());
            txtFotos[indice].setText("Foto " + (indice + 1) + " cargada");
            actualizarProgresoFotos();
        } catch (Exception e) {
            txtMensajeSolicitud.setText("No se pudo leer la foto.");
        }
    }

    private String leerImagenBase64(Uri uri) throws Exception {
        ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
        Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
            int width = info.getSize().getWidth();
            int height = info.getSize().getHeight();
            int max = Math.max(width, height);
            if (max > 1280) {
                float ratio = 1280f / max;
                decoder.setTargetSize(Math.round(width * ratio), Math.round(height * ratio));
            }
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
        bitmap.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private void actualizarProgresoFotos() {
        int cargadas = 0;
        for (String foto : fotosBase64) if (foto != null) cargadas++;
        txtProgresoFotos.setText(cargadas + " de 6 fotos cargadas" + (cargadas == 6 ? ". Listas para enviar." : "."));
        txtProgresoFotos.setTextColor(android.graphics.Color.parseColor(cargadas == 6 ? "#166534" : "#64748B"));
    }

    private void validarYEnviarSolicitud() {
        String descripcionCatalogo = edtDescripcionCatalogo.getText().toString().trim();
        String descripcionCompleta = edtDescripcionCompleta.getText().toString().trim();
        String historia = edtHistoria.getText().toString().trim();
        String artista = edtArtista.getText().toString().trim();
        String precioBaseSugerido = edtPrecioBaseSugerido.getText().toString().trim();
        JSONArray fotos = new JSONArray();

        if (descripcionCatalogo.isEmpty()) { mostrarErrorSolicitud("Ingresa un titulo corto para el catalogo."); return; }
        if (descripcionCompleta.isEmpty()) { mostrarErrorSolicitud("Ingresa una descripcion completa."); return; }

        for (int i = 0; i < TOTAL_FOTOS_REQUERIDAS; i++) {
            if (fotosBase64[i] == null) { mostrarErrorSolicitud("Selecciona las 6 fotos minimas del bien."); return; }
            fotos.put(fotosBase64[i]);
        }

        if (!precioBaseSugerido.isEmpty()) {
            try { Double.parseDouble(precioBaseSugerido); }
            catch (Exception e) { mostrarErrorSolicitud("El precio base sugerido debe ser numerico."); return; }
        }

        if (!chkPropiedad.isChecked()) { mostrarErrorSolicitud("Debes declarar que el bien te pertenece."); return; }
        if (!chkOrigenLicito.isChecked()) { mostrarErrorSolicitud("Debes declarar el origen licito del bien."); return; }

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
                        FeedbackDialog.ok(ProductRequestActivity.this, mensaje + "\n\nLa empresa revisara el bien y te enviara condiciones antes de incluirlo en una subasta.");
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
                        FeedbackDialog.error(ProductRequestActivity.this, error);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnEnviarSolicitud.setEnabled(true);
                    btnEnviarSolicitud.setText("Enviar solicitud");
                    txtMensajeSolicitud.setTextColor(android.graphics.Color.parseColor("#DC2626"));
                    txtMensajeSolicitud.setText("Sin conexión con el servidor.");
                    FeedbackDialog.error(ProductRequestActivity.this, "Sin conexión con el servidor.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void confirmarRespuestaPropuesta(int productId, String decision) {
        boolean acepta = "aceptar".equals(decision);
        FeedbackDialog.confirmar(
                this,
                acepta ? "Aceptar condiciones" : "Rechazar condiciones",
                acepta
                        ? "Al aceptar, autorizás a la empresa a incluir el bien en una subasta futura con el precio base y comisión informados."
                        : "Al rechazar, el bien no avanzará a catálogo y la empresa informará el proceso de devolución y sus gastos.",
                () -> responderPropuesta(productId, decision)
        );
    }

    private void responderPropuesta(int productId, String decision) {
        txtMensajeSolicitud.setText("");
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/products/" + productId + "/proposal-response");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("duenio", userId);
                body.put("decision", decision);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = connection.getResponseCode();
                InputStream is = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                JSONObject json = new JSONObject(leerRespuesta(is));

                mainHandler.post(() -> {
                    boolean ok = statusCode >= 200 && statusCode < 300;
                    String mensaje = json.optString(ok ? "mensaje" : "error", "Operacion procesada");
                    txtMensajeSolicitud.setTextColor(android.graphics.Color.parseColor(ok ? "#16A34A" : "#DC2626"));
                    txtMensajeSolicitud.setText(mensaje);
                    if (ok) FeedbackDialog.ok(ProductRequestActivity.this, mensaje);
                    else FeedbackDialog.error(ProductRequestActivity.this, mensaje);
                    cargarMisSolicitudes();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    txtMensajeSolicitud.setText("Sin conexión con el servidor.");
                    FeedbackDialog.error(ProductRequestActivity.this, "Sin conexión con el servidor.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarErrorSolicitud(String mensaje) {
        txtMensajeSolicitud.setTextColor(android.graphics.Color.parseColor("#DC2626"));
        txtMensajeSolicitud.setText(mensaje);
        FeedbackDialog.error(this, mensaje);
    }

    private void limpiarFormulario() {
        edtDescripcionCatalogo.setText("");
        edtDescripcionCompleta.setText("");
        edtHistoria.setText("");
        edtArtista.setText("");
        edtPrecioBaseSugerido.setText("");
        fotosBase64 = new String[TOTAL_FOTOS_REQUERIDAS];
        for (int i = 0; i < txtFotos.length; i++) txtFotos[i].setText("Foto " + (i + 1) + " pendiente");
        actualizarProgresoFotos();
        chkPropiedad.setChecked(false);
        chkOrigenLicito.setChecked(false);
    }

    private TextView crearTexto(String text, String color, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(android.graphics.Color.parseColor(color));
        view.setTextSize(size);
        view.setLineSpacing(dp(3), 1.0f);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private Button crearBotonRespuesta(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(android.graphics.Color.parseColor(primary ? "#071827" : "#991B1B"));
        button.setBackgroundResource(primary ? R.drawable.bg_button_gold : R.drawable.bg_button_outline);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
        params.setMargins(primary ? 0 : dp(6), 0, primary ? dp(6) : 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private String estadoLegible(String estado) {
        if ("pendiente".equals(estado) || "pendiente_inspeccion".equals(estado)) return "Pendiente de inspeccion";
        if ("propuesta_enviada".equals(estado)) return "Propuesta enviada";
        if ("aceptado_usuario".equals(estado)) return "Aceptado por usuario";
        if ("rechazado_usuario".equals(estado)) return "Rechazado por usuario";
        if ("incluido_subasta".equals(estado)) return "Incluido en subasta";
        if ("rechazado".equals(estado)) return "Rechazado por empresa";
        if ("aceptado".equals(estado)) return "Aceptado por empresa";
        return estado;
    }

    private String descripcionEstado(String estado) {
        if ("pendiente".equals(estado) || "pendiente_inspeccion".equals(estado)) {
            return "La empresa esta revisando fotos, origen y condiciones del bien.";
        }
        if ("propuesta_enviada".equals(estado)) {
            return "Revisa precio base y comision. El bien solo avanza si aceptas.";
        }
        if ("aceptado_usuario".equals(estado)) {
            return "Aceptaste las condiciones. El admin ya puede asignarlo a una subasta.";
        }
        if ("rechazado_usuario".equals(estado)) {
            return "Rechazaste las condiciones. El bien no se incluira en catalogo.";
        }
        if ("incluido_subasta".equals(estado)) {
            return "El bien ya fue incluido en una subasta.";
        }
        if ("rechazado".equals(estado)) {
            return "La empresa rechazo el bien. Revisa el motivo informado.";
        }
        return "Seguimiento de consignacion.";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private String leerRespuesta(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
