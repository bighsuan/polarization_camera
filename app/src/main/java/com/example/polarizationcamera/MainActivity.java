package com.example.polarizationcamera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;

import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioAttributes;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextureView tvPreview;
    private Button btnCapture, btnAlbum;
    private Size tvSize, previewSize, photoSize;
    private CameraDevice cameraDevice;
    CaptureRequest.Builder previewRequestBuilder, captureRequestBuilder;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private SoundPool soundPool;
    private int soundId;
    private int ind;
    private String TAG = "BIG---------------------";
    private final int RESULT_PERMISSION_ALL = 1;
    private String[] PERMISSIONS = {
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private Image image1, image2, image3, image4;
    private String cameraId;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    //int format = ImageFormat.JPEG;
    int format = ImageFormat.YUV_420_888;

    long startTime, endTime;
    long totalStartTime, totalEndTime;

    final class Result {
        private final float[] theta;
        private final float[] DoLP;

        public Result(float[] theta, float[] DoLP) {
            this.theta = theta;
            this.DoLP = DoLP;
        }

        public float[] getTheta() {
            return theta;
        }

        public float[] getDoLP() {
            return DoLP;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "(onCreate)");

        setContentView(R.layout.activity_main);
        tvPreview = (TextureView) findViewById(R.id.tvPreview);
        btnCapture = (Button) findViewById(R.id.btnCapture);
        btnAlbum = (Button) findViewById(R.id.btnAlbum);

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                totalStartTime = System.nanoTime();

                btnCapture_click();
                //testPhoto();
            }
        });

        // set SurfaceTexture listener
        tvPreview.setSurfaceTextureListener(surfaceTextureListener);

        // create soundPool
        // setMaxStreams(1) means only one sound at one time, if there's two sounds, the first one will stopped automatically
        AudioAttributes audioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        soundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(audioAttributes).build();

        // loading audio, the file put in "/res/raw" folder
        soundId = soundPool.load(this, R.raw.sound, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "(onResume)" );

        // this is used to be full screen (for my old htc phone)
        hideSystemNavigationBar();

        // open camera
        if (tvPreview.isAvailable()) {
            if(cameraDevice==null) {
                openCamera();
            }
        } else {
            tvPreview.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // close camera
        if(cameraDevice!=null) {
            stopCamera();
        }
    }


    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.i(TAG, "(onSurfaceTextureAvailable)" );
            tvSize = new Size(width, height);
            openCamera();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            stopCamera();
            return true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    private void openCamera() {
        // 1. check permission, if ok, open faceback camera
        // 2. set imageReader for saving image
        // 3. open faceback camera

        Log.i(TAG , "(openCamera)");
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        // 1. check permission
        try {
            List<String> listPermissionsNeeded = new ArrayList<>();
            for (String permission : PERMISSIONS) {
                if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    listPermissionsNeeded.add(permission);
                }
            }

            if (!listPermissionsNeeded.isEmpty()){
                String[] stringPermissionNeeded = listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]);
                requestPermission(stringPermissionNeeded);
            }
            else {
                // 2. set textureView's(for preview) and imageReader's(for saving photo) size and resolution
                setUpCameraOutputs(manager);

                // 3. open faceback camera
                manager.openCamera(cameraId, deviceStateCallback, null);
            }

        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            //not support camera2
        }
    }

    private void stopCamera(){
        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void setUpCameraOutputs(CameraManager manager) throws CameraAccessException {

        CameraCharacteristics characteristics = null;

        // 1. find faceback camera
        for (String cameraId : manager.getCameraIdList()) {
            characteristics = manager.getCameraCharacteristics(cameraId);
            int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                this.cameraId = cameraId;
                break;
            }
        }

        // 2. get the support output resolutions of the camera, and set imageReader
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        //Size largest = Collections.max(Arrays.asList(map.getOutputSizes(format)),new CompareSizesByArea());
        photoSize = chooseOptimalSize(map.getOutputSizes(format), tvSize.getWidth(), tvSize.getHeight());
        imageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), format, 4);
        imageReader.setOnImageAvailableListener(imageAvailableListener,null);

        previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), tvSize.getWidth(), tvSize.getHeight());

        Log.i(TAG, "previewSize = " + previewSize.getWidth() +" "+ previewSize.getHeight());
        Log.i(TAG, "photoSize = " + photoSize.getWidth() +" "+ photoSize.getHeight());
    }

    // compare the camera support resolutions
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        // choose the size which has the same ratio with screen(tvWidth, tvHeight)
        // first choose the smallest size which is big enough
        // or choose the largest size which is not biggest

        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        for (Size option : choices) {
            if( option.getHeight() == option.getWidth() * height / width )
            {
                if (option.getWidth() >= width && option.getHeight() >= height) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }else if( option.getHeight() == option.getWidth() *  width / height ){
                if (option.getWidth() >= height && option.getHeight() >= width) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.i( "BIG------------------: ", "Couldn't find any suitable CompareSizesByAreapreview size");
            return choices[0];
        }
    }


    private void createCameraPreview(){
        // 1. create preview request builder
        // 2. set preview request parameter (include target output surface)
        // 3. create capture session
        // 4. using request builder to create repeated request, and throw it to capture session

        try{
            SurfaceTexture st = tvPreview.getSurfaceTexture();

            st.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(st);

            // 1. create preview request builder
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // 2. set preview request parameter (include target output surface)
            previewRequestBuilder.addTarget(surface);
            setPreviewRequestParameter();

            // 3. create capture session
            cameraDevice.createCaptureSession(Arrays.asList(surface,imageReader.getSurface()), new CameraCaptureSession.StateCallback(){

                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    // 4. using request builder to create repeated request, and throw it to capture session
                    try {
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        captureSession.setRepeatingRequest(previewRequest, null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    showToast("Failed");
                }}, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void playSoundAndTakePicture(int ind) throws CameraAccessException {
        // 1. play sound
        // 2. waiting (keep on playing sound
        // 3. capture (similar to the process of "createCameraPreview()", but doesn't need to create capture session again)
        //    3-1. create capture request builder
        //    3-2. set capture request parameter
        //    (X). create capture session -> using the same capture session with preview
        //    3-3. using request builder to create request, and throw it to capture session
        // *4. capture again while capture completed (writing in CameraCaptureSession.CaptureCallback.onCaptureCompleted


        // 1. play sound
        // if there's two sounds, the first one will stopped automatically
        switch(ind){
            case 1: soundPool.play(soundId, 1, 1, 0, 0, 1); break;
            case 2: soundPool.play(soundId, 0, 1, 0, 0, 1); break;
            case 3: soundPool.play(soundId, 1, 0, 0, 0, 1); break;
            case 4: soundPool.play(soundId, 0, 0, 0, 0, 1); break;
        }
        // leftVolume, rightVolume is range from 0.0 to 1.0
        // loop = 0 means no loop, -1 means loop forever
        // rate is range from 0.5 to 2.0, 1.0 means normal speed


        // 2. waiting
        // delay 150 millisecond
        try{
            Thread.sleep(1000);
        } catch(InterruptedException e){
            e.printStackTrace();
        }

        // 3. capture
        if (ind == 1){
            // 3-1. create capture request builder
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            // 3-2. set capture request parameter
            captureRequestBuilder.addTarget(imageReader.getSurface());
            setCaptureRequestParameter();
        }

        // 3-3. using request builder to create request, and throw it to capture session
        CaptureRequest captureRequest = captureRequestBuilder.build();
        captureSession.capture(captureRequest,captureCallback, null);

    }

    private void setPreviewRequestParameter(){
        // auto-focus
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // auto-exposure with no flash
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        // auto-white-balance
        previewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_MODE_AUTO);
    }

    private void setCaptureRequestParameter(){
        // auto-focus
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // auto-exposure with no flash
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        // auto-white-balance
        captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);

        // orientations
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
    }

    // check the state of camera
    private final CameraDevice.StateCallback deviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG , "(CameraDevice.onOpened)");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    private final ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            if (image1==null){
                image1 = reader.acquireNextImage();
                return;
            }
            if (image2==null){
                image2 = reader.acquireNextImage();
                return;
            }
            if (image3==null){
                image3 = reader.acquireNextImage();
                return;
            }
            if (image4==null){
                image4 = reader.acquireNextImage();
                return;
            }
        }
    };


    // check the process of capture
    // *4. capture again while capture completed
    // private CameraCaptureSession.CaptureCallback captureCallback

    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            startTime = System.nanoTime();
        }


        @Override
        public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){
            super.onCaptureCompleted(session, request, result);
            endTime = System.nanoTime();
            Log.i(TAG , "capture using: " + timer());

            // keep on taking picture until finished 4 picture
            // and then deal with 4 picture
            if (ind < 4){
                ind++;
                try {
                    playSoundAndTakePicture(ind);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
            else{
                soundPool.stop(soundId);
                dealWithImage();
                unlockFocus();
            }
        }

    };

    String fileTime;
    String[] fileName;
    // saving four image
    private void dealWithImage(){
        Log.i(TAG, "(dealWithImage)");
        startTime = System.nanoTime();

        String path = Environment.getExternalStorageDirectory().getPath() + "/PolarizationCamera";
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdir();
        }

        fileTime = new SimpleDateFormat("MMdd_HH:mm").format(Calendar.getInstance().getTime());
        fileName = new String[]{
                "I0_"+fileTime,
                "I90_"+fileTime,
                "I45_"+fileTime,
                "I135_"+fileTime };


        Image image = null;
        int width = 0, height = 0;
        for (int i=1; i<=4; i++) {
            switch (i) {
                case 1:
                    image = image1;
                    break;
                case 2:
                    image = image2;
                    break;
                case 3:
                    image = image3;
                    break;
                case 4:
                    image = image4;
                    break;
            }

            if (width == 0 || height == 0) {
                width = image.getWidth();
                height = image.getHeight();
            }

            saveImageToJpg(image, path, fileName[i-1]);

            switch (i) {
                case 1:
                    if (image1!=null) image1.close();
                    break;
                case 2:
                    if (image2!=null) image2.close();
                    break;
                case 3:
                    if (image3!=null) image3.close();
                    break;
                case 4:
                    if (image4!=null) image4.close();
                    break;
            }
        }
        endTime = System.nanoTime();
        Log.i(TAG, "saving 4 image : " + timer());

        startTime = System.nanoTime();
        Result result = calculating(path, fileTime);
        endTime = System.nanoTime();
        Log.i(TAG, "calculate DoLP and theta: " + timer());

        float[] theta = result.getTheta();
        float[] DoLP = result.getDoLP();


        saveFloatToJpg(theta, DoLP, width, height, path, "Result_" + fileTime);
//        saveFloatToJpg(theta, "H", width, height, path, "theta_" + fileTime);
//        saveFloatToJpg(DoLP,"V",width, height, path, "DoLP_" + fileTime);
        totalEndTime = System.nanoTime();
        startTime = totalStartTime;
        endTime = totalEndTime;
        Log.i(TAG, "total process spend: " + timer());
    }//*/


    private void saveImageToJpg(Image image, String path, String fileName){
        // YUV to jpeg
        byte[] nv21;
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        nv21 = new byte[ySize + uSize + vSize];

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        // NV21toJPEG
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        yuv.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
        byte[] data = out.toByteArray();
        Bitmap bitmap = rotateYUV420Degree90(data, image.getWidth(), image.getHeight());

        String filename = fileName + ".jpg";
        File file = new File(path, filename);

        try {
            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut); // saving the Bitmap to a file compressed as a JPEG with 85% compression rate
            fOut.flush(); // Not really required
            fOut.close();
            refreshGallery(file);
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            image.close();
        }
    }

    private Result calculating(String path, String fileTime){
        short[] I45 = loadGrayShortArray(path, fileName[2]);
        short[] I135 = loadGrayShortArray(path, fileName[3]);

        startTime = System.nanoTime();
        int length = I45.length;
        short[] difference = new short[length];
        boolean[] bool = new boolean[length];
        for (int i = 0; i < length; i++) {
            difference[i] = (short)(I45[i] - I135[i]);
            //difference[i] = (short) ((I45[i] - I135[i])*(I45[i] - I135[i])); // faster
            //difference[i] = (short) Math.pow((I45[i] - I135[i]), 2);      // slower

            if (I45[i] > I135[i])
                bool[i] = true;
            else
                bool[i] = false;
        }

        endTime = System.nanoTime();
        Log.i(TAG, "calculate bool and difference: " + timer());
        I45 = null;
        I135 = null;

        startTime = System.nanoTime();

        short[] I0 = loadGrayShortArray(path, fileName[0]);
        short[] I90 = loadGrayShortArray(path, fileName[1]);

        float[] DoLP = new float[length];
        float[] theta = new float[length];
        for (int i = 0; i < length; i++) {
            if (I0[i]==0){
                theta[i] = 0;
            }
            else{
                theta[i] = (float)Math.atan(Math.sqrt((float)I90[i] / (float)I0[i]));
            }

            if (!bool[i])
                theta[i] = (float)Math.PI - theta[i];

            theta[i] = (float)(theta[i] / Math.PI * 360);

            if (I0[i]==0 && I90[i]==0)
                DoLP[i] = 0;
            else
                DoLP[i] = (float) (Math.sqrt((I90[i] - I0[i]) * (I90[i] - I0[i]) + (int)difference[i]*(int)difference[i]) / (I0[i] + I90[i]));
        }

        I0 = null;
        I90 = null;
        endTime = System.nanoTime();

        Result result = new Result(theta, DoLP);
        return result;
    }

    private short[] loadGrayShortArray(String path, String fileName){
        startTime = System.nanoTime();
        String fileString = path +"/" + fileName + ".jpg";
        Bitmap bitmap = BitmapFactory.decodeFile(fileString);

        Bitmap bitmapCopy = bitmap.copy(Bitmap.Config.RGB_565 ,true);
        Canvas c = new Canvas(bitmapCopy);

        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        c.drawBitmap(bitmap, 0, 0, paint);

        bitmap.recycle();

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width*height];
        bitmapCopy.getPixels(pixels, 0, width, 0, 0, width, height);

        short[] grays = new short[width*height];
        for (int i = 0; i < width*height; i++) {
            grays[i] = (short)((pixels[i]) >> 16 & 0xff); //get red color
        }

        endTime = System.nanoTime();
        Log.i(TAG, "loading gray image: " + timer());
        return grays;
    }

    private void saveFloatToJpg(float[] theta, float[] DoLP, int height, int width, String path, String fileName){
        startTime = System.nanoTime();

        Log.i(TAG, "start saving result");
        int length = width * height;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        int[] colors = new int[length];
//        if (place=="H"){
//            for (int i = 0; i < length; i++) {
//                float[] HSVcolor = { floats[i], 1, 1 }; // colorful
//                colors[i] = Color.HSVToColor(HSVcolor);
//            }
//        }else if (place == "V"){
//            for (int i = 0; i < length; i++) {
//                float[] HSVcolor = { 0, 1, floats[i] };  //from black to red
//                colors[i] = Color.HSVToColor(HSVcolor);
//            }
//        }
        for (int i = 0; i < length; i++) {
            float[] HSVcolor = { theta[i], 1, DoLP[i] };
            colors[i] = Color.HSVToColor(HSVcolor);
        }

        if (bitmap != null) {
            bitmap.setPixels(colors, 0, width, 0, 0, width, height);
        }


        String fileString = path +"/" + fileName  + ".jpg";
        File file = new File(fileString);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            refreshGallery(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        endTime = System.nanoTime();
        Log.i(TAG, "saving result using: " + timer());

    }

    private void testPhoto(){
        // 1. loading gray
        // 2. calculate
        // 3. saving
        fileTime = new SimpleDateFormat("MMdd_HH:mm").format(Calendar.getInstance().getTime());
        String path = Environment.getExternalStorageDirectory().getPath() + "/PolarizationCamera";
        fileName = new String[]{
                "I0",
                "I90",
                "I45",
                "I135" };

        Result result = calculating(path, fileTime);

        float[] theta = result.getTheta();
        float[] DoLP = result.getDoLP();

        int width = 1080;
        int height = 1920;

        saveFloatToJpg(theta, DoLP, width, height, path, "result_" + fileTime);
//        saveFloatToJpg(theta, "H", width, height, path, "theta_" + fileTime);
//        saveFloatToJpg(DoLP,"V",width, height, path, "DoLP_" + fileTime);
        totalEndTime = System.nanoTime();
        startTime = totalStartTime;
        endTime = totalEndTime;
        Log.i(TAG, "total process spend: " + timer());
    }

    private Bitmap rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight)
    {
        // API at least 28?
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        Matrix matrix = new Matrix();
        matrix.preRotate(90);
        bitmap = Bitmap.createBitmap(bitmap ,0,0, bitmap .getWidth(), bitmap.getHeight(),matrix,true);
        return bitmap;
    }


    private void unlockFocus(){
        Log.i(TAG, "(unlockFocus)--------------------");
        try{
            showToast("Capture Finish");
            CaptureRequest previewRequest = previewRequestBuilder.build();
            captureSession.setRepeatingRequest(previewRequest, null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void btnCapture_click(){
        Log.i(TAG, "(button click)-------------------");
        ind = 1;
        image1 = null;
        image2 = null;
        image3 = null;
        image4 = null;
        try {
            captureSession.stopRepeating();
            playSoundAndTakePicture(ind);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void showToast(final String text) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void requestPermission(String[] stringPermissionNeeded) {

        if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, PERMISSIONS[0]) || ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, PERMISSIONS[1])) {

            new AlertDialog.Builder(MainActivity.this)
                    .setMessage("需要相機和存取相簿權限才可使用此app")
                    .setPositiveButton("了解", (dialog, which) -> ActivityCompat.requestPermissions(MainActivity.this, stringPermissionNeeded, RESULT_PERMISSION_ALL))
                    .setNegativeButton("拒絕", (dialog, which) -> finish())
                    .show();
        }
        else{
            ActivityCompat.requestPermissions(MainActivity.this, stringPermissionNeeded, RESULT_PERMISSION_ALL);
        }
    }

    public void onRequestPermissionsResult(int permsRequestCode, String[] permissions, int[] grantResults){
        switch (permsRequestCode){
            case RESULT_PERMISSION_ALL:
                // if permission is ok, openCamera()
                // if not, do other thing
                // but I don't know why I didn't write anything but the code will go back to "openCamera()" automatically

                //openCamera();
                break;
        }



    }

    private void hideSystemNavigationBar() {
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
            View view = this.getWindow().getDecorView();
            view.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }

    private static String toString(long nanoSecs) {
        int minutes    = (int) (nanoSecs / 60000000000.0);
        int seconds    = (int) (nanoSecs / 1000000000.0)  - (minutes * 60);
        int millisecs  = (int) ( ((nanoSecs / 1000000000.0) - (seconds + minutes * 60)) * 1000);


        if (minutes == 0 && seconds == 0)
            return millisecs + "ms";
        else if (minutes == 0 && millisecs == 0)
            return seconds + "s";
        else if (seconds == 0 && millisecs == 0)
            return minutes + "min";
        else if (minutes == 0)
            return seconds + "s " + millisecs + "ms";
        else if (seconds == 0)
            return minutes + "min " + millisecs + "ms";
        else if (millisecs == 0)
            return minutes + "min " + seconds + "s";

        return minutes + "min " + seconds + "s " + millisecs + "ms";
    }

    private String timer(){
        return String.format("%s", toString(endTime - startTime));
    }

    private void refreshGallery(File file){
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // TODO Auto-generated method stub
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged");
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 什麼都不用寫
        }
        else {
            // 什麼都不用寫
        }
    }

    private void writeCsvFile(float[] data, String path, String filename) {
        try {
            String fileString = path +"/" + filename  + ".csv";
            File file = new File(fileString);
            BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));

            bw.newLine();
            for (int i = 0; i < data.length; i++) {
                bw.write(data[i]+ "");
                bw.newLine();
            }
            bw.close();
            Log.i(TAG, filename + " csv file is finished");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
