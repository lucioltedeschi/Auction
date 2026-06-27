package com.example.clase4;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BidActivity extends AppCompatActivity {

    private TextView txtTituloPuja;
    private TextView txtDatosItem;
    private TextView txtRangoPuja;
    private TextView txtMensajePuja;
    private TextView txtTopBarPagoBid;
    private ImageView imgLotePuja;
    private EditText edtImportePuja;
    private Button btnEnviarPuja;

    private int userId;
    private String token;
    private int auctionId;
    private int itemId;
    private int productId;

    private String descripcion;
    private String categoriaSubasta;
    private double precioBase;
    private double mejorOferta;
    private double pujaMinima;
    private Double pujaMaxima;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bid);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#071827"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#F3F0E8"));
        SystemBars.configure(this, "#071827", false, "#F3F0E8", true);

        BottomNavHelper.configurar(this);

        txtTituloPuja = findViewById(R.id.txtTituloPuja);
        txtDatosItem = findViewById(R.id.txtDatosItem);
        txtRangoPuja = findViewById(R.id.txtRangoPuja);
        txtMensajePuja = findViewById(R.id.txtMensajePuja);
        txtTopBarPagoBid = findViewById(R.id.txtTopBarPagoBid);
        imgLotePuja = findViewById(R.id.imgLotePuja);
        edtImportePuja = findViewById(R.id.edtImportePuja);
        btnEnviarPuja = findViewById(R.id.btnEnviarPuja);

        // Back button
        findViewById(R.id.btnBackBid).setOnClickListener(v -> finish());

        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = preferences.getInt("userId", 0);
        token = preferences.getString("token", "");

        auctionId = getIntent().getIntExtra("auctionId", 0);
        itemId = getIntent().getIntExtra("itemId", 0);
        productId = getIntent().getIntExtra("productId", 0);
        descripcion = getIntent().getStringExtra("descripcion");
        categoriaSubasta = getIntent().getStringExtra("categoria");
        if (categoriaSubasta == null) categoriaSubasta = "";
        precioBase = getIntent().getDoubleExtra("precioBase", 0);
        mejorOferta = getIntent().getDoubleExtra("mejorOferta", 0);

        mostrarDatosItem();
        cargarFotoProducto();
        cargarMetodoPago();

        btnEnviarPuja.setOnClickListener(v -> validarYEnviarPuja());
    }

    // ── ITEM DATA ─────────────────────────────────────────────────────────────────

    private void mostrarDatosItem() {
        double valorReferencia = mejorOferta > 0 ? mejorOferta : precioBase;
        boolean categoriaPremium = "oro".equals(categoriaSubasta) || "platino".equals(categoriaSubasta);

        pujaMinima = categoriaPremium
                ? valorReferencia + 0.01
                : valorReferencia + (precioBase * 0.01);

        pujaMaxima = categoriaPremium
                ? null
                : valorReferencia + (precioBase * 0.20);

        txtTituloPuja.setText(descripcion != null ? descripcion : "Lote #" + itemId);

        txtDatosItem.setText(
                "Precio base: $" + String.format("%.2f", precioBase) + "\n" +
                "Mejor oferta actual: $" + String.format("%.2f", mejorOferta) + "\n" +
                "Categoría: " + categoriaSubasta
        );

        if (pujaMaxima == null) {
            txtRangoPuja.setText(
                    "Mínimo: $" + String.format("%.2f", pujaMinima) + "\n" +
                    "Máximo: sin límite (categoría " + categoriaSubasta + ")"
            );
        } else {
            txtRangoPuja.setText(
                    "Mínimo: $" + String.format("%.2f", pujaMinima) + "\n" +
                    "Máximo: $" + String.format("%.2f", pujaMaxima)
            );
        }

        edtImportePuja.setHint("Mínimo: $" + String.format("%.2f", pujaMinima));
    }

    // ── VALIDATION & SUBMIT ───────────────────────────────────────────────────────

    private void validarYEnviarPuja() {
        String importeTexto = edtImportePuja.getText().toString().trim();

        if (importeTexto.isEmpty()) {
            mostrarValidacionPuja("Ingresa un importe para pujar.");
            return;
        }

        double importe;
        try {
            importe = Double.parseDouble(importeTexto);
        } catch (Exception e) {
            mostrarValidacionPuja("El importe ingresado no es valido.");
            return;
        }

        if (importe < pujaMinima) {
            mostrarValidacionPuja("La puja debe ser al menos $" + String.format("%.2f", pujaMinima));
            return;
        }

        if (pujaMaxima != null && importe > pujaMaxima) {
            mostrarValidacionPuja("La puja no puede superar $" + String.format("%.2f", pujaMaxima));
            return;
        }

        txtMensajePuja.setText("");
        btnEnviarPuja.setEnabled(false);
        btnEnviarPuja.setText("Enviando...");
        enviarPuja(importe);
    }

    private void enviarPuja(double importe) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/auctions/" + auctionId + "/items/" + itemId + "/bids");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("clienteId", userId);
                body.put("importe", importe);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int statusCode = connection.getResponseCode();
                InputStream inputStream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String respuesta = leerRespuesta(inputStream);
                JSONObject json = new JSONObject(respuesta);

                if (statusCode == 201 || statusCode == 200) {
                    String mensaje = json.optString("mensaje", "Puja registrada correctamente");
                    mainHandler.post(() -> mostrarModalExito(mensaje));
                } else {
                    String error = json.optString("error", "No se pudo registrar la puja");
                    mainHandler.post(() -> {
                        btnEnviarPuja.setEnabled(true);
                        btnEnviarPuja.setText("Enviar puja");
                        mostrarModalError(error);
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnEnviarPuja.setEnabled(true);
                    btnEnviarPuja.setText("Enviar puja");
                    mostrarModalError("No se pudo conectar con el servidor.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    // ── MODALES ───────────────────────────────────────────────────────────────────

    private void mostrarValidacionPuja(String mensaje) {
        txtMensajePuja.setText(mensaje);
        FeedbackDialog.error(this, mensaje);
    }

    private void mostrarModalExito(String mensaje) {
        View view = construirVistaModal("OK", "#16A34A", "Puja recibida", mensaje + "\n\nVolve al catalogo para seguir el estado en vivo del lote.");
        new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton("VOLVER AL CATALOGO", (d, w) -> {
                    finish();
                })
                .show();
    }

    private void mostrarModalError(String error) {
        String titulo;
        String lower = error.toLowerCase();
        if (lower.contains("oferta") || lower.contains("supera") || lower.contains("mayor")
                || lower.contains("menor") || lower.contains("importe") || lower.contains("puja")) {
            titulo = "Puja superada";
        } else if (lower.contains("habilitado") || lower.contains("verificado") || lower.contains("medio")
                || lower.contains("conectado") || lower.contains("subasta activa")) {
            titulo = "Sin acceso";
        } else {
            titulo = "No se pudo registrar";
        }

        View view = construirVistaModal("!", "#DC2626", titulo, error);
        new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .setPositiveButton("Entendido", null)
                .show();
    }

    private View construirVistaModal(String icono, String iconColor, String titulo, String mensaje) {
        float density = getResources().getDisplayMetrics().density;
        int pad = (int)(28 * density);
        int marginTop = (int)(10 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, (int)(16 * density));
        root.setGravity(Gravity.CENTER);

        TextView icon = new TextView(this);
        icon.setText(icono);
        icon.setTextSize(60);
        icon.setTextColor(Color.parseColor(iconColor));
        icon.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText(titulo);
        title.setTextSize(22);
        title.setTextColor(Color.parseColor("#071827"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMargins(0, marginTop, 0, 0);
        title.setLayoutParams(tp);

        TextView msg = new TextView(this);
        msg.setText(mensaje);
        msg.setTextSize(14);
        msg.setTextColor(Color.parseColor("#475569"));
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(4, 1.0f);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mp.setMargins(0, (int)(8 * density), 0, 0);
        msg.setLayoutParams(mp);

        root.addView(icon);
        root.addView(title);
        root.addView(msg);
        return root;
    }

    // ── FOTO & PAGO ───────────────────────────────────────────────────────────────

    private void cargarFotoProducto() {
        if (productId <= 0) return;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/products/" + productId + "/photos");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);

                if (connection.getResponseCode() != 200) {
                    mainHandler.post(this::setFotoPlaceholder);
                    return;
                }

                String respuesta = leerRespuesta(connection.getInputStream());
                JSONArray fotos = new JSONArray(respuesta);
                if (fotos.length() == 0) {
                    mainHandler.post(this::setFotoPlaceholder);
                    return;
                }

                String fotoBase64 = fotos.getJSONObject(0).optString("fotoBase64", "");
                if (fotoBase64.isEmpty()) {
                    mainHandler.post(this::setFotoPlaceholder);
                    return;
                }
                byte[] bytes = Base64.decode(fotoBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    mainHandler.post(this::setFotoPlaceholder);
                    return;
                }

                mainHandler.post(() -> {
                    imgLotePuja.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    imgLotePuja.setColorFilter(null);
                    imgLotePuja.setImageBitmap(bitmap);
                });
            } catch (Exception ignored) {
                mainHandler.post(this::setFotoPlaceholder);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void setFotoPlaceholder() {
        imgLotePuja.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        imgLotePuja.setBackgroundColor(android.graphics.Color.parseColor("#F1F5F9"));
        imgLotePuja.setImageResource(R.drawable.ic_photo_placeholder);
        imgLotePuja.setColorFilter(android.graphics.Color.parseColor("#CBD5E1"));
    }

    private void cargarMetodoPago() {
        executor.execute(() -> {
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/clients/" + userId + "/payment-methods");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + token);
                if (c.getResponseCode() == 200) {
                    JSONArray methods = new JSONArray(leerRespuesta(c.getInputStream()));
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
                    final String texto = display;
                    mainHandler.post(() -> txtTopBarPagoBid.setText(texto));
                }
                c.disconnect();
            } catch (Exception ignored) {}
        });
    }

    private String leerRespuesta(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String linea;
        while ((linea = reader.readLine()) != null) sb.append(linea);
        return sb.toString();
    }
}
