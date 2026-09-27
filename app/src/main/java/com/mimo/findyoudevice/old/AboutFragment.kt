package com.mimo.findyoudevice.old

import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mimo.findyoudevice.old.databinding.PageAboutBinding

/** 关于页（Metro）：版本 / 项目地址 / 联系方式 */
class AboutFragment : Fragment() {

    private var _b: PageAboutBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = PageAboutBinding.inflate(inflater, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.tvVersion.text = runCatching {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrNull()?.let { "FindYouDevice-old v$it" } ?: "FindYouDevice-old v1.0.0"

        b.tvBase.text = "基于 FindYouDevice v1.1.1 开发\n最低支持 Android 5.0（API 21）"
        b.rowProject.setOnClickListener {
            openUrl(requireContext(), "https://github.com/bcdidit67/FindYouDevice-old")
        }
        b.rowQq.setOnClickListener { copyText(requireContext(), "QQ 号", "3891605032") }
        b.rowMail.setOnClickListener {
            runCatching {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("mailto:fxxkhw676767@outlook.com")
                    )
                )
            }.onFailure {
                Toast.makeText(requireContext(), "邮箱：fxxkhw676767@outlook.com", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
