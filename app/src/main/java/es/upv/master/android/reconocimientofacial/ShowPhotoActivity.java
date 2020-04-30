package es.upv.master.android.reconocimientofacial;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static es.upv.master.android.reconocimientofacial.RecognitionActivity.REQUEST_WRITE_EXTERNAL_STORAGE;
import static es.upv.master.android.reconocimientofacial.RecognitionActivity.TYPE_PHOTO;
import static es.upv.master.android.reconocimientofacial.RecognitionActivity.bitmapPhoto;
import static es.upv.master.android.reconocimientofacial.camera.PhotoRotation.resize;

public class ShowPhotoActivity extends AppCompatActivity {
    private ImageView fotoFinal;
    private Button aceptar, cancelar;
    private Bitmap bitmapShowFoto;
    private String typePhotoResult;
    //public static final String BitmapPhoto = "BitmapPhoto";
    public static final String TypePhoto = "TypePhoto";
    public static final String nombreDirectorioFotos = "Mascarillas";
    //Nombre de las imágenes
    private SharedPreferences preferenciasNamePhoto;
    //Ruta de la imagen
    private File imageFile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_photo);
        //Parte gráfica
        fotoFinal= findViewById(R.id.imgPhotoFinal);
        aceptar = findViewById(R.id.btn_acept);
        cancelar = findViewById(R.id.btn_cancel);
        //Inicializo archivo de preferencia para guardar los nombres de las fotos
        preferenciasNamePhoto = getApplicationContext()
                .getSharedPreferences("PhotoName", Context.MODE_PRIVATE);

        Bundle extras = getIntent().getExtras();
        typePhotoResult = (String) extras.get(TypePhoto);
        //bitmapShowFoto = (Bitmap) extras.get(BitmapPhoto);
        bitmapShowFoto = bitmapPhoto;

        if(bitmapShowFoto != null)
            fotoFinal.setImageBitmap(bitmapShowFoto);

    }

    public void cancel(View view){
        bitmapShowFoto = null;
        bitmapPhoto = null;
        finish();
    }

    public void acept(View view){

        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String photoName = timeStamp+"_"+typePhotoResult;
        savePhotosExternalStorage(nombreDirectorioFotos, photoName, bitmapShowFoto, getApplicationContext());
        bitmapPhoto = null;
        TYPE_PHOTO++;
        savePhotoNameInPreference(""+TYPE_PHOTO, photoName);
        finish();
    }

    public void savePhotosExternalStorage(String nameFolder, String nameFile, Bitmap photo, Context context){

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            final String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, permissions, REQUEST_WRITE_EXTERNAL_STORAGE);
            return;
        }
        try {
            File imageFile;
            String state = Environment.getExternalStorageState();
            File folder = null;
            if (state.contains(Environment.MEDIA_MOUNTED)) {
                folder = new File(Environment
                        .getExternalStorageDirectory() + "/"+nameFolder);
            } else {
                folder = new File(Environment
                        .getExternalStorageDirectory() + "/"+nameFolder);
            }

            boolean success = true;
            if (!folder.exists()) {
                success = folder.mkdirs();
            }
            if (success) {
                imageFile = new File(folder.getAbsolutePath()
                        + File.separator
                        + nameFile+".jpg");


                imageFile.createNewFile();

            } else {
                Toast.makeText(getBaseContext(), "Image Not saved",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            ByteArrayOutputStream ostream = new ByteArrayOutputStream();
            // save image into gallery
            photo = resize(photo, 800, 600);
            photo.compress(Bitmap.CompressFormat.JPEG, 100, ostream);

            FileOutputStream fout = new FileOutputStream(imageFile);
            fout.write(ostream.toByteArray());
            fout.close();
            ContentValues values = new ContentValues();

            values.put(MediaStore.Images.Media.DATE_TAKEN,
                    System.currentTimeMillis());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.DATA,
                    imageFile.getAbsolutePath());

            context.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            setResult(Activity.RESULT_OK); //add this


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void savePhotoNameInPreference(String key, String value){
        SharedPreferences.Editor editor = preferenciasNamePhoto.edit();
        editor.putString(key,value);
        editor.commit();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String permissions[],
                                                     int[] grantResults) {
        switch (REQUEST_WRITE_EXTERNAL_STORAGE) {
            case 1: {
                if (!(grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(ShowPhotoActivity.this,
                            "Has denegado algún permiso de la aplicación.",
                            Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

}
