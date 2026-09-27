package com.mimo.findyoudevice.old

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/** 顶部 Tab 三页：设备 / 设置 / 关于 */
class MetroPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> DevicesFragment()
        1 -> SettingsFragment()
        else -> AboutFragment()
    }
}
