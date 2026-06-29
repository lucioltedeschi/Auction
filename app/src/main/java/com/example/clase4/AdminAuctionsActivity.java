package com.example.clase4;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminAuctionsActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText locationField;
    private EditText dateField;
    private EditText timeField;
    private EditText capacityField;
    private EditText durationField;
    private Spinner stateSpinner;
    private Spinner categorySpinner;
    private Spinner currencySpinner;
    private Spinner depositSpinner;
    private Spinner securitySpinner;
    private TextView formTitle;
    private TextView messageView;
    private LinearLayout container;
    private Button saveButton;
    private Button cancelButton;
    private String token;
    private Integer editingId;

    private final String[] states = {"programada", "abierta", "en_curso", "cerrada", "cancelada"};
    private final String[] categories = {"comun", "especial", "plata", "oro", "platino"};
    private final String[] currencies = {"pesos", "dolares"};
    private final String[] binaryOptions = {"si", "no"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_auctions);
        SystemBars.configure(this, "#F3F0E8", true, "#F3F0E8", true);

        token = getSharedPreferences("sesion", MODE_PRIVATE).getString("token", "");
        locationField = findViewById(R.id.edtAdminAuctionLocation);
        dateField = findViewById(R.id.edtAdminAuctionDate);
        timeField = findViewById(R.id.edtAdminAuctionTime);
        capacityField = findViewById(R.id.edtAdminAuctionCapacity);
        durationField = findViewById(R.id.edtAdminAuctionDuration);
        stateSpinner = findViewById(R.id.spAdminAuctionState);
        categorySpinner = findViewById(R.id.spAdminAuctionCategory);
        currencySpinner = findViewById(R.id.spAdminAuctionCurrency);
        depositSpinner = findViewById(R.id.spAdminAuctionDeposit);
        securitySpinner = findViewById(R.id.spAdminAuctionSecurity);
        formTitle = findViewById(R.id.txtAdminAuctionFormTitle);
        messageView = findViewById(R.id.txtAdminAuctionsMessage);
        container = findViewById(R.id.containerAdminAuctions);
        saveButton = findViewById(R.id.btnSaveAdminAuction);
        cancelButton = findViewById(R.id.btnCancelAdminAuctionEdit);

        configureSpinner(stateSpinner, states);
        configureSpinner(categorySpinner, categories);
        configureSpinner(currencySpinner, currencies);
        configureSpinner(depositSpinner, binaryOptions);
        configureSpinner(securitySpinner, binaryOptions);

        findViewById(R.id.btnBackAdminAuctions).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefreshAdminAuctions).setOnClickListener(v -> loadAuctions());
        saveButton.setOnClickListener(v -> saveAuction());
        cancelButton.setOnClickListener(v -> resetForm());
        resetForm();
        loadAuctions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (container != null) loadAuctions();
    }

    private void configureSpinner(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
    }

    private void resetForm() {
        editingId = null;
        formTitle.setText("NUEVA SUBASTA");
        saveButton.setText("CREAR SUBASTA Y CATÁLOGO");
        cancelButton.setVisibility(View.GONE);
        locationField.setText("");
        SimpleDateFormat argentinaDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        argentinaDate.setTimeZone(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        dateField.setText(argentinaDate.format(new Date()));
        timeField.setText("20:00");
        capacityField.setText("120");
        durationField.setText("180");
        stateSpinner.setSelection(0);
        categorySpinner.setSelection(0);
        currencySpinner.setSelection(0);
        depositSpinner.setSelection(0);
        securitySpinner.setSelection(0);
    }

    private void saveAuction() {
        String location = locationField.getText().toString().trim();
        String date = dateField.getText().toString().trim();
        String time = timeField.getText().toString().trim();
        if (location.isEmpty() || !date.matches("\\d{4}-\\d{2}-\\d{2}") || !time.matches("\\d{2}:\\d{2}.*")) {
            FeedbackDialog.error(this, "Completá una ubicación, fecha AAAA-MM-DD y hora HH:MM válidas.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("ubicacion", location);
            body.put("fecha", date);
            body.put("hora", time.substring(0, 5));
            body.put("estado", stateSpinner.getSelectedItem().toString());
            body.put("categoria", categorySpinner.getSelectedItem().toString());
            body.put("moneda", currencySpinner.getSelectedItem().toString());
            body.put("tieneDeposito", depositSpinner.getSelectedItem().toString());
            body.put("seguridadPropia", securitySpinner.getSelectedItem().toString());
            body.put("capacidadAsistentes", parsePositive(capacityField, 120));
            body.put("duracionItemMinutos", parsePositive(durationField, 180));
        } catch (Exception ignored) {
        }

        saveButton.setEnabled(false);
        saveButton.setText("GUARDANDO…");
        String path = editingId == null ? "/api/admin/auctions" : "/api/admin/auctions/" + editingId;
        String method = editingId == null ? "POST" : "PATCH";
        requestJson(path, method, body, () -> {
            FeedbackDialog.ok(this, editingId == null
                    ? "La subasta y su catálogo fueron creados."
                    : "La subasta fue actualizada.");
            resetForm();
            loadAuctions();
        });
    }

    private int parsePositive(EditText field, int fallback) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            return value > 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void loadAuctions() {
        if (token == null || token.isEmpty()) {
            FeedbackDialog.error(this, "Falta la sesión administrativa.");
            return;
        }
        messageView.setText("Cargando subastas…");
        container.removeAllViews();
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = openConnection("/api/admin/auctions", "GET");
                int code = connection.getResponseCode();
                String text = read(connection, code);
                if (code < 200 || code >= 300) throw new IllegalStateException(extractError(text));
                JSONArray auctions = new JSONArray(text);
                mainHandler.post(() -> showAuctions(auctions));
            } catch (Exception error) {
                mainHandler.post(() -> messageView.setText("No se pudieron cargar: " + error.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showAuctions(JSONArray auctions) {
        container.removeAllViews();
        messageView.setText(auctions.length() + " subastas registradas");
        for (int i = 0; i < auctions.length(); i += 1) {
            JSONObject auction = auctions.optJSONObject(i);
            if (auction != null) container.addView(createAuctionCard(auction));
        }
    }

    private View createAuctionCard(JSONObject auction) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(R.drawable.bg_card_white);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(cardParams);

        TextView title = label("#" + auction.optInt("id") + " · " + auction.optString("ubicacion"), 16, true, "#071827");
        card.addView(title);
        String time = formatTime(auction.optString("hora", ""));
        String details = formatDate(auction.optString("fecha")) + " · " + time
                + "\n" + auction.optString("estado").toUpperCase(Locale.ROOT)
                + " · " + auction.optString("categoria") + " · " + auction.optString("moneda")
                + "\n" + auction.optInt("cantidadLotes") + " lotes · "
                + auction.optInt("cantidadVentas") + " ventas · "
                + auction.optInt("duracionItemMinutos") + " min/lote";
        TextView detail = label(details, 13, false, "#475569");
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.setMargins(0, dp(7), 0, 0);
        detail.setLayoutParams(detailParams);
        card.addView(detail);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsParams.setMargins(0, dp(10), 0, 0);
        actions.setLayoutParams(actionsParams);

        Button edit = actionButton("Editar", R.drawable.bg_button_gold, "#071827", R.drawable.ic_action_edit);
        Button delete = actionButton("Eliminar", R.drawable.bg_button_outline, "#991B1B", R.drawable.ic_action_delete);
        edit.setOnClickListener(v -> editAuction(auction));
        delete.setOnClickListener(v -> confirmDelete(auction));
        actions.addView(edit);
        actions.addView(delete);
        card.addView(actions);
        return card;
    }

    private Button actionButton(String text, int background, String textColor, int icon) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setTextColor(Color.parseColor(textColor));
        button.setBackgroundResource(background);
        button.setAllCaps(false);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setGravity(android.view.Gravity.CENTER);
        button.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(6));
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setStateListAnimator(null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView label(String text, int size, boolean bold, String color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private void editAuction(JSONObject auction) {
        editingId = auction.optInt("id");
        formTitle.setText("EDITANDO SUBASTA #" + editingId);
        saveButton.setText("GUARDAR CAMBIOS");
        cancelButton.setVisibility(View.VISIBLE);
        locationField.setText(auction.optString("ubicacion"));
        dateField.setText(rawDate(auction.optString("fecha")));
        timeField.setText(formatTime(auction.optString("hora", "20:00")));
        capacityField.setText(String.valueOf(auction.optInt("capacidadAsistentes", 120)));
        durationField.setText(String.valueOf(auction.optInt("duracionItemMinutos", 180)));
        select(stateSpinner, states, auction.optString("estado"));
        select(categorySpinner, categories, auction.optString("categoria"));
        select(currencySpinner, currencies, auction.optString("moneda"));
        select(depositSpinner, binaryOptions, auction.optString("tieneDeposito"));
        select(securitySpinner, binaryOptions, auction.optString("seguridadPropia"));
        locationField.requestFocus();
    }

    private void select(Spinner spinner, String[] values, String value) {
        for (int i = 0; i < values.length; i += 1) {
            if (values[i].equals(value)) spinner.setSelection(i);
        }
    }

    private void confirmDelete(JSONObject auction) {
        int id = auction.optInt("id");
        FeedbackDialog.confirmar(this, "Eliminar subasta vacía",
                "Se eliminará #" + id + " · " + auction.optString("ubicacion")
                        + ". Si tiene lotes, asistentes, ventas o multas, el sistema la conservará.",
                () -> requestJson("/api/admin/auctions/" + id, "DELETE", null, () -> {
                    FeedbackDialog.ok(this, "La subasta vacía fue eliminada.");
                    if (editingId != null && editingId == id) resetForm();
                    loadAuctions();
                }));
    }

    private void requestJson(String path, String method, JSONObject body, Runnable success) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String transportMethod = ("PATCH".equals(method) || "DELETE".equals(method)) ? "POST" : method;
                connection = openConnection(path, transportMethod);
                if (!transportMethod.equals(method)) connection.setRequestProperty("X-HTTP-Method-Override", method);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    try (OutputStream output = connection.getOutputStream()) {
                        output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                } else if (!"GET".equals(transportMethod)) {
                    connection.setDoOutput(true);
                    connection.getOutputStream().close();
                }
                int code = connection.getResponseCode();
                String text = read(connection, code);
                if (code >= 200 && code < 300) mainHandler.post(success);
                else mainHandler.post(() -> FeedbackDialog.error(this, extractError(text)));
            } catch (Exception error) {
                mainHandler.post(() -> FeedbackDialog.error(this, "No se pudo conectar con el servidor."));
            } finally {
                if (connection != null) connection.disconnect();
                mainHandler.post(() -> {
                    saveButton.setEnabled(true);
                    saveButton.setText(editingId == null ? "CREAR SUBASTA Y CATÁLOGO" : "GUARDAR CAMBIOS");
                });
            }
        });
    }

    private HttpURLConnection openConnection(String path, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(ApiConfig.BASE_URL + path).openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        return connection;
    }

    private String read(HttpURLConnection connection, int code) throws Exception {
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        }
    }

    private String extractError(String text) {
        try {
            return new JSONObject(text).optString("error", "No se pudo completar la operación.");
        } catch (Exception ignored) {
            return "No se pudo completar la operación.";
        }
    }

    private String rawDate(String value) {
        if (value == null) return "";
        return value.length() >= 10 ? value.substring(0, 10) : value;
    }

    private String formatDate(String value) {
        String raw = rawDate(value);
        if (raw.length() != 10) return raw;
        return raw.substring(8, 10) + "/" + raw.substring(5, 7) + "/" + raw.substring(0, 4);
    }

    private String formatTime(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String raw = value.trim();
        int separator = raw.indexOf('T');
        if (separator >= 0 && raw.length() >= separator + 6) {
            return raw.substring(separator + 1, separator + 6);
        }
        if (raw.matches("\\d{2}:\\d{2}.*")) return raw.substring(0, 5);
        return raw;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
