package com.scollon.tapr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Half.EPSILON
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.scollon.amputil.Amp
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), SensorEventListener {

    private val PERMISSION_REQUEST_CODE = 200

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null

    private val gravity = FloatArray(3)
    private val linear_acceleration = FloatArray(3)

    private val NS2S = 1.0f / 1000000000.0f
    private val deltaRotationVector = FloatArray(4) { 0f }
    private var timestamp: Float = 0f


    var speedXOld:Double = 0.0
    var gyroZOld = 0
    var gyroXOld = 0
    var loudnessOld = 0


    val runnable = Runnable {
        updateTv()
        determineTap()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Amp.startEverything(runnable, 100)

//asking user for permisson
      if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE
            )
        }

        val mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)


        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val gyroSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_NORMAL)

    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Do something here if sensor accuracy changes.
    }

    override fun onSensorChanged(event: SensorEvent) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.


        //linear acceleration here
        val lux = event.values[0]

        val alpha: Float = 0.8f

        // Isolate the force of gravity with the low-pass filter.
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        // Remove the gravity contribution with the high-pass filter.
        linear_acceleration[0] = event.values[0] - gravity[0]   //X m/s2
        linear_acceleration[1] = event.values[1] - gravity[1]   //Y m/s2
        linear_acceleration[2] = event.values[2] - gravity[2]   //Z m/s2


        /*
        having every X,Y and Z acceleration will help us determine
        whether the phone was tapper from the side, top, botton or back
         */
        tvX.text = Math.abs(linear_acceleration[0].toInt()).toString()
        tvY.text = Math.abs(linear_acceleration[1].toInt()).toString()
        tvZ.text = Math.abs(linear_acceleration[2].toInt()).toString()


        //gyroscope here

            // This timestep's delta rotation to be multiplied by the current rotation
            // after computing it from the gyro sample data.
            if (timestamp != 0f && event != null) {
                val dT = (event.timestamp - timestamp) * NS2S
                // Axis of the rotation sample, not normalized yet.
                var axisX: Float = event.values[0]
                var axisY: Float = event.values[1]
                var axisZ: Float = event.values[2]

                var axisX2: Int = axisX.toInt()
                var axisY2: Int = axisY.toInt()
                var axisZ2: Int = axisZ.toInt()




                // Calculate the angular speed of the sample
                val omegaMagnitude: Float = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)

                // Normalize the rotation vector if it's big enough to get the axis
                // (that is, EPSILON should represent your maximum allowable margin of error)
                if (omegaMagnitude > EPSILON) {
                    axisX /= omegaMagnitude
                    axisY /= omegaMagnitude
                    axisZ /= omegaMagnitude

                }
                tvgX.text = Math.abs(axisX.toInt()).toString()
                tvgY.text = Math.abs(axisY.toInt()).toString()
                tvgZ.text = Math.abs(axisZ.toInt()).toString()
                // Integrate around this axis with the angular speed by the timestep
                // in order to get a delta rotation from this sample over the timestep
                // We will convert this axis-angle representation of the delta rotation
                // into a quaternion before turning it into the rotation matrix.
                val thetaOverTwo: Float = omegaMagnitude * dT / 2.0f
                val sinThetaOverTwo: Float = sin(thetaOverTwo)
                val cosThetaOverTwo: Float = cos(thetaOverTwo)
                deltaRotationVector[0] = sinThetaOverTwo * axisX
                deltaRotationVector[1] = sinThetaOverTwo * axisY
                deltaRotationVector[2] = sinThetaOverTwo * axisZ
                deltaRotationVector[3] = cosThetaOverTwo

            }
            timestamp = event?.timestamp?.toFloat() ?: 0f
            val deltaRotationMatrix = FloatArray(9) { 0f }
            SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);
            // User code should concatenate the delta rotation we computed with the current rotation
            // in order to get the updated rotation.
            // rotationCurrent = rotationCurrent * deltaRotationMatrix;


    }

    override fun onResume() {

        super.onResume()
        sensor?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        Amp.startRecorder()
    }

    override fun onPause() {
        super.onPause()
        try {
            sensorManager.unregisterListener(this)
        }catch (e: Exception){

            Log.e("rotacja", "dziwna rotacja")

        }
        Amp.stopRecorder()
    }
    fun updateTv(){
        //dB
        val dBlvl = Amp.soundDb().toInt()
        tv_dBells.text = dBlvl.toString()
            // amplitude EMA
        val ampLvl = Amp.getAmplitudeEMA().toInt()
        tv_amplitude.text =ampLvl.toString()

    }


    // this happens once every 100ms
    fun determineTap(){

        var speedXNew = (tvX.text as String).toDouble()
        var gyroZNew = Integer.parseInt(tvgZ.text as String)
        var gyroXNew = Integer.parseInt(tvgX.text as String)


        //compares curent stats with stats from 100ms ago

        if(loudnessOld != 0){
                //the acceleration happens before the noise giving palm hit therefore we have see if it accelerates first and then if it
                // generaten loud enough sound  (40ms is a random number with which I thought it would work (and it kinda does))
                    // new it's time to machine learn the shit out of this (but lemme add more sensor to get more stats)
          if(speedXNew > speedXOld &&(gyroZNew > gyroZOld)){

              CoroutineScope(Dispatchers.IO).launch    {
                  delay(TimeUnit.MILLISECONDS.toMillis(40))
                  withContext(Dispatchers.Main) {
                      // this is called after 0.5 sec
                      var loudnessNew = Amp.getAmplitudeEMA().toInt()
                      if(loudnessNew > loudnessOld+750){
                          Toast.makeText(applicationContext, "jebło", Toast.LENGTH_LONG).show()
                      }
                      loudnessOld = loudnessNew
                  }
              }
          }
            speedXOld = speedXNew
            gyroZOld = gyroZNew
            gyroXOld = gyroXNew


        }else{
            speedXOld = speedXNew
            gyroZOld = gyroZNew
            gyroXOld = gyroXNew
            loudnessOld = Amp.getAmplitudeEMA().toInt()
        }


        /*
        if((speedXNew > 1.5 || speedXNew < -1.5) && loudnessNew > 60 &&(gyroXNew > 3 || gyroZNew > 3)){
            Toast.makeText(this, "jebło", Toast.LENGTH_LONG).show()

        }

         */
    }


}

