package com.example.clase4;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;

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
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class AuctionDetailActivity extends AppCompatActivity {

    private TextView txtTopBarSubasta;
    private TextView txtTopBarStatus;
    private TextView txtTopBarPago;
    private TextView txtDatosResumen;
    private TextView txtChevron;
    private LinearLayout collapseBody;
    private TextView txtDatosSubasta;
    private TextView txtItemVivo;
    private TextView txtLiveChip;
    private TextView txtTiempoRestante;
    private TextView txtMejorOfertaVivo;
    private TextView txtPujaMinimaVivo;
    private TextView txtPujaMaximaVivo;
    private TextView txtMensajeDetalle;
    private LinearLayout contenedorCatalogo;
    private android.widget.ScrollView scrollViewAuction;
    private boolean infoExpanded = false;

    private int auctionId;
    private int userId;
    private boolean puedePujar;
    private boolean cumpleRequisitosPuja;
    private boolean subastaAbiertaAhora;
    private boolean conexionActivaRegistrada;
    private String fechaInicioMostrada = "";
    private String horaInicioMostrada = "";
    private String categoriaSubasta;
    private String token;
    private volatile boolean escuchandoEventos;
    private HttpURLConnection conexionEventos;
    private double ultimaMejorOfertaViva = -1;
    private final OkHttpClient webSocketClient = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    private WebSocket webSocket;
    private volatile boolean actividadDestruida;
    private final Map<Integer, LoteEnVivo> estadosLotes = new HashMap<>();
    private final Map<Integer, TextView> relojesLotes = new HashMap<>();
    private final Runnable actualizarRelojesLotes = new Runnable() {
        @Override public void run() {
            boolean hayRelojesActivos = false;
            if (!subastaAbiertaAhora) return;
            for (Map.Entry<Integer, LoteEnVivo> entry : estadosLotes.entrySet()) {
                LoteEnVivo lote = entry.getValue();
                if (!lote.vendido && lote.segundosRestantes > 0) {
                    lote.segundosRestantes--;
                    hayRelojesActivos = true;
                }
                TextView reloj = relojesLotes.get(entry.getKey());
                if (reloj != null) actualizarRelojLote(reloj, lote);
            }
            if (!actividadDestruida && hayRelojesActivos) {
                countdownHandler.postDelayed(this, 1000);
            }
        }
    };
    private final Runnable reconectarWebSocket = () -> {
        if (!actividadDestruida) conectarWebSocket();
    };

    // Countdown timer
    private volatile int segundosRestantes = 0;
    private String itemIdVivo = "";
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (segundosRestantes > 0) {
                segundosRestantes--;
                actualizarContadorUI();
                countdownHandler.postDelayed(this, 1000);
            } else if (segundosRestantes == 0) {
                if (txtTiempoRestante != null) {
                    txtTiempoRestante.setText("00:00");
                    txtTiempoRestante.setTextColor(Color.parseColor("#EF4444"));
                }
                // Timer just hit 0 — check if user won after server processes
                if (!itemIdVivo.isEmpty() && !tiempoAgotadoVerificado) {
                    tiempoAgotadoVerificado = true;
                    mainHandler.postDelayed(() -> verificarSiGano(), 3000);
                }
            }
        }
    };

    // Winner check fields
    private final Set<Integer> comprasConocidas = new HashSet<>();
    private volatile boolean verificandoGanador = false;
    private volatile boolean tiempoAgotadoVerificado = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auction_detail);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#F3F0E8"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#F3F0E8"));
        SystemBars.configure(this, "#F3F0E8", true, "#F3F0E8", true);
        BottomNavHelper.configurar(this);

        txtTopBarSubasta = findViewById(R.id.txtTopBarSubasta);
        txtTopBarStatus = findViewById(R.id.txtTopBarStatus);
        txtTopBarPago = findViewById(R.id.txtTopBarPago);
        txtDatosResumen = findViewById(R.id.txtDatosResumen);
        txtChevron = findViewById(R.id.txtChevron);
        collapseBody = findViewById(R.id.collapseBody);
        txtDatosSubasta = findViewById(R.id.txtDatosSubasta);
        txtItemVivo = findViewById(R.id.txtItemVivo);
        txtLiveChip = findViewById(R.id.txtLiveChip);
        txtTiempoRestante = findViewById(R.id.txtTiempoRestante);
        txtMejorOfertaVivo = findViewById(R.id.txtMejorOfertaVivo);
        txtPujaMinimaVivo = findViewById(R.id.txtPujaMinimaVivo);
        txtPujaMaximaVivo = findViewById(R.id.txtPujaMaximaVivo);
        txtMensajeDetalle = findViewById(R.id.txtMensajeDetalle);
        contenedorCatalogo = findViewById(R.id.contenedorCatalogo);
        scrollViewAuction  = findViewById(R.id.scrollViewAuction);

        // Tap en live panel: scroll al item activo en el catalogo
        View livePanel = findViewById(R.id.livePanel);
        if (livePanel != null) {
            livePanel.setOnClickListener(v -> scrollViewAuction.post(() ->
                    scrollViewAuction.smoothScrollTo(0, contenedorCatalogo.getTop())));
        }

        SharedPreferences prefs = getSharedPreferences("sesion", MODE_PRIVATE);
        token = prefs.getString("token", "");

        auctionId = getIntent().getIntExtra("auctionId", 0);
        puedePujar = getIntent().getBooleanExtra("puedePujar", false);
        cumpleRequisitosPuja = getIntent().getBooleanExtra("cumpleRequisitos", puedePujar);
        subastaAbiertaAhora = "en_curso".equals(getIntent().getStringExtra("estado"));
        fechaInicioMostrada = getIntent().getStringExtra("fecha");
        horaInicioMostrada = getIntent().getStringExtra("hora");
        categoriaSubasta = getIntent().getStringExtra("categoria");
        if (categoriaSubasta == null) categoriaSubasta = "";

        txtTopBarSubasta.setText("SUBASTA #" + auctionId);
        actualizarEstadoProgramacion();

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Collapsible auction info toggle
        findViewById(R.id.collapseHeader).setOnClickListener(v -> toggleCollapseInfo());

        SharedPreferences prefs2 = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = prefs2.getInt("userId", 0);
        if (puedePujar && subastaAbiertaAhora) {
            registrarConexionActiva();
            conexionActivaRegistrada = true;
        }
        cargarTopBarData(userId);

        cargarDetalleSubasta();
        cargarCatalogo();
        precargarComprasConocidas();
        conectarWebSocket();
    }

    @Override
    protected void onDestroy() {
        actividadDestruida = true;
        mainHandler.removeCallbacks(reconectarWebSocket);
        if (webSocket != null) webSocket.cancel();
        escuchandoEventos = false;
        countdownHandler.removeCallbacks(countdownRunnable);
        countdownHandler.removeCallbacks(actualizarRelojesLotes);
        if (conexionEventos != null) conexionEventos.disconnect();
        if (conexionActivaRegistrada) liberarConexionActiva();
        super.onDestroy();
    }

    private void conectarWebSocket() {
        mainHandler.removeCallbacks(reconectarWebSocket);
        String wsBase = ApiConfig.BASE_URL.replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://");
        Request request = new Request.Builder()
                .url(wsBase + "/ws?auctionId=" + auctionId + "&clientId=" + userId)
                .header("Authorization", "Bearer " + token)
                .build();
        webSocket = webSocketClient.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket socket, Response response) {
                mainHandler.post(() -> txtMensajeDetalle.setText(subastaAbiertaAhora
                        ? "Conexión en vivo activa · importes y relojes se actualizan automáticamente"
                        : "Catálogo disponible · las pujas se habilitarán al comenzar la subasta"));
            }

            @Override public void onMessage(WebSocket socket, String text) {
                try {
                    JSONObject evento = new JSONObject(text);
                    String tipo = evento.optString("tipo", "");
                    if ("catalog-state".equals(tipo)) {
                        JSONArray lotes = evento.optJSONArray("lotes");
                        mainHandler.post(() -> {
                            int[] resumen = actualizarEstadosLotes(lotes);
                            cargarCatalogo();
                            if (!subastaAbiertaAhora) {
                                actualizarEstadoProgramacion();
                                txtMensajeDetalle.setText("Catálogo disponible · las pujas se habilitarán al comenzar la subasta");
                                return;
                            }
                            txtItemVivo.setText("Sala sincronizada en tiempo real");
                            txtTiempoRestante.setTextSize(18);
                            txtTiempoRestante.setText("CONECTADO");
                            txtTiempoRestante.setTextColor(Color.parseColor("#86EFAC"));
                            txtMejorOfertaVivo.setText(resumen[0] + " abiertos");
                            txtPujaMinimaVivo.setText(resumen[1] + " pujas");
                            txtPujaMaximaVivo.setText("Por lote");
                            txtMensajeDetalle.setText("Cada lote tiene reloj e historial propios. Una nueva puja reinicia sólo su contador.");
                        });
                    }
                } catch (Exception ignored) {
                }
            }

            @Override public void onClosing(WebSocket socket, int code, String reason) {
                socket.close(1000, null);
            }

            @Override public void onClosed(WebSocket socket, int code, String reason) {
                programarReconexionWebSocket();
            }

            @Override public void onFailure(WebSocket socket, Throwable error, Response response) {
                mainHandler.post(() -> txtMensajeDetalle.setText(subastaAbiertaAhora
                        ? "Reconectando pujas en vivo..."
                        : "Actualizando el catálogo programado..."));
                programarReconexionWebSocket();
            }
        });
    }

    private void programarReconexionWebSocket() {
        if (actividadDestruida) return;
        mainHandler.removeCallbacks(reconectarWebSocket);
        mainHandler.postDelayed(reconectarWebSocket, 2000);
    }

    private int[] actualizarEstadosLotes(JSONArray lotes) {
        int abiertos = 0;
        int pujas = 0;
        if (lotes != null) {
            for (int i = 0; i < lotes.length(); i++) {
                JSONObject json = lotes.optJSONObject(i);
                if (json == null) continue;
                int itemId = json.optInt("itemId", 0);
                if (itemId <= 0) continue;
                LoteEnVivo lote = new LoteEnVivo();
                lote.vendido = "si".equals(json.optString("vendido", "no"));
                lote.segundosRestantes = Math.max(json.optInt("segundosRestantes", 0), 0);
                lote.duracionMinutos = Math.max(json.optInt("duracionItemMinutos", 0), 0);
                lote.cantidadPujas = Math.max(json.optInt("cantidadPujas", 0), 0);
                lote.ultimasPujas = json.optJSONArray("ultimasPujas");
                estadosLotes.put(itemId, lote);
                if (!lote.vendido) abiertos++;
                pujas += lote.cantidadPujas;
            }
        }
        countdownHandler.removeCallbacks(actualizarRelojesLotes);
        countdownHandler.postDelayed(actualizarRelojesLotes, 1000);
        return new int[]{abiertos, pujas};
    }

    private void actualizarRelojLote(TextView view, LoteEnVivo lote) {
        if (lote.vendido) {
            view.setText("LOTE FINALIZADO");
            view.setTextColor(Color.parseColor("#64748B"));
            return;
        }
        if (lote.segundosRestantes <= 0) {
            view.setText("CERRANDO LOTE…");
            view.setTextColor(Color.parseColor("#EF4444"));
            return;
        }
        int horas = lote.segundosRestantes / 3600;
        int minutos = (lote.segundosRestantes % 3600) / 60;
        int segundos = lote.segundosRestantes % 60;
        view.setText(horas > 0
                ? String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", horas, minutos, segundos)
                : String.format(java.util.Locale.getDefault(), "%02d:%02d", minutos, segundos));
        view.setTextColor(Color.parseColor(lote.segundosRestantes <= 30 ? "#DC2626" : "#A8872F"));
    }

    private static class LoteEnVivo {
        int segundosRestantes;
        int duracionMinutos;
        int cantidadPujas;
        boolean vendido;
        JSONArray ultimasPujas;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload catalog when coming back from bidding (bids may have changed)
        cargarCatalogo();
    }

    private void registrarConexionActiva() {
        if (userId <= 0 || auctionId <= 0) return;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/active-auction");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("auctionId", auctionId);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int statusCode = connection.getResponseCode();
                if (statusCode >= 200 && statusCode < 300) return;

                JSONObject json = new JSONObject(leerRespuesta(connection.getErrorStream()));
                String error = json.optString("error", "No se pudo ingresar a la subasta.");
                mainHandler.post(() -> FeedbackDialog.accionError(this,
                        "No podés ingresar a esta subasta",
                        error,
                        "VOLVER A SUBASTAS",
                        this::finish));
            } catch (Exception e) {
                mainHandler.post(() -> txtMensajeDetalle.setText(
                        "No pudimos validar tu ingreso. La sala seguirá reconectando; si persiste, volvé a Subastas."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void liberarConexionActiva() {
        if (userId <= 0 || auctionId <= 0) return;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/active-auction/release");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("auctionId", auctionId);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                connection.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    // ── LIVE EVENTS ──────────────────────────────────────────────────────────────

    private void escucharEventosEnVivo() {
        escuchandoEventos = true;
        eventExecutor.execute(() -> {
            while (escuchandoEventos) {
                try {
                    URL url = new URL(ApiConfig.BASE_URL + "/api/auctions/" + auctionId + "/events");
                    conexionEventos = (HttpURLConnection) url.openConnection();
                    conexionEventos.setRequestMethod("GET");
                    conexionEventos.setConnectTimeout(15000);
                    conexionEventos.setReadTimeout(0);
                    conexionEventos.setRequestProperty("Accept", "text/event-stream");
                    conexionEventos.setRequestProperty("Authorization", "Bearer " + token);

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conexionEventos.getInputStream())
                    );
                    String linea;
                    while (escuchandoEventos && (linea = reader.readLine()) != null) {
                        if (linea.startsWith("data: ")) {
                            JSONObject estado = new JSONObject(linea.substring(6));
                            mainHandler.post(() -> procesarEstadoVivo(estado));
                        }
                    }
                } catch (Exception e) {
                    if (escuchandoEventos) mainHandler.post(() -> {
                        if (txtItemVivo != null) txtItemVivo.setText("Reconectando estado en vivo...");
                    });
                } finally {
                    if (conexionEventos != null) conexionEventos.disconnect();
                }
                if (escuchandoEventos) {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
    }

    private void procesarEstadoVivo(JSONObject estado) {
        try {
            if (estado.optBoolean("finalizada", false)) {
                countdownHandler.removeCallbacks(countdownRunnable);
                txtItemVivo.setText("Subasta finalizada");
                txtTiempoRestante.setTextSize(44);
                txtTiempoRestante.setText("--:--");
                txtTiempoRestante.setTextColor(Color.parseColor("#64748B"));
                txtMejorOfertaVivo.setText("—");
                txtPujaMinimaVivo.setText("—");
                txtPujaMaximaVivo.setText("—");
                cargarCatalogo();
                return;
            }

            // Manejo de subastas pendientes (aún no comienzan)
            if (estado.optBoolean("pendiente", false)) {
                subastaAbiertaAhora = false;
                puedePujar = false;
                actualizarEstadoProgramacion();
                countdownHandler.removeCallbacks(countdownRunnable);
                String inicioPend = textoInicioSubasta(estado);
                txtItemVivo.setText(inicioPend.isEmpty()
                        ? "Subasta próxima a comenzar"
                        : "Comienza " + inicioPend);
                txtTiempoRestante.setTextSize(44);
                txtTiempoRestante.setText("--:--");
                txtTiempoRestante.setTextColor(Color.parseColor("#A8872F"));
                txtMejorOfertaVivo.setText("—");
                txtPujaMinimaVivo.setText("—");
                txtPujaMaximaVivo.setText("—");
                cargarCatalogo();
                return;
            }

            if (estado.has("error")) {
                txtItemVivo.setText(estado.optString("error", "Sin estado en vivo"));
                return;
            }

            if ("previa".equals(estado.optString("fase", ""))) {
                subastaAbiertaAhora = false;
                puedePujar = false;
                actualizarEstadoProgramacion();
                return;
            }

            if (!subastaAbiertaAhora) {
                subastaAbiertaAhora = true;
                puedePujar = cumpleRequisitosPuja;
                actualizarEstadoProgramacion();
                if (puedePujar && !conexionActivaRegistrada) {
                    registrarConexionActiva();
                    conexionActivaRegistrada = true;
                }
                cargarCatalogo();
            }

            JSONObject itemActual = estado.optJSONObject("itemActual");
            String nuevoItemId = itemActual != null ? String.valueOf(itemActual.optInt("itemId", 0)) : "";
            double mejorOferta = estado.optDouble("mejorOferta", 0);
            double pujaMinima = estado.optDouble("pujaMinima", 0);

            if (ultimaMejorOfertaViva >= 0
                    && Math.abs(ultimaMejorOfertaViva - mejorOferta) > 0.001) {
                cargarCatalogo();
            }
            ultimaMejorOfertaViva = mejorOferta;

            // El backend ya envía el tiempo en SEGUNDOS (no en minutos).
            String fase = estado.optString("fase", "");
            boolean tieneSegundos = !estado.isNull("segundosRestantes");
            int secs = tieneSegundos ? Math.max(estado.optInt("segundosRestantes", 0), 0) : 0;

            String pujaMaxima = estado.isNull("pujaMaxima")
                    ? "sin límite"
                    : "$" + String.format("%.2f", estado.optDouble("pujaMaxima", 0));

            // If item changed, reload catalog + check if user won the previous item
            if (!nuevoItemId.equals(itemIdVivo) && !nuevoItemId.equals("0")) {
                boolean itemAnteriorCerrado = !itemIdVivo.isEmpty() && !itemIdVivo.equals("0");
                itemIdVivo = nuevoItemId;
                tiempoAgotadoVerificado = false; // reset for new item
                cargarCatalogo();
                if (itemAnteriorCerrado) {
                    mainHandler.postDelayed(() -> verificarSiGano(), 2000);
                }
            }

            // Update live panel
            if (itemActual != null) {
                txtItemVivo.setText(itemActual.optString("descripcionCatalogo", "—"));
            }
            txtMejorOfertaVivo.setText(mejorOferta > 0 ? "$" + String.format("%.2f", mejorOferta) : "$—");
            txtPujaMinimaVivo.setText("$" + String.format("%.2f", pujaMinima));
            txtPujaMaximaVivo.setText(pujaMaxima);

            // Temporizador según la fase informada por el backend
            countdownHandler.removeCallbacks(countdownRunnable);

            if ("previa".equals(fase)) {
                // Aún no comienza: mostrar cuándo arranca + cuenta regresiva hasta el inicio
                String inicio = textoInicioSubasta(estado);
                if (!inicio.isEmpty()) {
                    txtItemVivo.setText("Comienza " + inicio);
                }
                segundosRestantes = secs;
                actualizarContadorUI();
                if (segundosRestantes > 0) countdownHandler.postDelayed(countdownRunnable, 1000);

            } else if ("esperando_oferta".equals(fase)) {
                // Ya comenzó pero sin ofertas: el contador de duracionItemMinutos
                // arranca recién con la primera puja
                segundosRestantes = 0;
                txtTiempoRestante.setTextSize(15);
                txtTiempoRestante.setText("Esperando 1ª oferta");
                txtTiempoRestante.setTextColor(Color.parseColor("#A8872F"));

            } else {
                // en_puja: corre la cuenta regresiva normal (MM:SS)
                segundosRestantes = secs;
                actualizarContadorUI();
                if (segundosRestantes > 0) countdownHandler.postDelayed(countdownRunnable, 1000);
            }

        } catch (Exception e) {
            txtItemVivo.setText("Error recibiendo evento.");
        }
    }

    private void actualizarContadorUI() {
        if (txtTiempoRestante == null) return;

        int totalMinutos = segundosRestantes / 60;
        int secs = segundosRestantes % 60;

        String textoTiempo;
        if (totalMinutos >= 60) {
            // Si hay más de una hora, mostrar en formato "Xd Yh Zm"
            int dias = totalMinutos / (60 * 24);
            int horas = (totalMinutos % (60 * 24)) / 60;
            int mins = totalMinutos % 60;

            if (dias > 0) {
                textoTiempo = String.format("%dd %dh %dm", dias, horas, mins);
            } else {
                textoTiempo = String.format("%dh %dm", horas, mins);
            }
        } else {
            // Para menos de una hora, mostrar MM:SS
            textoTiempo = String.format("%02d:%02d", totalMinutos, secs);
        }

        txtTiempoRestante.setText(textoTiempo);
        // MM:SS en grande; formatos largos (días/horas hasta el inicio) más chicos
        txtTiempoRestante.setTextSize(totalMinutos >= 60 ? 24 : 44);

        // Color changes as time runs low
        if (segundosRestantes <= 10) {
            txtTiempoRestante.setTextColor(Color.parseColor("#EF4444")); // red
        } else if (segundosRestantes <= 30) {
            txtTiempoRestante.setTextColor(Color.parseColor("#F59E0B")); // amber
        } else {
            txtTiempoRestante.setTextColor(Color.parseColor("#A8872F")); // gold
        }
    }

    // ── COLLAPSIBLE ────────────────────────────────────────────────────────────────

    private void toggleCollapseInfo() {
        infoExpanded = !infoExpanded;
        collapseBody.setVisibility(infoExpanded ? android.view.View.VISIBLE : android.view.View.GONE);
        txtChevron.setText(infoExpanded ? "▲" : "▼");
    }

    private void actualizarEstadoProgramacion() {
        if (txtLiveChip == null) return;
        if (subastaAbiertaAhora) {
            txtLiveChip.setText("● EN VIVO");
            txtLiveChip.setBackgroundResource(R.drawable.bg_live_chip);
            txtLiveChip.setTextColor(Color.parseColor("#166534"));
        } else {
            txtLiveChip.setText("PROGRAMADA");
            txtLiveChip.setBackgroundResource(R.drawable.bg_gold_chip);
            txtLiveChip.setTextColor(Color.parseColor("#071827"));
            String inicio = ((fechaInicioMostrada == null ? "" : fechaInicioMostrada) + " "
                    + (horaInicioMostrada == null ? "" : horaInicioMostrada)).trim();
            txtItemVivo.setText(inicio.isEmpty()
                    ? "Las pujas se habilitan cuando comience la subasta"
                    : "Las pujas comienzan el " + inicio + " (GMT-3)");
            txtTiempoRestante.setText("AÚN NO INICIÓ");
            txtTiempoRestante.setTextSize(18);
            txtTiempoRestante.setTextColor(Color.parseColor("#A8872F"));
        }
    }

    // ── TOP BAR DATA ──────────────────────────────────────────────────────────────

    private void cargarTopBarData(int userId) {
        executor.execute(() -> {
            // Load user status
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/users/" + userId);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + token);
                if (c.getResponseCode() == 200) {
                    JSONObject u = new JSONObject(leerRespuesta(c.getInputStream()));
                    String estado = u.optString("estado", "");
                    String admitido = u.optString("admitido", "no");
                    boolean habilitado = "activo".equals(estado) && "si".equals(admitido);
                    mainHandler.post(() -> {
                        txtTopBarStatus.setVisibility(android.view.View.VISIBLE);
                        if (habilitado) {
                            txtTopBarStatus.setText("HABILITADO");
                            txtTopBarStatus.setBackgroundResource(R.drawable.bg_success_chip);
                            txtTopBarStatus.setTextColor(android.graphics.Color.parseColor("#166534"));
                        } else {
                            txtTopBarStatus.setText("PENDIENTE");
                            txtTopBarStatus.setBackgroundResource(R.drawable.bg_danger_chip);
                            txtTopBarStatus.setTextColor(android.graphics.Color.parseColor("#991B1B"));
                        }
                    });
                }
                c.disconnect();
            } catch (Exception ignored) {}

            // Load first verified payment method
            try {
                URL url2 = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/payment-methods");
                HttpURLConnection c2 = (HttpURLConnection) url2.openConnection();
                c2.setRequestMethod("GET");
                c2.setRequestProperty("Accept", "application/json");
                c2.setRequestProperty("Authorization", "Bearer " + token);
                if (c2.getResponseCode() == 200) {
                    JSONArray methods = new JSONArray(leerRespuesta(c2.getInputStream()));
                    // Find first verified method
                    String display = "Sin medio de pago";
                    for (int i = 0; i < methods.length(); i++) {
                        JSONObject m = methods.getJSONObject(i);
                        if ("si".equals(m.optString("verificado", "no"))) {
                            String tipo = m.optString("tipo", "");
                            String entidad = m.optString("entidad", "");
                            String ref = m.optString("numeroReferencia", "");
                            String last = ref.length() > 4 ? "…" + ref.substring(ref.length() - 4) : ref;
                            display = ("tarjeta".equalsIgnoreCase(tipo) ? "💳 " : "📋 ") + entidad + " " + last;
                            break;
                        }
                    }
                    if ("Sin medio de pago".equals(display) && methods.length() > 0) {
                        JSONObject m = methods.getJSONObject(0);
                        String entidad = m.optString("entidad", "Pago");
                        display = "💳 " + entidad + " (pendiente)";
                    }
                    final String texto = display;
                    mainHandler.post(() -> txtTopBarPago.setText(texto));
                }
                c2.disconnect();
            } catch (Exception ignored) {
                mainHandler.post(() -> txtTopBarPago.setText("Sin datos"));
            }
        });
    }

    // ── AUCTION DETAIL ────────────────────────────────────────────────────────────

    private void cargarDetalleSubasta() {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/auctions/" + auctionId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();

                String respuesta = leerRespuesta(inputStream);
                if (statusCode == 200) {
                    JSONObject subasta = new JSONObject(respuesta);
                    mainHandler.post(() -> mostrarDetalleSubasta(subasta));
                } else {
                    JSONObject errorJson = new JSONObject(respuesta);
                    String error = errorJson.optString("error", "Error al cargar subasta");
                    mainHandler.post(() -> txtDatosSubasta.setText(error));
                }
            } catch (Exception e) {
                mainHandler.post(() -> txtDatosSubasta.setText("No se pudo conectar."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarDetalleSubasta(JSONObject subasta) {
        try {
            String fecha = formatearFecha(subasta.optString("fecha", "-"));
            String hora = formatearHora(subasta.optString("hora", "-"));
            String estado = subasta.optString("estado", "-");
            String ubicacion = subasta.optString("ubicacion", "-");
            String categoria = subasta.optString("categoria", "-");
            String moneda = formatearMoneda(subasta.optString("moneda", "-"));
            String subastador = subasta.optString("subastador", "-");
            String capacidad = subasta.optString("capacidadAsistentes", "-");
            categoriaSubasta = categoria;
            subastaAbiertaAhora = subasta.optBoolean("abiertaAhora", false);
            fechaInicioMostrada = fecha;
            horaInicioMostrada = hora;
            puedePujar = cumpleRequisitosPuja && subastaAbiertaAhora;
            actualizarEstadoProgramacion();

            // Single-line summary always visible in the header
            txtDatosResumen.setText(ubicacion + "  -  " + fecha + "  " + hora);

            // Full details in the expandable body
            txtDatosSubasta.setText(
                "Ubicación\n" + ubicacion + "\n\n" +
                "Fecha\n" + fecha + "\n\n" +
                "Hora de inicio\n" + hora + "\n\n" +
                "Estado\n" + estado + "\n\n" +
                "Moneda\n" + moneda + "\n\n" +
                "Categoría\n" + categoria + "\n\n" +
                "Subastador\n" + subastador + "\n\n" +
                "Capacidad\n" + capacidad + " asistentes\n\n" +
                "Tu acceso\n" + (!subastaAbiertaAhora
                        ? "Catálogo visible · las pujas se habilitan al comenzar"
                        : (puedePujar ? "Habilitado para pujar" : "Solo visualización"))
            );
        } catch (Exception e) {
            txtDatosResumen.setText("Error cargando datos.");
        }
    }

    // ── CATALOG ───────────────────────────────────────────────────────────────────

    private void cargarCatalogo() {
        mainHandler.post(() -> {
            txtMensajeDetalle.setText("Actualizando catálogo...");
        });

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/auctions/" + auctionId
                        + "/catalog?clientId=" + userId);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream() : connection.getErrorStream();

                String respuesta = leerRespuesta(inputStream);
                if (statusCode == 200) {
                    JSONArray catalogo = new JSONArray(respuesta);
                    mainHandler.post(() -> mostrarCatalogo(catalogo));
                } else {
                    JSONObject errorJson = new JSONObject(respuesta);
                    String error = errorJson.optString("error", "Error al cargar catálogo");
                    mainHandler.post(() -> txtMensajeDetalle.setText(error));
                }
            } catch (Exception e) {
                mainHandler.post(() -> txtMensajeDetalle.setText("No se pudo conectar."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void mostrarCatalogo(JSONArray catalogo) {
        contenedorCatalogo.removeAllViews();
        relojesLotes.clear();
        if (catalogo.length() == 0) {
            txtMensajeDetalle.setText("No hay ítems cargados para esta subasta.");
            return;
        }
        txtMensajeDetalle.setText(subastaAbiertaAhora
                ? catalogo.length() + " lotes en catálogo · cada reloj es independiente y se reinicia sólo con una puja de ese lote"
                : catalogo.length() + " lotes disponibles para consultar · las pujas comienzan con la apertura de la subasta");
        try {
            for (int i = 0; i < catalogo.length(); i++) {
                JSONObject item = catalogo.getJSONObject(i);
                int itemId = item.getInt("itemId");
                int productId = item.optInt("productoId", 0);
                String descripcionCatalogo = item.optString("descripcionCatalogo", "-");
                String descripcionCompleta = item.optString("descripcionCompleta", "-");
                String historia = item.optString("historia", "");
                String artistaDiseniador = item.optString("artistaDiseniador", "");
                double precioBase = item.optDouble("precioBase", 0);
                double comision = item.optDouble("comision", 0);
                double mejorOferta = item.optDouble("mejorOferta", precioBase);
                double miMejorOferta = item.optDouble("miMejorOferta", 0);
                String vendido = item.optString("vendido", "no");
                if (!estadosLotes.containsKey(itemId)) {
                    LoteEnVivo inicial = new LoteEnVivo();
                    inicial.vendido = "si".equals(vendido);
                    inicial.segundosRestantes = Math.max(item.optInt("segundosRestantes", 0), 0);
                    inicial.duracionMinutos = Math.max(item.optInt("duracionItemMinutos", 0), 0);
                    inicial.cantidadPujas = Math.max(item.optInt("cantidadPujas", 0), 0);
                    estadosLotes.put(itemId, inicial);
                }
                View card = crearCardCatalogo(itemId, productId, descripcionCatalogo,
                        descripcionCompleta, historia, artistaDiseniador,
                        precioBase, comision, mejorOferta, miMejorOferta, vendido);
                card.setTag(itemId); // used for scroll-to
                contenedorCatalogo.addView(card);
            }
        } catch (Exception e) {
            txtMensajeDetalle.setText("Error mostrando catálogo.");
        }
    }

    private View crearCardCatalogo(
            int itemId, int productId, String descripcionCatalogo,
            String descripcionCompleta, String historia, String artistaDiseniador,
            double precioBase, double comision, double mejorOferta, double miMejorOferta,
            String vendido
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(18));
        card.setBackgroundResource(R.drawable.bg_card_premium);
        card.setElevation(dp(4));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(22));
        card.setLayoutParams(cardParams);

        // VISUAL HERO DEL LOTE
        LinearLayout visual = new LinearLayout(this);
        visual.setOrientation(LinearLayout.VERTICAL);
        visual.setPadding(dp(18), dp(18), dp(18), dp(18));
        visual.setBackgroundResource(R.drawable.bg_visual_lot);
        LinearLayout.LayoutParams visualParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190));
        visual.setLayoutParams(visualParams);

        TextView status = new TextView(this);
        if (vendido.equals("si")) {
            status.setText("FINALIZADO - LOTE #" + itemId);
            status.setTextColor(Color.parseColor("#FECACA"));
        } else if (!subastaAbiertaAhora) {
            status.setText("PROGRAMADO · PUJAS DESDE " + fechaInicioMostrada + " " + horaInicioMostrada + " · LOTE #" + itemId);
            status.setTextColor(Color.parseColor("#FDE68A"));
        } else {
            status.setText("ABIERTO · PUJAS EN VIVO · LOTE #" + itemId);
            status.setTextColor(Color.parseColor("#86EFAC"));
        }
        status.setTextSize(11);
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setLetterSpacing(0.08f);

        TextView title = new TextView(this);
        title.setText(descripcionCatalogo);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(58), 0, 0);
        title.setLayoutParams(titleParams);

        TextView subtitle = new TextView(this);
        subtitle.setText("Pieza verificada - Autenticidad garantizada");
        subtitle.setTextColor(Color.parseColor("#E8EEF5"));
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(6), 0, 0);
        subtitle.setLayoutParams(subtitleParams);

        visual.addView(status);
        visual.addView(title);
        visual.addView(subtitle);

        ImageView fotoProducto = new ImageView(this);
        fotoProducto.setBackgroundColor(Color.parseColor("#F1F5F9"));
        fotoProducto.setScaleType(ImageView.ScaleType.CENTER);
        fotoProducto.setImageResource(R.drawable.ic_photo_placeholder);
        fotoProducto.setColorFilter(Color.parseColor("#CBD5E1"));
        LinearLayout.LayoutParams fotoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(300));
        fotoParams.setMargins(0, dp(14), 0, 0);
        fotoProducto.setLayoutParams(fotoParams);
        if (productId > 0) cargarFotoProducto(productId, fotoProducto);

        // DESCRIPCIÓN
        TextView descripcion = new TextView(this);
        descripcion.setText(descripcionCompleta);
        descripcion.setTextColor(Color.parseColor("#475569"));
        descripcion.setTextSize(14);
        descripcion.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(16), 0, 0);
        descripcion.setLayoutParams(descParams);

        // MÉTRICAS
        LinearLayout metricsRow = new LinearLayout(this);
        metricsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metricsRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metricsRowParams.setMargins(0, dp(16), 0, 0);
        metricsRow.setLayoutParams(metricsRowParams);

        TextView bidBox = new TextView(this);
        bidBox.setText("MEJOR OFERTA\n$" + mejorOferta);
        bidBox.setTextColor(Color.parseColor("#071827"));
        bidBox.setTextSize(13);
        bidBox.setTypeface(null, android.graphics.Typeface.BOLD);
        bidBox.setPadding(dp(14), dp(14), dp(14), dp(14));
        bidBox.setBackgroundResource(R.drawable.bg_metric_box);
        LinearLayout.LayoutParams bidParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        bidParams.setMargins(0, 0, dp(6), 0);
        bidBox.setLayoutParams(bidParams);

        TextView baseBox = new TextView(this);
        baseBox.setText("PRECIO BASE\n$" + precioBase);
        baseBox.setTextColor(Color.parseColor("#071827"));
        baseBox.setTextSize(13);
        baseBox.setTypeface(null, android.graphics.Typeface.BOLD);
        baseBox.setPadding(dp(14), dp(14), dp(14), dp(14));
        baseBox.setBackgroundResource(R.drawable.bg_metric_box);
        LinearLayout.LayoutParams baseParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        baseParams.setMargins(dp(6), 0, 0, 0);
        baseBox.setLayoutParams(baseParams);

        metricsRow.addView(bidBox);
        metricsRow.addView(baseBox);

        LoteEnVivo estadoVivo = estadosLotes.get(itemId);
        if (estadoVivo == null) {
            estadoVivo = new LoteEnVivo();
            estadoVivo.vendido = vendido.equals("si");
        }
        final int duracionLoteMinutos = estadoVivo.duracionMinutos;

        LinearLayout timerPanel = new LinearLayout(this);
        timerPanel.setOrientation(LinearLayout.VERTICAL);
        timerPanel.setPadding(dp(16), dp(13), dp(16), dp(13));
        timerPanel.setBackgroundResource(R.drawable.bg_metric_box);
        LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timerParams.setMargins(0, dp(12), 0, 0);
        timerPanel.setLayoutParams(timerParams);

        TextView timerLabel = new TextView(this);
        timerLabel.setText(subastaAbiertaAhora
                ? "TIEMPO RESTANTE DEL LOTE #" + itemId
                : "APERTURA DE PUJAS DEL LOTE #" + itemId);
        timerLabel.setTextColor(Color.parseColor("#475569"));
        timerLabel.setTextSize(10);
        timerLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        timerLabel.setLetterSpacing(0.07f);

        TextView timerValue = new TextView(this);
        timerValue.setTextSize(28);
        timerValue.setTypeface(null, android.graphics.Typeface.BOLD);
        timerValue.setContentDescription("Tiempo restante del lote " + itemId);
        if (subastaAbiertaAhora) actualizarRelojLote(timerValue, estadoVivo);
        else {
            timerValue.setText("AÚN NO INICIÓ");
            timerValue.setTextColor(Color.parseColor("#A8872F"));
        }
        relojesLotes.put(itemId, timerValue);

        TextView timerHelp = new TextView(this);
        String duracion = estadoVivo.duracionMinutos > 0
                ? estadoVivo.duracionMinutos + " min"
                : "la duración configurada";
        timerHelp.setText(subastaAbiertaAhora
                ? "El reloj parte de " + duracion + " al abrirse el lote y vuelve a ese valor después de cada puja válida."
                : "Podés revisar el catálogo ahora. El botón de puja se habilita automáticamente al comenzar la subasta.");
        timerHelp.setTextColor(Color.parseColor("#64748B"));
        timerHelp.setTextSize(11);
        timerHelp.setLineSpacing(dp(2), 1f);

        timerPanel.addView(timerLabel);
        timerPanel.addView(timerValue);
        timerPanel.addView(timerHelp);

        LinearLayout historyPanel = crearHistorialPujas(estadoVivo);

        TextView miParticipacion = new TextView(this);
        if (miMejorOferta > 0) {
            boolean liderando = miMejorOferta >= mejorOferta;
            miParticipacion.setText((liderando ? "VAS LIDERANDO" : "TU OFERTA FUE SUPERADA")
                    + "  ·  TU MEJOR: $" + String.format("%.2f", miMejorOferta));
            miParticipacion.setTextColor(Color.parseColor(liderando ? "#166534" : "#991B1B"));
            miParticipacion.setBackgroundResource(liderando
                    ? R.drawable.bg_success_chip : R.drawable.bg_danger_chip);
            miParticipacion.setVisibility(View.VISIBLE);
        } else {
            miParticipacion.setVisibility(View.GONE);
        }
        miParticipacion.setTextSize(11);
        miParticipacion.setTypeface(null, android.graphics.Typeface.BOLD);
        miParticipacion.setGravity(android.view.Gravity.CENTER);
        miParticipacion.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams participacionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        participacionParams.setMargins(0, dp(10), 0, 0);
        miParticipacion.setLayoutParams(participacionParams);

        // INFO PANEL
        LinearLayout infoPanel = new LinearLayout(this);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(16), dp(14), dp(16), dp(14));
        infoPanel.setBackgroundResource(R.drawable.bg_metric_box);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoParams.setMargins(0, dp(14), 0, 0);
        infoPanel.setLayoutParams(infoParams);

        TextView infoTitle = new TextView(this);
        infoTitle.setText("INFORMACIÓN DEL LOTE");
        infoTitle.setTextColor(Color.parseColor("#A8872F"));
        infoTitle.setTextSize(11);
        infoTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        infoTitle.setLetterSpacing(0.08f);

        String detalleInfo = "Comisión: $" + comision;
        if (artistaDiseniador != null && !artistaDiseniador.isEmpty() && !artistaDiseniador.equals("null"))
            detalleInfo += "\nArtista: " + artistaDiseniador;
        if (historia != null && !historia.isEmpty() && !historia.equals("null"))
            detalleInfo += "\nHistoria: " + historia;

        TextView infoText = new TextView(this);
        infoText.setText(detalleInfo);
        infoText.setTextColor(Color.parseColor("#475569"));
        infoText.setTextSize(13);
        infoText.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams infoTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoTextParams.setMargins(0, dp(8), 0, 0);
        infoText.setLayoutParams(infoTextParams);
        infoPanel.addView(infoTitle);
        infoPanel.addView(infoText);

        // SEGURIDAD
        TextView security = new TextView(this);
        security.setText("AUTENTICIDAD VERIFICADA - OPERACION SEGURA");
        security.setTextColor(Color.parseColor("#166534"));
        security.setTextSize(11);
        security.setTypeface(null, android.graphics.Typeface.BOLD);
        security.setGravity(android.view.Gravity.CENTER);
        security.setPadding(dp(12), dp(8), dp(12), dp(8));
        security.setBackgroundResource(R.drawable.bg_success_chip);
        LinearLayout.LayoutParams securityParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        securityParams.setMargins(0, dp(16), 0, 0);
        security.setLayoutParams(securityParams);

        Button btnPujar = new Button(this);
        if (subastaAbiertaAhora && puedePujar && !vendido.equals("si")) {
            btnPujar.setText("PUJAR POR ESTE LOTE");
            btnPujar.setBackgroundResource(R.drawable.bg_button_gold);
            btnPujar.setTextColor(Color.parseColor("#071827"));
            btnPujar.setOnClickListener(v -> {
                Intent intent = new Intent(AuctionDetailActivity.this, BidActivity.class);
                intent.putExtra("auctionId", auctionId);
                intent.putExtra("itemId", itemId);
                intent.putExtra("descripcion", descripcionCatalogo);
                intent.putExtra("productId", productId);
                intent.putExtra("precioBase", precioBase);
                intent.putExtra("mejorOferta", mejorOferta);
                intent.putExtra("categoria", categoriaSubasta);
                intent.putExtra("duracionMinutos", duracionLoteMinutos);
                startActivity(intent);
            });
        } else {
            btnPujar.setText(vendido.equals("si") ? "ADJUDICADO"
                    : (!subastaAbiertaAhora ? "PUJAS DISPONIBLES AL COMENZAR" : "SOLO VER"));
            btnPujar.setBackgroundResource(R.drawable.bg_button_outline);
            btnPujar.setTextColor(Color.parseColor(vendido.equals("si") ? "#64748B" : "#071827"));
            btnPujar.setEnabled(false);
        }
        btnPujar.setTextSize(12);
        btnPujar.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        btnParams.setMargins(0, dp(16), 0, 0);
        btnPujar.setLayoutParams(btnParams);

        card.addView(visual);
        card.addView(fotoProducto);
        card.addView(descripcion);
        card.addView(metricsRow);
        card.addView(timerPanel);
        card.addView(historyPanel);
        card.addView(miParticipacion);
        card.addView(infoPanel);
        card.addView(security);
        card.addView(btnPujar);

        return card;
    }

    private LinearLayout crearHistorialPujas(LoteEnVivo lote) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackgroundResource(R.drawable.bg_card_dark_premium);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(10), 0, 0);
        panel.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText("ÚLTIMAS OFERTAS");
        title.setTextColor(Color.parseColor("#A8872F"));
        title.setTextSize(10);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        panel.addView(title);

        JSONArray ofertas = lote.ultimasPujas;
        if (ofertas == null || ofertas.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText(subastaAbiertaAhora
                    ? "Todavía no hay pujas. La primera oferta puede ser la tuya."
                    : "La subasta todavía no comenzó. El historial se actualizará en tiempo real desde la apertura.");
            empty.setTextColor(Color.parseColor("#D7E3EF"));
            empty.setTextSize(12);
            empty.setPadding(0, dp(8), 0, 0);
            panel.addView(empty);
            return panel;
        }

        for (int i = 0; i < Math.min(3, ofertas.length()); i++) {
            JSONObject oferta = ofertas.optJSONObject(i);
            if (oferta == null) continue;
            TextView row = new TextView(this);
            row.setText("$" + String.format(java.util.Locale.getDefault(), "%,.2f", oferta.optDouble("importe", 0))
                    + "  ·  Postor #" + oferta.optInt("numeroPostor", 0)
                    + "  ·  " + formatearHoraPuja(oferta.optString("fechaHora", "")));
            row.setTextColor(i == 0 ? Color.WHITE : Color.parseColor("#CBD5E1"));
            row.setTextSize(i == 0 ? 13 : 12);
            if (i == 0) row.setTypeface(null, android.graphics.Typeface.BOLD);
            row.setPadding(0, dp(8), 0, 0);
            panel.addView(row);
        }
        return panel;
    }

    private String formatearHoraPuja(String raw) {
        if (raw == null || raw.isEmpty()) return "recién";
        int t = raw.indexOf('T');
        String hora = t >= 0 && raw.length() >= t + 6 ? raw.substring(t + 1, t + 6) : raw;
        return hora + " hs";
    }

    // ── SCROLL TO ACTIVE ITEM ────────────────────────────────────────────────────

    private void scrollToItemActivo() {
        if (itemIdVivo.isEmpty() || scrollViewAuction == null || contenedorCatalogo == null) return;
        try {
            int targetId = Integer.parseInt(itemIdVivo);
            for (int i = 0; i < contenedorCatalogo.getChildCount(); i++) {
                View card = contenedorCatalogo.getChildAt(i);
                if (card.getTag() instanceof Integer && (Integer) card.getTag() == targetId) {
                    // offset = top of contenedorCatalogo inside scroll + top of card inside contenedor
                    final int scrollY = contenedorCatalogo.getTop() + card.getTop();
                    scrollViewAuction.post(() ->
                        scrollViewAuction.smoothScrollTo(0, scrollY));
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    // ── PHOTO LOADING ────────────────────────────────────────────────────────────

    private void cargarFotoProducto(int productId, ImageView imageView) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/products/" + productId + "/photos");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                if (connection.getResponseCode() != 200) {
                    mainHandler.post(() -> setPhotoPlaceholder(imageView));
                    return;
                }
                String respuesta = leerRespuesta(connection.getInputStream());
                org.json.JSONArray fotos = new org.json.JSONArray(respuesta);
                if (fotos.length() == 0) {
                    mainHandler.post(() -> setPhotoPlaceholder(imageView));
                    return;
                }
                String fotoBase64 = fotos.getJSONObject(0).optString("fotoBase64", "");
                if (fotoBase64.isEmpty()) {
                    mainHandler.post(() -> setPhotoPlaceholder(imageView));
                    return;
                }
                byte[] bytes = Base64.decode(fotoBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    mainHandler.post(() -> setPhotoPlaceholder(imageView));
                    return;
                }
                mainHandler.post(() -> {
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imageView.setColorFilter(null);
                    imageView.setImageBitmap(bitmap);
                });
            } catch (Exception e) {
                mainHandler.post(() -> setPhotoPlaceholder(imageView));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void setPhotoPlaceholder(ImageView imageView) {
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setBackgroundColor(Color.parseColor("#F1F5F9"));
        imageView.setImageResource(R.drawable.ic_photo_placeholder);
        imageView.setColorFilter(Color.parseColor("#CBD5E1"));
    }

    // ── WINNER CHECK ─────────────────────────────────────────────────────────────

    private void precargarComprasConocidas() {
        int userId = getSharedPreferences("sesion", MODE_PRIVATE).getInt("userId", 0);
        executor.execute(() -> {
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/purchases");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + token);
                if (c.getResponseCode() == 200) {
                    JSONArray compras = new JSONArray(leerRespuesta(c.getInputStream()));
                    for (int i = 0; i < compras.length(); i++) {
                        comprasConocidas.add(compras.getJSONObject(i).optInt("ventaId", 0));
                    }
                }
                c.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private void verificarSiGano() {
        if (verificandoGanador) return;
        verificandoGanador = true;
        int userId = getSharedPreferences("sesion", MODE_PRIVATE).getInt("userId", 0);
        executor.execute(() -> {
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/purchases");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + token);
                if (c.getResponseCode() == 200) {
                    JSONArray compras = new JSONArray(leerRespuesta(c.getInputStream()));
                    boolean hayNueva = false;
                    for (int i = 0; i < compras.length(); i++) {
                        int ventaId = compras.getJSONObject(i).optInt("ventaId", 0);
                        if (!comprasConocidas.contains(ventaId)) {
                            comprasConocidas.add(ventaId);
                            hayNueva = true;
                        }
                    }
                    if (hayNueva) mainHandler.post(() -> mostrarModalGanador());
                }
                c.disconnect();
            } catch (Exception ignored) {
            } finally {
                verificandoGanador = false;
            }
        });
    }

    private void mostrarModalGanador() {
        FeedbackDialog.accionExitosa(this,
                "Ganaste el lote",
                "El lote fue adjudicado a tu nombre. Elegí un medio de pago verificado y completá la operación dentro de las próximas 72 horas para evitar la multa reglamentaria del 10%.",
                "VER COMPRAS Y PAGAR",
                () -> {
                    Intent intent = new Intent(AuctionDetailActivity.this, PurchasesActivity.class);
                    startActivity(intent);
                });
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────────



    private String leerRespuesta(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder respuesta = new StringBuilder();
        String linea;
        while ((linea = reader.readLine()) != null) respuesta.append(linea);
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

    // Construye un texto legible del inicio de la subasta a partir de los
    // campos fechaInicio ("yyyy-MM-dd") y horaInicio ("HH:mm") del evento.
    private String textoInicioSubasta(JSONObject estado) {
        if (estado == null) return "";
        String fechaRaw = estado.optString("fechaInicio", "");
        String hora = estado.optString("horaInicio", "");
        String fecha = formatearFecha(fechaRaw);
        boolean hayFecha = fecha != null && !fecha.isEmpty() && !fecha.equals("-");
        boolean hayHora = hora != null && !hora.isEmpty() && !hora.equals("null");
        if (!hayFecha && !hayHora) return "";
        if (hayFecha && hayHora) return fecha + " a las " + hora + " hs";
        return hayFecha ? fecha : hora + " hs";
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
