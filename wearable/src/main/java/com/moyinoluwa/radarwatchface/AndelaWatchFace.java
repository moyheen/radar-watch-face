package com.moyinoluwa.radarwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Andela Watch Face
 */
public class AndelaWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "AndelaWatchFace";

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {

        private boolean mRegisteredTimeZoneReceiver = false;

        private Calendar mCalendar;

        // Variables for painting Background
        private Paint mBackgroundPaint;
        private Bitmap mBackgroundBitmap;

        // Watch Face related objects
        private Paint mHandPaint;
        private Paint mDatePaint;

        private boolean mAmbient;

        /*
         * Whether the display supports fewer bits for each color in ambient mode.
         * When true, we disable anti-aliasing in ambient mode.
         */
        private boolean mLowBitAmbient;

        /*
         * Whether the display supports burn in protection in ambient mode.
         * When true, remove the background in ambient mode.
         */
        private boolean mBurnInProtection;

        private int mWidth;
        private int mHeight;
        private float mCenterX;
        private float mCenterY;
        private float mScale = 1;

        // Handler to update the time once a second in interactive mode.
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (R.id.message_update == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        mUpdateTimeHandler.sendEmptyMessageDelayed(R.id.message_update, delayMs);
                    }
                }
            }
        };

        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(AndelaWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mCalendar = Calendar.getInstance();

            initializeBackground();

            initializeText();

            initializeDate();
        }

        private void initializeBackground() {
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.WHITE);

            final int backgroundResId = R.drawable.custom_andela_background;
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), backgroundResId);
        }

        private void initializeText() {
            Resources resources = AndelaWatchFace.this.getResources();
            Typeface textTypeface = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Medium.ttf");

            mHandPaint = new Paint();
            mHandPaint.setColor(ContextCompat.getColor(AndelaWatchFace.this, R.color.andela_text_color));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setTextAlign(Paint.Align.CENTER);
            mHandPaint.setTextSize(resources.getDimension(R.dimen.digital_text_size));
            mHandPaint.setTypeface(textTypeface);
        }

        private void initializeDate() {
            Resources resources = AndelaWatchFace.this.getResources();
            Typeface textTypeface = Typeface.createFromAsset(getAssets(), "fonts/Roboto-Medium.ttf");

            mDatePaint = new Paint();
            mDatePaint.setColor(ContextCompat.getColor(AndelaWatchFace.this, R.color.andela_text_color));
            mDatePaint.setAntiAlias(true);
            mDatePaint.setTextAlign(Paint.Align.CENTER);
            mDatePaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
            mDatePaint.setTypeface(textTypeface);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
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
                    mHandPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            /*
             * Whether the timer should be running depends on whether we're visible (as well as
             * whether we're in ambient mode), so we may need to start or stop the timer.
             */
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

            mScale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * mScale),
                    (int) (mBackgroundBitmap.getHeight() * mScale), true);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);

            drawText(canvas);

            drawDate(canvas);
        }

        private void drawBackground(Canvas canvas) {
            if (mAmbient) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
            }
        }

        private void drawText(Canvas canvas) {
            String timeText;
            int hour;
            String thisIsAndela = "#TIA";
            int timeOfDay = mCalendar.get(Calendar.AM_PM);
            final String PM = "PM";
            final String AM = "AM";

            // Replaces the 0 hour with 12
            hour = (mCalendar.get(Calendar.HOUR) == 0) ? 12 : mCalendar.get(Calendar.HOUR);

            // Adds an AM or PM subscript to the time
            timeText = (timeOfDay > 0)
                    ? String.format(Locale.getDefault(), "%d:%02d %s", hour, mCalendar.get(Calendar.MINUTE), PM)
                    : String.format(Locale.getDefault(), "%d:%02d %s", hour, mCalendar.get(Calendar.MINUTE), AM);

            if (mAmbient) {
                mHandPaint.setColor(Color.WHITE);
                canvas.drawText(timeText, mCenterX, mCenterY - 50f, mHandPaint);
                canvas.drawText(thisIsAndela, mCenterX, mCenterY + 70f, mHandPaint);
            } else {
                mHandPaint.setColor(ContextCompat.getColor(AndelaWatchFace.this, R.color.andela_text_color));
                canvas.drawText(timeText, mCenterX, mCenterY - 50f, mHandPaint);
            }
        }

        private void drawDate(Canvas canvas) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

            if (mAmbient) {
                mDatePaint.setColor(Color.WHITE);
            } else {
                mDatePaint.setColor(ContextCompat.getColor(AndelaWatchFace.this, R.color.andela_text_color));
            }

            canvas.drawText(simpleDateFormat.format(mCalendar.getTime()), mCenterX, mCenterY - 20f, mDatePaint);
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

            /*
             * Whether the timer should be running depends on whether we're visible
             * (as well as whether we're in ambient mode),
             * so we may need to start or stop the timer.
             */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            AndelaWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            AndelaWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(R.id.message_update);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(R.id.message_update);
            }
        }

        /*
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}