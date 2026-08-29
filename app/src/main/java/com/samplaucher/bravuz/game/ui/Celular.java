package com.raiferoleplay.game.game.ui;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import com.raiferoleplay.game.R;

public class Celular {

    private static FrameLayout celularLayout;
    private static FrameLayout fixedContainer;
    private Activity activity;
    public static boolean celularVisible;

    private static final float BASE_WIDTH = 1080f;
    private static final float BASE_HEIGHT = 1920f;

    native void sendCommandV(byte[] str);

    public Celular(Activity activity) {
        this.activity = activity;

        celularLayout = (FrameLayout) activity.getLayoutInflater()
                .inflate(R.layout.celular, null);

        activity.addContentView(
                celularLayout,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

        fixedContainer = celularLayout.findViewById(R.id.celular_fixed);

        aplicarEscala();

        celularVisible = false;
        celularLayout.setVisibility(View.GONE);
    }

    private void aplicarEscala() {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);

        float scaleX = dm.widthPixels / BASE_WIDTH;
        float scaleY = dm.heightPixels / BASE_HEIGHT;

        float scale = Math.min(scaleX, scaleY);

        fixedContainer.setScaleX(scale);
        fixedContainer.setScaleY(scale);
    }

    public void showCelular() {
        if (!celularVisible) {
            celularLayout.setVisibility(View.VISIBLE);
            celularVisible = true;
        }
    }

    public void hideCelular() {
        if (celularVisible) {
            celularLayout.setVisibility(View.GONE);
            celularVisible = false;
        }
    }

    public static void CelularNaTela() {
        if (celularLayout == null) return;

        celularVisible = !celularVisible;
        celularLayout.setVisibility(celularVisible ? View.VISIBLE : View.GONE);
    }
}