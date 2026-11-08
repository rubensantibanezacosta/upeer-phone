package app.upeer.transport

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log

/**
 * Captura, codificación, decodificación y reproducción del audio de una llamada.
 *
 * El escritorio usa WebCodecs (`AudioEncoder`/`AudioDecoder`), que es API del navegador y no existe en
 * Android: aquí el códec es **MediaCodec** con Opus y el PCM entra y sale por `AudioRecord`/`AudioTrack`,
 * con los mismos parámetros que fija `CallAudioConfig` (48 kHz, mono, 64 kbps). El frame que se manda
 * es el de `CallMediaFrames`, idéntico al del escritorio.
 *
 * Tres cosas que se pagan caras si se hacen mal y aquí están explícitas:
 *
 * - **Cancelación de eco.** Sin ella, en un altavoz el interlocutor se oye a sí mismo con retardo y la
 *   llamada es inservible. Se activa si el dispositivo la tiene; si no, se avisa y se sigue, porque
 *   quitar la llamada entera por eso es peor.
 * - **El hilo de captura no puede bloquear al de la UI.** `AudioRecord.read` se queda esperando hasta
 *   llenar el buffer; si corre en el hilo principal, la app se congela en cuanto empieza la llamada.
 * - **Todo se suelta en el mismo sitio.** Un `AudioRecord`, un `MediaCodec` o un `AudioTrack` que se
 *   queda abierto sigue gastando batería hasta que muere el proceso, y además impide abrir otro.
 */
internal class CallAudioEngine(private val sendFrame: (ByteArray) -> Unit) : CallMediaSink {
  private val lock = Any()
  private var capturing = false
  private var playing = false
  private var sequence = 0L

  private var record: AudioRecord? = null
  private var encoder: MediaCodec? = null
  private var track: AudioTrack? = null
  private var decoder: MediaCodec? = null
  private var echoCanceler: AcousticEchoCanceler? = null
  private var noiseSuppressor: NoiseSuppressor? = null
  private var gainControl: AutomaticGainControl? = null
  private var captureThread: Thread? = null
  private var playbackThread: Thread? = null

  private var muted = false

  /** Abre el códec y arranca la captura. Lanza si el dispositivo no puede; el llamador decide. */
  override fun startCapture() {
    synchronized(lock) {
      if (capturing) return
      val format = MediaFormat.createAudioFormat(CallAudioConfig.MIME_TYPE, CallAudioConfig.SAMPLE_RATE, CallAudioConfig.CHANNELS)
      format.setInteger(MediaFormat.KEY_BIT_RATE, CallAudioConfig.VOICE_BITRATE)
      val codec = MediaCodec.createEncoderByType(CallAudioConfig.MIME_TYPE)
      codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
      codec.start()

      val minBuffer = AudioRecord.getMinBufferSize(
        CallAudioConfig.SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
      if (minBuffer <= 0) {
        runCatching { codec.stop(); codec.release() }
        throw IllegalStateException("El dispositivo no admite captura a ${CallAudioConfig.SAMPLE_RATE} Hz mono")
      }
      val record = AudioRecord(
        android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        CallAudioConfig.SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minBuffer, CallAudioConfig.BYTES_PER_FRAME * 4),
      )
      if (record.state != AudioRecord.STATE_INITIALIZED) {
        runCatching { codec.stop(); codec.release() }
        runCatching { record.release() }
        throw IllegalStateException("AudioRecord no se pudo inicializar")
      }
      // Efectos sobre el propio AudioRecord: se aplican al micrófono, no a la reproducción.
      attachEffects(record.audioSessionId)

      record.startRecording()
      encoder = codec
      this.record = record
      capturing = true
      sequence = 0L
      captureThread = Thread({ captureLoop(format) }, "upeer-call-capture").also { it.isDaemon = true; it.start() }
      Log.i(TAG, "Captura de audio abierta: ${CallAudioConfig.SAMPLE_RATE} Hz mono, ${CallAudioConfig.VOICE_BITRATE} bps")
    }
  }

