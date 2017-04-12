package com.example.davidprilutsky.apptwo;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String PACKAGE_NAME = "com.datumdroid.android.ocr.simple";
    public static final String EXTRA_MESSAGE = "com.example.myfirstapp.MESSAGE";

    private Button takePictureButton;
    private Uri file;
    private String recognizedText = "";
    final int REQUEST_TAKE_PHOTO = 1;
    final int CROP_PIC = 2;
    private static String mCurrentPhotoPath;
    private static File photoFile;
    private static File cropFile;
    private static Uri croppedUri;
    private Uri photoURI;

    public static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/SimpleAndroidOCR/";
    // You should have the trained data file in assets folder
    // You can get them at:
    // https://github.com/tesseract-ocr/tessdata
    public static final String lang = "eng";
    private static final String TAG = "SimpleAndroidOCR.java";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };

        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                } else {
                    Log.v(TAG, "Created directory " + path + " on sdcard");
                }
            }

        }

        // lang.traineddata file with the app (in assets folder)
        // You can get them at:
        // http://code.google.com/p/tesseract-ocr/downloads/list
        // This area needs work and optimization
        if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
            try {
                AssetManager assetManager = getAssets();
                InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
                //GZIPInputStream gin = new GZIPInputStream(in);
                OutputStream out = new FileOutputStream(DATA_PATH
                        + "tessdata/" + lang + ".traineddata");

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                //while ((lenf = gin.read(buff)) > 0) {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                //gin.close();
                out.close();

                Log.v(TAG, "Copied " + lang + " traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
            }
        }

        //THIS IS THE PICTURE TAKING CODE//
        takePictureButton = (Button) findViewById(R.id.button_image);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            takePictureButton.setEnabled(false);
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE }, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 0) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                takePictureButton.setEnabled(true);
            }
        }
    }

    public void takePicture(View view) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                return;
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(MainActivity.this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        photoFile);
                Log.v(TAG, "we did set photo uri");
                Log.v(TAG, photoURI.getPath());
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(TAG, "in result handler");
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            Log.v(TAG, "getting photo");
            Uri imageUri = Uri.parse(mCurrentPhotoPath);
            File mFile = new File(imageUri.getPath());

            try {
                InputStream ims = new FileInputStream(mFile);
                ImageView rawView = (ImageView) findViewById(R.id.imageview);
                rawView.setImageBitmap(BitmapFactory.decodeStream(ims));
            } catch (FileNotFoundException e) {
                return;
            }
            Log.v(TAG, "entering crop");
            performCrop(mFile);
        }
        // user is returning from cropping the image
        else if (requestCode == CROP_PIC) {
            // get the cropped bitmap
            System.out.println("processing crop result");
            Uri imageUri = Uri.parse(mCurrentPhotoPath);
            File mFile = new File(imageUri.getPath());

//            try {
//                InputStream ims = new FileInputStream(mFile);
//                ImageView croppedView = (ImageView) findViewById(R.id.cropview);
//                croppedView.setImageBitmap(BitmapFactory.decodeStream(ims));
//            } catch (FileNotFoundException e) {
//                System.out.println("File not found in crop post-processing");
//                return;
//            }
            ImageView croppedView = (ImageView) findViewById(R.id.cropview);
            croppedView.setImageURI(croppedUri);
            onPhotoTaken();
        }
    }

    private void performCrop(File imageFile) {
        Log.v(TAG, "at top of crop");

        cropFile = null;
        try {
            cropFile = createImageFile();
        } catch (IOException ex) {
            // Error occurred while creating the File
            return;
        }

        croppedUri = FileProvider.getUriForFile(MainActivity.this,
                BuildConfig.APPLICATION_ID + ".provider", cropFile);

        Log.v(TAG, photoURI.getPath());
        Log.v(TAG, mCurrentPhotoPath);
        Log.v(TAG, photoFile.getPath());
        //InputStream ims = new FileInputStream(mCurrentPhotoPath);
        ImageView croppedView = (ImageView) findViewById(R.id.cropview);
        croppedView.setImageURI(photoURI);

        try {
            Log.v(TAG, "in crop");

            try {
                Intent cropIntent = new Intent("com.android.camera.action.CROP");
                // indicate image type and Uri
                cropIntent.setDataAndType(photoURI, "image/*");
                cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                //cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, croppedUri);
                cropIntent.putExtra("crop", "true");
                cropIntent.putExtra("output", croppedUri);
                cropIntent.putExtra("outputFormat", "PNG");
                //cropIntent.putExtra("return-data", true);
                System.out.println("about to call crop");


                List<ResolveInfo> resInfoList = MainActivity.this.getPackageManager().queryIntentActivities(cropIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    MainActivity.this.grantUriPermission(packageName, croppedUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }


                cropIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                cropIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                startActivityForResult(cropIntent, CROP_PIC);
            } catch (ActivityNotFoundException anfe) {
                // display an error message
                System.out.println("no crop app");
                String errorMessage = "Whoops - your device doesn't support the crop action!";
                Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
                toast.show();
            }
        }
        // respond to users whose devices do not support the crop action
        catch (ActivityNotFoundException anfe) {
            Toast toast = Toast
                    .makeText(this, "This device doesn't support the crop action!", Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    private static File createImageFile() throws IOException{
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "Camera");
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = "file:" + image.getAbsolutePath();
        return image;
    }


    protected void onPhotoTaken() {

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;

        //Bitmap bitmap = BitmapFactory.decodeFile(cropFile.getPath(), options);
        Bitmap bitmap = BitmapFactory.decodeFile(cropFile.getPath(), options);
        if (bitmap == null) {
            Log.v(TAG, "BITMAP IS NULL!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! BAD BAD BAD");
            return;
        }
        try {
            //ExifInterface exif = new ExifInterface(cropFile.getPath());
            ExifInterface exif = new ExifInterface(cropFile.getPath());
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            Log.v(TAG, "Orient: " + exifOrientation);

            int rotate = 0;

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }

            Log.v(TAG, "Rotation: " + rotate);

            if (rotate != 0) {

                // Getting width & height of the given image.
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();

                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                // Rotating Bitmap
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
            }

            // Convert to ARGB_8888, required by tess
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        } catch (IOException e) {
            Log.e(TAG, "Couldn't correct orientation: " + e.toString());
        }

        //_image.setImageBitmap( bitmap );
        ImageView imageView = (ImageView) findViewById(R.id.imageview);
        imageView.setImageBitmap(bitmap);

        Log.v(TAG, "Before baseApi");

        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        if (!baseApi.setVariable("tessedit_write_images", "T"))
            Log.v(TAG, "COULDN'T SET SETTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!11");
        else
            Log.v(TAG, "I DID SET THE SETTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!11");
        baseApi.init(DATA_PATH, lang);
        baseApi.setImage(bitmap);
        recognizedText = baseApi.getUTF8Text();
        baseApi.end();
        Log.v(TAG, "\n\n\n\n\n\n\n\n AHHHHHHH!!!!!!!" + recognizedText + "\n\n\n\n\n OOOOOH");
    }

    /** Called when the user taps the Send button */
    public void sendMessage(View view) {
        Intent intent = new Intent(this, DisplayMessageActivity.class);
        //EditText editText = (EditText) findViewById(R.id.editText);
        //String message = editText.getText().toString();

        //Delete pics
        //new File(file.getPath()).delete();
        //new File(cropFile.getPath()).delete();
        //getContentResolver().delete(file, null, null);

        Log.v(TAG, "\n\n\n\n\n\n\n\n 2222222!!!!!!!" + recognizedText + "\n\n\n\n\n 33333333");
        intent.putExtra(EXTRA_MESSAGE, recognizedText);
        startActivity(intent);
    }
}
