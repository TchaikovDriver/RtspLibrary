package com.potterhsu.rtsplibrary;

import java.nio.ByteBuffer;

public interface OnFrameAvailableListener {
    void onFrameAvailable(ByteBuffer buffer, int width, int height, int channelCount);
}
