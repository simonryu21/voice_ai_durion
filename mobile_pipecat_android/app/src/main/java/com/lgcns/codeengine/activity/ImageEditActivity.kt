package com.lgcns.codeengine.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import com.lgcns.codeengine.R
import com.lgcns.codeengine.databinding.ActivityImageEditBinding
import com.lgcns.codeengine.ui.fragment.ImageEditFragment
import com.lgcns.codeengine.util.extension.format
import com.lgcns.codeengine.web.plugin.ImageEditPlugin.Companion.ACTION_EDIT_FROM_CAMERA
import com.lgcns.codeengine.web.plugin.ImageEditPlugin.Companion.ACTION_EDIT_FROM_GALLERY
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.File
import java.util.*

@AndroidEntryPoint
class ImageEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImageEditBinding

    private var latestTmpUri: Uri? = null

    private var maxSize: Int = DEFAULT_MAX_SIZE

    private val pictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) {
        Timber.d("result for takePicture: it=$it")
        if (it) {
            onPickImageResult(latestTmpUri)
        } else {
            onPickImageResult(null)
        }
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri ->
                    ImageEditFragment.newInstance(uri).show()
            }
            } else if (it.resultCode == Activity.RESULT_CANCELED) {
                Timber.d("User cancelled selection")
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_image_edit)

        val currentDate = now().format("yyyyMMdd_hhmmss")
        val fileName = "img_photo_$currentDate"
        val destDir = cacheDir.absolutePath + "/picture"
        val file = File(destDir, fileName)
        file.parentFile?.mkdirs()

        val actionType = intent.getStringExtra(ACTION_TYPE) ?: ""
        maxSize = intent.getIntExtra(MAX_SIZE, DEFAULT_MAX_SIZE)

        when (actionType) {
            ACTION_EDIT_FROM_CAMERA -> {
                val photoURI: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                latestTmpUri = photoURI
                Timber.d("pictureLauncher.launch(): latestTmpUri=$latestTmpUri")
                pictureLauncher.launch(photoURI)
            }
            ACTION_EDIT_FROM_GALLERY -> {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                Timber.d("start GALLERY_REQUEST_CODE")
                galleryLauncher.launch(intent)
            }
            else -> {
                Timber.e("Unsupported action type: $actionType")
            }
        }
    }

    private fun onPickImageResult(resultUri: Uri?) {
        when (resultUri) {
            null -> setResultCancel()
            else -> {
                Timber.d("onPickImageResult(): resultUri=$resultUri")
                ImageEditFragment.newInstance(resultUri).show()
            }
        }
    }

    /**
     * Cancel of this activity.
     */
    private fun setResultCancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun Fragment.show() {
        supportFragmentManager
            .beginTransaction()
            .replace(binding.imgContainer.id, this)
            .commit()
    }

    private fun now(): Date = Calendar.getInstance().time

    companion object {
        const val ACTION_TYPE = "extra.ACTION_TYPE"
        const val MAX_SIZE = "extra.MAX_SIZE"
        const val RESULT_URI = "RESULT_BYTE_ARRAY"

        const val DEFAULT_MAX_SIZE = (1.5 * 1024 * 1024).toInt()
    }

}
