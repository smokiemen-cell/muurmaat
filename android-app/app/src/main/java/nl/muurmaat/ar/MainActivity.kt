package nl.muurmaat.ar

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
    private lateinit var status: TextView
    private lateinit var result: TextView
    private var mode = MeasureMode.LENGTH
    private var firstPoint: Vector3? = null
    private var length: Float? = null
    private var height: Float? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment
        status = findViewById(R.id.status)
        result = findViewById(R.id.result)

        findViewById<Button>(R.id.length_button).setOnClickListener { selectMode(MeasureMode.LENGTH) }
        findViewById<Button>(R.id.height_button).setOnClickListener { selectMode(MeasureMode.HEIGHT) }
        findViewById<Button>(R.id.reset_button).setOnClickListener { resetMeasurement() }

        arFragment.setOnTapArPlaneListener { hitResult, plane, _ -> onPlaneTap(hitResult, plane) }
    }

    private fun onPlaneTap(hitResult: HitResult, plane: Plane) {
        if (plane.type != Plane.Type.VERTICAL) {
            status.text = "Richt de camera op een muur en tik op het vlak"
            return
        }
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
            status.text = "${mode.label} gemeten. Kies de andere maat."
            updateResult()
        }
    }

    private fun addMarker(hitResult: HitResult) {
        val anchorNode = AnchorNode(hitResult.createAnchor())
        anchorNode.setParent(arFragment.arSceneView.scene)
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
        arFragment.arSceneView.scene.removeChild(arFragment.arSceneView.scene.children.firstOrNull())
        firstPoint = null
        length = null
        height = null
        status.text = "Kies lengte en tik een beginpunt aan"
        updateResult()
    }

    private fun updateResult() {
        val lengthText = length?.let { "%.2f m".format(it) } ?: "-"
        val heightText = height?.let { "%.2f m".format(it) } ?: "-"
        result.text = "Lengte: $lengthText   Hoogte: $heightText"
    }

    private enum class MeasureMode(val label: String) { LENGTH("Lengte"), HEIGHT("Hoogte") }
}
