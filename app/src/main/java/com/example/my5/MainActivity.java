package com.example.my5;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private EditText etJournalId;
    private Button btnDownload, btnView, btnDelete;
    private TextView statusText;
    private File downloadedFile;
    private static final String PREF_NAME = "AppPreferences";
    private static final String PREF_SHOW_POPUP = "ShowPopup";
    private static final String BASE_URL = "https://ntv.ifmo.ru/file/journal/";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etJournalId = findViewById(R.id.etJournalId);
        btnDownload = findViewById(R.id.btnDownload);
        btnView = findViewById(R.id.btnView);
        btnDelete = findViewById(R.id.btnDelete);
        statusText = findViewById(R.id.statusText);

        new Handler().post(() -> {
            SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            boolean showPopup = preferences.getBoolean(PREF_SHOW_POPUP, true);

            if (showPopup && !isFinishing()) {
                showPopupInstructions();
            }
        });

        btnDownload.setOnClickListener(v -> downloadFile());
        btnView.setOnClickListener(v -> openFile());
        btnDelete.setOnClickListener(v -> deleteFile());
    }

    private void showPopupInstructions() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        if (inflater != null) {
            View popupView = inflater.inflate(R.layout.popup_instruction, null);
            PopupWindow popupWindow = new PopupWindow(popupView,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setFocusable(true);

            CheckBox cbDontShowAgain = popupView.findViewById(R.id.cbDontShowAgain);
            Button btnOk = popupView.findViewById(R.id.btnOk);

            View rootView = findViewById(R.id.popupContainer);
            if (rootView != null) {
                popupWindow.showAtLocation(rootView, Gravity.CENTER, 0, 0);
            }

            btnOk.setOnClickListener(v -> {
                SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(PREF_SHOW_POPUP, !cbDontShowAgain.isChecked());
                editor.apply();
                popupWindow.dismiss();
            });
        }
    }

    private void downloadFile() {
        String journalId = etJournalId.getText().toString().trim();
        if (journalId.isEmpty()) {
            statusText.setText("Пожалуйста, введите номер журнала!");
            return;
        }

        String fileUrl = BASE_URL + journalId + ".pdf";
        new DownloadFileTask().execute(fileUrl, journalId);
    }

    private class DownloadFileTask extends AsyncTask<String, Void, Boolean> {
        private String errorMessage = "";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            statusText.setText("Файл загружается...");
            btnView.setEnabled(false);
            btnDelete.setEnabled(false);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String fileUrl = params[0];
            String journalId = params[1];
            try {
                File dir = new File(getExternalFilesDir(null), "JournalDownloads");
                if (!dir.exists() && !dir.mkdirs()) {
                    errorMessage = "Ошибка при создании директории!";
                    return false;
                }

                downloadedFile = new File(dir, journalId + ".pdf");
                URL url = new URL(fileUrl);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.connect();
                int responseCode = urlConnection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = urlConnection.getInputStream();
                    FileOutputStream fileOutput = new FileOutputStream(downloadedFile);
                    byte[] buffer = new byte[1024];
                    int bufferLength;
                    while ((bufferLength = inputStream.read(buffer)) > 0) {
                        fileOutput.write(buffer, 0, bufferLength);
                    }
                    inputStream.close();
                    fileOutput.close();
                    return true;
                } else {
                    errorMessage = "Ошибка: " + responseCode;
                    return false;
                }
            } catch (Exception e) {
                e.getMessage();
                errorMessage = "Ошибка при скачивании файла: " + e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                statusText.setText("Скачивание завершено!");
                btnView.setEnabled(true);
                btnDelete.setEnabled(true);
            } else {
                statusText.setText(errorMessage);
            }
        }
    }

    private void openFile() {
        if (downloadedFile != null && downloadedFile.exists()) {
            Uri fileUri = FileProvider.getUriForFile(MainActivity.this,
                    getApplicationContext().getPackageName() + ".provider", downloadedFile);
            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(fileUri, "application/pdf");
            viewIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            try {
                startActivity(Intent.createChooser(viewIntent, "Открыть PDF в приложении"));
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Не установлено приложение для просмотра PDF-файлов", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void deleteFile() {
        if (downloadedFile != null && downloadedFile.exists() && downloadedFile.delete()) {
            statusText.setText("Файл удален");
            btnView.setEnabled(false);
            btnDelete.setEnabled(false);
        } else {
            statusText.setText("Ошибка при удалении файла");
        }
    }
}
