package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

import java.util.*

class CookingModeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // Data
    private lateinit var instructions: ArrayList<String>
    private lateinit var times: ArrayList<String>
    private var currentStep = 0
    private val RECORD_AUDIO_PERMISSION_CODE = 101

    // Views
    private lateinit var stepText: TextView
    private lateinit var stepTime: TextView
    private lateinit var timerText: TextView
    private lateinit var timerStatus: TextView
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var nextButton: LinearLayout
    private lateinit var prevButton: LinearLayout
    private lateinit var pauseButton: LinearLayout
    private lateinit var resetButton: LinearLayout
    private lateinit var voiceButton: MaterialButton
    private lateinit var exitButton: MaterialButton
    private lateinit var pauseIcon: ImageView
    private lateinit var cardTimer: CardView
    private lateinit var voiceIndicatorCard: CardView
    private lateinit var voiceIndicatorText: TextView

    // Timer
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0L
    private var isTimerRunning = false
    private var isTimerPaused = false

    // Voice
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val SPEECH_REQUEST_CODE = 100
    private var isVoiceEnabled = false
    private var isListening = false
    private var audioPermissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_cooking_mode)
        checkAudioPermission()
        // Keep screen awake during cooking
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        findViewById<View>(R.id.header_section).applyHeaderInsets()
        findViewById<View>(R.id.bottom_bar).applyBottomNavInsets()

        initializeViews()
        loadData()
        setupButtons()
        initializeTextToSpeech()

        showStep()
    }

    private fun initializeViews() {
        stepText = findViewById(R.id.text_step)
        stepTime = findViewById(R.id.text_step_time)
        timerText = findViewById(R.id.text_timer)
        timerStatus = findViewById(R.id.text_timer_status)
        progressText = findViewById(R.id.text_progress)
        progressBar = findViewById(R.id.progress_bar)
        nextButton = findViewById(R.id.button_next_step)
        prevButton = findViewById(R.id.button_prev_step)
        pauseButton = findViewById(R.id.button_pause_timer)
        resetButton = findViewById(R.id.button_reset_timer)
        voiceButton = findViewById(R.id.button_voice)
        exitButton = findViewById(R.id.button_exit)
        pauseIcon = findViewById(R.id.icon_pause_timer)
        cardTimer = findViewById(R.id.card_timer)
        voiceIndicatorCard = findViewById(R.id.card_voice_indicator)
        voiceIndicatorText = findViewById(R.id.text_voice_indicator)
    }

    private fun loadData() {
        instructions = intent.getStringArrayListExtra("instructions") ?: arrayListOf()
        times = intent.getStringArrayListExtra("times") ?: arrayListOf()

        if (instructions.isEmpty()) {
            Toast.makeText(this, "No instructions available", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupButtons() {
        nextButton.setOnClickListener {
            if (currentStep < instructions.size - 1) {
                currentStep++
                showStep()
                speakInstruction()
            } else {
                showCompletionDialog()
            }
        }

        prevButton.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                showStep()
                speakInstruction()
            }
        }

        pauseButton.setOnClickListener {
            toggleTimer()
        }

        resetButton.setOnClickListener {
            resetTimer()
        }

        voiceButton.setOnClickListener {
            if (!audioPermissionGranted) {
                Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            toggleVoiceMode()
        }

        exitButton.setOnClickListener {
            showExitConfirmation()
        }
    }

    private fun toggleVoiceMode() {
        if (isVoiceEnabled) {
            // Disable voice mode
            isVoiceEnabled = false
            voiceButton.alpha = 0.5f
            voiceIndicatorCard.visibility = View.GONE
            speak("Voice commands disabled")
            Toast.makeText(this, "Voice commands disabled", Toast.LENGTH_SHORT).show()
        } else {
            // Enable voice mode
            isVoiceEnabled = true
            voiceButton.alpha = 1.0f
            speak("Voice commands enabled. Say next, previous, repeat, or pause")
            startListening()
        }
    }

    private fun startListening() {
        if (!isVoiceEnabled || isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'next', 'previous', 'repeat', or 'pause'")
        }

        try {
            isListening = true
            voiceIndicatorCard.visibility = View.VISIBLE
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice recognition not available", Toast.LENGTH_SHORT).show()
            voiceIndicatorCard.visibility = View.GONE
            isListening = false
        }
    }

    private fun showStep() {
        // Cancel previous timer
        countDownTimer?.cancel()
        isTimerRunning = false
        isTimerPaused = false
        updatePauseIcon()

        // Update step text
        stepText.text = instructions[currentStep]

        // Update estimated time
        val stepTimeText = times.getOrNull(currentStep) ?: "N/A"
        stepTime.text = "Estimated: $stepTimeText"

        // Update progress
        val progress = ((currentStep + 1).toFloat() / instructions.size.toFloat() * 100).toInt()
        progressBar.progress = progress
        progressText.text = "Step ${currentStep + 1} of ${instructions.size}"

        // Update button states
        prevButton.alpha = if (currentStep > 0) 1.0f else 0.5f
        prevButton.isEnabled = currentStep > 0

        val nextButtonText = if (currentStep == instructions.size - 1) "Finish" else "Next"
        (nextButton.getChildAt(0) as TextView).text = nextButtonText

        // Setup timer
        setupStepTimer(stepTimeText)
    }

    private fun setupStepTimer(stepTimeText: String) {
        // Parse time from string (e.g., "5 min" or "N/A")
        val timeComponents = stepTimeText.split(" ")
        val minutes = timeComponents.getOrNull(0)?.toIntOrNull() ?: 0

        if (minutes > 0) {
            timeLeftInMillis = (minutes * 60 * 1000).toLong()
            cardTimer.visibility = View.VISIBLE
            timerStatus.text = "Ready to start"
            updateTimerDisplay()
            startTimer()
        } else {
            cardTimer.visibility = View.GONE
        }
    }

    private fun startTimer() {
        if (timeLeftInMillis > 0 && !isTimerRunning) {
            countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMillis = millisUntilFinished
                    updateTimerDisplay()
                }

                override fun onFinish() {
                    onTimerComplete()
                }
            }.start()

            isTimerRunning = true
            isTimerPaused = false
            timerStatus.text = "Counting down..."
            updatePauseIcon()
        }
    }

    private fun toggleTimer() {
        if (isTimerRunning && !isTimerPaused) {
            // Pause timer
            countDownTimer?.cancel()
            isTimerRunning = false
            isTimerPaused = true
            timerStatus.text = "Paused"
            updatePauseIcon()
        } else if (isTimerPaused) {
            // Resume timer
            startTimer()
            timerStatus.text = "Counting down..."
        }
    }

    private fun resetTimer() {
        countDownTimer?.cancel()

        val stepTimeText = times.getOrNull(currentStep) ?: "N/A"
        val timeComponents = stepTimeText.split(" ")
        val minutes = timeComponents.getOrNull(0)?.toIntOrNull() ?: 0

        timeLeftInMillis = (minutes * 60 * 1000).toLong()
        updateTimerDisplay()

        isTimerRunning = false
        isTimerPaused = false
        timerStatus.text = "Reset - Ready to start"
        updatePauseIcon()

        // Auto-start after reset
        startTimer()
    }

    private fun updateTimerDisplay() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        timerText.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun updatePauseIcon() {
        if (isTimerRunning && !isTimerPaused) {
            pauseIcon.setImageResource(R.drawable.ic_pause)
        } else {
            pauseIcon.setImageResource(R.drawable.ic_play)
        }
    }

    private fun onTimerComplete() {
        isTimerRunning = false
        timerText.text = "00:00"
        timerStatus.text = "✔ Step Complete!"
        timerText.setTextColor(getColor(R.color.green_primary))

        // Vibrate or play sound (optional)
        speak("Timer complete. Step finished.")
    }

    // ========== TEXT-TO-SPEECH ==========

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED

            if (isTtsReady) {
                // Speak first instruction
                speakInstruction()
            }
        }
    }

    private fun speakInstruction() {
        if (isTtsReady) {
            val text = "Step ${currentStep + 1}. ${instructions[currentStep]}"
            speak(text)
        }
    }

    private fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // ========== VOICE RECOGNITION ==========

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE) {
            voiceIndicatorCard.visibility = View.GONE

            if (resultCode == RESULT_OK) {
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spokenText = results?.get(0)?.lowercase(Locale.getDefault()) ?: ""

                handleVoiceCommand(spokenText)
            }

            isListening = false

            // Continue listening if voice mode is still enabled
            if (isVoiceEnabled) {
                startListening()
            }
        }
    }

    private fun handleVoiceCommand(command: String) {
        when {
            command.contains("next") || command.contains("continue") -> {
                speak("Going to next step")
                nextButton.performClick()
            }
            command.contains("previous") || command.contains("back") -> {
                speak("Going to previous step")
                prevButton.performClick()
            }
            command.contains("repeat") || command.contains("again") -> {
                speak("Repeating step")
                speakInstruction()
            }
            command.contains("pause") || command.contains("stop") -> {
                speak("Pausing timer")
                if (isTimerRunning) {
                    pauseButton.performClick()
                }
            }
            command.contains("reset") -> {
                speak("Resetting timer")
                resetButton.performClick()
            }
            else -> {
                speak("Command not recognized. Try saying next, previous, repeat, or pause")
            }
        }
    }

    // ========== DIALOGS ==========

    private fun showCompletionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Recipe Complete! 🎉")
            .setMessage("Congratulations! You've finished cooking. Enjoy your meal!")
            .setPositiveButton("Done") { _, _ ->
                finish()
            }
            .setNegativeButton("Review Steps") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Exit Cooking Mode?")
            .setMessage("Are you sure you want to exit? Your progress will not be saved.")
            .setPositiveButton("Yes, Exit") { _, _ ->
                finish()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            audioPermissionGranted = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                audioPermissionGranted = true
                Toast.makeText(this, "Voice commands available - tap mic icon to enable", Toast.LENGTH_SHORT).show()
            } else {
                audioPermissionGranted = false
                Toast.makeText(this, "Voice commands disabled - permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========== LIFECYCLE ==========

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        isVoiceEnabled = false
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        // Pause timer when app goes to background
        if (isTimerRunning && !isTimerPaused) {
            toggleTimer()
        }
    }
}