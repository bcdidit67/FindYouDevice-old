package com.mimo.findyoudevice.old

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 设置页（Metro，3A 先行版：关于 + 联系方式；服务与主机配置随后续阶段补充）。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.rowProject).setOnClickListener {
            openUrl(this, "https://github.com/bcdidit67/FindYouDevice-old")
        }
        findViewById<TextView>(R.id.rowQq).setOnClickListener {
            copyText(this, "QQ 号", "3891605032")
        }
        findViewById<TextView>(R.id.rowMail).setOnClickListener {
            runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("mailto:fxxkhw676767@outlook.com"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                Toast.makeText(this, "邮箱：fxxkhw676767@outlook.com", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** 用系统浏览器打开链接 */
fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, "无法打开链接：$url", Toast.LENGTH_LONG).show()
    }
}

/** 复制文本到剪贴板并提示 */
fun copyText(context: android.content.Context, label: String, text: String) {
    runCatching {
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制 $label：$text", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "$label：$text", Toast.LENGTH_LONG).show()
    }
}
