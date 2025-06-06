package com.example.intellidish

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.intellidish.adapters.PotluckIngredientAdapter
import com.example.intellidish.api.NetworkClient
import com.example.intellidish.models.Participant
import com.example.intellidish.models.Potluck
import com.example.intellidish.models.PotluckIngredient
import com.example.intellidish.utils.PreferencesManager
import com.example.intellidish.utils.UserManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class PotluckDetailActivity : AppCompatActivity() {

    private lateinit var textName: MaterialTextView
    private lateinit var textOwner: MaterialTextView
    private lateinit var textDate: MaterialTextView
    private lateinit var recyclerIngredients: RecyclerView

    private lateinit var btnAddIngredient: MaterialButton
    private lateinit var btnGenerateAI: MaterialButton
    private lateinit var btnManageParticipant: MaterialButton
    private lateinit var layoutHostButtons: LinearLayout

    private lateinit var btnUploadImage: MaterialButton
    private lateinit var btnViewImage: MaterialButton
    private lateinit var btnCuisineType: MaterialButton
    private lateinit var btnTogglePreferences: MaterialButton
    private lateinit var btnBack: ExtendedFloatingActionButton
    private lateinit var btnRefresh: ExtendedFloatingActionButton
    private lateinit var btnDeleteOrLeave: MaterialButton

    private var selectedImageUri: Uri? = null
    private lateinit var preferencesManager: PreferencesManager
    private val client = OkHttpClient()

    // Auto-refresh job
    private var autoRefreshJob: Job? = null
    private val AUTO_REFRESH_INTERVAL = 5000L // 5 seconds

    // Data from Intent
    private var potluckName: String = ""
    private var potluckOwner: String = ""
    private var potluckDate: String = ""
    private var currentUser: String = "Unknown User"
    private var potluckId: String = ""
    private var currentUserId: String = ""

    // Flattened potluck ingredient list
    private val potluckIngredients = mutableListOf<PotluckIngredient>()
    private lateinit var ingredientAdapter: PotluckIngredientAdapter

    private lateinit var progressBar: ProgressBar
    private lateinit var generateButton: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_potluck_detail)
        progressBar = findViewById(R.id.progress_bar)
        generateButton = findViewById(R.id.btn_generate_ai)

        initViews()
        retrieveLoggedInUser()
        loadIntentData()
        setupUI()
        setupRecycler()
        setupListeners()
        startAutoRefresh()
    }

    private fun initViews() {
        textName = findViewById(R.id.text_potluck_name)
        textOwner = findViewById(R.id.text_potluck_owner)
        textDate = findViewById(R.id.text_potluck_date)
        recyclerIngredients = findViewById(R.id.recycler_ingredients)

        btnAddIngredient = findViewById(R.id.btn_add_ingredient)
        btnGenerateAI = findViewById(R.id.btn_generate_ai)
        btnManageParticipant = findViewById(R.id.btn_manage_participant)
        layoutHostButtons = findViewById(R.id.layout_host_buttons)

        btnUploadImage = findViewById(R.id.btn_upload_image)
        btnViewImage = findViewById(R.id.btn_view_image)
        btnCuisineType = findViewById(R.id.btn_cuisine_type)
        btnTogglePreferences = findViewById(R.id.btn_toggle_preferences)
        btnBack = findViewById(R.id.btn_back)
        btnRefresh = findViewById(R.id.btn_refresh)

        btnDeleteOrLeave = findViewById(R.id.btn_delete_or_leave)

        preferencesManager = PreferencesManager(this)
    }

    private fun retrieveLoggedInUser() {
        currentUser = UserManager.getUserName() ?: "Unknown User"
        currentUserId = UserManager.getUserId() ?: ""
        potluckId = intent.getStringExtra("potluck_id") ?: ""

    }

    private fun loadIntentData() {
        potluckName = intent.getStringExtra("potluck_name") ?: "Untitled Potluck"
        potluckOwner = intent.getStringExtra("potluck_owner") ?: "???"
        potluckDate = intent.getStringExtra("potluck_date") ?: "2025-03-02"

        val participantsJson = intent.getStringExtra("potluck_participants") ?: "[]"
        val type = object : TypeToken<List<Participant>>() {}.type
        val participants: List<Participant> = Gson().fromJson(participantsJson, type) ?: emptyList()

        // Flatten each participant's ingredients
        for (p in participants) {
            val userName = p.user.name
            p.ingredients?.forEach { ing ->
                potluckIngredients.add(PotluckIngredient(ing, userName))
            }
        }
    }

    private fun setupUI() {
        textName.text = potluckName
        textOwner.text = "Owned by: $potluckOwner"
        val isoDateString = potluckDate

        try {
            // 1) Parse the ISO date string
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val date = inputFormat.parse(isoDateString)

            // 2) Format to just "yyyy-MM-dd"
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = date?.let { outputFormat.format(it) } ?: isoDateString

            // Now set the text to something like "Created on: 2025-02-28"
            textDate.text = "Created on: $formattedDate"
        } catch (e: ParseException) {
            // If parsing fails, just display the original string
            textDate.text = "Created on: $potluckDate"
            e.printStackTrace()
        }
        // If current user is the potluck owner, show Add/Remove participant
        if (currentUser.equals(potluckOwner, ignoreCase = true)) {
            layoutHostButtons.visibility = View.VISIBLE
        }

        // Show Delete if Owner, Leave if Participant
        if (currentUser.equals(potluckOwner, ignoreCase = true)) {
            btnDeleteOrLeave.text = "Delete This Potluck"
            btnDeleteOrLeave.setOnClickListener { deletePotluck() }
        } else {
            btnDeleteOrLeave.text = "Leave This Potluck"
            btnDeleteOrLeave.setOnClickListener { leavePotluck() }
        }
    }

    private fun setupRecycler() {
        recyclerIngredients.layoutManager = LinearLayoutManager(this)
        val potluckId = intent.getStringExtra("potluck_id") ?: ""
        val participantId = intent.getStringExtra("participant_id") ?: ""
        ingredientAdapter = PotluckIngredientAdapter(potluckId, currentUser, currentUserId)
        recyclerIngredients.adapter = ingredientAdapter
    }

    private fun setupListeners() {
        // Add Ingredient
        btnAddIngredient.setOnClickListener {
            val inputField = findViewById<TextInputEditText>(R.id.ingredients_input)
            val ingredientText = inputField.text?.toString()?.trim() ?: ""

            if (ingredientText.isNotEmpty()) {
                // Create a new PotluckIngredient with the current user
                ingredientAdapter.addIngredient(this, ingredientText)
                inputField.text?.clear()
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Please enter an ingredient first!", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Generate AI
        btnGenerateAI.setOnClickListener {
            updatePotluckRecipes()
        }

        // Manage Participant
        btnManageParticipant.setOnClickListener {
            startActivity(Intent(this, ManageParticipants::class.java).apply {
                putExtra("potluck_id", potluckId)
                putExtra("current_user_id", currentUserId)
            })
        }

        // Upload Image
        btnUploadImage.setOnClickListener {
            showImageSelectionDialog()
        }

        // View Image
        btnViewImage.setOnClickListener {
            showUploadedImage()
        }

        // Cuisine Type
        btnCuisineType.setOnClickListener {
            showCuisineDialog()
        }

        // Toggle Preferences
        btnTogglePreferences.setOnClickListener {
            showPreferencesDialog()
        }

        // Back button click handler
        btnBack.setOnClickListener {
            finish()
        }

        // Refresh button click handler
        btnRefresh.setOnClickListener {
            refreshPotluckDetails()
        }
    }

    private fun updatePotluckRecipes() {
        // Get references to the progress bar and generate button via findViewById
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        val generateButton = findViewById<Button>(R.id.btn_generate_ai)

        // Show progress indicator and disable the button
        progressBar.visibility = View.VISIBLE
        generateButton.isEnabled = false

        // Get the complete list of ingredient names from your adapter
        val ingredientNames = ingredientAdapter.getIngredientNames()

        val jsonBody = JSONObject().apply {
            put("ingredients", JSONArray(ingredientNames))
            put("cuisine", btnCuisineType.text.toString())
            put("preferences", JSONObject().apply {
                put("prepTime", preferencesManager.getSavedPrepTime())
                put("complexity", preferencesManager.getSavedComplexity())
                put("calories", preferencesManager.getSavedCalories())
                put("nutrition", preferencesManager.getSavedNutrition())
                put("spice", preferencesManager.getSavedSpice())
                put("price", preferencesManager.getSavedPrice())
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

        // Make the network call using coroutines
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = NetworkClient.apiService.updatePotluckRecipesByAI(potluckId, requestBody)
                withContext(Dispatchers.Main) {
                    // Hide the progress bar and re-enable the button regardless of outcome
                    progressBar.visibility = View.GONE
                    generateButton.isEnabled = true

                    if (response.isSuccessful && response.body() != null) {
                        val updateResponse = response.body()!!
                        // Convert the recipe list to a JSON array using Gson
                        val recipesJson = Gson().toJson(updateResponse.recipe)
                        // Wrap the recipes JSON in an object, if desired
                        val jsonObject = JSONObject().put("recipes", JSONArray(recipesJson)).toString()

                        // Launch RecipeResultsActivity, passing the recipes JSON string as extra
                        val intent = Intent(this@PotluckDetailActivity, RecipeResultsActivity::class.java)
                        intent.putExtra("recipe", jsonObject)
                        startActivity(intent)
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), "Failed to update recipes", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    generateButton.isEnabled = true
                    Snackbar.make(findViewById(android.R.id.content), "Network error: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
                Log.e("PotluckDetailActivity", "Error updating recipes", e)
            }
        }
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf("Take a Photo", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Upload Ingredient Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        getImageFromGallery.launch(intent)
    }

    private val getImageFromGallery =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedImageUri = result.data?.data
                selectedImageUri?.let {
                    Snackbar.make(findViewById(android.R.id.content), "Image Selected!", Snackbar.LENGTH_SHORT).show()
                    sendImageToBackend(it)
                }
            }
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAMERA && resultCode == Activity.RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as? Bitmap
            imageBitmap?.let {
                // Convert bitmap to Uri
                val bytes = ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
                val path = MediaStore.Images.Media.insertImage(
                    contentResolver,
                    it,
                    "Title",
                    null
                )
                selectedImageUri = Uri.parse(path)
                Snackbar.make(findViewById(android.R.id.content), "Image Captured!", Snackbar.LENGTH_SHORT).show()
                selectedImageUri?.let { uri -> sendImageToBackend(uri) }
            }
        }
    }

    private fun sendImageToBackend(imageUri: Uri) {
        val file = File(imageUri.path ?: return) // Convert URI to File
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image",
                file.name,
                RequestBody.create("image/*".toMediaTypeOrNull(), file)
            )
            .build()

        val request = Request.Builder()
            .url("https://ec2-3-21-30-112.us-east-2.compute.amazonaws.com/recipes/AI")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Snackbar.make(findViewById(android.R.id.content), "Image Upload Failed", Snackbar.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.use { body ->
                    val jsonResponse = JSONObject(body.string())
                    val detectedIngredients = jsonResponse.getJSONArray("ingredients")
                    runOnUiThread {
                        for (i in 0 until detectedIngredients.length()) {
                            val ingName = detectedIngredients.getString(i)
                            potluckIngredients.add(PotluckIngredient(ingName, currentUser))
                        }
                        ingredientAdapter.notifyDataSetChanged()
                    }
                }
            }
        })
    }

    private fun fetchRecipesFromBackend() {
        // Convert potluckIngredients -> list of names
        val ingredientNames = potluckIngredients.map { it.name }
        val jsonBody = JSONObject().apply {
            put("ingredients", JSONArray(ingredientNames))
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://ec2-3-21-30-112.us-east-2.compute.amazonaws.com/recipes/AI")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Snackbar.make(findViewById(android.R.id.content), "Failed to fetch recipes", Snackbar.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.string()?.let { responseStr ->
                        runOnUiThread {
                            Snackbar.make(findViewById(android.R.id.content), "Recipe generation started! This will be implemented soon.", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: IOException) {
                    runOnUiThread {
                        Snackbar.make(findViewById(android.R.id.content), "Error processing response", Snackbar.LENGTH_SHORT).show()
                    }
                    Log.e("RecommendationActivity", "Error parsing response", e)
                }
            }
        })
    }

    private fun showPreferencesDialog() {
        val dialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_preferences)
            .create()

        dialog.show()

        val sliders = getSliders(dialog)
        val textViews = getTextViews(dialog)

        setInitialValues(sliders, textViews)
        setupSliderListeners(sliders, textViews)
        setupDialogButtons(dialog, sliders)
    }

    private fun getSliders(dialog: AlertDialog): Map<String, Slider?> {
        return mapOf(
            "prepTime" to dialog.findViewById(R.id.seekbar_prep_time),
            "complexity" to dialog.findViewById(R.id.seekbar_complexity),
            "calories" to dialog.findViewById(R.id.seekbar_calories),
            "nutrition" to dialog.findViewById(R.id.seekbar_nutrition),
            "spice" to dialog.findViewById(R.id.seekbar_spice),
            "price" to dialog.findViewById(R.id.seekbar_price)
        )
    }

    private fun getTextViews(dialog: AlertDialog): Map<String, TextView?> {
        return mapOf(
            "prepTime" to dialog.findViewById(R.id.text_prep_time),
            "complexity" to dialog.findViewById(R.id.text_complexity),
            "calories" to dialog.findViewById(R.id.text_calories),
            "nutrition" to dialog.findViewById(R.id.text_nutrition),
            "spice" to dialog.findViewById(R.id.text_spice),
            "price" to dialog.findViewById(R.id.text_price)
        )
    }

    private fun setInitialValues(sliders: Map<String, Slider?>, textViews: Map<String, TextView?>) {
        sliders["prepTime"]?.value = preferencesManager.getSavedPrepTime().toFloat()
        sliders["complexity"]?.value = preferencesManager.getSavedComplexity().toFloat()
        sliders["calories"]?.value = preferencesManager.getSavedCalories().toFloat()
        sliders["nutrition"]?.value = preferencesManager.getSavedNutrition().toFloat()
        sliders["spice"]?.value = preferencesManager.getSavedSpice().toFloat()
        sliders["price"]?.value = preferencesManager.getSavedPrice().toFloat()

        updateTextViews(sliders, textViews)
    }

    private fun updateTextViews(sliders: Map<String, Slider?>, textViews: Map<String, TextView?>) {
        sliders["prepTime"]?.value?.toInt()?.let { updatePrepTimeText(it, textViews["prepTime"]) }
        sliders["complexity"]?.value?.toInt()?.let { updateComplexityText(it, textViews["complexity"]) }
        sliders["calories"]?.value?.toInt()?.let { updateCaloriesText(it, textViews["calories"]) }
        sliders["nutrition"]?.value?.toInt()?.let { updateNutritionText(it, textViews["nutrition"]) }
        sliders["spice"]?.value?.toInt()?.let { updateSpiceText(it, textViews["spice"]) }
        sliders["price"]?.value?.toInt()?.let { updatePriceText(it, textViews["price"]) }
    }

    private fun setupSliderListeners(sliders: Map<String, Slider?>, textViews: Map<String, TextView?>) {
        sliders.forEach { (key, slider) ->
            slider?.addOnChangeListener { _, value, _ ->
                updateTextView(key, value.toInt(), textViews)
            }
        }
    }

    private fun updateTextView(key: String, value: Int, textViews: Map<String, TextView?>) {
        when (key) {
            "prepTime" -> updatePrepTimeText(value, textViews["prepTime"])
            "complexity" -> updateComplexityText(value, textViews["complexity"])
            "calories" -> updateCaloriesText(value, textViews["calories"])
            "nutrition" -> updateNutritionText(value, textViews["nutrition"])
            "spice" -> updateSpiceText(value, textViews["spice"])
            "price" -> updatePriceText(value, textViews["price"])
        }
    }

    private fun setupDialogButtons(dialog: AlertDialog, sliders: Map<String, Slider?>) {
        dialog.findViewById<Button>(R.id.btn_cancel_preferences)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.btn_apply_preferences)?.setOnClickListener {
            savePreferences(sliders)
            Snackbar.make(findViewById(android.R.id.content), "Preferences saved", Snackbar.LENGTH_SHORT).show()
            dialog.dismiss()
        }
    }

    private fun savePreferences(sliders: Map<String, Slider?>) {
        sliders["prepTime"]?.value?.let { preferencesManager.savePrepTime(it.toInt()) }
        sliders["complexity"]?.value?.let { preferencesManager.saveComplexity(it.toInt()) }
        sliders["calories"]?.value?.let { preferencesManager.saveCalories(it.toInt()) }
        sliders["nutrition"]?.value?.let { preferencesManager.saveNutrition(it.toInt()) }
        sliders["spice"]?.value?.let { preferencesManager.saveSpice(it.toInt()) }
        sliders["price"]?.value?.let { preferencesManager.savePrice(it.toInt()) }
    }

    private fun updatePrepTimeText(progress: Int, textView: TextView?) {
        val text = if (progress == 0) "Any" else "${progress * 15} minutes"
        textView?.text = "Selected: $text"
    }

    private fun updateComplexityText(value: Int, textView: TextView?) {
        val text = when (value) {
            0 -> "Don't care"
            1, 2 -> "Very Easy"
            3, 4 -> "Easy"
            5, 6 -> "Moderate"
            7, 8 -> "Challenging"
            else -> "Complex"
        }
        textView?.text = "Selected: $text"
    }

    private fun updateCaloriesText(progress: Int, textView: TextView?) {
        val text = if (progress == 0) "Any" else "${progress * 150} calories"
        textView?.text = "Selected: $text"
    }

    private fun updateNutritionText(value: Int, textView: TextView?) {
        // Reusing the same logic from your code, adjust if you want actual "nutrition" labels
        val text = when (value) {
            0 -> "Don't care"
            1, 2 -> "Very Low"
            3, 4 -> "Low"
            5, 6 -> "Medium"
            7, 8 -> "High"
            else -> "Very High"
        }
        textView?.text = "Selected: $text"
    }

    private fun updateSpiceText(value: Int, textView: TextView?) {
        val text = when (value) {
            0 -> "Don't care"
            1 -> "No spice"
            2, 3 -> "Mild spice"
            4, 5 -> "Low spice"
            6, 7 -> "Medium spice"
            8, 9 -> "High spice"
            else -> "Extreme spice"
        }
        textView?.text = "Selected: $text"
    }

    private fun updatePriceText(value: Int, textView: TextView?) {
        val text = when (value) {
            0 -> "Don't care"
            10 -> "$90+"
            else -> "$${value * 10 - 10} - $${value * 10}"
        }
        textView?.text = "Selected: $text"
    }

    private fun showCuisineDialog() {
        val dialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_cuisine_search)
            .create()

        dialog.show()

        val searchInput = dialog.findViewById<EditText>(R.id.search_cuisine)
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.recycler_cuisines)

        val cuisineTypes = resources.getStringArray(R.array.cuisine_types).toMutableList()
        var filteredCuisines = cuisineTypes.toMutableList()
        var selectedCuisine = ""

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
                val textView: TextView = view.findViewById<TextView>(android.R.id.text1).apply {
                    setTextColor(resources.getColor(R.color.black, null))
                    textSize = 16f
                    setPadding(32, 16, 32, 16)
                }
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(android.R.layout.simple_list_item_1, parent, false)
                return ViewHolder(view)
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val cuisine = filteredCuisines[position]
                (holder as ViewHolder).textView.apply {
                    text = cuisine
                    isSelected = cuisine == selectedCuisine
                    setBackgroundColor(
                        if (isSelected) resources.getColor(R.color.primary_light, theme)
                        else android.graphics.Color.TRANSPARENT
                    )
                    setOnClickListener {
                        selectedCuisine = cuisine
                        notifyDataSetChanged()
                    }
                }
            }

            override fun getItemCount() = filteredCuisines.size
        }

        recyclerView?.layoutManager = LinearLayoutManager(this)
        recyclerView?.adapter = adapter

        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filteredCuisines.clear()
                val searchText = s.toString().lowercase()
                if (searchText.isEmpty()) {
                    filteredCuisines.addAll(cuisineTypes)
                } else {
                    filteredCuisines.addAll(cuisineTypes.filter { cuisine ->
                        val cuisineLower = cuisine.lowercase()
                        when {
                            cuisineLower.contains(searchText) -> true
                            // More lenient threshold for fuzzy matching
                            searchText.length >= 2 && calculateSimilarity(cuisineLower, searchText) > 0.4 -> true
                            // Special handling for prefix matches with more lenient threshold
                            cuisineLower.split(" ").any { word ->
                                word.startsWith(searchText) || 
                                (searchText.length >= 2 && calculateSimilarity(word, searchText) > 0.5)
                            } -> true
                            else -> false
                        }
                    })
                }
                adapter.notifyDataSetChanged()
            }
        })

        dialog.findViewById<Button>(R.id.btn_cancel_cuisine)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<Button>(R.id.btn_apply_cuisine)?.setOnClickListener {
            if (selectedCuisine.isNotEmpty()) {
                btnCuisineType.text = selectedCuisine
            }
            dialog.dismiss()
        }
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        
        val maxLength = maxOf(s1.length, s2.length)
        val distance = calculateLevenshteinDistance(s1, s2).toDouble()
        
        // Normalize the score between 0 and 1, where 1 means exact match
        return 1.0 - (distance / maxLength)
    }

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        // Initialize first row and column
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        // Fill in the rest of the matrix
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                dp[i][j] = if (s1[i-1] == s2[j-1]) {
                    dp[i-1][j-1]  // No operation needed
                } else {
                    minOf(
                        dp[i-1][j] + 1,    // deletion
                        dp[i][j-1] + 1,    // insertion
                        dp[i-1][j-1] + 1   // substitution
                    )
                }
            }
        }
        return dp[s1.length][s2.length]
    }

    private fun showUploadedImage() {
        if (selectedImageUri != null) {
            val dialog = AlertDialog.Builder(this)
                .setView(ImageView(this).apply {
                    setImageURI(selectedImageUri)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                .setPositiveButton("Close", null)
                .create()
            dialog.show()
        } else {
            Snackbar.make(findViewById(android.R.id.content), "No image has been uploaded", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel() // Cancel any existing job
        autoRefreshJob = lifecycleScope.launch(Dispatchers.Main + SupervisorJob()) {
            while (true) {
                try {
                    // Quietly refresh without showing loading
                    ingredientAdapter.fetchIngredientsFromServer()
                } catch (e: IOException) {
                    Log.e("PotluckDetail", "Auto-refresh error: ${e.message}")
                }
                delay(AUTO_REFRESH_INTERVAL)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        autoRefreshJob?.cancel()
    }

    private fun refreshPotluckDetails() {
        lifecycleScope.launch {
            try {
                showLoading()
                ingredientAdapter.fetchIngredientsFromServer()
                Snackbar.make(findViewById(android.R.id.content), "Potluck details refreshed", Snackbar.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Snackbar.make(findViewById(android.R.id.content), "Error refreshing: ${e.message}", Snackbar.LENGTH_SHORT).show()
            } finally {
                hideLoading()
            }
        }
    }

    private fun deletePotluck() {
        AlertDialog.Builder(this)
            .setTitle("Delete Potluck?")
            .setMessage("Are you sure you want to permanently delete $potluckName?")
            .setPositiveButton("Yes") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val response = NetworkClient.apiService.deletePotluck(potluckId)
                        if (response.isSuccessful) {
                            Snackbar.make(findViewById(android.R.id.content), "Potluck deleted!", Snackbar.LENGTH_SHORT).show()
                            finish() // Close activity after deleting
                        } else {
                            Snackbar.make(findViewById(android.R.id.content), "Failed to delete potluck", Snackbar.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        Snackbar.make(findViewById(android.R.id.content), "Network error", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun leavePotluck() {
        AlertDialog.Builder(this)
            .setTitle("Leave Potluck?")
            .setMessage("Are you sure you want to leave $potluckName?")
            .setPositiveButton("Yes") { _, _ ->
                val requestBody = hashMapOf("participants" to listOf(currentUserId))

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        Log.d("LeavePotluck", "Request body: $requestBody")
                        val response = NetworkClient.apiService.removePotluckParticipant(potluckId, requestBody)

                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful && response.body() != null) {
                                Snackbar.make(findViewById(android.R.id.content), "You have left the potluck!", Snackbar.LENGTH_SHORT).show()
                                finish() // Close activity after leaving
                            } else {
                                Snackbar.make(findViewById(android.R.id.content), "Failed to leave potluck", Snackbar.LENGTH_SHORT).show()
                                Log.e("LeavePotluck", "Response error: ${response.errorBody()?.string()}")
                            }
                        }
                    } catch (e: IOException) {
                        withContext(Dispatchers.Main) {
                            Snackbar.make(findViewById(android.R.id.content), "Network error: ${e.message}", Snackbar.LENGTH_SHORT).show()
                            Log.e("LeavePotluck", "Error leaving potluck", e)
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    companion object {
        private const val REQUEST_CAMERA = 100
    }
}
