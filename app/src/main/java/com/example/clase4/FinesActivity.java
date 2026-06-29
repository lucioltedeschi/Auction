package com.example.clase4;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
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

public class FinesActivity extends AppCompatActivity {

    private TextView txtMensajeMultas;
    private LinearLayout contenedorMultas;

    private int userId;
    private String token;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fines);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#071827"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#071827"));
        SystemBars.configure(this, "#071827", false, "#071827", false);

        txtMensajeMultas = findViewById(R.id.txtMensajeMultas);
        contenedorMultas = findViewById(R.id.contenedorMultas);

        findViewById(R.id.btnBackMultas).setOnClickListener(v -> finish());

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = preferences.getInt("userId", 0);
        token = preferences.getString("token", "");

    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMultas();
    }

    private void cargarMultas() {
        txtMensajeMultas.setText("Cargando multas...");
        contenedorMultas.removeAllViews();

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/fines");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String respuesta = leerRespuesta(inputStream);

                if (statusCode == 200) {
                    JSONArray multas = new JSONArray(respuesta);
                    mainHandler.post(() -> mostrarMultas(multas));
                } else {
                    JSONObject errorJson = new JSONObject(respuesta);
                    String error = errorJson.optString("error", "Error al cargar multas");
                    mainHandler.post(() -> txtMensajeMultas.setText(error));
                }
            } catch (Exception e) {
                mainHandler.post(() -> txtMensajeMultas.setText("No se pudo conectar con el servidor."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void mostrarMultas(JSONArray multas) {
        contenedorMultas.removeAllViews();

        if (multas.length() == 0) {
            txtMensajeMultas.setText("No tenés multas registradas.");
            return;
        }

        txtMensajeMultas.setText("Multas encontradas: " + multas.length());

        try {
            for (int i = 0; i < multas.length(); i++) {
                JSONObject multa = multas.getJSONObject(i);
                contenedorMultas.addView(crearCardMulta(multa));
            }
        } catch (Exception e) {
            txtMensajeMultas.setText("Error mostrando multas.");
        }
    }

    private View crearCardMulta(JSONObject multa) throws Exception {
        int id = multa.optInt("id", 0);
        int subastaId = multa.optInt("subastaId", 0);
        double monto = multa.optDouble("monto", 0);
        String pagada = multa.optString("pagada", "no");
        String moneda = multa.optString("moneda", "pesos");
        String producto = multa.optString("descripcionCatalogo", "Compra adjudicada");
        double importeOfertado = multa.optDouble("importeOfertado", monto * 10);
        String fecha = formatearFecha(multa.optString("fechaGeneracion", "-"));

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

        TextView titulo = new TextView(this);
        titulo.setText("Multa #" + id);
        titulo.setTextSize(17);
        titulo.setTextColor(Color.parseColor("#071827"));
        titulo.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView detalle = new TextView(this);
        detalle.setText(
                producto + "\n" +
                        "Subasta: #" + subastaId + "\n" +
                        "Oferta impaga: " + moneda + " " + String.format("%.2f", importeOfertado) + "\n" +
                        "Multa reglamentaria (10%): " + moneda + " " + String.format("%.2f", monto) + "\n" +
                        "Estado: " + (pagada.equals("si") ? "Pagada" : "Pendiente") + "\n" +
                        "Fecha: " + fecha
        );
        detalle.setTextSize(14);
        detalle.setTextColor(Color.parseColor("#475569"));
        detalle.setPadding(0, 10, 0, 10);
        detalle.setLineSpacing(4, 1.0f);

        Button btnPagar = new Button(this);
        btnPagar.setText(pagada.equals("si") ? "MULTA PAGADA" : "PAGAR MULTA");
        btnPagar.setEnabled(!pagada.equals("si"));
        if (pagada.equals("si")) {
            btnPagar.setBackgroundResource(R.drawable.bg_button_outline);
            btnPagar.setTextColor(Color.parseColor("#64748B"));
        } else {
            btnPagar.setBackgroundResource(R.drawable.bg_button_dark);
            btnPagar.setTextColor(Color.WHITE);
        }
        btnPagar.setTextSize(11);
        btnPagar.setTypeface(null, android.graphics.Typeface.BOLD);
        if (!pagada.equals("si")) {
            btnPagar.setOnClickListener(v -> FeedbackDialog.confirmar(
                    this,
                    "Regularizar multa",
                    "La multa equivale al 10% de la oferta impaga. Elegirás un medio verificado en "
                            + moneda + " para abonar " + String.format("%.2f", monto) + ".",
                    () -> seleccionarMedioPago(id, monto, moneda)
            ));
        }

        card.addView(titulo);
        card.addView(detalle);
        card.addView(btnPagar);

        return card;
    }

    private void seleccionarMedioPago(int fineId, double monto, String moneda) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(ApiConfig.BASE_URL + "/api/clients/"
                        + userId + "/payment-methods").openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                if (connection.getResponseCode() != 200) throw new Exception("No se pudieron consultar los medios de pago");
                JSONArray methods = new JSONArray(leerRespuesta(connection.getInputStream()));
                List<Integer> ids = new ArrayList<>();
                List<String> options = new ArrayList<>();
                for (int i = 0; i < methods.length(); i++) {
                    JSONObject method = methods.optJSONObject(i);
                    if (method == null || !"si".equals(method.optString("verificado"))) continue;
                    String methodCurrency = method.optString("moneda", "pesos");
                    String type = method.optString("tipo", "");
                    boolean compatible = moneda.equals(methodCurrency)
                            && ("pesos".equals(moneda) || "cuenta_bancaria".equals(type)
                            || ("tarjeta_credito".equals(type) && "si".equals(method.optString("esExtranjera")))
                            || "cheque_certificado".equals(type));
                    if (!compatible) continue;
                    ids.add(method.optInt("id"));
                    options.add(method.optString("entidad", tipoLegible(type)) + " · " + tipoLegible(type)
                            + "\n" + methodCurrency.toUpperCase());
                }
                mainHandler.post(() -> {
                    if (ids.isEmpty()) {
                        FeedbackDialog.error(this, "No tenés medios verificados compatibles con " + moneda + ".");
                        return;
                    }
                    FeedbackDialog.seleccionar(this, "Elegí cómo regularizarla",
                            "Multa #" + fineId + " · " + moneda + " " + String.format("%.2f", monto),
                            options, index -> pagarMulta(fineId, ids.get(index)));
                });
            } catch (Exception e) {
                mainHandler.post(() -> FeedbackDialog.error(this, e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void pagarMulta(int fineId, int medioPagoId) {
        txtMensajeMultas.setText("Registrando pago de multa...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/fines/" + fineId + "/pay");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("medioPagoId", medioPagoId);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String respuesta = leerRespuesta(inputStream);
                JSONObject json = new JSONObject(respuesta);

                mainHandler.post(() -> {
                    String mensaje = json.optString(
                            statusCode == 200 ? "mensaje" : "error",
                            statusCode == 200 ? "Multa marcada como pagada." : "No se pudo pagar la multa."
                    );
                    txtMensajeMultas.setText(mensaje);
                    if (statusCode == 200) {
                        FeedbackDialog.ok(FinesActivity.this, mensaje);
                        cargarMultas();
                    } else {
                        FeedbackDialog.error(FinesActivity.this, mensaje);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String mensaje = "No se pudo conectar con el servidor.";
                    txtMensajeMultas.setText(mensaje);
                    FeedbackDialog.error(FinesActivity.this, mensaje);
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String tipoLegible(String type) {
        if ("cuenta_bancaria".equals(type)) return "Cuenta bancaria";
        if ("tarjeta_credito".equals(type)) return "Tarjeta de crédito";
        if ("cheque_certificado".equals(type)) return "Cheque certificado";
        return "Medio de pago";
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

    private String formatearFecha(String raw) {
        if (raw == null || raw.equals("-")) return "-";
        raw = raw.trim();
        if (raw.startsWith("date ")) raw = raw.substring(5).trim();
        try {
            String datePart = raw.length() >= 10 ? raw.substring(0, 10) : raw;
            java.text.SimpleDateFormat inFmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            java.text.SimpleDateFormat outFmt = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
            return outFmt.format(inFmt.parse(datePart));
        } catch (Exception e) { return raw; }
    }
}
