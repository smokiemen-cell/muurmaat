package nl.muurmaat.ar

import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var savedMeasurements: TextView
    private val savedPrefsName = "voegmaatje_measurements"
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
        arFragment = ArFragment()
        supportFragmentManager.beginTransaction().add(R.id.ar_container, arFragment, "ar_fragment").commitNow()
        arFragment.planeDiscoveryController?.hide()
        status = findViewById(R.id.status)
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
        findViewById<Button>(R.id.average_price_button).setOnClickListener {
            findViewById<EditText>(R.id.manual_price).setText("21,95")
            status.text = "Richtprijs ingevuld: € 21,95 per zak"
        }
        findViewById<Button>(R.id.camera_button).setOnClickListener { button ->
            arContainer.visibility = View.VISIBLE
            cameraScreen.visibility = View.VISIBLE
            findViewById<View>(R.id.manual_panel).visibility = View.GONE
            handHint.visibility = View.VISIBLE
            status.text = "Automatische meting: tik beginpunt en daarna eindpunt"
            handHint.startAnimation(handAnimation())
        }
        findViewById<Button>(R.id.close_camera_button).setOnClickListener { closeCameraScreen() }

        arFragment.setOnTapArPlaneListener { hitResult, plane, _ -> onPlaneTap(hitResult, plane) }
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
    }

    private fun showSavedPage() {
        updateSavedMeasurements()
        manualPanel.visibility = View.GONE
        menuScreen.visibility = View.GONE
        savedScreen.visibility = View.VISIBLE
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
        savedMeasurements.text = if (records.isEmpty()) {
            "Nog geen metingen opgeslagen"
        } else {
            records.mapNotNull { record ->
                val parts = record.split('|')
                if (parts.size == 5) "${parts[0]} - ${parts[1]} x ${parts[2]} m | ${parts[3]} zakken | € ${parts[4]}" else null
            }.sorted().joinToString("\n")
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