  /**
   * Efectos de voz. No son opcionales en la práctica —sin cancelación de eco con altavoz la llamada no
   * sirve— pero el sistema no los ofrece en todos los móviles, así que si faltan se sigue sin ellos.
   */
  private fun attachEffects(sessionId: Int) {
    if (sessionId == 0) return
    echoCanceler = runCatching {
      if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true } else null
    }.getOrElse { cause ->
      Log.w(TAG, "Sin cancelación de eco: ${cause.message ?: cause.javaClass.simpleName}")
      null
    }
    if (echoCanceler == null) Log.w(TAG, "Este dispositivo no ofrece cancelación de eco: la llamada con altavoz tendrá eco")
    noiseSuppressor = runCatching {
      if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)?.also { it.enabled = true } else null
    }.getOrNull()
    gainControl = runCatching {
      if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)?.also { it.enabled = true } else null
    }.getOrNull()
  }

  override fun setMuted(value: Boolean) {
    muted = value
    // Silenciar aquí y no solo callar el envío: si el micrófono sigue capturando, el otro extremo
    // recibe frames vacíos que gastan batería y ancho de banda para nada.
    synchronized(lock) {
      runCatching {
        if (value) record?.stop() else record?.startRecording()
      }.onFailure { cause ->
        Log.w(TAG, "No se pudo cambiar el silencio del micrófono: ${cause.message ?: cause.javaClass.simpleName}")
      }
    }
  }

  private fun captureLoop(format: MediaFormat) {
    val buffer = ByteArray(CallAudioConfig.BYTES_PER_FRAME)
    val info = MediaCodec.BufferInfo()
    try {
      while (Thread.currentThread() == captureThread) {
        val source = synchronized(lock) { record } ?: break
        val read = source.read(buffer, 0, buffer.size)
        if (read <= 0) {
          if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) break
          continue
        }
        if (muted) continue
        encodeAndSend(buffer, read, info, format)
      }
    } catch (cause: Exception) {
      Log.w(TAG, "La captura de audio se detuvo: ${cause.message ?: cause.javaClass.simpleName}")
    }
  }

  private fun encodeAndSend(pcm: ByteArray, length: Int, info: MediaCodec.BufferInfo, format: MediaFormat) {
    val codec = synchronized(lock) { encoder } ?: return
    val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
    if (inputIndex >= 0) {
      val input = codec.getInputBuffer(inputIndex) ?: return
      input.clear()
      input.put(pcm, 0, length)
      val timestampUs = sequence * CallAudioConfig.FRAME_MILLIS * 1000
      codec.queueInputBuffer(inputIndex, 0, length, timestampUs, 0)
    }
    var outputIndex = codec.dequeueOutputBuffer(info, 0)
    while (outputIndex >= 0) {
      val output = codec.getOutputBuffer(outputIndex)
      if (output != null && info.size > 0) {
        val encoded = ByteArray(info.size)
        output.position(info.offset)
        output.get(encoded, 0, info.size)
        val timestampMillis = info.presentationTimeUs / 1000.0
        val frame = CallMediaFrames.encode(
          CallMediaFrames.KIND_AUDIO,
          true,
          timestampMillis,
          sequence,
          encoded,
        )
        sequence += 1
        sendFrame(frame)
      }
      codec.releaseOutputBuffer(outputIndex, false)
      outputIndex = codec.dequeueOutputBuffer(info, 0)
    }
  }

  /** Abre la reproducción. Los frames llegan por [playFrame] desde el hilo del transporte. */
  override fun startPlayback() {
    synchronized(lock) {
      if (playing) return
      val codec = MediaCodec.createDecoderByType(CallAudioConfig.MIME_TYPE)
      val format = MediaFormat.createAudioFormat(CallAudioConfig.MIME_TYPE, CallAudioConfig.SAMPLE_RATE, CallAudioConfig.CHANNELS)
      codec.configure(format, null, null, 0)
      codec.start()
      val minBuffer = AudioTrack.getMinBufferSize(
        CallAudioConfig.SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
      val track = AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(CallAudioConfig.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
        )
        .setBufferSizeInBytes(maxOf(minBuffer, CallAudioConfig.BYTES_PER_FRAME * 4))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
      track.play()
      decoder = codec
      this.track = track
      playing = true
      playbackThread = Thread({ playbackLoop() }, "upeer-call-playback").also { it.isDaemon = true; it.start() }
      Log.i(TAG, "Reproducción de audio abierta")
    }
  }

  /** Encola un frame ya codificado que llegó del otro extremo. */
  override fun playFrame(encoded: ByteArray) {
    val codec = synchronized(lock) { decoder } ?: return
    val frame = CallMediaFrames.decode(encoded) ?: return
    if (frame.kind != CallMediaFrames.KIND_AUDIO) return
    val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
    if (inputIndex < 0) return
    val input = codec.getInputBuffer(inputIndex) ?: return
    input.clear()
    input.put(frame.data)
    codec.queueInputBuffer(inputIndex, 0, frame.data.size, (frame.timestamp * 1000).toLong(), 0)
  }

  private fun playbackLoop() {
    val info = MediaCodec.BufferInfo()
    while (Thread.currentThread() == playbackThread) {
      try {
        val codec = synchronized(lock) { decoder } ?: break
        val outputIndex = codec.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)
        if (outputIndex >= 0) {
          val output = codec.getOutputBuffer(outputIndex)
          val sink = synchronized(lock) { track }
          if (output != null && sink != null && info.size > 0) {
            val pcm = ByteArray(info.size)
            output.position(info.offset)
            output.get(pcm, 0, info.size)
            sink.write(pcm, 0, pcm.size)
          }
          codec.releaseOutputBuffer(outputIndex, false)
        }
      } catch (cause: Exception) {
        Log.w(TAG, "La reproducción se detuvo: ${cause.message ?: cause.javaClass.simpleName}")
        break
      }
    }
  }

  /** Suelta todo. Idempotente y sin lanzar: se llama también desde rutas de error. */
  override fun stop() {
    val pending: List<Any?>
    synchronized(lock) {
      if (!capturing && !playing) return
      capturing = false
      playing = false
      captureThread = null
      playbackThread = null
      pending = listOf(echoCanceler, noiseSuppressor, gainControl, record, encoder, track, decoder)
      echoCanceler = null
      noiseSuppressor = null
      gainControl = null
      record = null
      encoder = null
      track = null
      decoder = null
    }
    for (resource in pending) {
      runCatching {
        when (resource) {
          is AcousticEchoCanceler -> { resource.enabled = false; resource.release() }
          is NoiseSuppressor -> { resource.enabled = false; resource.release() }
          is AutomaticGainControl -> { resource.enabled = false; resource.release() }
          is AudioRecord -> { resource.stop(); resource.release() }
          is AudioTrack -> { resource.stop(); resource.release() }
          is MediaCodec -> { resource.stop(); resource.release() }
        }
      }.onFailure { cause ->
        Log.w(TAG, "No se pudo soltar un recurso de audio: ${cause.message ?: cause.javaClass.simpleName}")
      }
    }
    Log.i(TAG, "Motor de audio cerrado")
  }

  companion object {
    private const val TAG = "UPeerTransport"
    private const val INPUT_TIMEOUT_US = 10_000L
    private const val OUTPUT_TIMEOUT_US = 10_000L
  }
}
