package com.potterhsu.rtsplibrary;

import java.nio.ByteBuffer;

public interface NativeCallback {
    /**
     *
     * @param frame Frame data
     * @param nChannel number of channels
     * @param width width
     * @param height height
     */
    void onFrame(byte[] frame, int nChannel, int width, int height);

    void onFrameAvailable(ByteBuffer buffer, int width, int height, int channelCount);
}
