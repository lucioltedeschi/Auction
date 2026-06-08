package com.example.clase4;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class BottomNavHelper {

    private static final int COLOR_ACTIVE_ICON   = Color.WHITE;
    private static final int COLOR_ACTIVE_TEXT   = Color.WHITE;
    private static final int COLOR_INACTIVE_ICON = Color.parseColor("#64748B");
    private static final int COLOR_INACTIVE_TEXT = Color.parseColor("#64748B");

    private static final int[] NAV_IDS   = {R.id.navHome,      R.id.navAuctions,      R.id.navActivity,      R.id.navProfile};
    private static final int[] IMG_IDS   = {R.id.imgNavHome,   R.id.imgNavAuctions,   R.id.imgNavActivity,   R.id.imgNavProfile};
    private static final int[] LABEL_IDS = {R.id.navHomeLabel, R.id.navAuctionsLabel, R.id.navActivityLabel, R.id.navProfileLabel};

    public static void configurar(Activity activity) {

        // ── Active tab index ──────────────────────────────────────────────────
        int activeIndex = 0;
        if (activity instanceof SubastasActivity || activity instanceof AuctionDetailActivity) {
            activeIndex = 1;
        } else if (activity instanceof HistoryActivity) {
            activeIndex = 2;
        } else if (activity instanceof ProfileActivity) {
            activeIndex = 3;
        }

        // ── Style tabs ────────────────────────────────────────────────────────
        for (int i = 0; i < NAV_IDS.length; i++) {
            View      card = activity.findViewById(NAV_IDS[i]);
            ImageView img  = activity.findViewById(IMG_IDS[i]);
            TextView  lbl  = activity.findViewById(LABEL_IDS[i]);
            if (card == null) continue;

            boolean active = (i == activeIndex);

            if (active) {
                card.setBackgroundResource(R.drawable.bg_nav_card_active);
            } else {
                card.setBackground(null);
            }
            if (img  != null) img.setColorFilter(active ? COLOR_ACTIVE_ICON : COLOR_INACTIVE_ICON, PorterDuff.Mode.SRC_IN);
            if (lbl  != null) lbl.setTextColor(active ? COLOR_ACTIVE_TEXT : COLOR_INACTIVE_TEXT);
        }

        // ── Animate active card: scale-pop in (only navbar animates) ──────────
        View activeCard = activity.findViewById(NAV_IDS[activeIndex]);
        if (activeCard != null) {
            activeCard.setScaleX(0.80f);
            activeCard.setScaleY(0.80f);
            activeCard.setAlpha(0.4f);
            activeCard.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }

        // ── Click listeners (overridePendingTransition(0,0) kills screen slide) ─
        View navHome = activity.findViewById(R.id.navHome);
        if (navHome != null) navHome.setOnClickListener(v -> {
            if (!(activity instanceof HomeActivity)) {
                activity.startActivity(new Intent(activity, HomeActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                activity.overridePendingTransition(0, 0);
            }
        });

        View navAuctions = activity.findViewById(R.id.navAuctions);
        if (navAuctions != null) navAuctions.setOnClickListener(v -> {
            if (!(activity instanceof SubastasActivity)) {
                activity.startActivity(new Intent(activity, SubastasActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                activity.overridePendingTransition(0, 0);
            }
        });

        View navActivity = activity.findViewById(R.id.navActivity);
        if (navActivity != null) navActivity.setOnClickListener(v -> {
            if (!(activity instanceof HistoryActivity)) {
                activity.startActivity(new Intent(activity, HistoryActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                activity.overridePendingTransition(0, 0);
            }
        });

        View navProfile = activity.findViewById(R.id.navProfile);
        if (navProfile != null) navProfile.setOnClickListener(v -> {
            if (!(activity instanceof ProfileActivity)) {
                activity.startActivity(new Intent(activity, ProfileActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                activity.overridePendingTransition(0, 0);
            }
        });
    }
}
