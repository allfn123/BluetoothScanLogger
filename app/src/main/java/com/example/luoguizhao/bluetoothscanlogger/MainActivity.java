package com.example.luoguizhao.bluetoothscanlogger;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Author: Trevor DeWitt(小钊君)
 * Date: 2017-4-11 22:09
 * Intro:
 *      1. 这是一个基于蓝牙扫描发现设备的近程签到App
 *      2. 蓝牙的开关控制者扫描按钮和扫描模式开关，蓝牙打开后，可点击SEARCH进行扫描，可修改manual<-->auto来修改扫描模式
 *          manual：手动模式，点击开始扫描后14s后自动停止扫描
 *          auto：自动模式，点击开始扫描后，在系统规定扫描时间结束后自动重新扫描，实现多轮次扫描
 *      3. 在扫描期间，SEARCH无法使用，直到扫描结束
 *      4. 强制扫描结束：点击蓝牙开关关闭蓝牙，或者在auto模式下，调至manual模式
 *      5. 一切名单文件均放在/sdcard/documents/  目录中，文件名均有限制
 *      6. 对于只有设备名名单（/sdcard/documents/name.csv)，点击INIT导入名单，然后在蓝牙打开的前提下使用扫描，
 *          一旦有扫描到的设备与名单中的设备名匹配，则更新名单的设备地址信息，并默认该设备此次签到成功。
 *          点击EXPORT导出本次签到情况，导出到两个文件:
 *          /sdcard/documents/export.csv
 *          /sdcard/documents/export_"time".csv
 *      7. 对于已知设备名及对应设备地址的名单(/sdcard/documents/import.csv)，点击IMPORT导入名单，然后在蓝牙打开前提下使用扫描，
 *          一旦有扫描到的设备与名单中的设备名及设备地址同时匹配时，则认为该设备此次签到成功。
 *          点击EXPORT导出本次签到情况，导出到两个文件:
 *          /sdcard/documents/export.csv
 *          /sdcard/documents/export_"time".csv
 *      8. 对于任何导入的名单，可以点击SAVE序列化名单保存到本地（/sdcard/documents/SER.ser)，
 *          并可点击OPEN打开本地的序列化名单(/sdcard/documents/SER.ser)
 *      9. 如果已经导出过csv文件(/sdcard/documents/export.csv)，可点击HIS查看最近一次export的签到情况
 *      10. 对于带时间戳的导出csv文件（/sdcard/documents/export_"time".csv），其作用是防止后面的导出文件覆盖了之前的签到情况
 *      11. 复制出所有的export_"time".csv文件，即可得到完整的签到情况。
 *      12. 初次安装App，在扫描时有概率会遇到程序崩溃的情况，原因未明。之后使用不再出现。
 */

public class MainActivity extends AppCompatActivity {

    //界面控件变量
    private ToggleButton tb_bluetooth,tb_auto;
    private Button btn_search,btn_import,btn_export,btn_open,btn_save,btn_exit,btn_init,btn_his;
    private ListView lv_btDevices;
    private TextView tv_state,tv_scanstate;

    //本机蓝牙适配器变量
    private BluetoothAdapter BA;

    //IO相关变量
    //output
    private FileOutputStream fos = null;
    private ObjectOutputStream oos = null;
    //input
    private FileInputStream fis = null;
    private ObjectInputStream ois = null;

    //相关文件地址常量
    private static String FOLDER = "/mnt/sdcard/documents/";                    //csv文件和序列化文件存储目录
    private static String SER_FILE = FOLDER + "SER.ser";                        //序列化文件地址及文件名，可用于读取保存与本地的名单，名单内容包括设备名+设备地址，非文本
    private static String IMPORT_CSV_FILE = FOLDER + "import.csv";              //导入csv文件地址及文件名，可用于读取来自外部的名单，名单内容包括设备名+设备地址
    private static String EXPORT_CSV_FILE = FOLDER + "export.csv";              //导出csv文件地址及文件名，内容包括设备名+设备地址+签到情况，可用于历史查询签到情况（最近一次）
    private static String EXPORT_REALTIME_CSV_FILE_PART = FOLDER + "export_";   //导出带导出时间的csv文件，文件名未完整，待加入time+".csv"，可用于备份签到情况，避免覆盖
    private static String DEVICES_NAME_CSV_FILE = FOLDER + "name.csv";          //初始化名单csv文件，可用于导入未知设备地址的设备名名单

