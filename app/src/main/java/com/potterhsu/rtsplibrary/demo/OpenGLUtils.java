package com.potterhsu.rtsplibrary.demo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * OpenGL相关Utils，包括检测当前设备是否支持OpenGL ES 2.0、加载纹理、Shader、Program等。
 * <p>
 * Created by yujieliu on 2018/3/19.
 */
public class OpenGLUtils {
    /**
     * 默认的无纹理的常量
     */
    public static final int NO_TEXTURE = -1;

    /**
     * Checks if OpenGL ES 2.0 is supported on the current device.
     *
     * @param context the context
     * @return true, if successful
     */
    public static boolean supportsOpenGLES2(final Context context) {
        final ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }
        final ConfigurationInfo configurationInfo =
                activityManager.getDeviceConfigurationInfo();
        return configurationInfo.reqGlEsVersion >= 0x20000;
    }

    /**
     * 给定Bitmap，创建或者复用纹理，并将其与纹理绑定，该方法默认在绑定纹理后自动回收Bitmap
     *
     * @param img       Bitmap
     * @param usedTexId 纹理ID，如果当前没有纹理ID的话，传{@link #NO_TEXTURE}
     * @return 纹理ID
     */
    public static int loadTexture(final Bitmap img, final int usedTexId) {
        return loadTexture(img, usedTexId, true);
    }

    /**
     * 给定Bitmap，创建或者复用纹理，并将其与纹理绑定
     *
     * @param img       Bitmap
     * @param usedTexId 纹理ID，如果当前没有纹理ID的话，传{@link #NO_TEXTURE}
     * @param recycle   是否在绑定纹理后自动回收Bitmap
     * @return 纹理ID
     */
    public static int loadTexture(final Bitmap img, final int usedTexId, final boolean recycle) {
        int textures[] = new int[1];
        if (usedTexId == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, img, 0);
        } else { // 复用纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId);
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, img);
            textures[0] = usedTexId;
        }
        if (recycle) {
            img.recycle();
        }
        return textures[0];
    }

    public static int loadTexture(ByteBuffer buffer, int width, int height, int usedTexId) {
        int[] textures = new int[1];
        if (usedTexId == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        } else { // 复用纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0,0, width, height, GLES20.GL_RGBA,
                     GLES20.GL_UNSIGNED_BYTE, buffer);
            textures[0] = usedTexId;
        }
        return textures[0];
    }

    /**
     * 给定IntBuffer以及图片宽高，将其转换成Bitmap，最后创建或者复用纹理
     *
     * @param data      IntBuffer，保存图片ARGB的数据
     * @param width     宽
     * @param height    高
     * @param usedTexId 复用的纹理ID，如果没有，传{@link #NO_TEXTURE}
     * @return 纹理ID
     */
    public static int loadTextureAsBitmap(final IntBuffer data, final int width, final int height, final int usedTexId) {
        Bitmap bitmap = Bitmap
                .createBitmap(data.array(), width, height, Bitmap.Config.ARGB_8888);
        return loadTexture(bitmap, usedTexId);
    }

    /**
     * 加载Shader
     *
     * @param strSource Shader字符串
     * @param iType     Shader类型
     * @return Shader ID
     */
    public static int loadShader(final String strSource, final int iType) {
        int[] compiled = new int[1];
        int iShader = GLES20.glCreateShader(iType);
        GLES20.glShaderSource(iShader, strSource);
        GLES20.glCompileShader(iShader);
        GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e("Load Shader Failed", "Compilation\n" + GLES20.glGetShaderInfoLog(iShader));
            return 0;
        }
        return iShader;
    }

    /**
     * 加载Shader Program
     *
     * @param strVSource Vertex Shader
     * @param strFSource Fragment Shader
     * @return Shader Program ID
     */
    public static int loadProgram(final String strVSource, final String strFSource) {
        int iVShader;
        int iFShader;
        int iProgId;
        int[] link = new int[1];
        iVShader = loadShader(strVSource, GLES20.GL_VERTEX_SHADER);
        if (iVShader == 0) {
            Log.e("Load Program", "Vertex Shader Failed");
            return 0;
        }
        iFShader = loadShader(strFSource, GLES20.GL_FRAGMENT_SHADER);
        if (iFShader == 0) {
            Log.e("Load Program", "Fragment Shader Failed");
            return 0;
        }

        iProgId = GLES20.glCreateProgram();

        GLES20.glAttachShader(iProgId, iVShader);
        GLES20.glAttachShader(iProgId, iFShader);

        GLES20.glLinkProgram(iProgId);

        GLES20.glGetProgramiv(iProgId, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.e("Load Program", "Linking Failed");
            return 0;
        }
        GLES20.glDeleteShader(iVShader);
        GLES20.glDeleteShader(iFShader);
        return iProgId;
    }

    /**
     * 给定Asset资源文件路径，加载Shader字符串
     *
     * @param file    asset资源文件路径
     * @param context Context
     * @return Shader String
     */
    public static String loadShader(String file, Context context) {
        try {
            AssetManager assetManager = context.getAssets();
            InputStream ims = assetManager.open(file);

            String re = convertStreamToString(ims);
            ims.close();
            return re;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    private static String convertStreamToString(InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    /**
     * 给定float数组，创建FloatBuffer
     *
     * @param buffer FloatBuffer
     * @return FloatBuffer
     */
    public static FloatBuffer newFloatBuffer(float[] buffer) {
        FloatBuffer ret = ByteBuffer
                .allocateDirect(4 * buffer.length)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(buffer);
        ret.position(0);
        return ret;
    }
}
