package dev.dev7.example;

import static android.Manifest.permission.POST_NOTIFICATIONS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import java.util.Objects;

import dev.dev7.lib.v2ray.V2rayController;
import dev.dev7.lib.v2ray.utils.AppConfigs;

public class MainActivity extends AppCompatActivity {
    private Button connection;
    private TextView connection_speed, connection_traffic, connection_time, server_delay, connected_server_delay, connection_mode;
    private EditText v2ray_json_config;
    private SharedPreferences sharedPreferences;
    private BroadcastReceiver v2rayBroadCastReceiver;


    @SuppressLint({"SetTextI18n", "UnspecifiedRegisterReceiverFlag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        connection = findViewById(R.id.btn_connection);
        connection_speed = findViewById(R.id.connection_speed);
        connection_time = findViewById(R.id.connection_duration);
        connection_traffic = findViewById(R.id.connection_traffic);
        server_delay = findViewById(R.id.server_delay);
        connection_mode = findViewById(R.id.connection_mode);
        connected_server_delay = findViewById(R.id.connected_server_delay);
        v2ray_json_config = findViewById(R.id.v2ray_json_config);
        TextView core_version = findViewById(R.id.core_version);
        // Checking the previous state value each time the activity is opened
        switch (V2rayController.getConnectionState().toString()) {
            case "V2RAY_CONNECTED":
                connection.setText("CONNECTED");
                break;
            case "V2RAY_DISCONNECTED":
                connection.setText("DISCONNECTED");
                break;
            case "V2RAY_CONNECTING":
                connection.setText("CONNECTING");
                break;
            default:
                break;
        }
        connection_mode.setText("connection mode : " + V2rayController.getConnectionMode() + " (tap to toggle)");
        core_version.setText("v" + BuildConfig.VERSION_NAME + ", " + V2rayController.getCoreVersion());
        // initialize shared preferences for save or reload config
        sharedPreferences = getSharedPreferences("conf", MODE_PRIVATE);
        // reload previous config to edit text
        v2ray_json_config.setText(sharedPreferences.getString("v2ray_config", getDefaultConfig()));

        connection.setOnClickListener(view -> {
            // check application have permission to create tunnel
            if (V2rayController.IsPreparedForConnection(MainActivity.this)) {
                // save config to shared preference
                sharedPreferences.edit().putString("v2ray_config", v2ray_json_config.getText().toString()).apply();
                if (V2rayController.getConnectionState() == AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED) {
                    // in StartV2ray function we can set remark to show that on notification.
                    // StartV2ray function steel need json config of v2ray. Unfortunately, it does not accept URI or base64 type at the moment.
                    V2rayController.StartV2ray(getApplicationContext(), "Default", v2ray_json_config.getText().toString(), null);
                    connected_server_delay.setText("connected server delay : measuring...");
                    //getConnectedV2rayServerDelay function need a text view for now
                    new Handler().postDelayed(() -> V2rayController.getConnectedV2rayServerDelay(getApplicationContext(), connected_server_delay), 1000);
                } else {
                    connected_server_delay.setText("connected server delay : wait for connection");
                    V2rayController.StopV2ray(getApplicationContext());
                }
            } else {
                // Prepare application permission (vpnService And POST_NOTIFICATION)
                prepareForConnection();
            }
        });
        // Check the connection delay of connected config.
        connected_server_delay.setOnClickListener(view -> {
            if (V2rayController.getConnectionState() == AppConfigs.V2RAY_STATES.V2RAY_CONNECTED) {
                connected_server_delay.setText("connected server delay : measuring...");
                new Handler().postDelayed(() -> V2rayController.getConnectedV2rayServerDelay(getApplicationContext(), connected_server_delay), 1000);
            } else {
                Toast.makeText(MainActivity.this, "Can`t measure delay , connection is down.", Toast.LENGTH_LONG).show();
            }
        });
        // Another way to check the connection delay of a config without connecting to it.
        server_delay.setOnClickListener(view -> {
            server_delay.setText("server delay : measuring...");
            new Handler().post(() -> server_delay.setText("server delay : " + V2rayController.getV2rayServerDelay(v2ray_json_config.getText().toString())));
        });
        // The connection mode determines whether the entire phone is tunneled or whether an internal proxy (http , socks) is run
        connection_mode.setOnClickListener(view -> {
            // Oh,Sorry you can`t change connection mode when you have active connection. I can`t solve that for now. ):
            if (V2rayController.getConnectionState() != AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED) {
                Toast.makeText(this, "You can change the connection type only when you do not have an active connection.", Toast.LENGTH_LONG).show();
                return;
            }
            if (V2rayController.getConnectionMode() == AppConfigs.V2RAY_CONNECTION_MODES.PROXY_ONLY) {
                V2rayController.changeConnectionMode(AppConfigs.V2RAY_CONNECTION_MODES.VPN_TUN);
            } else {
                V2rayController.changeConnectionMode(AppConfigs.V2RAY_CONNECTION_MODES.PROXY_ONLY);
            }
            connection_mode.setText("connection mode : " + V2rayController.getConnectionMode() + " (tap to toggle)");
        });

        //I tested several different ways to send information from the connection process side
        // to other places (such as interfaces, AIDL and singleton ,...) apparently the best way
        // to send information is broadcast.
        // So v2ray library will be broadcast information with action V2RAY_CONNECTION_INFO.
        v2rayBroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                connection_time.setText("connection time : " + Objects.requireNonNull(intent.getExtras()).getString("DURATION"));
                connection_speed.setText("connection speed : " + intent.getExtras().getString("UPLOAD_SPEED") + " | " + intent.getExtras().getString("DOWNLOAD_SPEED"));
                connection_traffic.setText("connection traffic : " + intent.getExtras().getString("UPLOAD_TRAFFIC") + " | " + intent.getExtras().getString("DOWNLOAD_TRAFFIC"));
                switch (Objects.requireNonNull(intent.getExtras().getSerializable("STATE")).toString()) {
                    case "V2RAY_CONNECTED":
                        connection.setText("CONNECTED");
                        break;
                    case "V2RAY_DISCONNECTED":
                        connection.setText("DISCONNECTED");
                        break;
                    case "V2RAY_CONNECTING":
                        connection.setText("CONNECTING");
                        break;
                    default:
                        break;
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(v2rayBroadCastReceiver, new IntentFilter("V2RAY_CONNECTION_INFO"), RECEIVER_EXPORTED);
        } else {
            registerReceiver(v2rayBroadCastReceiver, new IntentFilter("V2RAY_CONNECTION_INFO"));
        }

    }

    public static String getDefaultConfig() {
        return "";
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(v2rayBroadCastReceiver);
    }

    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Toast.makeText(this, "Permission granted please click again on connection button", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Permission not granted.", Toast.LENGTH_LONG).show();
        }
    });

    private void prepareForConnection() {
        Intent vpnServicePrepareIntent = VpnService.prepare(this);
        if (vpnServicePrepareIntent != null) {
            activityResultLauncher.launch(vpnServicePrepareIntent);
            return;
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{POST_NOTIFICATIONS}, 101);
            }
        }
    }

}