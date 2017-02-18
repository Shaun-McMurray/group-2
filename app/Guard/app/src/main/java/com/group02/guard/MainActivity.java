package com.group02.guard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button manualControlButton;
        manualControlButton = (Button) findViewById(R.id.manualControlButton);
        manualControlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ControllerActivity.class);
                MainActivity.this.startActivity(i);
            }
        });

        Button mapsButton;
        mapsButton = (Button) findViewById(R.id.mapsButton);
        mapsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MapsActivity.class);
                MainActivity.this.startActivity(i);
            }
        });


    }



}
