package com.lgcns.codeengine.ui.fragment

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.canhub.cropper.CropImage
import com.canhub.cropper.CropImageView
import com.canhub.cropper.CropImageView.CropResult
import com.canhub.cropper.CropImageView.OnCropImageCompleteListener
import com.canhub.cropper.CropImageView.OnSetImageUriCompleteListener
import com.canhub.cropper.parcelable
import com.lgcns.codeengine.R
import com.lgcns.codeengine.activity.ImageEditActivity.Companion.DEFAULT_MAX_SIZE
import com.lgcns.codeengine.activity.ImageEditActivity.Companion.RESULT_URI
import com.lgcns.codeengine.databinding.FragmentImageEditBinding
import com.lgcns.codeengine.util.extension.compressJpeg
import com.lgcns.codeengine.util.extension.reduceCompressJpeg
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File


internal class ImageEditFragment : Fragment(), OnSetImageUriCompleteListener, OnCropImageCompleteListener {
    private var _binding: FragmentImageEditBinding? = null
    private val binding get() = _binding!!

    private var selectedUri: Uri? = null

    private var maxSize: Int = DEFAULT_MAX_SIZE

    companion object {
        const val SELECTED_URI = "SELECTED_URI"
        const val MAX_SIZE = "MAX_SIZE"
        const val REDUCED_IMAGE = "reduced_image"

        @JvmStatic
        fun newInstance(uri: Uri) = ImageEditFragment().apply {
            arguments = Bundle().apply {
                putParcelable(SELECTED_URI, uri)
                putInt(MAX_SIZE, maxSize)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        setHasOptionsMenu(true)
        _binding = FragmentImageEditBinding.inflate(layoutInflater, container, false)

        binding.tvCancel.setOnClickListener {
            activity?.finish()
        }
        binding.tvConfrim.setOnClickListener {
            binding.cropImageView.croppedImageAsync()
        }
        binding.ivRotate.setOnClickListener {
            binding.cropImageView.rotateImage(90)
        }
        binding.ivReverse.setOnClickListener {
            binding.cropImageView.flipImageHorizontally()
        }

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.parcelable<Uri>(SELECTED_URI)?.let {
            selectedUri = it
        }

        arguments?.getInt(MAX_SIZE)?.let {
            maxSize = it
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.cropImageView.setOnSetImageUriCompleteListener(null)
        binding.cropImageView.setOnCropImageCompleteListener(null)
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cropImageView.cropRect = Rect(100, 300, 500, 1200)

        binding.cropImageView.setOnSetImageUriCompleteListener(this)
        binding.cropImageView.setOnCropImageCompleteListener(this)

        binding.cropImageView.setImageUriAsync(selectedUri)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.image_edit_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.main_action_crop -> {
            binding.cropImageView.croppedImageAsync()
            true
        }
        R.id.main_action_rotate -> {
            binding.cropImageView.rotateImage(90)
            true
        }
        R.id.main_action_flip_horizontally -> {
            binding.cropImageView.flipImageHorizontally()
            true
        }
        R.id.main_action_flip_vertically -> {
            binding.cropImageView.flipImageVertically()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onSetImageUriComplete(view: CropImageView, uri: Uri, error: Exception?) {
        if (error != null) {
            Timber.e(error, "Failed to load image by URI")
            Toast.makeText(activity, "Image load failed: " + error.message, Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCropImageComplete(view: CropImageView, result: CropResult) {
        if (result.error != null) {
            Timber.e(result.error, "Failed to crop image")
            return
        }

        val imageBitmap = if (binding.cropImageView.cropShape == CropImageView.CropShape.OVAL) {
            result.bitmap?.let(CropImage::toOvalBitmap)
        } else {
            result.bitmap
        }
        Timber.d("Original bitmap: ${result.originalBitmap}")
        Timber.d("Original uri: ${result.originalUri}")
        Timber.d("Output bitmap: $imageBitmap")
        Timber.d("Output uri: ${result.getUriFilePath(view.context)}")

        val reducedBitmap = imageBitmap?.reduceCompressJpeg(maxSize)

        val tempFile = File.createTempFile("temp", ".jpg")
        val uri = Uri.fromFile(tempFile)
        Timber.d("uri for reduced file: $uri")

        val inputStream = ByteArrayInputStream(reducedBitmap?.compressJpeg())
        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Timber.d("reduced size:${tempFile.length()}")

        val intent = Intent()
        intent.putExtra(RESULT_URI, uri)
        activity?.setResult(RESULT_OK, intent)
        activity?.finish()
    }

}
