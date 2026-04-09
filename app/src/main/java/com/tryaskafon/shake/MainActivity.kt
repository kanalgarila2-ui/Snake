package com.tryaskafon.shake

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.tryaskafon.shake.databinding.ActivityMainBinding
import com.tryaskafon.shake.repository.ConfigRepository
import com.tryaskafon.shake.service.ShakeDetectorService
import com.tryaskafon.shake.utils.SensorHelper
import java.io.File

/**
 * MainActivity — единственный экран приложения ТряскаФон.
 * Управляет включением/выключением сервиса, отображает статус тряски.
 */
class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    // ViewBinding — безопасный доступ к вьюшкам
    private lateinit var binding: ActivityMainBinding

    // ViewModel через делегат KTX
    private val viewModel: ShakeViewModel by viewModels()

    // Репозиторий для сохранения пути
    private lateinit var configRepository: ConfigRepository

    // Флаг — не запускать рекурсивный TextWatcher при программном обновлении поля
    private var isUpdatingFromViewModel = false

    // BroadcastReceiver для событий тряски от сервиса
    private val shakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ShakeDetectorService.ACTION_SHAKE_DETECTED -> {
                    // Обновляем счётчик и время последней тряски в ViewModel
                    val timestamp = intent.getLongExtra(ShakeDetectorService.EXTRA_TIMESTAMP, 0L)
                    viewModel.onShakeDetected(timestamp)
                    Log.d(TAG, "Получено событие SHAKE_DETECTED")
                }
                ShakeDetectorService.ACTION_SERVICE_STOPPED -> {
                    // Сервис остановился снаружи (например через уведомление)
                    viewModel.onServiceStopped()
                    Log.d(TAG, "Получено событие SERVICE_STOPPED")
                }
            }
        }
    }

    // Лончер для выбора файла через системный файловый менеджер
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                // Берём реальный путь из URI
                val realPath = getRealPathFromUri(uri)
                if (realPath != null) {
                    Log.d(TAG, "Выбран файл: $realPath")
                    viewModel.setFilePath(realPath)
                } else {
                    // Если реальный путь не удалось получить — берём URI строкой
                    val uriPath = uri.toString()
                    Log.w(TAG, "Реальный путь не найден, используем URI: $uriPath")
                    viewModel.setFilePath(uriPath)
                    Toast.makeText(
                        this,
                        "Предупреждение: не удалось получить реальный путь. Используется URI.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Лончер для запроса разрешений
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (perm, granted) ->
            Log.d(TAG, "Разрешение $perm: ${if (granted) "выдано" else "отказано"}")
        }
        // После ответа на разрешения пробуем снова запустить сервис
        if (binding.switchActivate.isChecked) {
            startServiceIfReady()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        // Инициализируем ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configRepository = ConfigRepository(this)

        // Проверяем наличие акселерометра
        val hasAccelerometer = SensorHelper.hasAccelerometer(this)
        if (!hasAccelerometer) {
            binding.switchActivate.isEnabled = false
            binding.tvStatus.text = getString(R.string.no_accelerometer)
            Toast.makeText(this, getString(R.string.no_accelerometer), Toast.LENGTH_LONG).show()
            Log.e(TAG, "Акселерометр не обнаружен!")
        }

        setupUI()
        observeViewModel()
        requestNecessaryPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Регистрируем receiver для событий тряски
        val filter = IntentFilter().apply {
            addAction(ShakeDetectorService.ACTION_SHAKE_DETECTED)
            addAction(ShakeDetectorService.ACTION_SERVICE_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(shakeReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(shakeReceiver, filter)
        }
        Log.d(TAG, "BroadcastReceiver зарегистрирован")
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(shakeReceiver)
            Log.d(TAG, "BroadcastReceiver отменён")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver не был зарегистрирован: ${e.message}")
        }
    }

    /**
     * Настройка всех элементов интерфейса и их слушателей.
     */
    private fun setupUI() {
        // --- Switch: включить/выключить детектор ---
        binding.switchActivate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startServiceIfReady()
            } else {
                stopShakeService()
            }
        }

        // --- EditText: путь к MP3 ---
        // TextWatcher — сохраняем путь СРАЗУ при каждом изменении (ТЗ: каждую 1 мс)
        binding.etFilePath.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingFromViewModel) return
                val newPath = s?.toString() ?: ""
                // Сохраняем синхронно — commit(), не apply() (ТЗ требует)
                configRepository.savePathImmediately(newPath)
                // ViewModel тоже обновляем, но без обратной петли
                viewModel.setFilePath(newPath, fromUi = true)
            }
        })

        // --- Кнопка "Выбрать файл" ---
        binding.btnPickFile.setOnClickListener {
            openFilePicker()
        }

        // --- SeekBar: чувствительность ---
        binding.seekBarSensitivity.min = 5
        binding.seekBarSensitivity.max = 30
        binding.seekBarSensitivity.progress = viewModel.sensitivity.value ?: 15
        binding.tvSensitivityValue.text = "${binding.seekBarSensitivity.progress} м/с²"

        binding.seekBarSensitivity.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    binding.tvSensitivityValue.text = "$progress м/с²"
                    if (fromUser) {
                        viewModel.setSensitivity(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )

        // --- CheckBox: вибрация ---
        binding.checkBoxVibrate.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setVibrateEnabled(isChecked)
        }
    }

    /**
     * Подписка на LiveData из ViewModel.
     */
    private fun observeViewModel() {
        // Путь к файлу
        viewModel.filePath.observe(this, Observer { path ->
            // Обновляем EditText, не вызывая TextWatcher рекурсивно
            if (binding.etFilePath.text.toString() != path) {
                isUpdatingFromViewModel = true
                binding.etFilePath.setText(path)
                isUpdatingFromViewModel = false
            }
        })

        // Состояние сервиса (включён/выключен)
        viewModel.isServiceRunning.observe(this, Observer { running ->
            binding.tvStatus.text = if (running) {
                getString(R.string.service_running)
            } else {
                getString(R.string.service_stopped)
            }
            // Switch обновляем без петли
            if (binding.switchActivate.isChecked != running) {
                binding.switchActivate.setOnCheckedChangeListener(null)
                binding.switchActivate.isChecked = running
                binding.switchActivate.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) startServiceIfReady() else stopShakeService()
                }
            }
        })

        // Счётчик трясок
        viewModel.shakeCount.observe(this, Observer { count ->
            binding.tvShakeCount.text = getString(R.string.shake_count, count)
        })

        // Время последней тряски
        viewModel.lastShakeTime.observe(this, Observer { time ->
            binding.tvLastShake.text = if (time == 0L) {
                getString(R.string.last_shake_never)
            } else {
                val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                getString(R.string.last_shake_at, sdf.format(java.util.Date(time)))
            }
        })

        // Чувствительность
        viewModel.sensitivity.observe(this, Observer { sens ->
            if (binding.seekBarSensitivity.progress != sens) {
                binding.seekBarSensitivity.progress = sens
                binding.tvSensitivityValue.text = "$sens м/с²"
            }
        })

        // Вибрация
        viewModel.vibrateEnabled.observe(this, Observer { enabled ->
            if (binding.checkBoxVibrate.isChecked != enabled) {
                binding.checkBoxVibrate.isChecked = enabled
            }
        })

        // Громкость воспроизведения (ProgressBar)
        viewModel.playbackVolume.observe(this, Observer { volume ->
            binding.progressBarVolume.progress = volume
        })
    }

    /**
     * Открываем системный файловый менеджер для выбора MP3.
     */
    private fun openFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/mpeg"
                // Разрешаем также audio/* на случай нестандартных MIME
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mpeg", "audio/mp3", "audio/*"))
            }
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка открытия файлового менеджера: ${e.message}", e)
            Toast.makeText(this, "Не удалось открыть файловый менеджер", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Получаем реальный путь к файлу из URI (через ContentResolver).
     */
    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            // Сначала пробуем через ContentResolver.query (MediaStore)
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns.DATA,
                OpenableColumns.DISPLAY_NAME
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                if (cursor.moveToFirst() && dataIndex >= 0) {
                    val path = cursor.getString(dataIndex)
                    if (!path.isNullOrBlank()) return path
                }
            }
            // Fallback: берём путь из URI напрямую
            if (uri.scheme == "file") {
                uri.path
            } else {
                // Для content:// URI — пробуем скопировать во временный файл
                copyUriToTempFile(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения пути из URI: ${e.message}", e)
            null
        }
    }

    /**
     * Копируем файл из content:// URI во временный файл кэша.
     * Используется как fallback, когда реальный путь недоступен.
     */
    private fun copyUriToTempFile(uri: Uri): String? {
        return try {
            val fileName = getFileNameFromUri(uri) ?: "shake_sound_${System.currentTimeMillis()}.mp3"
            val tempFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Файл скопирован во временный: ${tempFile.absolutePath}")
            tempFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка копирования файла: ${e.message}", e)
            null
        }
    }

    /**
     * Получаем имя файла из URI.
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось получить имя файла: ${e.message}")
            null
        }
    }

    /**
     * Запускаем ShakeDetectorService, предварительно проверив все условия.
     */
    private fun startServiceIfReady() {
        val path = binding.etFilePath.text.toString().trim()

        // Проверяем путь
        if (path.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_path_empty), Toast.LENGTH_SHORT).show()
            binding.switchActivate.isChecked = false
            return
        }

        // Проверяем существование файла (только для реальных путей, не URI)
        if (!path.startsWith("content://") && !path.startsWith("file://")) {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(this, getString(R.string.error_file_not_found), Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Файл не найден: $path")
                binding.switchActivate.isChecked = false
                return
            }
        }

        val sensitivity = viewModel.sensitivity.value ?: 15
        val vibrateEnabled = viewModel.vibrateEnabled.value ?: false

        Log.d(TAG, "Запускаем сервис: path=$path, sensitivity=$sensitivity, vibrate=$vibrateEnabled")

        // Запускаем foreground сервис
        ShakeDetectorService.start(
            context = this,
            filePath = path,
            sensitivity = sensitivity,
            vibrateEnabled = vibrateEnabled
        )

        viewModel.onServiceStarted()
    }

    /**
     * Останавливаем ShakeDetectorService.
     */
    private fun stopShakeService() {
        Log.d(TAG, "Останавливаем сервис")
        ShakeDetectorService.stop(this)
        viewModel.onServiceStopped()
    }

    /**
     * Запрашиваем необходимые разрешения в зависимости от версии Android.
     */
    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Уведомления (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Медиа (Android 13+)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Хранилище (до Android 13)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Запрашиваем разрешения: $permissionsToRequest")
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
