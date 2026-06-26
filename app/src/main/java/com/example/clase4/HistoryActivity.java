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

public class HistoryActivity extends AppCompatActivity {

    private TextView txtMensajeHistorial;
    private LinearLayout contenedorHistorial;

    private int userId;
    private String token;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#F3F0E8"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#F3F0E8"));
        BottomNavHelper.configurar(this);

        txtMensajeHistorial = findViewById(R.id.txtMensajeHistorial);
        contenedorHistorial = findViewById(R.id.contenedorHistorial);

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = preferences.getInt("userId", 0);
        token = preferences.getString("token", "");

        cargarHistorial();
    }

    private void cargarHistorial() {
        txtMensajeHistorial.setText("Cargando historial...");
        contenedorHistorial.removeAllViews();

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/history");
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
                    JSONArray historial = new JSONArray(respuesta);
                    mainHandler.post(() -> mostrarHistorial(historial));
                } else {
                    JSONObject errorJson = new JSONObject(respuesta);
                    String error = errorJson.optString("error", "Error al cargar historial");
                    mainHandler.post(() -> txtMensajeHistorial.setText(error));
                }

            } catch (Exception e) {
                mainHandler.post(() -> txtMensajeHistorial.setText("No se pudo conectar con el servidor."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void mostrarHistorial(JSONArray historial) {
        contenedorHistorial.removeAllViews();

        if (historial.length() == 0) {
            txtMensajeHistorial.setText("Todavía no tenés participaciones registradas.");
            return;
        }

        txtMensajeHistorial.setText("");

        try {
            for (int i = 0; i < historial.length(); i++) {
                JSONObject item = historial.getJSONObject(i);
                contenedorHistorial.addView(crearCardHistorial(item));
            }
        } catch (Exception e) {
            txtMensajeHistorial.setText("Error mostrando historial.");
        }
    }

    private View crearCardHistorial(JSONObject item) throws Exception {
        int pad = dp(18);

        int subastaId    = item.optInt("id", 0);
        String fecha     = formatearFecha(item.optString("fecha", "-"));
        String hora      = formatearHora(item.optString("hora", "-"));
        String ubicacion = item.optString("ubicacion", "-");
        String categoria = item.optString("categoria", "-");
        String moneda    = formatearMoneda(item.optString("moneda", "-"));
        String estado    = item.optString("estado", "-");
        int totalPujas   = item.optInt("totalPujas", 0);
        int itemsGanados = item.optInt("itemsGanados", 0);

        boolean cerrada   = "cerrada".equals(estado);
        boolean ganador   = itemsGanados > 0;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundResource(ganador ? R.drawable.bg_card_dark_premium : R.drawable.bg_card_premium);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(14));
        card.setLayoutParams(p);
        card.setElevation(4);

        // ── Header row: nro subasta + estado chip ─────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView titulo = new TextView(this);
        titulo.setText("Subasta #" + subastaId);
        titulo.setTextSize(17);
        titulo.setTextColor(ganador ? Color.WHITE : Color.parseColor("#071827"));
        titulo.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titulo.setLayoutParams(titleP);

        TextView chip = new TextView(this);
        if (ganador) {
            chip.setText("GANADOR");
            chip.setBackgroundResource(R.drawable.bg_button_gold);
            chip.setTextColor(Color.parseColor("#071827"));
        } else if (cerrada) {
            chip.setText("FINALIZADA");
            chip.setBackgroundResource(R.drawable.bg_success_chip);
            chip.setTextColor(Color.parseColor("#166534"));
        } else {
            chip.setText("EN CURSO");
            chip.setBackgroundResource(R.drawable.bg_danger_chip);
            chip.setTextColor(Color.parseColor("#991B1B"));
        }
        chip.setTextSize(10);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));

        header.addView(titulo);
        header.addView(chip);

        // ── Divider ───────────────────────────────────────────────────────
        View divider = new View(this);
        LinearLayout.LayoutParams divP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divP.setMargins(0, dp(10), 0, dp(10));
        divider.setLayoutParams(divP);
        divider.setBackgroundColor(ganador ? Color.parseColor("#1E3A6B") : Color.parseColor("#E2E8F0"));

        // ── Info rows ─────────────────────────────────────────────────────
        int textColor = ganador ? Color.parseColor("#D7E3EF") : Color.parseColor("#475569");

        TextView info = new TextView(this);
        info.setText(
                "Fecha: " + fecha + "  -  " + hora + "\n" +
                "Ubicacion: " + ubicacion + "\n" +
                "Categoria: " + capitalize(categoria) + "  -  " + moneda
        );
        info.setTextSize(13);
        info.setTextColor(textColor);
        info.setLineSpacing(dp(3), 1f);

        // ── Stats row ─────────────────────────────────────────────────────
        LinearLayout.LayoutParams statsP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statsP.setMargins(0, dp(10), 0, 0);

        TextView stats = new TextView(this);
        String statsText = totalPujas + " puja" + (totalPujas != 1 ? "s" : "") + " realizadas";
        if (itemsGanados > 0)
            statsText += "  -  " + itemsGanados + " lote" + (itemsGanados != 1 ? "s" : "") + " ganado" + (itemsGanados != 1 ? "s" : "");
        stats.setText(statsText);
        stats.setTextSize(12);
        stats.setTextColor(ganador ? Color.parseColor("#A8872F") : Color.parseColor("#64748B"));
        stats.setTypeface(null, android.graphics.Typeface.BOLD);
        stats.setLayoutParams(statsP);

        card.addView(header);
        card.addView(divider);
        card.addView(info);
        card.addView(stats);

        return card;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
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

    private String formatearHora(String raw) {
        if (raw == null || raw.equals("-")) return "-";
        raw = raw.trim();
        if (raw.startsWith("date ")) raw = raw.substring(5).trim();
        if (raw.length() > 10) {
            String after = raw.substring(10);
            if (after.startsWith("T") || after.startsWith(" ")) after = after.substring(1);
            raw = after;
        }
        return raw.length() >= 5 ? raw.substring(0, 5) : raw;
    }

    private String formatearMoneda(String moneda) {
        if (moneda == null || moneda.equals("-")) return "-";
        switch (moneda.toLowerCase().trim()) {
            case "pesos": return "$";
            case "dolares": case "dólares": case "usd": return "USD";
            default: return moneda.toUpperCase();
        }
    }
}
