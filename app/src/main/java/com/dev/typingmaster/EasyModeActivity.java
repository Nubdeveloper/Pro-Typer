package com.app.typingmaster;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;  // Import for logging
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Date;
import android.content.Context;
import android.content.SharedPreferences;

public class EasyModeActivity extends AppCompatActivity {
    private TextView wordDisplay, timerText, scoreText, tvDateTime;
    private EditText inputField;
    private List<String> words = new ArrayList<>();
    private Random random = new Random();
    private int score = 0;
    private CountDownTimer timer;
    private static final int TIME_LIMIT = 10000; // 10 seconds
    private List<String> usedWords;
    
    private InterstitialAd interstitialAd;
    
    // Variable to record when the game started (for duration)
    private long gameStartTime;

    // Returns the current date and time string, also updates tvDateTime if available
    private String getCurrentDateTime() {
        String currentDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        Log.d("DateTime", currentDateTime);  // Logs the current date and time
        
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

        // Load Interstitial Ads (for game over card only)
        loadInterstitialAd();

        // Initialize UI elements
        wordDisplay = findViewById(R.id.wordDisplay);
        timerText = findViewById(R.id.timerText);
        scoreText = findViewById(R.id.scoreText);
        inputField = findViewById(R.id.inputField);
        tvDateTime = findViewById(R.id.tvDateTime);
        getCurrentDateTime();

        // Apply custom font to word display
        wordDisplay.setTypeface(ResourcesCompat.getFont(this, R.font.difficulty));

        // Load words from assets
        loadWordsFromAssets();

        // Start game immediately (no ad at game start)
        startNewGame();

        // Monitor text input
        inputField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                checkWord();
            }
        });
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
        // Record game start time for duration calculation
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
        if (timer != null) {
            timer.cancel();
        }
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
        // Calculate duration played (in seconds)
        int durationPlayed = (int) ((System.currentTimeMillis() - gameStartTime) / 1000);
        // Add new history record using HistoryManager
        HistoryManager.addHistory(this, new GameHistory("Easy", durationPlayed, score, getCurrentDateTime()));
        showAdThenGameOver();
    }

    // Display ad only before showing the game over card.
    private void showAdThenGameOver() {
        if (interstitialAd != null) {
            interstitialAd.show(this);
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    showGameOverCard(score);
                    loadInterstitialAd(); // Load next ad
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
        
        // On retry, remove the game over view and start a new game (no ad on retry)
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
    
    // Save game history into SharedPreferences
    public static void saveGameHistory(Context context, String mode, int duration, int score) {
        SharedPreferences preferences = context.getSharedPreferences("GameHistory", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        String currentDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String existingHistory = preferences.getString("history", "");
        String newEntry = mode + ", " + duration + "s, " + score + " points, " + currentDateTime + "\n";
        editor.putString("history", existingHistory + newEntry);
        editor.apply();
    }
    
    // Method to open the main menu (or finish this activity)
    private void openMenu() {
        finish();
    }
}