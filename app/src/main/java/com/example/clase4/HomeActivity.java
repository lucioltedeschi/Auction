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
import android.widget.ScrollView;

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
import android.content.Intent;

public class HomeActivity extends AppCompatActivity {

    private TextView txtBienvenida;
    private TextView txtCategoria;
    private TextView txtMensajeHome;
    private Button btnActualizarSubastas;
    private LinearLayout contenedorSubastas;

    // tiles reemplazados por LinearLayout — se acceden via findViewById directo
    private Button btnMediosPago = null;
    private Button btnSolicitarSubasta = null;
    private Button btnHistorial = null;
    private Button btnPerfil = null;
    private Button btnNotificaciones = null;
    private Button btnMultas = null;
    private Button btnCompras = null;
    private Button btnAdmin = null;

    /*
     IMPORTANTE:
     Usá la misma IP que pusiste en LoginActivity.java.
    */

    private int userId;
    private String token;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#F3F0E8"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#F3F0E8"));

        BottomNavHelper.configurar(this);

        txtBienvenida = findViewById(R.id.txtBienvenida);
        txtCategoria = findViewById(R.id.txtCategoria);
        txtMensajeHome = findViewById(R.id.txtMensajeHome);
        btnActualizarSubastas = findViewById(R.id.btnActualizarSubastas);
        btnMediosPago = null;
        btnSolicitarSubasta = null;
        btnHistorial = null;
        btnPerfil = null;
        btnNotificaciones = null;
        btnMultas = null;
        btnCompras = null;
        btnAdmin = null;
        contenedorSubastas = findViewById(R.id.contenedorSubastas);

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);

        userId = preferences.getInt("userId", 0);
        token = preferences.getString("token", "");
        String nombre = preferences.getString("nombre", "");
        String apellido = preferences.getString("apellido", "");
        String categoria = preferences.getString("categoria", "");
        boolean esAdmin = preferences.getBoolean("esAdmin", false);

        txtBienvenida.setText("Bienvenido, " + nombre + " " + apellido);
        txtCategoria.setText("Categoría: " + categoria);

        View tileAdmin = findViewById(R.id.btnAdmin);
        tileAdmin.setVisibility(esAdmin ? View.VISIBLE : View.GONE);

        btnActualizarSubastas.setOnClickListener(v -> cargarSubastas());

        findViewById(R.id.btnMediosPago).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, PaymentMethodsActivity.class)));

        findViewById(R.id.btnSolicitarSubasta).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ProductRequestActivity.class)));

        findViewById(R.id.btnHistorial).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, HistoryActivity.class)));

        findViewById(R.id.btnPerfil).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class)));

        findViewById(R.id.btnNotificaciones).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, NotificationsActivity.class)));

        findViewById(R.id.btnMultas).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, FinesActivity.class)));

        findViewById(R.id.btnCompras).setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, PurchasesActivity.class)));

        tileAdmin.setOnClickListener(v ->
                startActivity(new Intent(HomeActivity.this, AdminActivity.class)));

        cargarSubastas();
        if (getIntent().getBooleanExtra("goToAuctions", false)) {
            View tituloSubastas = findViewById(R.id.txtTituloSubastas);

            if (tituloSubastas != null) {
                tituloSubastas.postDelayed(() -> irASeccionSubastas(), 500);
            }
        }
    }

    private void cargarSubastas() {
        txtMensajeHome.setText("Cargando subastas...");
        contenedorSubastas.removeAllViews();

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/auctions");
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
                    JSONArray subastas = new JSONArray(respuesta);

                    mainHandler.post(() -> mostrarSubastas(subastas));
                } else {
                    JSONObject errorJson = new JSONObject(respuesta);
                    String error = errorJson.optString("error", "Error al cargar subastas");

                    mainHandler.post(() -> txtMensajeHome.setText(error));
                }

            } catch (Exception e) {
                mainHandler.post(() -> txtMensajeHome.setText("No se pudo conectar con el servidor."));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void mostrarSubastas(JSONArray subastas) {
        contenedorSubastas.removeAllViews();

        if (subastas.length() == 0) {
            txtMensajeHome.setText("No hay subastas disponibles.");
            return;
        }

        int total = subastas.length();
        int mostrar = Math.min(total, 3);
        txtMensajeHome.setText(total == 1 ? "1 subasta disponible" : total + " subastas disponibles");

        try {
            for (int i = 0; i < mostrar; i++) {
                JSONObject subasta = subastas.getJSONObject(i);

                int id = subasta.getInt("id");
                String fecha = formatearFecha(subasta.optString("fecha", "-"));
                String hora = formatearHora(subasta.optString("hora", "-"));
                String estado = subasta.optString("estado", "-");
                String ubicacion = subasta.optString("ubicacion", "-");
                String categoria = subasta.optString("categoria", "-");
                String moneda = formatearMoneda(subasta.optString("moneda", "-"));
                boolean puedePujar = subasta.optBoolean("puedePujar", false);
                String motivoBloqueo = subasta.optString("motivoBloqueo", "");

                View card = crearCardSubasta(
                        id,
                        fecha,
                        hora,
                        estado,
                        ubicacion,
                        categoria,
                        moneda,
                        puedePujar,
                        motivoBloqueo
                );

                contenedorSubastas.addView(card);
            }

            if (total > 0) {
                Button btnVerTodas = new Button(this);
                btnVerTodas.setText(total > 3 ? "VER TODAS (" + total + " SUBASTAS) →" : "VER TODAS LAS SUBASTAS →");
                btnVerTodas.setTextColor(Color.parseColor("#071827"));
                btnVerTodas.setTextSize(12);
                btnVerTodas.setTypeface(null, android.graphics.Typeface.BOLD);
                btnVerTodas.setBackgroundResource(R.drawable.bg_button_outline);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
                p.setMargins(0, dp(8), 0, 0);
                btnVerTodas.setLayoutParams(p);
                btnVerTodas.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, SubastasActivity.class)));
                contenedorSubastas.addView(btnVerTodas);
            }
        } catch (Exception e) {
            txtMensajeHome.setText("Error mostrando subastas.");
        }
    }

    private View crearCardSubasta(
            int id,
            String fecha,
            String hora,
            String estado,
            String ubicacion,
            String categoria,
            String moneda,
            boolean puedePujar,
            String motivoBloqueo
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(18));
        card.setBackgroundResource(R.drawable.bg_card_premium);
        card.setElevation(dp(2));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(22));
        card.setLayoutParams(cardParams);

        // BLOQUE VISUAL SUPERIOR
        LinearLayout visual = new LinearLayout(this);
        visual.setOrientation(LinearLayout.VERTICAL);
        visual.setPadding(dp(18), dp(18), dp(18), dp(18));
        visual.setBackgroundResource(R.drawable.bg_visual_lot);

        LinearLayout.LayoutParams visualParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(170)
        );
        visual.setLayoutParams(visualParams);

        TextView chipLive = new TextView(this);
        chipLive.setText(estado.toUpperCase() + "  ·  SUBASTA #" + id);
        chipLive.setTextColor(Color.WHITE);
        chipLive.setTextSize(11);
        chipLive.setTypeface(null, android.graphics.Typeface.BOLD);
        chipLive.setLetterSpacing(0.08f);

        TextView titleVisual = new TextView(this);
        titleVisual.setText("Evento de subasta verificado");
        titleVisual.setTextColor(Color.WHITE);
        titleVisual.setTextSize(23);
        titleVisual.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(52), 0, 0);
        titleVisual.setLayoutParams(titleParams);

        TextView subVisual = new TextView(this);
        subVisual.setText(ubicacion);
        subVisual.setTextColor(Color.parseColor("#E8EEF5"));
        subVisual.setTextSize(14);

        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        subParams.setMargins(0, dp(6), 0, 0);
        subVisual.setLayoutParams(subParams);

        visual.addView(chipLive);
        visual.addView(titleVisual);
        visual.addView(subVisual);

        // CHIP CATEGORÍA
        TextView chipCategoria = new TextView(this);
        chipCategoria.setText(categoria.toUpperCase() + "  ·  " + moneda.toUpperCase());
        chipCategoria.setTextColor(Color.parseColor("#071827"));
        chipCategoria.setTextSize(11);
        chipCategoria.setTypeface(null, android.graphics.Typeface.BOLD);
        chipCategoria.setGravity(android.view.Gravity.CENTER);
        chipCategoria.setPadding(dp(14), dp(8), dp(14), dp(8));
        chipCategoria.setBackgroundResource(R.drawable.bg_gold_chip);

        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        chipParams.setMargins(0, dp(16), 0, 0);
        chipCategoria.setLayoutParams(chipParams);

        // DESCRIPCIÓN EDITORIAL
        TextView descripcion = new TextView(this);
        descripcion.setText("Evento de subasta verificado con activos seleccionados por especialistas. Accedé al catálogo para revisar lotes, precios base y disponibilidad de puja.");
        descripcion.setTextColor(Color.parseColor("#475569"));
        descripcion.setTextSize(14);
        descripcion.setLineSpacing(dp(3), 1.0f);

        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, dp(14), 0, 0);
        descripcion.setLayoutParams(descParams);

        // MÉTRICAS
        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams metricsRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metricsRowParams.setMargins(0, dp(16), 0, 0);
        metricsRow.setLayoutParams(metricsRowParams);

        TextView dateBox = new TextView(this);
        dateBox.setText("DATE\n" + fecha);
        dateBox.setTextColor(Color.parseColor("#071827"));
        dateBox.setTextSize(12);
        dateBox.setTypeface(null, android.graphics.Typeface.BOLD);
        dateBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        dateBox.setBackgroundResource(R.drawable.bg_metric_box);

        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        dateParams.setMargins(0, 0, dp(6), 0);
        dateBox.setLayoutParams(dateParams);

        TextView timeBox = new TextView(this);
        timeBox.setText("START\n" + hora);
        timeBox.setTextColor(Color.parseColor("#071827"));
        timeBox.setTextSize(12);
        timeBox.setTypeface(null, android.graphics.Typeface.BOLD);
        timeBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        timeBox.setBackgroundResource(R.drawable.bg_metric_box);

        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        timeParams.setMargins(dp(6), 0, 0, 0);
        timeBox.setLayoutParams(timeParams);

        metricsRow.addView(dateBox);
        metricsRow.addView(timeBox);

        // ESTADO DE ACCESO
        TextView acceso = new TextView(this);

        if (puedePujar) {
            acceso.setText("USUARIO HABILITADO PARA PUJAR");
            acceso.setTextColor(Color.parseColor("#166534"));
            acceso.setBackgroundResource(R.drawable.bg_success_chip);
        } else {
            acceso.setText("SOLO VISUALIZACIÓN · " + motivoBloqueo);
            acceso.setTextColor(Color.parseColor("#991B1B"));
            acceso.setBackgroundResource(R.drawable.bg_danger_chip);
        }

        acceso.setTextSize(11);
        acceso.setTypeface(null, android.graphics.Typeface.BOLD);
        acceso.setGravity(android.view.Gravity.CENTER);
        acceso.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout.LayoutParams accesoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        accesoParams.setMargins(0, dp(16), 0, 0);
        acceso.setLayoutParams(accesoParams);

        Button btnVerDetalle = new Button(this);
        btnVerDetalle.setText(puedePujar ? "ENTRAR AL CATÁLOGO" : "VER CATÁLOGO");
        btnVerDetalle.setTextColor(Color.parseColor("#071827"));
        btnVerDetalle.setTextSize(12);
        btnVerDetalle.setTypeface(null, android.graphics.Typeface.BOLD);
        btnVerDetalle.setBackgroundResource(
                puedePujar ? R.drawable.bg_button_gold : R.drawable.bg_button_outline
        );

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        btnParams.setMargins(0, dp(16), 0, 0);
        btnVerDetalle.setLayoutParams(btnParams);

        btnVerDetalle.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, AuctionDetailActivity.class);
            intent.putExtra("auctionId", id);
            intent.putExtra("puedePujar", puedePujar);
            intent.putExtra("categoria", categoria);
            startActivity(intent);
        });

        card.addView(visual);
        card.addView(chipCategoria);
        card.addView(descripcion);
        card.addView(metricsRow);
        card.addView(acceso);
        card.addView(btnVerDetalle);

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
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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
        } catch (Exception e) {
            return raw;
        }
    }

    private String formatearHora(String raw) {
        if (raw == null || raw.equals("-")) return "-";
        raw = raw.trim();
        if (raw.startsWith("date ")) raw = raw.substring(5).trim();
        // Handle concatenated datetime like "2026-01-0118:00:00" — take last part after date
        if (raw.length() > 10) {
            String after = raw.substring(10);
            // Could start with T, space, or immediately with time digits
            if (after.startsWith("T") || after.startsWith(" ")) after = after.substring(1);
            raw = after;
        }
        return raw.length() >= 5 ? raw.substring(0, 5) : raw;
    }

    private String formatearMoneda(String moneda) {
        if (moneda == null || moneda.equals("-")) return "-";
        switch (moneda.toLowerCase().trim()) {
            case "pesos": return "$";
            case "dolares":
            case "dólares":
            case "usd": return "USD";
            default: return moneda.toUpperCase();
        }
    }

    public void irASeccionSubastas() {
        ScrollView scrollHome = findViewById(R.id.scrollHome);
        View tituloSubastas = findViewById(R.id.txtTituloSubastas);

        if (scrollHome != null && tituloSubastas != null) {
            scrollHome.post(() -> scrollHome.smoothScrollTo(0, tituloSubastas.getTop()));
        }
    }
}
