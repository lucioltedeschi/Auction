package com.example.clase4;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FeedbackDialog {
    public static void ok(Context context, String mensaje) {
        mostrar(context, "Operacion realizada", mensaje, "OK", "#166534");
    }

    public static void error(Context context, String mensaje) {
        mostrar(context, "No se pudo completar", mensajeAmigable(mensaje), "!", "#991B1B");
    }

    public static void info(Context context, String titulo, String mensaje) {
        mostrar(context, titulo, mensaje, "i", "#A8872F");
    }

    private static void mostrar(Context context, String titulo, String mensaje, String badge, String badgeColor) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 12));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView badgeView = new TextView(context);
        badgeView.setText(badge);
        badgeView.setTextColor(Color.WHITE);
        badgeView.setTextSize(18);
        badgeView.setTypeface(null, android.graphics.Typeface.BOLD);
        badgeView.setGravity(Gravity.CENTER);
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setShape(GradientDrawable.OVAL);
        badgeBackground.setColor(Color.parseColor(badgeColor));
        badgeView.setBackground(badgeBackground);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42));
        badgeView.setLayoutParams(badgeParams);

        TextView titleView = new TextView(context);
        titleView.setText(titulo);
        titleView.setTextColor(Color.parseColor("#071827"));
        titleView.setTextSize(20);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, dp(context, 14), 0, 0);
        titleView.setLayoutParams(titleParams);

        TextView messageView = new TextView(context);
        messageView.setText(mensaje);
        messageView.setTextColor(Color.parseColor("#475569"));
        messageView.setTextSize(14);
        messageView.setGravity(Gravity.CENTER);
        messageView.setLineSpacing(dp(context, 3), 1.0f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageParams.setMargins(0, dp(context, 8), 0, 0);
        messageView.setLayoutParams(messageParams);

        root.addView(badgeView);
        root.addView(titleView);
        root.addView(messageView);

        new AlertDialog.Builder(context)
                .setView(root)
                .setPositiveButton("Entendido", null)
                .show();
    }

    private static String mensajeAmigable(String mensaje) {
        String base = mensaje == null || mensaje.trim().isEmpty()
                ? "La operacion no pudo procesarse."
                : mensaje.trim();
        String lower = base.toLowerCase();

        if (lower.contains("conectar") || lower.contains("conexion") || lower.contains("servidor")) {
            return base + "\n\nVerifica que el backend este encendido y que el celular/emulador este apuntando a la URL correcta.";
        }

        if (lower.contains("medio de pago") || lower.contains("verificado")) {
            return base + "\n\nCarga un medio de pago y espera la verificacion interna antes de intentar pujar.";
        }

        if (lower.contains("multa")) {
            return base + "\n\nRegulariza la multa desde la seccion Multas para volver a participar.";
        }

        if (lower.contains("categoria")) {
            return base + "\n\nTu categoria actual no alcanza para esta subasta. La empresa puede actualizarla luego de la verificacion.";
        }

        if (lower.contains("subasta activa") || lower.contains("conectado")) {
            return base + "\n\nSali de la subasta actual antes de entrar a otra.";
        }

        return base;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
