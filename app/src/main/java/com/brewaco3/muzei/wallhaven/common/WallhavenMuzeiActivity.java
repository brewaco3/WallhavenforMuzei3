package com.brewaco3.muzei.wallhaven.common;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.brewaco3.muzei.wallhaven.WallhavenInstrumentation;

public class WallhavenMuzeiActivity extends AppCompatActivity {

    private WallhavenInstrumentation mWallhavenInstrumentation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mWallhavenInstrumentation = new WallhavenInstrumentation();
        super.onCreate(savedInstanceState);
    }

    @NonNull
    protected WallhavenInstrumentation requireInstrumentation() {
        WallhavenInstrumentation instrumentation = getInstrumentation();
        if (instrumentation == null) {
            throw new IllegalStateException("Activity " + this + "not prepared.");
        }
        return instrumentation;
    }

    @Nullable
    protected WallhavenInstrumentation getInstrumentation() {
        return mWallhavenInstrumentation;
    }

}
