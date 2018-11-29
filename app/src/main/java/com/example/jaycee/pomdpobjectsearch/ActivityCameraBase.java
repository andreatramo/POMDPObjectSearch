package com.example.jaycee.pomdpobjectsearch;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;
import android.widget.Toolbar;

import com.example.jaycee.pomdpobjectsearch.helpers.ImageUtils;
import com.example.jaycee.pomdpobjectsearch.views.OverlayView;
import com.example.jaycee.pomdpobjectsearch.helpers.Logger;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public abstract class ActivityCameraBase extends Activity implements FrameHandler
{
    private static final String TAG = ActivityCameraBase.class.getSimpleName();
    private static final Logger LOGGER = new Logger(TAG);

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final String STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    private static final int PERMISSIONS_REQUEST = 0;

    private Handler handler;
    private HandlerThread handlerThread;
    private int[] imageBytes = null;

    private boolean debug = false;
    private boolean isProcessingFrame = false;
    private boolean isGeneratingSound = false;
    private boolean requestARCoreInstall = true;

    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private Runnable postInferenceCallback, postSoundGenerationCallback;
    private Runnable imageConverter;

    protected FrameHandler frameHandler;
    protected SoundHandler soundHandler;

    protected Session session;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);

        frameHandler = (FrameHandler)this;
        soundHandler = (SoundHandler)this;
    }

    protected int[] getRgbBytes()
    {
        if(imageConverter != null)
        {
            imageConverter.run();
        }
        return imageBytes;
    }

    @Override
    public synchronized void onStart()
    {
        LOGGER.d("onStart " + this);
        super.onStart();
        imageBytes = new int[previewWidth*previewHeight];
    }

    @Override
    public synchronized void onResume()
    {
        super.onResume();

        if(!hasPermission())
        {
            requestPermission();
        }

        if(session == null)
        {
            try
            {
                switch(ArCoreApk.getInstance().requestInstall(this, requestARCoreInstall))
                {
                    case INSTALLED:
                        break;
                    case INSTALL_REQUESTED:
                        requestARCoreInstall = false;
                        return;
                }

                session = new Session(this);

                // Set config settings
                Config conf = new Config(session);
                conf.setFocusMode(Config.FocusMode.AUTO);
                session.configure(conf);
            }
            catch(UnavailableUserDeclinedInstallationException | UnavailableArcoreNotInstalledException e)
            {
                LOGGER.e("Please install ARCore.");
                return;
            }
            catch(UnavailableDeviceNotCompatibleException e)
            {
                LOGGER.e("This device does not support ARCore.");
                return;
            }
            catch(UnavailableApkTooOldException e)
            {
                LOGGER.e("Please update the app.");
                return;
            }
            catch(UnavailableSdkTooOldException e)
            {
                LOGGER.e("Please update ARCore. ");
                return;
            }
            catch(Exception e)
            {
                Log.e(TAG, "Failed to create AR session.");
            }
        }

        handlerThread = new HandlerThread("InferenceThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause()
    {
        if(!isFinishing())
        {
            finish();
        }

        handlerThread.quitSafely();
        try
        {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        }
        catch(final InterruptedException e)
        {
            LOGGER.e("Exception onPause: " + e);
        }

        super.onPause();
    }

    @Override
    public synchronized void onStop()
    {
        LOGGER.d("onStop " + this);
        super.onStop();
    }

    @Override
    public synchronized void onDestroy()
    {
        LOGGER.d("onDestroy" + this);
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r)
    {
        if(handler != null)
        {
            handler.post(r);
        }
    }

    private boolean hasPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            return checkSelfPermission(CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(STORAGE_PERMISSION) == PackageManager.PERMISSION_GRANTED;
        }
        else
        {
            return true;
        }
    }

    private void requestPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (shouldShowRequestPermissionRationale(CAMERA_PERMISSION) ||
                    shouldShowRequestPermissionRationale(STORAGE_PERMISSION))
            {
                Toast.makeText(ActivityCameraBase.this,
                        "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[] {CAMERA_PERMISSION, STORAGE_PERMISSION}, PERMISSIONS_REQUEST);
        }
    }

    protected void readyForNextImage()
    {
        if (postInferenceCallback != null)
        {
            postInferenceCallback.run();
        }
    }

    protected int getScreenOrientation()
    {
        switch (getWindowManager().getDefaultDisplay().getRotation())
        {
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

    public boolean isDebug() {
        return debug;
    }

/*    public void requestRender() {
        final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.postInvalidate();
        }
    }

    public void addCallback(final OverlayView.DrawCallback callback) {
        final OverlayView overlay = (OverlayView) findViewById(R.id.debug_overlay);
        if (overlay != null) {
            overlay.addCallback(callback);
        }
    }*/

    protected abstract void processImage();
    protected abstract void generateSound();
    protected abstract void renderFrame();
}
