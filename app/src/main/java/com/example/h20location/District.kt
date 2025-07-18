package com.example.h20location

import com.google.gson.annotations.SerializedName

data class District(
    @SerializedName("district_code") // Maps JSON key "district_code" to this 'code' property
    val code: String?,               // Making it nullable is a good practice

    @SerializedName("district")      // Maps JSON key "district" to this 'name' property
    val name: String?                // Making it nullable is a good practice
)
