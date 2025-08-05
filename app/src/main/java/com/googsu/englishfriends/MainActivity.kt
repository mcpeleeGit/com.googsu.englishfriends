package com.googsu.englishfriends

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.googsu.englishfriends.ui.theme.EnglishFriendsTheme
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.os.Build
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Header
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    // 이미지 URI 저장용
    private var cameraImageUri: Uri? = null

    // 카메라 촬영 결과 런처
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    // 갤러리 선택 결과 런처
    private lateinit var pickGalleryLauncher: ActivityResultLauncher<String>
    // 카메라 권한 요청 런처
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    // 갤러리 권한 요청 런처
    private lateinit var requestGalleryPermissionLauncher: ActivityResultLauncher<String>
    // 음성 인식 권한 요청 런처
    lateinit var requestRecordAudioPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var selectedImageUri by mutableStateOf<Uri?>(null)
        var ocrResult by mutableStateOf<List<OcrResultItem>>(emptyList())
        var currentScreen by mutableStateOf<Screen>(Screen.Home)
        var quizData by mutableStateOf<QuizData?>(null)

        // 런처 초기화
        requestCameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
        requestGalleryPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                launchGallery()
            } else {
                Toast.makeText(this, "앨범 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
        requestRecordAudioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // 권한이 승인되면 음성 인식 시작
                startSpeechRecognition()
            } else {
                Toast.makeText(this, "음성 인식 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
        takePictureLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success: Boolean ->
            if (success && cameraImageUri != null) {
                selectedImageUri = cameraImageUri
                runOcrWithImageUri(cameraImageUri!!) { lines ->
                    val items = lines.map { line -> OcrResultItem(line, "") }
                    ocrResult = items
                    // 각 라인에 대해 번역 실행
                    items.forEachIndexed { index, item ->
                        translateText(item.originalText) { translation ->
                            ocrResult = ocrResult.toMutableList().apply {
                                this[index] = this[index].copy(translation = translation)
                            }
                        }
                    }
                }
            }
        }
        pickGalleryLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                runOcrWithImageUri(it) { lines ->
                    val items = lines.map { line -> OcrResultItem(line, "") }
                    ocrResult = items
                    // 각 라인에 대해 번역 실행
                    items.forEachIndexed { index, item ->
                        translateText(item.originalText) { translation ->
                            ocrResult = ocrResult.toMutableList().apply {
                                this[index] = this[index].copy(translation = translation)
                            }
                        }
                    }
                }
            }
        }

        setContent {
            EnglishFriendsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (currentScreen) {
                        Screen.Home -> {
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                selectedImageUri = selectedImageUri,
                                ocrResult = ocrResult,
                                onCameraClick = {
                                    when {
                                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                                            launchCamera()
                                        }
                                        else -> {
                                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    }
                                },
                                onGalleryClick = {
                                    val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        Manifest.permission.READ_MEDIA_IMAGES
                                    } else {
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                    }
                                    when {
                                        ContextCompat.checkSelfPermission(this, galleryPermission) == PackageManager.PERMISSION_GRANTED -> {
                                            launchGallery()
                                        }
                                        else -> {
                                            requestGalleryPermissionLauncher.launch(galleryPermission)
                                        }
                                    }
                                },
                                onHistoryClick = { /* TODO: 최근 학습 기록 연결 */ },
                                onRetryClick = { /* TODO: 문제 다시 풀기 연결 */ },
                                onStartQuiz = { direction ->
                                    val shuffledWords = ocrResult.shuffled()
                                    quizData = QuizData(
                                        words = shuffledWords,
                                        direction = direction,
                                        currentIndex = 0,
                                        correctAnswers = 0,
                                        totalQuestions = shuffledWords.size
                                    )
                                    currentScreen = Screen.Quiz
                                }
                            )
                        }
                        Screen.Quiz -> {
                            quizData?.let { quiz ->
                                QuizScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    quizData = quiz,
                                    onAnswerSubmit = { answer ->
                                        val currentWord = quiz.words[quiz.currentIndex]
                                        val isCorrect = if (quiz.direction == QuizDirection.ENGLISH_TO_KOREAN) {
                                            answer.trim().equals(currentWord.translation.trim(), ignoreCase = true)
                                        } else {
                                            answer.trim().equals(currentWord.originalText.trim(), ignoreCase = true)
                                        }
                                        
                                        quizData = quiz.copy(
                                            correctAnswers = if (isCorrect) quiz.correctAnswers + 1 else quiz.correctAnswers
                                        )
                                    },
                                    onNextQuestion = {
                                        if (quizData!!.currentIndex + 1 >= quizData!!.totalQuestions) {
                                            currentScreen = Screen.Result
                                        } else {
                                            quizData = quizData!!.copy(currentIndex = quizData!!.currentIndex + 1)
                                        }
                                    },
                                    onBackToHome = {
                                        currentScreen = Screen.Home
                                    }
                                )
                            }
                        }
                        Screen.Result -> {
                            quizData?.let { quiz ->
                                ResultScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    quizData = quiz,
                                    onBackToHome = {
                                        currentScreen = Screen.Home
                                    },
                                    onRetryQuiz = {
                                        val shuffledWords = quiz.words.shuffled()
                                        quizData = quiz.copy(
                                            words = shuffledWords,
                                            currentIndex = 0,
                                            correctAnswers = 0
                                        )
                                        currentScreen = Screen.Quiz
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 카메라 실행 함수
    private fun launchCamera() {
        val photoFile = createImageFile()
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        cameraImageUri?.let { takePictureLauncher.launch(it) }
    }

    // 갤러리 실행 함수
    private fun launchGallery() {
        pickGalleryLauncher.launch("image/*")
    }

    // 임시 이미지 파일 생성
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    // 번역 API 인터페이스
    interface TranslationService {
        @FormUrlEncoded
        @POST("translation")
        suspend fun translate(
            @Header("X-NCP-APIGW-API-KEY-ID") clientId: String,
            @Header("X-NCP-APIGW-API-KEY") clientSecret: String,
            @Field("source") source: String,
            @Field("target") target: String,
            @Field("text") text: String
        ): TranslationResponse
    }

    data class TranslationRequest(
        val source: String = "en",
        val target: String = "ko",
        val text: String
    )

    data class TranslationResponse(
        val message: Message
    )

    data class Message(
        val result: TranslationResult
    )

    data class TranslationResult(
        val translatedText: String
    )

    data class OcrResultItem(
        val originalText: String,
        val translation: String
    )

    // 번역 함수
    private fun translateText(text: String, onResult: (String) -> Unit) {
        Log.d("Translation", "번역 시작: $text")
        
        val retrofit = Retrofit.Builder()
            .baseUrl("https://papago.apigw.ntruss.com/nmt/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val service = retrofit.create(TranslationService::class.java)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("Translation", "API 호출 중...")
                val response = service.translate(
                    clientId = "olro1e28f9",
                    clientSecret = "lOMl8VazRzmG71EzGbzk00Yxzu2o4ZAIWJDiNPPw",
                    source = "en",
                    target = "ko",
                    text = text
                )
                Log.d("Translation", "번역 성공: ${response.message.result.translatedText}")
                withContext(Dispatchers.Main) {
                    onResult(response.message.result.translatedText)
                }
            } catch (e: Exception) {
                Log.e("Translation", "번역 실패: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onResult("번역 실패: ${e.message}")
                }
            }
        }
    }

    // OCR 실행 함수 (콜백으로 결과 전달)
    private fun runOcrWithImageUri(uri: Uri, onResult: (List<String>) -> Unit) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val lines = visionText.textBlocks.flatMap { block ->
                        block.lines.map { it.text }
                    }
                    onResult(lines)
                }
                .addOnFailureListener { e ->
                    onResult(listOf("OCR 실패: ${e.localizedMessage}"))
                }
        } catch (e: Exception) {
            onResult(listOf("이미지 처리 오류: ${e.localizedMessage}"))
        }
    }

    // 음성 인식 시작 함수
    fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR") // 한국어 설정
            putExtra(RecognizerIntent.EXTRA_PROMPT, "답변을 말씀해주세요")
        }
        
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "음성 인식을 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 음성 인식 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val spokenText: String? = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            spokenText?.let { text ->
                // 음성 인식 결과를 콜백으로 전달
                onSpeechResult?.invoke(text)
            }
        }
    }

    companion object {
        private const val SPEECH_REQUEST_CODE = 100
    }

    // 음성 인식 결과 콜백
    private var onSpeechResult: ((String) -> Unit)? = null

    // 음성 인식 결과 콜백 설정 함수
    fun setSpeechResultCallback(callback: (String) -> Unit) {
        onSpeechResult = callback
    }

    enum class Screen {
        Home, Quiz, Result
    }
    
    enum class QuizDirection {
        ENGLISH_TO_KOREAN, KOREAN_TO_ENGLISH
    }
    
    data class QuizData(
        val words: List<OcrResultItem>,
        val direction: QuizDirection,
        val currentIndex: Int,
        val correctAnswers: Int,
        val totalQuestions: Int
    )
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedImageUri: Uri?,
    ocrResult: List<MainActivity.OcrResultItem>,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onRetryClick: () -> Unit,
    onStartQuiz: (MainActivity.QuizDirection) -> Unit
) {
    val context = LocalContext.current
    var isOcrExpanded by remember { mutableStateOf(false) }
    var showQuizDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text("암기 도우미", style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(24.dp))
        if (selectedImageUri == null) {
            Image(
                painter = painterResource(id = R.drawable.icon),
                contentDescription = "앱 아이콘",
                modifier = Modifier.size(120.dp)
            )
        } else {
            val bitmap = remember(selectedImageUri) {
                try {
                    selectedImageUri?.let {
                        val inputStream = context.contentResolver.openInputStream(it)
                        inputStream?.use { BitmapFactory.decodeStream(it) }
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "선택된 이미지",
                    modifier = Modifier.size(120.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCameraClick, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("📷 사진 찍기")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGalleryClick, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("🖼️ 앨범에서 불러오기")
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        // OCR 결과 표시 (축소/확장 가능)
        if (ocrResult.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📝 OCR 결과 (${ocrResult.size}개)",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { isOcrExpanded = !isOcrExpanded }) {
                            Icon(
                                imageVector = if (isOcrExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isOcrExpanded) "축소" else "확장"
                            )
                        }
                    }
                    
                    if (isOcrExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 헤더
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE3F2FD))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "번호",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(40.dp),
                                color = Color.Gray
                            )
                            Text(
                                "영문",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                color = Color.Gray
                            )
                            Text(
                                "한글",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                color = Color.Gray
                            )
                        }
                        
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            itemsIndexed(ocrResult) { index, item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (index % 2 == 0) 
                                            Color(0xFFF5F5F5) else Color.White
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${index + 1}.",
                                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(40.dp),
                                            color = Color.Gray
                                        )
                                        Text(
                                            item.originalText,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            item.translation,
                                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 문제 내기 버튼
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { showQuizDialog = true },
                modifier = Modifier.fillMaxWidth(0.8f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("🎯 문제 내기")
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = onHistoryClick) { Text("최근 학습 기록") }
            TextButton(onClick = onRetryClick) { Text("문제 다시 풀기") }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // 문제 출제 방향 선택 다이얼로그
    if (showQuizDialog) {
        AlertDialog(
            onDismissRequest = { showQuizDialog = false },
            title = {
                Text("문제 출제 방향 선택", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("어떤 방향으로 문제를 출제하시겠습니까?")
            },
            confirmButton = {
                Column {
                    Button(
                        onClick = {
                            showQuizDialog = false
                            onStartQuiz(MainActivity.QuizDirection.ENGLISH_TO_KOREAN)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("🇺🇸 영어 → 🇰🇷 한글")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            showQuizDialog = false
                            onStartQuiz(MainActivity.QuizDirection.KOREAN_TO_ENGLISH)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("🇰🇷 한글 → 🇺🇸 영어")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuizDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

// 문제 풀이 화면
@Composable
fun QuizScreen(
    modifier: Modifier = Modifier,
    quizData: MainActivity.QuizData,
    onAnswerSubmit: (String) -> Unit,
    onNextQuestion: () -> Unit,
    onBackToHome: () -> Unit
) {
    var userAnswer by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    
    val currentWord = quizData.words[quizData.currentIndex]
    val question = if (quizData.direction == MainActivity.QuizDirection.ENGLISH_TO_KOREAN) {
        currentWord.originalText
    } else {
        currentWord.translation
    }
    val correctAnswer = if (quizData.direction == MainActivity.QuizDirection.ENGLISH_TO_KOREAN) {
        currentWord.translation
    } else {
        currentWord.originalText
    }
    
    // 음성 인식 결과 콜백 설정
    LaunchedEffect(Unit) {
        mainActivity?.setSpeechResultCallback { result ->
            userAnswer = result
            isListening = false
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 정보
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToHome) {
                Icon(Icons.Default.Home, contentDescription = "홈으로")
            }
            Text(
                "문제 ${quizData.currentIndex + 1}/${quizData.totalQuestions}",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "점수: ${quizData.correctAnswers}",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 문제 표시
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (quizData.direction == MainActivity.QuizDirection.ENGLISH_TO_KOREAN) "🇺🇸 영어 → 🇰🇷 한글" else "🇰🇷 한글 → 🇺🇸 영어",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    question,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 답변 입력
        if (!showResult) {
            OutlinedTextField(
                value = userAnswer,
                onValueChange = { userAnswer = it },
                label = {
                    Text(if (quizData.direction == MainActivity.QuizDirection.ENGLISH_TO_KOREAN) "한글 뜻을 입력하세요" else "영어 단어를 입력하세요")
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FloatingActionButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            isListening = true
                            mainActivity?.startSpeechRecognition()
                        } else {
                            mainActivity?.requestRecordAudioPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(56.dp),
                    containerColor = if (isListening) Color(0xFFE57373) else androidx.compose.material3.MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        if (isListening) Icons.Default.Close else Icons.Default.Call, 
                        contentDescription = if (isListening) "음성 입력 중지" else "음성 입력"
                    )
                }
                
                Button(
                    onClick = {
                        if (userAnswer.isNotBlank()) {
                            val correct = userAnswer.trim().equals(correctAnswer.trim(), ignoreCase = true)
                            isCorrect = correct
                            showResult = true
                            onAnswerSubmit(userAnswer)
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "정답 확인")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("정답 확인")
                }
            }
        } else {
            // 결과 표시
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect) Color(0xFFE8F5E8) else Color(0xFFFFEBEE)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (isCorrect) "✅ 정답입니다!" else "❌ 틀렸습니다",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "정답: $correctAnswer",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                    if (!isCorrect && userAnswer.isNotBlank()) {
                        Text(
                            "입력한 답: $userAnswer",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            Button(
                onClick = {
                    if (quizData.currentIndex + 1 >= quizData.totalQuestions) {
                        // 마지막 문제이므로 결과 화면으로
                        onNextQuestion()
                    } else {
                        userAnswer = ""
                        showResult = false
                        onNextQuestion()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = "다음")
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (quizData.currentIndex + 1 >= quizData.totalQuestions) "결과 보기" else "다음 문제")
            }
        }
    }
}

// 결과 화면
@Composable
fun ResultScreen(
    modifier: Modifier = Modifier,
    quizData: MainActivity.QuizData,
    onBackToHome: () -> Unit,
    onRetryQuiz: () -> Unit
) {
    val score = (quizData.correctAnswers.toFloat() / quizData.totalQuestions.toFloat()) * 100
    
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Text(
            "🎉 퀴즈 완료!",
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "최종 점수",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${score.toInt()}점",
                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        score >= 90 -> Color(0xFF2E7D32)
                        score >= 70 -> Color(0xFFF57C00)
                        else -> Color(0xFFC62828)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "맞힌 문제: ${quizData.correctAnswers}/${quizData.totalQuestions}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onBackToHome,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = "홈으로")
                Spacer(modifier = Modifier.width(8.dp))
                Text("홈으로")
            }
            
            Button(
                onClick = onRetryQuiz,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("다시 풀기")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    EnglishFriendsTheme {
        HomeScreen(
            selectedImageUri = null,
            ocrResult = emptyList(),
            onCameraClick = {},
            onGalleryClick = {},
            onHistoryClick = {},
            onRetryClick = {},
            onStartQuiz = {}
        )
    }
}