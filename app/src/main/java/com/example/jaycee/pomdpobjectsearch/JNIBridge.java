package com.example.jaycee.pomdpobjectsearch;

public class JNIBridge
{
    static
    {
        System.loadLibrary("JNI");
    }

    public static native boolean initSearch(long target, long horizon);
    public static native long getAction(long state);

    public static native boolean initSound();
    public static native boolean killSound();
    public static native void playSound(float[] src, float[] list, float gain, float pitch);
    public static native boolean stopSound();

    // Object detectors
    public static native void create(String cfg_file, String weights_file, float conf_thr);
    public static native float[] classify(long input_frame);
    public static native boolean isImageProcessed();
}
