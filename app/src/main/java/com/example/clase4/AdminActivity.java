package com.example.clase4;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdminActivity extends AppCompatActivity {

    private TextView txtMensajeAdmin;
    private TextView txtAdminPendientes;
    private TextView txtAdminUsuarioSeleccionado;
    private TextView txtAdminMedioSeleccionado;
    private LinearLayout contenedorUsuariosPendientes;
    private LinearLayout contenedorMediosPendientes;
    private LinearLayout contenedorProductosPendientes;
    private ImageView imgAdminDniFrente;
    private ImageView imgAdminDniDorso;
    private EditText edtAdminUsuarioId;
    private EditText edtAdminMedioPagoId;
    private EditText edtAdminProductoId;
    private EditText edtAdminMotivoRechazo;
    private EditText edtAdminSubastaId;
    private EditText edtAdminPrecioBase;
    private EditText edtAdminComision;
    private EditText edtAdminItemId;
    private EditText edtAdminClienteMulta;
    private EditText edtAdminMontoMulta;
    private Spinner spAdminCategoria;
    private String token;

    private static final int REQ_REVISAR_USUARIO = 101;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        txtMensajeAdmin = findViewById(R.id.txtMensajeAdmin);
        txtAdminPendientes = findViewById(R.id.txtAdminPendientes);
        txtAdminUsuarioSeleccionado = findViewById(R.id.txtAdminUsuarioSeleccionado);
        txtAdminMedioSeleccionado = findViewById(R.id.txtAdminMedioSeleccionado);
        contenedorUsuariosPendientes = findViewById(R.id.contenedorUsuariosPendientes);
        contenedorMediosPendientes = findViewById(R.id.contenedorMediosPendientes);
        contenedorProductosPendientes = findViewById(R.id.contenedorProductosPendientes);
        imgAdminDniFrente = findViewById(R.id.imgAdminDniFrente);
        imgAdminDniDorso = findViewById(R.id.imgAdminDniDorso);
        edtAdminUsuarioId = findViewById(R.id.edtAdminUsuarioId);
        edtAdminMedioPagoId = findViewById(R.id.edtAdminMedioPagoId);
        edtAdminProductoId = findViewById(R.id.edtAdminProductoId);
        edtAdminMotivoRechazo = findViewById(R.id.edtAdminMotivoRechazo);
        edtAdminSubastaId = findViewById(R.id.edtAdminSubastaId);
        edtAdminPrecioBase = findViewById(R.id.edtAdminPrecioBase);
        edtAdminComision = findViewById(R.id.edtAdminComision);
        edtAdminItemId = findViewById(R.id.edtAdminItemId);
        edtAdminClienteMulta = findViewById(R.id.edtAdminClienteMulta);
        edtAdminMontoMulta = findViewById(R.id.edtAdminMontoMulta);
        spAdminCategoria = findViewById(R.id.spAdminCategoria);
        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        token = preferences.getString("token", "");

        if (token == null || token.trim().isEmpty()) {
            mostrarError("Falta token Bearer de empleado.");
            txtAdminPendientes.setText("No se pueden cargar pendientes. Ingresa nuevamente con el admin 20000111 / 1234.");
        }

        spAdminCategoria.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"comun", "especial", "plata", "oro", "platino"}
        ));

        findViewById(R.id.btnAdminActualizarPendientes).setOnClickListener(v -> cargarPendientes());
        findViewById(R.id.cardAdminVerificarUsuario).setOnClickListener(v -> mostrarDialogVerificarUsuario());
        findViewById(R.id.cardAdminMediosPago).setOnClickListener(v -> mostrarDialogMediosPago());
        findViewById(R.id.cardAdminConsignaciones).setOnClickListener(v -> mostrarDialogConsignaciones());
        findViewById(R.id.cardAdminCatalogo).setOnClickListener(v -> mostrarDialogCatalogo());
        findViewById(R.id.cardAdminCerrarItem).setOnClickListener(v -> mostrarDialogCerrarItem());
        findViewById(R.id.cardAdminMultas).setOnClickListener(v -> mostrarDialogMultas());
        findViewById(R.id.btnVolverAdmin).setOnClickListener(v -> cerrarSesionAdmin());

        cargarPendientes();
    }

    private void cargarPendientes() {
        if (token == null || token.trim().isEmpty()) {
            txtAdminPendientes.setText("No se pueden cargar pendientes: falta token de admin. Ingresa nuevamente con 20000111 / 1234.");
            return;
        }

        txtAdminPendientes.setText("Cargando pendientes internos...");

        executor.execute(() -> {
            try {
                JSONArray usuarios = leerArrayAutorizado("/api/admin/users/pending");
                JSONArray medios = leerArrayAutorizado("/api/admin/payment-methods/pending");
                JSONArray productos = leerArrayAutorizado("/api/admin/products/pending");

                StringBuilder builder = new StringBuilder();
                builder.append("Usuarios pendientes\n");
                agregarResumen(builder, usuarios, "id", "documento", "categoria");
                builder.append("\nMedios de pago pendientes\n");
                agregarResumen(builder, medios, "id", "clienteNombre", "tipo");
                builder.append("\nConsignaciones pendientes\n");
                agregarResumen(builder, productos, "id", "duenioNombre", "descripcionCatalogo");

                mainHandler.post(() -> {
                    txtAdminPendientes.setText(builder.toString());
                    mostrarUsuariosPendientes(usuarios);
                    mostrarMediosPendientes(medios);
                    mostrarProductosPendientes(productos);
                });
            } catch (Exception e) {
                mainHandler.post(() -> txtAdminPendientes.setText(
                        "No se pudieron cargar los pendientes internos.\n" + e.getMessage()
                ));
            }
        });
    }

    private void agregarResumen(StringBuilder builder, JSONArray array, String idKey, String primaryKey, String secondaryKey) throws Exception {
        if (array.length() == 0) {
            builder.append("Sin pendientes.\n");
            return;
        }

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            builder
                    .append("#")
                    .append(item.optInt(idKey, 0))
                    .append(" - ")
                    .append(item.optString(primaryKey, "-"))
                    .append(" - ")
                    .append(item.optString(secondaryKey, "-"))
                    .append("\n");
        }
    }

    private void mostrarUsuariosPendientes(JSONArray usuarios) {
        contenedorUsuariosPendientes.removeAllViews();

        if (usuarios.length() == 0) {
            TextView empty = crearTexto("No hay usuarios pendientes para revisar.", "#64748B", 14, false);
            contenedorUsuariosPendientes.addView(empty);
            return;
        }

        for (int i = 0; i < usuarios.length(); i++) {
            try {
                JSONObject usuario = usuarios.getJSONObject(i);
                contenedorUsuariosPendientes.addView(crearCardUsuarioPendiente(usuario));
            } catch (Exception ignored) {
            }
        }
    }

    private View crearCardUsuarioPendiente(JSONObject usuario) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.bg_metric_box);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        String documento = usuario.optString("documento", "-");
        String nombre = usuario.optString("nombre", "-") + " " + usuario.optString("apellido", "-");
        String categoria = usuario.optString("categoria", "-");
        String admitido = usuario.optString("admitido", "-");

        TextView titulo = crearTexto("DNI " + documento, "#071827", 17, true);
        TextView detalle = crearTexto(nombre + "\nCategoria: " + categoria + " - Admitido: " + admitido, "#475569", 13, false);

        card.addView(titulo);
        card.addView(detalle);
        card.setOnClickListener(v -> abrirRevisionUsuario(usuario));

        return card;
    }

    private void abrirRevisionUsuario(JSONObject usuario) {
        Intent intent = new Intent(AdminActivity.this, UserReviewActivity.class);
        intent.putExtra("userId", usuario.optInt("id", 0));
        intent.putExtra("documento", usuario.optString("documento", ""));
        intent.putExtra("nombre", usuario.optString("nombre", ""));
        intent.putExtra("apellido", usuario.optString("apellido", ""));
        intent.putExtra("email", usuario.optString("email", ""));
        intent.putExtra("direccion", usuario.optString("direccion", ""));
        intent.putExtra("categoria", usuario.optString("categoria", ""));
        intent.putExtra("admitido", usuario.optString("admitido", ""));
        startActivityForResult(intent, REQ_REVISAR_USUARIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_REVISAR_USUARIO && resultCode == RESULT_OK) {
            // El usuario fue aprobado o rechazado en la pantalla de revisión
            cargarPendientes();
        }
    }

    private void seleccionarUsuarioPendiente(JSONObject usuario) {
        int id = usuario.optInt("id", 0);
        edtAdminUsuarioId.setText(String.valueOf(id));

        txtAdminUsuarioSeleccionado.setText(
                "Usuario seleccionado\n" +
                        "ID: " + id + "\n" +
                        "Documento: " + usuario.optString("documento", "-") + "\n" +
                        "Nombre: " + usuario.optString("nombre", "-") + " " + usuario.optString("apellido", "-") + "\n" +
                        "Email: " + usuario.optString("email", "-") + "\n" +
                        "Direccion: " + usuario.optString("direccion", "-") + "\n" +
                        "Categoria actual: " + usuario.optString("categoria", "-") + "\n" +
                        "Admitido: " + usuario.optString("admitido", "-")
        );

        cargarImagenBase64(usuario.optString("fotoDniFrenteBase64", ""), imgAdminDniFrente);
        cargarImagenBase64(usuario.optString("fotoDniDorsoBase64", ""), imgAdminDniDorso);
        mostrarInfo("Revisando DNI " + usuario.optString("documento", "-"));
    }

    private void mostrarMediosPendientes(JSONArray medios) {
        contenedorMediosPendientes.removeAllViews();

        if (medios.length() == 0) {
            contenedorMediosPendientes.addView(crearTexto("No hay medios de pago pendientes.", "#64748B", 14, false));
            return;
        }

        for (int i = 0; i < medios.length(); i++) {
            try {
                JSONObject medio = medios.getJSONObject(i);
                contenedorMediosPendientes.addView(crearCardMedioPendiente(medio));
            } catch (Exception ignored) {
            }
        }
    }

    private View crearCardMedioPendiente(JSONObject medio) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.bg_metric_box);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        String id = String.valueOf(medio.optInt("id", 0));
        String cliente = medio.optString("clienteNombre", "-");
        String tipo = medio.optString("tipo", "-");
        String entidad = medio.optString("entidad", "-");

        card.addView(crearTexto("Medio #" + id + " - " + formatearTipo(tipo), "#071827", 17, true));
        card.addView(crearTexto(cliente + "\n" + entidad, "#475569", 13, false));
        card.setOnClickListener(v -> seleccionarMedioPendiente(medio));

        return card;
    }

    private void mostrarProductosPendientes(JSONArray productos) {
        if (contenedorProductosPendientes == null) return;
        contenedorProductosPendientes.removeAllViews();

        if (productos.length() == 0) {
            contenedorProductosPendientes.addView(crearTexto("No hay consignaciones pendientes.", "#64748B", 14, false));
            return;
        }

        for (int i = 0; i < productos.length(); i++) {
            try {
                JSONObject producto = productos.getJSONObject(i);
                contenedorProductosPendientes.addView(crearCardProductoPendiente(producto));
            } catch (Exception ignored) {
            }
        }
    }

    private View crearCardProductoPendiente(JSONObject producto) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.bg_metric_box);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView foto = new ImageView(this);
        LinearLayout.LayoutParams fotoParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        fotoParams.setMargins(0, 0, dp(12), 0);
        foto.setLayoutParams(fotoParams);
        foto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cargarImagenBase64(producto.optString("fotoPrincipalBase64", ""), foto);

        LinearLayout datos = new LinearLayout(this);
        datos.setOrientation(LinearLayout.VERTICAL);
        datos.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        int id = producto.optInt("id", 0);
        String titulo = producto.optString("descripcionCatalogo", "-");
        String duenio = producto.optString("duenioNombre", "-");
        String estado = producto.optString("estadoDescripcion", producto.optString("estadoAprobacion", "-"));
        int fotos = producto.optInt("fotos", 0);

        datos.addView(crearTexto("#" + id + " - " + titulo, "#071827", 16, true));
        datos.addView(crearTexto("Consignante: " + duenio + "\nEstado: " + estado + "\nFotos: " + fotos, "#475569", 13, false));

        row.addView(foto);
        row.addView(datos);
        card.addView(row);

        String descripcion = producto.optString("descripcionCompleta", "");
        if (!descripcion.isEmpty() && !"null".equals(descripcion)) {
            card.addView(crearTexto(descripcion, "#334155", 13, false));
        }

        String historia = producto.optString("historia", "");
        if (!historia.isEmpty() && !"null".equals(historia)) {
            card.addView(crearTexto("Historia/procedencia\n" + historia, "#334155", 13, false));
        }

        double sugerido = producto.optDouble("precioBaseSugerido", 0);
        if (sugerido > 0) {
            card.addView(crearTexto("Sugerido por cliente\nPrecio base: $" + String.format("%.2f", sugerido), "#0F766E", 13, true));
        }

        double precio = producto.optDouble("precioBasePropuesto", 0);
        double comision = producto.optDouble("comisionPropuesta", 0);
        if (precio > 0 || comision > 0) {
            String propuesta = "Propuesta";
            if (precio > 0) propuesta += "\nPrecio base: $" + String.format("%.2f", precio);
            if (comision > 0) propuesta += "\nComision: $" + String.format("%.2f", comision);
            card.addView(crearTexto(propuesta, "#071827", 13, true));
        }

        card.setOnClickListener(v -> {
            edtAdminProductoId.setText(String.valueOf(id));
            if (precio > 0) edtAdminPrecioBase.setText(String.valueOf(precio));
            else if (sugerido > 0) edtAdminPrecioBase.setText(String.valueOf(sugerido));
            if (comision > 0) edtAdminComision.setText(String.valueOf(comision));
            mostrarInfo("Consignacion seleccionada #" + id);
            mostrarDialogConsignaciones();
        });

        return card;
    }

    private void seleccionarMedioPendiente(JSONObject medio) {
        int id = medio.optInt("id", 0);
        edtAdminMedioPagoId.setText(String.valueOf(id));

        String detalle =
                "Medio seleccionado\n" +
                        "ID: " + id + "\n" +
                        "Cliente: " + medio.optString("clienteNombre", "-") + "\n" +
                        "Tipo: " + formatearTipo(medio.optString("tipo", "-")) + "\n" +
                        "Entidad: " + medio.optString("entidad", "-") + "\n" +
                        "Datos: " + medio.optString("numeroReferencia", "-") + "\n" +
                        "Moneda: " + medio.optString("moneda", "-") + "\n" +
                        "Extranjera: " + medio.optString("esExtranjera", "-");

        if ("cheque_certificado".equals(medio.optString("tipo", ""))) {
            detalle += "\nMonto cheque: " + medio.optDouble("montoCheque", 0);
            detalle += "\nMonto disponible: " + medio.optDouble("montoDisponible", 0);
        }

        txtAdminMedioSeleccionado.setText(detalle);
        mostrarInfo("Revisando medio de pago #" + id);
    }

    private String formatearTipo(String tipo) {
        if ("tarjeta_credito".equals(tipo)) return "Tarjeta de credito";
        if ("cuenta_bancaria".equals(tipo)) return "Cuenta bancaria";
        if ("cheque_certificado".equals(tipo)) return "Cheque certificado";
        return tipo;
    }

    private void cargarImagenBase64(String base64, ImageView imageView) {
        try {
            if (base64 == null || base64.trim().isEmpty() || "null".equals(base64)) {
                imageView.setImageResource(R.drawable.logo);
                return;
            }

            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                imageView.setImageResource(R.drawable.logo);
                return;
            }

            imageView.setImageBitmap(bitmap);
        } catch (Exception e) {
            imageView.setImageResource(R.drawable.logo);
        }
    }

    private TextView crearTexto(String text, String color, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.parseColor(color));
        view.setTextSize(size);
        view.setLineSpacing(dp(3), 1.0f);
        if (bold) {
            view.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private JSONArray leerArrayAutorizado(String path) throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(ApiConfig.BASE_URL + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);

            int statusCode = connection.getResponseCode();
            InputStream inputStream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String respuesta = leerRespuesta(inputStream);
            if (statusCode >= 200 && statusCode < 300) {
                return new JSONArray(respuesta);
            }

            String detalle;
            try {
                JSONObject error = new JSONObject(respuesta);
                detalle = error.optString("error", respuesta);
            } catch (Exception e) {
                detalle = respuesta;
            }
            throw new IllegalStateException(path + " -> HTTP " + statusCode + ": " + detalle);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void verificarUsuario(String admitido) {
        String userId = edtAdminUsuarioId.getText().toString().trim();
        if (userId.isEmpty()) {
            mostrarError("Ingresá ID de usuario.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("admitido", admitido);
            body.put("categoria", spAdminCategoria.getSelectedItem().toString());
        } catch (Exception ignored) {
        }

        enviarJson("/api/admin/users/" + userId + "/verification", "PATCH", body);
    }

    private void verificarMedioPago() {
        String medioPagoId = edtAdminMedioPagoId.getText().toString().trim();
        if (medioPagoId.isEmpty()) {
            mostrarError("Ingresá ID de medio de pago.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("verificado", "si");
        } catch (Exception ignored) {
        }

        enviarJson("/api/admin/payment-methods/" + medioPagoId + "/verification", "PATCH", body);
    }

    private void rechazarMedioPago() {
        String medioPagoId = edtAdminMedioPagoId.getText().toString().trim();
        if (medioPagoId.isEmpty()) {
            mostrarError("Selecciona o ingresa un ID de medio de pago.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("verificado", "no");
            body.put("rechazado", "si");
            body.put("motivoRechazo", "Rechazado por validacion administrativa");
        } catch (Exception ignored) {
        }

        enviarJson("/api/admin/payment-methods/" + medioPagoId + "/verification", "PATCH", body);
    }

    private void revisarProducto(String estado) {
        String productId = edtAdminProductoId.getText().toString().trim();
        if (productId.isEmpty()) {
            mostrarError("Ingresá ID de producto.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("estadoAprobacion", estado);
            body.put("motivoRechazo", edtAdminMotivoRechazo.getText().toString().trim());
            if ("propuesta_enviada".equals(estado)) {
                String precioBase = edtAdminPrecioBase.getText().toString().trim();
                String comision = edtAdminComision.getText().toString().trim();
                if (precioBase.isEmpty() || comision.isEmpty()) {
                    mostrarError("Para enviar propuesta ingresa precio base y comision.");
                    return;
                }
                double precio = Double.parseDouble(precioBase);
                double comisionValor = Double.parseDouble(comision);
                if (precio <= 0 || comisionValor <= 0) {
                    mostrarError("Precio base y comision deben ser mayores a cero.");
                    return;
                }
                body.put("precioBase", precio);
                body.put("comision", comisionValor);
                body.put("condicionesPropuestas", "Condiciones informadas por la empresa y sujetas a aceptacion del usuario.");
            }
            body.put("ubicacionDeposito", "Depósito asignado desde panel interno");
            body.put("seguro", "Poliza base contratada por la empresa segun valor base propuesto.");
        } catch (Exception e) {
            mostrarError("Precio base y comision deben ser numericos.");
            return;
        }

        enviarJson("/api/admin/products/" + productId + "/review", "PATCH", body);
    }

    private void asignarProducto() {
        String auctionId = edtAdminSubastaId.getText().toString().trim();
        String productId = edtAdminProductoId.getText().toString().trim();
        String precioBase = edtAdminPrecioBase.getText().toString().trim();
        String comision = edtAdminComision.getText().toString().trim();

        if (auctionId.isEmpty() || productId.isEmpty() || precioBase.isEmpty() || comision.isEmpty()) {
            mostrarError("Ingresá subasta, producto, precio base y comisión.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            double precio = Double.parseDouble(precioBase);
            double comisionValor = Double.parseDouble(comision);
            if (precio <= 0 || comisionValor <= 0) {
                mostrarError("Precio base y comision deben ser mayores a cero.");
                return;
            }
            body.put("productId", Integer.parseInt(productId));
            body.put("precioBase", precio);
            body.put("comision", comisionValor);
        } catch (Exception e) {
            mostrarError("Precio base y comision deben ser numericos.");
            return;
        }

        enviarJson("/api/admin/auctions/" + auctionId + "/items", "POST", body);
    }

    private void cerrarItem() {
        String auctionId = edtAdminSubastaId.getText().toString().trim();
        String itemId = edtAdminItemId.getText().toString().trim();

        if (auctionId.isEmpty() || itemId.isEmpty()) {
            mostrarError("Ingresá subasta e item.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("costoEnvio", 0);
            body.put("retiroPersonal", "no");
        } catch (Exception ignored) {
        }

        enviarJson("/api/admin/auctions/" + auctionId + "/items/" + itemId + "/close", "POST", body);
    }

    private void crearMulta() {
        String clienteId = edtAdminClienteMulta.getText().toString().trim();
        String subastaId = edtAdminSubastaId.getText().toString().trim();
        String monto = edtAdminMontoMulta.getText().toString().trim();

        if (clienteId.isEmpty() || subastaId.isEmpty() || monto.isEmpty()) {
            mostrarError("Ingresá cliente, subasta y monto de multa.");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("clienteId", Integer.parseInt(clienteId));
            body.put("subastaId", Integer.parseInt(subastaId));
            body.put("monto", Double.parseDouble(monto));
        } catch (Exception e) {
            mostrarError("Cliente, subasta y monto deben ser numéricos.");
            return;
        }

        enviarJson("/api/admin/fines", "POST", body);
    }

    private void enviarJson(String path, String method, JSONObject body) {
        if (token == null || token.trim().isEmpty()) {
            mostrarError("Falta token de admin. Ingresa nuevamente con 20000111 / 1234.");
            return;
        }

        mostrarInfo("Enviando operacion interna...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(ApiConfig.BASE_URL + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod(method.equals("PATCH") ? "POST" : method);
                if (method.equals("PATCH")) {
                    connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                }
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setDoOutput(true);

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
                boolean ok = statusCode >= 200 && statusCode < 300;
                String mensaje = json.optString(
                        ok ? "mensaje" : "error",
                        ok ? "Operacion realizada." : "No se pudo completar la operacion."
                );

                mainHandler.post(() -> {
                    if (ok) {
                        mostrarOk(mensaje);
                        FeedbackDialog.ok(AdminActivity.this, mensaje);
                        cargarPendientes();
                    } else {
                        mostrarError(mensaje);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String mensaje = "No se pudo conectar con el servidor: " + e.getMessage();
                    mostrarError(mensaje);
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private LinearLayout buildDialogContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(8), dp(20), dp(8));
        return container;
    }

    private EditText buildInput(String hint, int inputType) {
        EditText edt = new EditText(this);
        edt.setHint(hint);
        edt.setInputType(inputType);
        edt.setBackgroundResource(R.drawable.bg_input_premium);
        edt.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        params.setMargins(0, dp(10), 0, 0);
        edt.setLayoutParams(params);
        return edt;
    }

    private TextView buildLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#A8872F"));
        tv.setTextSize(11);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(14), 0, 0);
        tv.setLayoutParams(p);
        return tv;
    }

    private void mostrarDialogVerificarUsuario() {
        LinearLayout container = buildDialogContainer();

        // Cards de usuarios pendientes clicables
        if (contenedorUsuariosPendientes.getChildCount() > 0) {
            container.addView(buildLabel("USUARIOS PENDIENTES — tocá uno para preseleccionar"));
        }

        final int[] selectedUserId = {0};
        final TextView selectedInfo = new TextView(this);
        selectedInfo.setTextColor(Color.parseColor("#334155"));
        selectedInfo.setTextSize(13);
        selectedInfo.setVisibility(View.GONE);
        selectedInfo.setBackgroundResource(R.drawable.bg_metric_box);
        selectedInfo.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams siParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        siParams.setMargins(0, dp(8), 0, 0);
        selectedInfo.setLayoutParams(siParams);

        container.addView(buildLabel("ID DE USUARIO"));
        EditText edtId = buildInput("ID de usuario", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtId);
        container.addView(selectedInfo);

        container.addView(buildLabel("CATEGORÍA A ASIGNAR"));
        Spinner spCat = new Spinner(this);
        spCat.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"comun", "especial", "plata", "oro", "platino"}));
        LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        spParams.setMargins(0, dp(8), 0, 0);
        spCat.setLayoutParams(spParams);
        container.addView(spCat);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Verificar usuario")
                .setView(scrollView)
                .setPositiveButton("APROBAR", null)
                .setNegativeButton("RECHAZAR", null)
                .setNeutralButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String id = edtId.getText().toString().trim();
                if (id.isEmpty()) { mostrarError("Ingresá el ID del usuario."); return; }
                edtAdminUsuarioId.setText(id);
                spAdminCategoria.setSelection(spCat.getSelectedItemPosition());
                dialog.dismiss();
                verificarUsuario("si");
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                String id = edtId.getText().toString().trim();
                if (id.isEmpty()) { mostrarError("Ingresá el ID del usuario."); return; }
                edtAdminUsuarioId.setText(id);
                dialog.dismiss();
                verificarUsuario("no");
            });
        });
        dialog.show();
    }

    private void mostrarDialogMediosPago() {
        LinearLayout container = buildDialogContainer();
        container.addView(buildLabel("ID DE MEDIO DE PAGO"));
        EditText edtId = buildInput("ID del medio de pago", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtId);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Verificar medio de pago")
                .setView(scrollView)
                .setPositiveButton("VERIFICAR", null)
                .setNegativeButton("RECHAZAR", null)
                .setNeutralButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String id = edtId.getText().toString().trim();
                if (id.isEmpty()) { mostrarError("Ingresá el ID."); return; }
                edtAdminMedioPagoId.setText(id);
                dialog.dismiss();
                verificarMedioPago();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                String id = edtId.getText().toString().trim();
                if (id.isEmpty()) { mostrarError("Ingresá el ID."); return; }
                edtAdminMedioPagoId.setText(id);
                dialog.dismiss();
                rechazarMedioPago();
            });
        });
        dialog.show();
    }

    private void mostrarDialogConsignaciones() {
        // Load pending products list first, then show dialog
        executor.execute(() -> {
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/admin/products/pending");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + token);
                JSONArray pendientes = c.getResponseCode() == 200
                        ? new JSONArray(leerRespuesta(c.getInputStream()))
                        : new JSONArray();
                c.disconnect();
                mainHandler.post(() -> mostrarDialogConsignacionesConLista(pendientes));
            } catch (Exception e) {
                mainHandler.post(() -> mostrarDialogConsignacionesConLista(new JSONArray()));
            }
        });
    }

    private void mostrarDialogConsignacionesConLista(JSONArray pendientes) {
        LinearLayout container = buildDialogContainer();

        // ── Pending list ─────────────────────────────────────────────────
        if (pendientes.length() > 0) {
            container.addView(buildLabel("CONSIGNACIONES PENDIENTES"));
            for (int i = 0; i < pendientes.length(); i++) {
                try {
                    JSONObject p = pendientes.getJSONObject(i);
                    int pid = p.optInt("id", 0);
                    String desc = p.optString("descripcionCatalogo", "-");
                    String duenio = p.optString("duenioNombre", "-");
                    String estado = p.optString("estadoDescripcion", p.optString("estadoAprobacion", "-"));
                    double sugerido = p.optDouble("precioBaseSugerido", 0);
                    double precio = p.optDouble("precioBasePropuesto", 0);
                    double comision = p.optDouble("comisionPropuesta", 0);

                    String linea = "#" + pid + "  " + desc + "\n" +
                            "Consignante: " + duenio + "\n" +
                            "Estado: " + estado +
                            (sugerido > 0 ? "\nSugerido cliente: $" + String.format("%.2f", sugerido) : "") +
                            (precio > 0 ? "\nPrecio propuesto: $" + String.format("%.2f", precio) : "") +
                            (comision > 0 ? "\nComision: $" + String.format("%.2f", comision) : "") +
                            "";

                    android.widget.Button btnItem = new android.widget.Button(this);
                    btnItem.setText(linea);
                    btnItem.setTextSize(12);
                    btnItem.setAllCaps(false);
                    btnItem.setTextColor(android.graphics.Color.parseColor("#071827"));
                    btnItem.setBackgroundResource(R.drawable.bg_card_premium);
                    btnItem.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
                    int pad = dp(12);
                    btnItem.setPadding(pad, pad, pad, pad);
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    bp.setMargins(0, 0, 0, dp(8));
                    btnItem.setLayoutParams(bp);
                    final int finalPid = pid;
                    btnItem.setOnClickListener(v -> {
                        edtAdminProductoId.setText(String.valueOf(finalPid));
                        if (precio > 0) edtAdminPrecioBase.setText(String.valueOf(precio));
                        else if (sugerido > 0) edtAdminPrecioBase.setText(String.valueOf(sugerido));
                        if (comision > 0) edtAdminComision.setText(String.valueOf(comision));
                    });
                    container.addView(btnItem);
                } catch (Exception ignored) {}
            }
        } else {
            TextView none = new TextView(this);
            none.setText("No hay consignaciones pendientes.");
            none.setTextColor(android.graphics.Color.parseColor("#64748B"));
            none.setTextSize(13);
            container.addView(none);
        }

        container.addView(buildLabel("ID DE PRODUCTO"));
        EditText edtId = buildInput("ID del producto", android.text.InputType.TYPE_CLASS_NUMBER);
        // Pre-fill if edtAdminProductoId already has a value
        String current = edtAdminProductoId.getText().toString().trim();
        if (!current.isEmpty()) edtId.setText(current);
        container.addView(edtId);
        container.addView(buildLabel("PRECIO BASE PROPUESTO"));
        EditText edtPrecio = buildInput("Precio base definido por la empresa", android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_CLASS_NUMBER);
        String precioActual = edtAdminPrecioBase.getText().toString().trim();
        if (!precioActual.isEmpty()) edtPrecio.setText(precioActual);
        container.addView(edtPrecio);
        container.addView(buildLabel("COMISION PROPUESTA"));
        EditText edtComision = buildInput("Comision a cobrar", android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_CLASS_NUMBER);
        String comisionActual = edtAdminComision.getText().toString().trim();
        if (!comisionActual.isEmpty()) edtComision.setText(comisionActual);
        container.addView(edtComision);
        container.addView(buildLabel("MOTIVO DE RECHAZO (solo si rechazás)"));
        EditText edtMotivo = buildInput("Motivo de rechazo, si aplica", android.text.InputType.TYPE_CLASS_TEXT);
        container.addView(edtMotivo);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Revisar consignación")
                .setView(scrollView)
                .setPositiveButton("ENVIAR PROPUESTA", null)
                .setNegativeButton("RECHAZAR", null)
                .setNeutralButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String id = edtId.getText().toString().trim();
                if (id.isEmpty()) { mostrarError("Ingresá el ID."); return; }
                edtAdminProductoId.setText(id);
                edtAdminPrecioBase.setText(edtPrecio.getText().toString().trim());
                edtAdminComision.setText(edtComision.getText().toString().trim());
                edtAdminMotivoRechazo.setText("");
                dialog.dismiss();
                revisarProducto("propuesta_enviada");
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                String id = edtId.getText().toString().trim();
                if (id.isEmpty()) { mostrarError("Ingresá el ID."); return; }
                edtAdminProductoId.setText(id);
                edtAdminMotivoRechazo.setText(edtMotivo.getText().toString().trim());
                dialog.dismiss();
                revisarProducto("rechazado");
            });
        });
        dialog.show();
    }



    private void mostrarDialogCatalogo() {
        LinearLayout container = buildDialogContainer();
        container.addView(buildLabel("ID DE SUBASTA"));
        EditText edtSubasta = buildInput("ID de subasta", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtSubasta);
        container.addView(buildLabel("ID DE PRODUCTO"));
        EditText edtProducto = buildInput("ID de producto", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtProducto);
        container.addView(buildLabel("PRECIO BASE"));
        EditText edtPrecio = buildInput("Precio base definido por la empresa", android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtPrecio);
        container.addView(buildLabel("COMISIÓN"));
        EditText edtComision = buildInput("Comisión", android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtComision);
        container.addView(buildLabel("DURACIÓN POR ITEM (minutos)"));
        EditText edtDuracion = buildInput("Dejar vacío para no cambiar (default: 3)", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtDuracion);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Asignar producto a subasta")
                .setView(scrollView)
                .setPositiveButton("ASIGNAR", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String subasta = edtSubasta.getText().toString().trim();
                String producto = edtProducto.getText().toString().trim();
                String precio = edtPrecio.getText().toString().trim();
                String comision = edtComision.getText().toString().trim();
                String duracion = edtDuracion.getText().toString().trim();
                if (subasta.isEmpty() || producto.isEmpty() || precio.isEmpty() || comision.isEmpty()) {
                    mostrarError("Completá todos los campos."); return;
                }
                edtAdminSubastaId.setText(subasta);
                edtAdminProductoId.setText(producto);
                edtAdminPrecioBase.setText(precio);
                edtAdminComision.setText(comision);
                dialog.dismiss();
                // If duration was specified, update the auction timer first
                if (!duracion.isEmpty()) {
                    actualizarDuracionSubasta(Integer.parseInt(subasta), Integer.parseInt(duracion));
                }
                asignarProducto();
            })
        );
        dialog.show();
    }

    private void actualizarDuracionSubasta(int subastaId, int minutos) {
        executor.execute(() -> {
            try {
                URL url = new URL(ApiConfig.BASE_URL + "/api/admin/auctions/" + subastaId + "/duracion");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("PATCH");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("minutos", minutos);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.getOutputStream().write(bytes);

                int status = conn.getResponseCode();
                InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
                String resp = leerRespuesta(is);
                conn.disconnect();

                if (status == 200) {
                    mainHandler.post(() -> mostrarOk("Timer de subasta #" + subastaId + " actualizado a " + minutos + " min"));
                } else {
                    JSONObject err = new JSONObject(resp);
                    mainHandler.post(() -> mostrarError("No se pudo actualizar duración: " + err.optString("error")));
                }
            } catch (Exception e) {
                mainHandler.post(() -> mostrarError("Error actualizando duración: " + e.getMessage()));
            }
        });
    }

    private void mostrarDialogCerrarItem() {
        LinearLayout container = buildDialogContainer();
        container.addView(buildLabel("ID DE SUBASTA"));
        EditText edtSubasta = buildInput("ID de subasta", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtSubasta);
        container.addView(buildLabel("ID DE ITEM / LOTE"));
        EditText edtItem = buildInput("ID de item o lote", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtItem);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Cerrar lote y generar venta")
                .setView(scrollView)
                .setPositiveButton("CERRAR LOTE", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String subasta = edtSubasta.getText().toString().trim();
                String item = edtItem.getText().toString().trim();
                if (subasta.isEmpty() || item.isEmpty()) {
                    mostrarError("Ingresá subasta e item."); return;
                }
                edtAdminSubastaId.setText(subasta);
                edtAdminItemId.setText(item);
                dialog.dismiss();
                cerrarItem();
            })
        );
        dialog.show();
    }

    private void mostrarDialogMultas() {
        LinearLayout container = buildDialogContainer();
        container.addView(buildLabel("ID DE CLIENTE"));
        EditText edtCliente = buildInput("ID del cliente", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtCliente);
        container.addView(buildLabel("ID DE SUBASTA"));
        EditText edtSubasta = buildInput("ID de subasta", android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtSubasta);
        container.addView(buildLabel("MONTO DE MULTA"));
        EditText edtMonto = buildInput("Monto", android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_CLASS_NUMBER);
        container.addView(edtMonto);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Crear multa por impago")
                .setView(scrollView)
                .setPositiveButton("CREAR MULTA", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(d ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String cliente = edtCliente.getText().toString().trim();
                String subasta = edtSubasta.getText().toString().trim();
                String monto = edtMonto.getText().toString().trim();
                if (cliente.isEmpty() || subasta.isEmpty() || monto.isEmpty()) {
                    mostrarError("Completá todos los campos."); return;
                }
                edtAdminClienteMulta.setText(cliente);
                edtAdminSubastaId.setText(subasta);
                edtAdminMontoMulta.setText(monto);
                dialog.dismiss();
                crearMulta();
            })
        );
        dialog.show();
    }

    private void cerrarSesionAdmin() {
        SharedPreferences preferences = getSharedPreferences("sesion", MODE_PRIVATE);
        preferences.edit().clear().apply();

        Intent intent = new Intent(AdminActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void mostrarMensaje(String mensaje) {
        mostrarInfo(mensaje);
    }

    private void mostrarInfo(String mensaje) {
        txtMensajeAdmin.setTextColor(Color.parseColor("#D7E3EF"));
        txtMensajeAdmin.setText(mensaje);
    }

    private void mostrarOk(String mensaje) {
        txtMensajeAdmin.setTextColor(Color.parseColor("#BBF7D0"));
        txtMensajeAdmin.setText(mensaje);
    }

    private void mostrarError(String mensaje) {
        txtMensajeAdmin.setTextColor(Color.parseColor("#FECACA"));
        txtMensajeAdmin.setText(mensaje);
        FeedbackDialog.error(this, mensaje);
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
}
