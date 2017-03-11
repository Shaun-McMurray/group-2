package com.group02.guard;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import java.nio.ByteBuffer;
import java.util.Random;

import static com.group02.guard.MainActivity.controlNav;

public class ControllerActivity extends AppCompatActivity {

    Intent batteryStats;
    Bundle batteryBundle;
    Handler batteryHandler;
    int interval = 500;

    ImageButton battery;
    Control analogue;
    TextView showMoveEvent;
    TextView connectionStatus;

    private double analogReadValue = 432;
    private double arduinoVoltage;

    String TAG = "Controller Activity";

    String address = "20:15:10:20:11:37";

    // The thread that does all the work
    BluetoothThread btt;

    // Handler for writing messages to the Bluetooth connection
    Handler writeHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        battery = (ImageButton) findViewById(R.id.batteryButton);
        showMoveEvent = (TextView) findViewById(R.id.coords);
        analogue = (Control) findViewById(R.id.controlView);

        controlNav = (ToggleButton) findViewById(R.id.controlNavigation);
        controlNav.setChecked(true);

        // Initialize the Bluetooth thread, passing in a MAC address
        // and a Handler that will receive incoming messages
        btt = new BluetoothThread(address, new Handler() {

            @Override
            public void handleMessage(Message message) {

                String s = (String) message.obj;

            }
        });

        // Get the handler that is used to send messages
        writeHandler = btt.getWriteHandler();

        // Run the thread
        btt.start();

        analogue.setOnMoveListener(new Control.OnMoveListener()
        {
            public void onMoveInDirection(final double polarAngle) {

                if (polarAngle <= 0) {
                    double speed = analogue.getSpeed(100.0);
                    Log.e("", "" + speed);
                    showMoveEvent.setText("Max move in " + polarAngle + " direction. " + "\nSpeed: " + speed);

                    write(polarAngle, speed);

                } else if (polarAngle > 0) {

                    double speed = analogue.getSpeed(-100.0);
                    Log.e("", "" + speed);
                    showMoveEvent.setText("Max move in " + polarAngle + " direction. " + "\nSpeed: " + speed);


                    write(polarAngle, speed);
                }

            }
        });
        batteryBundle = new Bundle();
        batteryHandler = new Handler();
        batteryStats = new Intent();
        battery.setVisibility(View.VISIBLE);
        startRefreshingBatteryTask();
    }

    public void write(double polarAngle, double speed){
        Message msg = Message.obtain();
        msg.obj = "A" + polarAngle;
        writeHandler.sendMessage(msg);

        Message msgSpeed = Message.obtain();
        msgSpeed.obj = "S" + speed;
        writeHandler.sendMessage(msgSpeed);
    }

    public static byte[] toByteArray(double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).putDouble(value);
        return bytes;
    }

    public static double toDouble(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getDouble();
    }

    /**
     * @author Erik Laurin
     * @purpose is to set battery icon depending on remaining battery level
     */
    public void setBatteryLevel() {
        double voltage = getVoltage(analogReadValue);   //Converts from an analog value to voltage
        voltage /=8; //To get average voltage for each battery

        if(voltage >= 1.40) {   //Sets image depending on battery voltage = approx level based on alkaline AA discharge curve
            battery.clearColorFilter();
            battery.setImageResource(R.drawable.full_battery);
        }
        else if(voltage >= 1.30 && voltage < 1.40) {
            battery.clearColorFilter();
            battery.setImageResource(R.drawable.charged_battery);
        }
        else if(voltage >= 1.20 && voltage < 1.30) {
            battery.clearColorFilter();
            battery.setImageResource(R.drawable.half_charged_battery);
        }
        else if(voltage >= 1.05 && voltage < 1.20) {
            battery.clearColorFilter();
            battery.setImageResource(R.drawable.low_battery);
        }
        if(voltage < 1.05){
            battery.setImageResource(R.drawable.empty_battery);
            battery.setColorFilter(Color.RED);  //For effect
            setCriticalBatteryLevelToast(); //Calls for toast
            //setCriticalBatteryLevelNotification();  //Calls for notification
        }
    }

    /**
     * @author Erik Laurin
     * @purpose is to create a toast to notify the user of the SmartCar's critical battery level
     */
    private void setCriticalBatteryLevelToast(){
        CharSequence text = "Critical battery level!";
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(this, text, duration);
        toast.show();
    }

    /**
     * @author Erik Laurin
     * @purpose is to create a notification to notify the user of the SmartCar's critical battery level
     */
    private void setCriticalBatteryLevelNotification(){
        NotificationCompat.Builder mBuilder= new NotificationCompat.Builder(this);
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        mBuilder.setSmallIcon(R.drawable.notification_battery)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.guard))
                .setContentTitle("SmartCar Critical Battery Level")
                .setAutoCancel(true)
                .setContentText("content")
                .setContentIntent(pendingIntent); //Sets the app to open MainActivity on press on notificaton
        NotificationManager notificationManager= (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, mBuilder.build());
    }

    /**
     * @author Erik Laurin
     * @purpose is to calculate the SmartCar's battery voltage
     * @param analogReadValue analogRead value from Arduino between 0-1024
     * @return returns the SmartCar's battery voltage
     */
    private double getVoltage(double analogReadValue){
        arduinoVoltage = analogReadValue* (5.0 / 1023.0); // Converts the analog reading to voltage
        double voltage = arduinoVoltage * 5.0; //Restores the actual voltage measured (divided by 5 from the voltage divider before entiring the Arduino
        return voltage;
    }

    /**
     * @author Erik Laurin
     * @purpose is to open a new View with battery stats when pressing the battery level indicator
     */
    public void displayBatteryStats(View view) {
        batteryStats = new Intent(this, BatteryActivity.class);
        batteryBundle = new Bundle();    //Sends intent extras in bundle
        batteryBundle.putDouble("EXTRA_ANALOG", analogReadValue);
        batteryBundle.putDouble("EXTRA_ARDUINO_VOLTAGE", arduinoVoltage);
        batteryStats.putExtras(batteryBundle);
        startActivity(batteryStats);
    }

    /**
     * @author Erik Laurin
     * @purpose refreshes the battery level indicator and values for the Battery activity
     */
    Runnable mBatteryRefresher = new Runnable() {
        @Override
        public void run() {
            try {
                analogReadValue = (new Random().nextInt(250)) + 250;
                setBatteryLevel();
                batteryBundle.putDouble("EXTRA_ANALOG", analogReadValue);
                batteryBundle.putDouble("EXTRA_ARDUINO_VOLTAGE", arduinoVoltage);
                batteryStats.putExtras(batteryBundle);
            } finally {
                batteryHandler.postDelayed(mBatteryRefresher, interval);
            }
        }
    };

    void startRefreshingBatteryTask() {
        mBatteryRefresher.run();
    }
}




