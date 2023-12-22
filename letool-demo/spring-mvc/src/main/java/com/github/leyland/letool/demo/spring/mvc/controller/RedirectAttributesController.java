package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.pojo.MyModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Map;

/**
 * @ClassName <h2>RedirectAttributesController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class RedirectAttributesController {

    @GetMapping("/redirectAttributes1")
    public String handle1(Model model) {
        model.addAttribute("a", "aaa");
        model.addAttribute("b", 111);
        model.addAttribute("c", new Object());
        model.addAttribute("d", new int[]{1, 2, 3});
        return "redirect:/redirectAttributes2";
    }

    @GetMapping("/redirectAttributes2")
    @ResponseBody
    public void handle2(@RequestParam MultiValueMap<String, String> map, HttpServletRequest request) {
        System.out.println(map);
        Map<String, String[]> parameterMap = request.getParameterMap();
        System.out.println(parameterMap);
    }

    @GetMapping("/redirectAttributes3")
    public String handle3(ModelMap model, RedirectAttributes redirectAttributes) {
        model.addAttribute("a", "aaa");
        model.addAttribute("b", 111);
        model.addAttribute("c", new Object());
        model.addAttribute("d", new int[]{1, 2, 3});

        redirectAttributes.addAttribute("a", "a");
        redirectAttributes.addAttribute("b", 222);
//        MyModel myModel = new MyModel();
//        myModel.setModelId(1L);
//        myModel.setModelName("Rungo");
//        myModel.setModelType(666);
//        redirectAttributes.addAttribute("c", myModel);
//        redirectAttributes.addAttribute("d", new int[]{4, 5});
        return "redirect:/redirectAttributes4";
    }

    @GetMapping("/redirectAttributes4")
    @ResponseBody
    public void handle4(@RequestParam MultiValueMap<String, String> map, HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        System.out.println(parameterMap);
        System.out.println(map);
    }

    @GetMapping("/redirectAttributes5")
    public String handle5(ModelMap model, RedirectAttributes redirectAttributes) {
        model.addAttribute("a", "aaa");
        model.addAttribute("b", 111);
        model.addAttribute("c", new Object());
        model.addAttribute("d", new int[]{1, 2, 3});

        redirectAttributes.addFlashAttribute("a", "aaa");
        redirectAttributes.addFlashAttribute("b", 111);
        MyModel myModel = new MyModel();
        myModel.setModelId(1L);
        myModel.setModelName("Rungo");
        myModel.setModelType(666);
        redirectAttributes.addFlashAttribute("c", myModel);
        redirectAttributes.addFlashAttribute("d", new int[]{1, 2, 3});
        return "redirect:/redirectAttributes7";
    }

    @GetMapping("/redirectAttributes6")
    @ResponseBody
    public void handle6(@RequestParam MultiValueMap<String, String> map, HttpServletRequest request,
                        @ModelAttribute("a") String a, @ModelAttribute("b") String b, @ModelAttribute("c") MyModel c, @ModelAttribute("d") int[] d) {
        //无法获取到参数
        Map<String, String[]> parameterMap = request.getParameterMap();
        System.out.println(parameterMap);
        System.out.println(map);
        //可以通过ModelAttribute获取到参数
        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
        System.out.println(Arrays.toString(d));
    }


    @GetMapping("/redirectAttributes7")
    @ResponseBody
    public void handle7(HttpServletRequest request,
                        @ModelAttribute("a") String a,
                        @ModelAttribute("c") MyModel c,
                        @ModelAttribute("d") int[] d) {
        Map<String, ?> inputFlashMap = RequestContextUtils.getInputFlashMap(request);
        System.out.println(inputFlashMap);
        //可以通过ModelAttribute获取到参数
        System.out.println(a);
        System.out.println(c);
        System.out.println(Arrays.toString(d));
    }

}
