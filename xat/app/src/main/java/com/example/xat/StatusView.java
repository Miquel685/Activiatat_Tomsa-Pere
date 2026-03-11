package com.example.xat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class StatusView extends View {
    private Paint paint;
    private int color = Color.GRAY; // Color inicial

    public StatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int radius = Math.min(getWidth(), getHeight()) / 2;
        paint.setColor(color);
        canvas.drawCircle(getWidth() / 2, getHeight() / 2, radius, paint);
    }

    // Aquest és el mètode que et donava error
    public void setColor(int newColor) {
        this.color = newColor;
        invalidate(); // Això obliga al component a redibuixar-se amb el nou color
    }
}