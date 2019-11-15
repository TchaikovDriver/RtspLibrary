package com.potterhsu.rtsplibrary.demo;

import android.graphics.PointF;
import android.opengl.GLES20;

import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 滤镜效果基类
 * <p>
 * 这个基类没有任何滤镜效果，原汁原味显示图片。继承该类自己写Shader的话需要遵守以下约束：
 * <p>
 * 1. 顶点坐标attribute命名必须为position
 * <p>
 * 2. 原图sampler2D纹理命名必须为inputImageTexture
 * <p>
 * 3. 纹理坐标attribute命名必须为textureCoordinate
 * <p>
 * Created by yujieliu on 2018/3/19.
 */
public class GLFilter {
    protected static final String NO_FILTER_VERTEX_SHADER = ShaderInitializer.instance().getNoFilterVertexShader();
    protected static final String NO_FILTER_FRAGMENT_SHADER = ShaderInitializer.instance().getNoFilterFragmentShader();
    /**
     * 在绘制前执行的动作队列
     */
    private final Queue<Runnable> mRunOnDrawQueue;
    /**
     * Vertex Shader 和 Fragment Shader的代码字符串
     */
    private final String mVertexShader, mFragmentShader;
    /**
     * 表示当前滤镜是否初始化成功
     */
    private boolean mHasInitialized;
    /**
     * Shader程序ID，Vertex Shader的attribute position，textureCoordinate location，还有uniform texture location
     */
    private int mProgramId, mGLAttribPosition, mGLUniformTexture, mGLAttribTextureCoordinate;
    /**
     * surface窗口宽高
     */
    private int mOutputWidth, mOutputHeight;

    public GLFilter() {
        this(NO_FILTER_VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER);
    }

    /**
     * 指定Vertex Shader和Fragment Shader的构造方法，一般由子类调用，详见类头注释。
     *
     * @param vertexShader   Vertex Shader
     * @param fragmentShader Fragment Shader
     */
    public GLFilter(String vertexShader, String fragmentShader) {
        mVertexShader = vertexShader;
        mFragmentShader = fragmentShader;
        mRunOnDrawQueue = new LinkedList<>();
    }

    /**
     * 初始化，干一些获取attribute和uniform location以及赋值的操作
     */
    public final void init() {
        onInit();
        mHasInitialized = true;
        onInitialized();
    }

    /**
     * 初始化操作，建议在这里获取Shader里边变量的location
     */
    protected void onInit() {
        if (mProgramId != 0) { // 可能是重新创建Surface了
            destroy();
        }
        mProgramId = OpenGLUtils.loadProgram(mVertexShader, mFragmentShader);
        mGLAttribPosition = GLES20.glGetAttribLocation(mProgramId, "position");
        mGLUniformTexture = GLES20.glGetUniformLocation(mProgramId, "inputImageTexture");
        mGLAttribTextureCoordinate = GLES20.glGetAttribLocation(mProgramId, "inputTextureCoordinate");
    }

    /**
     * 初始化收尾，建议这里给可以确定值的uniform赋值
     */
    protected void onInitialized() {

    }

    public void onOutputSizeChanged(int width, int height) {
        mOutputWidth = width;
        mOutputHeight = height;
    }

    /**
     * 绘制逻辑，这里会把传入的顶点坐标和纹理坐标还有纹理传给OpenGL并绘制
     *
     * @param textureId               纹理ID
     * @param vertexBuffer            顶点坐标Buffer
     * @param textureCoordinateBuffer 纹理坐标buffer
     */
    public void onDraw(int textureId, FloatBuffer vertexBuffer, FloatBuffer textureCoordinateBuffer) {
        GLES20.glUseProgram(mProgramId);
        runPendingOnDrawAction();
        if (!mHasInitialized) {
            return;
        }
        // 指定顶点坐标数据并启用attribute position
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        // 指定纹理坐标数据并启用attribute textureCoordinate
        textureCoordinateBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0, textureCoordinateBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);

        if (textureId != OpenGLUtils.NO_TEXTURE) {
            // 如果有纹理的话就绑定纹理，先启用第0个纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            // 绑定纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0); // 绘制Texture0
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    protected void runPendingOnDrawAction() {
        while (!mRunOnDrawQueue.isEmpty()) {
            mRunOnDrawQueue.poll().run();
        }
    }

    protected void runOnDraw(Runnable r) {
        synchronized (mRunOnDrawQueue) {
            mRunOnDrawQueue.add(r);
        }
    }

    protected void setInteger(final int location, final int intValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1i(location, intValue);
            }
        });
    }

    protected void setFloat(final int location, final float floatValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1f(location, floatValue);
            }
        });
    }

    protected void setFloatVec2(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform2fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatVec3(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform3fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatVec4(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform4fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setFloatArray(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1fv(location, arrayValue.length, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    protected void setPoint(final int location, final PointF point) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                float[] vec2 = new float[2];
                vec2[0] = point.x;
                vec2[1] = point.y;
                GLES20.glUniform2fv(location, 1, vec2, 0);
            }
        });
    }

    protected void setUniformMatrix3f(final int location, final float[] matrix) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glUniformMatrix3fv(location, 1, false, matrix, 0);
            }
        });
    }

    protected void setUniformMatrix4f(final int location, final float[] matrix) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glUniformMatrix4fv(location, 1, false, matrix, 0);
            }
        });
    }

    public int getProgram() {
        return mProgramId;
    }

    public void destroy() {
        mHasInitialized = false;
        GLES20.glDeleteProgram(mProgramId);
        mProgramId = 0;
        onDestroy();
    }

    protected void onDestroy() {

    }

    public int getOutputWidth() {
        return mOutputWidth;
    }

    public int getOutputHeight() {
        return mOutputHeight;
    }
}
