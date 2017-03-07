package com.immotor.ble.verify;

import android.Manifest;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.vise.baseble.ViseBluetooth;
import com.vise.baseble.callback.IConnectCallback;
import com.vise.baseble.callback.scan.PeriodScanCallback;
import com.vise.baseble.exception.BleException;
import com.vise.baseble.model.BluetoothLeDevice;
import com.vise.baseble.utils.BleLog;
import com.vise.baseble.utils.BleUtil;


import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static  final int RC_PERMISSION_CAMERA = 1002;

    private String mBluetoothMacAddress;
    private RelativeLayout activityMain;
    private LinearLayout resultLayout;
    private Button btnScan;
    private Button btnConnect;
    private EditText scanResultText;
    private TextView macAddressText;
    private LinearLayout verifyLayout;
    private String scanResultString;

    private ProgressBar bleProgress;
    private ProgressBar bluetoothProgress;

    private ImageView bleResultImg;
    private ImageView bluetoothResultImg;

    private BluetoothLeDevice mDevice;

    private TextView rssiText;

    private boolean isScanning = false;
    private boolean isConnected = false;
    private boolean isBleConnecting = false;

    // for classic bluetooth
    private BluetoothDevice mBluetoothDevice;   // current device
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothReceiver receiver;
    private Class<? extends BluetoothA2dp> mClazz;
    private BluetoothA2dp mA2dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activityMain = (RelativeLayout)findViewById(R.id.activity_main);
        resultLayout = (LinearLayout)findViewById(R.id.result_layout);
        scanResultText = (EditText)findViewById(R.id.scan_result);
        verifyLayout = (LinearLayout) findViewById(R.id.verify_layout);
        macAddressText = (TextView)findViewById(R.id.mac_address_text);

        bleProgress = (ProgressBar)findViewById(R.id.ble_progress);
        bluetoothProgress = (ProgressBar)findViewById(R.id.bluetooth_progress);

        bleResultImg = (ImageView)findViewById(R.id.ble_result_img);
        bluetoothResultImg = (ImageView)findViewById(R.id.bluetooth_result_img);

        rssiText = (TextView)findViewById(R.id.rssi_text);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        btnConnect = (Button) findViewById(R.id.btn_connect);
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String scanText = scanResultText.getText().toString();
                if (TextUtils.isEmpty(scanText)){
                    Snackbar.make(activityMain, getString(R.string.please_input_sid), Snackbar.LENGTH_SHORT).show();
                    return;
                }
                if (scanText.length() < 12){
                    Snackbar.make(activityMain, getString(R.string.sid_format_error), Snackbar.LENGTH_SHORT).show();
                }

                if (isBluetoothConnected()) {
                    bluetoothDisconnect();
                }

                if (isScanning || isConnected) {
                    stopScanning();
                    stopConnecting();
                }else if (!TextUtils.isEmpty(scanText)) {
                    scanResultString = scanText;
                    scanDevice();
                }
            }
        });

        btnScan = (Button)findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                methodRequiresPermissions();
            }
        });

        if(!BleUtil.isBleEnable(this)){
            BleUtil.enableBluetooth(this, 1);
        }

        ViseBluetooth.getInstance().init(getApplicationContext());

        // for classic bluetooth
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        receiver = new BluetoothReceiver();
        registerReceiver(receiver, filter);

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ViseBluetooth.getInstance().disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScanning();
        stopConnecting();
        ViseBluetooth.getInstance().clear();
    }

    @AfterPermissionGranted(RC_PERMISSION_CAMERA)
    private void methodRequiresPermissions() {
        String[] perms = {Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            gotoScanQRCodeScreen();
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permission_required_toast),
                    RC_PERMISSION_CAMERA, perms);
        }
    }

    private void gotoScanQRCodeScreen(){

        if (isBluetoothConnected()) {
            bluetoothDisconnect();
        }
        if (isScanning){
            stopScanning();
        }
        if (isConnected){
            stopConnecting();
        }
        resultLayout.setVisibility(View.GONE);
        verifyLayout.setVisibility(View.GONE);
        bleResultImg.setVisibility(View.GONE);
        bluetoothResultImg.setVisibility(View.GONE);

        IntentIntegrator integrator = new IntentIntegrator(this);//指定当前的activity
        integrator.setOrientationLocked(true);
        integrator.setPrompt(getString(R.string.please_scan_qr_code));
        integrator.setCaptureActivity(ScanActivity.class);
        integrator.initiateScan(IntentIntegrator.QR_CODE_TYPES); //启动扫描
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(TAG, "resultCode Code:"+resultCode);
        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(intentResult != null) {
            scanResultString = intentResult.getContents();
            Log.v(TAG, "scan qr code get :"+ scanResultString);
            resultLayout.setVisibility(View.VISIBLE);
            if (!TextUtils.isEmpty(scanResultString)) {
                // try to connect
                scanResultText.setText(scanResultString);
                scanDevice();
            }else {
                Snackbar.make(activityMain, getString(R.string.nothing), Snackbar.LENGTH_SHORT).show();
            }
        } else {
            Snackbar.make(activityMain, getString(R.string.nothing), Snackbar.LENGTH_SHORT).show();
            super.onActivityResult(requestCode,resultCode,data);
        }
    }

    private void scanDevice(){
        hideSoftKeyboard();
        verifyLayout.setVisibility(View.VISIBLE);
        bleResultImg.setVisibility(View.GONE);
        bluetoothProgress.setVisibility(View.GONE);
        bluetoothResultImg.setVisibility(View.GONE);
        btnConnect.setText(getString(R.string.stop));
        macAddressText.setText(null);
        rssiText.setText(null);
        setProgressStatus(true);
        isScanning = true;
        ViseBluetooth.getInstance().setScanTimeout(-1).startScan(periodScanCallback);
    }


    private PeriodScanCallback periodScanCallback = new PeriodScanCallback() {
        @Override
        public void scanTimeout() {
            BleLog.i("scan timeout");
        }

        @Override
        public void onDeviceFound(BluetoothLeDevice bluetoothLeDevice) {

            if (bluetoothLeDevice != null){
                mDevice = bluetoothLeDevice;
                String bleMacAddress = bluetoothLeDevice.getAddress();
                byte[] scanRecord = bluetoothLeDevice.getScanRecord();
                String scanRecordStr = StringUtils.bytesToHexString(scanRecord);
                Log.v(TAG, " scan record macAddress :"+bleMacAddress);

                // for test
                /*if (bleMacAddress.startsWith("DB:48:04:BB:19:A2")){
                    macAddressText.setText(bleMacAddress);
                    connectBleDevice();
                }*/

                if (scanRecordStr.length() > 58 ) {
                    String manufacturerData = scanRecordStr.substring(46, 58);
                    String manufacturerDataStr = StringUtils.reverseString2(manufacturerData);
                    if (manufacturerDataStr.equalsIgnoreCase(scanResultString)) {
                        macAddressText.setText(bleMacAddress);
                        rssiText.setText(getString(R.string.label_rssi)+ bluetoothLeDevice.getRssi()+"dB");
                        connectBleDevice();
                    }
                }

            }

        }
    };

    private void connectBleDevice(){
        if(!ViseBluetooth.getInstance().isConnected() && !isBleConnecting){
            isBleConnecting = true;
            ViseBluetooth.getInstance().connect(mDevice, false, connectCallback);
        }
    }

    private void setProgressStatus(boolean show){
        if (show){
            bleProgress.setVisibility(View.VISIBLE);
            bleResultImg.setVisibility(View.GONE);
        }else {
            bleProgress.setVisibility(View.GONE);
        }
    }

    private void stopScanning(){
        setProgressStatus(false);
        isScanning = false;
        ViseBluetooth.getInstance().stopScan(periodScanCallback);
        btnConnect.setText(getString(R.string.connect));
        verifyLayout.setVisibility(View.GONE);
    }

    private void stopConnecting(){
        ViseBluetooth.getInstance().disconnect();
        btnConnect.setText(getString(R.string.connect));
        verifyLayout.setVisibility(View.GONE);
    }


    private IConnectCallback connectCallback = new IConnectCallback() {
        @Override
        public void onConnectSuccess(BluetoothGatt gatt, int status) {
            BleLog.v("Connect Success!");
            Snackbar.make(activityMain, "Connect Success!", Snackbar.LENGTH_SHORT).show();
            isScanning = false;
            isConnected = true;
            isBleConnecting = false;
            ViseBluetooth.getInstance().stopScan(periodScanCallback);
            bleResultImg.setVisibility(View.VISIBLE);
            bleProgress.setVisibility(View.GONE);
            bleResultImg.setBackgroundResource(R.mipmap.icon_right);
            btnConnect.setText(getString(R.string.disconnect));

            // start class bluetooth connect
            startBluetoothConnect();
        }

        @Override
        public void onConnectFailure(BleException exception) {
            BleLog.i("Connect Failure!");
            //Snackbar.make(activityMain, "Connect Failure!", Snackbar.LENGTH_SHORT).show();
            isScanning = false;
            isConnected = false;
            isBleConnecting = false;
            /*bleResultImg.setVisibility(View.VISIBLE);
            bleProgress.setVisibility(View.GONE);
            bleResultImg.setBackgroundResource(R.mipmap.icon_wrong);*/
            //btnConnect.setText(getString(R.string.stop));
        }

        @Override
        public void onDisconnect() {
            BleLog.i("Disconnect!");
            Snackbar.make(activityMain, "Disconnect!", Snackbar.LENGTH_SHORT).show();
            isScanning = false;
            isConnected = false;
            isBleConnecting = false;
            bleResultImg.setVisibility(View.VISIBLE);
            bleProgress.setVisibility(View.GONE);
            bleResultImg.setBackgroundResource(R.mipmap.icon_wrong);
            btnConnect.setText(getString(R.string.connect));
        }
    };

    /*********************************** for classic bluetooth  ******************/

    private void startBluetoothConnect(){
        // 判断是否已经配对
        //获得已配对的远程蓝牙设备的集合
        boolean isPaired = false;
        String scanText = scanResultText.getText().toString();
        mBluetoothMacAddress = StringUtils.convertMacAddress(scanText);
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        if(devices.size()>0) {
            for (Iterator<BluetoothDevice> it = devices.iterator(); it.hasNext(); ) {
                BluetoothDevice device = it.next();
                //打印出远程蓝牙设备的物理地址
                System.out.println(device.getAddress());
                if (device.getAddress().equalsIgnoreCase(mBluetoothMacAddress)){
                    mBluetoothDevice = device;
                    isPaired = true;
                    break;
                }
            }
        }
        if (isPaired) {
            ConnectThread connectThread = new ConnectThread(mBluetoothDevice);
            connectThread.start();
        }else {
            mBluetoothAdapter.startDiscovery();
        }
    }


    class BluetoothReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {  //发现新设备
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //deviceList.add(bluetoothDevice);
                //showBluetoothDevice();
                // start connect:
                if (bluetoothDevice.getAddress().equalsIgnoreCase(mBluetoothMacAddress)) {
                    int status = bluetoothDevice.getBondState();
                    if (status == BluetoothDevice.BOND_NONE){       // 尚未配对，请求配对
                        createBond(bluetoothDevice.getClass(), bluetoothDevice);
                    }
                }
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {  // 扫描开始
                Toast.makeText(MainActivity.this, getString(R.string.start_discover), Toast.LENGTH_SHORT).show();
                bluetoothProgress.setVisibility(View.VISIBLE);
                bluetoothResultImg.setVisibility(View.GONE);
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) { // 扫描结束
                Toast.makeText(MainActivity.this, getString(R.string.discover_finish), Toast.LENGTH_SHORT).show();
                bluetoothProgress.setVisibility(View.GONE);
                bluetoothResultImg.setVisibility(View.VISIBLE);
                if (isBluetoothConnected()){
                    bluetoothResultImg.setBackgroundResource(R.mipmap.icon_right);
                }else {
                    bluetoothResultImg.setBackgroundResource(R.mipmap.icon_wrong);
                }
            }else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {   //配对状态变化
                mBluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (mBluetoothDevice.getBondState()) {
                    case BluetoothDevice.BOND_BONDING://正在配对
                        Log.v(TAG, "正在配对......");

                        break;
                    case BluetoothDevice.BOND_BONDED://配对结束
                        Log.v(TAG, "完成配对");
                        mBluetoothAdapter.cancelDiscovery();
                        // 开始连接
                        ConnectThread connectThread = new ConnectThread(mBluetoothDevice);
                        connectThread.start();
                        break;
                    case BluetoothDevice.BOND_NONE://取消配对/未配对
                        Log.v(TAG, "取消配对");
                    default:
                        break;
                }
            }

        }
    }


    public boolean createBond(Class btClass, BluetoothDevice btDevice){
        try {
            Method createBondMethod = btClass.getMethod("createBond");
            Boolean returnValue = (Boolean) createBondMethod.invoke(btDevice);
            return returnValue.booleanValue();
        }catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }


    private class ConnectThread extends Thread {

        BluetoothDevice bluetoothDevice;

        ConnectThread(BluetoothDevice bluetoothDevice){
            this.bluetoothDevice = bluetoothDevice;
        }
        public void run() {
            connectA2DP();
        }
    }

    private void connectA2DP() {
        if (!isBluetoothConnected()) {
            //在listener中完成A2DP服务的调用
            mBluetoothAdapter.getProfileProxy(this, new ConnServiceListener(), BluetoothProfile.A2DP);
        }
    }

    public class ConnServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            //use reflect method to get the Hide method "connect" in BluetoothA2DP
            BluetoothA2dp a2dp = (BluetoothA2dp) proxy;
            mA2dp = a2dp;
            //a2dp.isA2dpPlaying(mBTDevInThread);
            Class<? extends BluetoothA2dp> clazz = a2dp.getClass();
            mClazz = clazz;
            Method method_Connect;
            //通过BluetoothA2DP隐藏的connect(BluetoothDevice btDev)函数，打开btDev的A2DP服务
            try {
                method_Connect = mClazz.getMethod("connect",BluetoothDevice.class);
                //invoke(object receiver,object... args)
                //2.这步相当于调用函数,invoke需要传入args：BluetoothDevice的实例
                method_Connect.invoke(a2dp, mBluetoothDevice);

                bluetoothProgress.setVisibility(View.GONE);
                bluetoothResultImg.setVisibility(View.VISIBLE);
                bluetoothResultImg.setBackgroundResource(R.mipmap.icon_right);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                bluetoothProgress.setVisibility(View.GONE);
                bluetoothResultImg.setVisibility(View.VISIBLE);
                bluetoothResultImg.setBackgroundResource(R.mipmap.icon_wrong);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            // TODO Auto-generated method stub
            Log.e(TAG, "on service disconnect");
            mClazz = null;
        }

    }

    private boolean isBluetoothConnected(){
        if (mBluetoothAdapter == null){
            return false;
        }
        return mBluetoothAdapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED;
    }

    private void bluetoothDisconnect(){
        //mClazz = mA2dp.getClass();
        if (mClazz == null || mA2dp == null){
            Log.e("error", "mClazz OR mA2dp is null");
            return;
        }
        Method method_disconnect;
        //通过BluetoothA2DP隐藏的connect(BluetoothDevice btDev)函数，打开btDev的A2DP服务
        try {

            //1.这步相当于定义函数
            method_disconnect = mClazz.getMethod("disconnect",BluetoothDevice.class);
            //invoke(object receiver,object... args)
            //2.这步相当于调用函数,invoke需要传入args：BluetoothDevice的实例
            method_disconnect.invoke(mA2dp, mBluetoothDevice);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void hideSoftKeyboard(){
        View view = getWindow().peekDecorView();
        if (view != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }


}
