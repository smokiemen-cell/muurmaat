package nl.muurmaat.ar

import android.os.Bundle
import android.view.View
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest
import android.util.Patterns
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {
    private lateinit var arFragment: ArFragment
    private lateinit var arContainer: View
    private lateinit var status: TextView
    private lateinit var cameraScreen: View
    private lateinit var manualPanel: View
    private lateinit var menuScreen: View
    private lateinit var savedScreen: View
    private lateinit var accountScreen: View
    private lateinit var loginScreen: View
    private lateinit var accountDetailsScreen: View
    private lateinit var forgotScreen: View
    private lateinit var welcomeText: TextView
    private lateinit var savedMeasurements: LinearLayout
    private val savedPrefsName = "voegmaatje_measurements"
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private lateinit var result: TextView
    private var mode = MeasureMode.LENGTH
    private var firstPoint: Vector3? = null
    private var length: Float? = null
    private var height: Float? = null
    private val markerNodes = mutableListOf<AnchorNode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arContainer = findViewById(R.id.ar_container)
        manualPanel = findViewById(R.id.manual_panel)
        menuScreen = findViewById(R.id.menu_screen)
        savedScreen = findViewById(R.id.saved_screen)
        accountScreen = findViewById(R.id.account_screen)
        loginScreen = findViewById(R.id.login_screen)
        accountDetailsScreen = findViewById(R.id.account_details_screen)
        forgotScreen = findViewById(R.id.forgot_screen)
        status = findViewById(R.id.status)
        welcomeText = findViewById(R.id.welcome_text)
        savedMeasurements = findViewById(R.id.saved_measurements)
        updateSavedMeasurements()
        cameraScreen = findViewById(R.id.camera_screen)
        result = findViewById(R.id.result)
        val handHint = findViewById<TextView>(R.id.camera_hand)
        arContainer.visibility = View.GONE
        status.text = "Vul je muur in of kies camera"

        findViewById<Button>(R.id.length_button).setOnClickListener { selectMode(MeasureMode.LENGTH) }
        findViewById<Button>(R.id.height_button).setOnClickListener { selectMode(MeasureMode.HEIGHT) }
        findViewById<Button>(R.id.reset_button).setOnClickListener { resetMeasurement() }
        findViewById<Button>(R.id.manual_button).setOnClickListener { readManualInput() }
        findViewById<Button>(R.id.save_button).setOnClickListener { saveMeasurement() }
        findViewById<Button>(R.id.menu_button).setOnClickListener { openMenu() }
        findViewById<Button>(R.id.start_button).setOnClickListener { showStartPage() }
        findViewById<Button>(R.id.saved_button).setOnClickListener { showSavedPage() }
        findViewById<Button>(R.id.saved_back_button).setOnClickListener { showStartPage() }
        findViewById<Button>(R.id.create_account_button).setOnClickListener { createAccount() }
        findViewById<Button>(R.id.open_create_account_button).setOnClickListener { showCreateAccount() }
        findViewById<Button>(R.id.back_to_login_from_create_button).setOnClickListener { showAccountScreen() }
        findViewById<Button>(R.id.login_button).setOnClickListener { login() }
        findViewById<Button>(R.id.forgot_password_button).setOnClickListener { showForgotPassword() }
        findViewById<Button>(R.id.request_reset_button).setOnClickListener { requestPasswordReset() }
        findViewById<Button>(R.id.back_to_login_button).setOnClickListener { showAccountScreen() }
        findViewById<Button>(R.id.logout_button).setOnClickListener { logout() }
        findViewById<Button>(R.id.account_details_button).setOnClickListener { showAccountDetails() }
        findViewById<Button>(R.id.account_details_back).setOnClickListener { showStartPage() }
        findViewById<Button>(R.id.update_account_button).setOnClickListener { updateAccountDetails() }
        findViewById<CheckBox>(R.id.show_login_password).setOnCheckedChangeListener { _, checked -> togglePassword(R.id.login_password_input, checked) }
        findViewById<CheckBox>(R.id.show_account_password).setOnCheckedChangeListener { _, checked -> togglePassword(R.id.password_input, checked) }
        findViewById<CheckBox>(R.id.show_change_password).setOnCheckedChangeListener { _, checked -> togglePassword(R.id.change_password_input, checked) }
        findViewById<Button>(R.id.average_price_button).setOnClickListener {
            findViewById<EditText>(R.id.manual_price).setText("21,95")
            status.text = "Richtprijs ingevuld: € 21,95 per zak"
        }
        findViewById<Button>(R.id.camera_button).setOnClickListener { button ->
            openArCamera()
            arContainer.visibility = View.VISIBLE
            cameraScreen.visibility = View.VISIBLE
            findViewById<View>(R.id.manual_panel).visibility = View.GONE
            handHint.visibility = View.VISIBLE
            status.text = "Automatische meting: tik beginpunt en daarna eindpunt"
            handHint.startAnimation(handAnimation())
        }
        findViewById<Button>(R.id.close_camera_button).setOnClickListener { closeCameraScreen() }

        val accountPreferences = getSharedPreferences("voegmaatje_account", MODE_PRIVATE)
        val rememberUntil = accountPreferences.getLong("remember_until", 0L)
        if (firebaseAuth.currentUser?.isEmailVerified == true && accountPreferences.getBoolean("logged_in", false) && rememberUntil > System.currentTimeMillis()) {
            showMainForUser(accountPreferences.getString("username", "") ?: "")
        } else {
            showAccountScreen()
        }

    }

    private fun openArCamera() {
        if (!::arFragment.isInitialized) {
            arFragment = ArFragment()
            supportFragmentManager.beginTransaction().add(R.id.ar_container, arFragment, "ar_fragment").commitNow()
            arFragment.planeDiscoveryController?.hide()
            arFragment.setOnTapArPlaneListener { hitResult, plane, _ -> onPlaneTap(hitResult, plane) }
        }
    }

    private fun onPlaneTap(hitResult: HitResult, plane: Plane) {
        val point = Vector3(hitResult.hitPose.tx(), hitResult.hitPose.ty(), hitResult.hitPose.tz())
        addMarker(hitResult)
        if (firstPoint == null) {
            firstPoint = point
            status.text = "Beginpunt gezet. Loop langs de muur en tik het eindpunt aan."
        } else {
            val distance = distance(firstPoint!!, point)
            if (distance < 0.05f) {
                status.text = "De punten liggen te dicht bij elkaar. Probeer opnieuw."
                return
            }
            if (mode == MeasureMode.LENGTH) length = distance else height = distance
            firstPoint = null
            status.text = "${mode.label} gemeten. Kies de andere maat of gebruik Invoer."
            updateResult()
        }
    }

    private fun addMarker(hitResult: HitResult) {
        val anchorNode = AnchorNode(hitResult.createAnchor())
        anchorNode.setParent(arFragment.arSceneView.scene)
        markerNodes += anchorNode
        TransformableNode(arFragment.transformationSystem).also {
            it.setParent(anchorNode)
            MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.rgb(201, 111, 74))).thenAccept { material ->
                it.renderable = ShapeFactory.makeSphere(0.035f, Vector3.zero(), material)
            }
        }
    }

    private fun distance(first: Vector3, second: Vector3): Float {
        val x = second.x - first.x
        val y = second.y - first.y
        val z = second.z - first.z
        return sqrt(x * x + y * y + z * z)
    }

    private fun selectMode(newMode: MeasureMode) {
        mode = newMode
        firstPoint = null
        status.text = "${mode.label}: tik een beginpunt aan"
    }

    private fun resetMeasurement() {
        markerNodes.forEach { marker ->
            marker.anchor?.detach()
            arFragment.arSceneView.scene.removeChild(marker)
        }
        markerNodes.clear()
        closeCameraScreen()
        firstPoint = null
        length = null
        height = null
        mode = MeasureMode.LENGTH
        findViewById<EditText>(R.id.manual_length).text.clear()
        findViewById<EditText>(R.id.manual_height).text.clear()
        findViewById<EditText>(R.id.manual_price).setText("21,95")
        findViewById<EditText>(R.id.wall_name).text.clear()
        showStartPage()
        status.text = "Scan een vlak of vul lengte en hoogte handmatig in"
        updateResult()
    }

    private fun closeCameraScreen() {
        arContainer.visibility = View.GONE
        cameraScreen.visibility = View.GONE
        manualPanel.visibility = View.VISIBLE
        findViewById<View>(R.id.camera_hand).visibility = View.GONE
        findViewById<View>(R.id.camera_hand).clearAnimation()
        status.text = "Vul je muur in of kies camera"
    }

    private fun openMenu() {
        manualPanel.visibility = View.GONE
        menuScreen.visibility = View.VISIBLE
        savedScreen.visibility = View.GONE
    }

    private fun showStartPage() {
        manualPanel.visibility = View.VISIBLE
        menuScreen.visibility = View.GONE
        savedScreen.visibility = View.GONE
        accountDetailsScreen.visibility = View.GONE
    }

    private fun showSavedPage() {
        updateSavedMeasurements()
        manualPanel.visibility = View.GONE
        menuScreen.visibility = View.GONE
        savedScreen.visibility = View.VISIBLE
        accountDetailsScreen.visibility = View.GONE
    }

    private fun showAccountDetails() {
        val user = firebaseAuth.currentUser ?: return
        val username = getSharedPreferences("voegmaatje_account", MODE_PRIVATE).getString("username", "")
        findViewById<TextView>(R.id.account_details_current).text = "Gebruikersnaam: $username\nE-mailadres: ${user.email.orEmpty()}"
        manualPanel.visibility = View.GONE
        menuScreen.visibility = View.GONE
        savedScreen.visibility = View.GONE
        accountDetailsScreen.visibility = View.VISIBLE
    }

    private fun updateAccountDetails() {
        val user = firebaseAuth.currentUser ?: return
        val email = findViewById<EditText>(R.id.change_email_input).text.toString().trim()
        val password = findViewById<EditText>(R.id.change_password_input).text.toString()
        val status = findViewById<TextView>(R.id.account_details_status)
        if (email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            status.text = "Vul een geldig e-mailadres in"
            return
        }
        if (password.isNotBlank() && password.length < 4) {
            status.text = "Wachtwoord moet minimaal 4 tekens hebben"
            return
        }
        val updatePassword = {
            if (password.isBlank()) {
                status.text = "Accountgegevens opgeslagen"
            } else {
                user.updatePassword(password).addOnCompleteListener { task ->
                    status.text = if (task.isSuccessful) "Accountgegevens opgeslagen" else "Wachtwoord wijzigen mislukt: log opnieuw in"
                }
            }
        }
        if (email.isBlank() || email == user.email) {
            updatePassword()
        } else {
            user.updateEmail(email).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    getSharedPreferences("voegmaatje_account", MODE_PRIVATE).edit().putString("email", email).apply()
                    updatePassword()
                } else {
                    status.text = "E-mailadres wijzigen mislukt: log opnieuw in"
                }
            }
        }
    }

    private fun togglePassword(fieldId: Int, visible: Boolean) {
        val field = findViewById<EditText>(fieldId)
        field.transformationMethod = if (visible) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
        field.setSelection(field.text.length)
    }

    private fun createAccount() {
        val username = findViewById<EditText>(R.id.username_input).text.toString().trim()
        val password = findViewById<EditText>(R.id.password_input).text.toString()
        val email = findViewById<EditText>(R.id.email_input).text.toString().trim()
        val error = findViewById<TextView>(R.id.create_account_error)
        if (username.length < 2) {
            error.text = "Gebruikersnaam moet minimaal 2 tekens hebben"
            return
        }
        if (password.length < 4) {
            error.text = "Wachtwoord moet minimaal 4 tekens hebben"
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            error.text = "Vul een geldig e-mailadres in"
            return
        }
        checkUsername(username) { available, suggestions ->
            if (!available) {
                error.text = "Gebruikersnaam bestaat al. Beschikbaar: ${suggestions.joinToString(", ")}" 
                return@checkUsername
            }
            firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                    firebaseAuth.currentUser?.sendEmailVerification()
                    firestore.collection("usernames").document(username.lowercase()).set(mapOf("username" to username, "uid" to firebaseAuth.currentUser?.uid))
                    getSharedPreferences("voegmaatje_account", MODE_PRIVATE).edit()
                        .putString("username", username)
                        .putString("email", email)
                        .apply()
                    error.text = "Account gemaakt. Controleer je e-mail en log daarna in."
                } else {
                    val message = task.exception?.message.orEmpty()
                    error.text = if (message.contains("already", ignoreCase = true)) "Dit e-mailadres bestaat al. Gebruik Inloggen." else task.exception?.localizedMessage ?: "Account maken is mislukt"
                }
            }
        }
    }

    private fun checkUsername(username: String, callback: (Boolean, List<String>) -> Unit) {
        val base = username.lowercase()
        firestore.collection("usernames").document(base).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                callback(true, emptyList())
            } else {
                val candidates = listOf("${username}01", "${username}123", "${username}24", "${username}app")
                checkCandidates(candidates, 0, mutableListOf(), callback)
            }
        }
    }

    private fun checkCandidates(candidates: List<String>, index: Int, available: MutableList<String>, callback: (Boolean, List<String>) -> Unit) {
        if (index >= candidates.size) {
            callback(false, available)
            return
        }
        firestore.collection("usernames").document(candidates[index].lowercase()).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() && available.size < 3) available.add(candidates[index])
            checkCandidates(candidates, index + 1, available, callback)
        }
    }

    private fun login() {
        val username = findViewById<EditText>(R.id.login_username_input).text.toString().trim()
        val password = findViewById<EditText>(R.id.login_password_input).text.toString()
        val email = getSharedPreferences("voegmaatje_account", MODE_PRIVATE).getString("email", "").orEmpty()
        val remember = findViewById<CheckBox>(R.id.remember_checkbox).isChecked
        val error = findViewById<TextView>(R.id.account_error)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            error.text = "Dit account heeft geen geldig e-mailadres. Maak het account opnieuw aan."
            return
        }
        val savedUsername = getSharedPreferences("voegmaatje_account", MODE_PRIVATE).getString("username", username) ?: username
        firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                error.text = task.exception?.localizedMessage ?: "Inloggen is mislukt"
            } else if (firebaseAuth.currentUser?.isEmailVerified != true) {
                firebaseAuth.signOut()
                error.text = "Bevestig eerst je e-mailadres via de ontvangen e-mail"
            } else {
                error.text = ""
                val rememberUntil = if (remember) System.currentTimeMillis() + 180L * 24L * 60L * 60L * 1000L else 0L
                getSharedPreferences("voegmaatje_account", MODE_PRIVATE).edit().putLong("remember_until", rememberUntil).apply()
                showMainForUser(savedUsername)
            }
        }
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun showMainForUser(username: String) {
        welcomeText.text = "Welkom, $username"
        getSharedPreferences("voegmaatje_account", MODE_PRIVATE).edit().putBoolean("logged_in", true).apply()
        loginScreen.visibility = View.GONE
        accountScreen.visibility = View.GONE
        showStartPage()
    }

    private fun showAccountScreen() {
        loginScreen.visibility = View.VISIBLE
        accountScreen.visibility = View.GONE
        forgotScreen.visibility = View.GONE
        manualPanel.visibility = View.GONE
        menuScreen.visibility = View.GONE
        savedScreen.visibility = View.GONE
    }

    private fun showCreateAccount() {
        loginScreen.visibility = View.GONE
        accountScreen.visibility = View.VISIBLE
        forgotScreen.visibility = View.GONE
    }

    private fun showForgotPassword() {
        loginScreen.visibility = View.GONE
        accountScreen.visibility = View.GONE
        forgotScreen.visibility = View.VISIBLE
    }

    private fun requestPasswordReset() {
        val email = findViewById<EditText>(R.id.forgot_email_input).text.toString().trim()
        val message = findViewById<TextView>(R.id.forgot_status)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            message.text = "Vul een geldig e-mailadres in."
        } else {
            firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                message.text = if (task.isSuccessful) "Resetlink verzonden. Controleer je e-mail." else "Resetlink kon niet worden verzonden."
            }
        }
    }

    private fun logout() {
        getSharedPreferences("voegmaatje_account", MODE_PRIVATE).edit().putBoolean("logged_in", false).putLong("remember_until", 0L).apply()
        firebaseAuth.signOut()
        findViewById<EditText>(R.id.username_input).text.clear()
        findViewById<EditText>(R.id.password_input).text.clear()
        findViewById<EditText>(R.id.login_username_input).text.clear()
        findViewById<EditText>(R.id.login_password_input).text.clear()
        manualPanel.visibility = View.GONE
        menuScreen.visibility = View.GONE
        savedScreen.visibility = View.GONE
        showAccountScreen()
    }

    private fun readManualInput() {
        val lengthInput = findViewById<EditText>(R.id.manual_length).text.toString().replace(',', '.').toFloatOrNull()
        val heightInput = findViewById<EditText>(R.id.manual_height).text.toString().replace(',', '.').toFloatOrNull()
        if (lengthInput == null || heightInput == null || lengthInput <= 0f || heightInput <= 0f) {
            status.text = "Vul lengte en hoogte groter dan 0 in"
            return
        }
        length = lengthInput
        height = heightInput
        firstPoint = null
        status.text = "Handmatige maten opgeslagen"
        updateResult()
    }

    private fun saveMeasurement() {
        val name = findViewById<EditText>(R.id.wall_name).text.toString().trim()
        if (name.isBlank() || length == null || height == null) {
            status.text = "Vul een naam en geldige maten in voordat je opslaat"
            return
        }
        val price = findViewById<EditText>(R.id.manual_price).text.toString().replace(',', '.').toFloatOrNull() ?: 21.95f
        val bags = kotlin.math.ceil(length!! * height!! / 2.5f).toInt()
        val record = listOf(name.replace('|', '/'), "%.2f".format(length!!), "%.2f".format(height!!), bags, "%.2f".format(bags * price)).joinToString("|")
        val preferences = getSharedPreferences(savedPrefsName, MODE_PRIVATE)
        val records = preferences.getStringSet("records", emptySet()).orEmpty().toMutableSet()
        records.add(record)
        preferences.edit().putStringSet("records", records).apply()
        findViewById<EditText>(R.id.wall_name).text.clear()
        updateSavedMeasurements()
        Toast.makeText(this, "Meting opgeslagen", Toast.LENGTH_SHORT).show()
    }

    private fun updateSavedMeasurements() {
        val records = getSharedPreferences(savedPrefsName, MODE_PRIVATE).getStringSet("records", emptySet()).orEmpty()
        savedMeasurements.removeAllViews()
        if (records.isEmpty()) {
            savedMeasurements.addView(TextView(this).apply {
                text = "Nog geen metingen opgeslagen"
                textSize = 14f
                setTextColor(android.graphics.Color.rgb(23, 63, 58))
            })
            return
        }
        records.sorted().forEach { record ->
            val parts = record.split('|')
            if (parts.size != 5) return@forEach
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            row.addView(TextView(this).apply {
                text = "${parts[0]} - ${parts[1]} x ${parts[2]} m | ${parts[3]} zakken | € ${parts[4]}"
                textSize = 14f
                setTextColor(android.graphics.Color.rgb(23, 63, 58))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "Verwijder"
                setOnClickListener {
                    val updated = records.toMutableSet().apply { remove(record) }
                    getSharedPreferences(savedPrefsName, MODE_PRIVATE).edit().putStringSet("records", updated).apply()
                    updateSavedMeasurements()
                }
            })
            savedMeasurements.addView(row)
        }
    }

    private fun updateResult() {
        val lengthText = length?.let { "%.2f m".format(it) } ?: "-"
        val heightText = height?.let { "%.2f m".format(it) } ?: "-"
        val bagsText = if (length != null && height != null) "%.0f".format(kotlin.math.ceil(length!! * height!! / 2.5f)) else "-"
        val price = findViewById<EditText>(R.id.manual_price).text.toString().replace(',', '.').toFloatOrNull() ?: 8.5f
        val total = if (bagsText != "-") "€ %.2f".format(bagsText.toFloat() * price) else "-"
        val area = if (length != null && height != null) "%.2f m²".format(length!! * height!!) else "-"
        result.text = "Oppervlakte: $area\nZakken: $bagsText\nPrijs per zak: € %.2f\nTotaalprijs: $total".format(price)
    }

    private fun handAnimation(): Animation = TranslateAnimation(0f, 0f, 0f, 28f).apply {
        duration = 700
        repeatMode = Animation.REVERSE
        repeatCount = Animation.INFINITE
        interpolator = AccelerateDecelerateInterpolator()
    }

    private enum class MeasureMode(val label: String) { LENGTH("Lengte"), HEIGHT("Hoogte") }
}
