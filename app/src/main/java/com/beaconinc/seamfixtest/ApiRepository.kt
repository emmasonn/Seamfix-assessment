package com.beaconinc.seamfixtest

import android.util.Log
import retrofit2.HttpException
import java.io.IOException

//Repository create for the sake of separation of concern
class ApiRepository {

  private  val service  = ApiService.create()

    //the function below interacts with the api service interface
    suspend fun postEmergency(emergency: Emergency): PostResult {
       return try {
         service.postEmergency(emergency)
            Log.i("ApiService", "Post request was successful")
            PostResult.Success(msg = "Captured Image has been received")
        }catch (ioException: IOException) {
            Log.e("Service","$ioException")
            PostResult.Failure(ioException)
        }catch (exception: HttpException) {
            Log.e("Service","$exception")
            PostResult.Failure(exception)
        }
    }
}