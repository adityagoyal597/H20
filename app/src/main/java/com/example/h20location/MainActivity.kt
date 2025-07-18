package com.example.h20location

import android.content.Context
import android.content.SharedPreferences
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
    private lateinit var buttonSetDefaultStation: Button // Declare the button

    private val districtMap = mutableMapOf<String, String>() // district name -> district code

    // SharedPreferences constants
    private val PREFS_NAME = "H20LocationPrefs"
    private val KEY_DEFAULT_DISTRICT_CODE = "defaultDistrictCode"
    private val KEY_DEFAULT_DISTRICT_NAME = "defaultDistrictName" // To re-select by name
    private val KEY_DEFAULT_STATION_NAME = "defaultStationName"

    private lateinit var sharedPreferences: SharedPreferences

    // Flags to prevent auto-selection from triggering onItemSelected during initial setup
    private var isDistrictSpinnerInitialized = false
    private var isStationSpinnerInitialized = false
    private var isRestoringDefaultDistrict = false
    private var isRestoringDefaultStation = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        spinnerDistrict = findViewById(R.id.spinnerDistrict)
        spinnerStation = findViewById(R.id.spinnerStation)
        buttonSetDefaultStation = findViewById(R.id.buttonSetDefaultStation) // Initialize the button

        val emptyStationAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf("Select District First"))
        emptyStationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStation.adapter = emptyStationAdapter
        spinnerStation.isEnabled = false

        loadDistricts() // This will also attempt to load and set defaults

        spinnerDistrict.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isDistrictSpinnerInitialized) {
                    isDistrictSpinnerInitialized = true
                    return // Skip first automatic call if any
                }
                if (isRestoringDefaultDistrict) { // If we are in the process of setting the default, don't trigger loadStations yet
                    return
                }

                val selectedDistrictName = parent?.getItemAtPosition(position)?.toString()
                if (selectedDistrictName != null && isSensibleSpinnerItem(selectedDistrictName)) {
                    val code = districtMap[selectedDistrictName]
                    if (code != null) {
                        Log.d("DistrictSelected", "Selected district: $selectedDistrictName, Code: $code")
                        isStationSpinnerInitialized = false // Reset station spinner init flag for new district
                        loadStations(code) // This will load stations and then try to set default station
                    } else {
                        Log.w("DistrictSelected", "No code found for selected unique district: $selectedDistrictName")
                        clearStationSpinner("Select District")
                    }
                } else {
                    clearStationSpinner("Select District")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                clearStationSpinner("Select District")
            }
        }

        spinnerStation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isStationSpinnerInitialized) {
                    isStationSpinnerInitialized = true
                    return // Skip first automatic call
                }
                if (isRestoringDefaultStation) { // If we are in the process of setting the default station
                    return
                }

                val stationName = parent?.getItemAtPosition(position)?.toString()
                if (stationName != null && isSensibleSpinnerItem(stationName)) {
                    Toast.makeText(this@MainActivity, "Selected Station: $stationName", Toast.LENGTH_SHORT).show()
                    Log.d("StationSelected", "Selected station: $stationName")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        buttonSetDefaultStation.setOnClickListener {
            saveDefaultStation()
        }
    }

    private fun isSensibleSpinnerItem(item: String?): Boolean {
        if (item == null) return false
        return item != "No districts available" &&
                item != "Error loading districts" &&
                item != "Network error" &&
                item != "Select District First" &&
                item != "No stations available" &&
                item != "Loading stations..." &&
                item != "Loading..." &&
                item != "No stations data" &&
                item != "Select District"
    }


    private fun saveDefaultStation() {
        val selectedDistrictName = spinnerDistrict.selectedItem?.toString()
        val selectedStationName = spinnerStation.selectedItem?.toString()
        val selectedDistrictCode = districtMap[selectedDistrictName]

        if (selectedDistrictName != null && isSensibleSpinnerItem(selectedDistrictName) &&
            selectedStationName != null && isSensibleSpinnerItem(selectedStationName) &&
            selectedDistrictCode != null) {

            with(sharedPreferences.edit()) {
                putString(KEY_DEFAULT_DISTRICT_CODE, selectedDistrictCode)
                putString(KEY_DEFAULT_DISTRICT_NAME, selectedDistrictName)
                putString(KEY_DEFAULT_STATION_NAME, selectedStationName)
                apply()
            }
            Toast.makeText(this, "$selectedStationName in $selectedDistrictName set as default", Toast.LENGTH_LONG).show()
            Log.d("DefaultSave", "Saved default: District '$selectedDistrictName' ($selectedDistrictCode), Station '$selectedStationName'")
        } else {
            Toast.makeText(this, "Please select a valid district and station first", Toast.LENGTH_SHORT).show()
            Log.w("DefaultSave", "Attempted to save default with invalid selection. District: $selectedDistrictName, Station: $selectedStationName")
        }
    }

    private fun loadAndSetDefaultDistrict() {
        val defaultDistrictName = sharedPreferences.getString(KEY_DEFAULT_DISTRICT_NAME, null)
        if (defaultDistrictName != null) {
            val adapter = spinnerDistrict.adapter
            if (adapter is ArrayAdapter<*>) {
                val count = adapter.count
                for (i in 0 until count) {
                    if (defaultDistrictName == adapter.getItem(i).toString()) {
                        Log.d("DefaultLoad", "Restoring default district: $defaultDistrictName")
                        isRestoringDefaultDistrict = true
                        spinnerDistrict.setSelection(i)
                        // loadStations will be triggered by onItemSelected, but only after isRestoringDefaultDistrict is false
                        // The onItemSelected listener needs to handle this flag to call loadStations
                        // OR we can explicitly call loadStations if the item is truly found and set.
                        val defaultDistrictCode = sharedPreferences.getString(KEY_DEFAULT_DISTRICT_CODE, null)
                        if(defaultDistrictCode != null) {
                            // The onItemSelected will eventually call loadStations.
                            // We just need to make sure onItemSelected doesn't get skipped
                            // and doesn't get called multiple times.
                            // Let the natural onItemSelected flow handle it, but it will only
                            // proceed with loadStations if isRestoringDefaultDistrict is false.
                            // So we set it to false AFTER setting selection
                        }
                        isRestoringDefaultDistrict = false // Allow onItemSelected to proceed if selection changed
                        // If the selection actually changed due to setSelection, onItemSelected is called.
                        // If it was already selected, it won't be.
                        // Manually trigger if it was already selected.
                        if (spinnerDistrict.selectedItemPosition == i) {
                            districtMap[defaultDistrictName]?.let { code ->
                                isStationSpinnerInitialized = false // Reset for new station list
                                loadStations(code) // Then this will try to load default station
                            }
                        }
                        return // Default district found and selection attempt made
                    }
                }
                Log.w("DefaultLoad", "Saved default district '$defaultDistrictName' not found in current spinner items.")
            }
        } else {
            Log.d("DefaultLoad", "No default district saved.")
        }
        // If no default district or it wasn't found, ensure stations for the *current* first district are loaded (if any)
        if (spinnerDistrict.selectedItemPosition != AdapterView.INVALID_POSITION && spinnerDistrict.adapter.count > 0 && isSensibleSpinnerItem(spinnerDistrict.selectedItem.toString())) {
            districtMap[spinnerDistrict.selectedItem.toString()]?.let { code ->
                isStationSpinnerInitialized = false
                loadStations(code)
            }
        } else if (spinnerDistrict.adapter.count > 0 && isSensibleSpinnerItem(spinnerDistrict.getItemAtPosition(0).toString())) {
            // If nothing is "selected" but there are items, try loading for the first one.
            districtMap[spinnerDistrict.getItemAtPosition(0).toString()]?.let { code ->
                isStationSpinnerInitialized = false
                loadStations(code)
            }
        }

    }

    private fun loadAndSetDefaultStation(stationsAdapter: ArrayAdapter<String>) {
        val defaultStationName = sharedPreferences.getString(KEY_DEFAULT_STATION_NAME, null)
        val defaultDistrictCodeForStation = sharedPreferences.getString(KEY_DEFAULT_DISTRICT_CODE, null)
        val currentSelectedDistrictCode = districtMap[spinnerDistrict.selectedItem?.toString()]

        // Only try to set default station if it belongs to the currently selected district
        if (defaultStationName != null && defaultDistrictCodeForStation != null && defaultDistrictCodeForStation == currentSelectedDistrictCode) {
            val count = stationsAdapter.count
            for (i in 0 until count) {
                if (defaultStationName == stationsAdapter.getItem(i)) {
                    Log.d("DefaultLoad", "Restoring default station: $defaultStationName for district $currentSelectedDistrictCode")
                    isRestoringDefaultStation = true
                    spinnerStation.setSelection(i)
                    isRestoringDefaultStation = false
                    return // Default station found and set
                }
            }
            Log.w("DefaultLoad", "Saved default station '$defaultStationName' not found in current station spinner items for district $currentSelectedDistrictCode.")
        } else {
            if (defaultStationName != null) {
                Log.d("DefaultLoad", "Default station '$defaultStationName' belongs to district '$defaultDistrictCodeForStation', but current district is '$currentSelectedDistrictCode'. Not applying.")
            } else {
                Log.d("DefaultLoad", "No default station saved or applicable for the current district.")
            }
        }
    }


    private fun loadDistricts() {
        val call = RetrofitClient.instance.getDistricts()
        // ... (your existing Retrofit call setup) ...
        call.enqueue(object : Callback<List<District>> {
            override fun onResponse(call: Call<List<District>>, response: Response<List<District>>) {
                isDistrictSpinnerInitialized = false // Reset before populating
                if (response.isSuccessful) {
                    val districtsFromApi = response.body()
                    // ... (your existing unique district processing logic) ...
                    // Example from previous answer:
                    if (districtsFromApi != null) {
                        districtMap.clear()
                        val uniqueDistricts = mutableListOf<District>()
                        val seenDistrictNames = mutableSetOf<String>()
                        for (district in districtsFromApi) {
                            if (district.name != null && district.code != null) {
                                if (seenDistrictNames.add(district.name)) {
                                    uniqueDistricts.add(district)
                                    districtMap[district.name] = district.code
                                }
                            }
                        }
                        val districtSpinnerDisplayNames = uniqueDistricts.mapNotNull { it.name }

                        if (districtSpinnerDisplayNames.isNotEmpty()) {
                            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, districtSpinnerDisplayNames)
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerDistrict.adapter = adapter
                            spinnerDistrict.isEnabled = true
                            Log.d("DistrictLoad", "Loaded ${districtSpinnerDisplayNames.size} unique districts.")
                            // *** Attempt to load and set default district AFTER adapter is set ***
                            loadAndSetDefaultDistrict()
                        } else {
                            // ... (handle no districts found) ...
                            clearDistrictSpinner("No districts available")
                            clearStationSpinner("No districts available")
                        }
                    } else {
                        // ... (handle null response body) ...
                        clearDistrictSpinner("Error loading districts")
                        clearStationSpinner("Error loading districts")
                    }
                } else {
                    // ... (handle unsuccessful response) ...
                    clearDistrictSpinner("Error: ${response.code()}")
                    clearStationSpinner("Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<District>>, t: Throwable) {
                // ... (handle failure) ...
                clearDistrictSpinner("Network error")
                clearStationSpinner("Network error")
            }
        })
    }

    private fun loadStations(districtCode: String) {
        Log.d("StationLoad", "Attempting to load stations for district code: $districtCode")
        setStationSpinnerLoading()
        isStationSpinnerInitialized = false // Reset before populating
        val call = RetrofitClient.instance.getStationsByDistrict(districtCode)
        // ... (your existing Retrofit call setup) ...
        call.enqueue(object : Callback<List<Station>> {
            override fun onResponse(call: Call<List<Station>>, response: Response<List<Station>>) {
                if (response.isSuccessful) {
                    val stationsFromApi = response.body()
                    // ... (your existing station name processing) ...
                    if (stationsFromApi != null) {
                        val stationNames = stationsFromApi.mapNotNull { it.ps_name }
                        if (stationNames.isNotEmpty()) {
                            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, stationNames)
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerStation.adapter = adapter
                            spinnerStation.isEnabled = true
                            Log.d("StationLoad", "Loaded ${stationNames.size} stations for $districtCode.")
                            // *** Attempt to load and set default station AFTER adapter is set ***
                            loadAndSetDefaultStation(adapter)
                        } else {
                            // ... (handle no stations) ...
                            clearStationSpinner("No stations available")
                        }
                    } else {
                        // ... (handle null response body) ...
                        clearStationSpinner("No stations data")
                    }
                } else {
                    // ... (handle unsuccessful response) ...
                    clearStationSpinner("Error: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<Station>>, t: Throwable) {
                // ... (handle failure) ...
                clearStationSpinner("Network error")
            }
        })
    }

    // Helper methods (clearDistrictSpinner, clearStationSpinner, setStationSpinnerLoading)
    // as you had them before...
    private fun clearDistrictSpinner(message: String) {
        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf(message))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDistrict.adapter = adapter
        spinnerDistrict.isEnabled = false
    }

    private fun clearStationSpinner(message: String) {
        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf(message))
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStation.adapter = adapter
        spinnerStation.isEnabled = false
    }

    private fun setStationSpinnerLoading() {
        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listOf("Loading...")) // Changed message
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStation.adapter = adapter
        spinnerStation.isEnabled = false
    }
}
