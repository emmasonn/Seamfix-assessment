package com.beaconinc.seamfixtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Base64.DEFAULT
import android.util.Base64.encodeToString
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.tasks.Task
import com.google.android.material.imageview.ShapeableImageView
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.PictureResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val REQUEST_CHECK_SETTINGS = 2033
    private val PERMISSION_REQUEST = 101

   private lateinit var postProgress: ProgressBar
    private val repository: ApiRepository = ApiRepository()
    private lateinit var fusedLocationClient: FusedLocationProviderClient //location api recommended by google
    private lateinit var locationCallBack: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var currentLocation: android.location.Location? = null //this variable holds the current location

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val captureImage = findViewById<ShapeableImageView>(R.id.captureImage)
        val cameraView = findViewById<CameraView>(R.id.externalCamera)
        cameraView.setLifecycleOwner(this)
        postProgress = findViewById(R.id.postProgress)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        cameraView.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                super.onPictureTaken(result)
                lifecycleScope.launch {
                    postProgress.visibility = View.VISIBLE
                    compressImage(result.data)
                }
            }
        })

        captureImage.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                print("Sending request")
                cameraView.takePicture()
            }
        }

        locationCallBack = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.lastLocation.let { location ->
                      currentLocation = location
                    }
                }
            }
        locationRequest = LocationRequest().apply {
            interval = TimeUnit.SECONDS.toMillis(60)
            fastestInterval = TimeUnit.SECONDS.toMillis(30)
            priority = PRIORITY_HIGH_ACCURACY
        }
        requestLocationPermission()
    }

    //this function is used to listen to device location at interval
    @SuppressLint("MissingPermission")
    private fun getLocationUpdate() {
        if(checkPermission()) {
            val builder = locationRequest.let {
                LocationSettingsRequest.Builder()
                    .addLocationRequest(it)
            }
            val client: SettingsClient = LocationServices.getSettingsClient(this)
            val task: Task<LocationSettingsResponse> =
                client.checkLocationSettings(builder.build())

            task.addOnSuccessListener {
                fusedLocationClient.requestLocationUpdates(locationRequest,locationCallBack, Looper.getMainLooper())
            }

            task.addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this@MainActivity,
                            REQUEST_CHECK_SETTINGS)
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallBack)
    }

    //this suspend Function convert the captured image which is in ByteArray to Base64 string
    private suspend fun compressImage(b: ByteArray ) {
        val imageString = withContext(Dispatchers.Default) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Base64.getEncoder().encodeToString(b)
            }else {
                encodeToString(b, DEFAULT)
            }
        }
        Log.i("MainActivity", imageString)
        createPost(imageString, currentLocation)
    }

    //this function check the permission status of the device
    private fun checkPermission(): Boolean = (ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED)

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSION_REQUEST
            )
        }else {
            getLocationUpdate()
        }
    }

    //this override is used to check the request of each intent
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        getLastLocation()
                    }
                    Activity.RESULT_CANCELED -> {
                        Log.e( "MainActivity","User canceled location request")
                    }
                }
            }
            PERMISSION_REQUEST -> {
                when(resultCode) {
                    Activity.RESULT_OK -> {
                        getLocationUpdate()
                    }
                }

            }
        }
    }

    //this function gets the last location of the user
    private fun getLastLocation() {
        if(checkPermission()) {
            fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                if(task.isSuccessful) {
                    val location = task.result
                    currentLocation = location
                    Log.i("MainActivity","Longitude: ${location?.longitude}, Latitude: ${location?.latitude}")
                }
            }
        }
    }

    //this function is used to call the post Emergency in the repository
    private suspend fun createPost(imageBase64: String, myLocation: android.location.Location?) {

        val emergency = Emergency(
            phoneNumbers = listOf("080333333333", "080444444444"),
            image = imageBase64,
            location = Location(latitude = "${myLocation?.latitude}", longitude = "${myLocation?.longitude}")
        )
        Log.i("MainActivity", emergency.toString())

        //we consider the different state of the request
        when(val response = repository.postEmergency(emergency)) {
           is PostResult.Success -> {
               val msg = response.msg
                Toast.makeText(applicationContext, msg,Toast.LENGTH_SHORT).show()
               lifecycleScope.launch {
                   postProgress.visibility = View.GONE
               }
            }
            is PostResult.Failure -> {
                val error = response.error
                Toast.makeText(applicationContext, error.message ,Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    postProgress.visibility = View.GONE
                }
            }
        }
    }

    //    fun getCurrentLocation() {
//        if(checkPermission())
//       fusedLocationClient.getCurrentLocation(PRIORITY_HIGH_ACCURACY, object : CancellationToken(){
//           override fun isCancellationRequested(): Boolean {
//               TODO("Not yet implemented")
//           }
//
//           override fun onCanceledRequested(p0: OnTokenCanceledListener): CancellationToken {
//               TODO("Not yet implemented")
//           }
//       })
//    }

}