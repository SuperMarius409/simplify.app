package com.learn.simplify.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.learn.simplify.R;
import com.learn.simplify.database.DbQuery;
import com.learn.simplify.entities.LoadingView;
import com.learn.simplify.listeners.MyCompleteListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private Button main_signup_button;
    private Button main_login_button;
    private Button main_facebook_button;
    private Button main_google_button;
    private FirebaseAuth firebaseAuth;
    private CallbackManager callbackManager;
    private LoadingView loadingViewMain;
    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);

        loadingViewMain = findViewById(R.id.loadingViewMain);

        // Initialize Firebase Auth
        firebaseAuth = FirebaseAuth.getInstance();

        // Initialize Facebook SDK
        FacebookSdk.sdkInitialize(getApplicationContext());
        callbackManager = CallbackManager.Factory.create();

        // Animation
        RelativeLayout relativeLayout = findViewById(R.id.container);
        AnimationDrawable animationDrawable = (AnimationDrawable) relativeLayout.getBackground();
        animationDrawable.setEnterFadeDuration(2000);
        animationDrawable.setExitFadeDuration(4000);
        animationDrawable.start();

        // Use WindowCompat to set fitsSystemWindows to false
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        } else {
            // For versions below Android 11
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        // Initialize UI components
        main_signup_button = findViewById(R.id.main_signup_button);
        main_login_button = findViewById(R.id.main_login_button);
        main_facebook_button = findViewById(R.id.main_facebook_button);
        main_google_button = findViewById(R.id.main_google_button);

        // Set onClick listeners
        main_signup_button.setOnClickListener(v -> animateButtonOnClick(main_signup_button, Signup.class));

        main_login_button.setOnClickListener(v -> animateButtonOnClick(main_login_button, Login.class));

        main_facebook_button.setOnClickListener(v -> {
            animateButtonOnClickNull(main_facebook_button);
            handleFacebookLogin();
        });

        main_google_button.setOnClickListener(v -> {
            animateButtonOnClickNull(main_google_button);
            buttonGoogleSignIn(v);
        });

    }

    // Move Firebase Authentication check to onResume
    @Override
    protected void onResume() {
        super.onResume();
        DbQuery.g_firestore = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            String email = user.getEmail();
            String username = user.getDisplayName();
            String photourl = String.valueOf(user.getPhotoUrl());
            saveUserData(username, email, photourl);
            openHomeActivity();
        }
    }

    // Handle Google Sign-in failure with proper feedback
    public void buttonGoogleSignIn(View view) {

        // Initialize GoogleSignInOptions
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        // Initialize GoogleSignInClient
        GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Sign out any previously signed-in account to ensure account selection
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Start the sign-in intent to prompt the user to choose their Google account
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

    }
    private void handleFacebookLogin() {
        LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList("email", "public_profile"));
        LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                // Handle successful login
                GraphRequest request = GraphRequest.newMeRequest(loginResult.getAccessToken(), new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        try {
                            String userId = object.getString("id");
                            String userName = object.getString("name");
                            String userEmail = object.getString("email");
                            String profilePicUrl = object.getJSONObject("picture").getJSONObject("data").getString("url");

                            // Authenticate user with Firebase using Facebook credentials
                            AuthCredential credential = FacebookAuthProvider.getCredential(loginResult.getAccessToken().getToken());
                            startLoadingAnimation();
                            firebaseAuth.signInWithCredential(credential)
                                    .addOnCompleteListener(MainActivity.this, new OnCompleteListener<AuthResult>() {
                                        @Override
                                        public void onComplete(@NonNull Task<AuthResult> task) {
                                            if (task.isSuccessful()) {
                                                FirebaseUser user = firebaseAuth.getCurrentUser();
                                                assert user != null;
                                                user.updateProfile(new UserProfileChangeRequest.Builder().setPhotoUri(Uri.parse(profilePicUrl)).build());
                                                saveUserData(userName, userEmail, profilePicUrl);
                                                openHomeActivity();
                                            } else {
                                                // If sign in fails, display a message to the user.
                                                stopLoadingAnimation();
                                                Log.w(TAG, "signInWithCredential:failure", task.getException());
                                                Toast.makeText(MainActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    });

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
                Bundle parameters = new Bundle();
                parameters.putString("fields", "id,name,email,picture.type(large)");
                request.setParameters(parameters);
                request.executeAsync();
            }

            @Override
            public void onCancel() {
                // Handle cancelled login
            }

            @Override
            public void onError(FacebookException error) {
                // Handle login error
            }
        });
    }

    // Handle onActivityResult
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> googleTask = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = googleTask.getResult(ApiException.class);
                if (account != null) {
                    String displayName = account.getDisplayName();
                    String email = account.getEmail();
                    String photoUrl = Objects.requireNonNull(account.getPhotoUrl()).toString();
                    String idToken = account.getIdToken();

                    if (idToken != null) {
                        // Got an ID token from Google. Use it to authenticate
                        AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(idToken, null);
                        startLoadingAnimation();
                        firebaseAuth.signInWithCredential(firebaseCredential).addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                // Sign in success, update UI with the signed-in user's information
                                FirebaseUser user = firebaseAuth.getCurrentUser();
                                assert user != null;
                                user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(displayName).build());
                                saveUserData(displayName, email, photoUrl);
                                DbQuery.createUserData(email, displayName, photoUrl, new MyCompleteListener() {
                                    @Override
                                    public void onSucces() {
                                        openHomeActivity();
                                    }
                                    @Override
                                    public void onFailure() {
                                        Log.e(TAG, "Something went wrong");
                                    }
                                });
                            } else {
                                stopLoadingAnimation();
                                Log.e(TAG, "signInWithCredential:failure", task.getException());
                            }
                        });
                    }
                }
            } catch (ApiException e) {
                if (e.getStatusCode() == GoogleSignInStatusCodes.SIGN_IN_CANCELLED) {
                    // Handle sign-in cancellation
                    Log.e(TAG, "Google sign-in canceled");
                } else {
                    // Handle other Google sign-in failures
                    Log.e(TAG, "Google sign-in failed", e);
                }
            }
        }
    }

    private void animateButtonOnClick(Button button, Class<?> targetActivityClass) {
        button.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> button.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(50)
                        .withEndAction(() -> {
                            // Open the specified activity
                            Intent intent = new Intent(button.getContext(), targetActivityClass);
                            button.getContext().startActivity(intent);
                        })
                        .start())
                .start();
    }

    private void animateButtonOnClickNull(Button button) {
        button.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(() -> button.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(50)
                        .start())
                .start();
    }

    private void openHomeActivity() {
        Intent intent = new Intent(this, Home.class);
        startActivity(intent);
        stopLoadingAnimation();
        finish();
    }

    private void saveUserData(String userName, String userEmail, String userPhoto) {
        SharedPreferences.Editor editor = getSharedPreferences("UserData", MODE_PRIVATE).edit();
        editor.putString("userName", userName);
        editor.putString("userEmail", userEmail);
        editor.putString("userPhoto", userPhoto);
        editor.apply();
        Log.e(TAG, userName);
        Log.e(TAG, userEmail);
        Log.e(TAG, userPhoto);
    }

    private void startLoadingAnimation() {
        loadingViewMain.startLoadingAnimation(R.raw.uni_leaf_loading, true);
    }

    private void stopLoadingAnimation() {
        loadingViewMain.stopLoadingAnimation();
    }
}
