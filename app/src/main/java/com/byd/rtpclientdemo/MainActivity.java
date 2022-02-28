package com.byd.rtpclientdemo;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final String IP = "10.31.242.58";
    private static final int PORT = 5004;
    private Button mStartButton;
    private Button mStopButton;

    private HandlerThread mSendRequestThread;
    private Handler mSendRequestHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mStartButton = findViewById(R.id.start);
        mStartButton.setOnClickListener(this);
        mStopButton = findViewById(R.id.stop);
        mStopButton.setOnClickListener(this);

        initHandlerThraed();
    }

    private void initHandlerThraed() {
        mSendRequestThread = new HandlerThread("SendRequest");
        mSendRequestThread.start();
        Looper loop = mSendRequestThread.getLooper();
        mSendRequestHandler = new Handler(loop){
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case 0:
                        Log.i(TAG, "handleMessage start");
                        try {
                            Socket client = new Socket();
                            InetSocketAddress address = new InetSocketAddress(IP, PORT);
                            client.connect(address);
                            OutputStream outputStream = client.getOutputStream();
                            outputStream.write("OpenCamera1".getBytes());
                            client.shutdownOutput();

                            InputStream inputStream = client.getInputStream();
                            byte[] bytes = new byte[1024];
                            int len = inputStream.read(bytes);
                            Log.i(TAG, new String(bytes, 0, len));
                            client.shutdownInput();

                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case 1:
                        Log.i(TAG, "handleMessage stop");
                        try {
                            Socket client = new Socket();
                            InetSocketAddress address = new InetSocketAddress(IP, PORT);
                            client.connect(address);
                            OutputStream outputStream = client.getOutputStream();
                            outputStream.write("CloseCamera1".getBytes());
                            client.shutdownOutput();

                            InputStream inputStream = client.getInputStream();
                            byte[] bytes = new byte[1024];
                            int len = inputStream.read(bytes);
                            Log.i(TAG, new String(bytes, 0, len));
                            client.shutdownInput();

                            client.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }
        };
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.start) {
            Log.i(TAG, "onClick start");
            mSendRequestHandler.sendEmptyMessage(0);
        } else if (v.getId() == R.id.stop) {
            Log.i(TAG, "onClick stop");
            mSendRequestHandler.sendEmptyMessage(1);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSendRequestThread != null) {
            mSendRequestThread.quit();
        }
    }
}
