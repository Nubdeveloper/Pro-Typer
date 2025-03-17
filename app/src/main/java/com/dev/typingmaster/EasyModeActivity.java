package com.samarthshukla.protyper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class EasyModeActivity extends AppCompatActivity {
    private TextView wordDisplay, timerText, scoreText, tvDateTime;
    // Old inputField (if still used) can remain, but now our key views are:
    // wordCard: the "word card" view
    // textInputLayout: container for the input field
    // (Make sure your XML IDs match these exactly.)
    // For backward compatibility, we keep inputField for text watching.
    private EditText inputField;
    
    private List<String> words = new ArrayList<>();
    private Random random = new Random();
    private int score = 0;
    private CountDownTimer timer;
    private static final int TIME_LIMIT = 10000; // 7 seconds per word
    private List<String> usedWords;
    private InterstitialAd interstitialAd;
    private long gameStartTime; // timestamp when game starts

    private String getCurrentDateTime() {
        String currentDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        Log.d("DateTime", currentDateTime);
        if (tvDateTime != null) {
            tvDateTime.setText(currentDateTime);
        }
        return currentDateTime;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_easy_mode);

        // Initialize AdMob
        MobileAds.initialize(this, initializationStatus -> {});
        loadInterstitialAd();

        // Initialize UI elements (IDs as per your XML)
        // wordDisplay is still used for showing the current word.
        wordDisplay = findViewById(R.id.wordDisplay);
        timerText   = findViewById(R.id.timerText);
        scoreText   = findViewById(R.id.scoreText);
        inputField  = findViewById(R.id.inputField);
        tvDateTime  = findViewById(R.id.tvDateTime);
        getCurrentDateTime();

        // Apply custom font to wordDisplay
        wordDisplay.setTypeface(ResourcesCompat.getFont(this, R.font.difficulty));

        loadWordsFromAssets();
        startNewGame();

        inputField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                checkWord();
            }
        });

        // --- Android 15 (API 35+) patch ---
        // When the keyboard appears, we want to minimize the gap between the word card and text input container.
        if (Build.VERSION.SDK_INT >= 35) {
            // Let the window supply proper insets.
            getWindow().setDecorFitsSystemWindows(true);
            final View rootView = findViewById(android.R.id.content);
            // Find your two key views by their IDs.
            final View wordCard = findViewById(R.id.wordCard);
            final View textInputLayout = findViewById(R.id.textInputLayout);
            
            rootView.setOnApplyWindowInsetsListener((v, insets) -> {
                int imeHeight = insets.getInsets(WindowInsets.Type.ime()).bottom;
                if (imeHeight > 0) {
                    // Compute available height above keyboard.
                    int availableHeight = rootView.getHeight() - imeHeight;
                    // Get the screen location of textInputLayout.
                    int[] inputLocation = new int[2];
                    textInputLayout.getLocationOnScreen(inputLocation);
                    int inputBottom = inputLocation[1] + textInputLayout.getHeight();
                    // How much is textInputLayout overlapping the keyboard?
                    int overlap = inputBottom - availableHeight;
                    if (overlap < 0) overlap = 0;
                    
                    // Get current gap between wordCard and textInputLayout.
                    int[] wordLocation = new int[2];
                    wordCard.getLocationOnScreen(wordLocation);
                    int wordBottom = wordLocation[1] + wordCard.getHeight();
                    int currentGap = inputLocation[1] - wordBottom;
                    
                    // Define a minimum gap of 8dp.
                    int minGap = dpToPx(8);
                    int extraGapReduction = currentGap - minGap;
                    if (extraGapReduction < 0) extraGapReduction = 0;
                    
                    // We want textInputLayout to move up by the full overlap.
                    int translationForInput = -overlap;
                    // For wordCard, move it up by as much as possible without reducing gap below minGap.
                    int translationForWord = -Math.min(overlap, extraGapReduction);
                    
                    textInputLayout.animate().translationY(translationForInput).setDuration(100).start();
                    wordCard.animate().translationY(translationForWord).setDuration(100).start();
                } else {
                    // Reset translations when keyboard is hidden.
                    textInputLayout.animate().translationY(0).setDuration(100).start();
                    wordCard.animate().translationY(0).setDuration(100).start();
                }
                return insets;
            });
        }
    }
    
    // Helper: Convert dp to pixels.
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    private void loadWordsFromAssets() {
        try {
            for (char letter = 'A'; letter <= 'Z'; letter++) {
                String fileName = letter + " Words.txt";
                BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(fileName)));
                String word;
                while ((word = reader.readLine()) != null) {
                    words.add(word);
                }
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void startNewGame() {
        score = 0;
        scoreText.setText("Score: " + score);
        inputField.setText("");
        inputField.setEnabled(true);
        usedWords = new ArrayList<>();
        generateNewWord();
        gameStartTime = System.currentTimeMillis();
        startTimer();
    }
    
    private void generateNewWord() {
        String newWord;
        do {
            newWord = words.get(random.nextInt(words.size()));
        } while (usedWords.contains(newWord));
        usedWords.add(newWord);
        wordDisplay.setText(newWord);
    }
    
    private void startTimer() {
        if (timer != null) timer.cancel();
        timer = new CountDownTimer(TIME_LIMIT, 1000) {
            public void onTick(long millisUntilFinished) {
                timerText.setText("Time left: " + millisUntilFinished / 1000 + "s");
            }
            public void onFinish() {
                gameOver();
            }
        }.start();
    }
    
    private void checkWord() {
        String typedWord = inputField.getText().toString().trim();
        String displayedWord = wordDisplay.getText().toString().trim();
        if (typedWord.equalsIgnoreCase(displayedWord)) {
            score += 4;
            scoreText.setText("Score: " + score);
            inputField.setText("");
            generateNewWord();
            startTimer();
        }
    }
    
    private void gameOver() {
        inputField.setEnabled(false);
        int durationPlayed = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        HistoryManager.addHistory(this, new GameHistory("Easy", durationPlayed, score, getCurrentDateTime()));
        showAdThenGameOver();
    }

    private void showAdThenGameOver() {
        if (interstitialAd != null) {
            interstitialAd.show(this);
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    showGameOverCard(score);
                    loadInterstitialAd();
                }
            });
        } else {
            showGameOverCard(score);
        }
    }

    private void showGameOverCard(int score) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View gameOverView = inflater.inflate(R.layout.game_over_card, null);
        TextView gameOverMessage = gameOverView.findViewById(R.id.gameOverMessageInside);
        gameOverMessage.setText("Game Over!");
        TextView scoreInsideCard = gameOverView.findViewById(R.id.scoreTextInsideCard);
        scoreInsideCard.setText("Score: " + score);
        MaterialButton retryButton = gameOverView.findViewById(R.id.retryButton);
        MaterialButton menuButton = gameOverView.findViewById(R.id.menuButton);
        retryButton.setOnClickListener(v -> {
            removeGameOverView(gameOverView);
            startNewGame();
        });
        menuButton.setOnClickListener(v -> openMenu());
        ViewGroup rootView = findViewById(android.R.id.content);
        rootView.addView(gameOverView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }
    
    private void removeGameOverView(View gameOverView) {
        ViewGroup parent = (ViewGroup) gameOverView.getParent();
        if (parent != null) {
            parent.removeView(gameOverView);
        }
    }

    private void loadInterstitialAd() {
        AdRequest adRequest = new AdRequest.Builder().build();
        InterstitialAd.load(this, "ca-app-pub-6807409350203174/4005808164", adRequest,
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(InterstitialAd ad) {
                    interstitialAd = ad;
                }
                @Override
                public void onAdFailedToLoad(LoadAdError adError) {
                    interstitialAd = null;
                }
            });
    }
    
    @Override
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Exit Game?")
            .setMessage("Do you want to go back to the menu or continue playing?")
            .setPositiveButton("Go to Menu", (dialog, which) -> finish())
            .setNegativeButton("Continue Playing", (dialog, which) -> dialog.dismiss())
            .show();
    }
    
    public static void saveGameHistory(Context context, String mode, int duration, int score) {
        SharedPreferences preferences = context.getSharedPreferences("GameHistory", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        String currentDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        String existingHistory = preferences.getString("history", "");
        String newEntry = mode + ", " + duration + "s, " + score + " points, " + currentDateTime + "\n";
        editor.putString("history", existingHistory + newEntry);
        editor.apply();
    }
    
    private void openMenu() {
        finish();
    }
}