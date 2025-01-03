package com.example.kidsdrawingapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.example.kidsdrawingapp.databinding.DialogBrushSizeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private var drawingView:DrawingView? = null
    private var mImageButtonCurrentPaint : ImageButton? = null // A variable for current color is picked from color pallet.
    //Todo 1: create a variable for the dialog
    var customProgressDialog: Dialog? = null


    val openGallaryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result->
            if(result.resultCode == RESULT_OK && result.data!= null){
                val imageBackground : ImageView = findViewById(R.id.iv_background)
                imageBackground.setImageURI(result.data?.data)
            }
        }

    val requestPermission:ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value
                //if permission is granted show a toast and perform operation
                if(isGranted){
                    val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGallaryLauncher.launch(intent)
                }else{
                    //Displaying another toast if permission is not granted and this time focus on
                    //    Read external storage
                    if(permissionName == Manifest.permission.READ_MEDIA_IMAGES){
                        Toast.makeText(this@MainActivity,
                            "oops you just denied permission",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            }
        }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())
        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )

        val ib_brush:ImageButton = findViewById(R.id.ib_brush)
        ib_brush.setOnClickListener{
            showBrushSizeChooserDialog()
        }

        val ibUndo:ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener{
            // This is for undo recent stroke.
            drawingView?.onClickUndo()
        }

        val ibRedo:ImageButton = findViewById(R.id.ib_redo)
        ibRedo.setOnClickListener{
                drawingView?.onClickRedo()
        }
        val ibSave:ImageButton = findViewById(R.id.ib_save)
        //set onclick listener
        // TODO(Step 3 : Adding an click event to save or exporting the image to your phone storage.)
        ibSave.setOnClickListener{
            //check if permission is allowed
            if(isReadStorageAllowed()){
                //Todo 4: show dialog before launching coroutine
                showProgressDialog()
                //launch a coroutine block
                lifecycleScope.launch {
                    //reference the frame layout
                    val flDrawingView : FrameLayout = findViewById(R.id.fl_drawing_view_container)
                    //Save the image to the device
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }else {
                Toast.makeText(this@MainActivity, "Storage permission not granted", Toast.LENGTH_SHORT).show()
                requestStoragePermission()
            }
        }

        val ibGallary : ImageButton = findViewById(R.id.ib_gallery)
        ibGallary.setOnClickListener{
                requestStoragePermission()
        }


    }
    /**
     * Method is used to launch the dialog to select different brush sizes.
     */
    private fun showBrushSizeChooserDialog(){
        val brushDialog = Dialog(this@MainActivity)
        val binding:DialogBrushSizeBinding = DialogBrushSizeBinding.inflate(layoutInflater)
        brushDialog.setContentView(binding.root)
        binding.ibSmallBrush.setOnClickListener {
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        binding.ibMediumBrush.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        binding.ibLargeBrush.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }
    /**
     * Method is called when color is clicked from pallet_normal.
     *
     * @param view ImageButton on which click took place.
     */

    fun paintClicked(view: View){
        if(view !== mImageButtonCurrentPaint){
            // Update the color
            val imageButton = view as ImageButton
            // Here the tag is used for swaping the current color with previous color.
            // The tag stores the selected view
            val colorTag = imageButton.tag.toString()
            // The color is set as per the selected tag here
            drawingView?.setColor(colorTag)
            // Swap the backgrounds for last active and currently active image button.
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )
            //Current view is updated with selected view in the form of ImageButton.
            mImageButtonCurrentPaint = view
        }
    }
    /**
     * We are calling this method to check the permission status
     */
    private fun isReadStorageAllowed(): Boolean {
        //Getting the permission status
        // Here the checkSelfPermission is
        /**
         * Determine whether <em>you</em> have been granted a particular permission.
         *
         * @param permission The name of the permission being checked.
         *
         */
        val result = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_IMAGES
        )

        /**
         *
         * @return {@link android.content.pm.PackageManager#PERMISSION_GRANTED} if you have the
         * permission, or {@link android.content.pm.PackageManager#PERMISSION_DENIED} if not.
         *
         */
        //If permission is granted returning true and If permission is not granted returning false
        return result == PackageManager.PERMISSION_GRANTED
    }

    //create a method to requestStorage permission
    @SuppressLint("InlinedApi")
    private fun requestStoragePermission(){
        // Check if the permission was denied and show rationale
         if(ActivityCompat.shouldShowRequestPermissionRationale(
             this,
                Manifest.permission.READ_MEDIA_IMAGES)
             ){
             //call the rationale dialog to tell the user why they need to allow permission request
                showRationaleDialog("Kids drawing App","Kids Drawing App" +
                        "needs to access external storage")
         }else{
             // You can directly ask for the permission.
             //if it has not been denied then request for permission
             //  The registered ActivityResultCallback gets the result of this request.
             requestPermission.launch(arrayOf(
                 Manifest.permission.READ_MEDIA_IMAGES,
                 Manifest.permission.WRITE_EXTERNAL_STORAGE
             ))
         }
    }
    /**  create rationale dialog
     * Shows rationale dialog for displaying why the app needs permission
     * Only shown if the user has denied the permission request previously
     */
    private fun showRationaleDialog(
        title: String,
        message: String,
    ) {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }
    // TODO(Step 4 : Getting and bitmap Exporting the image to your phone storage.)
    /**
     * Create bitmap from view and returns it
     */

    private fun getBitmapFromView(view: View) : Bitmap {
        //Define a bitmap with the same size as the view.
        // CreateBitmap : Returns a mutable bitmap with the specified width and height
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        //Bind a canvas to it
        val canvas  = Canvas(returnedBitmap)
        //Get the view's background
        val bgDrawable = view.background
        if(bgDrawable != null){
            //has background drawable, then draw it on the canvas
            bgDrawable.draw(canvas)
        }else{
            //does not have background drawable, then draw white background on the canvas
            canvas.drawColor(Color.WHITE)
        }
        // draw the view on the canvas
        view.draw(canvas)
        //return the bitmap
        return  returnedBitmap
    }
    private suspend fun saveBitmapFile(mBitmap: Bitmap) : String{
        var result = ""
        withContext(Dispatchers.IO) {
            if (mBitmap != null) {
                try {
                    // Creates a new byte array output stream.
                    // The buffer capacity is initially 32 bytes, though its size increases if necessary.
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    // Here the Environment : Provides access to environment variables.
                    // getExternalStorageDirectory : returns the primary shared/external storage directory.
                    // absoluteFile : Returns the absolute form of this abstract pathname.
                    // File.separator : The system-dependent default name-separator character. This string contains a single character.

                    val f = File(
                        externalCacheDir?.absoluteFile.toString()
                                + File.separator + "KidDrawingApp_" + System.currentTimeMillis() / 1000 + ".png"
                    )
                    // Creates a file output stream to write to the file represented by the specified object.
                    val fo = FileOutputStream(f)
                    // Writes bytes from the specified byte array to this file output stream.
                    fo.write(bytes.toByteArray())
                    // Closes this file output stream and releases any system resources associated with this stream. This file output stream may no longer be used for writing bytes.
                    fo.close()
                    result = f.absolutePath // The file absolute path is return as a result.
                    //We switch from io to ui thread to show a toast
                    runOnUiThread {
                        cancelProgressDialog()
                        if (result.isNotEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully :$result",
                                Toast.LENGTH_SHORT
                            ).show()
                            shareImage(result)
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    /** Todo 2:create function to show the dialog
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        customProgressDialog?.show()
    }

    /** Todo 3: create function to cancel dialog
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }
    private fun shareImage(filePath: String) {
        //This line creates a File object using the given filePath.
        val file = File(filePath)
        //This method generates a content URI for the file, which is a secure way to share a file with other apps.
        /*
        this@MainActivity: A reference to the current MainActivity context.
        "${applicationContext.packageName}.fileprovider": This is the authority string for the FileProvider, which is a unique identifier defined in your AndroidManifest.xml. The placeholder ${applicationContext.packageName} is replaced by your app’s package name.
        file: The File object created earlier.
        Why Use FileProvider: Direct file paths are not shared between apps due to security reasons. FileProvider provides a secure mechanism to share files using content URIs.
         */
        val uri = FileProvider.getUriForFile(
            this@MainActivity,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        /**
         * Intent(): This creates a new Intent object, which is used to perform an action (in this case, sending a file).
         * action = Intent.ACTION_SEND: Sets the action of the intent to ACTION_SEND, which indicates that you're sending data to another app.
         * putExtra(Intent.EXTRA_STREAM, uri): Adds extra data to the intent, specifically the URI of the image file that you want to share. Intent.EXTRA_STREAM is the key used to store the URI.
         * type = "image/png": Specifies the MIME type of the data you're sharing. "image/png" indicates that the data is a PNG image.
         * addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION): This flag grants temporary read access to the content URI. Without this, the receiving app may not have permission to access the file.
         */

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Image"))
    }

}