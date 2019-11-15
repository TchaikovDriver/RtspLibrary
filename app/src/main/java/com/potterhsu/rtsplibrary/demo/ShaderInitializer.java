package com.potterhsu.rtsplibrary.demo;

import android.content.Context;

/**
 * 加载所有Shader字符串的初始化类。建议在{@link android.app.Application#attachBaseContext(Context)}时调用。
 * 这个类是否需要存在还是个问题，我希望shader能直接写在asset下的glsl文件里而不是写在{@link GLFilter}下，这样便于跨平台。
 * 但是这个初始化会一次性加载所有的Shader，也不是合理的设计。加个 fixme 标记一下。
 * <p>
 * Created by yujieliu on 2018/3/19.
 */
public class ShaderInitializer {
    /**
     * 纯正无添加的Vertex 和 Fragment Shader
     */
    private String noFilterVertexShader;
    private String noFilterFragmentShader;
    /**
     * 调整颜色的Fragment Shader
     */
    private String colorMatrixFragmentShader;
    /**
     * 灰色滤镜Fragment Shader
     */
    private String grayColorFragmentShader;
    /**
     * 调节对比度的Fragment Shader
     */
    private String contrastFragmentShader;
    /**
     * 马赛克Fragment Shader
     */
    private String mosaicFragmentShader;

    public static ShaderInitializer instance() {
        return Holder.INSTANCE;
    }

    public void init(Context context) {
        noFilterVertexShader = OpenGLUtils.loadShader("no_filter_vs.glsl", context);
        noFilterFragmentShader = OpenGLUtils.loadShader("no_filter_fs.glsl", context);
//        colorMatrixFragmentShader = OpenGLUtils.loadShader("color_matrix_filter_fs.glsl", context);
//        grayColorFragmentShader = OpenGLUtils.loadShader("gray_filter_fs.glsl", context);
//        contrastFragmentShader = OpenGLUtils.loadShader("contrast_filter_fs.glsl", context);
//        mosaicFragmentShader = OpenGLUtils.loadShader("mosaic_filter_fs.glsl", context);
    }

    public String getNoFilterVertexShader() {
        return noFilterVertexShader;
    }

    public String getNoFilterFragmentShader() {
        return noFilterFragmentShader;
    }

    public String getColorMatrixFragmentShader() {
        return colorMatrixFragmentShader;
    }

    public String getGrayColorFragmentShader() {
        return grayColorFragmentShader;
    }

    public String getContrastFragmentShader() {
        return contrastFragmentShader;
    }

    public String getMosaicFragmentShader() {
        return mosaicFragmentShader;
    }

    private static class Holder {
        private static final ShaderInitializer INSTANCE = new ShaderInitializer();
    }
}
