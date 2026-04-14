package com.tryaskafon.shake

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tryaskafon.shake.databinding.ActivityMainBinding
import com.tryaskafon.shake.ui.fragments.*
import com.tryaskafon.shake.crypto.CryptoFragment
import com.tryaskafon.shake.science.ScienceFragment
import com.tryaskafon.shake.health.HealthFragment
import com.tryaskafon.shake.generators.GeneratorsFragment
import com.tryaskafon.shake.casino.CasinoFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    val viewModel: ShakeViewModel by viewModels()

    private val fragmentMap = mapOf(
        R.id.nav_shake      to lazy { ShakeFragment() as Fragment },
        R.id.nav_games      to lazy { GamesFragment() as Fragment },
        R.id.nav_osint      to lazy { OsintFragment() as Fragment },
        R.id.nav_media      to lazy { MediaFragment() as Fragment },
        R.id.nav_more       to lazy { MoreFragment() as Fragment },
        R.id.nav_crypto     to lazy { CryptoFragment() as Fragment },
        R.id.nav_science    to lazy { ScienceFragment() as Fragment },
        R.id.nav_health     to lazy { HealthFragment() as Fragment },
        R.id.nav_generators to lazy { GeneratorsFragment() as Fragment },
        R.id.nav_casino     to lazy { CasinoFragment() as Fragment }
    )
    private var activeId = R.id.nav_shake

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* логируем если нужно */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestPermissions()
        setupBottomNav()
        if (savedInstanceState != null)
            activeId = savedInstanceState.getInt("active", R.id.nav_shake)
        showFragment(activeId)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("active", activeId)
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            showFragment(item.itemId); true
        }
    }

    private fun showFragment(id: Int) {
        activeId = id
        val tx = supportFragmentManager.beginTransaction()
        tx.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
        supportFragmentManager.fragments.forEach { tx.hide(it) }
        val tag = "f_$id"
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) tx.show(existing)
        else tx.add(R.id.fragmentContainer, fragmentMap[id]?.value ?: ShakeFragment(), tag)
        tx.commit()
    }

    private fun requestPermissions() {
        val needed = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.SEND_SMS
        ).apply {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                if (Build.VERSION.SDK_INT <= 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
