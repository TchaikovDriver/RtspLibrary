package com.potterhsu.rtsplibrary.demo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.Queue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 实现了{@link GLSurfaceView.Renderer}接口，掌管了OpenGL绘制流程的类。
 * <p>
 * Created by yujieliu on 2018/3/19.
 */
public class GLRenderer implements GLSurfaceView.Renderer {
    /**
     * 默认顶点坐标，不要修改里面的值，否则后果自负
     */
    private static final float[] VERTICES = {
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
    };

    /**
     * 纹理坐标，做了180度翻转，OpenGL中，y轴坐标的0表示图像的底部，但是在计算机图片里则是表示图像的顶部。所以，在纹理坐标
     * 不做翻转的情况下，显示图片会有上下颠倒的效果，因此这里做了180度翻转，才能显示"正"图像。
     */
    private static final float[] TEXTURE_COORDINATES = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    /**
     * 纹理坐标，不做任何翻转，跟顶点坐标一一对应。不做任何翻转的话，显示图片就会有倒置效果，因此命名加上FLIP_VERTICALLY前缀。
     * 离屏渲染截图时建议用这个纹理坐标。
     */
    private static final float[] FLIP_VERTICALLY_TEXTURE_COORDINATES = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
    };
    /**
     * 纹理ID
     */
    private int mTextureId = OpenGLUtils.NO_TEXTURE;
    /**
     * Surface变化时的lock，在{@link #onSurfaceChanged(GL10, int, int)}之后notify，通知外部Surface变化了。
     * 一般在页面刚创建时就需要根据Surface窗口大小获取合适尺寸的Bitmap展示的时候用。例如说，Activity启动，调用
     * , GL线程开始跑了，然后我们需要显示一张图片，图片的大小要跟GLImageView一致，
     * 但是我们不知道Surface窗口什么时候才创建好。这时候我们可以在Activity onCreate或者别的地方新建一个线程，调用
     * {@link #mSurfaceChangedLock#wait(long)}方法，notify后线程自然会继续执行下去。
     */
    public final Object mSurfaceChangedLock = new Object();
    /**
     * Vertex Buffer
     */
    private final FloatBuffer mVertexBuffer;
    /**
     * Texture Coordinate Buffer
     */
    private final FloatBuffer mTextureCoordinateBuffer;
    /**
     * 在绘制前和绘制后要在GL线程执行的操作的队列
     */
    private final Queue<Runnable> mRunOnDrawQueue;
    private final Queue<Runnable> mRunOnDrawEndQueue;
    /**
     * 滤镜效果
     */
    private GLFilter mFilter;
    /**
     * 清空画布的颜色，RGB
     */
    private float mBgColorR, mBgColorG, mBgColorB;
    /**
     * Surface窗口大小
     */
    private int mOutputWidth, mOutputHeight;
    /**
     * 展示的图片Bitmap原始宽高
     */
    private int mImageWidth, mImageHeight;
    /**
     * ScaleType
     */
    private ScaleType mScaleType;
    /**
     * 标记Surface是否初始化完成、绘制图片是否需要上下翻转的flag
     */
    private boolean mSurfaceInitialized, mFlipVertically;

    /**
     * 构造方法，默认图片是正方向显示，也就是显示出来的图片跟原图的上下方向保持一致
     *
     * @param filter 滤镜效果
     */
    public GLRenderer(GLFilter filter) {
        this(filter, false);
    }

    /**
     * 构造方法，可配置图片能否上下翻转
     *
     * @param filter         滤镜效果
     * @param flipVertically 图片是否需要旋转180度显示
     */
    public GLRenderer(GLFilter filter, boolean flipVertically) {
        mRunOnDrawQueue = new LinkedList<>();
        mRunOnDrawEndQueue = new LinkedList<>();
        mVertexBuffer = OpenGLUtils.newFloatBuffer(VERTICES);
        if (flipVertically) { // 根据上下翻转配置选择合适的纹理坐标
            mTextureCoordinateBuffer = OpenGLUtils.newFloatBuffer(FLIP_VERTICALLY_TEXTURE_COORDINATES);
        } else {
            mTextureCoordinateBuffer = OpenGLUtils.newFloatBuffer(TEXTURE_COORDINATES);
        }
        mFlipVertically = flipVertically;
        mFilter = filter;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(mBgColorR, mBgColorG, mBgColorB, 1f);
        if (mFilter != null) {
            mFilter.init();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mSurfaceInitialized = true;
        mOutputWidth = width;
        mOutputHeight = height;
        if (mFilter != null) {
            mFilter.onOutputSizeChanged(width, height);
        }
        synchronized (mSurfaceChangedLock) {
            mSurfaceChangedLock.notifyAll();
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        runAllAction(mRunOnDrawQueue);
        if (mFilter != null) {
            mFilter.onDraw(mTextureId, mVertexBuffer, mTextureCoordinateBuffer);
        }
        runAllAction(mRunOnDrawEndQueue);
    }

    private void runAllAction(final Queue<Runnable> actionQueue) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (actionQueue) {
            while (!actionQueue.isEmpty()) {
                actionQueue.poll().run();
            }
        }
    }

    /**
     * 设置清空画布的RGB颜色
     *
     * @param r RED
     * @param g GREEN
     * @param b BLUE
     */
    public void setBackgroundColor(float r, float g, float b) {
        mBgColorR = r;
        mBgColorG = g;
        mBgColorB = b;
        runOnDrawEnd(new Runnable() {
            @Override
            public void run() {
                GLES20.glClearColor(mBgColorR, mBgColorG, mBgColorB, 1f);
            }
        });
    }

    /**
     * 设置滤镜效果，这个会把设置放到GL线程中执行
     *
     * @param filter 滤镜效果
     */
    public void setFilter(final GLFilter filter) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLFilter oldFilter = mFilter;
                mFilter = filter;
                if (oldFilter != null) {
                    oldFilter.destroy();
                }
                mFilter.init();
                GLES20.glUseProgram(mFilter.getProgram());
                mFilter.onOutputSizeChanged(mOutputWidth, mOutputHeight);
            }
        });
    }

    /**
     * 删除纹理，释放资源
     */
    public void deleteTexture() {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glDeleteTextures(1, new int[]{mTextureId}, 0);
                mTextureId = OpenGLUtils.NO_TEXTURE;
            }
        });
    }

    /**
     * 在绘制前执行某个操作
     *
     * @param r 操作
     */
    protected void runOnDraw(Runnable r) {
        synchronized (mRunOnDrawQueue) {
            mRunOnDrawQueue.add(r);
        }
    }

    /**
     * 在绘制后执行某个操作
     *
     * @param r 操作
     */
    protected void runOnDrawEnd(Runnable r) {
        synchronized (mRunOnDrawEndQueue) {
            mRunOnDrawEndQueue.add(r);
        }
    }

    public void loadTexture(final ByteBuffer buffer, final int width, final int height) {
        mTextureId = OpenGLUtils.loadTexture(buffer, width, height, mTextureId);
        mImageWidth = width;
        mImageHeight = height;
        adjustImageScaling();
    }

    /**
     * 将Bitmap跟TextureId绑定起来
     *
     * @param bitmap  Bitmap
     * @param recycle 是否在绑定完成后自动回收Bitmap
     */
    public void setImageBitmap(final Bitmap bitmap, final boolean recycle) {
        if (bitmap == null) {
            return;
        }
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                Bitmap resizedBitmap = null;
                if (0 != (bitmap.getWidth() & 1)) { // 宽为奇数，重新创建一个新的宽度+1的Bitmap
                    resizedBitmap = Bitmap.createBitmap(bitmap.getWidth() + 1,
                            bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(resizedBitmap);
                    canvas.drawARGB(0, 0, 0, 0);
                    canvas.drawBitmap(bitmap, 0, 0, null);
                }
                mTextureId = OpenGLUtils.loadTexture(resizedBitmap != null ? resizedBitmap : bitmap, mTextureId, recycle);
                if (resizedBitmap != null) {
                    resizedBitmap.recycle();
                    if (recycle) {
                        bitmap.recycle();
                    }
                }
                mImageWidth = bitmap.getWidth();
                mImageHeight = bitmap.getHeight();
                adjustImageScaling();
            }
        });
    }

    /**
     * 根据Surface宽高和图片宽高，还有ScaleType来调整顶点或者纹理坐标
     */
    private void adjustImageScaling() {
        float outputHeight = mOutputHeight, outputWidth = mOutputWidth;
        float widthRatio = outputWidth / mImageWidth;
        float heightRatio = outputHeight / mImageHeight;
        float ratioMax = Math.max(widthRatio, heightRatio);
        int newImageWidth = Math.round(mImageWidth * ratioMax);
        int newImageHeight = Math.round(mImageHeight * ratioMax);

        float ratioWidth = newImageWidth / outputWidth;
        float ratioHeight = newImageHeight / outputHeight;

        float[] vertices = VERTICES;
        float[] textureCoord = mFlipVertically ? FLIP_VERTICALLY_TEXTURE_COORDINATES : TEXTURE_COORDINATES;

        if (ScaleType.CENTER_CROP == mScaleType) {
            // Center Crop，图片居中裁剪，如果图片比Surface窗口大，就只取图片中间部分显示。通过调整纹理坐标实现
            float distHorizontal = (1f - 1f / ratioWidth) / 2f;
            float distVertical = (1f - 1f / ratioHeight) / 2f;
            textureCoord = new float[]{
                    addDistance(textureCoord[0], distHorizontal), addDistance(textureCoord[1], distVertical),
                    addDistance(textureCoord[2], distHorizontal), addDistance(textureCoord[3], distVertical),
                    addDistance(textureCoord[4], distHorizontal), addDistance(textureCoord[5], distVertical),
                    addDistance(textureCoord[6], distHorizontal), addDistance(textureCoord[7], distVertical)
            };
        } else {
            // 按比例缩小图片到可以让Surface窗口完整显示，通过调整顶点坐标实现
            vertices = new float[]{
                    VERTICES[0] / ratioHeight, VERTICES[1] / ratioWidth,
                    VERTICES[2] / ratioHeight, VERTICES[3] / ratioWidth,
                    VERTICES[4] / ratioHeight, VERTICES[5] / ratioWidth,
                    VERTICES[6] / ratioHeight, VERTICES[7] / ratioWidth
            };
        }
        // 这部分代码不能放到上边分支里，因为每次设置scaleType都有必要重新调整顶点和纹理坐标
        mTextureCoordinateBuffer.clear();
        mTextureCoordinateBuffer.put(textureCoord).position(0);
        mVertexBuffer.clear();
        mVertexBuffer.put(vertices).position(0);
        /*for (int i = 0; i < 8; i+=2) {
            Log.e("WTF", String.format("texture[%d]: %f, texture[%d]: %f", i, textureCoord[i], i + 1, textureCoord[i+1]));
        }

        for (int i = 0; i < 8; i+=2) {
            Log.e("WTF", String.format("vertex[%d]: %f, vertex[%d]: %f", i, vertices[i], i + 1, vertices[i+1]));
        }*/
    }

    /**
     * 设置图片ScaleType
     *
     * @param scaleType ScaleType
     */
    public void setScaleType(ScaleType scaleType) {
        mScaleType = scaleType;
        if (mSurfaceInitialized) {
            // 如果Surface已经初始化好了，说明是在运行过程中重新设置了ScaleType，因此需要重新调整顶点和纹理坐标
            runOnDrawEnd(new Runnable() {
                @Override
                public void run() {
                    adjustImageScaling();
                }
            });
        }
    }

    private float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }

    /**
     * 获取Surface窗口宽
     *
     * @return Surface窗口宽
     */
    public int getOutputWidth() {
        return mOutputWidth;
    }

    /**
     * 获取Surface窗口高
     *
     * @return Surface窗口高
     */
    public int getOutputHeight() {
        return mOutputHeight;
    }

}
