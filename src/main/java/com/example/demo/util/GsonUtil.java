package com.example.demo.util;

import com.example.demo.adapter.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @Author: ytl
 * @Date: 2022/8/27 10:13
 * @Description: 描述功能
 */
public class GsonUtil {

    private static final String format2 = "yyyy-MM-dd HH:mm:ss";


    /**
     * @Author: ytl12
     * @Date: 2022/8/27 10:24
     * @Description:   java对象(包括list、map)转json字符串
     * @param:[]
     * @return:String
     */
    public static String toJsonStr(Object obj) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setDateFormat(format2);//序列化的对象存在时间Date属性时，构建时需要指定时间格式，不然在linux上会报错
        gsonBuilder.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter());
        gsonBuilder.serializeNulls();//gson本身默认不序列化空值的字段，如果需要序列化，需该配置
        Gson gson = gsonBuilder.create();
        return gson.toJson(obj);
    }
    /**
     * @Author: ytl12
     * @Date: 2022/8/27 10:30
     * @Description:    json字符串转java对象
     * @param:[jsonStr, c]
     * @return:T
     */
    public static <T> T fromJson(String jsonStr,Class<T> c) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setDateFormat(format2);//序列化的对象存在时间Date属性时，构建时需要指定时间格式，不然在linux上会报错
        Gson gson = gsonBuilder.create();
        return gson.fromJson(jsonStr, c);
    }
    /**
     * @Author: ytl12
     * @Date: 2022/8/27 10:34
     * @Description:  json字符串数组转list对象
     * @param:[jsonArrStr]
     * @return:List<T>
     */
    public static <T> List<T> fromJsonToList(String jsonArrStr) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setDateFormat(format2);//序列化的对象存在时间Date属性时，构建时需要指定时间格式，不然在linux上会报错
        Gson gson = gsonBuilder.create();
        return gson.fromJson(jsonArrStr, new TypeToken<List<T>>(){}.getType());
    }
    /**
     * @Author: ytl12
     * @Date: 2022/8/27 10:34
     * @Description:  json字符串转map对象
     * @param:[jsonArrStr]
     * @return:List<T>
     */
    public static Map<String,Object> fromJsonToMap(String jsonStr) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setDateFormat(format2);//序列化的对象存在时间Date属性时，构建时需要指定时间格式，不然在linux上会报错
        Gson gson = gsonBuilder.create();
        return gson.fromJson(jsonStr, new TypeToken<Map<String,Object> >(){}.getType());
    }

    public static <T> T transMapToObj(Map map, Class<T> cls) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setDateFormat(format2);//序列化的对象存在时间Date属性时，构建时需要指定时间格式，不然在linux上会报错
        Gson gson = gsonBuilder.create();
        return gson.fromJson(gson.toJson(map), cls);
    }

}
