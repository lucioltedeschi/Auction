package com.example.clase4;

import android.content.Intent;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PurchasesActivity extends AppCompatActivity {

    private TextView txtMensajeCompras;
    private LinearLayout contenedorCompras;
    private int userId;
    private String token;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchases);

        getWindow().setStatusBarColor(Color.parseColor("#071827"));
        getWindow().setNavigationBarColor(Color.parseColor("#071827"));
        SystemBars.configure(this, "#071827", false, "#071827", false);

        txtMensajeCompras = findViewById(R.id.txtMensajeCompras);
        contenedorCompras = findViewById(R.id.contenedorCompras);

        findViewById(R.id.btnBackCompras).setOnClickListener(v -> finish());

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = preferences.getInt("userId", 0);
        token = preferences.getString("token", "");

    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarCompras();
    }

    private void cargarCompras() {
        txtMensajeCompras.setText("Cargando compras...");
        contenedorCompras.removeAllViews();

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/purchases");
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
                    JSONArray compras = new JSONArray(respuesta);
                    mainHandler.post(() -> mostrarCompras(compras));
                } else {
                    JSONObject errorJson = new JSONObject(respuesta);
                    mainHandler.post(() -> txtMensajeCompras.setText(
                            errorJson.optString("error", "Error al cargar compras")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> txtMensajeCompras.setText("No se pudo conectar con el servidor."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarCompras(JSONArray compras) {
        contenedorCompras.removeAllViews();

        if (compras.length() == 0) {
            txtMensajeCompras.setText("No tenés compras adjudicadas.");
            return;
        }

        txtMensajeCompras.setText("");

        try {
            for (int i = 0; i < compras.length(); i++) {
                contenedorCompras.addView(crearCardCompra(compras.getJSONObject(i)));
            }
        } catch (Exception e) {
            txtMensajeCompras.setText("Error mostrando compras.");
        }
    }

    private View crearCardCompra(JSONObject compra) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int)(18 * density);
        int marginBottom = (int)(16 * density);

        int ventaId = compra.optInt("ventaId", 0);
        String articulo = compra.optString("descripcionCatalogo", "-");
        String estadoPago = compra.optString("estadoPago", "pendiente");
        double importe = compra.optDouble("importe", 0);
        double comision = compra.optDouble("comision", 0);
        double envio = compra.optDouble("costoEnvio", 0);
        double total = importe + comision + envio;
        String fechaLimite = formatearFecha(compra.optString("fechaLimitePago", "-"));
        String moneda = compra.optString("moneda", "pesos");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundResource(R.drawable.bg_card_premium);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, marginBottom);
        card.setLayoutParams(params);
        card.setElevation(4);

        // Header row: compra # + estado chip
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView txtTitulo = new TextView(this);
        txtTitulo.setText("Compra #" + ventaId);
        txtTitulo.setTextSize(16);
        txtTitulo.setTextColor(Color.parseColor("#071827"));
        txtTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        txtTitulo.setLayoutParams(titleParams);

        TextView chip = new TextView(this);
        if ("pagado".equals(estadoPago)) {
            chip.setText("PAGADO");
            chip.setBackgroundResource(R.drawable.bg_success_chip);
            chip.setTextColor(Color.parseColor("#166534"));
        } else {
            chip.setText("PENDIENTE");
            chip.setBackgroundResource(R.drawable.bg_danger_chip);
            chip.setTextColor(Color.parseColor("#991B1B"));
        }
        chip.setTextSize(10);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setPadding((int)(12 * density), (int)(4 * density), (int)(12 * density), (int)(4 * density));

        headerRow.addView(txtTitulo);
        headerRow.addView(chip);

        // Articulo
        TextView txtArticulo = new TextView(this);
        txtArticulo.setText(articulo);
        txtArticulo.setTextSize(14);
        txtArticulo.setTextColor(Color.parseColor("#475569"));
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        artParams.setMargins(0, (int)(8 * density), 0, (int)(8 * density));
        txtArticulo.setLayoutParams(artParams);

        // Financials
        TextView txtMontos = new TextView(this);
        txtMontos.setText(
                "Puja: $" + String.format("%.2f", importe) + "\n" +
                "Comisión: $" + String.format("%.2f", comision) + "\n" +
                "Envío: $" + String.format("%.2f", envio) + "\n" +
                "Total: $" + String.format("%.2f", total)
        );
        txtMontos.setTextSize(13);
        txtMontos.setTextColor(Color.parseColor("#475569"));
        txtMontos.setLineSpacing(3, 1.0f);

        // Deadline
        TextView txtFecha = new TextView(this);
        if (!"pagado".equals(estadoPago)) {
            txtFecha.setText("Pagar antes del " + fechaLimite);
            txtFecha.setTextColor(Color.parseColor("#A8872F"));
        } else {
            txtFecha.setText("Pago acreditado");
            txtFecha.setTextColor(Color.parseColor("#166534"));
        }
        txtFecha.setTextSize(12);
        txtFecha.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams fechaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fechaParams.setMargins(0, (int)(8 * density), 0, (int)(12 * density));
        txtFecha.setLayoutParams(fechaParams);

        // Pay button
        Button btnPagar = new Button(this);
        if ("pagado".equals(estadoPago)) {
            btnPagar.setText("PAGO ACREDITADO");
            btnPagar.setBackgroundResource(R.drawable.bg_button_outline);
            btnPagar.setTextColor(Color.parseColor("#64748B"));
            btnPagar.setEnabled(false);
        } else {
            btnPagar.setText("ELEGIR MEDIO Y PAGAR");
            btnPagar.setBackgroundResource(R.drawable.bg_button_gold);
            btnPagar.setTextColor(Color.parseColor("#071827"));
            btnPagar.setOnClickListener(v -> cargarMediosParaPago(ventaId, total, moneda));
        }
        btnPagar.setTextSize(12);
        btnPagar.setTypeface(null, android.graphics.Typeface.BOLD);

        card.addView(headerRow);
        card.addView(txtArticulo);
        card.addView(txtMontos);
        card.addView(txtFecha);
        card.addView(btnPagar);

        return card;
    }

    private void cargarMediosParaPago(int ventaId, double total, String moneda) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/payment-methods");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                int code = connection.getResponseCode();
                if (code != 200) throw new IllegalStateException("No se pudieron consultar los medios de pago.");
                JSONArray methods = new JSONArray(leerRespuesta(connection.getInputStream()));
                List<Integer> ids = new ArrayList<>();
                List<String> options = new ArrayList<>();
                for (int i = 0; i < methods.length(); i++) {
                    JSONObject method = methods.getJSONObject(i);
                    String type = method.optString("tipo");
                    String methodCurrency = method.optString("moneda", "pesos");
                    boolean verified = "si".equals(method.optString("verificado"));
                    boolean compatible = moneda.equals(methodCurrency) &&
                            ("pesos".equals(moneda) || "cuenta_bancaria".equals(type)
                                    || ("tarjeta_credito".equals(type) && "si".equals(method.optString("esExtranjera")))
                                    || "cheque_certificado".equals(type));
                    if (!verified || !compatible) continue;
                    ids.add(method.optInt("id"));
                    String reference = method.optString("numeroReferencia", "");
                    String last = reference.length() > 6 ? "…" + reference.substring(reference.length() - 6) : reference;
                    options.add(method.optString("entidad", tipoLegible(type)) + " · " + tipoLegible(type)
                            + "\n" + last + " · " + methodCurrency.toUpperCase());
                }
                mainHandler.post(() -> {
                    if (ids.isEmpty()) {
                        FeedbackDialog.error(this, "No tenés medios verificados compatibles con " + moneda + ".");
                        return;
                    }
                    FeedbackDialog.seleccionar(this, "Elegí cómo pagar",
                            "Compra #" + ventaId + " · Total " + moneda + " " + String.format("%.2f", total),
                            options, index -> FeedbackDialog.confirmar(this, "Confirmar pago",
                                    options.get(index) + "\n\nSe acreditará " + moneda + " " + String.format("%.2f", total) + ".",
                                    () -> pagarCompra(ventaId, ids.get(index))));
                });
            } catch (Exception error) {
                mainHandler.post(() -> FeedbackDialog.error(this, error.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void pagarCompra(int ventaId, int medioPagoId) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/purchases/" + ventaId + "/pay");
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
                    if (statusCode == 200) {
                        FeedbackDialog.ok(PurchasesActivity.this, "El pago quedó acreditado correctamente.");
                        cargarCompras();
                    } else {
                        String mensaje = json.optString("error", "No se pudo registrar el pago.");
                        txtMensajeCompras.setText(mensaje);
                        FeedbackDialog.error(PurchasesActivity.this, mensaje);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String mensaje = "No se pudo conectar con el servidor.";
                    txtMensajeCompras.setText(mensaje);
                    FeedbackDialog.error(PurchasesActivity.this, mensaje);
                });
            } finally {
                if (connection != null) connection.disconnect();
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
        StringBuilder sb = new StringBuilder();
        String linea;
        while ((linea = reader.readLine()) != null) sb.append(linea);
        return sb.toString();
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
