package com.norddev.packetcapper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

public class Pixel {

    private static final Paint PIXEL_PAINT = new Paint();
    private static final int RADIUS = 30;
    private static final int MAX_SPEED = 18;
    private static final int MIN_SPEED = 8;

    static {
        PIXEL_PAINT.setStyle(Paint.Style.FILL);
        PIXEL_PAINT.setColor(Color.BLUE);
        PIXEL_PAINT.setAntiAlias(true);
    }

    private float mX, mY, mSpeed;

    public static void setPixelPaintColor(int color){
        PIXEL_PAINT.setColor(color);
    }

    public Pixel(float x, float y, float speed) {
        mX = x;
        mY = y;
        mSpeed = Math.max(speed, MIN_SPEED);
    }

    public static Pixel random(int width, int height){
        return new Pixel((int) (width * Math.random()), height + Pixel.RADIUS, (int) (Pixel.MAX_SPEED * Math.random()));
    }

    public void draw(Canvas c) {
        c.drawRect(new RectF(
                        mX - RADIUS,
                        mY - RADIUS,
                        mX + RADIUS,
                        mY + RADIUS),
                PIXEL_PAINT);
    }

    public void move(float numFrames) {
        mY -= mSpeed * numFrames;
    }

    public boolean outOfRange(int width, int height) {
        return (mY + RADIUS < 0);
    }


}