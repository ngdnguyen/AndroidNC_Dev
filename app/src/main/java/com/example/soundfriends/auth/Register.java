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
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
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
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

public class Register extends AppCompatActivity {
    EditText edtEmail, edtPassword, edtPassword2;
    Button btnRegister, btnLogIn;
    ImageView btnRegisterWithGoogle;
    FirebaseAuth firebaseAuth;
    ProgressBar pbLoadRegister;
    CheckBox cbTerms;
    TextView tvTerms;
    boolean isTermsRead = false;
    private Toast mToast;

    GoogleSignInOptions googleSignInOptions;
    GoogleSignInClient googleSignInClient;

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if(currentUser != null){
            sharedAuthMethods sharedAuthMethods = new sharedAuthMethods();
            sharedAuthMethods.goHomeActivity(Register.this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        edtEmail = (EditText) findViewById(R.id.edtRegisterEmail);
        edtPassword = (EditText) findViewById(R.id.edtRegisterPassword);
        edtPassword2 = (EditText) findViewById(R.id.edtRegisterPassword2);
        btnRegister = (Button) findViewById(R.id.btnRegister);
        pbLoadRegister = (ProgressBar) findViewById(R.id.pbLoadRegister);
        btnLogIn = (Button) findViewById(R.id.btnLoginInRegister);
        btnRegisterWithGoogle = (ImageView) findViewById(R.id.btnRegisterWithGoogle);
        cbTerms = findViewById(R.id.cbTerms);
        tvTerms = findViewById(R.id.tvTerms);

        // Xử lý khi nhấn vào Checkbox
        cbTerms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isTermsRead) {
                    cbTerms.setChecked(false); // Bỏ tích nếu chưa đọc
                    showToast("Vui lòng đọc 'Điều khoản và Chính sách'");
                }
            }
        });

        tvTerms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTermsDialog();
            }
        });

        //getFirebaseAuth Instance
        firebaseAuth = FirebaseAuth.getInstance();

        //getGoogleSignInOptions
        googleSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.client_id))
                .requestEmail()
                .build();
        //getGoogleSignInClient
        googleSignInClient = GoogleSignIn.getClient(Register.this, googleSignInOptions);

        //1ST WAY: NORMAL AUTHENTICATE
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email, password, password2;
                email = edtEmail.getText().toString();
                password = edtPassword.getText().toString();
                password2 = edtPassword2.getText().toString();

                if(TextUtils.isEmpty(email)||TextUtils.isEmpty(password)||TextUtils.isEmpty(password2)){
                    showToast("Vui lòng điền đầy đủ thông tin");
                    return;
                }
                if (!isTermsRead || !cbTerms.isChecked()) {
                    showToast("Bạn phải đồng ý với điều khoản dịch vụ");
                    return;
                }
                if(!password.equals(password2)){
                    showToast("Mật khẩu nhập lại không trùng khớp");
                    return;
                }

                //set loading state
                ToggleShowHideUI.toggleShowUI(true, pbLoadRegister);
                firebaseAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    // Sign in success, update UI with the signed-in user's information
                                    FirebaseUser user = firebaseAuth.getCurrentUser();
                                    // If sign in success, display a message to the user.
                                    showToast("Đăng ký thành công");
                                    sharedAuthMethods sharedAuthMethods = new sharedAuthMethods();
                                    sharedAuthMethods.goHomeActivity(Register.this);
                                } else {
                                    // If sign in fails, display a message to the user.
                                    FirebaseAuthException exception = (FirebaseAuthException) task.getException();
                                    //validate
                                    String errorMessage = validator.validatorMessage(exception.getErrorCode());
                                    showToast(errorMessage);
                                }
                                //hide loading state
                                ToggleShowHideUI.toggleShowUI(false,pbLoadRegister);
                            }
                        });
            }
        });

        btnLogIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent LoginActivityIntent = new Intent(Register.this, Login.class);
                startActivity(LoginActivityIntent);
                finish();
            }
        });

        //2ND WAY: AUTHENTICATE WITH GOOGLE
        ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                    sharedAuthMethods sharedAuthMethods = new sharedAuthMethods();
                    sharedAuthMethods.GoogleIntentLauncher(result, Register.this, pbLoadRegister, firebaseAuth, googleSignInClient);
            }
        });

        btnRegisterWithGoogle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isTermsRead || !cbTerms.isChecked()) {
                    showToast("Bạn phải đồng ý với điều khoản dịch vụ trước khi tiếp tục với Google");
                    return;
                }
                Intent GoogleSignInIntent = googleSignInClient.getSignInIntent();
                activityResultLauncher.launch(GoogleSignInIntent);
            }
        });
    }

    private void showToast(String message) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(Register.this, message, Toast.LENGTH_SHORT);
        mToast.show();
    }

    private void showTermsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Điều khoản và Chính sách");
        builder.setMessage("1. Bạn phải tôn trọng quyền sở hữu trí tuệ của người khác.\n" +
                "2. Không đăng tải nội dung vi phạm pháp luật.\n" +
                "3. Chúng tôi có quyền xóa bỏ nội dung không phù hợp.\n" +
                "4. Dữ liệu cá nhân của bạn sẽ được bảo mật theo chính sách của chúng tôi.");
        builder.setPositiveButton("Đã hiểu", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isTermsRead = true;
                cbTerms.setEnabled(true); // Cho phép người dùng tích vào checkbox
                dialog.dismiss();
            }
        });
        builder.show();
    }
}