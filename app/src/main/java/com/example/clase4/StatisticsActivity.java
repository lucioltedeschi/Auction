package com.example.clase4;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
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
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatisticsActivity extends AppCompatActivity {
    private TextView estado;
    private GridLayout grid;
    private LinearLayout categorias;
    private LinearLayout actividad;
    private int userId;
    private String token;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        SystemBars.configure(this, "#F3F0E8", true, "#F3F0E8", true);
        BottomNavHelper.configurar(this);
        estado = findViewById(R.id.txtEstadoEstadisticas);
        grid = findViewById(R.id.gridMetricas);
        categorias = findViewById(R.id.contenedorCategorias);
        actividad = findViewById(R.id.contenedorActividad);
        userId = getSharedPreferences("sesion", MODE_PRIVATE).getInt("userId", 0);
        token = getSharedPreferences("sesion", MODE_PRIVATE).getString("token", "");
        cargar();
    }

    private void cargar() {
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/statistics").openConnection();
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + token);
                int status = c.getResponseCode();
                String body = leer(status < 300 ? c.getInputStream() : c.getErrorStream());
                if (status == 200) {
                    JSONObject data = new JSONObject(body);
                    main.post(() -> mostrar(data));
                } else {
                    String error = new JSONObject(body).optString("error", "No se pudieron cargar los indicadores");
                    main.post(() -> estado.setText(error));
                }
            } catch (Exception e) {
                main.post(() -> estado.setText("No se pudo conectar con el servidor."));
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private void mostrar(JSONObject data) {
        JSONObject r = data.optJSONObject("resumen");
        if (r == null) return;
        grid.removeAllViews(); categorias.removeAllViews(); actividad.removeAllViews();
        agregarMetrica("SUBASTAS", r.optInt("subastasParticipadas"), "participadas");
        agregarMetrica("LOTES", r.optInt("lotesOfertados"), "con ofertas");
        agregarMetrica("PUJAS", r.optInt("pujasRealizadas"), "confirmadas");
        agregarMetrica("GANADOS", r.optInt("lotesGanados"), "adjudicados");
        agregarMetrica("CONSIGNADOS", r.optInt("consignaciones"), "productos");
        agregarMetrica("AVISOS", r.optInt("avisosPendientes"), "sin leer");

        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("es", "AR"));
        estado.setText("Mejores ofertas acumuladas: " + nf.format(r.optDouble("totalMejoresOfertas"))
                + "  •  Total abonado: " + nf.format(r.optDouble("totalPagado")));

        JSONArray cats = data.optJSONArray("porCategoria");
        int max = 1;
        if (cats != null) for (int i=0; i<cats.length(); i++) max = Math.max(max, cats.optJSONObject(i).optInt("pujas"));
        if (cats == null || cats.length() == 0) categorias.addView(texto("Todavía no hay pujas para comparar.", 14, false, "#64748B"));
        else for (int i=0; i<cats.length(); i++) agregarBarra(cats.optJSONObject(i), max);

        JSONArray recientes = data.optJSONArray("actividadReciente");
        if (recientes == null || recientes.length() == 0) actividad.addView(texto("Tu actividad aparecerá aquí después de tu primera puja.", 14, false, "#64748B"));
        else for (int i=0; i<recientes.length(); i++) agregarActividad(recientes.optJSONObject(i));
    }

    private void agregarMetrica(String titulo, int valor, String detalle) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13)); card.setBackgroundResource(R.drawable.bg_metric_box);
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(); p.width = 0; p.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        p.setMargins(dp(4), dp(4), dp(4), dp(4)); card.setLayoutParams(p);
        card.addView(texto(String.valueOf(valor), 25, true, "#071827"));
        card.addView(texto(titulo, 11, true, "#A8872F")); card.addView(texto(detalle, 12, false, "#64748B")); grid.addView(card);
    }

    private void agregarBarra(JSONObject o, int max) {
        LinearLayout wrap = new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL); wrap.setPadding(0, dp(5), 0, dp(7));
        int valor = o.optInt("pujas");
        TextView label = texto(capitalizar(o.optString("categoria")) + "  ·  " + valor + " pujas", 13, true, "#334155"); wrap.addView(label);
        LinearLayout track = new LinearLayout(this); GradientDrawable fondo = forma("#DED8CB", 12); track.setBackground(fondo);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(9)); tp.setMargins(0, dp(5), 0, 0); track.setLayoutParams(tp);
        View fill = new View(this); fill.setBackground(forma("#A8872F", 12));
        track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.08f, valor/(float)max)));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, 1, Math.max(0f, 1f-valor/(float)max))); wrap.addView(track); categorias.addView(wrap);
    }

    private void agregarActividad(JSONObject o) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.bg_metric_box); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0,0,0,dp(8)); card.setLayoutParams(p);
        card.addView(texto(o.optString("articulo", "Artículo"), 15, true, "#071827"));
        card.addView(texto("Mejor oferta: " + o.optString("moneda", "ARS") + " " + String.format(Locale.getDefault(), "%,.2f", o.optDouble("mejorOferta")), 13, false, "#475569")); actividad.addView(card);
    }

    private TextView texto(String s, int size, boolean bold, String color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(Color.parseColor(color)); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private GradientDrawable forma(String color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(Color.parseColor(color)); g.setCornerRadius(dp(radius)); return g; }
    private String capitalizar(String s) { return s.isEmpty() ? "Sin categoría" : s.substring(0,1).toUpperCase() + s.substring(1); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private String leer(InputStream in) throws Exception { BufferedReader b = new BufferedReader(new InputStreamReader(in)); StringBuilder s = new StringBuilder(); String l; while ((l=b.readLine())!=null) s.append(l); return s.toString(); }
}
