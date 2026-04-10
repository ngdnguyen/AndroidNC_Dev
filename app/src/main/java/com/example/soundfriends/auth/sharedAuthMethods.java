package com.example.soundfriends.auth;

import static android.app.Activity.RESULT_OK;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

public class sharedAuthMethods extends AppCompatActivity {
    public void GoogleIntentLauncher(ActivityResult result, Context context, View pbLoading, FirebaseAuth firebaseAuth, GoogleSignInClient googleSignInClient) {
        if (result.getResultCode() == RESULT_OK){
            ToggleShowHideUI.toggleShowUI(true, pbLoading);
            Intent intentData = result.getData();
            Task<GoogleSignInAccount> googleSignInAccountTask = GoogleSignIn.getSignedInAccountFromIntent(intentData);
            try {
                GoogleSignInAccount account = googleSignInAccountTask.getResult(ApiException.class);
                if (account != null) {
                    //Show Merge Normal Account with Google Account
                    AlertDialog.Builder alertMergeAccounts = new AlertDialog.Builder(context);
                    alertMergeAccounts.setTitle("Thông báo");
                    alertMergeAccounts.setMessage("Nếu Email tài khoản Google và Email tài khoản thường bị trùng nhau, thì tài khoản thường và tài khoản Google sẽ liên kết với nhau và bạn chỉ có thể đăng nhập qua tài khoản Google. Bạn có chắc muốn tiếp tục?");
                    alertMergeAccounts.setIcon(R.mipmap.ic_launcher_round);
                    alertMergeAccounts.setPositiveButton("Có, hợp nhất", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            handleSignInFirebaseViaGoogle(account, context, pbLoading,firebaseAuth, googleSignInClient);
                        }
                    });
                    alertMergeAccounts.setNegativeButton("Không, dùng tài khoản khác", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //remove Google logins account automatically
                            googleSignInClient.revokeAccess();
                        }
                    });
                    alertMergeAccounts.show();
                }
            } catch (ApiException e) {
                Toast.makeText(context, e.getStatusCode(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void handleSignInFirebaseViaGoogle(GoogleSignInAccount account, Context context, View pbLoading, FirebaseAuth firebaseAuth, GoogleSignInClient googleSignInClient) {
        GoogleAuthCredential credential = (GoogleAuthCredential) GoogleAuthProvider.getCredential(account.getIdToken(), null);

        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(task.isSuccessful()){
                            Toast.makeText(context, "Đăng nhập tự động qua Google thành công!", Toast.LENGTH_LONG).show();
                            goHomeActivity(context);
                        } else {
                            String errorMessage = validator.validatorMessage(task.getException().getMessage());
                            Toast.makeText(context, errorMessage , Toast.LENGTH_LONG).show();
                        }

                        //remove Google logins account automatically
                        googleSignInClient.revokeAccess();
                        //set Load
                        ToggleShowHideUI.toggleShowUI(false, pbLoading);
                    }
                });
    }

    public void goHomeActivity(Context context) {
        Intent MainActivityIntent = new Intent(context, MainActivity.class);
        context.startActivity(MainActivityIntent);
    }
}
