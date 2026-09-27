import android.content.Context
import android.util.Log
import com.pranav.atmini.feature.voice.wake.WakeSignature
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WakeSignatureStore(private val context: Context) {

    private val fileName = "wake_signature.json"

    fun save(signature: WakeSignature) {

        val json = JSONObject().apply {
            put("energy", JSONArray(signature.energyPattern))
            put("zcr", JSONArray(signature.zcrPattern))
            put("delta", JSONArray(signature.energyDeltaPattern))
        }

        val file = File(context.filesDir, fileName)
        file.writeText(json.toString())

        Log.d("Atmini-SIG", "SAVE SUCCESS → ${file.absolutePath}")
    }

    fun load(): WakeSignature? {
        val file = File(context.filesDir, fileName)
        val jsonString = if (file.exists()) {
            file.readText()
        } else {
            try {
                context.assets.open("core/l0/memory/wake_signature.json").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e("Atmini-SIG", "Failed to load wake signature from assets", e)
                return null
            }
        }

        val json = JSONObject(jsonString)
        return WakeSignature(
            energyPattern = json.optJSONArray("energy")?.toListInt() ?: emptyList(),
            zcrPattern = json.optJSONArray("zcr")?.toListInt() ?: emptyList(),
            energyDeltaPattern = json.optJSONArray("delta")?.toListInt() ?: emptyList(),
            delta_normalized = json.optJSONArray("delta_normalized")?.toListDouble() ?: emptyList()
        )
    }
}


fun JSONArray.toListInt(): List<Int> {
    val list = mutableListOf<Int>()
    for (i in 0 until length()) {
        list.add(getInt(i))
    }
    return list
}

fun JSONArray.toListDouble(): List<Double> {
    val list = mutableListOf<Double>()
    for (i in 0 until length()) {
        list.add(getDouble(i))
    }
    return list
}