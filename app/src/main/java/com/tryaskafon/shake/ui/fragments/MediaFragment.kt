package com.tryaskafon.shake.ui.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tryaskafon.shake.databinding.FragmentTabsBinding
import com.tryaskafon.shake.media.*

class MediaFragment : Fragment() {
    private var _b: FragmentTabsBinding? = null
    private val b get() = _b!!

    private val tabs = listOf(
        "🎵 Плеер"     to { MusicPlayerFragment() as Fragment },
        "🎙 Запись"    to { AudioRecorderFragment() as Fragment },
        "✂️ Рингтон"   to { RingtoneMakerFragment() as Fragment },
        "📷 OCR"       to { OcrFragment() as Fragment },
        "💬 ChatGPT"   to { ChatGptFragment() as Fragment }
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
