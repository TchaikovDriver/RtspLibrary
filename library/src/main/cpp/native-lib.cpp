#include <jni.h>
#include <string>
#include <android/log.h>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libswscale/swscale.h>
}

const char *TAG = __FILE__;

class JNIConnector {
private:
    jmethodID onFrameAvailableMid;
    jobject callbackObj;
    jobject pixelBuffer; // ByteBuffer
    uint8_t *backBufferForPixels; // ByteBuffer's back array
    int width, height, channelCount;
    size_t capacity;
    bool run;

    void releasePixelBuffer(JNIEnv *env) {
        if (pixelBuffer) {
            env->DeleteGlobalRef(pixelBuffer);
            pixelBuffer = nullptr;
            delete[] backBufferForPixels;
            backBufferForPixels = nullptr;
        }
    }

    void allocatePixelBuffer(JNIEnv *env) {
        auto capacity = static_cast<uint32_t >(width * height * channelCount);
        backBufferForPixels = new uint8_t[capacity];
        pixelBuffer = env->NewGlobalRef(env->NewDirectByteBuffer(backBufferForPixels, capacity));
    }

public:
    JNIConnector(JNIEnv *env, jobject callback):
    pixelBuffer(nullptr),backBufferForPixels(nullptr), onFrameAvailableMid(nullptr),
    width(0), height(0), channelCount(0), capacity(0), run(true){
        callbackObj = env->NewGlobalRef(callback);
        jclass callbackClazz = env->GetObjectClass(callback);
        onFrameAvailableMid = env->GetMethodID(callbackClazz, "onFrameAvailable",
                                                             "(Ljava/nio/ByteBuffer;III)V");
    }

    bool isRunning() {
        return run;
    }

    void start() {
        run = true;
    }

    void stop() {
        run = false;
    }

    void updateFrameSize(JNIEnv* env, int width, int height, int channelCount) {
        __android_log_print(ANDROID_LOG_INFO, TAG, "updateFrameSize w:%d, h:%d", width, height);
        if (this->width != width || this->height != height || this->channelCount != channelCount) {
            releasePixelBuffer(env);
            this->width = width;
            this->height = height;
            this->channelCount = channelCount;
            capacity = static_cast<size_t>(width * height * channelCount);
            allocatePixelBuffer(env);
        }
    }

    void onFrameAvailable(JNIEnv* env, const uint8_t *buf, int channelCount, int width, int height) {
        auto bytes = reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(pixelBuffer));
        memcpy(bytes, buf, capacity);
        // Provides an RGB ByteBuffer currently, because alpha channel is not necessary for video.
        // If you want to display every frame in Bitmap (using RGBA_8888), you should change the pixelBuffer's capacity
        // and apply the codes below. Additional modification is required, go fuck yourself.
//        const uint32_t B = 16U;
//        const uint32_t G = 8U;
//        const uint32_t R = 0U;
//        for (int i = 0, j = 0; i < capacity; i+=3) {
//            // ABGR for Bitmap
//          bytes[j++] = static_cast<uint32_t>(0xFF000000U | (buf[i+2] << B) | (buf[i+1] << G) | (buf[i] << R));
//        }
        env->CallVoidMethod(callbackObj, onFrameAvailableMid, pixelBuffer, width, height, 4);
    }

    void destroy(JNIEnv *env) {
        env->DeleteGlobalRef(callbackObj);
        callbackObj = nullptr;
        releasePixelBuffer(env);
    }
};

