package edu.oaklandcc.young.kyle1.mymemory

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import edu.oaklandcc.young.kyle1.mymemory.models.BoardSize
import edu.oaklandcc.young.kyle1.mymemory.utils.*
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CreateActivity"
        private const val PICK_PHOTO_CODE = 655
        private const val READ_EXTERNAL_PHOTOS_CODE = 248
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_MEDIA_IMAGES
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14
    }

    private lateinit var adapter: ImagePickerAdapter
    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var pbUploading: ProgressBar


    private lateinit var boardSize: BoardSize
    private var numImagedRequired = -1
    private val chosenImageUri = mutableListOf<Uri>()
    private val storage = Firebase.storage
    private val db = Firebase.firestore


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)


        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE) as BoardSize
        numImagedRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose pics (0 / $numImagedRequired)"

        btnSave.setOnClickListener {
            savedDataToFirebase()
        }

        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameName.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(p0: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }


        })

        adapter = ImagePickerAdapter(
            this,
            chosenImageUri,
            boardSize,
            object : ImagePickerAdapter.ImageClickListener {
                override fun onPlaceHolderClicked() {
                    if (isPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION)) {
                        launchIntentForPhotos()
                    } else {
                        requestPermission(
                            this@CreateActivity,
                            READ_PHOTOS_PERMISSION,
                            READ_EXTERNAL_PHOTOS_CODE
                        )
                    }

                }

            })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())


    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == READ_EXTERNAL_PHOTOS_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchIntentForPhotos()

            } else {
                Toast.makeText(
                    this,
                    "To create a Custom Game, you need to provide access to your photos",
                    Toast.LENGTH_LONG
                ).show()
            }

        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PHOTO_CODE || resultCode != Activity.RESULT_OK || data == null) {
            Log.w(TAG, "Did not get data back from the launched activity, user likely canceled. ")
            return
        }
        val selectedUri = data.data
        val clipData = data.clipData
        if (clipData != null) {
            Log.i(TAG, "clipData numImages ${clipData.itemCount}: $clipData")
            for (i in 0 until clipData.itemCount) {
                val clipItem = clipData.getItemAt(i)
                if (chosenImageUri.size < numImagedRequired) {
                    chosenImageUri.add(clipItem.uri)
                }
            }
        } else if (selectedUri != null) {
            Log.i(TAG, "data: $selectedUri")
            chosenImageUri.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose pics(${chosenImageUri.size} / $numImagedRequired)"
        btnSave.isEnabled = shouldEnableSaveButton()

    }

    private fun savedDataToFirebase() {
        val customGameName = etGameName.text.toString()
        Log.i(TAG, "saveDataToFirebase")
        btnSave.isEnabled = false
        //Check we are not overwriting someone else data
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            if(document!= null && document.data != null){
                AlertDialog.Builder(this)
                    .setTitle("Name Taken")
                    .setMessage("A game already exists with the name '$customGameName'. Please choose a new name.")
                    .show()
                    btnSave.isEnabled = true
            } else{
                handleImageUploading(customGameName)
            }
        }.addOnFailureListener {exception ->
            Log.e(TAG, "Error occurred while saving game", exception)
            Toast.makeText(this, "Error occurred while saving game", Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
        }

    }

    private fun handleImageUploading(gameName: String) {
        pbUploading.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()
        for ((index, photoUri) in chosenImageUri.withIndex()) {
            //underlying data for the image
            val imageByteArray = getImageByteArray(photoUri)
            //Filepath to upload data
            val filePath = "images/$gameName/${System.currentTimeMillis()}-${index}.jpg"
            val photoReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                .continueWithTask { photoUploadTask ->
                    Log.i(TAG, "Upload bytes: ${photoUploadTask.result?.bytesTransferred}")
                    photoReference.downloadUrl

                }.addOnCompleteListener { downloadUrlTask ->
                    if (!downloadUrlTask.isSuccessful) {
                        Log.e(
                            "Exception with Firebase storage",
                            downloadUrlTask.exception.toString()
                        )
                        Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                        didEncounterError = true
                        return@addOnCompleteListener
                    }
                    if (didEncounterError){
                        pbUploading.visibility = View.GONE
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downloadUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    pbUploading.progress = uploadedImageUrls.size * 100 / chosenImageUri.size
                    Log.i(TAG, "Finished uploading $photoUri, num uploaded ${uploadedImageUrls.size}")
                    if (uploadedImageUrls.size == chosenImageUri.size){
                        handleAllImagesUploaded(gameName, uploadedImageUrls )
                    }

                }
        }

    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
        db.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener { gameCreationTask ->
                pbUploading.visibility = View.GONE
                if (!gameCreationTask.isSuccessful) {
                    Log.e(TAG, "Exception with game creation", gameCreationTask.exception)
                    Toast.makeText(this, "Failed game creation", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                Log.i(TAG, "Successfully created game $gameName")
                AlertDialog.Builder(this)
                    .setTitle("Upload complete! Play your game '$gameName'")
                    .setPositiveButton("OK") {_, _ ->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()

                    }.show()
            }

    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitMap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        Log.i(TAG, "Original width ${originalBitMap.width} and height ${originalBitMap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitMap, 250)
        Log.i(TAG, "Scaled width ${scaledBitmap.width} and height ${scaledBitmap.height}")
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    private fun shouldEnableSaveButton(): Boolean {
        if (chosenImageUri.size != numImagedRequired) {
            return false
        }
        if (etGameName.text.isBlank() || etGameName.text.length < MIN_GAME_NAME_LENGTH) {
            return false
        }
        return true

    }

    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Choose pics"), PICK_PHOTO_CODE)
    }
}