package com.example.iscancam;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iscancam.apiManager.apiManager;
import com.example.iscancam.camera.RectangleOverlay;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String API_URL = "https://your-api-url.com/upload";
    private static final int REQUEST_EXTERNAL_STORAGE_PERMISSION = 101;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int CAMERA_REQUEST_IMAGE_CAPTURE = 1;

    private Button btnScanQRCode, btnStartCamera, btnCloseCamera, btnCapture;
    private ImageView imageView;
    private TextView txvUsername, txvRecognitionResult;

    // OSD
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Camera camera;
    private ImageCapture imageCapture;
    private PreviewView previewView;
    private RectangleOverlay rectangleOverlay;
    private static final String[] PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    // Data
    private String scanIPCamId = "";
    /*  recognitionData
        index 0: Value
        index 1: ImagePath
     */
    private ArrayList<String> recognitionData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 元件綁定
        bindingsComponent();

        // 檢查相機權限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }

        // 檢查讀寫外部存儲的權限
        requestStoragePermission();

        // modify --------------------------------------------------------------------
        txvUsername.setText("登入中...");

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                String response = apiManager.login();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 更新TextView的文本
                        txvUsername.setText(response);

                        // 顯示Toast提示
                        Toast.makeText(MainActivity.this, response, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        thread.start();
        // ---------------------------------------------------------------------------
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        if (v == btnScanQRCode) {
            // 啟動掃描器
            startScanner();
        } else if (v == btnStartCamera) {
            // 相機按鈕點擊事件
            //dispatchTakePictureIntent();

            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                requestPermissionsIfNecessary();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        } else if (v == btnCloseCamera) {
            try {
                stopCamera();
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else if (v == btnCapture) {
            takePicture();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        // 接收相機拍照的結果
        if (requestCode == CAMERA_REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            // Do something with the imageBitmap
            imageView.setImageBitmap(imageBitmap);
            Toast.makeText(MainActivity.this, "圖片", Toast.LENGTH_LONG).show();

            // Launch DrawingActivity to draw a box around the live view
//            Intent intent = new Intent(MainActivity.this, DrawingActivity.class);
//            startActivity(intent);


            // call API to upload image
            // new Upload(API_URL).execute(imageBitmap);
        }

        // 接收 QR碼掃描器 的結果
        if (result != null) {
            if (result.getContents() != null) {
                // 顯示QR碼的資訊
                // Toast.makeText(this, result.getContents(), Toast.LENGTH_LONG).show();


                // modify --------------------------------------------------------------------
                // 透過 AIModel 辨識當前 IPCam
                txvUsername.setText("IPCam 辨識中...");

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        JSONObject response = apiManager.getIPCamInfoByAIModel(result.getContents());
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    scanIPCamId = response.getString("Id").toString().replace("\"", "");
                                    String scanIPCamName = response.getString("Name").toString().replace("\"", "");
                                    txvUsername.setText("目前 IPCam 是：" + scanIPCamName);
                                    JSONArray targetCoordinate = response.getJSONArray("TargetCoordinate");
                                    rectangleOverlay.drawRectByTargetCoordinate(targetCoordinate);
                                } catch (JSONException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                });
                thread.start();
                // ---------------------------------------------------------------------------

            } else {
                Toast.makeText(this, "掃描取消", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }


    // 元件綁定
    private void bindingsComponent() {
        // Button
        btnScanQRCode = findViewById(R.id.scan_button);
        btnStartCamera = findViewById(R.id.start_camera_button);
        btnCloseCamera = findViewById(R.id.close_camera_button);
        btnCapture = findViewById(R.id.capture_button);
        btnScanQRCode.setOnClickListener(this);
        btnStartCamera.setOnClickListener(this);
        btnCloseCamera.setOnClickListener(this);
        btnCapture.setOnClickListener(this);

        // ImageView: 顯示照片
        imageView = findViewById(R.id.imageView);

        // TextView
        txvUsername = findViewById(R.id.username_textview);
        txvRecognitionResult = findViewById(R.id.result);

        // Open camera and draw OSD on preview
        previewView = findViewById(R.id.previewView);
        rectangleOverlay = findViewById(R.id.rectangleOverlay);

//        changeSizeButton = new Button(this);
//        changeSizeButton.setText("改變方框大小");
//        changeSizeButton.setOnClickListener(v -> {
//            // 假設您希望將方框的寬度和高度設置為300像素
//            rectangleOverlay.setRectangleSize(300, 300);
//        });
    }


    // 設置元件監聽事件
    private void setOnClickListener() {
        // 設置 QRCode按鈕點擊事件
        btnScanQRCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        // 設置相機按鈕點擊事件
        btnStartCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
    }


    // 啟動 QR碼掃描器
    private void startScanner() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("請對準QR碼");
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }


    // 啟動相機：最簡單的方式
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, CAMERA_REQUEST_IMAGE_CAPTURE);
        }
    }


    // 處理相機權限請求的回調
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 啟動掃描器
                startScanner();
            } else {
                Toast.makeText(this, "需要相機權限才能掃描QR碼", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_EXTERNAL_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "存儲權限已授予");
            } else {
                Log.e(TAG, "存儲權限被拒絕");
                Toast.makeText(this, "存儲權限被拒絕", Toast.LENGTH_LONG).show();
            }
        }
    }

    // 檢查讀寫外部存儲的權限
    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_EXTERNAL_STORAGE_PERMISSION);
        }
    }


    // 啟動相機 preview:
    private void requestPermissionsIfNecessary() {
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            startCamera();
        }
    }

    public static boolean hasPermissions(MainActivity context, String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    // activity_main.xml 創建一個 view，將 RectangleOverlay.java 畫在 Canvas 的框與 view 疊加在一起。（因為有繼承 View）
    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        if (cameraProviderFuture != null) {

            cameraProviderFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        camera = bindCameraUseCases(cameraProvider, previewView);
                        showCamera(true);
                    } catch (InterruptedException | ExecutionException e) {
                        Log.e(TAG, "Failed to bind camera use cases", e);
                    }
                }
            }, ContextCompat.getMainExecutor(this));
        } else {
            Log.e(TAG, "Attempt to invoke interface method 'void com.google.common.util.concurrent.ListenableFuture.addListener(java.lang.Runnable, java.util.concurrent.Executor)' on a null object reference");
        }
    }

    private Camera bindCameraUseCases(ProcessCameraProvider cameraProvider, PreviewView previewView) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // 相機有 preview 功能
        Preview preview = new Preview.Builder()
                .build();

        // modify --------------------------------------------------------------------
        // 獲取裝置的屏幕解析度
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        // 相機有 拍照 功能
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(new Size(screenWidth, screenHeight))
                .build();

        // 設置預覽視圖的寬度和高度為相機的解析度
        // previewView.setLayoutParams(new ConstraintLayout.LayoutParams(screenWidth, screenHeight));
        // ---------------------------------------------------------------------------

        // modify --------------------------------------------------------------------
        previewView.post(new Runnable() {
            @Override
            public void run() {
                previewView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                return true;
                            case MotionEvent.ACTION_UP:
                                MeteringPointFactory factory = previewView.getMeteringPointFactory();
                                MeteringPoint autoFocusPoint = factory.createPoint(event.getX(), event.getY());
                                Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) v.getContext(), cameraSelector, preview, imageCapture);
                                CameraControl cameraControl = camera.getCameraControl();
                                cameraControl.startFocusAndMetering(
                                        new FocusMeteringAction.Builder(autoFocusPoint, FocusMeteringAction.FLAG_AF)
                                                .setAutoCancelDuration(5, TimeUnit.SECONDS)
                                                .build()
                                );
                                return true;
                            default:
                                return false; // Unhandled event.
                        }
                    }
                });
            }
        });
        // ---------------------------------------------------------------------------

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture);
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        return camera;
    }

    private void stopCamera() throws ExecutionException, InterruptedException {
        if (camera != null) {
            camera.getCameraControl().setLinearZoom(0f);
            try {
                cameraProviderFuture.get().unbindAll();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error unbinding use cases", e);
                // 处理异常，例如显示一个提示信息
            }
            camera = null;
        }

        showCamera(false);
    }

    private void takePhoto_saveImageAsInternalPath() {
        if (imageCapture == null) {
            return;
        }

        // 創建圖片文件
        File photoFile = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photo-" + System.currentTimeMillis() + ".jpg");

        // 創建輸出選項，包括圖片文件
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // 執行拍照並保存圖片
        imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // 成功保存圖片時的回調
                String msg = "Photo saved: " + photoFile.getAbsolutePath();
                Log.d("CameraXApp saved path: ", msg);
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                // 拍照出錯時的回調
                Log.e("CameraXApp", "Photo capture failed: " + exception.getMessage(), exception);
            }
        });
    }

    // 照片儲存在外部儲存位置：App > 我的檔案 > 圖像
    private void takePicture() {
        // Don't take a picture if the camera is not available
        if (camera == null) {
            return;
        }

        ContentValues contentValues = createContentValues();
        Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

        try {
            OutputStream outputStream = getContentResolver().openOutputStream(imageUri);
            ImageCapture.OutputFileOptions outputFileOptions =
                    new ImageCapture.OutputFileOptions.Builder(outputStream)
                            .setMetadata(new ImageCapture.Metadata())
                            .build();

            imageCapture.takePicture(
                    outputFileOptions,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {
                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Image saved", Toast.LENGTH_SHORT).show();
                            });
                            if (imageUri != null) {
                                String[] projection = {MediaStore.Images.Media.DATA};
                                Cursor cursor = getContentResolver().query(imageUri, projection, null, null, null);
                                if (cursor != null) {
                                    if (cursor.moveToFirst()) {
                                        @SuppressLint("Range") String filePath = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                                        Log.d("TAG", "Image saved path: " + filePath);

                                        // modify --------------------------------------------------------------------
                                        // 上傳圖片
                                        txvUsername.setText("圖片上傳中...");
                                        Thread uploadImagethread = new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                String iPCamId = scanIPCamId;
                                                File imageFile = new File(filePath);
//                                                File myFile = new File("/storage/emulated/0/Download/哈瑪星IPCam1 2023-05-11 下午 01.40.50.jpg");

                                                // 呼叫上傳圖片API
                                                String result = apiManager.uploadIPCamFile(iPCamId, imageFile);

                                                getRecognitionData(result);

                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
//                                                        imageView.setImageBitmap(BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
                                                        // 顯示圖片網址
                                                        if("".equals(recognitionData.get(1))){
                                                            txvUsername.setText("");
                                                        }else{
                                                            txvUsername.setText(recognitionData.get(1));
                                                        }
                                                        if (recognitionData.get(1).contains("java.net")){
                                                            // 顯示Toast提示
                                                            Toast.makeText(MainActivity.this, "上傳失敗", Toast.LENGTH_LONG).show();
                                                        } else {
                                                            // 顯示Toast提示
                                                            Toast.makeText(MainActivity.this, "上傳成功", Toast.LENGTH_LONG).show();
                                                        }

                                                        // 透過 url 取得圖片
                                                        Thread getImageThread = new Thread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                Bitmap imageBit = null;
                                                                if(!"".equals(recognitionData.get(1))){
                                                                    imageBit = apiManager.showImage(recognitionData.get(1));
                                                                }
                                                                Bitmap finalImageBit = imageBit;
                                                                runOnUiThread(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        if(finalImageBit != null){
                                                                            imageView.setImageBitmap(finalImageBit);
                                                                        }
                                                                        txvRecognitionResult.setText(recognitionData.get(0));
                                                                    }
                                                                });
                                                            }
                                                        });
                                                        getImageThread.start();
                                                    }
                                                });
                                            }
                                        });
                                        uploadImagethread.start();

                                        // 拍照後，關閉相機
                                        try {
                                            stopCamera();
                                        } catch (ExecutionException e) {
                                            throw new RuntimeException(e);
                                        } catch (InterruptedException e) {
                                            throw new RuntimeException(e);
                                        }
                                        // ---------------------------------------------------------------------------
                                    }
                                    cursor.close();
                                }
                            }
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Image capture failed", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, "Error opening output stream", Toast.LENGTH_SHORT).show();
        }
    }

    private void showCamera(boolean show) {
        if (show) {
            btnScanQRCode.setVisibility(View.GONE);
            btnStartCamera.setVisibility(View.GONE);
            btnCloseCamera.setVisibility(View.VISIBLE);
            previewView.setVisibility(View.VISIBLE);
            rectangleOverlay.setVisibility(View.VISIBLE);
        } else {
            btnScanQRCode.setVisibility(View.VISIBLE);
            btnStartCamera.setVisibility(View.VISIBLE);
            btnCloseCamera.setVisibility(View.GONE);
            previewView.setVisibility(View.GONE);
            rectangleOverlay.setVisibility(View.GONE);
        }
    }

    private ContentValues createContentValues() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "photo-" + System.currentTimeMillis() + ".jpg");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
        }
        return contentValues;
    }

    private ArrayList<String> getRecognitionData(String responseStr) {
        //imageView.setImageResource(R.drawable.ic_launcher_foreground);
        recognitionData.clear();
        String imageURL = "";
        String jsonRecognitionData = "";
        JSONArray jsonArray = null;

        if (responseStr != null && !responseStr.equals("\"[]\"")) {
            try {
                jsonArray = new JSONArray(responseStr);
                JSONObject jsonObject = jsonArray.getJSONObject(0);


                String imagePath = jsonObject.getString("imagePath");
                imageURL = String.format("https://magicmodulesdata.hamastar.com.tw/api/Storage/%s/download", imagePath);


                JSONObject valueObject = new JSONObject(jsonObject.getString("value"));

                // 將 valueObject 轉換為指定格式的字串
                StringBuilder formattedStrBuilder = new StringBuilder();
                Iterator<String> keys = valueObject.keys();
                while (keys.hasNext()) {
                    double value = 0;
                    String key = keys.next();
                    if (valueObject.isNull(key)){

                    } else {
                        value = valueObject.getDouble(key);
                    }
                    formattedStrBuilder.append(key).append(" = ").append(value).append(", ");
                }
                // 刪除最後一個多餘的逗號和空格
                String formattedStr = formattedStrBuilder.toString();
                if (formattedStr.endsWith(", ")) {
                    formattedStr = formattedStr.substring(0, formattedStr.length() - 2);
                }
                jsonRecognitionData = formattedStr;

            } catch (JSONException e) {
                e.printStackTrace();
            }

            recognitionData.add(0, jsonRecognitionData);     // Value: 感測數據
            recognitionData.add(1, imageURL);                // ImagePath: 圖檔儲存位置
        }

        return recognitionData;
    }
}