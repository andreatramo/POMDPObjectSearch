package com.example.jaycee.pomdpobjectsearch;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Size;
import android.view.SurfaceHolder;

import com.example.jaycee.pomdpobjectsearch.helpers.Logger;
import com.example.jaycee.pomdpobjectsearch.rendering.BackgroundRenderer;
import com.example.jaycee.pomdpobjectsearch.rendering.CameraRenderer;
import com.example.jaycee.pomdpobjectsearch.rendering.ObjectRenderer;
import com.google.ar.core.Session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL;

public class CameraSurface extends GLSurfaceView implements SurfaceHolder.Callback
{
    private static final String TAG = CameraSurface.class.getSimpleName();
    private static final Logger LOGGER = new Logger(TAG);

    private static final int MINIMUM_PREVIEW_SIZE = 320;

    private Context context;
    private CameraRenderer renderer;

    public CameraSurface(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        this.context = context;

        getHolder().addCallback(this);

        renderer = new CameraRenderer(context);

        setPreserveEGLContextOnPause(true);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder)
    {
        super.surfaceCreated(surfaceHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height)
    {
        super.surfaceChanged(surfaceHolder, format, width, height);
        LOGGER.i("Screen set to %dx%d pixels", width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder)
    {
        super.surfaceDestroyed(surfaceHolder);
    }

    public CameraRenderer getRenderer()
    {
        return this.renderer;
    }
}
