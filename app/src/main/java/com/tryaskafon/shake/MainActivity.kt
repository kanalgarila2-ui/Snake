package com.tryaskafon.shake

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.ActivityMainBinding
import com.tryaskafon.shake.ui.fragments.*

/**
 * MainActivity v2 — контейнер с BottomNavigationView на 5 пунктов.
 * Каждый пункт — отдельный фрагмент.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    val viewModel: ShakeViewModel by viewModels()

    // Кэш фрагментов — не пересоздаём при переключении вкладок
    private val fragments = mapOf(
        R.id.nav_shake  to lazy { ShakeFragment() },
        R.id.nav_games  to lazy { GamesFragment() },
        R.id.nav_osint  to lazy { OsintFragment() },
        R.id.nav_media  to lazy { MediaFragment() },
        R.id.nav_more   to lazy { MoreFragment() }
    )
    private var activeFragmentId = R.id.nav_shake

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        perms.forEach { (p, granted) -> Log.d(TAG, "$p → $granted") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestAllPermissions()
        setupBottomNav()

        // Восстанавливаем активный фрагмент после поворота
        if (savedInstanceState != null) {
            activeFragmentId = savedInstanceState.getInt("active_fragment", R.id.nav_shake)
        }
        showFragment(activeFragmentId)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("active_fragment", activeFragmentId)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            showFragment(item.itemId)
            true
        }
    }

    private fun showFragment(id: Int) {
        activeFragmentId = id
        val transaction = supportFragmentManager.beginTransaction()
        transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)

        // Скрываем все существующие фрагменты
        supportFragmentManager.fragments.forEach { transaction.hide(it) }

        // Ищем или создаём нужный фрагмент
        val tag = "fragment_$id"
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            transaction.show(existing)
        } else {
            val frag = fragments[id]?.value ?: ShakeFragment()
            transaction.add(R.id.fragmentContainer, frag, tag)
        }
        transaction.commit()
    }

    private fun requestAllPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
