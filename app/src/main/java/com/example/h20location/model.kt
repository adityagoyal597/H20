package com.example.h20location

data class District(val code: String, val name: String)
{override fun toString(): String {
    return name
}
}

data class Station(
    val ps_name: String
)
