package com.potterhsu.rtsplibrary.demo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.potterhsu.rtsplibrary.OnFrameAvailableListener;
import com.potterhsu.rtsplibrary.RtspClient;

import java.nio.ByteBuffer;

public class MainActivity extends Activity  {

    public static final String TAG = MainActivity.class.getSimpleName();
    private ImageView ivPreview;
    private EditText edtEndpoint;
    private RtspClient rtspClient;
    private GLSurfaceView mSurfaceView;
    private GLRenderer mGLRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ShaderInitializer.instance().init(this);
        mSurfaceView = findViewById(R.id.gl_surface_view);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mSurfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
        mGLRenderer = new GLRenderer(new GLFilter());
        mGLRenderer.setScaleType(ScaleType.CENTER_INSIDE);
        mSurfaceView.setRenderer(mGLRenderer);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.requestRender();
//        ivPreview = (ImageView) findViewById(R.id.ivPreview);
        edtEndpoint = (EditText) findViewById(R.id.edtEndpoint);

        rtspClient = new RtspClient(new OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(final ByteBuffer buffer, final int width, final int height, int channelCount) {
                mSurfaceView.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mGLRenderer.loadTexture(buffer, width, height);
                    }
                });
                mSurfaceView.requestRender();
            }
        });
    }

    @Override
    protected void onDestroy() {
        rtspClient.stop();
        rtspClient.destroy();
        super.onDestroy();
    }

    public void onBtnPlayClick(View view) {
        final String endpoint = edtEndpoint.getText().toString();
        if (endpoint.isEmpty()) {
            Toast.makeText(this, "Endpoint is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (rtspClient.play(endpoint) == 0)
                        break;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Connection error, retry after 3 seconds", Toast.LENGTH_SHORT).show();
                        }
                    });

                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void onBtnStopClick(View view) {
        rtspClient.stop();
    }
}
