package chris.sensortag;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID LIGHT_SENSOR_SERVICE = UUID.fromString("f000aa70-0451-4000-b000-000000000000");
    public static final UUID LIGHT_SENSOR_DATA_CHARACTERISTIC = UUID.fromString("f000aa71-0451-4000-b000-000000000000");
    public static final UUID LIGHT_SENSOR_CONFIG_CHARACTERISTIC = UUID.fromString("f000aa72-0451-4000-b000-000000000000");
    public static final UUID IR_TEMP_SERVICE = UUID.fromString("f000aa00-0451-4000-b000-000000000000");
    public static final UUID IR_TEMP_DATA_CHARACTERISTIC = UUID.fromString("f000aa01-0451-4000-b000-000000000000");
    public static final UUID IR_TEMP_CONFIG_CHARACTERISTIC = UUID.fromString("f000aa02-0451-4000-b000-000000000000");

    private boolean doneDescriptors = false;
    private boolean doneChars = false;

    private ProgressDialog progressDialog;

    private BluetoothAdapter mBluetoothAdapter;
    private final static int REQUEST_ENABLE_BT = 1;
    private boolean mScanning;
    private Handler mHandler;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothGatt mBluetoothGatt;


    private ListView lv;
    private LinearLayout tempLayout;
    private LinearLayout brightLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get temperature and brightness views
        tempLayout = (LinearLayout)findViewById(R.id.tempLayout);
        brightLayout = (LinearLayout)findViewById(R.id.brightLayout);


        mHandler = new Handler();
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        lv = (ListView)findViewById(R.id.listView);
        lv.setAdapter(mLeDeviceListAdapter);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
                if (device == null) return;
                scanLeDevice(false);
                mBluetoothGatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
            }
        });

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            scanLeDevice(true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(this, "You have to enable Bluetooth first!", Toast.LENGTH_LONG).show();
            finish();
            return;
        } else {
            scanLeDevice(true);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }


    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mLeDeviceListAdapter.addDevice(device);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };


    // Various callback methods defined by the BLE API.
    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                    int newState) {

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog = ProgressDialog.show(MainActivity.this, "Please wait ...",
                                        "Getting Services", true);
                            }
                        });
                        mBluetoothGatt.discoverServices();

                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Hide device list and show temp and brightness
                                lv.setVisibility(View.VISIBLE);
                                tempLayout.setVisibility(View.INVISIBLE);
                                brightLayout.setVisibility(View.INVISIBLE);
                            }
                        });

                    }
                }

                @Override
                // New services discovered
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Hide device list and show temp and brightness
                                lv.setVisibility(View.INVISIBLE);
                                tempLayout.setVisibility(View.VISIBLE);
                                brightLayout.setVisibility(View.VISIBLE);

                                progressDialog.dismiss();
                            }
                        });

                        // Enable notification for light sensor
                        BluetoothGattService s1 = mBluetoothGatt.getService(LIGHT_SENSOR_SERVICE);
                        BluetoothGattCharacteristic c1 = s1.getCharacteristic(LIGHT_SENSOR_DATA_CHARACTERISTIC);
                        BluetoothGattDescriptor d1 = c1.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                        mBluetoothGatt.setCharacteristicNotification(c1, true);
                        d1.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(d1);

                    } else {
                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                public void onDescriptorWrite (BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    super.onDescriptorWrite(gatt, descriptor, status);

                    if (doneDescriptors) {
                        // Enable light sensor measurements
                        BluetoothGattService s = mBluetoothGatt.getService(LIGHT_SENSOR_SERVICE);
                        BluetoothGattCharacteristic c = s.getCharacteristic(LIGHT_SENSOR_CONFIG_CHARACTERISTIC);
                        c.setValue(new byte[]{1});
                        mBluetoothGatt.writeCharacteristic(c);
                    } else {
                        // Enable IR temp sensor notification
                        BluetoothGattService s2 = mBluetoothGatt.getService(IR_TEMP_SERVICE);
                        BluetoothGattCharacteristic c2 = s2.getCharacteristic(IR_TEMP_DATA_CHARACTERISTIC);
                        BluetoothGattDescriptor d2 = c2.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                        mBluetoothGatt.setCharacteristicNotification(c2, true);
                        d2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        mBluetoothGatt.writeDescriptor(d2);
                        doneDescriptors = true;
                    }
                }

                @Override
                public void onCharacteristicWrite (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    if (!doneChars) {
                        // Enable IR temp sensor measurements
                        BluetoothGattService s = mBluetoothGatt.getService(IR_TEMP_SERVICE);
                        BluetoothGattCharacteristic c = s.getCharacteristic(IR_TEMP_CONFIG_CHARACTERISTIC);
                        c.setValue(new byte[]{1});
                        mBluetoothGatt.writeCharacteristic(c);
                        doneChars = true;
                    }

                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {

                    byte[] value = characteristic.getValue();

                    // If notification from light sensor
                    if (characteristic.getUuid().equals(LIGHT_SENSOR_DATA_CHARACTERISTIC)) {

                        final String s = Double.toString(extractLightSensorData(value));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView textView = (TextView) findViewById(R.id.txtBright);
                                textView.setText(s + " Lux");
                            }
                        });

                    // If notification from temp sensor
                    } else if (characteristic.getUuid().equals(IR_TEMP_DATA_CHARACTERISTIC)) {

                        final String s = Double.toString(extractAmbientTemperature(value));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView textView = (TextView) findViewById(R.id.txtTemp);
                                textView.setText(s + " \u00b0C");
                            }
                        });
                    }
                }
            };

    private double extractLightSensorData(byte[] v) {
        int mantissa;
        int exponent;

        Integer sfloat= shortUnsignedAtOffset(v, 0);

        mantissa = sfloat & 0x0FFF;
        exponent = (sfloat >> 12) & 0xFF;

        double output;
        double magnitude = Math.pow(2.0f, exponent);
        output = (mantissa * magnitude);
        return output / 100.0f;
    }

    private double extractAmbientTemperature(byte[] v) {
        int offset = 2;
        return shortUnsignedAtOffset(v, offset) / 128.0;
    }

    private static Integer shortUnsignedAtOffset(byte[] c, int offset) {
        Integer lowerByte = (int) c[offset] & 0xFF;
        Integer upperByte = (int) c[offset+1] & 0xFF;
        return (upperByte << 8) + lowerByte;
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText("Unkown Device");
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