    //ListView处理相关
    /**
    * 存储运行时使用的蓝牙设备信息集合
    * 使用Set保证导入名单中没用重复内容
    * */
    private Set<BtDevice> devicesSet = new HashSet<BtDevice>();
    /*important*/

    //存储要显示在ListView中的内容
    //每个内容包括三个键值对：
    //1. deviceName--设备名（String,String)
    //2. deviceAddress--设备地址(String,String)
    //3. checkedState--签到情况(String,boolean)
    final private ArrayList<HashMap<String,Object>> mylist = new ArrayList<HashMap<String,Object>>();

    //配对ListView中内容和mylist中内容的Adapter，连接两者的桥梁
    //每当mylist中内容发生改变，可调用mSchedule.notifyDataSetChanged()方法更新ListView
    private SimpleAdapter mSchedule = null;

    //签到模式变量
    //0:默认为已知设备地址（对应IMPORT和OPEN）
    //1:未知设备地址，待获取（对应INIT）
    //2:导入上一次签到情况（对应HIS）
    //3:所有设备列表上的设备都已经签到，停止签到（减少工作量）
    private int MODE = 0;

    //系统时间相关，用于导出csv文件时给出文件名中的时间，保证导出文件的唯一性
    private Date currentDate;
    //时间格式转换器，例：2017_4_11_22_09_12
    final private SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //蓝牙开关设置
        tb_bluetooth = (ToggleButton) findViewById(R.id.tb_bluetooth);
        tb_bluetooth.setChecked(false);     //App启动时默认关闭，在获取蓝牙适配器后再去判断蓝牙的开关
        tb_bluetooth.setOnClickListener(new ClickEvent());
        //自动签到开关
        tb_auto = (ToggleButton) findViewById(R.id.tb_auto);
        tb_auto.setChecked(false);          //App启动时默认手动模式（manual）
        tb_auto.setOnClickListener(new ClickEvent());


        //普通按钮初始化
        //搜索按钮：启动蓝牙扫描
        btn_search = (Button) findViewById(R.id.btn_search);
        btn_search.setOnClickListener(new ClickEvent());
        //导入csv文件按钮：导入含有设备名+设备地址的csv文件：/sdcard/documents/import.csv
        btn_import = (Button) findViewById(R.id.btn_import);
        btn_import.setOnClickListener(new ClickEvent());
        //导出csv文件按钮：导出含有设备名+设备地址+签到情况的csv文件：/sdcard/documents/export.csv  /sdcard/documents/export_"time".csv
        btn_export = (Button) findViewById(R.id.btn_export);
        btn_export.setOnClickListener(new ClickEvent());
        //打开序列化文件按钮 /sdcard/documents/SER.ser
        btn_open = (Button) findViewById(R.id.btn_open);
        btn_open.setOnClickListener(new ClickEvent());
        //将devicesSet保存为序列化文件按钮（内容不包括签到情况）  /sdcard/documents/SER.ser
        btn_save = (Button) findViewById(R.id.btn_save);
        btn_save.setOnClickListener(new ClickEvent());
        //退出按钮:退出App
        btn_exit = (Button) findViewById(R.id.btn_exit);
        btn_exit.setOnClickListener(new ClickEvent());
        //初次识别获取地址按钮：导入只有设备名的csv文件  /sdcard/documents/name.csv
        btn_init = (Button) findViewById(R.id.btn_init);
        btn_init.setOnClickListener(new ClickEvent());
        //查看签到历史按钮：导入含有设备名+设备地址+最近一次签到情况的csv文件：/sdcard/documents/export.csv
        btn_his = (Button) findViewById(R.id.btn_his);
        btn_his.setOnClickListener(new ClickEvent());


        //蓝牙状态显示
        tv_state = (TextView) findViewById(R.id.tv_state);          //显示蓝牙开关状态：bluetooth opened<-->bluetooth closed
        tv_scanstate = (TextView) findViewById(R.id.tv_scanstate);  //显示蓝牙扫描状态：scan stop<-->scanning...

