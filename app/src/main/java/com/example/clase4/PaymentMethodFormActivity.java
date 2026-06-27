package com.example.clase4;

import android.content.SharedPreferences;
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

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PaymentMethodFormActivity extends AppCompatActivity {

    private TextView txtDescripcionFlujoPago;
    private TextView txtTituloFlujoPago;
    private TextView txtMensajeFlujoPago;
    private Spinner spMonedaFlujoPago;
    private Spinner spExtranjeraFlujoPago;
    private EditText edtEntidadFlujoPago;
    private EditText edtReferenciaFlujoPago;
    private EditText edtMontoChequeFlujoPago;
    private LinearLayout panelDatosTarjeta;
    private EditText edtNumeroTarjeta;
    private EditText edtNombreTitularTarjeta;
    private EditText edtApellidoTitularTarjeta;
    private EditText edtVencimientoTarjeta;
    private EditText edtCodigoSeguridadTarjeta;
    private Button btnGuardarFlujoPago;

    private int userId;
    private String token;
    private String tipo;
    private int medioPagoId;
    private boolean esEdicion;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_method_form);

        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#071827"));
        getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#F3F0E8"));
        SystemBars.configure(this, "#071827", false, "#F3F0E8", true);

        txtTituloFlujoPago = findViewById(R.id.txtTituloFlujoPago);
        txtDescripcionFlujoPago = findViewById(R.id.txtDescripcionFlujoPago);
        txtMensajeFlujoPago = findViewById(R.id.txtMensajeFlujoPago);
        spMonedaFlujoPago = findViewById(R.id.spMonedaFlujoPago);
        spExtranjeraFlujoPago = findViewById(R.id.spExtranjeraFlujoPago);
        edtEntidadFlujoPago = findViewById(R.id.edtEntidadFlujoPago);
        edtReferenciaFlujoPago = findViewById(R.id.edtReferenciaFlujoPago);
        edtMontoChequeFlujoPago = findViewById(R.id.edtMontoChequeFlujoPago);
        panelDatosTarjeta = findViewById(R.id.panelDatosTarjeta);
        edtNumeroTarjeta = findViewById(R.id.edtNumeroTarjeta);
        edtNombreTitularTarjeta = findViewById(R.id.edtNombreTitularTarjeta);
        edtApellidoTitularTarjeta = findViewById(R.id.edtApellidoTitularTarjeta);
        edtVencimientoTarjeta = findViewById(R.id.edtVencimientoTarjeta);
        edtCodigoSeguridadTarjeta = findViewById(R.id.edtCodigoSeguridadTarjeta);
        btnGuardarFlujoPago = findViewById(R.id.btnGuardarFlujoPago);

        SharedPreferences prefs = getSharedPreferences("sesion", MODE_PRIVATE);
        userId = prefs.getInt("userId", 0);
        token = prefs.getString("token", "");

        tipo = getIntent().getStringExtra("tipo");
        if (tipo == null) tipo = "cuenta_bancaria";
        medioPagoId = getIntent().getIntExtra("medioPagoId", 0);
        esEdicion = medioPagoId > 0;

        configurarSpinners();
        configurarUI();

        findViewById(R.id.btnVolverFlujoPago).setOnClickListener(v -> finish());
        btnGuardarFlujoPago.setOnClickListener(v -> validarYGuardar());
    }

    private void configurarSpinners() {
        spMonedaFlujoPago.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Pesos", "Dolares"}));
        spExtranjeraFlujoPago.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"Nacional", "Extranjera"}));
    }

    private void configurarUI() {
        if (esEdicion) {
            // Edit mode: pre-fill available fields
            String entidad = getIntent().getStringExtra("entidad");
            String ref = getIntent().getStringExtra("numeroReferencia");
            String moneda = getIntent().getStringExtra("moneda");
            String extranjera = getIntent().getStringExtra("esExtranjera");
            double montoCheque = getIntent().getDoubleExtra("montoCheque", 0);

            if (entidad != null) edtEntidadFlujoPago.setText(entidad);
            if ("dolares".equals(moneda)) spMonedaFlujoPago.setSelection(1);
            if ("si".equals(extranjera)) spExtranjeraFlujoPago.setSelection(1);

            if ("tarjeta_credito".equals(tipo)) {
                txtTituloFlujoPago.setText("EDITAR TARJETA");
                txtDescripcionFlujoPago.setText("Podés actualizar el vencimiento y el código de seguridad de la tarjeta.");
                // Show only vencimiento and CVV — hide the rest
                panelDatosTarjeta.setVisibility(View.VISIBLE);
                edtNumeroTarjeta.setVisibility(View.GONE);
                edtNombreTitularTarjeta.setVisibility(View.GONE);
                edtApellidoTitularTarjeta.setVisibility(View.GONE);
                edtReferenciaFlujoPago.setVisibility(View.GONE);
                edtMontoChequeFlujoPago.setVisibility(View.GONE);
                // Pre-fill vencimiento from stored reference string
                if (ref != null) {
                    String venc = extraerVencimiento(ref);
                    if (venc != null) edtVencimientoTarjeta.setText(venc);
                }
                btnGuardarFlujoPago.setText("ACTUALIZAR");
            } else if ("cheque_certificado".equals(tipo)) {
                txtTituloFlujoPago.setText("EDITAR CHEQUE");
                txtDescripcionFlujoPago.setText("Modificá los datos del cheque certificado.");
                edtMontoChequeFlujoPago.setVisibility(View.VISIBLE);
                panelDatosTarjeta.setVisibility(View.GONE);
                edtReferenciaFlujoPago.setHint("Número de cheque");
                if (ref != null) edtReferenciaFlujoPago.setText(ref);
                if (montoCheque > 0) edtMontoChequeFlujoPago.setText(String.valueOf(montoCheque));
                btnGuardarFlujoPago.setText("ACTUALIZAR");
            } else {
                // cuenta_bancaria edit
                txtTituloFlujoPago.setText("EDITAR CUENTA");
                txtDescripcionFlujoPago.setText("Modificá los datos de la cuenta bancaria.");
                edtReferenciaFlujoPago.setHint("CBU, CVU o número de cuenta");
                if (ref != null) edtReferenciaFlujoPago.setText(ref);
                edtMontoChequeFlujoPago.setVisibility(View.GONE);
                panelDatosTarjeta.setVisibility(View.GONE);
                btnGuardarFlujoPago.setText("ACTUALIZAR");
            }
        } else {
            // New method
            if ("tarjeta_credito".equals(tipo)) {
                txtTituloFlujoPago.setText("TARJETA DE CRÉDITO");
                txtDescripcionFlujoPago.setText("Cargá los datos de la tarjeta. La empresa la verifica antes de habilitarla.");
                edtEntidadFlujoPago.setHint("Emisor, por ejemplo Visa o Mastercard");
                edtReferenciaFlujoPago.setVisibility(View.GONE);
                edtMontoChequeFlujoPago.setVisibility(View.GONE);
                panelDatosTarjeta.setVisibility(View.VISIBLE);
            } else if ("cheque_certificado".equals(tipo)) {
                txtTituloFlujoPago.setText("CHEQUE CERTIFICADO");
                txtDescripcionFlujoPago.setText("Informá banco, número de cheque y monto reservado.");
                edtEntidadFlujoPago.setHint("Banco certificante");
                edtReferenciaFlujoPago.setHint("Número de cheque");
                edtMontoChequeFlujoPago.setVisibility(View.VISIBLE);
                panelDatosTarjeta.setVisibility(View.GONE);
            } else {
                txtTituloFlujoPago.setText("CUENTA BANCARIA");
                txtDescripcionFlujoPago.setText("Cargá banco y CBU/CVU o número de cuenta.");
                edtEntidadFlujoPago.setHint("Banco");
                edtReferenciaFlujoPago.setHint("CBU, CVU o número de cuenta");
                edtMontoChequeFlujoPago.setVisibility(View.GONE);
                panelDatosTarjeta.setVisibility(View.GONE);
            }
        }
    }

    /** Extracts "MM/AA" from the stored compound reference string. */
    private String extraerVencimiento(String ref) {
        int idx = ref.indexOf("| Vence ");
        if (idx < 0) return null;
        String after = ref.substring(idx + 8).trim();
        int end = after.indexOf(" ");
        return end > 0 ? after.substring(0, end) : after;
    }

    private void validarYGuardar() {
        String entidad = edtEntidadFlujoPago.getText().toString().trim();
        String montoCheque = edtMontoChequeFlujoPago.getText().toString().trim();

        if (entidad.isEmpty()) {
            error("Completá la entidad emisora o banco.");
            return;
        }

        String referencia;

        if ("tarjeta_credito".equals(tipo)) {
            referencia = esEdicion ? armarReferenciaEdicionTarjeta() : armarReferenciaNuevaTarjeta();
            if (referencia == null) return;
        } else {
            referencia = edtReferenciaFlujoPago.getText().toString().trim();
            if (referencia.isEmpty()) {
                error("Completá la referencia del método de pago.");
                return;
            }
        }

        if ("cheque_certificado".equals(tipo) && montoCheque.isEmpty()) {
            error("Informá el monto certificado del cheque.");
            return;
        }

        btnGuardarFlujoPago.setEnabled(false);
        btnGuardarFlujoPago.setText("Guardando...");
        txtMensajeFlujoPago.setText("");
        guardar(entidad, referencia, montoCheque);
    }

    private String armarReferenciaNuevaTarjeta() {
        String numero = edtNumeroTarjeta.getText().toString().trim().replace(" ", "");
        String nombre = edtNombreTitularTarjeta.getText().toString().trim();
        String apellido = edtApellidoTitularTarjeta.getText().toString().trim();
        String venc = edtVencimientoTarjeta.getText().toString().trim();
        String cvv = edtCodigoSeguridadTarjeta.getText().toString().trim();

        if (!numero.matches("\\d{13,19}")) { error("Ingresá un número de tarjeta válido (13-19 dígitos)."); return null; }
        if (nombre.isEmpty() || apellido.isEmpty()) { error("Completá nombre y apellido del titular."); return null; }
        if (!venc.matches("\\d{2}/\\d{2}")) { error("Ingresá el vencimiento con formato MM/AA."); return null; }
        if (!cvv.matches("\\d{3,4}")) { error("Ingresá el código de seguridad (3 o 4 dígitos)."); return null; }

        String ref = "Tarjeta " + numero + " | Titular " + nombre + " " + apellido
                + " | Vence " + venc + " | CVV " + cvv;
        return ref.length() > 150 ? ref.substring(0, 150) : ref;
    }

    private String armarReferenciaEdicionTarjeta() {
        String venc = edtVencimientoTarjeta.getText().toString().trim();
        String cvv = edtCodigoSeguridadTarjeta.getText().toString().trim();

        if (!venc.matches("\\d{2}/\\d{2}")) { error("Ingresá el vencimiento con formato MM/AA."); return null; }
        if (!cvv.matches("\\d{3,4}")) { error("Ingresá el código de seguridad (3 o 4 dígitos)."); return null; }

        // Rebuild: keep everything before "| Vence" from stored reference
        String refExistente = getIntent().getStringExtra("numeroReferencia");
        if (refExistente == null) refExistente = "";
        String base = refExistente;
        int idx = refExistente.indexOf("| Vence ");
        if (idx > 0) base = refExistente.substring(0, idx).trim();

        String ref = base + " | Vence " + venc + " | CVV " + cvv;
        return ref.length() > 150 ? ref.substring(0, 150) : ref;
    }

    private void guardar(String entidad, String referencia, String montoCheque) {
        final String moneda = spMonedaFlujoPago.getSelectedItemPosition() == 1 ? "dolares" : "pesos";
        final String esExtranjera = spExtranjeraFlujoPago.getSelectedItemPosition() == 1 ? "si" : "no";

        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = esEdicion
                        ? ApiConfig.BASE_URL + "/api/clients/" + userId + "/payment-methods/" + medioPagoId
                        : ApiConfig.BASE_URL + "/api/clients/" + userId + "/payment-methods";
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("POST");
                if (esEdicion) conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("tipo", tipo);
                body.put("entidad", entidad);
                body.put("numeroReferencia", referencia);
                body.put("moneda", moneda);
                body.put("esExtranjera", esExtranjera);
                if ("cheque_certificado".equals(tipo) && !montoCheque.isEmpty())
                    body.put("montoCheque", Double.parseDouble(montoCheque));

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                JSONObject json = new JSONObject(leerRespuesta(is));

                mainHandler.post(() -> {
                    btnGuardarFlujoPago.setEnabled(true);
                    btnGuardarFlujoPago.setText(esEdicion ? "ACTUALIZAR" : "GUARDAR");
                    if (code == 200 || code == 201) {
                        String msg = json.optString("mensaje", esEdicion ? "Actualizado correctamente." : "Registrado. Pendiente de verificación.");
                        FeedbackDialog.ok(this, msg);
                    } else {
                        error(json.optString("error", "No se pudo guardar."));
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnGuardarFlujoPago.setEnabled(true);
                    btnGuardarFlujoPago.setText(esEdicion ? "ACTUALIZAR" : "GUARDAR");
                    error("No se pudo conectar con el servidor.");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void error(String msg) {
        txtMensajeFlujoPago.setText(msg);
    }

    private String leerRespuesta(InputStream is) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l);
        return sb.toString();
    }
}
