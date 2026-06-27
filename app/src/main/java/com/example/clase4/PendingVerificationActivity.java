package com.example.clase4;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class PendingVerificationActivity extends AppCompatActivity {

    private String documento;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_verification);
        SystemBars.configure(this, "#F4F7FB", true, "#F4F7FB", true);

        TextView txtDocumentoPendiente = findViewById(R.id.txtDocumentoPendiente);
        Button btnCompletarClavePendiente = findViewById(R.id.btnCompletarClavePendiente);
        Button btnVolverLoginPendiente = findViewById(R.id.btnVolverLoginPendiente);

        documento = getIntent().getStringExtra("documento");
        if (documento == null) {
            documento = "";
        }

        txtDocumentoPendiente.setText("Solicitud recibida para el documento " + documento);

        btnCompletarClavePendiente.setOnClickListener(v -> {
            Intent intent = new Intent(PendingVerificationActivity.this, RegistroActivity.class);
            intent.putExtra("documento", documento);
            startActivity(intent);
        });

        btnVolverLoginPendiente.setOnClickListener(v -> {
            Intent intent = new Intent(PendingVerificationActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}
