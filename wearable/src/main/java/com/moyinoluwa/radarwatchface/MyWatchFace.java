package com.moyinoluwa.radarwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "MyWatchFace";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private boolean mRegisteredTimeZoneReceiver = false;

        private Calendar mCalendar;

        // Variables for painting background
        private Paint mBackgroundPaint;

        // Watch Face Hand related objects
        private Paint mHourAndMinuteHandPaint;
        private Paint mSecondHandPaint;
        private Paint mCirclePaint;
        private Paint mTickPaint;
        private Paint radarTextPaint;
        private Paint hourTextPaint;
        private float mHourHandLength;
        private float mMinuteHandLength;
        private float mSecondHandLength;

        private boolean mAmbient;

        /*
        * Whether the display supports fewer bits for each color in ambient mode. When true, we
        * disable anti-aliasing in ambient mode.
        */
        boolean mLowBitAmbient;

        /*
        * Whether the display supports burn in protection in ambient mode.
        * When true, remove the background in ambient mode.
        */
        private boolean mBurnInProtection;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mCalendar = Calendar.getInstance();

            initializeBackground();

            initializeRadarTextPaint();

            initializeHourTextPaint();

            initializeTickPaint();

            initializeMinuteAndHourHand();

            initializeSecondHand();

            initializeCenterCircle();
        }

        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.background));
        }

        private void initializeMinuteAndHourHand() {
            Resources resources = MyWatchFace.this.getResources();

            mHourAndMinuteHandPaint = new Paint();
            mHourAndMinuteHandPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.analog_hands));
            mHourAndMinuteHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHourAndMinuteHandPaint.setAntiAlias(true);
            mHourAndMinuteHandPaint.setStrokeCap(Paint.Cap.SQUARE);
        }

        private void initializeSecondHand() {
            Resources resources = MyWatchFace.this.getResources();

            mSecondHandPaint = new Paint();
            mSecondHandPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.analog_hands));
            mSecondHandPaint.setStrokeWidth(resources.getDimension(R.dimen.tick_hand_stroke));
            mSecondHandPaint.setAntiAlias(true);
            mSecondHandPaint.setStrokeCap(Paint.Cap.SQUARE);
        }

        private void initializeCenterCircle() {
            mCirclePaint = new Paint();
            mCirclePaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.analog_hands));
            mCirclePaint.setAntiAlias(true);
        }

        private void initializeTickPaint() {
            Resources resources = MyWatchFace.this.getResources();

            mTickPaint = new Paint();
            mTickPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.tick_color));
            mTickPaint.setStrokeWidth(resources.getDimension(R.dimen.tick_hand_stroke));
            mTickPaint.setAntiAlias(true);
        }

        private void initializeRadarTextPaint() {
            Resources resources = MyWatchFace.this.getResources();
            Typeface radarTextTypeface = Typeface.createFromAsset(getAssets(), "fonts/NexaLight.ttf");
            float radarTextSize = 60;

            radarTextPaint = new Paint();
            radarTextPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.radar_text_color));
            radarTextPaint.setStrokeWidth(resources.getDimension(R.dimen.radar_hand_stroke));
            radarTextPaint.setAntiAlias(true);
            radarTextPaint.setTextAlign(Paint.Align.LEFT);
            radarTextPaint.setTextSize(radarTextSize);
            radarTextPaint.setTypeface(radarTextTypeface);
        }

        private void initializeHourTextPaint() {
            Resources resources = MyWatchFace.this.getResources();
            Typeface radarTextTypeface = Typeface.createFromAsset(getAssets(), "fonts/NexaLight.ttf");
            float hourTextSize = 20;

            hourTextPaint = new Paint();
            hourTextPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.tick_color));
            hourTextPaint.setStrokeWidth(resources.getDimension(R.dimen.radar_hand_stroke));
            hourTextPaint.setAntiAlias(true);
            hourTextPaint.setTextAlign(Paint.Align.LEFT);
            hourTextPaint.setTextSize(hourTextSize);
            hourTextPaint.setTypeface(radarTextTypeface);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient || mBurnInProtection) {
                    mHourAndMinuteHandPaint.setAntiAlias(!inAmbientMode);
                    mSecondHandPaint.setAntiAlias(!inAmbientMode);
                    mTickPaint.setAntiAlias(!inAmbientMode);
                    mCirclePaint.setAntiAlias(!inAmbientMode);
                    radarTextPaint.setAntiAlias(!inAmbientMode);
                    hourTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;

            /*
             * Find the coordinates of the center point on the screen.
             * Ignore the window insets so that, on round watches
             * with a "chin", the watch face is centered on the entire screen,
             * not just the usable portion.
             */
            mCenterX = mWidth / 2f;
            mCenterY = mHeight / 2f;

            // Calculate the lengths of the watch hands and store them in member variables.
            mHourHandLength = mCenterX - 80;
            mMinuteHandLength = mCenterX - 50;
            mSecondHandLength = mCenterX - 20;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            // Draw the background.
            drawBackground(canvas);

            // draw 'radar' text
            drawRadarText(canvas);

            // draw the minute ticks
            drawMinuteTicks(canvas);

            // draw the hour ticks
            drawHourTicks(canvas);

            // draw the hour and minute hand
            drawHourAndMinuteHand(canvas);

            // draw second hand
            drawSecondHand(canvas);

            // draw center circle
            drawCircle(canvas);
        }

        private void drawBackground(Canvas canvas) {
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            }
        }

        private void drawRadarText(Canvas canvas) {
            String radarText = getString(R.string.my_analog_name).toLowerCase();

            if (isInAmbientMode()) {
                radarTextPaint.setColor(Color.WHITE);
            } else {
                radarTextPaint.setColor(ContextCompat.getColor(MyWatchFace.this, R.color.radar_text_color));
            }
            canvas.drawText(radarText, mCenterX - 75f, mCenterY - 40f, radarTextPaint);
        }

        private void drawHourTicks(Canvas canvas) {

            // draws the hour values on specific positions on the canvas
            canvas.drawText("12", mCenterX - 10f, mCenterY - 120f, hourTextPaint);
            canvas.drawText("1", mCenterX + 60f, mCenterY - 100f, hourTextPaint);
            canvas.drawText("2", mCenterX + 105f, mCenterY - 57f, hourTextPaint);
            canvas.drawText("3", mCenterX + 120f, mCenterY + 7f, hourTextPaint);
            canvas.drawText("4", mCenterX + 105f, mCenterY + 73f, hourTextPaint);
            canvas.drawText("5", mCenterX + 55f, mCenterY + 120f, hourTextPaint);
            canvas.drawText("6", mCenterX - 3f, mCenterY + 130f, hourTextPaint);
            canvas.drawText("7", mCenterX - 69f, mCenterY + 120f, hourTextPaint);
            canvas.drawText("8", mCenterX - 115f, mCenterY + 73f, hourTextPaint);
            canvas.drawText("9", mCenterX - 132f, mCenterY + 7f, hourTextPaint);
            canvas.drawText("10", mCenterX - 115f, mCenterY - 57f, hourTextPaint);
            canvas.drawText("11", mCenterX - 69f, mCenterY - 100f, hourTextPaint);
        }

        private void drawMinuteTicks(Canvas canvas) {
            float innerTickRadius;

            // Doubles the length of the hour tick so that
            // there is a distinction between the minutes and hours
            for (int tickIndex = 0; tickIndex < 60; tickIndex++) {
                if (tickIndex == 0 || (tickIndex % 5) == 0) {
                    innerTickRadius = mCenterX - 20;
                } else {
                    innerTickRadius = mCenterX - 10;
                }

                float tickRotation = (float) (tickIndex * Math.PI * 2 / 60);
                float innerX = (float) Math.sin(tickRotation) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRotation) * innerTickRadius;
                float outerX = (float) Math.sin(tickRotation) * mCenterX;
                float outerY = (float) -Math.cos(tickRotation) * mCenterX;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY, mCenterX + outerX, mCenterY + outerY, mTickPaint);
            }
        }

        private void drawHourAndMinuteHand(Canvas canvas) {

            // Constant to help calculate clock hand rotations
            final float TWO_PI = (float) Math.PI * 2f;

            float seconds = mCalendar.get(Calendar.SECOND) +
                    mCalendar.get(Calendar.MILLISECOND) / 1000f;

            float minutes = mCalendar.get(Calendar.MINUTE) + seconds / 60f;
            float minutesRotation = minutes / 60f * TWO_PI;

            float hours = mCalendar.get(Calendar.HOUR) + minutes / 60f;
            float hoursRotation = hours / 12f * TWO_PI;

            float minX = (float) Math.sin(minutesRotation) * mMinuteHandLength;
            float minY = (float) -Math.cos(minutesRotation) * mMinuteHandLength;
            canvas.drawLine(mCenterX, mCenterY, mCenterX + minX, mCenterY + minY, mHourAndMinuteHandPaint);

            float hrX = (float) Math.sin(hoursRotation) * mHourHandLength;
            float hrY = (float) -Math.cos(hoursRotation) * mHourHandLength;
            canvas.drawLine(mCenterX, mCenterY, mCenterX + hrX, mCenterY + hrY, mHourAndMinuteHandPaint);
        }

        private void drawSecondHand(Canvas canvas) {
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);

            // Constant to help calculate clock hand rotations
            final float TWO_PI = (float) Math.PI * 2f;

            final float secondsRotation = seconds / 60f * TWO_PI;

            if (!mAmbient) {
                float secX = (float) Math.sin(secondsRotation) * mSecondHandLength;
                float secY = (float) -Math.cos(secondsRotation) * mSecondHandLength;
                canvas.drawLine(mCenterX, mCenterY, mCenterX + secX, mCenterY + secY, mSecondHandPaint);
            }
        }

        private void drawCircle(Canvas canvas) {
            final float circleRadius = 8.5f;
            canvas.drawCircle(mCenterX, mCenterY, circleRadius, mCirclePaint);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
