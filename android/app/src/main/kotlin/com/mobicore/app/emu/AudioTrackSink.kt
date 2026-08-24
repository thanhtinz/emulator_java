package com.mobicore.app.emu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.mobicore.core.audio.AudioClip
import com.mobicore.core.audio.AudioSink

/**
 * Plays what the emulator produces through Android's audio output.
 *
 * The core hands over a finished block of samples rather than a stream, which
 * is what J2ME audio actually is — a beep, a short effect, a looping tune — so
 * each sound becomes one static [AudioTrack]. Nothing here mixes or streams:
 * a static track already plays alongside the others, and Android's own mixer
 * does the work far better than one written here would.
 *
 * Tracks are pooled by nothing and released on stop, but a game that leaks
 * players cannot leak unboundedly: [MAX_VOICES] tracks live at once and the
 * oldest finished one is reclaimed before a new sound starts.
 */
class AudioTrackSink : AudioSink {

    private val voices = HashMap<Int, AudioTrack>()
    private var nextVoice = 0

    override fun start(clip: AudioClip, loops: Int, volume: Int): Int {
        reclaim()
        if (voices.size >= MAX_VOICES) return AudioSink.NO_VOICE
        val pcm = clip.pcm()
        if (pcm.isEmpty()) return AudioSink.NO_VOICE

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(clip.sampleRate())
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return AudioSink.NO_VOICE

        track.write(pcm, 0, pcm.size)
        // MIDP counts plays; AudioTrack counts repeats, and -1 is forever.
        val repeats = if (loops <= 0) -1 else loops - 1
        if (repeats != 0) {
            track.setLoopPoints(0, clip.frames(), repeats)
        }
        applyVolume(track, volume)
        track.play()

        val voice = nextVoice++
        voices[voice] = track
        return voice
    }

    override fun stop(voice: Int) {
        val track = voices.remove(voice) ?: return
        runCatching {
            track.pause()
            track.flush()
            track.release()
        }
    }

    override fun setVolume(voice: Int, volume: Int) {
        voices[voice]?.let { applyVolume(it, volume) }
    }

    override fun isPlaying(voice: Int): Boolean {
        val track = voices[voice] ?: return false
        return track.playState == AudioTrack.PLAYSTATE_PLAYING &&
            (track.playbackHeadPosition < frameCount(track) || isLooping(track))
    }

    override fun positionMs(voice: Int): Long {
        val track = voices[voice] ?: return 0
        val rate = track.sampleRate
        if (rate <= 0) return 0
        return track.playbackHeadPosition.toLong() * 1000L / rate
    }

    /** Releases everything; call when the game stops. */
    fun releaseAll() {
        voices.values.forEach { track -> runCatching { track.release() } }
        voices.clear()
    }

    private fun applyVolume(track: AudioTrack, volume: Int) {
        val level = volume.coerceIn(0, 100) / 100f
        runCatching { track.setVolume(level) }
    }

    private fun frameCount(track: AudioTrack): Int = track.bufferSizeInFrames

    private fun isLooping(track: AudioTrack): Boolean =
        track.playState == AudioTrack.PLAYSTATE_PLAYING &&
            track.playbackHeadPosition >= frameCount(track)

    /** Drops tracks that have finished, so a long session does not pile up. */
    private fun reclaim() {
        val finished = voices.filter { (voice, _) -> !isPlaying(voice) }
        finished.forEach { (voice, track) ->
            runCatching { track.release() }
            voices.remove(voice)
        }
    }

    private companion object {
        /** More than a J2ME game ever plays at once, and a cap on leaks. */
        const val MAX_VOICES = 8
    }
}
