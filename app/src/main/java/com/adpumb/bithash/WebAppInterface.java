package com.adpumb.bithash;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

public class WebAppInterface {
    Context context;
    WebView webView;

    public WebAppInterface(Context ctx, WebView webView) {
        this.context = ctx;
        this.webView = webView;
    }

    @JavascriptInterface
    public void downloadPDF(String base64Data, String filename) {
        try {
            // Remove data URL prefix if present
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }

            // Decode base64 to bytes
            byte[] pdfData;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                pdfData = Base64.getDecoder().decode(base64Data);
            } else {
                pdfData = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
            }

            saveToDownloads(pdfData, filename);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Error downloading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveToDownloads(byte[] bytes, String filename) {
        try {
            // Get the public Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            // Create the directory if it doesn't exist
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            // Create the file
            File file = new File(downloadsDir, filename);

            // Check if file already exists and create a unique name if needed
            int counter = 1;
            String baseName = filename.replace(".pdf", "");
            while (file.exists()) {
                file = new File(downloadsDir, baseName + "(" + counter + ").pdf");
                counter++;
            }

            // Write the file
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();

            // Show success message
            String message = "PDF saved to Downloads: " + file.getName();
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            Log.d("PDFDownload", message);

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Error saving PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}