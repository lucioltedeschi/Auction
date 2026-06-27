package com.example.clase4;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FeedbackDialog {
    public static void ok(Context context, String mensaje) {
        mostrar(context, "Operación realizada", mensaje, R.drawable.ic_status_success, "#166534");
    }

    public static void error(Context context, String mensaje) {
        mostrar(context, "No se pudo completar", mensajeAmigable(mensaje), R.drawable.ic_status_error, "#991B1B");
    }

    public static void info(Context context, String titulo, String mensaje) {
        mostrar(context, titulo, mensaje, R.drawable.ic_status_info, "#A8872F");
    }

    public static void confirmar(Context context, String titulo, String mensaje, Runnable alConfirmar) {
        LinearLayout root = crearContenido(context, titulo, mensaje, R.drawable.ic_status_info, "#A8872F");
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(root)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar", (ignored, which) -> alConfirmar.run())
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#A8872F"));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#64748B"));
        });
        dialog.show();
        estilizarVentana(dialog);
    }

    private static void mostrar(Context context, String titulo, String mensaje, int iconRes, String badgeColor) {
        LinearLayout root = crearContenido(context, titulo, mensaje, iconRes, badgeColor);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(root)
                .setPositiveButton("Entendido", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#A8872F"));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(null, android.graphics.Typeface.BOLD);
        });
        dialog.show();
        estilizarVentana(dialog);
    }

    private static LinearLayout crearContenido(Context context, String titulo, String mensaje, int iconRes, String badgeColor) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 24), dp(context, 26), dp(context, 24), dp(context, 16));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundResource(R.drawable.bg_dialog_premium);

        ImageView badgeView = new ImageView(context);
        badgeView.setImageResource(iconRes);
        badgeView.setColorFilter(Color.WHITE);
        badgeView.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        badgeView.setContentDescription(titulo);
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setShape(GradientDrawable.OVAL);
        badgeBackground.setColor(Color.parseColor(badgeColor));
        badgeView.setBackground(badgeBackground);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52));
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
        return root;
    }

    private static void estilizarVentana(AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private static String mensajeAmigable(String mensaje) {
        String base = mensaje == null || mensaje.trim().isEmpty()
                ? "La operacion no pudo procesarse."
                : mensaje.trim();
        String lower = base.toLowerCase();

        if (lower.contains("conectar") || lower.contains("conexion") || lower.contains("servidor")) {
            return base + "\n\nVerificá tu conexión a internet y volvé a intentar. El servicio gratuito puede tardar unos segundos en activarse.";
        }

        if (lower.contains("medio de pago") || lower.contains("verificado")) {
            return base + "\n\nCargá un medio de pago y esperá la verificación interna antes de intentar pujar.";
        }

        if (lower.contains("multa")) {
            return base + "\n\nRegularizá la multa desde la sección Multas para volver a participar.";
        }

        if (lower.contains("categoria")) {
            return base + "\n\nTu categoría actual no alcanza para esta subasta. La empresa puede actualizarla luego de la verificación.";
        }

        if (lower.contains("subasta activa") || lower.contains("conectado")) {
            return base + "\n\nSalí de la subasta actual antes de entrar a otra.";
        }

        return base;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }
}
