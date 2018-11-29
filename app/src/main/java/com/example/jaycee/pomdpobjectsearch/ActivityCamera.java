package com.example.jaycee.pomdpobjectsearch;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.jaycee.pomdpobjectsearch.helpers.ImageUtils;
import com.example.jaycee.pomdpobjectsearch.helpers.BorderedText;
import com.example.jaycee.pomdpobjectsearch.helpers.Logger;
import com.example.jaycee.pomdpobjectsearch.rendering.CameraRenderer;
import com.example.jaycee.pomdpobjectsearch.tracking.MultiBoxTracker;
import com.example.jaycee.pomdpobjectsearch.views.OverlayView;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.MissingGlContextException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

public class ActivityCamera extends ActivityCameraBase implements FrameListener, SoundHandler
{
    private enum DetectorMode {
        TF_OD_API
    }
    private static final String TAG = ActivityCamera.class.getSimpleName();
    private static final Logger LOGGER = new Logger(TAG);
    // Configuration values for the prepackaged SSD model.
    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final boolean SAVE_PREVIEW_BITMAP = false;private static final String TF_OD_API_MODEL_FILE = "mobilenet/detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/mobilenet/coco_labels_list.txt";
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final float TEXT_SIZE_DIP = 10;

    //choose this size, because are the same of the image size given by the listener
    //change all the size in the code, so that they take this size
    private Size DESIRED_PREVIEW_SIZE  = new Size(1600, 1200);

    private CameraSurface surfaceView;
    private CameraRenderer renderer;
    private OverlayView trackingOverlay;
    private BorderedText borderedText;
    private MultiBoxTracker tracker;

    private Classifier detector;

    private Bitmap rgbFrameBitmap;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private Integer sensorOrientation;

    private long lastProcessingTimeMs;
    private long timestamp = 0;

    private boolean computingDetection = false;

    private byte[] luminanceCopy;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        surfaceView = findViewById(R.id.surfaceview);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        try
        {
            session.resume();
        }
        catch (CameraNotAvailableException e)
        {
            session = null;
            LOGGER.e("Camera not available", e);
            return;
        }
        surfaceView.getRenderer().setSession(session);
        // runFrameRendererInBackground(new FrameRenderRunnable());
        surfaceView.onResume();
    }

    @Override
    public void onPause()
    {
        surfaceView.onPause();
        if(session != null)
        {
            session.pause();
        }

        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPreviewFrame(Frame frame)
    {
        surfaceView.getRenderer().setFrame(frame);
        surfaceView.requestRender();
    }

    @Override
    public void onSoundGenerated(Session session)
    {
/*        try
        {
            session.setCameraTextureName(surfaceView.getRenderer().getNativeARTextureId());
            Frame frame = session.update();
        }
        catch(CameraNotAvailableException e)
        {
            LOGGER.e("Camera not available ", e);
        }*/
    }

    @Override
    protected void processImage()
    {
/*        timestamp++;
        final long currTimestamp = timestamp;
        byte[] originalLuminance = getLuminance();
        tracker.onFrame(
                previewWidth,
                previewHeight,
                getLuminanceStride(),
                sensorOrientation,
                originalLuminance,
                timestamp);
         trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection)
        {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP)
        {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        if(detector == null)
        {
            LOGGER.w("Detector not initialised. ");
            return;
        }
        runInBackground(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE)
                        {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results)
                        {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence)
                            {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }

                        tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                        trackingOverlay.postInvalidate();

                        requestRender();
                        computingDetection = false;
                    }
                });*/
    }

    @Override
    protected void renderFrame(Frame frame)
    {
        frameListener.onPreviewFrame(frame);
    }

    @Override
    protected void generateSound()
    {
        // soundHandler.onSoundGenerated(session);
    }

    class FrameRenderRunnable implements Runnable
    {
        @Override
        public void run()
        {
            while(true)
            {
                LOGGER.i("HERE");
                if(session == null)
                {
                    LOGGER.e("Session is null");
                    SystemClock.sleep(2000);
                    continue;
                }
                if(!surfaceView.getRenderer().isRendererInitialised())
                {
                    LOGGER.e("Renderer unavailable");
                    SystemClock.sleep(2000);
                    continue;
                }
                try
                {
                    session.setCameraTextureName(surfaceView.getRenderer().getCameraTextureId());
                    Frame frame = session.update();
                    renderFrame(frame);
                    processImage();
                    generateSound();
                }
                catch(CameraNotAvailableException e)
                {
                    LOGGER.e("Camera not available: ", e);
                }
/*                catch(MissingGlContextException e)
                {
                    LOGGER.e("Context unavailable: ", e);
                }*/
            }
        }
    }
}
