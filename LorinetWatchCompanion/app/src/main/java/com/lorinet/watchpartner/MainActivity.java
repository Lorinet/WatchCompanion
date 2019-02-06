package com.lorinet.watchpartner;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import com.androidhiddencamera.CameraConfig;
import com.androidhiddencamera.HiddenCameraActivity;
import com.androidhiddencamera.HiddenCameraFragment;
import com.androidhiddencamera.HiddenCameraService;
import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener;
import com.garmin.android.connectiq.ConnectIQ.IQConnectType;
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener;
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.IQDevice.IQDeviceStatus;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;
import com.lorinet.watchpartner.R;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {
    ConnectIQ iq;
    TextView stateLabel;
    TextView deviceLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stateLabel = findViewById(R.id.connectionStateLabel);
        deviceLabel = findViewById(R.id.deviceLabel);

        iq = ConnectIQ.getInstance(getApplicationContext(), IQConnectType.WIRELESS);
        iq.initialize(getApplicationContext(), true, new ConnectIQListener() {
            @Override
            public void onSdkReady() {
            }

            @Override
            public void onInitializeError(IQSdkErrorStatus status) {
            }

            @Override
            public void onSdkShutDown() {
            }
        });
        Button cb = findViewById(R.id.connectbtn);
        cb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                IQDevice dev = selectDevice();
                if (dev != null) {
                    deviceLabel.setText(dev.toString());
                    connect(dev);
                }
            }
        });
        Button wdlb = findViewById(R.id.wdlbtn);
        wdlb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DownloadTask dt = new DownloadTask();
                Message("Downloading");
                dt.execute();
            }
        });
        Button pcb = findViewById(R.id.connectbtn2);
        pcb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                connectPC();
            }
        });
        Button ccb = findViewById(R.id.camControlActivityButton);
        ccb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getSelf(), CameraActivity.class));
            }
        });
    }

    public AppCompatActivity getSelf()
    {
        return this;
    }

    public void connectPC() {
        if(!connected)
        {
            EditText ipet = (EditText)findViewById(R.id.ipBox);
            ip = ipet.getText().toString();
            mTcpClient = new TcpClient(null);
            ct = new ConnectTask();
            ct.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            Button pcb = findViewById(R.id.connectbtn2);
            pcb.setText("Disconnect");
            connected = true;
        }
        else
        {
            mTcpClient.stopClient();
            connected = false;
            Button pcb = findViewById(R.id.connectbtn2);
            pcb.setText("Connect to PC");
        }

    }

    public IQDevice selectDevice() {
        try {
            List paired = iq.getKnownDevices();
            if (paired != null && paired.size() > 0) {
                for (Object device : paired) {
                    IQDeviceStatus status = iq.getDeviceStatus((IQDevice) device);
                    if (status == IQDeviceStatus.CONNECTED) {
                        return (IQDevice) device;
                    }
                }
            }
        } catch (Exception e) {
            Message("No connected Garmin devices found!");
            return null;
        }
        return null;
    }

    public void connect(IQDevice device) {
        try {
            iq.registerForAppEvents(device, new IQApp("6c8f72ab-c4b3-492d-8985-7592f33fd3d1", "WatchMouse", 1), new ConnectIQ.IQApplicationEventListener() {

                @Override
                public void onMessageReceived(IQDevice device, IQApp app, List<Object> messageData, ConnectIQ.IQMessageStatus status) {
                    if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
                        for (Object o : messageData) {
                            if (o != null) {
                                String s = o.toString();
                                if (s.length() > 0) {
                                    tx(s);
                                }
                            }
                        }
                    }
                }
            });
            stateLabel.setText("Connected");
            Message("Connected");
        } catch (Exception ex) {

        }
    }



    private boolean unpackZip(String path, String zipname)
    {
        InputStream is;
        ZipInputStream zis;
        try
        {
            String filename;
            is = new FileInputStream(path + zipname);
            zis = new ZipInputStream(new BufferedInputStream(is));
            ZipEntry ze;
            byte[] buffer = new byte[1024];
            int count;

            while ((ze = zis.getNextEntry()) != null)
            {
                filename = ze.getName();

                // Need to create directories if not exists, or
                // it will generate an Exception...
                if (ze.isDirectory()) {
                    File fmd = new File(path + filename);
                    fmd.mkdirs();
                    continue;
                }

                FileOutputStream fout = new FileOutputStream(path + filename);

                while ((count = zis.read(buffer)) != -1)
                {
                    fout.write(buffer, 0, count);
                }

                fout.close();
                zis.closeEntry();
            }

            zis.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
            return false;
        }

        return true;
    }
    TcpClient mTcpClient;
    ConnectTask ct;
    String ip = "";

    boolean connected = false;

    private void tx(String b) {
        try {
            class st extends AsyncTask<Void, Void, Void>
            {
                String str;
                st(String s) { str = s;}
                public Void doInBackground(Void... params)
                {
                    mTcpClient.sendMessage(str);
                    return null;
                }
            }
            new st(b).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            Message("Fail:" + e.toString() + e.getMessage());
        }
    }

    public void Message(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }
    public String getLocalIpAddress() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return ip;
    }



    class ConnectTask extends AsyncTask<String, Void, TcpClient> {

        @Override
        protected TcpClient doInBackground(String... message) {

            mTcpClient.SERVERIP = ip;
            mTcpClient = new TcpClient(null);
            mTcpClient.run();

            return null;
        }
    }
    class DownloadTask extends AsyncTask<Void, Void, Void>
    {

        @Override
        protected Void doInBackground(Void... voids) {
            downloadZipFile("http://lorinet.rf.gd/watchmouse/watchmouseapp.zip", Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DOWNLOADS + "/" + "watchmouseapp.zip");
            unpackZip(Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DOWNLOADS + "/" + "watchmouseapp.zip");
            return null;
        }
        public void downloadZipFile(String urlStr, String destinationFilePath) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.d("downloadZipFile", "Server ResponseCode=" + connection.getResponseCode() + " ResponseMessage=" + connection.getResponseMessage());
                }
                input = connection.getInputStream();
                new File(destinationFilePath).createNewFile();
                output = new FileOutputStream(destinationFilePath);

                byte data[] = new byte[4096];
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (connection != null) connection.disconnect();
            }

            File f = new File(destinationFilePath);
        }

        public boolean unpackZip(String filePath) {
            InputStream is;
            ZipInputStream zis;
            try {

                File zipfile = new File(filePath);
                String parentFolder = zipfile.getParentFile().getPath();
                String filename;

                is = new FileInputStream(filePath);
                zis = new ZipInputStream(new BufferedInputStream(is));
                ZipEntry ze;
                byte[] buffer = new byte[1024];
                int count;

                while ((ze = zis.getNextEntry()) != null) {
                    filename = ze.getName();

                    if (ze.isDirectory()) {
                        File fmd = new File(parentFolder + "/" + filename);
                        fmd.mkdirs();
                        continue;
                    }

                    FileOutputStream fout = new FileOutputStream(parentFolder + "/" + filename);

                    while ((count = zis.read(buffer)) != -1) {
                        fout.write(buffer, 0, count);
                    }

                    fout.close();
                    zis.closeEntry();
                }

                zis.close();
            } catch(IOException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }
    }
}

