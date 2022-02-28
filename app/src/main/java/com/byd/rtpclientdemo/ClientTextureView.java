package com.byd.rtpclientdemo;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class ClientTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String MIME_TYPE = "video/avc";
    private static final String TAG = "ClientTextureView";
    private byte[] mH264Data = new byte[200000];
    private static final int PORT = 5004;
    private int mHeight = 1080;
    private int mWidth = 1920;
    private DatagramSocket mSocket;
    private MediaCodec mDecode;

    public ClientTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSurfaceTextureListener(this);
        try {
            mSocket = new DatagramSocket(PORT);
            mSocket.setReuseAddress(true);
            mSocket.setBroadcast(true);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        new PreviewThread(new Surface(surface));
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mSocket != null) {
            mSocket.close();
            mSocket = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

	//处理线程
    private class PreviewThread extends Thread {
        DatagramPacket datagramPacket = null;

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        public PreviewThread(Surface surface) {
            try {
                mDecode = MediaCodec.createDecoderByType(MIME_TYPE);
                final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
                format.setInteger(MediaFormat.KEY_BIT_RATE, 1900000);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                byte[] header_sps = {0, 0, 0, 1, 103, 66, 0, 41, -115, -115, 64, 80, 30, -48, 15, 8, -124, 83, -128};
                byte[] header_pps = {0, 0, 0, 1, 104, -54, 67, -56};
                format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
                format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));

                mDecode.configure(format, surface, null, 0);
                mDecode.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
            start();
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            boolean isFiset = true;
            int pre_seq_num = 0;
            int destPos = 0;
            int h264Length;

            while (true) {
                if (mSocket != null) {
                    try {
                        byte[] data = new byte[1500];
                        datagramPacket = new DatagramPacket(data, data.length);
                        mSocket.receive(datagramPacket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                byte[] rtpData = datagramPacket.getData();
                if (rtpData != null) {
                    int l3 = (rtpData[12] << 24) & 0xff000000;
                    int l4 = (rtpData[13] << 16) & 0x00ff0000;
                    int l5 = (rtpData[14] << 8) & 0x0000ff00;
                    int l6 = rtpData[15] & 0x000000FF;
                    h264Length = l3 + l4 + l5 + l6;
                    Log.i(TAG, "run: h264Length = " + h264Length);

                    byte[] snm = new byte[2];
                    System.arraycopy(rtpData, 2, snm, 0, 2);
                    int seq_num = CalculateUtil.byte2short(snm);
                    Log.i(TAG, "seq_num = " + seq_num);

                    int timeStamp1 = (rtpData[4] << 24) & 0xff000000;
                    int timeStamp2 = (rtpData[5] << 16) & 0x00ff0000;
                    int timeStamp3 = (rtpData[6] << 8) & 0x0000ff00;
                    int timeStamp4 = rtpData[7] & 0x000000FF;
                    int timeStamp = timeStamp1 + timeStamp2 + timeStamp3 + timeStamp4;
                    Log.i(TAG, "timeStamp = " + timeStamp);

                    if (isFiset) {
                        pre_seq_num = seq_num;
                        isFiset = false;
                    } else {
                        if (seq_num - pre_seq_num > 1) {
                            Log.i(TAG, "Packet loss" + (seq_num - pre_seq_num));
                        } else if (seq_num - pre_seq_num < 1) {
                            Log.i(TAG, "Out of order packets" + (seq_num - pre_seq_num));
                        }
                        pre_seq_num = seq_num;
                    }

                    byte indicatorType = (byte) (CalculateUtil.byteToInt(rtpData[16]) & 0x1f);
                    Log.i(TAG, "indicatorType = " + indicatorType);
                    if (indicatorType == 28) {
                        byte s = (byte) (rtpData[17] & 0x80);
                        byte e = (byte) (rtpData[17] & 0x40);
                        Log.i(TAG, "s = " + s + "; e = " + e);

                        if (s == -128) {        // frist packet
                            System.arraycopy(rtpData, 18, mH264Data, destPos, h264Length);
                            destPos += h264Length;
                        } else if (e == 64) {   // end packet
                            System.arraycopy(rtpData, 18, mH264Data, destPos, h264Length);
                            destPos = 0;
                            offerDecoder(mH264Data, mH264Data.length);
                            CalculateUtil.memset(mH264Data, 0, mH264Data.length);
                        } else {
                            System.arraycopy(rtpData, 18, mH264Data, destPos, h264Length);
                            destPos += h264Length;
                        }
                    } else {
                        System.arraycopy(rtpData, 16, mH264Data, 0, h264Length);
                        offerDecoder(mH264Data, mH264Data.length);
                        CalculateUtil.memset(mH264Data, 0, mH264Data.length);
                    }

                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    private void offerDecoder(byte[] input, int length) {
        Log.d(TAG, "offerDecoder");
        try {
            ByteBuffer[] inputBuffers = mDecode.getInputBuffers();
            int inputBufferIndex = mDecode.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                try {
                    inputBuffer.put(input, 0, length);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mDecode.queueInputBuffer(inputBufferIndex, 0, length, 0, 0);
            }
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            int outputBufferIndex = mDecode.dequeueOutputBuffer(bufferInfo, 0);
            while (outputBufferIndex >= 0) {
                //If a valid surface was specified when configuring the codec,
                //passing true renders this output buffer to the surface.
                mDecode.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = mDecode.dequeueOutputBuffer(bufferInfo, 0);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
