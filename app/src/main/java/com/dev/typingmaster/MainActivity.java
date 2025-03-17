package com.samarthshukla.protyper;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.analytics.FirebaseAnalytics;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.content.SharedPreferences;
import android.content.Context;

public class MainActivity extends AppCompatActivity {

    private boolean doubleBackToExitPressedOnce = false;
    private Handler doubleBackHandler = new Handler();
    private BroadcastReceiver networkReceiver;

    private InterstitialAd interstitialAd;
    private com.google.android.gms.ads.AdView bannerAdView;
    private Handler bannerRefreshHandler = new Handler();

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    // Firebase Analytics instance and session start time variable
    private FirebaseAnalytics mFirebaseAnalytics;
    private long sessionStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Analytics and log app open event
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null);

        // Initialize Navigation Drawer (if any)

        // Initialize AdMob
        MobileAds.initialize(this, initializationStatus -> {});

        // Load Ads
        loadInterstitialAd();
        loadBannerAd();
        startBannerAdRefresh();

        // Initialize buttons
        MaterialButton btnSingleWordMode = findViewById(R.id.btnSingleWordMode);
        // Rename Single Word Mode to PLAY
        btnSingleWordMode.setText("START");

        // Removed: Paragraph mode button
        // MaterialButton btnParagraphMode = findViewById(R.id.btnParagraphMode);

        MaterialButton btnHistory = findViewById(R.id.btnHistory);
        btnHistory.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, HistoryActivity.class)));
        MaterialButton btnExit = findViewById(R.id.btnExit);

        // New ImageButtons for How To Play and About
        ImageButton btnHowToPlay = findViewById(R.id.btnHowToPlay);
        // Instead of launching a separate activity, show a popup
        btnHowToPlay.setOnClickListener(v -> showHowToPlayPopup());

        ImageButton btnAbout = findViewById(R.id.btnAbout);
        btnAbout.setOnClickListener(v -> showAdThenStart(AboutActivity.class));

        // Check Internet Connection
        checkInternetOnStart();

        // Button Click Listeners
        btnSingleWordMode.setOnClickListener(v -> showAdThenStart(DifficultyActivity.class));
        // Removed: Paragraph Mode click listener
        // btnParagraphMode.setOnClickListener(v -> showAdThenStart(ParagraphActivity.class));
        btnExit.setOnClickListener(v -> showConfirmExitDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start session tracking
        sessionStartTime = System.currentTimeMillis();
        // Log a custom session start event
        mFirebaseAnalytics.logEvent("session_start", null);

        checkInternetOnStart();

        // Register network change receiver
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!isInternetAvailable()) {
                    showRetryInternetDialog();
                }
            }
        };
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
        }
        // End session tracking and log session duration
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        Bundle bundle = new Bundle();
        bundle.putLong("session_duration_ms", sessionDuration);
        mFirebaseAnalytics.logEvent("session_end", bundle);
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            showAdThenFinish();
            return;
        }
        doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit.", Toast.LENGTH_SHORT).show();
        doubleBackHandler.postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    private void checkInternetOnStart() {
        if (!isInternetAvailable()) {
            showRetryInternetDialog();
        }
    }

    private void showRetryInternetDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Whoops!!")
                .setMessage("It seems you're offline. Check your internet connection and try again!")
                .setCancelable(false)
                .setPositiveButton("Retry", (dialog, which) -> {
                    if (isInternetAvailable()) {
                        restartActivity();
                    } else {
                        showRetryInternetDialog();
                    }
                })
                .show();
    }

    private void showConfirmExitDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exit")
                .setMessage("You're about to leave. Do you really want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", (dialog, which) -> showAdThenFinish())
                .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnected();
        }
        return false;
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
                    loadInterstitialAd();
                }
            });
    }

    private void loadBannerAd() {
        bannerAdView = findViewById(R.id.bannerAdView);
        AdRequest adRequest = new AdRequest.Builder().build();
        bannerAdView.loadAd(adRequest);
    }

    private void startBannerAdRefresh() {
        Runnable bannerRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (bannerAdView != null) {
                    bannerAdView.loadAd(new AdRequest.Builder().build());
                }
                bannerRefreshHandler.postDelayed(this, 30000);
            }
        };
        bannerRefreshHandler.post(bannerRefreshRunnable);
    }

    private void showAdThenStart(Class<?> activity) {
        if (interstitialAd != null) {
            interstitialAd.show(MainActivity.this);
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadInterstitialAd();
                    startActivity(new Intent(MainActivity.this, activity));
                }
            });
        } else {
            startActivity(new Intent(MainActivity.this, activity));
        }
    }

    private void showAdThenFinish() {
        if (interstitialAd != null) {
            interstitialAd.show(MainActivity.this);
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdDismissedFullScreenContent() {
                    loadInterstitialAd();
                    finish();
                }
            });
        } else {
            finish();
        }
    }

    private void restartActivity() {
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // Modified: Updated How To Play popup to only include PLAY mode instructions.
    private void showHowToPlayPopup() {
        // Inflate the custom layout for the popup
        View popupView = LayoutInflater.from(this).inflate(R.layout.layout_how_to_play_popup, null);
        // Build the dialog using MaterialAlertDialogBuilder
        final androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(popupView)
            .create();
        // Allow dismiss when touching outside the card
        dialog.setCanceledOnTouchOutside(true);

        // Get references from the popup view
        TextView tvHeading = popupView.findViewById(R.id.tvHeadingPopup);
        TextView tvInstruction = popupView.findViewById(R.id.tvInstructionPopup);
        View buttonContainer = popupView.findViewById(R.id.buttonContainer);
        MaterialButton btnSingleWord = popupView.findViewById(R.id.btnSingleWordPopup);
        // Removed: Paragraph mode button from popup
        // MaterialButton btnParagraph = popupView.findViewById(R.id.btnParagraphPopup);
        // Optionally hide the paragraph mode button if present in the layout
        // btnParagraph.setVisibility(View.GONE);

        // Set heading text (using difficulty.otf via XML in the layout)
        tvHeading.setText("How To Play");

        // Set click listener for PLAY (formerly Single Word Mode) button
        btnSingleWord.setOnClickListener(view -> {
            String instruction = "INSTRUCTIONS:\n\n" +
                    "1. Choose your difficulty: Easy, Medium, or Hard.\n" +
                    "2. Hit 'Start' to begin the timer.\n" +
                    "3. Type the displayed word correctly to earn points.\n" +
                    "4. Accumulate points before the timer runs out.\n" +
                    "5. Challenge yourself to beat your high score!\n\n" +
                    "Good luck!";
            tvInstruction.setText(instruction);
            buttonContainer.setVisibility(View.GONE); // Hide buttons after selection
        });

        // Removed: Paragraph mode instructions listener
        // btnParagraph.setOnClickListener(view -> {
        //     String instruction = "PARAGRAPH MODE:\n\n" +
        //             "Type the entire paragraph exactly as shown before the timer expires. " +
        //             "Earn +4 points for each correctly typed paragraph. " +
        //             "Keep going until time runs out.";
        //     tvInstruction.setText(instruction);
        //     buttonContainer.setVisibility(View.GONE);
        // });

        // Show the dialog popup
        dialog.show();
    }
}