package com.idkjava.thelements.rs;

import android.app.Activity;
import android.os.Bundle;
import android.support.v8.renderscript.*;

public class MainActivity extends Activity {

    private RenderScript mRS;
    private SandView mSandView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRS = RenderScript.create(this);
        mSandView = (SandView) findViewById(R.id.sand_view);
        mSandView.setRS(mRS);
    }
}
