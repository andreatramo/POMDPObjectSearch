/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
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

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Size;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.example.jaycee.pomdpobjectsearch.env.ImageUtils;
import com.example.jaycee.pomdpobjectsearch.env.Logger;

import java.nio.ByteBuffer;

//public abstract class CameraActivity
//    implements OnImageAvailableListener{


//  @Override
//  protected void onCreate(final Bundle savedInstanceState) {
//    LOGGER.d("onCreate " + this);
//    super.onCreate(null);
//    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//
//    setContentView(R.layout.activity_camera);
//
//    if (hasPermission()) {
//      setFragment();
//    } else {
//      requestPermission();
//    }
//  }







//  @Override
//  public synchronized void onStart() {
//    LOGGER.d("onStart " + this);
//    super.onStart();
//  }
//
//  @Override
//  public synchronized void onResume() {
//    LOGGER.d("onResume " + this);
//    super.onResume();
//
//    handlerThread = new HandlerThread("inference");
//    handlerThread.start();
//    handler = new Handler(handlerThread.getLooper());
//  }
//
//  @Override
//  public synchronized void onPause() {
//    LOGGER.d("onPause " + this);
//
//    if (!isFinishing()) {
//      LOGGER.d("Requesting finish");
//      finish();
//    }
//
//    handlerThread.quitSafely();
//    try {
//      handlerThread.join();
//      handlerThread = null;
//      handler = null;
//    } catch (final InterruptedException e) {
//      LOGGER.e(e, "Exception!");
//    }
//
//    super.onPause();
//  }
//
//  @Override
//  public synchronized void onStop() {
//    LOGGER.d("onStop " + this);
//    super.onStop();
//  }
//
//  @Override
//  public synchronized void onDestroy() {
//    LOGGER.d("onDestroy " + this);
//    super.onDestroy();
//  }



//  @Override
//  public void onRequestPermissionsResult(
//      final int requestCode, final String[] permissions, final int[] grantResults) {
//    if (requestCode == PERMISSIONS_REQUEST) {
//      if (grantResults.length > 0
//          && grantResults[0] == PackageManager.PERMISSION_GRANTED
//          && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
//        setFragment();
//      } else {
//        requestPermission();
//      }
//    }
//  }

//  private boolean hasPermission() {
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//      return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
//          checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED;
//    } else {
//      return true;
//    }
//  }
//
//  private void requestPermission() {
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//      if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
//          shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
//        Toast.makeText(CameraActivity.this,
//            "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
//      }
//      requestPermissions(new String[] {PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
//    }
//  }

//  // Returns true if the device supports the required hardware level, or better.
//  private boolean isHardwareLevelSupported(
//      CameraCharacteristics characteristics, int requiredLevel) {
//    int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
//    if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
//      return requiredLevel == deviceLevel;
//    }
//    // deviceLevel is not LEGACY, can use numerical sort
//    return requiredLevel <= deviceLevel;
//  }

//  private String chooseCamera() {
//    final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
//    try {
//      for (final String cameraId : manager.getCameraIdList()) {
//        final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
//
//        // We don't use a front facing camera in this sample.
//        final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
//        if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
//          continue;
//        }
//
//        final StreamConfigurationMap map =
//            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//
//        if (map == null) {
//          continue;
//        }
//
//        // Fallback to camera1 API for internal cameras that don't have full support.
//        // This should help with legacy situations where using the camera2 API causes
//        // distorted or otherwise broken previews.
//        useCamera2API = (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
//            || isHardwareLevelSupported(characteristics,
//                                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
//        LOGGER.i("Camera API lv2?: %s", useCamera2API);
//        return cameraId;
//      }
//    } catch (CameraAccessException e) {
//      LOGGER.e(e, "Not allowed to access camera");
//    }
//
//    return null;
//  }

//  protected void setFragment() {
//    String cameraId = chooseCamera();
//
//    Fragment fragment;
//    if (useCamera2API) {
//      CameraConnectionFragment camera2Fragment =
//          CameraConnectionFragment.newInstance(
//              new CameraConnectionFragment.ConnectionCallback() {
//                @Override
//                public void onPreviewSizeChosen(final Size size, final int rotation) {
//                  previewHeight = size.getHeight();
//                  previewWidth = size.getWidth();
//                  CameraActivity.this.onPreviewSizeChosen(size, rotation);
//                }
//              },
//              this,
//              getLayoutId(),
//              getDesiredPreviewFrameSize());
//
//      camera2Fragment.setCamera(cameraId);
//      fragment = camera2Fragment;
//    } else {
//      fragment =
//          new LegacyCameraConnectionFragment(this, getLayoutId(), getDesiredPreviewFrameSize());
//    }
//
//    getFragmentManager()
//        .beginTransaction()
//        .replace(R.id.container, fragment)
//        .commit();
//  }


//  public void onSetDebug(final boolean debug) {}

//  @Override
//  public boolean onKeyDown(final int keyCode, final KeyEvent event) {
//    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
//      debug = !debug;
//      requestRender();
//      onSetDebug(debug);
//      return true;
//    }
//    return super.onKeyDown(keyCode, event);
//  }



//  protected abstract void processImage();
//
//  protected abstract int getLayoutId();
//  protected abstract Size getDesiredPreviewFrameSize();
//}
