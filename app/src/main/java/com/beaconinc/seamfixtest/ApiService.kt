package com.beaconinc.seamfixtest

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("create")
    suspend fun postEmergency(@Body emergency: Emergency)

    companion object {
        private const val BASE_URL = "http://dummy.restapiexample.com/api/v1/"

        fun create(): ApiService {
            val client = OkHttpClient.Builder()
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }

}