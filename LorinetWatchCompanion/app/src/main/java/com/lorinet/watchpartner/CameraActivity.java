package com.lorinet.watchpartner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class CameraActivity extends AppCompatActivity
{
    ConnectIQ iq;
    TextView stateLabel;
    TextView deviceLabel;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cameracontrol);

        deviceLabel = findViewById(R.id.deviceLabelCC);
        iq = ConnectIQ.getInstance(getApplicationContext(), ConnectIQ.IQConnectType.WIRELESS);
        iq.initialize(getApplicationContext(), true, new ConnectIQ.ConnectIQListener() {
            @Override
            public void onSdkReady() {

                // Do any post initialization setup.
            }

            @Override
            public void onInitializeError(ConnectIQ.IQSdkErrorStatus status) {

                // A failure has occurred during initialization.  Inspect
                // the IQSdkErrorStatus value for more information regarding
                // the failure.

            }

            @Override
            public void onSdkShutDown() {

            }
        });
        Button cb = findViewById(R.id.connectbtnCC);
        cb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                IQDevice dev = selectDevice();
                if (dev != null) {
                    deviceLabel.setText(dev.toString());
                    connect(dev);
                }
            }
        });
        Button bb = findViewById(R.id.backCC);
        bb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getSelf().finish();
            }
        });
        videoQuality = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if(Build.VERSION.SDK_INT > 23)
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
        }
        else
            cameraInit();
    }
    public AppCompatActivity getSelf()
    {
        return this;
    }

    public IQDevice selectDevice() {
        try {
            List paired = iq.getKnownDevices();
            if (paired != null && paired.size() > 0) {
                for (Object device : paired) {
                    IQDevice.IQDeviceStatus status = iq.getDeviceStatus((IQDevice) device);
                    if (status == IQDevice.IQDeviceStatus.CONNECTED) {
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
            iq.registerForAppEvents(device, new IQApp("ae0e6d47-947b-44c3-890e-0bca8e7b6dc8", "Camera Control", 1), new ConnectIQ.IQApplicationEventListener() {
                @Override
                public void onMessageReceived(IQDevice iqDevice, IQApp iqApp, List<Object> list, ConnectIQ.IQMessageStatus iqMessageStatus) {
                    if(iqMessageStatus == ConnectIQ.IQMessageStatus.SUCCESS) {
                        for(Object o : list)
                        {
                            String s = o.toString();
                            if(s.equals("fo"))
                            {
                                flash(true);
                            }
                            else if(s.equals("fc"))
                            {
                                flash(false);
                            }
                            else if(s.equals("sh"))
                            {
                                shoot();
                            }
                            else if(s.equals("vi"))
                            {
                                record();
                            }
                            else if(s.equals("ff"))
                            {
                                front();
                            }
                            else if(s.equals("bf"))
                            {
                                back();
                            }
                            else if(s.equals("hq"))
                            {
                                highquality();
                            }
                            else if(s.equals("mq"))
                            {
                                mediumquality();
                            }
                            else if(s.equals("lq"))
                            {
                                lowquality();
                            }
                            else if(s.equals("op"))
                            {
                                orientation(true);
                                Message("Portrait");
                            }
                            else if(s.equals("ol"))
                            {
                                orientation(false);
                                Message("Landscape");
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
    private Camera camera;
    private CameraPreview preview;
    private Camera.PictureCallback picture;
    private Button capture, switchCamera;
    private SurfaceTexture stx;
    private Camera.Parameters params;
    private boolean cameraFront = false;
    int cameraID = 0;
    MediaRecorder mediaRecorder;
    boolean filming = false;
    boolean canShoot = false;
    boolean canRecord = false;
    boolean flashState = false;
    boolean portrait = false;
    Camera.PictureCallback cb;
    CamcorderProfile videoQuality;

    public void highquality()
    {
        cameraDeinit();
        videoQuality = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        cameraInit();
    }

    public void mediumquality()
    {
        cameraDeinit();
        videoQuality = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
        cameraInit();
    }

    public void lowquality()
    {
        cameraDeinit();
        videoQuality = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        cameraInit();
    }

    public void vidInit()
    {
        try
        {
            mediaRecorder = new MediaRecorder();
            camera = Camera.open(cameraID);
            stx = new SurfaceTexture(0);
            camera.setPreviewTexture(stx);
            params = camera.getParameters();
            if(flashState) params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            else params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(params);
            camera.unlock();
            mediaRecorder.setCamera(camera);
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mediaRecorder.setProfile(videoQuality);
            mediaRecorder.setVideoSize(videoQuality.videoFrameWidth, videoQuality.videoFrameHeight);
            mediaRecorder.setMaxDuration(5000000);
            mediaRecorder.setMaxFileSize(500000000);
            mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/CameraControl/VID_" + System.currentTimeMillis() + ".mp4");
            mediaRecorder.prepare();
            canRecord = true;
        }
        catch(IOException iex)
        {
            Message("Could not initialize video recording: " + iex.getMessage());
        }
        catch(IllegalStateException isex)
        {
            Message("Camera is not supported!");
        }
    }

    public void vidDeinit()
    {
        mediaRecorder.release();
        mediaRecorder = null;
        camera.release();
        camera = null;
        canRecord = false;
    }

    public void cameraInit()
    {
        try
        {
            picture = new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    camera.startPreview();
                    File f = new File(String.format(Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/CameraControl/IMG_" + System.currentTimeMillis() + ".jpg"));
                    try
                    {
                        File dir = new File(f.getParent());
                        if(!dir.exists()) dir.mkdir();
                        if (!f.exists()) f.createNewFile();
                        FileOutputStream fos = new FileOutputStream(f);
                        fos.write(data);
                        fos.close();
                    }
                    catch(IOException ie)
                    {
                        Message("Could not save image: " + ie.getMessage());
                    }

                }
            };
            camera = Camera.open(cameraID);
            params = camera.getParameters();
            params.setPictureSize(1920,1080);
            if(flashState) params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            else params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            if(portrait)
            {
                params.set("orientation", "portrait");
                params.setRotation(270);
            }
            camera.setParameters(params);
            stx = new SurfaceTexture(0);
            camera.setPreviewTexture(stx);
            camera.startPreview();
            canShoot = true;
        }
        catch(IOException ie)
        {
            Message("Could not initialize camera.");
        }
    }

    public void cameraDeinit()
    {
        camera.stopPreview();
        camera.release();
        canShoot = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode)
        {
            case 0:
            {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    cameraInit();
                }
                else
                {
                    Message("Camera Control will not work until permissions granted!");
                }
                return;
            }
        }
    }

    public void shoot()
    {
        try {
            camera.takePicture(null, null, picture);
            Message("Picture taken");
        }
        catch(Exception ex)
        {
            Message("An error occurred while trying to take a picture: " + ex.toString() + ": " + ex.getMessage());
        }

    }

    public void record()
    {
        if (filming) {
            filming = false;
            mediaRecorder.stop();
            Message("Not Recording");
            vidDeinit();
            if(!canShoot)
                cameraInit();
        } else {
            if(canShoot)
                cameraDeinit();
            vidInit();
            filming = true;
            try {
                mediaRecorder.start();
                Message("Recording");
            }
            catch(Exception ex)
            {
                Message("The selected quality profile is not supported. Choose a lower quality profile!");
            }
        }
    }

    public void front()
    {
        cameraDeinit();
        cameraID = 1;
        cameraInit();
        Message("Front camera");
    }

    public void back()
    {
        cameraDeinit();
        cameraID = 0;
        cameraInit();
        Message("Back camera");
    }

    public void flash(boolean on)
    {
        params = camera.getParameters();
        if(on)
        {
            params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        }
        else
        {
            params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        camera.setParameters(params);
        flashState = on;
        Message("Flash state: " + Boolean.toString(on));
    }

    public void orientation(boolean portrt)
    {
        portrait = portrt;
        params = camera.getParameters();
        if(portrt)
        {
            params.set("orientation", "portrait");
            if(cameraFront)
                params.setRotation(270);
            else
                params.setRotation(90);
        }
        else
        {
            params.set("orientation", "landscape");
            params.setRotation(0);
        }
        camera.setParameters(params);
    }
    public void Message(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }
}
