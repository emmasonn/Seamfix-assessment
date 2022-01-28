package com.beaconinc.seamfixtest

import java.lang.Exception

//data model that is parse to json during network interaction
data class Emergency(
    val phoneNumbers: List<String>,
    val image: String,
    val location: Location
)

data class Location(
    val latitude: String,
    val longitude: String
)

sealed class PostResult {
    data class Success(val msg: String) : PostResult()
    data class Failure(val error: Exception) : PostResult()
}
