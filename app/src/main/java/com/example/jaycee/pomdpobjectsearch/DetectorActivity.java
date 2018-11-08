/*
 * Copyright 2018 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jaycee.pomdpobjectsearch;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Size;
import android.util.TypedValue;
import android.view.Surface;
import android.widget.Toast;

import com.example.jaycee.pomdpobjectsearch.OverlayView.DrawCallback;
import com.example.jaycee.pomdpobjectsearch.env.BorderedText;
import com.example.jaycee.pomdpobjectsearch.env.ImageUtils;
import com.example.jaycee.pomdpobjectsearch.env.Logger;
import com.example.jaycee.pomdpobjectsearch.rendering.SurfaceRenderer;
import com.example.jaycee.pomdpobjectsearch.tracking.MultiBoxTracker;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity implements OnImageAvailableListener {

    private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged SSD model.
  private static final int TF_OD_API_INPUT_SIZE = 300;
  private static final boolean TF_OD_API_IS_QUANTIZED = true;
  private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  private enum DetectorMode {
    TF_OD_API;
  }

  private static final DetectorMode MODE = DetectorMode.TF_OD_API;

  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;

  private static final boolean MAINTAIN_ASPECT = false;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;

  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;

  private BorderedText borderedText;

/* ----------------- from CameraActivity ----------------- */

    private boolean debug = false;

    private boolean isProcessingFrame = false;
    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    /* ----------------- mine variable ----------------- */

    private static final String TAG = ObjectDetector.class.getSimpleName();
    private static final int O_NOTHING = 0;
    //index of the found object. code=0 if no objects were found.
    private int objectCode = O_NOTHING;

    private Handler handler = new Handler();
    private boolean stop = false;
    private SurfaceRenderer renderer;
    private Context context;


    /**
     * Callback for Camera2 API
     */
    @Override
    public void onImageAvailable(final ImageReader reader) {

        this.renderer = renderer;
        this.context = context;

        //We need wait until we have some size from onPreviewSizeChosen
//        if (previewWidth == 0 || previewHeight == 0) {
//            return;
//        }
//        if (rgbBytes == null) {
//            rgbBytes = new int[previewWidth * previewHeight];
//        }
        try {
            final Image image = reader.acquireLatestImage();
//
//            if (image == null) {
//                return;
//            }

            if (isProcessingFrame) {
                image.close();
                return;
            }
            isProcessingFrame = true;
//            Trace.beginSection("imageAvailable");
            final Image.Plane[] planes = image.getPlanes();
            fillBytes(planes, yuvBytes);
            yRowStride = planes[0].getRowStride();
            final int uvRowStride = planes[1].getRowStride();
            final int uvPixelStride = planes[1].getPixelStride();

            imageConverter =
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageUtils.convertYUV420ToARGB8888(
                                    yuvBytes[0],
                                    yuvBytes[1],
                                    yuvBytes[2],
                                    previewWidth,
                                    previewHeight,
                                    yRowStride,
                                    uvRowStride,
                                    uvPixelStride,
                                    rgbBytes);
                        }
                    };

            postInferenceCallback =
                    new Runnable() {
                        @Override
                        public void run() {
                            image.close();
                            isProcessingFrame = false;
                        }
                    };

            processImage();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
            Trace.endSection();
            return;
        }
        Trace.endSection();
    }

  public void onPreviewSizeChosen(final Size size, final int rotation, Context context) {

    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(context);

    int cropSize = TF_OD_API_INPUT_SIZE;

    try {
      detector =
          TFLiteObjectDetectionAPIModel.create(
              context.getAssets(),
              TF_OD_API_MODEL_FILE,
              TF_OD_API_LABELS_FILE,
              TF_OD_API_INPUT_SIZE,
              TF_OD_API_IS_QUANTIZED);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      LOGGER.e("Exception initializing classifier!", e);
      Toast toast =
          Toast.makeText(
              context, "Classifier could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
//      finish();
    }


    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    Activity activity = (Activity) context;

    trackingOverlay = (OverlayView) activity.findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            tracker.draw(canvas);
            if (isDebug()) {
              tracker.drawDebug(canvas);
            }
          }
        });

    addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
              if (!isDebug()) {
                  return;
              }
              final Bitmap copy = cropCopyBitmap;
              if (copy == null) {
                  return;
              }

              final int backgroundColor = Color.argb(100, 0, 0, 0);
              canvas.drawColor(backgroundColor);

              final Matrix matrix = new Matrix();
              final float scaleFactor = 2;
              matrix.postScale(scaleFactor, scaleFactor);
              matrix.postTranslate(
                      canvas.getWidth() - copy.getWidth() * scaleFactor,
                      canvas.getHeight() - copy.getHeight() * scaleFactor);
              canvas.drawBitmap(copy, matrix, new Paint());

              final Vector<String> lines = new Vector<String>();
              if (detector != null) {
                  final String statString = detector.getStatString();
                  final String[] statLines = statString.split("\n");
                  for (final String line : statLines) {
                      lines.add(line);
                  }
              }
              lines.add("");

              lines.add("Frame: " + previewWidth + "x" + previewHeight);
              lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
              lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
              lines.add("Rotation: " + sensorOrientation);
              lines.add("Inference time: " + lastProcessingTimeMs + "ms");

              borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
          }
        });
  }

  OverlayView trackingOverlay;

  protected void processImage() {

      objectCode = O_NOTHING;

      ++timestamp;
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
    if(computingDetection) {
        readyForNextImage();
        return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    if(luminanceCopy == null) {
        luminanceCopy = new byte[originalLuminance.length];
    }
    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
        ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.i("Running detection on image " + currTimestamp);
            final long startTime = SystemClock.uptimeMillis();
            final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            switch (MODE) {
              case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
            }

            final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

            for (final Classifier.Recognition result : results) {
              final RectF location = result.getLocation();
              if (location != null && result.getConfidence() >= minimumConfidence) {
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
        });
  }

    /**
     * The getCode method return the actual code of the found object.
     *
     * @return int The actual code.
     */
    public int getCode() {
        return objectCode;
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected int getLuminanceStride() {
        return yRowStride;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                LOGGER.d("Initializing buffer %d at size %d", i, buffer.capacity());
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }

    public boolean isDebug() {
        return debug;
    }

    public void requestRender() {
        Activity activity = (Activity) context;
        final OverlayView overlay = (OverlayView) activity.findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.postInvalidate();
        }
    }

    public void addCallback(final OverlayView.DrawCallback callback) {
        Activity activity = (Activity) context;
        final OverlayView overlay = (OverlayView) activity.findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.addCallback(callback);
        }
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation() {
        Activity activity = (Activity) context;
        switch (activity.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

  protected int getLayoutId() {
    return R.layout.activity_camera;
  }

  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  public void onSetDebug(final boolean debug) {
    detector.enableStatLogging(debug);
  }
}
