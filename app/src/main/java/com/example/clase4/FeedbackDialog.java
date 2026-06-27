package com.example.clase4;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.function.IntConsumer;

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

    public static void accionExitosa(Context context, String titulo, String mensaje,
                                     String textoAccion, Runnable accion) {
        LinearLayout root = crearContenido(context, titulo, mensaje, R.drawable.ic_status_success, "#166534");
        AlertDialog dialog = crearDialogo(context, root);
        Button button = crearBoton(context, textoAccion, R.drawable.bg_button_gold, "#071827");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 50));
        params.setMargins(0, dp(context, 18), 0, 0);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> {
            dialog.dismiss();
            accion.run();
        });
        root.addView(button);
        dialog.setCancelable(false);
        mostrarDialogo(dialog);
    }

    public static void confirmar(Context context, String titulo, String mensaje, Runnable alConfirmar) {
        LinearLayout root = crearContenido(context, titulo, mensaje, R.drawable.ic_status_info, "#A8872F");
        AlertDialog dialog = crearDialogo(context, root);
        LinearLayout actions = crearFilaAcciones(context);
        Button cancel = crearBoton(context, "CANCELAR", R.drawable.bg_button_outline, "#64748B");
        Button confirm = crearBoton(context, "CONFIRMAR", R.drawable.bg_button_gold, "#071827");
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            alConfirmar.run();
        });
        actions.addView(cancel);
        actions.addView(confirm);
        root.addView(actions);
        mostrarDialogo(dialog);
    }

    public static void seleccionar(Context context, String titulo, String mensaje,
                                   List<String> opciones, IntConsumer alSeleccionar) {
        LinearLayout root = crearContenido(context, titulo, mensaje, R.drawable.ic_status_info, "#A8872F");
        AlertDialog dialog = crearDialogo(context, root);
        for (int i = 0; i < opciones.size(); i++) {
            final int index = i;
            Button option = crearBoton(context, opciones.get(i), R.drawable.bg_button_outline, "#071827");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 52));
            params.setMargins(0, dp(context, 8), 0, 0);
            option.setLayoutParams(params);
            option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            option.setPadding(dp(context, 16), 0, dp(context, 16), 0);
            option.setOnClickListener(v -> {
                dialog.dismiss();
                alSeleccionar.accept(index);
            });
            root.addView(option);
        }
        Button cancel = crearBoton(context, "CANCELAR", R.drawable.bg_button_outline, "#64748B");
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 48));
        cancelParams.setMargins(0, dp(context, 12), 0, 0);
        cancel.setLayoutParams(cancelParams);
        cancel.setOnClickListener(v -> dialog.dismiss());
        root.addView(cancel);
        mostrarDialogo(dialog);
    }

    private static void mostrar(Context context, String titulo, String mensaje, int iconRes, String badgeColor) {
        LinearLayout root = crearContenido(context, titulo, mensaje, iconRes, badgeColor);

        AlertDialog dialog = crearDialogo(context, root);
        Button understood = crearBoton(context, "ENTENDIDO", R.drawable.bg_button_gold, "#071827");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 50));
        params.setMargins(0, dp(context, 18), 0, 0);
        understood.setLayoutParams(params);
        understood.setOnClickListener(v -> dialog.dismiss());
        root.addView(understood);
        mostrarDialogo(dialog);
    }

    private static LinearLayout crearContenido(Context context, String titulo, String mensaje, int iconRes, String badgeColor) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 24), dp(context, 26), dp(context, 24), dp(context, 22));
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

    private static AlertDialog crearDialogo(Context context, View root) {
        return new AlertDialog.Builder(context)
                .setView(root)
                .setCancelable(true)
                .create();
    }

    private static LinearLayout crearFilaAcciones(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 50));
        params.setMargins(0, dp(context, 18), 0, 0);
        row.setLayoutParams(params);
        return row;
    }

    private static Button crearBoton(Context context, String text, int background, String textColor) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(12);
        button.setTextColor(Color.parseColor(textColor));
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setBackgroundResource(background);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(context, 4), 0, dp(context, 4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private static void mostrarDialogo(AlertDialog dialog) {
        dialog.show();
        estilizarVentana(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (dialog.getContext().getResources().getDisplayMetrics().widthPixels * 0.90f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.62f;
            window.setAttributes(attributes);
        }
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
