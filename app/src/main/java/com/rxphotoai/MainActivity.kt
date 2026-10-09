package com.rxphotoai

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var enhanceButton: Button
    private lateinit var saveButton: Button
    private var original: Bitmap? = null
    private var enhanced: Bitmap? = null
    private var thumbnailMode = false
    private val pickerCode = 1201

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 16, 32)
        window.navigationBarColor = Color.rgb(12, 16, 32)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(12, 16, 32))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(TextView(this).apply {
            text = "RX PHOTO AI"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-black", 1)
            gravity = Gravity.CENTER
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "Make every photo look its best"
            textSize = 14f
            setTextColor(Color.rgb(174, 188, 218))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(18))
        }, matchWrap())

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val photoMode = makeButton("Photo Enhance")
        val thumbMode = makeButton("Thumbnail Enhance")
        photoMode.setOnClickListener {
            thumbnailMode = false
            photoMode.alpha = 1f
            thumbMode.alpha = 0.65f
            status.text = "Photo mode: natural clarity and colour"
        }
        thumbMode.setOnClickListener {
            thumbnailMode = true
            thumbMode.alpha = 1f
            photoMode.alpha = 0.65f
            status.text = "Thumbnail mode: stronger sharpening and contrast"
        }
        modeRow.addView(photoMode, LinearLayout.LayoutParams(0, dp(52), 1f))
        modeRow.addView(thumbMode, LinearLayout.LayoutParams(0, dp(52), 1f))
        content.addView(modeRow, matchWrap())

        preview = ImageView(this).apply {
            setBackgroundColor(Color.rgb(23, 30, 51))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Photo preview"
            setImageResource(android.R.drawable.ic_menu_gallery)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        content.addView(preview, LinearLayout.LayoutParams(-1, dp(320)).apply {
            setMargins(0, dp(16), 0, dp(12))
        })

        status = TextView(this).apply {
            text = "Choose a photo to get started"
            textSize = 14f
            setTextColor(Color.rgb(196, 207, 231))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
        content.addView(status, matchWrap())

        val pick = makeButton("1  ·  Choose Photo")
        pick.setOnClickListener { openPicker() }
        content.addView(pick, buttonParams())

        enhanceButton = makeButton("2  ·  Enhance Photo")
        enhanceButton.setOnClickListener { runEnhancement() }
        content.addView(enhanceButton, buttonParams())

        saveButton = makeButton("3  ·  Save to Gallery")
        saveButton.isEnabled = false
        saveButton.setOnClickListener { saveEnhanced() }
        content.addView(saveButton, buttonParams())

        content.addView(TextView(this).apply {
            text = "This starter version uses on-device image filters. AI-model restoration is a planned upgrade."
            textSize = 12f
            setTextColor(Color.rgb(139, 153, 185))
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(14), dp(6), dp(12))
        }, matchWrap())
        content.addView(TextView(this).apply {
            text = "Created by Ravi"
            textSize = 12f
            setTextColor(Color.rgb(111, 130, 173))
            gravity = Gravity.CENTER
        }, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(74, 91, 220))
    }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun buttonParams() = LinearLayout.LayoutParams(-1, dp(52)).apply {
        setMargins(0, dp(5), 0, dp(5))
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, pickerCode)
    }

    @Deprecated("Legacy callback kept for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickerCode || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val bitmap = contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                ?: throw IllegalArgumentException("Unsupported image")
            original = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            enhanced = null
            preview.setImageBitmap(original)
            status.text = "Photo loaded: " + bitmap.width + " × " + bitmap.height
            saveButton.isEnabled = false
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open this image. Try another photo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun runEnhancement() {
        val source = original
        if (source == null) {
            Toast.makeText(this, "Choose a photo first", Toast.LENGTH_SHORT).show()
            return
        }
        enhanceButton.isEnabled = false
        status.text = "Enhancing image…"
        Thread {
            try {
                val result = applyEnhancement(source, thumbnailMode)
                runOnUiThread {
                    enhanced = result
                    preview.setImageBitmap(result)
                    status.text = "Enhancement complete: " + result.width + " × " + result.height +
                        "\nLocal filter enhancement; not generative AI."
                    saveButton.isEnabled = true
                    enhanceButton.isEnabled = true
                }
            } catch (_: Exception) {
                runOnUiThread {
                    status.text = "Could not enhance this image. Try a smaller photo."
                    enhanceButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun applyEnhancement(input: Bitmap, thumbnail: Boolean): Bitmap {
        val maxSide = 2400
        val scale = min(1f, maxSide.toFloat() / max(input.width, input.height).toFloat())
        val w = max(1, (input.width * scale).toInt())
        val h = max(1, (input.height * scale).toInt())
        val scaled = if (w != input.width || h != input.height) Bitmap.createScaledBitmap(input, w, h, true) else input
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        val contrast = if (thumbnail) 1.16f else 1.07f
        val saturation = if (thumbnail) 1.20f else 1.06f
        val sharp = if (thumbnail) 0.72f else 0.38f
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = pixels[i]
            val l = pixels[y * w + max(0, x - 1)]
            val rgt = pixels[y * w + min(w - 1, x + 1)]
            val up = pixels[max(0, y - 1) * w + x]
            val dn = pixels[min(h - 1, y + 1) * w + x]
            var r = Color.red(c).toFloat()
            var g = Color.green(c).toFloat()
            var b = Color.blue(c).toFloat()
            val br = (Color.red(l) + Color.red(rgt) + Color.red(up) + Color.red(dn)) / 4f
            val bg = (Color.green(l) + Color.green(rgt) + Color.green(up) + Color.green(dn)) / 4f
            val bb = (Color.blue(l) + Color.blue(rgt) + Color.blue(up) + Color.blue(dn)) / 4f
            r = (r + (r - br) * sharp - 128f) * contrast + 128f
            g = (g + (g - bg) * sharp - 128f) * contrast + 128f
            b = (b + (b - bb) * sharp - 128f) * contrast + 128f
            val gray = 0.299f * r + 0.587f * g + 0.114f * b
            r = gray + (r - gray) * saturation
            g = gray + (g - gray) * saturation
            b = gray + (b - gray) * saturation
            out[i] = Color.argb(Color.alpha(c), r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }

    private fun saveEnhanced() {
        val bitmap = enhanced ?: run {
            Toast.makeText(this, "Enhance a photo first", Toast.LENGTH_SHORT).show()
            return
        }
        var stream: OutputStream? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "RX_Photo_AI_" + System.currentTimeMillis() + ".jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RX Photo AI")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create gallery item")
            stream = contentResolver.openOutputStream(uri)
            if (stream == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                throw IllegalStateException("Could not write image")
            }
            stream.flush()
            Toast.makeText(this, "Saved to Pictures / RX Photo AI", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not save image. Please try again.", Toast.LENGTH_LONG).show()
        } finally {
            try { stream?.close() } catch (_: Exception) {}
        }
    }
}
