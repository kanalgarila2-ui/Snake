package com.tryaskafon.shake.ui.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.tryaskafon.shake.MainActivity
import com.tryaskafon.shake.R
import com.tryaskafon.shake.ShakeViewModel
import com.tryaskafon.shake.databinding.FragmentShakeBinding
import com.tryaskafon.shake.service.ShakeDetectorService
import com.tryaskafon.shake.utils.SensorHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ShakeFragment — основная вкладка: детектор тряски, управление сервисом.
 * Исправления v2:
 *   - ProgressBar горит всё время воспроизведения (PLAYBACK_STARTED / PLAYBACK_STOPPED)
 *   - Сохранение пути — debounce 10 сек через ViewModel
 */
class ShakeFragment : Fragment() {

    private var _binding: FragmentShakeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ShakeViewModel by activityViewModels()
    private var isUpdatingFromVm = false

    // Receiver слушает SHAKE + PLAYBACK события от сервиса
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                ShakeDetectorService.ACTION_SHAKE_DETECTED -> {
                    val ts = intent.getLongExtra(ShakeDetectorService.EXTRA_TIMESTAMP, 0L)
                    viewModel.onShakeDetected(ts)
                    // Анимируем иконку
                    binding.ivShakeIcon.animate().scaleX(1.3f).scaleY(1.3f).setDuration(100)
                        .withEndAction { binding.ivShakeIcon.animate().scaleX(1f).scaleY(1f).setDuration(100).start() }
                        .start()
                }
                ShakeDetectorService.ACTION_PLAYBACK_STARTED -> viewModel.onPlaybackStarted()
                ShakeDetectorService.ACTION_PLAYBACK_STOPPED -> viewModel.onPlaybackStopped()
                ShakeDetectorService.ACTION_SERVICE_STOPPED  -> viewModel.onServiceStopped()
            }
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val path = getRealPath(uri) ?: uri.toString()
                viewModel.setFilePath(path)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShakeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val hasAccelerometer = SensorHelper.hasAccelerometer(requireContext())
        if (!hasAccelerometer) {
            binding.switchActivate.isEnabled = false
            Toast.makeText(requireContext(), getString(R.string.no_accelerometer), Toast.LENGTH_LONG).show()
        }

        setupUI()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(ShakeDetectorService.ACTION_SHAKE_DETECTED)
            addAction(ShakeDetectorService.ACTION_PLAYBACK_STARTED)
            addAction(ShakeDetectorService.ACTION_PLAYBACK_STOPPED)
            addAction(ShakeDetectorService.ACTION_SERVICE_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else requireContext().registerReceiver(receiver, filter)
    }

    override fun onPause() {
        super.onPause()
        try { requireContext().unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupUI() {
        // Switch
        binding.switchActivate.setOnCheckedChangeListener { _, checked ->
            if (checked) startServiceIfReady() else stopService()
        }

        // EditText — debounce через ViewModel (не каждый символ)
        binding.etFilePath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromVm) return
                viewModel.setFilePath(s?.toString() ?: "", fromUi = true)
            }
        })

        binding.btnPickFile.setOnClickListener { openPicker() }

        // SeekBar чувствительности
        binding.seekBarSensitivity.min = 5
        binding.seekBarSensitivity.max = 30
        binding.seekBarSensitivity.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                binding.tvSensitivityValue.text = "$p м/с²"
                if (fromUser) viewModel.setSensitivity(p)
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        binding.checkBoxVibrate.setOnCheckedChangeListener { _, c -> viewModel.setVibrateEnabled(c) }
    }

    private fun observeViewModel() {
        viewModel.filePath.observe(viewLifecycleOwner) { path ->
            if (binding.etFilePath.text.toString() != path) {
                isUpdatingFromVm = true
                binding.etFilePath.setText(path)
                isUpdatingFromVm = false
            }
        }
        viewModel.isServiceRunning.observe(viewLifecycleOwner) { running ->
            binding.tvStatus.text = if (running) getString(R.string.service_running) else getString(R.string.service_stopped)
            if (binding.switchActivate.isChecked != running) {
                binding.switchActivate.setOnCheckedChangeListener(null)
                binding.switchActivate.isChecked = running
                binding.switchActivate.setOnCheckedChangeListener { _, c ->
                    if (c) startServiceIfReady() else stopService()
                }
            }
        }
        viewModel.shakeCount.observe(viewLifecycleOwner) {
            binding.tvShakeCount.text = getString(R.string.shake_count, it)
        }
        viewModel.lastShakeTime.observe(viewLifecycleOwner) { ts ->
            binding.tvLastShake.text = if (ts == 0L) getString(R.string.last_shake_never)
            else getString(R.string.last_shake_at, SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts)))
        }
        viewModel.sensitivity.observe(viewLifecycleOwner) { s ->
            if (binding.seekBarSensitivity.progress != s) binding.seekBarSensitivity.progress = s
            binding.tvSensitivityValue.text = "$s м/с²"
        }
        viewModel.vibrateEnabled.observe(viewLifecycleOwner) { e ->
            if (binding.checkBoxVibrate.isChecked != e) binding.checkBoxVibrate.isChecked = e
        }
        // ProgressBar горит ВСЁ время воспроизведения
        viewModel.playbackVolume.observe(viewLifecycleOwner) { v ->
            binding.progressBarVolume.progress = v
        }
    }

    private fun startServiceIfReady() {
        val path = binding.etFilePath.text.toString().trim()
        if (path.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_path_empty), Toast.LENGTH_SHORT).show()
            binding.switchActivate.isChecked = false; return
        }
        if (!path.startsWith("content://") && !path.startsWith("file://") && !File(path).exists()) {
            Toast.makeText(requireContext(), getString(R.string.error_file_not_found), Toast.LENGTH_SHORT).show()
            binding.switchActivate.isChecked = false; return
        }
        ShakeDetectorService.start(requireContext(), path,
            viewModel.sensitivity.value ?: 15, viewModel.vibrateEnabled.value ?: false)
        viewModel.onServiceStarted()
    }

    private fun stopService() {
        ShakeDetectorService.stop(requireContext())
        viewModel.onServiceStopped()
    }

    private fun openPicker() {
        filePickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        })
    }

    private fun getRealPath(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri,
                arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { c ->
                    val i = c.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (c.moveToFirst() && i >= 0) c.getString(i) else null
                } ?: run {
                // Копируем в кэш как fallback
                val name = requireContext().contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?: "audio_${System.currentTimeMillis()}.mp3"
                val tmp = File(requireContext().cacheDir, name)
                requireContext().contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
                tmp.absolutePath
            }
        } catch (e: Exception) { null }
    }
}
