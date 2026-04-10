package com.example.soundfriends.utils;

import android.view.View;

public class ToggleShowHideUI {
    public static void toggleShowUI(boolean state, View view){
        if(state){
            view.setVisibility(view.VISIBLE);
        } else view.setVisibility(view.GONE);
    }
}
