package tech.yangle.matriximage

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import tech.yangle.matriximage.databinding.ActivityMainBinding

/**
 * 首页
 * <p>
 * Created by yangle on 2021/8/10.
 * Website：http://www.yangle.tech
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mivSample.setOnImageLongClickListener { view, pointF ->
            Toast.makeText(this, "长按事件 x:${pointF.x} y:${pointF.y}", Toast.LENGTH_LONG).show()
        }
    }
}