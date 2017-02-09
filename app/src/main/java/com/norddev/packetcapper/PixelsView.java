package com.norddev.packetcapper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.LinkedList;
import java.util.List;

public class PixelsView extends SurfaceView {

    private static final float DEFAULT_FREQUENCY = 0.15f;

    private final List<Pixel> mPixels = new LinkedList<>();
    private final Paint mBackgroundPaint = new Paint();
    private RenderLoop mRenderLoop;
    private float mFrequency;
    private volatile boolean mActive;

    public PixelsView(Context context) {
        super(context);
        init();
    }

    public PixelsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PixelsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                startLoop(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopLoop();
            }
        });
        setFrequency(DEFAULT_FREQUENCY);
        setBackgroundPaintColor(Color.WHITE);
        setPixelPaintColor(getResources().getColor(R.color.colorPrimaryDark));
    }

    public void setPixelPaintColor(int color){
        Pixel.setPixelPaintColor(color);
    }

    public void setBackgroundPaintColor(int color) {
        mBackgroundPaint.setColor(color);
    }

    public void setFrequency(float frequency) {
        mFrequency = frequency;
    }

    private void drawScreen(Canvas c) {
        c.drawRect(0, 0, c.getWidth(), c.getHeight(), mBackgroundPaint);
        synchronized (mPixels) {
            for (Pixel pixel : mPixels) {
                pixel.draw(c);
            }
        }
    }

    private void calculateDisplay(Canvas c, float numberOfFrames, boolean drawMore) {
        if(drawMore){
            randomlyAddPixels(c.getWidth(), c.getHeight(), numberOfFrames);
        }
        LinkedList<Pixel> pixelsToRemove = new LinkedList<>();
        synchronized (mPixels) {
            for (Pixel pixel : mPixels) {
                pixel.move(numberOfFrames);
                if (pixel.outOfRange(c.getWidth(), c.getHeight())) {
                    pixelsToRemove.add(pixel);
                }
            }
            for (Pixel pixel : pixelsToRemove) {
                mPixels.remove(pixel);
            }
        }
    }

    private void randomlyAddPixels(int screenWidth, int screenHeight, float numFrames) {
        if (Math.random() > mFrequency * numFrames){
            return;
        }
        synchronized (mPixels) {
            mPixels.add(Pixel.random(screenWidth, screenHeight));
        }
    }

    private void startLoop(SurfaceHolder surfaceHolder) {
        synchronized (this) {
            if (mRenderLoop == null) {
                mRenderLoop = new RenderLoop(surfaceHolder);
                mRenderLoop.start();
            }
        }
    }

    private void stopLoop() {
        synchronized (this) {
            boolean retry = true;
            if (mRenderLoop != null) {
                mRenderLoop.running = false;
                while (retry) {
                    try {
                        mRenderLoop.join();
                        retry = false;
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            mRenderLoop = null;
        }
    }

    public void startAnimation(){
        mActive = true;
    }

    public void stopAnimation(){
        mActive = false;
    }

//    @Override
//    public boolean onTouchEvent(MotionEvent event) {
//        boolean handled = false;
//        if (event.getAction() == MotionEvent.ACTION_DOWN) {
//            createSomePixels(event.getX(), event.getY());
//            handled = true;
//        }
//        return handled;
//    }
//
//    private void createSomePixels(float x, float y) {
//        for (int numPixels = 0; numPixels < 1; ++numPixels) {
//            synchronized (mPixels) {
//                mPixels.add(Pixel.random((int)x, (int)y));
//            }
//        }
//    }

    private class RenderLoop extends Thread {

        private final SurfaceHolder mSurfaceHolder;
        private long msPerFrame = 1000 / 25;
        boolean running = true;

        RenderLoop(SurfaceHolder surfaceHolder) {
            mSurfaceHolder = surfaceHolder;
        }

        public void run() {
            Canvas canvas = null;
            long thisFrameTime;
            long lastFrameTime = System.currentTimeMillis();
            float framesSinceLastFrame = 0;
            while (running) {
                try {
                    canvas = mSurfaceHolder.lockCanvas();
                    if (canvas != null) {
                        drawScreen(canvas);
                        calculateDisplay(canvas, framesSinceLastFrame, mActive);
                    }
                } finally {
                    if (canvas != null) {
                        mSurfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
                thisFrameTime = System.currentTimeMillis();
                framesSinceLastFrame = (float) (thisFrameTime - lastFrameTime) / msPerFrame;
                lastFrameTime = thisFrameTime;
            }
        }

    }

}