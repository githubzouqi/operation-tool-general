package com.example.pc2.general.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.pc2.general.R;
import com.example.pc2.general.entity.CarBatteryInfoEntity;
import com.example.pc2.general.utils.ToastUtil;

import java.util.List;

/**
 * Created by Administrator on 2018/3/3.
 */

public class CarBatteryInfoAdapter extends BaseAdapter{

    private LayoutInflater inflater;
    private List<CarBatteryInfoEntity> tempList;
    private Context context;

    public CarBatteryInfoAdapter(Context context, List<CarBatteryInfoEntity> tempList) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.tempList = tempList;
    }

    @Override
    public int getCount() {
        return tempList.size();
    }

    @Override
    public Object getItem(int position) {
        return tempList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        if(convertView == null){
            convertView = inflater.inflate(R.layout.listview_item_battery_info, parent, false);
            holder = new ViewHolder();
            holder.tv_car_id = convertView.findViewById(R.id.tv_car_id);
            holder.tv_car_battery_value = convertView.findViewById(R.id.tv_car_battery_value);
            holder.tv_voltage = convertView.findViewById(R.id.tv_car_battery_voltage);

            convertView.setTag(holder);
        }
        holder = (ViewHolder) convertView.getTag();

        long laveBattery = tempList.get(position).getLaveBattery();// 小车电量
        String status = "";
        if(laveBattery <= 1000 && laveBattery >= 500){
            status = context.getResources().getString(R.string.power_good);
            holder.tv_car_battery_value.setTextColor(context.getResources().getColor(R.color.color_battery_fine));
        }else if(laveBattery < 500 && laveBattery >= 300){
            status = context.getResources().getString(R.string.power_critical);
            holder.tv_car_battery_value.setTextColor(context.getResources().getColor(R.color.color_battery_crisis));
        }else if(laveBattery < 300 && laveBattery >= 100){
            status = context.getResources().getString(R.string.power_low);
            holder.tv_car_battery_value.setTextColor(context.getResources().getColor(R.color.color_battery_low));
        }else if(laveBattery < 100 && laveBattery >= 0){
            status = context.getResources().getString(R.string.power_suspended);
            holder.tv_car_battery_value.setTextColor(context.getResources().getColor(R.color.color_battery_pause));
        }

        holder.tv_car_id.setText(tempList.get(position).getRobotID()+"");
        holder.tv_car_battery_value.setText((float)tempList.get(position).getLaveBattery() / 10 + "%" + "（" + status + "）");
        holder.tv_voltage.setText(Float.parseFloat(String.valueOf(tempList.get(position).getVoltage()))/1000 + "V");

        return convertView;
    }

    class ViewHolder{

        TextView tv_car_id;// 小车id
        TextView tv_car_battery_value;// 小车电池电量
        TextView tv_voltage;// 小车电池电压

    }

}