        //签到列表相关：
        //lv_btDevices是ListView控件
        //mylist是保存要显示到lv_btDevices中的内容
        //mSchedule是连接lv_btDevices和mylist的桥梁（Adapter），使用Map配对
        //每个Map内容包括三个键值对：
        //1. deviceName--设备名（String,String)
        //2. deviceAddress--设备地址(String,String)
        //3. checkedState--签到情况(String,boolean)
        lv_btDevices = (ListView) findViewById(R.id.lv_btDevices);
        mylist.clear();//刚启动程序，先不显示信息，将mylist清空
        mSchedule = new SimpleAdapter(MainActivity.this,mylist, R.layout.my_devicelist, new String[]{"deviceName","deviceAddress","checkedState"}, new int[]{R.id.deviceName,R.id.deviceAddress,R.id.present});
        lv_btDevices.setAdapter(mSchedule);//将Adapter和ListView关联起来

        //获取本机蓝牙适配器
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
            tb_auto.setEnabled(false);
            tv_state.setText("bluetooth closed");
        } else {
            tv_state.setText("bluetooth opened");
        }


        //消息广播
        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);        //蓝牙状态（开关）变化广播
        intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);    //蓝牙开始扫描广播
        intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);   //蓝牙扫描结束广播
        intent.addAction(BluetoothDevice.ACTION_FOUND);                 //发现蓝牙设备广播
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
            //退出按钮，退出程序
            if (v == btn_exit){
                MainActivity.this.finish();
            }

            //蓝牙开关按钮，设置蓝牙开关
            else if (v == tb_bluetooth){
                if (tb_bluetooth.isChecked()==true){
                    BA.enable();                //打开蓝牙
                }
                else if (tb_bluetooth.isChecked()==false){
                    BA.cancelDiscovery();       //先停止扫描，再关闭蓝牙
                    if (tb_auto.isChecked()){   //一旦蓝牙关闭，将扫描模式调回手动
                        tb_auto.setChecked(false);
                    }
                    BA.disable();               //关闭蓝牙
                }
            }

            //扫描按钮
            else if (v == btn_search){
                // TODO: 2017/4/11
                //在扫描过程中，不允许btn_search可用
                //否则会因为监听了BluetoothAdapter的ACTION_DISCOVERY_STARTED和ACTION_DISCOVERY_FINISHED广播而导致程序奔溃
                //原因未明
                btn_search.setEnabled(false);   

                if (BA.getState() == BluetoothAdapter.STATE_OFF){       //先确定蓝牙已打开
                    Toast.makeText(getApplicationContext(),"请先打开蓝牙",Toast.LENGTH_SHORT).show();
                    return;
                }
                if (BA.isDiscovering()){                                //若已经在扫描，先关闭扫描
                    BA.cancelDiscovery();
                }

                BA.startDiscovery();                                    //开启扫描
            }

            //导入csv文件按钮，从指定csv文件中导入设备名单，包含设备名+设备地址
            else if (v == btn_import){
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

            //导出csv文件按钮，将devicesSet中的设备名+设备地址+签到情况（布尔量）导出到csv文件
            else if (v == btn_export){
                synchronize();//先同步mylist和devicesSet

                //time
                currentDate = new Date(System.currentTimeMillis());
                String time = formatter.format(currentDate);

                //保存到两个csv文件中，内容一样
                //1. /sdcard/documents/export.csv 为最新保存的一个签到记录，便于历史查看（只能查看最近一次）
                //2. /sdcard/documents/export_"time".csv 其中time部分为保存时的时间，不同时间保存到不同的csv文件，避免以前的签到csv文件内容被覆盖
                boolean b1 = csvProcessor.exportCsv(new File(EXPORT_CSV_FILE),devicesSet);//保存到 /sdcard/documents/export.csv
                boolean b2 = csvProcessor.exportCsv(new File(EXPORT_REALTIME_CSV_FILE_PART+time+".csv"),devicesSet);//将签到情况备份，带时间戳
                if (b1 == true && b2 == true){
                    Toast.makeText(MainActivity.this,"列表已导出到 /sdcard/documents/export.csv\r\n／sdcard/documents/export_"+time+".csv",Toast.LENGTH_SHORT).show();
                } else{
                    Toast.makeText(MainActivity.this,"列表导出失败",Toast.LENGTH_SHORT).show();
                }
            }

            //导入未知设备地址csv文件，仅含有设备名
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

            //导入序列化文件（解序列化），将设备名+设备地址导入到devicesSet中
            else if (v == btn_open){
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

            //导出序列化文件（序列化），将设备名+设备地址导出到SER.ser文件，不包含签到信息
            else if (v == btn_save){
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

            //查看上一次签到情况
            else if (v == btn_his){
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

            //自动/手动扫描开关
            else if (v == tb_auto){
                if (!tb_auto.isChecked()){          //如果从自动模式转为手动模式，那么要求先关闭扫描。
                    if (BA.isDiscovering()){
                        BA.cancelDiscovery();
                    }
                }
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
            MODE = 3;       //当导入的名单都签到完后，则使MODE=3，这时若扫描到新设备也不再调用本方法
            return;
        }
        for (int i=0; i<mylist.size(); i++){
            if(mylist.get(i).get("deviceName").equals(bluetoothDevice.getName())){  //判断设备名
                if (MODE == 0){                 //默认模式，设备名+设备地址同时匹配时才签到成功
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
                } else if (MODE == 1){          //仅知道设备名，设备地址后加入
                    if (mylist.get(i).get("deviceAddress").equals("")){
                        //Toast.makeText(this, "find "+bluetoothDevice.getName()+bluetoothDevice.getAddress(), Toast.LENGTH_SHORT).show();
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
    //这是因为签到信息仅对mylist更新，而export和save都是对devicesSet进行
    private void synchronize(){
        devicesSet.clear();
        for (int i=0;i<mylist.size();i++){
            BtDevice tmpDev = new BtDevice((String)mylist.get(i).get("deviceName"),(String)mylist.get(i).get("deviceAddress"),Boolean.parseBoolean(mylist.get(i).get("checkedState").toString()));
            devicesSet.add(tmpDev);
        }
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
                    //蓝牙打开后，btn_search和tb_auto都可以使用，并通过tv_state显示蓝牙状态
                    btn_search.setEnabled(true);
                    tb_auto.setEnabled(true);
                    tv_state.setText("bluetooth opened");
                }
                else{
                    //蓝牙关闭后，btn_search和tb_auto都不可以使用，并通过tv_state显示蓝牙状态
                    btn_search.setEnabled(false);
                    tb_auto.setEnabled(false);
                    tv_state.setText("bluetooth closed");
                    tv_scanstate.setText("scan stop");
                }
            }
            //蓝牙开始扫描广播
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
                tv_scanstate.setText("scanning...");
                //蓝牙扫描过程中必须将btn_search临时禁用掉，否则会导致程序在扫描时奔溃，原因未知
                btn_search.setEnabled(false);
            }
            //蓝牙扫描结束广播
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
                if (tb_auto.isChecked()){       //使用自动模式时，当前一次扫描结束时，自动开启新的扫描，实现连续扫描
                    BA.startDiscovery();
                } else{                         //使用手动模式时，当本次扫描结束后，在tv_scanstate显示扫描结束，并使btn_search可用
                    tv_scanstate.setText("scan finished");
                    btn_search.setEnabled(true);
                }
            }
            //扫描到新设备
            if (BluetoothDevice.ACTION_FOUND.equals(action)){
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // MODE=0，默认模式，已知设备名+设备地址，当两者都匹配时才认为签到成功
                // MODE=1，仅知道设备名，当设备名匹配时，添加设备地址，并默认签到成功
                // 如果MODE=3，那么停止对扫描到的设备进行信息配置
                // MODE=2，仅与读入有关，与这里无关
                if (MODE ==0 || MODE==1){
                    setState(device,MODE);
                }
            }
        }
    };

    //Activity生命结束
    @Override
    protected void onDestroy(){
        this.unregisterReceiver(stateChanged);
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
