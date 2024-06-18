package com.example.geminiproject

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.geminiproject.databinding.ActivityMainBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var bitmap: Bitmap
    private lateinit var imageUri: Uri

    lateinit var providesRealTimeDatabaseInstance: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        providesRealTimeDatabaseInstance = FirebaseDatabase.getInstance().getReference("Product Information")

        binding.save.setOnClickListener{
            val product = setProductRequest()
            dbCreation(product)
        }

        binding.predButton.setOnClickListener {
            binding.predimage.setImageBitmap(null)
            binding.predimage.setBackgroundResource(R.drawable.bg_img)
            binding.predimage.rotation = 0f
            binding.productName.text = "Product name : "
            binding.description.text = "Description : "
            binding.color.text = "Colour : "
            binding.pattern.text = "Pattern : "
            binding.save.isVisible = false
            binding.predButton.text = "Loading...."
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                imageUri = createImageUri()!!
                imageUri.let { uri ->
                    contract.launch(uri)
                }
            } else {
                requestPermission.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    private fun dbCreation(Product: Details) {

        val product = Details(Product.product, Product.description, Product.colour, Product.pattern, Product.uniqueId)
        providesRealTimeDatabaseInstance.child(Product.uniqueId!!).setValue(product).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this@MainActivity,"Stored the atrributes in Firebase Successfully!",Toast.LENGTH_LONG).show()
                binding.predimage.setImageBitmap(null)
                binding.predimage.setBackgroundResource(R.drawable.bg_img)
                binding.predimage.rotation = 0f
                binding.productName.text = "Product name : "
                binding.description.text = "Description : "
                binding.color.text = "Colour : "
                binding.pattern.text = "Pattern : "
                binding.save.isVisible = false
            } else {
                Toast.makeText(this@MainActivity,"Unable to store the attributes in Firebase!",Toast.LENGTH_LONG).show()
            }

        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            imageUri = createImageUri()!!
            imageUri.let { uri ->
                contract.launch(uri)
            }
        } else {
            Toast.makeText(this, "Permission Denied!! Try Again!!", Toast.LENGTH_SHORT).show()
        }
    }

    private val contract = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            bitmap = uriToBitmap(imageUri)
            binding.predimage.setImageBitmap(bitmap)
            binding.predimage.rotation = 90f
            binding.predimage.background = null
            processImage(bitmap)
        } else {
            Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createImageUri(): Uri? {
        val image = File(applicationContext.filesDir, "camera_photo_gemini.png")
        return FileProvider.getUriForFile(
            applicationContext,
            "com.example.geminiproject.fileProvider",
            image
        )
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        val contentResolver = contentResolver
        val inputStream = contentResolver.openInputStream(uri)
        val bitmaps = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        return bitmaps
    }

    private fun processImage(image: Bitmap) {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "AIzaSyA9Vsb118mBSKcOiUKzqHzt8K-kW1T_LfE"
        )

        val inputContent = content {
            image(image)
            text("Scan the image and provide 1)Product Name, 2)Description, 3)Colour and 4) Pattern. Try to give the details of this four parameters in points.")
        }

        lifecycleScope.launch {
            try {
                val response = generativeModel.generateContent(inputContent)
                parseResponse(response.text.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Failed to generate content", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseResponse(response: String) {
        val lines = response.split("\n")
        var productName = ""
        var description = ""
        var color = ""
        var pattern = ""

        for (line in lines) {
            when {
                line.contains("Product Name:", true) -> productName = "Product name : "+line.substringAfter("Product Name:").trim().trimStart('*')
                line.contains("Description:", true) -> description = "Description : "+line.substringAfter("Description:").trim().trimStart('*')
                line.contains("Colour:", true) -> color = "Colour : "+line.substringAfter("Colour:").trim().trimStart('*')
                line.contains("Pattern:", true) -> pattern = "Pattern : "+line.substringAfter("Pattern:").trim().trimStart('*')
            }
        }

        binding.productName.text = productName
        binding.description.text = description
        binding.color.text = color
        binding.pattern.text = pattern
        binding.predButton.text = "Scan Image"
        binding.save.isVisible = true
    }

    private fun setProductRequest() : Details{
        val Product = binding.productName.text.toString()
        val Description = binding.description.text.toString()
        val Colour = binding.color.text.toString()
        val Pattern = binding.pattern.text.toString()
        val UniqueId = providesRealTimeDatabaseInstance?.push()?.key
        return Details(product = Product, description = Description, colour = Colour, pattern = Pattern, uniqueId = UniqueId)
    }
}
