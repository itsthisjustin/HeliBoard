package helium314.keyboard.latin;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

import androidx.annotation.NonNull;

import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.latin.permissions.PermissionsUtil;

import java.util.ArrayList;

/**
 * Helper class for handling voice input functionality using SpeechRecognizer API.
 * This approach works even when a hardware keyboard is attached.
 */
public class VoiceInputHelper {
    private static final String TAG = VoiceInputHelper.class.getSimpleName();

    private final LatinIME mLatinIME;
    private SpeechRecognizer mSpeechRecognizer;
    private boolean mIsListening = false;
    private View mVoiceInputView;
    private TextView mStatusText;
    private TextView mResultText;
    private final Handler mHandler;

    public VoiceInputHelper(@NonNull LatinIME latinIME) {
        mLatinIME = latinIME;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void setVoiceInputView(View view) {
        mVoiceInputView = view;
        if (view != null) {
            mStatusText = view.findViewById(R.id.voice_status_text);
            mResultText = view.findViewById(R.id.voice_result_text);
        }
    }

    /**
     * Starts voice recognition using SpeechRecognizer.
     * This works even when a hardware keyboard is attached.
     */
    public void startVoiceRecognition() {
        if (mIsListening) {
            Log.w(TAG, "Already listening, ignoring request");
            return;
        }

        // Check for microphone permission
        if (!checkMicrophonePermission()) {
            Log.w(TAG, "Microphone permission not granted, requesting permission");
            requestMicrophonePermission();
            return;
        }

        Log.i(TAG, "Starting voice recognition");

        // Show the voice input view via KeyboardSwitcher
        KeyboardSwitcher.getInstance().setVoiceInputKeyboard();

        if (mSpeechRecognizer == null) {
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mLatinIME);
            mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.i(TAG, "Ready for speech");
                    updateStatus("Listening...");
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.i(TAG, "Beginning of speech");
                    updateStatus("Speak now");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Audio level changed - could visualize this
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Audio buffer received
                }

                @Override
                public void onEndOfSpeech() {
                    Log.i(TAG, "End of speech");
                    updateStatus("Processing...");
                    mIsListening = false;
                }

                @Override
                public void onError(int error) {
                    Log.e(TAG, "Speech recognition error: " + error);
                    String errorMsg = "Error: " + getErrorMessage(error);
                    updateStatus(errorMsg);
                    mIsListening = false;
                    // Hide the voice view after a short delay
                    mHandler.postDelayed(() -> {
                        KeyboardSwitcher.getInstance().setAlphabetKeyboard();
                    }, 1500);
                }

                @Override
                public void onResults(Bundle results) {
                    Log.i(TAG, "Speech recognition results received");
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String recognizedText = matches.get(0);
                        Log.i(TAG, "Recognized text: " + recognizedText);
                        updateResult(recognizedText);
                        insertText(recognizedText);
                        // Hide the voice view after showing the result briefly
                        mHandler.postDelayed(() -> {
                            KeyboardSwitcher.getInstance().setAlphabetKeyboard();
                        }, 800);
                    }
                    mIsListening = false;
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    // Show partial recognition results
                    ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        updateResult(matches.get(0));
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    // Reserved for future events
                }
            });
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mLatinIME.getPackageName());

        try {
            mSpeechRecognizer.startListening(intent);
            mIsListening = true;
            updateStatus("Initializing...");
            Log.i(TAG, "Voice recognition started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start voice recognition", e);
            updateStatus("Failed to start");
            mIsListening = false;
        }
    }

    private void updateStatus(final String status) {
        mHandler.post(() -> {
            if (mStatusText != null) {
                mStatusText.setText(status);
            }
        });
    }

    private void updateResult(final String result) {
        mHandler.post(() -> {
            if (mResultText != null) {
                mResultText.setText(result);
            }
        });
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No match found";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech detected";
            default:
                return "Unknown error";
        }
    }

    /**
     * Stops voice recognition if it's currently active.
     */
    public void stopVoiceRecognition() {
        if (mSpeechRecognizer != null && mIsListening) {
            Log.i(TAG, "Stopping voice recognition");
            mSpeechRecognizer.cancel();
            mIsListening = false;
        }
    }

    /**
     * Cleans up resources.
     */
    public void destroy() {
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
            mSpeechRecognizer = null;
        }
        mIsListening = false;
    }

    /**
     * Inserts the recognized text into the current input field.
     */
    private void insertText(String text) {
        InputConnection ic = mLatinIME.getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text + " ", 1);
            Log.i(TAG, "Inserted text: " + text);
        } else {
            Log.w(TAG, "No input connection available");
        }
    }

    /**
     * Checks if microphone permission is granted.
     */
    private boolean checkMicrophonePermission() {
        return PermissionsUtil.checkAllPermissionsGranted(mLatinIME, Manifest.permission.RECORD_AUDIO);
    }

    /**
     * Requests microphone permission from the user.
     * Opens a transparent activity that can show the permission dialog.
     */
    private void requestMicrophonePermission() {
        try {
            Intent intent = new Intent(mLatinIME, helium314.keyboard.latin.permissions.PermissionsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mLatinIME.startActivity(intent);
            Log.i(TAG, "Launched permission request activity");
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch permission activity", e);
            // Fallback to showing message
            KeyboardSwitcher.getInstance().setVoiceInputKeyboard();
            updateStatus("Permission needed");
            updateResult("Please enable Microphone permission in Settings");
            mHandler.postDelayed(() -> {
                KeyboardSwitcher.getInstance().setAlphabetKeyboard();
            }, 3000);
        }
    }
}

