package com.tryaskafon.shake.ui.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tryaskafon.shake.databinding.FragmentTabsBinding
import com.tryaskafon.shake.system.*
import com.tryaskafon.shake.tools.*

class MoreFragment : Fragment() {
    private var _b: FragmentTabsBinding? = null
    private val b get() = _b!!

    private val tabs = listOf(
        "🔦 Стробо"    to { StrobeLightFragment() as Fragment },
        "📍 Компас"    to { CompassFragment() as Fragment },
        "⏱ Секундомер" to { StopwatchFragment() as Fragment },
        "🔤 Морзе"     to { MorseFragment() as Fragment },
        "🐾 Тамагочи"  to { TamagotchiFragment() as Fragment },
        "📱 QR"        to { QrFragment() as Fragment },
        "🆘 SOS"       to { SosFragment() as Fragment },
        "🔐 Пароли"    to { PasswordGeneratorFragment() as Fragment },
        "⚙ Настройки"  to { SettingsFragment() as Fragment }
    )

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentTabsBinding.inflate(i, c, false); return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabs.size
            override fun createFragment(pos: Int) = tabs[pos].second()
        }
        TabLayoutMediator(b.tabLayout, b.viewPager) { tab, pos -> tab.text = tabs[pos].first }.attach()
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
