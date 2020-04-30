package es.upv.master.android.reconocimientofacial;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
//import com.google.android.gms.vision.CameraSource;
import es.upv.master.android.reconocimientofacial.camera.CameraSource;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnPausedListener;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import es.upv.master.android.reconocimientofacial.camera.CameraSourcePreview;
import es.upv.master.android.reconocimientofacial.camera.PhotoRotation;
import es.upv.master.android.reconocimientofacial.camera.GraphicOverlay;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/*import static es.upv.master.android.reconocimientofacial.Comun.getStorageReference;
import static es.upv.master.android.reconocimientofacial.Comun.storage;
import static es.upv.master.android.reconocimientofacial.Comun.storageRef;*/
import static es.upv.master.android.reconocimientofacial.ShowPhotoActivity.nombreDirectorioFotos;
import static es.upv.master.android.reconocimientofacial.camera.PhotoRotation.rotateImage;

public class RecognitionActivity extends AppCompatActivity {

    private static final String TAG = "FaceTracker";

    private CameraSource mCameraSource = null;
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private Button btnFlas, btnGirarCamara, btnTakePhoto;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;
    static final int REQUEST_CAMERA_AND_STORAGE = 0;
    static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private ImageView diagramaCara, miniaturaFotoF, miniaturaFotoP;
    public static float valorVisibilidadCara = (float) 0.4;

    //Tipo de foto frontal (F), perfil (P)
    public static int TYPE_PHOTO = 0;
    private String typePhoto;
    public static Bitmap bitmapPhoto;
    private boolean voltearCamara;
    private boolean autoFocus, useFlash;
    private int idCamera;

    //Firebase
    StorageReference imagenRef;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    static UploadTask uploadTask=null;

