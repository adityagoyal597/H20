// Station.kt (assuming JSON key is "ps_name" as in your previous code)
package com.example.h20location

import com.google.gson.annotations.SerializedName

data class Station(
    // If your JSON key for station name is something else, adjust "ps_name"
    @SerializedName("ps_name")
    val ps_name: String? // Make it nullable for safety
)