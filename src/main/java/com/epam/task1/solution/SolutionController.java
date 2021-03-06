package com.epam.task1.solution;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Controller
@RequestMapping("/solution")
public class SolutionController {


    //请求缓存
    private Map<String, Object> provinces = null;
    private final Map<String, Map<String, Object>> citys = new HashMap<>();
    private final Map<String, Map<String, Object>> countrys = new HashMap<>();

    private final TokenServer tokenServer;

    {
        tokenServer = TokenServer.newBuilder();
        tokenServer.maxFlowRate(100);
        tokenServer.avgFlowRate(100);
        tokenServer.build();
    }

    @RequestMapping("/index")
    @ResponseBody
    public Optional<String> getTemperature(String province, String city, String country) throws InterruptedException {


        //获取不到令牌则进行阻塞
        if (!tokenServer.tryAcquire()) {
            return Optional.of("服务器繁忙，请稍后再试");
        }
        //构建Http请求
        String provincesUrl = "http://www.weather.com.cn/data/city3jdata/china.html";
        try {
            if (null == provinces) {
                //push to the cahe
                this.provinces = getOptionalResult(provincesUrl);
            }
            String provinceCode = getCode(province, this.provinces);

            if (null != provinceCode) {
                String cityCode = getCityCodeByProvinceCode(provinceCode, city);
                if (null != cityCode) {
                    String countryCode = getCountryCodeByCityCode(provinceCode + cityCode, country);
                    if (null != countryCode) {
                        System.out.println();
                        String res = getOptionalResult("http://www.weather.com.cn/data/sk/"
                                + provinceCode + cityCode + countryCode + ".html").
                                get("weatherinfo", JSONObject.class).get("temp").toString();
                        System.out.println(new Date()+"请求成功，结果："+res);
                        return Optional.of(
                                res);
                    } else {
                        return Optional.of("该城市不存在这个县");
                    }
                } else {
                    return Optional.of("该省不存在这个城市");
                }
            } else {
                return Optional.of("省份不存在");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return Optional.of("系统超时");
        } catch (IORuntimeException e) {
            return Optional.of("请求超时");
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.of("服务器错误");
        }

    }

    private String getCountryCodeByCityCode(String cityCode, String country) throws InterruptedException {
        Map<String, Object> countrys = this.countrys.get(cityCode);
        if (null != countrys) {
            return getCode(country, countrys);
        }
        String countryUrl = "http://www.weather.com.cn/data/city3jdata/station/" + cityCode + ".html";
        countrys = getOptionalResult(countryUrl);
        this.countrys.put(cityCode, countrys);

        return getCode(country, countrys);
    }

    private String getCityCodeByProvinceCode(String proviceCode, String city) throws InterruptedException {
        Map<String, Object> citys = this.citys.get(proviceCode);
        if (null != citys) {
            return getCode(city, citys);
        }
        String cityUrl = "http://www.weather.com.cn/data/city3jdata/provshi/" + proviceCode + ".html";
        citys = getOptionalResult(cityUrl);

        this.citys.put(proviceCode, citys);
        return getCode(city, citys);
    }

    //get the code from the collentions
    private String getCode(String province, Map<String, Object> maps) {
        //get the key of value
        for (Map.Entry<String, Object> entry : maps.entrySet()) {
            if (province.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    //get url returns
    private JSONObject getOptionalResult(String url) throws InterruptedException {
        //default try 5 times
        return this.getOptionalResult(url, 5);
    }

    private JSONObject getOptionalResult(String url, Integer times) throws InterruptedException {


        if (null == times) {
            //we have five time to get the url returns
            times = 5;
        }
        HttpResponse result = HttpRequest.get(url)
                .header(Header.USER_AGENT, "Native http")//头信息，多个头信息多次调用此方法即可
                .setConnectionTimeout(2000)
                .execute();

        if (!result.isOk()) {
            if (times == 0) {
                throw new InterruptedException();
            }
            times--;
            Thread.sleep(500L);
            getOptionalResult(url, times);
        }
        return new JSONObject(result.body()
                , true);
    }


}