    final int SOLICITUD_SUBIR_PUTDATA = 0;
    final int SOLICITUD_SUBIR_PUTSTREAM = 1;
    Boolean subiendoDatos =false;
    private ArrayList<Bitmap> listBitmapPhotos;
    private int numPhotoUp = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);
        //Parte Gráfica
        mPreview = (CameraSourcePreview) findViewById(R.id.facePreview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        diagramaCara = (ImageView)findViewById(R.id.imgCara);
        miniaturaFotoF = (ImageView)findViewById(R.id.imgPhotoF);
        miniaturaFotoP = (ImageView)findViewById(R.id.imgPhotoP);
        btnFlas = (Button) findViewById(R.id.btn_flash);
        btnGirarCamara = (Button) findViewById(R.id.btn_girarCamara);
        //btnSharePhotos = (Button) findViewById(R.id.btn_sharePhoto);
        btnTakePhoto = (Button) findViewById(R.id.btn_takePhoto);

        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReferenceFromUrl( "gs://reconocimiento-facial-2ff83.appspot.com");

        //Añade el permiso para acceder a la cámara y a almacenamiento
        String[] PERMISOS =
                {Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.GET_ACCOUNTS,
                        Manifest.permission.CAMERA};
        ActivityCompat.requestPermissions(this, PERMISOS, REQUEST_CAMERA_AND_STORAGE);
        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            //configurando camara
            autoFocus = true; useFlash = false;
            createCameraSource(autoFocus, useFlash);
        } else {
            requestCameraPermission();
        }


        listBitmapPhotos = new ArrayList<Bitmap>();
        //Primer tipo de foto es frontal FRONT
        typePhoto = "F";
        idCamera = CameraSource.CAMERA_FACING_FRONT;
        voltearCamara = false;


    }

    public void visibilidadImagCara( ){
        new Thread(new Runnable() {
         public void run() {
            if(valorVisibilidadCara < 0){
                valorVisibilidadCara = 0;
            }
            diagramaCara.setAlpha(valorVisibilidadCara);
             }
            }).start();
    }

    public void takeImage(View view) {

           // new Thread(new Runnable() {
               // public void run() {
                    try{
                    //Aquí ejecutamos nuestras tareas costosas
                    mCameraSource.takePicture(null, new CameraSource.PictureCallback() {

                        @Override
                        public void onPictureTaken(byte[] bytes) {
                            try {
                                // convert byte array into bitmap
                                Bitmap loadedImage = null;
                                Bitmap rotatedBitmap = null;
                                loadedImage = BitmapFactory.decodeByteArray(bytes, 0,
                                        bytes.length);
                                //Observo cuanto es el bitma que obtengo al tomar la foto
                                Log.d("BITMAP", loadedImage.getWidth() + "x" + loadedImage.getHeight());

                                // rotate Image
                                int orientation = PhotoRotation.getOrientation(bytes);
                                switch(orientation) {
                                    case 90:
                                        rotatedBitmap= rotateImage(loadedImage, 90);

                                        break;
                                    case 180:
                                        rotatedBitmap= rotateImage(loadedImage, 180);

                                        break;
                                    case 270:
                                        rotatedBitmap= rotateImage(loadedImage, 270);

                                        break;
                                    case 0:
                                        // if orientation is zero we don't need to rotate this
                                        rotatedBitmap = loadedImage;
                                    default:
                                        break;
                                }
                                //Fin de rotar Image
                                bitmapPhoto = rotatedBitmap;
                                listBitmapPhotos.add(rotatedBitmap);

                                Intent i = new Intent(getApplicationContext(), ShowPhotoActivity.class);
                                //i.putExtra("BitmapPhoto", rotatedBitmap);
                                i.putExtra("TypePhoto", typePhoto);//"F-P"
                                startActivity(i);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                }catch (Exception ex){
                    Log.e(TAG, "Error al capturar fotografia!");
                }
           // }
           // }).start();
    }


    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    private void createCameraSource(boolean autoFocus, boolean useFlash) {

        Context context = this.getApplicationContext();

       FaceDetector detector = new FaceDetector.Builder(context)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();
        detector.setProcessor(
                new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
                        .build());

        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.

        if (!detector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }
        //Eligiendo la cámara
        idCamera = voltearCamara ? CameraSource.CAMERA_FACING_BACK :  CameraSource.CAMERA_FACING_FRONT;
        //Configurando recursos de vista
        settingCameraResource(idCamera);

        mCameraSource = new CameraSource.Builder(getApplicationContext(), detector)
                .setFacing(idCamera )
                .setRequestedPreviewSize(1280, 720) //1024, 768 //1280, 960 //1280, 720
                .setRequestedFps(30.0f)
                //.setAutoFocusEnabled(true)
                .setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
                .build();

/*        CameraSource.Builder builder = new CameraSource.Builder(getApplicationContext(), detector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1600, 1024)
                .setRequestedFps(15.0f);

        builder = builder.setFocusMode(
                autoFocus ? Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE : null);

        mCameraSource = builder
                .setFlashMode(useFlash ? Camera.Parameters.FLASH_MODE_TORCH : null)
                .build();*/

    }

    private void transiccionEntreActivities(){
        valorVisibilidadCara = 0.5f;
        switch (TYPE_PHOTO){
            case 0:
                typePhoto = "F";
                if(listBitmapPhotos.size() == 1)
                    listBitmapPhotos.remove(0);
                break;
            case 1:
                typePhoto = "P";
                miniaturaFotoP.setVisibility(View.VISIBLE);
                miniaturaFotoF.setImageBitmap(listBitmapPhotos.get(0));
                diagramaCara.setImageResource(R.drawable.cara_p);
                if(listBitmapPhotos.size() == 2)
                    listBitmapPhotos.remove(1);
                break;
            case 2:
                typePhoto = "F";
                TYPE_PHOTO = 0;
                diagramaCara.setImageResource(R.drawable.cara_f);
                miniaturaFotoP.setImageBitmap(listBitmapPhotos.get(1));
                subirAFirebaseStorage(SOLICITUD_SUBIR_PUTDATA,null);
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCameraSource();
        //según el usuario acepte las fotos se va configurando las vistas
        transiccionEntreActivities();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mPreview.stop();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode){
            //Pregunto por todos los permisos si hay más de 1 quiere decir que tengo todos los permisos(2) y activo la cámara
            case REQUEST_CAMERA_AND_STORAGE: {
                if (!(grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(TAG, "We have not write external storage permission ");
                    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish();
                        }
                    };
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Reconocimiento de Mascarilla")
                            .setMessage(R.string.no_camera_permission)
                            .setPositiveButton(R.string.ok, listener)
                            .show();


                }else{
                    autoFocus = true; useFlash = false;
                    createCameraSource(autoFocus, useFlash);
                }
                return;
            }
            case REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.d(TAG, "We have not write external storage permission ");
                    Toast.makeText(RecognitionActivity.this, "Has denegado algún permiso de la aplicación.",
                            Toast.LENGTH_SHORT).show();
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                }//else{
                return;
                //}

            }
            case RC_HANDLE_CAMERA_PERM: {
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission granted - initialize the camera source");
                    // we have permission, so create the camerasource
                    createCameraSource(autoFocus, useFlash);
                    return;
                }else{
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                }
            }

        }

    }

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */

    private void startCameraSource()  throws SecurityException {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview.start(mCameraSource, mGraphicOverlay);
               // openCamera(voltearCamara ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }



    private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
        @Override
        public Tracker<Face> create(Face face) {
            return new GraphicFaceTracker(mGraphicOverlay);
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private class GraphicFaceTracker extends Tracker<Face> {
        private GraphicOverlay mOverlay;
        private FaceGraphic mFaceGraphic;

        GraphicFaceTracker(GraphicOverlay overlay) {
            mOverlay = overlay;
            mFaceGraphic = new FaceGraphic(overlay);
        }

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        @Override
        public void onNewItem(int faceId, Face item) {
            mFaceGraphic.setId(faceId);
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        @Override
        public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
            mOverlay.add(mFaceGraphic);
            mFaceGraphic.updateFace(face);
            visibilidadImagCara();
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        @Override
        public void onMissing(FaceDetector.Detections<Face> detectionResults) {
            if(valorVisibilidadCara <= 0){
                valorVisibilidadCara = 0.5f;
            }
            visibilidadImagCara();
            mOverlay.remove(mFaceGraphic);
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        @Override
        public void onDone() {
            mOverlay.remove(mFaceGraphic);
        }
    }


    ///
    public void rotarCamara(View view){
        //Informo que se desea voltear la cámara a través del atributo voltearCamara
        //voltearCamara = !voltearCamara;
        if(voltearCamara){
            voltearCamara = false;
        }
        else{
            voltearCamara = true;
        }
        //Libero la camara
        if (mCameraSource != null) {
            mCameraSource.release();
        }
        //Comfiguro la nueva cámara de la nueva camara y la inicio
        createCameraSource(true, useFlash);
        startCameraSource();
    }

    public void settingCameraResource(int idCamera){
        if(idCamera == CameraSource.CAMERA_FACING_FRONT){
            useFlash = false;
            btnFlas.setVisibility(View.INVISIBLE);

        }else {
            useFlash = false;
            btnFlas.setVisibility(View.VISIBLE);
        }
    }


    public void activarFlash(View view){
        useFlash = !useFlash;
        if(useFlash){
            mCameraSource .setFlashMode( Camera.Parameters.FLASH_MODE_TORCH);// FLASH_MODE_OFF,  FLASH_MODE_ON,  FLASH_MODE_TORCH
            btnFlas.setBackgroundResource(R.drawable.ic_flash_on);
        }else{
            mCameraSource .setFlashMode( Camera.Parameters.FLASH_MODE_OFF);
            btnFlas.setBackgroundResource(R.drawable.ic_flash_off);
        }

    }

    //Función me permite subir las fotos al servidos a través del botón btn_sharePhoto
    public void sharePhoto(View view){
        subirAFirebaseStorage(SOLICITUD_SUBIR_PUTDATA,null);
    }


    //
     public void subirAFirebaseStorage(Integer opcion, String ficheroDispositivo) {

        if (!listBitmapPhotos.isEmpty()){
            //Condición
            //La función es recursiva por eso esta variable me permite enumerar las fotos que se van subiendo a firebase
            numPhotoUp++;

            final ProgressDialog progresoSubida = new ProgressDialog(RecognitionActivity.this);
            progresoSubida.setTitle("Subiendo... Foto"+numPhotoUp+"/"+2);
            progresoSubida.setMessage("Espere...");
            progresoSubida.setCancelable(true);
            progresoSubida.setCanceledOnTouchOutside(false);
            progresoSubida.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancelar",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            uploadTask.cancel();
                            settingToStart();
                            return;
                        }
                    });

            final SharedPreferences preferenciasNamePhoto = getApplicationContext()
                    .getSharedPreferences("PhotoName", Context.MODE_PRIVATE);
            String photoName = preferenciasNamePhoto.getString(""+numPhotoUp, null);
            if(photoName == null){

            }
            imagenRef = storageRef.child(nombreDirectorioFotos).child(photoName);
            try {
                switch (opcion) {
                    case SOLICITUD_SUBIR_PUTDATA:
                        //miniaturaFotoF.setDrawingCacheEnabled(true);
                        //miniaturaFotoF.buildDrawingCache();
                        //Bitmap bitmap = miniaturaFotoF.getDrawingCache();
                        Bitmap bitmap = listBitmapPhotos.get(0);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                        byte[] data = baos.toByteArray();
                        uploadTask = imagenRef.putBytes(data);
                        break;
                    case SOLICITUD_SUBIR_PUTSTREAM:
                        InputStream stream = new FileInputStream( new File(ficheroDispositivo));
                        uploadTask = imagenRef.putStream(stream);
                        break;
                }

                uploadTask .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        subiendoDatos=false;
                        //mostrarDialogo(getApplicationContext(), "Ha ocurrido un error al" +
                        //    " subir la imagen o el usuario ha cancelado la subida.");
                        Snackbar.make( mGraphicOverlay,R.string.error_upload_photos, Snackbar.LENGTH_INDEFINITE)
                                .setAction("SI", new View.OnClickListener()
                                { @Override public void onClick(View view) {
                                    if (!listBitmapPhotos.isEmpty()){
                                        //Si hay un error debe disminuir
                                        if(numPhotoUp>0) numPhotoUp--;
                                        subirAFirebaseStorage(SOLICITUD_SUBIR_PUTDATA,null);
                                    }
                                } })
                                .setAction("NO",new View.OnClickListener()
                                { @Override public void onClick(View view) {  settingToStart(); } }).show();

                    }
                })
                        .addOnSuccessListener(
                                new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                        progresoSubida.dismiss();
                                        subiendoDatos=false;
                                        listBitmapPhotos.remove(0);
                                        subirAFirebaseStorage(SOLICITUD_SUBIR_PUTDATA, null);
                                    }
                                })
                        .addOnProgressListener(
                                new OnProgressListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                                        if (!subiendoDatos) {
                                            progresoSubida.show();
                                            subiendoDatos=true;
                                        } else {
                                            if (taskSnapshot.getTotalByteCount()>0)
                                                progresoSubida.setMessage("Espere... " +
                                                        String.valueOf(100*taskSnapshot.getBytesTransferred()
                                                                /taskSnapshot.getTotalByteCount())+"%");
                                        }
                                    }
                                })
                        .addOnPausedListener(
                                new OnPausedListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onPaused(UploadTask.TaskSnapshot taskSnapshot) {
                                        //UploadTask pausa
                                        subiendoDatos=false;
                                        // mostrarDialogo(getApplicationContext(), "La subida ha sido pausada.");

                                    }
                                });
            }catch (IOException e) {
                //mostrarDialogo(this,"ERROR", e.toString());
                Snackbar.make( mGraphicOverlay,"ERROR INESPERADO", Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", new View.OnClickListener()
                        { @Override public void onClick(View view) { } }) .show();
                settingToStart();
            }
            //}

        }else{
            //Si ya está vacío el arreglo, se enviado todas las fotos
            mostrarDialogo(this,"Operación Exitosa!",
                    ". ¿Desea repetir la Operación?");
        }


    }

    private void mostrarDialogo(final Activity activity, final String title,
              final String message) {

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title)
                .setMessage(message)
        .setPositiveButton("Repetir",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //listener.onPossitiveButtonClick();
                                settingToStart();
                            }
                        })
        .setNegativeButton("Salir",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //listener.onNegativeButtonClick();
                                Intent i = new Intent(getApplicationContext(), MainActivity.class);
                                startActivity(i);
                                finish();
                            }
                        });
        //}
        builder.create().show();
    }

    private void settingToStart(){
        typePhoto = "F";
        TYPE_PHOTO = 0;
        numPhotoUp = 0;
        miniaturaFotoP.setVisibility(View.INVISIBLE);
        miniaturaFotoF.setImageResource(R.drawable.cara_f);
        miniaturaFotoP.setImageResource(R.drawable.cara_p);
        diagramaCara.setImageResource(R.drawable.cara_f);
        listBitmapPhotos.clear();
    }

}


