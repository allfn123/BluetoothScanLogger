package com.example.luoguizhao.bluetoothscanlogger;

import java.io.Serializable;

/**
 * Created by luoguizhao on 2017/4/9.
 * 该类存储每台蓝牙设备的信息，包括蓝牙设备名+蓝牙设备地址+设备签到情况，提供响应的getter和setter
 * 实现了两个构造器：BtDevice()和BtDevice(String,String)
 * 覆盖了toString(),hashCode()和equals()
 * 实现了Serializable接口，该类的对象可以序列化-反序列化
 */

public class BtDevice implements Serializable {
    private static final long serialVersionUID = 496169334L;//序列化唯一识别码

    private String deviceName;//设别名
    private String deviceAdress;//设备地址
    private boolean checkedState=false;//设备签到情况

    //默认构造器
    public BtDevice(){

    }

    //重载构造器，带设备名和设备地址两个参数
    public BtDevice(String name,String address,boolean state){
        deviceName = name;
        deviceAdress = address;
        checkedState = state;
    }

    public String getName(){
        return deviceName;
    }

    public void setName(String name){
        deviceName = name;
    }

    public String getAddress(){
        return deviceAdress;
    }

    public void setAdress(String adress){
        deviceAdress = adress;
    }

    public boolean getCheckedState(){
        return checkedState;
    }

    public void setCheckedState(boolean b){
        checkedState = b;
    }

    //覆盖了toString()方法，返回的是设备名和设备地址组成的字符串，以"|"隔开
    public String toString(){
        return deviceName + "|" + deviceAdress;
    }

    //在使用集合容器存储该类的变量时，先通过hashCode()判断两个对象是否相同，如果hashCode()值相同，则再根据equals()判断；如果hashCode()不相同，则认为两个对象不相同
    //覆盖了hashCode()方法
    //新方法的hashCode计算是根据设备蓝牙地址计算，比如00-11-22-33-44-AB，则将"-"隔开的六个段处理为6个两位十六进制数，将他们相加，转化为十进制返回
    public int hashCode(){
        int hash = 0;//最后结果
        int dash = 0;//蓝牙地址中"-"位置判断flag
        String s = deviceAdress.toLowerCase();//将字符AaBbCcDdEeFf统一处理为小写abcdef

        for (int i =0; i<deviceAdress.length();i++){
            dash++;
            if (dash % 3 == 0){ //遇到"-"自动跳过
                dash = 0;
                continue;
            }
            //字符转十六进制部分
            if (s.charAt(i)>='a' && s.charAt(i)<='f'){
                hash+=Math.pow(16,2-dash)*(s.charAt(i)-'a'+10);
            } else if (s.charAt(i)>='0' && s.charAt(i)<='9'){
                hash+=Math.pow(16,2-dash)*(s.charAt(i)-'0');
            }
        }

        return hash;
    }

    //覆盖了equals()方法，判断两个对象完全相等是根据其设备名和设备地址都相等
    public boolean equals(Object obj){
        BtDevice dev = (BtDevice) obj;
        if (dev.toString().equals(this.toString())){
            return true;
        } else {
            return false;
        }
    }
}
