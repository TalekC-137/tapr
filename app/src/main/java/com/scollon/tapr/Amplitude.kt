package com.scollon.tapr

import android.app.PendingIntent.getActivity
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_amplitude.*
import java.io.IOException
import java.util.jar.Manifest


class Amplitude : AppCompatActivity() {


    var mRecorder: MediaRecorder? = null
    var runner: Thread? = null
    private var mEMA = 0.0
    private val EMA_FILTER = 0.6

    val updater = Runnable { updateTv() }
    val mHandler: Handler = Handler()



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_amplitude)



        if (runner == null) {
            runner = object : Thread() {
                override fun run() {
                    while (runner != null) {
                        try {
                            sleep(1000)
                            Log.i("Noise", "Tock")
                        } catch (e: InterruptedException) {
                        }
                        mHandler.post(updater)
                    }
                }
            }
            (runner as Thread).start()
            Log.d("Noise", "start runner()")
        }


    }


    override fun onResume() {
        super.onResume()

/*
        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(getActivity(),
                new String[]{Manifest.permission.RECORD_AUDIO},
                REQUEST_MICROPHONE);

        }

 */


        startRecorder()
    }

    override fun onPause() {
        super.onPause()
        stopRecorder()
    }

    fun startRecorder() {
        if (mRecorder == null) {
            mRecorder = MediaRecorder()
            mRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mRecorder!!.setOutputFile("/dev/null")
            try {
                mRecorder!!.prepare()
            } catch (ioe: IOException) {
                Log.e("[Monkey]", "IOException: " + Log.getStackTraceString(ioe))
            } catch (e: SecurityException) {
                Log.e("[Monkey]", "SecurityException: " + Log.getStackTraceString(e))
            }
            try {
                mRecorder!!.start()
            } catch (e: SecurityException) {
                Log.e("[Monkey]", "SecurityException: " + Log.getStackTraceString(e))
            }

            //mEMA = 0.0;
        }
    }

    fun stopRecorder() {
        if (mRecorder != null) {
            mRecorder!!.stop()
            mRecorder!!.release()
            mRecorder = null
        }
    }

    fun updateTv() {
        tv_ampitude!!.text = java.lang.Double.toString(getAmplitudeEMA()) + " dB"
    }

    fun soundDb(ampl: Double): Double {
        return 20 * Math.log10(getAmplitudeEMA() / ampl)
    }

    fun getAmplitude(): Double {
        return if (mRecorder != null) mRecorder!!.maxAmplitude.toDouble() else 0.0
    }

    fun getAmplitudeEMA(): Double {
        val amp = getAmplitude()
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA
        return mEMA
    }


}