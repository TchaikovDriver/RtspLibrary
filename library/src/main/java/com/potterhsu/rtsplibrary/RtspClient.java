package com.potterhsu.rtsplibrary;

import android.util.Log;

import java.nio.ByteBuffer;

public class RtspClient {

    private static final String TAG = RtspClient.class.getSimpleName();

    public RtspClient(NativeCallback callback) {
        if (initialize(callback) == -1)
            Log.d(TAG, "RtspClient initialize failed");
        else
            Log.d(TAG, "RtspClient initialize successfully");
    }

    public RtspClient(boolean useBuffer, NativeCallback callback) {
        if (useBuffer) {
            if (initializeWithBuffer(callback) == -1) {
                Log.e(TAG, "initialized failed");
            } else {
                Log.i(TAG, "initialized successfully");
            }
        } else {
            if (initialize(callback) == -1)
                Log.d(TAG, "RtspClient initialize failed");
            else
                Log.d(TAG, "RtspClient initialize successfully");
        }
    }

    static {
        System.loadLibrary("rtsp");
    }

    private native int initialize(NativeCallback callback);

    private native int initializeWithBuffer(NativeCallback callback);

    /**
     * Play stream synchronously.
     *
     * @param endpoint resource endpoint
     * @return 0 if exit normally or -1 otherwise
     */
    public native int play(String endpoint);

    public native void stop();

    public native void dispose();
}
