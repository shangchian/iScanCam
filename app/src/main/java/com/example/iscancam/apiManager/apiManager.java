package com.example.iscancam.apiManager;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class apiManager {
//    private static String baseUrl = "http://localhost:16021";
    private static String baseUrl = "https://iscan.hamastar.com.tw";
    private static String account = "hamaadm";
    private static String password = "TPc@Hp201910";
    private static String token = "";

    private apiManager() {
    }

    // 使用者登入
    public static String login() {
        String url = baseUrl + "/api/UserData/login-form2";
        try {
            //對url建立POST要求
            URL urlData = new URL(url);
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlData.openConnection();
            httpURLConnection.setReadTimeout(30000);
            httpURLConnection.setConnectTimeout(30000);
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setRequestProperty("Accept", "application/json");
            httpURLConnection.setDoOutput(true);

            // 設置請求參數
            StringBuilder formData = new StringBuilder();
            formData.append("account=").append(account).append("&");
            formData.append("PASSWORD=").append(password);

            //將要發送的數據寫入輸出流
            try (OutputStream outputStream = httpURLConnection.getOutputStream()) {
                outputStream.write(formData.toString().getBytes(StandardCharsets.UTF_8));
            }

            //獲取server的回應
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    token = response.toString().replace("\"", "");
                    return ("登入成功");
                }
            }
            return ("登入失敗");
        } catch (Exception e) {
            e.printStackTrace();
            // DebugMessage.errorLog(TAG, "post", "Exception: "+e.toString());
        }
        return ("登入失敗");
    }

    public static JSONObject getIPCamInfoByAIModel(String aiModel) {
        String url = baseUrl + "/api/Manage/IPCam/GetIPCamInfoByAIModel?AIModel=" + aiModel;
        try {
            //對url建立POST要求
            URL urlData = new URL(url);
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlData.openConnection();
            httpURLConnection.setReadTimeout(30000);
            httpURLConnection.setConnectTimeout(30000);
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.setRequestProperty("Accept", "application/json");
            httpURLConnection.setRequestProperty("Content-type", "application/json");
            // 在請求標頭中添加身份驗證的 token
            httpURLConnection.setRequestProperty("Authorization", token);

            //獲取server的回應
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    String resultString = "[" + response.toString() + "]";

                    // Parse the response
                    if (resultString != null && !resultString.equals("\"[]\"")) {
                        JSONArray jsonArray = new JSONArray(resultString);
                        JSONObject jsonObject = jsonArray.getJSONObject(0);
                        return jsonObject;
                    } else {
                        return null;
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            httpURLConnection.disconnect();
            return new JSONObject("{\"Id\": \"找不到 IPCam\"}");
        } catch (Exception e) {
            e.printStackTrace();
            // DebugMessage.errorLog(TAG, "post", "Exception: "+e.toString());
        }
        return new JSONObject();
    }

    public static String getTrainModelImage(String aiModel) {
        String url = baseUrl + "/api/Manage/IPCamImageProcessor/SolomonProjectOriginalImage?AIModel=" + aiModel;
        String resultString = "";

        try {
            URL urlData = new URL(url);
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlData.openConnection();
            httpURLConnection.setReadTimeout(30000);
            httpURLConnection.setConnectTimeout(30000);
            httpURLConnection.setRequestMethod("GET");
            // 在請求標頭中添加身份驗證的 token
            httpURLConnection.setRequestProperty("Authorization", token);

            //獲取server的回應
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    JSONObject jsonObject = new JSONObject(response.toString());

                    // 從 JSON 物件中取得 "imagePath" 的值
                    String imageBase64Stream = jsonObject.getString("fileContents");
                    resultString = imageBase64Stream;
                }
            }
            httpURLConnection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
            // DebugMessage.errorLog(TAG, "post", "Exception: "+e.toString());
        }
        return resultString;
    }

    public static String uploadIPCamFile(String iPCamId, File imageFile) {
        String boundary = "----Boundary" + System.currentTimeMillis();
        String lineBreak = "\r\n";
        String myUrl = baseUrl + "/api/Manage/IPCam";

        try {
            URL url = new URL(myUrl);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setDoOutput(true);
//            httpURLConnection.setReadTimeout(30000);
//            httpURLConnection.setConnectTimeout(30000);
            httpURLConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            httpURLConnection.setRequestProperty("Authorization", token);

            OutputStream outputStream = httpURLConnection.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream));

            addFormField(writer, boundary, "iPCamId", iPCamId);
            addFilePart(writer, boundary, "files", outputStream, imageFile);

            // End of multipart/form-data request
            writer.append(lineBreak).append("--").append(boundary).append("--").append(lineBreak);
            writer.close();

            // Read the response
            int responseCode = httpURLConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    String resultString = response.toString();;

                    // Parse the response
                    if (resultString != null && !resultString.equals("\"[]\"")) {
//                        JSONArray jsonArray = new JSONArray(resultString);
//                        JSONObject jsonObject = jsonArray.getJSONObject(0);
//                        String imagePath = jsonObject.getString("imagePath");
//                        return String.format("https://magicmodulesdata.hamastar.com.tw/api/Storage/%s/download", imagePath);
                        return resultString;
                    } else {
                        return null;
                    }
                }
            } else {
                // Read the error response
                try (BufferedReader br = new BufferedReader(new InputStreamReader(httpURLConnection.getErrorStream()))) {
                    String line;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((line = br.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    return errorResponse.toString();
                }
            }
        } catch (ProtocolException e) {
            System.out.println(e);
            return(e.toString());
        } catch (MalformedURLException e) {
            System.out.println(e);
            return(e.toString());
        } catch (IOException e) {
            System.out.println(e);
            return(e.toString());
        }
    }

    private static void addFormField(PrintWriter writer, String boundary, String fieldName, String fieldValue) {
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"").append("\r\n");
        writer.append("\r\n");
        writer.append(fieldValue).append("\r\n");
        writer.flush();
    }

    private static void addFilePart(PrintWriter writer, String boundary, String fieldName, OutputStream outputStream, File file) throws IOException {
        writer.append("--").append(boundary).append("\r\n");
        writer.append("Content-Disposition: form-data; name=\"").append(fieldName)
                .append("\"; filename=\"").append(file.getName()).append("\"").append("\r\n");
        writer.append("Content-Type: ").append("application/octet-stream").append("\r\n");
        writer.append("\r\n");
        writer.flush();

        FileInputStream inputStream = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        inputStream.close();

        writer.append("\r\n");
        writer.flush();
    }

    public static Bitmap showImage(String imageURL) {
        try {
            URL url = new URL(imageURL);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setDoInput(true);
            httpURLConnection.connect();
            InputStream inputStream = httpURLConnection.getInputStream();
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            return bitmap;
        } catch (Exception e) {
            Log.e("Error Message", e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
