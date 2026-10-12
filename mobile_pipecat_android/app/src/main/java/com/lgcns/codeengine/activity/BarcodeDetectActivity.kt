package com.lgcns.codeengine.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lgcns.codeengine.R
import com.lgcns.codeengine.util.barcode.scan.VisionProcessorBase
import com.google.android.gms.common.annotation.KeepName
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber
import java.io.IOException

@KeepName
class BarcodeDetectActivity : AppCompatActivity() {

    private var cameraSource: com.lgcns.codeengine.util.barcode.scan.CameraSource? = null
    private var preview: com.lgcns.codeengine.util.barcode.scan.CameraSourcePreview? = null
    private var graphicOverlay: com.lgcns.codeengine.util.barcode.scan.GraphicOverlay? = null

    var requestId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("onCreate")
        setContentView(R.layout.activity_barcode_detect)

        intent?.getStringExtra(com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.REQUEST_ID)?.let { requestId = it }

        preview = findViewById(R.id.preview_view)
        graphicOverlay = findViewById(R.id.graphic_overlay)

        createCameraSource()

        startCameraSource()
    }

    private fun createCameraSource() {
        // If there's no existing cameraSource, create one.
        if (cameraSource == null) {
            cameraSource =
                com.lgcns.codeengine.util.barcode.scan.CameraSource(
                    this,
                    graphicOverlay
                )
        }
        try {
            Timber.i("Using Barcode Detector Processor")
            cameraSource!!.setMachineLearningFrameProcessor(
                com.lgcns.codeengine.activity.BarcodeDetectActivity.BarcodeScannerProcessor(
                    this
                )
            )
        } catch (e: Exception) {
            Timber.e("Can not create image processor: ", e)
        }
    }

    /**
     * Starts or restarts the camera source, if it exists. If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private fun startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Timber.d("resume: Preview is null")
                }
                if (graphicOverlay == null) {
                    Timber.d("resume: graphOverlay is null")
                }
                preview!!.start(cameraSource, graphicOverlay)
            } catch (e: IOException) {
                Timber.e("Unable to start camera source.", e)
                cameraSource!!.release()
                cameraSource = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        createCameraSource()
        startCameraSource()
    }

    /** Stops the camera. */
    override fun onPause() {
        preview?.stop()
        super.onPause()
    }

    public override fun onDestroy() {
        if (cameraSource != null) {
            cameraSource?.release()
        }
        super.onDestroy()
    }

    class BarcodeScannerProcessor(context: Context) : VisionProcessorBase<List<Barcode>>(context) {
        private val thisActivity = context as com.lgcns.codeengine.activity.BarcodeDetectActivity

        private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()

        override fun stop() {
            super.stop()
            barcodeScanner.close()
        }

        override fun detectInImage(image: InputImage): Task<List<Barcode>> {
            return barcodeScanner.process(image)
        }

        override fun onSuccess(results: List<Barcode>, graphicOverlay: com.lgcns.codeengine.util.barcode.scan.GraphicOverlay) {
            if (results.isEmpty()) {
                //Timber.v("No barcode has been detected")
                return
            }
            val barcode = results[0]

            Timber.d("onSuccess() requestId=${thisActivity.requestId}, barcode.rawValue=${barcode.rawValue}")
            val intent = Intent()
                .putExtra(com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.BARCODE_VALUE, barcode.rawValue)
                .putExtra(com.lgcns.codeengine.activity.BaseWebViewActivity.Companion.REQUEST_ID, thisActivity.requestId)

            thisActivity.setResult(RESULT_OK, intent)
            thisActivity.finish()
        }

        override fun onFailure(e: Exception) {
            Timber.e("Barcode detection failed $e")
        }
    }

}
