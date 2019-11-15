package com.potterhsu.rtsplibrary;

public class RtspClient {
    private final long mNativeInstance;
    private boolean mDestroyed;

    public RtspClient(OnFrameAvailableListener callback) {
        mNativeInstance = nativeCreate(callback);
        if (mNativeInstance == -1) {
            throw new IllegalStateException("Cannot initialize RtspClient!");
        }
    }

    public int play(String endPoint) {
        checkAlive();
        return play(mNativeInstance, endPoint);
    }

    public void stop() {
        checkAlive();
        stop(mNativeInstance);
    }

    private void checkAlive() {
        if (mDestroyed) {
            throw new IllegalStateException("Current RtspClient has been destroyed!");
        }
    }

    public void destroy() {
        mDestroyed = true;
        destroy(mNativeInstance);
    }

    static {
        System.loadLibrary("rtsp");
    }

    private static native long nativeCreate(OnFrameAvailableListener callback);

    /**
     * Play stream synchronously.
     *
     * @param endpoint resource endpoint
     * @return 0 if exit normally or -1 otherwise
     */
    private static native int play(long nativeInstance, String endpoint);

    private static native void stop(long nativeInstance);

    private static native void destroy(long nativeInstance);
}
