package com.tryaskafon.shake.ui.fragments

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.tryaskafon.shake.databinding.FragmentGamesBinding
import com.tryaskafon.shake.games.*

/**
 * GamesFragment — хаб игр.
 * Вкладки: Кубик | Рулетка | Угадайка | Гонки | Змейка | 2048
 */
class GamesFragment : Fragment() {

    private var _binding: FragmentGamesBinding? = null
    private val binding get() = _binding!!

    private val tabs = listOf(
        "🎲 Кубик"    to { DiceFragment() as Fragment },
        "🎰 Рулетка"  to { RouletteFragment() as Fragment },
        "🔢 Угадайка" to { GuessFragment() as Fragment },
        "🏎️ Гонки"   to { RacerFragment() as Fragment },
        "🐍 Змейка"   to { SnakeFragment() as Fragment },
        "🔢 2048"     to { Game2048Fragment() as Fragment }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGamesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewPagerGames.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabs.size
            override fun createFragment(position: Int) = tabs[position].second()
        }
        TabLayoutMediator(binding.tabLayoutGames, binding.viewPagerGames) { tab, pos ->
            tab.text = tabs[pos].first
        }.attach()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
