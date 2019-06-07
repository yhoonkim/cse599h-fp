package edu.washington.cs.cse599h.android.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;

public class BleUtils {
    private final static String TAG = BleUtils.class.getSimpleName();

    public static final int STATUS_BLE_ENABLED = 0;
    public static final int STATUS_BLUETOOTH_NOT_AVAILABLE = 1;
    public static final int STATUS_BLE_NOT_AVAILABLE = 2;
    public static final int STATUS_BLUETOOTH_DISABLED = 3;

    private static ResetBluetoothAdapter sResetHelper;

    // Use this check to determine whether BLE is supported on the device.  Then you can  selectively disable BLE-related features.
    public static int getBleStatus(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return STATUS_BLE_NOT_AVAILABLE;
        }

        final BluetoothAdapter adapter = getBluetoothAdapter(context);
        // Checks if Bluetooth is supported on the device.
        if (adapter == null) {
            return STATUS_BLUETOOTH_NOT_AVAILABLE;
        }

        if (!adapter.isEnabled()) {
            return STATUS_BLUETOOTH_DISABLED;
        }

        return STATUS_BLE_ENABLED;
    }

    // Initializes a Bluetooth adapter.
    public static BluetoothAdapter getBluetoothAdapter(Context context) {
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            return null;
        } else {
            return bluetoothManager.getAdapter();
        }
    }

    public static void enableWifi(boolean enable, Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(enable);
    }

    public static boolean isWifiEnabled(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return wifiManager.isWifiEnabled();
    }

    //region Reset Bluetooth Adapter
    public static void resetBluetoothAdapter(Context context, ResetBluetoothAdapterListener listener) {
        if (sResetHelper == null) {
            sResetHelper = new ResetBluetoothAdapter(context, listener);
        } else {
            Log.w(TAG, "Reset already in progress");
        }
    }

    public static void cancelBluetoothAdapterReset(Context context) {
        if (isBluetoothAdapterResetInProgress()) {
            sResetHelper.cancel(context);
            sResetHelper = null;
        }
    }

    private static boolean isBluetoothAdapterResetInProgress() {
        return sResetHelper != null;
    }

    public interface ResetBluetoothAdapterListener {
        void resetBluetoothCompleted();
    }

    private static class ResetBluetoothAdapter {
        //        private Context mContext;
        private ResetBluetoothAdapterListener mListener;

        private final BroadcastReceiver mBleAdapterStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                    onBleAdapterStatusChanged(state, context);
                }
            }
        };


        ResetBluetoothAdapter(Context context, ResetBluetoothAdapterListener listener) {
            // mContext = context;
            mListener = listener;

            // Set receiver
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(mBleAdapterStateReceiver, filter);

            // Reset
            BluetoothAdapter bleAdapter = BleUtils.getBluetoothAdapter(context);
            if (bleAdapter != null && bleAdapter.isEnabled()) {
                boolean isDisablingBle = bleAdapter.disable();
                if (isDisablingBle) {
                    Log.w(TAG, "Reset ble adapter started. Waiting to turn off");
                    // Now wait for BluetoothAdapter.ACTION_STATE_CHANGED notification
                } else {
                    Log.w(TAG, "Can't disable bluetooth adapter");
                    resetCompleted(context);
                }
            }
        }

        private void onBleAdapterStatusChanged(int state, Context context) {
            switch (state) {
                case BluetoothAdapter.STATE_OFF: {
                    // Turn off has finished. Turn it on again
                    Log.d(TAG, "Ble adapter turned off. Turning on");
                    BluetoothAdapter bleAdapter = BleUtils.getBluetoothAdapter(context);
                    if (bleAdapter != null) {
                        bleAdapter.enable();
                    }
                    break;
                }
                case BluetoothAdapter.STATE_TURNING_OFF:
                    break;
                case BluetoothAdapter.STATE_ON: {
                    Log.d(TAG, "Ble adapter turned on. Reset completed");
                    // Turn on has finished.
                    resetCompleted(context);
                    break;
                }
                case BluetoothAdapter.STATE_TURNING_ON:
                    break;
            }
        }

        private void resetCompleted(Context context) {
            context.unregisterReceiver(mBleAdapterStateReceiver);
            if (mListener != null) {
                mListener.resetBluetoothCompleted();
            }
            sResetHelper = null;
        }

        public void cancel(Context context) {
            try {
                context.unregisterReceiver(mBleAdapterStateReceiver);
            } catch (IllegalArgumentException ignored) {

            }
        }

    }
    //endregion


    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        if (bytes != null) {
            char[] hexChars = new char[bytes.length * 2];
            for (int j = 0; j < bytes.length; j++) {
                int v = bytes[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        } else return null;
    }

    public static String byteToHex(byte value) {
        if (value > 0x0f) {
            char[] hexChars = new char[2];
            hexChars[0] = hexArray[value >>> 4];
            hexChars[1] = hexArray[value & 0x0F];
            return new String(hexChars);
        } else {
            return "" + hexArray[value & 0x0F];
        }
    }

    public static String bytesToHex2(byte[] bytes) {
        StringBuilder stringBuffer = new StringBuilder();
        for (byte aByte : bytes) {
            String charString = String.format("%02X", (byte) aByte);

            stringBuffer.append(charString).append(" ");
        }
        return stringBuffer.toString();
    }

    public static String bytesToText(byte[] bytes, boolean simplifyNewLine) {
        String text = new String(bytes, Charset.forName("UTF-8"));
        if (simplifyNewLine) {
            text = text.replaceAll("(\\r\\n|\\r)", "\n");
        }
        return text;
    }

    public static String stringToHex(String string) {
        return bytesToHex(string.getBytes());
    }

    public static String bytesToHexWithSpaces(byte[] bytes) {
        StringBuilder newString = new StringBuilder();
        for (byte aByte : bytes) {
            String byteHex = String.format("%02X", (byte) aByte);
            newString.append(byteHex).append(" ");

        }
        return newString.toString().trim();
    }

    public static String getUuidStringFromByteArray(byte[] bytes) {
        StringBuilder buffer = new StringBuilder();
        for (byte aByte : bytes) {
            buffer.append(String.format("%02x", aByte));
        }
        return buffer.toString();
    }

    public static UUID getUuidFromByteArrayBigEndian(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        long high = bb.getLong();
        long low = bb.getLong();
        UUID uuid = new UUID(high, low);
        return uuid;
    }

    public static UUID getUuidFromByteArraLittleEndian(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long high = bb.getLong();
        long low = bb.getLong();
        UUID uuid = new UUID(low, high);
        return uuid;
    }

    // region Send Data to UART
    public static void sendData(String text, BleManager bleManager, BluetoothGattService uartService) {
        final byte[] value = text.getBytes(Charset.forName("UTF-8"));
        sendDataWithCRC(value, bleManager, uartService);
    }

    public static final String UUID_TX = "6e400002-b5a3-f393-e0a9-e50e24dcca9e";
    public static final int kTxMaxCharacters = 20;

    public static void sendData(byte[] data, BleManager bleManager, BluetoothGattService uartService) {
        if (uartService != null) {
            // Split the value into chunks (UART service has a maximum number of characters that can be written )
            for (int i = 0; i < data.length; i += kTxMaxCharacters) {
                final byte[] chunk = Arrays.copyOfRange(data, i, Math.min(i + kTxMaxCharacters, data.length));
                bleManager.writeService(uartService, UUID_TX, chunk);
            }
        } else {
            Log.w(TAG, "Uart Service not discovered. Unable to send data");
        }
    }

    // Send data to UART and add a byte with a custom CRC
    public static void sendDataWithCRC(byte[] data, BleManager bleManager, BluetoothGattService uartService) {

        // Calculate checksum
        byte checksum = 0;
        for (byte aData : data) {
            checksum += aData;
        }
        checksum = (byte) (~checksum);       // Invert

        // Add crc to data
        byte dataCrc[] = new byte[data.length + 1];
        System.arraycopy(data, 0, dataCrc, 0, data.length);
        dataCrc[data.length] = checksum;

        // Send it
        Log.d(TAG, "Send to UART: " + BleUtils.bytesToHexWithSpaces(dataCrc));
        sendData(dataCrc, bleManager, uartService);
    }
    // endregion
}
