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
        if (!file.exists()) return null

        val json = JSONObject(file.readText())

        return WakeSignature(
            energyPattern = json.getJSONArray("energy").toListInt(),
            zcrPattern = json.getJSONArray("zcr").toListInt(),
            energyDeltaPattern = json.getJSONArray("delta").toListInt(),
            delta_normalized = json.getJSONArray("delta_normalized").toListDouble()
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