import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.ui_v01.MainActivity
import com.example.ui_v01.R
import com.example.ui_v01.databinding.ActivityMainBinding


class BTNenable: AppCompatActivity() {

    val btnid: List<String> = listOf<String>(
        "lsup",
        "lsdown",
        "packagebt",
        "connectbt",
        "demo",
        "file",
        "m1","m2","m3","m4","m5","m6",
        "p1","p2","p3","p4","p5","p6",
        "speedcontorl",
        //"stopbtn",
        "homepositionbtn",
        "gobtn",
        "xm","xp",
        "ym","yp",
        "zm","zp",
        "rxm","rxp",
        "rym","ryp",
        "rzm","rzp"
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 데이터 바인딩 객체 생성
        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        for (buttonId in btnid) {
            val field = binding::class.java.getDeclaredField(buttonId)
            field.isAccessible = true

            val button = field.get(binding) as Button

            button.isEnabled = true
        }

    }
}
