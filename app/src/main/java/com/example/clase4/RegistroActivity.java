package com.example.clase4;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistroActivity extends AppCompatActivity {

    private static final int REQ_DNI_FRENTE = 1001;
    private static final int REQ_DNI_DORSO = 1002;

    private EditText etDocumento;
    private EditText etNombre;
    private EditText etApellido;
    private EditText etDireccion;
    private EditText etEmail;
    private EditText etTelefono;
    private Spinner etPais;
    private EditText etDocumentoPaso2;
    private EditText etClavePaso2;
    private TextView txtDniFrente;
    private TextView txtDniDorso;
    private TextView txtMensajeRegistro;
    private TextView txtMensajePaso2;
    private Button btnSeleccionarDniFrente;
    private Button btnSeleccionarDniDorso;
    private Button btnRegistrar;
    private Button btnCompletarRegistro;

    private String fotoDniFrenteBase64;
    private String fotoDniDorsoBase64;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);
        SystemBars.configure(this, "#F3F0E8", true, "#F3F0E8", true);

        etDocumento = findViewById(R.id.etDocumento);
        etNombre = findViewById(R.id.etNombre);
        etApellido = findViewById(R.id.etApellido);
        etDireccion = findViewById(R.id.etDireccion);
        etEmail = findViewById(R.id.etEmail);
        etTelefono = findViewById(R.id.etTelefono);
        etPais = findViewById(R.id.etPais);
        etDocumentoPaso2 = findViewById(R.id.etDocumentoPaso2);
        etClavePaso2 = findViewById(R.id.etClavePaso2);
        txtDniFrente = findViewById(R.id.txtDniFrente);
        txtDniDorso = findViewById(R.id.txtDniDorso);
        txtMensajeRegistro = findViewById(R.id.txtMensajeRegistro);
        txtMensajePaso2 = findViewById(R.id.txtMensajePaso2);
        btnSeleccionarDniFrente = findViewById(R.id.btnSeleccionarDniFrente);
        btnSeleccionarDniDorso = findViewById(R.id.btnSeleccionarDniDorso);
        btnRegistrar = findViewById(R.id.btnRegistrar);
        btnCompletarRegistro = findViewById(R.id.btnCompletarRegistro);

        configurarPaises();

        String documentoPendiente = getIntent().getStringExtra("documento");
        if (documentoPendiente != null) {
            etDocumentoPaso2.setText(documentoPendiente);
        }

        btnSeleccionarDniFrente.setOnClickListener(v -> seleccionarImagen(REQ_DNI_FRENTE));
        btnSeleccionarDniDorso.setOnClickListener(v -> seleccionarImagen(REQ_DNI_DORSO));
        btnRegistrar.setOnClickListener(v -> enviarPaso1());
        btnCompletarRegistro.setOnClickListener(v -> enviarPaso2());
    }

    private void configurarPaises() {
        etPais.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Argentina", "Estados Unidos"}
        ));
    }

    private void seleccionarImagen(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        try {
            String base64 = leerImagenBase64(uri);

            if (requestCode == REQ_DNI_FRENTE) {
                fotoDniFrenteBase64 = base64;
                txtDniFrente.setText("Frente seleccionado");
            } else if (requestCode == REQ_DNI_DORSO) {
                fotoDniDorsoBase64 = base64;
                txtDniDorso.setText("Dorso seleccionado");
            }
        } catch (Exception e) {
            mostrarErrorRegistro("No se pudo leer la imagen seleccionada.");
        }
    }

    private String leerImagenBase64(Uri uri) throws Exception {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        Bitmap original = BitmapFactory.decodeStream(inputStream);
        if (inputStream != null) {
            inputStream.close();
        }

        if (original == null) {
            throw new IllegalArgumentException("No se pudo decodificar la imagen");
        }

        Bitmap bitmap = redimensionarBitmap(original, 1280);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream);

        if (bitmap != original) {
            original.recycle();
        }

        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private Bitmap redimensionarBitmap(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return original;
        }

        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);

        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }

    private void enviarPaso1() {
        String documento = etDocumento.getText().toString().trim();
        String nombre = etNombre.getText().toString().trim();
        String apellido = etApellido.getText().toString().trim();
        String direccion = etDireccion.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String telefono = etTelefono.getText().toString().trim();

        if (documento.isEmpty() || nombre.isEmpty() || apellido.isEmpty() || direccion.isEmpty()) {
            mostrarErrorRegistro("Completá documento, nombre, apellido y domicilio legal.");
            return;
        }

        if (fotoDniFrenteBase64 == null || fotoDniDorsoBase64 == null) {
            mostrarErrorRegistro("Seleccioná frente y dorso del DNI.");
            return;
        }

        Integer numeroPais = etPais.getSelectedItemPosition() == 1 ? 840 : 32;

        btnRegistrar.setEnabled(false);
        btnRegistrar.setText("Enviando...");
        txtMensajeRegistro.setText("");

        RegistroRequest request = new RegistroRequest(
                documento,
                nombre,
                apellido,
                direccion,
                email,
                telefono,
                numeroPais,
                fotoDniFrenteBase64,
                fotoDniDorsoBase64
        );

        ApiClient.getClient().create(ApiService.class).registroPaso1(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                btnRegistrar.setEnabled(true);
                btnRegistrar.setText("Enviar solicitud");

                if (response.isSuccessful()) {
                    Intent intent = new Intent(RegistroActivity.this, PendingVerificationActivity.class);
                    intent.putExtra("documento", documento);
                    startActivity(intent);
                    finish();
                } else if (response.code() == 409) {
                    mostrarErrorRegistro("Ya existe una solicitud con ese documento.");
                } else {
                    mostrarErrorRegistro(mensajeErrorRegistro(response));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                btnRegistrar.setEnabled(true);
                btnRegistrar.setText("Enviar solicitud");
                mostrarErrorRegistro("No se pudo conectar con el servidor. Revisá tu conexión a internet y volvé a intentar en unos segundos.");
            }
        });
    }

    private String mensajeErrorRegistro(Response<ResponseBody> response) {
        String detalle = "No se pudo enviar la solicitud. Código: " + response.code();

        try {
            if (response.errorBody() != null) {
                detalle += " - " + response.errorBody().string();
            }
        } catch (Exception ignored) {
        }

        return detalle;
    }

    private void enviarPaso2() {
        String documento = etDocumentoPaso2.getText().toString().trim();
        String clave = etClavePaso2.getText().toString().trim();

        if (documento.isEmpty() || clave.isEmpty()) {
            mostrarErrorPaso2("Ingresá documento y clave.");
            return;
        }

        btnCompletarRegistro.setEnabled(false);
        btnCompletarRegistro.setText("Generando...");
        txtMensajePaso2.setText("");

        RegistroPaso2Request request = new RegistroPaso2Request(documento, clave);

        ApiClient.getClient().create(ApiService.class).registroPaso2(request).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                btnCompletarRegistro.setEnabled(true);
                btnCompletarRegistro.setText("Generar clave");

                if (response.isSuccessful()) {
                    txtMensajePaso2.setText("Clave generada. Ya podés iniciar sesión.");
                    FeedbackDialog.ok(RegistroActivity.this, "Clave generada. Ya podés iniciar sesión.");
                    startActivity(new Intent(RegistroActivity.this, LoginActivity.class));
                    finish();
                } else if (response.code() == 403) {
                    mostrarErrorPaso2("La empresa todavía no aprobó esta cuenta.");
                } else {
                    mostrarErrorPaso2("No se pudo generar la clave.");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                btnCompletarRegistro.setEnabled(true);
                btnCompletarRegistro.setText("Generar clave");
                mostrarErrorPaso2("No se pudo conectar con el servidor.");
            }
        });
    }

    private void mostrarErrorRegistro(String mensaje) {
        txtMensajeRegistro.setText(mensaje);
        FeedbackDialog.error(this, mensaje);
    }

    private void mostrarErrorPaso2(String mensaje) {
        txtMensajePaso2.setText(mensaje);
        FeedbackDialog.error(this, mensaje);
    }
}
