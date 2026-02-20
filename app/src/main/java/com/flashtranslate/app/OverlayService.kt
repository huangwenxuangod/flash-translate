package com.flashtranslate.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import android.content.Context
import android.util.Log
import android.widget.Toast

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // simple floating button layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        windowManager.addView(floatingView, params)

        setupTouchListener(params)
        setupSpeechRecognizer()
    }

    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        val closeButton = floatingView.findViewById<ImageView>(R.id.close_btn)
        val speakButton = floatingView.findViewById<View>(R.id.fab_container)

        closeButton.setOnClickListener {
            stopSelf()
        }

        speakButton.setOnClickListener {
            if (!isListening) {
                startListening()
            } else {
                stopListening()
            }
        }
        
        // Drag logic
        speakButton.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val Xdiff = (event.rawX - initialTouchX).toInt()
                        val Ydiff = (event.rawY - initialTouchY).toInt()
                        // If it was a tap (small movement), trigger click
                        if (Math.abs(Xdiff) < 10 && Math.abs(Ydiff) < 10) {
                            v.performClick()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                updateVisualState(false)
            }

            override fun onError(error: Int) {
                isListening = false
                updateVisualState(false)
                Log.e("OverlayService", "Speech Error: $error")
                Toast.makeText(applicationContext, "Speech Error: $error", Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    AccessibilityInputService.instance?.insertText(text)
                    Toast.makeText(applicationContext, "Input: $text", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        
        // Visual Feedback
        updateVisualState(true)
        
        speechRecognizer.startListening(intent)
        isListening = true
        Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()
    }

    private fun stopListening() {
        speechRecognizer.stopListening()
        isListening = false
        updateVisualState(false)
    }

    private fun updateVisualState(listening: Boolean) {
        val fabContainer = floatingView.findViewById<View>(R.id.fab_container)
        if (listening) {
            fabContainer.setBackgroundResource(R.drawable.bg_floating_listening)
        } else {
            fabContainer.setBackgroundResource(R.drawable.bg_floating_idle)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
    }
}