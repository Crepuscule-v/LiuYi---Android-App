package com.bytedance.sjtu.liuyi.Activity

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.bytedance.sjtu.liuyi.DataClass.IdeaItem
import com.bytedance.sjtu.liuyi.DBHelper.IdeaItemDBHelper
import com.bytedance.sjtu.liuyi.R
import java.io.OutputStream
import java.util.*


class IdeaItemCreationActivity : AppCompatActivity() {
    private lateinit var ivIdeaItemCreation: ImageView
    private val dbHelper = IdeaItemDBHelper(this, IDEA_DB_NAME, 1)
    private var db : SQLiteDatabase? = null
    private val ideaItemCreationImageURIPathList : MutableList<String> = arrayListOf()
    private var ideaItemCreationImageURI : Uri? = null
    private lateinit var ideaCreateBar : androidx.appcompat.widget.Toolbar
    private val systemPhotoRequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (it.resultCode) {
            RESULT_CANCELED -> {
//                Log.d("IdeaItemCreationActivity", "System Photo Request Cancelled")
            }
            RESULT_OK -> {
                val imageURI = it.data?.data
                val bitmap = uriToBitmap(imageURI)
                val saveImageURI = saveImage(bitmap!!, this, "Liuyi")
                ideaItemCreationImageURI = saveImageURI
                setPreviewImageView(saveImageURI)
            }
        }
    }

    companion object {
        const val IdeaItemCreationSuccessCode : Int = 1001
        const val IdeaItemCreationFailCode : Int = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_idea_item_creation)
        bindView()
        bindDatabase()
        setViewOnClickListener()
    }

    private fun bindView() {
        ivIdeaItemCreation = findViewById(R.id.iv_idea_item_creation)
        ideaCreateBar = findViewById(R.id.idea_create_page_bar)
    }

    private fun bindDatabase() {
        db = dbHelper.writableDatabase
    }

    private fun setViewOnClickListener() {
        ivIdeaItemCreation.setOnClickListener {
            getAllPhoto()
        }

        ideaCreateBar.setTitle("新想法")
        ideaCreateBar.setNavigationIcon(R.drawable.rtn_left_icon_black)
        setSupportActionBar(ideaCreateBar)
        getSupportActionBar()?.setDisplayHomeAsUpEnabled(true);
        ideaCreateBar.setNavigationOnClickListener {
            Toast.makeText(this, "已舍弃", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.getItemId()) {
            R.id.save_idea -> {
                val ideaItemCreated = createIdeaItem()
                if (ideaItemCreated) {
                    setResult(IdeaItemCreationSuccessCode)
                    finish()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // 添加保存按钮
        ideaCreateBar.inflateMenu(R.menu.idea_edit_menu)
        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("LongLogTag")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun createIdeaItem() : Boolean {
        val ideaItem = packIdeaItemData()
        Log.d("IdeaItemCreationActivity", "dataItem : $ideaItem")
        if (checkValidIdeaItem(ideaItem)) {
            dbHelper.insertIdeaItem(db, ideaItem)
            return true
        }
        else {
            Toast.makeText(this, "记录一些想法吧", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    private fun getAllPhoto() {
        val intent = Intent()
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        intent.type = "image/*"
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        systemPhotoRequestLauncher.launch(intent)
    }

    private fun uriToBitmap(imageURI : Uri?): Bitmap? {
        val options = BitmapFactory.Options()
        options.inSampleSize = 2
        return BitmapFactory.decodeStream(contentResolver.openInputStream(imageURI!!), null, options)
    }

    private fun saveImage(bitmap: Bitmap, context: Context, folderName: String) : Uri? {
        val values = contentValues()
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName")
        values.put(MediaStore.Images.Media.IS_PENDING, true)
        // RELATIVE_PATH and IS_PENDING are introduced in API 29.
        val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            saveImageToStream(bitmap, context.contentResolver.openOutputStream(uri))
            values.put(MediaStore.Images.Media.IS_PENDING, false)
            context.contentResolver.update(uri, values, null, null)
        }
        return uri
    }

    private fun contentValues() : ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        return values
    }

    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setPreviewImageView(imageURI : Uri?) {
        ivIdeaItemCreation.setImageURI(imageURI)
        ivIdeaItemCreation.scaleType=ImageView.ScaleType.CENTER_CROP
        ivIdeaItemCreation.alpha=1.0f
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("SimpleDateFormat")
    private fun packIdeaItemData() : IdeaItem {
        val tv = findViewById<TextView>(R.id.et_idea_item_creation)
        val text = tv.text.toString()
        val tagFormatter = SimpleDateFormat(TAG_PATTERN)
        val formattedTag = tagFormatter.format(Date())
        val formattedDate = formattedTag.substring(0, 10)
        var imgURI = ""
        if (ideaItemCreationImageURI != null) {
            imgURI = ideaItemCreationImageURI.toString()
        }
        val videoURI = ""
        return IdeaItem(formattedDate, text, imgURI, videoURI, formattedTag)
    }

    private fun checkValidIdeaItem(ideaItem: IdeaItem) : Boolean {
        return !(ideaItem.text.isBlank() && ideaItem.img.isBlank() && ideaItem.video.isBlank())
    }
}