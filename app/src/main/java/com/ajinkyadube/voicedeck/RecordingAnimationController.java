package com.ajinkyadube.voicedeck;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Recording-only feedback. Contains no audio capture, timers, or retained Activity reference. */
final class RecordingAnimationController {
    private final View microphone;
    private final ViewGroup waveform;
    private AnimatorSet pulse;
    private boolean recording;
    private float smoothedLevel;

    RecordingAnimationController(View microphone, ViewGroup waveform) {
        this.microphone = microphone;
        this.waveform = waveform;
    }

    void start() {
        stop();
        recording = true;
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        if (microphone != null) {
            ObjectAnimator x = ObjectAnimator.ofFloat(microphone, View.SCALE_X, 1f, 1.08f);
            ObjectAnimator y = ObjectAnimator.ofFloat(microphone, View.SCALE_Y, 1f, 1.08f);
            for (ObjectAnimator animator : new ObjectAnimator[]{x, y}) {
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setRepeatMode(ValueAnimator.REVERSE);
            }
            pulse = new AnimatorSet();
            pulse.playTogether(x, y);
            pulse.setDuration(650);
            pulse.setInterpolator(new AccelerateDecelerateInterpolator());
            pulse.start();
        }
        if (waveform != null) for (int i = 0; i < waveform.getChildCount(); i++) {
            waveform.getChildAt(i).setScaleY(0.28f);
        }
    }

    void onVolume(float rmsDb) {
        if (!recording || waveform == null || !Float.isFinite(rmsDb)
                || !ValueAnimator.areAnimatorsEnabled()) return;
        // Providers use different RMS ranges; clamp visual levels rather than treating them as dB SPL.
        float level = Math.max(0f, Math.min(1f, (rmsDb + 2f) / 12f));
        smoothedLevel = smoothedLevel * 0.55f + level * 0.45f;
        for (int i = 0; i < waveform.getChildCount(); i++) {
            View bar = waveform.getChildAt(i);
            float weight = 0.75f + (i % 3) * 0.125f;
            bar.animate().cancel();
            bar.animate().scaleY(0.28f + 0.72f * smoothedLevel * weight)
                    .setDuration(90).start();
        }
    }

    void stop() {
        recording = false;
        smoothedLevel = 0f;
        if (pulse != null) { pulse.cancel(); pulse = null; }
        if (microphone != null) {
            microphone.animate().cancel();
            microphone.setScaleX(1f);
            microphone.setScaleY(1f);
        }
        if (waveform != null) for (int i = 0; i < waveform.getChildCount(); i++) {
            View bar = waveform.getChildAt(i);
            bar.animate().cancel();
            bar.setScaleY(1f);
        }
    }
}
