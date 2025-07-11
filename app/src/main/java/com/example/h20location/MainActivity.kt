package com.example.h20location

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerDistrict: Spinner
    private lateinit var spinnerStation: Spinner
    private val districtMap = mutableMapOf<String, String>() // district name → code

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerDistrict = findViewById(R.id.spinnerDistrict)
        spinnerStation = findViewById(R.id.spinnerStation)

        loadDistricts()

        spinnerDistrict.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val selectedDistrict = parent?.getItemAtPosition(position).toString()
                val code = districtMap[selectedDistrict]
                if (code != null) loadStations(code)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadDistricts() {
        val call = RetrofitClient.instance.getDistricts()

        call.enqueue(object : Callback<List<District>> {
            override fun onResponse(
                call: Call<List<District>>, response: Response<List<District>>
            ) {
                if (response.isSuccessful) {
                    val districts = response.body() ?: emptyList()
                    val districtNames = districts.map { district ->
                        districtMap[district.name] = district.code
                        district.name
                    }

                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, districtNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerDistrict.adapter = adapter
                }
            }

            override fun onFailure(call: Call<List<District>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Failed to load districts", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadStations(districtCode: String) {
        val call = RetrofitClient.instance.getStationsByDistrict(districtCode)

        call.enqueue(object : Callback<List<Station>> {
            override fun onResponse(
                call: Call<List<Station>>, response: Response<List<Station>>
            ) {
                if (response.isSuccessful) {
                    val stations = response.body()?.map { it.ps_name } ?: emptyList()
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, stations)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerStation.adapter = adapter
                }
            }

            override fun onFailure(call: Call<List<Station>>, t: Throwable) {

                Log.e("RetrofitError", "Error: ${t.message}", t)
                Toast.makeText(this@MainActivity, "Failed to load districts", Toast.LENGTH_SHORT).show()            }
        })
    }
}