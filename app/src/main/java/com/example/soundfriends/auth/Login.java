package com.example.soundfriends.auth;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.soundfriends.MainActivity;
import com.example.soundfriends.R;
import com.example.soundfriends.utils.ToggleShowHideUI;
import com.example.soundfriends.utils.validator;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

public class Login extends AppCompatActivity {
    EditText edtEmail, edtPassword;
    Button btnRegister, btnLogIn;
    ImageView btnLoginWithGoogle;
    FirebaseAuth firebaseAuth;
    ProgressBar pbLoadLogin;

    GoogleSignInOptions googleSignInOptions;
    GoogleSignInClient googleSignInClient;

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if(currentUser != null){
            sharedAuthMethods sharedAuthMethods = new sharedAuthMethods();
            sharedAuthMethods.goHomeActivity(Login.this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        edtEmail = (EditText) findViewById(R.id.edtLoginEmail);
        edtPassword = (EditText) findViewById(R.id.edtLoginPassword);
        btnRegister = (Button) findViewById(R.id.btnRegisterInLogin);
        pbLoadLogin = (ProgressBar) findViewById(R.id.pbLoadLogin);
        btnLogIn = (Button) findViewById(R.id.btnLogin);
        btnLoginWithGoogle = (ImageView) findViewById(R.id.logInWithGoogle);

        //getFirebaseAuth Instance
        firebaseAuth = FirebaseAuth.getInstance();

        //getGoogleSignInOptions
        googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.client_id))
                .requestEmail()
                .build();
        //getGoogleSignInClient
        googleSignInClient = GoogleSignIn.getClient(Login.this, googleSignInOptions);

        btnLogIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email, password, password2;
                email = edtEmail.getText().toString();
                password = edtPassword.getText().toString();

                if(TextUtils.isEmpty(email)||TextUtils.isEmpty(password)){
                    Toast.makeText(Login.this, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                    return;
                }

                //set loading state
                ToggleShowHideUI.toggleShowUI(true, pbLoadLogin);

                firebaseAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    // Sign in success, update UI with the signed-in user's information
                                    FirebaseUser user = firebaseAuth.getCurrentUser();
                                    // If sign in success, display a message to the user.
                                    Toast.makeText(Login.this, "Đăng nhập thành công",
                                            Toast.LENGTH_SHORT).show();
                                    //goHome Intent
                                    sharedAuthMethods sharedAuthMethods = new sharedAuthMethods();
                                    sharedAuthMethods.goHomeActivity(Login.this);
                                }
                                else {
                                    // If sign in fails, display a message to the user.
                                    FirebaseException exception = (FirebaseException) task.getException();
                                    String errorMessage = validator.validatorMessage(exception.getMessage());
                                    Log.d("Error", errorMessage);
                                    Toast.makeText(Login.this, errorMessage,
                                            Toast.LENGTH_LONG).show();
                                }
                                //hide loading state
                                ToggleShowHideUI.toggleShowUI(false, pbLoadLogin);
                            }
                        });
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent RegisterActivityIntent = new Intent(Login.this, Register.class);
                startActivity(RegisterActivityIntent);
                finish();
            }
        });

        //2ND WAY: AUTHENTICATE WITH GOOGLE
        ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                sharedAuthMethods sharedAuthMethods = new sharedAuthMethods();
                sharedAuthMethods.GoogleIntentLauncher(result, Login.this, pbLoadLogin, firebaseAuth, googleSignInClient);
            }
        });

        btnLoginWithGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent GoogleSignInIntent = googleSignInClient.getSignInIntent();
                activityResultLauncher.launch(GoogleSignInIntent);
            }
        });
    }
}