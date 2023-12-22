package com.github.leyland.base.fastjson.test;

import com.alibaba.fastjson2.*;
import org.apache.log4j.Logger;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * @ClassName <h2>FastJsonTest</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class FastJsonTest {
    /**
     {
         "studentName": "lily",
         "studentAge": 12
     }
     */
    //json字符串-简单对象型
    private static final String JSON_OBJ_STR = "{\"studentName\":\"lily\",\"studentAge\":12}";


    /*
        [{
            "studentName": "lily",
            "studentAge": 12
        }, {
            "studentName": "lucy",
            "studentAge": 15
        }]
    */

    //json字符串-数组类型
    private static final String JSON_ARRAY_STR = "[{\"studentName\":\"lily\",\"studentAge\":12},{\"studentName\":\"lucy\",\"studentAge\":15}]";

    /*
        {
            "teacherName": "crystall",
            "teacherAge": 27,
            "course": {
                "courseName": "english",
                "code": 1270
            },
            "students": [
                {
                    "studentName": "lily",
                    "studentAge": 12
                },
                {
                    "studentName": "lucy",
                    "studentAge": 15
                }
            ]
        }
    */
    //复杂格式json字符串
    private static final String COMPLEX_JSON_STR = "{\"teacherName\":\"crystall\",\"teacherAge\":27,\"course\":{\"courseName\":\"english\",\"code\":1270},\"students\":[{\"studentName\":\"lily\",\"studentAge\":12},{\"studentName\":\"lucy\",\"studentAge\":15}]}";

    private static Logger log = Logger.getLogger(FastJsonTest.class);

    /**
     * json字符串-简单对象型到JSONObject的转换
     */
    @Test
    public void testJSONStrToJSONObject() {
        JSONObject data = JSON.parseObject(JSON_OBJ_STR);
        System.out.println("studentName:  " + data.getString("studentName") + ":" + "  studentAge:  " + data.getInteger("studentAge"));
        log.info(data.toString(JSONWriter.Feature.PrettyFormat));

        JSONObject byteData = JSON.parseObject(JSON_OBJ_STR.getBytes());
        log.info(byteData.toString(JSONWriter.Feature.PrettyFormat));
    }

    @Test
    public void testJSONStrToJSONObjectForByte() {
        JSONObject byteData = JSON.parseObject(JSON_OBJ_STR.getBytes());
        log.info(byteData.toString(JSONWriter.Feature.PrettyFormat));
    }


    /**
     * json字符串-数组类型到JSONArray的转换
     */
    @Test
    public void testJSONStrToJSONArray(){
        JSONArray data = JSON.parseArray(JSON_ARRAY_STR);
        log.info(data.toString(JSONWriter.Feature.PrettyFormat));

        JSONArray byteData = JSON.parseArray(JSON_ARRAY_STR);
        log.info(byteData.toString());
    }


    /**
     * json字符串-混合类型到Map的转换
     */
    @Test
    public void testJSONStrToMap(){
        String jsonStr = "{\n" +
                "            \"msg\": \"操作成功!\",\n" +
                "                \"code\": \"200\",\n" +
                "                \"data\": [\n" +
                "            {\n" +
                "                \"id\": \"0683d34aa31e4323be6d21b6d2bbd19eE41891461\",\n" +
                "                    \"status\": 1,\n" +
                "                    \"netAddress\": \"36.7.108.200\"\n" +
                "            },\n" +
                "            {\n" +
                "                \"id\": \"0683d34aa31e4323be6d21b6d2bbd19eE41891466\",\n" +
                "                    \"status\": 1,\n" +
                "                    \"netAddress\": \"39.7.109.208\"\n" +
                "            }\n" +
                "        ],\n" +
                "        }";

        Map<String, Object> maps = JSON.parseObject(jsonStr, Map.class);
        maps.entrySet().stream().forEach(x -> log.info((x.getKey() + " : " + x.getValue())));
    }

    @Test
    public void testJSONStrToList(){
        String jsonStr = "[\n" +
                "  {\n" +
                "    \"id\": \"4545454562dew33wf3f433213322ssssssffrretyuu\",\n" +
                "    \"status\": 1,\n" +
                "    \"netAddress\": \"8.8.9.9\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"34f323232323df4f132424kikikunukikiololscsuu\",\n" +
                "    \"status\": 1,\n" +
                "    \"netAddress\": \"114.114.114.114\"\n" +
                "  }\n" +
                "]\n";
        List<Map> listMaps = JSON.parseArray(jsonStr, Map.class);
        listMaps.stream().forEach(map -> {
            // 在这里进行每个 Map 的处理
            log.info(map);
        });
    }


    @Test
    public void testJSONStrToList2(){
        String jsonStr = "[\n" +
                "  {\n" +
                "    \"id\": \"4545454562dew33wf3f433213322ssssssffrretyuu\",\n" +
                "    \"status\": 1,\n" +
                "    \"netAddress\": \"8.8.9.9\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"id\": \"34f323232323df4f132424kikikunukikiololscsuu\",\n" +
                "    \"status\": 1,\n" +
                "    \"netAddress\": \"114.114.114.114\"\n" +
                "  }\n" +
                "]\n";
        List<Map<String,Object>> listMaps = JSON.parseObject(jsonStr, new TypeReference<List<Map<String,Object>>>(){});
        listMaps.stream().forEach(map -> {
            // 在这里进行每个 Map 的处理
            map.entrySet().stream().forEach(entry ->
                    log.info("key=" + entry.getKey() + ", value= " + entry.getValue())
            );
        });
    }


    @Test
    public void test22(){
        String jsonStr = "[{\"name\":\"Tom\",\"age\":19},{\"name\":\"Jack\",\"age\":20}]";
        System.out.println(FastJsonTest.class.getSimpleName());


    }












    @Test
    public void testParse1(){
        JSONObject jsonObject = (JSONObject) JSON.parse(JSON_OBJ_STR);
        System.out.println("studentName:  " + jsonObject.getString("studentName") + ":" + "  studentAge:  " + jsonObject.getInteger("studentAge"));
    }

    @Test
    public void testParseObject1(){
        JSONObject jsonObject = (JSONObject) JSON.parse(COMPLEX_JSON_STR);
        System.out.println(jsonObject.toString());
    }

    @Test
    public void testParseObject2(){
        Map<String, Object> jsonObject = JSON.parseObject(COMPLEX_JSON_STR, Map.class);
        System.out.println(jsonObject.toString());
    }


}
