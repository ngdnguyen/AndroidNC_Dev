package com.example.soundfriends;

import android.app.Application;
import android.widget.Toast;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

public class SoundFriendsApp extends Application {

    public static final boolean USE_EMULATOR = false;  //true để dùng firebase emulater, false dùng firebase thật
    public static final String EMULATOR_HOST = "10.201.55.223";

    @Override
    public void onCreate() {
        super.onCreate();
        
        if (USE_EMULATOR) {
            // Xóa cấu hình mặc định từ google-services.json để tránh xung đột
            try {
                for (FirebaseApp app : FirebaseApp.getApps(this)) {
                    app.delete();
                }
            } catch (Exception e) {}

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId("demo-no-project")
                    .setApplicationId("demo-no-project")
                    .setApiKey("fake-api-key")
                    // Chỉ định rõ namespace demo-no-project
                    .setDatabaseUrl("http://" + EMULATOR_HOST + ":2002?ns=demo-no-project")
                    .setStorageBucket("demo-no-project.appspot.com")
                    .build();
            
            FirebaseApp.initializeApp(this, options);
            Toast.makeText(this, "Đang kết nối Firebase Demo Emulator", Toast.LENGTH_SHORT).show();

            try {
                // Kết nối Database
                FirebaseDatabase database = FirebaseDatabase.getInstance();
                database.useEmulator(EMULATOR_HOST, 2002);
                database.setPersistenceEnabled(false);

                // Kết nối Auth
                FirebaseAuth.getInstance().useEmulator(EMULATOR_HOST, 2000);
                
                // Kết nối Storage
                FirebaseStorage.getInstance().useEmulator(EMULATOR_HOST, 2003);
                
                // Kết nối Firestore
                FirebaseFirestore.getInstance().useEmulator(EMULATOR_HOST, 2001);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Chế độ Production
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
            }
        }
    }
}