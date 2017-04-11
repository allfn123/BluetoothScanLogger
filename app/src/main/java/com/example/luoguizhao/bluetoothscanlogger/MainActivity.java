package com.example.luoguizhao.bluetoothscanlogger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.BoolRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    //界面控件变量
    ToggleButton tb_bluetooth;
    Button btn_search,btn_import,btn_export,btn_open,btn_save,btn_exit,btn_init,btn_his;
    ListView lv_btDevices;
    TextView tv_state,tv_scanstate;

    //本机蓝牙适配器变量
    private BluetoothAdapter BA;

    //IO相关变量
    private FileOutputStream fos = null;
    private ObjectOutputStream oos = null;

    private FileInputStream fis = null;
    private ObjectInputStream ois = null;

    //相关文件地址常量
    private static String FOLDER = "/mnt/sdcard/documents/";//csv文件和序列化文件存储目录
    private static String SER_FILE = FOLDER + "SER.ser";//序列化文件地址及文件名
    private static String IMPORT_CSV_FILE = FOLDER + "import.csv";//导入csv文件地址及文件名
    private static String EXPORT_CSV_FILE = FOLDER + "export.csv";//导出csv文件地址及文件名
    private static String DEVICES_NAME_CSV_FILE = FOLDER + "name.csv";

    //ListView处理相关
    /*存储运行时使用的蓝牙设备信息集合*/
    private Set<BtDevice> devicesSet = new HashSet<BtDevice>();
    /*important*/

    //存储要显示在ListView中的内容
    final ArrayList<HashMap<String,Object>> mylist = new ArrayList<HashMap<String,Object>>();

    //配对ListView中内容和mylist中内容的Adapter
    SimpleAdapter mSchedule = null;

    //签到模式变量
    //0:默认为已知蓝牙地址
    //1:未知蓝牙地址，待获取
    //2:导入上一次签到情况
    //3:所有设备列表上的设备都已经签到，停止签到
    private int MODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //蓝牙开关设置
        tb_bluetooth = (ToggleButton) findViewById(R.id.tb_bluetooth);
        tb_bluetooth.setChecked(false);
        tb_bluetooth.setOnClickListener(new ClickEvent());

        //普通按钮初始化
        //搜索按钮
        btn_search = (Button) findViewById(R.id.btn_search);
        btn_search.setOnClickListener(new ClickEvent());
        //导入csv文件按钮
        btn_import = (Button) findViewById(R.id.btn_import);
        btn_import.setOnClickListener(new ClickEvent());
        //导出csv文件按钮（内容包括签到情况）
        btn_export = (Button) findViewById(R.id.btn_export);
        btn_export.setOnClickListener(new ClickEvent());
        //打开序列化文件按钮
        btn_open = (Button) findViewById(R.id.btn_open);
        btn_open.setOnClickListener(new ClickEvent());
        //将devicesSet保存为序列化文件按钮（内容不包括签到情况）
        btn_save = (Button) findViewById(R.id.btn_save);
        btn_save.setOnClickListener(new ClickEvent());
        //退出按钮
        btn_exit = (Button) findViewById(R.id.btn_exit);
        btn_exit.setOnClickListener(new ClickEvent());
        //初次识别获取地址按钮
        btn_init = (Button) findViewById(R.id.btn_init);
        btn_init.setOnClickListener(new ClickEvent());
        //查看签到历史按钮
        btn_his = (Button) findViewById(R.id.btn_his);
        btn_his.setOnClickListener(new ClickEvent());

        //蓝牙状态显示
        tv_state = (TextView) findViewById(R.id.tv_state);
        tv_scanstate = (TextView) findViewById(R.id.tv_scanstate);

        //签到列表相关：
        //lv_btDevices是ListView控件
        //mylist是保存要显示到lv_btDevices中的内容
        //mSchedule是连接lv_btDevices和mylist的桥梁（Adapter），使用Map配对
        lv_btDevices = (ListView) findViewById(R.id.lv_btDevices);
        mylist.clear();//刚启动程序，先不显示信息，将mylist清空
        mSchedule = new SimpleAdapter(MainActivity.this,mylist, R.layout.my_devicelist, new String[]{"deviceName","deviceAddress","checkedState"}, new int[]{R.id.deviceName,R.id.deviceAddress,R.id.present});

        lv_btDevices.setAdapter(mSchedule);//将Adapter和ListView关联起来

        //本机蓝牙适配器
        BA = BluetoothAdapter.getDefaultAdapter();
        //先检查本设备是否支持蓝牙
        if (BA == null){
            tb_bluetooth.setChecked(false);
            tb_bluetooth.setEnabled(false);
            btn_search.setEnabled(false);
            Toast.makeText(MainActivity.this,"本设备不支持蓝牙，请推出App",Toast.LENGTH_SHORT).show();
        }else{
            tb_bluetooth.setChecked(BA.isEnabled());
        }

        //如果打开App前没打开蓝牙，则不允许点击搜索按钮
        if (!BA.isEnabled()){
            btn_search.setEnabled(false);
            tv_state.setText("bluetooth closed");
        } else {
            tv_state.setText("bluetooth opened");
        }


        //消息广播
        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//蓝牙状态（开关）变化广播
        intent.addAction(BluetoothDevice.ACTION_FOUND);
        intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        //广播注册

        registerReceiver(stateChanged,intent);


        //本App需要用到 /sdcard/documents/ 这个目录，如果不存在该目录，先创立
        File folder = new File(FOLDER);//FOLDER是csv导入和序列化文件保存目录和
        if (!folder.exists()){
            folder.mkdir();
        }



    }

    //按键处理
    class ClickEvent implements View.OnClickListener{

        @Override
        public void onClick(View v) {
            if (v == btn_exit){                 //退出按钮，退出程序
                MainActivity.this.finish();
            }

            else if (v == tb_bluetooth){      //蓝牙开关按钮，设置蓝牙开关
                if (tb_bluetooth.isChecked()==true){
                    BA.enable();    //打开蓝牙
                }
                else if (tb_bluetooth.isChecked()==false){
                    BA.cancelDiscovery();       //先停止扫描，再关闭蓝牙
                    BA.disable();
                }
            }

            else if (v == btn_search){          //扫描按钮
                if (BA.getState() == BluetoothAdapter.STATE_OFF){       //先确定蓝牙已打开
                    Toast.makeText(getApplicationContext(),"请先打开蓝牙",Toast.LENGTH_SHORT).show();
                    return;
                }
                if (BA.isDiscovering()){        //若已经在扫描，先关闭扫描
                    BA.cancelDiscovery();
                }

                BA.startDiscovery();            //开启扫描
                Toast.makeText(getApplicationContext(), "扫描准备开始", Toast.LENGTH_SHORT).show();
            }

            else if (v == btn_import){        //导入csv文件按钮，从指定csv文件中导入设备名单，包含设备名+设备地址
                MODE = 0;
                File file = new File(IMPORT_CSV_FILE);
                if (!file.exists()){
                    Toast.makeText(MainActivity.this,"文件 /sdcard/documents/import.csv 不存在",Toast.LENGTH_SHORT).show();
                } else {
                    devicesSet = csvProcessor.importCsv(file, MODE);      //打开 /sdcard/documents/import.csv
                }
                flashListView();    //更新lv_btDevices
                Toast.makeText(MainActivity.this,"文件 /sdcard/documents/import.csv 已导入\r\n包含" + devicesSet.size() + "个对象",Toast.LENGTH_SHORT).show();
            }

            else if (v == btn_export){        //导出csv文件按钮，将devicesSet中的设备名+设备地址+签到情况（布尔量）导出到csv文件
                synchronize();//先同步mylist和devicesSet
                boolean b = csvProcessor.exportCsv(new File(EXPORT_CSV_FILE),devicesSet);//保存到 /sdcard/documents/import.csv
                if (b == true){
                    Toast.makeText(MainActivity.this,"列表已导出到 /sdcard/documents/export.csv",Toast.LENGTH_SHORT).show();
                } else{
                    Toast.makeText(MainActivity.this,"列表导出失败",Toast.LENGTH_SHORT).show();
                }
            }

            else if (v == btn_init){
                MODE = 1;
                File file = new File(DEVICES_NAME_CSV_FILE);
                if (!file.exists()){
                    Toast.makeText(MainActivity.this,"文件 /sdcard/documents/name.csv 不存在",Toast.LENGTH_SHORT).show();
                } else {
                    devicesSet = csvProcessor.importCsv(file,MODE);      //打开 /sdcard/documents/name.csv
                }
                flashListView();    //更新lv_btDevices
                Toast.makeText(MainActivity.this,"文件 /sdcard/documents/name.csv 已导入\r\n包含" + devicesSet.size() + "个对象",Toast.LENGTH_SHORT).show();
            }

            else if (v == btn_open){          //导入序列化文件（解序列化），将设备名+设备地址导入到devicesSet中
                MODE = 0;
                try{
                    File file = new File (SER_FILE);
                    if (!file.exists()){    //检查文件是否存在
                        Toast.makeText(MainActivity.this,"文件 /sdcard/documents/SER.ser 不存在",Toast.LENGTH_SHORT).show();
                    } else{
                        fis = new FileInputStream(file);    //打开 /sdcard/documents/SER.ser文件
                        ois = new ObjectInputStream(fis);
                        devicesSet = (Set<BtDevice>) ois.readObject();  //更新devicesSet集合，注意类型转换
                        for(BtDevice dev:devicesSet){
                            dev.setCheckedState(false);
                        }
                    }
                } catch(Exception e){
                    e.printStackTrace();
                } finally {
                    if(fis != null){
                        try{
                            fis.close();
                        }catch (IOException e){
                            e.printStackTrace();
                        }
                    }
                    if(ois != null){
                        try{
                            ois.close();
                        }catch (IOException e){
                            e.printStackTrace();
                        }
                    }
                }
                flashListView();    //更新lv_btDevices
                Toast.makeText(MainActivity.this,"文件 /sdcard/documents/SER.ser 已导入\r\n包含" + devicesSet.size() + "个对象",Toast.LENGTH_SHORT).show();
            }

            else if (v == btn_save){           //导出序列化文件（序列化），将设备名+设备地址导出到SER.ser文件，不包含签到信息
                synchronize();
                try{
                    fos = new FileOutputStream(SER_FILE);       //保存 /sdcard/documents/SER.ser文件
                    oos = new ObjectOutputStream(fos);
                    oos.writeObject(devicesSet);
                    oos.flush();
                } catch(Exception e){
                    e.printStackTrace();
                } finally{
                    if (oos != null){
                        try{
                            oos.close();
                        } catch(IOException e){
                            e.printStackTrace();
                        }
                    }

                    if (fos != null){
                        try{
                            fos.close();
                        } catch(IOException e){
                            e.printStackTrace();
                        }
                    }

                    Toast.makeText(MainActivity.this,"列表设备信息已保存到本地 /sdcard/documents/SER.ser",Toast.LENGTH_SHORT).show();
                }
            }

            else if (v == btn_his){             //查看上一次签到情况
                MODE = 2;
                File file = new File(EXPORT_CSV_FILE);
                if (!file.exists()){
                    Toast.makeText(MainActivity.this,"文件 /sdcard/documents/export.csv 不存在",Toast.LENGTH_SHORT).show();
                } else {
                    devicesSet = csvProcessor.importCsv(file, MODE);      //打开 /sdcard/documents/export.csv
                }
                flashListView();    //更新lv_btDevices
                Toast.makeText(MainActivity.this,"文件 /sdcard/documents/export.csv 已导入\r\n包含" + devicesSet.size() + "个对象",Toast.LENGTH_SHORT).show();

            }
        }
    }

    //更新ListView方法，将元素导入到mylist中，然后通过Adapter（mSchedule）更新ListView (lv_btDevices)
    private void flashListView(){
        mylist.clear();
        for (BtDevice btDevice:devicesSet){
            HashMap<String,Object> map = new HashMap<String,Object>();
            map.put("deviceName",btDevice.getName());
            map.put("deviceAddress",btDevice.getAddress());
            //HashMap<String,Object> map1 = new HashMap<String,Object>();
            map.put("checkedState",btDevice.getCheckedState());
            mylist.add(map);
        }
        mSchedule.notifyDataSetChanged();//更新ListView
    }

    //更新设备状态方法，一旦蓝牙扫描到设备，调用此方法，检查设备名+设备地址是否与mylist（导入）中的某一项匹配，如果有，则将其checkedState置为true
    //传入参数为扫描发现的设备
    /**难点：mylist是一个存放HashMap<String,Object>的ArrayList
     * mylist可以通过get(int index)方法来获取某一个HashMap
     * HashMap可以通过get(String Key)来获取指定键值Key对应的元素-Object
     * HashMap要改变其中某项<Key,Value>的Value，先用remove(Key)方法删除原来的值，再用put(Key,Value)来添加修改的新值
     * @param bluetoothDevice
     */
    private void setState(BluetoothDevice bluetoothDevice,int mode){
        if (mylist.get(0).get("checkedState").equals(true)){
            MODE = 3;
            return;
        }
        for (int i=0; i<mylist.size(); i++){
            if(mylist.get(i).get("deviceName").equals(bluetoothDevice.getName())){  //判断设备名
                if (MODE == 0){
                    if (mylist.get(i).get("deviceAddress").equals(bluetoothDevice.getAddress())){   //判断设备地址
                        if (mylist.get(i).get("checkedState").equals(false)){   //判断设备状态是否已经设置过了（可能有设备被重复扫描）
                            mylist.get(i).remove("checkedState");
                            mylist.get(i).put("checkedState",true);
                            HashMap<String,Object> map = new HashMap<String,Object> ();
                            map = mylist.get(i);
                            mylist.remove(i);//将设置过状态的元素放在最后一项，优化循环
                            mylist.add(map);
                            mSchedule.notifyDataSetChanged();   //更新lv_btDevices
                            break;      //找到其中一项，即可退出循环
                        }
                    }
                } else if (MODE == 1){
                    if (mylist.get(i).get("deviceAddress").equals("")){
                        Toast.makeText(this, "find "+bluetoothDevice.getName()+bluetoothDevice.getAddress(), Toast.LENGTH_SHORT).show();
                        HashMap<String,Object> map = new HashMap<String,Object> ();
                        map = mylist.get(i);   //更新当前设备信息
                        map.put("deviceAddress",bluetoothDevice.getAddress());
                        map.put("checkedState",true);

                        mylist.remove(i);//将设置过状态的元素放在最后一项，优化循环
                        mylist.add(map);
                        mSchedule.notifyDataSetChanged();   //更新lv_btDevices
                        break;      //找到其中一项，即可退出循环
                    }
                }
            }
        }
    }

    //将修改过的mylist内容同步到devicesSet中，在export或save前调用
    private void synchronize(){
        devicesSet.clear();
        for (int i=0;i<mylist.size();i++){
            BtDevice tmpDev = new BtDevice((String)mylist.get(i).get("deviceName"),(String)mylist.get(i).get("deviceAddress"),Boolean.parseBoolean(mylist.get(i).get("checkedState").toString()));
            devicesSet.add(tmpDev);
        }
    }

    //Activity生命结束
    @Override
    protected void onDestroy(){
        this.unregisterReceiver(stateChanged);
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    //广播声明与处理
    private BroadcastReceiver stateChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = null;

            //蓝牙开关状态改变
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                if (BA.isEnabled()){
                    btn_search.setEnabled(true);
                    tv_state.setText("bluetooth opened");
                }
                else{
                    btn_search.setEnabled(false);
                    tv_state.setText("bluetooth closed");
                    tv_scanstate.setText("scan stop");
                }
            }
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                tv_scanstate.setText("scanning...");
                btn_search.setEnabled(false);
            }
            //蓝牙扫描状态改变
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                tv_scanstate.setText("scan finished");
                btn_search.setEnabled(true);
            }
            //扫描到新设备
            if (BluetoothDevice.ACTION_FOUND.equals(action)){
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (MODE ==0 || MODE==1){//如果MODE=3，那么停止对扫描到的设备进行信息配置
                    setState(device,MODE);
                }
            }
        }
    };


}
