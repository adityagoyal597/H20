package com.example.h20location

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ApiClient {

    @Headers("Accept: application/json")
    @GET("get_districts.php")
    fun getDistricts(): Call<List<District>>

    @GET("get_stations.php")
    fun getStationsByDistrict(@Query("district_code") districtCode: String): Call<List<Station>>
}
