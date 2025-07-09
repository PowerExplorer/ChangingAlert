package com.example.batteryalert;

import android.content.Context;
import android.os.BatteryManager;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class BatteryPropertyInspector {

    public static String inspectBatteryProperties(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        Class<?> clazz = BatteryManager.class;
		StringBuilder sb = new StringBuilder();

        Field[] fields = clazz.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];

            if (Modifier.isStatic(field.getModifiers()) &&
                Modifier.isFinal(field.getModifiers()) &&
                field.getType() == int.class &&
                field.getName().startsWith("BATTERY_PROPERTY_")) {

                try {
                    int id = field.getInt(null);
                    long value = bm.getLongProperty(id);

                    String explanation = explain(field.getName());
                    sb.append(field.getName().substring("BATTERY_PROPERTY_".length()) + " = " + value + " → " + explanation + "\n\n");

                } catch (Exception e) {
                    System.out.println("Lỗi truy cập: " + field.getName());
                }
            }
        }
		return sb.toString().trim();
    }

    // Giải thích từng BATTERY_PROPERTY_*
    private static String explain(String name) {
        if ("BATTERY_PROPERTY_CHARGE_COUNTER".equals(name)) {
            return "Tổng số microampere-giờ (µAh) còn lại trong pin.";
        } else if ("BATTERY_PROPERTY_CURRENT_NOW".equals(name)) {
            return "Dòng điện tức thời (µA), dương nghĩa là đang sạc.";
        } else if ("BATTERY_PROPERTY_CURRENT_AVERAGE".equals(name)) {
            return "Dòng điện trung bình gần đây (µA).";
        } else if ("BATTERY_PROPERTY_CAPACITY".equals(name)) {
            return "Phần trăm pin hiện tại (%), giống như mức hiển thị trên thanh trạng thái.";
        } else if ("BATTERY_PROPERTY_ENERGY_COUNTER".equals(name)) {
            return "Tổng năng lượng còn lại trong pin (nWh).";
        } else {
            return "Không có giải thích cụ thể.";
        }
    }
}

