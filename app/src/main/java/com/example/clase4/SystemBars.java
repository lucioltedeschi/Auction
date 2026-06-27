package com.example.clase4;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.WindowInsetsController;

public final class SystemBars {
    private SystemBars() { }

    public static void configure(Activity activity, String statusColor, boolean darkStatusIcons,
                                 String navigationColor, boolean darkNavigationIcons) {
        activity.getWindow().setStatusBarColor(Color.parseColor(statusColor));
        activity.getWindow().setNavigationBarColor(Color.parseColor(navigationColor));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null) {
                int appearance = 0;
                if (darkStatusIcons) appearance |= WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                if (darkNavigationIcons) appearance |= WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(
                        appearance,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS |
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else {
            int flags = 0;
            if (darkStatusIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (darkNavigationIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            activity.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }
}
