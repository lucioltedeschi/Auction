package com.example.clase4;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PaymentMethodsActivity extends AppCompatActivity {

    private TextView txtMensajeMediosPago;
    private LinearLayout contenedorMediosPago;

    private int userId;
    private String token;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_methods);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#071827"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#F3F0E8"));

        txtMensajeMediosPago = findViewById(R.id.txtMensajeMediosPago);
        contenedorMediosPago = findViewById(R.id.contenedorMediosPago);

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = preferences.getInt("userId", 0);
        token = preferences.getString("token", "");

        findViewById(R.id.btnBackMediosPago).setOnClickListener(v -> finish());
        findViewById(R.id.btnNuevaCuentaBancaria).setOnClickListener(v -> abrirFlujo("cuenta_bancaria"));
        findViewById(R.id.btnNuevaTarjetaCredito).setOnClickListener(v -> abrirFlujo("tarjeta_credito"));
        findViewById(R.id.btnNuevoChequeCertificado).setOnClickListener(v -> abrirFlujo("cheque_certificado"));

        cargarMediosPago();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarMediosPago();
    }

    private void abrirFlujo(String tipo) {
        Intent intent = new Intent(this, PaymentMethodFormActivity.class);
        intent.putExtra("tipo", tipo);
        startActivity(intent);
    }

    private void cargarMediosPago() {
        txtMensajeMediosPago.setText("Cargando...");
        contenedorMediosPago.removeAllViews();

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/payment-methods");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String respuesta = leerRespuesta(inputStream);

                if (statusCode == 200) {
                    JSONArray medios = new JSONArray(respuesta);
                    mainHandler.post(() -> mostrarMedios(medios));
                } else {
                    JSONObject err = new JSONObject(respuesta);
                    mainHandler.post(() -> txtMensajeMediosPago.setText(err.optString("error", "Error al cargar")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> txtMensajeMediosPago.setText("No se pudo conectar con el servidor."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarMedios(JSONArray medios) {
        contenedorMediosPago.removeAllViews();
        if (medios.length() == 0) {
            txtMensajeMediosPago.setText("No tenés métodos de pago registrados.");
            return;
        }
        txtMensajeMediosPago.setText(medios.length() == 1 ? "1 método registrado" : medios.length() + " métodos registrados");

        try {
            for (int i = 0; i < medios.length(); i++) {
                contenedorMediosPago.addView(crearCard(medios.getJSONObject(i)));
            }
        } catch (Exception e) {
            txtMensajeMediosPago.setText("Error mostrando métodos.");
        }
    }

    private View crearCard(JSONObject medio) throws Exception {
        int id = medio.getInt("id");
        String tipo = medio.optString("tipo", "-");
        String entidad = medio.optString("entidad", "-");
        String ref = medio.optString("numeroReferencia", "-");
        String moneda = medio.optString("moneda", "-");
        String verificado = medio.optString("verificado", "-");

        float d = getResources().getDisplayMetrics().density;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int)(18*d),(int)(18*d),(int)(18*d),(int)(18*d));
        card.setBackgroundResource(R.drawable.bg_card_premium);
        card.setElevation(2*d);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, (int)(16*d));
        card.setLayoutParams(cp);

        // Header row: icon + type + chip
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconTv = new TextView(this);
        iconTv.setText(tipoIcono(tipo));
        iconTv.setTextSize(22);

        LinearLayout.LayoutParams iconP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconP.setMargins(0, 0, (int)(10*d), 0);
        iconTv.setLayoutParams(iconP);

        TextView tipoTv = new TextView(this);
        tipoTv.setText(formatTipo(tipo));
        tipoTv.setTextSize(15);
        tipoTv.setTextColor(Color.parseColor("#071827"));
        tipoTv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams tipoP = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tipoTv.setLayoutParams(tipoP);

        TextView chipTv = new TextView(this);
        if ("si".equals(verificado)) {
            chipTv.setText("VERIFICADO");
            chipTv.setTextColor(Color.parseColor("#166534"));
            chipTv.setBackgroundResource(R.drawable.bg_success_chip);
        } else {
            chipTv.setText("PENDIENTE");
            chipTv.setTextColor(Color.parseColor("#991B1B"));
            chipTv.setBackgroundResource(R.drawable.bg_danger_chip);
        }
        chipTv.setTextSize(10);
        chipTv.setTypeface(null, android.graphics.Typeface.BOLD);
        chipTv.setPadding((int)(10*d),(int)(4*d),(int)(10*d),(int)(4*d));

        header.addView(iconTv);
        header.addView(tipoTv);
        header.addView(chipTv);

        // Entity
        TextView entidadTv = new TextView(this);
        entidadTv.setText(entidad + "  ·  " + moneda.toUpperCase());
        entidadTv.setTextSize(13);
        entidadTv.setTextColor(Color.parseColor("#475569"));
        LinearLayout.LayoutParams entP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        entP.setMargins(0, (int)(8*d), 0, 0);
        entidadTv.setLayoutParams(entP);

        // Masked reference
        String refDisplay = mascararRef(tipo, ref);
        TextView refTv = new TextView(this);
        refTv.setText(refDisplay);
        refTv.setTextSize(12);
        refTv.setTextColor(Color.parseColor("#94A3B8"));
        LinearLayout.LayoutParams refP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        refP.setMargins(0, (int)(4*d), 0, 0);
        refTv.setLayoutParams(refP);

        // Edit button
        Button editBtn = new Button(this);
        editBtn.setText("MODIFICAR");
        editBtn.setTextColor(Color.parseColor("#071827"));
        editBtn.setTextSize(12);
        editBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        editBtn.setBackgroundResource(R.drawable.bg_button_outline);
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(46*d));
        btnP.setMargins(0, (int)(14*d), 0, 0);
        editBtn.setLayoutParams(btnP);
        editBtn.setOnClickListener(v -> {
            Intent intent = new Intent(PaymentMethodsActivity.this, PaymentMethodFormActivity.class);
            intent.putExtra("medioPagoId", id);
            intent.putExtra("tipo", tipo);
            intent.putExtra("entidad", entidad);
            intent.putExtra("numeroReferencia", ref);
            intent.putExtra("moneda", moneda);
            intent.putExtra("esExtranjera", medio.optString("esExtranjera", "no"));
            intent.putExtra("montoCheque", medio.optDouble("montoCheque", 0));
            startActivity(intent);
        });

        card.addView(header);
        card.addView(entidadTv);
        card.addView(refTv);
        card.addView(editBtn);
        return card;
    }

    private String tipoIcono(String tipo) {
        if ("tarjeta_credito".equals(tipo)) return "💳";
        if ("cheque_certificado".equals(tipo)) return "📋";
        return "🏦";
    }

    private String formatTipo(String tipo) {
        if ("tarjeta_credito".equals(tipo)) return "Tarjeta de crédito";
        if ("cuenta_bancaria".equals(tipo)) return "Cuenta bancaria";
        if ("cheque_certificado".equals(tipo)) return "Cheque certificado";
        return tipo;
    }

    private String mascararRef(String tipo, String ref) {
        if ("tarjeta_credito".equals(tipo)) {
            // ref is compound "Tarjeta XXXX | Titular ... | Vence MM/AA | CVV ..."
            // Show only last 4 digits hint
            int idx = ref.indexOf("Tarjeta ");
            if (idx >= 0) {
                String after = ref.substring(idx + 8).trim();
                // after is something like "1234567890 | Titular..."
                String[] parts = after.split("\\|");
                if (parts.length > 0) {
                    String num = parts[0].trim().replaceAll("\\s", "");
                    String last4 = num.length() > 4 ? "•••• " + num.substring(num.length() - 4) : num;
                    return last4;
                }
            }
            return "•••• ••••";
        }
        if (ref.length() > 8) return "•••••" + ref.substring(ref.length() - 4);
        return ref;
    }

    private String leerRespuesta(InputStream is) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l);
        return sb.toString();
    }
}
