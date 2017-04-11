package com.example.luoguizhao.bluetoothscanlogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by luoguizhao on 2017/4/10.
 * csv文件处理相关类，包含两个处理方法：
 * 1. importCsv(File)，导入包含设备名称和设备地址的csv文件，更新Set
 * 2. exportCsv(File, Set<BtDevice>)，将需要导入的Set导入到csv文件中，包含设备名+设备地址+签到确认信息（布尔量）
 */

public class csvProcessor {
    //csv文件导入方法，带文件地址参数
    public static Set<BtDevice> importCsv(File file,int mode){
        Set<BtDevice> tmpSet = new HashSet<BtDevice>();

        BufferedReader br=null;
        try {
            br = new BufferedReader(new FileReader(file));
            String line = "";
            while ((line = br.readLine()) != null) {
                //Log.e("TAG",line);

                String [] sourceStrArray = line.split(",",3);
                BtDevice tmpDev=null;
                if (mode == 0){
                    tmpDev = new BtDevice(sourceStrArray[0],sourceStrArray[1],false);//mode=0,导入名单列表(设备名+设备地址），默认未签到，state取false
                } else if (mode == 1){
                    tmpDev = new BtDevice(sourceStrArray[0],"",false);//mode=1,导入名单列表（设备名），设备地址未知，默认未签到，state取false
                } else if (mode == 2){
                    tmpDev = new BtDevice(sourceStrArray[0],sourceStrArray[1],Boolean.parseBoolean(sourceStrArray[2]));//导入名单列表以及签到情况
                }
                tmpSet.add(tmpDev);
            }
        }catch (Exception e) {
        }finally{
            if(br!=null){
                try {
                    br.close();
                    br=null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return tmpSet;
    }

    //csv文件导出方法，带文件地址和存储内容参数
    public static boolean exportCsv(File file, Set<BtDevice> exportDevSet){
        boolean isSucess=false;

        FileOutputStream out=null;
        OutputStreamWriter osw=null;
        BufferedWriter bw=null;
        try {
            out = new FileOutputStream(file);
            osw = new OutputStreamWriter(out);
            bw =new BufferedWriter(osw);
            if(exportDevSet!=null && !exportDevSet.isEmpty()){
                for(BtDevice tmpDev : exportDevSet){
                    bw.append(tmpDev.getName()).append(",").append(tmpDev.getAddress()).append(",").append(""+tmpDev.getCheckedState()).append("\r\n");
                }
            }
            isSucess=true;
        } catch (Exception e) {
            isSucess=false;
        }finally{
            if(bw!=null){
                try {
                    bw.close();
                    bw=null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(osw!=null){
                try {
                    osw.close();
                    osw=null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(out!=null){
                try {
                    out.close();
                    out=null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return isSucess;
    }
}