extern "C"
JNIEXPORT jlong JNICALL
Java_com_potterhsu_rtsplibrary_RtspClient_nativeCreate(JNIEnv *env, jclass thiz,
                                                       jobject callback) {
    auto ptr = new JNIConnector(env, callback);
    return reinterpret_cast<jlong>(ptr);
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_potterhsu_rtsplibrary_RtspClient_play(JNIEnv *env, jclass thiz, jlong instance,
                                                       jstring endpoint) {
    auto jniConnector = reinterpret_cast<JNIConnector*>(instance);
    if (nullptr == jniConnector) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "play# Cannot interpret JNIConnector");
        return JNI_ERR;
    }
    SwsContext *img_convert_ctx;
    AVFormatContext* context = avformat_alloc_context();
    AVCodecContext* ccontext = avcodec_alloc_context3(NULL);
    int video_stream_index = -1;

    av_register_all();
    avformat_network_init();

    AVDictionary *option = NULL;
    av_dict_set(&option, "rtsp_transport", "tcp", 0);

    // Open RTSP
    const char *rtspUrl= env->GetStringUTFChars(endpoint, JNI_FALSE);
    if (int err = avformat_open_input(&context, rtspUrl, NULL, &option) != 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot open input %s, error code: %d", rtspUrl, err);
        return JNI_ERR;
    }
    env->ReleaseStringUTFChars(endpoint, rtspUrl);

    av_dict_free(&option);

    if (avformat_find_stream_info(context, NULL) < 0){
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot find stream info");
        return JNI_ERR;
    }

    // Search video stream
    for (int i = 0; i < context->nb_streams; i++) {
        if (context->streams[i]->codec->codec_type == AVMEDIA_TYPE_VIDEO)
            video_stream_index = i;
    }

    if (video_stream_index == -1) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Video stream not found");
        return JNI_ERR;
    }

    AVPacket packet;
    av_init_packet(&packet);

    // Open output file
    AVFormatContext *oc = avformat_alloc_context();
    AVStream *stream = NULL;

    // Start reading packets from stream and write them to file
    av_read_play(context);

    AVCodec *codec = NULL;
    codec = avcodec_find_decoder(AV_CODEC_ID_H264);
    if (!codec) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot find decoder H264");
        return JNI_ERR;
    }

    avcodec_get_context_defaults3(ccontext, codec);
    avcodec_copy_context(ccontext, context->streams[video_stream_index]->codec);

    if (avcodec_open2(ccontext, codec, NULL) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot open codec");
        return JNI_ERR;
    }

    img_convert_ctx = sws_getContext(ccontext->width, ccontext->height, ccontext->pix_fmt, ccontext->width, ccontext->height,
                                     AV_PIX_FMT_RGB24, SWS_BICUBIC, NULL, NULL, NULL);

    size_t size = (size_t) avpicture_get_size(AV_PIX_FMT_YUV420P, ccontext->width, ccontext->height);
    uint8_t *picture_buf = (uint8_t*)(av_malloc(size));
    AVFrame *pic = av_frame_alloc();
    AVFrame *picrgb = av_frame_alloc();
    size_t size2 = (size_t) avpicture_get_size(AV_PIX_FMT_RGB24, ccontext->width, ccontext->height);
    uint8_t *picture_buf2 = (uint8_t*)(av_malloc(size2));
    avpicture_fill( (AVPicture*) pic, picture_buf, AV_PIX_FMT_YUV420P, ccontext->width, ccontext->height );
    avpicture_fill( (AVPicture*) picrgb, picture_buf2, AV_PIX_FMT_RGB24, ccontext->width, ccontext->height );

    jniConnector->updateFrameSize(env, ccontext->width, ccontext->height, 3);
    jniConnector->start();
    while (jniConnector->isRunning() && av_read_frame(context, &packet) >= 0) {
        if (packet.stream_index == video_stream_index) { // Packet is video
            if (stream == NULL) {
                stream = avformat_new_stream(oc,
                                             context->streams[video_stream_index]->codec->codec);
                avcodec_copy_context(stream->codec, context->streams[video_stream_index]->codec);
                stream->sample_aspect_ratio = context->streams[video_stream_index]->codec->sample_aspect_ratio;
            }

            int check = 0;
            packet.stream_index = stream->id;
            avcodec_decode_video2(ccontext, pic, &check, &packet);
            sws_scale(img_convert_ctx, (const uint8_t *const *) pic->data, pic->linesize, 0,
                      ccontext->height, picrgb->data, picrgb->linesize);
            jniConnector->onFrameAvailable(env, picture_buf2, 3, ccontext->width, ccontext->height);
        }
        av_free_packet(&packet);
        av_init_packet(&packet);
    }

    av_free(pic);
    av_free(picrgb);
    av_free(picture_buf);
    av_free(picture_buf2);

    av_read_pause(context);
    avio_close(oc->pb);
    avformat_free_context(oc);
    avformat_close_input(&context);

    return !jniConnector->isRunning() ? JNI_OK : JNI_ERR;
}

extern "C"
void
Java_com_potterhsu_rtsplibrary_RtspClient_stop(
        JNIEnv *env,
        jclass thisClazz, jlong instance) {
    auto ptr = reinterpret_cast<JNIConnector*>(instance);
    if (nullptr == ptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot find JNIConnector on stop");
        return;
    }
    ptr->stop();
}


extern "C"
JNIEXPORT void JNICALL
Java_com_potterhsu_rtsplibrary_RtspClient_destroy(JNIEnv *env, jclass clazz, jlong instance) {
    auto ptr = reinterpret_cast<JNIConnector*>(instance);
    if (nullptr == ptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Cannot find JNIConnector on destroy");
        return;
    }
    ptr->destroy(env);
    delete ptr;
}
