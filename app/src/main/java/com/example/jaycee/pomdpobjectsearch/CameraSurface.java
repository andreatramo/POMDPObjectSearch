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
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.example.jaycee.pomdpobjectsearch.rendering.CameraRenderer;
import com.example.jaycee.pomdpobjectsearch.rendering.SurfaceRenderer;
import com.google.ar.core.Session;

import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraSurface extends GLSurfaceView implements SurfaceHolder.Callback
{
    private static final String TAG = CameraSurface.class.getSimpleName();

    private Context context;

    private Session session;

    private CameraRenderer renderer;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private ImageReader imageReader;

    private Semaphore cameraOpenCloseLock = new Semaphore(1);

    private String cameraId;
    private Integer sensorOrientation;

    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    public CameraSurface(Context context, AttributeSet attrs)
    {
        super(context, attrs);

        Log.i(TAG, "Surface init");

        this.context = context;

        renderer = new CameraRenderer(this);

        getHolder().addCallback(this);
        // getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        setPreserveEGLContextOnPause(true);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
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
        if(imageReader == null)
        {
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener((ActivityCamera)context, backgroundHandler);
            openCamera();
        }
        // ((ActivityCamera)context).startObjectDetector();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder)
    {
        super.surfaceDestroyed(surfaceHolder);
        // ((ActivityCamera)context).stopObjectDetector();
    }

/*    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        Log.i(TAG, "Pressed");
        final int action = event.getAction();

        switch(action)
        {
            case (MotionEvent.ACTION_DOWN):
            {
                performClick();
            }
        }
        return super.onTouchEvent(event);
    }*/

/*
    @Override
    public boolean performClick()
    {
        super.performClick();

        renderer.toggleDrawObjects();

        return true;
    }

*/

    public void openCamera()
    {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;

        CameraManager manager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
        try
        {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS))
            {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            for (String cameraId : manager.getCameraIdList())
            {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                {
                    continue;
                }
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                this.cameraId = cameraId;
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler);
        }
        catch (CameraAccessException e)
        {
            Log.e(TAG, "Cannot access the camera." + e.toString());
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    public void closeCamera()
    {
        try
        {
            cameraOpenCloseLock.acquire();

            if (null != cameraCaptureSession)
            {
                cameraCaptureSession.close();
                cameraCaptureSession = null;
            }
            if (null != cameraDevice)
            {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != imageReader)
            {
                imageReader.close();
                imageReader = null;
            }
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        }
        finally
        {
            cameraOpenCloseLock.release();
        }

        Log.i(TAG, "closeCamera");
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice camera)
        {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release();
            cameraDevice = camera;
            createCaptureSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera)
        {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private void createCaptureSession()
    {
        try
        {
            if (null == cameraDevice || null == imageReader) return;
            cameraDevice.createCaptureSession(Collections.singletonList(imageReader.getSurface()),
                    sessionStateCallback, backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "createCaptureSession " + e.toString());
        }
    }

    public void startBackgroundThread()
    {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    public void stopBackgroundThread()
    {
        backgroundThread.quitSafely();
        try
        {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        startBackgroundThread();
    }

    @Override
    public void onPause()
    {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback()
    {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session)
        {
            cameraCaptureSession = session;
            try
            {
                CaptureRequest captureRequest = createCaptureRequest();
                if (captureRequest != null)
                {
                    session.setRepeatingRequest(captureRequest, null, backgroundHandler);
                }
                else
                {
                    Log.e(TAG, "captureRequest is null");
                }
            }
            catch (CameraAccessException e)
            {
                Log.e(TAG, "onConfigured " + e.toString());
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session)
        {
            Log.e(TAG, "onConfigureFailed");
        }
    };

    private CaptureRequest createCaptureRequest()
    {
        if (null == cameraDevice) return null;
        try
        {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(imageReader.getSurface());

            return builder.build();
        }
        catch (CameraAccessException e)
        {
            Log.e(TAG, e.getMessage());

            return null;
        }
    }

    public CameraRenderer getRenderer()
    {
        return renderer;
    }

    public void setSession(Session session) { this.session = session; }
    public Session getSession() { return this.session; }
    public Integer getSensorOrientation() { return this.sensorOrientation; }
}
